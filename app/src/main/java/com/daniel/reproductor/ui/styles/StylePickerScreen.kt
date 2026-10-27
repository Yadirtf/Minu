package com.daniel.reproductor.ui.styles

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.daniel.reproductor.ui.components.ArtworkImage
import com.daniel.reproductor.ui.components.ScreenTitle
import com.daniel.reproductor.ui.components.glow
import com.daniel.reproductor.ui.player.VinylDisc
import com.daniel.reproductor.ui.theme.AppStyle
import com.daniel.reproductor.ui.theme.AppStyles
import com.daniel.reproductor.ui.theme.LocalAppStyle
import com.daniel.reproductor.ui.theme.ReproductorTheme
import com.daniel.reproductor.ui.theme.StyleId

/** Elegir estilo: cada tarjeta se dibuja con su propio estilo para verlo antes de aplicarlo. */
@Composable
fun StylePickerScreen(current: StyleId, artwork: Any?, onPick: (StyleId) -> Unit, onBack: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
                ScreenTitle("Estilos", subtitle = "Toca uno para aplicarlo")
            }
        }
        items(AppStyles.all, key = { it.id }) { style ->
            ReproductorTheme(style) {
                StyleCard(LocalAppStyle.current, selected = style.id == current, artwork = artwork, onClick = { onPick(style.id) })
            }
        }
        item { Spacer(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun StyleCard(style: AppStyle, selected: Boolean, artwork: Any?, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val borderWidth by animateDpAsState(if (selected) 3.dp else if (style.border > 0.dp) style.border else 1.dp, label = "border")
    Surface(
        onClick = onClick,
        shape = style.cardShape,
        color = c.background,
        border = BorderStroke(borderWidth, if (selected) c.primary else c.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            // Fondo característico
            Canvas(Modifier.matchParentSize()) {
                when (style.id) {
                    StyleId.NEON -> drawCircle(
                        Brush.radialGradient(listOf(c.primary.copy(alpha = 0.25f), Color.Transparent), Offset(size.width, 0f), size.width * 0.7f),
                        size.width * 0.7f, Offset(size.width, 0f),
                    )
                    StyleId.AURORA -> {
                        drawCircle(Brush.radialGradient(listOf(c.primary.copy(alpha = 0.5f), Color.Transparent), Offset(size.width * 0.8f, 0f), size.width * 0.6f), size.width * 0.6f, Offset(size.width * 0.8f, 0f))
                        drawCircle(Brush.radialGradient(listOf(c.secondary.copy(alpha = 0.35f), Color.Transparent), Offset(0f, size.height), size.width * 0.5f), size.width * 0.5f, Offset(0f, size.height))
                    }
                    StyleId.TERMINAL -> {
                        var y = 0f
                        while (y < size.height) { drawLine(c.primary.copy(alpha = 0.05f), Offset(0f, y), Offset(size.width, y), 1f); y += 6f }
                    }
                    else -> Unit
                }
            }
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            when (style.id) {
                                StyleId.TERMINAL -> "> ${style.name.uppercase()}_"
                                StyleId.BRUTAL -> style.name.uppercase()
                                else -> style.name
                            },
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontStyle = if (style.id == StyleId.VINYL) FontStyle.Italic else FontStyle.Normal,
                            ),
                            color = if (style.id == StyleId.TERMINAL) c.primary else c.onBackground,
                        )
                        Text(style.tagline, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                    }
                    if (selected) Icon(Icons.Default.CheckCircle, "Estilo actual", tint = c.primary, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(16.dp))
                PreviewPlayer(style, artwork)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(c.primary, c.secondary, c.tertiary, c.onBackground).forEach {
                        Box(Modifier.size(18.dp).clip(if (style.cardShape == RoundedCornerShape(0.dp)) RectangleShape else CircleShape).background(it).border(1.dp, c.outline.copy(alpha = 0.4f), if (style.cardShape == RoundedCornerShape(0.dp)) RectangleShape else CircleShape))
                    }
                }
            }
        }
    }
}

/** Mini maqueta del reproductor con el carácter del estilo. */
@Composable
private fun PreviewPlayer(style: AppStyle, artwork: Any?) {
    val c = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        when (style.id) {
            StyleId.VINYL -> VinylDisc(artwork, 20f, Modifier.size(64.dp))
            StyleId.NEON -> Box(Modifier.size(64.dp).glow(c.primary, 10.dp, 0.4f)) { ArtworkImage(artwork, Modifier.size(64.dp)) }
            else -> ArtworkImage(artwork, Modifier.size(64.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(style.label("Canción de ejemplo"), style = MaterialTheme.typography.titleMedium, color = c.onBackground)
            Text("Artista", style = MaterialTheme.typography.bodySmall, color = if (style.id == StyleId.NEON || style.id == StyleId.BRUTAL) c.primary else c.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            PreviewSeek(style)
        }
        Spacer(Modifier.width(12.dp))
        val playShape = when (style.id) {
            StyleId.BRUTAL, StyleId.TERMINAL -> RectangleShape
            else -> CircleShape
        }
        Box(
            Modifier.size(48.dp).clip(playShape)
                .background(if (style.id == StyleId.NEON) Brush.linearGradient(listOf(c.primary, c.secondary)) else Brush.linearGradient(listOf(c.primary, c.primary)))
                .then(if (style.border > 0.dp) Modifier.border(style.border, c.outline, playShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.PlayArrow, null, tint = if (style.id == StyleId.NEON) Color.Black else c.onPrimary)
        }
    }
}

@Composable
private fun PreviewSeek(style: AppStyle) {
    val c = MaterialTheme.colorScheme
    val f = 0.42f
    Canvas(Modifier.fillMaxWidth().height(if (style.id == StyleId.NEON) 22.dp else 12.dp)) {
        when (style.id) {
            StyleId.NEON -> {
                val n = 30
                val w = size.width / n
                for (i in 0 until n) {
                    val h = size.height * (0.3f + 0.7f * kotlin.math.abs(kotlin.math.sin(i * 0.5f)))
                    val color = if (i < n * f) c.primary else c.onSurfaceVariant.copy(alpha = 0.3f)
                    drawRoundRect(color, Offset(i * w + 1, (size.height - h) / 2), Size(w - 2, h), CornerRadius(2f))
                }
            }
            StyleId.TERMINAL -> {
                val n = 20
                val w = size.width / n
                for (i in 0 until n) drawRect(if (i < n * f) c.primary else c.primary.copy(alpha = 0.18f), Offset(i * w + 1, 0f), Size(w - 2, size.height))
            }
            StyleId.BRUTAL -> {
                drawRect(c.primary, size = Size(size.width * f, size.height))
                drawRect(c.onBackground, style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
            }
            else -> {
                val cy = size.height / 2
                drawLine(c.onSurfaceVariant.copy(alpha = 0.3f), Offset(0f, cy), Offset(size.width, cy), 4f)
                drawLine(c.primary, Offset(0f, cy), Offset(size.width * f, cy), 6f)
                drawCircle(if (style.id == StyleId.AURORA) Color.White else c.primary, 12f, Offset(size.width * f, cy))
            }
        }
    }
}
