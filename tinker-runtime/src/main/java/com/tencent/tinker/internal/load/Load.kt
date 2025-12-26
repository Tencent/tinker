package com.tencent.tinker.internal.load

import com.tencent.tinker.internal.Patch
import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.util.expected

/**
 * Patch loader.
 *
 * The patch loading is separated into two stages:
 *
 * - Create loaders by factories. If any factory raises a throwable, the patch loading will be
 *   skipped. The application is still able to launch.
 * - Load patch by loaders. Any throwable raised by loaders are treated as unexpected errors and
 *   caused the application to crash.
 *
 * Therefore, any recoverable preparation should be done in the factory, instead of loader loading.
 */
internal abstract class Loader {
    /**
     * Load patch.
     */
    abstract fun load()

    abstract class Factory {
        /**
         * Creates a loader by patch, or returns null if unneeded.
         */
        abstract fun createLoaderIfNeeded(patch: Patch): Loader?
    }
}

private object UnexpectedLoadError : TinkerError.Type {
    override val group: TinkerError.TypeGroup
        get() = TinkerError.TypeGroup.LOAD
    override val typeCode: Int
        get() = 0
}

/**
 * Tries to load patch by factories.
 *
 * If any error occurs, the function returns the error. Otherwise, returns null.
 */
internal fun Iterable<Loader.Factory>.tryLoad(patch: Patch): TinkerError? {
    val loaders = try {
        expected("factor loaders for patch \"${patch.version}\"", UnexpectedLoadError) {
            mapNotNull { it.createLoaderIfNeeded(patch) }
        }
    } catch (error: TinkerError) {
        return error
    }
    loaders.forEach { it.load() }
    return null
}