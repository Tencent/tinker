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

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.ArrayMap;
import android.util.Log;

import com.tencent.tinker.loader.shareutil.ShareConstants;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.KITKAT;

class TinkerResourcePatcher {
    private static final String TAG = "Tinker.ResourcePatcher";
    private static final String TEST_STRING_NAME  = "tinker_test_resource";
    private static final String TEST_STRING_VALUE = "only use for test tinker resource: b";
    // original value
    private static Collection<WeakReference<Resources>> references;
    private static AssetManager newAssetManager          = null;
    private static Method       addAssetPathMethod       = null;
    private static Method       ensureStringBlocksMethod = null;
    private static Field        assetsFiled              = null;
    private static Field        resourcesImplFiled      = null;
    private static Field        resDir      = null;

    public static void isResourceCanPatch(Context context) throws Throwable {
        //   - Replace mResDir to point to the external resource file instead of the .apk. This is
        //     used as the asset path for new Resources objects.
        //   - Set Application#mLoadedApk to the found LoadedApk instance

        // Find the ActivityThread instance for the current thread
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object currentActivityThread = getActivityThread(context, activityThread);
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
        newAssetManager = AssetManager.class.getConstructor().newInstance();
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
            @SuppressWarnings("unchecked")
            HashMap<?, WeakReference<Resources>> map =
                (HashMap<?, WeakReference<Resources>>) fMActiveResources.get(currentActivityThread);
            references = map.values();
        }
        // check resource
        if (references == null || references.isEmpty()) {
            throw new IllegalStateException("resource references is null or empty");
        }
        try {
            assetsFiled = Resources.class.getDeclaredField("mAssets");
            assetsFiled.setAccessible(true);
        } catch (Throwable ignore) {
            // N moved the mAssets inside an mResourcesImpl field
            resourcesImplFiled = Resources.class.getDeclaredField("mResourcesImpl");
            resourcesImplFiled.setAccessible(true);
        }
    }

    public static void monkeyPatchExistingResources(Context context, String externalResourceFile) throws Throwable {
        if (externalResourceFile == null) {
            return;
        }
        // Find the ActivityThread instance for the current thread
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object currentActivityThread = getActivityThread(context, activityThread);

        for (String fieldName : new String[]{"mPackages", "mResourcePackages"}) {
            Field field = activityThread.getDeclaredField(fieldName);
            field.setAccessible(true);
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
                    //N
                    Object resourceImpl = resourcesImplFiled.get(resources);
                    Field implAssets = resourceImpl.getClass().getDeclaredField("mAssets");
                    implAssets.setAccessible(true);
                    implAssets.set(resourceImpl, newAssetManager);
                }

                resources.updateConfiguration(resources.getConfiguration(), resources.getDisplayMetrics());
            }
        }

        if (!checkResUpdate(context)) {
            throw new TinkerRuntimeException(ShareConstants.CHECK_RES_INSTALL_FAIL);
        }
    }

    private static boolean checkResUpdate(Context context) {
        int testStringID = context.getResources().getIdentifier(TEST_STRING_NAME, "string", context.getPackageName());
        if (testStringID > 0) {
            String value = context.getString(testStringID);
            Log.w(TAG, "checkResUpdate resource value:" + value);

            if (!value.equals(TEST_STRING_VALUE)) {
                return false;
            }
        } else  {
            Log.e(TAG, "checkResUpdate resource id < 0 " + testStringID);
        }
        return true;
    }
    private static Object getActivityThread(Context context,
                                            Class<?> activityThread) {
        try {
            if (activityThread == null) {
                activityThread = Class.forName("android.app.ActivityThread");
            }
            Method m = activityThread.getMethod("currentActivityThread");
            m.setAccessible(true);
            Object currentActivityThread = m.invoke(null);
            if (currentActivityThread == null && context != null) {
                // In older versions of Android (prior to frameworks/base 66a017b63461a22842)
                // the currentActivityThread was built on thread locals, so we'll need to try
                // even harder
                Field mLoadedApk = context.getClass().getField("mLoadedApk");
                mLoadedApk.setAccessible(true);
                Object apk = mLoadedApk.get(context);
                Field mActivityThreadField = apk.getClass().getDeclaredField("mActivityThread");
                mActivityThreadField.setAccessible(true);
                currentActivityThread = mActivityThreadField.get(apk);
            }
            return currentActivityThread;
        } catch (Throwable ignore) {
            return null;
        }
    }
}
