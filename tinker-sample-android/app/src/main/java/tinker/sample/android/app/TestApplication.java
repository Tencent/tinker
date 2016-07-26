/*
 * Copyright (C) 2016 Tencent WeChat, Inc.
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

package tinker.sample.android.app;

import android.annotation.TargetApi;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Build;
import android.util.Log;

import com.tencent.tinker.loader.AndroidNClassLoader;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import dalvik.system.PathClassLoader;

/**
 * Created by shwenzhang on 16/7/25.
 */
public class TestApplication extends Application {
    private final String realClassName;
    private final String packageName;
    private static Context     baseContext     = null;
    private        Application realApplication = null;
    private        ClassLoader classLoader     = null;

    public TestApplication() {
        realClassName = "com.tencent.tinker.loader.app.TinkerApplication";
        packageName = "tinker.sample.android";

        Log.e("StubApplication", String.format(
            "StubApplication created. Android package is %s, real application class is %s.",
            packageName, realClassName));
    }

    @Override
    public ClassLoader getClassLoader() {
        if (classLoader != null) {
            return classLoader;
        }
        return super.getClassLoader();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        baseContext = base;

    }

    @Override
    public void onCreate() {
        super.onCreate();
        onAttachBaseContext(baseContext);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)

    protected void onAttachBaseContext(Context base) {
        PathClassLoader pathClassLoader = (PathClassLoader) TestApplication.class.getClassLoader();
        Log.e("test", "before classloader:" + getClassLoader());
        String nativeLibraryPath = "";
        AndroidNClassLoader androidNClassLoader = AndroidNClassLoader.createAndroidNClassLoader(this, nativeLibraryPath, pathClassLoader);
        classLoader = androidNClassLoader;
        try {
            AndroidNClassLoader.reflectPackageInfoClassloader(this, androidNClassLoader);
            Log.e("test", "after classloader:" + getClassLoader());
            Log.e("test", "androidNClassLoader:" + androidNClassLoader.toString());
            Log.e("test", "androidNClassLoader parent :" + androidNClassLoader.getParent().toString());
            Log.e("test", "attach_real_context");
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        // StubApplication is created by reflection in Application#handleBindApplication() ->
        // LoadedApk#makeApplication(), and its return value is used to set the Application field in all
        // sorts of Android internals.
        //
        // Fortunately, Application#onCreate() is called quite soon after, so what we do is monkey
        // patch in the real Application instance in StubApplication#onCreate().
        //
        // A few places directly use the created Application instance (as opposed to the fields it is
        // eventually stored in). Fortunately, it's easy to forward those to the actual real
        // Application class.
        try {
            Class delegateClass = Class.forName(realClassName, true, getClassLoader());
            Constructor<?> constructor = delegateClass.getConstructor();
            realApplication = (Application) constructor.newInstance();
            // Find the ActivityThread instance for the current thread
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Method m = activityThread.getMethod("currentActivityThread");
            m.setAccessible(true);
            Object currentActivityThread = m.invoke(null);

            // Find the mInitialApplication field of the ActivityThread to the real application
            Field mInitialApplication = activityThread.getDeclaredField("mInitialApplication");
            mInitialApplication.setAccessible(true);
            Application initialApplication = (Application) mInitialApplication.get(currentActivityThread);
            if (initialApplication == TestApplication.this) {
                mInitialApplication.set(currentActivityThread, realApplication);
            }

            // Replace all instance of the stub application in ActivityThread#mAllApplications with the
            // real one
            Field mAllApplications = activityThread.getDeclaredField("mAllApplications");
            mAllApplications.setAccessible(true);
            List<Application> allApplications = (List<Application>) mAllApplications
                .get(currentActivityThread);
            for (int i = 0; i < allApplications.size(); i++) {
                if (allApplications.get(i) == TestApplication.this) {
                    allApplications.set(i, realApplication);
                }
            }

            // Figure out how loaded APKs are stored.

            // API version 8 has PackageInfo, 10 has LoadedApk. 9, I don't know.
            Class<?> loadedApkClass;
            try {
                loadedApkClass = Class.forName("android.app.LoadedApk");
            } catch (ClassNotFoundException e) {
                loadedApkClass = Class.forName("android.app.ActivityThread$PackageInfo");
            }
            Field mApplication = loadedApkClass.getDeclaredField("mApplication");
            mApplication.setAccessible(true);
            Field mResDir = loadedApkClass.getDeclaredField("mResDir");
            mResDir.setAccessible(true);

            // 10 doesn't have this field, 14 does. Fortunately, there are not many Honeycomb devices
            // floating around.
            Field mLoadedApk = null;
            try {
                mLoadedApk = Application.class.getDeclaredField("mLoadedApk");
            } catch (NoSuchFieldException e) {
                // According to testing, it's okay to ignore this.
            }

            // Enumerate all LoadedApk (or PackageInfo) fields in ActivityThread#mPackages and
            // ActivityThread#mResourcePackages and do two things:
            //   - Replace the Application instance in its mApplication field with the real one
            //   - Replace mResDir to point to the external resource file instead of the .apk. This is
            //     used as the asset path for new Resources objects.
            //   - Set Application#mLoadedApk to the found LoadedApk instance
            for (String fieldName : new String[]{"mPackages", "mResourcePackages"}) {
                Field field = activityThread.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(currentActivityThread);

                for (Map.Entry<String, WeakReference<?>> entry :
                    ((Map<String, WeakReference<?>>) value).entrySet()) {
                    Object loadedApk = entry.getValue().get();
                    if (loadedApk == null) {
                        continue;
                    }

                    if (mApplication.get(loadedApk) == TestApplication.this) {
                        mApplication.set(loadedApk, realApplication);

                        if (mLoadedApk != null) {
                            mLoadedApk.set(realApplication, loadedApk);
                        }
                    }
                }
            }

            Method attachBaseContext =
                ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
            attachBaseContext.setAccessible(true);
            attachBaseContext.invoke(realApplication, base);

        } catch (IllegalAccessException | NoSuchFieldException | NoSuchMethodException |
            ClassNotFoundException | InvocationTargetException e) {
            throw new IllegalStateException(e);
        } catch (InstantiationException e) {
            e.printStackTrace();
        }
    }
}
