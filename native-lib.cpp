#include <jni.h>
#include <opencv2/opencv.hpp>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <android/log.h>

#define LOG_TAG "NativeLib"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT void JNICALL
Java_com_example_edgedetectionviewer_MainActivity_initOpenCV(JNIEnv* env, jobject /* this */) {
    LOGD("Initializing OpenCV");
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_edgedetectionviewer_MainActivity_processFrame(JNIEnv* env, jobject /* this */,
    jlong texIn, jlong texOut, jboolean applyEdges) {
    // Read the input texture (OES texture from camera)
    GLuint inputTexture = (GLuint) texIn;
    GLuint outputTexture = (GLuint) texOut;

    // Get texture dimensions (hardcoded for simplicity; in practice, query via glGetTexLevelParameter)
    int width = 1280;
    int height = 720;

    // Read pixels from the input texture
    cv::Mat rgba(height, width, CV_8UC4);
    glBindTexture(GL_TEXTURE_2D, 0); // Unbind any previous texture
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, inputTexture);
    glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, rgba.data);
    glBindTexture(GL_TEXTURE_EXTERNAL_OES, 0);

    // Process the frame
    cv::Mat processed;
    if (applyEdges) {
        cv::Mat gray, edges;
        cv::cvtColor(rgba, gray, cv::COLOR_RGBA2GRAY);
        cv::Canny(gray, edges, 80, 100);
        cv::cvtColor(edges, processed, cv::COLOR_GRAY2RGBA);
    } else {
        processed = rgba;
    }

    // Write to the output texture
    glBindTexture(GL_TEXTURE_2D, outputTexture);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, processed.data);
    glBindTexture(GL_TEXTURE_2D, 0);

    LOGD("Frame processed, edges: %d", applyEdges);
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_edgedetectionviewer_MainActivity_releaseOpenCV(JNIEnv* env, jobject /* this */) {
    LOGD("OpenCV released");
}