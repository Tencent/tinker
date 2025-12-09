package com.tencent.tinker.test.internal.module.patch

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import java.io.File

abstract class PatchManagerTestService : Service() {

    interface Delegate {
        val baseDirectory: File
        val latestVersionFile: File
        fun patchDirectory(version: String): File
        fun acquire(): ParcelableRawPatch?
        fun requestUnavailable(version: String)
        fun releaseAllEscapedGuardedContent()
        fun create(version: String, patch: File)
        fun cleanAll(): Array<String>
        fun cleanObsolete(): Array<String>
        val isRequestUnavailableListenerInvoked: Boolean
    }

    protected val delegate by lazy {
        Class.forName(Delegate::class.java.name.substringBeforeLast('.') + ".PatchManagerDelegate")
            .getDeclaredConstructor(Context::class.java)
            .newInstance(applicationContext)
            .let { it as Delegate }
    }

    abstract val binder: IBinder

    override fun onBind(intent: Intent): IBinder = binder
}

class PatchManagerTestMainService : PatchManagerTestService() {

    override val binder by lazy {
        object : IPatchManagerTestMainService.Stub() {

            override fun baseDirectory(): String =
                delegate.baseDirectory.absolutePath

            override fun latestVersionFile(): String =
                delegate.latestVersionFile.absolutePath

            override fun patchDirectory(version: String): String =
                delegate.patchDirectory(version).absolutePath

            override fun invalidCreate(version: String, patchPath: String) {
                delegate.create(version, patchPath.let(::File))
            }

            override fun invalidCleanAll() {
                delegate.cleanAll()
            }

            override fun invalidCleanObsolete() {
                delegate.cleanObsolete()
            }

            override fun acquire(): ParcelableRawPatch? =
                delegate.acquire()

            override fun requestUnavailable(version: String) {
                delegate.requestUnavailable(version)
            }

            override fun assumeProcessIsDead() {
                delegate.releaseAllEscapedGuardedContent()
            }

            override fun isRequestUnavailableListenerInvoked(): Boolean =
                delegate.isRequestUnavailableListenerInvoked
        }
    }
}

class PatchManagerTestOthersService : PatchManagerTestService() {

    override val binder by lazy {
        object : IPatchManagerTestOthersService.Stub() {
            override fun acquire(): ParcelableRawPatch? =
                delegate.acquire()

            override fun requestUnavailable(version: String) {
                delegate.requestUnavailable(version)
            }

            override fun assumeProcessIsDead() {
                delegate.releaseAllEscapedGuardedContent()
            }
        }
    }
}

class PatchManagerTestPatchService : PatchManagerTestService() {
    override val binder by lazy {
        object : IPatchManagerTestPatchService.Stub() {
            override fun invalidAcquire(): ParcelableRawPatch? =
                delegate.acquire()

            override fun invalidRequestUnavailable(version: String) {
                delegate.requestUnavailable(version)
            }

            override fun create(version: String, patchPath: String) {
                delegate.create(version, patchPath.let(::File))
            }

            override fun cleanAll(): Array<String> =
                delegate.cleanAll()

            override fun cleanObsolete(): Array<String> =
                delegate.cleanObsolete()
        }
    }
}

