package com.daniel.reproductor.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daniel.reproductor.data.db.DownloadState
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.ui.common.TextInputDialog
import com.daniel.reproductor.ui.components.ArtworkImage
import com.daniel.reproductor.ui.components.MenuAction
import com.daniel.reproductor.ui.theme.LocalAppStyle

/**
 * Hoja inferior "¿A qué playlist la agrego?". Marca con ✓ las playlists donde ya está la canción.
 * [onSaved] avisa solo cuando la canción quedó en una playlist nueva para ella.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPickerSheet(song: Song, lib: LibraryViewModel, onDismiss: () -> Unit, onSaved: (Song) -> Unit = {}) {
    val playlists by lib.playlists.collectAsStateWithLifecycle()
    val covers by lib.covers.collectAsStateWithLifecycle()
    val containing by remember(song.videoId) { lib.playlistsContaining(song.videoId) }
        .collectAsStateWithLifecycle(emptySet())
    var creating by remember { mutableStateOf(false) }
    val style = LocalAppStyle.current

    if (creating) {
        TextInputDialog(
            title = "Nueva playlist", label = "Nombre", confirmText = "Crear y agregar",
            onDismiss = { creating = false },
            onConfirm = { lib.createPlaylistAndAdd(it, song); onSaved(song); creating = false; onDismiss() },
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = style.cardShape,
    ) {
        Text(
            style.label("Agregar a playlist"),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Text(
            listOf(song.title, song.uploader).filter { it.isNotBlank() }.joinToString(" · "),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        LazyColumn(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            item {
                PickerRow(onClick = { creating = true }) {
                    Icon(Icons.Default.Add, null, Modifier.size(48.dp).padding(12.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Text(style.label("Nueva playlist"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            items(playlists, key = { it.playlist.id }) { item ->
                val added = item.playlist.id in containing
                PickerRow(onClick = {
                    lib.addToPlaylist(song, item.playlist)
                    if (!added) onSaved(song)
                    onDismiss()
                }) {
                    ArtworkImage(covers[item.playlist.id]?.firstOrNull()?.let(lib::thumbnail), Modifier.size(48.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.playlist.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (added) "Ya está aquí · ${item.songCount}" else songCountLabel(item.songCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (added) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (added) Icon(Icons.Default.CheckCircle, "Ya está en esta playlist", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

private fun songCountLabel(count: Int) = if (count == 1) "1 canción" else "$count canciones"

@Composable
private fun PickerRow(onClick: () -> Unit, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Acciones del menú ⋮ de una canción de la biblioteca. */
fun songActions(song: Song, lib: LibraryViewModel, onAddToPlaylist: () -> Unit): List<MenuAction> = buildList {
    add(MenuAction("Agregar a playlist", Icons.AutoMirrored.Filled.PlaylistAdd, onAddToPlaylist))
    when (song.downloadState) {
        DownloadState.NONE, DownloadState.FAILED -> add(MenuAction("Descargar", Icons.Default.Download) { lib.download(song) })
        DownloadState.DONE -> add(MenuAction("Eliminar descarga", Icons.Default.DownloadForOffline) { lib.deleteDownload(song) })
        else -> add(MenuAction("Cancelar descarga", Icons.Default.DownloadForOffline) { lib.deleteDownload(song) })
    }
    add(MenuAction("Eliminar de mi biblioteca", Icons.Default.Delete) { lib.removeFromLibrary(song) })
}
