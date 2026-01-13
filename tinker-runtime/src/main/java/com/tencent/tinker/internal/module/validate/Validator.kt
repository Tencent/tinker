package com.tencent.tinker.internal.module.validate

import androidx.annotation.VisibleForTesting
import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.util.asMd5String
import com.tencent.tinker.internal.util.expected
import java.io.File
import java.security.MessageDigest
import kotlin.io.resolve

private enum class ErrorType : TinkerError.Type {
    UNEXPECTED,
    OPERATE_NON_DIRECTORY,
    INVALID_FINGERPRINT,
    VALIDATE_FAILED;

    override val group: TinkerError.TypeGroup
        get() = TinkerError.TypeGroup.MODULE_VALIDATE

    override val typeCode: Int
        get() = ordinal
}

@VisibleForTesting
internal fun validateErrorTypeOfForTesting(type: String): TinkerError.Type =
    ErrorType.valueOf(type)

internal object ValidatorImpl : Validator() {

    private const val VERIFICATION_FINGERPRINT_FILE_NAME = "verification.fingerprint"

    private val File.validationFingerprintFile: File
        get() = resolve(VERIFICATION_FINGERPRINT_FILE_NAME)

    @VisibleForTesting
    internal fun validationFingerprintFileForTesting(directory: File): File =
        directory.validationFingerprintFile


    private val File.allChildrenSorted: List<Pair<File, String>>
        get() = walk().filter { it.isFile }
            .map { it to it.toRelativeString(this) }
            .filter { it.second != VERIFICATION_FINGERPRINT_FILE_NAME }
            .sortedBy { it.second }
            .toList()

    private val File.fingerprint: ByteArray
        get() {
            val calculator = MessageDigest.getInstance("MD5")
            val children = allChildrenSorted
            children.forEach { (file, relativePath) ->
                // File path.
                calculator.update(relativePath.toByteArray())
                // File content.
                file.inputStream().use { stream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read == -1) {
                            break
                        }
                        calculator.update(buffer, 0, read)
                    }
                }
            }
            return calculator.digest()
        }

    override fun createValidationFingerprint(directory: File) {
        expected<ErrorType>("create validation fingerprint") {
            if (!directory.isDirectory) {
                throw TinkerError(
                    ErrorType.OPERATE_NON_DIRECTORY,
                    "\"${directory.absolutePath}\" is not a directory."
                )
            }
            val fingerprintFile = directory.validationFingerprintFile
                .apply {
                    if (exists()) {
                        delete()
                    }
                }
            directory.fingerprint.let(fingerprintFile::writeBytes)
        }
    }

    override fun validate(directory: File) {
        expected<ErrorType>("validate") {
            if (!directory.isDirectory) {
                throw TinkerError(
                    ErrorType.OPERATE_NON_DIRECTORY,
                    "\"${directory.absolutePath}\" is not a directory."
                )
            }
            val fingerprintFile = directory.validationFingerprintFile
            if (!fingerprintFile.isFile) {
                throw TinkerError(
                    ErrorType.INVALID_FINGERPRINT,
                    "Fingerprint file \"${fingerprintFile.absolutePath}\" is not an existing file."
                )
            }
            val fingerprintFileLength = fingerprintFile.length()
            if (fingerprintFileLength != 16L) {
                throw TinkerError(
                    ErrorType.INVALID_FINGERPRINT,
                    "Length $fingerprintFileLength of fingerprint file \"${fingerprintFile.absolutePath}\" is not same as a MD5 digest."
                )
            }
            val expected = fingerprintFile.readBytes()
            val actual = directory.fingerprint
            if (!expected.contentEquals(actual)) {
                throw TinkerError(
                    ErrorType.VALIDATE_FAILED,
                    "Expected fingerprint of directory \"${directory.absolutePath}\" is \"${expected.asMd5String}\" but actual is \"${actual.asMd5String}\".",
                )
            }
        }
    }
}