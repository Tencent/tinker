package com.tencent.tinker.test

import android.os.Build
import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.util.errorCode
import java.io.File
import java.nio.file.Files

internal fun createTestDirectory(): File =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Files.createTempDirectory("tinker-test-").toFile()
    } else {
        File.createTempFile("tinker-test-", "").apply {
            delete()
            mkdirs()
        }
    }

internal inline fun <T> rethrowAsIllegalState(action: () -> T) =
    try {
        action()
    } catch (error: TinkerError) {
        throw IllegalStateException("error#${error.type.errorCode}", error)
    }

private val rethrowMessagePattern = "error#(\\d+)".toRegex()

internal val IllegalStateException.tinkerErrorCode: Int
    get() = message
        ?.let(rethrowMessagePattern::matchEntire)
        ?.let { match ->
            match.groupValues[1].toInt()
        }
        ?: throw AssertionError("Exception is not tinker error as expected", this)