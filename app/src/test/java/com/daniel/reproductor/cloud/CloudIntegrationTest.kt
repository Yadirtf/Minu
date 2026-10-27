package com.daniel.reproductor.cloud

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.daniel.reproductor.ReproductorApp
import com.daniel.reproductor.data.db.Playlist
import com.daniel.reproductor.data.db.Song
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.daniel.reproductor.ui.library.LibraryViewModel
import com.daniel.reproductor.ui.theme.AppStyles
import com.daniel.reproductor.ui.theme.ReproductorTheme
import com.daniel.reproductor.ui.theme.StyleId
import com.daniel.reproductor.ui.together.TogetherScreen
import com.daniel.reproductor.ui.together.TogetherViewModel
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Prueba de punta a punta contra el servidor real (con MongoDB en memoria):
 *   cd server && node test/dev-server.js 3999
 *   ./gradlew testDebugUnitTest -Pcloud --tests "*CloudIntegrationTest*"
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xhdpi")
class CloudIntegrationTest {

    @get:Rule val compose = createComposeRule()

    private val app: ReproductorApp = ApplicationProvider.getApplicationContext()
    private val server = "http://localhost:3999"
    private val suffix = System.currentTimeMillis().toString().takeLast(6)
    private val http = OkHttpClient()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        app.session.serverUrl = server
    }

    private val songA = Song("dQw4w9WgXcQ", "Never Gonna Give You Up", "Rick Astley", 213, "https://i.ytimg.com/vi/dQw4w9WgXcQ/hq.jpg")
    private val songB = Song("jNQXAC9IVRw", "Me at the zoo", "jawed", 19, "https://i.ytimg.com/vi/jNQXAC9IVRw/hq.jpg")

    @Test
    fun restoreLibraryOnNewPhone(): Unit = runBlocking {
        val dao = app.database.musicDao()
        val id = dao.insertPlaylist(Playlist(name = "Para el carro"))
        listOf(songA, songB).forEach { dao.insertSong(it); dao.appendToPlaylist(id, it.videoId) }

        // Crear cuenta: la biblioteca local se sube
        app.session.signIn(app.cloudApi.register("dan$suffix", "clave123", "Daniel", "dev"))
        app.cloudSync.syncNow()
        val (remote, version) = app.cloudApi.getLibrary()
        assertTrue(version > 0)
        assertEquals("Para el carro", remote!!.getJSONArray("playlists").getJSONObject(0).getString("name"))

        // "Perdí el celular": todo borrado y se inicia sesión de nuevo
        app.session.signOut()
        dao.deletePlaylistsExcept(emptyList())
        dao.getOrphanSongs().forEach { dao.deleteSong(it.videoId) }
        assertEquals(0, dao.getPlaylists().size)

        app.session.signIn(app.cloudApi.login("dan$suffix", "clave123"))
        app.cloudSync.syncNow()

        val restored = dao.getPlaylists().single()
        assertEquals("Para el carro", restored.name)
        assertEquals(listOf(songA.videoId, songB.videoId), dao.getSongsOrdered(restored.id).map { it.videoId })
    }

    @Test
    fun dedicationArrivesLive(): Unit = runBlocking {
        // Daniel en este celular, su novia por HTTP directo
        app.session.signIn(app.cloudApi.register("yo$suffix", "clave123", "Daniel", "dev"))
        val novia = post("/auth/register", JSONObject().put("username", "ella$suffix").put("password", "clave123")
            .put("displayName", "Ana").put("inviteCode", "dev"), null).getString("token")

        val hub = app.partnerHub
        hub.requestPartner("ella$suffix")
        post("/partner/accept", JSONObject(), novia)
        hub.refresh()
        waitUntil { hub.partner.value?.linked == true }
        assertEquals("Ana", hub.partner.value!!.partner!!.displayName)

        // Con la app abierta se conecta en vivo
        hub.setForeground(true)
        waitUntil { hub.connected.value }

        val song = JSONObject().put("videoId", songB.videoId).put("title", songB.title).put("uploader", songB.uploader)
        post("/dedications", JSONObject().put("song", song).put("message", "Para ti ❤️").put("autoplay", false), novia)
        waitUntil { hub.dedications.value.any { it.message == "Para ti ❤️" && it.incoming } }

        // Reacción de vuelta
        val d = hub.dedications.value.first { it.incoming }
        hub.react(d, "😍")
        waitUntil { hub.dedications.value.first { it.id == d.id }.reactions.any { it.emoji == "😍" } }
        hub.markSeen(d)

        // Captura de la pestaña Juntos con la pareja vinculada, en dos estilos
        val vm = TogetherViewModel(app.session, app.cloudApi, app.cloudSync, hub)
        val lib = LibraryViewModel(app.repository, app.files, app.settings, app.network)
        var style by mutableStateOf(StyleId.NEON)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ReproductorTheme(AppStyles.byId(style)) {
                Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                    TogetherScreen(vm, lib, null, onOpenAccount = {})
                }
            }
        }
        for (id in listOf(StyleId.NEON, StyleId.VINYL)) {
            style = id
            repeat(15) { compose.mainClock.advanceTimeBy(100); shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(30) }
            compose.onRoot().captureRoboImage(File("build/screenshots/juntos_${id.name.lowercase()}.png").path)
        }
        hub.setForeground(false)
    }

    private fun post(path: String, body: JSONObject, token: String?): JSONObject {
        val req = Request.Builder().url(server + path)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .apply { token?.let { header("Authorization", "Bearer $it") } }
            .build()
        http.newCall(req).execute().use { res ->
            val text = res.body.string()
            assertTrue("$path -> ${res.code} $text", res.isSuccessful)
            return JSONObject(text)
        }
    }

    /** Espera dejando correr el hilo principal (donde vive la lógica de la app). */
    private fun waitUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            if (System.currentTimeMillis() > end) throw AssertionError("Se agotó el tiempo esperando")
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
    }
}
