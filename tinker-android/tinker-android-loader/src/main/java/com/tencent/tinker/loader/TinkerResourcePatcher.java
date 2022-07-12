/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tinker.loader;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.KITKAT;
import static com.tencent.tinker.loader.shareutil.ShareReflectUtil.findConstructor;
import static com.tencent.tinker.loader.shareutil.ShareReflectUtil.findField;
import static com.tencent.tinker.loader.shareutil.ShareReflectUtil.findMethod;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.ArrayMap;

import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.ShareReflectUtil;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;
import com.tencent.tinker.loader.shareutil.ShareTinkerLog;

import java.io.File;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by zhangshaowen on 16/9/21.
 * Thanks for Android Fragmentation
 */
class TinkerResourcePatcher {
    private static final String TAG = "Tinker.ResourcePatcher";
    private static final String TEST_ASSETS_VALUE = "only_use_to_test_tinker_resource.txt";

    // original object
    private static Collection<WeakReference<Resources>> references = null;
    private static Object currentActivityThread = null;
    private static AssetManager newAssetManager = null;

    // method
    private static Constructor<?> newAssetManagerCtor = null;
    private static Method addAssetPathMethod = null;
    private static Method addAssetPathAsSharedLibraryMethod = null;
    private static Method ensureStringBlocksMethod = null;

    // field
    private static Field assetsFiled = null;
    private static Field resourcesImplFiled = null;
    private static Field resDir = null;
    private static Field packagesFiled = null;
    private static Field resourcePackagesFiled = null;
    private static Field publicSourceDirField = null;
    private static Field stringBlocksField = null;

    private static long storedPatchedResModifiedTime = 0L;

    @SuppressWarnings("unchecked")
    public static void isResourceCanPatch(Context context) throws Throwable {
        //   - Replace mResDir to point to the external resource file instead of the .apk. This is
        //     used as the asset path for new Resources objects.
        //   - Set Application#mLoadedApk to the found LoadedApk instance

        // Find the ActivityThread instance for the current thread
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        currentActivityThread = ShareReflectUtil.getActivityThread(context, activityThread);

        // API version 8 has PackageInfo, 10 has LoadedApk. 9, I don't know.
        Class<?> loadedApkClass;
        try {
            loadedApkClass = Class.forName("android.app.LoadedApk");
        } catch (ClassNotFoundException e) {
            loadedApkClass = Class.forName("android.app.ActivityThread$PackageInfo");
        }

        resDir = findField(loadedApkClass, "mResDir");
        packagesFiled = findField(activityThread, "mPackages");
        if (Build.VERSION.SDK_INT < 27) {
            resourcePackagesFiled = findField(activityThread, "mResourcePackages");
        }

        // Create a new AssetManager instance and point it to the resources
        final AssetManager assets = context.getAssets();
        addAssetPathMethod = findMethod(assets, "addAssetPath", String.class);
        if (shouldAddSharedLibraryAssets(context.getApplicationInfo())) {
            addAssetPathAsSharedLibraryMethod =
                    findMethod(assets, "addAssetPathAsSharedLibrary", String.class);
        }

        // Kitkat needs this method call, Lollipop doesn't. However, it doesn't seem to cause any harm
        // in L, so we do it unconditionally.
        try {
            stringBlocksField = findField(assets, "mStringBlocks");
            ensureStringBlocksMethod = findMethod(assets, "ensureStringBlocks");
        } catch (Throwable ignored) {
            // Ignored.
        }

        // Use class fetched from instance to avoid some ROMs that use customized AssetManager
        // class. (e.g. Baidu OS)
        newAssetManagerCtor = findConstructor(assets);

        // Iterate over all known Resources objects
        if (SDK_INT >= KITKAT) {
            //pre-N
            // Find the singleton instance of ResourcesManager
            final Class<?> resourcesManagerClass = Class.forName("android.app.ResourcesManager");
            final Method mGetInstance = findMethod(resourcesManagerClass, "getInstance");
            final Object resourcesManager = mGetInstance.invoke(null);
            try {
                Field fMActiveResources = findField(resourcesManagerClass, "mActiveResources");
                final ArrayMap<?, WeakReference<Resources>> activeResources19 =
                        (ArrayMap<?, WeakReference<Resources>>) fMActiveResources.get(resourcesManager);
                references = activeResources19.values();
            } catch (NoSuchFieldException ignore) {
                // N moved the resources to mResourceReferences
                final Field mResourceReferences = findField(resourcesManagerClass, "mResourceReferences");
                references = (Collection<WeakReference<Resources>>) mResourceReferences.get(resourcesManager);
            }
        } else {
            final Field fMActiveResources = findField(activityThread, "mActiveResources");
            final HashMap<?, WeakReference<Resources>> activeResources7 =
                    (HashMap<?, WeakReference<Resources>>) fMActiveResources.get(currentActivityThread);
            references = activeResources7.values();
        }
        // check resource
        if (references == null) {
            throw new IllegalStateException("resource references is null");
        }

        final Resources resources = context.getResources();

        // fix jianGuo pro has private field 'mAssets' with Resource
        // try use mResourcesImpl first
        if (SDK_INT >= 24) {
            try {
                // N moved the mAssets inside an mResourcesImpl field
                resourcesImplFiled = findField(resources, "mResourcesImpl");
            } catch (Throwable ignore) {
                // for safety
                assetsFiled = findField(resources, "mAssets");
            }
        } else {
            assetsFiled = findField(resources, "mAssets");
        }

        try {
            publicSourceDirField = findField(ApplicationInfo.class, "publicSourceDir");
        } catch (NoSuchFieldException ignore) {
            // Ignored.
        }
    }

    /**
     * @param context
     * @param externalResourceFile
     * @throws Throwable
     */
    public static void monkeyPatchExistingResources(Context context, String externalResourceFile, boolean isReInject) throws Throwable {
        if (externalResourceFile == null) {
            return;
        }

        final ApplicationInfo appInfo = context.getApplicationInfo();

        final Field[] packagesFields;
        if (Build.VERSION.SDK_INT < 27) {
            packagesFields = new Field[]{packagesFiled, resourcePackagesFiled};
        } else {
            packagesFields = new Field[]{packagesFiled};
        }
        for (Field field : packagesFields) {
            final Object value = field.get(currentActivityThread);

            for (Map.Entry<String, WeakReference<?>> entry
                    : ((Map<String, WeakReference<?>>) value).entrySet()) {
                final Object loadedApk = entry.getValue().get();
                if (loadedApk == null) {
                    continue;
                }
                final String resDirPath = (String) resDir.get(loadedApk);
                if (appInfo.sourceDir.equals(resDirPath)) {
                    resDir.set(loadedApk, externalResourceFile);
                }
            }
        }

        if (isReInject) {
            ShareTinkerLog.i(TAG, "Re-injecting, skip rest logic.");
            recordCurrentPatchedResModifiedTime(externalResourceFile);
            return;
        }

        newAssetManager = (AssetManager) newAssetManagerCtor.newInstance();
        // Create a new AssetManager instance and point it to the resources installed under
        if (((Integer) addAssetPathMethod.invoke(newAssetManager, externalResourceFile)) == 0) {
            throw new IllegalStateException("Could not create new AssetManager");
        }
        recordCurrentPatchedResModifiedTime(externalResourceFile);

        // Add SharedLibraries to AssetManager for resolve system resources not found issue
        // This influence SharedLibrary Package ID
        if (shouldAddSharedLibraryAssets(appInfo)) {
            for (String sharedLibrary : appInfo.sharedLibraryFiles) {
                if (!sharedLibrary.endsWith(".apk")) {
                    continue;
                }
                if (((Integer) addAssetPathAsSharedLibraryMethod.invoke(newAssetManager, sharedLibrary)) == 0) {
                    throw new IllegalStateException("AssetManager add SharedLibrary Fail");
                }
                ShareTinkerLog.i(TAG, "addAssetPathAsSharedLibrary " + sharedLibrary);
            }
        }

        // Kitkat needs this method call, Lollipop doesn't. However, it doesn't seem to cause any harm
        // in L, so we do it unconditionally.
        if (stringBlocksField != null && ensureStringBlocksMethod != null) {
            stringBlocksField.set(newAssetManager, null);
            ensureStringBlocksMethod.invoke(newAssetManager);
        }

        for (WeakReference<Resources> wr : references) {
            final Resources resources = wr.get();
            if (resources == null) {
                continue;
            }
            // Set the AssetManager of the Resources instance to our brand new one
            try {
                //pre-N
                assetsFiled.set(resources, newAssetManager);
            } catch (Throwable ignore) {
                // N
                final Object resourceImpl = resourcesImplFiled.get(resources);
                // for Huawei HwResourcesImpl
                final Field implAssets = findField(resourceImpl, "mAssets");
                implAssets.set(resourceImpl, newAssetManager);
            }

            clearPreloadTypedArrayIssue(resources);

            resources.updateConfiguration(resources.getConfiguration(), resources.getDisplayMetrics());
        }

        // Handle issues caused by WebView on Android N.
        // Issue: On Android N, if an activity contains a webview, when screen rotates
        // our resource patch may lost effects.
        // for 5.x/6.x, we found Couldn't expand RemoteView for StatusBarNotification Exception
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                if (publicSourceDirField != null) {
                    publicSourceDirField.set(context.getApplicationInfo(), externalResourceFile);
                }
            } catch (Throwable ignore) {
                // Ignored.
            }
        }

        if (!checkResUpdate(context)) {
            throw new TinkerRuntimeException(ShareConstants.CHECK_RES_INSTALL_FAIL);
        }

        installResourceInsuranceHacks(context, externalResourceFile);
    }

    private static void installResourceInsuranceHacks(Context context, String patchedResApkPath) {
        try {
            final Object activityThread = ShareReflectUtil.getActivityThread(context, null);
            final Field mHField = ShareReflectUtil.findField(activityThread, "mH");
            final Handler mH = (Handler) mHField.get(activityThread);
            final Field mCallbackField = ShareReflectUtil.findField(Handler.class, "mCallback");
            final Handler.Callback originCallback = (Handler.Callback) mCallbackField.get(mH);
            if (!(originCallback instanceof ResourceInsuranceHandlerCallback)) {
                final ResourceInsuranceHandlerCallback hackCallback = new ResourceInsuranceHandlerCallback(
                        context, patchedResApkPath, originCallback, mH.getClass());
                mCallbackField.set(mH, hackCallback);
            } else {
                ShareTinkerLog.w(TAG, "installResourceInsuranceHacks: already installed, skip rest logic.");
            }
        } catch (Throwable thr) {
            ShareTinkerLog.printErrStackTrace(TAG, thr, "failed to install resource insurance hack.");
        }
    }

    private static final class ResourceInsuranceHandlerCallback implements Handler.Callback {
        private static final String LAUNCH_ACTIVITY_LIFECYCLE_ITEM_CLASSNAME = "android.app.servertransaction.LaunchActivityItem";

        private final Context mContext;
        private final String mPatchResApkPath;
        private final Handler.Callback mOriginalCallback;

        private final int LAUNCH_ACTIVITY;
        private final int RELAUNCH_ACTIVITY;
        private final int EXECUTE_TRANSACTION;

        private Method mGetCallbacksMethod = null;
        private boolean mSkipInterceptExecuteTransaction = false;

        ResourceInsuranceHandlerCallback(Context context, String patchResApkPath, Handler.Callback original, Class<?> hClazz) {
            Context appContext = context.getApplicationContext();
            mContext = (appContext != null ? appContext : context);
            mPatchResApkPath = patchResApkPath;
            mOriginalCallback = original;
            LAUNCH_ACTIVITY = fetchMessageId(hClazz, "LAUNCH_ACTIVITY", 100);
            RELAUNCH_ACTIVITY = fetchMessageId(hClazz, "RELAUNCH_ACTIVITY", 126);

            if (ShareTinkerInternals.isNewerOrEqualThanVersion(28, true)) {
                EXECUTE_TRANSACTION  = fetchMessageId(hClazz, "EXECUTE_TRANSACTION ", 159);
            } else {
                EXECUTE_TRANSACTION = -1;
            }
        }

        private int fetchMessageId(Class<?> hClazz, String name, int defVal) {
            int value;
            try {
                value = ShareReflectUtil.findField(hClazz, name).getInt(null);
            } catch (Throwable e) {
                value = defVal;
            }
            return value;
        }

        @Override
        public boolean handleMessage(Message msg) {
            boolean consume = false;
            if (hackMessage(msg)) {
                consume = true;
            } else if (mOriginalCallback != null) {
                consume = mOriginalCallback.handleMessage(msg);
            }
            return consume;
        }

        @SuppressWarnings("unchecked")
        private boolean hackMessage(Message msg) {
            boolean shouldReInjectPatchedResources = false;
            if (!isPatchedResModifiedAfterLastLoad(mPatchResApkPath)) {
                shouldReInjectPatchedResources = false;
            } else {
                if (msg.what == LAUNCH_ACTIVITY || msg.what == RELAUNCH_ACTIVITY) {
                    shouldReInjectPatchedResources = true;
                } else if (msg.what == EXECUTE_TRANSACTION) {
                    do {
                        if (mSkipInterceptExecuteTransaction) {
                            break;
                        }
                        final Object transaction = msg.obj;
                        if (transaction == null) {
                            ShareTinkerLog.w(TAG, "transaction is null, skip rest insurance logic.");
                            break;
                        }
                        if (mGetCallbacksMethod == null) {
                            try {
                                mGetCallbacksMethod = ShareReflectUtil.findMethod(transaction, "getCallbacks");
                            } catch (Throwable ignored) {
                                // Ignored.
                            }
                        }
                        if (mGetCallbacksMethod == null) {
                            ShareTinkerLog.e(TAG, "fail to find getLifecycleStateRequest method, skip rest insurance logic.");
                            mSkipInterceptExecuteTransaction = true;
                            break;
                        }
                        try {
                            final List<Object> req = (List<Object>) mGetCallbacksMethod.invoke(transaction);
                            if (req != null && req.size() > 0) {
                                final Object cb = req.get(0);
                                shouldReInjectPatchedResources = cb != null && cb.getClass().getName().equals(LAUNCH_ACTIVITY_LIFECYCLE_ITEM_CLASSNAME);
                            }
                        } catch (Throwable ignored) {
                            ShareTinkerLog.e(TAG, "fail to call getLifecycleStateRequest method, skip rest insurance logic.");
                        }
                    } while (false);
                }
            }
            if (shouldReInjectPatchedResources) {
                try {
                    monkeyPatchExistingResources(mContext, mPatchResApkPath, true);
                } catch (Throwable thr) {
                    ShareTinkerLog.printErrStackTrace(TAG, thr, "fail to ensure patched resources available after it's modified.");
                }
            }
            return false;
        }
    }

    private static boolean isPatchedResModifiedAfterLastLoad(String patchedResPath) {
        long patchedResModifiedTime;
        try {
            patchedResModifiedTime = new File(patchedResPath).lastModified();
        } catch (Throwable thr) {
            ShareTinkerLog.printErrStackTrace(TAG, thr, "Fail to get patched res modified time.");
            patchedResModifiedTime = 0L;
        }
        if (patchedResModifiedTime == 0) {
            return false;
        }
        if (patchedResModifiedTime == storedPatchedResModifiedTime) {
            return false;
        }
        return true;
    }

    private static void recordCurrentPatchedResModifiedTime(String patchedResPath) {
        try {
            storedPatchedResModifiedTime = new File(patchedResPath).lastModified();
        } catch (Throwable thr) {
            ShareTinkerLog.printErrStackTrace(TAG, thr, "Fail to store patched res modified time.");
            storedPatchedResModifiedTime = 0L;
        }
    }

    /**
     * Why must I do these?
     * Resource has mTypedArrayPool field, which just like Message Poll to reduce gc
     * MiuiResource change TypedArray to MiuiTypedArray, but it get string block from offset instead of assetManager
     */
    private static void clearPreloadTypedArrayIssue(Resources resources) {
        // Perform this trick not only in Miui system since we can't predict if any other
        // manufacturer would do the same modification to Android.
        // if (!isMiuiSystem) {
        //     return;
        // }
        ShareTinkerLog.w(TAG, "try to clear typedArray cache!");
        // Clear typedArray cache.
        try {
            final Field typedArrayPoolField = findField(Resources.class, "mTypedArrayPool");
            final Object origTypedArrayPool = typedArrayPoolField.get(resources);
            final Method acquireMethod = findMethod(origTypedArrayPool, "acquire");
            while (true) {
                if (acquireMethod.invoke(origTypedArrayPool) == null) {
                    break;
                }
            }
        } catch (Throwable ignored) {
            ShareTinkerLog.e(TAG, "clearPreloadTypedArrayIssue failed, ignore error: " + ignored);
        }
    }

    private static boolean checkResUpdate(Context context) {
        InputStream is = null;
        try {
            is = context.getAssets().open(TEST_ASSETS_VALUE);
        } catch (Throwable e) {
            ShareTinkerLog.e(TAG, "checkResUpdate failed, can't find test resource assets file " + TEST_ASSETS_VALUE + " e:" + e.getMessage());
            return false;
        } finally {
            SharePatchFileUtil.closeQuietly(is);
        }
        ShareTinkerLog.i(TAG, "checkResUpdate success, found test resource assets file " + TEST_ASSETS_VALUE);
        return true;
    }

    private static boolean shouldAddSharedLibraryAssets(ApplicationInfo applicationInfo) {
        return SDK_INT >= Build.VERSION_CODES.N && applicationInfo != null &&
                applicationInfo.sharedLibraryFiles != null;
    }
}
