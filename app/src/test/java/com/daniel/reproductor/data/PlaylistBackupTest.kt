package com.daniel.reproductor.data

import com.daniel.reproductor.data.db.DownloadState
import com.daniel.reproductor.data.db.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistBackupTest {

    @Test
    fun roundTrip() {
        val song = Song("dQw4w9WgXcQ", "Título \"con\" comillas", "Canal", 213, "https://i.ytimg.com/x.jpg",
            localPath = "/data/x.webm", downloadState = DownloadState.DONE)
        val original = listOf(
            PlaylistBackup.Entry("Rock", listOf(song)),
            PlaylistBackup.Entry("Vacía", emptyList()),
        )

        val decoded = PlaylistBackup.decode(PlaylistBackup.encode(original))

        assertEquals(2, decoded.size)
        assertEquals("Rock", decoded[0].name)
        // La descarga no viaja en el respaldo
        assertEquals(song.copy(localPath = null, downloadState = DownloadState.NONE), decoded[0].songs.single())
        assertEquals(emptyList<Song>(), decoded[1].songs)
    }

    @Test(expected = UserFacingException::class)
    fun rejectsGarbage() {
        PlaylistBackup.decode("esto no es json")
    }
}
