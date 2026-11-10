#include <jni.h>
#include <string>
#include <android/log.h>

#define TAG "WhisperJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_orvex_traductor_MainActivity_stringFromJNI(
        JNIEnv *env,
        jobject /* this */) {
    std::string hello = "Whisper.cpp v1.7.6 Integrado (V0.2 + V0.3 Translation)";
    return env->NewStringUTF(hello.c_str());
}

}
