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
import com.daniel.reproductor.ui.components.OfflineBanner
import com.daniel.reproductor.ui.components.ScreenTitle
import com.daniel.reproductor.ui.components.SongRow
import com.daniel.reproductor.ui.library.LibraryViewModel
import com.daniel.reproductor.ui.profile.ProfileButton
import com.daniel.reproductor.ui.library.PlaylistPickerSheet
import com.daniel.reproductor.data.remote.SongSource
import com.daniel.reproductor.ui.player.PlayerState
import com.daniel.reproductor.ui.theme.LocalAppStyle

/**
 * Buscar en YouTube Music y YouTube a la vez. Sin texto muestra "Escuchar ahora" y lo escuchado hace poco.
 * Con [targetPlaylist] (al entrar desde una playlist) el botón + agrega directo a esa playlist.
 * Para buscar en la biblioteca está la barra de Inicio.
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
    val online by vm.online.collectAsStateWithLifecycle()
    val autoplay by vm.autoplay.collectAsStateWithLifecycle()
    val forYou by vm.forYou.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val isOnline by lib.isOnline.collectAsStateWithLifecycle()
    val inLibrary by lib.savedIds.collectAsStateWithLifecycle()
    var pickFor by remember { mutableStateOf<Song?>(null) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val style = LocalAppStyle.current

    // Desde una playlist lo normal es buscar canciones nuevas. La pestaña Buscar no abre el teclado sola:
    // se llega a ella deslizando y el teclado saltaría a media animación.
    LaunchedEffect(targetPlaylist) {
        if (targetPlaylist != null && query.isEmpty()) focus.requestFocus()
    }
    // Las recomendaciones se rearman solas si cambiaron tus gustos o pasó un rato (ver ListeningHistory)
    LaunchedEffect(isOnline) {
        if (isOnline && targetPlaylist == null) vm.refreshForYou()
    }

    fun play(song: Song, source: SongSource, all: List<Song>, index: Int) {
        if (autoplay) player?.playRadio(song, source) else player?.playSongs(all, index)
    }
    val discover = remember(player, autoplay) {
        DiscoverActions(
            play = { songs, index ->
                if (autoplay) player?.playSession(songs, index, SongSource.MUSIC) else player?.playSongs(songs, index)
            },
            playNext = { song -> player?.playNext(song) },
            addToPlaylist = { song -> pickFor = song },
        )
    }

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
            placeholder = { Text("¿Qué quieres escuchar?") },
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
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus(); vm.search() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).focusRequester(focus),
        )

        Box(Modifier.fillMaxSize()) {
            when {
                query.isBlank() && targetPlaylist != null -> EmptyState(
                    Icons.Default.Search, "Busca en YouTube",
                    "Toca + para agregar la canción a ${targetPlaylist.second}.",
                )
                query.isBlank() -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (!isOnline) item { OfflineBanner("Sin conexión: las recomendaciones vuelven cuando tengas internet") }
                    else item {
                        ForYouSection(
                            forYou = forYou,
                            currentVideoId = player?.currentVideoId,
                            isPlaying = player?.isPlaying == true,
                            actions = discover,
                            onRetry = { vm.refreshForYou(force = true) },
                        )
                    }
                    item { RecentSection(recent, discover) }
                }
                !isOnline -> EmptyState(Icons.Default.CloudOff, "Sin conexión", "Para buscar en YouTube necesitas internet.")
                else -> Column {
                    if (online.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                    when {
                        online.error != null -> EmptyState(Icons.Default.SearchOff, "No se pudo buscar", online.error.orEmpty())
                        online.results.isEmpty() && !online.loading && online.searchedFor != null ->
                            EmptyState(Icons.Default.SearchOff, "Sin resultados", "Prueba con otras palabras.")
                        else -> {
                            val songs = remember(online.results) { online.results.map { it.song } }
                            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                                itemsIndexed(online.results, key = { _, hit -> hit.song.videoId }) { index, hit ->
                                    val song = hit.song
                                    SongRow(
                                        song = song,
                                        thumbnail = song.thumbnailUrl,
                                        isCurrent = player?.currentVideoId == song.videoId,
                                        isPlaying = player?.isPlaying == true,
                                        onClick = { play(song, hit.source, songs, index) },
                                        tag = hit.source.label,
                                        trailing = {
                                            IconButton(onClick = {
                                                if (targetPlaylist != null) {
                                                    vm.addToPlaylist(targetPlaylist.first, song)
                                                    lib.message("Agregada a ${targetPlaylist.second}")
                                                } else {
                                                    pickFor = song
                                                }
                                            }) {
                                                if (song.videoId in inLibrary) {
                                                    Icon(Icons.Default.CheckCircle, "Ya está guardada", tint = MaterialTheme.colorScheme.primary)
                                                } else {
                                                    Icon(Icons.Default.AddCircleOutline, "Agregar al álbum")
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
        }
    }

    pickFor?.let { PlaylistPickerSheet(it, lib, onDismiss = { pickFor = null }) }
}
