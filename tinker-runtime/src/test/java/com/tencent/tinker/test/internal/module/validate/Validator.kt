package com.tencent.tinker.test.internal.module.validate

import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.module.validate.ValidatorImpl
import com.tencent.tinker.internal.module.validate.validateErrorTypeOfForTesting
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
        val error = assertThrows(TinkerError::class.java) {
            ValidatorImpl.createValidationFingerprint(file)
        }
        assertEquals(
            TinkerError.TypeGroup.MODULE_VALIDATE,
            error.type.group,
        )
        assertEquals(
            validateErrorTypeOfForTesting("OPERATE_NON_DIRECTORY"),
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
        val error = assertThrows(TinkerError::class.java) {
            ValidatorImpl.createValidationFingerprint(directory)
        }
        assertEquals(
            TinkerError.TypeGroup.MODULE_VALIDATE,
            error.type.group,
        )
        assertEquals(
            validateErrorTypeOfForTesting("OPERATE_NON_DIRECTORY"),
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
        val errorForAddingFiles = assertThrows(TinkerError::class.java) {
            ValidatorImpl.validate(directoryCopyForAddingFiles)
        }
        assertEquals(
            TinkerError.TypeGroup.MODULE_VALIDATE,
            errorForAddingFiles.type.group,
        )
        assertEquals(
            validateErrorTypeOfForTesting("VALIDATE_FAILED"),
            errorForAddingFiles.type,
        )

        // Remove file will break the validation.
        val directoryCopyForRemovingFiles = Files.createTempDirectory("tinker-test-").toFile()
            .apply {
                directory.copyRecursively(this, true)
                resolve("foo.txt").delete()
            }
        val errorForRemovingFiles = assertThrows(TinkerError::class.java) {
            ValidatorImpl.validate(directoryCopyForRemovingFiles)
        }
        assertEquals(
            TinkerError.TypeGroup.MODULE_VALIDATE,
            errorForRemovingFiles.type.group,
        )
        assertEquals(
            validateErrorTypeOfForTesting("VALIDATE_FAILED"),
            errorForRemovingFiles.type,
        )

        // Modify file will break the validation.
        val directoryCopyForModifyingFiles = Files.createTempDirectory("tinker-test-").toFile()
            .apply {
                directory.copyRecursively(this, true)
                resolve("foo.txt").writeText("Hello qux!")
            }
        val errorForModifyingFiles = assertThrows(TinkerError::class.java) {
            ValidatorImpl.validate(directoryCopyForModifyingFiles)
        }
        assertEquals(
            TinkerError.TypeGroup.MODULE_VALIDATE,
            errorForModifyingFiles.type.group,
        )
        assertEquals(
            validateErrorTypeOfForTesting("VALIDATE_FAILED"),
            errorForModifyingFiles.type,
        )

        // Modify fingerprint file will break the validation.
        val directoryCopyForModifyingFingerprint = Files.createTempDirectory("tinker-test-").toFile()
            .apply {
                directory.copyRecursively(this, true)
                ValidatorImpl.validationFingerprintFileForTesting(this)
                    .writeBytes(ByteArray(16))
            }
        val errorForModifyingFingerprint = assertThrows(TinkerError::class.java) {
            ValidatorImpl.validate(directoryCopyForModifyingFingerprint)
        }
        assertEquals(
            TinkerError.TypeGroup.MODULE_VALIDATE,
            errorForModifyingFingerprint.type.group,
        )
        assertEquals(
            validateErrorTypeOfForTesting("VALIDATE_FAILED"),
            errorForModifyingFingerprint.type,
        )
    }

    /**
     * Tests if validating a file can raise error expectedly.
     */
    @Test
    fun validateWithFile() {
        val file = Files.createTempFile("tinker-test-", ".txt").toFile()
        val error = assertThrows(TinkerError::class.java) {
            ValidatorImpl.validate(file)
        }
        assertEquals(
            TinkerError.TypeGroup.MODULE_VALIDATE,
            error.type.group,
        )
        assertEquals(
            validateErrorTypeOfForTesting("OPERATE_NON_DIRECTORY"),
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
        val error = assertThrows(TinkerError::class.java) {
            ValidatorImpl.validate(directory)
        }
        assertEquals(
            TinkerError.TypeGroup.MODULE_VALIDATE,
            error.type.group,
        )
        assertEquals(
            validateErrorTypeOfForTesting("OPERATE_NON_DIRECTORY"),
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
        val error = assertThrows(TinkerError::class.java) {
            ValidatorImpl.validate(directory)
        }
        assertEquals(
            TinkerError.TypeGroup.MODULE_VALIDATE,
            error.type.group,
        )
        assertEquals(
            validateErrorTypeOfForTesting("INVALID_FINGERPRINT"),
            error.type,
        )
    }
}