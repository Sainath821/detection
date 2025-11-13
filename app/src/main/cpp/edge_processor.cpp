#include "edge_processor.h"
#include <android/log.h>

#define LOG_TAG "EdgeProcessor"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace edgevision {

EdgeProcessor::EdgeProcessor()
    : cannyThreshold1(50.0)
    , cannyThreshold2(150.0)
    , cannyApertureSize(3)
    , lastWidth(0)
    , lastHeight(0)
{
    LOGD("EdgeProcessor initialized with buffer reuse optimization");
}

EdgeProcessor::~EdgeProcessor() {
    LOGD("EdgeProcessor destroyed");
}

void EdgeProcessor::setCannyThresholds(double threshold1, double threshold2) {
    cannyThreshold1 = threshold1;
    cannyThreshold2 = threshold2;
}

cv::Mat EdgeProcessor::yuv420ToGray(const uint8_t* yuvData, int width, int height) {
    // YUV_420_888 format: Y plane is already grayscale
    // Reuse buffer if dimensions match
    if (grayBuffer.cols != width || grayBuffer.rows != height) {
        grayBuffer.create(height, width, CV_8UC1);
        lastWidth = width;
        lastHeight = height;
    }

    // Copy Y plane directly
    memcpy(grayBuffer.data, yuvData, width * height);
    return grayBuffer;
}

cv::Mat EdgeProcessor::yuv420ToBGR(const uint8_t* yuvData, int width, int height) {
    // Create Mat from YUV data
    // YUV_420_888: Y plane (width*height), U plane (width*height/4), V plane (width*height/4)
    cv::Mat yuvMat(height + height / 2, width, CV_8UC1, (void*)yuvData);

    cv::Mat bgrMat;
    cv::cvtColor(yuvMat, bgrMat, cv::COLOR_YUV2BGR_I420);

    return bgrMat;
}

cv::Mat EdgeProcessor::processGrayscale(const uint8_t* yuvData, int width, int height) {
    return yuv420ToGray(yuvData, width, height);
}

cv::Mat EdgeProcessor::processCanny(const uint8_t* yuvData, int width, int height) {
    // Convert to grayscale first (reuses grayBuffer)
    cv::Mat grayMat = yuv420ToGray(yuvData, width, height);

    // Ensure blur buffer exists with correct dimensions
    if (blurredBuffer.cols != width || blurredBuffer.rows != height) {
        blurredBuffer.create(height, width, CV_8UC1);
    }

    // Ensure edges buffer exists with correct dimensions
    if (edgesBuffer.cols != width || edgesBuffer.rows != height) {
        edgesBuffer.create(height, width, CV_8UC1);
    }

    // Apply Gaussian blur to reduce noise (in-place operation)
    cv::GaussianBlur(grayMat, blurredBuffer, cv::Size(5, 5), 1.5);

    // Apply Canny edge detection (in-place operation)
    cv::Canny(blurredBuffer, edgesBuffer, cannyThreshold1, cannyThreshold2, cannyApertureSize);

    return edgesBuffer;
}

std::vector<uint8_t> EdgeProcessor::matToByteArray(const cv::Mat& mat) {
    std::vector<uint8_t> byteArray;

    if (mat.isContinuous()) {
        byteArray.assign(mat.data, mat.data + mat.total() * mat.elemSize());
    } else {
        for (int i = 0; i < mat.rows; ++i) {
            byteArray.insert(byteArray.end(), mat.ptr<uint8_t>(i), mat.ptr<uint8_t>(i) + mat.cols * mat.elemSize());
        }
    }

    return byteArray;
}

} // namespace edgevision
