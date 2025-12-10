package com.tencent.tinker.internal.module.layout

import android.content.Context
import com.tencent.tinker.internal.annotation.NonPatchProcessOnly
import com.tencent.tinker.internal.util.SynchronizedCache
import java.io.File

/**
 * Constructor used to construct patch directory with specified layout.
 *
 * The constructor is used to combine non-writable files or directories, like directories from raw
 * patches, and files or directories should be writable, like OAT directories, as a new directory
 * based on symbolic linking, which file structure is conform to patch loading, but avoiding
 * breaking file structure of non-writable sources.
 *
 * Directories constructed by constructor are independent by each process. When the process exits,
 * the constructed directories become invalid and will be cleaned up.
 */
internal abstract class PatchLayoutConstructor {

    companion object {
        private val implCache = SynchronizedCache<PatchLayoutConstructor>()

        fun with(context: Context): PatchLayoutConstructor =
            implCache.getOrPut { PatchLayoutConstructorImpl(context.applicationContext) }
    }

    /**
     * Constructs a new directory based on [baseDirectory] from raw patch manager and [oatDirectory]
     * from OAT manager.
     */
    @NonPatchProcessOnly
    abstract fun construct(
        baseDirectory: File,
        oatDirectory: File,
    ): File
}