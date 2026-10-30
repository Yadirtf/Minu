package com.daniel.reproductor.data.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.daniel.reproductor.data.AppSettings
import com.daniel.reproductor.data.MusicFiles
import com.daniel.reproductor.data.db.MusicDao
import com.daniel.reproductor.data.db.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Decide dónde se guardan las canciones descargadas y mantiene ese lugar en orden.
 *
 * - Sin carpeta elegida: memoria privada de la app (como siempre).
 * - Con carpeta elegida: `<carpeta>/<nombre de la playlist>/<Título - Artista>.m4a`.
 *
 * Una canción puede estar en varias playlists, pero el archivo existe una sola vez: vive en la
 * carpeta de una de sus playlists (la que se usó para descargarla o, si no, la primera a la que se
 * agregó). Cuando esa relación se rompe (se renombra o borra la playlist, se quita la canción, se
 * cambia de carpeta) [organize] la mueve a donde corresponde. Toda la reorganización corre en
 * [OrganizeDownloadsWorker], así sobrevive aunque se cierre la app.
 */
class DownloadStorage(
    private val context: Context,
    private val dao: MusicDao,
    private val files: MusicFiles,
    private val settings: AppSettings,
    private val workManager: () -> WorkManager,
) {
    private val resolver get() = context.contentResolver

    /** Avance de la reorganización en curso. */
    data class Progress(val done: Int, val total: Int)

    private fun currentTree(): DocumentTree? = settings.downloadTree.value?.let { DocumentTree(resolver, it) }

    /** Texto para Ajustes: la carpeta elegida, o null si se usa la memoria de la app. */
    suspend fun describeLocation(): String? = withContext(Dispatchers.IO) {
        currentTree()?.let { runCatching { it.describe() }.getOrNull() }
    }

    // --- Elegir carpeta ---

    fun useFolder(tree: Uri) {
        resolver.takePersistableUriPermission(
            tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        settings.setDownloadTree(tree)
        schedule(force = true)
    }

    fun useAppStorage() {
        settings.setDownloadTree(null)
        schedule(force = true) // trae de vuelta lo que estaba en la carpeta
    }

    // --- Descargas ---

    /** Lleva una descarga terminada a su lugar definitivo y devuelve el valor para `Song.localPath`. */
    suspend fun store(song: Song, finished: File, extension: String, preferredPlaylistId: Long?): String =
        withContext(Dispatchers.IO) {
            currentTree()?.let { tree ->
                try {
                    val folder = homeFolder(song.videoId, preferredPlaylistId)
                    val name = tree.importFile(finished, folder, StorageNames.fileFor(song.title, song.uploader, extension), mimeOf(extension))
                    finished.delete()
                    return@withContext StoredAudio.InFolder(tree.uri.toString(), folder, name).encode()
                } catch (e: Exception) {
                    // Carpeta borrada o sin permiso: la canción no se pierde, queda en la app.
                    Log.w(TAG, "No se pudo guardar en la carpeta elegida", e)
                }
            }
            val target = files.audioFile(song.videoId, extension)
            if (!finished.renameTo(target)) throw IOException("No se pudo guardar el archivo")
            target.absolutePath
        }

    /** URI reproducible del audio local, o null si ya no está (entonces se reproduce online). Bloquea. */
    fun playableUri(localPath: String): Uri? = runCatching {
        when (val stored = StoredAudio.parse(localPath)) {
            is StoredAudio.AppPrivate -> stored.file.takeIf { it.exists() }?.let(Uri::fromFile)
            is StoredAudio.InFolder -> DocumentTree(resolver, Uri.parse(stored.tree)).locate(stored.folder, stored.fileName)
            null -> null
        }
    }.getOrNull()

    /** Borra el audio y los archivos auxiliares de la canción. */
    suspend fun deleteFor(song: Song) = withContext(Dispatchers.IO) {
        song.localPath?.let { path ->
            runCatching {
                when (val stored = StoredAudio.parse(path)) {
                    is StoredAudio.AppPrivate -> stored.file.delete()
                    is StoredAudio.InFolder -> DocumentTree(resolver, Uri.parse(stored.tree)).delete(stored.folder, stored.fileName)
                    null -> Unit
                }
            }.onFailure { Log.w(TAG, "No se pudo borrar $path", it) }
        }
        files.deleteExtras(song.videoId)
    }

    // --- Mantener el orden ---

    /**
     * Pide reorganizar en segundo plano. [renamed] (nombre viejo → nuevo) permite renombrar la carpeta
     * entera en vez de mover canción por canción, y [removed] son playlists borradas cuya carpeta se
     * elimina si quedó vacía. Sin carpeta elegida no hay nada que hacer, salvo que se pida con [force].
     */
    fun schedule(renamed: Map<String, String> = emptyMap(), removed: Collection<String> = emptyList(), force: Boolean = false) {
        if (!force && settings.downloadTree.value == null) return
        val request = OneTimeWorkRequestBuilder<OrganizeDownloadsWorker>()
            .setInputData(
                workDataOf(
                    OrganizeDownloadsWorker.KEY_RENAMED_FROM to renamed.keys.toTypedArray(),
                    OrganizeDownloadsWorker.KEY_RENAMED_TO to renamed.values.toTypedArray(),
                    OrganizeDownloadsWorker.KEY_REMOVED to removed.toTypedArray(),
                )
            )
            .build()
        // En cadena: cada pedido corre después del anterior y ve el estado ya actualizado.
        workManager().enqueueUniqueWork(OrganizeDownloadsWorker.NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun observeProgress(): Flow<Progress?> =
        workManager().getWorkInfosForUniqueWorkFlow(OrganizeDownloadsWorker.NAME).map { infos ->
            infos.firstOrNull { it.state == WorkInfo.State.RUNNING }?.let {
                Progress(it.progress.getInt(OrganizeDownloadsWorker.KEY_DONE, 0), it.progress.getInt(OrganizeDownloadsWorker.KEY_TOTAL, 0))
            }
        }

    /** Deja cada descarga donde corresponde. Es idempotente: si todo está en orden no toca nada. */
    suspend fun organize(
        renamed: Map<String, String>,
        removed: Collection<String>,
        onProgress: suspend (Progress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val tree = currentTree()?.takeIf { it.isAccessible() }
        if (tree == null && settings.downloadTree.value != null) {
            Log.w(TAG, "La carpeta de descargas no está disponible; se reintenta en el próximo cambio")
            return@withContext
        }
        val playlistFolders = dao.getPlaylists().map { StorageNames.folderFor(it.name) }
        val emptied = mutableSetOf<String>()

        if (tree != null) {
            renamed.forEach { (old, new) -> renameFolder(tree, StorageNames.folderFor(old), StorageNames.folderFor(new), playlistFolders) }
            // Una carpeta por playlist, aunque todavía no tenga canciones descargadas.
            val existing = tree.list().filter { it.isDirectory }.map { it.name }
            playlistFolders.distinctBy { it.lowercase() }
                .filter { folder -> existing.none { StorageNames.sameFolder(it, folder) } }
                .forEach { runCatching { tree.ensureFolder(it) } }
        }

        val folders = dao.getMemberships().groupBy({ it.videoId }, { StorageNames.folderFor(it.playlistName) })
        val songs = dao.getDownloadedSongs()
        songs.forEachIndexed { index, song ->
            onProgress(Progress(index, songs.size))
            val stored = song.localPath?.let(StoredAudio::parse) ?: return@forEachIndexed
            val homes = folders[song.videoId].orEmpty().ifEmpty { listOf(StorageNames.UNSORTED_FOLDER) }
            try {
                val moved = relocate(song, stored, tree, homes) ?: return@forEachIndexed
                dao.setLocalPath(song.videoId, moved.encode())
                if (stored is StoredAudio.InFolder && stored.tree == tree?.uri?.toString()) emptied += stored.folder
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo mover ${song.videoId}", e)
            }
        }
        onProgress(Progress(songs.size, songs.size))

        if (tree != null) {
            (emptied + removed.map(StorageNames::folderFor))
                .filter { folder -> playlistFolders.none { StorageNames.sameFolder(it, folder) } }
                .forEach { runCatching { tree.deleteFolderIfEmpty(it) } }
        }
        releaseUnusedFolders()
    }

    /** Nuevo lugar del archivo si hay que moverlo, o null si ya está bien donde está. */
    private fun relocate(song: Song, stored: StoredAudio, tree: DocumentTree?, homes: List<String>): StoredAudio? {
        val home = homes.first()
        return when (stored) {
            is StoredAudio.AppPrivate -> {
                if (tree == null || !stored.file.exists()) return null
                val name = tree.importFile(
                    stored.file, home, StorageNames.fileFor(song.title, song.uploader, stored.file.extension), mimeOf(stored.file.extension),
                )
                stored.file.delete()
                StoredAudio.InFolder(tree.uri.toString(), home, name)
            }

            is StoredAudio.InFolder -> when {
                tree == null -> {
                    // Volver a la memoria de la app.
                    val source = DocumentTree(resolver, Uri.parse(stored.tree))
                    val target = files.audioFile(song.videoId, stored.fileName.substringAfterLast('.', "m4a"))
                    source.open(stored.folder, stored.fileName).use { input -> target.outputStream().use { input.copyTo(it) } }
                    source.delete(stored.folder, stored.fileName)
                    source.deleteFolderIfEmpty(stored.folder)
                    StoredAudio.AppPrivate(target)
                }

                stored.tree == tree.uri.toString() -> {
                    if (homes.any { StorageNames.sameFolder(it, stored.folder) }) return null
                    StoredAudio.InFolder(stored.tree, home, tree.move(stored.folder, stored.fileName, home))
                }

                else -> {
                    // Se eligió otra carpeta: copiar desde la anterior.
                    val source = DocumentTree(resolver, Uri.parse(stored.tree))
                    val name = source.open(stored.folder, stored.fileName).use {
                        tree.write(it, home, stored.fileName, mimeOf(stored.fileName.substringAfterLast('.', "")))
                    }
                    source.delete(stored.folder, stored.fileName)
                    source.deleteFolderIfEmpty(stored.folder)
                    StoredAudio.InFolder(tree.uri.toString(), home, name)
                }
            }
        }
    }

    /**
     * Renombra la carpeta completa (una sola operación) y actualiza las rutas guardadas. Si otra
     * playlist todavía usa el nombre viejo, o ya existe una carpeta con el nuevo, no se renombra:
     * las canciones se mueven una por una en [organize].
     */
    private suspend fun renameFolder(tree: DocumentTree, old: String, new: String, playlistFolders: List<String>) {
        if (old == new) return
        if (playlistFolders.any { StorageNames.sameFolder(it, old) }) return
        if (!tree.renameFolder(old, new)) return
        val root = tree.uri.toString()
        for (song in dao.getDownloadedSongs()) {
            val stored = song.localPath?.let(StoredAudio::parse) as? StoredAudio.InFolder ?: continue
            if (stored.tree == root && StorageNames.sameFolder(stored.folder, old)) {
                dao.setLocalPath(song.videoId, stored.inFolder(new).encode())
            }
        }
    }

    /** Carpeta de la playlist desde la que se descargó; si ya no la contiene, la primera a la que se agregó. */
    private suspend fun homeFolder(videoId: String, preferredPlaylistId: Long?): String {
        val playlists = dao.getPlaylistsOf(videoId)
        val home = playlists.firstOrNull { it.id == preferredPlaylistId } ?: playlists.firstOrNull()
        return home?.let { StorageNames.folderFor(it.name) } ?: StorageNames.UNSORTED_FOLDER
    }

    /** Suelta el permiso de carpetas anteriores cuando ya no queda ninguna canción en ellas. */
    private suspend fun releaseUnusedFolders() {
        val current = settings.downloadTree.value
        val inUse = dao.getDownloadedSongs().mapNotNull { (it.localPath?.let(StoredAudio::parse) as? StoredAudio.InFolder)?.tree }.toSet()
        resolver.persistedUriPermissions
            .filter { DocumentsContract.isTreeUri(it.uri) && it.uri != current && it.uri.toString() !in inUse }
            .forEach {
                runCatching {
                    resolver.releasePersistableUriPermission(
                        it.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
            }
    }

    private fun mimeOf(extension: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "application/octet-stream"

    private companion object {
        const val TAG = "DownloadStorage"
    }
}
