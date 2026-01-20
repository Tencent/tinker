#include <jni.h>

int example_from_dependency();

static jboolean jni_from_jni(JNIEnv *env, jclass _) {
#ifdef __example_updated__
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

static jboolean jni_from_dependency(JNIEnv *env, jclass _) {
    return example_from_dependency() == 1 ? JNI_TRUE : JNI_FALSE;
}

static const JNINativeMethod jni_methods_[] = {
        {
                "fromJni",
                "()Z",
                jni_from_jni,
        },
        {
                "fromDependency",
                "()Z",
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
            "com/tencent/tinker/example/cases/Library"
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
