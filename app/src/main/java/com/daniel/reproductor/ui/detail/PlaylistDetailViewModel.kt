package com.daniel.reproductor.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.daniel.reproductor.data.AddResult
import com.daniel.reproductor.data.MusicFiles
import com.daniel.reproductor.data.MusicRepository
import com.daniel.reproductor.data.NetworkMonitor
import com.daniel.reproductor.data.UserFacingException
import com.daniel.reproductor.data.db.Playlist
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.ui.common.app
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistDetailViewModel(
    private val playlistId: Long,
    private val repo: MusicRepository,
    private val files: MusicFiles,
    private val network: NetworkMonitor,
) : ViewModel() {

    val playlist: StateFlow<Playlist?> = repo.observePlaylist(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val songs: StateFlow<List<Song>?> = repo.observeSongs(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _adding = MutableStateFlow(false)
    val adding: StateFlow<Boolean> = _adding.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun add(link: String) {
        _adding.value = true
        viewModelScope.launch {
            val msg = try {
                when (val r = repo.addToPlaylist(playlistId, link)) {
                    is AddResult.Added -> "Agregada: ${r.song.title}"
                    is AddResult.AlreadyInPlaylist -> "Ya estaba en la playlist: ${r.song.title}"
                }
            } catch (e: UserFacingException) {
                e.message.orEmpty()
            }
            _adding.value = false
            _messages.send(msg)
        }
    }

    val downloadProgress: StateFlow<Map<String, Int>> = repo.observeDownloadProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun thumbnail(song: Song): Any = files.thumbnailFor(song)

    fun download(song: Song) = viewModelScope.launch { repo.download(song.videoId) }

    fun deleteDownload(song: Song) = viewModelScope.launch {
        repo.deleteDownload(song.videoId)
        _messages.send("Descarga eliminada: ${song.title}")
    }

    fun downloadAll() = viewModelScope.launch {
        val n = repo.downloadPlaylist(playlistId)
        _messages.send(if (n == 0) "Todo ya está descargado" else "Descargando $n canciones")
    }

    fun deleteAllDownloads() = viewModelScope.launch {
        repo.deletePlaylistDownloads(playlistId)
        _messages.send("Descargas de la playlist eliminadas")
    }

    val isOnline: StateFlow<Boolean> = network.isOnline

    fun saveOrder(orderedIds: List<String>) = viewModelScope.launch { repo.reorder(playlistId, orderedIds) }

    fun remove(song: Song) = viewModelScope.launch {
        repo.removeFromPlaylist(playlistId, song.videoId)
        _messages.send("Quitada: ${song.title}")
    }

    companion object {
        fun factory(playlistId: Long) = viewModelFactory {
            initializer { PlaylistDetailViewModel(playlistId, app.repository, app.files, app.network) }
        }
    }
}
