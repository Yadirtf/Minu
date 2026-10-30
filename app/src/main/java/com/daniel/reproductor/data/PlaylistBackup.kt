package com.daniel.reproductor.data

import com.daniel.reproductor.data.db.Song
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Formato JSON de la biblioteca. Se usa para el respaldo manual (exportar/importar)
 * y para la copia en la nube. No incluye los archivos descargados, solo los datos.
 */
object PlaylistBackup {

    data class Entry(
        val name: String,
        val songs: List<Song>,
        val uid: String = UUID.randomUUID().toString(),
        val createdAt: Long = System.currentTimeMillis(),
    )

    data class Snapshot(val playlists: List<Entry>, val settings: Map<String, String> = emptyMap())

    private const val VERSION = 2

    fun encode(playlists: List<Entry>, settings: Map<String, String> = emptyMap()): String =
        encodeObject(playlists, settings).toString(2)

    fun encodeObject(playlists: List<Entry>, settings: Map<String, String> = emptyMap()): JSONObject {
        val array = JSONArray()
        for (p in playlists) {
            val songs = JSONArray()
            for (s in p.songs) {
                songs.put(
                    JSONObject()
                        .put("videoId", s.videoId)
                        .put("title", s.title)
                        .put("uploader", s.uploader)
                        .put("durationSec", s.durationSec)
                        .put("thumbnailUrl", s.thumbnailUrl)
                )
            }
            array.put(
                JSONObject().put("uid", p.uid).put("name", p.name).put("createdAt", p.createdAt).put("songs", songs)
            )
        }
        return JSONObject()
            .put("app", "minu")
            .put("version", VERSION)
            .put("playlists", array)
            .put("settings", JSONObject(settings))
    }

    /** Lanza [UserFacingException] si el archivo no es un respaldo válido. */
    fun decode(json: String): List<Entry> = decodeSnapshot(json).playlists

    fun decodeSnapshot(json: String): Snapshot {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw UserFacingException("El archivo no es un respaldo válido.", e)
        }
        return decodeObject(root)
    }

    fun decodeObject(root: JSONObject): Snapshot {
        val playlists = root.optJSONArray("playlists")
            ?: throw UserFacingException("El archivo no contiene álbumes.")

        val entries = (0 until playlists.length()).map { i ->
            val p = playlists.getJSONObject(i)
            val songs = p.optJSONArray("songs") ?: JSONArray()
            Entry(
                name = p.optString("name").ifBlank { "Playlist importada" },
                uid = p.optString("uid").ifBlank { UUID.randomUUID().toString() },
                createdAt = p.optLong("createdAt", System.currentTimeMillis()),
                songs = (0 until songs.length()).mapNotNull { j ->
                    val s = songs.getJSONObject(j)
                    val id = s.optString("videoId").takeIf { YouTubeLinks.extractVideoId(it) == it }
                        ?: return@mapNotNull null
                    Song(
                        videoId = id,
                        title = s.optString("title"),
                        uploader = s.optString("uploader"),
                        durationSec = s.optLong("durationSec"),
                        thumbnailUrl = s.optString("thumbnailUrl"),
                    )
                },
            )
        }
        val settingsJson = root.optJSONObject("settings")
        val settings = settingsJson?.keys()?.asSequence()?.associateWith { settingsJson.optString(it) }.orEmpty()
        return Snapshot(entries, settings)
    }
}
