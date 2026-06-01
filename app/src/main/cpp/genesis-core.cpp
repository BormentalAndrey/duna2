#include "genesis-core.hpp"
#include <cstring>
#include <cstdio>
#include <android/log.h>

#define TAG "GenesisPlus"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

GenesisCore::~GenesisCore() {
    if (rom_data) {
        delete[] rom_data;
        rom_data = nullptr;
    }
    if (vram) {
        delete[] vram;
        vram = nullptr;
    }
}

bool GenesisCore::init(const std::string& rom_path, const std::string& save_dir) {
    LOGD("Loading ROM from: %s", rom_path.c_str());
    
    // Загрузка ROM
    FILE* file = fopen(rom_path.c_str(), "rb");
    if (!file) {
        LOGD("Failed to open ROM file");
        return false;
    }
    
    fseek(file, 0, SEEK_END);
    rom_size = ftell(file);
    fseek(file, 0, SEEK_SET);
    
    rom_data = new uint8_t[rom_size];
    fread(rom_data, 1, rom_size, file);
    fclose(file);
    
    LOGD("ROM loaded: %zu bytes", rom_size);
    
    // Инициализация VRAM
    vram = new uint8_t[0x10000]();
    
    // Инициализация CPU
    memset(&m68k_state, 0, sizeof(m68k_state));
    m68k_state.pc = 0x200;
    
    // Инициализация VDP
    memset(&vdp_state, 0, sizeof(vdp_state));
    
    // Настройка путей сохранений
    save_path = save_dir;
    
    initialized = true;
    LOGD("Genesis core initialized successfully");
    return true;
}

void GenesisCore::runFrame() {
    if (!initialized) return;
    
    // Эмуляция одного кадра (1/60 секунды для NTSC)
    const int cycles_per_frame = 488 * 60; // Приблизительно
    
    for (int i = 0; i < cycles_per_frame; i++) {
        emulate_m68k();
        emulate_z80();
        emulate_vdp();
    }
    
    emulate_audio();
    update_input();
}

void GenesisCore::emulate_m68k() {
    // Упрощенная эмуляция M68K
    if (m68k_state.pc < rom_size) {
        uint16_t opcode = (rom_data[m68k_state.pc] << 8) | rom_data[m68k_state.pc + 1];
        m68k_state.pc += 2;
        
        // Обработка базовых опкодов
        switch (opcode >> 12) {
            case 0x0: // MOVE
                if (m68k_state.pc + 2 < rom_size) {
                    uint16_t src = (rom_data[m68k_state.pc] << 8) | rom_data[m68k_state.pc + 1];
                    m68k_state.regs[(opcode >> 9) & 0x7] = src;
                    m68k_state.pc += 2;
                }
                break;
                
            case 0x6: // Bcc (условный переход)
                if ((m68k_state.flags & 0x4) == 0) { // Проверка флага Z
                    int8_t offset = opcode & 0xFF;
                    m68k_state.pc += offset;
                }
                break;
                
            case 0xE: // Разные операции
                if ((opcode & 0xF00) == 0xE00) { // ASR
                    m68k_state.regs[0] >>= 1;
                    if (m68k_state.regs[0] == 0) {
                        m68k_state.flags |= 0x4; // Установка Z флага
                    }
                }
                break;
        }
    }
}

void GenesisCore::emulate_z80() {
    // Заглушка для Z80 (используется для звука в Genesis)
}

void GenesisCore::emulate_vdp() {
    // Упрощенная эмуляция VDP
    static int scanline = 0;
    scanline = (scanline + 1) % 262;
    
    if (scanline < 224) { // Активная область экрана
        // Заполнение фреймбуфера тестовым паттерном для Dune 2
        for (int x = 0; x < 320; x++) {
            uint32_t color;
            
            // Генерация цвета на основе позиции и содержимого VRAM
            uint8_t tile = vram[(scanline / 8) * 40 + (x / 8)];
            
            if (tile > 0) {
                // Отображаем "песок" Dune 2 (оранжево-коричневые тона)
                color = 0xFFE0A060;
                
                // Если это здание или юнит
                if (tile > 100) {
                    color = 0xFF404040; // Серый для зданий
                }
            } else {
                // Фон пустыни
                color = 0xFFD2A060;
            }
            
            framebuffer[scanline * 320 + x] = color;
        }
    }
}

void GenesisCore::emulate_audio() {
    // Заглушка для аудио
    audio_buffer.resize(1024, 0);
}

void GenesisCore::update_input() {
    // Обработка ввода через input_state
}

void GenesisCore::render() {
    // Рендеринг через OpenGL ES
    glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);
    
    // Создание текстуры из фреймбуфера
    static GLuint texture = 0;
    if (texture == 0) {
        glGenTextures(1, &texture);
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    }
    
    glBindTexture(GL_TEXTURE_2D, texture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 320, 240, 0,
                 GL_RGBA, GL_UNSIGNED_BYTE, framebuffer);
    
    // Отображение текстуры
    // ... (код рендеринга fullscreen quad)
}

void GenesisCore::setButton(int player, int button, bool pressed) {
    if (pressed) {
        input_state[player] |= (1 << button);
    } else {
        input_state[player] &= ~(1 << button);
    }
}

bool GenesisCore::saveState(int slot) {
    char filename[256];
    snprintf(filename, sizeof(filename), "%s/save_%d.state", save_path.c_str(), slot);
    
    FILE* file = fopen(filename, "wb");
    if (!file) return false;
    
    // Сохранение состояния
    fwrite(&m68k_state, sizeof(m68k_state), 1, file);
    fwrite(&vdp_state, sizeof(vdp_state), 1, file);
    fwrite(vram, 0x10000, 1, file);
    
    fclose(file);
    return true;
}

bool GenesisCore::loadState(int slot) {
    char filename[256];
    snprintf(filename, sizeof(filename), "%s/save_%d.state", save_path.c_str(), slot);
    
    FILE* file = fopen(filename, "rb");
    if (!file) return false;
    
    // Загрузка состояния
    fread(&m68k_state, sizeof(m68k_state), 1, file);
    fread(&vdp_state, sizeof(vdp_state), 1, file);
    fread(vram, 0x10000, 1, file);
    
    fclose(file);
    return true;
}
