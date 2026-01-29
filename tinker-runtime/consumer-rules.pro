# API classes should always avoid obfuscation. Users can configure legacy patch generating rules more effectively.
#
# TODO:
#   Allow API classes to be obfuscated if legacy patch generator is deprecated. Rules that treat API classes as loader
#   classes should be configured automatically.
-keepnames class com.tencent.tinker.Tinker* {
    *;
}

# Package of internal classes should always avoid obfuscation. Users can configure legacy patch generating rules more
# effectively.
#
# TODO:
#   Allow internal classes to be obfuscated if legacy patch generator is deprecated. Rules that treat API classes as
#   loader classes should be configured automatically.
-keeppackagenames com.tencent.tinker.internal.**

# Application delegate classes should always be kept since they are loaded by reflection. However, their members can be
# obfuscated or optimized.
-keep class * extends com.tencent.tinker.Tinker$AppLike {
    <init>(android.app.Application);
}

# Test classes. Tests are based on reflection or JNI invocation.
-keep class com.tencent.tinker.internal.load.code.test.* {
    *;
}

# Test members should be removed.
-assumenosideeffects class ** {
    *** *ForTesting(...);
}

# Class from legacy runtime implemetation. It was kept, just keep behavior.
#
# TODO: Remove if legacy code is refactored.
-keep class com.tencent.tinker.internal.legacy.loader.TinkerClassLoader {
    *;
}

# Class from legacy runtime implemetation. It was kept, just keep behavior.
#
# TODO: Remove if legacy code is refactored.
-keep class com.tencent.tinker.internal.legacy.loader.TinkerClassLoader$CompoundEnumeration {
    *;
}