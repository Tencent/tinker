package com.tencent.tinker.test.internal.module.oat

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import java.io.File

abstract class OatManagerTestService : Service() {

    interface Delegate {
        val baseDirectory: File
        fun metadataFile(directory: File): File
        fun contentBaseDirectory(directory: File): File
        fun acquire(
            directory: File,
            skipGenerateIfMissing: Boolean
        ): File?

        fun generateIfNeeded(directory: File)
        fun clean(directory: File): Boolean
        fun release()
        fun releaseGuard()
        fun useFailureGenerator()
        fun useExceptionGenerator()
        fun reset()
        fun isInterpreterGenerated(): Boolean
        fun isCompilerGenerated(): Boolean
    }

    protected val delegate by lazy {
        Class.forName(Delegate::class.java.name.substringBeforeLast('.') + ".OatManagerDelegate")
            .getDeclaredConstructor(Context::class.java)
            .newInstance(applicationContext)
            .let { it as Delegate }
    }

    abstract val binder: IBinder

    override fun onBind(intent: Intent): IBinder = binder
}

class OatManagerTestMainService : OatManagerTestService() {

    override val binder by lazy {
        object : IOatManagerTestMainService.Stub() {

            override fun baseDirectory(): String =
                delegate.baseDirectory.absolutePath

            override fun metadataFile(directoryPath: String): String =
                delegate.metadataFile(directoryPath.let(::File)).absolutePath

            override fun contentBaseDirectory(directoryPath: String): String =
                delegate.contentBaseDirectory(directoryPath.let(::File)).absolutePath

            override fun invalidGenerateIfNeeded(directoryPath: String) {
                delegate.generateIfNeeded(directoryPath.let(::File))
            }

            override fun invalidClean(directoryPath: String): Boolean =
                delegate.clean(directoryPath.let(::File))

            override fun acquire(
                directoryPath: String,
                skipGenerateIfMissing: Boolean
            ): String? = delegate
                .acquire(
                    directoryPath.let(::File),
                    skipGenerateIfMissing,
                )
                ?.absolutePath

            override fun release() {
                delegate.release()
            }

            override fun releaseGuard() {
                delegate.releaseGuard()
            }

            override fun reset() {
                delegate.reset()
            }

            override fun useFailureGenerator() {
                delegate.useFailureGenerator()
            }

            override fun useExceptionGenerator() {
                delegate.useExceptionGenerator()
            }

            override fun isInterpreterGenerated(): Boolean =
                delegate.isInterpreterGenerated()
        }
    }
}

class OatManagerTestDeployService : OatManagerTestService() {

    override val binder by lazy {
        object : IOatManagerTestDeployService.Stub() {
            override fun generateIfNeeded(directoryPath: String) {
                delegate.generateIfNeeded(directoryPath.let(::File))
            }

            override fun clean(directoryPath: String): Boolean =
                delegate.clean(directoryPath.let(::File))

            override fun invalidAcquire(
                directoryPath: String,
                skipGenerateIfMissing: Boolean
            ): String? = delegate
                .acquire(
                    directoryPath.let(::File),
                    skipGenerateIfMissing,
                )
                ?.absolutePath

            override fun invalidRelease() {
                delegate.release()
            }

            override fun reset() {
                delegate.reset()
            }

            override fun useFailureGenerator() {
                delegate.useFailureGenerator()
            }

            override fun useExceptionGenerator() {
                delegate.useExceptionGenerator()
            }

            override fun isCompilerGenerated(): Boolean =
                delegate.isCompilerGenerated()
        }
    }
}