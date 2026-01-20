cmake_minimum_required(VERSION 3.10.2)
project(tinker.test)

option(UPDATED "Is updated example type." OFF)

if (UPDATED)
    add_definitions(-D__example_updated__)
endif ()

add_compile_options(-Oz)
add_link_options(-Wl,-z,max-page-size=16384)

add_library(example.jni SHARED jni.c)
add_library(example.dep SHARED dep.c)
target_link_libraries(example.jni example.dep)