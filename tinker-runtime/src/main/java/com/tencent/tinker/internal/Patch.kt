package com.tencent.tinker.internal

import java.io.File

/**
 * Information about a patch.
 *
 * Attention: all content should be treated as unavailable if current process already requested it
 * as unavailable.
 */
internal class TinkerPatch(
    /**
     * Version of this patch.
     */
    val version: String,

    /**
     * Base directory of this patch. This directory and all its children are non-writable.
     *
     * The directory may be cleaned if current process already requested patch as unavailable.
     */
    val directory: File,
)