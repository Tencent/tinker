package com.tencent.tinker.internal.load

import android.app.Application
import android.os.Build
import androidx.annotation.VisibleForTesting
import com.tencent.tinker.Tinker
import com.tencent.tinker.internal.Patch
import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.annotation.NonPatchProcessOnly
import com.tencent.tinker.internal.load.code.InjectPathCodeLoader
import com.tencent.tinker.internal.load.code.V24NonHardeningCodeLoader
import com.tencent.tinker.internal.load.code.V27NonHardeningCodeLoader
import com.tencent.tinker.internal.load.code.V31NonHardeningCodeLoader
import com.tencent.tinker.internal.load.resource.ResourceLoader
import com.tencent.tinker.internal.module.layout.PatchLayoutConstructor
import com.tencent.tinker.internal.module.oat.OatManager
import com.tencent.tinker.internal.module.patch.RawPatch
import com.tencent.tinker.internal.module.patch.RawPatchManager
import com.tencent.tinker.internal.module.validate.Validator
import com.tencent.tinker.internal.module.validate.ValidatorImpl
import com.tencent.tinker.internal.util.errorCode
import com.tencent.tinker.internal.util.expected
import com.tencent.tinker.internal.util.isInPatchProcess

private enum class LoadErrorType : TinkerError.Type {
    UNEXPECTED,
    UNRECOVERABLE_LOAD_FAILED;

    override val group: TinkerError.TypeGroup
        get() = TinkerError.TypeGroup.LOAD

    override val typeCode: Int
        get() = ordinal
}

@VisibleForTesting
internal fun loadErrorTypeOfForTesting(type: String): TinkerError.Type =
    LoadErrorType.valueOf(type)

/**
 * Patch loader used to load patch in runtime.
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

/**
 * Tries to load patch by factories.
 */
@NonPatchProcessOnly
private fun Iterable<Loader.Factory>.tryLoad(patch: Patch) {
    val loaders = expected<LoadErrorType, List<Loader>>("create loaders") {
        mapNotNull { it.createLoaderIfNeeded(patch) }
    }
    try {
        loaders.forEach { it.load() }
    } catch (throwable: Throwable) {
        throw TinkerError(
            LoadErrorType.UNRECOVERABLE_LOAD_FAILED,
            "Cannot load patch \"${patch.version}\", and it is unrecoverable.",
            throwable,
        )
    }
}

@VisibleForTesting
@NonDeployProcessOnly
internal fun Iterable<Loader.Factory>.tryLoadForTesting(patch: Patch) =
    tryLoad(patch)

@NonPatchProcessOnly
private fun Application.loadWith(
    hardening: Boolean,
    patch: Patch,
): ClassLoader? {
    val classLoaderReference = arrayOfNulls<ClassLoader>(1)
    val loaders = buildList {
        if (!hardening && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            V31NonHardeningCodeLoader.Factory(
                reference = classLoaderReference,
                application = this@loadWith
            ).let(::add)
        } else if (!hardening && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            V27NonHardeningCodeLoader.Factory(
                reference = classLoaderReference,
                application = this@loadWith
            ).let(::add)
        } else if (!hardening && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            V24NonHardeningCodeLoader.Factory(
                reference = classLoaderReference,
                application = this@loadWith,
                outputDirectory = patch.oatDirectory,
            ).let(::add)
        } else {
            InjectPathCodeLoader.Factory(
                application = this@loadWith,
                outputDirectory = patch.oatDirectory,
            ).let(::add)
        }
        ResourceLoader.Factory(
            applicationContext = this@loadWith,
        ).let(::add)
    }
    loaders.tryLoad(patch)
    return classLoaderReference[0]
}

@NonPatchProcessOnly
private fun Application.loadWith(
    hardening: Boolean,
    rawPatch: RawPatch,
    validator: Validator = ValidatorImpl,
    oatManager: OatManager = OatManager.with(this),
    patchLayoutConstructor: PatchLayoutConstructor = PatchLayoutConstructor.with(this),
): ClassLoader? {
    validator.validate(rawPatch.directory)
    val oatDirectory = oatManager.acquire(rawPatch.directory)
    val patchDirectory = patchLayoutConstructor.construct(
        baseDirectory = rawPatch.directory,
        oatDirectory = oatDirectory,
    )
    val patch = Patch(rawPatch.version, patchDirectory)
    return loadWith(
        hardening = hardening,
        patch = patch,
    )
}

@NonPatchProcessOnly
private fun Application.loadInternal(
    hardening: Boolean,
    rawPatchManager: RawPatchManager = RawPatchManager.with(this),
): ClassLoader? {
    val rawPatch = rawPatchManager.acquire() ?: return null
    try {
        return loadWith(
            hardening = hardening,
            rawPatch = rawPatch,
        )
    } catch (throwable: Throwable) {
        rawPatchManager.requestUnavailable(rawPatch.version)
        throw throwable
    }
}

/**
 * Loads patch.
 *
 * If any patch is loaded successfully, a class loader which can load patch classes will be returned. Otherwise, the
 * function returns null.
 */
@NonPatchProcessOnly
internal fun Application.load(
    hardening: Boolean,
    callback: Tinker.Callback?,
): ClassLoader? {
    val (classLoader, error) = try {
        val classLoader = expected<LoadErrorType, ClassLoader?>("load patch") {
            loadInternal(hardening)
        }
        classLoader to null
    } catch (error: TinkerError) {
        if (error.type == LoadErrorType.UNRECOVERABLE_LOAD_FAILED) {
            throw error
        }
        null to Tinker.Error(
            code = error.type.errorCode,
            message = error.message,
            reason = error,
        )
    }
    callback?.onTaskComplete(error)
    return classLoader
}