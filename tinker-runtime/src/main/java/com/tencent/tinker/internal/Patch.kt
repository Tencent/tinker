package com.tencent.tinker.internal

import android.content.Context
import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import com.tencent.tinker.internal.annotation.MainProcessOnly
import com.tencent.tinker.internal.annotation.PatchProcessOnly
import com.tencent.tinker.internal.annotation.NonPatchProcessOnly
import com.tencent.tinker.internal.utils.EscapedGuardedContent
import com.tencent.tinker.internal.utils.escapedGuardedContentExclusive
import com.tencent.tinker.internal.utils.escapedGuardedContentExclusiveNullable
import com.tencent.tinker.internal.utils.escapedGuardedContentSharedNullable
import com.tencent.tinker.internal.utils.guardedContent
import com.tencent.tinker.internal.utils.guardedContentNullable
import com.tencent.tinker.internal.utils.guardedReadOrWriteContent
import com.tencent.tinker.internal.utils.isInMainProcess
import com.tencent.tinker.internal.utils.isInPatchProcess
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.collections.toString

private fun File.createNotWritableCopy(target: File) {
    if (isDirectory) {
        target.mkdirs()
        listFiles()!!.forEach { from ->
            val to = target.resolve(from.relativeTo(this))
            from.createNotWritableCopy(to)
        }
    } else {
        copyTo(target)
    }
    target.setReadable(canRead())
    target.setWritable(false)
    target.setExecutable(canExecute())
}

/**
 * Information about a patch managed by [TinkerPatchManager].
 *
 * Attention: all content should be treated as unavailable if current process already requested it
 * as unavailable by using [TinkerPatchManager.requestUnavailable].
 */
internal class TinkerPatch(
    /**
     * Version of this patch.
     */
    val version: String,

    /**
     * Base directory of this patch. This directory and all its children are non-writable.
     *
     * The directory may be cleaned if current process already requested patch as unavailable by
     * using [TinkerPatchManager.requestUnavailable].
     */
    val directory: File,
)

/**
 * Manager for patches.
 */
internal object TinkerPatchManager {

    /**
     * Base directory of patch files.
     */
    private val Context.baseDirectory: File
        get() = rootDirectory.resolve("patches-isolated")

    @VisibleForTesting
    fun baseDirectoryForTesting(context: Context): File =
        context.baseDirectory


    /**
     * A simple file to record latest patch version.
     *
     * The content of this file may be empty or corrupted because of any I/O error. The caller
     * should check its content is valid before using.
     */
    private val Context.latestVersionFile: File
        get() = baseDirectory.resolve("latest_version")

    @VisibleForTesting
    fun latestVersionFileForTesting(context: Context): File =
        context.latestVersionFile

    @set:PatchProcessOnly
    private var Context.latestVersion: String?
        get() = latestVersionFile
            .takeIf { it.exists() }
            ?.guardedContent
            ?.toString(Charsets.UTF_8)
            ?.takeIf { it.isNotEmpty() }
        set(value) {
            value?.let {
                latestVersionFile
                    .apply { parentFile!!.mkdirs() }
                    .guardedContent = it.toByteArray(Charsets.UTF_8)
            } ?: latestVersionFile.delete()
        }


    /**
     * Guard lock is a mechanism based on file lock to protect used patch files not be deleted by
     * cleaner.
     *
     * Each patch version has its independent guard lock file. When a process is trying to apply a
     * patch version, it have to acquire a use guard (based on shared lock) of the guard lock file
     * first, and check the guard file content is not [GUARD_CLEANING_CONTENT] (its meaning is
     * described below). While cleaner is cleaning obsolete patch version directories, it tries to
     * acquire a clean guard (based on exclusive lock), If failed, which means there are other
     * processes using this patch version, the cleaner must skip cleaning this patch version.
     *
     * The content of the guard lock file is empty or a byte with value [GUARD_CLEANING_CONTENT].
     * The content is set only if the cleaner already acquired the exclusive lock, and prepares to
     * clean the guard lock file itself after cleaning the patch version directory. Even if a
     * process acquires the shared lock successfully, if the content is [GUARD_CLEANING_CONTENT],
     * the patch version is still invalid.
     */
    private fun Context.guardFile(version: String): File {
        return baseDirectory
            .resolve("guards")
            .resolve(version)
    }

    /**
     * A byte may in the guard file to indicate that the patch is being cleaned up.
     */
    private const val GUARD_CLEANING_CONTENT = 1.toByte()

    /**
     * Acquires patch of [version] is used by current process. The function returns null if patch
     * process is holding patch to clean up.
     */
    private fun Context.acquirePatchAsUsing(version: String): EscapedGuardedContent? {
        val file = guardFile(version)
            .apply {
                if (!exists()) {
                    parentFile!!.mkdirs()
                    // Create empty file with lock if it is not exists.
                    this.guardedContent = ByteArray(0)
                }
            }
        val guardedContent = file.escapedGuardedContentSharedNullable ?: return null
        if (guardedContent.content.isNotEmpty() && guardedContent.content[0] == GUARD_CLEANING_CONTENT) {
            guardedContent.close()
            return null
        }
        return guardedContent
    }

    /**
     * Acquires patch of [version] is prepared to be cleaned by patch process. The function returns
     * null if patch is now used by any process.
     */
    @PatchProcessOnly
    private fun Context.acquirePatchAsCleaning(version: String): EscapedGuardedContent? {
        val file = guardFile(version)
            .apply {
                parentFile!!.mkdirs()
            }
        return file.escapedGuardedContentExclusiveNullable(
            ByteArray(1) { GUARD_CLEANING_CONTENT }
        )
    }


    /**
     * A simple file to record latest patch version.
     *
     * Its content is only available when exclusive lock of [mainAliveFile] is held. Which means
     * the main process is still alive.
     */
    private val Context.mainVersionFile: File
        get() = baseDirectory.resolve("main_version")

    /**
     * A simple file to indicate whether main process is alive.
     *
     * The main process should hold an exclusive lock of this file to represent that it is alive.
     */
    private val Context.mainAliveFile: File
        get() = baseDirectory.resolve("main_alive")

    /**
     * For main process, the process should modify main version file before main alive exclusive
     * lock is held using [markMainAlive]. Once non-main processes check if main process is alive
     * by acquiring main alive shared lock, they may check main version immediately.
     *
     * For non-main process, the process should always read main version after check [isMainAlive]
     * and it returns true. The returned value is meaningless if main process is not alive.
     */
    @set:MainProcessOnly
    private var Context.mainVersion: String?
        get() = mainVersionFile.takeIf { it.exists() }
            ?.guardedContentNullable
            ?.toString(Charsets.UTF_8)
        set(value) {
            value?.let {
                mainVersionFile
                    .apply { parentFile!!.mkdirs() }
                    .guardedContent = it.toByteArray(Charsets.UTF_8)
            } ?: mainVersionFile.delete()
        }

    @GuardedBy("this")
    private var mainAliveHolder: EscapedGuardedContent? = null

    @MainProcessOnly
    @GuardedBy("this")
    private fun Context.markMainAlive() {
        val file = mainAliveFile
            .apply {
                parentFile!!.mkdirs()
            }
        file.escapedGuardedContentExclusive(ByteArray(0))
            .also {
                check(mainAliveHolder == null) {
                    "Main process is already marked as alive"
                }
                mainAliveHolder = it
            }
    }

    /**
     * The function does nothing if current process is not main process.
     */
    @GuardedBy("this")
    private fun unmarkMainAlive() {
        mainAliveHolder?.close()
        mainAliveHolder = null
    }

    /**
     * Checks if main process is alive in non-main processes.
     */
    private val Context.isMainAlive: Boolean
        get() = mainAliveFile.takeIf { it.exists() }?.guardedContentNullable == null


    /**
     * A simple file to record unavailable patch versions.
     *
     * Each line in the file is a patch version. Once a patch version is marked as unavailable by
     * non-patch processes using [requestUnavailable], it will be added to this file.
     *
     * While patch processes are cleaning up obsolete patch versions, non-remained patch versions
     * will be removed from this file.
     */
    private val Context.unavailableFile: File
        get() = baseDirectory.resolve("unavailable")

    private val Context.unavailable: Set<String>
        get() = unavailableFile.takeIf { it.exists() }
            ?.guardedContent
            ?.toString(Charsets.UTF_8)
            ?.lines()
            ?.toSet()
            ?: emptySet()

    private fun Context.updateUnavailable(action: (Set<String>) -> Set<String>) {
        unavailableFile.guardedReadOrWriteContent { file ->
            file.seek(0)
            val content = ByteArray(file.length().toInt())
                .also(file::read)
            val updated = content
                .toString(Charsets.UTF_8)
                .lines()
                .toSet()
                .let(action)
            file.seek(0)
            file.setLength(0)
            updated.sorted()
                .joinToString("\n")
                .toByteArray(Charsets.UTF_8)
                .let(file::write)
        }
    }

    private val unavailableRequestListeners = ConcurrentLinkedQueue<Runnable>()


    /**
     * A container used to hold guarded content of patch guard file. Unless release manually,
     * guarded content will never released until process dead. Which means lifecycles of guarded
     * content are same as process lifecycle.
     */
    @GuardedBy("this")
    @NonPatchProcessOnly
    private var versionHolder: Pair<EscapedGuardedContent, AcquiringRecord>? = null

    private class AcquiringRecord(version: String) :
        RuntimeException("Last acquiring with version \"${version}\" is invoked with following stacktrace") {
        init {
            stackTrace = stackTrace
                .dropWhile {
                    it.className == TinkerPatchManager.javaClass.name
                }
                .toTypedArray()
        }
    }

    @NonPatchProcessOnly
    private fun releaseVersion() {
        versionHolder?.let { (guardedContent, _) ->
            guardedContent.close()
        }
        versionHolder = null
    }

    @VisibleForTesting
    @Synchronized
    fun releaseAllHoldersForTesting() {
        unmarkMainAlive()
        releaseVersion()
    }


    /**
     * Each patch version has its own independent directory to store patch files.
     *
     * The patch directories are maintained by patch processes, and will be marked as non-writable
     * after it is created and until it is cleaned up. Other processes can only read or execute
     * files in these directories.
     */
    private val Context.patchesDirectory: File
        get() = baseDirectory.resolve("patches")

    private fun Context.patchDirectory(version: String): File =
        patchesDirectory.resolve(version)

    @VisibleForTesting
    fun patchDirectoryForTesting(context: Context, version: String): File =
        context.patchDirectory(version)

    @PatchProcessOnly
    private fun Context.cleanPatches(keep: (String) -> Boolean): List<String> {
        val patchDirectories = patchesDirectory
            .takeIf { it.exists() }
            ?.listFiles()
            ?.filter { it.isDirectory }
            ?: return emptyList()
        // A pair of version and whether the patch is cleaned.
        val pairs = patchDirectories.mapNotNull { dir ->
            val version = dir.name
            if (keep(version)) {
                return@mapNotNull Pair(version, false)
            }
            val guardedContent = try {
                acquirePatchAsCleaning(version)
            } catch (throwable: Throwable) {
                throw Error(
                    Error.Type.ACQUIRE_PATCH_AS_CLEANING,
                    "Cannot acquire patch \"${version}\" as clean",
                    throwable,
                )
            }
            if (guardedContent != null) {
                try {
                    dir.walk(direction = FileWalkDirection.TOP_DOWN).forEach {
                        it.setWritable(true)
                    }
                } catch (throwable: Throwable) {
                    throw Error(
                        Error.Type.RECOVER_PATCH_WRITE_PERMISSION,
                        "Cannot recover patch directory \"${dir.absolutePath}\" as writable",
                        throwable,
                    )
                }
                try {
                    dir.deleteRecursively()
                } catch (throwable: Throwable) {
                    throw Error(
                        Error.Type.CLEAN_PATCH,
                        "Cannot clean patch directory \"${dir.absolutePath}\"",
                        throwable,
                    )
                }
                guardedContent.close()
                return@mapNotNull Pair(version, true)
            } else {
                return@mapNotNull Pair(version, false)
            }
        }
        // Remove unavailable records except those that are still remaining.
        pairs.filter { !it.second }
            .map { it.first }
            .let { remaining ->
                try {
                    updateUnavailable { unavailable ->
                        unavailable
                            .toMutableSet()
                            .apply {
                                retainAll(remaining)
                            }
                    }
                } catch (throwable: Throwable) {
                    throw Error(
                        Error.Type.CLEAN_UNAVAILABLE,
                        "Cannot clean unavailable records",
                        throwable,
                    )
                }
            }
        return pairs.filter { it.second }.map { it.first }
    }


    /**
     * An error raised by patch manager.
     */
    class Error(
        val type: Type,
        message: String,
        cause: Throwable?
    ) : Exception(message, cause) {
        enum class Type(val code: Int) {
            ACQUIRE_PATCH_AS_USING(-100),
            ACQUIRE_PATCH_AS_CLEANING(-101),
            HAS_ACQUIRED_PATCH(-102),
            READ_LATEST_VERSION(-110),
            WRITE_LATEST_VERSION(-110),
            READ_MAIN_VERSION(-112),
            WRITE_MAIN_VERSION(-113),
            READ_UNAVAILABLE(-120),
            APPEND_UNAVAILABLE(-121),
            CLEAN_UNAVAILABLE(-122),
            MARK_MAIN_ALIVE(-140),
            CHECK_MAIN_ALIVE(-141),
            CREATE_EXIST_PATCH(-150),
            CLONE_PATCH(-151),
            CLEAN_PATCH(-152),
            DROP_PATCH_WRITE_PERMISSION(-153),
            RECOVER_PATCH_WRITE_PERMISSION(-154),
        }
    }


    /**
     * Gets patch which is available for current process.
     *
     * If caller process is main process, state of patches which are used by running non-main
     * processes is ignored, and latest version patch is returned directly. Caller processes should
     * deal with issues of inconsistent patch state by themselves, like killing other processes.
     *
     * Otherwise, the function tries to get version of patch which is used by running main processes
     * first.
     *
     * The function returns null if nothing is available.
     *
     * The manager does not guarantee that the patch is valid. The caller should check if the patch
     * is valid manually. If the patch is corrupted, the caller should drop the returned result and
     * use [requestUnavailable] to ask patch processes to clean up this patch.
     */
    @JvmStatic
    @Synchronized
    @NonPatchProcessOnly
    @Throws(Error::class)
    fun acquire(context: Context): TinkerPatch? {
        check(!context.isInPatchProcess) {
            "Cannot acquire patch in patch process"
        }
        versionHolder?.let { (_, lastAcquired) ->
            throw Error(
                Error.Type.HAS_ACQUIRED_PATCH,
                "Cannot acquire patch while current process has already acquired a patch",
                lastAcquired,
            )
        }
        val version = run {
            if (!context.isInMainProcess) {
                val alive = try {
                    context.isMainAlive
                } catch (throwable: Throwable) {
                    throw Error(
                        Error.Type.CHECK_MAIN_ALIVE,
                        "Cannot check if main process is alive",
                        throwable,
                    )
                }
                if (alive) {
                    try {
                        context.mainVersion?.let { return@run it }
                    } catch (throwable: Throwable) {
                        throw Error(
                            Error.Type.READ_MAIN_VERSION,
                            "Cannot read main version",
                            throwable,
                        )
                    }
                }
            }
            val latest = try {
                context.latestVersion ?: return@run null
            } catch (throwable: Throwable) {
                throw Error(
                    Error.Type.READ_LATEST_VERSION,
                    "Cannot read latest version",
                    throwable,
                )
            }
            val unavailable = try {
                context.unavailable
            } catch (throwable: Throwable) {
                throw Error(
                    Error.Type.READ_UNAVAILABLE,
                    "Cannot read unavailable",
                    throwable,
                )
            }
            if (latest in unavailable) {
                return@run null
            }
            return@run latest
        } ?: return null
        val acquired = try {
            context.acquirePatchAsUsing(version) ?: return null
        } catch (throwable: Throwable) {
            throw Error(
                Error.Type.ACQUIRE_PATCH_AS_USING,
                "Cannot acquire patch with version \"${version}\" for using",
                throwable,
            )
        }
        versionHolder = Pair(
            acquired,
            AcquiringRecord(version),
        )
        if (context.isInMainProcess) {
            try {
                context.mainVersion = version
            } catch (throwable: Throwable) {
                releaseVersion()
                throw Error(
                    Error.Type.WRITE_MAIN_VERSION,
                    "Cannot update main version",
                    throwable,
                )
            }
            try {
                context.markMainAlive()
            } catch (throwable: Throwable) {
                releaseVersion()
                unmarkMainAlive()
                throw Error(
                    Error.Type.MARK_MAIN_ALIVE,
                    "Cannot mark main process is alive",
                    throwable,
                )
            }
        }
        return TinkerPatch(
            version,
            context.patchDirectory(version)
        )
    }

    /**
     * Requests manager marks provided patch version as unavailable to clean up this patch, and
     * does not provide this patch anymore.
     *
     * Depends on implementation of manager, the request may be ignored or be used for counting.
     *
     * Once the function is called, the caller should not use anything returned by [acquire] any
     * more.
     */
    @JvmStatic
    @Synchronized
    @NonPatchProcessOnly
    @Throws(Error::class)
    fun requestUnavailable(context: Context, version: String) {
        check(!context.isInPatchProcess) {
            "Cannot request patch as unavailable in patch process"
        }
        if (context.isInMainProcess) {
            unmarkMainAlive()
        }
        releaseVersion()
        try {
            context.updateUnavailable {
                it + version
            }
        } catch (throwable: Throwable) {
            throw Error(
                Error.Type.APPEND_UNAVAILABLE,
                "Cannot append \"$version\" as unavailable record",
                throwable,
            )
        }
        unavailableRequestListeners.forEach { it.run() }
    }

    /**
     * Add listener to be notified when a patch is requested to be unavailable by
     * [requestUnavailable]. It can be used to notify patch processes to clean up patches.
     *
     * Listeners are only invoked in current process while [requestUnavailable] is called. While
     * [requestUnavailable] cannot be invoked in patch process, the function is useless in patch
     * process.
     */
    @JvmStatic
    fun addUnavailableRequestListener(listener: Runnable) {
        unavailableRequestListeners.add(listener)
    }


    /**
     * Creates new patch with provided version and patch directory. The manager copies and stores
     * the patch directory as non-writable to its storage, and marks this version as latest.
     */
    @JvmStatic
    @Synchronized
    @PatchProcessOnly
    @Throws(Error::class)
    fun create(context: Context, version: String, patch: File): TinkerPatch {
        require(!version.startsWith("#")) {
            "Version cannot start with '#'"
        }
        require(patch.exists()) {
            "Provided directory does not exist"
        }
        require(patch.isDirectory) {
            "Provided directory is not a directory"
        }
        check(context.isInPatchProcess) {
            "Only available for patch process"
        }
        val patchDirectory = context.patchDirectory(version)
        if (patchDirectory.exists()) {
            throw Error(
                Error.Type.CREATE_EXIST_PATCH,
                "Patch directory \"${patchDirectory.absolutePath}\" already exists",
                null,
            )
        }
        try {
            patch.createNotWritableCopy(patchDirectory)
        } catch (throwable: Throwable) {
            throw Error(
                Error.Type.CLONE_PATCH,
                "Cannot copy \"${patch.absolutePath}\" to patch directory \"${patchDirectory.absolutePath}\"",
                throwable,
            )
        }
        try {
            patchDirectory.walk(direction = FileWalkDirection.BOTTOM_UP).forEach {
                it.setWritable(false)
            }
        } catch (throwable: Throwable) {
            throw Error(
                Error.Type.DROP_PATCH_WRITE_PERMISSION,
                "Cannot update patch directory \"${patchDirectory.absolutePath}\" as non-writable",
                throwable,
            )
        }
        try {
            context.latestVersion = version
        } catch (throwable: Throwable) {
            throw Error(
                Error.Type.WRITE_LATEST_VERSION,
                "Cannot update latest version to \"${version}\"",
                throwable,
            )
        }
        return TinkerPatch(version, patchDirectory)
    }

    /**
     * Gets the latest version, or null if no patch is available.
     */
    @JvmStatic
    @Synchronized
    @PatchProcessOnly
    @Throws(Error::class)
    fun latestVersion(context: Context): String? {
        check(context.isInPatchProcess) {
            "Only available for patch process"
        }
        return try {
            context.latestVersion
        } catch (throwable: Throwable) {
            throw Error(
                Error.Type.READ_LATEST_VERSION,
                "Cannot read latest version",
                throwable,
            )
        }
    }

    /**
     * Gets patch with specified [version], or null if no patch is available.
     */
    @JvmStatic
    @Synchronized
    @PatchProcessOnly
    fun getByVersion(context: Context, version: String): TinkerPatch? {
        check(context.isInPatchProcess) {
            "Only available for patch process"
        }
        val patchDirectory = context.patchDirectory(version)
        if (!patchDirectory.exists()) {
            return null
        }
        return TinkerPatch(version, patchDirectory)
    }

    /**
     * Cleans all inactive patch directories. Inactive patches are those which are not used by any
     * running process.
     *
     * The function returns list of patch versions are cleaned.
     */
    @JvmStatic
    @Synchronized
    @PatchProcessOnly
    @Throws(Error::class)
    fun cleanAll(context: Context): List<String> {
        check(context.isInPatchProcess) {
            "Only available for patch process"
        }
        try {
            context.latestVersion = null
        } catch (throwable: Throwable) {
            throw Error(
                Error.Type.WRITE_LATEST_VERSION,
                "Cannot remove latest version",
                throwable,
            )
        }
        return context.cleanPatches { false }
    }

    /**
     * Cleans all obsolete patch directories. Different from [cleanAll], latest version is kept,
     * unless latest version is marked as unavailable.
     *
     * The function returns list of patch versions are cleaned.
     */
    @JvmStatic
    @Synchronized
    @PatchProcessOnly
    @Throws(Error::class)
    fun cleanObsolete(context: Context): List<String> {
        check(context.isInPatchProcess) {
            "Only available for patch process"
        }
        // Patches that meets these conditions will be cleaned up:
        //   - inactive, a.k.a not used by any running process
        //   - not marked as latest version, or if marked as latest version, also marked as
        //     unavailable
        val latest = try {
            context.latestVersion
        } catch (throwable: Throwable) {
            throw Error(
                Error.Type.READ_LATEST_VERSION,
                "Cannot read latest version",
                throwable,
            )
        }
        val removeLatest = try {
            context.unavailable.contains(latest)
        } catch (throwable: Throwable) {
            throw Error(
                Error.Type.READ_UNAVAILABLE,
                "Cannot read unavailable records",
                throwable,
            )
        }
        if (removeLatest) {
            try {
                context.latestVersion = null
            } catch (throwable: Throwable) {
                throw Error(
                    Error.Type.WRITE_LATEST_VERSION,
                    "Cannot remove latest version",
                    throwable,
                )
            }
        }
        return context.cleanPatches {
            if (removeLatest) false else (it == latest)
        }
    }
}