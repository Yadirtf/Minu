package com.daniel.reproductor.ui.together

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daniel.reproductor.cloud.Dedication
import com.daniel.reproductor.cloud.DuoDates
import com.daniel.reproductor.cloud.DuoInfo
import com.daniel.reproductor.cloud.DuoStreak
import com.daniel.reproductor.cloud.MissionState
import com.daniel.reproductor.cloud.ListenMode
import com.daniel.reproductor.cloud.ListenSession
import com.daniel.reproductor.cloud.PartnerInfo
import com.daniel.reproductor.ui.components.ArtworkImage
import com.daniel.reproductor.ui.components.EqualizerBars
import com.daniel.reproductor.ui.components.PillButton
import com.daniel.reproductor.ui.components.ScreenTitle
import com.daniel.reproductor.ui.library.LibraryViewModel
import com.daniel.reproductor.ui.library.SaveAction
import com.daniel.reproductor.ui.library.SaveToPlaylistButton
import com.daniel.reproductor.ui.library.SaveToPlaylistHost
import com.daniel.reproductor.ui.library.rememberSaveToPlaylistState
import com.daniel.reproductor.ui.player.PlayerState
import com.daniel.reproductor.ui.profile.ProfileButton
import com.daniel.reproductor.ui.theme.LocalAppStyle
import com.daniel.reproductor.ui.together.minu.MinuDailySheet
import com.daniel.reproductor.ui.together.minu.MinuEntry
import com.daniel.reproductor.ui.together.minu.MinuJarSheet
import com.daniel.reproductor.ui.together.minu.MinuNewsSheet
import com.daniel.reproductor.ui.together.minu.MinuQuestionSheet
import com.daniel.reproductor.ui.together.minu.MinuHouse
import com.daniel.reproductor.ui.together.minu.MinuHouseActions
import com.daniel.reproductor.ui.together.minu.MinuCareDialog
import com.daniel.reproductor.ui.together.minu.MinuPromisesSheet
import com.daniel.reproductor.ui.together.minu.MinuTruceInvite
import com.daniel.reproductor.ui.together.minu.MinuTruceSheet
import com.daniel.reproductor.ui.together.minu.MinuUnsaidSheet
import com.daniel.reproductor.ui.together.minu.PromiseActions
import com.daniel.reproductor.ui.together.minu.UnsaidActions
import com.daniel.reproductor.ui.together.minu.MinuTermsSheet
import com.daniel.reproductor.ui.together.minu.MinuVoice
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

private val AIRMAIL_BLUE = Color(0xFF3D6BFF)

/**
 * Pestaña Dúo: el rincón de la pareja. Arriba el hilo rojo que los une (con gestos), luego escuchar
 * juntos y dedicar, los detalles que se lanzan por el hilo y las cartas (dedicatorias).
 */
@Composable
fun TogetherScreen(vm: TogetherViewModel, lib: LibraryViewModel, player: PlayerState?, onOpenAccount: () -> Unit) {
    val account by vm.account.collectAsStateWithLifecycle()
    val partner by vm.partner.collectAsStateWithLifecycle()
    val dedications by vm.dedications.collectAsStateWithLifecycle()
    val listen by vm.listen.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val savedIds by lib.savedIds.collectAsStateWithLifecycle()
    var dedicating by remember { mutableStateOf(false) }
    var confirmUnlink by remember { mutableStateOf(false) }
    var editingOrigin by remember { mutableStateOf(false) }
    var showMemories by remember { mutableStateOf(false) }
    val capsuleOpen by vm.capsuleOpen.collectAsStateWithLifecycle()
    val openTape by vm.openTape.collectAsStateWithLifecycle()
    val anniversaryOpen by vm.anniversaryOpen.collectAsStateWithLifecycle()
    val duo by vm.duo.collectAsStateWithLifecycle()
    var showMinuTerms by rememberSaveable { mutableStateOf(false) }
    var showMinuHouse by rememberSaveable { mutableStateOf(false) }
    var minuTermsOffered by rememberSaveable { mutableStateOf(false) }
    var dailySheet by rememberSaveable { mutableStateOf<MinuDailySheet?>(null) }
    val minuContent by vm.minuContent.collectAsStateWithLifecycle()
    val pastQuestions by vm.pastQuestions.collectAsStateWithLifecycle()
    val saveSheet = rememberSaveToPlaylistState()
    val save = player?.takeIf { it.hasMedia }?.let { p ->
        SaveAction(saved = p.currentVideoId in savedIds) { saveSheet.open(p.currentSong()) }
    }

    val user = account
    val status = partner
    val myName = user?.displayName?.ifBlank { user.username } ?: "Tú"
    val linkedPartner = status?.partner?.takeIf { user != null && status.linked }
    val minu = duo?.minu?.takeIf { linkedPartner != null }

    // Al vincularse (o si aún no lo has hecho) Minu se presenta una vez con sus términos
    LaunchedEffect(minu?.terms?.mine) {
        if (minu != null && !minu.terms.mine && !minuTermsOffered) {
            minuTermsOffered = true
            showMinuTerms = true
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp, bottom = 32.dp,
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileButton(Modifier.padding(end = 14.dp))
                ScreenTitle(
                    "Dúo",
                    subtitle = linkedPartner?.let { "$myName & ${it.displayName}" } ?: "Un rincón para dos",
                )
            }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }

        item {
            DuoHero(
                me = myName,
                partner = linkedPartner,
                playing = player?.isPlaying == true,
                events = vm.events,
                onHeartbeat = { vm.sendHeartbeats(it) },
                onHug = { vm.sendLiveReaction(TogetherViewModel.HUG) },
                streak = duo?.streak?.takeIf { linkedPartner != null },
                pet = minu?.pet,
                onPetTap = { showMinuHouse = true },
            )
        }
        if (linkedPartner != null) {
            minu?.let { m ->
                item {
                    MinuEntry(
                        m, linkedPartner.displayName,
                        onOpenTerms = { showMinuTerms = true },
                        onOpenHouse = { showMinuHouse = true },
                        onTouch = { vm.sendTouch(it) },
                        onOpenDaily = { sheet ->
                            dailySheet = sheet
                            vm.loadMinuContent()
                        },
                    )
                }
            }
            item { OriginLine(duo, onEdit = { editingOrigin = true }) }
            duo?.anniversaries?.lastOrNull { !it.opened && !it.beforeMinu }?.let { gift ->
                item { GiftCard(gift.years, linkedPartner.displayName) { vm.showAnniversary(gift.years) } }
            }
            item { MemoriesEntry { showMemories = true } }
            missionToShow(duo)?.let { (month, state) ->
                item { MissionCard(month, state, linkedPartner.displayName) { done -> vm.markMission(month, done) } }
            }
        }

        when {
            user == null -> item {
                InfoCard(
                    "Su rincón empieza aquí 💞",
                    "Crea tu cuenta para atar tu hilo con tu pareja: se dedican canciones, escuchan lo mismo al " +
                        "mismo tiempo y se mandan latidos. También respalda tu música en la nube.",
                ) {
                    PillButton("Crear cuenta o iniciar sesión", Icons.Default.Cloud, onOpenAccount, Modifier.fillMaxWidth())
                }
            }
            status == null -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            status.status == "none" -> item { LinkPartner(vm, user.username) }
            status.status == "sent" -> item {
                InfoCard(
                    "Esperando a ${status.partner?.displayName}",
                    "Le enviaste una invitación. Cuando abra Minu y la acepte, el hilo quedará atado.",
                ) { TextButton(onClick = vm::unlinkPartner) { Text("Cancelar invitación") } }
            }
            status.status == "received" -> item {
                InfoCard(
                    "${status.partner?.displayName} quiere atar su hilo contigo 💞",
                    "Podrán dedicarse canciones, escuchar música al mismo tiempo y mandarse latidos.",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PillButton("Aceptar", Icons.Default.Favorite, vm::acceptPartner, Modifier.weight(1f))
                        PillButton("Rechazar", Icons.Default.LinkOff, vm::unlinkPartner, Modifier.weight(1f), primary = false)
                    }
                }
            }
            linkedPartner != null -> {
                val p = linkedPartner
                duo?.streak?.takeIf { it.inDanger }?.let { streak -> item { RescueCard(vm, streak, p, listen) } }
                item { DuoActions(vm, p, listen, onDedicate = { dedicating = true }) }
                if (listen.mode == ListenMode.INVITED || listen.active) {
                    item { ListenCard(vm, p, listen, player, save) }
                }
                item { ReactionTray(p, onSend = vm::sendLiveReaction) }
                item { LettersHeader(dedications.size) }
                if (dedications.isEmpty()) {
                    item {
                        Text(
                            "Aún no hay cartas. Dedícale la primera canción 💌",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                itemsIndexed(dedications, key = { _, d -> d.id }) { i, d -> LetterCard(d, i, p.displayName, vm) }
                item {
                    TextButton(onClick = { confirmUnlink = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.LinkOff, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Desvincular")
                    }
                }
            }
        }
    }

    SaveToPlaylistHost(saveSheet, lib, onSaved = vm::onSongSaved)

    if (dedicating) {
        DedicateSheet(
            lib = lib, player = player,
            partnerOnline = partner?.partner?.online == true,
            partnerName = partner?.partner?.displayName.orEmpty(),
            onDismiss = { dedicating = false },
            onSend = { song, message, autoplay -> vm.sendDedication(song, message, autoplay); dedicating = false },
        )
    }

    if (capsuleOpen) {
        CapsuleScreen(vm, lib, partner?.partner?.displayName.orEmpty(), onDismiss = { vm.showCapsule(false) })
    }
    if (showMemories || openTape != null) {
        MemoriesScreen(
            vm, duo, partner?.partner?.displayName.orEmpty(), startTape = openTape, playing = player?.isPlaying == true,
            onDismiss = { showMemories = false; vm.requestTape(null) },
        )
    }
    anniversaryOpen?.let { years ->
        AnniversaryScreen(vm, years, myName, partner?.partner?.displayName.orEmpty(), onDismiss = { vm.showAnniversary(null) })
    }

    if (showMinuTerms && minu != null && !minu.terms.mine) {
        MinuTermsSheet(
            partnerName = linkedPartner?.displayName.orEmpty(),
            update = minu.terms.update,
            onAccept = { vm.acceptMinuTerms(); showMinuTerms = false },
            onDismiss = { showMinuTerms = false },
        )
    }
    val pet = minu?.pet
    if (showMinuHouse && pet != null) {
        MinuHouse(
            pet, linkedPartner?.displayName.orEmpty(),
            MinuHouseActions(
                propose = { kind, value -> vm.proposePet(kind, value) },
                accept = { vm.acceptPetProposal(it) },
                drop = { vm.dropPetProposal(it) },
                erase = { vm.eraseMinuData() },
                help = { vm.loadMinuContent(); vm.showHelp() },
            ),
            onDismiss = { showMinuHouse = false },
        )
    }

    // Conexión diaria: Pregunta del día, frasco de gracias y buenas noticias
    val daily = minu?.daily
    if (pet != null && daily != null) {
        val name = linkedPartner?.displayName.orEmpty()
        when (dailySheet) {
            MinuDailySheet.QUESTION -> MinuQuestionSheet(
                pet, daily.question, name,
                reactions = minuContent?.questionReactions?.takeIf { it.isNotEmpty() } ?: MinuVoice.QUESTION_REACTIONS,
                past = pastQuestions,
                onAnswer = { vm.answerQuestion(it) },
                onReact = { vm.reactQuestion(it) },
                onLoadPast = { vm.loadPastQuestions() },
                onDismiss = { dailySheet = null },
            )
            MinuDailySheet.JAR -> MinuJarSheet(pet, daily.jar, name, onGive = { vm.giveThanks(it) }, onDismiss = { dailySheet = null })
            MinuDailySheet.NEWS -> MinuNewsSheet(
                pet, daily.news, name,
                cheers = minuContent?.cheers?.takeIf { it.isNotEmpty() } ?: MinuVoice.CHEERS,
                onShare = { vm.shareNews(it) },
                onCheer = { id, text -> vm.cheerNews(id, text) },
                onDismiss = { dailySheet = null },
            )
            // Confianza y reparación
            MinuDailySheet.UNSAID -> minu?.trust?.let { trust ->
                MinuUnsaidSheet(
                    pet, trust.unsaid, name,
                    feelStarters = minuContent?.feelStarters?.takeIf { it.isNotEmpty() } ?: MinuVoice.FEEL_STARTERS,
                    needStarters = minuContent?.needStarters?.takeIf { it.isNotEmpty() } ?: MinuVoice.NEED_STARTERS,
                    actions = UnsaidActions(
                        send = { feel, whenText, need, onSent -> vm.sendUnsaid(feel, whenText, need, onSent) },
                        open = { vm.openUnsaid(it) },
                        thank = { vm.thankUnsaid(it) },
                        reply = { id, text -> vm.replyUnsaid(id, text) },
                    ),
                    onDismiss = { dailySheet = null },
                )
            }
            MinuDailySheet.PROMISES -> minu?.trust?.let { trust ->
                MinuPromisesSheet(
                    pet, trust.promises, name,
                    PromiseActions(
                        make = { text, dueAt, onMade -> vm.makePromise(text, dueAt, onMade) },
                        keep = { vm.keepPromise(it) },
                        confirm = { vm.confirmPromise(it) },
                        drop = { vm.dropPromise(it) },
                    ),
                    onDismiss = { dailySheet = null },
                )
            }
            MinuDailySheet.TRUCE -> {
                val truce = minu?.trust?.truce
                if (truce == null) {
                    MinuTruceInvite(pet, name, onStart = { vm.startTruce() }, onDismiss = { dailySheet = null })
                } else {
                    MinuTruceSheet(
                        pet, truce, name,
                        repairs = minuContent?.repairs?.takeIf { it.isNotEmpty() } ?: MinuVoice.REPAIRS,
                        onExtend = { vm.extendTruce() },
                        onReady = { vm.readyTruce() },
                        onRepair = { vm.repairTruce(it) },
                        onDismiss = { dailySheet = null },
                    )
                }
            }
            null -> Unit
        }
    }

    // Cuidado: encima de cualquier hoja, cuando algo suena a peligro o desde "Si algo no está bien"
    val care by vm.care.collectAsStateWithLifecycle()
    care?.let { request ->
        MinuCareDialog(request.help, onDismiss = { vm.dismissCare() }, onProceed = request.proceed?.let { { vm.proceedCare() } })
    }

    if (editingOrigin) {
        OriginSheet(
            origin = duo?.origin,
            partnerName = partner?.partner?.displayName.orEmpty(),
            lib = lib,
            onDismiss = { editingOrigin = false },
            onSave = { startedOn, song, story -> vm.saveOrigin(startedOn, song, story); editingOrigin = false },
        )
    }

    if (confirmUnlink) {
        AlertDialog(
            onDismissRequest = { confirmUnlink = false },
            title = { Text("¿Cortar el hilo?") },
            text = {
                Text(
                    "Dejarán de compartir dedicatorias, latidos y la música en vivo. Pueden volver a vincularse cuando quieran." +
                        (pet?.let { "\n\n${it.name}: “${MinuVoice.FAREWELL}”" } ?: ""),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmUnlink = false; vm.unlinkPartner() }) { Text("Desvincular") }
            },
            dismissButton = { TextButton(onClick = { confirmUnlink = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun LinkPartner(vm: TogetherViewModel, myUsername: String) {
    var username by rememberSaveable { mutableStateOf("") }
    InfoCard(
        "Ata tu hilo 💞",
        "Escribe el usuario de Minu de tu pareja. El tuyo es @$myUsername: compártelo para que te encuentre.",
    ) {
        OutlinedTextField(
            value = username, onValueChange = { username = it }, label = { Text("Usuario de tu pareja") },
            singleLine = true, shape = LocalAppStyle.current.cardShape, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        PillButton("Enviar invitación", Icons.Default.PersonAdd, { vm.requestPartner(username) }, Modifier.fillMaxWidth())
    }
}

@Composable
private fun InfoCard(title: String, text: String, content: @Composable () -> Unit) {
    val style = LocalAppStyle.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer, shape = style.cardShape,
        border = BorderStroke(
            if (style.border > 0.dp) style.border else 1.dp,
            if (style.border > 0.dp) MaterialTheme.colorScheme.outline else THREAD_RED.copy(alpha = 0.25f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

/** Un aniversario que todavía no abren: un regalo envuelto que espera el abrazo de los dos. */
@Composable
private fun GiftCard(years: Int, partnerName: String, onOpen: () -> Unit) {
    InfoCard("🎁 ${DuoDates.yearsPhrase(years).replaceFirstChar { it.uppercase() }} juntos", "Su aniversario está envuelto. Solo se abre con un abrazo de los dos en el hilo.") {
        PillButton("Abrirlo con $partnerName", Icons.Default.Favorite, onOpen, Modifier.fillMaxWidth())
    }
}

/** Entrada a Recuerdos: cintas del mes, cápsulas y misiones. */
@Composable
private fun MemoriesEntry(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(LocalAppStyle.current.cardShape).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("📼", fontSize = 24.sp)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Recuerdos", style = MaterialTheme.typography.titleMedium)
            Text("Sus cintas del mes, cápsulas y misiones", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** La misión del mes se muestra la semana siguiente al mesiversario, hasta que la marquen los dos. */
private fun missionToShow(duo: DuoInfo?): Pair<Int, MissionState?>? {
    val start = DuoDates.parse(duo?.origin?.startedOn) ?: return null
    val today = LocalDate.now()
    val month = DuoDates.monthsBetween(start, today)
    if (month < 1) return null
    val state = duo?.missions?.get(month)
    val since = DuoDates.days(DuoDates.monthRange(start, month).second, today)
    return (month to state).takeIf { since in 0..6 && state?.done != true }
}

@Composable
private fun MissionCard(month: Int, state: MissionState?, partnerName: String, onDone: (Boolean) -> Unit) {
    InfoCard("🎶 Misión del mes $month", DuoDates.mission(month)) {
        Text(
            when {
                state?.partner == true -> "$partnerName ya la marcó. Si la hicieron, márcala tú también."
                state?.mine == true -> "La marcaste. Falta que $partnerName la marque."
                else -> "Cuando la hagan, márquenla los dos y quedará en sus recuerdos."
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        val mine = state?.mine == true
        PillButton(if (mine) "Desmarcar" else "Lo hicimos", Icons.Default.Favorite, { onDone(!mine) }, Modifier.fillMaxWidth(), primary = !mine)
    }
}

/** El día del reto: qué toca para salvar el hilo y cuánto llevan. */
@Composable
private fun RescueCard(vm: TogetherViewModel, streak: DuoStreak, partner: PartnerInfo, listen: ListenSession) {
    val rescue = streak.rescue ?: return
    if (rescue.type == null) {
        InfoCard(
            "🧵 El hilo se está deshilachando",
            "Ayer no se dedicaron los dos una canción. Hoy pueden salvar sus ${streak.count} días con un reto " +
                "(les ${if (streak.rescuesLeft == 1) "queda 1 rescate" else "quedan ${streak.rescuesLeft} rescates"} este mes).",
        ) {
            PillButton("Media hora en la misma canción", Icons.Default.Headphones, { vm.chooseRescue("listen") }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            PillButton("Cápsula de tres canciones", Icons.Default.Favorite, { vm.chooseRescue("capsule") }, Modifier.fillMaxWidth(), primary = false)
            TextButton(onClick = vm::giveUpRescue, modifier = Modifier.fillMaxWidth()) { Text("Dejar que se rompa") }
        }
        return
    }
    if (rescue.type == "capsule") {
        InfoCard(
            "💌 En rescate: la cápsula de tres canciones",
            "Cada uno responde tres preguntas con una canción. Se abre cuando terminen los dos, y ahí el hilo queda salvado.",
        ) {
            PillButton("Abrir la cápsula", Icons.Default.Favorite, { vm.showCapsule(true) }, Modifier.fillMaxWidth())
        }
        return
    }
    val minutes = rescue.listenedSec / 60
    val goal = rescue.goalSec / 60
    InfoCard(
        "🧵 En rescate: $minutes de $goal minutos juntos",
        "Escuchen juntos con los dos conectados y la música sonando. Hoy no se puede dedicar: el reto es la dedicatoria.",
    ) {
        LinearProgressIndicator(
            progress = { (rescue.listenedSec.toFloat() / rescue.goalSec).coerceIn(0f, 1f) },
            color = AMBER, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        )
        if (listen.mode == ListenMode.NONE) {
            Spacer(Modifier.height(14.dp))
            PillButton("Invitar a ${partner.displayName} a escuchar", Icons.Default.Headphones, vm::inviteListen, Modifier.fillMaxWidth())
        }
    }
}

/** Las dos cosas grandes que hacen juntos: escuchar a la vez y dedicarse canciones. */
@Composable
private fun DuoActions(vm: TogetherViewModel, partner: PartnerInfo, listen: ListenSession, onDedicate: () -> Unit) {
    val name = partner.displayName
    val listenTitle = when (listen.mode) {
        ListenMode.NONE -> "Escuchar juntos"
        ListenMode.INVITING -> "Esperando…"
        ListenMode.INVITED -> "Te invitó"
        ListenMode.ACTIVE -> "En sintonía"
    }
    val listenSubtitle = when (listen.mode) {
        ListenMode.NONE -> if (partner.online) "Invita a $name" else "Invita a $name · le llegará un aviso"
        ListenMode.INVITING -> "Toca para cancelar"
        ListenMode.INVITED -> "Toca para unirte"
        ListenMode.ACTIVE -> if (listen.iAmDj) "Tú pones la música" else "$name pone la música"
    }
    val onListen: () -> Unit = when (listen.mode) {
        ListenMode.NONE -> { { vm.inviteListen() } }
        ListenMode.INVITING -> { { vm.leaveListen() } }
        ListenMode.INVITED -> { { vm.acceptListen() } }
        ListenMode.ACTIVE -> { {} }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DuoTile(
            "🎧", listenTitle, listenSubtitle, listOf(Color(0xFF7B5CFF), Color(0xFF36C2F5)),
            spinning = listen.active, onClick = onListen, modifier = Modifier.weight(1f),
        )
        DuoTile(
            "💌", "Dedicar", "Una canción con mensaje", listOf(THREAD_RED, WARM),
            spinning = false, onClick = onDedicate, modifier = Modifier.weight(1f),
        )
    }
}

/** Tarjeta grande con degradado que se hunde un poco al presionarla. */
@Composable
private fun DuoTile(
    emoji: String,
    title: String,
    subtitle: String,
    colors: List<Color>,
    spinning: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = LocalAppStyle.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(dampingRatio = 0.5f), label = "tile")
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "spin",
    )
    Box(
        modifier
            .height(136.dp)
            .scale(scale)
            .shadow(8.dp, style.cardShape, ambientColor = colors.first(), spotColor = colors.first())
            .clip(style.cardShape)
            .background(Brush.linearGradient(colors))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(16.dp),
    ) {
        Text(emoji, fontSize = 34.sp, modifier = Modifier.rotate(if (spinning) spin else -8f))
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                subtitle, color = Color.White.copy(alpha = 0.88f), style = MaterialTheme.typography.bodySmall,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Invitación recibida o sesión activa de escuchar juntos. */
@Composable
private fun ListenCard(vm: TogetherViewModel, partner: PartnerInfo, listen: ListenSession, player: PlayerState?, save: SaveAction?) {
    val name = partner.displayName
    when (listen.mode) {
        ListenMode.INVITED -> InfoCard("$name te invita a escuchar juntos 🎧", "Escucharás lo mismo que suena en su celular.") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Unirme", Icons.Default.Headphones, vm::acceptListen, Modifier.weight(1f))
                PillButton("Ahora no", Icons.Default.LinkOff, vm::leaveListen, Modifier.weight(1f), primary = false)
            }
        }
        ListenMode.ACTIVE -> InfoCard(
            "Escuchando juntos 🎶",
            if (listen.iAmDj) "Lo que pongas suena en el celular de $name. El hilo vibra con la música."
            else "$name pone la música. Puedes reaccionar o tomar el control.",
        ) {
            if (player != null && save != null) {
                ListenNowPlaying(player, save, invite = !listen.iAmDj)
                Spacer(Modifier.height(16.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!listen.iAmDj) PillButton("Tomar el control", Icons.Default.SportsEsports, vm::takeControl, Modifier.weight(1f))
                PillButton("Salir", Icons.Default.LinkOff, vm::leaveListen, Modifier.weight(1f), primary = false)
            }
        }
        else -> Unit
    }
}

/**
 * Lo que suena en la sesión. A quien escucha se le invita a guardarla si le gusta; al guardarla,
 * a quien controla le llega un 💚 en vivo.
 */
@Composable
private fun ListenNowPlaying(player: PlayerState, save: SaveAction, invite: Boolean) {
    val c = MaterialTheme.colorScheme
    val style = LocalAppStyle.current
    Row(
        Modifier.fillMaxWidth().clip(style.cardShape).background(c.surfaceContainerHigh)
            .clickable(onClick = save.onClick).padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(player.metadata.artworkUri, Modifier.size(52.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                EqualizerBars(player.isPlaying, Modifier.size(width = 14.dp, height = 12.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    player.metadata.title?.toString().orEmpty(), style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                when {
                    save.saved -> "En tu biblioteca · toca para otro álbum"
                    invite -> "¿Te gusta? Guárdala en un álbum"
                    else -> player.metadata.artist?.toString().orEmpty()
                },
                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (invite && !save.saved) c.primary else c.onSurfaceVariant,
            )
        }
        SaveToPlaylistButton(save, tint = c.onSurface)
    }
}

/** Emojis que se tocan o se lanzan hacia arriba: salen volando y viajan por el hilo. */
@Composable
private fun ReactionTray(partner: PartnerInfo, onSend: (String) -> Unit) {
    val style = LocalAppStyle.current
    Column {
        Text(style.label("Lanza un detalle"), style = MaterialTheme.typography.titleMedium)
        Text(
            if (partner.online) "Toca o desliza hacia arriba: le llega al instante."
            else "${partner.displayName} no tiene Minu abierta: lo verá cuando esté en línea contigo.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 10.dp),
        ) {
            items(TogetherViewModel.LIVE_EMOJIS) { emoji -> FlickEmoji(emoji, onSend) }
        }
    }
}

@Composable
private fun FlickEmoji(emoji: String, onSend: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val send by rememberUpdatedState(onSend)
    val launchPx = with(LocalDensity.current) { 34.dp.toPx() }
    var dy by remember { mutableFloatStateOf(0f) }
    fun settle(flung: Boolean) {
        scope.launch {
            if (flung) {
                animate(dy, -launchPx * 4f, animationSpec = tween(180)) { v, _ -> dy = v }
                dy = launchPx * 0.6f // reaparece desde abajo
            }
            animate(dy, 0f, animationSpec = spring(dampingRatio = 0.45f)) { v, _ -> dy = v }
        }
    }
    Box(
        Modifier
            .size(54.dp)
            .graphicsLayer {
                translationY = dy
                val lift = (-dy / launchPx).coerceIn(0f, 1f)
                scaleX = 1f + lift * 0.3f
                scaleY = 1f + lift * 0.3f
                alpha = 1f - ((-dy - launchPx * 1.5f) / (launchPx * 2.5f)).coerceIn(0f, 1f)
            }
            .clip(CircleShape)
            .background(c.surfaceContainerHigh)
            .border(1.dp, THREAD_RED.copy(alpha = 0.18f), CircleShape)
            .pointerInput(emoji) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        val flung = dy < -launchPx
                        if (flung) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            send(emoji)
                        }
                        settle(flung)
                    },
                    onDragCancel = { settle(false) },
                ) { change, amount ->
                    change.consume()
                    dy = (dy + amount).coerceIn(-launchPx * 2f, launchPx * 0.4f)
                }
            }
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                send(emoji)
                scope.launch {
                    animate(0f, -launchPx * 0.5f, animationSpec = tween(90)) { v, _ -> dy = v }
                    animate(dy, 0f, animationSpec = spring(dampingRatio = 0.35f)) { v, _ -> dy = v }
                }
            },
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = 26.sp) }
}

@Composable
private fun LettersHeader(count: Int) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(LocalAppStyle.current.label("Cartas"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (count > 0) {
            Text(
                "Toca dos veces una carta para mandarle ❤️",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Una dedicatoria como carta de correo aéreo: papel, borde de rayas, la portada como estampilla y el
 * mensaje a mano. Las que recibes van a la izquierda y las que mandas a la derecha, un poco ladeadas.
 */
@Composable
private fun LetterCard(d: Dedication, index: Int, partnerName: String, vm: TogetherViewModel) {
    val style = LocalAppStyle.current
    val dark = style.isDark
    val paper = if (dark) Color(0xFF2B2327) else Color(0xFFFFF7EC)
    val ink = if (dark) Color(0xFFF5E7EA) else Color(0xFF3B2A2F)
    val faded = ink.copy(alpha = 0.65f)
    val shape = RoundedCornerShape(10.dp)
    val tilt = if (index % 2 == 0) -1.2f else 1.2f
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val pop = remember { Animatable(0f) }

    Box(Modifier.fillMaxWidth(), contentAlignment = if (d.incoming) Alignment.TopStart else Alignment.TopEnd) {
        Box(Modifier.fillMaxWidth(0.94f).rotate(tilt)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .shadow(6.dp, shape)
                    .clip(shape)
                    .background(paper)
                    .pointerInput(d.id) {
                        detectTapGestures(onDoubleTap = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.react(d, "❤️")
                            scope.launch {
                                pop.snapTo(0.01f)
                                pop.animateTo(1f, tween(700))
                                pop.snapTo(0f)
                            }
                        })
                    },
            ) {
                AirmailStripe()
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (d.incoming) "De ${d.fromName}, para ti" else "Para $partnerName",
                                style = MaterialTheme.typography.labelLarge, color = THREAD_RED,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                d.song.title, style = MaterialTheme.typography.titleMedium, color = ink,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            Text(d.song.uploader, style = MaterialTheme.typography.bodySmall, color = faded, maxLines = 1)
                        }
                        Spacer(Modifier.width(10.dp))
                        // La portada como estampilla
                        Box(
                            Modifier.rotate(5f).background(Color.White, RoundedCornerShape(3.dp)).padding(3.dp),
                        ) {
                            ArtworkImage(d.song.thumbnailUrl, Modifier.size(58.dp), RoundedCornerShape(2.dp))
                        }
                    }
                    if (d.message.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "“${d.message}”", color = ink, fontFamily = FontFamily.Cursive, fontSize = 21.sp,
                            lineHeight = 26.sp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(d.createdAt)) +
                                    if (!d.incoming && d.seenAt != null) " · Vista ✓✓" else "",
                                style = MaterialTheme.typography.bodySmall, color = faded,
                            )
                            val reactions = d.reactions.takeLast(6).joinToString("") { it.emoji }
                            if (reactions.isNotEmpty()) Text(reactions, fontSize = 18.sp)
                        }
                        IconButton(
                            onClick = { vm.play(d) },
                            modifier = Modifier.size(44.dp).clip(CircleShape).background(THREAD_RED),
                        ) { Icon(Icons.Default.PlayArrow, "Reproducir", tint = Color.White) }
                    }
                    if (d.incoming) {
                        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TogetherViewModel.EMOJIS.take(5).forEach { emoji ->
                                Text(
                                    emoji, fontSize = 20.sp,
                                    modifier = Modifier.clip(CircleShape).clickable { vm.react(d, emoji) }.padding(6.dp),
                                )
                            }
                        }
                    }
                }
            }
            // Corazón que aparece al tocar dos veces
            if (pop.value > 0f) {
                val p = pop.value
                Text(
                    "❤️", fontSize = 72.sp,
                    modifier = Modifier.align(Alignment.Center).graphicsLayer {
                        val s = 0.4f + 0.9f * (1f - (1f - p) * (1f - p))
                        scaleX = s; scaleY = s
                        alpha = if (p < 0.7f) 1f else (1f - p) / 0.3f
                    },
                )
            }
        }
    }
}

/** Borde de rayas rojas y azules de las cartas de correo aéreo. */
@Composable
private fun AirmailStripe() {
    Canvas(Modifier.fillMaxWidth().height(7.dp)) {
        val w = 12.dp.toPx()
        val h = size.height
        var x = -h
        var i = 0
        while (x < size.width) {
            val stripe = Path().apply {
                moveTo(x, h)
                lineTo(x + h, 0f)
                lineTo(x + h + w, 0f)
                lineTo(x + w, h)
                close()
            }
            drawPath(stripe, if (i % 2 == 0) THREAD_RED else AIRMAIL_BLUE)
            x += w * 2f
            i++
        }
    }
}
