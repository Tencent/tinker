package com.tencent.tinker.loader.app;

import android.app.Application;
import android.support.annotation.Keep;

/**
 * Created by tomystang on 2019-10-29.
 */
@Keep
public interface IClassLoaderInitializer {
    void initializeClassLoader(Application application, ClassLoader currentCl) throws Throwable;
}
