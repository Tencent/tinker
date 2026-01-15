package com.tencent.tinker

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import com.tencent.tinker.internal.deploy.deployPatchByRemote
import com.tencent.tinker.internal.deploy.legacy.globalCustomLegacyMerger
import com.tencent.tinker.internal.load.load
import com.tencent.tinker.internal.util.globalLogger
import com.tencent.tinker.internal.util.isInDeployProcess
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

    /**
     * Error which is raised by Tinker.
     */
    class Error internal constructor(
        /**
         * Type of the error.
         */
        val type: Type,

        /**
         * Message of the error.
         */
        override val message: String,

        /**
         * Throwable which causes the error has occurred. It may be null if the error is not caused by a throwable.
         */
        cause: Throwable? = null,
    ) : Exception(message, cause) {

        /**
         * Type of the error.
         *
         * Each error type can be represented by a unique 32-bit unsigned integer code getting by calling [Tinker.code].
         * A code consists of two parts:
         *
         * - The higher 16 bits represent the group of error type.
         * - The lower 16 bits represent the index of error type in its group.
         */
        sealed interface Type {
            val groupCode: Int
        }

        /**
         * Error type group of error caused by patch loading.
         */
        enum class Load : Type {
            /**
             * Type of error caused by raised unexpected throwable.
             */
            UNEXPECTED,

            /**
             * Type of error caused by unrecoverable failed patch loading.
             *
             * Error with this type is always thrown as an uncaught exception. Once error with this type is thrown, the
             * process is in an unrecoverable damaged state and must be stopped immediately to prevent unexpected
             * behavior.
             */
            UNRECOVERABLE_LOAD_FAILED,

            /**
             * Type of error caused by reflect-getting non-existing element.
             */
            NO_SUCH_ELEMENT,

            /**
             * Type of error caused by type cast failure.
             */
            CAST_FAILED;

            override val groupCode: Int
                get() = 0x1000

            /**
             * Error type group of error caused by patched code loading.
             */
            enum class Code : Type {
                /**
                 * Type of error caused by raised unexpected throwable.
                 */
                UNEXPECTED,

                /**
                 * Type of error caused by missing valid inputs.
                 */
                NO_VALID_INPUTS,

                /**
                 * Type of error caused by invalid library directory.
                 */
                INVALID_LIBRARY_DIRECTORY,

                /**
                 * Type of error caused by reading test resource but it is broken.
                 */
                TEST_RESOURCE_BROKEN,

                /**
                 * Type of error caused by failed verification.
                 */
                VERIFY_FAILED;

                override val groupCode: Int
                    get() = 0x1100

                /**
                 * Error type group of error caused by patched code loading with inject-path strategy.
                 */
                enum class InjectPath : Type {
                    /**
                     * Type of error caused by raised unexpected throwable.
                     */
                    UNEXPECTED;

                    override val groupCode: Int
                        get() = 0x1110
                }

                /**
                 * Error type group of error caused by patched code loading with new-class-loader strategy.
                 */
                enum class NewClassLoader : Type {
                    /**
                     * Type of error caused by raised unexpected throwable.
                     */
                    UNEXPECTED;

                    override val groupCode: Int
                        get() = 0x1120
                }
            }

            /**
             * Error type group of error caused by patched resource loading.
             */
            enum class Resource : Type {
                /**
                 * Type of error caused by raised unexpected throwable.
                 */
                UNEXPECTED,

                /**
                 * Type of error caused by missing valid inputs.
                 */
                NO_VALID_INPUTS,

                /**
                 * Type of error caused by failed verification.
                 */
                VERIFY_FAILED;

                override val groupCode: Int
                    get() = 0x1200
            }
        }

        /**
         * Error type group of error caused by patch deploying.
         */
        enum class Deploy : Type {
            /**
             * Type of error caused by raised unexpected throwable.
             */
            UNEXPECTED,

            /**
             * Type of error caused by missing version while transferring data across processes.
             */
            MISSING_VERSION,

            /**
             * Type of error caused by missing diff package while transferring data across processes.
             */
            MISSING_DIFF_PACKAGE,

            /**
             * Type of error caused by unsupported diff package format or broken diff package.
             */
            INVALID_DIFF_PACKAGE;

            override val groupCode: Int
                get() = 0x2000

            /**
             * Error type group of error caused by legacy patch deploying.
             */
            enum class Legacy : Type {
                /**
                 * Type of error caused by raised unexpected throwable.
                 */
                UNEXPECTED,

                /**
                 * Type of error caused by missing metadata in diff package.
                 */
                MISSING_METADATA,

                /**
                 * Type of error caused by missing custom merger is required but not provided.
                 */
                MISSING_CUSTOM_MERGER;

                override val groupCode: Int
                    get() = 0x2100

                /**
                 * Error type group of error caused by legacy patch dex deploying.
                 */
                enum class Dex : Type {
                    /**
                     * Type of error caused by raised unexpected throwable.
                     */
                    UNEXPECTED,

                    /**
                     * Type of error caused by missing metadata in diff package.
                     */
                    MISSING_METADATA,

                    /**
                     * Type of error caused by invalid metadata in diff package.
                     */
                    INVALID_METADATA,

                    /**
                     * Type of error caused by unsupported dex mode which is defined in diff package.
                     */
                    UNSUPPORTED_DEX_MODE,

                    /**
                     * Type of error caused by missing base entry in base apk file.
                     */
                    MISSING_BASE_ENTRY,

                    /**
                     * Type of error caused by invalid base entry in base apk file.
                     */
                    INVALID_BASE_ENTRY,

                    /**
                     * Type of error caused by invalid diff entry in diff package.
                     */
                    MISSING_DIFF_ENTRY,

                    /**
                     * Type of error caused by missing test dex in base apk file.
                     */
                    MISSING_TEST_DEX,

                    /**
                     * Type of error caused by failure deploying, which may because hash of deployed file is mismatched.
                     */
                    INVALID_DEPLOY_RESULT,

                    /**
                     * Type of error caused by missing deployed result.
                     */
                    NO_DEPLOYED_DEX;

                    override val groupCode: Int
                        get() = 0x2110
                }

                /**
                 * Error type group of error caused by legacy patch library deploying.
                 */
                enum class Library : Type {
                    /**
                     * Type of error caused by raised unexpected throwable.
                     */
                    UNEXPECTED,

                    /**
                     * Type of error caused by invalid metadata in diff package.
                     */
                    INVALID_METADATA,

                    /**
                     * Type of error caused by missing base entry in base apk file.
                     */
                    MISSING_BASE_ENTRY,

                    /**
                     * Type of error caused by invalid base entry in base apk file.
                     */
                    INVALID_BASE_ENTRY,

                    /**
                     * Type of error caused by missing diff entry in diff package.
                     */
                    MISSING_DIFF_ENTRY,

                    /**
                     * Type of error caused by invalid diff entry in diff package.
                     */
                    INVALID_DIFF_ENTRY,

                    /**
                     * Type of error caused by failure deploying, which may because hash of deployed file is mismatched.
                     */
                    INVALID_DEPLOY_RESULT;

                    override val groupCode: Int
                        get() = 0x2120
                }

                /**
                 * Error type group of error caused by legacy patch resource deploying.
                 */
                enum class Resource : Type {
                    /**
                     * Type of error caused by raised unexpected throwable.
                     */
                    UNEXPECTED,

                    /**
                     * Type of error caused by invalid metadata in diff package.
                     */
                    INVALID_METADATA,

                    /**
                     * Type of error caused by missing manifest file (a.k.a. `AndroidManifest.xml`) in base apk file.
                     */
                    MISSING_MANIFEST,

                    /**
                     * Type of error caused by missing base entry in base apk file.
                     */
                    MISSING_BASE_ENTRY,

                    /**
                     * Type of error caused by invalid base entry in base apk file.
                     */
                    INVALID_BASE_ENTRY,

                    /**
                     * Type of error caused by missing diff entry in diff package.
                     */
                    MISSING_DIFF_ENTRY,

                    /**
                     * Type of error caused by failure deploying, which may because hash of deployed file is mismatched.
                     */
                    INVALID_DEPLOY_RESULT;

                    override val groupCode: Int
                        get() = 0x2130
                }
            }
        }

        /**
         * Error type group of error caused by raw patch management.
         */
        enum class RawPatch : Type {
            /**
             * Type of error caused by raised unexpected throwable.
             */
            UNEXPECTED,

            /**
             * Type of error caused by throwable while acquiring raw patch with version as using.
             */
            ACQUIRE_PATCH_AS_USING,

            /**
             * Type of error caused by throwable while acquiring raw patch with version as cleaning.
             */
            ACQUIRE_PATCH_AS_CLEANING,

            /**
             * Type of error caused by acquiring raw patch more than once in same process.
             */
            HAS_ACQUIRED_PATCH,

            /**
             * Type of error caused by I/O exception while reading latest version.
             */
            READ_LATEST_VERSION,

            /**
             * Type of error caused by I/O exception while writing latest version.
             */
            WRITE_LATEST_VERSION,

            /**
             * Type of error caused by I/O exception while reading main version.
             */
            READ_MAIN_VERSION,

            /**
             * Type of error caused by I/O exception while writing main version.
             */
            WRITE_MAIN_VERSION,

            /**
             * Type of error caused by I/O exception while reading unavailable versions.
             */
            READ_UNAVAILABLE,

            /**
             * Type of error caused by I/O exception while appending unavailable versions.
             */
            APPEND_UNAVAILABLE,

            /**
             * Type of error caused by I/O exception while cleaning unavailable versions.
             */
            CLEAN_UNAVAILABLE,

            /**
             * Type of error caused by throwable while marking main process as alive.
             */
            MARK_MAIN_ALIVE,

            /**
             * Type of error caused by throwable while checking main process is alive.
             */
            CHECK_MAIN_ALIVE,

            /**
             * Type of error caused by throwable patch with version which already exists.
             */
            CREATE_EXIST_PATCH,

            /**
             * Type of error caused by throwable while cloning raw patch files.
             */
            CLONE_PATCH,

            /**
             * Type of error caused by throwable while cleaning raw patch files.
             */
            CLEAN_PATCH,

            /**
             * Type of error caused by throwable while dropping write permissions of raw patch files.
             */
            DROP_PATCH_WRITE_PERMISSION,

            /**
             * Type of error caused by throwable while recovering write permissions of raw patch files.
             */
            RECOVER_PATCH_WRITE_PERMISSION;

            override val groupCode: Int
                get() = 0x3000
        }

        /**
         * Error type group of error caused by OAT file management.
         */
        enum class Oat : Type {
            /**
             * Type of error caused by raised unexpected throwable.
             */
            UNEXPECTED,

            /**
             * Type of error caused by acquiring OAT files with same directory more than once in same process.
             */
            HAS_ACQUIRED_OAT,

            /**
             * Type of error caused by exception while generating or storing OAT files.
             */
            GENERATE_OR_STORE_FAILED;

            override val groupCode: Int
                get() = 0x4000
        }

        /**
         * Error type group of error caused by patch layout management.
         */
        enum class Layout : Type {
            /**
             * Type of error caused by raised unexpected throwable.
             */
            UNEXPECTED,

            /**
             * Type of error caused by invalid construct source.
             */
            INVALID_SOURCE;

            override val groupCode: Int
                get() = 0x5000
        }

        /**
         * Error type group of error caused by validation.
         */
        enum class Validate : Type {
            /**
             * Type of error caused by raised unexpected throwable.
             */
            UNEXPECTED,

            /**
             * Type of error caused by validating a non-directory element.
             */
            OPERATE_NON_DIRECTORY,

            /**
             * Type of error caused by invalid fingerprint file.
             */
            INVALID_FINGERPRINT,

            /**
             * Type of error caused by failed validation.
             */
            VALIDATE_FAILED;

            override val groupCode: Int
                get() = 0x6000
        }
    }

    /**
     * Gets code of error type. See [Error.Type] for more details.
     */
    @get:JvmName("codeOfErrorType")
    val <T: Error.Type> T.code: Int
        get() = (groupCode shl 16) or (this as Enum<*>).ordinal

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
            val appLikeClassLoader = if (!isInDeployProcess) {
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