package com.tencent.tinker.test.base

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.tencent.tinker.base.TinkerPatch
import com.tencent.tinker.base.TinkerPatchManager

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import java.io.File
import java.nio.file.Files

private val rethrowMessagePattern = "error#(\\d+)#(.*)".toRegex()

private inline fun <T> rethrowAsIllegalState(action: () -> T) =
    try {
        action()
    } catch (error: TinkerPatchManager.Error) {
        throw IllegalStateException("error#${error.type.ordinal}#${error.message}", error)
    }

private val IllegalStateException.asError: TinkerPatchManager.Error
    get() = message
        ?.let(rethrowMessagePattern::matchEntire)
        ?.let { match ->
            TinkerPatchManager.Error(
                match.groupValues[1].toInt().let {
                    TinkerPatchManager.Error.Type.values()[it]
                },
                match.groupValues[2],
                cause
            )
        }
        ?: throw AssertionError("Exception is not manager error as expected", this)

@Suppress("unused")
class TinkerPatchManagerTestServiceDelegateImpl : TinkerPatchManagerTestService.Delegate {

    override var isRequestUnavailableListenerInvoked = false

    init {
        rethrowAsIllegalState {
            TinkerPatchManager.addUnavailableRequestListener {
                isRequestUnavailableListenerInvoked = true
            }
        }
    }

    override fun acquire(context: Context): ParcelableTinkerPatch? =
        rethrowAsIllegalState {
            TinkerPatchManager.acquire(context)?.casted
        }

    override fun requestUnavailable(context: Context, version: String) {
        rethrowAsIllegalState {
            TinkerPatchManager.requestUnavailable(context, version)
        }
    }

    override fun releaseAllEscapedGuardedContent() {
        rethrowAsIllegalState {
            isRequestUnavailableListenerInvoked = false
            TinkerPatchManager.releaseAllHoldersForTesting()
        }
    }

    override fun create(context: Context, version: String, patch: File) {
        rethrowAsIllegalState {
            TinkerPatchManager.create(context, version, patch)
        }
    }

    override fun cleanAll(context: Context): Array<String> =
        rethrowAsIllegalState {
            TinkerPatchManager.cleanAll(context).toTypedArray()
        }

    override fun cleanObsolete(context: Context): Array<String> =
        rethrowAsIllegalState {
            TinkerPatchManager.cleanObsolete(context).toTypedArray()
        }
}

internal val TinkerPatch.casted: ParcelableTinkerPatch
    get() = ParcelableTinkerPatch(version, directory)

internal val ParcelableTinkerPatch.casted: TinkerPatch
    get() = TinkerPatch(version, directory)

@RunWith(AndroidJUnit4::class)
class TinkerPatchManagerTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    private fun Context.mainService(): ITinkerPatchManagerTestMainService =
        Intent(this, TinkerPatchManagerTestMainService::class.java)
            .let(serviceRule::bindService)
            .let(ITinkerPatchManagerTestMainService.Stub::asInterface)

    private fun Context.othersService(): ITinkerPatchManagerTestOthersService =
        Intent(this, TinkerPatchManagerTestOthersService::class.java)
            .let(serviceRule::bindService)
            .let(ITinkerPatchManagerTestOthersService.Stub::asInterface)

    private fun Context.patchService(): ITinkerPatchManagerTestPatchService =
        Intent(this, TinkerPatchManagerTestPatchService::class.java)
            .let(serviceRule::bindService)
            .let(ITinkerPatchManagerTestPatchService.Stub::asInterface)


    @Before
    fun clean() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        TinkerPatchManager.baseDirectoryForTesting(context).apply {
            walk(direction = FileWalkDirection.TOP_DOWN).forEach {
                it.setWritable(true)
            }
            deleteRecursively()
        }
        val mainService = context.mainService()
        val othersService = context.othersService()
        mainService.assumeProcessIsDead()
        othersService.assumeProcessIsDead()
    }

    /**
     * Tests if creating patches and acquiring patches are work expectedly.
     */
    @Test
    fun createAndAcquirePatch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val patchService = context.patchService()
        val version = "foo"
        val plainFileName = "content.txt"
        val plainFileContent = "Hello world!"
        val executableFileName = "executable"
        val sourceDir = Files.createTempDirectory("tinker-test-")
            .toFile()
            .apply {
                resolve(plainFileName).apply {
                    writeText(plainFileContent)
                }
                resolve(executableFileName).apply {
                    createNewFile()
                    setExecutable(true)
                }
            }
        // While creating patch, the manager should create its own copy, instead of just recording
        // the source directory.
        patchService.create(version, sourceDir.absolutePath)
        val patch = mainService.acquire()
        assertNotNull(patch)
        assertEquals(version, patch.version)
        assertNotEquals(sourceDir.absolutePath, patch.directory.absolutePath)
        // The acquired patch directory should not be writable.
        assertTrue(patch.directory.walk().all { !it.canWrite() })
        // The acquired patch directory should has same structure, files content and executable
        // permission as the source directory.
        assertTrue(patch.directory.resolve(plainFileName).isFile)
        assertEquals(plainFileContent, patch.directory.resolve(plainFileName).readText())
        assertTrue(patch.directory.resolve(executableFileName).isFile)
        assertTrue(patch.directory.resolve(executableFileName).canExecute())
    }

    /**
     * Tests if acquiring patch in main process is work expectedly.
     */
    @Test
    fun acquireInMainProcess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val othersService = context.othersService()
        val patchService = context.patchService()
        val fooVersion = "foo"
        val barVersion = "bar"
        // Main process gets nothing if none of patch is created.
        assertNull(mainService.acquire())
        // Creates patch "foo", and lets others process acquires it.
        patchService.create(
            fooVersion,
            Files.createTempDirectory("tinker-test-")
                .toFile()
                .absolutePath
        )
        val patchFromOthers = othersService.acquire()
        assertNotNull(patchFromOthers)
        assertEquals(fooVersion, patchFromOthers.version)
        // Creates patch "bar" as latest version. Main process always get latest version.
        patchService.create(
            barVersion,
            Files.createTempDirectory("tinker-test-")
                .toFile()
                .absolutePath
        )
        val patchFromMain = mainService.acquire()
        assertNotNull(patchFromMain)
        assertEquals(barVersion, patchFromMain.version)
    }

    /**
     * Tests if acquiring patch in others process is work expectedly.
     */
    @Test
    fun acquireInOthersProcess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val othersService = context.othersService()
        val patchService = context.patchService()
        val fooVersion = "foo"
        val barVersion = "bar"
        // Others process gets nothing if none of patch is created.
        assertNull(othersService.acquire())
        // Creates patch "foo", and lets main process acquires it.
        patchService.create(
            fooVersion,
            Files.createTempDirectory("tinker-test-")
                .toFile()
                .absolutePath
        )
        val patchFromMain = mainService.acquire()
        assertNotNull(patchFromMain)
        assertEquals(fooVersion, patchFromMain.version)
        // Creates patch "bar" as latest version. Others process cannot get latest version while
        // main process is alive and using patch "foo".
        patchService.create(
            barVersion,
            Files.createTempDirectory("tinker-test-")
                .toFile()
                .absolutePath
        )
        val patchFromOthers = othersService.acquire()
        assertNotNull(patchFromOthers)
        assertEquals(patchFromMain.version, patchFromOthers.version)
        // Others process can get latest version until main process is dead.
        mainService.assumeProcessIsDead()
        othersService.assumeProcessIsDead()
        val patchFromOthersAfterMainProcessIsDead = othersService.acquire()
        assertNotNull(patchFromOthersAfterMainProcessIsDead)
        assertEquals(barVersion, patchFromOthersAfterMainProcessIsDead.version)
    }

    /**
     * Tests if cleaning obsolete patches is work expectedly, cleaning all unused patches without
     * latest version in this case.
     */
    @Test
    fun cleanObsolete() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val othersService = context.othersService()
        val patchService = context.patchService()
        val fooVersion = "foo"
        val barVersion = "bar"
        val bazVersion = "baz"
        //  patch "foo", and lets others process acquires it.
        patchService.create(
            fooVersion,
            Files.createTempDirectory("tinker-test-")
                .toFile()
                .absolutePath
        )
        val patchFromOthers = othersService.acquire()
        assertNotNull(patchFromOthers)
        assertEquals(fooVersion, patchFromOthers.version)
        // Creates patch "bar", and lets main process acquires it.
        patchService.create(
            barVersion,
            Files.createTempDirectory("tinker-test-")
                .toFile()
                .absolutePath
        )
        val patchFromMain = mainService.acquire()
        assertNotNull(patchFromMain)
        assertEquals(barVersion, patchFromMain.version)
        // Creates patch "baz" as latest version.
        patchService.create(
            bazVersion,
            Files.createTempDirectory("tinker-test-")
                .toFile()
                .absolutePath
        )
        // Cleans all patches. While patches "foo" and "bar" are used, "baz" is latest version, none
        // of them is cleaned.
        val cleaned = patchService.cleanObsolete()
        assertTrue(cleaned.isEmpty())
        assertTrue(TinkerPatchManager.patchDirectoryForTesting(context, fooVersion).exists())
        assertTrue(TinkerPatchManager.patchDirectoryForTesting(context, barVersion).exists())
        assertTrue(TinkerPatchManager.patchDirectoryForTesting(context, bazVersion).exists())
    }

    /**
     * Tests if cleaning obsolete patches is work expectedly. Cleaning unavailable latest version in
     * this case.
     */
    @Test
    fun cleanObsoleteContainsUnavailableLatest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val patchService = context.patchService()
        val expectedVersion = "foo"
        patchService.create(
            expectedVersion,
            Files.createTempDirectory("tinker-test-")
                .toFile()
                .absolutePath
        )
        val patch = mainService.acquire()
        assertNotNull(patch)
        assertEquals(expectedVersion, patch.version)
        assertTrue(patch.directory.exists())
        mainService.requestUnavailable(expectedVersion)
        patchService.cleanObsolete()
        assertFalse(patch.directory.exists())
    }

    /**
     * Tests if manager skips providing unavailable patch.
     */
    @Test
    fun skipProvidingUnavailableIfAcquiring() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val othersService = context.othersService()
        val patchService = context.patchService()
        patchService.create(
            "foo",
            Files.createTempDirectory("tinker-test-")
                .toFile()
                .absolutePath
        )
        // Main process acquires patch and requests it as unavailable. The manager should treats
        // the patch is not used by main process.
        val patch = mainService.acquire()
        assertNotNull(patch)
        mainService.requestUnavailable(patch.version)
        // Others process should get nothing if main process is already requesting latest patch as
        // unavailable.
        assertNull(othersService.acquire())
        // Main process should get nothing if last main process is already requesting latest patch
        // as unavailable.
        assertNull(mainService.acquire())
    }

    /**
     * Tests if patch process skips cleaning unavailable patch if it is still used by other
     * processes. Patch is requested as unavailable in others process while main process is using
     * it without any issues.
     */
    @Test
    fun skipCleaningUnavailableIfUsingByMainProcess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val othersService = context.othersService()
        val patchService = context.patchService()
        patchService.create(
            "foo",
            Files.createTempDirectory("tinker-test-")
                .toFile()
                .absolutePath
        )
        // Main process acquires patch.
        val patchFromMain = mainService.acquire()
        assertNotNull(patchFromMain)
        // Others process acquires patch. The acquired patch should be same as the one which main
        // process is using while main process is still alive.
        val patchFromOthersBeforeCleaning = othersService.acquire()
        assertNotNull(patchFromOthersBeforeCleaning)
        assertEquals(patchFromMain.version, patchFromOthersBeforeCleaning.version)
        assertEquals(patchFromMain.directory, patchFromOthersBeforeCleaning.directory)
        // Patch process should not clean this unavailable patch while main process is still using.
        othersService.requestUnavailable(patchFromOthersBeforeCleaning.version)
        patchService.cleanAll()
        assertTrue(patchFromMain.directory.exists())
        // Others process still gets this unavailable patch while main process is still using.
        // Callers should handle errors caused by unavailable patch by themselves.
        val patchFromOthersAfterCleaning = othersService.acquire()
        assertNotNull(patchFromOthersAfterCleaning)
        assertEquals(patchFromMain.version, patchFromOthersAfterCleaning.version)
        assertEquals(patchFromMain.directory, patchFromOthersAfterCleaning.directory)
        othersService.assumeProcessIsDead()
        // Let main process die.
        mainService.assumeProcessIsDead()
        // Others process should not get this unavailable patch after main process is dead.
        assertNull(othersService.acquire())
        // The unavailable patch should be cleaned correctly while nobody is using it.
        patchService.cleanAll()
        assertFalse(patchFromMain.directory.exists())
    }

    /**
     * Tests if patch process skips cleaning unavailable patch if it is still used by other
     * processes. Patch is requested as unavailable in main process after others process start using
     * it.
     */
    @Test
    fun skipCleaningUnavailableIfUsingByOthersProcess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val othersService = context.othersService()
        val patchService = context.patchService()
        patchService.create(
            "foo",
            Files.createTempDirectory("tinker-test-")
                .toFile()
                .absolutePath
        )
        // Main process acquires patch.
        val patchFromMain = mainService.acquire()
        assertNotNull(patchFromMain)
        // Others process acquire patch.
        val patchFromOthers = othersService.acquire()
        assertNotNull(patchFromOthers)
        assertEquals(patchFromMain.version, patchFromOthers.version)
        // Main process requests patch as unavailable. Patch process should not clean this
        // unavailable patch while others process is still using.
        mainService.requestUnavailable(patchFromMain.version)
        patchService.cleanAll()
        assertTrue(patchFromOthers.directory.exists())
        // Unavailable patches should be cleaned until others process is dead.
        othersService.assumeProcessIsDead()
        patchService.cleanAll()
        assertFalse(patchFromOthers.directory.exists())
    }

    /**
     * Tests requesting unavailable with unavailable version, nothing should happen.
     */
    @Test
    fun requestUnavailableWithNonExistentVersion() {
        ApplicationProvider.getApplicationContext<Context>()
            .mainService()
            .requestUnavailable("foo")
    }

    /**
     * Tests requesting unavailable works expectedly even current process never acquires it.
     */
    @Test
    fun requestUnavailableWithoutAcquiring() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val patchService = context.patchService()
        val version = "foo"
        patchService.create(
            version,
            Files.createTempDirectory("tinker-test-")
                .toFile()
                .absolutePath
        )
        mainService.requestUnavailable(version)
        assertNull(mainService.acquire())
        patchService.cleanAll()
        assertFalse(TinkerPatchManager.patchDirectoryForTesting(context, version).exists())
    }

    /**
     * Tests requesting unavailable works expectedly even if it is not latest version.
     */
    @Test
    fun requestUnavailableNotLatest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val patchService = context.patchService()
        val oldVersion = "old"
        val newVersion = "new"
        patchService.create(
            oldVersion,
            Files.createTempDirectory("tinker-test-")
                .toFile()
                .absolutePath
        )
        patchService.create(
            newVersion,
            Files.createTempDirectory("tinker-test-")
                .toFile()
                .absolutePath
        )
        mainService.requestUnavailable(oldVersion)
        patchService.cleanObsolete()
        // Latest version should be kept.
        val patch = mainService.acquire()
        assertNotNull(patch)
        assertEquals(newVersion, patch.version)
        // Old version should be cleaned.
        assertFalse(TinkerPatchManager.patchDirectoryForTesting(context, oldVersion).exists())
    }

    /**
     * Tests if listener is invoked when same process requests unavailable version.
     */
    @Test
    fun requestUnavailableListenerIsInvoked() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val patchService = context.patchService()
        patchService.create(
            "foo",
            Files.createTempDirectory("tinker-test-")
                .toFile().absolutePath
        )
        val patch = mainService.acquire()
        assertFalse(mainService.isRequestUnavailableListenerInvoked)
        mainService.requestUnavailable(patch.version)
        assertTrue(mainService.isRequestUnavailableListenerInvoked)
    }

    /**
     * Tests acquiring patch in patch process can raise error expectedly.
     */
    @Test
    fun acquireInPatchProcess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val patchService = context.patchService()
        assertThrows(IllegalStateException::class.java) {
            patchService.invalidAcquire()
        }
    }

    /**
     * Tests acquiring patch twice can raise error expectedly. Latest version is not updated between
     * two acquiring in this case.
     */
    @Test
    fun acquireTwiceWithoutUpdatingLatestVersion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val patchService = context.patchService()
        patchService.create(
            "foo",
            Files.createTempDirectory("tinker-test-")
                .toFile().absolutePath
        )
        mainService.acquire()
        val error = assertThrows(IllegalStateException::class.java) {
            mainService.acquire()
        }.asError
        assertEquals(TinkerPatchManager.Error.Type.HAS_ACQUIRED_PATCH, error.type)
    }

    /**
     * Tests acquiring patch twice can raise error expectedly. Latest version is updated between two
     * acquiring in this case.
     */
    @Test
    fun acquireTwiceWithUpdatingLatestVersion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val patchService = context.patchService()
        patchService.create(
            "foo",
            Files.createTempDirectory("tinker-test-")
                .toFile().absolutePath
        )
        mainService.acquire()
        patchService.create(
            "bar",
            Files.createTempDirectory("tinker-test-")
                .toFile().absolutePath
        )
        val error = assertThrows(IllegalStateException::class.java) {
            mainService.acquire()
        }.asError
        assertEquals(TinkerPatchManager.Error.Type.HAS_ACQUIRED_PATCH, error.type)
    }

    /**
     * Tests requesting unavailable in patch process can raise error expectedly.
     */
    @Test
    fun requestUnavailableInPatchProcess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val patchService = context.patchService()
        assertThrows(IllegalStateException::class.java) {
            patchService.invalidRequestUnavailable("foo")
        }
    }

    /**
     * Tests creating new patch in non-patch process can raise error expectedly.
     */
    @Test
    fun createInNonPatchProcess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertThrows(IllegalStateException::class.java) {
            TinkerPatchManager.create(
                context,
                "foo",
                Files.createTempDirectory("tinker-test-")
                    .toFile()
            )
        }
    }

    /**
     * Tests clean all patches in non-patch process can raise error expectedly.
     */
    @Test
    fun cleanAllInNonPatchProcess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertThrows(IllegalStateException::class.java) {
            TinkerPatchManager.cleanAll(context)
        }
    }

    /**
     * Tests clean obsolete patches in non-patch process can raise error expectedly.
     */
    @Test
    fun cleanObsoleteInNonPatchProcess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertThrows(IllegalStateException::class.java) {
            TinkerPatchManager.cleanObsolete(context)
        }
    }

    /**
     * Tests creating new patch with version has reserved prefix "#" can raise error expectedly.
     */
    @Test
    fun createWithIllegalVersion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val patchService = context.patchService()
        assertThrows(IllegalArgumentException::class.java) {
            patchService.create(
                "#foo",
                Files.createTempDirectory("tinker-test-")
                    .toFile()
                    .absolutePath
            )
        }
    }

    /**
     * Tests creating new patch with non-existent patch directory can raise error expectedly.
     */
    @Test
    fun createWithNonExistentPatch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val patchService = context.patchService()
        assertThrows(IllegalArgumentException::class.java) {
            patchService.create(
                "foo",
                Files.createTempDirectory("tinker-test-")
                    .toFile()
                    .apply { deleteRecursively() }
                    .absolutePath
            )
        }
    }

    /**
     * Tests creating new patch but provided patch directory path is not a directory, is a file in
     * this case, can raise error expectedly.
     */
    @Test
    fun createWithNotDirectoryPatch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val patchService = context.patchService()
        assertThrows(IllegalArgumentException::class.java) {
            patchService.create(
                "foo",
                Files.createTempFile("tinker-test-", ".txt")
                    .toFile()
                    .apply {
                        createNewFile()
                    }
                    .absolutePath
            )
        }
    }

    /**
     * Tests creating new patch with version already exists can raise error expectedly.
     */
    @Test
    fun createWithDuplicateVersion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val patchService = context.patchService()
        patchService.create(
            "foo",
            Files.createTempDirectory("tinker-test-")
                .toFile()
                .absolutePath
        )
        val error =
            assertThrows(IllegalStateException::class.java) {
                patchService.create(
                    "foo",
                    Files.createTempDirectory("tinker-test-")
                        .toFile()
                        .absolutePath
                )
            }?.asError
        assertNotNull(error)
        assertEquals(
            TinkerPatchManager.Error.Type.CREATE_EXIST_PATCH,
            error!!.type
        )
    }

    /**
     * Tests creating new patch with invalid patch directory, a non-readable directory in this case,
     * can raise error expectedly.
     */
    @Test
    fun createWithInvalidPatch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val patchService = context.patchService()
        val sourceDir = Files.createTempDirectory("tinker-test-")
            .toFile()
            .apply {
                resolve("content.txt").apply {
                    writeText("Hello world!")
                    setReadable(false)
                }
                setReadable(false)
            }
        val error =
            assertThrows(IllegalStateException::class.java) {
                patchService.create(
                    "not-readable",
                    sourceDir.absolutePath,
                )
            }.asError
        assertEquals(
            TinkerPatchManager.Error.Type.CLONE_PATCH,
            error.type
        )
        // Cleans up the source directory.
        sourceDir.walk(direction = FileWalkDirection.TOP_DOWN).forEach {
            it.setReadable(true)
        }
        sourceDir.deleteRecursively()
    }

    /**
     * Tests if latest version is corrupted can raise fail-fast error expectedly.
     */
    @Test
    fun latestVersionIsCorrupted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        TinkerPatchManager.latestVersionFileForTesting(context)
            .apply {
                parentFile?.mkdirs()
                createNewFile()
                setWritable(false)
            }
        val patchService = context.patchService()
        val error =
            assertThrows(IllegalStateException::class.java) {
                patchService.create(
                    "foo",
                    Files.createTempDirectory("tinker-test-")
                        .toFile()
                        .absolutePath
                )
            }.asError
        assertEquals(TinkerPatchManager.Error.Type.WRITE_LATEST_VERSION, error.type)
    }
}