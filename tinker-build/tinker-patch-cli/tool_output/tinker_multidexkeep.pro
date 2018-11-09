#tinker multidex keep patterns:
-keep public class * extends com.tencent.tinker.loader.TinkerLoader {
    <init>();
}

-keep public class * extends android.app.Application {
     <init>();
     void attachBaseContext(android.content.Context);
}

-keep class com.tencent.tinker.loader.TinkerTestAndroidNClassLoader {
    <init>();
}

#your dex.loader patterns here
-keep class tinker.sample.android.app.SampleApplication {
    <init>();
}

-keep class com.tencent.tinker.loader.** {
    <init>();
}