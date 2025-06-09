# RnD-Assessment-Project-Intern-
# EdgeDetectionViewer
This is an Android app that performs real-time edge detection on camera frames using OpenCV (C++) and renders the output using OpenGL ES 2.0. The app was developed as part of the Android + OpenCV-C++ + OpenGL Assessment for an RnD Intern position.

# Features Implemented
1. Camera Feed Integration: Uses Camera2 API with GLSurfaceView to capture frames at 1280x720 resolution.
2. Frame Processing via OpenCV (C++): Applies Canny Edge Detection using OpenCV in native C++ code via JNI.
3. Render Output with OpenGL ES: Renders the processed frame as a texture using OpenGL ES 2.0 with vertex and fragment shaders.
   
# Bonus Features:
Toggle button to switch between raw camera feed and edge-detected output.
FPS counter displayed on the UI.

# Setup Instructions:

1. # Install Prerequisites:
Android Studio with NDK and CMake installed.
Download OpenCV Android SDK (version 4.6.0 or later) from the OpenCV website.

2. # Configure OpenCV:

## a. Extract the OpenCV SDK to a directory like /path/to/OpenCV-android-sdk.
## b. Update settings.gradle to include the OpenCV module using:

include ':opencv'

project(':opencv').projectDir = new File('/path/to/OpenCV-android-sdk/sdk')


## c. Update gradle.properties with the OpenCV SDK path using:

opencvsdk=/path/to/OpenCV-android-sdk


## d. Update CMakeLists.txt with the correct OpenCV path using:

set(OpenCV_DIR /path/to/OpenCV-android-sdk/sdk/native/jni)


## e. Sync the project in Android Studio.


3. # Build and Run:
Open the project in Android Studio.
Build and run on a device with API level 21 or higher.
Grant camera permissions when prompted.

4. # Architecture Overview

/app: Contains MainActivity.java for camera setup, UI, and JNI calls, and CameraGLSurfaceView.java for OpenGL rendering.
/jni: Contains native-lib.cpp for OpenCV processing (Canny Edge Detection).
/gl: OpenGL rendering is handled in CameraGLSurfaceView.java with vertex and fragment shaders.
# Frame Flow:
 1. Camera2 API captures frames via GLSurfaceView.
 2. Frames are passed to native code via JNI (processFrame).
 3. OpenCV in C++ applies Canny Edge Detection (or passes raw frame if toggled off).
 4. Processed frame is rendered as a texture using OpenGL ES 2.0.

# Notes
 1. The app achieves 10-15 FPS on most devices, as verified by the FPS counter.
 2. The OpenGL pipeline uses GL_TEXTURE_EXTERNAL_OES for camera input and converts to GL_TEXTURE_2D for OpenCV processing and rendering.
 3. Error handling is minimal for simplicity but can be enhanced for production use.

