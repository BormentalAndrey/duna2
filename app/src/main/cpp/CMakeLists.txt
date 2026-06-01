cmake_minimum_required(VERSION 3.22.1)
project("dune2emu")

# Genesis Plus GX source files
set(GENESIS_SRC
    genesis-plus-gx/core/io_ctrl.c
    genesis-plus-gx/core/io_joypad.c
    genesis-plus-gx/core/cart_hw/sram.c
    genesis-plus-gx/core/cart_hw/eeprom.c
    genesis-plus-gx/core/cart_hw/md_cart.c
    genesis-plus-gx/core/cd_hw/cd_cart.c
    genesis-plus-gx/core/state.c
    genesis-plus-gx/core/m68k/m68kcpu.c
    genesis-plus-gx/core/z80/z80.c
    genesis-plus-gx/core/sound/sn76489.c
    genesis-plus-gx/core/sound/ym2612.c
    genesis-plus-gx/core/vdp_render.c
    genesis-plus-gx/core/system.c
    genesis-plus-gx/core/loadrom.c
    genesis-plus-gx/core/unzip.c
    genesis-plus-gx/core/input_hw/input.c
    genesis-plus-gx/core/sms.c
    genesis-plus-gx/core/genesis.c
)

# Libretro interface
set(LIBRETRO_SRC
    libretro-interface.cpp
    emulator-bridge.cpp
)

add_library(dune2emu SHARED
    ${GENESIS_SRC}
    ${LIBRETRO_SRC}
)

target_include_directories(dune2emu PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/m68k
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/z80
)

target_compile_definitions(dune2emu PRIVATE
    USE_16BPP_RENDERING=1
    HAVE_INTTYPES=1
    LSB_FIRST=1
    ALIGN_LONG=1
    ANDROID=1
)

target_link_libraries(dune2emu
    android
    GLESv2
    EGL
    log
    OpenSLES
)
