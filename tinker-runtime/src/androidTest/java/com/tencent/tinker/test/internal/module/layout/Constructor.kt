package com.tencent.tinker.test.internal.module.layout

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.tencent.tinker.internal.module.layout.PatchLayoutConstructorImpl
import com.tencent.tinker.internal.module.layout.patchLayoutConstructErrorTypeOfForTesting
import com.tencent.tinker.internal.patchApkFile
import com.tencent.tinker.internal.patchDexDirectory
import com.tencent.tinker.internal.patchLibraryDirectory
import com.tencent.tinker.internal.patchOatDirectory
import com.tencent.tinker.internal.patchResourceApkFile
import com.tencent.tinker.internal.util.errorCode
import com.tencent.tinker.test.createTestDirectory
import com.tencent.tinker.test.rethrowAsIllegalState
import com.tencent.tinker.test.tinkerErrorCode
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.io.File

@Suppress("unused")
internal class PatchLayoutConstructorDelegate(
    context: Context
) : PatchLayoutConstructorTestService.Delegate {

    private val constructorImpl = PatchLayoutConstructorImpl(context)

    override val processBaseDirectory: File
        get() = rethrowAsIllegalState {
            constructorImpl.processBaseDirectoryForTesting()
        }

    override fun construct(baseDirectory: File, oatDirectory: File): File =
        rethrowAsIllegalState {
            constructorImpl.construct(baseDirectory, oatDirectory)
        }

    override fun assumeProcessIsRestarted() {
        rethrowAsIllegalState {
            constructorImpl.refreshProcessBaseDirectoryForTesting()
        }
    }
}

@RunWith(AndroidJUnit4::class)
class PatchLayoutConstructorImplTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    private fun Context.mainService(): IPatchLayoutConstructorTestMainService =
        Intent(this, PatchLayoutConstructorTestMainService::class.java)
            .let(serviceRule::bindService)
            .let(IPatchLayoutConstructorTestMainService.Stub::asInterface)

    private fun Context.othersService(): IPatchLayoutConstructorTestOthersService =
        Intent(this, PatchLayoutConstructorTestOthersService::class.java)
            .let(serviceRule::bindService)
            .let(IPatchLayoutConstructorTestOthersService.Stub::asInterface)

    private fun Context.patchService(): IPatchLayoutConstructorTestPatchService =
        Intent(this, PatchLayoutConstructorTestPatchService::class.java)
            .let(serviceRule::bindService)
            .let(IPatchLayoutConstructorTestPatchService.Stub::asInterface)

    @Before
    fun cleanUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        mainService.assumeProcessIsRestarted()
        val othersService = context.othersService()
        othersService.assumeProcessIsRestarted()
    }

    private fun assertDifferentLink(
        first: File,
        second: File,
        source: File,
    ) {
        assertNotEquals(first.absolutePath, second.absolutePath)
        assertNotEquals(source.absolutePath, first.absolutePath)
        assertNotEquals(source.absolutePath, second.absolutePath)
        assertEquals(first.canonicalPath, second.canonicalPath)
        assertEquals(source.canonicalPath, first.canonicalPath)
        assertEquals(source.canonicalPath, second.canonicalPath)
    }

    /**
     * Tests if directories constructed by different processes are independent, but content is
     * layout-valid and is linked to same source.
     */
    @Test
    fun constructWithProcessIndependence() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val othersService = context.othersService()

        val baseDirectory = createTestDirectory().apply {
            patchApkFile.createNewFile()
            patchDexDirectory.mkdirs()
            patchLibraryDirectory.mkdirs()
            patchResourceApkFile.createNewFile()
        }
        val oatDirectory = createTestDirectory()

        val constructedByMain = mainService
            .construct(
                baseDirectory.absolutePath,
                oatDirectory.absolutePath,
            )
            .let(::File)
        val constructedByOthers = othersService
            .construct(
                baseDirectory.absolutePath,
                oatDirectory.absolutePath,
            )
            .let(::File)
        assertNotEquals(constructedByMain, constructedByOthers)

        assertNotNull(
            constructedByMain.relativeToOrNull(
                mainService.processBaseDirectory().let(::File)
            )
        )
        assertNotNull(
            constructedByOthers.relativeToOrNull(
                othersService.processBaseDirectory().let(::File)
            )
        )

        assertDifferentLink(
            constructedByMain.patchApkFile,
            constructedByOthers.patchApkFile,
            baseDirectory.patchApkFile,
        )
        assertDifferentLink(
            constructedByMain.patchDexDirectory,
            constructedByOthers.patchDexDirectory,
            baseDirectory.patchDexDirectory,
        )
        assertDifferentLink(
            constructedByMain.patchLibraryDirectory,
            constructedByOthers.patchLibraryDirectory,
            baseDirectory.patchLibraryDirectory,
        )
        assertDifferentLink(
            constructedByMain.patchResourceApkFile,
            constructedByOthers.patchResourceApkFile,
            baseDirectory.patchResourceApkFile,
        )
        assertDifferentLink(
            constructedByMain.patchOatDirectory,
            constructedByOthers.patchOatDirectory,
            oatDirectory,
        )
    }

    /**
     * Test if cleaning content directories works but not removing source files or directories
     * unexpectedly.
     */
    @Test
    fun justCleanSymbolicLink() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()

        val baseDirectory = createTestDirectory()
        val apkFile = baseDirectory
            .patchApkFile
            .apply {
                createNewFile()
            }
        val dexDirectory = baseDirectory
            .patchDexDirectory
            .apply {
                mkdirs()
            }
        val dexFile = dexDirectory
            .resolve("classes.dex")
            .apply {
                createNewFile()
            }
        val libraryDirectory = baseDirectory
            .patchLibraryDirectory
            .apply {
                mkdirs()
            }
        val libraryFile = libraryDirectory
            .resolve("libfoo.so")
            .apply {
                createNewFile()
            }
        val resourceApkFile = baseDirectory
            .patchResourceApkFile
            .apply {
                createNewFile()
            }
        val oatDirectory = createTestDirectory()
        val oatFile = oatDirectory
            .resolve("classes.oat")
            .apply {
                createNewFile()
            }

        val constructed = mainService
            .construct(
                baseDirectory.absolutePath,
                oatDirectory.absolutePath,
            )
            .let(::File)

        mainService.assumeProcessIsRestarted()

        assertFalse(constructed.exists())
        assertTrue(baseDirectory.exists())
        assertTrue(apkFile.exists())
        assertTrue(dexDirectory.exists())
        assertTrue(dexFile.exists())
        assertTrue(libraryDirectory.exists())
        assertTrue(libraryFile.exists())
        assertTrue(resourceApkFile.exists())
        assertTrue(oatDirectory.exists())
        assertTrue(oatFile.exists())
    }

    /**
     * Tests constructing in patch process can raise error expectedly.
     */
    @Test
    fun constructInPatchProcess() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val patchService = context.patchService()
        val baseDirectory = createTestDirectory().apply {
            patchApkFile.createNewFile()
            patchDexDirectory.mkdirs()
            patchLibraryDirectory.mkdirs()
            patchResourceApkFile.createNewFile()
        }
        val oatDirectory = createTestDirectory()
        assertThrows(IllegalStateException::class.java) {
            patchService.invalidConstruct(
                baseDirectory.absolutePath,
                oatDirectory.absolutePath,
            )
        }
    }

    /**
     * Tests constructing with invalid base directory can raise error expectedly.
     */
    @Test
    fun constructWithInvalidBaseDirectory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        // Invalid base directory, should be a directory.
        val baseDirectory = File.createTempFile("tinker-test-", "")
        val oatDirectory = createTestDirectory()
        assertThrows(IllegalStateException::class.java) {
            mainService.construct(
                baseDirectory.absolutePath,
                oatDirectory.absolutePath,
            )
        }
    }

    /**
     * Tests constructing with invalid oat directory can raise error expectedly.
     */
    @Test
    fun constructWithInvalidOatDirectory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val baseDirectory = createTestDirectory().apply {
            patchApkFile.createNewFile()
            patchDexDirectory.mkdirs()
            patchLibraryDirectory.mkdirs()
            patchResourceApkFile.createNewFile()
        }
        // Invalid oat directory, should be a directory.
        val oatDirectory = File.createTempFile("tinker-test-", "")
        assertThrows(IllegalStateException::class.java) {
            mainService.construct(
                baseDirectory.absolutePath,
                oatDirectory.absolutePath,
            )
        }
    }

    /**
     * Tests constructing with invalid apk file can raise error expectedly.
     */
    @Test
    fun constructWithInvalidApkFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val baseDirectory = createTestDirectory().apply {
            // Invalid apk file, should be a file.
            patchApkFile.mkdirs()
            patchDexDirectory.mkdirs()
            patchLibraryDirectory.mkdirs()
            patchResourceApkFile.createNewFile()
        }
        val oatDirectory = createTestDirectory()
        val errorCode = assertThrows(IllegalStateException::class.java) {
            mainService.construct(
                baseDirectory.absolutePath,
                oatDirectory.absolutePath,
            )
        }.tinkerErrorCode
        assertEquals(
            patchLayoutConstructErrorTypeOfForTesting("INVALID_SOURCE").errorCode,
            errorCode,
        )
    }

    /**
     * Tests constructing with invalid dex directory can raise error expectedly.
     */
    @Test
    fun constructWithInvalidDexDirectory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val baseDirectory = createTestDirectory().apply {
            patchApkFile.createNewFile()
            // Invalid dex directory, should be a directory.
            patchDexDirectory.createNewFile()
            patchLibraryDirectory.mkdirs()
            patchResourceApkFile.createNewFile()
        }
        val oatDirectory = createTestDirectory()
        val errorCode = assertThrows(IllegalStateException::class.java) {
            mainService.construct(
                baseDirectory.absolutePath,
                oatDirectory.absolutePath,
            )
        }.tinkerErrorCode
        assertEquals(
            patchLayoutConstructErrorTypeOfForTesting("INVALID_SOURCE").errorCode,
            errorCode,
        )
    }

    /**
     * Tests constructing with invalid library directory can raise error expectedly.
     */
    @Test
    fun constructWithInvalidLibraryDirectory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val baseDirectory = createTestDirectory().apply {
            patchApkFile.createNewFile()
            patchDexDirectory.mkdirs()
            // Invalid library directory, should be a directory.
            patchLibraryDirectory.createNewFile()
            patchResourceApkFile.createNewFile()
        }
        val oatDirectory = createTestDirectory()
        val errorCode = assertThrows(IllegalStateException::class.java) {
            mainService.construct(
                baseDirectory.absolutePath,
                oatDirectory.absolutePath,
            )
        }.tinkerErrorCode
        assertEquals(
            patchLayoutConstructErrorTypeOfForTesting("INVALID_SOURCE").errorCode,
            errorCode,
        )
    }

    /**
     * Tests constructing with invalid resource apk file can raise error expectedly.
     */
    @Test
    fun constructWithInvalidResourceApkFile() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mainService = context.mainService()
        val baseDirectory = createTestDirectory().apply {
            patchApkFile.createNewFile()
            patchDexDirectory.mkdirs()
            patchLibraryDirectory.mkdirs()
            // Invalid resource apk file, should be a file.
            patchResourceApkFile.mkdirs()
        }
        val oatDirectory = createTestDirectory()
        val errorCode = assertThrows(IllegalStateException::class.java) {
            mainService.construct(
                baseDirectory.absolutePath,
                oatDirectory.absolutePath,
            )
        }.tinkerErrorCode
        assertEquals(
            patchLayoutConstructErrorTypeOfForTesting("INVALID_SOURCE").errorCode,
            errorCode,
        )
    }
}