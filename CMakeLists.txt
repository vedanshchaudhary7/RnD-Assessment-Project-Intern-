cmake_minimum_required(VERSION 3.10)

project(native-lib)

set(OpenCV_DIR /path/to/OpenCV-android-sdk/sdk/native/jni)
find_package(OpenCV REQUIRED)

add_library(native-lib SHARED native-lib.cpp)

include_directories(${OpenCV_DIR}/include)

target_link_libraries(native-lib ${OpenCV_LIBS} log GLESv2)