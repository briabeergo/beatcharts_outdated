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

add_library(
        ww2ogg
        SHARED
        ww2ogg.cpp
        wwriff.cpp
        codebook.cpp
        errors.h
        Bit_stream.h
        crc.c)

find_library(
        log-ww2ogg
        log)

target_link_libraries(
        ww2ogg
        ${log-ww2ogg}
)