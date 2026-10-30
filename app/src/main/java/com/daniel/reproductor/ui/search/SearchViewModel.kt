package com.daniel.reproductor.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.daniel.reproductor.data.AppSettings
import com.daniel.reproductor.data.ForYou
import com.daniel.reproductor.data.ListeningHistory
import com.daniel.reproductor.data.MusicRepository
import com.daniel.reproductor.data.UserFacingException
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.data.remote.SongSource
import com.daniel.reproductor.ui.common.app
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Un resultado de la búsqueda y el catálogo del que salió. */
data class SearchHit(val song: Song, val source: SongSource)

data class OnlineResults(
    val loading: Boolean = false,
    val results: List<SearchHit> = emptyList(),
    val error: String? = null,
    val searchedFor: String? = null,
)

/** Buscar en YouTube Music y YouTube a la vez, y las recomendaciones de "Escuchar ahora". */
class SearchViewModel(
    private val repo: MusicRepository,
    private val history: ListeningHistory,
    settings: AppSettings,
) : ViewModel() {

    val query = MutableStateFlow("")

    /** Si está activa, tocar un resultado arranca su radio en vez de encolar los demás resultados. */
    val autoplay: StateFlow<Boolean> = settings.autoplay

    private val _online = MutableStateFlow(OnlineResults())
    val online: StateFlow<OnlineResults> = _online.asStateFlow()

    val forYou: StateFlow<ForYou> = history.forYou
    val recent: StateFlow<List<Song>> = history.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var job: Job? = null

    fun setQuery(text: String) {
        query.value = text
        search(debounce = true)
    }

    fun refreshForYou(force: Boolean = false) {
        viewModelScope.launch { history.refresh(force) }
    }

    /** Al escribir espera un momento para no buscar en cada letra. */
    fun search(debounce: Boolean = false) {
        val q = query.value.trim()
        if (q.isEmpty()) {
            job?.cancel()
            _online.value = OnlineResults()
            return
        }
        if (_online.value.searchedFor == q && _online.value.error == null) return
        job?.cancel()
        job = viewModelScope.launch {
            if (debounce) delay(600)
            _online.value = _online.value.copy(loading = true, error = null)
            val result = searchBoth(q)
            ensureActive() // una búsqueda cancelada no pisa a la nueva
            _online.value = result
        }
    }

    /**
     * Las dos búsquedas van en paralelo. Si una falla se muestra la otra; el error solo aparece
     * cuando no llegó nada de ninguna.
     */
    private suspend fun searchBoth(q: String): OnlineResults = coroutineScope {
        val music = async { runCatching { repo.search(q, SongSource.MUSIC) } }
        val videos = async { runCatching { repo.search(q, SongSource.YOUTUBE) } }
        val m = music.await()
        val v = videos.await()
        val hits = interleave(
            m.getOrDefault(emptyList()).map { SearchHit(it, SongSource.MUSIC) },
            v.getOrDefault(emptyList()).map { SearchHit(it, SongSource.YOUTUBE) },
        )
        val error = (m.exceptionOrNull() ?: v.exceptionOrNull())
            ?.takeIf { m.isFailure && v.isFailure }
            ?.let { (it as? UserFacingException)?.message ?: "No se pudo buscar." }
        OnlineResults(results = hits, error = error, searchedFor = q)
    }

    fun addToPlaylist(playlistId: Long, song: Song) = viewModelScope.launch { repo.addSong(playlistId, song) }

    companion object {
        val Factory = viewModelFactory {
            initializer { SearchViewModel(app.repository, app.history, app.settings) }
        }

        /**
         * Alterna las dos listas respetando el orden de cada una, y sin repetir un video que salga en ambas
         * (gana YouTube Music, que trae la versión de audio).
         */
        internal fun interleave(music: List<SearchHit>, videos: List<SearchHit>): List<SearchHit> {
            val seen = music.mapTo(HashSet()) { it.song.videoId }
            val onlyVideos = videos.filter { seen.add(it.song.videoId) }
            val out = ArrayList<SearchHit>(music.size + onlyVideos.size)
            for (i in 0 until maxOf(music.size, onlyVideos.size)) {
                music.getOrNull(i)?.let(out::add)
                onlyVideos.getOrNull(i)?.let(out::add)
            }
            return out
        }
    }
}
