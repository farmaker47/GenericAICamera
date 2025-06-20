package com.example.genericaicamera

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle


@Composable
fun OverlayComposable(
    paddingValues: PaddingValues,
    viewModel: MainViewModel = hiltViewModel()
) {
    val mask = viewModel.maskBitmap.collectAsStateWithLifecycle()

    Image(
        painter = BitmapPainter(mask.value.asImageBitmap()),
        contentDescription = "Bitmap mask",
        //alpha = 0.5f,
        modifier = Modifier.padding(paddingValues)
    )
}