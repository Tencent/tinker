package com.tencent.tinker.internal.module.layout

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.annotation.NonPatchProcessOnly
import com.tencent.tinker.internal.patchApkFile
import com.tencent.tinker.internal.patchDexDirectory
import com.tencent.tinker.internal.patchLibraryDirectory
import com.tencent.tinker.internal.patchOatDirectory
import com.tencent.tinker.internal.patchResourceDirectory
import com.tencent.tinker.internal.rootDirectory
import com.tencent.tinker.internal.util.currentProcess
import com.tencent.tinker.internal.util.ensureIsExistingDirectory
import com.tencent.tinker.internal.util.expected
import com.tencent.tinker.internal.util.isInPatchProcess
import com.tencent.tinker.internal.util.symlinkTo
import java.io.File
import kotlin.random.Random

internal class PatchLayoutConstructorImpl(private val context: Context) : PatchLayoutConstructor() {

    @VisibleForTesting
    fun contextForTesting(): Context = context

    companion object {

        private val structuresFromBase = arrayOf(
            File::patchApkFile to File::isFile,
            File::patchDexDirectory to File::isDirectory,
            File::patchLibraryDirectory to File::isDirectory,
            File::patchResourceDirectory to File::isDirectory
        )

        private fun refreshProcessBaseDirectory(directory: File) {
            directory.apply {
                ensureIsExistingDirectory()
                listFiles()!!.forEach { contentDirectory ->
                    contentDirectory
                        .also(::cleanContentDirectory)
                        .delete()
                }
            }
        }

        private fun cleanContentDirectory(directory: File) {
            directory.apply {
                if (!exists()) {
                    return
                }
                listFiles()?.forEach {
                    it.delete()
                }
                delete()
            }
        }

        @VisibleForTesting
        fun errorTypeOf(type: String): TinkerError.Type {
            return ErrorType.valueOf(type)
        }
    }

    private enum class ErrorType : TinkerError.Type {
        UNEXPECTED,
        INVALID_SOURCE;

        override val group: TinkerError.TypeGroup
            get() = TinkerError.TypeGroup.MODULE_LAYOUT

        override val typeCode: Int
            get() = ordinal
    }


    private val baseDirectory: File
        get() = context.rootDirectory.resolve("layout")

    /**
     * Encoded process name, which is used as a valid directory name for process base directory.
     */
    @OptIn(ExperimentalStdlibApi::class)
    private val encodedProcessName by lazy {
        context.currentProcess
            .toByteArray(Charsets.UTF_8)
            .toHexString() // The conversion is just for avoiding illegal characters.
            .let(baseDirectory::resolve)
    }

    /**
     * Directory to store constructed directories.
     *
     * Once a process is started, the process base directory will be fully cleaned and
     * reconstructed.
     */
    private val processBaseDirectory by lazy {
        baseDirectory.resolve(encodedProcessName).apply {
            refreshProcessBaseDirectory(this)
        }
    }

    @VisibleForTesting
    fun processBaseDirectoryForTesting(): File =
        processBaseDirectory

    @VisibleForTesting
    fun refreshProcessBaseDirectoryForTesting() {
        refreshProcessBaseDirectory(processBaseDirectory)
    }


    /**
     * Directory with [hash] as name as constructed.
     */
    private fun contentDirectory(hash: String): File =
        processBaseDirectory.resolve(hash)


    @OptIn(ExperimentalStdlibApi::class)
    private fun generateRandomHash(): String {
        val currentTime = System.currentTimeMillis()
        val randomSuffix = Random.nextBytes(4)
        return buildString {
            currentTime.toHexString().let(::append)
            "-".let(::append)
            randomSuffix.toHexString().let(::append)
        }
    }

    private fun construct(
        contentDirectory: File,
        baseDirectory: File,
        oatDirectory: File,
    ) {
        contentDirectory.ensureIsExistingDirectory()
        structuresFromBase.forEach { (structure, checker) ->
            val source = structure(baseDirectory)
            val target = structure(contentDirectory)
            if (!checker(source)) {
                throw TinkerError(
                    ErrorType.INVALID_SOURCE,
                    "Construct source \"${source.absolutePath}\" is invalid.",
                )
            }
            source.symlinkTo(target)
        }
        oatDirectory.apply {
            if (!isDirectory) {
                throw TinkerError(
                    ErrorType.INVALID_SOURCE,
                    "Construct source \"${absolutePath}\" is invalid."
                )
            }
            symlinkTo(contentDirectory.patchOatDirectory)
        }
    }

    @Synchronized
    @NonPatchProcessOnly
    override fun construct(
        baseDirectory: File,
        oatDirectory: File
    ): File {
        check(!context.isInPatchProcess) {
            "Cannot construct directory in patch process"
        }
        check(baseDirectory.isDirectory) {
            "Base directory is not a existence directory"
        }
        check(oatDirectory.isDirectory) {
            "OAT directory is not a existence directory"
        }
        expected<ErrorType>("construct layout") {
            val contentDirectory = contentDirectory(generateRandomHash())
            try {
                construct(contentDirectory, baseDirectory, oatDirectory)
            } catch (throwable: Throwable) {
                cleanContentDirectory(contentDirectory)
                if (throwable is TinkerError) {
                    throw throwable
                }
                throw TinkerError(
                    ErrorType.UNEXPECTED,
                    "Construct patch directory failed with unexpected error.",
                    throwable,
                )
            }
            return contentDirectory
        }
    }
}