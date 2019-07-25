/*
 * Tencent is pleased to support the open source community by making Tinker available.
 *
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tinker.loader;

import android.content.res.Configuration;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;

import static com.tencent.tinker.loader.shareutil.ShareReflectUtil.findField;

/**
 * Created by zhangshaowen on 17/1/12.
 *
 * TODO:
 * Thanks for Android Fragmentation
 * hold the issue https://github.com/Tencent/tinker/issues/302
 */
public class TinkerResourcesKey {

    private static final String TAG = "Tinker.ResourcesKey";

    public static String lookOverUnadaptedInfo() {
        try {
            checkClassCorrect();
        } catch (Throwable ignore) {
            StringBuilder info = new StringBuilder("ClassInfo { ");
            try {
                Class ResourcesKeyClazz = BaseKey.ResourcesKeyClazz;
                info.append(ResourcesKeyClazz.getName()).append(", ");
                Constructor[] constructors = ResourcesKeyClazz.getDeclaredConstructors();
                for (Constructor c : constructors) {
                    if (c == null) {
                        continue;
                    }
                    c.setAccessible(true);
                    Class[] args = c.getParameterTypes();
                    if (args != null && args.length > 0) {
                        info.append(Arrays.deepToString(args)).append(", ");
                    }
                }
            } catch (Throwable e) {
                info.append("err=" + e.getMessage()).append(" ");
            }
            info.append("}");
            return info.toString();
        }
        return null;
    }

    static void checkClassCorrect() throws Throwable {
        BaseKey keyWrap = createLyingKey();
        keyWrap.getConstructor();
    }

    static String getResDir(Object resourcesKey) throws Exception {
        return (String) BaseKey.mResDirField.get(resourcesKey);
    }

    static Object newKey(Object oldKey, String newResourcePath) throws Exception {
        BaseKey keyWrap = createLyingKey();
        if (keyWrap != null) {
            return keyWrap.getNewKey(oldKey, newResourcePath);
        }
        throw new IllegalAccessException("can not create new ResourcesKey object");
    }

    private static BaseKey createLyingKey() {
        final int sdk = Build.VERSION.SDK_INT;
        BaseKey key = null;
        if (sdk >= 24) {
            key =  new V24();
        } else if (sdk >= 23) {
            key =  new V23();
        } else if (sdk >= 19) {
            key =  new V19();
        } else if (sdk >= 17) {
            key =  new V17();
        } else if (sdk >= 16) {
            key =  new V16();
        }
        try {
            key.getConstructor();
        } catch (Throwable e) {
            key = monkeyTryingKey();
            if (key != null) {
                Log.w(TAG, "monkey trying ResourcesKey success");
            }
        }
        return key;
    }

    private static BaseKey monkeyTryingKey() {
        BaseKey key = null;
        for (int i = 0; ; i++) {
            try {
                if (i == 0) {
                    key = new Customized.A();
                } else if (i == 1) {
                    key = new Customized.B();
                } else if (i == 2) {
                    key = new Customized.C();
                } else {
                    break;
                }
                key.getConstructor();
            } catch (Throwable ignore) {
                key = null;
                continue;
            }
            break;
        }
        return key;
    }


    private abstract static class BaseKey {
        static final Class ResourcesKeyClazz;
        static final Field mResDirField;

        static {
            try {
                Class rkc; // monkey check the resources key class
                try {
                    rkc = Class.forName("android.content.res.ResourcesKey");
                } catch (Exception ignore) {
                    rkc = Class.forName("android.app.ActivityThread$ResourcesKey");
                }
                ResourcesKeyClazz = rkc;
                mResDirField = findField(ResourcesKeyClazz, "mResDir");
            } catch (Exception ignore) {
                throw new RuntimeException(ignore);
            }
        }

        String resDir;

        abstract Constructor getConstructor() throws Exception;

        Object getNewKey(Object oldKey, String newResDir) throws Exception {
            this.resDir = newResDir;
            return null;
        }
    }

    private abstract static class CommonKey extends BaseKey {
        static final Field mDisplayIdField;
        static final Field mOverrideConfigurationField;

        static {
            try {
                mDisplayIdField = findField(ResourcesKeyClazz, "mDisplayId");
                mOverrideConfigurationField = findField(ResourcesKeyClazz, "mOverrideConfiguration");
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        int displayId;
        Configuration configuration;

        @Override
        Object getNewKey(Object oldKey, String newResDir) throws Exception {
            super.getNewKey(oldKey, newResDir);
            displayId = (int) mDisplayIdField.get(oldKey);
            configuration = (Configuration) mOverrideConfigurationField.get(oldKey);
            return null;
        }
    }

    private static final class V24 extends CommonKey {
        static final Field mSplitResDirsField;
        static final Field mOverlayDirsField;
        static final Field mLibDirsField;
        static final Field mCompatInfoField;

        static {
            try {
                mSplitResDirsField = findField(ResourcesKeyClazz, "mSplitResDirs");
                mOverlayDirsField = findField(ResourcesKeyClazz, "mOverlayDirs");
                mLibDirsField = findField(ResourcesKeyClazz, "mLibDirs");
                mCompatInfoField = findField(ResourcesKeyClazz, "mCompatInfo");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        Constructor getConstructor() throws Exception {
            Class CompatibilityInfoClazz = Class.forName("android.content.res.CompatibilityInfo");
            Constructor cst = ResourcesKeyClazz.getDeclaredConstructor(
                    String.class, String[].class, String[].class, String[].class, int.class, Configuration.class, CompatibilityInfoClazz);
            cst.setAccessible(true);
            return cst;
        }

        @Override
        Object getNewKey(Object oldKey, String newResDir) throws Exception {
            super.getNewKey(oldKey, newResDir);
            String[] splitResDirs = (String[]) mSplitResDirsField.get(oldKey);
            String[] overlayDirs = (String[]) mOverlayDirsField.get(oldKey);
            String[] libDirs = (String[]) mLibDirsField.get(oldKey);
            Object compatInfo = mCompatInfoField.get(oldKey);
            return getConstructor()
                    .newInstance(resDir, splitResDirs, overlayDirs, libDirs, displayId, configuration, compatInfo);
        }
    }

    private static final class V23 extends CommonKey {
        static final Field mScaleField;

        static {
            try {
                mScaleField = findField(ResourcesKeyClazz, "mScale");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        Constructor getConstructor() throws Exception {
            Constructor cst = ResourcesKeyClazz.getDeclaredConstructor(
                    String.class, int.class, Configuration.class, float.class);
            cst.setAccessible(true);
            return cst;
        }

        @Override
        Object getNewKey(Object oldKey, String newResDir) throws Exception {
            super.getNewKey(oldKey, newResDir);
            float scale = (float) mScaleField.get(oldKey);
            return getConstructor()
                    .newInstance(resDir, displayId, configuration, scale);
        }
    }

    private static final class V19 extends CommonKey {
        static final Field mScaleField;
        static final Field mTokenField;

        static {
            try {
                mScaleField = findField(ResourcesKeyClazz, "mScale");
                mTokenField = findField(ResourcesKeyClazz, "mToken");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        Constructor getConstructor() throws Exception {
            Constructor cst = ResourcesKeyClazz.getDeclaredConstructor(
                    String.class, int.class, Configuration.class, float.class, IBinder.class);
            cst.setAccessible(true);
            return cst;
        }

        @Override
        Object getNewKey(Object oldKey, String newResDir) throws Exception {
            super.getNewKey(oldKey, newResDir);
            float scale = (float) mScaleField.get(oldKey);
            IBinder token = (IBinder) mTokenField.get(oldKey);
            return getConstructor()
                    .newInstance(resDir, displayId, configuration, scale, token);
        }
    }

    /**
     * the same as {@link V23}
     */
    private static final class V17 extends CommonKey {
        static final Field mScaleField;

        static {
            try {
                mScaleField = findField(ResourcesKeyClazz, "mScale");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        Constructor getConstructor() throws Exception {
            Constructor cst = ResourcesKeyClazz.getDeclaredConstructor(
                    String.class, int.class, Configuration.class, float.class);
            cst.setAccessible(true);
            return cst;
        }

        @Override
        Object getNewKey(Object oldKey, String newResDir) throws Exception {
            super.getNewKey(oldKey, newResDir);
            float scale = (float) mScaleField.get(oldKey);
            return getConstructor()
                    .newInstance(resDir, displayId, configuration, scale);
        }
    }

    private static final class V16 extends BaseKey {
        static final Field mScaleField;

        static {
            try {
                mScaleField = findField(ResourcesKeyClazz, "mScale");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        Constructor getConstructor() throws Exception {
            Constructor cst = ResourcesKeyClazz.getDeclaredConstructor(
                    String.class, float.class);
            cst.setAccessible(true);
            return cst;
        }

        @Override
        Object getNewKey(Object oldKey, String newResDir) throws Exception {
            super.getNewKey(oldKey, newResDir);
            float scale = (float) mScaleField.get(oldKey);
            return getConstructor()
                    .newInstance(resDir, scale);
        }
    }


    private static class Customized {

        private static final class A extends CommonKey {
            static final Field mScaleField;
            static final Field mIsThemeableField;

            static {
                try {
                    mScaleField = findField(ResourcesKeyClazz, "mScale");
                    mIsThemeableField = findField(ResourcesKeyClazz, "mIsThemeable");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            Constructor getConstructor() throws Exception {
                Constructor cst = ResourcesKeyClazz.getDeclaredConstructor(
                        String.class, int.class, Configuration.class, float.class, boolean.class);
                cst.setAccessible(true);
                return cst;
            }

            @Override
            Object getNewKey(Object oldKey, String newResDir) throws Exception {
                super.getNewKey(oldKey, newResDir);
                float scale = (float) mScaleField.get(oldKey);
                boolean isThemeable = (boolean) mIsThemeableField.get(oldKey);
                return getConstructor()
                        .newInstance(resDir, displayId, configuration, scale, isThemeable);
            }
        }

        private static final class B extends CommonKey {
            static final Field mScaleField;
            static final Field mTokenField;
            static final Field mIsThemeableField;

            static {
                try {
                    mScaleField = findField(ResourcesKeyClazz, "mScale");
                    mTokenField = findField(ResourcesKeyClazz, "mToken");
                    mIsThemeableField = findField(ResourcesKeyClazz, "mIsThemeable");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            Constructor getConstructor() throws Exception {
                Constructor cst = ResourcesKeyClazz.getDeclaredConstructor(
                        String.class, int.class, Configuration.class, float.class, IBinder.class, boolean.class);
                cst.setAccessible(true);
                return cst;
            }

            @Override
            Object getNewKey(Object oldKey, String newResDir) throws Exception {
                super.getNewKey(oldKey, newResDir);
                float scale = (float) mScaleField.get(oldKey);
                IBinder token = (IBinder) mTokenField.get(oldKey);
                boolean isThemeable = (boolean) mIsThemeableField.get(oldKey);
                return getConstructor()
                        .newInstance(resDir, displayId, configuration, scale, token, isThemeable);
            }
        }

        private static final class C extends CommonKey {
            static final Field mScaleField;
            static final Field mTokenField;
            static final Field mIsThemeableField;

            static {
                try {
                    mScaleField = findField(ResourcesKeyClazz, "mScale");
                    mTokenField = findField(ResourcesKeyClazz, "mToken");
                    mIsThemeableField = findField(ResourcesKeyClazz, "mIsThemeable");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            Constructor getConstructor() throws Exception {
                Constructor cst = ResourcesKeyClazz.getDeclaredConstructor(
                        String.class, int.class, Configuration.class, float.class, boolean.class, IBinder.class);
                cst.setAccessible(true);
                return cst;
            }

            @Override
            Object getNewKey(Object oldKey, String newResDir) throws Exception {
                super.getNewKey(oldKey, newResDir);
                float scale = (float) mScaleField.get(oldKey);
                IBinder token = (IBinder) mTokenField.get(oldKey);
                boolean isThemeable = (boolean) mIsThemeableField.get(oldKey);
                return getConstructor()
                        .newInstance(resDir, displayId, configuration, scale, isThemeable, token);
            }
        }

    }

}
