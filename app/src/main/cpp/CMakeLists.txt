cmake_minimum_required(VERSION 3.22.1)
project("dune2emu")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Core Genesis Plus GX sources (only what's needed)
set(GENESIS_CORE_SOURCES
    # System core
    genesis-plus-gx/core/system.c
    genesis-plus-gx/core/loadrom.c
    genesis-plus-gx/core/unzip.c
    genesis-plus-gx/core/membnk.c
    
    # M68K CPU core
    genesis-plus-gx/core/m68k/m68kcpu.c
    genesis-plus-gx/core/m68k/m68kops.c
    
    # Z80 CPU core
    genesis-plus-gx/core/z80/z80.c
    
    # VDP
    genesis-plus-gx/core/vdp_render.c
    genesis-plus-gx/core/vdp_ctrl.c
    genesis-plus-gx/core/render.c
    
    # Sound
    genesis-plus-gx/core/sound/ym2612.c
    genesis-plus-gx/core/sound/sn76489.c
    genesis-plus-gx/core/sound/sound.c
    
    # Input
    genesis-plus-gx/core/input_hw/input.c
    genesis-plus-gx/core/io_ctrl.c
    genesis-plus-gx/core/io_joypad.c
    
    # Cartridge/Save
    genesis-plus-gx/core/cart_hw/sram.c
    genesis-plus-gx/core/cart_hw/md_cart.c
    
    # State management
    genesis-plus-gx/core/state.c
    
    # SMS support (Genesis needs this)
    genesis-plus-gx/core/sms.c
)

# Our bridge
set(EMULATOR_SOURCES
    emulator-bridge.cpp
    genesis-core.cpp
)

# Build library
add_library(dune2emu SHARED
    ${GENESIS_CORE_SOURCES}
    ${EMULATOR_SOURCES}
)

# Include paths
target_include_directories(dune2emu PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/m68k
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/z80
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/sound
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/cart_hw
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/input_hw
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/cd_hw
)

# Defines for Genesis Plus GX
target_compile_definitions(dune2emu PRIVATE
    ANDROID
    __LIBRETRO__
    LSB_FIRST
    ALIGN_LONG
    HAVE_INTTYPES
    USE_16BPP_RENDERING
    ALIGN_SND
    HAVE_ALLOCA_H
    HAVE_MKSTEMP
)

# Compiler flags
target_compile_options(dune2emu PRIVATE
    -O3
    -funroll-loops
    -fomit-frame-pointer
    -ffast-math
    -fno-strict-aliasing
    -Wno-unused
    -Wno-parentheses
    -Wno-missing-braces
)

# Link libraries
target_link_libraries(dune2emu
    android
    GLESv2
    EGL
    log
    OpenSLES
    z
)
