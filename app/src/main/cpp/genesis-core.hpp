#ifndef GENESIS_CORE_HPP
#define GENESIS_CORE_HPP

#include <string>
#include <vector>
#include <cstdint>
#include <GLES3/gl3.h>
#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

class GenesisCore {
public:
    GenesisCore() = default;
    ~GenesisCore();

    bool init(const std::string& rom_path, const std::string& save_dir);
    void runFrame();
    void render();
    void setButton(int player, int button, bool pressed);
    bool saveState(int slot);
    bool loadState(int slot);

    // Публичные для доступа из C коллбэков
    static GenesisCore* s_instance;
    std::string save_path;

private:
    bool initialized = false;

    // Видео-буфер и OpenGL ресурсы
    uint16_t local_framebuffer[320 * 240]; 
    GLuint program_id = 0;
    GLuint texture_id = 0;
    GLuint vbo_id = 0;
    
    // OpenSLES Аудио-ресурсы
    SLObjectItf engine_object = nullptr;
    SLEngineItf engine_interface = nullptr;
    SLObjectItf output_mix_object = nullptr;
    SLObjectItf player_object = nullptr;
    SLPlayItf player_play = nullptr;
    SLAndroidSimpleBufferQueueItf player_buffer_queue = nullptr;

    // Внутренние утилиты
    void initOpenGL();
    void initOpenSLES();
    void shutdownOpenSLES();
    GLuint compileShader(GLenum type, const char* source);
};

#endif // GENESIS_CORE_HPP
