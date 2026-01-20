-keep class com.tencent.tinker.example.MainAppLike {
    public void attachBaseContext(android.content.Context);
    public void onCreate();
    public void onTerminate();
    public void onLowMemory();
    public void onTrimMemory(int);
    public void onConfigurationChanged(android.content.res.Configuration);
}

-keep class com.tencent.tinker.example.cases.** {
    *;
}