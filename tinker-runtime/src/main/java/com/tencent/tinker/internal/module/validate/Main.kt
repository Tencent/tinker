package com.tencent.tinker.internal.module.validate

import com.tencent.tinker.internal.TinkerError
import java.io.File

/**
 * Validator for validating content of a directory is modified or not.
 */
internal abstract class Validator {

    /**
     * Creates a validation fingerprint of current content of given [directory] and records it into given [directory].
     */
    abstract fun createValidationFingerprint(directory: File)

    /**
     * Validates fingerprint or content of given [directory] are modified or not. If modified, the function raises
     * a [TinkerError].
     */
    @Throws(TinkerError::class)
    abstract fun validate(directory: File)
}