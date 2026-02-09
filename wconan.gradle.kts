/**
 * version: 0.1.0
 */

if (extra.has("wconanBuild") && extra.get("wconanBuild") == true) {
    allprojects {
        apply(plugin = "com.tencent.wconan.init")
    }
}