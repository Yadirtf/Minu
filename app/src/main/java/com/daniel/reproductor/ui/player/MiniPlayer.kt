package com.daniel.reproductor.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daniel.reproductor.ui.components.ArtworkImage
import com.daniel.reproductor.ui.theme.LocalAppStyle
import com.daniel.reproductor.ui.theme.StyleId

/** Barra con la canción actual, encima de la barra inferior. Al tocarla abre el reproductor completo. */
@Composable
fun MiniPlayer(state: PlayerState, onOpen: () -> Unit) {
    val style = LocalAppStyle.current
    val c = MaterialTheme.colorScheme
    val fraction = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f

    val floating = style.id == StyleId.AURORA || style.id == StyleId.MATERIAL
    val shape: Shape = when (style.id) {
        StyleId.AURORA -> RoundedCornerShape(22.dp)
        StyleId.MATERIAL -> RoundedCornerShape(16.dp)
        else -> RectangleShape
    }
    val container = when (style.id) {
        StyleId.NEON -> c.surfaceContainer
        StyleId.VINYL -> c.surfaceContainerHigh
        StyleId.AURORA -> c.primary.copy(alpha = 0.22f)
        StyleId.TERMINAL -> c.background
        StyleId.BRUTAL -> c.onBackground
        StyleId.MATERIAL -> c.secondaryContainer
    }
    val content = if (style.id == StyleId.BRUTAL) c.background else c.onSurface

    val outer = if (floating) Modifier.padding(horizontal = 10.dp, vertical = 6.dp) else Modifier
    val border = when (style.id) {
        StyleId.TERMINAL -> Modifier.border(1.dp, c.primary)
        StyleId.AURORA -> Modifier.border(1.dp, Color.White.copy(alpha = 0.15f), shape)
        else -> Modifier
    }

    Column(
        outer.fillMaxWidth()
            .then(if (floating) Modifier.shadow(8.dp, shape) else Modifier)
            .clip(shape).background(c.background).background(container).then(border)
            .clickable(onClick = onOpen),
    ) {
        // Progreso
        when (style.id) {
            StyleId.NEON -> Box(Modifier.fillMaxWidth().height(2.dp)) {
                Box(
                    Modifier.fillMaxWidth(fraction).height(2.dp)
                        .background(Brush.horizontalGradient(listOf(c.primary, c.secondary)))
                )
            }
            StyleId.BRUTAL -> Box(Modifier.fillMaxWidth().height(4.dp)) {
                Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(c.primary))
            }
            StyleId.AURORA, StyleId.MATERIAL -> Unit
            else -> Box(Modifier.fillMaxWidth().height(2.dp).background(c.primary.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxWidth(fraction).height(2.dp).background(c.primary))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (style.id) {
                StyleId.VINYL -> VinylDisc(state.metadata.artworkUri, rememberMiniSpin(state.isPlaying), Modifier.size(46.dp))
                StyleId.TERMINAL -> Text(if (state.isPlaying) "▶" else "■", color = c.primary, style = MaterialTheme.typography.titleLarge)
                else -> ArtworkImage(
                    state.metadata.artworkUri, Modifier.size(46.dp),
                    if (style.id == StyleId.NEON) CircleShape else style.artShape,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val title = state.metadata.title?.toString().orEmpty()
                Text(
                    if (style.id == StyleId.TERMINAL) "> $title" else if (style.id == StyleId.BRUTAL) title.uppercase() else title,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall, color = content,
                )
                Text(
                    state.error ?: state.metadata.artist?.toString().orEmpty(),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        state.error != null -> c.error
                        style.id == StyleId.BRUTAL -> c.primary
                        else -> c.onSurfaceVariant
                    },
                )
            }
            Box(contentAlignment = Alignment.Center) {
                if (state.isBuffering) CircularProgressIndicator(Modifier.size(40.dp), strokeWidth = 2.dp, color = c.primary)
                if (style.id == StyleId.AURORA && floating) {
                    CircularProgressIndicator(progress = { fraction }, modifier = Modifier.size(40.dp), strokeWidth = 2.dp, color = c.primary, trackColor = Color.White.copy(alpha = 0.15f))
                }
                IconButton(onClick = state::togglePlay) {
                    Icon(
                        if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Reproducir o pausar",
                        tint = if (style.id == StyleId.BRUTAL) c.background else c.primary,
                    )
                }
            }
            IconButton(onClick = state::next, enabled = state.hasNext) {
                Icon(Icons.Default.SkipNext, "Siguiente", tint = content.copy(alpha = if (state.hasNext) 1f else 0.3f))
            }
        }
        if (style.id == StyleId.MATERIAL) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 6.dp).height(3.dp).clip(CircleShape).background(c.onSecondaryContainer.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxWidth(fraction).height(3.dp).background(c.primary))
            }
        }
    }
}

@Composable
private fun rememberMiniSpin(playing: Boolean): Float {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        while (playing) {
            angle.animateTo(angle.value + 360f, tween(4000, easing = LinearEasing))
            angle.snapTo(angle.value % 360f)
        }
    }
    return angle.value
}
