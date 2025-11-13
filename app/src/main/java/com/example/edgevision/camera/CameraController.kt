package com.example.edgevision.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.util.Log
import android.util.Size
import androidx.core.content.ContextCompat

class CameraController(private val context: Context) {

    private val cameraManager: CameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var cameraDevice: CameraDevice? = null
    private var cameraId: String? = null
    private var sensorOrientation: Int = 0

    companion object {
        private const val TAG = "CameraController"
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "Camera opened: ${camera.id}")
            cameraDevice = camera
            onCameraOpened(camera)
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.d(TAG, "Camera disconnected")
            camera.close()
            cameraDevice = null
            onCameraDisconnected()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "Camera error: $error")
            camera.close()
            cameraDevice = null
            onCameraError(error)
        }
    }

    // Callbacks for activity to implement
    var onCameraOpened: (CameraDevice) -> Unit = {}
    var onCameraDisconnected: () -> Unit = {}
    var onCameraError: (Int) -> Unit = {}

    init {
        selectBackCamera()
    }

    private fun selectBackCamera() {
        try {
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)

                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id
                    sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                    Log.d(TAG, "Selected back camera: $id, sensor orientation: $sensorOrientation")
                    return
                }
            }

            // Fallback to first available camera
            if (cameraManager.cameraIdList.isNotEmpty()) {
                cameraId = cameraManager.cameraIdList[0]
                val characteristics = cameraManager.getCameraCharacteristics(cameraId!!)
                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
                Log.d(TAG, "Using first available camera: $cameraId, sensor orientation: $sensorOrientation")
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to access camera", e)
        }
    }

    fun openCamera() {
        val id = cameraId ?: run {
            Log.e(TAG, "No camera available")
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Camera permission not granted")
            return
        }

        try {
            cameraManager.openCamera(id, stateCallback, null)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to open camera", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception opening camera", e)
        }
    }

    fun closeCamera() {
        cameraDevice?.close()
        cameraDevice = null
        Log.d(TAG, "Camera closed")
    }

    fun getOutputSizes(): Array<Size> {
        val id = cameraId ?: return emptyArray()

        try {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            return map?.getOutputSizes(android.graphics.ImageFormat.YUV_420_888) ?: emptyArray()
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to get output sizes", e)
            return emptyArray()
        }
    }

    fun getCameraDevice(): CameraDevice? = cameraDevice

    fun getSensorOrientation(): Int = sensorOrientation
}
