package com.daniel.reproductor.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daniel.reproductor.data.ForYou
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.ui.components.ArtworkImage
import com.daniel.reproductor.ui.components.EqualizerBars
import com.daniel.reproductor.ui.theme.LocalAppStyle

/** Lo que se puede hacer con una canción recomendada desde su menú ⋮. */
class DiscoverActions(
    val play: (songs: List<Song>, index: Int) -> Unit,
    val playNext: (Song) -> Unit,
    val addToPlaylist: (Song) -> Unit,
)

/** Título de sección al estilo Spotify: una línea chica que explica y el nombre grande debajo. */
@Composable
private fun SectionHeader(kicker: String, title: String, modifier: Modifier = Modifier) {
    val style = LocalAppStyle.current
    Column(modifier.padding(horizontal = 20.dp)) {
        Text(kicker, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            style.label(title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/**
 * "Escuchar ahora": columnas de tres canciones que se pasan de lado, dejando ver un poco de la siguiente.
 * Tocar una arranca una sesión con esa y las que le siguen.
 */
@Composable
fun ForYouSection(
    forYou: ForYou,
    currentVideoId: String?,
    isPlaying: Boolean,
    actions: DiscoverActions,
    onRetry: () -> Unit,
) {
    Column(Modifier.padding(top = 8.dp, bottom = 16.dp)) {
        SectionHeader("Explora una sesión basada en tus gustos musicales", "Escuchar ahora")
        Spacer(Modifier.height(12.dp))
        when {
            forYou.songs.isNotEmpty() -> {
                val columns = remember(forYou.songs) { forYou.songs.chunked(ROWS_PER_COLUMN) }
                val listState = rememberLazyListState()
                LazyRow(
                    state = listState,
                    flingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Start),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    itemsIndexed(columns) { col, songs ->
                        Column(Modifier.fillParentMaxWidth(COLUMN_WIDTH)) {
                            songs.forEachIndexed { row, song ->
                                val index = col * ROWS_PER_COLUMN + row
                                CompactSongRow(
                                    song = song,
                                    isCurrent = song.videoId == currentVideoId,
                                    isPlaying = isPlaying,
                                    onClick = { actions.play(forYou.songs, index) },
                                    onPlayNext = { actions.playNext(song) },
                                    onAddToPlaylist = { actions.addToPlaylist(song) },
                                )
                            }
                        }
                    }
                }
            }
            forYou.loading -> Column(Modifier.padding(horizontal = 12.dp)) { repeat(ROWS_PER_COLUMN) { SkeletonRow() } }
            forYou.error != null -> Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    forYou.error, Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry) { Text(LocalAppStyle.current.label("Reintentar")) }
            }
            else -> Text(
                "Escucha o guarda algunas canciones y aquí aparecerá música elegida para ti.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

/** "Escuchado recientemente": portadas grandes en fila, como los atajos de Spotify. */
@Composable
fun RecentSection(songs: List<Song>, actions: DiscoverActions) {
    if (songs.isEmpty()) return
    Column(Modifier.padding(bottom = 16.dp)) {
        SectionHeader("Vuelve a lo que te gustó", "Escuchado recientemente")
        Spacer(Modifier.height(12.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            itemsIndexed(songs, key = { _, s -> s.videoId }) { index, song ->
                Column(Modifier.width(132.dp).clip(RoundedCornerShape(6.dp)).clickable { actions.play(songs, index) }) {
                    ArtworkImage(song.thumbnailUrl, Modifier.size(132.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        song.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        song.uploader, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactSongRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    val style = LocalAppStyle.current
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick)
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            ArtworkImage(song.thumbnailUrl, Modifier.size(56.dp))
            if (isCurrent) {
                Box(Modifier.size(56.dp).clip(style.artShape).background(Color.Black.copy(alpha = 0.45f)))
                EqualizerBars(isPlaying, color = Color.White)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) colors.primary else colors.onBackground,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.uploader,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Opciones", tint = colors.onSurfaceVariant) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Reproducir a continuación") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                    onClick = { menu = false; onPlayNext() },
                )
                DropdownMenuItem(
                    text = { Text("Agregar a un álbum") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                    onClick = { menu = false; onAddToPlaylist() },
                )
            }
        }
    }
}

/** Fila gris mientras llegan las recomendaciones, para que la sección no salte al aparecer. */
@Composable
private fun SkeletonRow() {
    val tone = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(56.dp).clip(LocalAppStyle.current.artShape).background(tone))
        Spacer(Modifier.width(14.dp))
        Column {
            Box(Modifier.width(160.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(tone))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.width(100.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).background(tone))
        }
    }
}

private const val ROWS_PER_COLUMN = 3
private const val COLUMN_WIDTH = 0.88f
