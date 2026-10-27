package com.daniel.reproductor.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Color dominante y vibrante de una portada (para el estilo Aurora). */
@Composable
fun rememberArtworkColor(model: Any?): Color? {
    val context = LocalContext.current
    var color by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(model) {
        if (model == null) return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(model).size(128).allowHardware(false).build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return@LaunchedEffect
        color = withContext(Dispatchers.Default) {
            val palette = Palette.from(result.image.toBitmap()).generate()
            val swatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch
                ?: palette.dominantSwatch ?: return@withContext null
            Color(swatch.rgb)
        }
    }
    return color
}
