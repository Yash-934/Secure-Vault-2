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

// Advanced Manual Obfuscation (Simulating OLLVM -fla, -bcf, -sub)
// Applied via Clang attribute noinline and opaque constants
__attribute__((noinline)) int opaquePredicate1(int x) {
    return (x * x + x) % 2 == 0;
}

__attribute__((noinline)) int opaquePredicate2(int y) {
    volatile int a = y;
    volatile int b = a + 1;
    return (a * b) % 2 == 0;
}

__attribute__((noinline)) void substituteInstructions(volatile int* acc) {
    int a = *acc & 0xFF;
    int b = 0x42;
    // (a ^ b) + 2*(a & b) == a + b
    int sub = (a ^ b) + 2 * (a & b);
    *acc = (*acc & 0xFF00) | (sub & 0xFF);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_security_NativeBridge_getSecretString(JNIEnv *env, jobject /* this */, jint id) {
    // Basic XOR string encryption in native layer with split functions
    volatile int bogus = 0;
    if (!opaquePredicate1(id)) {
        bogus = 1;
    }
    
    char secret[] = { 0x55, 0x47, 0x45, 0x48, 0x41, 0x00 }; // "frida" ^ 0x33
    if (id == 1 || bogus) {
        for (int i = 0; i < 5; i++) {
            secret[i] ^= 0x33;
        }
        if (bogus) {
            return env->NewStringUTF("BOGUS");
        }
        return env->NewStringUTF(secret);
    }
    return env->NewStringUTF("UNKNOWN");
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_security_NativeBridge_runObfuscatedCheck(JNIEnv *env, jobject /* this */) {
    // Native control flow flattening and opaque predicates (OLLVM simulation)
    volatile int state = 1;
    volatile int accumulator = 0xA5A5;
    
    while(state != 0) {
        switch (state) {
            case 1:
                if (opaquePredicate1(5)) {
                    accumulator ^= 0x3C3C;
                    state = 3;
                } else {
                    state = 99;
                }
                break;
            case 2:
                // Unreachable state (bogus control flow)
                accumulator = 0xDEAD;
                state = 0;
                break;
            case 3:
                if (opaquePredicate2(10)) {
                    state = 4;
                } else {
                    state = 2; // Bogus
                }
                break;
            case 4:
                substituteInstructions(&accumulator);
                state = 0;
                break;
            case 99:
                accumulator = -1;
                state = 0;
                break;
            default:
                state = 0;
                break;
        }
    }
    return accumulator;
}
