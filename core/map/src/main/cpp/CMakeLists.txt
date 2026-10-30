cmake_minimum_required(VERSION 3.22.1)
project(triptrackfog LANGUAGES CXX)

add_library(triptrackfog SHARED fog_layer.cpp fog_jni.cpp)
# The verbatim MapLibre headers (see core/map/maplibre-abi.properties).
target_include_directories(triptrackfog PRIVATE maplibre/include)
target_compile_options(triptrackfog PRIVATE -Wall -Wextra -O2)
# NDK 28 already aligns to 16 KB; spelled out so a toolchain change cannot drop it (Play rule for
# Android 15+).
target_link_options(triptrackfog PRIVATE -Wl,-z,max-page-size=16384)
# Only GLES 3.0 is needed: instancing, gl_VertexID, GL_MAX blending and R8 render targets are core
# there. No link against MapLibre: the layer talks to it only through the host vtable.
target_link_libraries(triptrackfog GLESv3 EGL log)
