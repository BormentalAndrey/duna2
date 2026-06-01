cmake_minimum_required(VERSION 3.22.1)
project("dune2emu")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Все существующие .c файлы в core/
file(GLOB_RECURSE GENESIS_SOURCES
    genesis-plus-gx/core/*.c
    genesis-plus-gx/core/m68k/*.c
    genesis-plus-gx/core/z80/*.c
    genesis-plus-gx/core/sound/*.c
    genesis-plus-gx/core/cart_hw/*.c
    genesis-plus-gx/core/input_hw/*.c
)

# Наши файлы
set(EMULATOR_SOURCES
    emulator-bridge.cpp
    genesis-core.cpp
)

add_library(dune2emu SHARED ${GENESIS_SOURCES} ${EMULATOR_SOURCES})

# Include пути
target_include_directories(dune2emu PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/m68k
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/z80
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/sound
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/cart_hw
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/input_hw
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/gx
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro/libretro-common/include
)

# Defines
target_compile_definitions(dune2emu PRIVATE
    ANDROID
    __LIBRETRO__
    LSB_FIRST
    ALIGN_LONG
    HAVE_INTTYPES
    USE_16BPP_RENDERING
)

# Компиляция
target_compile_options(dune2emu PRIVATE
    -O3
    -fomit-frame-pointer
    -w
)

# Линковка
target_link_libraries(dune2emu
    android
    GLESv2
    EGL
    log
    OpenSLES
    z
)
