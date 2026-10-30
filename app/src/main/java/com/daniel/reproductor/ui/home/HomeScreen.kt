package com.daniel.reproductor.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.ui.components.EmptyState
import com.daniel.reproductor.ui.components.OfflineBanner
import com.daniel.reproductor.ui.components.PillButton
import com.daniel.reproductor.ui.components.ScreenTitle
import com.daniel.reproductor.ui.components.SongRow
import com.daniel.reproductor.ui.library.LibraryViewModel
import com.daniel.reproductor.ui.library.PlaylistPickerSheet
import com.daniel.reproductor.ui.library.SortMode
import com.daniel.reproductor.ui.library.songActions
import com.daniel.reproductor.ui.player.PlayerState
import com.daniel.reproductor.ui.profile.ProfileButton
import com.daniel.reproductor.ui.settings.SettingsViewModel
import com.daniel.reproductor.ui.theme.LocalAppStyle

/** Inicio: todas las canciones guardadas. */
@Composable
fun HomeScreen(
    lib: LibraryViewModel,
    settings: SettingsViewModel,
    player: PlayerState?,
) {
    val songs by lib.homeSongs.collectAsStateWithLifecycle()
    val total by lib.allSongs.collectAsStateWithLifecycle()
    val sort by lib.sort.collectAsStateWithLifecycle()
    val onlyDownloaded by lib.onlyDownloaded.collectAsStateWithLifecycle()
    val online by lib.isOnline.collectAsStateWithLifecycle()
    val progress by lib.downloadProgress.collectAsStateWithLifecycle()
    val update by settings.extractorUpdate.collectAsStateWithLifecycle()
    var pickFor by remember { mutableStateOf<Song?>(null) }
    val style = LocalAppStyle.current

    val list = songs.orEmpty()
    val totalSec = total.orEmpty().sumOf { it.durationSec }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(), bottom = 24.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                ProfileButton(Modifier.padding(end = 14.dp))
                ScreenTitle(
                    "Mis canciones",
                    Modifier.weight(1f),
                    subtitle = "${total.orEmpty().size} canciones · ${formatTotal(totalSec)}",
                )
            }
        }
        update?.let { version ->
            item { ExtractorUpdateBanner(version, settings.extractorVersion, settings::dismissExtractorUpdate) }
        }
        if (!online) item { OfflineBanner() }
        if (list.isNotEmpty() && player != null) {
            item {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Reproducir", Icons.Default.PlayArrow, { player.playSongs(list, 0) }, Modifier.weight(1f))
                    PillButton(
                        "Aleatorio", Icons.Default.Shuffle,
                        { player.playSongs(list, list.indices.random(), shuffled = true) },
                        Modifier.weight(1f), primary = false,
                    )
                }
            }
        }
        if (!total.isNullOrEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    items(SortMode.entries) { mode ->
                        FilterChip(
                            selected = sort == mode,
                            onClick = { lib.sort.value = mode },
                            label = { Text(style.label(mode.label)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                    item {
                        FilterChip(
                            selected = onlyDownloaded,
                            onClick = { lib.onlyDownloaded.value = !onlyDownloaded },
                            label = { Text(style.label("Descargadas")) },
                            leadingIcon = { Icon(Icons.Default.DownloadDone, null) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondary,
                            ),
                        )
                    }
                }
            }
        }
        if (songs != null && list.isEmpty()) {
            item {
                if (total.isNullOrEmpty()) {
                    EmptyState(
                        Icons.Default.LibraryMusic, "Tu biblioteca está vacía",
                        "Busca canciones con la lupa de abajo, o comparte un video desde la app de YouTube.",
                    )
                } else {
                    EmptyState(Icons.Default.DownloadDone, "Nada descargado", "Descarga canciones para escucharlas sin conexión.")
                }
            }
        }
        itemsIndexed(list, key = { _, s -> s.videoId }) { index, song ->
            SongRow(
                song = song,
                thumbnail = lib.thumbnail(song),
                isCurrent = player?.currentVideoId == song.videoId,
                isPlaying = player?.isPlaying == true,
                downloadProgress = progress[song.videoId],
                onClick = { player?.playSongs(list, index) },
                actions = songActions(song, lib) { pickFor = song },
                modifier = Modifier.animateItem(),
            )
        }
    }

    pickFor?.let { PlaylistPickerSheet(it, lib, onDismiss = { pickFor = null }) }
}

private fun formatTotal(sec: Long): String {
    val h = sec / 3600
    val m = sec % 3600 / 60
    return if (h > 0) "$h h $m min" else "$m min"
}

/** Aviso cuando hay una versión nueva del extractor: es lo primero que se rompe con el tiempo. */
@Composable
fun ExtractorUpdateBanner(newVersion: String, currentVersion: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f),
        shape = LocalAppStyle.current.cardShape,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SystemUpdate, null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("NewPipe Extractor $newVersion disponible", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Tienes $currentVersion. Si YouTube deja de funcionar, cambia newpipeExtractor en " +
                        "gradle/libs.versions.toml y vuelve a compilar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Ocultar") }
        }
    }
}
