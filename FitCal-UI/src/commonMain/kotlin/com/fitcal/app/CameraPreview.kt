package com.fitcal.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fitcal.app.ui.screens.camera.barcode.CaptureMode

/**
 * Live camera. In [CaptureMode.Photo] it shows the shutter and gallery buttons and returns a photo;
 * in [CaptureMode.Barcode] it reads retail barcodes on-device and reports each detected value.
 * Switching [mode] keeps the same camera (and permission), so the user is asked only once.
 */
@Composable
expect fun CameraPreview(
    modifier: Modifier = Modifier,
    onPhotoCaptured: (ByteArray) -> Unit,
    onCancel: () -> Unit,
    mode: CaptureMode = CaptureMode.Photo,
    onBarcodeDetected: (String) -> Unit = {},
)
