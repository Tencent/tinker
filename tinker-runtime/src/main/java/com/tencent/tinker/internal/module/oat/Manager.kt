package com.tencent.tinker.internal.module.oat

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.annotation.NonDeployProcessOnly
import com.tencent.tinker.internal.annotation.DeployProcessOnly
import com.tencent.tinker.internal.rootDirectory
import com.tencent.tinker.internal.util.EscapedGuardedContent
import com.tencent.tinker.internal.util.currentSdk
import com.tencent.tinker.internal.util.ensureIsExistingFile
import com.tencent.tinker.internal.util.errorLog
import com.tencent.tinker.internal.util.escapedGuardedContentExclusiveNullable
import com.tencent.tinker.internal.util.escapedGuardedContentShared
import com.tencent.tinker.internal.util.expected
import com.tencent.tinker.internal.util.guardedReadOrWriteContent
import com.tencent.tinker.internal.util.guardedReadOrWriteContentNullable
import com.tencent.tinker.internal.util.infoLog
import com.tencent.tinker.internal.util.isInDeployProcess
import com.tencent.tinker.internal.util.use
import com.tencent.tinker.internal.util.warnLog
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.CRC32
import kotlin.concurrent.thread
import kotlin.random.Random

private enum class ErrorType : TinkerError.Type {
    UNEXPECTED,
    HAS_ACQUIRED_OAT,
    GENERATE_OR_STORE_FAILED;

    override val group: TinkerError.TypeGroup
        get() = TinkerError.TypeGroup.MODULE_OAT

    override val typeCode: Int
        get() = ordinal
}

@VisibleForTesting
internal fun oatErrorTypeOfForTesting(type: String): TinkerError.Type =
    ErrorType.valueOf(type)

/**
 * On Android 8 and above, interpreting dex files is unnecessary.
 */
private val defaultInterpreter
    get() = when {
        currentSdk >= Build.VERSION_CODES.O -> EmptyGenerator
        else -> Interpreter
    }

/**
 * On Android 8 and above, compiling dex files is unnecessary.
 */
private val defaultCompiler
    get() = when {
        currentSdk >= Build.VERSION_CODES.O -> EmptyGenerator
        else -> Compiler
    }

internal class OatManagerImpl(
    private val context: Context,
    private val interpreter: Generator = defaultInterpreter,
    private val compiler: Generator = defaultCompiler,
) : OatManager() {

    @VisibleForTesting
    fun contextForTesting(): Context = context

    companion object {
        private const val TAG = "Tinker.Oat.Manager"
    }

    private enum class State(val code: Int) {
        DONE(0),
        FAILED(-1);

        companion object {
            fun decode(code: Int): State =
                when (code) {
                    DONE.code -> DONE
                    FAILED.code -> FAILED
                    else -> throw IllegalArgumentException("Invalid code $code")
                }
        }
    }

    private enum class GenerateMode(val code: Int) {
        COMPILE(0),
        INTERPRET(1);

        companion object {
            fun decode(code: Int): GenerateMode =
                when (code) {
                    COMPILE.code -> COMPILE
                    INTERPRET.code -> INTERPRET
                    else -> throw IllegalArgumentException("Invalid code $code")
                }
        }
    }

    /**
     * Metadata of OAT files of a specific patch.
     */
    private data class Metadata(
        /**
         * Base directory name of the OAT files.
         */
        val directoryName: String,
        /**
         * State of the generation.
         */
        val state: State,
        /**
         * Which mode is the OAT files generated in.
         *
         * The property is stored in the metadata, but is not being used at the moment.
         */
        val generateMode: GenerateMode,
        /**
         * Hash of the inputs. It is used to check if inputs are changed.
         */
        val inputsHash: Long,
        /**
         * Android fingerprint. It is used to check if the device system is OTA-updated.
         */
        val androidFingerprint: String = Build.FINGERPRINT,
        /**
         * Metadata version. It is used to check if Tinker is updated with incompatible changes.
         */
        val metadataVersion: Int = VERSION,
    ) {
        companion object {

            const val VERSION = 1
            private const val KEY_DIR = "dir"
            private const val KEY_STATE = "state"
            private const val KEY_GENERATE_MODE = "generate-mode"
            private const val KEY_INPUTS_HASH = "inputs-hash"
            private const val KEY_ANDROID_FINGERPRINT = "android-fingerprint"
            private const val KEY_METADATA_VERSION = "metadata-version"

            fun read(data: ByteArray): Metadata? = try {
                Properties()
                    .apply {
                        data.inputStream().use {
                            load(it)
                        }
                    }
                    .run {
                        Metadata(
                            getProperty(KEY_DIR)
                                ?: return@run null,
                            getProperty(KEY_STATE)
                                ?.toInt()
                                ?.let(State::decode)
                                ?: return@run null,
                            getProperty(KEY_GENERATE_MODE)
                                ?.toInt()
                                ?.let(GenerateMode::decode)
                                ?: return@run null,
                            getProperty(KEY_INPUTS_HASH)
                                ?.toLong()
                                ?: return@run null,
                            getProperty(KEY_ANDROID_FINGERPRINT)
                                ?: return@run null,
                            getProperty(KEY_METADATA_VERSION)?.toInt()
                                ?: return@run null,
                        )
                    }
            } catch (_: Throwable) {
                null
            }
        }

        val encoded: ByteArray
            get() = Properties()
                .apply {
                    setProperty(KEY_DIR, directoryName)
                    setProperty(KEY_STATE, state.code.toString())
                    setProperty(KEY_GENERATE_MODE, generateMode.code.toString())
                    setProperty(KEY_INPUTS_HASH, inputsHash.toString())
                    setProperty(KEY_ANDROID_FINGERPRINT, androidFingerprint)
                    setProperty(KEY_METADATA_VERSION, metadataVersion.toString())
                }
                .run {
                    ByteArrayOutputStream()
                        .use {
                            store(it, null)
                            it.toByteArray()
                        }
                }
    }


    /**
     * Base directory of OAT files.
     */
    private val baseDirectory: File
        get() = context.rootDirectory.resolve("oat-isolated")

    @VisibleForTesting
    fun baseDirectoryForTesting(): File =
        baseDirectory

    @OptIn(ExperimentalStdlibApi::class)
    private val File.pathHash: String
        get() = MessageDigest.getInstance("MD5").run {
            update(canonicalPath.toByteArray())
            digest().toHexString()
        }


    /**
     * File which contains metadata of OAT files.
     *
     * It is also the guard file of OAT files of dex directory. If a process uses OAT files,
     * the shared lock of metadata file must be acquired and be held until the process is dead.
     */
    private fun metadataFile(directoryPathHash: String): File =
        baseDirectory.resolve("${directoryPathHash}.metadata")

    @VisibleForTesting
    fun metadataFileForTesting(directory: File): File =
        metadataFile(directory.pathHash)


    /**
     * Directory to store directories which store OAT files.
     *
     * The directory is not storing OAT files directly. It contains one (in general) or several
     * directories.
     *
     * The key reason of designing is that OAT files generating is usually executed by system and
     * may be asynchronous. Which causes:
     *
     * - If the base directory stores OAT files by itself, the content may be overwritten since
     *   there is no lock to protect while generating is running by system.
     * - If using temporary directory, there is no solution to checks if OAT files are generated and
     *   ready to be moved.
     */
    private fun contentBaseDirectory(directoryPathHash: String): File =
        baseDirectory.resolve(directoryPathHash)

    @VisibleForTesting
    fun contentBaseDirectoryForTesting(directory: File): File =
        contentBaseDirectory(directory.pathHash)

    /**
     * Directory with [name] to truly store OAT files.
     */
    private fun contentDirectory(directoryPathHash: String, name: String): File =
        contentBaseDirectory(directoryPathHash).resolve(name)


    @GuardedBy("this")
    @NonDeployProcessOnly
    private var guardHolder: Pair<EscapedGuardedContent, AcquiringRecord>? = null

    private class AcquiringRecord(directory: File) :
        RuntimeException("Last acquiring with dex directory \"${directory.absolutePath}\" is invoked with following stacktrace") {
        init {
            stackTrace = stackTrace
                .dropWhile {
                    it.className == this.javaClass.name
                }
                .toTypedArray()
        }
    }

    @NonDeployProcessOnly
    private fun releaseGuard() {
        guardHolder?.let { (guardedContent, _) ->
            guardedContent.close()
        }
        guardHolder = null
    }

    @VisibleForTesting
    @NonDeployProcessOnly
    fun releaseGuardForTesting() {
        releaseGuard()
    }

    private val File.oatInputs: List<File>
        get() = takeIf { it.isDirectory }
            ?.listFiles()
            ?.filter {
                it.extension == "dex" || it.extension == "jar" || it.extension == "apk"
            }
            ?: emptyList()

    private val Collection<File>.inputsHash: Long
        get() = CRC32().let { calculator ->
            sortedBy { it.name }
                .forEachIndexed { index, input ->
                    calculator.update(index)
                    calculator.update(input.name.toByteArray())
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    input.inputStream().use { stream ->
                        while (true) {
                            val read = stream.read(buffer)
                            if (read == -1) {
                                break
                            }
                            calculator.update(buffer, 0, read)
                        }
                    }
                }
            calculator.value
        }

    private fun shouldGenerate(
        inputs: List<File>,
        stored: Pair<Metadata, File>?,
    ): Boolean {
        if (stored == null) {
            infoLog(
                TAG,
                "Generating is required while metadata is not created or corrupted."
            )
            return true
        }
        val (metadata, contentDirectory) = stored
        if (metadata.metadataVersion != Metadata.VERSION) {
            infoLog(
                TAG,
                "Generating is required while metadata version \"${metadata.metadataVersion}\" is updated to \"${Metadata.VERSION}\".",
            )
            return true
        }
        if (metadata.androidFingerprint != Build.FINGERPRINT) {
            infoLog(
                TAG,
                "Generating is required while android fingerprint \"${metadata.androidFingerprint}\" is updated to \"${Build.FINGERPRINT}\".",
            )
            return true
        }
        if (metadata.state == State.DONE && !contentDirectory.isDirectory) {
            // Content directory missing or corrupted only available if state is skipped or failed.
            warnLog(
                TAG,
                "Generating is required while metadata state is done but content directory is missing or corrupted.",
            )
            return true
        }
        val expectedHash = inputs.inputsHash
        if (metadata.inputsHash != expectedHash) {
            warnLog(
                TAG,
                "Generating is required while inputs are changed, hash \"${metadata.inputsHash}\" not equals to expected \"${expectedHash}\".",
            )
            return true
        }
        return false
    }

    /**
     * Creates a random string as directory name.
     */
    private fun createRandomDirectoryName(): String {
        return buildString {
            SystemClock.elapsedRealtimeNanos()
                .toString(Character.MAX_RADIX)
                .let(::append)
            "-".let(::append)
            Random.nextInt()
                .toString(Character.MAX_RADIX)
                .let(::append)
        }
    }

    /**
     * Strategy to skip generating OAT files if matching specified conditions.
     */
    private enum class SkipGenerateStrategy {
        /**
         * Never skip generating OAT files if OAT files is needed to be generated. The caller will
         * be blocked until OAT files are generated.
         */
        NO,

        /**
         * Skip generating OAT files if existing OAT files is used.
         */
        SKIP_IF_USING,

        /**
         * Never generating OAT files by itself.
         */
        SKIP_IF_MISSING,
    }

    private fun getFilesOrNull(
        directory: File,
        skipGenerateStrategy: SkipGenerateStrategy,
        generateMode: GenerateMode,
    ): Pair<EscapedGuardedContent, File>? {
        val directoryPathHash = directory.pathHash
        val metadataFile = metadataFile(directoryPathHash)
        val inputs = directory.oatInputs
            .takeIf { it.isNotEmpty() }
            ?: run {
                infoLog(
                    TAG,
                    "skip getting OAT files for directory \"${directory.absolutePath}\" due to empty inputs"
                )
                return null
            }
        val inputsHash = inputs.inputsHash
        while (true) {
            // Always check metadata file is exists in the loop. The metadata file may be cleaned by
            // cleaner.
            metadataFile.ensureIsExistingFile()
            val guardedContent = metadataFile.escapedGuardedContentShared
            val originStored = guardedContent.content.let(Metadata::read)
                ?.let { it to contentDirectory(directoryPathHash, it.directoryName) }
            if (!shouldGenerate(inputs, originStored)) {
                // Returns content directory. It may be not exists if metadata state is skipped or
                // failed.
                val result = originStored?.second?.takeIf { it.isDirectory }
                if (result != null) {
                    return Pair(guardedContent, result)
                }
                guardedContent.close()
                return null
            }
            guardedContent.close()
            if (skipGenerateStrategy == SkipGenerateStrategy.SKIP_IF_MISSING) {
                return null
            }
            val contentDirectoryName = createRandomDirectoryName()
            val contentDirectory = contentDirectory(directoryPathHash, contentDirectoryName)
            val generator = when (generateMode) {
                GenerateMode.COMPILE -> compiler
                GenerateMode.INTERPRET -> interpreter
            }
            val (generated, reason) = try {
                generator.generate(context, inputs, contentDirectory) to null
            } catch (throwable: Throwable) {
                false to throwable
            }
            // Compares and sets metadata file.
            val action = action@{ file: RandomAccessFile ->
                file.seek(0)
                val currentMetadata = ByteArray(file.length().toInt())
                    .also(file::read)
                    .let(Metadata::read)
                if (originStored?.first != currentMetadata) {
                    return@action false
                }
                try {
                    Metadata(
                        directoryName = contentDirectoryName,
                        state = if (generated) State.DONE else State.FAILED,
                        generateMode = generateMode,
                        inputsHash = inputsHash
                    ).encoded.let(file::write)
                } catch (throwable: Throwable) {
                    errorLog(
                        TAG,
                        "Stores metadata failed.",
                        throwable,
                    )
                    return@action true
                }
                contentBaseDirectory(directoryPathHash)
                    .listFiles()
                    ?.forEach {
                        if (it.name != contentDirectoryName) {
                            try {
                                it.deleteRecursively()
                            } catch (throwable: Throwable) {
                                errorLog(
                                    TAG,
                                    "Cleans obsolete content directory \"${it.absolutePath}\" failed.",
                                    throwable,
                                )
                            }
                        }
                    }
                return@action !generated
            }
            val hasFailure = if (skipGenerateStrategy == SkipGenerateStrategy.NO) {
                metadataFile.guardedReadOrWriteContent(action)
            } else {
                // Same as `skipGenerateStrategy == SkipGenerateStrategy.SKIP_IF_USING`.
                metadataFile.guardedReadOrWriteContentNullable(action)
            }
            if (hasFailure != false) {
                contentDirectory.deleteRecursively()
                if (hasFailure == null) {
                    return null
                }
                throw TinkerError(
                    ErrorType.GENERATE_OR_STORE_FAILED,
                    "Generates or stores OAT files failed.",
                    reason,
                )
            }
        }
    }

    /**
     * Acquires OAT files for available inputs (like dex files or apk files) in [directory].
     *
     * If set [skipGenerateIfMissing] as true, the function skips generating even if OAT files are not
     * available.
     */
    @Synchronized
    @NonDeployProcessOnly
    override fun acquire(
        directory: File,
        skipGenerateIfMissing: Boolean
    ): File? {
        check(!context.isInDeployProcess) {
            "Cannot acquire OAT files in deploy process"
        }
        expected<ErrorType>("acquire OAT files") {
            val skipGenerateStrategy =
                if (skipGenerateIfMissing) SkipGenerateStrategy.SKIP_IF_MISSING else SkipGenerateStrategy.NO
            val (metadataGuardedContent, result) = getFilesOrNull(
                directory,
                skipGenerateStrategy,
                GenerateMode.INTERPRET
            ) ?: return null
            guardHolder?.let { (_, lastAcquired) ->
                metadataGuardedContent.close()
                throw TinkerError(
                    ErrorType.HAS_ACQUIRED_OAT,
                    "Cannot acquire OAT files while current process has already acquired one",
                    lastAcquired,
                )
            }
            guardHolder = Pair(
                metadataGuardedContent,
                AcquiringRecord(directory),
            )
            return result
        }
    }


    @Synchronized
    @NonDeployProcessOnly
    override fun release() {
        check(!context.isInDeployProcess) {
            "Cannot request OAT files in deploy process"
        }
        expected<ErrorType>("release reference") {
            releaseGuard()
        }
    }

    /**
     * Generates OAT files for available inputs (like dex files or apk files) in [directory] if OAT
     * files are not available.
     */
    @Synchronized
    @DeployProcessOnly
    override fun generateIfNeeded(directory: File, async: Boolean) {
        check(context.isInDeployProcess) {
            "Only available for deploy process"
        }
        expected<ErrorType>("generate OAT files") {
            val action: () -> Unit = lambda@{
                val metadataGuardedContent = getFilesOrNull(
                    directory,
                    SkipGenerateStrategy.SKIP_IF_USING,
                    GenerateMode.COMPILE
                )
                    ?.first
                    ?: return@lambda
                // deploy process does not use OAT files, so we can close the metadata file.
                metadataGuardedContent.close()
            }
            if (async) {
                thread(name = "tinker-oat-generate", block = action)
            } else {
                action.invoke()
            }
        }
    }

    @Synchronized
    @DeployProcessOnly
    override fun clean(directory: File): Boolean {
        check(context.isInDeployProcess) {
            "Only available for deploy process"
        }
        expected<ErrorType>("clean OAT files") {
            val directoryPathHash = directory.pathHash
            val metadataFile = metadataFile(directoryPathHash)
            val contentBaseDirectory = contentBaseDirectory(directoryPathHash)
            if (metadataFile.exists()) {
                return metadataFile
                    .escapedGuardedContentExclusiveNullable(ByteArray(0))
                    ?.use {
                        contentBaseDirectory.deleteRecursively()
                        metadataFile.delete()
                    } != null
            } else {
                // Metadata is missing, but content directory remains.
                contentBaseDirectory.deleteRecursively()
                return true
            }
        }
    }
}
