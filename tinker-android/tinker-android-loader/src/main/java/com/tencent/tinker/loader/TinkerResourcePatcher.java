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

import android.app.Instrumentation;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;
import android.util.ArrayMap;
import android.util.Log;

import com.tencent.tinker.loader.shareutil.ActivityResFixInstrumentation;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareReflectUtil;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.KITKAT;

class TinkerResourcePatcher {
    private static final String TAG               = "Tinker.ResourcePatcher";
    private static final String TEST_ASSETS_VALUE = "only_use_to_test_tinker_resource.txt";

    // original value
    private static Collection<WeakReference<Resources>> references;
    private static AssetManager newAssetManager          = null;
    private static Method       addAssetPathMethod       = null;
    private static Method       ensureStringBlocksMethod = null;
    private static Field        assetsFiled              = null;
    private static Field        resourcesImplFiled       = null;
    private static Field        typedArrayPoolField      = null;
    private static Field        resDir                   = null;
    private static Field        packagesFiled            = null;
    private static Field        resourcePackagesFiled    = null;

    private static Field        instrumentationField     = null;
//    private static Field        publicSourceDirField     = null;

    public static void isResourceCanPatch(Context context) throws Throwable {
        //   - Replace mResDir to point to the external resource file instead of the .apk. This is
        //     used as the asset path for new Resources objects.
        //   - Set Application#mLoadedApk to the found LoadedApk instance

        // Find the ActivityThread instance for the current thread
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        // API version 8 has PackageInfo, 10 has LoadedApk. 9, I don't know.
        Class<?> loadedApkClass;
        try {
            loadedApkClass = Class.forName("android.app.LoadedApk");
        } catch (ClassNotFoundException e) {
            loadedApkClass = Class.forName("android.app.ActivityThread$PackageInfo");
        }
        Field mApplication = loadedApkClass.getDeclaredField("mApplication");
        mApplication.setAccessible(true);
        resDir = loadedApkClass.getDeclaredField("mResDir");
        resDir.setAccessible(true);
        packagesFiled = activityThread.getDeclaredField("mPackages");
        packagesFiled.setAccessible(true);

        resourcePackagesFiled = activityThread.getDeclaredField("mResourcePackages");
        resourcePackagesFiled.setAccessible(true);
        /*
        (Note: the resource directory is *also* inserted into the loadedApk in
        monkeyPatchApplication)
        The code seems to perform this:
        File externalResourceFile = <path to resources.ap_ or extracted directory>

        AssetManager newAssetManager = new AssetManager();
        newAssetManager.addAssetPath(externalResourceFile)

        // Kitkat needs this method call, Lollipop doesn't. However, it doesn't seem to cause any harm
        // in L, so we do it unconditionally.
        newAssetManager.ensureStringBlocks();

        // Find the singleton instance of ResourcesManager
        ResourcesManager resourcesManager = ResourcesManager.getInstance();

        // Iterate over all known Resources objects
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            for (WeakReference<Resources> wr : resourcesManager.mActiveResources.values()) {
                Resources resources = wr.get();
                // Set the AssetManager of the Resources instance to our brand new one
                resources.mAssets = newAssetManager;
                resources.updateConfiguration(resources.getConfiguration(), resources.getDisplayMetrics());
            }
        }

        // Also, for each context, call getTheme() to get the current theme; null out its
        // mTheme field, then invoke initializeTheme() to force it to be recreated (with the
        // new asset manager!)

        */
        // Create a new AssetManager instance and point it to the resources installed under
        // /sdcard
        AssetManager assets = context.getAssets();
        // Baidu os
        if (assets.getClass().getName().equals("android.content.res.BaiduAssetManager")) {
            Class baiduAssetManager = Class.forName("android.content.res.BaiduAssetManager");
            newAssetManager = (AssetManager) baiduAssetManager.getConstructor().newInstance();
        } else {
            newAssetManager = AssetManager.class.getConstructor().newInstance();
        }

        addAssetPathMethod = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
        addAssetPathMethod.setAccessible(true);

        // Kitkat needs this method call, Lollipop doesn't. However, it doesn't seem to cause any harm
        // in L, so we do it unconditionally.
        ensureStringBlocksMethod = AssetManager.class.getDeclaredMethod("ensureStringBlocks");
        ensureStringBlocksMethod.setAccessible(true);

        // Iterate over all known Resources objects
        if (SDK_INT >= KITKAT) {
            //pre-N
            // Find the singleton instance of ResourcesManager
            Class<?> resourcesManagerClass = Class.forName("android.app.ResourcesManager");
            Method mGetInstance = resourcesManagerClass.getDeclaredMethod("getInstance");
            mGetInstance.setAccessible(true);
            Object resourcesManager = mGetInstance.invoke(null);
            try {
                Field fMActiveResources = resourcesManagerClass.getDeclaredField("mActiveResources");
                fMActiveResources.setAccessible(true);
                ArrayMap<?, WeakReference<Resources>> arrayMap =
                    (ArrayMap<?, WeakReference<Resources>>) fMActiveResources.get(resourcesManager);
                references = arrayMap.values();
            } catch (NoSuchFieldException ignore) {
                // N moved the resources to mResourceReferences
                Field mResourceReferences = resourcesManagerClass.getDeclaredField("mResourceReferences");
                mResourceReferences.setAccessible(true);
                //noinspection unchecked
                references = (Collection<WeakReference<Resources>>) mResourceReferences.get(resourcesManager);
            }
        } else {
            Field fMActiveResources = activityThread.getDeclaredField("mActiveResources");
            fMActiveResources.setAccessible(true);
            Object thread = ShareReflectUtil.getActivityThread(context, activityThread);
            @SuppressWarnings("unchecked")
            HashMap<?, WeakReference<Resources>> map =
                (HashMap<?, WeakReference<Resources>>) fMActiveResources.get(thread);
            references = map.values();
        }
        // check resource
        if (references == null) {
            throw new IllegalStateException("resource references is null");
        }
        try {
            assetsFiled = Resources.class.getDeclaredField("mAssets");
            assetsFiled.setAccessible(true);
        } catch (Throwable ignore) {
            // N moved the mAssets inside an mResourcesImpl field
            resourcesImplFiled = Resources.class.getDeclaredField("mResourcesImpl");
            resourcesImplFiled.setAccessible(true);
        }

        try {
            typedArrayPoolField = Resources.class.getDeclaredField("mTypedArrayPool");
            typedArrayPoolField.setAccessible(true);
        } catch (Throwable ignored) {
        }

        try {
            instrumentationField = ShareReflectUtil.findField(activityThread, "mInstrumentation");
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("cannot find 'mInstrumentation' field");
        }

//        try {
//            publicSourceDirField = ShareReflectUtil.findField(ApplicationInfo.class, "publicSourceDir");
//        } catch (NoSuchFieldException e) {
//            throw new IllegalStateException("cannot find 'mInstrumentation' field");
//        }
    }

    public static void monkeyPatchExistingResources(Context context, String externalResourceFile) throws Throwable {
        if (externalResourceFile == null) {
            return;
        }
        // Find the ActivityThread instance for the current thread
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object currentActivityThread = ShareReflectUtil.getActivityThread(context, activityThread);

        for (Field field : new Field[]{packagesFiled, resourcePackagesFiled}) {
            Object value = field.get(currentActivityThread);

            for (Map.Entry<String, WeakReference<?>> entry
                : ((Map<String, WeakReference<?>>) value).entrySet()) {
                Object loadedApk = entry.getValue().get();
                if (loadedApk == null) {
                    continue;
                }
                if (externalResourceFile != null) {
                    resDir.set(loadedApk, externalResourceFile);
                }
            }
        }
        // Create a new AssetManager instance and point it to the resources installed under
        // /sdcard
        if (((Integer) addAssetPathMethod.invoke(newAssetManager, externalResourceFile)) == 0) {
            throw new IllegalStateException("Could not create new AssetManager");
        }

        // Kitkat needs this method call, Lollipop doesn't. However, it doesn't seem to cause any harm
        // in L, so we do it unconditionally.
        ensureStringBlocksMethod.invoke(newAssetManager);

        for (WeakReference<Resources> wr : references) {
            Resources resources = wr.get();
            //pre-N
            if (resources != null) {
                // Set the AssetManager of the Resources instance to our brand new one
                try {
                    assetsFiled.set(resources, newAssetManager);
                } catch (Throwable ignore) {
                    // N
                    Object resourceImpl = resourcesImplFiled.get(resources);
                    // for Huawei HwResourcesImpl
                    Field implAssets = ShareReflectUtil.findField(resourceImpl, "mAssets");
                    implAssets.setAccessible(true);
                    implAssets.set(resourceImpl, newAssetManager);
                }
                // Clear typedArray cache.
                try {
                    final Object origTypedArrayPool = typedArrayPoolField.get(resources);
                    final Constructor<?> ctor = origTypedArrayPool.getClass().getConstructor(int.class);
                    ctor.setAccessible(true);
                    // 5 is the harcoded value in AOSP
                    final Object newTypedArrayPool = ctor.newInstance(5);
                    typedArrayPoolField.set(resources, newTypedArrayPool);
                } catch (Throwable ignored) {
                }

                resources.updateConfiguration(resources.getConfiguration(), resources.getDisplayMetrics());
            }
        }

        try {
            final Instrumentation origInstrumentation = (Instrumentation) instrumentationField.get(currentActivityThread);
            final Instrumentation activityResFixInstrumentation = new ActivityResFixInstrumentation(context, origInstrumentation);
            instrumentationField.set(currentActivityThread, activityResFixInstrumentation);
        } catch (NoSuchFieldException e) {
            // Version below api 17 has no mResources field in ContextThemeWrapper,
            // so just ignore this exception. Otherwise we should rethrow it.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                throw e;
            }
        }

        // Handle issues caused by WebView on Android N.
        // Issue: On Andorid N, if an activity contains a webview, when screen rotates
        // our resource patch may lost effects.
//        publicSourceDirField.set(context.getApplicationInfo(), externalResourceFile);

        if (!checkResUpdate(context)) {
            throw new TinkerRuntimeException(ShareConstants.CHECK_RES_INSTALL_FAIL);
        }
    }

    private static boolean checkResUpdate(Context context) {
        try {
            Log.e(TAG, "checkResUpdate success, found test resource assets file " + TEST_ASSETS_VALUE);
            context.getAssets().open(TEST_ASSETS_VALUE);
        } catch (Throwable e) {
            Log.e(TAG, "checkResUpdate failed, can't find test resource assets file " + TEST_ASSETS_VALUE + " e:" + e.getMessage());
            return false;
        }
        return true;
    }
}
