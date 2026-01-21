package com.tencent.tinker.internal.module.layout

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.tencent.tinker.Tinker
import com.tencent.tinker.internal.annotation.NonDeployProcessOnly
import com.tencent.tinker.internal.patchDexApkFile
import com.tencent.tinker.internal.patchDexDirectory
import com.tencent.tinker.internal.patchLibraryDirectory
import com.tencent.tinker.internal.patchOatDirectory
import com.tencent.tinker.internal.patchResourceApkFile
import com.tencent.tinker.internal.rootDirectory
import com.tencent.tinker.internal.util.crc32
import com.tencent.tinker.internal.util.currentProcess
import com.tencent.tinker.internal.util.debugLog
import com.tencent.tinker.internal.util.ensureIsExistingDirectory
import com.tencent.tinker.internal.util.expected
import com.tencent.tinker.internal.util.isInDeployProcess
import com.tencent.tinker.internal.util.symlinkTo
import com.tencent.tinker.internal.util.warnLog
import java.io.File
import kotlin.random.Random

private const val TAG = "Tinker.Layout"

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
            debugLog(TAG) {
                "Refreshing process base directory \"${directory.absolutePath}\"."
            }
            directory.apply {
                ensureIsExistingDirectory()
                listFiles()!!.forEach { contentDirectory ->
                    contentDirectory.also(::cleanContentDirectory)
                }
            }
        }

        private fun cleanContentDirectory(directory: File) {
            directory.apply {
                if (!exists()) {
                    debugLog(TAG) {
                        "Skip cleaning content directory children \"${this.absolutePath}\" because it is not exist."
                    }
                    return
                }
                listFiles()?.forEach {
                    debugLog(TAG) {
                        "Cleaning content directory children \"${it.absolutePath}\"."
                    }
                    it.delete()
                }
                debugLog(TAG) {
                    "Cleaning content directory \"${absolutePath}\" itself."
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
    private val processHash by lazy {
        context.currentProcess
            .toByteArray(Charsets.UTF_8)
            .crc32
            .toString(16)
    }

    /**
     * Directory to store constructed directories.
     *
     * Once a process is started, the process base directory will be fully cleaned and
     * reconstructed.
     */
    private val processBaseDirectory by lazy {
        baseDirectory.resolve(processHash).apply {
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
            currentTime.toString(Character.MAX_RADIX).let(::append)
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
                        warnLog(TAG) {
                            "Source \"${source.absolutePath}\" is missing or invalid."
                        }
                        return@firstNotNullOfOrNull null
                    }
                    val target = builder(contentDirectory)
                    return@firstNotNullOfOrNull source to target
                }
                ?.let {
                    debugLog(TAG) {
                        "Symbolic-linking \"${it.first.absolutePath}\" to \"${it.second.absolutePath}\"."
                    }
                    it.first.symlinkTo(it.second)
                }
                ?: throw Tinker.Error(
                    Tinker.Error.Layout.INVALID_SOURCE,
                    "Construct source with group $index is invalid.",
                )
        }
        if (oatDirectory != null) {
            if (!oatDirectory.isDirectory) {
                throw Tinker.Error(
                    Tinker.Error.Layout.INVALID_SOURCE,
                    "Construct source \"${oatDirectory.absolutePath}\" is invalid."
                )
            }
            debugLog(TAG) {
                "Symbolic-linking \"${oatDirectory.absolutePath}\" to \"${contentDirectory.patchOatDirectory.absolutePath}\"."
            }
            oatDirectory.symlinkTo(contentDirectory.patchOatDirectory)
        } else {
            // Create a temporary directory. All files generated by system will be dropped after next process start.
            debugLog(TAG) {
                "Creating temporary OAT directory \"${contentDirectory.patchOatDirectory.absolutePath}\"."
            }
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
        expected<Tinker.Error.Layout>("construct layout") {
            val contentDirectory = contentDirectory(generateRandomHash())
            debugLog(TAG) {
                "Constructing patch layout directory" +
                        " for base directory \"${baseDirectory.absolutePath}\"" +
                        " and OAT directory \"${oatDirectory?.absolutePath}\"" +
                        " at \"${contentDirectory.absolutePath}\"."
            }
            try {
                construct(contentDirectory, baseDirectory, oatDirectory)
            } catch (throwable: Throwable) {
                cleanContentDirectory(contentDirectory)
                if (throwable is Tinker.Error) {
                    throw throwable
                }
                throw Tinker.Error(
                    Tinker.Error.Layout.UNEXPECTED,
                    "Construct patch directory failed with unexpected error.",
                    throwable,
                )
            }
            return contentDirectory
        }
    }
}