package com.example.edgevision.native

import android.graphics.Bitmap

/**
 * JNI interface for native OpenCV image processing
 */
object NativeProcessor {

    init {
        System.loadLibrary("edgevision")
    }

    /**
     * Process frame with Canny edge detection
     * @param inputData YUV frame data
     * @param width Frame width
     * @param height Frame height
     * @return Processed frame data (grayscale edge detected image)
     */
    external fun processFrameCanny(
        inputData: ByteArray,
        width: Int,
        height: Int
    ): ByteArray?

    /**
     * Convert frame to grayscale
     * @param inputData YUV frame data
     * @param width Frame width
     * @param height Frame height
     * @return Grayscale frame data
     */
    external fun processFrameGrayscale(
        inputData: ByteArray,
        width: Int,
        height: Int
    ): ByteArray?

    /**
     * Process frame and return as Bitmap
     * @param inputData YUV frame data
     * @param width Frame width
     * @param height Frame height
     * @param processingType 0=Canny, 1=Grayscale, 2=Original
     * @return Processed bitmap
     */
    external fun processFrameToBitmap(
        inputData: ByteArray,
        width: Int,
        height: Int,
        processingType: Int
    ): Bitmap?

    /**
     * Test native library connection
     * @return Version string from native library
     */
    external fun getVersionString(): String

    // Processing type constants
    const val PROCESSING_TYPE_CANNY = 0
    const val PROCESSING_TYPE_GRAYSCALE = 1
    const val PROCESSING_TYPE_ORIGINAL = 2
}
