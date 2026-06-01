cmake_minimum_required(VERSION 3.22.1)
project("dune2emu")
set(CMAKE_CXX_STANDARD 17)

# Найти ВСЕ .c файлы
file(GLOB_RECURSE ALL_C_FILES
    genesis-plus-gx/core/*.c
    genesis-plus-gx/core/**/*.c
    genesis-plus-gx/libretro/*.c
    genesis-plus-gx/libretro/**/*.c
)

# Исключить проблемные файлы
list(FILTER ALL_C_FILES EXCLUDE REGEX ".*/cart_hw/(eeprom_93c|eeprom_i2c|eeprom_spi|flash_cfi|areplay|ggenie|megasd|yx5200|sms_cart)\\.c$")
list(FILTER ALL_C_FILES EXCLUDE REGEX ".*/cart_hw/svp/.*")
list(FILTER ALL_C_FILES EXCLUDE REGEX ".*/cd_hw/.*")
list(FILTER ALL_C_FILES EXCLUDE REGEX ".*/tremor/.*")
list(FILTER ALL_C_FILES EXCLUDE REGEX ".*/async_stream\\.c$")

# Наши файлы
set(OUR_SRC emulator-bridge.cpp genesis-core.cpp)

# Собрать
add_library(dune2emu SHARED ${ALL_C_FILES} ${OUR_SRC})

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
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro/libretro-common/include
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro/libretro-common/include/compat
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro/libretro-common/include/streams
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro/libretro-common/include/string
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro/libretro-common/include/encodings
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro/libretro-common/include/file
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro/libretro-common/include/vfs
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/libretro/deps/zlib-1.2.11
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx/sdl
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
)

target_compile_options(dune2emu PRIVATE -O3 -fomit-frame-pointer -w)
target_link_libraries(dune2emu android GLESv2 EGL log OpenSLES z)
