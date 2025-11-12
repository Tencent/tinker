package com.tencent.tinker.test.internal.modules.oat

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import com.tencent.tinker.internal.modules.oat.Compiler
import com.tencent.tinker.internal.modules.oat.Interpreter
import com.tencent.tinker.test.createTestDirectory
import org.junit.Assert.assertTrue
import org.junit.Test

@SdkSuppress(maxSdkVersion = Build.VERSION_CODES.O)
class GeneratorTest {

    @Test
    fun compilerGenerate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = createTestDirectory()
        val input = directory.resolve("input")
            .apply { mkdirs() }
            .resolve("classes.dex")
            .also { file ->
                file.outputStream().buffered().use { outputStream ->
                    context.assets.open("test.dex").use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        val output = directory.resolve("output")
            .apply { mkdirs() }
            .resolve("classes.oat")
        Compiler.generate(context, input, output)
        assertTrue(output.exists())
    }

    @Test
    fun interpreterGenerate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = createTestDirectory()
        val input = directory.resolve("input")
            .apply { mkdirs() }
            .resolve("classes.dex")
            .also { file ->
                file.outputStream().buffered().use { outputStream ->
                    context.assets.open("test.dex").use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        val output = directory.resolve("output")
            .apply { mkdirs() }
            .resolve("classes.oat")
        Interpreter.generate(context, input, output)
        assertTrue(output.exists())
    }
}