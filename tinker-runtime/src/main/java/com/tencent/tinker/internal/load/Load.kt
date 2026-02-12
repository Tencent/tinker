package com.tencent.tinker.internal.load

import android.app.Application
import android.os.Build
import androidx.annotation.VisibleForTesting
import com.tencent.tinker.Tinker
import com.tencent.tinker.internal.Patch
import com.tencent.tinker.internal.annotation.NonDeployProcessOnly
import com.tencent.tinker.internal.errorTypeShouldBeThrown
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
import com.tencent.tinker.internal.util.className
import com.tencent.tinker.internal.util.currentProcess
import com.tencent.tinker.internal.util.debugLog
import com.tencent.tinker.internal.util.expected
import com.tencent.tinker.internal.util.infoLog
import com.tencent.tinker.internal.util.isInDeployProcess
import com.tencent.tinker.internal.util.traceE
import com.tencent.tinker.internal.util.traceS
import com.tencent.tinker.internal.util.traceTask
import java.io.File

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
        mapNotNull {
            traceE("load.factor(factory = ${it.javaClass.className}@${it.hashCode().toString(16)})") {
                it.createLoaderIfNeeded(patch)
            }
        }
    }
    try {
        loaders.forEach {
            traceS("load.load(loader = ${it.javaClass.className}@${it.hashCode().toString(16)})") {
                it.load()
            }
        }
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
    val factories = buildList {
        if (!hardening && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            traceS("load.create_factory(type = code#v31_non_hardening)") {
                V31NonHardeningCodeLoader.Factory(
                    reference = classLoaderReference,
                    application = this@loadWith
                ).let(::add)
            }
        } else if (!hardening && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            traceS("load.create_factory(type = code#v27_non_hardening)") {
                V27NonHardeningCodeLoader.Factory(
                    reference = classLoaderReference,
                    application = this@loadWith
                ).let(::add)
            }
        } else if (!hardening && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            traceS("load.create_factory(type = code#v24_non_hardening)") {
                V24NonHardeningCodeLoader.Factory(
                    reference = classLoaderReference,
                    application = this@loadWith,
                    outputDirectory = patch.oatDirectory,
                ).let(::add)
            }
        } else {
            traceS("load.create_factory(type = code#inject_path)") {
                InjectPathCodeLoader.Factory(
                    application = this@loadWith,
                    outputDirectory = patch.oatDirectory,
                ).let(::add)
            }
        }
        traceS("load.create_factory(type = resource)") {
            ResourceLoader.Factory(
                applicationContext = this@loadWith,
            ).let(::add)
        }
    }
    debugLog(TAG) {
        buildList {
            add("Load with loaders:")
            factories.forEach {
                add("  ${it.javaClass.name}")
            }
        }.joinToString("\n")
    }
    factories.tryLoad(patch)
    return classLoaderReference[0]
}

@NonDeployProcessOnly
private fun Application.loadWith(
    hardening: Boolean,
    rawPatch: RawPatch,
    validator: Validator?,
    oatManager: OatManager = OatManager.with(this),
    patchLayoutConstructor: PatchLayoutConstructor = PatchLayoutConstructor.with(this),
): Pair<ClassLoader, File>? {
    traceS("load.validate.validate") {
        validator?.run {
            debugLog(TAG) {
                "Validating \"${rawPatch.directory.absolutePath}\" with validator <${javaClass.name}>."
            }
            validate(rawPatch.directory)
        }
    }
    debugLog(TAG) {
        "Acquiring OAT for \"${rawPatch.directory.absolutePath}\"" +
                " with manager <${oatManager.javaClass.name}>."
    }
    val oatDirectory = traceE("load.oat.acquire") {
        oatManager.acquire(rawPatch.directory, skipGenerateIfMissing = true)
    }
    debugLog(TAG) {
        "Constructing layout with constructor <${patchLayoutConstructor.javaClass.name}>."
    }
    val patchDirectory = traceE("load.layout.construct") {
        patchLayoutConstructor.construct(
            baseDirectory = rawPatch.directory,
            oatDirectory = oatDirectory,
        )
    }
    debugLog(TAG) {
        "Patch directory is \"${patchDirectory.absolutePath}\"."
    }
    val patch = Patch(rawPatch.version, patchDirectory)
    val classLoader = loadWith(
        hardening = hardening,
        patch = patch,
    ) ?: return null
    return classLoader to patchDirectory
}

private class LoadResult(
    val classLoader: ClassLoader,
    val version: String,
    val patchDirectory: File
)

@NonDeployProcessOnly
private fun Application.loadInternal(
    hardening: Boolean,
    skipValidating: Boolean,
    rawPatchManager: RawPatchManager = RawPatchManager.with(this),
): LoadResult? {
    val rawPatch = traceE("load.raw_patch.acquire") {
        rawPatchManager.acquire() ?: run {
            infoLog(TAG) {
                "No loadable patch found, skip loading."
            }
            return null
        }
    }
    infoLog(TAG) {
        "Raw patch \"${rawPatch.version}\" is acquired, try loading."
    }
    val (classLoader, patchDirectory) = try {
        loadWith(
            hardening = hardening,
            rawPatch = rawPatch,
            validator = if (skipValidating) null else ValidatorImpl,
        ) ?: return null
    } catch (throwable: Throwable) {
        rawPatchManager.requestUnavailable(rawPatch.version)
        throw throwable
    }
    return LoadResult(
        classLoader = classLoader,
        version = rawPatch.version,
        patchDirectory = patchDirectory,
    )
}

/**
 * Loads patch.
 *
 * If any patch is loaded successfully, a class loader which can load patch classes will be returned. Otherwise, the
 * function returns null.
 */
@NonDeployProcessOnly
private fun Application.load(
    hardening: Boolean,
    skipValidating: Boolean,
    callback: Tinker.Callback<Tinker.TaskSummary.Load>?,
): ClassLoader? {
    infoLog(TAG) {
        "Try loading patch in process \"${currentProcess}\"."
    }
    val (pair, events) = traceTask("load") {
        try {
            val result = expected<Tinker.Error.Load, LoadResult?>("load patch") {
                loadInternal(
                    hardening = hardening,
                    skipValidating = skipValidating,
                )
            }
            result to null
        } catch (error: Tinker.Error) {
            if (error.type in errorTypeShouldBeThrown) {
                throw error
            }
            null to error
        }
    }
    val (result, error) = pair
    callback?.apply {
        onTaskComplete(
            Tinker.TaskSummary.Load(
                error,
                events,
                result?.version,
                result?.patchDirectory,
            )
        )
    }
    return result?.classLoader
}

private fun loadInternal(
    application: Application,
    config: Tinker.AppConfig,
    disabled: Boolean,
    hardening: Boolean,
    skipValidating: Boolean,
): Tinker.AppLike? {
    val appLikeClassLoader = if (disabled || application.isInDeployProcess) {
        application.classLoader
    } else {
        application.load(
            hardening = hardening,
            skipValidating = skipValidating,
            callback = config.loadCallback(),
        ) ?: application.classLoader
    }
    // Do not catch any throwable while creating delegate application class. It should be fail-fast if user
    // provides an invalid delegate application class name.
    return config.appLikeClassName()
        ?.let {
            appLikeClassLoader.loadClass(it)
        }
        ?.getConstructor(Application::class.java)
        ?.newInstance(application)
        ?.let {
            it as Tinker.AppLike
        }
}

internal fun Tinker.App.load(
    disabled: Boolean,
    hardening: Boolean,
    skipValidating: Boolean,
): Tinker.AppLike? = loadInternal(
    application = this,
    config = this,
    disabled = disabled,
    hardening = hardening,
    skipValidating = skipValidating,
)

internal fun Application.legacyLoad(
    disabled: Boolean,
    hardening: Boolean,
    skipValidating: Boolean,
): Tinker.AppLike? = loadInternal(
    application = this,
    config = this as? Tinker.AppConfig
        ?: throw IllegalArgumentException("Application must be a Tinker.AppConfig"),
    disabled = disabled,
    hardening = hardening,
    skipValidating = skipValidating,
)