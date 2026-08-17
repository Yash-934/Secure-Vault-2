cmake_minimum_required(VERSION 3.22.1)
project("native-lib")

# Fallback Clang flags as OLLVM is not natively available in this environment.
# These flags prevent inlining and unrolling to preserve opaque predicates and 
# manual control flow flattening implemented in the C++ source.
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fno-inline -fno-unroll-loops -fvisibility=hidden")

add_library(
        native-lib
        SHARED
        native-lib.cpp)

find_library(
        log-lib
        log)

target_link_libraries(
        native-lib
        ${log-lib})