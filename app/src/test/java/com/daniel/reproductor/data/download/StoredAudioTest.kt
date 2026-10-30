package com.daniel.reproductor.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StoredAudioTest {

    private val tree = "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FMinu"

    @Test
    fun legacyPathsAreAppPrivate() {
        assertEquals(StoredAudio.AppPrivate(File("/data/user/0/x/files/music/abc.m4a")), StoredAudio.parse("/data/user/0/x/files/music/abc.m4a"))
    }

    @Test
    fun folderPathsRoundTrip() {
        val stored = StoredAudio.InFolder(tree, "Para el carro", "Canción - Artista (1).m4a")
        assertEquals(stored, StoredAudio.parse(stored.encode()))
        assertEquals(StoredAudio.InFolder(tree, "Rock", stored.fileName), StoredAudio.parse(stored.inFolder("Rock").encode()))
    }

    @Test
    fun garbageIsIgnored() {
        assertNull(StoredAudio.parse("algo raro"))
        assertNull(StoredAudio.parse("$tree#sin-carpeta"))
    }

    @Test
    fun folderNamesAreSafe() {
        assertEquals("AC DC Hits", StorageNames.folderFor("AC/DC: Hits"))
        assertEquals("Fin de año", StorageNames.folderFor("  Fin de año...  "))
        assertEquals("Playlist", StorageNames.folderFor("///"))
        assertTrue(StorageNames.folderFor("x".repeat(300)).length <= 80)
        assertTrue(StorageNames.sameFolder("Rock", "ROCK"))
    }

    @Test
    fun fileNamesIncludeTheArtist() {
        assertEquals("Flowers - Miley Cyrus.m4a", StorageNames.fileFor("Flowers", "Miley Cyrus - Topic", "m4a"))
        // Si el artista ya está en el título no se repite.
        assertEquals("Miley Cyrus - Flowers.webm", StorageNames.fileFor("Miley Cyrus - Flowers", "Miley Cyrus", "webm"))
        assertEquals("¿Qué pasa - Canal.m4a", StorageNames.fileFor("¿Qué # pasa?", "Canal", "m4a"))
        assertEquals("Canción.m4a", StorageNames.fileFor("???", "", "m4a"))
    }

    @Test
    fun describesTheChosenFolder() {
        assertEquals("Memoria interna › Music › Minu", StorageNames.describeTree("primary:Music/Minu"))
        assertEquals("Tarjeta SD › Minu", StorageNames.describeTree("1A2B-3C4D:Minu"))
        assertEquals("Memoria interna", StorageNames.describeTree("primary:"))
    }
}
