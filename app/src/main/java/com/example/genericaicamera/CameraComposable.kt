package com.example.genericaicamera

import android.Manifest
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPreview(
    paddingValues: PaddingValues,
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
    val cameraEnabled by remember { mutableStateOf(true) }

    // Check for permissions.
    // https://medium.com/@chiragthummar16/requesting-permission-jetpack-compose-the-complete-guide-03e8aaa1f4a0
    val permissionCamera = rememberPermissionState(
        permission = Manifest.permission.CAMERA
    )
    val showCameraRationalDialog = remember { mutableStateOf(false) }
    val launcherCamera = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { permissionGranted ->
        if (permissionGranted) {
            // isLocked = false
        } else {
            if (permissionCamera.status.shouldShowRationale) {
                // Show a rationale if needed (optional)
                showCameraRationalDialog.value = true
            } else {
                showCameraRationalDialog.value = true
            }
        }
    }

    LaunchedEffect(Unit) {
        launcherCamera.launch(Manifest.permission.CAMERA)
    }

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
                    viewModel.detectLivestreamFrame(it)
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
            .padding(paddingValues)
            .aspectRatio(3f / 4)
            .onSizeChanged { size ->
                // size.width and size.height represent the actual dimensions of the preview view
                // Log.d("CameraPreview", "Preview size: ${size.width} ${size.height}")
                viewModel.updateCameraViewValues(size.width, size.height)
            }
    ) {
        preview.surfaceProvider = previewView.surfaceProvider
    }
}
