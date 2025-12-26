#include <jni.h>

const char *tinker_test_from_dependency();

static jstring jni_from_jni(JNIEnv *env, jclass _) {
    return (*env)->NewStringUTF(env, "<_B_>");
}

static jstring jni_from_dependency(JNIEnv *env, jclass _) {
    return (*env)->NewStringUTF(env, tinker_test_from_dependency());
}

static const JNINativeMethod jni_methods_[] = {
        {
                "fromJni",
                "()Ljava/lang/String;",
                jni_from_jni,
        },
        {
                "fromDependency",
                "()Ljava/lang/String;",
                jni_from_dependency,
        },
};

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    jclass clazz = (*env)->FindClass(
            env,
            "com/tencent/tinker/internal/load/library/test/TestLibrary"
    );
    if (clazz == NULL) {
        return JNI_ERR;
    }
    jint result = (*env)->RegisterNatives(
            env,
            clazz,
            jni_methods_,
            sizeof(jni_methods_) / sizeof(JNINativeMethod)
    );
    if (result != JNI_OK) {
        return result;
    }
    return JNI_VERSION_1_6;
}
