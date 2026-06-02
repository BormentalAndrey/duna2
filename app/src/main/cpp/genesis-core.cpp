#include <GLES3/gl3.h>
#include <cstring>
#include <cstdio>
#include <vector>
#include <atomic>
#include <chrono>
#include <thread>
#include <android/log.h>
#include "genesis-core.hpp"

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

static std::atomic<bool> g_player_buttons[2][8] = {};

static std::atomic<int> g_free_audio_buffers(4);
static int16_t g_audio_buffer_pool[4][4096]; 
static int g_next_audio_buffer = 0;

static bool libretro_environ(unsigned cmd, void *data) {
    if (cmd == 9) { // RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY
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

// Опрашивается внутренним механизмом libretro.c
static int16_t libretro_input_state(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port < 2 && device == 1) { // 1 = RETRO_DEVICE_JOYPAD
        switch (id) {
            case 4: return g_player_buttons[port][0].load() ? 1 : 0; // UP
            case 5: return g_player_buttons[port][1].load() ? 1 : 0; // DOWN
            case 6: return g_player_buttons[port][2].load() ? 1 : 0; // LEFT
            case 7: return g_player_buttons[port][3].load() ? 1 : 0; // RIGHT
            case 1: return g_player_buttons[port][4].load() ? 1 : 0; // Retro Y -> Genesis A
            case 0: return g_player_buttons[port][5].load() ? 1 : 0; // Retro B -> Genesis B
            case 8: return g_player_buttons[port][6].load() ? 1 : 0; // Retro A -> Genesis C
            case 3: return g_player_buttons[port][7].load() ? 1 : 0; // START
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

    if (!load_rom((char*)rom_path.c_str())) {
        LOGE("Failed to load ROM");
        return false;
    }

    audio_init(44100, 60.0);
    
    // ВАЖНО: Инициализация ввода ДО сброса системы. 
    // Это гарантирует, что эмулятор физически "подключит" геймпады к шине.
    input.system[0] = SYSTEM_GAMEPAD;
    input.system[1] = SYSTEM_GAMEPAD;
    input.dev[0] = DEVICE_PAD3B; // 3-кнопочный геймпад стабильнее для Dune 2
    input.dev[1] = DEVICE_PAD3B;

    system_init();

    std::memset(local_framebuffer, 0, sizeof(local_framebuffer));
    bitmap.width  = 320;
    bitmap.height = 240;
    bitmap.pitch  = 320 * sizeof(uint16_t);
    bitmap.data   = (uint8_t*)local_framebuffer;
    vdp_init();

    // Сброс прочитает input.system и правильно настроит IO порты
    system_reset();

    initOpenSLES();

    initialized = true;
    LOGD("Genesis core ready");
    return true;
}

void GenesisCore::runFrame() {
    if (!initialized) return;

    // Оригинальное ядро libretro.c само опросит libretro_input_state 
    // и смаппит кнопки внутри этого вызова. Никаких хаков с массивами!
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
    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, w, h, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, local_framebuffer);
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
    if (player >= 0 && player <= 1 && button >= 0 && button < 8) {
        g_player_buttons[player][button].store(pressed);
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
