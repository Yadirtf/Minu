package com.daniel.reproductor.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM PlayHistory ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PlayHistory>>

    @Query("SELECT * FROM PlayHistory ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<PlayHistory>

    @Query(
        "INSERT OR IGNORE INTO PlayHistory (videoId, title, uploader, durationSec, thumbnailUrl, playCount, lastPlayedAt) " +
            "VALUES (:videoId, :title, :uploader, :durationSec, :thumbnailUrl, 0, :at)"
    )
    suspend fun insertIfMissing(videoId: String, title: String, uploader: String, durationSec: Long, thumbnailUrl: String, at: Long)

    @Query(
        "UPDATE PlayHistory SET playCount = playCount + 1, lastPlayedAt = :at, title = :title, uploader = :uploader, " +
            "thumbnailUrl = CASE WHEN :thumbnailUrl = '' THEN thumbnailUrl ELSE :thumbnailUrl END WHERE videoId = :videoId"
    )
    suspend fun bump(videoId: String, title: String, uploader: String, thumbnailUrl: String, at: Long)

    /** Suma una escucha, creando la fila si es la primera vez. */
    @Transaction
    suspend fun record(song: Song, at: Long) {
        insertIfMissing(song.videoId, song.title, song.uploader, song.durationSec, song.thumbnailUrl, at)
        bump(song.videoId, song.title, song.uploader, song.thumbnailUrl, at)
    }

    /** Se quedan solo las [keep] más recientes: el historial no necesita crecer sin fin. */
    @Query("DELETE FROM PlayHistory WHERE videoId NOT IN (SELECT videoId FROM PlayHistory ORDER BY lastPlayedAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}
