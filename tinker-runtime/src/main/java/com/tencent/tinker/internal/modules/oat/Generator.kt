package com.tencent.tinker.internal.modules.oat

import android.content.Context
import android.os.Build
import com.tencent.tinker.internal.utils.arkHotRunning
import com.tencent.tinker.internal.utils.currentInstructionSet
import com.tencent.tinker.internal.utils.currentSdk
import com.tencent.tinker.internal.utils.ensureIsExistingDirectory
import com.tencent.tinker.internal.utils.errorLog
import com.tencent.tinker.internal.utils.isReadableNonEmptyFile
import com.tencent.tinker.internal.utils.warnLog
import dalvik.system.DexFile
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors

private fun File.odexOutputOf(input: File): File =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        resolve(currentInstructionSet).resolve("${input.nameWithoutExtension}.odex")
    } else {
        resolve("${input.nameWithoutExtension}.dex")
    }

internal abstract class Generator {
    abstract fun generate(context: Context, inputs: List<File>, outputDirectory: File): Boolean
}

internal object EmptyGenerator : Generator() {
    override fun generate(
        context: Context,
        inputs: List<File>,
        outputDirectory: File
    ): Boolean = true
}

internal sealed class DefaultGenerator : Generator() {

    companion object {
        private const val TAG = "Tinker.Oat.Generator"

        private val specialManufacturers = setOf("vivo", "oppo", "meizu")
    }

    private val missingOutputAccepted by lazy {
        Build.MANUFACTURER.lowercase() in specialManufacturers || currentSdk >= Build.VERSION_CODES.Q || arkHotRunning
    }

    abstract fun generate(
        context: Context,
        input: File,
        output: File,
    ): Boolean

    private fun generatePerFile(
        context: Context,
        input: File,
        outputDirectory: File,
    ): Boolean {
        val output = outputDirectory.odexOutputOf(input)
        val generated = try {
            generate(context, input, output)
        } catch (throwable: Throwable) {
            errorLog(TAG, "Generates OAT file failed with throwable.", throwable)
            return false
        }
        if (!generated) {
            errorLog(TAG, "Generates OAT file failed with generator error.")
            return false
        }
        if (!output.isReadableNonEmptyFile) {
            warnLog(TAG, "Generates OAT file output is missing, not readable or empty.")
            if (!missingOutputAccepted) {
                errorLog(TAG, "Generates OAT file failed with illegal output.")
                return false
            }
        }
        return true
    }

    override fun generate(
        context: Context,
        inputs: List<File>,
        outputDirectory: File
    ): Boolean {
        val executor = Executors.newCachedThreadPool {
            Thread(it, "tinker-oat-generate")
        }
        val futures = inputs.sortedByDescending { it.length() }
            .map { input ->
                return@map executor.submit(
                    Callable {
                        val result = generatePerFile(context, input, outputDirectory)
                        if (!result) {
                            errorLog(
                                TAG,
                                "Generates OAT file for \"${input.absolutePath}\" failed."
                            )
                        }
                        return@Callable result
                    }
                )
            }
        val results = futures.map {
            try {
                it.get()
            } catch (throwable: Throwable) {
                errorLog(
                    TAG,
                    "OAT files generating subtask is corrupted.",
                    throwable,
                )
                false
            }
        }
        executor.shutdownNow()
        return results.all { it }
    }
}

internal object Interpreter : DefaultGenerator() {

    private const val TAG = "Tinker.Oat.Interpreter"

    override fun generate(
        context: Context,
        input: File,
        output: File
    ): Boolean {
        output.parentFile!!.ensureIsExistingDirectory()
        val command = buildList {
            add("dex2oat")
            // for 7.1.1, duplicate class fix
            if (Build.VERSION.SDK_INT >= 24) {
                add("--runtime-arg")
                add("-classpath")
                add("--runtime-arg")
                add("&")
            }
            input.absolutePath
                .let { "--dex-file=$it" }
                .let(::add)
            output.absolutePath
                .let { "--oat-file=$it" }
                .let(::add)
            currentInstructionSet
                .let { "--instruction-set=$it" }
                .let(::add)
            (if (Build.VERSION.SDK_INT > 25) "quicken" else "interpret-only")
                .let { "--compiler-filter=$it" }
                .let(::add)
        }

        val code = try {
            ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
                .waitFor()
        } catch (exception: InterruptedException) {
            errorLog(
                TAG,
                "Generates OAT file failed because \"dex2oat\" is interrupted.",
                exception,
            )
            return false
        }
        if (code != 0) {
            errorLog(TAG, "Generates OAT file failed because \"dex2oat\" returns $code.")
            return false
        }
        return true
    }
}

internal object Compiler : DefaultGenerator() {

    private const val TAG = "Tinker.Oat.Compiler"

    @Suppress("DEPRECATION")
    override fun generate(
        context: Context,
        input: File,
        output: File
    ): Boolean = try {
        DexFile.loadDex(input.absolutePath, output.absolutePath, 0)
        true
    } catch (throwable: Throwable) {
        errorLog(
            TAG,
            "Generates OAT file failed with throwable.",
            throwable,
        )
        false
    }
}