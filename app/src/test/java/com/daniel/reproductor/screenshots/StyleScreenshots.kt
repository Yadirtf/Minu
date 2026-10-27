package com.daniel.reproductor.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.daniel.reproductor.AppRoot
import com.daniel.reproductor.ReproductorApp
import com.daniel.reproductor.data.db.DownloadState
import com.daniel.reproductor.data.db.Playlist
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.ui.library.LibraryViewModel
import com.daniel.reproductor.ui.player.FullPlayerScreen
import com.daniel.reproductor.ui.player.PlayerState
import com.daniel.reproductor.ui.playlists.PlaylistsScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.daniel.reproductor.ui.settings.SettingsViewModel
import com.daniel.reproductor.ui.styles.StylePickerScreen
import com.daniel.reproductor.ui.theme.AppStyles
import com.daniel.reproductor.ui.theme.ReproductorTheme
import com.daniel.reproductor.ui.theme.StyleId
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import coil3.ImageLoader
import coil3.SingletonImageLoader
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.io.File

/**
 * Genera capturas de cada estilo (Inicio y reproductor) sin necesidad de un teléfono.
 * Ejecutar con: ./gradlew testDebugUnitTest -Pscreenshots --tests "*StyleScreenshots*"
 * Las imágenes quedan en app/build/screenshots/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// API 27: Robolectric no soporta ImageDecoder, así Coil usa BitmapFactory y salen las portadas
@Config(sdk = [27], qualifiers = "w393dp-h852dp-xhdpi")
class StyleScreenshots {

    @get:Rule val compose = createComposeRule()

    private val app: ReproductorApp = ApplicationProvider.getApplicationContext()
    private lateinit var songs: List<Song>

    private val samples = listOf(
        Triple("Bohemian Rhapsody", "Queen", 0xFFE53935 to 0xFF3949AB),
        Triple("Blinding Lights", "The Weeknd", 0xFFFF6F00 to 0xFFD81B60),
        Triple("Clocks", "Coldplay", 0xFF00ACC1 to 0xFF1A237E),
        Triple("Billie Jean", "Michael Jackson", 0xFF212121 to 0xFFFFC107),
        Triple("Rolling in the Deep", "Adele", 0xFF5E35B1 to 0xFF00897B),
        Triple("Smells Like Teen Spirit", "Nirvana", 0xFF43A047 to 0xFFFFEB3B),
        Triple("La Bachata", "Manuel Turizo", 0xFFEC407A to 0xFF7E57C2),
        Triple("Vivir Mi Vida", "Marc Anthony", 0xFFFFA726 to 0xFF26A69A),
    )

    @Before
    fun setUp(): Unit = runBlocking {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        // Carga de imágenes síncrona para que las portadas salgan en la captura
        SingletonImageLoader.setUnsafe(ImageLoader.Builder(app).coroutineContext(Dispatchers.Unconfined).build())
        forceOnline()
        val dao = app.database.musicDao()
        songs = samples.mapIndexed { i, (title, artist, colors) ->
            Song(
                videoId = "video%07d".format(i),
                title = title, uploader = artist,
                durationSec = 180L + i * 37,
                thumbnailUrl = "file:///" + cover(i, colors.first, colors.second).absolutePath.replace(File.separatorChar, '/'),
                downloadState = if (i % 3 == 0) DownloadState.DONE else DownloadState.NONE,
            )
        }
        songs.reversed().forEach { dao.insertSong(it) }
        val rock = dao.insertPlaylist(Playlist(name = "Rock clásico"))
        val latin = dao.insertPlaylist(Playlist(name = "Para bailar"))
        val focus = dao.insertPlaylist(Playlist(name = "Concentración"))
        songs.take(5).forEach { dao.appendToPlaylist(rock, it.videoId) }
        songs.takeLast(2).forEach { dao.appendToPlaylist(latin, it.videoId) }
        dao.appendToPlaylist(focus, songs[2].videoId)
    }

    @Test
    fun allStyles() {
        val player = PlayerState(FakePlayer(songs), app.files).apply { refresh() }
        var style by mutableStateOf(StyleId.NEON)
        var screen by mutableStateOf("home")
        val lib = LibraryViewModel(app.repository, app.files, app.settings, app.network)
        val settings = SettingsViewModel(app.repository, app.settings, app.updateChecker, app.contentResolver)

        compose.mainClock.autoAdvance = false
        compose.setContent {
            ReproductorTheme(AppStyles.byId(style), artworkColor = androidx.compose.ui.graphics.Color(0xFFE53935).takeIf { style == StyleId.AURORA }) {
                when (screen) {
                    "player" -> FullPlayerScreen(player, onClose = {})
                    "playlists" -> androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.background(androidx.compose.material3.MaterialTheme.colorScheme.background)) {
                        PlaylistsScreen(lib, onOpenPlaylist = {})
                    }
                    "styles" -> StylePickerScreen(style, player.metadata.artworkUri, onPick = {}, onBack = {})
                    else -> AppRoot(lib, settings, player)
                }
            }
        }
        for (id in StyleId.entries) {
            style = id
            app.settings.setStyle(id)
            for (s in listOf("home", "player", "playlists")) {
                screen = s
                settle()
                compose.onRoot().captureRoboImage(File("build/screenshots/${id.name.lowercase()}_$s.png").path)
            }
        }
        style = StyleId.NEON
        screen = "styles"
        settle()
        compose.onRoot().captureRoboImage(File("build/screenshots/estilos.png").path)
    }

    @Test
    fun logo() {
        compose.setContent {
            androidx.compose.foundation.layout.Row(
                androidx.compose.ui.Modifier.background(androidx.compose.ui.graphics.Color(0xFF2B2D35))
                    .padding(16.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                LogoPreview(androidx.compose.foundation.shape.CircleShape, 96.dp)
                LogoPreview(androidx.compose.foundation.shape.RoundedCornerShape(30), 96.dp)
                LogoPreview(androidx.compose.foundation.shape.CircleShape, 60.dp)
                // Ícono temático (Android 13+)
                androidx.compose.foundation.layout.Box(
                    androidx.compose.ui.Modifier.size(60.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(androidx.compose.ui.graphics.Color(0xFFD7E3FF)),
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) {
                    androidx.compose.foundation.Image(
                        androidx.compose.ui.res.painterResource(com.daniel.reproductor.R.drawable.ic_launcher_monochrome), null,
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(androidx.compose.ui.graphics.Color(0xFF1B3A6B)),
                        modifier = androidx.compose.ui.Modifier.requiredSize(90.dp),
                    )
                }
            }
        }
        compose.onRoot().captureRoboImage(File("build/screenshots/logo.png").path)
    }

    /** Deja correr animaciones y cargas de imágenes un rato con el reloj pausado. */
    private fun settle() {
        repeat(25) {
            compose.mainClock.advanceTimeBy(120)
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(40)
        }
    }

    private fun forceOnline() {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, caps)
    }

    /** Portada de ejemplo: degradado con círculos, al estilo de una carátula. */
    private fun cover(i: Int, from: Long, to: Long): File {
        val size = 360
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), from.toInt(), to.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        paint.shader = null
        paint.color = 0x33FFFFFF
        canvas.drawCircle(size * (0.3f + 0.08f * i % 0.5f), size * 0.65f, size * 0.35f, paint)
        paint.color = 0x22000000
        canvas.drawCircle(size * 0.75f, size * (0.25f + 0.05f * i), size * 0.22f, paint)
        val file = File(app.filesDir, "cover_$i.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }
}

/** Reproductor falso: la cola de ejemplo, sonando la primera canción a 2:10. */
@androidx.annotation.OptIn(UnstableApi::class)
private class FakePlayer(songs: List<Song>) : SimpleBasePlayer(Looper.getMainLooper()) {
    private val items = songs.map { song ->
        MediaItemData.Builder(song.videoId)
            .setMediaItem(
                MediaItem.Builder().setMediaId(song.videoId).setMediaMetadata(
                    MediaMetadata.Builder().setTitle(song.title).setArtist(song.uploader)
                        .setArtworkUri(Uri.parse(song.thumbnailUrl)).build()
                ).build()
            )
            .setDurationUs(song.durationSec * 1_000_000)
            .build()
    }

    override fun getState(): State = State.Builder()
        .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
        .setPlaylist(items)
        .setCurrentMediaItemIndex(0)
        .setContentPositionMs(130_000)
        .setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        .setPlaybackState(Player.STATE_READY)
        .setShuffleModeEnabled(true)
        .setRepeatMode(Player.REPEAT_MODE_ALL)
        .build()

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> = Futures.immediateVoidFuture()
}

/** El ícono adaptable visto con una máscara: solo se ve el 72/108 central, como en el teléfono. */
@androidx.compose.runtime.Composable
private fun LogoPreview(shape: androidx.compose.ui.graphics.Shape, size: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier.size(size).clip(shape),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        val full = androidx.compose.ui.Modifier.requiredSize(size * 1.5f)
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.daniel.reproductor.R.drawable.ic_launcher_background), null, full)
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.daniel.reproductor.R.drawable.ic_launcher_foreground), null, full)
    }
}
