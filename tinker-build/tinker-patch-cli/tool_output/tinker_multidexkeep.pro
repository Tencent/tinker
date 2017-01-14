-keep public class * implements com.tencent.tinker.loader.app.ApplicationLifeCycle {
    *;
}

-keep public class * extends com.tencent.tinker.loader.TinkerLoader {
    *;
}

-keep public class * extends android.app.Application {
    *;
}

#your dex.loader pattern here
-keep class com.tencent.tinker.loader.** {
    *;
}

-keep class tinker.sample.android.app.SampleApplication {
    *;
}

