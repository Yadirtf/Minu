package com.daniel.reproductor.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * Zona táctil para adelantar/retroceder: tocar o arrastrar. [content] recibe la fracción
 * a dibujar (la del dedo mientras arrastra, o la posición real).
 */
@Composable
fun SeekArea(
    state: PlayerState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(fraction: Float, dragging: Boolean) -> Unit,
) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val duration by rememberUpdatedState(state.durationMs)
    val fraction = drag ?: if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f

    Box(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    if (duration > 0) state.seekTo(((o.x / size.width).coerceIn(0f, 1f) * duration).toLong())
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        drag?.let { if (duration > 0) state.seekTo((it * duration).toLong()) }
                        drag = null
                    },
                    onDragCancel = { drag = null },
                    onHorizontalDrag = { change, _ -> drag = (change.position.x / size.width).coerceIn(0f, 1f) },
                )
            },
    ) { content(fraction, drag != null) }
}

/** Neón: forma de onda con barras; lo reproducido brilla en degradado. */
@Composable
fun WaveSeekBar(state: PlayerState, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val seed = state.currentVideoId?.hashCode() ?: 0
    val bars = remember(seed) {
        val r = Random(seed)
        // Onda suave con algo de ruido para que parezca audio real
        List(56) { i -> (0.25f + 0.55f * kotlin.math.abs(kotlin.math.sin(i * 0.33f + r.nextFloat())) + r.nextFloat() * 0.2f).coerceIn(0.12f, 1f) }
    }
    SeekArea(state, modifier) { fraction, _ ->
        Canvas(Modifier.fillMaxSize()) {
            val gap = 3.dp.toPx()
            val w = (size.width - gap * (bars.size - 1)) / bars.size
            val played = Brush.horizontalGradient(listOf(colors.primary, colors.secondary), 0f, size.width)
            bars.forEachIndexed { i, h ->
                val x = i * (w + gap)
                val barH = size.height * h
                val isPlayed = (x + w / 2) / size.width <= fraction
                val top = (size.height - barH) / 2
                if (isPlayed) {
                    drawRoundRect(played, Offset(x, top), Size(w, barH), CornerRadius(w / 2))
                } else {
                    drawRoundRect(colors.onSurfaceVariant.copy(alpha = 0.28f), Offset(x, top), Size(w, barH), CornerRadius(w / 2))
                }
            }
        }
    }
}

/** Vinilo: línea fina con marcas cada 10 % y una aguja redonda. */
@Composable
fun TickSeekBar(state: PlayerState, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    SeekArea(state, modifier) { fraction, dragging ->
        Canvas(Modifier.fillMaxSize()) {
            val cy = size.height / 2
            for (i in 0..20) {
                val x = size.width * i / 20f
                val tall = i % 5 == 0
                drawLine(
                    colors.onSurfaceVariant.copy(alpha = if (tall) 0.6f else 0.3f),
                    Offset(x, cy - if (tall) 9.dp.toPx() else 5.dp.toPx()),
                    Offset(x, cy + if (tall) 9.dp.toPx() else 5.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            drawLine(colors.outline, Offset(0f, cy), Offset(size.width, cy), 1.5.dp.toPx())
            drawLine(colors.primary, Offset(0f, cy), Offset(size.width * fraction, cy), 3.dp.toPx(), StrokeCap.Round)
            drawCircle(colors.primary, if (dragging) 10.dp.toPx() else 7.dp.toPx(), Offset(size.width * fraction, cy))
            drawCircle(colors.background, 2.5.dp.toPx(), Offset(size.width * fraction, cy))
        }
    }
}

/** Aurora: línea fina de cristal con perilla blanca. */
@Composable
fun GlassSeekBar(state: PlayerState, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    SeekArea(state, modifier) { fraction, dragging ->
        Canvas(Modifier.fillMaxSize()) {
            val cy = size.height / 2
            val stroke = if (dragging) 8.dp.toPx() else 5.dp.toPx()
            drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, cy), Offset(size.width, cy), stroke, StrokeCap.Round)
            drawLine(
                Brush.horizontalGradient(listOf(colors.primary.copy(alpha = 0.7f), colors.primary)),
                Offset(0f, cy), Offset((size.width * fraction).coerceAtLeast(0.1f), cy), stroke, StrokeCap.Round,
            )
            drawCircle(Color.White, if (dragging) 11.dp.toPx() else 8.dp.toPx(), Offset(size.width * fraction, cy))
        }
    }
}

/** Terminal: bloques tipo barra de progreso de consola. */
@Composable
fun BlockSeekBar(state: PlayerState, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    SeekArea(state, modifier) { fraction, _ ->
        Canvas(Modifier.fillMaxSize()) {
            val blocks = 28
            val gap = 2.dp.toPx()
            val w = (size.width - gap * (blocks - 1)) / blocks
            val filled = (fraction * blocks).toInt()
            for (i in 0 until blocks) {
                val x = i * (w + gap)
                when {
                    i < filled -> drawRect(colors.primary, Offset(x, 0f), Size(w, size.height))
                    i == filled -> drawRect(colors.secondary, Offset(x, 0f), Size(w, size.height))
                    else -> drawRect(colors.primary.copy(alpha = 0.18f), Offset(x, size.height * 0.35f), Size(w, size.height * 0.3f))
                }
            }
        }
    }
}

/** Brutal: barra gruesa con borde negro y relleno rojo. */
@Composable
fun BrutalSeekBar(state: PlayerState, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    SeekArea(state, modifier) { fraction, _ ->
        Canvas(Modifier.fillMaxSize()) {
            val border = 2.dp.toPx()
            drawRect(colors.surfaceContainer)
            drawRect(colors.primary, size = Size(size.width * fraction, size.height))
            drawRect(colors.onBackground, style = Stroke(border * 2))
            drawLine(colors.onBackground, Offset(size.width * fraction, 0f), Offset(size.width * fraction, size.height), border * 2)
        }
    }
}

/** Material You: barra redondeada simple. */
@Composable
fun SimpleSeekBar(state: PlayerState, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    SeekArea(state, modifier) { fraction, dragging ->
        Canvas(Modifier.fillMaxSize()) {
            val cy = size.height / 2
            val h = if (dragging) 12.dp.toPx() else 8.dp.toPx()
            drawRoundRect(colors.surfaceVariant, Offset(0f, cy - h / 2), Size(size.width, h), CornerRadius(h / 2))
            drawRoundRect(colors.primary, Offset(0f, cy - h / 2), Size((size.width * fraction).coerceAtLeast(h), h), CornerRadius(h / 2))
        }
    }
}
