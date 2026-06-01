cmake_minimum_required(VERSION 3.22.1)
project("dune2emu")
set(CMAKE_CXX_STANDARD 17)

# Найти ВСЕ .c файлы во ВСЕХ папках рекурсивно
file(GLOB_RECURSE ALL_C_FILES
    genesis-plus-gx/core/*.c
    genesis-plus-gx/core/**/*.c
    genesis-plus-gx/libretro/*.c
    genesis-plus-gx/libretro/**/*.c
    genesis-plus-gx/sdl/*.c
    genesis-plus-gx/gx/*.c
    genesis-plus-gx/gcw0/*.c
    genesis-plus-gx/psp2/*.c
)

# Наши файлы
set(OUR_SRC emulator-bridge.cpp genesis-core.cpp)

# Собрать
add_library(dune2emu SHARED ${ALL_C_FILES} ${OUR_SRC})

# Автоматически найти ВСЕ папки с .h файлами
file(GLOB_RECURSE ALL_H_FILES
    genesis-plus-gx/core/**/*.h
    genesis-plus-gx/libretro/**/*.h
    genesis-plus-gx/sdl/**/*.h
    genesis-plus-gx/gx/**/*.h
    genesis-plus-gx/gcw0/**/*.h
    genesis-plus-gx/psp2/**/*.h
)

set(INCLUDE_DIRS "")
foreach(H_FILE ${ALL_H_FILES})
    get_filename_component(H_DIR ${H_FILE} DIRECTORY)
    list(APPEND INCLUDE_DIRS ${H_DIR})
endforeach()
list(REMOVE_DUPLICATES INCLUDE_DIRS)

# Базовые пути
list(APPEND INCLUDE_DIRS
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/genesis-plus-gx
)

target_include_directories(dune2emu PRIVATE ${INCLUDE_DIRS})

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

target_compile_options(dune2emu PRIVATE -O3 -fomit-frame-pointer -w)
target_link_libraries(dune2emu android GLESv2 EGL log OpenSLES z)
