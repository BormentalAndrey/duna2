// 1. КРИТИЧЕСКИ ВАЖНО: OpenGL ES на самом верху, до эмулятора и локальных заголовков
#include <GLES2/gl2.h>

#include "genesis-core.hpp"
#include <cstring>
#include <cstdio>
#include <vector>
#include <android/log.h>

extern "C" {
#include "shared.h"

// Genesis Plus GX использует разные функции цикла для каждой консоли.
void system_frame_gen(int skip);

// Заглушка для libchdr. Требуется линкером из-за флага сборки -D__LIBRETRO__
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

// Полностью нейтрализуем макросы Libretro для стандартного ввода-вывода
#undef FILE
#undef fopen
#undef fclose
#undef fwrite
#undef fread
#undef fseek
#undef ftell

#define TAG "GenesisPlus"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define SEGA_UP    0x0001
#define SEGA_DOWN  0x0002
#define SEGA_LEFT  0x0004
#define SEGA_RIGHT 0x0008
#define SEGA_B     0x0010
#define SEGA_C     0x0020
#define SEGA_A     0x0040
#define SEGA_START 0x0080

static void bqPlayerCallback(SLAndroidSimpleBufferQueueItf bq, void *context) {}

GenesisCore::~GenesisCore() {
    shutdownOpenSLES();
    if (program_id) glDeleteProgram(program_id);
    if (texture_id) glDeleteTextures(1, &texture_id);
    if (vbo_id) glDeleteBuffers(1, &vbo_id);
    if (initialized) {
        audio_shutdown();
    }
}

bool GenesisCore::init(const std::string& rom_path, const std::string& save_dir) {
    LOGD("Initializing Genesis Plus GX Core for ROM: %s", rom_path.c_str());
    save_path = save_dir;

    // 1. Предварительная настройка конфигурации структуры эмулятора
    memset(&config, 0, sizeof(config));
    config.hq_fm = 1;         
    config.filter = 1;        
    config.psg_preamp = 150;
    config.fm_preamp = 150;
    config.lp_range = 0x9999; 
    config.region_detect = 0;
    config.vdp_mode = 0; 
    config.master_clock = 0;

    // 2. СНАЧАЛА ЗАГРУЖАЕМ ROM (Ядро определяет тип консоли и размечает ROM-банки)
    if (!load_rom((char*)rom_path.c_str())) {
        LOGE("Genesis Plus GX failed to load ROM file.");
        return false;
    }

    // 3. ТЕПЕРЬ инициализируем аудио и аппаратную часть под конкретную архитектуру игры
    audio_init(44100, 60.0);
    system_init();

    // 4. Безопасно сбрасываем процессоры эмулятора
    system_reset();

    // 5. Подготовка локального звукового и графического окружения Android
    initOpenSLES();
    std::memset(local_framebuffer, 0, sizeof(local_framebuffer));

    initialized = true;
    LOGD("Genesis core engine started successfully.");
    return true;
}

void GenesisCore::runFrame() {
    if (!initialized) return;

    system_frame_gen(0);

    int16_t audio_samples[2048];
    int samples_emulated = audio_update(audio_samples);
    if (samples_emulated > 0 && player_buffer_queue) {
        (*player_buffer_queue)->Enqueue(player_buffer_queue, audio_samples, samples_emulated * 2 * sizeof(int16_t));
    }
}

void GenesisCore::initOpenGL() {
    if (program_id != 0) return; 

    const char* vertex_shader_src =
        "attribute vec4 vPosition;\n"
        "attribute vec2 vTexCoord;\n"
        "varying vec2 fTexCoord;\n"
        "void main() {\n"
        "  gl_Position = vPosition;\n"
        "  fTexCoord = vTexCoord;\n"
        "}\n";

    const char* fragment_shader_src =
        "precision mediump float;\n"
        "varying vec2 fTexCoord;\n"
        "uniform sampler2D uTexture;\n"
        "void main() {\n"
        "  gl_FragColor = texture2D(uTexture, fTexCoord);\n"
        "}\n";

    GLuint vs = compileShader(GL_VERTEX_SHADER, vertex_shader_src);
    GLuint fs = compileShader(GL_FRAGMENT_SHADER, fragment_shader_src);

    program_id = glCreateProgram();
    glAttachShader(program_id, vs);
    glAttachShader(program_id, fs);
    glLinkProgram(program_id);

    glDeleteShader(vs);
    glDeleteShader(fs);

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

    int width = bitmap.viewport.w;
    int height = bitmap.viewport.h;
    int pitch = bitmap.pitch; 

    int pitch_pixels = pitch / 2;
    uint16_t* src_data = (uint16_t*)bitmap.data + (bitmap.viewport.y * pitch_pixels) + bitmap.viewport.x;

    for (int y = 0; y < height; ++y) {
        std::memcpy(&local_framebuffer[y * width], &src_data[y * pitch_pixels], width * sizeof(uint16_t));
    }

    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    glUseProgram(program_id);

    glActiveTexture(GL_TEXTURE0);
    glBindTexture(GL_TEXTURE_2D, texture_id);
    
    static int last_texture_width = 0;
    static int last_texture_height = 0;

    if (width != last_texture_width || height != last_texture_height) {
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, local_framebuffer);
        last_texture_width = width;
        last_texture_height = height;
    } else {
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, local_framebuffer);
    }
    
    glUniform1i(glGetUniformLocation(program_id, "uTexture"), 0);

    glBindBuffer(GL_ARRAY_BUFFER, vbo_id);
    
    GLint pos_attrib = glGetAttribLocation(program_id, "vPosition");
    glEnableVertexAttribArray(pos_attrib);
    glVertexAttribPointer(pos_attrib, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void*)0);

    GLint tex_attrib = glGetAttribLocation(program_id, "vTexCoord");
    glEnableVertexAttribArray(tex_attrib);
    glVertexAttribPointer(tex_attrib, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), (void*)(2 * sizeof(float)));

    glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
}

void GenesisCore::setButton(int player, int button, bool pressed) {
    if (!initialized || player < 0 || player > 1) return;

    uint16_t sega_mask = 0;
    switch (button) {
        case 0: sega_mask = SEGA_UP;    break;
        case 1: sega_mask = SEGA_DOWN;  break;
        case 2: sega_mask = SEGA_LEFT;  break;
        case 3: sega_mask = SEGA_RIGHT; break;
        case 4: sega_mask = SEGA_A;     break;
        case 5: sega_mask = SEGA_B;     break;
        case 6: sega_mask = SEGA_C;     break;
        case 7: sega_mask = SEGA_START; break;
        default: return;
    }

    if (pressed) {
        input.pad[player] |= sega_mask;
    } else {
        input.pad[player] &= ~sega_mask;
    }
}

bool GenesisCore::saveState(int slot) {
    if (!initialized) return false;
    char filepath[512];
    std::snprintf(filepath, sizeof(filepath), "%s/save_%d.state", save_path.c_str(), slot);

    FILE* f = std::fopen(filepath, "wb");
    if (!f) return false;

    unsigned char state_buffer[1024 * 512]; 
    int state_size = state_save(state_buffer);
    if (state_size > 0) {
        std::fwrite(state_buffer, 1, state_size, f);
    }
    std::fclose(f);
    return state_size > 0;
}

bool GenesisCore::loadState(int slot) {
    if (!initialized) return false;
    char filepath[512];
    std::snprintf(filepath, sizeof(filepath), "%s/save_%d.state", save_path.c_str(), slot);

    FILE* f = std::fopen(filepath, "rb");
    if (!f) return false;

    std::fseek(f, 0, SEEK_END);
    long size = std::ftell(f);
    std::fseek(f, 0, SEEK_SET);

    std::vector<unsigned char> state_buffer(size);
    size_t read_bytes = std::fread(state_buffer.data(), 1, size, f);
    std::fclose(f);

    if (read_bytes > 0) {
        return state_load(state_buffer.data()) > 0;
    }
    return false;
}

void GenesisCore::initOpenSLES() {
    slCreateEngine(&engine_object, 0, nullptr, 0, nullptr, nullptr);
    (*engine_object)->Realize(engine_object, SL_BOOLEAN_FALSE);
    (*engine_object)->GetInterface(engine_object, SL_IID_ENGINE, &engine_interface);

    (*engine_interface)->CreateOutputMix(engine_interface, &output_mix_object, 0, nullptr, nullptr);
    (*output_mix_object)->Realize(output_mix_object, SL_BOOLEAN_FALSE);

    SLDataLocator_AndroidSimpleBufferQueue loc_bufq = { SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 2 };
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

    (*player_buffer_queue)->RegisterCallback(player_buffer_queue, bqPlayerCallback, this);
    (*player_play)->SetPlayState(player_play, SL_PLAYSTATE_PLAYING);

    int16_t silence[441 * 2] = {0};
    (*player_buffer_queue)->Enqueue(player_buffer_queue, silence, sizeof(silence));
}

void GenesisCore::shutdownOpenSLES() {
    if (player_play) {
        (*player_play)->SetPlayState(player_play, SL_PLAYSTATE_STOPPED);
    }
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
