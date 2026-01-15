package com.tencent.tinker.internal.module.layout

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.annotation.NonDeployProcessOnly
import com.tencent.tinker.internal.patchDexApkFile
import com.tencent.tinker.internal.patchDexDirectory
import com.tencent.tinker.internal.patchLibraryDirectory
import com.tencent.tinker.internal.patchOatDirectory
import com.tencent.tinker.internal.patchResourceApkFile
import com.tencent.tinker.internal.rootDirectory
import com.tencent.tinker.internal.util.currentProcess
import com.tencent.tinker.internal.util.ensureIsExistingDirectory
import com.tencent.tinker.internal.util.expected
import com.tencent.tinker.internal.util.isInDeployProcess
import com.tencent.tinker.internal.util.symlinkTo
import java.io.File
import kotlin.random.Random

private enum class ErrorType : TinkerError.Type {
    UNEXPECTED,
    INVALID_SOURCE;

    override val group: TinkerError.TypeGroup
        get() = TinkerError.TypeGroup.MODULE_LAYOUT

    override val typeCode: Int
        get() = ordinal
}

@VisibleForTesting
internal fun patchLayoutConstructErrorTypeOfForTesting(type: String): TinkerError.Type =
    ErrorType.valueOf(type)

internal class PatchLayoutConstructorImpl(private val context: Context) : PatchLayoutConstructor() {

    @VisibleForTesting
    fun contextForTesting(): Context = context

    companion object {

        private val structurersAndCheckersFromBase = arrayOf(
            arrayOf(
                File::patchDexApkFile to File::isFile,
                File::patchDexDirectory to File::isDirectory,
            ),
            arrayOf(
                File::patchLibraryDirectory to File::isDirectory,
            ),
            arrayOf(
                File::patchResourceApkFile to File::isFile,
            ),
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
        oatDirectory: File?,
    ) {
        contentDirectory.ensureIsExistingDirectory()
        structurersAndCheckersFromBase.forEachIndexed { index, group ->
            group
                .firstNotNullOfOrNull { (builder, checker) ->
                    val source = builder(baseDirectory)
                    if (!checker(source)) {
                        return@firstNotNullOfOrNull null
                    }
                    val target = builder(contentDirectory)
                    return@firstNotNullOfOrNull source to target
                }
                ?.let {
                    it.first.symlinkTo(it.second)
                }
                ?: throw TinkerError(
                    ErrorType.INVALID_SOURCE,
                    "Construct source with group $index is invalid.",
                )
        }
        if (oatDirectory != null) {
            if (!oatDirectory.isDirectory) {
                throw TinkerError(
                    ErrorType.INVALID_SOURCE,
                    "Construct source \"${oatDirectory.absolutePath}\" is invalid."
                )
            }
            oatDirectory.symlinkTo(contentDirectory.patchOatDirectory)
        } else {
            // Create a temporary directory. All files generated by system will be dropped after next process start.
            contentDirectory.patchOatDirectory.mkdirs()
        }
    }

    @Synchronized
    @NonDeployProcessOnly
    override fun construct(
        baseDirectory: File,
        oatDirectory: File?,
    ): File {
        check(!context.isInDeployProcess) {
            "Cannot construct directory in deploy process"
        }
        check(baseDirectory.isDirectory) {
            "Base directory is not a existence directory"
        }
        check(oatDirectory?.isDirectory != false) {
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