package com.daniel.reproductor.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe

/** Prueba real contra YouTube (necesita internet). Sirve para saber si el extractor sigue funcionando. */
class YouTubeResolverTest {

    @Test
    fun resolvesTitleAndAudioUrl() = runBlocking {
        NewPipe.init(OkHttpDownloader(OkHttpClient()))
        val resolver = YouTubeResolver()
        val info = resolver.getInfo("https://youtu.be/jNQXAC9IVRw")
        val audio = resolver.bestAudio(info)
        println("Título: ${info.name} | ${info.uploaderName} | ${info.duration}s")
        println("Audio: ${audio.format?.name} ${audio.averageBitrate} kbps -> ${audio.content.take(80)}…")
        assertTrue(info.name.isNotBlank())
        assertTrue(audio.content.startsWith("https://"))
    }
}

class YouTubeSearchTest {
    @Test
    fun searchesVideosAndMusic() = runBlocking {
        NewPipe.init(OkHttpDownloader(OkHttpClient()))
        val resolver = YouTubeResolver()
        val videos = resolver.search("queen bohemian rhapsody", SongSource.YOUTUBE)
        val songs = resolver.search("queen bohemian rhapsody", SongSource.MUSIC)
        println("Videos: ${videos.size}, primero: ${videos.firstOrNull()?.title}")
        println("YT Music: ${songs.size}, primero: ${songs.firstOrNull()?.title} (${songs.firstOrNull()?.durationSec}s)")
        assertTrue(videos.isNotEmpty())
        assertTrue(songs.isNotEmpty())
    }
}
