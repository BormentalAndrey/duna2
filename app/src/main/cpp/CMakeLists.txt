cmake_minimum_required(VERSION 3.22.1)
project("dune2emu")
set(CMAKE_CXX_STANDARD 17)

# ТОЛЬКО нужные исходники, БЕЗ cd_hw
file(GLOB_RECURSE GENESIS_SRC
    genesis-plus-gx/core/system.c
    genesis-plus-gx/core/genesis.c
    genesis-plus-gx/core/loadrom.c
    genesis-plus-gx/core/membnk.c
    genesis-plus-gx/core/state.c
    genesis-plus-gx/core/sms.c
    genesis-plus-gx/core/vdp_render.c
    genesis-plus-gx/core/vdp_ctrl.c
    genesis-plus-gx/core/render.c
    genesis-plus-gx/core/io_ctrl.c
    genesis-plus-gx/core/io_joypad.c
    genesis-plus-gx/core/m68k/m68kcpu.c
    genesis-plus-gx/core/m68k/m68kops.c
    genesis-plus-gx/core/z80/z80.c
    genesis-plus-gx/core/sound/ym2612.c
    genesis-plus-gx/core/sound/ym3438.c
    genesis-plus-gx/core/sound/sn76489.c
    genesis-plus-gx/core/sound/sound.c
    genesis-plus-gx/core/sound/eq.c
    genesis-plus-gx/core/cart_hw/sram.c
    genesis-plus-gx/core/cart_hw/md_cart.c
    genesis-plus-gx/core/cart_hw/svp.c
    genesis-plus-gx/core/input_hw/input.c
    genesis-plus-gx/core/input_hw/gamepad.c
)

# Libretro-common (нужно для file_stream)
file(GLOB_RECURSE LIBRETRO_COMMON_SRC
    genesis-plus-gx/libretro/libretro-common/streams/file_stream.c
    genesis-plus-gx/libretro/libretro-common/string/string_list.c
    genesis-plus-gx/libretro/libretro-common/encodings/encoding_utf.c
    genesis-plus-gx/libretro/libretro-common/compat/compat_strl.c
    genesis-plus-gx/libretro/libretro-common/compat/compat_strcasestr.c
    genesis-plus-gx/libretro/libretro-common/file/file_path.c
    genesis-plus-gx/libretro/libretro-common/vfs/vfs_implementation.c
)

# Libretro core
file(GLOB LIBRETRO_CORE_SRC
    genesis-plus-gx/libretro/libretro.c
    genesis-plus-gx/libretro/scrc32.c
)

# Наши файлы
set(OUR_SRC emulator-bridge.cpp genesis-core.cpp)

# Собираем
add_library(dune2emu SHARED
    ${GENESIS_SRC}
    ${LIBRETRO_COMMON_SRC}
    ${LIBRETRO_CORE_SRC}
    ${OUR_SRC}
)

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

# Подавить все предупреждения
target_compile_options(dune2emu PRIVATE -O3 -fomit-frame-pointer -w)

# Линковка
target_link_libraries(dune2emu android GLESv2 EGL log OpenSLES z)
