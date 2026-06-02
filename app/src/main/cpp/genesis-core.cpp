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

// --- AUDIO SYNC ---
static std::atomic<int> g_free_audio_buffers(4);

static void bqPlayerCallback(SLAndroidSimpleBufferQueueItf bq, void *context) {
    g_free_audio_buffers++;
}

// --- INPUT STATE ---
static uint16_t g_input_state[2] = {0xFFFF, 0xFFFF};

// Libretro callbacks
static bool retro_environment_cb(unsigned cmd, void *data) {
    return false;
}

static void retro_video_refresh_cb(const void *data, unsigned width, unsigned height, size_t pitch) {
    // Видео обновляется через bitmap напрямую
}

static void retro_audio_sample_cb(int16_t left, int16_t right) {
    // Аудио берется через audio_update
}

static size_t retro_audio_sample_batch_cb(const int16_t *data, size_t frames) {
    return frames;
}

static void retro_input_poll_cb(void) {
    // Ввод уже установлен
}

static int16_t retro_input_state_cb(unsigned port, unsigned device, unsigned index, unsigned id) {
    if (port > 1) return 0;
    
    // Инвертируем биты потому что в Genesis 0 = нажата
    uint16_t state = ~g_input_state[port];
    
    switch (id) {
        case RETRO_DEVICE_ID_JOYPAD_UP:    return (state >> 0) & 1;
        case RETRO_DEVICE_ID_JOYPAD_DOWN:  return (state >> 1) & 1;
        case RETRO_DEVICE_ID_JOYPAD_LEFT:  return (state >> 2) & 1;
        case RETRO_DEVICE_ID_JOYPAD_RIGHT: return (state >> 3) & 1;
        case RETRO_DEVICE_ID_JOYPAD_B:     return (state >> 4) & 1; // A
        case RETRO_DEVICE_ID_JOYPAD_A:     return (state >> 6) & 1; // C
        case RETRO_DEVICE_ID_JOYPAD_Y:     return (state >> 5) & 1; // B
        case RETRO_DEVICE_ID_JOYPAD_START: return (state >> 7) & 1;
        default: return 0;
    }
}

GenesisCore::~GenesisCore() {
    shutdownOpenSLES();
    if (program_id) glDeleteProgram(program_id);
    if (texture_id) glDeleteTextures(1, &texture_id);
    if (vbo_id) glDeleteBuffers(1, &vbo_id);
}

bool GenesisCore::init(const std::string& rom_path, const std::string& save_dir) {
    LOGD("Init Genesis Plus GX: %s", rom_path.c_str());
    save_path = save_dir;

    // Установка libretro коллбэков
    retro_set_environment(retro_environment_cb);
    retro_set_video_refresh(retro_video_refresh_cb);
    retro_set_audio_sample(retro_audio_sample_cb);
    retro_set_audio_sample_batch(retro_audio_sample_batch_cb);
    retro_set_input_poll(retro_input_poll_cb);
    retro_set_input_state(retro_input_state_cb);

    // Конфигурация
    memset(&config, 0, sizeof(config));
    config.hq_fm = 1;
    config.filter = 1;
    config.psg_preamp = 100;
    config.fm_preamp = 100;
    config.lp_range = 0x9999;
    config.region_detect = 0;
    config.vdp_mode = 0;
    config.master_clock = 0;
    config.render = 1;

    // Загрузка ROM
    if (!load_rom((char*)rom_path.c_str())) {
        LOGE("Failed to load ROM");
        return false;
    }

    // Инициализация аудио
    audio_init(44100, 60.0);
    
    // Инициализация системы
    system_init();
    
    // Настройка bitmap для рендеринга
    memset(local_framebuffer, 0, sizeof(local_framebuffer));
    bitmap.width  = 320;
    bitmap.height = 240;
    bitmap.pitch  = 320 * 2;
    bitmap.data   = (uint8_t*)local_framebuffer;
    
    // Сброс
    system_reset();
    
    // Инициализация OpenSLES
    initOpenSLES();

    initialized = true;
    LOGD("Genesis core ready");
    return true;
}

void GenesisCore::runFrame() {
    if (!initialized) return;

    // Обновляем ввод ПЕРЕД фреймом
    osd_input_update();
    
    // Запускаем оригинальный фрейм эмулятора
    system_frame(0);

    // Получаем аудио
    int16_t audio_samples[2048];
    int samples = audio_update(audio_samples);

    if (samples > 0 && player_buffer_queue) {
        // Ждем свободный буфер для синхронизации скорости
        int timeout = 0;
        while (g_free_audio_buffers <= 0 && timeout < 100) {
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
            timeout++;
        }

        if (g_free_audio_buffers > 0) {
            g_free_audio_buffers--;
        }

        (*player_buffer_queue)->Enqueue(player_buffer_queue, audio_samples, samples * 2 * sizeof(int16_t));
    }
}

void GenesisCore::initOpenGL() {
    if (program_id != 0) return;

    const char* vs =
        "attribute vec4 vPosition;\n"
        "attribute vec2 vTexCoord;\n"
        "varying vec2 fTexCoord;\n"
        "void main() {\n"
        "  gl_Position = vPosition;\n"
        "  fTexCoord = vTexCoord;\n"
        "}\n";

    const char* fs =
        "precision mediump float;\n"
        "varying vec2 fTexCoord;\n"
        "uniform sampler2D uTexture;\n"
        "void main() {\n"
        "  gl_FragColor = texture2D(uTexture, fTexCoord);\n"
        "}\n";

    program_id = glCreateProgram();
    GLuint vsh = compileShader(GL_VERTEX_SHADER, vs);
    GLuint fsh = compileShader(GL_FRAGMENT_SHADER, fs);
    glAttachShader(program_id, vsh);
    glAttachShader(program_id, fsh);
    glLinkProgram(program_id);
    glDeleteShader(vsh);
    glDeleteShader(fsh);

    float vertices[] = {
        -1.0f,  1.0f,  0.0f, 0.0f,
        -1.0f, -1.0f,  0.0f, 1.0f,
         1.0f,  1.0f,  1.0f, 0.0f,
         1.0f, -1.0f,  1.0f, 1.0f
    };

    glGenBuffers(1, &vbo_id);
    glBindBuffer(GL_ARRAY_BUFFER, vbo_id);
    glBufferData(GL_ARRAY_BUFFER, sizeof(vertices), vertices, GL_STATIC_DRAW);

    glGenTextures(1, &texture_id);
    glBindTexture(GL_TEXTURE_2D, texture_id);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
}

GLuint GenesisCore::compileShader(GLenum type, const char* source) {
    GLuint shader = glCreateShader(type);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    return shader;
}

void GenesisCore::render() {
    if (!initialized) return;
    initOpenGL();

    int w = bitmap.viewport.w > 0 ? bitmap.viewport.w : 320;
    int h = bitmap.viewport.h > 0 ? bitmap.viewport.h : 240;

    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    glUseProgram(program_id);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, texture_id);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, 320, 240, 0, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, local_framebuffer);
    glUniform1i(glGetUniformLocation(program_id, "uTexture"), 0);

    glBindBuffer(GL_ARRAY_BUFFER, vbo_id);

    GLint pos = glGetAttribLocation(program_id, "vPosition");
    glEnableVertexAttribArray(pos);
    glVertexAttribPointer(pos, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void*)0);

    GLint tex = glGetAttribLocation(program_id, "vTexCoord");
    glEnableVertexAttribArray(tex);
    glVertexAttribPointer(tex, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void*)(2 * sizeof(float)));

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
}

void GenesisCore::setButton(int player, int button, bool pressed) {
    if (player < 0 || player > 1 || button < 0 || button > 7) return;

    if (pressed) {
        g_input_state[player] &= ~(1 << button);  // 0 = нажата
    } else {
        g_input_state[player] |= (1 << button);   // 1 = отпущена
    }
}

bool GenesisCore::saveState(int slot) {
    if (!initialized) return false;
    char path[512];
    snprintf(path, sizeof(path), "%s/save_%d.state", save_path.c_str(), slot);
    FILE* f = fopen(path, "wb");
    if (!f) return false;
    unsigned char buf[512 * 1024];
    int size = state_save(buf);
    if (size > 0) fwrite(buf, 1, size, f);
    fclose(f);
    return size > 0;
}

bool GenesisCore::loadState(int slot) {
    if (!initialized) return false;
    char path[512];
    snprintf(path, sizeof(path), "%s/save_%d.state", save_path.c_str(), slot);
    FILE* f = fopen(path, "rb");
    if (!f) return false;
    fseek(f, 0, SEEK_END);
    long size = ftell(f);
    fseek(f, 0, SEEK_SET);
    std::vector<unsigned char> buf(size);
    size_t read = fread(buf.data(), 1, size, f);
    fclose(f);
    return (read > 0) ? state_load(buf.data()) > 0 : false;
}

void GenesisCore::initOpenSLES() {
    slCreateEngine(&engine_object, 0, nullptr, 0, nullptr, nullptr);
    (*engine_object)->Realize(engine_object, SL_BOOLEAN_FALSE);
    (*engine_object)->GetInterface(engine_object, SL_IID_ENGINE, &engine_interface);

    (*engine_interface)->CreateOutputMix(engine_interface, &output_mix_object, 0, nullptr, nullptr);
    (*output_mix_object)->Realize(output_mix_object, SL_BOOLEAN_FALSE);

    SLDataLocator_AndroidSimpleBufferQueue loc_bufq = { SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 4 };
    SLDataFormat_PCM format_pcm = {
        SL_DATAFORMAT_PCM, 2, SL_SAMPLINGRATE_44_1,
        SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
        SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT, SL_BYTEORDER_LITTLEENDIAN
    };

    SLDataSource audio_src = { &loc_bufq, &format_pcm };
    SLDataLocator_OutputMix loc_outmix = { SL_DATALOCATOR_OUTPUTMIX, output_mix_object };
    SLDataSink audio_snk = { &loc_outmix, nullptr };

    const SLInterfaceID ids[1] = { SL_IID_BUFFERQUEUE };
    const SLboolean req[1] = { SL_BOOLEAN_TRUE };

    (*engine_interface)->CreateAudioPlayer(engine_interface, &player_object, &audio_src, &audio_snk, 1, ids, req);
    (*player_object)->Realize(player_object, SL_BOOLEAN_FALSE);
    (*player_object)->GetInterface(player_object, SL_IID_PLAY, &player_play);
    (*player_object)->GetInterface(player_object, SL_IID_BUFFERQUEUE, &player_buffer_queue);

    (*player_buffer_queue)->RegisterCallback(player_buffer_queue, bqPlayerCallback, nullptr);
    (*player_play)->SetPlayState(player_play, SL_PLAYSTATE_PLAYING);

    g_free_audio_buffers = 4;

    int16_t silence[735 * 2] = {0};
    g_free_audio_buffers -= 2;
    (*player_buffer_queue)->Enqueue(player_buffer_queue, silence, sizeof(silence));
    (*player_buffer_queue)->Enqueue(player_buffer_queue, silence, sizeof(silence));
}

void GenesisCore::shutdownOpenSLES() {
    if (player_play) (*player_play)->SetPlayState(player_play, SL_PLAYSTATE_STOPPED);
    if (player_object) {
        (*player_object)->Destroy(player_object);
        player_object = nullptr;
        player_buffer_queue = nullptr;
    }
    if (output_mix_object) {
        (*output_mix_object)->Destroy(output_mix_object);
        output_mix_object = nullptr;
    }
    if (engine_object) {
        (*engine_object)->Destroy(engine_object);
        engine_object = nullptr;
        engine_interface = nullptr;
    }
}
