cmake_minimum_required(VERSION 3.22.1)
project(fogspike LANGUAGES CXX)

add_library(fogspike SHARED fog_layer.cpp)
target_compile_options(fogspike PRIVATE -Wall -Wextra -O2)
# Only GLES 3.0 is needed: instancing, gl_VertexID, GL_MAX blending and R8 render targets are
# core there. No link against MapLibre: the layer talks to it only through the host vtable.
target_link_libraries(fogspike GLESv3 EGL log)
