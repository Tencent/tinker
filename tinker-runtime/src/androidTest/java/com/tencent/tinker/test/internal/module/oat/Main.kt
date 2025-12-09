package com.tencent.tinker.test.internal.module.oat

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.tencent.tinker.internal.TinkerPatch
import com.tencent.tinker.internal.module.oat.TinkerOatManager
import com.tencent.tinker.internal.module.fs.dexDirectory
import com.tencent.tinker.internal.module.oat.Generator
import com.tencent.tinker.test.internal.ParcelableTinkerPatch
import com.tencent.tinker.test.casted
import com.tencent.tinker.test.createTestDirectory
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.junit.runner.RunWith
import java.io.File
import java.util.Properties

private val rethrowMessagePattern = "error#(\\d+)#(.*)".toRegex()

private inline fun <T> rethrowAsIllegalState(action: () -> T) =
    try {
        action()
    } catch (error: TinkerOatManager.Error) {
        throw IllegalStateException("error#${error.type.ordinal}#${error.message}", error)
    }

private val IllegalStateException.asError: TinkerOatManager.Error
    get() = message
        ?.let(rethrowMessagePattern::matchEntire)
        ?.let { match ->
            TinkerOatManager.Error(
                match.groupValues[1].toInt().let {
                    TinkerOatManager.Error.Type.entries[it]
                },
                match.groupValues[2],
                cause
            )
        }
        ?: throw AssertionError("Exception is not manager error as expected", this)

@Suppress("unused")
internal class TinkerOatManagerTestServiceDelegateImpl : TinkerOatManagerTestService.Delegate {

    override fun acquire(
        context: Context,
        patch: ParcelableTinkerPatch,
        skipGenerateIfMissing: Boolean
    ): String? = rethrowAsIllegalState {
        TinkerOatManager
            .acquire(
                context,
                TinkerPatch(patch.version, patch.directory),
                skipGenerateIfMissing,
            )
            ?.absolutePath
    }

    override fun generateIfNeeded(
        context: Context,
        patch: ParcelableTinkerPatch
    ) {
        rethrowAsIllegalState {
            TinkerOatManager.generateIfNeeded(context, TinkerPatch(patch.version, patch.directory))
        }
    }

    override fun clean(context: Context, version: String) =
        rethrowAsIllegalState {
            TinkerOatManager.clean(context, version)
        }

    override fun release(context: Context) {
        rethrowAsIllegalState {
            TinkerOatManager.release(context)
        }
    }

    override fun releaseGuard() {
        rethrowAsIllegalState {
            TinkerOatManager.releaseGuardForTesting()
        }
    }

    override fun setMainProcessCompilerAsInvalid() {
        rethrowAsIllegalState {
            TinkerOatManager.setCompilerForTesting(
                TinkerOatManagerTestMainProcessIllegalCompiler
            )
        }
    }

    override fun useSuccessCompilerForPatchProcess() {
        rethrowAsIllegalState {
            TinkerOatManagerTestPatchProcessSuccessCompiler
                .apply { cleanGenerated() }
                .let(TinkerOatManager::setCompilerForTesting)
        }
    }

    override fun setPatchProcessInterpreterAsInvalid() {
        rethrowAsIllegalState {
            TinkerOatManager.setInterpreterForTesting(
                TinkerOatManagerTestPatchProcessIllegalInterpreter
            )
        }
    }

    override fun useSuccessInterpreterForMainProcess() {
        rethrowAsIllegalState {
            TinkerOatManagerTestMainProcessSuccessInterpreter
                .apply { cleanGenerated() }
                .let(TinkerOatManager::setInterpreterForTesting)
        }
    }

    override fun useFailureCompiler() {
        rethrowAsIllegalState {
            TinkerOatManager.setCompilerForTesting(TinkerOatManagerTestFailureCompiler)
        }
    }

    override fun useFailureInterpreter() {
        rethrowAsIllegalState {
            TinkerOatManager.setInterpreterForTesting(TinkerOatManagerTestFailureInterpreter)
        }
    }

    override fun useExceptionCompiler() {
        rethrowAsIllegalState {
            TinkerOatManager.setCompilerForTesting(TinkerOatManagerTestExceptionCompiler)
        }
    }

    override fun useExceptionInterpreter() {
        rethrowAsIllegalState {
            TinkerOatManager.setInterpreterForTesting(TinkerOatManagerTestExceptionInterpreter)
        }
    }

    override fun isCompilerGenerated(): Boolean =
        rethrowAsIllegalState {
            TinkerOatManagerTestPatchProcessSuccessCompiler.generated
        }

    override fun isInterpreterGenerated(): Boolean =
        rethrowAsIllegalState {
            TinkerOatManagerTestMainProcessSuccessInterpreter.generated
        }
}

private sealed class TinkerOatManagerTestSuccessGenerator : Generator() {

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

private object TinkerOatManagerTestPatchProcessSuccessCompiler :
    TinkerOatManagerTestSuccessGenerator()

private object TinkerOatManagerTestMainProcessSuccessInterpreter :
    TinkerOatManagerTestSuccessGenerator()

private sealed class TinkerOatManagerTestIllegalGenerator(
    private val process: String,
    private val type: String
) : Generator() {
    override fun generate(context: Context, inputs: List<File>, outputDirectory: File): Boolean {
        throw AssertionError("$process process should never use $type")
    }
}

private object TinkerOatManagerTestMainProcessIllegalCompiler :
    TinkerOatManagerTestIllegalGenerator("main", "compiler")

private object TinkerOatManagerTestPatchProcessIllegalInterpreter :
    TinkerOatManagerTestIllegalGenerator("patch", "interpreter")

private sealed class TinkerOatManagerTestFailureGenerator : Generator() {
    override fun generate(
        context: Context,
        inputs: List<File>,
        outputDirectory: File
    ): Boolean = false
}

private object TinkerOatManagerTestFailureCompiler : TinkerOatManagerTestFailureGenerator()

private object TinkerOatManagerTestFailureInterpreter : TinkerOatManagerTestFailureGenerator()

private sealed class TinkerOatManagerTestExceptionGenerator : Generator() {

    override fun generate(
        context: Context,
        inputs: List<File>,
        outputDirectory: File
    ): Boolean {
        throw IllegalStateException("Throws as expected")
    }
}

private object TinkerOatManagerTestExceptionCompiler : TinkerOatManagerTestExceptionGenerator()

private object TinkerOatManagerTestExceptionInterpreter : TinkerOatManagerTestExceptionGenerator()

@RunWith(AndroidJUnit4::class)
class TinkerOatManagerTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    private fun Context.mainService(): ITinkerOatManagerTestMainService =
        Intent(this, TinkerOatManagerTestMainService::class.java)
            .let(serviceRule::bindService)
            .let(ITinkerOatManagerTestMainService.Stub::asInterface)

    private fun Context.patchService(): ITinkerOatManagerTestPatchService =
        Intent(this, TinkerOatManagerTestPatchService::class.java)
            .let(serviceRule::bindService)
            .let(ITinkerOatManagerTestPatchService.Stub::asInterface)

    @Before
    fun cleanUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        TinkerOatManager.baseDirectoryForTesting(context).apply {
            walk(direction = FileWalkDirection.TOP_DOWN).forEach {
                it.setWritable(true)
            }
            deleteRecursively()
        }
        val mainService = context.mainService()
        mainService.setCompilerIsInvalid()
        mainService.useSuccessInterpreter()
        mainService.releaseGuard()
        val patchService = context.patchService()
        patchService.setInterpreterIsInvalid()
        patchService.useSuccessCompiler()
    }

    /**
     * Tests if creating OAT files and acquiring OAT files are work expectedly.
     */
    @Test
    fun createAndAcquire() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val patchService = context.patchService()
        val sourceDirectory = createTestDirectory()
        val patch = TinkerPatch("foo", sourceDirectory)
        patch.dexDirectory.apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
            resolve("bar.jar").createNewFile()
            resolve("baz.apk").createNewFile()
            resolve("qux.txt").createNewFile()
        }
        patchService.generateIfNeeded(patch.casted)
        assertTrue(patchService.isCompilerGenerated)
        val acquired = mainService
            .acquire(patch.casted, false)
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
        val baseDirectory = TinkerOatManager.baseDirectoryForTesting(context)
        assertEquals(
            setOf(
                TinkerOatManager.metadataFileForTesting(context, patch.version),
                TinkerOatManager.contentBaseDirectoryForTesting(context, patch.version),
            ),
            baseDirectory.listFiles()?.toSet()
        )
    }

    /**
     * Tests if acquiring OAT files while missing are work expectedly.
     */
    @Test
    fun acquireBySelf() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val sourceDirectory = createTestDirectory()
        val patch = TinkerPatch("foo", sourceDirectory)
        patch.dexDirectory.apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
            resolve("bar.jar").createNewFile()
            resolve("baz.apk").createNewFile()
            resolve("qux.txt").createNewFile()
        }
        val acquired = mainService
            .acquire(patch.casted, false)
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
        val sourceDirectory = createTestDirectory()
        val patch = TinkerPatch("foo", sourceDirectory)
        patch.dexDirectory.apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
            resolve("bar.jar").createNewFile()
            resolve("baz.apk").createNewFile()
            resolve("qux.txt").createNewFile()
        }
        val acquired = mainService
            .acquire(patch.casted, true)
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
        val patchService = context.patchService()
        val sourceDirectory = createTestDirectory()

        val patch = TinkerPatch("foo", sourceDirectory)
        patch.dexDirectory.apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        patchService.generateIfNeeded(patch.casted)
        // Make sure there are existing OAT files.
        val beforeReGenerateContentDirectories =
            TinkerOatManager.contentBaseDirectoryForTesting(context, patch.version)
                .listFiles()
                ?.toList()
                ?: emptyList()
        assertEquals(1, beforeReGenerateContentDirectories.size)

        TinkerOatManager.metadataFileForTesting(context, patch.version).delete()

        val acquired = mainService
            .acquire(patch.casted, false)
            ?.let(::File)
        assertTrue(mainService.isInterpreterGenerated)
        assertNotNull(acquired)
        assertTrue(acquired!!.exists())
        assertTrue(acquired.resolve("foo.dex.oat").exists())
        // Make sure OAT files directory are completely refreshed.
        val afterReGeneratedContentDirectories =
            TinkerOatManager.contentBaseDirectoryForTesting(context, patch.version)
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
        val patchService = context.patchService()
        val sourceDirectory = createTestDirectory()

        val patch = TinkerPatch("foo", sourceDirectory)
        patch.dexDirectory.apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        patchService.generateIfNeeded(patch.casted)
        // Make sure there are existing OAT files.
        val beforeReGenerateContentDirectories =
            TinkerOatManager.contentBaseDirectoryForTesting(context, patch.version)
                .listFiles()
                ?.toList()
                ?: emptyList()
        assertEquals(1, beforeReGenerateContentDirectories.size)

        TinkerOatManager.metadataFileForTesting(context, patch.version)
            .writeText("corrupted")

        val acquired = mainService
            .acquire(patch.casted, false)
            ?.let(::File)
        assertTrue(mainService.isInterpreterGenerated)
        assertNotNull(acquired)
        assertTrue(acquired!!.exists())
        assertTrue(acquired.resolve("foo.dex.oat").exists())
        // Make sure OAT files directory are completely refreshed.
        val afterReGeneratedContentDirectories =
            TinkerOatManager.contentBaseDirectoryForTesting(context, patch.version)
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
        val patchService = context.patchService()
        val sourceDirectory = createTestDirectory()

        val patch = TinkerPatch("foo", sourceDirectory)
        patch.dexDirectory.apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        patchService.generateIfNeeded(patch.casted)

        TinkerOatManager.contentBaseDirectoryForTesting(context, patch.version)
            .listFiles()
            ?.forEach {
                it.deleteRecursively()
            }

        val acquired = mainService
            .acquire(patch.casted, false)
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
        val patchService = context.patchService()
        val sourceDirectory = createTestDirectory()
        val patch = TinkerPatch("foo", sourceDirectory)
        patch.dexDirectory.apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        patchService.generateIfNeeded(patch.casted)
        // Make sure there are existing OAT files.
        val beforeReGenerateContentDirectories =
            TinkerOatManager.contentBaseDirectoryForTesting(context, patch.version)
                .listFiles()
                ?.toList()
                ?: emptyList()
        assertEquals(1, beforeReGenerateContentDirectories.size)

        TinkerOatManager.metadataFileForTesting(context, patch.version).apply {
            val metadata = Properties().also { properties ->
                inputStream().use(properties::load)
            }
            metadata.setProperty("metadata-version", "invalid")
            outputStream().use {
                metadata.store(it, null)
            }
        }

        val acquired = mainService
            .acquire(patch.casted, false)
            ?.let(::File)
        assertTrue(mainService.isInterpreterGenerated)
        assertNotNull(acquired)
        assertTrue(acquired!!.exists())
        assertTrue(acquired.resolve("foo.dex.oat").exists())
        // Make sure OAT files directory are completely refreshed.
        val afterReGeneratedContentDirectories =
            TinkerOatManager.contentBaseDirectoryForTesting(context, patch.version)
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
        val patchService = context.patchService()
        val sourceDirectory = createTestDirectory()
        val patch = TinkerPatch("foo", sourceDirectory)
        patch.dexDirectory.apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        patchService.generateIfNeeded(patch.casted)
        // Make sure there are existing OAT files.
        val beforeReGenerateContentDirectories =
            TinkerOatManager.contentBaseDirectoryForTesting(context, patch.version)
                .listFiles()
                ?.toList()
                ?: emptyList()
        assertEquals(1, beforeReGenerateContentDirectories.size)

        TinkerOatManager.metadataFileForTesting(context, patch.version).apply {
            val metadata = Properties().also { properties ->
                inputStream().use(properties::load)
            }
            metadata.setProperty("android-fingerprint", "invalid")
            outputStream().use {
                metadata.store(it, null)
            }
        }

        val acquired = mainService
            .acquire(patch.casted, false)
            ?.let(::File)
        assertTrue(mainService.isInterpreterGenerated)
        assertNotNull(acquired)
        assertTrue(acquired!!.exists())
        assertTrue(acquired.resolve("foo.dex.oat").exists())
        // Make sure OAT files directory are completely refreshed.
        val afterReGeneratedContentDirectories =
            TinkerOatManager.contentBaseDirectoryForTesting(context, patch.version)
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
        val patchService = context.patchService()
        val sourceDirectory = createTestDirectory()
        val patch = TinkerPatch("foo", sourceDirectory)
        patch.dexDirectory.apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        patchService.generateIfNeeded(patch.casted)
        patch.dexDirectory.apply {
            resolve("bar.dex").createNewFile()
        }

        val acquired = mainService
            .acquire(patch.casted, false)
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
        val patchService = context.patchService()
        val sourceDirectory = createTestDirectory()
        val patch = TinkerPatch("foo", sourceDirectory)
        patch.dexDirectory.apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        val generatedByMain = mainService.acquire(patch.casted, false)
            ?.let(::File)
        assertNotNull(generatedByMain)
        patch.dexDirectory.apply {
            resolve("bar.dex").createNewFile()
        }
        patchService.generateIfNeeded(patch.casted)
        assertFalse(generatedByMain!!.resolve("bar.dex.oat").exists())
    }

    /**
     * Tests if interpreter generates failed while generating in main process is work expectedly.
     */
    @Test
    fun acquireWithFailureInterpreter() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        mainService.useFailureInterpreter()
        val sourceDirectory = createTestDirectory()
        val patch = TinkerPatch("foo", sourceDirectory)
        patch.dexDirectory.apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        val error = assertThrows(IllegalStateException::class.java) {
            mainService.acquire(patch.casted, false)
        }.asError
        assertEquals(TinkerOatManager.Error.Type.GENERATE_OR_STORE_FAILED, error.type)
        // Make sure none of temporary files remains.
        assertEquals(
            setOf(TinkerOatManager.metadataFileForTesting(context, patch.version)),
            TinkerOatManager.baseDirectoryForTesting(context).listFiles()?.toSet(),
        )
    }

    /**
     * Tests if compiler generates failed while generating in patch process is work expectedly.
     */
    @Test
    fun generateWithFailureCompiler() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val patchService = context.patchService()
        patchService.useFailureCompiler()
        val sourceDirectory = createTestDirectory()
        val patch = TinkerPatch("foo", sourceDirectory)
        patch.dexDirectory.apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        val error = assertThrows(IllegalStateException::class.java) {
            patchService.generateIfNeeded(patch.casted)
        }.asError
        assertEquals(TinkerOatManager.Error.Type.GENERATE_OR_STORE_FAILED, error.type)
        // Make sure none of temporary files remains.
        assertEquals(
            setOf(TinkerOatManager.metadataFileForTesting(context, patch.version)),
            TinkerOatManager.baseDirectoryForTesting(context).listFiles()?.toSet(),
        )
    }

    /**
     * Tests if interpreter raises exception while generating in main process is work expectedly.
     */
    @Test
    fun acquireWithExceptionInterpreter() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        mainService.useExceptionInterpreter()
        val sourceDirectory = createTestDirectory()
        val patch = TinkerPatch("foo", sourceDirectory)
        patch.dexDirectory.apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        val error = assertThrows(IllegalStateException::class.java) {
            mainService.acquire(patch.casted, false)
        }.asError
        assertEquals(TinkerOatManager.Error.Type.GENERATE_OR_STORE_FAILED, error.type)
        // Make sure none of temporary files remains.
        assertEquals(
            setOf(TinkerOatManager.metadataFileForTesting(context, patch.version)),
            TinkerOatManager.baseDirectoryForTesting(context).listFiles()?.toSet(),
        )
    }

    /**
     * Tests if compiler raises exception while generating in patch process is work expectedly.
     */
    @Test
    fun generateWithExceptionCompiler() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val patchService = context.patchService()
        patchService.useExceptionCompiler()
        val sourceDirectory = createTestDirectory()
        val patch = TinkerPatch("foo", sourceDirectory)
        patch.dexDirectory.apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        val error = assertThrows(IllegalStateException::class.java) {
            patchService.generateIfNeeded(patch.casted)
        }.asError
        assertEquals(TinkerOatManager.Error.Type.GENERATE_OR_STORE_FAILED, error.type)
        // Make sure none of temporary files remains.
        assertEquals(
            setOf(TinkerOatManager.metadataFileForTesting(context, patch.version)),
            TinkerOatManager.baseDirectoryForTesting(context).listFiles()?.toSet(),
        )
    }

    /**
     * Tests if cleaning is work expectedly.
     */
    @Test
    fun clean() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val patchService = context.patchService()
        val sourceDirectory = createTestDirectory()
        val patch = TinkerPatch("foo", sourceDirectory)
        patch.dexDirectory.apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        patchService.generateIfNeeded(patch.casted)
        // Checks if files generated successfully. It is to make sure files are cleaned after
        // cleaning is because of cleaning, not because of generating failure.
        assertEquals(
            setOf(
                TinkerOatManager.metadataFileForTesting(context, patch.version),
                TinkerOatManager.contentBaseDirectoryForTesting(context, patch.version),
            ),
            TinkerOatManager.baseDirectoryForTesting(context).listFiles()?.toSet(),
        )
        // Make sure none of files remains.
        val cleaned = patchService.clean(patch.version)
        assertTrue(cleaned)
        assertEquals(
            0,
            TinkerOatManager.baseDirectoryForTesting(context).listFiles()?.size ?: 0,
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
        val patchService = context.patchService()
        val sourceDirectory = createTestDirectory()
        val patch = TinkerPatch("foo", sourceDirectory)
        patch.dexDirectory.apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        val generated = mainService.acquire(patch.casted, false)
        assertNotNull(generated)
        // Make sure none of files are cleaned.
        val cleanedWhileUsing = patchService.clean(patch.version)
        assertFalse(cleanedWhileUsing)
        assertEquals(
            setOf(
                TinkerOatManager.metadataFileForTesting(context, patch.version),
                TinkerOatManager.contentBaseDirectoryForTesting(context, patch.version),
            ),
            TinkerOatManager.baseDirectoryForTesting(context).listFiles()?.toSet(),
        )
        // Make sure cleaning works as expectedly after using is released.
        mainService.release()
        val cleanedAfterUsing = patchService.clean(patch.version)
        assertTrue(cleanedAfterUsing)
        assertEquals(
            0,
            TinkerOatManager.baseDirectoryForTesting(context).listFiles()?.size ?: 0,
        )
    }

    /**
     * Tests acquiring OAT files in patch process can raise error expectedly.
     */
    @Test
    fun acquireInPatchProcess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val patchService = context.patchService()
        val sourceDirectory = createTestDirectory()
        val patch = TinkerPatch("foo", sourceDirectory)
        patch.dexDirectory.apply {
            mkdirs()
            resolve("foo.dex").createNewFile()
        }
        patchService.generateIfNeeded(patch.casted)
        assertThrows(IllegalStateException::class.java) {
            patchService.invalidAcquire(patch.casted, false)
        }
    }
}