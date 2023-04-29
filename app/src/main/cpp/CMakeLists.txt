cmake_minimum_required(VERSION 3.4.1)

add_library(
        decoder_texture
        SHARED
        decoder_texture.cpp)

find_library(
        decoder_texture
        log)

add_library(
        rg_etc1
        SHARED
        rg_etc1.cpp)

find_library(
        rg_etc1
        log)

target_link_libraries(
        decoder_texture
        rg_etc1
        ${decoder_texture})