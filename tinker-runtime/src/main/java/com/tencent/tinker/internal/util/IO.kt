package com.tencent.tinker.internal.util

import android.os.Build
import android.system.Os
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * Some old versions of Android throws `IOException` with errno `EAGAIN` instead of returning null
 * while calling `FileChannel.tryLock()`. We have to handle this case.
 */
private fun FileChannel.compatTryLock(shared: Boolean = false): FileLock? {
    try {
        return tryLock(0, Long.MAX_VALUE, shared)
    } catch (exception: IOException) {
        exception.message?.let {
            if (it.contains("EAGAIN")) {
                return null
            }
        }
        throw exception
    }
}

/**
 * Closes the given resource without throwing any exceptions.
 */
internal fun AutoCloseable.closeQuietly() {
    try {
        close()
    } catch (_: Throwable) {
    }
}

/**
 * Reads/writes file content with shared/exclusive lock.
 *
 * The caller must guarantee that the file is exist.
 */
internal var File.guardedContent: ByteArray
    get() = inputStream().use { stream ->
        stream.channel
            .lock(0, Long.MAX_VALUE, true)
            .use {
                stream.readBytes()
            }
    }
    set(value) {
        outputStream().use { stream ->
            stream.channel
                .lock()
                .use {
                    stream.write(value)
                }
        }
    }

/**
 * Reads file content with shared lock. The function returns null if read lock cannot be acquired.
 *
 * The caller must guarantee that the file is exist.
 */
internal val File.guardedContentNullable: ByteArray?
    get() = inputStream().use { stream ->
        stream.channel
            .compatTryLock(true)
            ?.use {
                stream.readBytes()
            }
    }

/**
 * Reads/writes file content with exclusive lock inside the given [block] function.
 *
 * The function returns the result of the [block] function.
 */
internal fun <T> File.guardedReadOrWriteContent(block: (RandomAccessFile) -> T): T = run {
    parentFile?.let {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
    RandomAccessFile(this, "rw").use { file ->
        file.channel.lock().use {
            block(file)
        }
    }
}

/**
 * Tries read/write file content with exclusive lock inside the given [block] function.
 *
 * The function returns the result of the [block] function.
 */
internal fun <T> File.guardedReadOrWriteContentNullable(block: (RandomAccessFile) -> T): T? = run {
    parentFile?.let {
        if (!it.exists()) {
            it.mkdirs()
        }
    }
    RandomAccessFile(this, "rw").use { file ->
        file.channel.compatTryLock()
            ?.use {
                block(file)
            }
    }
}

/**
 * File content reads from file, and as a handler to release escaped lock and stream.
 */
internal class EscapedGuardedContent(
    val content: ByteArray,
    private val stream: AutoCloseable,
    private val lock: FileLock
) : AutoCloseable {
    override fun close() {
        lock.closeQuietly()
        stream.closeQuietly()
    }
}

/**
 * Reads content from file with shared lock, and does not release lock and stream automatically.
 * The caller should release them by releasing returned handler.
 */
internal val File.escapedGuardedContentShared: EscapedGuardedContent
    get() {
        val stream = inputStream()
        val lock = try {
            stream.channel.lock(0, Long.MAX_VALUE, true)
        } catch (throwable: Throwable) {
            stream.closeQuietly()
            throw throwable
        }
        val content = try {
            stream.readBytes()
        } catch (exception: IOException) {
            lock.closeQuietly()
            stream.closeQuietly()
            throw exception
        }
        return EscapedGuardedContent(content, stream, lock)
    }

/**
 * Tries to read content from file with shared lock, and does not release lock and stream
 * automatically. The caller should release them by releasing returned handler.
 */
internal val File.escapedGuardedContentSharedNullable: EscapedGuardedContent?
    get() {
        val stream = inputStream()
        val lock = try {
            stream.channel.compatTryLock(true)
        } catch (_: Throwable) {
            stream.closeQuietly()
            return null
        }
        if (lock == null) {
            stream.closeQuietly()
            return null
        }
        val content = try {
            stream.readBytes()
        } catch (_: IOException) {
            lock.closeQuietly()
            stream.closeQuietly()
            return null
        }
        return EscapedGuardedContent(content, stream, lock)
    }

/**
 * Writes [content] to file with exclusive lock, and does not release lock and stream automatically.
 * The caller should release them by releasing returned handler.
 */
internal fun File.escapedGuardedContentExclusive(content: ByteArray): EscapedGuardedContent {
    val stream = outputStream()
    val lock = stream.channel.lock()
    stream.write(content)
    return EscapedGuardedContent(content, stream, lock)
}

/**
 * Tries to write [content] to file with exclusive lock, and does not release lock and stream
 * automatically. The caller should release them by releasing returned handler.
 */
internal fun File.escapedGuardedContentExclusiveNullable(content: ByteArray): EscapedGuardedContent? {
    val stream = outputStream()
    val lock = try {
        stream.channel.compatTryLock()
    } catch (_: Throwable) {
        stream.closeQuietly()
        return null
    }
    if (lock == null) {
        stream.closeQuietly()
        return null
    }
    try {
        stream.write(content)
    } catch (_: IOException) {
        lock.closeQuietly()
        stream.closeQuietly()
        return null
    }
    return EscapedGuardedContent(content, stream, lock)
}

/**
 * Ensures the file is existing and is a directory.
 *
 * Returns the file itself.
 */
internal fun File.ensureIsExistingDirectory(): File = apply {
    if (exists() && !isDirectory) {
        delete()
    }
    if (!exists()) {
        mkdirs()
    }
}

/**
 * Ensures the parent directory of the file is existing.
 *
 * Returns the file itself.
 */
internal fun File.ensureParentIsExistingDirectory(): File = apply {
    parentFile!!.ensureIsExistingDirectory()
}

/**
 * Ensures the file is existing and is a file.
 *
 * Returns the file itself.
 */
internal fun File.ensureIsExistingFile(): File = apply {
    if (exists() && !isFile) {
        if (isDirectory) {
            deleteRecursively()
        } else {
            delete()
        }
    }
    ensureParentIsExistingDirectory()
    if (!exists()) {
        createNewFile()
    }
}

/**
 * Returns true if the file is a readable non-empty file.
 */
internal val File.isReadableNonEmptyFile: Boolean
    get() = isFile && canRead() && length() > 0

/**
 * Creates a symbolic link to [target] path.
 */
internal fun File.symlinkTo(target: File) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Files.createSymbolicLink(target.toPath(), toPath())
    } else {
        Os.symlink(canonicalPath, target.canonicalPath)
    }
}

/**
 * Does [action] with a temporary file. The file will be deleted after [action] is done.
 */
@OptIn(ExperimentalContracts::class)
internal fun <T> withTemporaryFile(action: (File) -> T): T {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }
    val file = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Files.createTempFile("tinker-", ".temp").toFile()
    } else {
        File.createTempFile("tinker-", ".temp")
    }
    return file
        .ensureIsExistingFile()
        .let {
            try {
                action(it)
            } finally {
                it.delete()
            }
        }
}

/**
 * Does [action] with a temporary directory. The directory will be deleted after [action] is done.
 */
@OptIn(ExperimentalContracts::class)
internal fun <T> withTemporaryDirectory(action: (File) -> T): T {
    contract {
        callsInPlace(action, InvocationKind.EXACTLY_ONCE)
    }
    val directory = File.createTempFile("tinker-", ".temp")
        .apply {
            delete()
        }
    return directory
        .ensureIsExistingDirectory()
        .let {
            try {
                action(it)
            } finally {
                it.deleteRecursively()
            }
        }
}