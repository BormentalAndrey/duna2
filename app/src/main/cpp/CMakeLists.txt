cmake_minimum_required(VERSION 3.22.1)
project("dune2emu")
set(CMAKE_CXX_STANDARD 17)

# Все исходники Genesis Plus GX
file(GLOB_RECURSE GENESIS_SRC
    genesis-plus-gx/core/*.c
    genesis-plus-gx/core/m68k/*.c
    genesis-plus-gx/core/z80/*.c
    genesis-plus-gx/core/sound/*.c
    genesis-plus-gx/core/cart_hw/*.c
    genesis-plus-gx/core/input_hw/*.c
)

# Libretro-common
file(GLOB_RECURSE LIBRETRO_COMMON_SRC
    genesis-plus-gx/libretro/libretro-common/streams/*.c
    genesis-plus-gx/libretro/libretro-common/string/*.c
    genesis-plus-gx/libretro/libretro-common/encodings/*.c
    genesis-plus-gx/libretro/libretro-common/compat/*.c
    genesis-plus-gx/libretro/libretro-common/file/*.c
    genesis-plus-gx/libretro/libretro-common/vfs/*.c
)

# Libretro core
file(GLOB LIBRETRO_CORE_SRC
    genesis-plus-gx/libretro/libretro.c
    genesis-plus-gx/libretro/scrc32.c
)

# Наши файлы
set(OUR_SRC emulator-bridge.cpp genesis-core.cpp)

# Исключаем проблемные файлы
list(FILTER GENESIS_SRC EXCLUDE REGEX ".*/cart_hw/(eeprom_93c|eeprom_i2c|eeprom_spi|flash_cfi|areplay|ggenie|megasd|yx5200|sms_cart)\\.c$")
list(FILTER GENESIS_SRC EXCLUDE REGEX ".*/cart_hw/svp/.*")
list(FILTER LIBRETRO_COMMON_SRC EXCLUDE REGEX ".*/async_stream\\.c$")

# Собираем библиотеку
add_library(dune2emu SHARED
    ${GENESIS_SRC}
    ${LIBRETRO_COMMON_SRC}
    ${LIBRETRO_CORE_SRC}
    ${OUR_SRC}
)

# Include пути (все где есть нужные .h файлы)
target_include_directories(dune2emu PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/m68k
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/z80
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/sound
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/cart_hw
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/core/input_hw
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro/libretro-common/include
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro/libretro-common/include/compat
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro/libretro-common/include/streams
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro/libretro-common/include/string
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro/libretro-common/include/encodings
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro/libretro-common/include/file
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro/libretro-common/include/vfs
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/sdl
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro/deps/zlib-1.2.11
)

# Defines
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
    HAVE_ZLIB
)

# Компиляция
target_compile_options(dune2emu PRIVATE -O3 -fomit-frame-pointer -w)

# Линковка
target_link_libraries(dune2emu android GLESv2 EGL log OpenSLES z)
