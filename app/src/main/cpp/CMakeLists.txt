cmake_minimum_required(VERSION 3.22.1)

project("edgevision")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Compiler flags for optimization
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -ffast-math")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fvisibility=hidden")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra")

# Enable NEON for ARM
if(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfpu=neon")
elseif(ANDROID_ABI STREQUAL "arm64-v8a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8-a")
endif()

# Set OpenCV directory using PROJECT_SOURCE_DIR (points to app/src/main/cpp)
# Go up to project root then into OpenCV
get_filename_component(PROJECT_ROOT "${CMAKE_SOURCE_DIR}/../../../.." ABSOLUTE)
set(OpenCV_DIR ${PROJECT_ROOT}/OpenCV/native/jni)

# Log OpenCV paths for debugging
message(STATUS "Project Root: ${PROJECT_ROOT}")
message(STATUS "OpenCV Directory: ${OpenCV_DIR}")
message(STATUS "Android ABI: ${ANDROID_ABI}")

# Add OpenCV includes
include_directories(${OpenCV_DIR}/include)

# Add OpenCV as prebuilt library
add_library(lib_opencv SHARED IMPORTED)
set_target_properties(lib_opencv PROPERTIES IMPORTED_LOCATION
    ${OpenCV_DIR}/../libs/${ANDROID_ABI}/libopencv_java4.so)

# Verify OpenCV library exists
if(NOT EXISTS ${OpenCV_DIR}/../libs/${ANDROID_ABI}/libopencv_java4.so)
    message(FATAL_ERROR "OpenCV library not found at: ${OpenCV_DIR}/../libs/${ANDROID_ABI}/libopencv_java4.so")
endif()

# Add native library sources
add_library(
    edgevision
    SHARED
    native-lib.cpp
    edge_processor.cpp
)

# Set library properties
set_target_properties(edgevision PROPERTIES
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)

# Link libraries
target_link_libraries(
    edgevision
    lib_opencv
    android
    log
    GLESv2
    EGL
    jnigraphics  # For Bitmap support
)

# Strip symbols in release builds
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    add_custom_command(TARGET edgevision POST_BUILD
        COMMAND ${CMAKE_STRIP} --strip-unneeded $<TARGET_FILE:edgevision>
    )
endif()
