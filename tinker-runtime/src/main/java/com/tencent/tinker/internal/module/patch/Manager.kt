package com.tencent.tinker.internal.module.patch

import android.content.Context
import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import com.tencent.tinker.Tinker
import com.tencent.tinker.internal.annotation.DeployProcessOnly
import com.tencent.tinker.internal.annotation.MainProcessOnly
import com.tencent.tinker.internal.annotation.NonDeployProcessOnly
import com.tencent.tinker.internal.module.patch.RawPatchManagerImpl.Companion.GUARD_CLEANING_CONTENT
import com.tencent.tinker.internal.util.EscapedGuardedContent
import com.tencent.tinker.internal.util.debugLog
import com.tencent.tinker.internal.util.ensureParentIsExistingDirectory
import com.tencent.tinker.internal.util.escapedGuardedContentExclusive
import com.tencent.tinker.internal.util.escapedGuardedContentExclusiveNullable
import com.tencent.tinker.internal.util.escapedGuardedContentSharedNullable
import com.tencent.tinker.internal.util.expected
import com.tencent.tinker.internal.util.guardedContent
import com.tencent.tinker.internal.util.guardedContentNullable
import com.tencent.tinker.internal.util.guardedReadOrWriteContent
import com.tencent.tinker.internal.util.infoLog
import com.tencent.tinker.internal.util.isInDeployProcess
import com.tencent.tinker.internal.util.isInMainProcess
import com.tencent.tinker.internal.util.warnLog
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

private const val TAG = "Tinker.RawPatch"

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

internal class RawPatchManagerImpl(
    private val context: Context,
    private val baseDirectory: File,
) : RawPatchManager() {

    @VisibleForTesting
    fun contextForTesting(): Context = context

    companion object {
        /**
         * A byte may in the guard file to indicate that the patch is being cleaned up.
         */
        private const val GUARD_CLEANING_CONTENT = 1.toByte()
    }

    /**
     * A simple file to record latest patch version.
     *
     * The content of this file may be empty or corrupted because of any I/O error. The caller
     * should check its content is valid before using.
     */
    private val latestVersionFile: File
        get() = baseDirectory.resolve("latest_version")

    @VisibleForTesting
    fun latestVersionFileForTesting(): File =
        latestVersionFile

    @set:DeployProcessOnly
    private var latestVersion: String?
        get() = latestVersionFile
            .takeIf { it.exists() }
            ?.guardedContent
            ?.toString(Charsets.UTF_8)
            ?.takeIf { it.isNotEmpty() }
        set(value) {
            debugLog(TAG) {
                val valueReadable = if (value != null) "\"${value}\"" else "<null>"
                "Try setting latest version to ${valueReadable}."
            }
            value?.let {
                latestVersionFile
                    .ensureParentIsExistingDirectory()
                    .guardedContent = it.toByteArray(Charsets.UTF_8)
            } ?: latestVersionFile.delete()
        }


    /**
     * Guard lock is a mechanism based on file lock to protect used patch files not be deleted by
     * cleaner.
     *
     * Each patch version has its independent guard lock file. When a process is trying to apply a
     * patch version, it has to acquire a use guard (based on shared lock) of the guard lock file
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
    private fun guardFile(version: String): File {
        return baseDirectory
            .resolve("guards")
            .resolve(version)
    }

    /**
     * Acquires patch of [version] is used by current process. The function returns null if patch
     * process is holding patch to clean up.
     */
    private fun acquireGuardAsUsing(version: String): EscapedGuardedContent? {
        val file = guardFile(version)
            .apply {
                if (!exists()) {
                    ensureParentIsExistingDirectory()
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
     * Acquires patch of [version] is prepared to be cleaned by deploy process. The function returns
     * null if patch is now used by any process.
     */
    @DeployProcessOnly
    private fun acquireGuardAsCleaning(version: String): EscapedGuardedContent? {
        val file = guardFile(version).ensureParentIsExistingDirectory()
        return file.escapedGuardedContentExclusiveNullable(
            ByteArray(1) { GUARD_CLEANING_CONTENT }
        )
    }

    /**
     * Drop guard file after patch of [version] is cleaned.
     */
    @DeployProcessOnly
    private fun dropGuard(version: String) {
        guardFile(version).delete()
    }


    /**
     * A simple file to record latest patch version.
     *
     * Its content is only available when exclusive lock of [mainAliveFile] is held. Which means
     * the main process is still alive.
     */
    private val mainVersionFile: File
        get() = baseDirectory.resolve("main_version")

    /**
     * A simple file to indicate whether main process is alive.
     *
     * The main process should hold an exclusive lock of this file to represent that it is alive.
     */
    private val mainAliveFile: File
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
    private var mainVersion: String?
        get() = mainVersionFile.takeIf { it.exists() }
            ?.guardedContentNullable
            ?.toString(Charsets.UTF_8)
        set(value) {
            debugLog(TAG) {
                val valueReadable = if (value != null) "\"${value}\"" else "<null>"
                "Try setting latest version to ${valueReadable}."
            }
            value?.let {
                mainVersionFile
                    .ensureParentIsExistingDirectory()
                    .guardedContent = it.toByteArray(Charsets.UTF_8)
            } ?: mainVersionFile.delete()
        }

    @GuardedBy("this")
    private var mainAliveHolder: EscapedGuardedContent? = null

    @MainProcessOnly
    @GuardedBy("this")
    private fun markMainAlive() {
        val file = mainAliveFile.ensureParentIsExistingDirectory()
        file.escapedGuardedContentExclusive(ByteArray(0))
            .also {
                check(mainAliveHolder == null) {
                    "Main process is already marked as alive"
                }
                mainAliveHolder = it
                debugLog(TAG) {
                    "Main process is marked as alive."
                }
            }
    }

    /**
     * The function does nothing if current process is not main process.
     */
    @GuardedBy("this")
    private fun unmarkMainAlive() {
        mainAliveHolder?.close()
        mainAliveHolder = null
        debugLog(TAG) {
            "Main process is unmarked as alive."
        }
    }

    /**
     * Checks if main process is alive in non-main processes.
     */
    private val isMainAlive: Boolean
        get() = mainAliveFile.takeIf { it.exists() }?.guardedContentNullable == null


    /**
     * A simple file to record unavailable patch versions.
     *
     * Each line in the file is a patch version. Once a patch version is marked as unavailable by
     * non-deploy processes using [requestUnavailable], it will be added to this file.
     *
     * While deploy processes are cleaning up obsolete patch versions, non-remained patch versions
     * will be removed from this file.
     */
    private val unavailableFile: File
        get() = baseDirectory.resolve("unavailable")

    private val unavailable: Set<String>
        get() = unavailableFile.takeIf { it.exists() }
            ?.guardedContent
            ?.toString(Charsets.UTF_8)
            ?.lines()
            ?.toSet()
            ?: emptySet()

    private fun updateUnavailable(action: (Set<String>) -> Set<String>) {
        unavailableFile.guardedReadOrWriteContent { file ->
            file.seek(0)
            val content = ByteArray(file.length().toInt())
                .also(file::read)
            val original = content
                .toString(Charsets.UTF_8)
                .lines()
                .filter { it.isNotBlank() }
                .toSet()
            debugLog(TAG) {
                buildList {
                    add("Unavailable list before updating:")
                    original.takeIf { it.isNotEmpty() }
                        ?.forEach {
                            add("  $it")
                        }
                        ?: add("  (empty)")
                }.joinToString("\n")
            }
            val updated = action(original)
            file.seek(0)
            file.setLength(0)
            val sorted = updated.sorted()
            sorted
                .joinToString("\n")
                .toByteArray(Charsets.UTF_8)
                .let(file::write)
            debugLog(TAG) {
                buildList {
                    add("Unavailable list after updated:")
                    sorted.takeIf { it.isNotEmpty() }
                        ?.forEach {
                            add("  $it")
                        }
                        ?: add("  (empty)")
                }.joinToString("\n")
            }
        }
    }

    private val unavailableRequestListeners = ConcurrentLinkedQueue<Runnable>()


    /**
     * A container used to hold guarded content of patch guard file. Unless release manually,
     * guarded content will never be released until process dead. Which means lifecycles of guarded
     * content are same as process lifecycle.
     */
    @GuardedBy("this")
    @NonDeployProcessOnly
    private var versionHolder: Pair<EscapedGuardedContent, AcquiringRecord>? = null

    private class AcquiringRecord(version: String) :
        RuntimeException("Last acquiring with version \"${version}\" is invoked with following stacktrace") {
        init {
            stackTrace = stackTrace
                .dropWhile {
                    it.className == this.javaClass.name
                }
                .toTypedArray()
        }
    }

    @NonDeployProcessOnly
    private fun releaseVersion() {
        debugLog(TAG) {
            val versionHolderReadable = versionHolder
                ?.hashCode()
                ?.toString(16)
                ?.let { "\"${it}\"" }
                ?: "<null>"
            "Releasing version holder $versionHolderReadable of current process."
        }
        versionHolder?.let { (guardedContent, _) ->
            guardedContent.close()
        }
        versionHolder = null
    }

    @VisibleForTesting
    @Synchronized
    @NonDeployProcessOnly
    fun releaseAllHoldersForTesting() {
        unmarkMainAlive()
        releaseVersion()
    }


    /**
     * Each patch version has its own independent directory to store patch files.
     *
     * The patch directories are maintained by deploy processes, and will be marked as non-writable
     * after it is created and until it is cleaned up. Other processes can only read or execute
     * files in these directories.
     */
    private val patchesDirectory: File
        get() = baseDirectory.resolve("patches")

    private fun patchDirectory(version: String): File =
        patchesDirectory.resolve(version)

    @VisibleForTesting
    fun patchDirectoryForTesting(version: String): File =
        patchDirectory(version)

    @DeployProcessOnly
    private fun cleanPatches(keep: (String) -> Boolean): List<CleanedRawPatch> {
        val patchDirectories = patchesDirectory
            .takeIf { it.exists() }
            ?.listFiles()
            ?.filter { it.isDirectory }
            ?: return emptyList()
        debugLog(TAG) {
            buildList {
                add("Found raw patch directories prepare for cleaning:")
                patchDirectories.forEach {
                    add("  ${it.absolutePath}")
                }
            }.joinToString("\n")
        }
        // Pairs of version and cleanable raw patch directory.
        val pairs = patchDirectories.mapNotNull { dir ->
            val version = dir.name
            if (keep(version)) {
                debugLog(TAG) {
                    "Skip cleaning raw patch directory \"${dir.absolutePath}\" as required."
                }
                return@mapNotNull Pair(version, null)
            }
            val guardedContent = try {
                acquireGuardAsCleaning(version)
            } catch (throwable: Throwable) {
                throw Tinker.Error(
                    Tinker.Error.RawPatch.ACQUIRE_PATCH_AS_CLEANING,
                    "Cannot acquire patch \"${version}\" as clean",
                    throwable,
                )
            }
            if (guardedContent != null) {
                guardedContent.use {
                    try {
                        dir.walk(direction = FileWalkDirection.TOP_DOWN).forEach {
                            it.setWritable(true)
                        }
                    } catch (throwable: Throwable) {
                        throw Tinker.Error(
                            Tinker.Error.RawPatch.RECOVER_PATCH_WRITE_PERMISSION,
                            "Cannot recover patch directory \"${dir.absolutePath}\" as writable",
                            throwable,
                        )
                    }
                    try {
                        dir.deleteRecursively()
                    } catch (throwable: Throwable) {
                        throw Tinker.Error(
                            Tinker.Error.RawPatch.CLEAN_PATCH,
                            "Cannot clean patch directory \"${dir.absolutePath}\"",
                            throwable,
                        )
                    }
                }
                debugLog(TAG) {
                    "Raw patch directory \"${dir.absolutePath}\" is cleaned."
                }
                dropGuard(version)
                debugLog(TAG) {
                    "Guard of \"${version}\" is dropped."
                }
                return@mapNotNull Pair(version, dir)
            } else {
                warnLog(TAG) {
                    "Skip cleaning raw patch directory \"${dir.absolutePath}\" because it is using."
                }
                return@mapNotNull Pair(version, null)
            }
        }
        // Remove unavailable records except those that are still remaining.
        pairs.filter { it.second == null }
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
                    throw Tinker.Error(
                        Tinker.Error.RawPatch.CLEAN_UNAVAILABLE,
                        "Cannot clean unavailable records",
                        throwable,
                    )
                }
            }
        return pairs.mapNotNull {
            val dir = it.second ?: return@mapNotNull null
            val version = it.first
            return@mapNotNull CleanedRawPatch(version, dir)
        }
    }

    @Synchronized
    @NonDeployProcessOnly
    override fun acquire(): RawPatch? {
        check(!context.isInDeployProcess) {
            "Cannot acquire patch in deploy process"
        }
        expected<Tinker.Error.RawPatch>("acquire patch") {
            versionHolder?.let { (_, lastAcquired) ->
                throw Tinker.Error(
                    Tinker.Error.RawPatch.HAS_ACQUIRED_PATCH,
                    "Cannot acquire patch while current process has already acquired a patch",
                    lastAcquired,
                )
            }
            val version = run {
                if (!context.isInMainProcess) {
                    val alive = try {
                        isMainAlive
                    } catch (throwable: Throwable) {
                        throw Tinker.Error(
                            Tinker.Error.RawPatch.CHECK_MAIN_ALIVE,
                            "Cannot check if main process is alive",
                            throwable,
                        )
                    }
                    if (alive) {
                        try {
                            mainVersion?.let {
                                debugLog(TAG) {
                                    "Use \"${it}\" which is used by running main process."
                                }
                                return@run it
                            }
                        } catch (throwable: Throwable) {
                            throw Tinker.Error(
                                Tinker.Error.RawPatch.READ_MAIN_VERSION,
                                "Cannot read main version",
                                throwable,
                            )
                        }
                    }
                }
                val latest = try {
                    latestVersion ?: return@run null
                } catch (throwable: Throwable) {
                    throw Tinker.Error(
                        Tinker.Error.RawPatch.READ_LATEST_VERSION,
                        "Cannot read latest version",
                        throwable,
                    )
                }
                debugLog(TAG) {
                    "Found latest version \"${latest}\" for acquiring."
                }
                val unavailable = try {
                    unavailable
                } catch (throwable: Throwable) {
                    throw Tinker.Error(
                        Tinker.Error.RawPatch.READ_UNAVAILABLE,
                        "Cannot read unavailable",
                        throwable,
                    )
                }
                debugLog(TAG) {
                    buildList {
                        add("Current unavailable versions:")
                        unavailable.forEach {
                            add("  $it")
                        }
                    }.joinToString("\n")
                }
                if (latest in unavailable) {
                    debugLog(TAG) {
                        "Acquired nothing since latest version \"${latest}\" is unavailable."
                    }
                    return@run null
                }
                return@run latest
            } ?: return null
            val acquired = try {
                acquireGuardAsUsing(version) ?: run {
                    warnLog(TAG) {
                        "Acquired failed because raw patch directory of target version \"${version}\" is cleaning."
                    }
                    return null
                }
            } catch (throwable: Throwable) {
                throw Tinker.Error(
                    Tinker.Error.RawPatch.ACQUIRE_PATCH_AS_USING,
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
                    mainVersion = version
                } catch (throwable: Throwable) {
                    releaseVersion()
                    throw Tinker.Error(
                        Tinker.Error.RawPatch.WRITE_MAIN_VERSION,
                        "Cannot update main version",
                        throwable,
                    )
                }
                try {
                    markMainAlive()
                } catch (throwable: Throwable) {
                    releaseVersion()
                    unmarkMainAlive()
                    throw Tinker.Error(
                        Tinker.Error.RawPatch.MARK_MAIN_ALIVE,
                        "Cannot mark main process is alive",
                        throwable,
                    )
                }
            }
            return RawPatch(
                version,
                patchDirectory(version)
            )
        }
    }

    @Synchronized
    @NonDeployProcessOnly
    override fun requestUnavailable(version: String) {
        check(!context.isInDeployProcess) {
            "Cannot request patch as unavailable in deploy process"
        }
        expected<Tinker.Error.RawPatch>("request patch as unavailable") {
            if (context.isInMainProcess) {
                unmarkMainAlive()
            }
            releaseVersion()
            try {
                updateUnavailable {
                    it + version
                }
            } catch (throwable: Throwable) {
                throw Tinker.Error(
                    Tinker.Error.RawPatch.APPEND_UNAVAILABLE,
                    "Cannot append \"$version\" as unavailable record",
                    throwable,
                )
            }
            unavailableRequestListeners.forEach { it.run() }
        }
    }

    @NonDeployProcessOnly
    override fun addUnavailableRequestListener(listener: Runnable) {
        unavailableRequestListeners.add(listener)
    }

    @Synchronized
    @DeployProcessOnly
    override fun create(version: String, patch: File): RawPatch {
        require(!version.startsWith("#")) {
            "Version cannot start with '#'"
        }
        require(patch.exists()) {
            "Provided directory does not exist"
        }
        require(patch.isDirectory) {
            "Provided directory is not a directory"
        }
        check(context.isInDeployProcess) {
            "Only available for deploy process"
        }
        expected<Tinker.Error.RawPatch>("create patch") {
            val patchDirectory = patchDirectory(version)
            if (patchDirectory.exists()) {
                throw Tinker.Error(
                    Tinker.Error.RawPatch.CREATE_EXIST_PATCH,
                    "Patch directory \"${patchDirectory.absolutePath}\" already exists",
                    null,
                )
            }
            debugLog(TAG) {
                "Raw patch directory \"${patchDirectory.absolutePath}\" is created for version \"${version}\"."
            }
            try {
                patch.createNotWritableCopy(patchDirectory)
            } catch (throwable: Throwable) {
                throw Tinker.Error(
                    Tinker.Error.RawPatch.CLONE_PATCH,
                    "Cannot copy \"${patch.absolutePath}\" to patch directory \"${patchDirectory.absolutePath}\"",
                    throwable,
                )
            }
            try {
                latestVersion = version
            } catch (throwable: Throwable) {
                throw Tinker.Error(
                    Tinker.Error.RawPatch.WRITE_LATEST_VERSION,
                    "Cannot update latest version to \"${version}\"",
                    throwable,
                )
            }
            return RawPatch(version, patchDirectory)
        }
    }

    @Synchronized
    @DeployProcessOnly
    override fun latestVersion(): String? {
        check(context.isInDeployProcess) {
            "Only available for deploy process"
        }
        expected<Tinker.Error.RawPatch>("get latest version") {
            return try {
                latestVersion
            } catch (throwable: Throwable) {
                throw Tinker.Error(
                    Tinker.Error.RawPatch.READ_LATEST_VERSION,
                    "Cannot read latest version",
                    throwable,
                )
            }
        }
    }

    @Synchronized
    @DeployProcessOnly
    override fun getByVersion(version: String): RawPatch? {
        check(context.isInDeployProcess) {
            "Only available for deploy process"
        }
        expected<Tinker.Error.RawPatch>("get patch by version") {
            val patchDirectory = patchDirectory(version)
            if (!patchDirectory.exists()) {
                return null
            }
            return RawPatch(version, patchDirectory)
        }
    }

    @Synchronized
    @DeployProcessOnly
    override fun cleanAll(): List<CleanedRawPatch> {
        check(context.isInDeployProcess) {
            "Only available for deploy process"
        }
        expected<Tinker.Error.RawPatch>("clean all patches") {
            try {
                latestVersion = null
            } catch (throwable: Throwable) {
                throw Tinker.Error(
                    Tinker.Error.RawPatch.WRITE_LATEST_VERSION,
                    "Cannot remove latest version",
                    throwable,
                )
            }
            return cleanPatches { false }
        }
    }

    @Synchronized
    @DeployProcessOnly
    override fun cleanObsolete(): List<CleanedRawPatch> {
        check(context.isInDeployProcess) {
            "Only available for deploy process"
        }
        expected<Tinker.Error.RawPatch>("clean obsolete patches") {
            // Patches that meets these conditions will be cleaned up:
            //   - inactive, a.k.a not used by any running process
            //   - not marked as latest version, or if marked as latest version, also marked as
            //     unavailable
            val latest = try {
                latestVersion
            } catch (throwable: Throwable) {
                throw Tinker.Error(
                    Tinker.Error.RawPatch.READ_LATEST_VERSION,
                    "Cannot read latest version",
                    throwable,
                )
            }
            val removeLatest = try {
                unavailable.contains(latest)
            } catch (throwable: Throwable) {
                throw Tinker.Error(
                    Tinker.Error.RawPatch.READ_UNAVAILABLE,
                    "Cannot read unavailable records",
                    throwable,
                )
            }
            if (removeLatest) {
                infoLog(TAG) {
                    "Latest version \"${latest}\" is cleanable because it is already marked as unavailable."
                }
                try {
                    latestVersion = null
                } catch (throwable: Throwable) {
                    throw Tinker.Error(
                        Tinker.Error.RawPatch.WRITE_LATEST_VERSION,
                        "Cannot remove latest version",
                        throwable,
                    )
                }
            }
            return cleanPatches {
                if (removeLatest) false else (it == latest)
            }
        }
    }
}