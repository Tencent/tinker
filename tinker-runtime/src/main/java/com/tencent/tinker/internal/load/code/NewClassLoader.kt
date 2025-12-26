package com.tencent.tinker.internal.load.code

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.load.code.NewClassLoaderCodeLoader.ClassLoaderInjector
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
 * Code loader which loads by creating a new class loader.
 */
@RequiresApi(Build.VERSION_CODES.N)
internal abstract class NewClassLoaderCodeLoader(
    private val reference: Array<ClassLoader?>,
    private val classLoaderInjectors: Iterable<ClassLoaderInjector>,
) : CodeLoader() {

    private object ErrorType : TinkerError.Type {

        override val group: TinkerError.TypeGroup
            get() = TinkerError.TypeGroup.LOAD_CODE_NOUGAT

        override val typeCode: Int
            get() = 0
    }

    abstract class ClassLoaderInjector {
        abstract fun inject(classLoader: ClassLoader)
    }

    protected abstract fun createClassLoader(): ClassLoader

    override fun loadForCode(): ClassLoader =
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
        abiList: Array<String>,
    ) : CodeLoader.Factory(abiList) {

        override fun createLoader(
            dexFiles: List<File>,
            libraryDirectories: List<File>,
        ): CodeLoader {
            expected("create code loader", ErrorType) {
                val dexPathList =
                    source.pathList
                val dexPaths =
                    dexFiles.joinToString(File.pathSeparator) { it.absolutePath }
                val sourceNativeLibraryDirectories =
                    dexPathList.nativeLibraryDirectoriesV23
                val updatedNativeLibraryDirectories =
                    if (sourceNativeLibraryDirectories != null) {
                        val pathsSet =
                            libraryDirectories.map { it.absolutePath }
                        libraryDirectories + sourceNativeLibraryDirectories.filter { it.absolutePath !in pathsSet }
                    } else {
                        libraryDirectories
                    }
                val libraryDirectoryPaths =
                    updatedNativeLibraryDirectories.joinToString(File.pathSeparator) { it.absolutePath }
                return createLoaderByPaths(dexPaths, libraryDirectoryPaths)
            }
        }

        protected abstract fun createLoaderByPaths(
            dexPaths: String,
            libraryDirectoryPaths: String
        ): NewClassLoaderCodeLoader
    }
}

@RequiresApi(Build.VERSION_CODES.S)
internal class V31NonHardeningCodeLoader private constructor(
    reference: Array<ClassLoader?>,
    classLoaderInjectors: Iterable<ClassLoaderInjector>,
    private val parentInjector: ReflectInjector,
    private val dexPaths: String,
    private val libraryDirectoryPaths: String,
    private val classLoaderConstructor: (String, String, ClassLoader) -> ClassLoader,
) : NewClassLoaderCodeLoader(reference, classLoaderInjectors) {

    override fun createClassLoader(): ClassLoader =
        classLoaderConstructor(dexPaths, libraryDirectoryPaths, ClassLoader.getSystemClassLoader())
            .also(parentInjector::inject)

    class Factory(
        reference: Array<ClassLoader?>,
        classLoaderInjectors: Iterable<ClassLoaderInjector>,
        source: ClassLoader = V31NonHardeningCodeLoader::class.java.classLoader!!,
        abiList: Array<String> = Build.SUPPORTED_ABIS,
        private val classLoaderConstructor: (String, String, ClassLoader) -> ClassLoader = ::DelegateLastClassLoader
    ) : NewClassLoaderCodeLoader.Factory(
        reference,
        classLoaderInjectors,
        source,
        abiList,
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
        ): V31NonHardeningCodeLoader {
            val parentInjector = ClassLoader::class.java.parentLazyInjector(source)
            return V31NonHardeningCodeLoader(
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
internal class V27NonHardeningCodeLoader private constructor(
    reference: Array<ClassLoader?>,
    classLoaderInjectors: Iterable<ClassLoaderInjector>,
    private val source: ClassLoader,
    private val dexPaths: String,
    private val libraryDirectoryPaths: String,
    private val classLoaderConstructor: (String, String, ClassLoader) -> ClassLoader,
) : NewClassLoaderCodeLoader(reference, classLoaderInjectors) {

    override fun createClassLoader(): ClassLoader =
        classLoaderConstructor(dexPaths, libraryDirectoryPaths, source)

    class Factory(
        reference: Array<ClassLoader?>,
        classLoaderInjectors: Iterable<ClassLoaderInjector>,
        source: ClassLoader = V27NonHardeningCodeLoader::class.java.classLoader!!,
        abiList: Array<String> = Build.SUPPORTED_ABIS,
        private val classLoaderConstructor: (String, String, ClassLoader) -> ClassLoader = ::DelegateLastClassLoader,
    ) : NewClassLoaderCodeLoader.Factory(
        reference,
        classLoaderInjectors,
        source,
        abiList,
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
        ): V27NonHardeningCodeLoader = V27NonHardeningCodeLoader(
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
internal class V24NonHardeningCodeLoader private constructor(
    reference: Array<ClassLoader?>,
    classLoaderInjectors: Iterable<ClassLoaderInjector>,
    private val source: ClassLoader,
    private val outputDirectory: File,
    private val dexPaths: String,
    private val libraryDirectoryPaths: String,
    private val classLoaderConstructor: (String, File, String, ClassLoader) -> ClassLoader,
) : NewClassLoaderCodeLoader(reference, classLoaderInjectors) {

    override fun createClassLoader(): ClassLoader =
        classLoaderConstructor(dexPaths, outputDirectory, libraryDirectoryPaths, source)

    class Factory(
        reference: Array<ClassLoader?>,
        classLoaderInjectors: Iterable<ClassLoaderInjector>,
        private val outputDirectory: File,
        source: ClassLoader = V24NonHardeningCodeLoader::class.java.classLoader!!,
        abiList: Array<String> = Build.SUPPORTED_ABIS,
        private val classLoaderConstructor: (String, File, String, ClassLoader) -> ClassLoader = ::TinkerClassLoader
    ) : NewClassLoaderCodeLoader.Factory(
        reference,
        classLoaderInjectors,
        source,
        abiList,
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
        ): V24NonHardeningCodeLoader = V24NonHardeningCodeLoader(
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