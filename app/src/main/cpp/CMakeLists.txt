cmake_minimum_required(VERSION 3.22.1)
project("dune2emu")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Автоматически найти все .c файлы
file(GLOB_RECURSE GENESIS_SOURCES 
    genesis-plus-gx/core/*.c
    genesis-plus-gx/core/m68k/*.c
    genesis-plus-gx/core/z80/*.c
    genesis-plus-gx/core/sound/*.c
    genesis-plus-gx/core/cart_hw/*.c
    genesis-plus-gx/core/input_hw/*.c
    genesis-plus-gx/core/cd_hw/*.c
)

# Удалить проблемные файлы
list(FILTER GENESIS_SOURCES EXCLUDE REGEX ".*/cd_hw/.*")
list(FILTER GENESIS_SOURCES EXCLUDE REGEX ".*/(areplay|eeprom_93c|eeprom_i2c|eeprom_spi|flash_cfi|ggenie|svp)\\.c$")

# Наши файлы
set(EMULATOR_SOURCES
    emulator-bridge.cpp
    genesis-core.cpp
)

# Все исходники
set(ALL_SOURCES ${GENESIS_SOURCES} ${EMULATOR_SOURCES})

# Создаем библиотеку
add_library(dune2emu SHARED ${ALL_SOURCES})

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
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/cd_hw
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/ntsc
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/tremor
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro
)

# Defines
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

# Компиляция
target_compile_options(dune2emu PRIVATE
    -O3
    -funroll-loops
    -fomit-frame-pointer
    -ffast-math
    -fno-strict-aliasing
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
