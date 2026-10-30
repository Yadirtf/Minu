package com.daniel.reproductor.ui.detail

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.reproductor.data.db.DownloadState
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.ui.common.TextInputDialog
import com.daniel.reproductor.ui.components.EmptyState
import com.daniel.reproductor.ui.components.MenuAction
import com.daniel.reproductor.ui.components.OfflineBanner
import com.daniel.reproductor.ui.components.PillButton
import com.daniel.reproductor.ui.components.ScreenTitle
import com.daniel.reproductor.ui.components.SongRow
import com.daniel.reproductor.ui.library.LibraryViewModel
import com.daniel.reproductor.ui.library.PlaylistPickerSheet
import com.daniel.reproductor.ui.library.songActions
import com.daniel.reproductor.ui.player.PlayerState
import com.daniel.reproductor.ui.playlists.PlaylistMosaic
import com.daniel.reproductor.ui.theme.LocalAppStyle
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    lib: LibraryViewModel,
    player: PlayerState?,
    onBack: () -> Unit,
    onSearch: (name: String) -> Unit,
    vm: PlaylistDetailViewModel = viewModel(
        key = "playlist-$playlistId",
        factory = PlaylistDetailViewModel.factory(playlistId),
    ),
) {
    val playlist by vm.playlist.collectAsStateWithLifecycle()
    val allSongs by vm.songs.collectAsStateWithLifecycle()
    val online by vm.isOnline.collectAsStateWithLifecycle()
    val adding by vm.adding.collectAsStateWithLifecycle()
    val progress by vm.downloadProgress.collectAsStateWithLifecycle()
    var showAddLink by remember { mutableStateOf(false) }
    var pickFor by remember { mutableStateOf<Song?>(null) }
    val style = LocalAppStyle.current

    LaunchedEffect(vm) { vm.messages.collect { lib.message(it) } }

    // Sin conexión solo se muestran (y reproducen) las canciones descargadas
    val songs = allSongs?.let { list -> if (online) list else list.filter { it.downloadState == DownloadState.DONE } }

    // Copia local para que el arrastre se vea fluido; se guarda en la base al soltar
    var ordered by remember { mutableStateOf(emptyList<Song>()) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(songs) { if (!dragging) ordered = songs.orEmpty() }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = ordered.indexOfFirst { it.videoId == from.key }
        val toIndex = ordered.indexOfFirst { it.videoId == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            ordered = ordered.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
    }

    val name = playlist?.name.orEmpty()
    val totalMin = allSongs.orEmpty().sumOf { it.durationSec } / 60

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
                    Spacer(Modifier.weight(1f))
                    if (online) {
                        IconButton(onClick = { onSearch(name) }) { Icon(Icons.Default.Search, "Buscar y agregar") }
                        IconButton(onClick = { showAddLink = true }) { Icon(Icons.Default.Link, "Agregar por link") }
                    }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Más opciones") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Descargar todo el álbum") },
                                leadingIcon = { Icon(Icons.Default.Download, null) },
                                onClick = { menu = false; vm.downloadAll() },
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar descargas del álbum") },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { menu = false; vm.deleteAllDownloads() },
                            )
                        }
                    }
                }
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.Bottom) {
                    PlaylistMosaic(ordered.take(4), vm::thumbnail, Modifier.size(132.dp))
                    Spacer(Modifier.size(16.dp))
                    ScreenTitle(
                        name,
                        Modifier.weight(1f),
                        subtitle = "${allSongs.orEmpty().size} canciones · $totalMin min",
                    )
                }
                if (ordered.isNotEmpty() && player != null) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillButton("Reproducir", Icons.Default.PlayArrow, { player.playSongs(ordered, 0) }, Modifier.weight(1f))
                        PillButton(
                            "Aleatorio", Icons.Default.Shuffle,
                            { player.playSongs(ordered, ordered.indices.random(), shuffled = true) },
                            Modifier.weight(1f), primary = false,
                        )
                    }
                } else {
                    Spacer(Modifier.height(16.dp))
                }
                if (!online) OfflineBanner()
                if (adding) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }

        if (songs != null && ordered.isEmpty() && !adding) {
            item {
                if (!online && allSongs.orEmpty().isNotEmpty()) {
                    EmptyState(Icons.AutoMirrored.Filled.QueueMusic, "Nada descargado aquí", "Sin conexión solo suenan las canciones descargadas.")
                } else {
                    EmptyState(
                        Icons.AutoMirrored.Filled.QueueMusic, "Álbum vacío",
                        "Usa la lupa de arriba para buscar canciones, pega un link, o comparte desde YouTube.",
                    )
                }
            }
        }

        itemsIndexed(ordered, key = { _, song -> song.videoId }) { index, song ->
            ReorderableItem(reorderState, key = song.videoId) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag")
                Surface(
                    shadowElevation = elevation,
                    color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background,
                ) {
                    SongRow(
                        song = song,
                        thumbnail = vm.thumbnail(song),
                        isCurrent = player?.currentVideoId == song.videoId,
                        isPlaying = player?.isPlaying == true,
                        downloadProgress = progress[song.videoId],
                        onClick = { player?.playSongs(ordered, index) },
                        actions = listOf(
                            MenuAction("Quitar de este álbum", Icons.Default.RemoveCircleOutline) { vm.remove(song) },
                        ) + songActions(song, lib) { pickFor = song },
                        // Reordenar solo con la lista completa (no en modo sin conexión)
                        trailing = if (online) {
                            {
                                Icon(
                                    Icons.Default.DragHandle, "Arrastrar para reordenar",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.draggableHandle(
                                        onDragStarted = { dragging = true },
                                        onDragStopped = {
                                            dragging = false
                                            vm.saveOrder(ordered.map { it.videoId })
                                        },
                                    ).padding(10.dp),
                                )
                            }
                        } else null,
                    )
                }
            }
        }
    }

    if (showAddLink) {
        val clipboard = LocalClipboardManager.current
        val clip = remember { clipboard.getText()?.text.orEmpty().takeIf { "youtu" in it }.orEmpty() }
        TextInputDialog(
            title = style.label("Agregar por link"),
            label = "Link de YouTube",
            confirmText = "Agregar",
            initial = clip,
            onDismiss = { showAddLink = false },
            onConfirm = { vm.add(it); showAddLink = false },
        )
    }
    pickFor?.let { PlaylistPickerSheet(it, lib, onDismiss = { pickFor = null }) }
}
