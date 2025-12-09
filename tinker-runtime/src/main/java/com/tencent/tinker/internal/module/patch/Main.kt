package com.tencent.tinker.internal.module.patch

import android.content.Context
import com.tencent.tinker.internal.annotation.NonPatchProcessOnly
import com.tencent.tinker.internal.annotation.PatchProcessOnly

import java.io.File

/**
 * Information about a raw patch managed by [RawPatchManager].
 *
 * The raw patch is not the actual patch to be used on loading, it just represents information of
 * a patch and its content, which are immutable.
 *
 * Attention: all content should be treated as unavailable if current process already requested it
 * as unavailable by using [RawPatchManager.requestUnavailable].
 */
internal class RawPatch(
    /**
     * Version of this patch.
     */
    val version: String,

    /**
     * Base directory of this patch. This directory and all its children are non-writable.
     *
     * The directory may be cleaned if current process already requested patch as unavailable by
     * using [RawPatchManager.requestUnavailable].
     */
    val directory: File,
)

/**
 * Manager for raw patches.
 */
internal abstract class RawPatchManager {

    companion object {
        private val implCache = arrayOfNulls<RawPatchManager>(1)

        fun with(context: Context): RawPatchManager {
            implCache[0]?.let { return it }
            synchronized(implCache) {
                implCache[0]?.let { return it }
                return RawPatchManagerImpl(context.applicationContext)
                    .also { implCache[0] = it }
            }
        }
    }

    /**
     * An error raised by manager.
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
    @NonPatchProcessOnly
    @Throws(Error::class)
    abstract fun acquire(): RawPatch?

    /**
     * Requests manager marks provided patch version as unavailable to clean up this patch, and
     * does not provide this patch anymore.
     *
     * Depends on implementation of manager, the request may be ignored or be used for counting.
     *
     * Once the function is called, the caller should not use anything returned by [acquire] any
     * more.
     */
    @NonPatchProcessOnly
    @Throws(Error::class)
    abstract fun requestUnavailable(version: String)

    /**
     * Add listener to be notified when a patch is requested to be unavailable by
     * [requestUnavailable]. It can be used to notify patch processes to clean up patches.
     *
     * Listeners are only invoked in current process while [requestUnavailable] is called. While
     * [requestUnavailable] cannot be invoked in patch process, the function is useless in patch
     * process.
     */
    @NonPatchProcessOnly
    abstract fun addUnavailableRequestListener(listener: Runnable)

    /**
     * Creates new patch with provided version and patch directory. The manager copies and stores
     * the patch directory as non-writable to its storage, and marks this version as latest.
     */
    @PatchProcessOnly
    @Throws(Error::class)
    abstract fun create(version: String, patch: File): RawPatch

    /**
     * Gets the latest version, or null if no patch is available.
     */
    @PatchProcessOnly
    @Throws(Error::class)
    abstract fun latestVersion(): String?

    /**
     * Gets patch with specified [version], or null if no patch is available.
     */
    @PatchProcessOnly
    abstract fun getByVersion(version: String): RawPatch?

    /**
     * Cleans all inactive patch directories. Inactive patches are those which are not used by any
     * running process.
     *
     * The function returns list of patch versions are cleaned.
     */
    @PatchProcessOnly
    @Throws(Error::class)
    abstract fun cleanAll(): List<String>

    /**
     * Cleans all obsolete patch directories. Different from [cleanAll], latest version is kept,
     * unless latest version is marked as unavailable.
     *
     * The function returns list of patch versions are cleaned.
     */
    @PatchProcessOnly
    @Throws(Error::class)
    abstract fun cleanObsolete(): List<String>
}