package com.tencent.tinker.test

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.tencent.tinker.Tinker
import java.io.File

internal class TinkerTestApp : Tinker.App() {

    override fun baseDirectory(): File {
        return filesDir.resolve("tinker-test")
    }
}

@Suppress("unused")
internal class TinkerTestRunner : AndroidJUnitRunner() {

    override fun newApplication(classLoader: ClassLoader, className: String, context: Context): Application {
        return super.newApplication(classLoader, TinkerTestApp::class.java.name, context)
    }
}