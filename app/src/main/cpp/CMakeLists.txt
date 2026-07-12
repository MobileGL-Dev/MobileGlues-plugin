cmake_minimum_required(VERSION 3.10)
project(optimizedmg_info_getter)

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

add_library(optimizedmg_info_getter SHARED
    optimizedmg_info_getter.cpp
)

find_library(android-lib android)

target_compile_options(optimizedmg_info_getter PRIVATE
    -Oz
    -fdata-sections
    -ffunction-sections
    -fvisibility=hidden
    -fvisibility-inlines-hidden
    -fno-rtti
    -fno-exceptions
    -flto
)

target_link_options(optimizedmg_info_getter PRIVATE
    -Wl,--gc-sections
    -Wl,--strip-all
    -flto
)

target_include_directories(optimizedmg_info_getter PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(optimizedmg_info_getter
    ${android-lib}
    dl
)
