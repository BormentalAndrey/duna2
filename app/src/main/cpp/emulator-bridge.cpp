#include <jni.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <string>
#include <thread>
#include <mutex>
#include "genesis-core.hpp"

#define LOG_TAG "Dune2Emu"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

static GenesisCore* g_emu = nullptr;
static std::thread g_emu_thread;
static std::mutex g_mutex;
static bool g_running = false;

static EGLDisplay g_display = EGL_NO_DISPLAY;
static EGLSurface g_surface = EGL_NO_SURFACE;
static EGLContext g_context = EGL_NO_CONTEXT;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_dune2emu_RetroBridge_initEmulator(JNIEnv* env, jobject thiz,
                                           jstring rom_path, jstring save_dir) {
    std::lock_guard<std::mutex> lock(g_mutex);
    
    const char* rom = env->GetStringUTFChars(rom_path, nullptr);
    const char* save = env->GetStringUTFChars(save_dir, nullptr);
    
    g_emu = new GenesisCore();
    bool success = g_emu->init(rom, save);
    
    env->ReleaseStringUTFChars(rom_path, rom);
    env->ReleaseStringUTFChars(save_dir, save);
    
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dune2emu_RetroBridge_setSurface(JNIEnv* env, jobject thiz, jobject surface) {
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) return JNI_FALSE;
    
    g_display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    eglInitialize(g_display, nullptr, nullptr);
    
    EGLint attribs[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_BLUE_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_RED_SIZE, 8,
        EGL_NONE
    };
    
    EGLConfig config;
    EGLint num_configs;
    eglChooseConfig(g_display, attribs, &config, 1, &num_configs);
    
    g_surface = eglCreateWindowSurface(g_display, config, window, nullptr);
    
    EGLint ctx_attribs[] = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE };
    g_context = eglCreateContext(g_display, config, EGL_NO_CONTEXT, ctx_attribs);
    
    // КРИТИЧЕСКИ ВАЖНО: Отпускаем контекст на UI-потоке, чтобы его занял рабочий поток эмуляции
    eglMakeCurrent(g_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_dune2emu_RetroBridge_startEmulation(JNIEnv* env, jobject thiz) {
    g_running = true;
    
    g_emu_thread = std::thread([]() {
        // 1. Привязываем EGL контекст к выделенному фоновому потоку
        if (g_display != EGL_NO_DISPLAY && g_surface != EGL_NO_SURFACE && g_context != EGL_NO_CONTEXT) {
            eglMakeCurrent(g_display, g_surface, g_surface, g_context);
        }

        while (g_running) {
            std::lock_guard<std::mutex> lock(g_mutex);
            
            if (g_emu) {
                g_emu->runFrame();
                g_emu->render();
                
                if (g_display != EGL_NO_DISPLAY) {
                    eglSwapBuffers(g_display, g_surface);
                }
            }
        }

        // 2. Освобождаем контекст перед завершением потока
        if (g_display != EGL_NO_DISPLAY) {
            eglMakeCurrent(g_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        }
    });
}

JNIEXPORT void JNICALL
Java_com_dune2emu_RetroBridge_stopEmulation(JNIEnv* env, jobject thiz) {
    g_running = false;
    
    if (g_emu_thread.joinable()) {
        g_emu_thread.join();
    }
    
    std::lock_guard<std::mutex> lock(g_mutex);
    
    if (g_emu) {
        delete g_emu;
        g_emu = nullptr;
    }
    
    if (g_display != EGL_NO_DISPLAY) {
        eglMakeCurrent(g_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (g_surface != EGL_NO_SURFACE) eglDestroySurface(g_display, g_surface);
        if (g_context != EGL_NO_CONTEXT) eglDestroyContext(g_display, g_context);
    }
    g_surface = EGL_NO_SURFACE;
    g_context = EGL_NO_CONTEXT;
}

JNIEXPORT void JNICALL
Java_com_dune2emu_RetroBridge_setButtonState(JNIEnv* env, jobject thiz,
                                             jint player, jint button, jboolean pressed) {
    if (g_emu) {
        g_emu->setButton(player, button, pressed);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_dune2emu_RetroBridge_saveState(JNIEnv* env, jobject thiz, jint slot) {
    return g_emu ? g_emu->saveState(slot) : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_dune2emu_RetroBridge_loadState(JNIEnv* env, jobject thiz, jint slot) {
    return g_emu ? g_emu->loadState(slot) : JNI_FALSE;
}

} // extern "C"
