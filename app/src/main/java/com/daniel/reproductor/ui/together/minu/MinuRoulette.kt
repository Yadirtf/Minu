package com.daniel.reproductor.ui.together.minu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daniel.reproductor.cloud.Dedication
import com.daniel.reproductor.cloud.MinuPet
import com.daniel.reproductor.cloud.PetMood
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.ui.components.PillButton
import com.daniel.reproductor.ui.together.THREAD_RED
import com.daniel.reproductor.ui.together.WARM
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Los modos de la Ruleta de citas musicales. */
enum class DateMode(val emoji: String, val title: String, val blurb: String, val color: Color) {
    DISCOVER("🧭", "Descubrir juntos", "Canciones que ninguno ha escuchado, parecidas a las suyas.", Color(0xFF7C6CF2)),
    WHY("💭", "¿Por qué esta?", "Una canción que se dedicaron. ¿Recuerdan por qué?", THREAD_RED),
    JOURNEY("🕰️", "Viaje por su historia", "Una canción de cada época de su relación, en orden.", WARM),
}

/** Una parada del viaje: la canción y cuándo fue. */
data class JourneyStop(val song: Song, val label: String)

/** La cita que salió en la ruleta, ya armada con sus canciones. */
sealed interface MusicDate {
    val mode: DateMode
    data class Discover(val seed: Song) : MusicDate { override val mode = DateMode.DISCOVER }
    data class WhyThisOne(val dedication: Dedication) : MusicDate { override val mode = DateMode.WHY }
    data class Journey(val stops: List<JourneyStop>) : MusicDate { override val mode = DateMode.JOURNEY }
}

private val MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.forLanguageTag("es-CO"))

/**
 * Arma la cita de [mode] con lo que la pareja ya tiene. null si aún no alcanza (por ejemplo, sin
 * dedicatorias no hay "¿Por qué esta?").
 */
fun buildDate(mode: DateMode, dedications: List<Dedication>, originSong: Song?, random: Random = Random.Default): MusicDate? = when (mode) {
    DateMode.DISCOVER -> (dedications.map { it.song } + listOfNotNull(originSong)).randomOrNull(random)?.let { MusicDate.Discover(it) }
    DateMode.WHY -> {
        val weekAgo = System.currentTimeMillis() - 7L * 86_400_000
        (dedications.filter { it.createdAt < weekAgo }.ifEmpty { dedications }).randomOrNull(random)?.let { MusicDate.WhyThisOne(it) }
    }
    DateMode.JOURNEY -> {
        val sorted = dedications.sortedBy { it.createdAt }.distinctBy { it.song.videoId }
        val picks = if (sorted.size <= 6) sorted else (0 until 6).map { i -> sorted[i * (sorted.size - 1) / 5] }
        val stops = listOfNotNull(originSong?.let { JourneyStop(it, "El comienzo") }) + picks.map {
            JourneyStop(it.song, Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).format(MONTH))
        }
        stops.takeIf { it.size >= 2 }?.let { MusicDate.Journey(it) }
    }
}

/**
 * La Ruleta: una rueda de tres colores que gira con inercia y "clics" al pasar cada sección (vibración
 * suave). Al detenerse, Minu presenta la cita y se puede escuchar en vivo con la pareja o a solas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinuRouletteSheet(
    pet: MinuPet,
    partnerName: String,
    dedications: List<Dedication>,
    originSong: Song?,
    partnerOnline: Boolean,
    onPlay: (MusicDate, together: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        MinuRouletteContent(pet, partnerName, dedications, originSong, partnerOnline, onPlay)
    }
}

/** El contenido de la hoja, aparte para poder verlo en capturas. */
@Composable
fun MinuRouletteContent(
    pet: MinuPet,
    partnerName: String,
    dedications: List<Dedication>,
    originSong: Song?,
    partnerOnline: Boolean,
    onPlay: (MusicDate, together: Boolean) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val rotation = remember { Animatable(0f) }
    var spinning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<MusicDate?>(null) }
    var landed by remember { mutableStateOf<DateMode?>(null) }
    var spins by remember { mutableIntStateOf(0) }
    val segment = 360f / DateMode.entries.size

    // Un "clic" cada vez que el puntero cruza a otra sección
    LaunchedEffect(Unit) {
        var last = -1
        snapshotFlow { rotation.value }.collect { r ->
            val index = (((-r % 360f) + 360f) % 360f / segment).toInt()
            if (last != -1 && index != last && spinning) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            last = index
        }
    }

    fun spin() {
        if (spinning) return
        val available = DateMode.entries.filter { buildDate(it, dedications, originSong) != null }
        val target = available.randomOrNull() ?: return
        spinning = true
        result = null
        landed = null
        scope.launch {
            // Cae en el centro de la sección (con un poco de azar) tras 4 a 6 vueltas
            val index = DateMode.entries.indexOf(target)
            val inSegment = segment * (0.25f + Random.nextFloat() * 0.5f)
            val goal = -(index * segment + inSegment)
            val current = rotation.value % 360f
            val turns = 360f * (4 + Random.nextInt(3))
            rotation.snapTo(current)
            rotation.animateTo(goal - turns, tween(3600, easing = CubicBezierEasing(0.15f, 0.9f, 0.25f, 1f)))
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            landed = target
            result = buildDate(target, dedications, originSong)
            spins++
            spinning = false
        }
    }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Ruleta de citas musicales", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (dedications.isEmpty() && originSong == null) {
            MinuCreature(pet.species, PetMood.HAPPY, pet.stage, Modifier.size(110.dp))
            SpeechBubble("Para girar necesito canciones suyas. ¡Dedíquense una y volvemos!")
            Spacer(Modifier.height(16.dp))
            return@Column
        }
        Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
            Wheel(rotation.value, landed, Modifier.size(280.dp))
            // Minu en el centro, saltando cuando sale algo
            MinuCreature(pet.species, if (landed != null) PetMood.RADIANT else PetMood.HAPPY, pet.stage, Modifier.size(96.dp), celebrate = spins)
            Pointer(Modifier.align(Alignment.TopCenter).size(34.dp, 40.dp))
        }
        val date = result
        if (date == null) {
            PillButton(if (spinning) "Girando…" else "¡Girar!", Icons.Default.Refresh, { spin() }, Modifier.fillMaxWidth(), primary = !spinning)
            Text("Elige el destino la ruleta; ustedes solo ponen los audífonos.", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        AnimatedVisibility(date != null, enter = fadeIn() + scaleIn(initialScale = 0.85f)) {
            date?.let { DateCard(it, partnerName, partnerOnline, onPlay, onSpinAgain = { spin() }) }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun DateCard(date: MusicDate, partnerName: String, partnerOnline: Boolean, onPlay: (MusicDate, Boolean) -> Unit, onSpinAgain: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var revealed by remember(date) { mutableStateOf(false) }
    Surface(color = date.mode.color.copy(alpha = 0.12f), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${date.mode.emoji} ${date.mode.title}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = date.mode.color)
            when (date) {
                is MusicDate.Discover -> Text(
                    "Arranco desde “${date.seed.title}” y les traigo canciones que ninguno ha dedicado todavía. Si una les encanta, guárdenla.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                is MusicDate.WhyThisOne -> {
                    val d = date.dedication
                    Text(
                        if (d.incoming) "${d.fromName} te dedicó “${d.song.title}”. ¿Recuerdas por qué?"
                        else "Le dedicaste “${d.song.title}” a $partnerName. ¿Adivinará por qué?",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (d.message.isNotBlank()) {
                        if (revealed) Text("“${d.message}”", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = c.onSurfaceVariant)
                        else TextButton(onClick = { revealed = true }) { Text("Ver lo que decía la dedicatoria") }
                    }
                }
                is MusicDate.Journey -> JourneyLine(date.stops)
            }
            PillButton(
                if (partnerOnline) "Escuchar con $partnerName" else "Invitar a $partnerName a escuchar",
                Icons.Default.Headphones, { onPlay(date, true) }, Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onPlay(date, false) }) { Text("Escuchar yo") }
                TextButton(onClick = onSpinAgain) { Text("Girar otra vez") }
            }
        }
    }
}

/** El viaje como una línea de tiempo: un punto por época, unidos por el hilo rojo. */
@Composable
private fun JourneyLine(stops: List<JourneyStop>) {
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        stops.forEachIndexed { i, stop ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.width(24.dp).height(40.dp)) {
                    val x = size.width / 2
                    if (i > 0) drawLine(THREAD_RED.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, size.height / 2), strokeWidth = 3f)
                    if (i < stops.lastIndex) drawLine(THREAD_RED.copy(alpha = 0.6f), Offset(x, size.height / 2), Offset(x, size.height), strokeWidth = 3f)
                    drawCircle(if (i == 0) THREAD_RED else WARM, 7f, Offset(x, size.height / 2))
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(stop.song.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    Text(stop.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.PlayArrow)
            }
        }
    }
}

@Composable
private fun Icon(icon: androidx.compose.ui.graphics.vector.ImageVector) =
    androidx.compose.material3.Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))

/** La rueda: tres secciones con su emoji, borde dorado y luces que brillan cuando se detiene. */
@Composable
private fun Wheel(rotation: Float, landed: DateMode?, modifier: Modifier) {
    val glow = remember { Animatable(0f) }
    LaunchedEffect(landed) { if (landed != null) { glow.snapTo(1f); glow.animateTo(0f, tween(1400)) } }
    val modes = DateMode.entries
    val emojiPaint = remember { android.graphics.Paint().apply { textAlign = android.graphics.Paint.Align.CENTER } }
    Canvas(modifier) {
        val r = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)
        val sweep = 360f / modes.size
        rotate(rotation, center) {
            modes.forEachIndexed { i, mode ->
                // El puntero está arriba (-90°): la sección i ocupa [i*sweep, (i+1)*sweep) desde ahí
                val start = -90f + i * sweep
                val highlight = landed == mode
                drawArc(
                    Brush.radialGradient(listOf(mode.color.copy(alpha = 0.55f), mode.color), center, r),
                    start, sweep, true, Offset(center.x - r, center.y - r), Size(r * 2, r * 2),
                    alpha = if (landed == null || highlight) 1f else 0.55f,
                )
                val mid = Math.toRadians((start + sweep / 2).toDouble())
                emojiPaint.textSize = r * 0.24f
                drawContext.canvas.nativeCanvas.drawText(
                    mode.emoji, center.x + cos(mid).toFloat() * r * 0.66f, center.y + sin(mid).toFloat() * r * 0.66f + r * 0.08f, emojiPaint,
                )
            }
            // Separadores
            modes.indices.forEach { i ->
                val a = Math.toRadians((-90f + i * sweep).toDouble())
                drawLine(Color.White.copy(alpha = 0.7f), center, Offset(center.x + cos(a).toFloat() * r, center.y + sin(a).toFloat() * r), strokeWidth = 3f)
            }
        }
        // Aro con luces (no giran: como en una feria)
        drawCircle(Color(0xFFFFD27A), r, center, style = Stroke(r * 0.07f))
        repeat(18) { i ->
            val a = Math.toRadians(i * 20.0)
            val lit = (i % 2 == 0) xor (glow.value > 0.5f)
            drawCircle(
                if (lit) Color.White else Color(0xFFFFE9B8).copy(alpha = 0.6f), r * 0.025f,
                Offset(center.x + cos(a).toFloat() * r, center.y + sin(a).toFloat() * r),
            )
        }
        drawCircle(Color.White.copy(alpha = 0.12f + glow.value * 0.3f), r * 0.36f, center)
    }
}

/** El puntero: una gota roja con brillo, apuntando hacia la rueda. */
@Composable
private fun Pointer(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w / 2, h)
            cubicTo(w * 0.1f, h * 0.55f, 0f, h * 0.3f, w / 2, 0f)
            cubicTo(w, h * 0.3f, w * 0.9f, h * 0.55f, w / 2, h)
            close()
        }
        drawPath(path, THREAD_RED)
        drawCircle(Color.White.copy(alpha = 0.7f), w * 0.12f, Offset(w * 0.42f, h * 0.28f))
    }
}
