#include <jni.h>
#include <string>
#include <sys/mman.h>
#include <android/log.h>
#include <vector>
#include <sys/prctl.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "NativeSecurity", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "NativeSecurity", __VA_ARGS__)

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    // Disable core dumps for the process to prevent memory extraction on crash
    prctl(PR_SET_DUMPABLE, 0);
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_security_NativeBridge_mlockBuffer(JNIEnv *env, jobject /* this */, jobject buffer, jint size) {
    if (buffer == nullptr || size <= 0) return JNI_FALSE;
    void* ptr = env->GetDirectBufferAddress(buffer);
    if (ptr == nullptr) return JNI_FALSE;

    if (mlock(ptr, size) == 0) {
        return JNI_TRUE;
    } else {
        LOGE("mlock failed");
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_security_NativeBridge_munlockBuffer(JNIEnv *env, jobject /* this */, jobject buffer, jint size) {
    if (buffer == nullptr || size <= 0) return JNI_FALSE;
    void* ptr = env->GetDirectBufferAddress(buffer);
    if (ptr == nullptr) return JNI_FALSE;

    if (munlock(ptr, size) == 0) {
        return JNI_TRUE;
    } else {
        return JNI_FALSE;
    }
}

// Obfuscated String Decryption (Example XOR)
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_security_NativeBridge_getSecretString(JNIEnv *env, jobject /* this */, jint id) {
    // Basic XOR string encryption in native layer
    // Real implementation would use OLLVM -fla, -bcf, -sub
    char secret[] = { 0x55, 0x47, 0x45, 0x48, 0x41, 0x00 }; // "frida" ^ 0x33 -> 66 72 69 64 61 -> 55 47 45 48 41
    if (id == 1) {
        for (int i = 0; i < 5; i++) {
            secret[i] ^= 0x33;
        }
        return env->NewStringUTF(secret);
    }
    return env->NewStringUTF("UNKNOWN");
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_security_NativeBridge_runObfuscatedCheck(JNIEnv *env, jobject /* this */) {
    // Native control flow flattening and opaque predicates
    volatile int state = 1;
    volatile int accumulator = 0xA5A5;
    
    while(state != 0) {
        if (state == 1) {
            // Opaque predicate
            int x = 5;
            if ((x * x + x) % 2 == 0) {
                accumulator ^= 0x3C3C;
                state = 2;
            } else {
                state = 99;
            }
        } else if (state == 2) {
            // Instruction substitution
            int a = accumulator & 0xFF;
            int b = 0x42;
            int sub = (a ^ b) + 2*(a & b);
            accumulator = (accumulator & 0xFF00) | (sub & 0xFF);
            state = 0;
        } else {
            state = 0;
        }
    }
    return accumulator;
}
