package com.fitcal.app

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.fitcal.app.ui.screens.camera.barcode.CaptureMode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.io.File
import java.util.concurrent.Executors

@Composable
actual fun CameraPreview(
    modifier: Modifier,
    onPhotoCaptured: (ByteArray) -> Unit,
    onCancel: () -> Unit,
    mode: CaptureMode,
    onBarcodeDetected: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            onCancel()
        }
    }

    // Gallery picker — reads the selected image URI into ByteArray and
    // feeds it straight into onPhotoCaptured, which runs the existing
    // compressImage → apiClient.analyzeMealImage pipeline.
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    onPhotoCaptured(bytes)
                } else {
                    Log.w("CameraPreview", "Gallery URI produced null stream: $uri")
                }
            } catch (e: Exception) {
                Log.e("CameraPreview", "Failed to read gallery image: ${e.message}", e)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    // Barcode mode: ML Kit's bundled on-device model (no download, no network, free).
    val currentOnBarcodeDetected by rememberUpdatedState(onBarcodeDetected)
    val barcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                )
                .build()
        )
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(analysisExecutor) { proxy ->
                    scanBarcodeFrame(barcodeScanner, proxy) { code -> currentOnBarcodeDetected(code) }
                }
            }
    }
    DisposableEffect(Unit) {
        onDispose {
            imageAnalysis.clearAnalyzer()
            barcodeScanner.close()
            analysisExecutor.shutdown()
        }
    }

    // Rebinds when the mode changes: photo mode uses ImageCapture, barcode mode ImageAnalysis.
    // The preview and the permission stay the same.
    LaunchedEffect(mode) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    if (mode == CaptureMode.Barcode) imageAnalysis else imageCapture
                )
            } catch (exc: Exception) {
                Log.e("CameraPreview", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Cancel Button (Top Left)
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Cancel",
                tint = Color.White
            )
        }

        // Bottom controls: [Gallery] ── [Shutter] ── (photo mode only; barcodes are read live)
        if (mode == CaptureMode.Photo) Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gallery picker button
            IconButton(
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)
            ) {
                Icon(
                    imageVector = PhotoIcon,
                    contentDescription = "Upload from gallery",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Shutter button
            Button(
                onClick = {
                    val tempFile = File.createTempFile("meal_capture", ".jpg", context.cacheDir)
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()
                    imageCapture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                val bytes = tempFile.readBytes()
                                tempFile.delete()
                                onPhotoCaptured(bytes)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e("CameraPreview", "Photo capture failed: ${exception.message}", exception)
                            }
                        }
                    )
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                modifier = Modifier
                    .size(76.dp)
                    .border(4.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(Color.White, shape = CircleShape)
                )
            }
        }
    }
}

/**
 * Reads one camera frame with ML Kit and reports the first retail barcode found.
 * The frame is always closed, so the analyzer keeps receiving new ones.
 */
@androidx.annotation.OptIn(markerClass = [ExperimentalGetImage::class])
private fun scanBarcodeFrame(scanner: BarcodeScanner, proxy: ImageProxy, onCode: (String) -> Unit) {
    val mediaImage = proxy.image
    if (mediaImage == null) {
        proxy.close()
        return
    }
    val input = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
    scanner.process(input)
        .addOnSuccessListener { barcodes ->
            // Success listeners run on the main thread, where the UI state lives.
            barcodes.firstNotNullOfOrNull { it.rawValue?.takeIf { value -> value.isNotBlank() } }?.let(onCode)
        }
        .addOnFailureListener { e -> Log.w("CameraPreview", "Barcode scan failed on a frame: ${e.message}") }
        .addOnCompleteListener { proxy.close() }
}

private val PhotoIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Filled.Photo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(21f, 19f)
            verticalLineTo(5f)
            curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
            horizontalLineTo(5f)
            curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
            verticalLineTo(19f)
            curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
            horizontalLineTo(19f)
            curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
            close()
            moveTo(8.5f, 13.5f)
            lineTo(11f, 16.51f)
            lineTo(14.5f, 12f)
            lineTo(19f, 18f)
            horizontalLineTo(5f)
            lineTo(8.5f, 13.5f)
            close()
        }
    }.build()
}

