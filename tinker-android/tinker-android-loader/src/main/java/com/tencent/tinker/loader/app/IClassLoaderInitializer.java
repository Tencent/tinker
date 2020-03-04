package com.tencent.tinker.loader.app;

import android.app.Application;

import androidx.annotation.Keep;

/**
 * Created by tomystang on 2019-10-29.
 */
@Keep
public interface IClassLoaderInitializer {
    @Keep
    void initializeClassLoader(Application application, ClassLoader currentCl) throws Throwable;
}
