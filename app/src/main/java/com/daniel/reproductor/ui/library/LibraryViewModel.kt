package com.daniel.reproductor.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.daniel.reproductor.data.AddResult
import com.daniel.reproductor.data.AppSettings
import com.daniel.reproductor.data.MusicFiles
import com.daniel.reproductor.data.MusicRepository
import com.daniel.reproductor.data.NetworkMonitor
import com.daniel.reproductor.data.db.DownloadState
import com.daniel.reproductor.data.db.Playlist
import com.daniel.reproductor.data.db.PlaylistWithCount
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.ui.common.app
import com.daniel.reproductor.ui.theme.StyleId
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Normalizer

enum class SortMode(val label: String) {
    RECENT("Recientes"), TITLE("Título"), ARTIST("Artista"), DURATION("Duración")
}

/** Estado compartido de la biblioteca: lo usan Inicio, Playlists, Buscar y el selector de playlist. */
class LibraryViewModel(
    private val repo: MusicRepository,
    private val files: MusicFiles,
    private val settings: AppSettings,
    network: NetworkMonitor,
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = network.isOnline
    val style: StateFlow<StyleId> = settings.style
    fun setStyle(id: StyleId) = settings.setStyle(id)

    val allSongs: StateFlow<List<Song>?> = repo.observeAllSongs()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** videoIds de todo lo guardado: sirve para marcar con ✓ lo que ya está en la biblioteca. */
    val savedIds: StateFlow<Set<String>> = allSongs
        .map { songs -> songs.orEmpty().mapTo(HashSet()) { it.videoId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun playlistsContaining(videoId: String): Flow<Set<Long>> = repo.observePlaylistIdsFor(videoId)

    val playlists: StateFlow<List<PlaylistWithCount>> = repo.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val covers: StateFlow<Map<Long, List<Song>>> = repo.observePlaylistCovers()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val downloadProgress: StateFlow<Map<String, Int>> = repo.observeDownloadProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val sort = MutableStateFlow(SortMode.RECENT)
    val onlyDownloaded = MutableStateFlow(false)

    /** Canciones de Inicio: ordenadas, filtradas y sin conexión solo las descargadas. */
    val homeSongs: StateFlow<List<Song>?> =
        combine(allSongs, sort, onlyDownloaded, isOnline) { songs, sort, onlyDone, online ->
            songs?.let { list ->
                val filtered = if (onlyDone || !online) list.filter { it.downloadState == DownloadState.DONE } else list
                when (sort) {
                    SortMode.RECENT -> filtered
                    SortMode.TITLE -> filtered.sortedBy { it.title.lowercase() }
                    SortMode.ARTIST -> filtered.sortedBy { it.uploader.lowercase() }
                    SortMode.DURATION -> filtered.sortedByDescending { it.durationSec }
                }
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun thumbnail(song: Song): Any = files.thumbnailFor(song)

    /** Búsqueda local, sin distinguir mayúsculas ni tildes. */
    fun searchLocal(query: String): List<Song> {
        val q = query.normalized()
        if (q.isBlank()) return emptyList()
        return allSongs.value.orEmpty().filter { q in it.title.normalized() || q in it.uploader.normalized() }
    }

    fun addToPlaylist(song: Song, playlist: Playlist) = viewModelScope.launch {
        val msg = when (repo.addSong(playlist.id, song)) {
            is AddResult.Added -> "Agregada a ${playlist.name}"
            is AddResult.AlreadyInPlaylist -> "Ya estaba en ${playlist.name}"
        }
        _messages.send(msg)
    }

    fun createPlaylistAndAdd(name: String, song: Song) = viewModelScope.launch {
        val id = repo.createPlaylist(name)
        repo.addSong(id, song)
        _messages.send("Creada \"${name.trim()}\" con ${song.title}")
    }

    fun createPlaylist(name: String) = viewModelScope.launch { repo.createPlaylist(name) }
    fun renamePlaylist(id: Long, name: String) = viewModelScope.launch { repo.renamePlaylist(id, name) }
    fun deletePlaylist(id: Long) = viewModelScope.launch { repo.deletePlaylist(id) }

    fun download(song: Song) = viewModelScope.launch { repo.download(song.videoId) }

    fun deleteDownload(song: Song) = viewModelScope.launch {
        repo.deleteDownload(song.videoId)
        _messages.send("Descarga eliminada")
    }

    fun removeFromLibrary(song: Song) = viewModelScope.launch {
        repo.removeFromLibrary(song.videoId)
        _messages.send("Eliminada de tu biblioteca: ${song.title}")
    }

    fun message(text: String) = viewModelScope.launch { _messages.send(text) }

    companion object {
        val Factory = viewModelFactory {
            initializer { LibraryViewModel(app.repository, app.files, app.settings, app.network) }
        }
    }
}

private fun String.normalized(): String =
    Normalizer.normalize(lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").trim()
