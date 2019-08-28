package com.tencent.tinker.loader.app;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;

public interface ITinkerInlineFenceBridge {
    void attachBaseContext(TinkerApplication app, Context base);

    void onCreate(TinkerApplication app);

    void onConfigurationChanged(Configuration newConfig);

    void onTrimMemory(int level);

    void onLowMemory();

    void onTerminate();

    ClassLoader getClassLoader(ClassLoader cl);

    Context getBaseContext(Context base);

    AssetManager getAssets(AssetManager assets);

    Resources getResources(Resources res);

    Object getSystemService(String name, Object service);
}
