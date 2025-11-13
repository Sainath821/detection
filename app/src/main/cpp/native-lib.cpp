#include <jni.h>
#include <string>
#include <android/log.h>
#include <vector>
#include <chrono>
#include "edge_processor.h"

#define LOG_TAG "EdgeVision-Native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// Global edge processor instance
static edgevision::EdgeProcessor* g_edgeProcessor = nullptr;

// Initialize edge processor
static void ensureEdgeProcessorInitialized() {
    if (g_edgeProcessor == nullptr) {
        g_edgeProcessor = new edgevision::EdgeProcessor();
    }
}

extern "C" {

/**
 * Get version string - test function
 */
JNIEXPORT jstring JNICALL
Java_com_example_edgevision_native_NativeProcessor_getVersionString(
        JNIEnv* env,
        jobject /* this */) {
    std::string version = "EdgeVision Native v1.0 - OpenCV Ready";
    LOGD("getVersionString called");
    return env->NewStringUTF(version.c_str());
}

/**
 * Process frame with Canny edge detection
 */
JNIEXPORT jbyteArray JNICALL
Java_com_example_edgevision_native_NativeProcessor_processFrameCanny(
        JNIEnv* env,
        jobject /* this */,
        jbyteArray inputData,
        jint width,
        jint height) {

    auto startTime = std::chrono::high_resolution_clock::now();

    // Initialize edge processor
    ensureEdgeProcessorInitialized();

    // Get input data
    jbyte* inputBytes = env->GetByteArrayElements(inputData, nullptr);
    if (inputBytes == nullptr) {
        LOGE("Failed to get input bytes");
        return nullptr;
    }

    try {
        // Validate input dimensions
        if (width <= 0 || height <= 0) {
            LOGE("Invalid dimensions: %dx%d", width, height);
            env->ReleaseByteArrayElements(inputData, inputBytes, JNI_ABORT);
            return nullptr;
        }

        // Process with OpenCV Canny
        cv::Mat edges = g_edgeProcessor->processCanny(
            reinterpret_cast<const uint8_t*>(inputBytes),
            width,
            height
        );

        // Convert Mat to byte array
        std::vector<uint8_t> resultBytes = g_edgeProcessor->matToByteArray(edges);

        // Create Java byte array
        jbyteArray outputArray = env->NewByteArray(resultBytes.size());
        if (outputArray == nullptr) {
            env->ReleaseByteArrayElements(inputData, inputBytes, JNI_ABORT);
            LOGE("Failed to allocate output array");
            return nullptr;
        }

        env->SetByteArrayRegion(outputArray, 0, resultBytes.size(),
                                reinterpret_cast<const jbyte*>(resultBytes.data()));

        env->ReleaseByteArrayElements(inputData, inputBytes, JNI_ABORT);

        // Calculate processing time
        auto endTime = std::chrono::high_resolution_clock::now();
        auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(endTime - startTime);
        LOGI("Canny processing: %dx%d in %lld ms, output: %zu bytes",
             width, height, duration.count(), resultBytes.size());

        return outputArray;

    } catch (const cv::Exception& e) {
        LOGE("OpenCV exception in processCanny: %s", e.what());
        env->ReleaseByteArrayElements(inputData, inputBytes, JNI_ABORT);
        return nullptr;
    } catch (const std::exception& e) {
        LOGE("Standard exception in processCanny: %s", e.what());
        env->ReleaseByteArrayElements(inputData, inputBytes, JNI_ABORT);
        return nullptr;
    } catch (...) {
        LOGE("Unknown exception in processCanny");
        env->ReleaseByteArrayElements(inputData, inputBytes, JNI_ABORT);
        return nullptr;
    }
}

/**
 * Process frame to grayscale
 */
JNIEXPORT jbyteArray JNICALL
Java_com_example_edgevision_native_NativeProcessor_processFrameGrayscale(
        JNIEnv* env,
        jobject /* this */,
        jbyteArray inputData,
        jint width,
        jint height) {

    auto startTime = std::chrono::high_resolution_clock::now();

    // Initialize edge processor
    ensureEdgeProcessorInitialized();

    // Get input data
    jbyte* inputBytes = env->GetByteArrayElements(inputData, nullptr);
    if (inputBytes == nullptr) {
        LOGE("Failed to get input bytes");
        return nullptr;
    }

    try {
        // Validate input dimensions
        if (width <= 0 || height <= 0) {
            LOGE("Invalid dimensions: %dx%d", width, height);
            env->ReleaseByteArrayElements(inputData, inputBytes, JNI_ABORT);
            return nullptr;
        }

        // Process with OpenCV grayscale
        cv::Mat grayMat = g_edgeProcessor->processGrayscale(
            reinterpret_cast<const uint8_t*>(inputBytes),
            width,
            height
        );

        // Convert Mat to byte array
        std::vector<uint8_t> resultBytes = g_edgeProcessor->matToByteArray(grayMat);

        // Create Java byte array
        jbyteArray outputArray = env->NewByteArray(resultBytes.size());
        if (outputArray == nullptr) {
            env->ReleaseByteArrayElements(inputData, inputBytes, JNI_ABORT);
            LOGE("Failed to allocate output array");
            return nullptr;
        }

        env->SetByteArrayRegion(outputArray, 0, resultBytes.size(),
                                reinterpret_cast<const jbyte*>(resultBytes.data()));

        env->ReleaseByteArrayElements(inputData, inputBytes, JNI_ABORT);

        // Calculate processing time
        auto endTime = std::chrono::high_resolution_clock::now();
        auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(endTime - startTime);
        LOGI("Grayscale processing: %dx%d in %lld ms, output: %zu bytes",
             width, height, duration.count(), resultBytes.size());

        return outputArray;

    } catch (const cv::Exception& e) {
        LOGE("OpenCV exception in processGrayscale: %s", e.what());
        env->ReleaseByteArrayElements(inputData, inputBytes, JNI_ABORT);
        return nullptr;
    } catch (const std::exception& e) {
        LOGE("Standard exception in processGrayscale: %s", e.what());
        env->ReleaseByteArrayElements(inputData, inputBytes, JNI_ABORT);
        return nullptr;
    } catch (...) {
        LOGE("Unknown exception in processGrayscale");
        env->ReleaseByteArrayElements(inputData, inputBytes, JNI_ABORT);
        return nullptr;
    }
}

/**
 * Process frame and return as Bitmap
 */
JNIEXPORT jobject JNICALL
Java_com_example_edgevision_native_NativeProcessor_processFrameToBitmap(
        JNIEnv* /* env */,
        jobject /* this */,
        jbyteArray /* inputData */,
        jint width,
        jint height,
        jint processingType) {

    LOGD("processFrameToBitmap: %dx%d, type=%d", width, height, processingType);

    // TODO: Implement bitmap conversion with OpenCV
    // For now, return null
    return nullptr;
}

} // extern "C"
