package com.tencent.tinker.internal.annotation

/**
 * Functions with this annotation are only available for main process.
 */
@Retention(AnnotationRetention.SOURCE)
annotation class MainProcessOnly

/**
 * Functions with this annotation are only available for deploy process.
 */
@Retention(AnnotationRetention.SOURCE)
annotation class DeployProcessOnly

/**
 * Functions with this annotation are only available for non-deploy process.
 */
@Retention(AnnotationRetention.SOURCE)
annotation class NonDeployProcessOnly