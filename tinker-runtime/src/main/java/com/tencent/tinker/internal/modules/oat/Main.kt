package com.tencent.tinker.internal.modules.oat

import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import com.tencent.tinker.internal.TinkerPatch
import com.tencent.tinker.internal.annotation.NonPatchProcessOnly
import com.tencent.tinker.internal.annotation.PatchProcessOnly
import com.tencent.tinker.internal.modules.fs.dexDirectory
import com.tencent.tinker.internal.rootDirectory
import com.tencent.tinker.internal.utils.EscapedGuardedContent
import com.tencent.tinker.internal.utils.currentSdk
import com.tencent.tinker.internal.utils.ensureIsExistingFile
import com.tencent.tinker.internal.utils.errorLog
import com.tencent.tinker.internal.utils.escapedGuardedContentExclusiveNullable
import com.tencent.tinker.internal.utils.escapedGuardedContentShared
import com.tencent.tinker.internal.utils.guardedReadOrWriteContent
import com.tencent.tinker.internal.utils.guardedReadOrWriteContentNullable
import com.tencent.tinker.internal.utils.infoLog
import com.tencent.tinker.internal.utils.isInPatchProcess
import com.tencent.tinker.internal.utils.use
import com.tencent.tinker.internal.utils.warnLog
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.Properties
import java.util.zip.CRC32
import kotlin.concurrent.thread
import kotlin.random.Random

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

/**
 * Manager for OAT files.
 */
internal object TinkerOatManager {

    private const val TAG = "Tinker.Oat.Manager"

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
     * An error raised by patch manager.
     */
    class Error(
        val type: Type,
        message: String,
        cause: Throwable?
    ) : Exception(message, cause) {
        enum class Type(val code: Int) {
            HAS_ACQUIRED_OAT(-200),
            GENERATE_OR_STORE_FAILED(-210)
        }
    }

    private var interpreter: Generator = defaultInterpreter

    @VisibleForTesting
    fun setInterpreterForTesting(interpreter: Generator) {
        this.interpreter = interpreter
    }

    private var compiler: Generator = defaultCompiler

    @VisibleForTesting
    fun setCompilerForTesting(compiler: Generator) {
        this.compiler = compiler
    }


    /**
     * Base directory of OAT files.
     */
    private val Context.baseDirectory: File
        get() = rootDirectory.resolve("oat-isolated")

    @VisibleForTesting
    fun baseDirectoryForTesting(context: Context): File =
        context.baseDirectory


    /**
     * File which contains metadata of the patch version.
     *
     * It is also the guard file of OAT files of patch with [version]. If a process uses OAT files,
     * the shared lock of metadata file must be acquired and be held until the process is dead.
     */
    private fun Context.metadataFile(version: String): File =
        baseDirectory.resolve("${version}.metadata")

    @VisibleForTesting
    fun metadataFileForTesting(context: Context, version: String): File =
        context.metadataFile(version)


    /**
     * Directory to store directories which store OAT files of patch with [version].
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
    private fun Context.contentBaseDirectory(version: String): File =
        baseDirectory.resolve(version)

    @VisibleForTesting
    fun contentBaseDirectoryForTesting(context: Context, version: String): File =
        context.contentBaseDirectory(version)

    /**
     * Directory to store OAT files of patch with [version], and its name is [name].
     */
    private fun Context.contentDirectory(version: String, name: String): File =
        contentBaseDirectory(version).resolve(name)


    @GuardedBy("this")
    @NonPatchProcessOnly
    private var guardHolder: Pair<EscapedGuardedContent, AcquiringRecord>? = null

    private class AcquiringRecord(version: String) :
        RuntimeException("Last acquiring with version \"${version}\" is invoked with following stacktrace") {
        init {
            stackTrace = stackTrace
                .dropWhile {
                    it.className == TinkerOatManager.javaClass.name
                }
                .toTypedArray()
        }
    }

    @NonPatchProcessOnly
    private fun releaseGuard() {
        guardHolder?.let { (guardedContent, _) ->
            guardedContent.close()
        }
        guardHolder = null
    }

    @VisibleForTesting
    fun releaseGuardForTesting() {
        releaseGuard()
    }

    private val TinkerPatch.oatInputs: List<File>
        get() = dexDirectory
            .takeIf { it.isDirectory }
            ?.listFiles()
            ?.filter {
                it.name.endsWith(".dex") || it.name.endsWith(".jar") || it.name.endsWith(".apk")
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

    private fun Context.getFilesOrNull(
        patch: TinkerPatch,
        skipGenerateStrategy: SkipGenerateStrategy,
        generateMode: GenerateMode,
    ): Pair<EscapedGuardedContent, File>? {
        val metadataFile = metadataFile(patch.version)
        val inputs = patch.oatInputs
            .takeIf { it.isNotEmpty() }
            ?: run {
                infoLog(
                    TAG,
                    "skip getting OAT files for patch with version \"${patch.version}\" due to empty inputs"
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
                ?.let { it to contentDirectory(patch.version, it.directoryName) }
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
            val contentDirectory = contentDirectory(patch.version, contentDirectoryName)
            val generator = when (generateMode) {
                GenerateMode.COMPILE -> compiler
                GenerateMode.INTERPRET -> interpreter
            }
            val (generated, reason) = try {
                generator.generate(this, inputs, contentDirectory) to null
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
                contentBaseDirectory(patch.version)
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
                throw Error(
                    Error.Type.GENERATE_OR_STORE_FAILED,
                    "Generates or stores OAT files failed.",
                    reason,
                )
            }
        }
    }

    /**
     * Acquires OAT files for provided [patch].
     *
     * If set [skipGenerateIfMissing] as true, the function skips generating even if OAT files are not
     * available.
     */
    @JvmStatic
    @Synchronized
    @NonPatchProcessOnly
    @Throws(Error::class)
    fun acquire(
        context: Context,
        patch: TinkerPatch,
        skipGenerateIfMissing: Boolean = false
    ): File? {
        check(!context.isInPatchProcess) {
            "Cannot acquire OAT files in patch process"
        }
        val skipGenerateStrategy =
            if (skipGenerateIfMissing) SkipGenerateStrategy.SKIP_IF_MISSING else SkipGenerateStrategy.NO
        val (metadataGuardedContent, result) = context.getFilesOrNull(
            patch,
            skipGenerateStrategy,
            GenerateMode.INTERPRET
        ) ?: return null
        guardHolder?.let { (_, lastAcquired) ->
            metadataGuardedContent.close()
            throw Error(
                Error.Type.HAS_ACQUIRED_OAT,
                "Cannot acquire OAT files while current process has already acquired one",
                lastAcquired,
            )
        }
        guardHolder = Pair(
            metadataGuardedContent,
            AcquiringRecord(patch.version),
        )
        return result
    }

    /**
     * Releases OAT files using lock to mark OAT files is not used any more in current process.
     */
    @JvmStatic
    @Synchronized
    @NonPatchProcessOnly
    fun release(context: Context) {
        check(!context.isInPatchProcess) {
            "Cannot request OAT files unavailable in patch process"
        }
        releaseGuard()
    }

    /**
     * Generates OAT files for provided [patch] if OAT files are not available.
     */
    @JvmStatic
    @Synchronized
    @PatchProcessOnly
    @Throws(Error::class)
    fun generateIfNeeded(context: Context, patch: TinkerPatch, async: Boolean = false) {
        check(context.isInPatchProcess) {
            "Only available for patch process"
        }
        val action: () -> Unit = lambda@{
            val metadataGuardedContent = context
                .getFilesOrNull(
                    patch,
                    SkipGenerateStrategy.SKIP_IF_USING,
                    GenerateMode.COMPILE
                )
                ?.first
                ?: return@lambda
            // Patch process does not use OAT files, so we can close the metadata file.
            metadataGuardedContent.close()
        }
        if (async) {
            thread(name = "tinker-oat-generate", block = action)
        } else {
            action.invoke()
        }
    }

    /**
     * Cleans OAT files of patch with [version].
     */
    @JvmStatic
    @Synchronized
    @PatchProcessOnly
    @Throws(Error::class)
    fun clean(context: Context, version: String): Boolean {
        check(context.isInPatchProcess) {
            "Only available for patch process"
        }
        val metadataFile = context.metadataFile(version)
        val contentBaseDirectory = context.contentBaseDirectory(version)
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
