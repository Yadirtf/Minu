package com.daniel.reproductor.ui.playlists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daniel.reproductor.data.db.Playlist
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.ui.common.ConfirmDialog
import com.daniel.reproductor.ui.common.TextInputDialog
import com.daniel.reproductor.ui.components.ArtworkImage
import com.daniel.reproductor.ui.components.EmptyState
import com.daniel.reproductor.ui.components.PillButton
import com.daniel.reproductor.ui.components.ScreenTitle
import com.daniel.reproductor.ui.library.LibraryViewModel
import com.daniel.reproductor.ui.profile.ProfileButton
import com.daniel.reproductor.ui.theme.LocalAppStyle

private sealed interface PlaylistDialog {
    data object Create : PlaylistDialog
    data class Rename(val playlist: Playlist) : PlaylistDialog
    data class Delete(val playlist: Playlist) : PlaylistDialog
}

/** Playlists en cuadrícula con portada tipo mosaico. */
@Composable
fun PlaylistsScreen(lib: LibraryViewModel, onOpenPlaylist: (Long) -> Unit) {
    val playlists by lib.playlists.collectAsStateWithLifecycle()
    val covers by lib.covers.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<PlaylistDialog?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 156.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, bottom = 24.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
        ),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(start = 4.dp, top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileButton(Modifier.padding(end = 14.dp))
                    ScreenTitle("Playlists", subtitle = "${playlists.size} playlists")
                }
                PillButton(
                    "Nueva playlist", Icons.Default.Add, { dialog = PlaylistDialog.Create },
                    Modifier.padding(top = 16.dp).fillMaxWidth(),
                )
            }
        }
        if (playlists.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(Icons.AutoMirrored.Filled.QueueMusic, "Sin playlists", "Crea una para organizar tus canciones.")
            }
        }
        items(playlists, key = { it.playlist.id }) { item ->
            PlaylistCard(
                playlist = item.playlist,
                songCount = item.songCount,
                cover = covers[item.playlist.id].orEmpty(),
                thumbnail = lib::thumbnail,
                onClick = { onOpenPlaylist(item.playlist.id) },
                onRename = { dialog = PlaylistDialog.Rename(item.playlist) },
                onDelete = { dialog = PlaylistDialog.Delete(item.playlist) },
            )
        }
    }

    when (val d = dialog) {
        null -> Unit
        PlaylistDialog.Create -> TextInputDialog(
            title = "Nueva playlist", label = "Nombre", confirmText = "Crear",
            onDismiss = { dialog = null },
            onConfirm = { lib.createPlaylist(it); dialog = null },
        )
        is PlaylistDialog.Rename -> TextInputDialog(
            title = "Renombrar playlist", label = "Nombre", confirmText = "Guardar",
            initial = d.playlist.name,
            onDismiss = { dialog = null },
            onConfirm = { lib.renamePlaylist(d.playlist.id, it); dialog = null },
        )
        is PlaylistDialog.Delete -> ConfirmDialog(
            title = "Eliminar playlist",
            text = "¿Eliminar \"${d.playlist.name}\"? Las canciones que estén en otras playlists se conservan.",
            confirmText = "Eliminar",
            onDismiss = { dialog = null },
            onConfirm = { lib.deletePlaylist(d.playlist.id); dialog = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistCard(
    playlist: Playlist,
    songCount: Int,
    cover: List<Song>,
    thumbnail: (Song) -> Any,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val style = LocalAppStyle.current
    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = { menu = true })) {
        PlaylistMosaic(cover, thumbnail, Modifier.fillMaxWidth().aspectRatio(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                Text(
                    playlist.name, style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    style.label(if (songCount == 1) "1 canción" else "$songCount canciones"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, "Opciones", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Renombrar") }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Eliminar") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

/** Portada: 1 imagen, o mosaico 2×2 si hay 4 canciones o más. */
@Composable
fun PlaylistMosaic(cover: List<Song>, thumbnail: (Song) -> Any, modifier: Modifier) {
    val style = LocalAppStyle.current
    val shape = style.cardShape
    val border = if (style.border > 0.dp) Modifier.border(style.border, MaterialTheme.colorScheme.outline, shape) else Modifier
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant).then(border)) {
        when {
            cover.isEmpty() -> Icon(
                Icons.AutoMirrored.Filled.QueueMusic, null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.Center).fillMaxSize(0.4f),
            )
            cover.size < 4 -> ArtworkImage(thumbnail(cover.first()), Modifier.fillMaxSize(), RectangleShape)
            else -> Column(Modifier.fillMaxSize()) {
                cover.take(4).chunked(2).forEach { row ->
                    Row(Modifier.weight(1f)) {
                        row.forEach { song ->
                            ArtworkImage(thumbnail(song), Modifier.weight(1f).fillMaxSize(), RectangleShape)
                        }
                    }
                }
            }
        }
    }
}
