package com.tencent.tinker.internal.load.dex

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.load.dex.NewClassLoaderDexLoader.ClassLoaderInjector
import com.tencent.tinker.internal.module.hidden.ReflectInjector
import com.tencent.tinker.internal.module.hidden.ReflectSetter
import com.tencent.tinker.internal.module.hidden.base
import com.tencent.tinker.internal.module.hidden.classLoaderSetter
import com.tencent.tinker.internal.module.hidden.drawableInflater
import com.tencent.tinker.internal.module.hidden.nativeLibraryDirectoriesV23
import com.tencent.tinker.internal.module.hidden.packageInfo
import com.tencent.tinker.internal.module.hidden.parentLazyInjector
import com.tencent.tinker.internal.module.hidden.pathList
import com.tencent.tinker.internal.util.expected
import com.tencent.tinker.loader.TinkerClassLoader
import dalvik.system.DelegateLastClassLoader
import java.io.File

@RequiresApi(Build.VERSION_CODES.N)
private object CurrentThreadContextClassLoaderInjector
    : ClassLoaderInjector() {
    override fun inject(classLoader: ClassLoader) {
        Thread.currentThread().contextClassLoader = classLoader
    }
}

@RequiresApi(Build.VERSION_CODES.N)
private class FieldClassLoaderInjector(
    private val delegate: ReflectSetter
) : ClassLoaderInjector() {
    override fun inject(classLoader: ClassLoader) {
        delegate.set(classLoader)
    }
}

@RequiresApi(Build.VERSION_CODES.N)
private fun createDefaultClassLoaderInjectors(
    application: Application
): List<ClassLoaderInjector> =
    buildList {
        CurrentThreadContextClassLoaderInjector.let(::add)
        val baseContext = application.base
        try {
            baseContext.classLoaderSetter
                .let(::FieldClassLoaderInjector)
                .let(::add)
        } catch (_: Throwable) {
            // There's no `mClassLoader` field in `ContextImpl` before Android O. However we
            // should try our best to replace this field in case some customized system has one.
        }
        baseContext.packageInfo
            ?.classLoaderSetter
            ?.let(::FieldClassLoaderInjector)
            ?.let(::add)
        val resources = application.resources
        try {
            resources.classLoaderSetter
                .let(::FieldClassLoaderInjector)
                .let(::add)
        } catch (_: Throwable) {
            // Ignored.
        }
        try {
            resources.drawableInflater
                ?.classLoaderSetter
                ?.let(::FieldClassLoaderInjector)
                ?.let(::add)
        } catch (_: Throwable) {
            // Ignored.
        }
    }

/**
 * Dex loader which loads by creating a new class loader.
 */
@RequiresApi(Build.VERSION_CODES.N)
internal abstract class NewClassLoaderDexLoader(
    private val reference: Array<ClassLoader?>,
    private val classLoaderInjectors: Iterable<ClassLoaderInjector>,
) : DexLoader() {

    private object ErrorType : TinkerError.Type {

        override val group: TinkerError.TypeGroup
            get() = TinkerError.TypeGroup.LOAD_DEX_NOUGAT

        override val typeCode: Int
            get() = 0
    }

    abstract class ClassLoaderInjector {
        abstract fun inject(classLoader: ClassLoader)
    }

    protected abstract fun createClassLoader(): ClassLoader

    override fun dexLoad(): ClassLoader =
        createClassLoader().also { classLoader ->
            classLoaderInjectors.forEach { injector ->
                injector.inject(classLoader)
            }
            reference[0] = classLoader
        }

    internal abstract class Factory(
        protected val reference: Array<ClassLoader?>,
        protected val classLoaderInjectors: Iterable<ClassLoaderInjector>,
        protected val source: ClassLoader,
    ) : DexLoader.Factory() {

        override fun createLoaderByDexFiles(inputs: List<File>): DexLoader {
            expected("create loader by dex files", ErrorType) {
                val dexPathList = source.pathList
                val sourceNativeLibraryDirectories =
                    dexPathList.nativeLibraryDirectoriesV23
                val dexPaths =
                    inputs.joinToString(File.pathSeparator) { it.absolutePath }
                val libraryDirectoryPaths =
                    sourceNativeLibraryDirectories
                        ?.joinToString(File.pathSeparator) { it.absolutePath }
                        ?: ""
                return createLoaderByPaths(dexPaths, libraryDirectoryPaths)
            }
        }

        protected abstract fun createLoaderByPaths(
            dexPaths: String,
            libraryDirectoryPaths: String
        ): NewClassLoaderDexLoader
    }
}

@RequiresApi(Build.VERSION_CODES.S)
internal class V31NonHardeningDexLoader private constructor(
    reference: Array<ClassLoader?>,
    classLoaderInjectors: Iterable<ClassLoaderInjector>,
    private val parentInjector: ReflectInjector,
    private val dexPaths: String,
    private val libraryDirectoryPaths: String,
    private val classLoaderConstructor: (String, String, ClassLoader) -> ClassLoader,
) : NewClassLoaderDexLoader(reference, classLoaderInjectors) {

    override fun createClassLoader(): ClassLoader =
        classLoaderConstructor(dexPaths, libraryDirectoryPaths, ClassLoader.getSystemClassLoader())
            .also(parentInjector::inject)

    class Factory(
        reference: Array<ClassLoader?>,
        classLoaderInjectors: Iterable<ClassLoaderInjector>,
        source: ClassLoader = V31NonHardeningDexLoader::class.java.classLoader!!,
        private val classLoaderConstructor: (String, String, ClassLoader) -> ClassLoader = ::DelegateLastClassLoader
    ) : NewClassLoaderDexLoader.Factory(
        reference,
        classLoaderInjectors,
        source,
    ) {
        constructor(
            reference: Array<ClassLoader?>,
            application: Application,
        ) : this(
            reference = reference,
            classLoaderInjectors = createDefaultClassLoaderInjectors(application),
        )

        override fun createLoaderByPaths(
            dexPaths: String,
            libraryDirectoryPaths: String
        ): NewClassLoaderDexLoader {
            val parentInjector = ClassLoader::class.java.parentLazyInjector(source)
            return V31NonHardeningDexLoader(
                reference = reference,
                classLoaderInjectors = classLoaderInjectors,
                parentInjector = parentInjector,
                dexPaths = dexPaths,
                libraryDirectoryPaths = libraryDirectoryPaths,
                classLoaderConstructor = classLoaderConstructor,
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O_MR1)
internal class V27NonHardeningDexLoader private constructor(
    reference: Array<ClassLoader?>,
    classLoaderInjectors: Iterable<ClassLoaderInjector>,
    private val source: ClassLoader,
    private val dexPaths: String,
    private val libraryDirectoryPaths: String,
    private val classLoaderConstructor: (String, String, ClassLoader) -> ClassLoader,
) : NewClassLoaderDexLoader(reference, classLoaderInjectors) {

    override fun createClassLoader(): ClassLoader =
        classLoaderConstructor(dexPaths, libraryDirectoryPaths, source)

    class Factory(
        reference: Array<ClassLoader?>,
        classLoaderInjectors: Iterable<ClassLoaderInjector>,
        source: ClassLoader = V27NonHardeningDexLoader::class.java.classLoader!!,
        private val classLoaderConstructor: (String, String, ClassLoader) -> ClassLoader = ::DelegateLastClassLoader,
    ) : NewClassLoaderDexLoader.Factory(
        reference,
        classLoaderInjectors,
        source,
    ) {
        constructor(
            reference: Array<ClassLoader?>,
            application: Application,
        ) : this(
            reference = reference,
            classLoaderInjectors = createDefaultClassLoaderInjectors(application),
        )

        override fun createLoaderByPaths(
            dexPaths: String,
            libraryDirectoryPaths: String
        ): NewClassLoaderDexLoader = V27NonHardeningDexLoader(
            reference = reference,
            classLoaderInjectors = classLoaderInjectors,
            source = source,
            dexPaths = dexPaths,
            libraryDirectoryPaths = libraryDirectoryPaths,
            classLoaderConstructor = classLoaderConstructor,
        )
    }
}

@RequiresApi(Build.VERSION_CODES.N)
internal class V24NonHardeningDexLoader private constructor(
    reference: Array<ClassLoader?>,
    classLoaderInjectors: Iterable<ClassLoaderInjector>,
    private val source: ClassLoader,
    private val outputDirectory: File,
    private val dexPaths: String,
    private val libraryDirectoryPaths: String,
    private val classLoaderConstructor: (String, File, String, ClassLoader) -> ClassLoader,
) : NewClassLoaderDexLoader(reference, classLoaderInjectors) {

    override fun createClassLoader(): ClassLoader =
        classLoaderConstructor(dexPaths, outputDirectory, libraryDirectoryPaths, source)

    class Factory(
        reference: Array<ClassLoader?>,
        classLoaderInjectors: Iterable<ClassLoaderInjector>,
        private val outputDirectory: File,
        source: ClassLoader = V24NonHardeningDexLoader::class.java.classLoader!!,
        private val classLoaderConstructor: (String, File, String, ClassLoader) -> ClassLoader = ::TinkerClassLoader
    ) : NewClassLoaderDexLoader.Factory(
        reference,
        classLoaderInjectors,
        source,
    ) {
        constructor(
            reference: Array<ClassLoader?>,
            application: Application,
            outputDirectory: File,
        ) : this(
            reference = reference,
            classLoaderInjectors = createDefaultClassLoaderInjectors(application),
            outputDirectory = outputDirectory,
        )

        override fun createLoaderByPaths(
            dexPaths: String,
            libraryDirectoryPaths: String
        ): NewClassLoaderDexLoader = V24NonHardeningDexLoader(
            reference = reference,
            classLoaderInjectors = classLoaderInjectors,
            source = source,
            outputDirectory = outputDirectory,
            dexPaths = dexPaths,
            libraryDirectoryPaths = libraryDirectoryPaths,
            classLoaderConstructor = classLoaderConstructor,
        )
    }
}