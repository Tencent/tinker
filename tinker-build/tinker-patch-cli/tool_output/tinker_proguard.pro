#-applymapping "old apk mapping here"

-keepattributes *Annotation* 
-dontwarn com.tencent.tinker.anno.AnnotationProcessor 
-keep @com.tencent.tinker.anno.DefaultLifeCycle public class *
-keep public class * extends android.app.Application {
    *;
}

-keep public class com.tencent.tinker.loader.app.ApplicationLifeCycle {
    *;
}
-keep public class * implements com.tencent.tinker.loader.app.ApplicationLifeCycle {
    *;
}

-keep public class com.tencent.tinker.loader.TinkerLoader {
    *;
}
-keep public class * extends com.tencent.tinker.loader.TinkerLoader {
    *;
}

-keep public class com.tencent.tinker.loader.TinkerTestDexLoad {
    *;
}

#your dex.loader pattern here
-keep class com.tencent.tinker.loader.**
-keep class tinker.sample.android.app.SampleApplication

