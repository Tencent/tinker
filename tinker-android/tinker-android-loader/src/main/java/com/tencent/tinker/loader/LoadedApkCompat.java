package com.tencent.tinker.loader;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by andyzhang on 2017/7/4.
 */

class LoadedApkCompat {

    static String getPackageNameForLoadedApkOrThrow(Object loadedApkObj) throws InvocationTargetException, IllegalAccessException, ClassNotFoundException, NoSuchMethodException {
        Class LoadedAPK = Class.forName("android.app.LoadedApk");
        Method getPackageName = LoadedAPK.getDeclaredMethod("getPackageName");
        getPackageName.setAccessible(true);
        if (getPackageName.getReturnType() == String.class) {
            return (String) getPackageName.invoke(loadedApkObj);
        } else {
            //Never to here
            throw new TinkerRuntimeException("Method [public String android.app.LoadedApk.getPackageName() ] not found");
        }
    }
}
