package com.tencent.tinker.internal.annotation

/**
 * Functions with this annotation are only available for main process.
 */
@Retention(AnnotationRetention.SOURCE)
annotation class MainProcessOnly

/**
 * Functions with this annotation are only available for patch process.
 */
@Retention(AnnotationRetention.SOURCE)
annotation class PatchProcessOnly

/**
 * Functions with this annotation are only available for non-patch process.
 */
@Retention(AnnotationRetention.SOURCE)
annotation class NonPatchProcessOnly