package com.tencent.tinker.internal.load.resource

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Message
import com.tencent.tinker.internal.Patch
import com.tencent.tinker.internal.TinkerError
import com.tencent.tinker.internal.load.Loader
import com.tencent.tinker.internal.module.hidden.ActivityThreadDelegate
import com.tencent.tinker.internal.module.hidden.AssetManagerDelegate
import com.tencent.tinker.internal.module.hidden.ResourceManagerDelegate
import com.tencent.tinker.internal.module.hidden.transactionGetCallbacks
import com.tencent.tinker.internal.util.expected
import com.tencent.tinker.internal.util.warnLog
import java.io.File

private const val TAG = "Tinker.Loader.Res"

private enum class ErrorType : TinkerError.Type {
    UNEXPECTED,
    NO_VALID_INPUTS;

    override val group: TinkerError.TypeGroup
        get() = TinkerError.TypeGroup.LOAD_RESOURCE

    override val typeCode: Int
        get() = ordinal
}

internal class ResourceLoader(
    private val context: Context,
    private val resourceApk: File
) : Loader() {

    companion object {
        private fun Context.replaceResourceDirectoriesOfCurrentActivityThreadPackages(
            resourceApk: File
        ) {
            val activityThread = ActivityThreadDelegate.currentActivityThread
            activityThread.referencedPackages.forEach {
                if (applicationInfo.sourceDir != it.resDir) {
                    return@forEach
                }
                it.resDir = resourceApk.absolutePath
            }
        }

        private fun Context.replaceAssetManager(
            resourceApk: File
        ) {
            val newAssetManager =
                AssetManagerDelegate.createInstanceLike(assets)
            newAssetManager.addAssetPath(resourceApk.absolutePath)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                applicationInfo.sharedLibraryFiles.forEach { path ->
                    if (!path.endsWith(".apk")) {
                        return@forEach
                    }
                    newAssetManager.addAssetPathAsSharedLibrary(path)
                }
            }
            newAssetManager.initializeStringBlocksIfNeeded()

            ResourceManagerDelegate.referencedResources.forEach {
                it.assets = newAssetManager
                it.clearPreloadTypedArrayIssue()
                it.refreshConfiguration()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ResourceManagerDelegate.resourceImplementations.forEach { (key, impl) ->
                    if (applicationInfo.sourceDir != key.resDir) {
                        return@forEach
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                        key.resDir = resourceApk.absolutePath
                    }
                    impl?.assets = newAssetManager
                }
            }
        }

        /**
         * Try resolving issues caused by WebView on Android N.
         *
         * On Android N, if an activity contains a webview, our resource patch may lost effects if
         * screen rotates.
         */
        private fun Context.replacePublicSourceDirectory(
            resourceApk: File
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                applicationInfo.publicSourceDir = resourceApk.absolutePath
            }
        }

        private fun Context.injectCallback(
            resourceApk: File,
            initialUpdatedTimestamp: Long,
        ) {
            val activityThread = ActivityThreadDelegate.currentActivityThread
            val handler = activityThread.handler
            val original = handler.callback
            if (original is InsuranceHandlerCallback) {
                return
            }
            handler.callback = InsuranceHandlerCallback(
                context = this,
                resourceApk = resourceApk,
                reloadTriggerMessageIds = buildList {
                    handler.launchActivityMessageId?.let(::add)
                    handler.relaunchActivityMessageId?.let(::add)
                }.toIntArray(),
                transactionMessageId = handler.transactionMessageId,
                original = original,
                initialUpdatedTimestamp = initialUpdatedTimestamp,
            )
        }
    }

    private class InsuranceHandlerCallback(
        private val context: Context,
        private val resourceApk: File,
        private val reloadTriggerMessageIds: IntArray,
        private val transactionMessageId: Int?,
        private val original: Handler.Callback,
        initialUpdatedTimestamp: Long,
    ) : Handler.Callback {

        companion object {
            private const val LAUNCH_ACTIVITY_LIFECYCLE_ITEM_CLASS_NAME =
                "android.app.servertransaction.LaunchActivityItem"
        }

        private var updatedTimestamp: Long = initialUpdatedTimestamp

        private fun checkAndUpdateTimestampIfNeeded(): Boolean {
            val lastModified = resourceApk.lastModified()
            if (lastModified == updatedTimestamp) {
                return false
            }
            updatedTimestamp = lastModified
            return true
        }

        override fun handleMessage(message: Message): Boolean {
            if (shouldReload(message)) {
                context.replaceResourceDirectoriesOfCurrentActivityThreadPackages(resourceApk)
            }
            return original.handleMessage(message)
        }

        private fun shouldReload(message: Message): Boolean {
            if (!checkAndUpdateTimestampIfNeeded()) {
                return false
            }
            return when (message.what) {
                in reloadTriggerMessageIds -> true
                transactionMessageId -> shouldReloadWhileTransacting(message)
                else -> false
            }
        }

        private var interceptTransactingFuse = false

        private var cachedGetCallbacks: (Any.() -> List<Any>?)? = null

        private fun shouldReloadWhileTransacting(message: Message): Boolean {
            if (interceptTransactingFuse) {
                return false
            }
            val transaction = message.obj ?: return false
            val getCallbacks = cachedGetCallbacks
                ?: try {
                    transaction.transactionGetCallbacks.also {
                        cachedGetCallbacks = it
                    }
                } catch (throwable: Throwable) {
                    warnLog(
                        TAG,
                        "Cannot get \"getCallbacks\" method from transaction with type \"${transaction.javaClass.name}\"",
                        throwable,
                    )
                    interceptTransactingFuse = true
                    return false
                }
            try {
                return transaction.getCallbacks()
                    ?.firstOrNull()
                    ?.takeIf {
                        it.javaClass.name == LAUNCH_ACTIVITY_LIFECYCLE_ITEM_CLASS_NAME
                    } != null
            } catch (throwable: Throwable) {
                warnLog(
                    TAG,
                    "Invokes \"getCallbacks\" failed.",
                    throwable,
                )
                interceptTransactingFuse = true
                return false
            }
        }
    }

    override fun load() {
        doLoad()
        verify()
    }

    private fun doLoad() {
        val initialUpdatedTimestamp = resourceApk.lastModified()
        context.replaceResourceDirectoriesOfCurrentActivityThreadPackages(resourceApk)
        context.replaceAssetManager(resourceApk)
        context.replacePublicSourceDirectory(resourceApk)
        context.injectCallback(resourceApk, initialUpdatedTimestamp)
    }

    private fun verify() {
        // TODO
    }

    class Factory(
        private val context: Context,
    ) : Loader.Factory() {

        private fun searchResourceApk(patch: Patch): File =
            patch.resourceApkFile
                .takeIf { it.isFile }
                ?: throw TinkerError(
                    ErrorType.NO_VALID_INPUTS,
                    "Missing valid input resource apk \"${patch.resourceApkFile.absolutePath}\".",
                )

        override fun createLoaderIfNeeded(patch: Patch): ResourceLoader {
            expected<ErrorType>("create resource loader") {
                return createLoader(
                    resourceApk = searchResourceApk(patch),
                )
            }
        }

        private fun createLoader(resourceApk: File): ResourceLoader =
            ResourceLoader(context, resourceApk)
    }
}