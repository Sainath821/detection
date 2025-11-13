package com.example.edgevision

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraDevice
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import android.view.TextureView
import android.opengl.GLSurfaceView
import com.example.edgevision.camera.CameraCaptureManager
import com.example.edgevision.camera.CameraController
import com.example.edgevision.camera.FrameBufferQueue
import com.example.edgevision.camera.FrameReader
import com.example.edgevision.camera.PreviewSurface
import com.example.edgevision.gl.EdgeVisionRenderer
import com.example.edgevision.native.NativeProcessor
import com.example.edgevision.ui.theme.EdgeVisionTheme
import com.example.edgevision.websocket.WebSocketManager

class MainActivity : ComponentActivity() {

    private var showPermissionDeniedDialog by mutableStateOf(false)
    private var hasPermission by mutableStateOf(false)
    private var cameraStatus by mutableStateOf("Initializing...")
    private var frameCount by mutableStateOf(0)
    private var isEdgeDetectionEnabled by mutableStateOf(true)
    private var isWebSocketServerRunning by mutableStateOf(false)
    private var webSocketUrl by mutableStateOf("Not started")
    private var connectedClients by mutableStateOf(0)

    // Camera components
    private lateinit var cameraController: CameraController
    private lateinit var captureManager: CameraCaptureManager
    private var frameReader: FrameReader? = null
    private var textureView: TextureView? = null
    private var previewSurface: PreviewSurface? = null
    private var glSurfaceView: GLSurfaceView? = null
    private var glRenderer: EdgeVisionRenderer? = null

    // WebSocket components
    private lateinit var webSocketManager: WebSocketManager

    companion object {
        private const val TAG = "MainActivity"
        // Use square dimensions to match camera output
        private val PREVIEW_SIZE = Size(1088, 1088)
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onCameraPermissionGranted()
        } else {
            onCameraPermissionDenied()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize camera components
        cameraController = CameraController(this)
        captureManager = CameraCaptureManager()

        // Initialize WebSocket manager
        webSocketManager = WebSocketManager(this)
        setupWebSocketCallbacks()

        // Test native library loading
        testNativeLibrary()

        // Set up camera callbacks
        setupCameraCallbacks()

        setContent {
            EdgeVisionTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    if (hasPermission) {
                        CameraScreen(
                            status = cameraStatus,
                            frameCount = frameCount,
                            isEdgeDetectionEnabled = isEdgeDetectionEnabled,
                            isWebSocketServerRunning = isWebSocketServerRunning,
                            webSocketUrl = webSocketUrl,
                            connectedClients = connectedClients,
                            onToggleProcessing = { toggleProcessing() },
                            onCaptureFrame = { captureFrame() },
                            onToggleWebSocket = { toggleWebSocketServer() },
                            onTextureViewCreated = { view ->
                                textureView = view
                                setupPreviewSurface(view)
                            },
                            onGLSurfaceViewCreated = { view ->
                                glSurfaceView = view
                                setupGLSurface(view)
                            },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        PermissionRequiredScreen(
                            modifier = Modifier.padding(innerPadding),
                            onRequestPermission = { checkCameraPermission() }
                        )
                    }

                    if (showPermissionDeniedDialog) {
                        PermissionDeniedDialog(
                            onDismiss = { showPermissionDeniedDialog = false },
                            onOpenSettings = { openAppSettings() }
                        )
                    }
                }
            }
        }

        checkCameraPermission()
    }

    private fun testNativeLibrary() {
        try {
            val version = NativeProcessor.getVersionString()
            Log.i(TAG, "✓ Native library loaded successfully: $version")
            cameraStatus = "Native library ready"
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "✗ Failed to load native library", e)
            cameraStatus = "Native library error"
            Toast.makeText(this, "Native library failed to load", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "✗ Error testing native library", e)
            cameraStatus = "Native library test failed"
        }
    }

    private fun setupWebSocketCallbacks() {
        webSocketManager.onServerStateChanged = { isRunning ->
            isWebSocketServerRunning = isRunning
            if (isRunning) {
                webSocketUrl = webSocketManager.getServerUrl()
                Log.i(TAG, "WebSocket server URL: $webSocketUrl")
            } else {
                webSocketUrl = "Not started"
            }
        }

        webSocketManager.onClientCountChanged = { count ->
            connectedClients = count
            Log.d(TAG, "Connected WebSocket clients: $count")
        }
    }

    private fun setupCameraCallbacks() {
        cameraController.onCameraOpened = { camera ->
            Log.d(TAG, "Camera opened callback")
            cameraStatus = "Camera opened"
            startCameraPreview(camera)
        }

        cameraController.onCameraDisconnected = {
            Log.d(TAG, "Camera disconnected callback")
            cameraStatus = "Camera disconnected"
        }

        cameraController.onCameraError = { error ->
            Log.e(TAG, "Camera error callback: $error")
            cameraStatus = "Camera error: $error"
            Toast.makeText(this, "Camera error: $error", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupPreviewSurface(view: TextureView) {
        previewSurface = PreviewSurface(view, PREVIEW_SIZE).apply {
            onSurfaceReady = { surface ->
                Log.d(TAG, "Preview surface ready")
                // Surface is ready but we don't use it for preview
                // We use ImageReader for frame capture instead
            }
        }
    }

    private fun setupGLSurface(view: GLSurfaceView) {
        Log.d(TAG, "Setting up GLSurfaceView with renderer")

        // Create and set renderer with context
        glRenderer = EdgeVisionRenderer(this)
        view.setRenderer(glRenderer)

        // Set render mode to continuous (will optimize later)
        view.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        Log.i(TAG, "GLSurfaceView configured with EdgeVisionRenderer")
    }

    private fun startCameraPreview(cameraDevice: CameraDevice) {
        // Initialize frame reader with buffer queue
        frameReader = FrameReader(PREVIEW_SIZE).apply {
            onFrameAvailable = { frameBuffer ->
                processFrame(frameBuffer)
            }
        }

        val reader = frameReader?.getImageReader() ?: return

        // Create capture session with both preview surface and image reader
        val surfaces = mutableListOf(reader.surface)
        previewSurface?.getSurface()?.let { surfaces.add(it) }

        // Create capture session
        captureManager.createCaptureSession(
            cameraDevice = cameraDevice,
            reader = reader,
            onSessionConfigured = { session ->
                Log.d(TAG, "Capture session configured")
                cameraStatus = "Camera ready - capturing frames"
                captureManager.startRepeatingCapture(cameraDevice, reader.surface)
            },
            onSessionFailed = {
                Log.e(TAG, "Failed to configure capture session")
                cameraStatus = "Failed to start camera"
            }
        )
    }

    private fun processFrame(frameBuffer: FrameBufferQueue.FrameBuffer) {
        // Update frame count
        frameCount++

        // Process frames with OpenCV
        try {
            val processedData = if (isEdgeDetectionEnabled) {
                // Process with Canny edge detection
                NativeProcessor.processFrameCanny(
                    frameBuffer.data,
                    frameBuffer.width,
                    frameBuffer.height
                )
            } else {
                // Process with grayscale (raw feed)
                NativeProcessor.processFrameGrayscale(
                    frameBuffer.data,
                    frameBuffer.width,
                    frameBuffer.height
                )
            }

            if (processedData != null) {
                // Update GL renderer with processed frame
                glRenderer?.updateFrame(processedData)

                // Send frame via WebSocket if server is running
                val processingMode = if (isEdgeDetectionEnabled) "Canny Edge Detection" else "Grayscale"
                val fps = glRenderer?.getFPS() ?: 0.0
                webSocketManager.sendFrame(
                    frameData = processedData,
                    width = frameBuffer.width,
                    height = frameBuffer.height,
                    format = "Grayscale",
                    processingMode = processingMode,
                    fps = fps
                )

                // Log every 30th frame
                if (frameCount % 30 == 0) {
                    val mode = if (isEdgeDetectionEnabled) "edge detection" else "grayscale"
                    Log.d(TAG, "Frame #$frameCount: Processed ($mode), " +
                            "input: ${frameBuffer.data.size} bytes, output: ${processedData.size} bytes")
                }
            } else {
                Log.e(TAG, "Frame #$frameCount: Processing failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing frame #$frameCount", e)
        }
    }

    private fun toggleProcessing() {
        isEdgeDetectionEnabled = !isEdgeDetectionEnabled
        val mode = if (isEdgeDetectionEnabled) "Edge Detection" else "Grayscale"
        Toast.makeText(this, "Switched to $mode", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Processing mode: $mode")
    }

    private fun toggleWebSocketServer() {
        if (isWebSocketServerRunning) {
            webSocketManager.stopServer()
            Toast.makeText(this, "WebSocket server stopped", Toast.LENGTH_SHORT).show()
        } else {
            if (!webSocketManager.isWifiConnected()) {
                Toast.makeText(this, "Please connect to WiFi first", Toast.LENGTH_LONG).show()
                return
            }
            val started = webSocketManager.startServer()
            if (started) {
                Toast.makeText(this, "WebSocket server started\n$webSocketUrl", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Failed to start WebSocket server", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun captureFrame() {
        // Get current frame from renderer
        glRenderer?.let { renderer ->
            renderer.captureCurrentFrame { bitmap ->
                if (bitmap != null) {
                    // Save to gallery
                    val fileName = "EdgeVision_${System.currentTimeMillis()}.png"
                    try {
                        val savedUri = android.provider.MediaStore.Images.Media.insertImage(
                            contentResolver,
                            bitmap,
                            fileName,
                            "EdgeVision processed frame"
                        )
                        if (savedUri != null) {
                            Toast.makeText(this, "Saved: $fileName", Toast.LENGTH_SHORT).show()
                            Log.d(TAG, "Frame saved: $savedUri")
                        } else {
                            Toast.makeText(this, "Failed to save frame", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error saving frame", e)
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    bitmap.recycle()
                } else {
                    Toast.makeText(this, "No frame available", Toast.LENGTH_SHORT).show()
                }
            }
        } ?: run {
            Toast.makeText(this, "Renderer not ready", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocketManager.stopServer()
        captureManager.stopCapture()
        cameraController.closeCamera()
        frameReader?.close()
        previewSurface?.release()
        glSurfaceView?.onPause()
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                onCameraPermissionGranted()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                showPermissionDeniedDialog = true
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun onCameraPermissionGranted() {
        hasPermission = true
        cameraStatus = "Opening camera..."
        Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show()
        cameraController.openCamera()
    }

    private fun onCameraPermissionDenied() {
        hasPermission = false
        showPermissionDeniedDialog = true
    }

    private fun openAppSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            startActivity(this)
        }
    }
}

@Composable
fun PermissionRequiredScreen(
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Camera Permission Required",
            modifier = Modifier.padding(16.dp)
        )
        Text(
            text = "EdgeVision needs camera access to process frames",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.padding(16.dp)
        ) {
            Text("Grant Permission")
        }
    }
}

@Composable
fun PermissionDeniedDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Camera Permission Denied") },
        text = {
            Text("Camera permission is required for EdgeVision to function. Please grant permission in app settings.")
        },
        confirmButton = {
            TextButton(onClick = {
                onOpenSettings()
                onDismiss()
            }) {
                Text("Open Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CameraScreen(
    status: String,
    frameCount: Int,
    isEdgeDetectionEnabled: Boolean = true,
    isWebSocketServerRunning: Boolean = false,
    webSocketUrl: String = "Not started",
    connectedClients: Int = 0,
    onToggleProcessing: () -> Unit = {},
    onCaptureFrame: () -> Unit = {},
    onToggleWebSocket: () -> Unit = {},
    onTextureViewCreated: (TextureView) -> Unit,
    onGLSurfaceViewCreated: ((GLSurfaceView) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // OpenGL rendering surface
        if (onGLSurfaceViewCreated != null) {
            AndroidView(
                factory = { context ->
                    GLSurfaceView(context).apply {
                        setEGLContextClientVersion(2)
                        onGLSurfaceViewCreated(this)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Camera preview TextureView (fallback)
            AndroidView(
                factory = { context ->
                    TextureView(context).also { textureView ->
                        onTextureViewCreated(textureView)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Status overlay at top
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            color = Color.Black.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "EdgeVision",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "Frames: $frameCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Green,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = if (isWebSocketServerRunning) "WS: $connectedClients clients" else "WebSocket: Off",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isWebSocketServerRunning) Color.Cyan else Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (isWebSocketServerRunning) {
                    Text(
                        text = webSocketUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Yellow,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        // Control buttons at bottom
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = onToggleProcessing) {
                    Text(if (isEdgeDetectionEnabled) "Grayscale" else "Edge Det")
                }
                Button(onClick = onCaptureFrame) {
                    Text("Capture")
                }
                Button(onClick = onToggleWebSocket) {
                    Text(if (isWebSocketServerRunning) "Stop WS" else "Start WS")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CameraScreenPreview() {
    EdgeVisionTheme {
        CameraScreen(
            status = "Camera ready - capturing frames",
            frameCount = 1234,
            onTextureViewCreated = {}
        )
    }
}