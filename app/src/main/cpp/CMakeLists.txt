cmake_minimum_required(VERSION 3.22.1)
project("dune2emu")

# Enable C++17
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Collect Genesis Plus GX source files
file(GLOB_RECURSE GENESIS_SOURCES
    genesis-plus-gx/core/*.c
    genesis-plus-gx/core/m68k/*.c
    genesis-plus-gx/core/z80/*.c
    genesis-plus-gx/core/sound/*.c
    genesis-plus-gx/core/cart_hw/*.c
    genesis-plus-gx/core/input_hw/*.c
    genesis-plus-gx/core/cd_hw/*.c
    genesis-plus-gx/core/ntsc/*.c
    genesis-plus-gx/core/tremor/*.c
)

# Our wrapper sources
set(EMULATOR_SOURCES
    emulator-bridge.cpp
    genesis-core.cpp
)

# Check if sources exist
if(NOT GENESIS_SOURCES)
    message(WARNING "Genesis Plus GX sources not found. Creating minimal implementation.")
    # Create minimal implementation if Genesis Plus GX not found
    set(GENESIS_SOURCES dummy_genesis.c)
endif()

# Create shared library
add_library(dune2emu SHARED
    ${GENESIS_SOURCES}
    ${EMULATOR_SOURCES}
)

# Include directories
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
)

# Compile definitions
target_compile_definitions(dune2emu PRIVATE
    ANDROID
    __LIBRETRO__
    LSB_FIRST
    ALIGN_LONG
    HAVE_INTTYPES
)

# Compile options
target_compile_options(dune2emu PRIVATE
    -O3
    -funroll-loops
    -fomit-frame-pointer
)

# Link libraries
target_link_libraries(dune2emu
    android
    GLESv2
    EGL
    log
    OpenSLES
)
