package com.tencent.tinker.internal.load.dex

import android.app.Application
import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.module.hidden.ReflectLazySetter
import com.tencent.tinker.internal.module.hidden.dexElements
import com.tencent.tinker.internal.module.hidden.lazySetDexElements
import com.tencent.tinker.internal.module.hidden.makeElements
import com.tencent.tinker.internal.module.hidden.pathList
import com.tencent.tinker.internal.util.expected
import java.io.File
import java.io.IOException
import java.lang.reflect.Array as JvmReflectArray

/**
 * Dex loader which loads by injecting path into system class loader.
 */
internal class InjectPathDexLoader(
    private val source: ClassLoader,
    private val injector: ReflectLazySetter,
) : DexLoader() {

    private object ErrorType : TinkerError.Type {
        override val group: TinkerError.TypeGroup
            get() = TinkerError.TypeGroup.LOAD_DEX_OLD

        override val typeCode: Int
            get() = 0
    }

    override fun dexLoad(): ClassLoader {
        injector.run()
        return source
    }

    class Factory(
        private val source: ClassLoader,
        private val outputDirectory: File,
    ) : DexLoader.Factory() {

        constructor(
            application: Application,
            outputDirectory: File,
        ) : this(
            source = application.classLoader,
            outputDirectory = outputDirectory,
        )

        @Suppress("UNCHECKED_CAST")
        override fun createLoaderByDexFiles(inputs: List<File>): DexLoader {
            expected("create loader by dex files", ErrorType) {
                val pathList = source.pathList
                val inputsAsArrayList = ArrayList<File>().apply {
                    addAll(inputs)
                }
                val suppressedExceptions = ArrayList<IOException>()
                val expandedElements = pathList.makeElements(
                    files = inputsAsArrayList,
                    optimizedDirectory = outputDirectory,
                    suppressedExceptions = suppressedExceptions
                )
                suppressedExceptions.forEach { throw it }
                val originalDexElements = pathList.dexElements
                val updatedDexElements = JvmReflectArray
                    .newInstance(
                        originalDexElements.javaClass.componentType!!,
                        expandedElements.size + originalDexElements.size,
                    )
                    .apply {
                        System.arraycopy(
                            expandedElements,
                            0,
                            this,
                            0,
                            expandedElements.size
                        )
                        System.arraycopy(
                            originalDexElements,
                            0,
                            this,
                            expandedElements.size,
                            originalDexElements.size
                        )
                    }
                    .let {
                        it as Array<Any>
                    }
                return InjectPathDexLoader(
                    source = source,
                    injector = pathList.lazySetDexElements(updatedDexElements),
                )
            }
        }
    }
}