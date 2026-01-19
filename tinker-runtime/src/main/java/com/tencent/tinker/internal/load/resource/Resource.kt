package com.tencent.tinker.internal.load.resource

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Message
import androidx.annotation.RequiresApi
import com.tencent.tinker.Tinker
import com.tencent.tinker.internal.Patch
import com.tencent.tinker.internal.TEST_ADDED_ASSET_FILE_NAME
import com.tencent.tinker.internal.TEST_ASSETS_DIRECTORY_NAME
import com.tencent.tinker.internal.TEST_MODIFIED_ASSET_FILE_NAME
import com.tencent.tinker.internal.TEST_REMOVED_ASSET_FILE_NAME
import com.tencent.tinker.internal.load.ActivityThreadDelegate
import com.tencent.tinker.internal.load.AssetManagerDelegate
import com.tencent.tinker.internal.load.ClientTransactionDelegate
import com.tencent.tinker.internal.load.ClientTransactionDelegate.Companion.delegatedAsClientTransaction
import com.tencent.tinker.internal.load.LoadedApkDelegate
import com.tencent.tinker.internal.load.Loader
import com.tencent.tinker.internal.load.ResourceImplementationDelegate
import com.tencent.tinker.internal.load.ResourceKeyDelegate
import com.tencent.tinker.internal.load.ResourceManagerDelegate
import com.tencent.tinker.internal.load.ResourcesDelegate
import com.tencent.tinker.internal.util.expected
import com.tencent.tinker.internal.util.warnLog
import java.io.File
import java.io.IOException

private const val TAG = "Tinker.Load.Res"

private class CurrentActivityThreadUpdater(
    private val original: String,
    private val updated: String,
) : () -> Unit {

    private val getCurrentActivityThreadReferencedPackages: () -> List<LoadedApkDelegate> =
        ActivityThreadDelegate.currentActivityThread.referencedPackagesSelfGetter

    private val getResourceDirectory: LoadedApkDelegate.() -> String =
        LoadedApkDelegate.resDirGetter

    private val setResourceDirectory: LoadedApkDelegate.(String) -> Unit =
        LoadedApkDelegate.resDirSetter

    override fun invoke() {
        getCurrentActivityThreadReferencedPackages().forEach {
            if (original != it.getResourceDirectory()) {
                return@forEach
            }
            it.setResourceDirectory(updated)
        }
    }
}

private class ResourceManagerUpdater(
    private val assetManager: AssetManagerDelegate,
    private val original: String,
    private val updated: String,
) : () -> Unit {

    private class ReferencedResources(
        private val assetManager: AssetManagerDelegate,
    ) : () -> Unit {

        private val getResourceManagerReferencedResources: () -> List<ResourcesDelegate> =
            ResourceManagerDelegate.instance.referencedResourcesSelfGetter

        private val setAssetManager: ResourcesDelegate.(AssetManagerDelegate) -> Unit =
            ResourcesDelegate.assetsSetter

        override fun invoke() {
            getResourceManagerReferencedResources().forEach {
                it.setAssetManager(assetManager)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private class ResourceImplementationsUpdater(
        private val assetManager: AssetManagerDelegate,
        private val original: String,
        private val updated: String,
    ) : () -> Unit {

        private val getResourceManagerResourceImplementations: () -> List<Pair<ResourceKeyDelegate, ResourceImplementationDelegate?>> =
            ResourceManagerDelegate.instance.resourceImplementationsSelfGetter

        private val getResourceDirectory: ResourceKeyDelegate.() -> String =
            ResourceKeyDelegate.resourceDirectoryGetter

        private val setResourceDirectory: ResourceKeyDelegate.(String) -> Unit =
            ResourceKeyDelegate.resourceDirectorySetter

        private val setAssetManager: ResourceImplementationDelegate.(AssetManagerDelegate) -> Unit =
            ResourceImplementationDelegate.assetsSetter

        override fun invoke() {
            getResourceManagerResourceImplementations().forEach { (key, impl) ->
                if (original != key.getResourceDirectory()) {
                    return@forEach
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    key.setResourceDirectory(updated)
                }
                impl?.setAssetManager(assetManager)
            }
        }
    }

    private val actions =
        buildList {
            ReferencedResources(
                assetManager = assetManager,
            ).let(::add)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                ResourceImplementationsUpdater(
                    assetManager = assetManager,
                    original = original,
                    updated = updated,
                ).let(::add)
            }
        }

    override fun invoke() {
        actions.forEach { it.invoke() }
    }
}

/**
 * Tries resolving issues caused by WebView on Android N.
 *
 * On Android N, if an activity contains a webview, our resource patch may lost effects if screen
 * rotates.
 */
@RequiresApi(Build.VERSION_CODES.N)
private class PublicSourceDirectoryUpdater(
    private val context: Context,
    private val updated: String,
) : () -> Unit {
    override fun invoke() {
        context.applicationInfo.publicSourceDir = updated
    }
}

internal class ResourceLoader(
    private val context: Context,
    private val resourceApk: File,
    private val loaders: Iterable<() -> Unit>,
    private val injectInsuranceCallback: ((Long) -> Unit)?,
) : Loader() {

    override fun load() {
        doLoad()
        verify()
    }

    private fun doLoad() {
        val initialUpdatedTimestamp = resourceApk.lastModified()
        loaders.forEach {
            it.invoke()
        }
        injectInsuranceCallback?.invoke(initialUpdatedTimestamp)
    }

    private fun verify() {
        val added = context.assets
            .open(TEST_ADDED_ASSET_FILE_NAME)
            .use { it.readBytes() }
            .toString(Charsets.UTF_8)
        if (added != "patched") {
            throw Tinker.Error(
                Tinker.Error.Load.Resource.VERIFY_FAILED,
                "Cannot load patch-added test asset.",
            )
        }
        val modified = context.assets
            .open(TEST_MODIFIED_ASSET_FILE_NAME)
            .use { it.readBytes() }
            .toString(Charsets.UTF_8)
        if (modified != "patched") {
            throw Tinker.Error(
                Tinker.Error.Load.Resource.VERIFY_FAILED,
                "Cannot load patch-modified test asset.",
            )
        }
        try {
            context.assets.open(TEST_REMOVED_ASSET_FILE_NAME)
            throw Tinker.Error(
                Tinker.Error.Load.Resource.VERIFY_FAILED,
                "Patch-removed test asset is still exists.",
            )
        } catch (_: IOException) {
            // Expected.
        }
    }

    private class InsuranceHandlerCallback private constructor(
        private val resourceApk: File,
        private val reloadTriggerMessageIds: IntArray,
        private val transactionMessageId: Int?,
        private val loaders: Iterable<() -> Unit>,
        private val original: Handler.Callback?,
        initialUpdatedTimestamp: Long,
    ) : Handler.Callback {

        class Injector(
            private val resourceApk: File,
            private val reloadTriggerMessageIds: IntArray,
            private val transactionMessageId: Int?,
            private val loaders: Iterable<() -> Unit>,
            private val getCallback: () -> Handler.Callback,
            private val setCallback: (Handler.Callback) -> Unit,
        ) : (Long) -> Unit {
            override fun invoke(initialUpdatedTimestamp: Long) {
                val original = getCallback()
                if (original is InsuranceHandlerCallback) {
                    return
                }
                InsuranceHandlerCallback(
                    resourceApk =
                        resourceApk,
                    reloadTriggerMessageIds =
                        reloadTriggerMessageIds,
                    transactionMessageId =
                        transactionMessageId,
                    loaders =
                        loaders,
                    original =
                        original,
                    initialUpdatedTimestamp =
                        initialUpdatedTimestamp,
                ).let(setCallback)
            }
        }

        companion object {
            private const val LAUNCH_ACTIVITY_ITEM_CLASS_NAME =
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
                loaders.forEach { it.invoke() }
            }
            return original?.handleMessage(message) ?: false
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

        private var cachedCallbacksGetter: (ClientTransactionDelegate.() -> List<Any>?)? = null

        private fun shouldReloadWhileTransacting(message: Message): Boolean {
            if (interceptTransactingFuse) {
                return false
            }
            val transaction = message.obj
                ?.delegatedAsClientTransaction
                ?: return false
            val getCallbacks = cachedCallbacksGetter
                ?: try {
                    transaction.callbacksGetter.also {
                        cachedCallbacksGetter = it
                    }
                } catch (throwable: Throwable) {
                    warnLog(TAG, throwable = throwable) {
                        "Cannot get \"getCallbacks\" method from transaction" +
                                " with type <${transaction.javaClass.name}>."
                    }
                    interceptTransactingFuse = true
                    return false
                }
            try {
                return transaction.getCallbacks()
                    ?.firstOrNull()
                    ?.takeIf {
                        it.javaClass.name == LAUNCH_ACTIVITY_ITEM_CLASS_NAME
                    } != null
            } catch (throwable: Throwable) {
                warnLog(TAG, throwable = throwable) {
                    "Invokes \"getCallbacks\" failed."
                }
                interceptTransactingFuse = true
                return false
            }
        }
    }

    class Factory(
        private val applicationContext: Context,
    ) : Loader.Factory() {

        private fun searchResourceApk(patch: Patch): File =
            patch.resourceApkFile
                .takeIf { it.isFile }
                ?: throw Tinker.Error(
                    Tinker.Error.Load.Resource.NO_VALID_INPUTS,
                    "Missing valid input resource apk \"${patch.resourceApkFile.absolutePath}\".",
                )

        override fun createLoaderIfNeeded(patch: Patch): ResourceLoader {
            expected<Tinker.Error.Load.Resource>("create resource loader") {
                return createLoader(
                    resourceApk = searchResourceApk(patch),
                )
            }
        }

        private fun createLoader(resourceApk: File): ResourceLoader {

            val currentActivityThreadUpdater =
                CurrentActivityThreadUpdater(
                    original =
                        applicationContext.applicationInfo.sourceDir,
                    updated =
                        resourceApk.absolutePath,
                )

            val loaders = buildList {
                currentActivityThreadUpdater
                    .let(::add)

                val updatedAssets =
                    AssetManagerDelegate.createInstanceLike(applicationContext.assets)
                        .apply {
                            addAssetPath(resourceApk.absolutePath)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                applicationContext.applicationInfo.sharedLibraryFiles.forEach { path ->
                                    if (!path.endsWith(".apk")) {
                                        return@forEach
                                    }
                                    addAssetPathAsSharedLibrary(path)
                                }
                            }
                            initializeStringBlocksIfNeeded()
                        }

                ResourceManagerUpdater(
                    assetManager =
                        updatedAssets,
                    original =
                        applicationContext.applicationInfo.sourceDir,
                    updated =
                        resourceApk.absolutePath,
                ).let(::add)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    PublicSourceDirectoryUpdater(
                        context =
                            applicationContext,
                        updated =
                            resourceApk.absolutePath,
                    ).let(::add)
                }
            }

            // Creates new handler callback for intercepting ones from current activity thread.
            //
            // When last modified time of resources apk file is changed in runtime, the resource manager will trigger
            // resources reload. We have to inject patch resources again once lifecycle state is changed.
            val activityThread = ActivityThreadDelegate.currentActivityThread
            val handler = activityThread.handler
            val callbackInjector = InsuranceHandlerCallback.Injector(
                resourceApk =
                    resourceApk,
                reloadTriggerMessageIds =
                    buildList {
                        handler.launchActivityMessageId?.let(::add)
                        handler.relaunchActivityMessageId?.let(::add)
                    }.toIntArray(),
                transactionMessageId =
                    handler.transactionMessageId,
                loaders = listOf(
                    currentActivityThreadUpdater
                ),
                getCallback =
                    handler.callbackSelfGetter,
                setCallback =
                    handler.callbackSelfSetter,
            )

            return ResourceLoader(
                context =
                    applicationContext,
                resourceApk =
                    resourceApk,
                loaders =
                    loaders,
                injectInsuranceCallback =
                    callbackInjector,
            )
        }
    }
}