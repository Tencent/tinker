package com.tencent.tinker.test.internal.modules.patch

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import java.io.File
import com.tencent.tinker.test.internal.ParcelableTinkerPatch



abstract class TinkerPatchManagerTestService : Service() {

    interface Delegate {
        fun acquire(context: Context): ParcelableTinkerPatch?
        fun requestUnavailable(context: Context, version: String)
        fun releaseAllEscapedGuardedContent()
        fun create(context: Context, version: String, patch: File)
        fun cleanAll(context: Context): Array<String>
        fun cleanObsolete(context: Context): Array<String>
        val isRequestUnavailableListenerInvoked: Boolean
    }

    protected val delegate by lazy {
        Class.forName(Delegate::class.java.name.substringBeforeLast('.') + ".TinkerPatchManagerTestServiceDelegateImpl")
            .getDeclaredConstructor()
            .newInstance()
            .let { it as Delegate }
    }

    abstract val binder: IBinder

    override fun onBind(intent: Intent): IBinder = binder
}

class TinkerPatchManagerTestMainService : TinkerPatchManagerTestService() {

    override val binder by lazy {
        object : ITinkerPatchManagerTestMainService.Stub() {
            override fun acquire(): ParcelableTinkerPatch? =
                delegate.acquire(applicationContext)

            override fun requestUnavailable(version: String) {
                delegate.requestUnavailable(applicationContext, version)
            }

            override fun assumeProcessIsDead() {
                delegate.releaseAllEscapedGuardedContent()
            }

            override fun isRequestUnavailableListenerInvoked(): Boolean =
                delegate.isRequestUnavailableListenerInvoked
        }
    }
}

class TinkerPatchManagerTestOthersService : TinkerPatchManagerTestService() {

    override val binder by lazy {
        object : ITinkerPatchManagerTestOthersService.Stub() {
            override fun acquire(): ParcelableTinkerPatch? =
                delegate.acquire(applicationContext)

            override fun requestUnavailable(version: String) {
                delegate.requestUnavailable(applicationContext, version)
            }

            override fun assumeProcessIsDead() {
                delegate.releaseAllEscapedGuardedContent()
            }
        }
    }
}

class TinkerPatchManagerTestPatchService : TinkerPatchManagerTestService() {
    override val binder by lazy {
        object : ITinkerPatchManagerTestPatchService.Stub() {
            override fun create(version: String, patchPath: String) {
                delegate.create(applicationContext, version, patchPath.let(::File))
            }

            override fun cleanAll(): Array<String> =
                delegate.cleanAll(applicationContext)

            override fun cleanObsolete(): Array<String> =
                delegate.cleanObsolete(applicationContext)

            override fun invalidAcquire(): ParcelableTinkerPatch? =
                delegate.acquire(applicationContext)

            override fun invalidRequestUnavailable(version: String) {
                delegate.requestUnavailable(applicationContext, version)
            }
        }
    }
}

