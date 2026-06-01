#pragma once
#include <string>
#include <cstdint>
#include <vector>

class GenesisCore {
private:
    uint8_t* rom_data = nullptr;
    size_t rom_size = 0;
    uint8_t* vram = nullptr;
    
    // CPU state
    struct CPUState {
        uint32_t pc;
        uint32_t regs[16];
        uint8_t flags;
    } m68k_state;
    
    // VDP state
    struct VDPState {
        uint8_t regs[24];
        uint16_t vram[32768];
        uint16_t cram[64];
        uint16_t vsram[40];
    } vdp_state;
    
    // Audio buffers
    std::vector<int16_t> audio_buffer;
    
    // Input state
    uint16_t input_state[2];
    
    // Frame buffer
    uint32_t framebuffer[320 * 240];
    
    bool initialized = false;
    std::string save_path;
    
public:
    GenesisCore() = default;
    ~GenesisCore();
    
    bool init(const std::string& rom_path, const std::string& save_dir);
    void runFrame();
    void render();
    void setButton(int player, int button, bool pressed);
    bool saveState(int slot);
    bool loadState(int slot);
    
private:
    void emulate_m68k();
    void emulate_z80();
    void emulate_vdp();
    void emulate_audio();
    void update_input();
};
