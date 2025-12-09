package com.tencent.tinker.test.internal.module.patch

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.tencent.tinker.internal.module.patch.RawPatchManager
import com.tencent.tinker.internal.module.patch.RawPatchManagerImpl
import com.tencent.tinker.test.casted
import com.tencent.tinker.test.createTestDirectory

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import java.io.File

private val rethrowMessagePattern = "error#(\\d+)#(.*)".toRegex()

private inline fun <T> rethrowAsIllegalState(action: () -> T) =
    try {
        action()
    } catch (error: RawPatchManager.Error) {
        throw IllegalStateException("error#${error.type.ordinal}#${error.message}", error)
    }

private val IllegalStateException.asError: RawPatchManager.Error
    get() = message
        ?.let(rethrowMessagePattern::matchEntire)
        ?.let { match ->
            RawPatchManager.Error(
                match.groupValues[1].toInt().let {
                    RawPatchManager.Error.Type.entries[it]
                },
                match.groupValues[2],
                cause
            )
        }
        ?: throw AssertionError("Exception is not manager error as expected", this)

@Suppress("unused")
internal class PatchManagerDelegate(
    context: Context
) : PatchManagerTestService.Delegate {

    private val managerImpl = RawPatchManagerImpl(context)

    override var isRequestUnavailableListenerInvoked = false

    init {
        rethrowAsIllegalState {
            managerImpl.addUnavailableRequestListener {
                isRequestUnavailableListenerInvoked = true
            }
        }
    }

    override val baseDirectory: File
        get() = managerImpl.baseDirectoryForTesting()

    override val latestVersionFile: File
        get() = managerImpl.latestVersionFileForTesting()

    override fun patchDirectory(version: String): File =
        managerImpl.patchDirectoryForTesting(version)

    override fun acquire(): ParcelableRawPatch? =
        rethrowAsIllegalState {
            managerImpl.acquire()?.casted
        }

    override fun requestUnavailable(version: String) {
        rethrowAsIllegalState {
            managerImpl.requestUnavailable(version)
        }
    }

    override fun releaseAllEscapedGuardedContent() {
        rethrowAsIllegalState {
            isRequestUnavailableListenerInvoked = false
            managerImpl.releaseAllHoldersForTesting()
        }
    }

    override fun create(version: String, patch: File) {
        rethrowAsIllegalState {
            managerImpl.create(version, patch)
        }
    }

    override fun cleanAll(): Array<String> =
        rethrowAsIllegalState {
            managerImpl.cleanAll().toTypedArray()
        }

    override fun cleanObsolete(): Array<String> =
        rethrowAsIllegalState {
            managerImpl.cleanObsolete().toTypedArray()
        }
}

@RunWith(AndroidJUnit4::class)
class TinkerPatchManagerTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    private fun Context.mainService(): IPatchManagerTestMainService =
        Intent(this, PatchManagerTestMainService::class.java)
            .let(serviceRule::bindService)
            .let(IPatchManagerTestMainService.Stub::asInterface)

    private fun Context.othersService(): IPatchManagerTestOthersService =
        Intent(this, PatchManagerTestOthersService::class.java)
            .let(serviceRule::bindService)
            .let(IPatchManagerTestOthersService.Stub::asInterface)

    private fun Context.patchService(): IPatchManagerTestPatchService =
        Intent(this, PatchManagerTestPatchService::class.java)
            .let(serviceRule::bindService)
            .let(IPatchManagerTestPatchService.Stub::asInterface)


    @Before
    fun clean() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val othersService = context.othersService()
        mainService.baseDirectory().let(::File).apply {
            walk(direction = FileWalkDirection.TOP_DOWN).forEach {
                it.setWritable(true)
            }
            deleteRecursively()
        }
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
        val sourceDir = createTestDirectory()
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
            createTestDirectory()
                .absolutePath
        )
        val patchFromOthers = othersService.acquire()
        assertNotNull(patchFromOthers)
        assertEquals(fooVersion, patchFromOthers.version)
        // Creates patch "bar" as latest version. Main process always get latest version.
        patchService.create(
            barVersion,
            createTestDirectory()
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
            createTestDirectory()
                .absolutePath
        )
        val patchFromMain = mainService.acquire()
        assertNotNull(patchFromMain)
        assertEquals(fooVersion, patchFromMain.version)
        // Creates patch "bar" as latest version. Others process cannot get latest version while
        // main process is alive and using patch "foo".
        patchService.create(
            barVersion,
            createTestDirectory()
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
            createTestDirectory()
                .absolutePath
        )
        val patchFromOthers = othersService.acquire()
        assertNotNull(patchFromOthers)
        assertEquals(fooVersion, patchFromOthers.version)
        // Creates patch "bar", and lets main process acquires it.
        patchService.create(
            barVersion,
            createTestDirectory()
                .absolutePath
        )
        val patchFromMain = mainService.acquire()
        assertNotNull(patchFromMain)
        assertEquals(barVersion, patchFromMain.version)
        // Creates patch "baz" as latest version.
        patchService.create(
            bazVersion,
            createTestDirectory()
                .absolutePath
        )
        // Cleans all patches. While patches "foo" and "bar" are used, "baz" is latest version, none
        // of them is cleaned.
        val cleaned = patchService.cleanObsolete()
        assertTrue(cleaned.isEmpty())
        assertTrue(mainService.patchDirectory(barVersion).let(::File).exists())
        assertTrue(mainService.patchDirectory(barVersion).let(::File).exists())
        assertTrue(mainService.patchDirectory(bazVersion).let(::File).exists())
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
            createTestDirectory()
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
            createTestDirectory()
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
            createTestDirectory()
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
            createTestDirectory()
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
            createTestDirectory()
                .absolutePath
        )
        mainService.requestUnavailable(version)
        assertNull(mainService.acquire())
        patchService.cleanAll()
        assertFalse(mainService.patchDirectory(version).let(::File).exists())
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
            createTestDirectory()
                .absolutePath
        )
        patchService.create(
            newVersion,
            createTestDirectory()
                .absolutePath
        )
        mainService.requestUnavailable(oldVersion)
        patchService.cleanObsolete()
        // Latest version should be kept.
        val patch = mainService.acquire()
        assertNotNull(patch)
        assertEquals(newVersion, patch.version)
        // Old version should be cleaned.
        assertFalse(mainService.patchDirectory(oldVersion).let(::File).exists())
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
            createTestDirectory().absolutePath
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
            createTestDirectory().absolutePath
        )
        mainService.acquire()
        val error = assertThrows(IllegalStateException::class.java) {
            mainService.acquire()
        }.asError
        assertEquals(RawPatchManager.Error.Type.HAS_ACQUIRED_PATCH, error.type)
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
            createTestDirectory().absolutePath
        )
        mainService.acquire()
        patchService.create(
            "bar",
            createTestDirectory().absolutePath
        )
        val error = assertThrows(IllegalStateException::class.java) {
            mainService.acquire()
        }.asError
        assertEquals(RawPatchManager.Error.Type.HAS_ACQUIRED_PATCH, error.type)
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
        val mainService = context.mainService()
        assertThrows(IllegalStateException::class.java) {
            mainService.invalidCreate(
                "foo",
                createTestDirectory().absolutePath
            )
        }
    }

    /**
     * Tests clean all patches in non-patch process can raise error expectedly.
     */
    @Test
    fun cleanAllInNonPatchProcess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        assertThrows(IllegalStateException::class.java) {
            mainService.invalidCleanAll()
        }
    }

    /**
     * Tests clean obsolete patches in non-patch process can raise error expectedly.
     */
    @Test
    fun cleanObsoleteInNonPatchProcess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        assertThrows(IllegalStateException::class.java) {
            mainService.invalidCleanObsolete()
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
                createTestDirectory()
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
                createTestDirectory()
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
                File.createTempFile("tinker-test-", ".txt").absolutePath
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
            createTestDirectory()
                .absolutePath
        )
        val error =
            assertThrows(IllegalStateException::class.java) {
                patchService.create(
                    "foo",
                    createTestDirectory()
                        .absolutePath
                )
            }?.asError
        assertNotNull(error)
        assertEquals(
            RawPatchManager.Error.Type.CREATE_EXIST_PATCH,
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
        val sourceDir = createTestDirectory()
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
            RawPatchManager.Error.Type.CLONE_PATCH,
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
        val mainService = context.mainService()
        mainService.latestVersionFile().let(::File)
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
                    createTestDirectory()
                        .absolutePath
                )
            }.asError
        assertEquals(RawPatchManager.Error.Type.WRITE_LATEST_VERSION, error.type)
    }
}