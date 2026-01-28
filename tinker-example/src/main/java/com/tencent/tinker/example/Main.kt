package com.tencent.tinker.example

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.Toast
import com.tencent.tinker.Tinker
import com.tencent.tinker.example.cases.Library
import com.tencent.tinker.example.cases.ModifiedClass
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread

class MainApp : Tinker.App() {

    override fun appLikeClassName(): String =
        "com.tencent.tinker.example.MainAppLike"

    override fun loadCallback(): Tinker.Callback<Tinker.TaskSummary.Load> =
        Callbacks.Load(this)

    override fun deployCallback(): Tinker.Callback<Tinker.TaskSummary.Deploy> =
        Callbacks.Deploy(this)

    override fun cleanCallback(): Tinker.Callback<Tinker.TaskSummary.Clean> =
        Callbacks.Clean(this)
}

@Suppress("unused")
class MainAppLike(application: Application) : Tinker.AppLike(application) {

    companion object {
        private const val TAG = "Tinker.Example.AppLike"
    }

    override fun attachBaseContext(base: Context) {
        Log.d(TAG, "attachBaseContext() invoked")
    }

    override fun onCreate() {
        Log.d(TAG, "onCreate() invoked")
    }

    override fun onTerminate() {
        Log.d(TAG, "onTerminate() invoked")
    }

    override fun onLowMemory() {
        Log.d(TAG, "onLowMemory() invoked")
    }

    override fun onTrimMemory(level: Int) {
        Log.d(TAG, "onTrimMemory() invoked")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        Log.d(TAG, "onConfigurationChanged() invoked")
    }
}

private object Callbacks {

    class Load(private val context: Context) : Tinker.Callback<Tinker.TaskSummary.Load> {

        companion object {
            private const val TAG = "Tinker.Example.Load"
        }

        override fun onTaskComplete(summary: Tinker.TaskSummary.Load) {
            val error = summary.error
            if (error != null) {
                Log.e(TAG, "Load with error ${Tinker.codeOfErrorType(error.type)}.", error)
            } else {
                Log.d(TAG, "Complete loading without error.")
            }
            thread {
                val traceFile = context.filesDir.resolve("tinker_load_trace.json")
                Tinker.dumpTraceEventsToFile(summary.events, traceFile)
                Log.i(TAG, "Dump load trace to \"${traceFile.absolutePath}\".")
            }
        }
    }

    class Deploy(private val context: Context) : Tinker.Callback<Tinker.TaskSummary.Deploy> {

        companion object {
            private const val TAG = "Tinker.Example.Deploy"
        }

        override fun onTaskComplete(summary: Tinker.TaskSummary.Deploy) {
            val error = summary.error
            if (error != null) {
                Log.e(TAG, "Deploy with error ${Tinker.codeOfErrorType(error.type)}.", error)
            } else {
                Log.d(TAG, "Complete deploying without error.")
            }
            thread {
                val traceFile = context.filesDir.resolve("tinker_deploy_trace.json")
                Tinker.dumpTraceEventsToFile(summary.events, traceFile)
                Log.i(TAG, "Dump deploy trace to \"${traceFile.absolutePath}\".")
            }
        }
    }

    class Clean(private val context: Context) : Tinker.Callback<Tinker.TaskSummary.Clean> {

        companion object {
            private const val TAG = "Tinker.Example.Clean"
        }

        override fun onTaskComplete(summary: Tinker.TaskSummary.Clean) {
            val error = summary.error
            if (error != null) {
                Log.e(TAG, "Clean with error ${Tinker.codeOfErrorType(error.type)}.", error)
            } else {
                Log.d(TAG, "Complete cleaning without error.")
            }
            thread {
                val traceFile = context.filesDir.resolve("tinker_clean_trace.json")
                Tinker.dumpTraceEventsToFile(summary.events, traceFile)
                Log.i(TAG, "Dump clean trace to \"${traceFile.absolutePath}\".")
            }
        }
    }
}

private val diffPackage = File("/data/local/tmp/tinker-example-diff.apk")

class MainActivity : Activity() {

    private fun ImageView.setResultIcon(updated: Boolean) {
        if (updated) {
            setImageResource(R.drawable.updated)
        } else {
            setImageResource(R.drawable.original)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.clean_patch -> {
                cleanPatch()
                true
            }

            R.id.deploy_patch -> {
                deployPatch()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Dex
        thread {
            val updated = try {
                Class.forName("com.tencent.tinker.example.cases.AddedClass")
                true
            } catch (_: ClassNotFoundException) {
                false
            }
            runOnUiThread {
                findViewById<ImageView>(R.id.dex_added_class).setResultIcon(updated)
            }
        }
        thread @Suppress("KotlinConstantConditions") {
            val updated = ModifiedClass.DATA
            runOnUiThread {
                findViewById<ImageView>(R.id.dex_modified_class).setResultIcon(updated)
            }
        }
        thread {
            val updated = try {
                Class.forName("com.tencent.tinker.example.cases.RemovedClass")
                false
            } catch (_: ClassNotFoundException) {
                true
            }
            runOnUiThread {
                findViewById<ImageView>(R.id.dex_removed_class).setResultIcon(updated)
            }
        }

        // Library
        thread {
            val updated = Library.fromJni()
            runOnUiThread {
                findViewById<ImageView>(R.id.library_jni).setResultIcon(updated)
            }
        }
        thread {
            val updated = Library.fromDependency()
            runOnUiThread {
                findViewById<ImageView>(R.id.library_dependency).setResultIcon(updated)
            }
        }

        // Resource case is already referenced in layout XML file.

        // Asset
        thread {
            val updated = try {
                assets.open("added.txt").close()
                true
            } catch (_: IOException) {
                false
            }
            runOnUiThread {
                findViewById<ImageView>(R.id.asset_added).setResultIcon(updated)
            }
        }
        thread {
            val updated = assets.open("modified.txt")
                .use { it.readBytes() }
                .toString(Charsets.UTF_8)
                .toInt() == 1
            runOnUiThread {
                findViewById<ImageView>(R.id.asset_modified).setResultIcon(updated)
            }
        }
        thread {
            val updated = try {
                assets.open("removed.txt").close()
                false
            } catch (_: IOException) {
                true
            }
            runOnUiThread {
                findViewById<ImageView>(R.id.asset_removed).setResultIcon(updated)
            }
        }
    }

    private fun deployPatch() {
        thread {
            if (!diffPackage.isFile) {
                runOnUiThread {
                    AlertDialog.Builder(this)
                        .setMessage(
                            getString(
                                R.string.missing_diff_package,
                                diffPackage.absolutePath,
                            ).let {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY)
                                } else {
                                    @Suppress("DEPRECATION")
                                    Html.fromHtml(it)
                                }
                            }
                        )
                        .setPositiveButton(android.R.string.ok) { _, _ -> }
                        .create()
                        .show()
                }
                return@thread
            }
            runOnUiThread {
                Toast.makeText(this, R.string.start_deploying_patch, Toast.LENGTH_SHORT).show()
            }
            Tinker.deployPatch(
                this,
                "updated",
                diffPackage,
                true,
            )
        }
    }

    private fun cleanPatch() {
        thread {
            runOnUiThread {
                Toast.makeText(this, R.string.start_cleaning_patch, Toast.LENGTH_SHORT).show()
            }
            Tinker.cleanAllPatches(this)
        }
    }
}