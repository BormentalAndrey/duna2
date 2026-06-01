cmake_minimum_required(VERSION 3.22.1)
project("dune2emu")
set(CMAKE_CXX_STANDARD 17)

# Найти абсолютно ВСЕ файлы .c эмулятора
file(GLOB_RECURSE ALL_C_FILES
    genesis-plus-gx/core/*.c
    genesis-plus-gx/libretro/*.c
)

list(FILTER ALL_C_FILES EXCLUDE REGEX ".*/async_stream\\.c$")

set(OUR_SRC emulator-bridge.cpp genesis-core.cpp)

add_library(dune2emu SHARED ${ALL_C_FILES} ${OUR_SRC})

# Добавлен путь к include для lzma-19.00
target_include_directories(dune2emu PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/m68k
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/z80
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/sound
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/sound/minimp3
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/cart_hw
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/cart_hw/svp
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/cd_hw
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/cd_hw/libchdr/include
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/cd_hw/libchdr/deps
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/cd_hw/libchdr/deps/zlib
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/cd_hw/libchdr/deps/libFLAC/include
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/cd_hw/libchdr/deps/lzma
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/input_hw
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/ntsc
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/tremor
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro/libretro-common/include
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro/deps/zlib-1.2.11
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro/deps/libchdr/include
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro/deps/zstd/lib
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro/deps/lzma-19.00/include
)

target_compile_definitions(dune2emu PRIVATE
    ANDROID
    __LIBRETRO__
    LSB_FIRST
    ALIGN_LONG
    HAVE_INTTYPES
    HAVE_STDINT
    USE_16BPP_RENDERING
    RARCH_INTERNAL
    HAVE_STREAMS
    HAVE_CHD
    HAVE_ZLIB
    HAVE_ZSTD
)

target_compile_options(dune2emu PRIVATE 
    -O3 
    -fomit-frame-pointer 
    -w
    -Wno-error=implicit-function-declaration
    -include "${CMAKE_CURRENT_SOURCE_DIR}/flac_fixes.h"
)

target_link_libraries(dune2emu android GLESv2 EGL log OpenSLES z)
