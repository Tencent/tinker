package com.tencent.tinker.internal.load.library.test;

public class TestLibrary {

    static {
        System.loadLibrary("tinker.test.jni");
    }

    public static native String fromJni();
    public static native String fromDependency();
}
