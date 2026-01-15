package com.tencent.tinker.test.internal.module.oat

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.tencent.tinker.internal.module.oat.Generator
import com.tencent.tinker.internal.module.oat.OatManagerImpl
import com.tencent.tinker.internal.module.oat.oatErrorTypeOfForTesting
import com.tencent.tinker.internal.util.errorCode
import com.tencent.tinker.internal.util.isInDeployProcess
import com.tencent.tinker.test.createTestDirectory
import com.tencent.tinker.test.rethrowAsIllegalState
import com.tencent.tinker.test.tinkerErrorCode
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.runner.RunWith
import java.io.File
import java.util.Properties

@Suppress("unused")
internal class OatManagerDelegate(
    context: Context
) : OatManagerTestService.Delegate {

    private var interpreter: Generator = SuccessGenerator

    private var compiler: Generator = SuccessGenerator

    private val managerImpl = OatManagerImpl(
        context = context,
        interpreter = if (context.isInDeployProcess) {
            IllegalGenerator("patch", "interpreter")
        } else {
            object : Generator() {
                override fun generate(
                    context: Context,
                    inputs: List<File>,
                    outputDirectory: File
                ): Boolean = interpreter.generate(context, inputs, outputDirectory)
            }
        },
        compiler = if (!context.isInDeployProcess) {
            IllegalGenerator("main", "compiler")
        } else {
            object : Generator() {
                override fun generate(
                    context: Context,
                    inputs: List<File>,
                    outputDirectory: File
                ): Boolean = compiler.generate(context, inputs, outputDirectory)
            }
        }
    )

    override val baseDirectory: File
        get() = managerImpl.baseDirectoryForTesting()

    override fun metadataFile(directory: File): File =
        managerImpl.metadataFileForTesting(directory)

    override fun contentBaseDirectory(directory: File): File =
        managerImpl.contentBaseDirectoryForTesting(directory)

    override fun acquire(
        directory: File,
        skipGenerateIfMissing: Boolean
    ): File? = rethrowAsIllegalState {
        managerImpl
            .acquire(
                directory,
                skipGenerateIfMissing,
            )
    }

    override fun generateIfNeeded(directory: File) {
        rethrowAsIllegalState {
            managerImpl.generateIfNeeded(directory)
        }
    }

    override fun clean(directory: File): Boolean =
        rethrowAsIllegalState {
            managerImpl.clean(directory)
        }

    override fun release() {
        rethrowAsIllegalState {
            managerImpl.release()
        }
    }

    override fun releaseGuard() {
        rethrowAsIllegalState {
            managerImpl.releaseGuardForTesting()
        }
    }

    override fun reset() {
        rethrowAsIllegalState {
            managerImpl.baseDirectoryForTesting().apply {
                walk(direction = FileWalkDirection.TOP_DOWN).forEach {
                    it.setWritable(true)
                }
                deleteRecursively()
            }
            SuccessGenerator
                .apply { cleanGenerated() }
                .also {
                    interpreter = it
                    compiler = it
                }
        }
    }

    override fun useFailureGenerator() {
        rethrowAsIllegalState {
            FailureGenerator
                .let {
                    interpreter = it
                    compiler = it
                }
        }
    }

    override fun useExceptionGenerator() {
        rethrowAsIllegalState {
            ExceptionGenerator
                .let {
                    interpreter = it
                    compiler = it
                }
        }
    }

    override fun isInterpreterGenerated(): Boolean =
        rethrowAsIllegalState {
            (interpreter as? SuccessGenerator)?.generated == true
        }

    override fun isCompilerGenerated(): Boolean =
        rethrowAsIllegalState {
            (compiler as? SuccessGenerator)?.generated == true
        }
}

private object SuccessGenerator : Generator() {

    var generated = false
        private set

    fun cleanGenerated() {
        generated = false
    }

    override fun generate(
        context: Context,
        inputs: List<File>,
        outputDirectory: File
    ): Boolean {
        generated = true
        inputs.forEach { input ->
            input.copyTo(outputDirectory.resolve("${input.name}.oat"))
        }
        return true
    }
}

private class IllegalGenerator(
    private val process: String,
    private val type: String
) : Generator() {
    override fun generate(
        context: Context,
        inputs: List<File>,
        outputDirectory: File
    ): Boolean {
        throw AssertionError("$process process should never use $type")
    }
}

private object FailureGenerator : Generator() {
    override fun generate(
        context: Context,
        inputs: List<File>,
        outputDirectory: File
    ): Boolean = false
}

private object ExceptionGenerator : Generator() {

    override fun generate(
        context: Context,
        inputs: List<File>,
        outputDirectory: File
    ): Boolean {
        throw IllegalStateException("Throws as expected")
    }
}

@RunWith(AndroidJUnit4::class)
class OatManagerImplTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    private fun Context.mainService(): IOatManagerTestMainService =
        Intent(this, OatManagerTestMainService::class.java)
            .let(serviceRule::bindService)
            .let(IOatManagerTestMainService.Stub::asInterface)

    private fun Context.deployService(): IOatManagerTestDeployService =
        Intent(this, OatManagerTestDeployService::class.java)
            .let(serviceRule::bindService)
            .let(IOatManagerTestDeployService.Stub::asInterface)

    @Before
    fun cleanUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        mainService.reset()
        mainService.releaseGuard()
        val deployService = context.deployService()
        deployService.reset()
    }

    /**
     * Tests if creating OAT files and acquiring OAT files are work expectedly.
     */
    @Test
    fun createAndAcquire() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val deployService = context.deployService()
        val inputDirectory = createTestDirectory().apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
            resolve("bar.jar").createNewFile()
            resolve("baz.apk").createNewFile()
            resolve("qux.txt").createNewFile()
        }
        deployService.generateIfNeeded(inputDirectory.absolutePath)
        assertTrue(deployService.isCompilerGenerated)
        val acquired = mainService
            .acquire(inputDirectory.absolutePath, false)
            ?.let(::File)
        assertFalse(mainService.isInterpreterGenerated)
        assertNotNull(acquired)
        assertTrue(acquired!!.exists())
        // Only dex, jar, apk files are used to generate.
        assertTrue(acquired.resolve("foo.dex.oat").exists())
        assertTrue(acquired.resolve("bar.jar.oat").exists())
        assertTrue(acquired.resolve("baz.apk.oat").exists())
        assertFalse(acquired.resolve("qux.txt.oat").exists())
        // No other files or directories remains in base directory, except metadata and OAT files
        // content directory.
        val baseDirectory = mainService.baseDirectory().let(::File)
        assertEquals(
            setOf(
                mainService.metadataFile(inputDirectory.absolutePath),
                mainService.contentBaseDirectory(inputDirectory.absolutePath),
            ),
            baseDirectory.listFiles()?.map { it.absolutePath }?.toSet(),
        )
    }

    /**
     * Tests if acquiring OAT files while missing are work expectedly.
     */
    @Test
    fun acquireBySelf() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val inputDirectory = createTestDirectory().apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
            resolve("bar.jar").createNewFile()
            resolve("baz.apk").createNewFile()
            resolve("qux.txt").createNewFile()
        }
        val acquired = mainService
            .acquire(inputDirectory.absolutePath, false)
            ?.let(::File)
        assertTrue(mainService.isInterpreterGenerated)
        assertNotNull(acquired)
        assertTrue(acquired!!.exists())
        // Only dex, jar, apk files are used to generate.
        assertTrue(acquired.resolve("foo.dex.oat").exists())
        assertTrue(acquired.resolve("bar.jar.oat").exists())
        assertTrue(acquired.resolve("baz.apk.oat").exists())
        assertFalse(acquired.resolve("qux.txt.oat").exists())
    }

    /**
     * Tests if acquiring OAT files while missing, but skip generating, are work expectedly.
     */
    @Test
    fun acquireBySelfButSkipGenerating() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val inputDirectory = createTestDirectory().apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
            resolve("bar.jar").createNewFile()
            resolve("baz.apk").createNewFile()
            resolve("qux.txt").createNewFile()
        }
        val acquired = mainService
            .acquire(inputDirectory.absolutePath, true)
            ?.let(::File)
        assertFalse(mainService.isInterpreterGenerated)
        assertNull(acquired)
    }

    /**
     * Tests if re-generating while metadata is missing is work expectedly.
     */
    @Test
    fun reGenerateWithMissingMetadata() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val deployService = context.deployService()
        val inputDirectory = createTestDirectory().apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        deployService.generateIfNeeded(inputDirectory.absolutePath)
        // Make sure there are existing OAT files.
        val beforeReGenerateContentDirectories =
            mainService.contentBaseDirectory(inputDirectory.absolutePath).let(::File)
                .listFiles()
                ?.toList()
                ?: emptyList()
        assertEquals(1, beforeReGenerateContentDirectories.size)

        mainService.metadataFile(inputDirectory.absolutePath).let(::File).delete()

        val acquired = mainService
            .acquire(inputDirectory.absolutePath, false)
            ?.let(::File)
        assertTrue(mainService.isInterpreterGenerated)
        assertNotNull(acquired)
        assertTrue(acquired!!.exists())
        assertTrue(acquired.resolve("foo.dex.oat").exists())
        // Make sure OAT files directory are completely refreshed.
        val afterReGeneratedContentDirectories =
            mainService.contentBaseDirectory(inputDirectory.absolutePath).let(::File)
                .listFiles()
                ?.toList()
                ?: emptyList()
        assertEquals(1, afterReGeneratedContentDirectories.size)
        assertNotEquals(
            beforeReGenerateContentDirectories[0].absolutePath,
            afterReGeneratedContentDirectories[0].absolutePath
        )
    }

    /**
     * Tests if re-generating while metadata is corrupted is work expectedly.
     */
    @Test
    fun reGenerateWithCorruptedMetadata() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val deployService = context.deployService()
        val inputDirectory = createTestDirectory().apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        deployService.generateIfNeeded(inputDirectory.absolutePath)
        // Make sure there are existing OAT files.
        val beforeReGenerateContentDirectories =
            mainService.contentBaseDirectory(inputDirectory.absolutePath).let(::File)
                .listFiles()
                ?.toList()
                ?: emptyList()
        assertEquals(1, beforeReGenerateContentDirectories.size)

        mainService.metadataFile(inputDirectory.absolutePath).let(::File)
            .writeText("corrupted")

        val acquired = mainService
            .acquire(inputDirectory.absolutePath, false)
            ?.let(::File)
        assertTrue(mainService.isInterpreterGenerated)
        assertNotNull(acquired)
        assertTrue(acquired!!.exists())
        assertTrue(acquired.resolve("foo.dex.oat").exists())
        // Make sure OAT files directory are completely refreshed.
        val afterReGeneratedContentDirectories =
            mainService.contentBaseDirectory(inputDirectory.absolutePath).let(::File)
                .listFiles()
                ?.toList()
                ?: emptyList()
        assertEquals(1, afterReGeneratedContentDirectories.size)
        assertNotEquals(
            beforeReGenerateContentDirectories[0].absolutePath,
            afterReGeneratedContentDirectories[0].absolutePath
        )
    }

    /**
     * Tests if re-generating while OAT files are missing is work expectedly.
     */
    @Test
    fun reGenerateWithMissingContent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val deployService = context.deployService()
        val inputDirectory = createTestDirectory().apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        deployService.generateIfNeeded(inputDirectory.absolutePath)

        mainService.contentBaseDirectory(inputDirectory.absolutePath).let(::File)
            .listFiles()
            ?.forEach {
                it.deleteRecursively()
            }

        val acquired = mainService
            .acquire(inputDirectory.absolutePath, false)
            ?.let(::File)
        assertTrue(mainService.isInterpreterGenerated)
        assertNotNull(acquired)
        assertTrue(acquired!!.exists())
        assertTrue(acquired.resolve("foo.dex.oat").exists())
    }

    /**
     * Tests if re-generating when metadata version is changed or corrupted is work expectedly.
     */
    @Test
    fun reGenerateWithChangedMetadataVersion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val deployService = context.deployService()
        val inputDirectory = createTestDirectory().apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        deployService.generateIfNeeded(inputDirectory.absolutePath)
        // Make sure there are existing OAT files.
        val beforeReGenerateContentDirectories =
            mainService.contentBaseDirectory(inputDirectory.absolutePath).let(::File)
                .listFiles()
                ?.toList()
                ?: emptyList()
        assertEquals(1, beforeReGenerateContentDirectories.size)

        mainService.metadataFile(inputDirectory.absolutePath).let(::File).apply {
            val metadata = Properties().also { properties ->
                inputStream().use(properties::load)
            }
            metadata.setProperty("metadata-version", "invalid")
            outputStream().use {
                metadata.store(it, null)
            }
        }

        val acquired = mainService
            .acquire(inputDirectory.absolutePath, false)
            ?.let(::File)
        assertTrue(mainService.isInterpreterGenerated)
        assertNotNull(acquired)
        assertTrue(acquired!!.exists())
        assertTrue(acquired.resolve("foo.dex.oat").exists())
        // Make sure OAT files directory are completely refreshed.
        val afterReGeneratedContentDirectories =
            mainService.contentBaseDirectory(inputDirectory.absolutePath).let(::File)
                .listFiles()
                ?.toList()
                ?: emptyList()
        assertEquals(1, afterReGeneratedContentDirectories.size)
        assertNotEquals(
            beforeReGenerateContentDirectories[0].absolutePath,
            afterReGeneratedContentDirectories[0].absolutePath
        )
    }

    /**
     * Tests if re-generating when android fingerprint is changed or corrupted is work expectedly.
     */
    @Test
    fun reGenerateWithChangedAndroidFingerprint() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val deployService = context.deployService()
        val inputDirectory = createTestDirectory().apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        deployService.generateIfNeeded(inputDirectory.absolutePath)
        // Make sure there are existing OAT files.
        val beforeReGenerateContentDirectories =
            mainService.contentBaseDirectory(inputDirectory.absolutePath).let(::File)
                .listFiles()
                ?.toList()
                ?: emptyList()
        assertEquals(1, beforeReGenerateContentDirectories.size)

        mainService.metadataFile(inputDirectory.absolutePath).let(::File).apply {
            val metadata = Properties().also { properties ->
                inputStream().use(properties::load)
            }
            metadata.setProperty("android-fingerprint", "invalid")
            outputStream().use {
                metadata.store(it, null)
            }
        }

        val acquired = mainService
            .acquire(inputDirectory.absolutePath, false)
            ?.let(::File)
        assertTrue(mainService.isInterpreterGenerated)
        assertNotNull(acquired)
        assertTrue(acquired!!.exists())
        assertTrue(acquired.resolve("foo.dex.oat").exists())
        // Make sure OAT files directory are completely refreshed.
        val afterReGeneratedContentDirectories =
            mainService.contentBaseDirectory(inputDirectory.absolutePath).let(::File)
                .listFiles()
                ?.toList()
                ?: emptyList()
        assertEquals(1, afterReGeneratedContentDirectories.size)
        assertNotEquals(
            beforeReGenerateContentDirectories[0].absolutePath,
            afterReGeneratedContentDirectories[0].absolutePath
        )
    }

    /**
     * Tests if re-generating while inputs are updated is work expectedly.
     */
    @Test
    fun reGenerateWithUpdatedInputs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val deployService = context.deployService()
        val inputDirectory = createTestDirectory().apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        deployService.generateIfNeeded(inputDirectory.absolutePath)
        inputDirectory.apply {
            resolve("bar.dex").createNewFile()
        }

        val acquired = mainService
            .acquire(inputDirectory.absolutePath, false)
            ?.let(::File)
        assertTrue(mainService.isInterpreterGenerated)
        assertNotNull(acquired)
        assertTrue(acquired!!.exists())
        assertTrue(acquired.resolve("foo.dex.oat").exists())
        assertTrue(acquired.resolve("bar.dex.oat").exists())
    }

    /**
     * Tests if re-generating is skipped while OAT files are used.
     */
    @Test
    fun skipReGeneratingIfUsing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val deployService = context.deployService()
        val inputDirectory = createTestDirectory().apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        val generatedByMain = mainService.acquire(inputDirectory.absolutePath, false)
            ?.let(::File)
        assertNotNull(generatedByMain)
        inputDirectory.apply {
            resolve("bar.dex").createNewFile()
        }
        deployService.generateIfNeeded(inputDirectory.absolutePath)
        assertFalse(generatedByMain!!.resolve("bar.dex.oat").exists())
    }

    /**
     * Tests if interpreter generates failed while generating in main process is work expectedly.
     */
    @Test
    fun acquireWithFailureInterpreter() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        mainService.useFailureGenerator()
        val inputDirectory = createTestDirectory().apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        val errorCode = assertThrows(IllegalStateException::class.java) {
            mainService.acquire(inputDirectory.absolutePath, false)
        }.tinkerErrorCode
        assertEquals(
            oatErrorTypeOfForTesting("GENERATE_OR_STORE_FAILED").errorCode,
            errorCode
        )
        // Make sure none of temporary files remains.
        assertEquals(
            setOf(mainService.metadataFile(inputDirectory.absolutePath).let(::File)),
            mainService.baseDirectory().let(::File).listFiles()?.toSet(),
        )
    }

    /**
     * Tests if compiler generates failed while generating in deploy process is work expectedly.
     */
    @Test
    fun generateWithFailureCompiler() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val deployService = context.deployService()
        deployService.useFailureGenerator()
        val inputDirectory = createTestDirectory().apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        val errorCode = assertThrows(IllegalStateException::class.java) {
            deployService.generateIfNeeded(inputDirectory.absolutePath)
        }.tinkerErrorCode
        assertEquals(
            oatErrorTypeOfForTesting("GENERATE_OR_STORE_FAILED").errorCode,
            errorCode
        )
        // Make sure none of temporary files remains.
        assertEquals(
            setOf(mainService.metadataFile(inputDirectory.absolutePath).let(::File)),
            mainService.baseDirectory().let(::File).listFiles()?.toSet(),
        )
    }

    /**
     * Tests if interpreter raises exception while generating in main process is work expectedly.
     */
    @Test
    fun acquireWithExceptionInterpreter() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        mainService.useExceptionGenerator()
        val inputDirectory = createTestDirectory().apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        val errorCode = assertThrows(IllegalStateException::class.java) {
            mainService.acquire(inputDirectory.absolutePath, false)
        }.tinkerErrorCode
        assertEquals(
            oatErrorTypeOfForTesting("GENERATE_OR_STORE_FAILED").errorCode,
            errorCode
        )
        // Make sure none of temporary files remains.
        assertEquals(
            setOf(mainService.metadataFile(inputDirectory.absolutePath).let(::File)),
            mainService.baseDirectory().let(::File).listFiles()?.toSet(),
        )
    }

    /**
     * Tests if compiler raises exception while generating in deploy process is work expectedly.
     */
    @Test
    fun generateWithExceptionCompiler() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val deployService = context.deployService()
        deployService.useExceptionGenerator()
        val inputDirectory = createTestDirectory().apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        val errorCode = assertThrows(IllegalStateException::class.java) {
            deployService.generateIfNeeded(inputDirectory.absolutePath)
        }.tinkerErrorCode
        assertEquals(
            oatErrorTypeOfForTesting("GENERATE_OR_STORE_FAILED").errorCode,
            errorCode
        )
        // Make sure none of temporary files remains.
        assertEquals(
            setOf(mainService.metadataFile(inputDirectory.absolutePath).let(::File)),
            mainService.baseDirectory().let(::File).listFiles()?.toSet(),
        )
    }

    /**
     * Tests if cleaning is work expectedly.
     */
    @Test
    fun clean() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val deployService = context.deployService()
        val inputDirectory = createTestDirectory().apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        deployService.generateIfNeeded(inputDirectory.absolutePath)
        // Checks if files generated successfully. It is to make sure files are cleaned after
        // cleaning is because of cleaning, not because of generating failure.
        assertEquals(
            setOf(
                mainService.metadataFile(inputDirectory.absolutePath).let(::File),
                mainService.contentBaseDirectory(inputDirectory.absolutePath).let(::File),
            ),
            mainService.baseDirectory().let(::File).listFiles()?.toSet(),
        )
        // Make sure none of files remains.
        val cleaned = deployService.clean(inputDirectory.absolutePath)
        assertTrue(cleaned)
        assertEquals(
            0,
            mainService.baseDirectory().let(::File).listFiles()?.size ?: 0,
        )
    }

    /**
     * Tests if cleaning is skipped while OAT files are used, and if cleans as expectedly after
     * using is released.
     */
    @Test
    fun cleanAfterUsingIsReleased() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val deployService = context.deployService()
        val inputDirectory = createTestDirectory().apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        val generated = mainService.acquire(inputDirectory.absolutePath, false)
        assertNotNull(generated)
        // Make sure none of files are cleaned.
        val cleanedWhileUsing = deployService.clean(inputDirectory.absolutePath)
        assertFalse(cleanedWhileUsing)
        assertEquals(
            setOf(
                mainService.metadataFile(inputDirectory.absolutePath).let(::File),
                mainService.contentBaseDirectory(inputDirectory.absolutePath).let(::File),
            ),
            mainService.baseDirectory().let(::File).listFiles()?.toSet(),
        )
        // Make sure cleaning works as expectedly after using is released.
        mainService.release()
        val cleanedAfterUsing = deployService.clean(inputDirectory.absolutePath)
        assertTrue(cleanedAfterUsing)
        assertEquals(
            0,
            mainService.baseDirectory().let(::File).listFiles()?.size ?: 0,
        )
    }

    /**
     * Tests acquiring OAT files in  procesps can raise error expectedly.
     */
    @Test
    fun acquireInDeployProcess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val deployService = context.deployService()
        val inputDirectory = createTestDirectory().apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        deployService.generateIfNeeded(inputDirectory.absolutePath)
        assertThrows(IllegalStateException::class.java) {
            deployService.invalidAcquire(inputDirectory.absolutePath, false)
        }
    }
}