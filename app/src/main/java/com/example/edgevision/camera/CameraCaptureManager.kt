package com.example.edgevision.camera

import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.util.Log
import android.view.Surface

class CameraCaptureManager {

    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null

    companion object {
        private const val TAG = "CameraCaptureManager"
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            // Frame captured successfully
        }

        override fun onCaptureFailed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            failure: android.hardware.camera2.CaptureFailure
        ) {
            Log.e(TAG, "Capture failed: ${failure.reason}")
        }
    }

    fun createCaptureSession(
        cameraDevice: CameraDevice,
        reader: ImageReader,
        onSessionConfigured: (CameraCaptureSession) -> Unit,
        onSessionFailed: () -> Unit
    ) {
        this.imageReader = reader

        val surfaces = listOf(reader.surface)

        val stateCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                Log.d(TAG, "Capture session configured")
                captureSession = session
                onSessionConfigured(session)
            }

            override fun onConfigureFailed(session: CameraCaptureSession) {
                Log.e(TAG, "Failed to configure capture session")
                onSessionFailed()
            }
        }

        try {
            cameraDevice.createCaptureSession(surfaces, stateCallback, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating capture session", e)
            onSessionFailed()
        }
    }

    fun startRepeatingCapture(cameraDevice: CameraDevice, surface: Surface) {
        val session = captureSession ?: run {
            Log.e(TAG, "Capture session not initialized")
            return
        }

        try {
            val captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)

            // Set auto focus and auto exposure
            captureRequestBuilder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            captureRequestBuilder.set(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON
            )

            session.setRepeatingRequest(
                captureRequestBuilder.build(),
                captureCallback,
                null
            )

            Log.d(TAG, "Started repeating capture request")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting repeating capture", e)
        }
    }

    fun stopCapture() {
        try {
            captureSession?.stopRepeating()
            captureSession?.close()
            captureSession = null
            Log.d(TAG, "Capture stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping capture", e)
        }
    }

    fun closeImageReader() {
        imageReader?.close()
        imageReader = null
    }
}
