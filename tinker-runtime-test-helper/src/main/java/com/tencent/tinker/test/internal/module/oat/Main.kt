package com.tencent.tinker.test.internal.module.oat

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.tencent.tinker.test.internal.ParcelableTinkerPatch

abstract class TinkerOatManagerTestService : Service() {

    interface Delegate {
        fun acquire(
            context: Context,
            patch: ParcelableTinkerPatch,
            skipGenerateIfMissing: Boolean
        ): String?

        fun generateIfNeeded(context: Context, patch: ParcelableTinkerPatch)
        fun clean(context: Context, version: String): Boolean
        fun release(context: Context)
        fun releaseGuard()
        fun setMainProcessCompilerAsInvalid()
        fun setPatchProcessInterpreterAsInvalid()
        fun useSuccessCompilerForPatchProcess()
        fun useSuccessInterpreterForMainProcess()
        fun useFailureCompiler()
        fun useFailureInterpreter()
        fun useExceptionCompiler()
        fun useExceptionInterpreter()
        fun isCompilerGenerated(): Boolean
        fun isInterpreterGenerated(): Boolean
    }

    protected val delegate by lazy {
        Class.forName(Delegate::class.java.name.substringBeforeLast('.') + ".TinkerOatManagerTestServiceDelegateImpl")
            .getDeclaredConstructor()
            .newInstance()
            .let { it as Delegate }
    }

    abstract val binder: IBinder

    override fun onBind(intent: Intent): IBinder = binder
}

class TinkerOatManagerTestMainService : TinkerOatManagerTestService() {

    override val binder by lazy {
        object : ITinkerOatManagerTestMainService.Stub() {
            override fun acquire(
                patch: ParcelableTinkerPatch,
                skipGenerateIfMissing: Boolean
            ): String? = delegate.acquire(applicationContext, patch, skipGenerateIfMissing)

            override fun release() {
                delegate.release(applicationContext)
            }

            override fun releaseGuard() {
                delegate.releaseGuard()
            }

            override fun invalidGenerateIfNeeded(patch: ParcelableTinkerPatch) {
                delegate.generateIfNeeded(applicationContext, patch)
            }

            override fun invalidClean(version: String): Boolean =
                delegate.clean(applicationContext, version)

            override fun setCompilerIsInvalid() {
                delegate.setMainProcessCompilerAsInvalid()
            }

            override fun useSuccessInterpreter() {
                delegate.useSuccessInterpreterForMainProcess()
            }

            override fun useFailureInterpreter() {
                delegate.useFailureInterpreter()
            }

            override fun useExceptionInterpreter() {
                delegate.useExceptionInterpreter()
            }

            override fun isInterpreterGenerated(): Boolean =
                delegate.isInterpreterGenerated()
        }
    }
}

class TinkerOatManagerTestPatchService : TinkerOatManagerTestService() {

    override val binder by lazy {
        object : ITinkerOatManagerTestPatchService.Stub() {
            override fun generateIfNeeded(patch: ParcelableTinkerPatch) {
                delegate.generateIfNeeded(applicationContext, patch)
            }

            override fun clean(version: String): Boolean =
                delegate.clean(applicationContext, version)

            override fun invalidAcquire(
                patch: ParcelableTinkerPatch,
                skipGenerateIfMissing: Boolean
            ): String? = delegate.acquire(applicationContext, patch, skipGenerateIfMissing)

            override fun invalidRelease() {
                delegate.release(applicationContext)
            }

            override fun setInterpreterIsInvalid() {
                delegate.setPatchProcessInterpreterAsInvalid()
            }

            override fun useSuccessCompiler() {
                delegate.useSuccessCompilerForPatchProcess()
            }

            override fun useFailureCompiler() {
                delegate.useFailureCompiler()
            }

            override fun useExceptionCompiler() {
                delegate.useExceptionCompiler()
            }

            override fun isCompilerGenerated(): Boolean =
                delegate.isCompilerGenerated()
        }
    }
}