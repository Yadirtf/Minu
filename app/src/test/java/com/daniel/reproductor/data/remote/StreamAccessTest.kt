package com.daniel.reproductor.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe

/** Comprueba que la URL de audio resuelta se puede leer como lo hará ExoPlayer (con Range). */
class StreamAccessTest {

    @Test
    fun audioUrlIsReadable() = runBlocking {
        val client = OkHttpClient()
        NewPipe.init(OkHttpDownloader(client))
        val resolver = YouTubeResolver()
        val audio = resolver.bestAudio(resolver.getInfo("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))

        val start = System.currentTimeMillis()
        val request = Request.Builder().url(audio.content)
            .header("User-Agent", OkHttpDownloader.USER_AGENT)
            .header("Range", "bytes=0-")
            .build()
        client.newCall(request).execute().use { r ->
            var read = 0L
            val buf = ByteArray(64 * 1024)
            val input = r.body.byteStream()
            while (read < 1_000_000) {
                val n = input.read(buf)
                if (n < 0) break
                read += n
            }
            val ms = System.currentTimeMillis() - start
            println("HTTP ${r.code} length=${r.header("Content-Length")} leídos=$read en ${ms}ms")
            assertTrue(r.code in 200..206)
            assertTrue(read >= 500_000)
        }
    }
}
