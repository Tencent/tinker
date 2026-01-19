package com.tencent.tinker.internal.load

import android.app.Application
import android.os.Build
import androidx.annotation.VisibleForTesting
import com.tencent.tinker.Tinker
import com.tencent.tinker.internal.Patch
import com.tencent.tinker.internal.annotation.NonDeployProcessOnly
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
import com.tencent.tinker.internal.util.currentProcess
import com.tencent.tinker.internal.util.debugLog
import com.tencent.tinker.internal.util.expected
import com.tencent.tinker.internal.util.infoLog

private const val TAG = "Tinker.Load"

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
@NonDeployProcessOnly
private fun Iterable<Loader.Factory>.tryLoad(patch: Patch) {
    val loaders = expected<Tinker.Error.Load, List<Loader>>("create loaders") {
        mapNotNull { it.createLoaderIfNeeded(patch) }
    }
    try {
        loaders.forEach { it.load() }
    } catch (throwable: Throwable) {
        throw Tinker.Error(
            Tinker.Error.Load.UNRECOVERABLE_LOAD_FAILED,
            "Cannot load patch \"${patch.version}\", and it is unrecoverable.",
            throwable,
        )
    }
}

@VisibleForTesting
@NonDeployProcessOnly
internal fun Iterable<Loader.Factory>.tryLoadForTesting(patch: Patch) =
    tryLoad(patch)

@NonDeployProcessOnly
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
    debugLog(TAG) {
        buildList {
            add("Load with loaders:")
            loaders.forEach {
                add("  ${it.javaClass.name}")
            }
        }.joinToString("\n")
    }
    loaders.tryLoad(patch)
    return classLoaderReference[0]
}

@NonDeployProcessOnly
private fun Application.loadWith(
    hardening: Boolean,
    rawPatch: RawPatch,
    validator: Validator = ValidatorImpl,
    oatManager: OatManager = OatManager.with(this),
    patchLayoutConstructor: PatchLayoutConstructor = PatchLayoutConstructor.with(this),
): ClassLoader? {
    debugLog(TAG) {
        "Validating \"${rawPatch.directory.absolutePath}\" with validator <${validator.javaClass.name}>."
    }
    validator.validate(rawPatch.directory)
    debugLog(TAG) {
        "Acquiring OAT for \"${rawPatch.directory.absolutePath}\"" +
                " with manager <${oatManager.javaClass.name}>."
    }
    val oatDirectory = oatManager.acquire(rawPatch.directory)
    debugLog(TAG) {
        "Constructing layout with constructor <${patchLayoutConstructor.javaClass.name}>."
    }
    val patchDirectory = patchLayoutConstructor.construct(
        baseDirectory = rawPatch.directory,
        oatDirectory = oatDirectory,
    )
    debugLog(TAG) {
        "Patch directory is \"${patchDirectory.absolutePath}\"."
    }
    val patch = Patch(rawPatch.version, patchDirectory)
    return loadWith(
        hardening = hardening,
        patch = patch,
    )
}

@NonDeployProcessOnly
private fun Application.loadInternal(
    hardening: Boolean,
    rawPatchManager: RawPatchManager = RawPatchManager.with(this),
): ClassLoader? {
    val rawPatch = rawPatchManager.acquire() ?: run {
        infoLog(TAG) {
            "No loadable patch found, skip loading."
        }
        return null
    }
    infoLog(TAG) {
        "Raw patch \"${rawPatch.version}\" is acquired, try loading."
    }
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
@NonDeployProcessOnly
internal fun Application.load(
    hardening: Boolean,
    callback: Tinker.Callback?,
): ClassLoader? {
    infoLog(TAG) {
        "Try loading patch in process \"${currentProcess}\"."
    }
    val (classLoader, error) = try {
        val classLoader = expected<Tinker.Error.Load, ClassLoader?>("load patch") {
            loadInternal(hardening)
        }
        classLoader to null
    } catch (error: Tinker.Error) {
        if (error.type == Tinker.Error.Load.UNRECOVERABLE_LOAD_FAILED) {
            throw error
        }
        null to error
    }
    callback?.onTaskComplete(error)
    return classLoader
}