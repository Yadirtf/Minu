package com.daniel.reproductor.data

import com.daniel.reproductor.data.db.HistoryDao
import com.daniel.reproductor.data.db.MusicDao
import com.daniel.reproductor.data.db.PlayHistory
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.data.remote.SongSource
import com.daniel.reproductor.data.remote.YouTubeResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.exp
import kotlin.math.ln

/** Recomendaciones de "Escuchar ahora". [songs] vacía y sin [loading] = todavía no hay de dónde sacar. */
data class ForYou(
    val songs: List<Song> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * Historial de escucha y recomendaciones a partir de él.
 *
 * Las recomendaciones mezclan la radio de YouTube Music de unas pocas "semillas": las canciones que más
 * escuchas últimamente, una por artista para no llenar todo de lo mismo. Sin historial, las semillas
 * salen de lo último que guardaste.
 */
class ListeningHistory(
    private val dao: HistoryDao,
    private val music: MusicDao,
    private val resolver: YouTubeResolver,
) {
    fun observeRecent(limit: Int = RECENT_SHOWN): Flow<List<Song>> =
        dao.observeRecent(limit).map { rows -> rows.map(PlayHistory::toSong) }

    suspend fun record(song: Song) {
        // Lo que suena de la biblioteca puede traer la portada local, que no sirve como URL: se usa la guardada
        val thumb = song.thumbnailUrl.ifEmpty { music.getSong(song.videoId)?.thumbnailUrl.orEmpty() }
        dao.record(song.copy(thumbnailUrl = thumb), System.currentTimeMillis())
        dao.trim(KEEP)
    }

    private val _forYou = MutableStateFlow(ForYou())
    val forYou: StateFlow<ForYou> = _forYou.asStateFlow()

    private val mutex = Mutex()
    private var lastSeeds: List<String> = emptyList()
    private var lastLoadAt = 0L

    /** Vuelve a armar las recomendaciones si cambiaron tus gustos o pasó un buen rato. */
    suspend fun refresh(force: Boolean = false) = mutex.withLock {
        val seeds = pickSeeds()
        val fresh = System.currentTimeMillis() - lastLoadAt < TTL_MS
        if (!force && seeds == lastSeeds && fresh && _forYou.value.songs.isNotEmpty()) return@withLock
        if (seeds.isEmpty()) {
            _forYou.value = ForYou()
            return@withLock
        }
        _forYou.value = _forYou.value.copy(loading = true, error = null)
        val batches = coroutineScope {
            seeds.map { id -> async { runCatching { resolver.radio(id, SongSource.MUSIC).songs }.getOrNull() } }
                .map { it.await() }
        }
        val ok = batches.filterNotNull()
        if (ok.isEmpty()) {
            _forYou.value = _forYou.value.copy(loading = false, error = "No se pudieron cargar tus recomendaciones.")
            return@withLock
        }
        // Una de cada radio por turno, para que se mezclen los estilos
        val skip = seeds.toHashSet()
        val picked = LinkedHashMap<String, Song>()
        val iterators = ok.map { it.iterator() }
        while (picked.size < SHOWN && iterators.any { it.hasNext() }) {
            for (radio in iterators) {
                while (radio.hasNext()) {
                    val song = radio.next()
                    if (song.videoId !in skip && song.videoId !in picked) {
                        picked[song.videoId] = song
                        break
                    }
                }
                if (picked.size == SHOWN) break
            }
        }
        lastSeeds = seeds
        lastLoadAt = System.currentTimeMillis()
        _forYou.value = ForYou(songs = picked.values.toList())
    }

    /**
     * Puntaje = escuchas × olvido (vale la mitad cada [HALF_LIFE_DAYS] días). Así pesa lo que escuchas
     * mucho y también lo de esta semana, y lo de hace meses se va apagando.
     */
    private suspend fun pickSeeds(): List<String> {
        val now = System.currentTimeMillis()
        val history = dao.getRecent(SCORED)
        if (history.isEmpty()) return music.getRecentSongs(SEEDS).map { it.videoId }
        val decay = ln(2.0) / (HALF_LIFE_DAYS * DAY_MS)
        return history
            .sortedByDescending { it.playCount * exp(-decay * (now - it.lastPlayedAt)) }
            .distinctBy { it.uploader.lowercase() }
            .take(SEEDS)
            .map { it.videoId }
    }

    private companion object {
        const val SEEDS = 3
        const val SHOWN = 18 // 6 columnas de 3, como en Spotify
        const val SCORED = 100
        const val KEEP = 500
        const val RECENT_SHOWN = 12
        const val HALF_LIFE_DAYS = 7.0
        const val DAY_MS = 24 * 60 * 60 * 1000.0
        const val TTL_MS = 60 * 60 * 1000L
    }
}
