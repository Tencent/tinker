package com.tencent.tinker.test.internal.module.layout

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import java.io.File

abstract class PatchLayoutConstructorTestService : Service() {

    interface Delegate {
        val processBaseDirectory: File
        fun construct(baseDirectory: File, oatDirectory: File?): File
        fun assumeProcessIsRestarted()
    }

    protected val delegate by lazy {
        Class.forName(Delegate::class.java.name.substringBeforeLast('.') + ".PatchLayoutConstructorDelegate")
            .getDeclaredConstructor(Context::class.java)
            .newInstance(applicationContext)
            .let { it as Delegate }
    }

    abstract val binder: IBinder

    override fun onBind(intent: Intent): IBinder = binder
}

class PatchLayoutConstructorTestMainService : PatchLayoutConstructorTestService() {

    override val binder by lazy {
        object : IPatchLayoutConstructorTestMainService.Stub() {
            override fun processBaseDirectory(): String =
                delegate.processBaseDirectory.absolutePath

            override fun construct(
                baseDirectoryPath: String,
                oatDirectoryPath: String?,
            ): String = delegate
                .construct(
                    baseDirectoryPath.let(::File),
                    oatDirectoryPath?.let(::File)
                )
                .absolutePath

            override fun assumeProcessIsRestarted() {
                delegate.assumeProcessIsRestarted()
            }
        }
    }
}

class PatchLayoutConstructorTestOthersService : PatchLayoutConstructorTestService() {

    override val binder by lazy {
        object : IPatchLayoutConstructorTestOthersService.Stub() {
            override fun processBaseDirectory(): String =
                delegate.processBaseDirectory.absolutePath

            override fun construct(
                baseDirectoryPath: String,
                oatDirectoryPath: String?,
            ): String = delegate
                .construct(
                    baseDirectoryPath.let(::File),
                    oatDirectoryPath?.let(::File)
                )
                .absolutePath

            override fun assumeProcessIsRestarted() {
                delegate.assumeProcessIsRestarted()
            }
        }
    }
}

class PatchLayoutConstructorTestDeployService : PatchLayoutConstructorTestService() {

    override val binder by lazy {
        object : IPatchLayoutConstructorTestDeployService.Stub() {

            override fun invalidConstruct(
                baseDirectoryPath: String,
                oatDirectoryPath: String?,
            ): String = delegate
                .construct(
                    baseDirectoryPath.let(::File),
                    oatDirectoryPath?.let(::File)
                )
                .absolutePath
        }
    }
}