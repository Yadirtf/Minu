package com.daniel.reproductor

import android.Manifest
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.reproductor.ui.components.StyledBottomBar
import com.daniel.reproductor.ui.components.Tab
import com.daniel.reproductor.ui.detail.PlaylistDetailScreen
import com.daniel.reproductor.ui.home.HomeScreen
import com.daniel.reproductor.ui.library.LibraryViewModel
import com.daniel.reproductor.ui.player.FullPlayerScreen
import com.daniel.reproductor.ui.player.MiniPlayer
import com.daniel.reproductor.ui.player.PlayerState
import com.daniel.reproductor.ui.player.rememberMediaController
import com.daniel.reproductor.ui.player.rememberPlayerState
import com.daniel.reproductor.ui.playlists.PlaylistsScreen
import com.daniel.reproductor.ui.resolver.ResolverScreen
import com.daniel.reproductor.ui.search.SearchScreen
import com.daniel.reproductor.ui.settings.SettingsSheet
import com.daniel.reproductor.ui.settings.SettingsViewModel
import com.daniel.reproductor.ui.styles.StylePickerScreen
import com.daniel.reproductor.ui.theme.AppStyles
import com.daniel.reproductor.ui.theme.LocalAppStyle
import com.daniel.reproductor.ui.theme.ReproductorTheme
import com.daniel.reproductor.ui.theme.StyleId
import com.daniel.reproductor.ui.theme.rememberArtworkColor
import com.daniel.reproductor.cloud.HubEvent
import com.daniel.reproductor.cloud.ListenMode
import com.daniel.reproductor.ui.together.AccountScreen
import com.daniel.reproductor.ui.together.FloatingReactions
import com.daniel.reproductor.ui.together.IncomingDedicationOverlay
import com.daniel.reproductor.ui.together.ListenBanner
import com.daniel.reproductor.ui.together.TogetherScreen
import com.daniel.reproductor.ui.together.TogetherViewModel
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val lib: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory)
            val settings: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
            val styleId by lib.style.collectAsStateWithLifecycle()
            val controller = rememberMediaController()
            val player = controller?.let { rememberPlayerState(it) }
            val artColor = rememberArtworkColor(if (styleId == StyleId.AURORA) player?.metadata?.artworkUri else null)

            ReproductorTheme(AppStyles.byId(styleId), artColor.takeIf { styleId == StyleId.AURORA }) {
                SystemBarsForStyle()
                RequestNotificationPermission()
                AppRoot(lib, settings, player)
            }
        }
    }
}

/** Íconos de la barra de estado claros u oscuros según el estilo. */
@Composable
private fun SystemBarsForStyle() {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    val dark = LocalAppStyle.current.isDark
    LaunchedEffect(dark) {
        val bars = if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
    }
}

/** La notificación con los controles necesita este permiso desde Android 13. */
@Composable
private fun RequestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

private const val NONE = -1L

@Composable
internal fun AppRoot(
    lib: LibraryViewModel,
    settings: SettingsViewModel,
    player: PlayerState?,
    together: TogetherViewModel = viewModel(factory = TogetherViewModel.Factory),
) {
    val app = LocalContext.current.applicationContext as ReproductorApp
    val hub = app.partnerHub
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var openPlaylist by rememberSaveable { mutableLongStateOf(NONE) }
    var searchInPlaylist by rememberSaveable { mutableLongStateOf(NONE) }
    var searchInPlaylistName by rememberSaveable { mutableStateOf("") }
    var fullPlayer by rememberSaveable { mutableStateOf(false) }
    var showStyles by rememberSaveable { mutableStateOf(false) }
    var resolverTest by rememberSaveable { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAccount by rememberSaveable { mutableStateOf(false) }
    val listen by together.listen.collectAsStateWithLifecycle()
    val partner by together.partner.collectAsStateWithLifecycle()
    val dedications by together.dedications.collectAsStateWithLifecycle()
    val partnerName = partner?.partner?.displayName.orEmpty()
    // Dedicatorias recibidas que todavía no se han visto
    var liveDedicationId by remember { mutableStateOf<String?>(null) }
    val pending = dedications.firstOrNull { it.incoming && it.seenAt == null }
    val styleId by lib.style.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { lib.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { settings.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { together.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) {
        hub.events.collect { e ->
            when (e) {
                is HubEvent.Message -> snackbar.showSnackbar(e.text)
                is HubEvent.IncomingDedication -> if (e.dedication.autoplay) liveDedicationId = e.dedication.id
                is HubEvent.LiveReaction -> Unit
            }
        }
    }
    // Mientras sigues la música de tu pareja, tus controles quedan en pausa
    LaunchedEffect(player) {
        player?.lockedReason = hub::lockedReason
        player?.onLocked = { msg -> lib.message(msg) }
    }

    // Atrás: cierra capas en orden y al final vuelve a Inicio
    BackHandler(enabled = searchInPlaylist != NONE) { searchInPlaylist = NONE }
    BackHandler(enabled = searchInPlaylist == NONE && openPlaylist != NONE) { openPlaylist = NONE }
    BackHandler(enabled = openPlaylist == NONE && tab != Tab.HOME) { tab = Tab.HOME }

    val hasMedia = player != null && player.hasMedia
    val screenKey = when {
        searchInPlaylist != NONE -> "search-$searchInPlaylist"
        openPlaylist != NONE -> "playlist-$openPlaylist"
        else -> tab.name
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.navigationBars)) {
                Crossfade(screenKey, animationSpec = tween(220), label = "screens") { key ->
                    when {
                        key.startsWith("search-") -> SearchScreen(
                            lib = lib, player = player,
                            targetPlaylist = searchInPlaylist to searchInPlaylistName,
                            onBack = { searchInPlaylist = NONE },
                        )
                        key.startsWith("playlist-") -> PlaylistDetailScreen(
                            playlistId = openPlaylist, lib = lib, player = player,
                            onBack = { openPlaylist = NONE },
                            onSearch = { name -> searchInPlaylistName = name; searchInPlaylist = openPlaylist },
                        )
                        key == Tab.PLAYLISTS.name -> PlaylistsScreen(lib, onOpenPlaylist = { openPlaylist = it })
                        key == Tab.SEARCH.name -> SearchScreen(lib = lib, player = player)
                        key == Tab.TOGETHER.name -> TogetherScreen(together, lib, player, onOpenAccount = { showAccount = true })
                        else -> HomeScreen(
                            lib = lib, settings = settings, player = player,
                            onOpenStyles = { showStyles = true },
                            onOpenSettings = { showSettings = true },
                        )
                    }
                }
            }
            if (listen.mode != ListenMode.NONE && listen.mode != ListenMode.INVITED) {
                ListenBanner(listen, partnerName) { tab = Tab.TOGETHER; openPlaylist = NONE; searchInPlaylist = NONE }
            }
            if (hasMedia) MiniPlayer(player!!, onOpen = { fullPlayer = true })
            StyledBottomBar(selected = tab) { selected ->
                tab = selected
                openPlaylist = NONE
                searchInPlaylist = NONE
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (hasMedia) 150.dp else 90.dp))
        FloatingReactions(hub.events)

        AnimatedVisibility(
            visible = fullPlayer && hasMedia,
            enter = slideInVertically(tween(320)) { it } + fadeIn(),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(),
        ) {
            BackHandler { fullPlayer = false }
            player?.let { FullPlayerScreen(it, onClose = { fullPlayer = false }) }
        }

        AnimatedVisibility(visible = showStyles, enter = fadeIn(), exit = fadeOut()) {
            BackHandler { showStyles = false }
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                StylePickerScreen(
                    current = styleId,
                    artwork = player?.metadata?.artworkUri,
                    onPick = lib::setStyle,
                    onBack = { showStyles = false },
                )
            }
        }

        AnimatedVisibility(visible = showAccount, enter = fadeIn(), exit = fadeOut()) {
            BackHandler { showAccount = false }
            AccountScreen(together, onBack = { showAccount = false })
        }

        pending?.let { d ->
            IncomingDedicationOverlay(
                dedication = d,
                playingNow = d.id == liveDedicationId,
                onListen = { together.play(d) },
                onLater = { together.markSeen(d) },
                onReact = { emoji -> together.react(d, emoji) },
            )
        }

        if (resolverTest) {
            BackHandler { resolverTest = false }
            Scaffold { padding -> ResolverScreen(Modifier.padding(padding)) }
        }
    }

    if (showSettings) {
        SettingsSheet(
            vm = settings,
            onDismiss = { showSettings = false },
            onOpenStyles = { showStyles = true },
            onOpenResolverTest = { resolverTest = true },
            onOpenAccount = { showAccount = true },
        )
    }
}
