-dontwarn android.content.pm.PackageManager$DexModuleRegisterCallback

-keep class * extends android.content.pm.PackageManager$DexModuleRegisterCallback {
    <fields>;
    <methods>;
}

# TODO: Remove if legacy code is refactored.
-keep class com.tencent.tinker.internal.legacy.loader.TinkerClassLoader {
    *;
}

# TODO: Remove if legacy code is refactored.
-keep class com.tencent.tinker.internal.legacy.loader.TinkerClassLoader$CompoundEnumeration {
    *;
}

-keepnames class com.tencent.tinker.internal.load.code.test.TestLibrary {
    native <methods>;
}

-assumenosideeffects class ** {
    *** *ForTesting(...);
}