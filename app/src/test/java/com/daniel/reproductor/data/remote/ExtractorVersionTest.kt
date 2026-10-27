package com.daniel.reproductor.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractorVersionTest {
    @Test
    fun comparesVersions() {
        assertTrue(ExtractorUpdateChecker.isNewer("v0.26.6", "v0.26.5"))
        assertTrue(ExtractorUpdateChecker.isNewer("v0.27.0", "v0.26.5"))
        assertTrue(ExtractorUpdateChecker.isNewer("v1.0", "v0.26.5"))
        assertFalse(ExtractorUpdateChecker.isNewer("v0.26.5", "v0.26.5"))
        assertFalse(ExtractorUpdateChecker.isNewer("v0.25.9", "v0.26.5"))
    }
}
