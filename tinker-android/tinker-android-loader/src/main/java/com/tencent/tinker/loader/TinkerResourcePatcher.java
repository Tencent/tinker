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

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;
import android.util.ArrayMap;

import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.ShareReflectUtil;
import com.tencent.tinker.loader.shareutil.ShareTinkerLog;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
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

    private static Map<Object, WeakReference<Object>> resourceImpls = null;
    private static Object currentActivityThread = null;

    // method
    private static Constructor<?> newAssetManagerCtor = null;
    private static Method addAssetPathMethod = null;
    private static Method addAssetPathAsSharedLibraryMethod = null;
    private static Method ensureStringBlocksMethod = null;

    // field
    private static Field assetsFiled = null;
    private static Field resourcesImplField = null;
    private static Field resDir = null;
    private static Field resources = null;
    private static Field application = null;
    private static Field classloader = null;
    private static Field packagesFiled = null;
    private static Field resourcePackagesFiled = null;
    private static Field publicSourceDirField = null;
    private static Field stringBlocksField = null;

    private static long storedPatchedResModifiedTime = 0L;

    private static Context packageContext = null;

    private static Context packageResContext = null;

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
        resources = findField(loadedApkClass, "mResources");
        application = findField(loadedApkClass, "mApplication");
        classloader = findField(loadedApkClass, "mClassLoader");
        packagesFiled = findField(activityThread, "mPackages");
        try {
            resourcePackagesFiled = findField(activityThread, "mResourcePackages");
        } catch (Throwable thr) {
            ShareTinkerLog.printErrStackTrace(TAG, thr, "Fail to get mResourcePackages field.");
            resourcePackagesFiled = null;
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

                try {
                    final Field mResourceImplsField = findField(resourcesManagerClass, "mResourceImpls");
                    resourceImpls = (Map<Object, WeakReference<Object>>) mResourceImplsField.get(resourcesManager);
                } catch (Throwable ignored) {
                    resourceImpls = null;
                }
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
                resourcesImplField = findField(resources, "mResourcesImpl");
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
     * @param app
     * @param externalResourceFile
     * @throws Throwable
     */
    @SuppressWarnings({"ConstantConditions", "unchecked"})
    public static void monkeyPatchExistingResources(Application app, String externalResourceFile, boolean isReInject) throws Throwable {
        if (externalResourceFile == null) {
            return;
        }

        final ApplicationInfo appInfo = app.getApplicationInfo();

        // Ensure mPackages and mResourcePackages cache contains LoadedApk instance related to this app.
        packageContext = app.createPackageContext(app.getPackageName(), Context.CONTEXT_INCLUDE_CODE);
        packageResContext = app.createPackageContext(app.getPackageName(), 0);

        final Resources oldResources = packageResContext.getResources();
        Field implAssetsField = null;
        Object appAssetManager = null;
        if (resourcesImplField != null) {
            final Object appResourcesImpl = resourcesImplField.get(oldResources);
            implAssetsField = findField(appResourcesImpl, "mAssets");
            appAssetManager = implAssetsField.get(appResourcesImpl);
        } else {
            appAssetManager = assetsFiled.get(oldResources);
        }

        final Field[] packagesFields = new Field[]{packagesFiled, resourcePackagesFiled};
        for (Field field : packagesFields) {
            if (field == null) {
                continue;
            }
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
                    if (isReInject) {
                        application.set(loadedApk, app);
                        classloader.set(loadedApk, SystemClassLoaderAdder.getInjectedClassLoader());
                    }
                    resources.set(loadedApk, null);
                }
            }
        }

        // Let modified resDir take effect and use it to create new Context instance.
        packageContext = app.createPackageContext(app.getPackageName(), Context.CONTEXT_INCLUDE_CODE);
        packageResContext = app.createPackageContext(app.getPackageName(), 0);
        PatchedResourcesInsuranceLogic.recordCurrentPatchedResModifiedTime(externalResourceFile);

        final Resources newResources = packageResContext.getResources();
        Object newAssetManager = null;
        if (resourcesImplField != null) {
            // N
            final Object resourceImpl = resourcesImplField.get(newResources);
            newAssetManager = implAssetsField.get(resourceImpl);
        } else {
            //pre-N
            newAssetManager = assetsFiled.get(newResources);
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
            if (resourcesImplField != null) {
                // N
                final Object resourcesImpl = resourcesImplField.get(resources);
                final Object assetManager = implAssetsField.get(resourcesImpl);
                if (assetManager == appAssetManager) {
                    implAssetsField.set(resourcesImpl, newAssetManager);
                }
            } else {
                //pre-N
                final Object assetManager = assetsFiled.get(resources);
                if (assetManager == appAssetManager) {
                    assetsFiled.set(resources, newAssetManager);
                }
            }

            clearPreloadTypedArrayIssue(resources);
            resources.updateConfiguration(resources.getConfiguration(), resources.getDisplayMetrics());
        }

        try {
            if (resourceImpls != null) {
                for (WeakReference<Object> wr : resourceImpls.values()) {
                    final Object resourcesImpl = wr.get();
                    if (resourcesImpl != null) {
                        final Object assetManager = implAssetsField.get(resourcesImpl);
                        if (assetManager == appAssetManager) {
                            implAssetsField.set(resourcesImpl, newAssetManager);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // Ignored.
        }

        if (isReInject) {
            ShareTinkerLog.i(TAG, "Re-injecting, skip rest logic.");
            return;
        }

        // Handle issues caused by WebView on Android N.
        // Issue: On Android N, if an activity contains a webview, when screen rotates
        // our resource patch may lost effects.
        // for 5.x/6.x, we found Couldn't expand RemoteView for StatusBarNotification Exception
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                if (publicSourceDirField != null) {
                    publicSourceDirField.set(app.getApplicationInfo(), externalResourceFile);
                }
            } catch (Throwable ignore) {
                // Ignored.
            }
        }

        if (!checkResUpdate(app)) {
            throw new TinkerRuntimeException(ShareConstants.CHECK_RES_INSTALL_FAIL);
        }

        if (!PatchedResourcesInsuranceLogic.install(app, externalResourceFile)) {
            ShareTinkerLog.w(TAG, "tryLoadPatchFiles:PatchedResourcesInsuranceLogic install fail.");
            throw new TinkerRuntimeException("fail to install PatchedResourcesInsuranceLogic.");
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
