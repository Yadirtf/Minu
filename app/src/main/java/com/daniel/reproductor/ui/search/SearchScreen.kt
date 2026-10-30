package com.daniel.reproductor.ui.search

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.ui.components.EmptyState
import com.daniel.reproductor.ui.components.ScreenTitle
import com.daniel.reproductor.ui.components.SongRow
import com.daniel.reproductor.ui.library.LibraryViewModel
import com.daniel.reproductor.ui.profile.ProfileButton
import com.daniel.reproductor.ui.library.PlaylistPickerSheet
import com.daniel.reproductor.ui.library.songActions
import com.daniel.reproductor.playback.PlaybackItems
import com.daniel.reproductor.ui.player.PlayerState
import com.daniel.reproductor.ui.theme.LocalAppStyle

/**
 * Buscar en mis canciones, en YouTube Music o en YouTube.
 * Con [targetPlaylist] (al entrar desde una playlist) el botón + agrega directo a esa playlist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    lib: LibraryViewModel,
    player: PlayerState?,
    targetPlaylist: Pair<Long, String>? = null,
    onBack: (() -> Unit)? = null,
    vm: SearchViewModel = viewModel(key = "search-${targetPlaylist?.first}", factory = SearchViewModel.Factory),
) {
    val query by vm.query.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val autoplay by vm.autoplay.collectAsStateWithLifecycle()
    val isOnline by lib.isOnline.collectAsStateWithLifecycle()
    val library by lib.allSongs.collectAsStateWithLifecycle()
    val progress by lib.downloadProgress.collectAsStateWithLifecycle()
    var pickFor by remember { mutableStateOf<Song?>(null) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val style = LocalAppStyle.current

    // Desde una playlist lo normal es buscar canciones nuevas. La pestaña Buscar no abre el teclado sola:
    // se llega a ella deslizando y el teclado saltaría a media animación.
    LaunchedEffect(targetPlaylist) {
        if (targetPlaylist == null) return@LaunchedEffect
        if (mode == SearchMode.LOCAL) vm.setMode(SearchMode.MUSIC)
        if (query.isEmpty()) focus.requestFocus()
    }

    val inLibrary = remember(library) { library.orEmpty().mapTo(HashSet()) { it.videoId } }
    val localResults = remember(query, library) { lib.searchLocal(query) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = if (onBack != null) 4.dp else 20.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
            else ProfileButton(Modifier.padding(end = 14.dp))
            ScreenTitle(
                "Buscar",
                subtitle = targetPlaylist?.let { "Agregando a ${it.second}" },
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = vm::setQuery,
            placeholder = { Text(if (mode == SearchMode.LOCAL) "Título o artista" else "Canción o artista en ${mode.label}") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Default.Clear, "Borrar") }
            },
            singleLine = true,
            shape = style.cardShape,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus(); vm.searchOnline() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).focusRequester(focus),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            SearchMode.entries.forEachIndexed { index, m ->
                SegmentedButton(
                    selected = mode == m,
                    onClick = { vm.setMode(m) },
                    shape = SegmentedButtonDefaults.itemShape(index, SearchMode.entries.size, style.cardShape),
                    icon = {},
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) { Text(style.label(m.label), maxLines = 1, style = MaterialTheme.typography.labelMedium) }
            }
        }

        Box(Modifier.fillMaxSize().padding(top = 8.dp)) {
            when {
                mode == SearchMode.LOCAL -> {
                    if (query.isBlank()) {
                        EmptyState(Icons.Default.Search, "Busca en tu biblioteca", "Escribe el nombre de una canción o artista.")
                    } else if (localResults.isEmpty()) {
                        EmptyState(
                            Icons.Default.SearchOff, "No está en tu biblioteca",
                            "Prueba buscarla en YouTube Music o en YouTube con las pestañas de arriba.",
                        )
                    } else {
                        ResultList(localResults) { index, song ->
                            SongRow(
                                song = song,
                                thumbnail = lib.thumbnail(song),
                                isCurrent = player?.currentVideoId == song.videoId,
                                isPlaying = player?.isPlaying == true,
                                downloadProgress = progress[song.videoId],
                                onClick = { player?.playSongs(localResults, index) },
                                actions = songActions(song, lib) { pickFor = song },
                            )
                        }
                    }
                }
                !isOnline -> EmptyState(Icons.Default.CloudOff, "Sin conexión", "Para buscar en YouTube necesitas internet.")
                else -> Column {
                    if (online.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    when {
                        online.error != null -> EmptyState(Icons.Default.SearchOff, "No se pudo buscar", online.error.orEmpty())
                        query.isBlank() -> EmptyState(
                            Icons.Default.Search, "Busca en ${mode.label}",
                            "Toca una canción para escucharla y + para guardarla en una playlist.",
                        )
                        online.results.isEmpty() && !online.loading && online.searchedFor != null ->
                            EmptyState(Icons.Default.SearchOff, "Sin resultados", "Prueba con otras palabras.")
                        else -> ResultList(online.results) { index, song ->
                            val saved = song.videoId in inLibrary
                            SongRow(
                                song = song,
                                thumbnail = song.thumbnailUrl,
                                isCurrent = player?.currentVideoId == song.videoId,
                                isPlaying = player?.isPlaying == true,
                                onClick = {
                                    if (autoplay) {
                                        val radio = if (mode == SearchMode.MUSIC) PlaybackItems.Radio.MUSIC else PlaybackItems.Radio.VIDEO
                                        player?.playRadio(song, radio)
                                    } else {
                                        player?.playSongs(online.results, index)
                                    }
                                },
                                trailing = {
                                    IconButton(onClick = {
                                        if (targetPlaylist != null) {
                                            vm.addToPlaylist(targetPlaylist.first, song)
                                            lib.message("Agregada a ${targetPlaylist.second}")
                                        } else {
                                            pickFor = song
                                        }
                                    }) {
                                        if (saved) {
                                            Icon(Icons.Default.CheckCircle, "Ya está guardada", tint = MaterialTheme.colorScheme.primary)
                                        } else {
                                            Icon(Icons.Default.AddCircleOutline, "Agregar a playlist")
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    pickFor?.let { PlaylistPickerSheet(it, lib, onDismiss = { pickFor = null }) }
}

@Composable
private fun ResultList(songs: List<Song>, row: @Composable (Int, Song) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        itemsIndexed(songs, key = { _, s -> s.videoId }) { index, song -> row(index, song) }
    }
}
