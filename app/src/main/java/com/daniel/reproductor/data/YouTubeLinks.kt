package com.daniel.reproductor.data

import java.net.URI

/** Convierte cualquier link de YouTube (o texto compartido que lo contenga) en el videoId. */
object YouTubeLinks {

    private val ID = Regex("^[A-Za-z0-9_-]{11}$")
    private val URL_IN_TEXT = Regex("""https?://\S+""")
    private val PATH_PREFIXES = listOf("shorts", "embed", "live", "v", "e")

    fun extractVideoId(text: String): String? {
        val trimmed = text.trim()
        if (ID.matches(trimmed)) return trimmed
        val candidates = URL_IN_TEXT.findAll(trimmed).map { it.value }.toList()
            .ifEmpty { listOf("https://$trimmed") }
        return candidates.firstNotNullOfOrNull(::fromUrl)
    }

    private fun fromUrl(url: String): String? {
        val uri = runCatching { URI(url.trimEnd('.', ',', ')')) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }

        val id = when (host) {
            "youtu.be" -> segments.firstOrNull()
            "youtube.com", "music.youtube.com", "youtube-nocookie.com" -> when {
                segments.firstOrNull() == "watch" -> queryParam(uri, "v")
                segments.size >= 2 && segments[0] in PATH_PREFIXES -> segments[1]
                else -> null
            }
            else -> null
        }
        return id?.takeIf { ID.matches(it) }
    }

    private fun queryParam(uri: URI, name: String): String? =
        uri.rawQuery?.split('&')
            ?.map { it.split('=', limit = 2) }
            ?.firstOrNull { it[0] == name }
            ?.getOrNull(1)

    fun watchUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId"
}
