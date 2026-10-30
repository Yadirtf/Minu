package com.daniel.reproductor.data.download

import java.io.File

/**
 * Dónde vive el audio de una canción descargada. Se guarda en `Song.localPath` como texto:
 *  - Memoria privada de la app: la ruta absoluta del archivo (formato original, sigue siendo válido).
 *  - Carpeta elegida por el usuario: `<treeUri>#<carpeta de la playlist>/<archivo>`.
 *
 * La carpeta y el archivo se guardan por nombre (no por URI del documento) para que renombrar
 * la carpeta de una playlist solo cambie el texto, y el URI real se busca al reproducir.
 */
sealed interface StoredAudio {
    fun encode(): String

    data class AppPrivate(val file: File) : StoredAudio {
        override fun encode(): String = file.absolutePath
    }

    data class InFolder(val tree: String, val folder: String, val fileName: String) : StoredAudio {
        override fun encode(): String = "$tree$SEPARATOR$folder/$fileName"

        fun inFolder(newFolder: String) = copy(folder = newFolder)
    }

    companion object {
        private const val SEPARATOR = '#' // nunca aparece en un tree URI ni en los nombres limpios

        fun parse(localPath: String): StoredAudio? {
            if (localPath.startsWith("/")) return AppPrivate(File(localPath))
            val hash = localPath.indexOf(SEPARATOR).takeIf { it > 0 } ?: return null
            val relative = localPath.substring(hash + 1)
            val slash = relative.indexOf('/').takeIf { it > 0 } ?: return null
            return InFolder(localPath.substring(0, hash), relative.substring(0, slash), relative.substring(slash + 1))
        }
    }
}

/** Nombres de carpetas y archivos válidos en cualquier almacenamiento (incluidas memorias SD en FAT). */
object StorageNames {
    /** Respaldo para una canción que, por algún motivo, no está en ninguna playlist. */
    const val UNSORTED_FOLDER = "Sin playlist"

    private val forbidden = Regex("""[\\/:*?"<>|#\p{Cntrl}]""")
    private val spaces = Regex("""\s+""")
    private const val MAX_FOLDER = 80
    private const val MAX_FILE = 120

    fun folderFor(playlistName: String): String = clean(playlistName, MAX_FOLDER).ifEmpty { "Playlist" }

    /** "Título - Artista.m4a". El sufijo " - Topic" de los canales automáticos de YouTube se quita. */
    fun fileFor(title: String, uploader: String, extension: String): String {
        val artist = uploader.removeSuffix(" - Topic").trim()
        val base = if (artist.isEmpty() || title.contains(artist, ignoreCase = true)) title else "$title - $artist"
        return clean(base, MAX_FILE).ifEmpty { "Canción" } + "." + extension
    }

    /** Las memorias de Android no distinguen mayúsculas en los nombres. */
    fun sameFolder(a: String, b: String) = a.equals(b, ignoreCase = true)

    /**
     * Texto legible para el id del documento raíz que da el selector de carpetas
     * (por ejemplo "primary:Music/Minu" → "Memoria interna › Music › Minu").
     */
    fun describeTree(treeDocumentId: String): String {
        val volume = treeDocumentId.substringBefore(':')
        val path = treeDocumentId.substringAfter(':', "").split('/').filter { it.isNotBlank() }
        val volumeName = when {
            volume.equals("primary", ignoreCase = true) -> "Memoria interna"
            volume.equals("home", ignoreCase = true) -> "Documentos"
            Regex("[0-9A-F]{4}-[0-9A-F]{4}", RegexOption.IGNORE_CASE).matches(volume) -> "Tarjeta SD"
            else -> volume
        }
        return (listOf(volumeName) + path).joinToString(" › ")
    }

    private fun clean(text: String, max: Int): String =
        text.replace(forbidden, " ").replace(spaces, " ").trim().take(max).trim().trimEnd('.').trim()
}
