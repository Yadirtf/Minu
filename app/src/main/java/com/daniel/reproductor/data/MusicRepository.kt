package com.daniel.reproductor.data

import com.daniel.reproductor.data.db.MusicDao
import com.daniel.reproductor.data.db.Playlist
import com.daniel.reproductor.data.db.PlaylistWithCount
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.data.remote.SongSource
import com.daniel.reproductor.data.remote.YouTubeResolver
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.daniel.reproductor.data.db.DownloadState
import com.daniel.reproductor.data.download.DownloadStorage
import com.daniel.reproductor.data.download.DownloadWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

sealed interface AddResult {
    val song: Song

    data class Added(override val song: Song) : AddResult
    data class AlreadyInPlaylist(override val song: Song) : AddResult
}

/** Error con un mensaje listo para mostrar al usuario. */
class UserFacingException(message: String, cause: Throwable? = null) : Exception(message, cause)

class MusicRepository(
    private val dao: MusicDao,
    private val resolver: YouTubeResolver,
    private val storage: DownloadStorage,
    private val workManager: () -> WorkManager, // diferido: se pide solo al usarlo
    private val settings: AppSettings,
) {
    fun observePlaylists(): Flow<List<PlaylistWithCount>> = dao.observePlaylists()
    fun observePlaylist(id: Long): Flow<Playlist?> = dao.observePlaylist(id)
    fun observeSongs(playlistId: Long): Flow<List<Song>> = dao.observeSongs(playlistId)
    fun observeAllSongs(): Flow<List<Song>> = dao.observeAllSongs()
    fun observePlaylistIdsFor(videoId: String): Flow<Set<Long>> = dao.observePlaylistIdsFor(videoId).map { it.toSet() }

    /** Hasta 4 canciones por playlist (para el mosaico de portada). */
    fun observePlaylistCovers(): Flow<Map<Long, List<Song>>> = dao.observePlaylistSongs().map { rows ->
        rows.groupBy({ it.playlistId }, { it.song }).mapValues { it.value.take(4) }
    }

    /** Quita la canción de todas las playlists y borra su descarga. */
    suspend fun removeFromLibrary(videoId: String) {
        dao.removeFromAllPlaylists(videoId)
        deleteOrphans()
    }

    suspend fun getPlaylists(): List<Playlist> = dao.getPlaylists()
    suspend fun getSong(videoId: String): Song? = dao.getSong(videoId)
    suspend fun createPlaylist(name: String): Long =
        dao.insertPlaylist(Playlist(name = name.trim())).also { storage.schedule() } // crea su carpeta

    suspend fun renamePlaylist(id: Long, name: String) {
        val old = dao.getPlaylist(id)?.name ?: return
        dao.renamePlaylist(id, name.trim())
        if (old != name.trim()) storage.schedule(renamed = mapOf(old to name.trim()))
    }

    suspend fun deletePlaylist(id: Long) {
        val name = dao.getPlaylist(id)?.name
        dao.deletePlaylist(id)
        deleteOrphans()
        // Las canciones que siguen en otra playlist se mudan a su carpeta.
        storage.schedule(removed = listOfNotNull(name))
    }

    suspend fun removeFromPlaylist(playlistId: Long, videoId: String) {
        dao.removeFromPlaylist(playlistId, videoId)
        deleteOrphans()
        storage.schedule()
    }

    /** Borra las canciones (y sus descargas) que ya no están en ninguna playlist. */
    private suspend fun deleteOrphans() {
        for (song in dao.getOrphanSongs()) {
            workManager().cancelUniqueWork(DownloadWorker.uniqueName(song.videoId))
            storage.deleteFor(song)
            dao.deleteSong(song.videoId)
        }
    }

    // --- Descargas ---

    /** [playlistId]: desde qué playlist se pidió; la canción se guarda en su carpeta. */
    suspend fun download(videoId: String, playlistId: Long? = null) {
        val song = dao.getSong(videoId) ?: return
        if (song.downloadState == DownloadState.DONE) return
        val network = if (settings.wifiOnly.value) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    DownloadWorker.KEY_VIDEO_ID to videoId,
                    DownloadWorker.KEY_PLAYLIST_ID to (playlistId ?: DownloadWorker.NO_PLAYLIST),
                )
            )
            .setConstraints(Constraints(requiredNetworkType = network, requiresStorageNotLow = true))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag(DownloadWorker.TAG)
            .build()
        dao.setDownloadState(videoId, DownloadState.QUEUED)
        workManager().enqueueUniqueWork(DownloadWorker.uniqueName(videoId), ExistingWorkPolicy.REPLACE, request)
    }

    suspend fun downloadPlaylist(playlistId: Long): Int {
        val pending = dao.getSongs(playlistId).filter {
            it.downloadState != DownloadState.DONE && it.downloadState != DownloadState.DOWNLOADING
        }
        pending.forEach { download(it.videoId, playlistId) }
        return pending.size
    }

    suspend fun deleteDownload(videoId: String) {
        workManager().cancelUniqueWork(DownloadWorker.uniqueName(videoId))
        dao.getSong(videoId)?.let { storage.deleteFor(it) }
        dao.clearDownload(videoId)
    }

    suspend fun deletePlaylistDownloads(playlistId: Long) {
        dao.getSongs(playlistId).filter { it.downloadState != DownloadState.NONE }
            .forEach { deleteDownload(it.videoId) }
    }

    /** Porcentaje de descarga de cada canción que se está descargando ahora. */
    fun observeDownloadProgress(): Flow<Map<String, Int>> =
        workManager().getWorkInfosByTagFlow(DownloadWorker.TAG).map { infos ->
            infos.filter { it.state == WorkInfo.State.RUNNING }
                .mapNotNull { info ->
                    val id = info.progress.getString(DownloadWorker.KEY_VIDEO_ID) ?: return@mapNotNull null
                    id to info.progress.getInt(DownloadWorker.KEY_PROGRESS, 0)
                }.toMap()
        }

    /**
     * Agrega un link de YouTube a la playlist. Si la canción ya existe en la base de datos
     * (por estar en otra playlist) no se vuelve a consultar YouTube.
     */
    suspend fun addToPlaylist(playlistId: Long, link: String): AddResult {
        val videoId = YouTubeLinks.extractVideoId(link)
            ?: throw UserFacingException("Eso no parece un link de un video de YouTube.")

        val song = dao.getSong(videoId) ?: resolveSong(videoId).also { dao.insertSong(it) }
        return if (dao.appendToPlaylist(playlistId, videoId)) AddResult.Added(song)
        else AddResult.AlreadyInPlaylist(song)
    }

    /** Agrega un resultado de búsqueda (ya trae los datos, no hace falta resolverlo). */
    suspend fun addSong(playlistId: Long, song: Song): AddResult {
        dao.insertSong(song)
        val stored = dao.getSong(song.videoId) ?: song
        return if (dao.appendToPlaylist(playlistId, song.videoId)) AddResult.Added(stored)
        else AddResult.AlreadyInPlaylist(stored)
    }

    suspend fun search(query: String, source: SongSource): List<Song> = try {
        resolver.search(query, source)
    } catch (e: Exception) {
        throw UserFacingException(YouTubeResolver.describeError(e), e)
    }

    suspend fun reorder(playlistId: Long, orderedIds: List<String>) = dao.reorder(playlistId, orderedIds)

    // --- Respaldo JSON ---

    suspend fun exportJson(): String = PlaylistBackup.encode(snapshotEntries())

    /** Todas las playlists con sus canciones en orden (para respaldo y nube). */
    suspend fun snapshotEntries(): List<PlaylistBackup.Entry> =
        dao.getPlaylists().map { PlaylistBackup.Entry(it.name, dao.getSongsOrdered(it.id), it.uid, it.createdAt) }

    /** Importar un respaldo: se une con lo que ya hay. */
    suspend fun importJson(json: String): Int {
        val entries = PlaylistBackup.decode(json)
        mergeEntries(entries)
        return entries.size
    }

    /**
     * Une playlists con las locales (misma playlist = mismo uid, o mismo nombre en respaldos viejos)
     * y agrega las canciones que falten. No borra nada.
     */
    suspend fun mergeEntries(entries: List<PlaylistBackup.Entry>) = trackingFolders {
        for (entry in entries) {
            val existing = dao.findPlaylistByUid(entry.uid) ?: dao.findPlaylistByName(entry.name)
            val id = existing?.id ?: dao.insertPlaylist(Playlist(name = entry.name, createdAt = entry.createdAt, uid = entry.uid))
            for (song in entry.songs) {
                dao.insertSong(song)
                dao.appendToPlaylist(id, song.videoId)
            }
        }
    }

    /** Deja la biblioteca exactamente como la copia de la nube (conserva las descargas que sigan en uso). */
    suspend fun replaceWithEntries(entries: List<PlaylistBackup.Entry>) = trackingFolders {
        dao.applySnapshot(entries)
        deleteOrphans()
    }

    /**
     * Para cambios que llegan en bloque (nube, respaldo): compara las playlists antes y después y
     * avisa a [storage] qué se renombró o borró, para que las carpetas sigan los cambios.
     */
    private suspend fun trackingFolders(change: suspend () -> Unit) {
        val before = dao.getPlaylists().associate { it.uid to it.name }
        change()
        val after = dao.getPlaylists().associate { it.uid to it.name }
        storage.schedule(
            renamed = before.filter { (uid, name) -> after[uid] != null && after[uid] != name }.map { (uid, name) -> name to after.getValue(uid) }.toMap(),
            removed = before.filterKeys { it !in after }.values,
        )
    }

    private suspend fun resolveSong(videoId: String): Song {
        val info = try {
            resolver.getInfo(YouTubeLinks.watchUrl(videoId))
        } catch (e: Exception) {
            throw UserFacingException(YouTubeResolver.describeError(e), e)
        }
        return Song(
            videoId = videoId,
            title = info.name,
            uploader = info.uploaderName.orEmpty(),
            durationSec = info.duration,
            thumbnailUrl = info.thumbnails.maxByOrNull { it.height }?.url.orEmpty(),
        )
    }
}
