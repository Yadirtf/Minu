package com.daniel.reproductor.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.daniel.reproductor.data.PlaylistBackup
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    // --- Playlists ---

    @Query(
        """SELECT Playlist.*, (SELECT COUNT(*) FROM PlaylistSong WHERE playlistId = Playlist.id) AS songCount
           FROM Playlist ORDER BY createdAt DESC"""
    )
    fun observePlaylists(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM Playlist ORDER BY createdAt DESC")
    suspend fun getPlaylists(): List<Playlist>

    @Query("SELECT * FROM Playlist WHERE id = :id")
    fun observePlaylist(id: Long): Flow<Playlist?>

    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("UPDATE Playlist SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: Long, name: String)

    @Query("DELETE FROM Playlist WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    // --- Canciones ---

    @Query(
        """SELECT Song.* FROM Song INNER JOIN PlaylistSong ON Song.videoId = PlaylistSong.videoId
           WHERE PlaylistSong.playlistId = :playlistId ORDER BY PlaylistSong.position"""
    )
    fun observeSongs(playlistId: Long): Flow<List<Song>>

    /** Toda la biblioteca, lo agregado más reciente primero. */
    @Query("SELECT * FROM Song ORDER BY rowid DESC")
    fun observeAllSongs(): Flow<List<Song>>

    /** Canciones de cada playlist en orden, para armar las portadas tipo mosaico. */
    @Query(
        """SELECT PlaylistSong.playlistId AS playlistId, Song.* FROM PlaylistSong
           INNER JOIN Song ON Song.videoId = PlaylistSong.videoId
           ORDER BY PlaylistSong.playlistId, PlaylistSong.position"""
    )
    fun observePlaylistSongs(): Flow<List<PlaylistSongRow>>

    @Query("DELETE FROM PlaylistSong WHERE videoId = :videoId")
    suspend fun removeFromAllPlaylists(videoId: String)

    @Query("SELECT * FROM Song WHERE videoId = :videoId")
    suspend fun getSong(videoId: String): Song?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSong(song: Song)

    @Query("UPDATE Song SET downloadState = :state WHERE videoId = :videoId")
    suspend fun setDownloadState(videoId: String, state: DownloadState)

    @Query("UPDATE Song SET downloadState = 'DONE', localPath = :path WHERE videoId = :videoId")
    suspend fun setDownloaded(videoId: String, path: String)

    @Query("UPDATE Song SET downloadState = 'NONE', localPath = NULL WHERE videoId = :videoId")
    suspend fun clearDownload(videoId: String)

    /** Canciones que ya no están en ninguna playlist. */
    @Query("SELECT * FROM Song WHERE videoId NOT IN (SELECT videoId FROM PlaylistSong)")
    suspend fun getOrphanSongs(): List<Song>

    @Query("DELETE FROM Song WHERE videoId = :videoId")
    suspend fun deleteSong(videoId: String)

    @Query(
        """SELECT Song.* FROM Song INNER JOIN PlaylistSong ON Song.videoId = PlaylistSong.videoId
           WHERE PlaylistSong.playlistId = :playlistId"""
    )
    suspend fun getSongs(playlistId: Long): List<Song>

    // --- Relación playlist ↔ canción ---

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM PlaylistSong WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    /** Devuelve -1 si la canción ya estaba en la playlist. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistSong(entry: PlaylistSong): Long

    @Transaction
    suspend fun appendToPlaylist(playlistId: Long, videoId: String): Boolean =
        insertPlaylistSong(PlaylistSong(playlistId, videoId, nextPosition(playlistId))) != -1L

    @Query("UPDATE PlaylistSong SET position = :position WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun setPosition(playlistId: Long, videoId: String, position: Int)

    @Transaction
    suspend fun reorder(playlistId: Long, orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id -> setPosition(playlistId, id, index) }
    }

    @Query("SELECT * FROM Playlist WHERE uid = :uid LIMIT 1")
    suspend fun findPlaylistByUid(uid: String): Playlist?

    @Query("DELETE FROM Playlist WHERE uid NOT IN (:keep)")
    suspend fun deletePlaylistsExcept(keep: List<String>)

    @Query("DELETE FROM PlaylistSong WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("UPDATE Playlist SET name = :name, createdAt = :createdAt WHERE id = :id")
    suspend fun updatePlaylistInfo(id: Long, name: String, createdAt: Long)

    @Query("SELECT * FROM Playlist WHERE name = :name LIMIT 1")
    suspend fun findPlaylistByName(name: String): Playlist?

    @Query(
        """SELECT Song.* FROM Song INNER JOIN PlaylistSong ON Song.videoId = PlaylistSong.videoId
           WHERE PlaylistSong.playlistId = :playlistId ORDER BY PlaylistSong.position"""
    )
    suspend fun getSongsOrdered(playlistId: Long): List<Song>

    @Query("DELETE FROM PlaylistSong WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun removeFromPlaylist(playlistId: Long, videoId: String)

    /** Reemplaza playlists y su contenido por los de [entries], en una sola transacción. */
    @Transaction
    suspend fun applySnapshot(entries: List<PlaylistBackup.Entry>) {
        deletePlaylistsExcept(entries.map { it.uid })
        for (entry in entries) {
            val existing = findPlaylistByUid(entry.uid)
            val id = if (existing != null) {
                updatePlaylistInfo(existing.id, entry.name, entry.createdAt)
                clearPlaylist(existing.id)
                existing.id
            } else {
                insertPlaylist(Playlist(name = entry.name, createdAt = entry.createdAt, uid = entry.uid))
            }
            entry.songs.forEachIndexed { index, song ->
                insertSong(song)
                insertPlaylistSong(PlaylistSong(id, song.videoId, index))
            }
        }
    }
}
