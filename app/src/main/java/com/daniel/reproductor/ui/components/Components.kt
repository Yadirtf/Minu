package com.daniel.reproductor.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.daniel.reproductor.data.db.DownloadState
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.ui.common.formatDuration
import com.daniel.reproductor.ui.theme.LocalAppStyle
import com.daniel.reproductor.ui.theme.StyleId

data class MenuAction(val label: String, val icon: ImageVector? = null, val onClick: () -> Unit)

/** Portada con la forma del estilo (círculo en Vinilo, cuadrada en Brutal/Terminal…). */
@Composable
fun ArtworkImage(model: Any?, modifier: Modifier = Modifier, shape: Shape = LocalAppStyle.current.artShape) {
    val style = LocalAppStyle.current
    val bordered = if (style.border > 0.dp) Modifier.border(style.border, MaterialTheme.colorScheme.outline, shape) else Modifier
    SubcomposeAsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant).then(bordered),
        error = { PlaceholderArt() },
        loading = { PlaceholderArt() },
    )
}

@Composable
private fun PlaceholderArt() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
    }
}

/** Barras de ecualizador animadas para la canción que suena. */
@Composable
fun EqualizerBars(playing: Boolean, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val transition = rememberInfiniteTransition(label = "eq")
    val heights = listOf(420, 300, 520).map { duration ->
        transition.animateFloat(
            initialValue = 0.25f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(duration), RepeatMode.Reverse), label = "bar",
        )
    }
    Row(modifier.size(18.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        heights.forEach { h ->
            Box(
                Modifier.weight(1f).fillMaxHeight(if (playing) h.value else 0.3f)
                    .background(color, RoundedCornerShape(1.dp))
            )
        }
    }
}

/** Título grande de pantalla, con el carácter de cada estilo. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    val style = LocalAppStyle.current
    val colors = MaterialTheme.colorScheme
    val textStyle: TextStyle = when (style.id) {
        StyleId.NEON -> MaterialTheme.typography.headlineLarge.copy(
            color = colors.onBackground,
            shadow = Shadow(colors.primary.copy(alpha = 0.7f), Offset.Zero, 28f),
        )
        StyleId.VINYL -> MaterialTheme.typography.headlineLarge.copy(fontStyle = FontStyle.Italic, color = colors.onBackground)
        StyleId.BRUTAL -> MaterialTheme.typography.headlineLarge.copy(color = colors.onBackground)
        StyleId.TERMINAL -> MaterialTheme.typography.headlineMedium.copy(color = colors.primary)
        else -> MaterialTheme.typography.headlineLarge.copy(color = colors.onBackground)
    }
    Column(modifier) {
        val shown = when (style.id) {
            StyleId.TERMINAL -> "> ${text.uppercase()}_"
            StyleId.BRUTAL -> text.uppercase()
            else -> text
        }
        Text(shown, style = textStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Text(
                style.label(subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        if (style.id == StyleId.BRUTAL) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).background(colors.onBackground))
        }
    }
}

/** Botón píldora principal (Reproducir / Aleatorio). */
@Composable
fun PillButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
) {
    val style = LocalAppStyle.current
    val colors = MaterialTheme.colorScheme
    val shape: Shape = when (style.id) {
        StyleId.BRUTAL, StyleId.TERMINAL -> RoundedCornerShape(0.dp)
        StyleId.VINYL -> RoundedCornerShape(4.dp)
        else -> CircleShape
    }
    val container = if (primary) colors.primary else Color.Transparent
    val content = if (primary) colors.onPrimary else colors.onBackground
    val glow = if (style.glow && primary) {
        Modifier.shadow(14.dp, shape, ambientColor = colors.primary, spotColor = colors.primary)
    } else Modifier
    Surface(
        onClick = onClick,
        shape = shape,
        color = container,
        contentColor = content,
        border = if (primary) null else androidx.compose.foundation.BorderStroke(
            if (style.border > 0.dp) style.border else 1.dp, colors.outline,
        ),
        modifier = modifier.then(glow),
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(style.label(text), style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Halo de color detrás de un elemento (estilo Neón). */
fun Modifier.glow(color: Color, radius: Dp, alpha: Float = 0.55f): Modifier = drawBehind {
    val r = radius.toPx()
    drawCircle(
        brush = androidx.compose.ui.graphics.Brush.radialGradient(
            listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = size.maxDimension / 2 + r,
        ),
        radius = size.maxDimension / 2 + r,
    )
}

/** Fila de canción usada en Inicio, Playlists y Buscar. */
@Composable
fun SongRow(
    song: Song,
    thumbnail: Any?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    downloadProgress: Int? = null,
    actions: List<MenuAction> = emptyList(),
    tag: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val style = LocalAppStyle.current
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val highlight = if (isCurrent) colors.primary.copy(alpha = if (style.isDark) 0.10f else 0.12f) else Color.Transparent

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().background(highlight).clickable(onClick = onClick)
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                ArtworkImage(thumbnail, Modifier.size(52.dp))
                if (isCurrent) {
                    Box(Modifier.size(52.dp).clip(style.artShape).background(Color.Black.copy(alpha = 0.45f)))
                    EqualizerBars(isPlaying, color = Color.White)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (style.id == StyleId.TERMINAL && isCurrent) "▶ ${song.title}" else song.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrent) colors.primary else colors.onBackground,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DownloadBadge(song.downloadState, downloadProgress)
                    tag?.let { SmallTag(it, Modifier.padding(end = 6.dp)) }
                    Text(
                        "${song.uploader} · ${formatDuration(song.durationSec)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke()
            if (actions.isNotEmpty()) {
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Default.MoreVert, "Opciones", tint = colors.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        actions.forEach { action ->
                            DropdownMenuItem(
                                text = { Text(action.label) },
                                leadingIcon = action.icon?.let { { Icon(it, null) } },
                                onClick = { menu = false; action.onClick() },
                            )
                        }
                    }
                }
            }
        }
        if (style.id == StyleId.BRUTAL || style.id == StyleId.TERMINAL) {
            HorizontalDivider(
                thickness = if (style.id == StyleId.BRUTAL) 1.dp else 0.5.dp,
                color = colors.outline.copy(alpha = if (style.id == StyleId.BRUTAL) 1f else 0.6f),
            )
        }
    }
}

/** Etiqueta discreta junto al artista, como la "E" de Spotify: de dónde viene la canción o si es sugerida. */
@Composable
fun SmallTag(text: String, modifier: Modifier = Modifier) {
    val style = LocalAppStyle.current
    val colors = MaterialTheme.colorScheme
    val shape = when (style.id) {
        StyleId.BRUTAL, StyleId.TERMINAL -> RoundedCornerShape(0.dp)
        else -> RoundedCornerShape(3.dp)
    }
    Text(
        style.label(text),
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier
            .background(colors.onSurfaceVariant.copy(alpha = 0.16f), shape)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** Ícono pequeño junto al artista: descargada, en cola, descargando o fallida. */
@Composable
fun DownloadBadge(state: DownloadState, progress: Int?) {
    val colors = MaterialTheme.colorScheme
    val mod = Modifier.padding(end = 6.dp).size(14.dp)
    when (state) {
        DownloadState.NONE -> Unit
        DownloadState.DONE -> Icon(Icons.Default.DownloadDone, "Descargada", mod, tint = colors.primary)
        DownloadState.QUEUED -> Icon(Icons.Default.Schedule, "En cola", mod, tint = colors.onSurfaceVariant)
        DownloadState.FAILED -> Icon(Icons.Default.ErrorOutline, "Falló la descarga", mod, tint = colors.error)
        DownloadState.DOWNLOADING ->
            if (progress != null && progress > 0) {
                CircularProgressIndicator(progress = { progress / 100f }, modifier = mod, strokeWidth = 2.dp)
            } else {
                CircularProgressIndicator(mod, strokeWidth = 2.dp)
            }
    }
}

@Composable
fun OfflineBanner(text: String = "Sin conexión: solo se muestran las canciones descargadas") {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CloudOff, null, Modifier.size(18.dp))
            Spacer(Modifier.width(12.dp))
            Text(LocalAppStyle.current.label(text), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(56.dp), tint = colors.primary.copy(alpha = 0.7f))
        Spacer(Modifier.height(16.dp))
        Text(LocalAppStyle.current.label(title), style = MaterialTheme.typography.titleLarge, color = colors.onBackground)
        Spacer(Modifier.height(6.dp))
        Text(
            text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
