package com.example.genericaicamera

import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun CameraPreview(
    viewModel: MainViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var previewDisplayRotation by remember { mutableIntStateOf(Surface.ROTATION_0) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val cameraProvider = cameraProviderFuture.get()
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraSelector = remember {
        CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()
    }
    val preview = remember { Preview.Builder().build() }
    val previewView = remember { PreviewView(context) }
    val cameraEnabled by viewModel.cameraEnabled.observeAsState(false)

    if (cameraEnabled) {

        cameraProvider.unbindAll()

        // Override bug with wrong ImageProxy orientation
        previewView.display?.let { display ->
            previewDisplayRotation = display.rotation
        }

        val imageAnalyzer = ImageAnalysis.Builder()
            .setTargetRotation(previewDisplayRotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .apply {
                setAnalyzer(ContextCompat.getMainExecutor(context)) {
                    viewModel.detectorHelper.detectLivestreamFrame(it)
                }
            }

        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageAnalyzer
        )
    } else {
        cameraProvider.unbindAll()
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4)
    ) {
        // preview.setSurfaceProvider(previewView.surfaceProvider)
    }
}