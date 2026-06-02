cmake_minimum_required(VERSION 3.22.1)
project("dune2emu")
set(CMAKE_CXX_STANDARD 17)

# Найти файлы .c эмулятора во всех подпапках ядра и libretro
file(GLOB_RECURSE ALL_C_FILES
    genesis-plus-gx/core/*.c
    genesis-plus-gx/libretro/*.c
)

# ИСКЛЮЧАЕМ папки и файлы, которые не нужны или ломают сборку на Android
list(FILTER ALL_C_FILES EXCLUDE REGEX ".*/libretro/deps/.*")
list(FILTER ALL_C_FILES EXCLUDE REGEX ".*/async_stream\\.c$")
list(FILTER ALL_C_FILES EXCLUDE REGEX ".*/libretro-common/cdrom/.*")
list(FILTER ALL_C_FILES EXCLUDE REGEX ".*/vfs_implementation_cdrom\\.c$") # Исключаем физический CD-ROM VFS

# Наши файлы моста
set(OUR_SRC emulator-bridge.cpp genesis-core.cpp)

# Собрать общую библиотеку
add_library(dune2emu SHARED ${ALL_C_FILES} ${OUR_SRC})

# Include пути
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
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro/deps/libchdr/include
)

# Родные макроопределения эмулятора
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
)

# Флаги компиляции
target_compile_options(dune2emu PRIVATE 
    -O3 
    -fomit-frame-pointer 
    -w
    -Wno-error=implicit-function-declaration
    -include "${CMAKE_CURRENT_SOURCE_DIR}/flac_fixes.h"
)

# Линковка системных библиотек Android
target_link_libraries(dune2emu android GLESv2 EGL log OpenSLES z)
