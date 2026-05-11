#include <jni.h>

extern "C" JNIEXPORT jstring JNICALL
Java_com_lance_litertchat_inference_NativeLlamaBridge_nativeRuntimeVersion(
        JNIEnv *env,
        jobject /* thiz */) {
    return env->NewStringUTF("native-llamacpp");
}
