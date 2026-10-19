# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/zhangshaowen/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# NOTE for tinker patch authors:
# When a patch introduces NEW static methods or fields on an existing class,
# proguard may strip or rename them in the patch build because they are not
# referenced from any code path exercised at minification time in the base
# build. To avoid this, either:
#   (a) ensure the new static members are referenced from already-shipped
#       code in the base apk, or
#   (b) add explicit keep rules in the host app's proguard config, e.g.:
#         -keep class your.pkg.YourClass { public static *; }
# Additionally, always feed the mapping file produced by the base build into
# the patch build via `-applymapping <base-mapping.txt>` so that obfuscated
# names stay consistent between the base apk and the patch dex.
