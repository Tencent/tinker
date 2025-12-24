package com.tencent.tinker.internal.load.dex

import androidx.annotation.VisibleForTesting
import com.tencent.tinker.internal.Patch
import com.tencent.tinker.internal.TEST_DEX_FILE_NAME
import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.load.Loader
import com.tencent.tinker.internal.util.expected
import java.io.File

internal abstract class DexLoader : Loader() {

    companion object {
        @VisibleForTesting
        fun errorTypeOfForTesting(type: String): TinkerError.Type =
            ErrorType.valueOf(type)
    }

    private enum class ErrorType : TinkerError.Type {
        UNEXPECTED,
        NO_VALID_INPUTS,
        TEST_CLASS_BROKEN,
        VERIFY_FAILED;

        override val group: TinkerError.TypeGroup
            get() = TinkerError.TypeGroup.LOAD_DEX

        override val typeCode: Int
            get() = ordinal
    }

    override fun load() {
        dexLoad().also(::verify)
    }

    /**
     * Loads patch dex files, and returns a class loader that can load classes from patch dex files.
     */
    protected abstract fun dexLoad(): ClassLoader

    @VisibleForTesting
    fun dexLoadForTesting() {
        dexLoad()
    }

    private fun verify(classLoader: ClassLoader) {
        // FIXME:
        //   The best way to verify if patch test dex is loaded is using application class loader,
        //     not class loader returned from implementation. We never know the class loader we get
        //     is the same class loader the application will use.
        //   Getting application class loader may cause code is too complex to test. Try to find a
        //     better solution.
        val patched = try {
            Class.forName(
                "com.tencent.tinker.internal.load.dex.test.TestClass",
                true,
                classLoader
            ).getDeclaredField("isPatched").getBoolean(null)
        } catch (throwable: Throwable) {
            throw TinkerError(
                ErrorType.TEST_CLASS_BROKEN,
                "Cannot find \"com.tencent.tinker.internal.load.dex.test.TestClass\" from provided class loader.",
                throwable,
            )
        }
        if (!patched) {
            throw TinkerError(
                ErrorType.VERIFY_FAILED,
                "Cannot load patched test class by provided class loader.",
            )
        }
    }

    abstract class Factory : Loader.Factory() {

        companion object {
            private val classesDexWithIndexPattern =
                "classes(\\d*)\\.dex".toRegex()
        }

        private fun buildInputs(patch: Patch): List<File> =
            patch.dexDirectory.takeIf { it.isDirectory }
                ?.listFiles()
                ?.filter {
                    it.isFile && it.extension == "dex"
                }
                ?.takeIf { it.isNotEmpty() }
                ?.map { dex ->
                    if (dex.name == TEST_DEX_FILE_NAME) {
                        return@map dex to (2 to 0)
                    }
                    val match = classesDexWithIndexPattern
                        .matchEntire(dex.name)
                        ?: return@map dex to (1 to dex.name)
                    val index = match.groupValues[1]
                        .takeIf { it.isNotEmpty() }
                        ?.toInt()
                        ?: 0
                    return@map dex to (0 to index)
                }
                ?.sortedWith(
                    compareBy(
                        { it.second.first },
                        { it.second.second }
                    )
                )
                ?.map { it.first }
                ?: throw TinkerError(
                    ErrorType.NO_VALID_INPUTS,
                    "Missing valid input dex files in \"${patch.dexDirectory.absolutePath}\".",
                )


        override fun createLoaderIfNeeded(patch: Patch): DexLoader {
            expected<ErrorType>("create dex loader") {
                return buildInputs(patch).let(::createLoaderByDexFiles)
            }
        }

        protected abstract fun createLoaderByDexFiles(inputs: List<File>): DexLoader
    }

}