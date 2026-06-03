#include <GLES3/gl3.h>
#include <cstring>
#include <cstdio>
#include <vector>
#include <atomic>
#include <chrono>
#include <thread>
#include <android/log.h>
#include <stdatomic.h>
#include "genesis-core.hpp"

// Глобальные переменные из emulator-bridge.cpp
extern atomic_uint g_android_pads[2];

// Глобальный фреймбуфер (определён в emulator-bridge.cpp)
extern uint16_t local_framebuffer[320 * 240];

extern "C" {
#include "shared.h"

#undef FILE
#undef fopen
#undef fclose
#undef fread
#undef fwrite
#undef fseek
#undef ftell

void system_frame_gen(int skip);
void retro_set_environment(bool (*cb)(unsigned, void *));
void retro_set_video_refresh(void (*cb)(const void *, unsigned, unsigned, size_t));
void retro_set_audio_sample(void (*cb)(int16_t, int16_t));
void retro_set_audio_sample_batch(size_t (*cb)(const int16_t *, size_t));
void retro_set_input_poll(void (*cb)(void));
void retro_set_input_state(int16_t (*cb)(unsigned, unsigned, unsigned, unsigned));

int64_t core_fsize(void *stream) {
    if (!stream) return 0;
    FILE *f = (FILE *)stream;
    long current = ftell(f);
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, current, SEEK_SET);
    return size;
}
}

#define TAG "GenesisPlus"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

GenesisCore* GenesisCore::s_instance = nullptr;

static std::atomic<int> g_free_audio_buffers(4);
static int16_t g_audio_buffer_pool[4][4096]; 
static int g_next_audio_buffer = 0;

static bool libretro_environ(unsigned cmd, void *data) {
    if (cmd == 9) {
        if (GenesisCore::s_instance && data) {
            const char** dir = (const char**)data;
            *dir = GenesisCore::s_instance->save_path.c_str();
            return true;
        }
    }
    return false;
}

static void libretro_video(const void *data, unsigned width, unsigned height, size_t pitch) {}
static void libretro_audio(int16_t left, int16_t right) {}
static size_t libretro_audio_batch(const int16_t *data, size_t frames) { return frames; }
static void libretro_input_poll(void) {}

static int16_t libretro_input_state(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port < 2 && device == 1) {
        unsigned int pad = atomic_load(&g_android_pads[port]);
        
        // Битовая маска (osd_input_update_internal_bitmasks)
        if (id == RETRO_DEVICE_ID_JOYPAD_MASK) {
            int16_t mask = 0;
            if ((pad >> 0) & 1)  mask |= (1 << RETRO_DEVICE_ID_JOYPAD_UP);
            if ((pad >> 1) & 1)  mask |= (1 << RETRO_DEVICE_ID_JOYPAD_DOWN);
            if ((pad >> 2) & 1)  mask |= (1 << RETRO_DEVICE_ID_JOYPAD_LEFT);
            if ((pad >> 3) & 1)  mask |= (1 << RETRO_DEVICE_ID_JOYPAD_RIGHT);
            if ((pad >> 4) & 1)  mask |= (1 << RETRO_DEVICE_ID_JOYPAD_Y);     // A
            if ((pad >> 5) & 1)  mask |= (1 << RETRO_DEVICE_ID_JOYPAD_B);     // B
            if ((pad >> 6) & 1)  mask |= (1 << RETRO_DEVICE_ID_JOYPAD_A);     // C
            if ((pad >> 7) & 1)  mask |= (1 << RETRO_DEVICE_ID_JOYPAD_START);
            if ((pad >> 8) & 1)  mask |= (1 << RETRO_DEVICE_ID_JOYPAD_L);     // X
            if ((pad >> 9) & 1)  mask |= (1 << RETRO_DEVICE_ID_JOYPAD_X);     // Y
            if ((pad >> 10) & 1) mask |= (1 << RETRO_DEVICE_ID_JOYPAD_R);     // Z
            if ((pad >> 11) & 1) mask |= (1 << RETRO_DEVICE_ID_JOYPAD_SELECT); // MODE
            return mask;
        }
        
        // Одиночные кнопки
        switch (id) {
            case RETRO_DEVICE_ID_JOYPAD_UP:     return (pad >> 0) & 1;
            case RETRO_DEVICE_ID_JOYPAD_DOWN:   return (pad >> 1) & 1;
            case RETRO_DEVICE_ID_JOYPAD_LEFT:   return (pad >> 2) & 1;
            case RETRO_DEVICE_ID_JOYPAD_RIGHT:  return (pad >> 3) & 1;
            case RETRO_DEVICE_ID_JOYPAD_Y:      return (pad >> 4) & 1;  // A
            case RETRO_DEVICE_ID_JOYPAD_B:      return (pad >> 5) & 1;  // B
            case RETRO_DEVICE_ID_JOYPAD_A:      return (pad >> 6) & 1;  // C
            case RETRO_DEVICE_ID_JOYPAD_START:  return (pad >> 7) & 1;
            case RETRO_DEVICE_ID_JOYPAD_L:      return (pad >> 8) & 1;  // X
            case RETRO_DEVICE_ID_JOYPAD_X:      return (pad >> 9) & 1;  // Y
            case RETRO_DEVICE_ID_JOYPAD_R:      return (pad >> 10) & 1; // Z
            case RETRO_DEVICE_ID_JOYPAD_SELECT: return (pad >> 11) & 1; // MODE
        }
    }
    return 0;
}

static void bqPlayerCallback(SLAndroidSimpleBufferQueueItf bq, void *context) {
    g_free_audio_buffers++;
}

GenesisCore::~GenesisCore() {
    shutdownOpenSLES();
    if (program_id) glDeleteProgram(program_id);
    if (texture_id) glDeleteTextures(1, &texture_id);
    if (vbo_id) glDeleteBuffers(1, &vbo_id);
    if (initialized) audio_shutdown();
    s_instance = nullptr;
}

bool GenesisCore::init(const std::string& rom_path, const std::string& save_dir) {
    LOGD("Init Genesis Plus GX: %s", rom_path.c_str());
    save_path = save_dir;
    s_instance = this;

    retro_set_environment(libretro_environ);
    retro_set_video_refresh(libretro_video);
    retro_set_audio_sample(libretro_audio);
    retro_set_audio_sample_batch(libretro_audio_batch);
    retro_set_input_poll(libretro_input_poll);
    retro_set_input_state(libretro_input_state);

    std::memset(&config, 0, sizeof(config));
    config.hq_fm = 1;
    config.filter = 1;
    config.psg_preamp = 150;
    config.fm_preamp = 150;
    config.lp_range = 0x9999;
    config.region_detect = 0;
    config.vdp_mode = 0;
    config.master_clock = 0;

    for (int i = 0; i < 8; i++) {
        config.input[i].padtype = DEVICE_PAD6B;
    }

    if (!load_rom((char*)rom_path.c_str())) {
        LOGE("Failed to load ROM");
        return false;
    }

    audio_init(44100, 60.0);
    
    input.system[0] = SYSTEM_GAMEPAD;
    input.system[1] = SYSTEM_GAMEPAD;
    input.dev[0] = DEVICE_PAD6B;
    input.dev[1] = DEVICE_PAD6B;

    system_init();

    std::memset(::local_framebuffer, 0, sizeof(::local_framebuffer));
    bitmap.width  = 320;
    bitmap.height = 240;
    bitmap.pitch  = 320 * sizeof(uint16_t);
    bitmap.data   = (uint8_t*)::local_framebuffer;
    vdp_init();

    system_reset();
    initOpenSLES();

    initialized = true;
    LOGD("Genesis core ready");
    return true;
}

void GenesisCore::runFrame() {
    if (!initialized) return;

    system_frame_gen(0);

    int16_t temp_samples[2048];
    int samples_emulated = audio_update(temp_samples);
    
    if (samples_emulated > 0 && player_buffer_queue) {
        int timeout = 0;
        while (g_free_audio_buffers <= 0 && timeout < 2000) {
            std::this_thread::yield();
            timeout++;
        }
        if (g_free_audio_buffers > 0) {
            g_free_audio_buffers--;
            size_t bytes = samples_emulated * 2 * sizeof(int16_t);
            std::memcpy(g_audio_buffer_pool[g_next_audio_buffer], temp_samples, bytes);
            (*player_buffer_queue)->Enqueue(player_buffer_queue, g_audio_buffer_pool[g_next_audio_buffer], bytes);
            g_next_audio_buffer = (g_next_audio_buffer + 1) % 4;
        }
    }
}

void GenesisCore::initOpenGL() {
    if (program_id != 0) return;
    const char* vs = "attribute vec4 vPosition;\nattribute vec2 vTexCoord;\nvarying vec2 fTexCoord;\nvoid main() { gl_Position = vPosition; fTexCoord = vTexCoord; }\n";
    const char* fs = "precision mediump float;\nvarying vec2 fTexCoord;\nuniform sampler2D uTexture;\nvoid main() { gl_FragColor = texture2D(uTexture, fTexCoord); }\n";
    program_id = glCreateProgram();
    GLuint vsh = compileShader(GL_VERTEX_SHADER, vs);
    GLuint fsh = compileShader(GL_FRAGMENT_SHADER, fs);
    glAttachShader(program_id, vsh); glAttachShader(program_id, fsh);
    glLinkProgram(program_id);
    glDeleteShader(vsh); glDeleteShader(fsh);
    float vertices[] = { -1,1,0,0, -1,-1,0,1, 1,1,1,0, 1,-1,1,1 };
    glGenBuffers(1, &vbo_id); glBindBuffer(GL_ARRAY_BUFFER, vbo_id);
    glBufferData(GL_ARRAY_BUFFER, sizeof(vertices), vertices, GL_STATIC_DRAW);
    glGenTextures(1, &texture_id); glBindTexture(GL_TEXTURE_2D, texture_id);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, 320, 240, 0, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, nullptr);
}

GLuint GenesisCore::compileShader(GLenum type, const char* src) {
    GLuint s = glCreateShader(type); glShaderSource(s, 1, &src, nullptr); glCompileShader(s); return s;
}

void GenesisCore::render() {
    if (!initialized) return;
    initOpenGL();
    int w = bitmap.viewport.w > 0 ? bitmap.viewport.w : 320;
    int h = bitmap.viewport.h > 0 ? bitmap.viewport.h : 240;
    glClearColor(0,0,0,1); glClear(GL_COLOR_BUFFER_BIT); glUseProgram(program_id);
    glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, texture_id);
    glPixelStorei(GL_UNPACK_ROW_LENGTH, 320);
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, w, h, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, ::local_framebuffer);
    glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
    glUniform1i(glGetUniformLocation(program_id, "uTexture"), 0);
    glBindBuffer(GL_ARRAY_BUFFER, vbo_id);
    GLint pa = glGetAttribLocation(program_id, "vPosition"); glEnableVertexAttribArray(pa);
    glVertexAttribPointer(pa, 2, GL_FLOAT, GL_FALSE, 16, 0);
    GLint ta = glGetAttribLocation(program_id, "vTexCoord"); glEnableVertexAttribArray(ta);
    glVertexAttribPointer(ta, 2, GL_FLOAT, GL_FALSE, 16, (void*)8);
    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
}

void GenesisCore::setButton(int player, int button, bool pressed) {
    if (player >= 0 && player <= 1 && button >= 0 && button < 12) {
        if (pressed) {
            atomic_fetch_or(&g_android_pads[player], (1u << button));
        } else {
            atomic_fetch_and(&g_android_pads[player], ~(1u << button));
        }
    }
}

bool GenesisCore::saveState(int slot) {
    if (!initialized) return false;
    char p[512]; snprintf(p, sizeof(p), "%s/save_%d.state", save_path.c_str(), slot);
    FILE* f = std::fopen(p, "wb"); if (!f) return false;
    std::vector<unsigned char> buf(1024*1024*2);
    int sz = state_save(buf.data());
    if (sz > 0) std::fwrite(buf.data(), 1, sz, f);
    std::fclose(f); return sz > 0;
}

bool GenesisCore::loadState(int slot) {
    if (!initialized) return false;
    char p[512]; snprintf(p, sizeof(p), "%s/save_%d.state", save_path.c_str(), slot);
    FILE* f = std::fopen(p, "rb"); if (!f) return false;
    std::fseek(f, 0, SEEK_END); long sz = std::ftell(f); std::fseek(f, 0, SEEK_SET);
    std::vector<unsigned char> buf(sz);
    size_t rd = std::fread(buf.data(), 1, sz, f); std::fclose(f);
    return (rd > 0) ? state_load(buf.data()) > 0 : false;
}

void GenesisCore::initOpenSLES() {
    slCreateEngine(&engine_object, 0, nullptr, 0, nullptr, nullptr);
    (*engine_object)->Realize(engine_object, SL_BOOLEAN_FALSE);
    (*engine_object)->GetInterface(engine_object, SL_IID_ENGINE, &engine_interface);
    (*engine_interface)->CreateOutputMix(engine_interface, &output_mix_object, 0, nullptr, nullptr);
    (*output_mix_object)->Realize(output_mix_object, SL_BOOLEAN_FALSE);
    SLDataLocator_AndroidSimpleBufferQueue lbq = { SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 4 };
    SLDataFormat_PCM fmt = { SL_DATAFORMAT_PCM, 2, SL_SAMPLINGRATE_44_1, SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16, SL_SPEAKER_FRONT_LEFT|SL_SPEAKER_FRONT_RIGHT, SL_BYTEORDER_LITTLEENDIAN };
    SLDataSource src = { &lbq, &fmt };
    SLDataLocator_OutputMix om = { SL_DATALOCATOR_OUTPUTMIX, output_mix_object };
    SLDataSink snk = { &om, nullptr };
    const SLInterfaceID ids[1] = { SL_IID_BUFFERQUEUE };
    const SLboolean req[1] = { SL_BOOLEAN_TRUE };
    (*engine_interface)->CreateAudioPlayer(engine_interface, &player_object, &src, &snk, 1, ids, req);
    (*player_object)->Realize(player_object, SL_BOOLEAN_FALSE);
    (*player_object)->GetInterface(player_object, SL_IID_PLAY, &player_play);
    (*player_object)->GetInterface(player_object, SL_IID_BUFFERQUEUE, &player_buffer_queue);
    (*player_buffer_queue)->RegisterCallback(player_buffer_queue, bqPlayerCallback, nullptr);
    (*player_play)->SetPlayState(player_play, SL_PLAYSTATE_PLAYING);
    g_free_audio_buffers = 4; g_next_audio_buffer = 0;
    int16_t sil[735*2] = {0}; g_free_audio_buffers -= 2;
    (*player_buffer_queue)->Enqueue(player_buffer_queue, sil, sizeof(sil));
    (*player_buffer_queue)->Enqueue(player_buffer_queue, sil, sizeof(sil));
}

void GenesisCore::shutdownOpenSLES() {
    if (player_play) (*player_play)->SetPlayState(player_play, SL_PLAYSTATE_STOPPED);
    if (player_buffer_queue) (*player_buffer_queue)->Clear(player_buffer_queue);
    if (player_object) { (*player_object)->Destroy(player_object); player_object = nullptr; player_play = nullptr; player_buffer_queue = nullptr; }
    if (output_mix_object) { (*output_mix_object)->Destroy(output_mix_object); output_mix_object = nullptr; }
    if (engine_object) { (*engine_object)->Destroy(engine_object); engine_object = nullptr; engine_interface = nullptr; }
}
