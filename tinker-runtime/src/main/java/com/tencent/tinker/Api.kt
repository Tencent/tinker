package com.tencent.tinker

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.tencent.tinker.internal.deploy.deployPatchByRemote
import com.tencent.tinker.internal.deploy.legacy.globalCustomLegacyMerger
import com.tencent.tinker.internal.load.load
import com.tencent.tinker.internal.util.globalLogger
import com.tencent.tinker.internal.util.isInPatchProcess
import java.io.File
import java.io.InputStream
import java.io.OutputStream

@Suppress("unused")
object Tinker {

    /**
     * Logger used to print log messages.
     */
    interface Logger {

        /**
         * Log a [message] with [tag] and [priority].
         */
        fun log(
            priority: Int,
            tag: String,
            message: String,
        )
    }

    /**
     * Set logger implementation.
     */
    @JvmStatic
    fun setLogger(logger: Logger) {
        globalLogger = logger
    }

    /**
     * Merger used to generate patched data from base data and diff data.
     *
     * The API will be deprecated once new patch format is ready.
     */
    // TODO: Deprecate legacy merger once new patch format is ready.
    interface LegacyMerger {

        /**
         * Merge base data from [baseInput] and diff data from [diffInput] to patched data, and write to
         * [patchedOutput].
         */
        fun merge(
            baseInput: InputStream,
            diffInput: InputStream,
            patchedOutput: OutputStream,
        )
    }

    /**
     * Set custom legacy merger implementation.
     *
     * The API will be deprecated once new patch format is ready.
     */
    // TODO: Deprecate legacy merger once new patch format is ready.
    @JvmStatic
    fun setCustomLegacyMerger(merger: LegacyMerger) {
        globalCustomLegacyMerger = merger
    }

    class Error(
        val code: Int,
        val message: String,
        val reason: Throwable,
    )

    /**
     * Callback to notify the result of task.
     */
    interface Callback {

        /**
         * Once the task is complete, this function will be called.
         *
         * If task is successful, [error] is `null`.
         */
        fun onTaskComplete(error: Error?)
    }

    /**
     * The application base class for setting up Tinker.
     *
     * Following these steps to set up Tinker:
     *
     * - Create a subclass of [AppLike], which we refer it as "delegate application class" in the following text, and
     *   move all implementation code of original [Application] into created delegate application class. The subclass
     *   must have a public constructor with only single parameter typed as [Application]. This constructor is only used
     *   for creating delegate application class.
     * - Use a subclass of [App] as replacement of original [Application], which we refer it as "application class" in
     *   the following text. Because all classes accessed by application class are "non-patchable", it is recommended
     *   to write as less code as possible to application class.
     * - Returns the name of created delegate application class in [App.appLikeClassName] in application class.
     *
     * If implementing [App] by self and overriding [Application.attachBaseContext], make sure
     * `super.attachBaseContext(base)` is called before any other code.
     */
    abstract class App : Application() {

        /**
         * Gets class name of delegate class implementing [AppLike] which is used for current application.
         *
         * Always implement this property by returning a string constant value, instead of getting name by class
         * instance, which causes class loading.
         *
         * If the property returns `null`, none of delegate class is used.
         */
        abstract val appLikeClassName: String?

        /**
         * Gets callback of patch loading task.
         *
         * The callback is called in patch loading process.
         */
        abstract val loadCallback: Callback?

        /**
         * Gets callback of patch deploying task.
         *
         * The callback is only called in patch deploying process.
         */
        abstract val deployCallback: Callback?

        /**
         * Whether current application is hardening. Tinker will try to use special strategy for loading hardening
         * application.
         */
        open val hardening: Boolean
            get() = false

        private var appLike = null as AppLike?

        override fun attachBaseContext(base: Context) {
            super.attachBaseContext(base)
            val appLikeClassLoader = if (!isInPatchProcess) {
                load(
                    hardening = hardening,
                    callback = loadCallback,
                ) ?: classLoader
            } else {
                classLoader
            }
            // Do not catch any throwable while creating delegate application class. It should be fail-fast if user
            // provides an invalid delegate application class name.
            appLike = appLikeClassName
                ?.let {
                    appLikeClassLoader.loadClass(it)
                }
                ?.getConstructor(Application::class.java)
                ?.newInstance(this)
                ?.let {
                    it as AppLike
                }
            appLike?.attachBaseContext(base)
        }

        override fun onCreate() {
            super.onCreate()
            appLike?.onCreate()
        }

        override fun onTerminate() {
            super.onTerminate()
            appLike?.onTerminate()
        }

        override fun onLowMemory() {
            super.onLowMemory()
            appLike?.onLowMemory()
        }

        override fun onTrimMemory(level: Int) {
            super.onTrimMemory(level)
            appLike?.onTrimMemory(level)
        }

        override fun onConfigurationChanged(newConfig: Configuration) {
            super.onConfigurationChanged(newConfig)
            appLike?.onConfigurationChanged(newConfig)
        }
    }

    /**
     * A delegate of [Application] to make sure that as less as possible classes are loaded before Tinker patch is
     * loaded.
     *
     * See [App] for more details on how to set up Tinker.
     */
    abstract class AppLike(val application: Application) {

        /**
         * See [Application.attachBaseContext].
         */
        open fun attachBaseContext(base: Context) {}

        /**
         * See [Application.onCreate].
         */
        open fun onCreate() {}

        /**
         * See [Application.onTerminate].
         */
        open fun onTerminate() {}

        /**
         * See [Application.onLowMemory].
         */
        open fun onLowMemory() {}

        /**
         * See [Application.onTrimMemory].
         */
        open fun onTrimMemory(level: Int) {}

        /**
         * See [Application.onConfigurationChanged].
         */
        open fun onConfigurationChanged(newConfig: Configuration) {}
    }

    /**
     * Asks Tinker to create a patch with provided [version] and [diffPackage].
     */
    @JvmStatic
    fun deployPatch(context: Context, version: String, diffPackage: File) {
        context.deployPatchByRemote(version, diffPackage)
    }
}