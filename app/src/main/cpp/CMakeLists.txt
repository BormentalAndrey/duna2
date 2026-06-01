cmake_minimum_required(VERSION 3.22.1)
project("dune2emu")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Core Genesis Plus GX sources (only what's needed)
# Используем GLOB для автоматического поиска файлов
file(GLOB GENESIS_SYSTEM_SOURCES
    genesis-plus-gx/core/system.c
    genesis-plus-gx/core/loadrom.c
    genesis-plus-gx/core/membnk.c
    genesis-plus-gx/core/genesis.c
    genesis-plus-gx/core/md_ntsc.c
    genesis-plus-gx/core/md_pal.c
)

file(GLOB GENESIS_M68K_SOURCES
    genesis-plus-gx/core/m68k/m68kcpu.c
    genesis-plus-gx/core/m68k/m68kops.c
)

file(GLOB GENESIS_Z80_SOURCES
    genesis-plus-gx/core/z80/z80.c
)

file(GLOB GENESIS_VDP_SOURCES
    genesis-plus-gx/core/vdp_render.c
    genesis-plus-gx/core/vdp_ctrl.c
    genesis-plus-gx/core/render.c
)

file(GLOB GENESIS_SOUND_SOURCES
    genesis-plus-gx/core/sound/ym2612.c
    genesis-plus-gx/core/sound/sn76489.c
    genesis-plus-gx/core/sound/sound.c
    genesis-plus-gx/core/sound/eq.c
)

file(GLOB GENESIS_INPUT_SOURCES
    genesis-plus-gx/core/input_hw/input.c
    genesis-plus-gx/core/input_hw/gamepad.c
    genesis-plus-gx/core/input_hw/mouse.c
    genesis-plus-gx/core/input_hw/activator.c
    genesis-plus-gx/core/input_hw/xe_a1p.c
    genesis-plus-gx/core/input_hw/teamplayer.c
    genesis-plus-gx/core/input_hw/wayplay.c
    genesis-plus-gx/core/input_hw/paddle.c
    genesis-plus-gx/core/input_hw/sportspad.c
    genesis-plus-gx/core/input_hw/terebi_oekaki.c
    genesis-plus-gx/core/input_hw/graph_board.c
    genesis-plus-gx/core/io_ctrl.c
    genesis-plus-gx/core/io_joypad.c
)

file(GLOB GENESIS_CART_SOURCES
    genesis-plus-gx/core/cart_hw/sram.c
    genesis-plus-gx/core/cart_hw/md_cart.c
    genesis-plus-gx/core/cart_hw/svp.c
    genesis-plus-gx/core/cart_hw/areplay.c
    genesis-plus-gx/core/cart_hw/ggenie.c
    genesis-plus-gx/core/cart_hw/eeprom_93c.c
    genesis-plus-gx/core/cart_hw/eeprom_i2c.c
    genesis-plus-gx/core/cart_hw/eeprom_spi.c
    genesis-plus-gx/core/cart_hw/flash_cfi.c
)

file(GLOB GENESIS_STATE_SOURCES
    genesis-plus-gx/core/state.c
)

file(GLOB GENESIS_SMS_SOURCES
    genesis-plus-gx/core/sms.c
)

file(GLOB GENESIS_CD_SOURCES
    genesis-plus-gx/core/cd_hw/cd_cart.c
    genesis-plus-gx/core/cd_hw/cdc.c
    genesis-plus-gx/core/cd_hw/cdd.c
    genesis-plus-gx/core/cd_hw/gfx.c
    genesis-plus-gx/core/cd_hw/lc89510.c
    genesis-plus-gx/core/cd_hw/pcm.c
    genesis-plus-gx/core/cd_hw/scd.c
)

# Our bridge
set(EMULATOR_SOURCES
    emulator-bridge.cpp
    genesis-core.cpp
)

# Build library
add_library(dune2emu SHARED
    ${GENESIS_SYSTEM_SOURCES}
    ${GENESIS_M68K_SOURCES}
    ${GENESIS_Z80_SOURCES}
    ${GENESIS_VDP_SOURCES}
    ${GENESIS_SOUND_SOURCES}
    ${GENESIS_INPUT_SOURCES}
    ${GENESIS_CART_SOURCES}
    ${GENESIS_STATE_SOURCES}
    ${GENESIS_SMS_SOURCES}
    ${GENESIS_CD_SOURCES}
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
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/ntsc
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/tremor
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
    USE_LIBRETRO
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
    -Wno-unused-function
    -Wno-unused-variable
    -Wno-unused-parameter
    -Wno-implicit-function-declaration
    -Wno-incompatible-pointer-types
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
