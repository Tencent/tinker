package com.tencent.tinker;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.util.Log;

import com.tencent.tinker.internal.BaseKt;
import com.tencent.tinker.internal.clean.CleanKt;
import com.tencent.tinker.internal.deploy.DeployKt;
import com.tencent.tinker.internal.deploy.legacy.LegacyKt;
import com.tencent.tinker.internal.load.LoadKt;
import com.tencent.tinker.internal.util.LogKt;
import com.tencent.tinker.internal.util.TraceKt;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.List;

@SuppressWarnings({"unused", "NullableProblems", "JavadocReference"})
public final class Tinker {

    /**
     * Logger used to print log messages.
     */
    public static abstract class Logger {

        /**
         * Gets filter log level. All log messages with level lower than {@code level} will be ignored.
         * <p>
         */
        public int filterLogLevel() {
            return Log.VERBOSE;
        }

        /**
         * Logs a {@code message} with {@code tag} and {@code priority}.
         */
        public abstract void log(int priority, String tag, String message);
    }

    /**
     * Merger used to generate patched data from base data and diff data.
     * <p>
     * The API will be deprecated once new patch format is ready.
     */
    // TODO: Deprecate legacy merger once new patch format is ready.
    public interface LegacyMerger {

        /**
         * Merge base data from {@code baseInput} and diff data from {@code diffInput} to patched data, and write to
         * {@code patchedOutput}.
         */
        void merge(InputStream baseInput, InputStream diffInput, OutputStream patchedOutput);
    }

    /**
     * Error which is raised by Tinker.
     */
    public static final class Error extends Exception {

        public interface Type {
            int groupCode();
        }

        private final Type mType;

        /**
         * Type of the error.
         */
        public Type getType() {
            return mType;
        }

        private final String mMessage;

        /**
         * Message of the error.
         */
        public String getMessage() {
            return mMessage;
        }

        public Error(Type type, String message) {
            super(message, null);
            mType = type;
            mMessage = message;
        }

        public Error(Type type, String message, Throwable cause) {
            super(message, cause);
            mType = type;
            mMessage = message;
        }

        /**
         * Error type groups of unexpected error, which may be caused by faulty code design.
         * <p>
         * If errors with these types are raised, please report to developers via
         * [GitHub Issues](https://github.com/Tencent/tinker/issues/new).
         */
        public static final class Unexpected {

            /**
             * Error type group of calling trace functions incorrectly.
             */
            public enum Trace implements Type {
                /**
                 * Type of error caused by raised unexpected throwable.
                 */
                UNEXPECTED,

                /**
                 * Type of error caused by starting a task tracing inside another task.
                 */
                TRACE_TASK_INSIDE_A_TASK;

                @Override
                public int groupCode() {
                    return 0x0100;
                }
            }
        }

        /**
         * Error type group of error caused by patch loading.
         */
        public enum Load implements Type {
            /**
             * Type of error caused by raised unexpected throwable.
             */
            UNEXPECTED,

            /**
             * Type of error caused by unrecoverable failed patch loading.
             * <p>
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

            @Override
            public int groupCode() {
                return 0x1000;
            }

            /**
             * Error type group of error caused by patched code loading.
             */
            public enum Code implements Type {
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
                 * Type of error caused by reading test resource, but it is broken.
                 */
                TEST_RESOURCE_BROKEN,

                /**
                 * Type of error caused by failed verification.
                 */
                VERIFY_FAILED;

                @Override
                public int groupCode() {
                    return 0x1100;
                }

                /**
                 * Error type group of error caused by patched code loading with inject-path strategy.
                 */
                public enum InjectPath implements Type {
                    /**
                     * Type of error caused by raised unexpected throwable.
                     */
                    UNEXPECTED;

                    @Override
                    public int groupCode() {
                        return 0x1110;
                    }
                }

                /**
                 * Error type group of error caused by patched code loading with new-class-loader strategy.
                 */
                public enum NewClassLoader implements Type {
                    /**
                     * Type of error caused by raised unexpected throwable.
                     */
                    UNEXPECTED;

                    @Override
                    public int groupCode() {
                        return 0x1120;
                    }
                }
            }

            /**
             * Error type group of error caused by patched resource loading.
             */
            public enum Resource implements Type {
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

                @Override
                public int groupCode() {
                    return 0x1200;
                }
            }
        }

        /**
         * Error type group of error caused by patch deploying.
         */
        public enum Deploy implements Type {
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
            INVALID_DIFF_PACKAGE,

            /**
             * Type of error caused by system interruption.
             */
            INTERRUPTED;

            @Override
            public int groupCode() {
                return 0x2000;
            }

            /**
             * Error type group of error caused by legacy patch deploying.
             */
            public enum Legacy implements Type {
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
                MISSING_CUSTOM_MERGER,

                /**
                 * Type of error caused by reading base apk signature failed.
                 */
                READ_BASE_APK_SIGNATURE_FAILED,

                /**
                 * Type of error caused by checking signature failed.
                 */
                CHECK_SIGNATURE_FAILED;

                @Override
                public int groupCode() {
                    return 0x2100;
                }

                /**
                 * Error type group of error caused by legacy patch dex deploying.
                 */
                public enum Dex implements Type {
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

                    @Override
                    public int groupCode() {
                        return 0x2110;
                    }
                }

                /**
                 * Error type group of error caused by legacy patch library deploying.
                 */
                public enum Library implements Type {
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

                    @Override
                    public int groupCode() {
                        return 0x2120;
                    }
                }

                /**
                 * Error type group of error caused by legacy patch resource deploying.
                 */
                public enum Resource implements Type {
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
                     * Type of error caused by missing test asset in base apk file.
                     */
                    MISSING_TEST_ASSET,

                    /**
                     * Type of error caused by failure deploying, which may because hash of deployed file is mismatched.
                     */
                    INVALID_DEPLOY_RESULT;

                    @Override
                    public int groupCode() {
                        return 0x2130;
                    }
                }
            }
        }

        /**
         * Error type group of error caused by patch cleaning.
         */
        public enum Clean implements Type {
            /**
             * Type of error caused by raised unexpected throwable.
             */
            UNEXPECTED,

            /**
             * Type of error caused by missing strategy while transferring data across processes.
             */
            MISSING_STRATEGY,

            /**
             * Type of error caused by invalid strategy while transferring data across processes.
             */
            INVALID_STRATEGY,

            /**
             * Type of error caused by system interruption.
             */
            INTERRUPTED;

            @Override
            public int groupCode() {
                return 0x3000;
            }
        }

        /**
         * Error type group of error caused by raw patch management.
         */
        public enum RawPatch implements Type {
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
             * Type of error caused by I/O exception while reading the latest version.
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
             * Type of error caused by throwable while recovering write permissions of raw patch files.
             */
            RECOVER_PATCH_WRITE_PERMISSION;

            @Override
            public int groupCode() {
                return 0x4000;
            }
        }

        /**
         * Error type group of error caused by OAT file management.
         */
        public enum Oat implements Type {
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

            @Override
            public int groupCode() {
                return 0x5000;
            }
        }

        /**
         * Error type group of error caused by patch layout management.
         */
        public enum Layout implements Type {
            /**
             * Type of error caused by raised unexpected throwable.
             */
            UNEXPECTED,

            /**
             * Type of error caused by invalid construct source.
             */
            INVALID_SOURCE;

            @Override
            public int groupCode() {
                return 0x6000;
            }
        }

        /**
         * Error type group of error caused by validation.
         */
        public enum Validate implements Type {
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

            @Override
            public int groupCode() {
                return 0x7000;
            }
        }

        /**
         * Error type group of error caused by invalid Tinker API usage.
         */
        public enum Usage implements Type {
            /**
             * Type of error caused by raised unexpected throwable.
             */
            UNEXPECTED,

            /**
             * Type of error caused by invalid application class type. Application class should be a subclass of
             * {@link Tinker.App}.
             */
            APP_IS_NOT_TINKER_APP;

            @Override
            public int groupCode() {
                return 0xf000;
            }
        }
    }

    /**
     * Gets code of error type. See {@link Error.Type} for more details.
     */
    public static int codeOfErrorType(Error.Type type) {
        return (type.groupCode() << 16) | ((Enum<?>) type).ordinal();
    }

    /**
     * Event of the traced task procedure.
     */
    public static final class TraceEvent implements Serializable {
        private final String mName;

        /**
         * Name of the event.
         */
        public String getName() {
            return mName;
        }

        private final int mPid;

        /**
         * PID of the process which the procedure is running on.
         */
        public int getPid() {
            return mPid;
        }

        private final int mTid;

        /**
         * TID of the thread which the procedure is running on.
         */
        public int getTid() {
            return mTid;
        }

        private final long mTimestamp;

        /**
         * Start time since boot of the procedure in microseconds.
         */
        public long getTimestamp() {
            return mTimestamp;
        }

        private final long mDuration;

        /**
         * Duration of the procedure in microseconds.
         */
        public long getDuration() {
            return mDuration;
        }

        public TraceEvent(String name, int pid, int tid, long timestamp, long duration) {
            mName = name;
            mPid = pid;
            mTid = tid;
            mTimestamp = timestamp;
            mDuration = duration;
        }
    }

    /**
     * Dumps trace events as
     * <a href="https://perfetto.dev/docs/getting-started/other-formats#chrome-json-format">Chromium JSON trace format</a>
     * to {@code file}.
     */
    public static void dumpTraceEventsToFile(Iterable<TraceEvent> events, File file) {
        TraceKt.dumpToFile(events, file);
    }

    /**
     * Summary of the task.
     */
    public static abstract class TaskSummary implements Serializable {

        private final Error mError;

        /**
         * Error raised during task. If task is successful, returned value is `null`.
         */
        public Error getError() {
            return mError;
        }

        private final List<TraceEvent> mEvents;

        /**
         * Event of traced task procedures.
         * <p>
         * If <a href="https://developer.android.com/topic/performance/tracing">system tracing</a> is enabled, events
         * are also recorded as system trace events.
         */
        public List<TraceEvent> getEvents() {
            return mEvents;
        }

        public TaskSummary(Error error, List<TraceEvent> events) {
            mError = error;
            mEvents = events;
        }

        /**
         * Whether the task is successful.
         */
        public boolean isSuccess() {
            return mError == null;
        }

        /**
         * Summary of load task.
         */
        public static class Load extends TaskSummary {

            private final String mVersion;

            /**
             * Version of patch loaded by current process.
             * <p>
             * If none of patch is loaded, returned value is `null`.
             */
            public String getVersion() {
                return mVersion;
            }

            private final File mPatchDirectory;

            /**
             * Directory of patch loaded by current process.
             * <p>
             * Depending on implementation, this directory may be non-writable. Never try to modify contents of this
             * directory.
             * <p>
             * If none of patch is loaded, returned value is `null`.
             */
            public File getPatchDirectory() {
                return mPatchDirectory;
            }

            public Load(Error error, List<TraceEvent> events, String version, File patchDirectory) {
                super(error, events);
                mVersion = version;
                mPatchDirectory = patchDirectory;
            }
        }

        /**
         * Summary of deploy task.
         */
        public static class Deploy extends TaskSummary {

            private final String mVersion;

            /**
             * Version of deployed patch.
             * <p>
             * If deploy task is failed, returned value is `null`.
             */
            public String getVersion() {
                return mVersion;
            }

            private final File mSourceDiffPackage;

            /**
             * Diff package which triggered this deploy task.
             * <p>
             * If deploy task is failed, returned value is `null`.
             */
            public File getSourceDiffPackage() {
                return mSourceDiffPackage;
            }

            public Deploy(Error error, List<TraceEvent> events, String version, File sourceDiffPackage) {
                super(error, events);
                mVersion = version;
                mSourceDiffPackage = sourceDiffPackage;
            }
        }

        /**
         * Summary of clean task.
         */
        public static class Clean extends TaskSummary {
            private final List<String> mVersions;

            /**
             * Gets cleaned patch versions.
             */
            public List<String> getVersions() {
                return mVersions;
            }

            public Clean(Error error, List<TraceEvent> events, List<String> versions) {
                super(error, events);
                mVersions = versions;
            }
        }
    }

    /**
     * Callback to notify the result of task.
     */
    public interface Callback<T extends TaskSummary> {

        /**
         * Once the task is complete, this function will be called.
         */
        void onTaskComplete(T summary);
    }

    /**
     * Config class for testing Tinker V2 with legacy Tinker.
     * <p>
     * TODO: Remove this interface when test is complete.
     */
    public interface AppConfig {

        /**
         * Gets base directory of Tinker, which is used for storing files created by Tinker.
         * <p>
         * Since Tinker is implemented based on file system, key files are required to be protected by Tinker users.
         * Returned directory must be guaranteed by Tinker caller that it is neither modified nor deleted.
         */
        File baseDirectory();

        /**
         * Gets class name of delegate class implementing {@link AppLike} which is used for current application.
         * <p>
         * Always implement this property by returning a string constant value, instead of getting name by class
         * instance, which causes class loading.
         * <p>
         * If the property returns <code>null</code>, none of delegate class is used.
         */
        String appLikeClassName();

        /**
         * Gets callback of patch loading task.
         * <p>
         * The callback is called in patch loading process.
         */
        Callback<TaskSummary.Load> loadCallback();

        /**
         * Gets callback of patch deploying task.
         * <p>
         * The callback is only called in patch deploying process.
         */
        Callback<TaskSummary.Deploy> deployCallback();

        /**
         * Gets callback of patch cleaning task.
         * <p>
         * The callback is only called in patch deploying process.
         */
        Callback<TaskSummary.Clean> cleanCallback();

        /**
         * Whether to disable loading patch for current process.
         * <p>
         * The function is called at an early stage since base context is not attached to application.
         * {@code baseContext} is provided.
         */
        boolean disabled(Context baseContext);

        /**
         * Whether to skip validating patch files while loading, which may speed up loading if application is huge.
         * However, patch files may be corrupted if application code modifies patch files unexpectedly.
         * <p>
         * The function is called at an early stage since base context is not attached to application.
         * {@code baseContext} is provided.
         */
        boolean skipValidating(Context baseContext);

        /**
         * Whether current application is hardening. Tinker will try to use special strategy for loading hardening
         * application.
         * <p>
         * The function is called at an early stage since base context is not attached to application.
         * {@code baseContext} is provided.
         */
        boolean hardening(Context baseContext);

        /**
         * Gets logger implementation.
         * <p>
         * If {@code null} is returned, default logger implementation is used.
         * <p>
         * The function is called at an early stage since base context is not attached to application.
         * {@code baseContext} is provided.
         */
        Logger logger(Context baseContext);

        /**
         * Gets custom legacy merger implementation.
         * <p>
         * The API will be deprecated once new patch format is ready.
         * <p>
         * The function is called at an early stage since base context is not attached to application.
         * {@code baseContext} is provided.
         */
        // TODO: Deprecate legacy merger once new patch format is ready.
        LegacyMerger customLegacyMerger(Context baseContext);
    }

    public static AppLike legacyAttachBaseContext(
            Application application,
            Context baseContext,
            AppConfig appConfig
    ) {
        final AppLike appLike = LoadKt.legacyLoad(
                application,
                appConfig.disabled(baseContext),
                appConfig.hardening(baseContext),
                appConfig.skipValidating(baseContext)
        );
        if (appLike != null) {
            appLike.attachBaseContext(baseContext);
        }
        return appLike;
    }

    /**
     * The application base class for setting up Tinker.
     * <p>
     * Following these steps to set up Tinker:
     * <ul>
     * <li>
     * Create a subclass of {@link AppLike}, which we refer it as "delegate application class" in the following text,
     * and move all implementation code of original {@link Application} into created delegate application class. The
     * subclass must have a public constructor with only single parameter typed as {@link Application}. This constructor
     * is only used for creating delegate application class.
     * </li>
     * <li>
     * Use a subclass of {@link App} as replacement of original {@link Application}, which we refer it as "application
     * class" in the following text. Because all classes accessed by application class are "non-patchable", it is
     * recommended to write as less code as possible to application class.
     * </li>
     * <li>
     * Returns the name of created delegate application class in {@link App#appLikeClassName} in application class.
     * </li>
     * </ul>
     * <p>
     * If implementing {@link App} by self and overriding {@link Application#attachBaseContext}, make sure
     * <code>super.attachBaseContext(base)</code> is called before any other code.
     */
    public static abstract class App extends Application implements AppConfig {

        /**
         * Gets base directory of Tinker, which is used for storing files created by Tinker.
         * <p>
         * Since Tinker is implemented based on file system, key files are required to be protected by Tinker users.
         * Returned directory must be guaranteed by Tinker caller that it is neither modified nor deleted.
         */
        @Override
        public File baseDirectory() {
            return BaseKt.getDefaultBaseDirectory(this);
        }

        /**
         * Gets class name of delegate class implementing {@link AppLike} which is used for current application.
         * <p>
         * Always implement this property by returning a string constant value, instead of getting name by class
         * instance, which causes class loading.
         * <p>
         * If the property returns <code>null</code>, none of delegate class is used.
         */
        @Override
        public String appLikeClassName() {
            return "com.tencent.tinker.Tinker$AppLike";
        }

        /**
         * Gets callback of patch loading task.
         * <p>
         * The callback is called in patch loading process.
         */
        @Override
        public Callback<TaskSummary.Load> loadCallback() {
            return null;
        }

        /**
         * Gets callback of patch deploying task.
         * <p>
         * The callback is only called in patch deploying process.
         */
        @Override
        public Callback<TaskSummary.Deploy> deployCallback() {
            return null;
        }

        /**
         * Gets callback of patch cleaning task.
         * <p>
         * The callback is only called in patch deploying process.
         */
        @Override
        public Callback<TaskSummary.Clean> cleanCallback() {
            return null;
        }

        /**
         * Whether to disable loading patch for current process.
         * <p>
         * The function is called at an early stage since base context is not attached to application.
         * {@code baseContext} is provided.
         */
        @Override
        public boolean disabled(Context baseContext) {
            return false;
        }

        /**
         * Whether to skip validating patch files while loading, which may speed up loading if application is huge.
         * However, patch files may be corrupted if application code modifies patch files unexpectedly.
         * <p>
         * The function is called at an early stage since base context is not attached to application.
         * {@code baseContext} is provided.
         */
        @Override
        public boolean skipValidating(Context baseContext) {
            return false;
        }

        /**
         * Whether current application is hardening. Tinker will try to use special strategy for loading hardening
         * application.
         * <p>
         * The function is called at an early stage since base context is not attached to application.
         * {@code baseContext} is provided.
         */
        @Override
        public boolean hardening(Context baseContext) {
            return false;
        }

        /**
         * Gets logger implementation.
         * <p>
         * If {@code null} is returned, default logger implementation is used.
         * <p>
         * The function is called at an early stage since base context is not attached to application.
         * {@code baseContext} is provided.
         */
        @Override
        public Logger logger(Context baseContext) {
            return null;
        }

        /**
         * Gets custom legacy merger implementation.
         * <p>
         * The API will be deprecated once new patch format is ready.
         * <p>
         * The function is called at an early stage since base context is not attached to application.
         * {@code baseContext} is provided.
         */
        // TODO: Deprecate legacy merger once new patch format is ready.
        @Override
        public LegacyMerger customLegacyMerger(Context baseContext) {
            return null;
        }

        private AppLike mAppLike = null;

        @Override
        protected void attachBaseContext(Context base) {
            super.attachBaseContext(base);
            final Logger logger = logger(base);
            if (logger != null) {
                LogKt.setGlobalLogger(logger);
            }
            final LegacyMerger legacyMerger = customLegacyMerger(base);
            if (legacyMerger != null) {
                LegacyKt.setGlobalCustomLegacyMerger(legacyMerger);
            }
            mAppLike = LoadKt.load(
                    this,
                    disabled(base),
                    hardening(base),
                    skipValidating(base)
            );
            final AppLike appLike = mAppLike;
            if (appLike != null) {
                appLike.attachBaseContext(base);
            }
        }

        @Override
        public void onCreate() {
            super.onCreate();
            final AppLike appLike = mAppLike;
            if (appLike != null) {
                appLike.onCreate();
            }
        }

        @Override
        public void onTerminate() {
            super.onTerminate();
            final AppLike appLike = mAppLike;
            if (appLike != null) {
                appLike.onTerminate();
            }
        }

        @Override
        public void onLowMemory() {
            super.onLowMemory();
            final AppLike appLike = mAppLike;
            if (appLike != null) {
                appLike.onLowMemory();
            }
        }

        @Override
        public void onTrimMemory(int level) {
            super.onTrimMemory(level);
            final AppLike appLike = mAppLike;
            if (appLike != null) {
                appLike.onTrimMemory(level);
            }
        }

        @Override
        public void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            final AppLike appLike = mAppLike;
            if (appLike != null) {
                appLike.onConfigurationChanged(newConfig);
            }
        }
    }

    /**
     * A delegate of {@link Application} to make sure that as less as possible classes are loaded before Tinker patch is
     * loaded.
     * <p>
     * See {@link App} for more details on how to set up Tinker.
     */
    public static class AppLike {

        private final Application mApplication;

        public Application getApplication() {
            return mApplication;
        }

        public AppLike(Application application) {
            mApplication = application;
        }

        /**
         * See {@link Application#attachBaseContext}.
         */
        public void attachBaseContext(Context base) {
        }

        /**
         * See {@link Application#onCreate}.
         */
        public void onCreate() {
        }

        /**
         * See {@link Application#onTerminate}.
         */
        public void onTerminate() {
        }

        /**
         * See {@link Application#onLowMemory}.
         */
        public void onLowMemory() {
        }

        /**
         * See {@link Application#onTrimMemory}.
         */
        public void onTrimMemory(int level) {
        }

        /**
         * See {@link Application#onConfigurationChanged}.
         */
        public void onConfigurationChanged(Configuration newConfig) {
        }
    }

    /**
     * Asks Tinker to create a patch with provided {@code} and {@code diffPackage}.
     * <p>
     * If {@code skipCheckingSignature}, Tinker will treat diff package is trusted, otherwise, diff package should have
     * same signature as base apk file.
     */
    public static void deployPatch(
            Context context,
            String version,
            File diffPackage,
            boolean skipCheckingSignature
    ) {
        BaseKt.checkIfVersionIsValid(version);
        DeployKt.deployPatchByRemote(context, version, diffPackage, skipCheckingSignature);
    }

    /**
     * Asks Tinker to create a patch with provided {@code version} and {@code diffPackage}.
     */
    public static synchronized void deployPatch(
            Context context,
            String version,
            File diffPackage
    ) {
        deployPatch(context, version, diffPackage, false);
    }

    /**
     * Asks Tinker to clean all patches **except patches are in use**.
     * <p>
     * For cleaning using patches, using processes should be terminated, and a new patch should be deployed to
     * overwrite, or using {@code requestPatchAsUnavailable} to mark patch as unavailable, so that the process does not
     * use target patch when it starts again.
     */
    public static void cleanAllPatches(Context context) {
        CleanKt.cleanAllPatchesByRemote(context);
    }

    /**
     * Asks Tinker to clean obsolete patches **except patches are in use**.
     * <p>
     * Different from {@link Tinker#cleanAllPatches}, latest version is kept, unless latest version is marked as
     * unavailable.
     */
    public static void cleanObsoletePatches(Context context) {
        CleanKt.cleanObsoletePatchesByRemote(context);
    }

    /**
     * Requests Tinker marks provided patch version as unavailable to clean up this patch, and does not provide this
     * patch anymore.
     * <p>
     * If marking current using patch as unavailable, the patch is still unable to be cleaned up until the process is
     * terminated. See {@link Tinker#cleanAllPatches} or {@link Tinker#cleanObsoletePatches}.
     */
    public static void requestPatchAsUnavailable(Context context, String version) {
        BaseKt.checkIfVersionIsValid(version);
        CleanKt.requestPatchAsUnavailable(context, version);
    }
}
