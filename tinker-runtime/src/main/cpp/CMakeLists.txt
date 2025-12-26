cmake_minimum_required(VERSION 3.10.2)
project(tinker.test)

add_compile_options(-Oz)
add_link_options(-Wl,-z,max-page-size=16384)

add_library(tinker.test.jni SHARED test/jni.c)
add_library(tinker.test.dep SHARED test/dep.c)
target_link_libraries(tinker.test.jni tinker.test.dep)