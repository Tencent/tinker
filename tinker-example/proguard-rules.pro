# While we are using Tinker runtime by composite build, which is same as project dependency, the package transform is
# not applied. Kotlin standard library classes which are used by Tinker runtime that have to be treated as loader
# classes that cannot be obfuscated and patched.
#
# If you are using Tinker runtime by prebuilt AAR from Maven, this configuration is unneeded.
-keepnames class kotlin.** {
    *;
}

# Callbacks are used by loading. While they are configured in Tinker config, obfuscation should be disabled.
-keepnames class com.tencent.tinker.example.Callbacks* {
    *;
}

#
-keep class com.tencent.tinker.example.cases.** {
    *;
}