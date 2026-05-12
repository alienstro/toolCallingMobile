cmake_minimum_required(VERSION 3.22.1)

project(native_llamacpp_android LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(LLAMA_CPP_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp")

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BLAS OFF CACHE BOOL "" FORCE)
set(GGML_CUDA OFF CACHE BOOL "" FORCE)
set(GGML_METAL OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)

add_subdirectory("${LLAMA_CPP_DIR}" llama-cpp-build)

if(TARGET llama-common)
    set(LLAMA_COMMON_TARGET llama-common)
elseif(TARGET common)
    set(LLAMA_COMMON_TARGET common)
else()
    message(FATAL_ERROR "llama.cpp common target was not found")
endif()

add_library(native-llamacpp SHARED native_llama.cpp)

target_include_directories(native-llamacpp PRIVATE
    "${LLAMA_CPP_DIR}/include"
    "${LLAMA_CPP_DIR}/common"
    "${LLAMA_CPP_DIR}/ggml/include"
)

target_link_libraries(native-llamacpp
    android
    log
    llama
    ${LLAMA_COMMON_TARGET}
    ggml
)
