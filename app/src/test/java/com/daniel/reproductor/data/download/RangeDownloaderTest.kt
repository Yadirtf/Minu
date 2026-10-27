package com.daniel.reproductor.data.download

import com.daniel.reproductor.data.remote.OkHttpDownloader
import com.daniel.reproductor.data.remote.YouTubeResolver
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import java.io.File

/** Descarga real por bloques y comprueba que continuar una descarga interrumpida da el mismo archivo. */
class RangeDownloaderTest {

    @Test
    fun downloadsInChunksAndResumes(): Unit = runBlocking {
        val client = OkHttpClient()
        NewPipe.init(OkHttpDownloader(client))
        val resolver = YouTubeResolver()
        val audio = resolver.bestAudio(resolver.getInfo("dQw4w9WgXcQ"))
        val downloader = RangeDownloader(client, OkHttpDownloader.USER_AGENT, chunkSize = 1024 * 1024)

        val full = File.createTempFile("full", ".part").apply { delete() }
        var lastTotal = 0L
        var chunks = 0
        val start = System.currentTimeMillis()
        downloader.download(audio.content, full) { _, total -> lastTotal = total; chunks++ }
        println("Descargado ${full.length()} bytes (${audio.format?.suffix}) en $chunks bloques, ${System.currentTimeMillis() - start} ms")
        assertEquals(lastTotal, full.length())
        assertTrue(chunks > 1)

        // Simula una descarga interrumpida a la mitad y la continúa
        val resumed = File.createTempFile("resumed", ".part")
        resumed.writeBytes(full.readBytes().copyOf((full.length() / 2).toInt()))
        downloader.download(audio.content, resumed) { _, _ -> }
        assertArrayEquals(full.readBytes(), resumed.readBytes())

        full.delete()
        resumed.delete()
    }
}
