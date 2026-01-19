package com.tencent.tinker.internal.module.oat

import android.content.Context
import com.tencent.tinker.internal.annotation.DeployProcessOnly
import com.tencent.tinker.internal.annotation.NonDeployProcessOnly
import com.tencent.tinker.internal.util.SynchronizedCache
import java.io.File

/**
 * Manager for OAT files.
 */
internal abstract class OatManager {

    companion object {
        private val implCache = SynchronizedCache<OatManager>()

        /**
         * Gets OAT manager instance by context.
         *
         * Noticed that provided context may be referenced by manager implementation, so use application context is
         * recommended.
         */
        fun with(applicationContext: Context): OatManager =
            implCache.getOrPut { OatManagerImpl(applicationContext) }
    }

    /**
     * Acquires OAT files for available inputs (like dex files or apk files) in [directory].
     *
     * If set [skipGenerateIfMissing] as true, the function skips generating even if OAT files are not
     * available.
     */
    @NonDeployProcessOnly
    abstract fun acquire(
        directory: File,
        skipGenerateIfMissing: Boolean = false
    ): File?

    /**
     * Releases OAT files using lock to mark OAT files is not used any more in current process.
     */
    @NonDeployProcessOnly
    abstract fun release()

    /**
     * Generates OAT files for available inputs (like dex files or apk files) in [directory] if OAT
     * files are not available.
     */
    @DeployProcessOnly
    abstract fun generateIfNeeded(directory: File, async: Boolean = false)

    /**
     * Cleans OAT files acquired by [directory].
     */
    @DeployProcessOnly
    abstract fun clean(directory: File): Boolean
}