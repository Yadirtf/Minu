package com.daniel.reproductor.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.daniel.reproductor.data.MusicRepository
import com.daniel.reproductor.data.UserFacingException
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.ui.common.app
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SearchMode(val label: String) {
    LOCAL("Mis canciones"), MUSIC("YouTube Music"), YOUTUBE("YouTube")
}

data class OnlineResults(
    val loading: Boolean = false,
    val results: List<Song> = emptyList(),
    val error: String? = null,
    val searchedFor: String? = null,
)

class SearchViewModel(private val repo: MusicRepository) : ViewModel() {

    val query = MutableStateFlow("")
    val mode = MutableStateFlow(SearchMode.LOCAL)

    private val _online = MutableStateFlow(OnlineResults())
    val online: StateFlow<OnlineResults> = _online.asStateFlow()

    private var job: Job? = null

    fun setQuery(text: String) {
        query.value = text
        if (mode.value != SearchMode.LOCAL) searchOnline(debounce = true)
    }

    fun setMode(value: SearchMode) {
        mode.value = value
        if (value != SearchMode.LOCAL) searchOnline(debounce = false)
    }

    /** Busca en YouTube o YouTube Music. Al escribir espera un momento para no buscar en cada letra. */
    fun searchOnline(debounce: Boolean = false) {
        val q = query.value.trim()
        val musicOnly = mode.value == SearchMode.MUSIC
        val key = "$musicOnly:$q"
        if (q.isEmpty() || (_online.value.searchedFor == key && _online.value.error == null)) return
        job?.cancel()
        job = viewModelScope.launch {
            if (debounce) delay(600)
            _online.value = _online.value.copy(loading = true, error = null)
            _online.value = try {
                OnlineResults(results = repo.search(q, musicOnly), searchedFor = key)
            } catch (e: UserFacingException) {
                OnlineResults(error = e.message, searchedFor = key)
            }
        }
    }

    fun addToPlaylist(playlistId: Long, song: Song) = viewModelScope.launch { repo.addSong(playlistId, song) }

    companion object {
        val Factory = viewModelFactory { initializer { SearchViewModel(app.repository) } }
    }
}
