package com.tencent.tinker.internal.utils

import android.os.Build
import android.system.Os
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.file.Files
import kotlin.io.use

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
 * Executes the given block function on this resource and then closes it down.
 *
 * The function returns the result of the [block] function.
 */
internal inline fun <T : AutoCloseable, R> T.use(block: (T) -> R): R {
    try {
        return block(this)
    } finally {
        this.close()
    }
}

/**
 * Reads/writes file content with shared/exclusive lock.
 *
 * The caller must guarantee that the file is exists.
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
 * The caller must guarantee that the file is exists.
 */
internal val File.guardedContentNullable: ByteArray?
    get() = inputStream().use { stream ->
        stream.channel
            .tryLock(0, Long.MAX_VALUE, true)
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
        file.channel.tryLock()
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
            stream.channel.tryLock(0, Long.MAX_VALUE, true)
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
        stream.channel.tryLock()
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

internal fun File.ensureIsExistingFile(): File = apply {
    if (exists() && !isFile) {
        if (isDirectory) {
            deleteRecursively()
        } else {
            delete()
        }
    }
    parentFile!!.ensureIsExistingDirectory()
    if (!exists()) {
        createNewFile()
    }
}

internal fun File.ensureIsExistingDirectory(): File = apply {
    if (exists() && !isDirectory) {
        delete()
    }
    if (!exists()) {
        mkdirs()
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