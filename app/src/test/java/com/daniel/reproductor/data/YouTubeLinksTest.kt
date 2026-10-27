package com.daniel.reproductor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeLinksTest {

    private val id = "dQw4w9WgXcQ"

    @Test
    fun recognizesCommonFormats() {
        listOf(
            "https://youtu.be/$id",
            "https://youtu.be/$id?si=abc123",
            "https://www.youtube.com/watch?v=$id",
            "https://m.youtube.com/watch?feature=share&v=$id&t=42",
            "https://music.youtube.com/watch?v=$id&list=RDAMVM",
            "https://www.youtube.com/shorts/$id",
            "https://www.youtube.com/embed/$id",
            "https://www.youtube.com/live/$id?feature=share",
            "youtube.com/watch?v=$id",
            "Mira este video: https://youtu.be/$id compartido desde YouTube",
            id,
        ).forEach { assertEquals(it, id, YouTubeLinks.extractVideoId(it)) }
    }

    @Test
    fun rejectsOtherLinks() {
        assertNull(YouTubeLinks.extractVideoId("https://example.com/watch?v=$id"))
        assertNull(YouTubeLinks.extractVideoId("https://www.youtube.com/playlist?list=PL123"))
        assertNull(YouTubeLinks.extractVideoId("hola"))
    }
}
