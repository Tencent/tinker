package com.tencent.tinker.internal.module.oat

import android.content.Context
import com.tencent.tinker.internal.annotation.NonPatchProcessOnly
import com.tencent.tinker.internal.annotation.PatchProcessOnly
import java.io.File

/**
 * Manager for OAT files.
 */
internal abstract class OatManager {

    companion object {
        private val implCache = arrayOfNulls<OatManager>(1)

        fun with(context: Context): OatManager {
            implCache[0]?.let { return it }
            synchronized(implCache) {
                implCache[0]?.let { return it }
                return OatManagerImpl(context.applicationContext)
                    .also { implCache[0] = it }
            }
        }
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
            HAS_ACQUIRED_OAT(-200),
            GENERATE_OR_STORE_FAILED(-210)
        }
    }

    /**
     * Acquires OAT files for available inputs (like dex files or apk files) in [directory].
     *
     * If set [skipGenerateIfMissing] as true, the function skips generating even if OAT files are not
     * available.
     */
    @NonPatchProcessOnly
    @Throws(Error::class)
    abstract fun acquire(
        directory: File,
        skipGenerateIfMissing: Boolean = false
    ): File?

    /**
     * Releases OAT files using lock to mark OAT files is not used any more in current process.
     */
    @NonPatchProcessOnly
    abstract fun release()

    /**
     * Generates OAT files for available inputs (like dex files or apk files) in [directory] if OAT
     * files are not available.
     */
    @PatchProcessOnly
    @Throws(Error::class)
    abstract fun generateIfNeeded(directory: File, async: Boolean = false)

    /**
     * Cleans OAT files acquired by [directory].
     */
    @PatchProcessOnly
    @Throws(Error::class)
    abstract fun clean(directory: File): Boolean
}