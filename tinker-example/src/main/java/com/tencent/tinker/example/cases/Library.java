package com.tencent.tinker.example.cases;

public class Library {

    static {
        System.loadLibrary("example.jni");
    }

    public static native boolean fromJni();
    public static native boolean fromDependency();
}
