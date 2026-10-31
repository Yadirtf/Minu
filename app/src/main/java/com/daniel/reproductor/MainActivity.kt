package com.daniel.reproductor

import android.Manifest
import android.content.Intent
import com.daniel.reproductor.cloud.Push
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.reproductor.ui.components.START_TAB
import com.daniel.reproductor.ui.components.StyledBottomBar
import com.daniel.reproductor.ui.components.Tab
import com.daniel.reproductor.ui.detail.PlaylistDetailScreen
import com.daniel.reproductor.ui.home.HomeScreen
import com.daniel.reproductor.ui.library.LibraryViewModel
import com.daniel.reproductor.ui.library.SaveAction
import com.daniel.reproductor.ui.library.SaveToPlaylistHost
import com.daniel.reproductor.ui.library.rememberSaveToPlaylistState
import com.daniel.reproductor.ui.player.FullPlayerScreen
import com.daniel.reproductor.ui.player.MiniPlayer
import com.daniel.reproductor.ui.player.PlayerState
import com.daniel.reproductor.ui.player.rememberMediaController
import com.daniel.reproductor.ui.player.rememberPlayerState
import com.daniel.reproductor.ui.playlists.PlaylistsScreen
import com.daniel.reproductor.ui.profile.LocalProfileEntry
import com.daniel.reproductor.ui.profile.ProfileDrawer
import com.daniel.reproductor.ui.profile.ProfileEntry
import com.daniel.reproductor.ui.profile.ProfileSummary
import com.daniel.reproductor.ui.resolver.ResolverScreen
import com.daniel.reproductor.ui.search.SearchScreen
import com.daniel.reproductor.ui.search.SearchViewModel
import com.daniel.reproductor.ui.settings.SettingsSheet
import com.daniel.reproductor.ui.settings.SettingsViewModel
import com.daniel.reproductor.ui.styles.StylePickerScreen
import com.daniel.reproductor.ui.theme.AppStyles
import com.daniel.reproductor.ui.theme.LocalAppStyle
import com.daniel.reproductor.ui.theme.ReproductorTheme
import com.daniel.reproductor.ui.theme.StyleId
import com.daniel.reproductor.ui.theme.rememberArtworkColor
import com.daniel.reproductor.cloud.DuoDates
import com.daniel.reproductor.cloud.DuoMilestone
import com.daniel.reproductor.cloud.DuoPrefs
import com.daniel.reproductor.cloud.HubEvent
import com.daniel.reproductor.cloud.ListenMode
import com.daniel.reproductor.ui.together.AccountScreen
import com.daniel.reproductor.ui.together.FloatingReactions
import com.daniel.reproductor.ui.together.IncomingDedicationOverlay
import com.daniel.reproductor.ui.together.MilestoneOverlay
import com.daniel.reproductor.ui.together.StreakMoment
import com.daniel.reproductor.ui.together.StreakOverlay
import com.daniel.reproductor.ui.together.ListenBanner
import com.daniel.reproductor.ui.together.TogetherScreen
import com.daniel.reproductor.ui.together.TogetherViewModel
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
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

/** Se tocó una notificación de Minu. */
private fun ComponentActivity.handleIntent(intent: Intent?) {
    if (intent?.getBooleanExtra(Push.EXTRA_LISTEN_INVITE, false) == true) {
        (application as ReproductorApp).partnerHub.onListenInviteNotification()
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
private const val TABS = "tabs"

@Composable
internal fun AppRoot(
    lib: LibraryViewModel,
    settings: SettingsViewModel,
    player: PlayerState?,
    together: TogetherViewModel = viewModel(factory = TogetherViewModel.Factory),
    // La misma instancia que usa la pestaña Buscar, para mandarle una búsqueda desde Inicio
    search: SearchViewModel = viewModel(key = "search-null", factory = SearchViewModel.Factory),
) {
    val app = LocalContext.current.applicationContext as ReproductorApp
    val hub = app.partnerHub
    // Las pestañas viven en un pager: se cambia deslizando o con la barra inferior, y ambos quedan sincronizados
    val pager = rememberPagerState(initialPage = START_TAB.ordinal) { Tab.entries.size }
    // targetPage, no currentPage: la barra resalta el destino apenas el dedo pasa la mitad, como en WhatsApp
    val tab = Tab.entries[pager.targetPage]
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
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
    val account by together.account.collectAsStateWithLifecycle()
    // Dedicatorias recibidas que todavía no se han visto
    var liveDedicationId by remember { mutableStateOf<String?>(null) }
    val pending = dedications.firstOrNull { it.incoming && it.seenAt == null }
    // Mesiversario, aniversario o días redondos: se celebra una vez al abrir la app ese día
    val duo by together.duo.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    var celebrated by remember { mutableStateOf(DuoPrefs.celebrated(app)) }
    val milestone = DuoDates.parse(duo?.origin?.startedOn)?.let { DuoDates.milestoneOn(it, today) }
        ?.takeIf { celebrated != "$today:${it.key}" }
    // La racha a pantalla completa: en peligro, rota o salvada (cada una se ve una vez en este celular)
    var streakSeen by remember { mutableStateOf(0) } // cambia para volver a leer lo visto
    var liveSaved by remember { mutableStateOf(false) }
    val streak = duo?.streak
    val streakMoment = remember(streak, streakSeen, liveSaved) {
        when {
            streak == null -> null
            streak.rescue != null && DuoPrefs.seen(app, "risk") != streak.rescue.day ->
                StreakMoment.AtRisk(streak)
            streak.savedDay == streak.today && (liveSaved || DuoPrefs.seen(app, "saved") != streak.savedDay) ->
                StreakMoment.Saved(streak.count)
            streak.broken != null && streak.broken.lost > 0 && DuoPrefs.seen(app, "broken") != streak.broken.on &&
                runCatching { !LocalDate.parse(streak.broken.on).isBefore(today.minusDays(2)) }.getOrDefault(false) ->
                StreakMoment.Broken(streak.broken, streak.best)
            else -> null
        }
    }
    fun markStreakSeen(moment: StreakMoment) {
        val s = streak ?: return
        when (moment) {
            is StreakMoment.AtRisk -> s.rescue?.let { DuoPrefs.setSeen(app, "risk", it.day) }
            is StreakMoment.Saved -> { DuoPrefs.setSeen(app, "saved", s.today); liveSaved = false }
            is StreakMoment.Broken -> s.broken?.let { DuoPrefs.setSeen(app, "broken", it.on) }
        }
        streakSeen++
    }
    val styleId by lib.style.collectAsStateWithLifecycle()
    val savedIds by lib.savedIds.collectAsStateWithLifecycle()
    val saveSheet = rememberSaveToPlaylistState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { lib.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { settings.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) { together.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) {
        hub.events.collect { e ->
            when (e) {
                is HubEvent.Message -> snackbar.showSnackbar(e.text)
                is HubEvent.IncomingDedication -> if (e.dedication.autoplay) liveDedicationId = e.dedication.id
                is HubEvent.LiveReaction, is HubEvent.AnniversaryChanged -> Unit
                HubEvent.StreakSaved -> liveSaved = true
            }
        }
    }
    // Al cambiar de pestaña se cierra el teclado que haya quedado abierto en Buscar
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { focusManager.clearFocus() } }
    // Mientras sigues la música de tu pareja, tus controles quedan en pausa
    LaunchedEffect(player) {
        player?.lockedReason = hub::lockedReason
        player?.onLocked = { msg -> lib.message(msg) }
    }

    fun goTo(target: Tab) {
        openPlaylist = NONE
        searchInPlaylist = NONE
        scope.launch { pager.animateScrollToPage(target.ordinal) }
    }
    fun closeDrawerThen(action: () -> Unit) {
        action()
        scope.launch { drawer.close() }
    }

    val profile = remember(account, partner) {
        ProfileSummary(
            displayName = account?.displayName,
            partnerName = partner?.partner?.displayName,
            partnerOnline = partner?.partner?.online == true,
        )
    }
    val profileEntry = remember(profile) { ProfileEntry(profile) { scope.launch { drawer.open() } } }

    // Atrás: cierra capas en orden y al final vuelve a Buscar, la pestaña de inicio (el último registrado tiene prioridad)
    BackHandler(enabled = searchInPlaylist != NONE) { searchInPlaylist = NONE }
    BackHandler(enabled = searchInPlaylist == NONE && openPlaylist != NONE) { openPlaylist = NONE }
    BackHandler(enabled = openPlaylist == NONE && tab != START_TAB) { goTo(START_TAB) }
    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }

    val hasMedia = player != null && player.hasMedia
    val screenKey = when {
        searchInPlaylist != NONE -> "search-$searchInPlaylist"
        openPlaylist != NONE -> "playlist-$openPlaylist"
        else -> TABS
    }
    // En Dúo las reacciones viajan por el hilo: ahí no hace falta que además floten por la pantalla
    val duoVisible by rememberUpdatedState(screenKey == TABS && tab == Tab.TOGETHER && partner?.linked == true)

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // El panel solo se arrastra para cerrarlo: abrirlo deslizando chocaría con el cambio de pestañas
        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = drawer.isOpen,
            drawerContent = {
                ProfileDrawer(
                    summary = profile,
                    onViewProfile = { closeDrawerThen { showAccount = true } },
                    onOpenThemes = { closeDrawerThen { showStyles = true } },
                    onOpenSettings = { closeDrawerThen { showSettings = true } },
                )
            },
        ) {
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
                            else -> CompositionLocalProvider(LocalProfileEntry provides profileEntry) {
                                HorizontalPager(pager, key = { Tab.entries[it].name }) { page ->
                                    when (Tab.entries[page]) {
                                        Tab.HOME -> HomeScreen(
                                            lib = lib, settings = settings, player = player,
                                            onOpenPlaylist = { openPlaylist = it },
                                            onSearchOnline = { q -> search.setQuery(q); goTo(Tab.SEARCH) },
                                        )
                                        Tab.PLAYLISTS -> PlaylistsScreen(lib, onOpenPlaylist = { openPlaylist = it })
                                        Tab.SEARCH -> SearchScreen(lib = lib, player = player, vm = search)
                                        Tab.TOGETHER -> TogetherScreen(together, lib, player, onOpenAccount = { showAccount = true })
                                    }
                                }
                            }
                        }
                    }
                }
                if (listen.mode != ListenMode.NONE && listen.mode != ListenMode.INVITED) {
                    ListenBanner(listen, partnerName) { goTo(Tab.TOGETHER) }
                }
                if (hasMedia) MiniPlayer(player!!, onOpen = { fullPlayer = true })
                StyledBottomBar(selected = tab, onSelect = ::goTo)
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (hasMedia) 150.dp else 90.dp))
        FloatingReactions(hub.events) { duoVisible }

        AnimatedVisibility(
            visible = fullPlayer && hasMedia,
            enter = slideInVertically(tween(320)) { it } + fadeIn(),
            exit = slideOutVertically(tween(260)) { it } + fadeOut(),
        ) {
            BackHandler { fullPlayer = false }
            player?.let { p ->
                FullPlayerScreen(
                    p, onClose = { fullPlayer = false },
                    save = SaveAction(saved = p.currentVideoId in savedIds) { saveSheet.open(p.currentSong()) },
                )
            }
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

        val moment = streakMoment
        if (moment != null && pending == null && partner?.linked == true) {
            StreakOverlay(
                moment = moment,
                partner = partnerName,
                onChoose = { type ->
                    val current = (moment as? StreakMoment.AtRisk)?.streak?.rescue?.type
                    if (current == type) together.showCapsule(true) else together.chooseRescue(type)
                    markStreakSeen(moment)
                    if (type == "capsule") goTo(Tab.TOGETHER)
                },
                onGiveUp = { together.giveUpRescue(); markStreakSeen(moment) },
                onRetie = { markStreakSeen(moment); goTo(Tab.TOGETHER) },
                onDismiss = { markStreakSeen(moment) },
            )
        } else if (milestone is DuoMilestone.Anniversary && pending == null && partner?.linked == true) {
            // El aniversario no se celebra aquí: es un regalo envuelto en Dúo que se abre con el abrazo de los dos
            LaunchedEffect(milestone.key) {
                val mark = "$today:${milestone.key}"
                DuoPrefs.setCelebrated(app, mark)
                celebrated = mark
                if (duo?.anniversaries?.any { it.years == milestone.years && it.opened } != true) {
                    together.showAnniversary(milestone.years)
                    goTo(Tab.TOGETHER)
                }
            }
        } else if (milestone != null && pending == null && partner?.linked == true) {
            MilestoneOverlay(
                milestone = milestone,
                me = account?.displayName.orEmpty().ifBlank { "Tú" },
                partner = partnerName,
                story = duo?.origin?.story.orEmpty(),
                song = duo?.origin?.song,
                onPlay = { together.playSong(it) },
                onDismiss = {
                    val mark = "$today:${milestone.key}"
                    DuoPrefs.setCelebrated(app, mark)
                    celebrated = mark
                },
                onOpenTape = { month ->
                    val mark = "$today:${milestone.key}"
                    DuoPrefs.setCelebrated(app, mark)
                    celebrated = mark
                    together.requestTape(month)
                    goTo(Tab.TOGETHER)
                },
            )
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

    // Si se guarda mientras escuchan juntos, a la pareja le llega el aviso
    SaveToPlaylistHost(saveSheet, lib, onSaved = together::onSongSaved)

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
