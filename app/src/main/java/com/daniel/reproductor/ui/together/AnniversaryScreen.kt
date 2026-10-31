package com.daniel.reproductor.ui.together

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daniel.reproductor.cloud.AnniversaryGift
import com.daniel.reproductor.cloud.DuoDates
import com.daniel.reproductor.cloud.DuoMilestone
import com.daniel.reproductor.cloud.SealedLetter
import com.daniel.reproductor.cloud.YearStory
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.ui.components.ArtworkImage
import com.daniel.reproductor.ui.components.PillButton
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.sin
import kotlin.random.Random

private val NIGHT = listOf(Color(0xFF0B0820), Color(0xFF1A0E2E), Color(0xFF2A0F2A))
private val GOLD = Color(0xFFFFC857)
private val SOFT = Color.White.copy(alpha = 0.72f)

/**
 * El aniversario: un regalo envuelto que solo se abre con el abrazo de los dos. Al abrirse suena su
 * canción, el hilo cuenta el año y luego vienen las historias, las cartas y la canción del año.
 */
@Composable
fun AnniversaryScreen(vm: TogetherViewModel, years: Int, me: String, partnerName: String, onDismiss: () -> Unit) {
    val gift by vm.anniversary.collectAsStateWithLifecycle()
    val duo by vm.duo.collectAsStateWithLifecycle()
    // Si se abre con la pantalla a la vista, primero la apertura; si ya estaba abierto, se puede saltar
    var watched by rememberSaveable(years) { mutableStateOf(false) }

    FullScreen(onDismiss) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(NIGHT))) {
            val g = gift
            when {
                g == null || g.years != years -> Text(
                    "Desenvolviendo…", color = SOFT, modifier = Modifier.align(Alignment.Center),
                )
                g.openedAt == null -> WrappedGift(vm, g, me, partnerName)
                !watched && g.story != null -> Opening(g.years, g.story, me, partnerName, onPlay = { vm.playSong(it) }) { watched = true }
                g.story != null -> YearStories(vm, g, g.story, partnerName, duo?.letter)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.systemBarsPadding().align(Alignment.TopEnd)) {
                Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
            }
        }
    }
}

/** El hilo dorado con un moño: mantener presionado es abrazar. Si el otro abraza a la vez, se abre. */
@Composable
private fun WrappedGift(vm: TogetherViewModel, gift: AnniversaryGift, me: String, partnerName: String) {
    val haptics = LocalHapticFeedback.current
    var holding by remember { mutableStateOf(false) }
    var time by remember { mutableFloatStateOf(0f) }
    val myPull by animateFloatAsState(if (holding) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 60f), label = "me")
    val theirPull by animateFloatAsState(if (gift.partnerHugging) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 60f), label = "partner")

    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { time = (it - start) / 1000f }
    }
    // Mientras abrazas, el celular lo repite para que el servidor vea los dos abrazos a la vez
    LaunchedEffect(holding) {
        while (holding) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            vm.hug(gift.years)
            delay(2500)
        }
    }
    // Y mientras tanto, mira si el otro está abrazando (por si el aviso en vivo no llega)
    LaunchedEffect(gift.years) {
        while (true) {
            delay(3000)
            vm.loadAnniversary(gift.years)
        }
    }

    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(36.dp))
        Text(DuoDates.title(DuoMilestone.Anniversary(gift.years)), color = GOLD, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("Hay algo esperando, pero solo se abre con los dos.", color = SOFT, textAlign = TextAlign.Center)

        Canvas(Modifier.fillMaxWidth().height(260.dp)) {
            val y = size.height * 0.5f
            val r = 30.dp.toPx()
            // Las burbujas se acercan cuando cada uno abraza
            val gap = size.width * 0.5f - r * 1.4f
            val left = Offset(r * 1.4f + gap * 0.42f * myPull, y)
            val right = Offset(size.width - r * 1.4f - gap * 0.42f * theirPull, y)
            val path = Path()
            for (i in 0..48) {
                val p = threadPoint(left, right, i / 48f, 22.dp.toPx() * (1f - 0.6f * (myPull + theirPull) / 2f), 3.dp.toPx(), time * 2f)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, GOLD.copy(alpha = 0.25f), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, GOLD, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            // El moño en el centro del hilo
            val bow = threadPoint(left, right, 0.5f, 22.dp.toPx() * (1f - 0.6f * (myPull + theirPull) / 2f), 3.dp.toPx(), time * 2f)
            val wiggle = 1f + 0.06f * sin(time * 3f)
            drawCircle(GOLD, radius = 16.dp.toPx() * wiggle, center = bow + Offset(-18.dp.toPx(), 0f), alpha = 0.9f)
            drawCircle(GOLD, radius = 16.dp.toPx() * wiggle, center = bow + Offset(18.dp.toPx(), 0f), alpha = 0.9f)
            drawCircle(Color(0xFFE0A63A), radius = 8.dp.toPx(), center = bow)
            listOf(left to myPull, right to theirPull).forEach { (c, pull) ->
                drawCircle(THREAD_RED, radius = r * (1f + 0.25f * pull), center = c, alpha = 0.25f + 0.25f * pull)
                drawCircle(Color.White, radius = r * 0.8f, center = c)
                drawHeart(c, r * 0.8f, THREAD_RED, 0.4f + 0.6f * pull)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(me, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(partnerName, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(28.dp))
        Text(
            when {
                holding && gift.partnerHugging -> "Abrazo encontrado…"
                gift.partnerHugging -> "$partnerName te está abrazando. Mantén presionado tú también."
                holding -> "Abrazando… Pídele a $partnerName que mantenga presionado en su celular."
                else -> "Mantén presionado el corazón para abrazar a $partnerName. Si los dos lo hacen a la vez, el moño se desata."
            },
            color = Color.White, textAlign = TextAlign.Center, fontSize = 16.sp,
        )
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.size(96.dp).clip(CircleShape)
                .background(Brush.radialGradient(listOf(THREAD_RED, THREAD_RED.copy(alpha = 0.6f))))
                .border(2.dp, GOLD.copy(alpha = 0.4f + 0.6f * myPull), CircleShape)
                .graphicsLayer { scaleX = 1f + 0.12f * myPull; scaleY = 1f + 0.12f * myPull }
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        holding = true
                        tryAwaitRelease()
                        holding = false
                    })
                },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Favorite, "Abrazar", tint = Color.White, modifier = Modifier.size(42.dp)) }
        Spacer(Modifier.weight(1f))
        if (gift.canOpenSolo) {
            TextButton(onClick = { vm.openAnniversaryAlone(gift.years) }) { Text("Abrirlo solo", color = SOFT) }
        } else {
            Text(
                "Si $partnerName no puede hoy, desde las 8 p. m. podrás abrirlo solo.",
                color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp, textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * La apertura (unos 25 segundos, se adelanta tocando): empieza la canción, aparece cómo empezó, el
 * hilo se dibuja con sus 12 nudos, pasan sus números del año y el hilo escribe los años.
 */
@Composable
private fun Opening(years: Int, story: YearStory, me: String, partner: String, onPlay: (Song) -> Unit, onDone: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var time by remember { mutableFloatStateOf(0f) }
    val hearts = remember { List(30) { Triple(Random.nextFloat(), Random.nextFloat() * 2.5f, 0.6f + Random.nextFloat() * 0.8f) } }
    val numbers = remember(story) {
        listOfNotNull(
            "${story.dedications} canciones dedicadas",
            (story.listenSec / 3600).takeIf { it > 0 }?.let { DuoDates.plural(it, "hora", "horas") + " escuchando juntos" },
            story.bestStreak.takeIf { it > 0 }?.let { "Su racha más larga: ${DuoDates.plural(it, "día", "días")}" },
            story.saves.takeIf { it > 0 }?.let { "Salvaron el hilo ${DuoDates.plural(it, "vez", "veces")}" },
        )
    }
    val numbersEnd = THREAD_END + numbers.size * NUMBER_SECONDS
    val end = numbersEnd + 5f

    LaunchedEffect(Unit) {
        (story.vote.song ?: story.originSong ?: story.top.firstOrNull()?.song)?.let(onPlay)
        val start = withFrameMillis { it }
        while (time < end) withFrameMillis { time = (it - start) / 1000f }
        onDone()
    }
    LaunchedEffect(time >= numbersEnd) { if (time >= numbersEnd) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }

    Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, indication = null) { onDone() }) {
        Canvas(Modifier.fillMaxSize()) {
            if (time < STORY_END) return@Canvas
            val drawn = FastOutSlowInEasing.transform(((time - STORY_END) / (THREAD_END - STORY_END)).coerceIn(0f, 1f))
            val y0 = size.height * 0.3f
            val a = Offset(-20f, y0)
            val b = Offset(size.width + 20f, y0)
            fun at(t: Float) = threadPoint(a, b, t, 26.dp.toPx(), 8.dp.toPx(), time * 2f)
            val path = Path()
            val steps = (64 * drawn).toInt().coerceAtLeast(1)
            for (i in 0..steps) {
                val p = at(i / 64f)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, GOLD.copy(alpha = 0.28f), style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, GOLD, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            // Un nudo por cada mes, encendiéndose cuando el hilo pasa
            for (k in 0 until 12) {
                val t = 0.06f + 0.88f * k / 11f
                if (drawn >= t) drawCircle(GOLD, radius = 5.dp.toPx(), center = at(t))
            }
            if (time >= numbersEnd) {
                val since = time - numbersEnd
                hearts.forEach { (x, delay, speed) ->
                    val life = (since - delay) * speed / 3f
                    if (life !in 0f..1f) return@forEach
                    drawHeart(Offset(size.width * x, size.height * (1.02f - life * 0.9f)), 16.dp.toPx(), if (x > 0.5f) GOLD else THREAD_RED, 1f - life)
                }
            }
        }
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when {
                time < STORY_END -> {
                    val alpha = fade(time, 0.6f, STORY_END)
                    Text(
                        story.originStory.takeIf { it.isNotBlank() }?.let { "“$it”" } ?: "Hace un año, $me y $partner ataron este hilo.",
                        color = GOLD, fontSize = 22.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center,
                        modifier = Modifier.graphicsLayer { this.alpha = alpha },
                    )
                }
                time < THREAD_END -> Spacer(Modifier.height(1.dp))
                time < numbersEnd -> {
                    val i = ((time - THREAD_END) / NUMBER_SECONDS).toInt().coerceIn(0, numbers.lastIndex)
                    val from = THREAD_END + i * NUMBER_SECONDS
                    Text(
                        numbers[i], color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 120.dp).graphicsLayer { alpha = fade(time, from, from + NUMBER_SECONDS) },
                    )
                }
                else -> {
                    val pop = ((time - numbersEnd) / 0.8f).coerceIn(0f, 1f)
                    Text(
                        "$years", color = GOLD, fontSize = 120.sp, fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(top = 80.dp).graphicsLayer { scaleX = 0.4f + 0.6f * pop; scaleY = 0.4f + 0.6f * pop; alpha = pop },
                    )
                    Text(if (years == 1) "año" else "años", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text(
            "Toca para adelantar", color = Color.White.copy(alpha = 0.35f), fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(bottom = 16.dp),
        )
    }
}

private const val STORY_END = 5f
private const val THREAD_END = 10f
private const val NUMBER_SECONDS = 3f

/** Aparece al entrar en [from] y se desvanece al acercarse a [to]. */
private fun fade(time: Float, from: Float, to: Float) =
    minOf(((time - from) / 0.6f).coerceIn(0f, 1f), ((to - time) / 0.6f).coerceIn(0f, 1f))

/** Las historias del año, una por pantalla. Las últimas son las cartas, la canción del año y la carta del próximo. */
@Composable
private fun YearStories(vm: TogetherViewModel, gift: AnniversaryGift, story: YearStory, partner: String, letter: SealedLetter?) {
    // Una llave por pantalla: "c3" es la cápsula número 3
    val pages = buildList {
        if (story.first != null) add("first")
        if (story.top.isNotEmpty()) add("top")
        add("thread")
        story.capsules.indices.forEach { add("c$it") }
        add("letters")
        if (story.top.isNotEmpty()) add("vote")
        add("next")
    }
    val pager = rememberPagerState { pages.size }
    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        // Rayitas de historias arriba
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 56.dp, top = 18.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(pages.size) { i ->
                Box(
                    Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (i <= pager.currentPage) GOLD else Color.White.copy(alpha = 0.2f)),
                )
            }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page -> StoryAt(pages[page], vm, gift, story, partner, letter) }
    }
}

@Composable
private fun StoryAt(page: String, vm: TogetherViewModel, gift: AnniversaryGift, story: YearStory, partner: String, letter: SealedLetter?) {
    when {
        page == "first" -> story.first?.let { first ->
            StoryPage("La primera canción del año", DuoDates.short(LocalDate.parse(first.day))) {
                SongLine(first.song) { vm.playSong(it) }
                Spacer(Modifier.height(12.dp))
                Text(if (first.fromMe) "Se la dedicaste tú." else "Te la dedicó $partner.", color = SOFT)
                if (first.message.isNotBlank()) Quote(first.message)
            }
        }
        page == "top" -> story.top.firstOrNull()?.let { top ->
            StoryPage("La que más se dedicaron", "${DuoDates.plural(top.count, "vez", "veces")} este año") {
                SongLine(top.song) { vm.playSong(it) }
                Spacer(Modifier.height(16.dp))
                Text(
                    "En total fueron ${story.dedications} canciones: ${story.mine} tuyas y ${story.dedications - story.mine} de $partner.",
                    color = SOFT, textAlign = TextAlign.Center,
                )
            }
        }
        page == "thread" -> StoryPage("El hilo", "Lo que sostuvieron entre los dos") {
            BigNumber(DuoDates.plural(story.listenSec / 3600, "hora", "horas"), "escuchando música juntos")
            BigNumber(DuoDates.plural(story.bestStreak, "día", "días"), "su racha más larga")
            if (story.saves > 0) BigNumber(DuoDates.plural(story.saves, "vez", "veces"), "salvaron el hilo con un reto")
        }
        page.startsWith("c") -> story.capsules.getOrNull(page.drop(1).toInt())?.let { capsule ->
            StoryPage("Una cápsula", DuoDates.short(LocalDate.parse(capsule.day))) {
                CapsuleReveal(capsule, partner, onPlay = { vm.playSong(it) }, animate = false, scroll = false)
            }
        }
        page == "letters" -> StoryPage("Las cartas selladas", "Las escribieron para este día") {
            if (story.letters.isEmpty()) {
                Text("Este año no hubo cartas. Escriban la del próximo: se abrirá en su siguiente aniversario.", color = SOFT, textAlign = TextAlign.Center)
            }
            story.letters.sortedBy { it.fromMe }.forEach { l ->
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFFFF8EC)).padding(16.dp),
                ) {
                    Text(if (l.fromMe) "Tu carta" else "De $partner", color = Color(0xFFB3202F), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(l.text, color = Color(0xFF2B1B2E), fontSize = 16.sp)
                }
            }
        }
        page == "vote" -> SongOfTheYear(vm, gift, story, partner)
        else -> NextLetter(vm, gift.years, letter, partner)
    }
}

@Composable
private fun StoryPage(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = GOLD, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(subtitle, color = SOFT, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        content()
    }
}

@Composable
private fun SongLine(song: Song, onPlay: (Song) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.08f))
            .clickable { onPlay(song) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(song.thumbnailUrl, Modifier.size(64.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(song.uploader, color = SOFT, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Default.PlayArrow, "Escuchar", tint = GOLD)
    }
}

@Composable
private fun Quote(text: String) {
    Spacer(Modifier.height(10.dp))
    Text("“$text”", color = GOLD, fontStyle = FontStyle.Italic, fontSize = 18.sp, textAlign = TextAlign.Center)
}

@Composable
private fun BigNumber(value: String, label: String) {
    Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black)
        Text(label, color = SOFT)
    }
}

/** Votan entre las 5 más dedicadas; si coinciden gana esa, si no, la más dedicada de las dos. */
@Composable
private fun SongOfTheYear(vm: TogetherViewModel, gift: AnniversaryGift, story: YearStory, partner: String) {
    val vote = story.vote
    StoryPage("La canción del año ${gift.years}", "Elijan entre sus 5 más dedicadas") {
        vote.song?.let { song ->
            val reveal = remember { Animatable(0f) }
            LaunchedEffect(song.videoId) { reveal.animateTo(1f, tween(900)) }
            Column(Modifier.graphicsLayer { alpha = reveal.value; scaleX = 0.9f + 0.1f * reveal.value; scaleY = 0.9f + 0.1f * reveal.value }) {
                Text(
                    if (vote.mine == vote.partner) "¡Eligieron la misma!" else "No coincidieron, así que gana la que más se dedicaron.",
                    color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                SongLine(song) { vm.playSong(it) }
            }
            Spacer(Modifier.height(24.dp))
        }
        story.top.forEach { t ->
            val mine = vote.mine == t.song.videoId
            val theirs = vote.partner == t.song.videoId && vote.song != null // el voto del otro se ve al final
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (mine) GOLD.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f))
                    .clickable(enabled = vote.song == null) { vm.voteSong(gift.years, t.song.videoId) }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtworkImage(t.song.thumbnailUrl, Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.song.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        DuoDates.plural(t.count, "vez", "veces") + listOfNotNull(
                            " · tu voto".takeIf { mine }, " · voto de $partner".takeIf { theirs },
                        ).joinToString(""),
                        color = SOFT, fontSize = 12.sp,
                    )
                }
            }
        }
        if (vote.song == null) {
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    vote.mine != null && vote.partner != null -> ""
                    vote.mine != null -> "Votaste. Falta $partner."
                    vote.partner != null -> "$partner ya votó. Te toca."
                    else -> "Toca la que para ti fue la de este año."
                },
                color = SOFT, textAlign = TextAlign.Center,
            )
        }
    }
}

/** La carta para el próximo aniversario: queda sellada en el servidor hasta ese día. */
@Composable
private fun NextLetter(vm: TogetherViewModel, years: Int, letter: SealedLetter?, partner: String) {
    var text by rememberSaveable(letter?.year) { mutableStateOf(letter?.mine.orEmpty()) }
    StoryPage("Para el próximo año", "Se abrirá cuando abran ${DuoDates.yearsPhrase(years + 1)} juntos") {
        Text(
            "Escríbele a $partner la carta que leerá en su próximo aniversario. Nadie la verá antes, ni siquiera tú desde su celular." +
                if (letter?.partnerWrote == true) " $partner ya selló la suya." else "",
            color = SOFT, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = text, onValueChange = { text = it.take(3000) },
            placeholder = { Text("Querida, querido…") }, minLines = 6,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                focusedBorderColor = GOLD, unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        PillButton(
            if (letter?.mine != null) "Volver a sellar" else "Sellar la carta", Icons.Default.Lock,
            { if (text.isNotBlank()) vm.writeLetter(text.trim()) }, Modifier.fillMaxWidth(), primary = text.isNotBlank(),
        )
        Spacer(Modifier.height(32.dp))
        Text("Ideas para hoy", color = GOLD, fontWeight = FontWeight.Bold)
        ANNIVERSARY_IDEAS.forEach { Text("• $it", color = Color.White, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) }
    }
}

private val ANNIVERSARY_IDEAS = listOf(
    "Recreen su primera cita con su canción de origen sonando.",
    "Cenen escuchando sus cintas del año, una canción de cada mes.",
    "Escríbanse a mano la letra de su canción del año.",
)
