package com.daniel.reproductor.ui.player

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.daniel.reproductor.ui.common.formatDuration
import com.daniel.reproductor.ui.components.ArtworkImage
import com.daniel.reproductor.ui.components.glow
import com.daniel.reproductor.ui.library.SaveAction
import com.daniel.reproductor.ui.library.SaveToPlaylistButton
import com.daniel.reproductor.ui.library.saveLabel
import com.daniel.reproductor.ui.theme.LocalAppStyle
import com.daniel.reproductor.ui.theme.StyleId

/**
 * Reproductor a pantalla completa: cada estilo tiene su propio diseño. Deslizar hacia abajo lo cierra.
 * [save] agrega la canción que suena a una playlist (útil sobre todo con lo que viene de YouTube).
 */
@Composable
fun FullPlayerScreen(state: PlayerState, onClose: () -> Unit, save: SaveAction) {
    val style = LocalAppStyle.current
    val closeOnSwipe = Modifier.pointerInput(Unit) {
        var total = 0f
        detectVerticalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = { if (total > 140.dp.toPx()) onClose() },
        ) { _, dy -> total += dy }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).then(closeOnSwipe)) {
        when (style.id) {
            StyleId.NEON -> NeonPlayer(state, onClose, save)
            StyleId.VINYL -> VinylPlayer(state, onClose, save)
            StyleId.AURORA -> AuroraPlayer(state, onClose, save)
            StyleId.TERMINAL -> TerminalPlayer(state, onClose, save)
            StyleId.BRUTAL -> BrutalPlayer(state, onClose, save)
            StyleId.MATERIAL -> MaterialPlayer(state, onClose, save)
        }
    }
}

// ---------- Piezas comunes ----------

private val PlayerState.title get() = metadata.title?.toString().orEmpty()
private val PlayerState.artist get() = metadata.artist?.toString().orEmpty()
private val PlayerState.queueLabel get() = if (itemCount > 0) "${currentIndex + 1} / $itemCount" else ""

@Composable
private fun TopBar(onClose: () -> Unit, label: String, save: SaveAction, tint: Color = MaterialTheme.colorScheme.onBackground) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, "Cerrar", tint = tint, modifier = Modifier.size(32.dp)) }
        Text(
            label, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.2.em), color = tint.copy(alpha = 0.7f),
        )
        SaveToPlaylistButton(save, tint)
    }
}

@Composable
private fun ControlIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color,
    size: Dp = 32.dp,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size + 24.dp)) {
        Icon(icon, description, tint = if (enabled) tint else tint.copy(alpha = 0.3f), modifier = Modifier.size(size))
    }
}

@Composable
private fun RepeatIcon(state: PlayerState) =
    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat

@Composable
private fun TimeRow(state: PlayerState, color: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatDuration(state.positionMs / 1000), style = MaterialTheme.typography.labelMedium, color = color)
        Text(formatDuration(state.durationMs / 1000), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun ErrorLine(state: PlayerState) {
    state.error?.let {
        Text(
            it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}

/** Ángulo que sigue girando mientras suena y se detiene donde quedó al pausar. */
@Composable
private fun rememberSpin(playing: Boolean, secondsPerTurn: Int = 8): Float {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        while (playing) {
            angle.animateTo(angle.value + 360f, tween(secondsPerTurn * 1000, easing = LinearEasing))
            angle.snapTo(angle.value % 360f)
        }
    }
    return angle.value
}

// ---------- NEÓN (inspirado en Poweramp) ----------

@Composable
private fun NeonPlayer(state: PlayerState, onClose: () -> Unit, save: SaveAction) {
    val c = MaterialTheme.colorScheme
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0.35f, 0.65f, infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "glow",
    )
    Box(Modifier.fillMaxSize()) {
        // Halo de color detrás de todo
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(listOf(c.primary.copy(alpha = 0.22f), Color.Transparent), Offset(size.width * 0.5f, size.height * 0.32f), size.width),
                radius = size.width, center = Offset(size.width * 0.5f, size.height * 0.32f),
            )
            drawCircle(
                Brush.radialGradient(listOf(c.secondary.copy(alpha = 0.14f), Color.Transparent), Offset(size.width, size.height * 0.85f), size.width * 0.8f),
                radius = size.width * 0.8f, center = Offset(size.width, size.height * 0.85f),
            )
        }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(onClose, "REPRODUCIENDO · ${state.queueLabel}", save)
            Spacer(Modifier.weight(0.6f))
            Box(Modifier.fillMaxWidth(0.86f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxSize(0.9f).glow(c.primary, 40.dp, alpha = if (state.isPlaying) pulse else 0.25f))
                ArtworkImage(state.metadata.artworkUri, Modifier.fillMaxSize().border(1.dp, c.primary.copy(alpha = 0.5f), RoundedCornerShape(10.dp)))
            }
            Spacer(Modifier.weight(0.5f))
            Text(
                state.title, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold, shadow = Shadow(c.primary.copy(alpha = 0.6f), Offset.Zero, 18f),
                ),
            )
            Text(state.artist.uppercase(), color = c.primary, style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.15.em))
            ErrorLine(state)
            Spacer(Modifier.height(24.dp))
            WaveSeekBar(state, Modifier.fillMaxWidth().height(56.dp))
            TimeRow(state, c.onSurfaceVariant, Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ControlIcon(Icons.Default.Shuffle, "Aleatorio", state::toggleShuffle, if (state.shuffle) c.secondary else c.onSurfaceVariant, 24.dp)
                ControlIcon(Icons.Default.SkipPrevious, "Anterior", state::previous, c.onBackground, 38.dp)
                Box(
                    Modifier.size(84.dp).glow(c.primary, 18.dp, if (state.isPlaying) pulse else 0.3f)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(c.primary, c.secondary)))
                        .clickable(onClick = state::togglePlay),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isBuffering) CircularProgressIndicator(Modifier.size(78.dp), color = Color.White, strokeWidth = 2.dp)
                    Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Reproducir o pausar", tint = Color.Black, modifier = Modifier.size(44.dp))
                }
                ControlIcon(Icons.Default.SkipNext, "Siguiente", state::next, c.onBackground, 38.dp, state.hasNext)
                ControlIcon(RepeatIcon(state), "Repetir", state::cycleRepeat, if (state.repeatMode != Player.REPEAT_MODE_OFF) c.secondary else c.onSurfaceVariant, 24.dp)
            }
            Spacer(Modifier.weight(0.4f))
        }
    }
}

// ---------- VINILO ----------

@Composable
private fun VinylPlayer(state: PlayerState, onClose: () -> Unit, save: SaveAction) {
    val c = MaterialTheme.colorScheme
    val spin = rememberSpin(state.isPlaying, secondsPerTurn = 4)
    val armAngle by animateFloatAsState(if (state.isPlaying) 0f else -16f, tween(700), label = "arm")
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.surfaceContainerLow, c.background, c.surfaceContainer)))
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(onClose, "— lado A · ${state.queueLabel} —", save)
        Spacer(Modifier.weight(0.5f))
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            VinylDisc(state.metadata.artworkUri, spin, Modifier.fillMaxSize(0.92f).align(Alignment.CenterStart))
            // Brazo del tocadiscos
            Canvas(Modifier.fillMaxSize()) {
                val pivot = Offset(size.width * 0.93f, size.height * 0.08f)
                rotate(armAngle, pivot) {
                    drawCircle(Color(0xFF8E8370), 14.dp.toPx(), pivot)
                    drawCircle(Color(0xFFD9D1C2), 8.dp.toPx(), pivot)
                    val end = Offset(size.width * 0.86f, size.height * 0.56f)
                    drawLine(Color(0xFFBDB4A3), pivot, end, 6.dp.toPx(), StrokeCap.Round)
                    drawLine(Color(0xFF6E6555), end, Offset(end.x - 14.dp.toPx(), end.y + 22.dp.toPx()), 12.dp.toPx(), StrokeCap.Round)
                }
            }
        }
        Spacer(Modifier.weight(0.4f))
        Text(
            state.title, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.headlineMedium.copy(fontStyle = FontStyle.Italic), color = c.onBackground,
        )
        Text(state.artist, style = MaterialTheme.typography.titleMedium, color = c.primary)
        ErrorLine(state)
        Spacer(Modifier.height(22.dp))
        TickSeekBar(state, Modifier.fillMaxWidth().height(32.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(state.positionMs / 1000), style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = c.onSurfaceVariant)
            Text(formatDuration(state.durationMs / 1000), style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic), color = c.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ControlIcon(Icons.Default.Shuffle, "Aleatorio", state::toggleShuffle, if (state.shuffle) c.primary else c.onSurfaceVariant.copy(alpha = 0.6f), 22.dp)
            ControlIcon(Icons.Default.SkipPrevious, "Anterior", state::previous, c.onBackground, 34.dp)
            Surface(
                onClick = state::togglePlay, shape = CircleShape, color = c.primary, contentColor = c.onPrimary,
                border = BorderStroke(4.dp, c.primaryContainer), modifier = Modifier.size(78.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (state.isBuffering) CircularProgressIndicator(Modifier.size(70.dp), color = c.onPrimary, strokeWidth = 2.dp)
                    Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Reproducir o pausar", Modifier.size(38.dp))
                }
            }
            ControlIcon(Icons.Default.SkipNext, "Siguiente", state::next, c.onBackground, 34.dp, state.hasNext)
            ControlIcon(RepeatIcon(state), "Repetir", state::cycleRepeat, if (state.repeatMode != Player.REPEAT_MODE_OFF) c.primary else c.onSurfaceVariant.copy(alpha = 0.6f), 22.dp)
        }
        Spacer(Modifier.weight(0.3f))
    }
}

/** Disco de vinilo con surcos y la portada como etiqueta central. */
@Composable
fun VinylDisc(art: Any?, rotation: Float, modifier: Modifier) {
    Box(modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().shadow(16.dp, CircleShape).graphicsLayer { rotationZ = rotation }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2
                drawCircle(Color(0xFF141210), r)
                var f = 0.42f
                while (f < 0.98f) {
                    drawCircle(Color.White.copy(alpha = if ((f * 100).toInt() % 7 == 0) 0.07f else 0.035f), r * f, style = Stroke(1f))
                    f += 0.018f
                }
            }
            ArtworkImage(art, Modifier.fillMaxSize(0.38f), CircleShape)
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(0xFFF3E9D7)))
        }
        // Brillo fijo (no gira) para que se note el movimiento
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.sweepGradient(
                    0f to Color.Transparent, 0.12f to Color.White.copy(alpha = 0.10f), 0.22f to Color.Transparent,
                    0.55f to Color.Transparent, 0.62f to Color.White.copy(alpha = 0.07f), 0.72f to Color.Transparent,
                ),
                size.minDimension / 2,
            )
        }
    }
}

// ---------- AURORA ----------

@Composable
private fun AuroraPlayer(state: PlayerState, onClose: () -> Unit, save: SaveAction) {
    val c = MaterialTheme.colorScheme
    val t by rememberInfiniteTransition(label = "aurora").animateFloat(
        0f, 1f, infiniteRepeatable(tween(12000, easing = LinearEasing), RepeatMode.Reverse), label = "t",
    )
    Box(Modifier.fillMaxSize()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AsyncImage(
                state.metadata.artworkUri, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(70.dp).alpha(0.55f),
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val a = Offset(size.width * (0.2f + 0.6f * t), size.height * 0.2f)
            val b = Offset(size.width * (0.9f - 0.7f * t), size.height * (0.55f + 0.2f * t))
            drawCircle(Brush.radialGradient(listOf(c.primary.copy(alpha = 0.45f), Color.Transparent), a, size.width * 0.8f), size.width * 0.8f, a)
            drawCircle(Brush.radialGradient(listOf(c.secondary.copy(alpha = 0.30f), Color.Transparent), b, size.width * 0.7f), size.width * 0.7f, b)
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
        }
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopBar(onClose, "Reproduciendo · ${state.queueLabel}", save, Color.White)
            Spacer(Modifier.weight(0.5f))
            ArtworkImage(
                state.metadata.artworkUri,
                Modifier.fillMaxWidth(0.88f).aspectRatio(1f).shadow(30.dp, RoundedCornerShape(28.dp), spotColor = c.primary),
                RoundedCornerShape(28.dp),
            )
            Spacer(Modifier.weight(0.5f))
            // Panel de cristal
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(28.dp))
                    .padding(20.dp),
            ) {
                Text(state.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
                Text(state.artist, color = Color.White.copy(alpha = 0.7f), maxLines = 1, style = MaterialTheme.typography.bodyLarge)
                ErrorLine(state)
                Spacer(Modifier.height(14.dp))
                GlassSeekBar(state, Modifier.fillMaxWidth().height(28.dp))
                TimeRow(state, Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    ControlIcon(Icons.Default.Shuffle, "Aleatorio", state::toggleShuffle, if (state.shuffle) c.primary else Color.White.copy(alpha = 0.6f), 22.dp)
                    ControlIcon(Icons.Default.SkipPrevious, "Anterior", state::previous, Color.White, 34.dp)
                    Box(
                        Modifier.size(72.dp).clip(CircleShape).background(Color.White).clickable(onClick = state::togglePlay),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.isBuffering) CircularProgressIndicator(Modifier.size(66.dp), color = c.primary, strokeWidth = 2.dp)
                        Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Reproducir o pausar", tint = Color(0xFF111118), modifier = Modifier.size(36.dp))
                    }
                    ControlIcon(Icons.Default.SkipNext, "Siguiente", state::next, Color.White, 34.dp, state.hasNext)
                    ControlIcon(RepeatIcon(state), "Repetir", state::cycleRepeat, if (state.repeatMode != Player.REPEAT_MODE_OFF) c.primary else Color.White.copy(alpha = 0.6f), 22.dp)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// ---------- TERMINAL ----------

private val greenPhosphor = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.05f, 0.1f, 0.02f, 0f, 0f,
            0.30f, 0.60f, 0.12f, 0f, 10f,
            0.03f, 0.08f, 0.02f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)

@Composable
private fun TerminalPlayer(state: PlayerState, onClose: () -> Unit, save: SaveAction) {
    val c = MaterialTheme.colorScheme
    val cursor by rememberInfiniteTransition(label = "cursor").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "blink",
    )
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "[X]", color = c.secondary, style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.clickable(onClick = onClose).padding(8.dp),
                )
                Text("NOW_PLAYING.EXE", style = MaterialTheme.typography.titleLarge, color = c.primary, modifier = Modifier.weight(1f))
                Text("TRK ${state.queueLabel}", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().aspectRatio(1f).border(1.dp, c.primary).padding(10.dp)) {
                AsyncImage(
                    state.metadata.artworkUri, null, contentScale = ContentScale.Crop, colorFilter = greenPhosphor,
                    modifier = Modifier.fillMaxSize(),
                )
                CornerBrackets(c.primary)
            }
            Spacer(Modifier.height(16.dp))
            Text("> TITLE : ${state.title}", color = c.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text("> ARTIST: ${state.artist}", color = c.onSurfaceVariant, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
            Text(
                "> STATUS: " + when {
                    state.error != null -> "ERROR · ${state.error}"
                    state.isBuffering -> "LOADING..."
                    state.isPlaying -> "PLAYING"
                    else -> "PAUSED"
                } + if (cursor < 0.5f) "█" else " ",
                color = if (state.error != null) c.error else c.secondary, style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${formatDuration(state.positionMs / 1000)} / ${formatDuration(state.durationMs / 1000)}",
                style = MaterialTheme.typography.displayMedium.copy(shadow = Shadow(c.primary.copy(alpha = 0.6f), Offset.Zero, 16f)),
                color = c.primary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
            )
            BlockSeekBar(state, Modifier.fillMaxWidth().height(22.dp))
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TerminalButton("|<<", state::previous)
                TerminalButton(if (state.isPlaying) " || " else " |> ", state::togglePlay, primary = true)
                TerminalButton(">>|", state::next, enabled = state.hasNext)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TerminalButton("SHUF:" + if (state.shuffle) "ON " else "OFF", state::toggleShuffle, small = true)
                TerminalButton(if (save.saved) "SAVED" else "+LIST", save.onClick, small = true)
                TerminalButton(
                    "REP:" + when (state.repeatMode) { Player.REPEAT_MODE_ONE -> "ONE"; Player.REPEAT_MODE_ALL -> "ALL"; else -> "OFF" },
                    state::cycleRepeat, small = true,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        Scanlines()
    }
}

@Composable
private fun TerminalButton(text: String, onClick: () -> Unit, primary: Boolean = false, enabled: Boolean = true, small: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Text(
        "[$text]",
        style = if (small) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
        color = when {
            !enabled -> c.onSurfaceVariant.copy(alpha = 0.4f)
            primary -> c.onPrimary
            else -> c.primary
        },
        modifier = Modifier
            .background(if (primary) c.primary else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun CornerBrackets(color: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val l = 22.dp.toPx()
        val w = 3.dp.toPx()
        listOf(Offset.Zero to Offset(1f, 1f), Offset(size.width, 0f) to Offset(-1f, 1f),
            Offset(0f, size.height) to Offset(1f, -1f), Offset(size.width, size.height) to Offset(-1f, -1f),
        ).forEach { (p, d) ->
            drawLine(color, p, Offset(p.x + l * d.x, p.y), w)
            drawLine(color, p, Offset(p.x, p.y + l * d.y), w)
        }
    }
}

/** Líneas horizontales y viñeta para imitar una pantalla CRT. */
@Composable
fun Scanlines() {
    Canvas(Modifier.fillMaxSize()) {
        val step = 3.dp.toPx()
        var y = 0f
        while (y < size.height) {
            drawLine(Color.Black.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), 1f)
            y += step
        }
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)), center, size.maxDimension * 0.75f))
    }
}

// ---------- BRUTAL ----------

@Composable
private fun BrutalPlayer(state: PlayerState, onClose: () -> Unit, save: SaveAction) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "← CERRAR", style = MaterialTheme.typography.labelLarge, color = c.onBackground,
                modifier = Modifier.clickable(onClick = onClose).border(2.dp, c.onBackground).padding(horizontal = 10.dp, vertical = 6.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (state.itemCount > 0) "%02d/%02d".format(state.currentIndex + 1, state.itemCount) else "",
                style = MaterialTheme.typography.headlineMedium, color = c.primary,
            )
            Spacer(Modifier.width(12.dp))
            BrutalSquare(
                if (save.saved) Icons.Default.Check else Icons.Default.Add, saveLabel(save.saved), save.onClick,
                active = save.saved, size = 44.dp,
            )
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth(0.92f).aspectRatio(1f)) {
            Box(Modifier.fillMaxSize().offset(10.dp, 10.dp).background(c.onBackground))
            ArtworkImage(state.metadata.artworkUri, Modifier.fillMaxSize(), RectangleShape)
        }
        Spacer(Modifier.height(24.dp))
        Text(
            state.title.uppercase(), maxLines = 3, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 34.sp, lineHeight = 34.sp),
            color = c.onBackground,
        )
        Text(state.artist.uppercase(), style = MaterialTheme.typography.titleMedium, color = c.primary)
        ErrorLine(state)
        Spacer(Modifier.weight(1f))
        BrutalSeekBar(state, Modifier.fillMaxWidth().height(22.dp))
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(state.positionMs / 1000), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Text(formatDuration(state.durationMs / 1000), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            BrutalSquare(Icons.Default.Shuffle, "Aleatorio", state::toggleShuffle, active = state.shuffle, size = 52.dp)
            BrutalSquare(Icons.Default.SkipPrevious, "Anterior", state::previous, size = 52.dp)
            Box(Modifier.weight(1f).height(72.dp)) {
                Box(Modifier.fillMaxSize().offset(6.dp, 6.dp).background(c.onBackground))
                Box(
                    Modifier.fillMaxSize().background(c.primary).border(2.dp, c.onBackground).clickable(onClick = state::togglePlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Reproducir o pausar", tint = c.onPrimary, modifier = Modifier.size(40.dp))
                }
            }
            BrutalSquare(Icons.Default.SkipNext, "Siguiente", state::next, enabled = state.hasNext, size = 52.dp)
            BrutalSquare(RepeatIcon(state), "Repetir", state::cycleRepeat, active = state.repeatMode != Player.REPEAT_MODE_OFF, size = 52.dp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BrutalSquare(icon: ImageVector, description: String, onClick: () -> Unit, active: Boolean = false, enabled: Boolean = true, size: Dp) {
    val c = MaterialTheme.colorScheme
    Box(
        Modifier.size(size).background(if (active) c.onBackground else c.surfaceContainer).border(2.dp, c.onBackground)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = when { active -> c.background; enabled -> c.onBackground; else -> c.onBackground.copy(alpha = 0.3f) })
    }
}

// ---------- MATERIAL YOU ----------

@Composable
private fun MaterialPlayer(state: PlayerState, onClose: () -> Unit, save: SaveAction) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.primaryContainer, c.background)))
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TopBar(onClose, "Reproduciendo · ${state.queueLabel}", save)
        Spacer(Modifier.weight(0.5f))
        ArtworkImage(state.metadata.artworkUri, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(28.dp))
        Spacer(Modifier.weight(0.5f))
        Text(state.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(state.artist, style = MaterialTheme.typography.titleMedium, color = c.onSurfaceVariant)
        ErrorLine(state)
        Spacer(Modifier.height(20.dp))
        SimpleSeekBar(state, Modifier.fillMaxWidth().height(28.dp))
        TimeRow(state, c.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ControlIcon(Icons.Default.Shuffle, "Aleatorio", state::toggleShuffle, if (state.shuffle) c.primary else c.onSurfaceVariant, 24.dp)
            ControlIcon(Icons.Default.SkipPrevious, "Anterior", state::previous, c.onBackground, 36.dp)
            Surface(onClick = state::togglePlay, shape = RoundedCornerShape(24.dp), color = c.primary, contentColor = c.onPrimary, modifier = Modifier.size(width = 96.dp, height = 72.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Reproducir o pausar", Modifier.size(36.dp))
                }
            }
            ControlIcon(Icons.Default.SkipNext, "Siguiente", state::next, c.onBackground, 36.dp, state.hasNext)
            ControlIcon(RepeatIcon(state), "Repetir", state::cycleRepeat, if (state.repeatMode != Player.REPEAT_MODE_OFF) c.primary else c.onSurfaceVariant, 24.dp)
        }
        Spacer(Modifier.weight(0.3f))
    }
}
