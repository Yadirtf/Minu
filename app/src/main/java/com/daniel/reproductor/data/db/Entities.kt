package com.daniel.reproductor.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/** Solo se guarda el videoId: la URL del audio caduca y se resuelve justo antes de reproducir. */
@Entity
data class Song(
    @PrimaryKey val videoId: String,
    val title: String,
    val uploader: String,
    val durationSec: Long,
    val thumbnailUrl: String,
    val localPath: String? = null, // null = no descargada
    val downloadState: DownloadState = DownloadState.NONE,
)

enum class DownloadState { NONE, QUEUED, DOWNLOADING, DONE, FAILED }

@Entity
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** Identificador estable para sincronizar la misma playlist entre celulares. */
    @ColumnInfo(defaultValue = "") val uid: String = UUID.randomUUID().toString(),
)

/** Tabla intermedia: una canción puede estar en varias playlists sin duplicar la descarga. */
@Entity(
    primaryKeys = ["playlistId", "videoId"],
    foreignKeys = [
        ForeignKey(Playlist::class, ["id"], ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Song::class, ["videoId"], ["videoId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("videoId")],
)
data class PlaylistSong(
    val playlistId: Long,
    val videoId: String,
    val position: Int,
)

data class PlaylistSongRow(
    val playlistId: Long,
    @Embedded val song: Song,
)

/** Una canción dentro de una playlist, en el orden en que se agregó. */
data class Membership(
    val videoId: String,
    val playlistId: Long,
    val playlistName: String,
)

data class PlaylistWithCount(
    @Embedded val playlist: Playlist,
    val songCount: Int,
)
