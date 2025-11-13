#ifndef EDGEVISION_EDGE_PROCESSOR_H
#define EDGEVISION_EDGE_PROCESSOR_H

#include <opencv2/opencv.hpp>
#include <vector>

namespace edgevision {

/**
 * OpenCV image processing for edge detection with optimized memory management
 */
class EdgeProcessor {
public:
    EdgeProcessor();
    ~EdgeProcessor();

    /**
     * Set Canny edge detection thresholds
     */
    void setCannyThresholds(double threshold1, double threshold2);

    /**
     * Convert YUV_420_888 to grayscale Mat
     */
    cv::Mat yuv420ToGray(const uint8_t* yuvData, int width, int height);

    /**
     * Convert YUV_420_888 to BGR Mat
     */
    cv::Mat yuv420ToBGR(const uint8_t* yuvData, int width, int height);

    /**
     * Process frame to grayscale (optimized with buffer reuse)
     */
    cv::Mat processGrayscale(const uint8_t* yuvData, int width, int height);

    /**
     * Process frame with Canny edge detection (optimized with buffer reuse)
     */
    cv::Mat processCanny(const uint8_t* yuvData, int width, int height);

    /**
     * Convert Mat to byte array for JNI return
     */
    std::vector<uint8_t> matToByteArray(const cv::Mat& mat);

private:
    double cannyThreshold1;
    double cannyThreshold2;
    int cannyApertureSize;

    // Reusable buffers to minimize allocations
    cv::Mat grayBuffer;
    cv::Mat blurredBuffer;
    cv::Mat edgesBuffer;
    int lastWidth;
    int lastHeight;
};

} // namespace edgevision

#endif // EDGEVISION_EDGE_PROCESSOR_H
