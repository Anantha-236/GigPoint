cmake_minimum_required(VERSION 3.10)

project(dhwani_whisper C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# app/src/main/cpp/whisper -> project root = ../../../../../
set(
    WHISPER_LIB_DIR
    ${CMAKE_CURRENT_LIST_DIR}/../../../../../third_party/whisper.cpp
)

if (NOT EXISTS "${WHISPER_LIB_DIR}/CMakeLists.txt")
    message(
        FATAL_ERROR
        "whisper.cpp not found at ${WHISPER_LIB_DIR}. Run scripts/add-whisper-cpp.ps1 first."
    )
endif()

# ---------------------------------------------------------------------------
# whisper.cpp expects WHISPER_VERSION to be supplied by the build system.
# We compile src/whisper.cpp directly, so we must define it ourselves.
#
# Current whisper.cpp keeps the version as:
#   set(WHISPER_VERSION_MAJOR ...)
#   set(WHISPER_VERSION_MINOR ...)
#   set(WHISPER_VERSION_PATCH ...)
# ---------------------------------------------------------------------------

file(
    READ
    "${WHISPER_LIB_DIR}/CMakeLists.txt"
    WHISPER_MAIN_CMAKE
)

string(
    REGEX MATCH
    "set\\(WHISPER_VERSION_MAJOR[ \t]+([0-9]+)\\)"
    _
    "${WHISPER_MAIN_CMAKE}"
)
set(WHISPER_VERSION_MAJOR "${CMAKE_MATCH_1}")

string(
    REGEX MATCH
    "set\\(WHISPER_VERSION_MINOR[ \t]+([0-9]+)\\)"
    _
    "${WHISPER_MAIN_CMAKE}"
)
set(WHISPER_VERSION_MINOR "${CMAKE_MATCH_1}")

string(
    REGEX MATCH
    "set\\(WHISPER_VERSION_PATCH[ \t]+([0-9]+)\\)"
    _
    "${WHISPER_MAIN_CMAKE}"
)
set(WHISPER_VERSION_PATCH "${CMAKE_MATCH_1}")

if (
    WHISPER_VERSION_MAJOR STREQUAL ""
    OR WHISPER_VERSION_MINOR STREQUAL ""
    OR WHISPER_VERSION_PATCH STREQUAL ""
)
    set(WHISPER_VERSION "unknown")
else()
    set(
        WHISPER_VERSION
        "${WHISPER_VERSION_MAJOR}.${WHISPER_VERSION_MINOR}.${WHISPER_VERSION_PATCH}"
    )
endif()

message(STATUS "DhwaniMitra whisper.cpp version: ${WHISPER_VERSION}")

set(
    SOURCE_FILES
    ${WHISPER_LIB_DIR}/src/whisper.cpp
    ${WHISPER_LIB_DIR}/src/aneforge/whisper-aneforge.cpp
    ${CMAKE_CURRENT_LIST_DIR}/jni.c
)

find_library(LOG_LIB log)

include(FetchContent)

add_library(
    dhwani_whisper
    SHARED
    ${SOURCE_FILES}
)

# Required by whisper.cpp when src/whisper.cpp is compiled directly.
target_compile_definitions(
    dhwani_whisper
    PUBLIC
    GGML_USE_CPU
    PRIVATE
    WHISPER_VERSION="${WHISPER_VERSION}"
)

if (NOT ${CMAKE_BUILD_TYPE} STREQUAL "Debug")
    target_compile_options(
        dhwani_whisper
        PRIVATE
        -O3
        -fvisibility=hidden
        -fvisibility-inlines-hidden
        -ffunction-sections
        -fdata-sections
    )

    target_link_options(
        dhwani_whisper
        PRIVATE
        -Wl,--gc-sections
        -Wl,--exclude-libs,ALL
        -flto
    )
endif()

FetchContent_Declare(
    ggml
    SOURCE_DIR
    ${WHISPER_LIB_DIR}/ggml
)

FetchContent_MakeAvailable(ggml)

target_link_libraries(
    dhwani_whisper
    ${LOG_LIB}
    android
    ggml
)

target_include_directories(
    dhwani_whisper
    PRIVATE
    ${WHISPER_LIB_DIR}
    ${WHISPER_LIB_DIR}/src
    ${WHISPER_LIB_DIR}/include
    ${WHISPER_LIB_DIR}/ggml/include
    ${WHISPER_LIB_DIR}/ggml/src
    ${WHISPER_LIB_DIR}/ggml/src/ggml-cpu
)
