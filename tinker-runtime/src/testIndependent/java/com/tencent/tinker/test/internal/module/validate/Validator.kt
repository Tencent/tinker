package com.tencent.tinker.test.internal.module.validate

import com.tencent.tinker.Tinker
import com.tencent.tinker.internal.module.validate.ValidatorImpl
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class ValidateTest {

    /**
     * Tests if creating a validation fingerprint works expectedly.
     */
    @Test
    fun createValidationFingerprint() {
        val directory = Files.createTempDirectory("tinker-test-").toFile()
            .apply {
                resolve("foo.txt").writeText("Hello foo!")
                resolve("bar").apply {
                    mkdirs()
                    resolve("baz.txt").writeBytes("Hello baz!".toByteArray())
                }
            }
        val expected = MessageDigest.getInstance("MD5")
            .apply {
                arrayOf("bar", "baz.txt")
                    .joinToString(File.separator)
                    .toByteArray()
                    .let(::update)
                "Hello baz!".toByteArray()
                    .let(::update)
                arrayOf("foo.txt")
                    .joinToString(File.separator)
                    .toByteArray()
                    .let(::update)
                "Hello foo!".toByteArray()
                    .let(::update)
            }
            .digest()
        ValidatorImpl.createValidationFingerprint(directory)
        val actual = ValidatorImpl.validationFingerprintFileForTesting(directory).readBytes()
        assertArrayEquals(expected, actual)
    }

    /**
     * Tests if creating a validation fingerprint for a file can raise error expectedly.
     */
    @Test
    fun createValidationFingerprintWithFile() {
        val file = Files.createTempFile("tinker-test-", ".txt").toFile()
        val error = assertThrows(Tinker.Error::class.java) {
            ValidatorImpl.createValidationFingerprint(file)
        }
        assertEquals(
            Tinker.Error.Validate.OPERATE_NON_DIRECTORY,
            error.type,
        )
    }

    /**
     * Tests if creating a validation fingerprint for a non-existing directory can raise error expectedly.
     */
    @Test
    fun createValidationFingerprintWithMissingDirectory() {
        val directory = Files.createTempDirectory("tinker-test-").toFile()
            .apply {
                deleteRecursively()
            }
        val error = assertThrows(Tinker.Error::class.java) {
            ValidatorImpl.createValidationFingerprint(directory)
        }
        assertEquals(
            Tinker.Error.Validate.OPERATE_NON_DIRECTORY,
            error.type,
        )
    }

    /**
     * Tests if validating a directory works expectedly.
     */
    @Test
    fun validate() {
        val directory = Files.createTempDirectory("tinker-test-").toFile()
            .apply {
                resolve("foo.txt").writeText("Hello foo!")
                resolve("bar").apply {
                    mkdirs()
                    resolve("baz.txt").writeBytes("Hello baz!".toByteArray())
                }
            }
        val fingerprintFile = ValidatorImpl.validationFingerprintFileForTesting(directory)
        MessageDigest.getInstance("MD5")
            .apply {
                arrayOf("bar", "baz.txt")
                    .joinToString(File.separator)
                    .toByteArray()
                    .let(::update)
                "Hello baz!".toByteArray()
                    .let(::update)
                arrayOf("foo.txt")
                    .joinToString(File.separator)
                    .toByteArray()
                    .let(::update)
                "Hello foo!".toByteArray()
                    .let(::update)
            }
            .digest()
            .let(fingerprintFile::writeBytes)

        // Make sure non of throwable is thrown.
        ValidatorImpl.validate(directory)

        // Copies will not break the validation.
        val directoryCopy = Files.createTempDirectory("tinker-test-").toFile()
            .apply {
                directory.copyRecursively(this, true)
            }
        ValidatorImpl.validate(directoryCopy)

        // Creates new file will break the validation.
        val directoryCopyForAddingFiles = Files.createTempDirectory("tinker-test-").toFile()
            .apply {
                directory.copyRecursively(this, true)
                resolve("new.txt").writeText("Hello new!")
            }
        val errorForAddingFiles = assertThrows(Tinker.Error::class.java) {
            ValidatorImpl.validate(directoryCopyForAddingFiles)
        }
        assertEquals(
            Tinker.Error.Validate.VALIDATE_FAILED,
            errorForAddingFiles.type,
        )

        // Remove file will break the validation.
        val directoryCopyForRemovingFiles = Files.createTempDirectory("tinker-test-").toFile()
            .apply {
                directory.copyRecursively(this, true)
                resolve("foo.txt").delete()
            }
        val errorForRemovingFiles = assertThrows(Tinker.Error::class.java) {
            ValidatorImpl.validate(directoryCopyForRemovingFiles)
        }
        assertEquals(
            Tinker.Error.Validate.VALIDATE_FAILED,
            errorForRemovingFiles.type,
        )

        // Modify file will break the validation.
        val directoryCopyForModifyingFiles = Files.createTempDirectory("tinker-test-").toFile()
            .apply {
                directory.copyRecursively(this, true)
                resolve("foo.txt").writeText("Hello qux!")
            }
        val errorForModifyingFiles = assertThrows(Tinker.Error::class.java) {
            ValidatorImpl.validate(directoryCopyForModifyingFiles)
        }
        assertEquals(
            Tinker.Error.Validate.VALIDATE_FAILED,
            errorForModifyingFiles.type,
        )

        // Modify fingerprint file will break the validation.
        val directoryCopyForModifyingFingerprint = Files.createTempDirectory("tinker-test-").toFile()
            .apply {
                directory.copyRecursively(this, true)
                ValidatorImpl.validationFingerprintFileForTesting(this)
                    .writeBytes(ByteArray(16))
            }
        val errorForModifyingFingerprint = assertThrows(Tinker.Error::class.java) {
            ValidatorImpl.validate(directoryCopyForModifyingFingerprint)
        }
        assertEquals(
            Tinker.Error.Validate.VALIDATE_FAILED,
            errorForModifyingFingerprint.type,
        )
    }

    /**
     * Tests if validating a file can raise error expectedly.
     */
    @Test
    fun validateWithFile() {
        val file = Files.createTempFile("tinker-test-", ".txt").toFile()
        val error = assertThrows(Tinker.Error::class.java) {
            ValidatorImpl.validate(file)
        }
        assertEquals(
            Tinker.Error.Validate.OPERATE_NON_DIRECTORY,
            error.type,
        )
    }

    /**
     * Tests if validating a non-existing directory can raise error expectedly.
     */
    @Test
    fun validateWithMissingDirectory() {
        val directory = Files.createTempDirectory("tinker-test-").toFile()
            .apply {
                deleteRecursively()
            }
        val error = assertThrows(Tinker.Error::class.java) {
            ValidatorImpl.validate(directory)
        }
        assertEquals(
            Tinker.Error.Validate.OPERATE_NON_DIRECTORY,
            error.type,
        )
    }

    /**
     * Tests if validating a directory with broken fingerprint can raise error expectedly.
     */
    @Test
    fun validateWithBrokenFingerprint() {
        val directory = Files.createTempDirectory("tinker-test-").toFile()
            .apply {
                ValidatorImpl.validationFingerprintFileForTesting(this)
                    .writeBytes(ByteArray(12))
            }
        val error = assertThrows(Tinker.Error::class.java) {
            ValidatorImpl.validate(directory)
        }
        assertEquals(
            Tinker.Error.Validate.INVALID_FINGERPRINT,
            error.type,
        )
    }
}