package com.daniel.reproductor.data.remote

import com.daniel.reproductor.data.YouTubeLinks
import com.daniel.reproductor.data.db.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.PaidContentException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException

/** De qué catálogo viene una canción: cambia la búsqueda y la radio que se arma con ella. */
enum class SongSource(val label: String) { MUSIC("YT Music"), YOUTUBE("YouTube") }

class YouTubeResolver {

    /** Acepta un link de YouTube o un videoId. Nunca guardes la URL del audio: caduca en horas. */
    suspend fun getInfo(urlOrId: String): StreamInfo = withContext(Dispatchers.IO) {
        val url = if (urlOrId.startsWith("http")) urlOrId
        else "https://www.youtube.com/watch?v=$urlOrId"
        StreamInfo.getInfo(ServiceList.YouTube, url)
    }

    /**
     * Busca en YouTube Music (solo canciones) o en YouTube (videos).
     * Devuelve las canciones listas para guardar, sin tener que resolver cada una.
     */
    suspend fun search(query: String, source: SongSource): List<Song> = withContext(Dispatchers.IO) {
        val service = ServiceList.YouTube
        val filter = when (source) {
            SongSource.MUSIC -> YoutubeSearchQueryHandlerFactory.MUSIC_SONGS
            SongSource.YOUTUBE -> YoutubeSearchQueryHandlerFactory.VIDEOS
        }
        val handler = service.searchQHFactory.fromQuery(query, listOf(filter), "")
        SearchInfo.getInfo(service, handler).relatedItems.toSongs()
    }

    /** Un tramo de la radio de una canción. [next] es null cuando YouTube ya no da más. */
    class RadioPage(val url: String, val songs: List<Song>, val next: Page?)

    /**
     * La radio que arma YouTube a partir de una canción: canciones parecidas por género, estilo y artista.
     * Con [SongSource.MUSIC] usa la de YouTube Music. Si YouTube no da la radio (pasa en algunos países),
     * usa los videos relacionados.
     */
    suspend fun radio(videoId: String, source: SongSource): RadioPage = withContext(Dispatchers.IO) {
        val list = (if (source == SongSource.MUSIC) "RDAMVM" else "RD") + videoId
        val url = "https://www.youtube.com/watch?v=$videoId&list=$list"
        try {
            val info = PlaylistInfo.getInfo(ServiceList.YouTube, url)
            RadioPage(url, info.relatedItems.toSongs(), info.nextPage)
        } catch (e: Exception) {
            if (e is IOException || e is ReCaptchaException) throw e
            RadioPage(url, getInfo(videoId).relatedItems.toSongs(), null)
        }
    }

    /** El siguiente tramo de la misma radio. */
    suspend fun radioMore(page: RadioPage): RadioPage = withContext(Dispatchers.IO) {
        val more = PlaylistInfo.getMoreItems(ServiceList.YouTube, page.url, page.next)
        RadioPage(page.url, more.items.toSongs(), more.nextPage)
    }

    private fun List<*>.toSongs(): List<Song> = filterIsInstance<StreamInfoItem>()
        .filter { it.duration > 0 } // sin transmisiones en vivo
        .mapNotNull { item ->
            val id = YouTubeLinks.extractVideoId(item.url) ?: return@mapNotNull null
            Song(
                videoId = id,
                title = item.name,
                uploader = item.uploaderName.orEmpty(),
                durationSec = item.duration,
                thumbnailUrl = item.thumbnails.maxByOrNull { it.height }?.url.orEmpty(),
            )
        }

    /** Mejor audio descargable de una sola URL (m4a o webm/opus), ignorando DASH/HLS. */
    fun bestAudio(info: StreamInfo): AudioStream {
        val streams = info.audioStreams
        return streams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .maxByOrNull { it.averageBitrate }
            ?: streams.maxByOrNull { it.averageBitrate }
            ?: throw ContentNotAvailableException("El video no tiene pistas de audio")
    }

    companion object {
        /** Mensaje legible para mostrar al usuario. */
        fun describeError(e: Throwable): String = when (e) {
            is AgeRestrictedContentException -> "Video restringido por edad."
            is PrivateContentException -> "El video es privado."
            is GeographicRestrictionException -> "El video no está disponible en tu país."
            is PaidContentException -> "El video es de pago."
            is ContentNotAvailableException -> "El video no está disponible (eliminado o bloqueado)."
            is ReCaptchaException -> "YouTube pidió un captcha. Espera un rato e intenta de nuevo."
            is IOException -> "Error de red. Revisa tu conexión."
            else -> "No se pudo resolver el video. Puede que haya que actualizar NewPipe Extractor.\n(${e.javaClass.simpleName}: ${e.message})"
        }
    }
}
