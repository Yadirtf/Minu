package com.daniel.reproductor.ui.together.minu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daniel.reproductor.cloud.Adventure
import com.daniel.reproductor.cloud.AdventurePlan
import com.daniel.reproductor.cloud.MinuPet
import com.daniel.reproductor.cloud.PetMood
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.ui.components.PillButton
import com.daniel.reproductor.ui.together.THREAD_RED
import com.daniel.reproductor.ui.together.WARM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.random.Random

private val PARCHMENT = Color(0xFFF6E7C8)
private val PARCHMENT_DARK = Color(0xFFE2C795)
private val MAP_INK = Color(0xFF6B4A2B)

class AdventureActions(
    val vote: (planId: String) -> Unit,
    val reshuffle: () -> Unit,
    val complete: (song: Song?) -> Unit,
    val rate: (week: String, stars: Int) -> Unit,
    val uploadPhoto: (week: String, jpeg: ByteArray) -> Unit,
    val loadPhoto: (week: String) -> Unit,
)

/**
 * Aventura de la semana: un mapa del tesoro con tres planes (uno en casa, uno para salir, uno para crear).
 * Cada uno vota con su corazón; cuando coinciden, el camino del mapa llega a la X. Al vivirla guardan una
 * foto y una canción, que quedan como polaroid en Recuerdos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinuAdventureSheet(
    pet: MinuPet,
    adventure: Adventure,
    partnerName: String,
    songs: List<Song>,
    photo: ImageBitmap?,
    actions: AdventureActions,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        MinuAdventureContent(pet, adventure, partnerName, songs, photo, actions)
    }
}

/** El contenido de la hoja, aparte para poder verlo en capturas. */
@Composable
fun MinuAdventureContent(
    pet: MinuPet,
    adventure: Adventure,
    partnerName: String,
    songs: List<Song>,
    photo: ImageBitmap?,
    actions: AdventureActions,
) {
    LaunchedEffect(adventure.week, adventure.photoAt) { if (adventure.hasPhoto) actions.loadPhoto(adventure.week) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Aventura de la semana", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        TreasureMap(adventure, pet, Modifier.fillMaxWidth().height(170.dp))
        val chosen = adventure.chosen
        when {
            chosen == null -> Voting(adventure, partnerName, actions)
            !adventure.done -> Chosen(chosen, adventure, partnerName, songs, photo, actions)
            else -> Lived(chosen, adventure, partnerName, photo, actions)
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ---------- Votar ----------

@Composable
private fun ColumnScope.Voting(adventure: Adventure, partnerName: String, actions: AdventureActions) {
    val c = MaterialTheme.colorScheme
    SpeechBubble(
        when {
            adventure.myVote == null && adventure.partnerVote != null -> "$partnerName ya votó. ¿Coincidirán? 👀"
            adventure.myVote != null && adventure.partnerVote == null -> "Guardé tu voto en secreto… bueno, casi. Falta $partnerName."
            adventure.myVote != null -> "Votaron distinto. ¡Pónganse de acuerdo y yo marco la X!"
            else -> "Hacer algo nuevo juntos los acerca. Elijan uno y yo los acompaño."
        },
    )
    adventure.options.forEach { plan ->
        PlanCard(plan, mine = adventure.myVote == plan.id, theirs = adventure.partnerVote == plan.id, partnerName) { actions.vote(plan.id) }
    }
    if (adventure.canReshuffle) {
        TextButton(onClick = actions.reshuffle, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Otras propuestas (una vez por semana)")
        }
    }
    Text(
        "Ves el voto de $partnerName, y $partnerName ve el tuyo. Cuando los dos elijan el mismo, es su aventura.",
        style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant,
    )
}

@Composable
private fun PlanCard(plan: AdventurePlan, mine: Boolean, theirs: Boolean, partnerName: String, onVote: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val pop = remember { Animatable(1f) }
    LaunchedEffect(mine) { if (mine) { pop.snapTo(0.94f); pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium)) } }
    Surface(
        color = if (mine) THREAD_RED.copy(alpha = 0.10f) else c.surfaceContainerHigh,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(if (mine) 2.dp else 1.dp, if (mine) THREAD_RED else c.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onVote).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).background(PARCHMENT, CircleShape), contentAlignment = Alignment.Center) {
                Text(plan.emoji, style = MaterialTheme.typography.headlineSmall)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("${plan.kind.label} · ${"🔥".repeat(plan.effort.coerceIn(1, 3))}", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                Text(plan.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(plan.detail, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                if (mine || theirs) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (mine) VoteChip("Tu voto", THREAD_RED)
                        if (theirs) VoteChip("Voto de $partnerName", WARM)
                    }
                }
            }
        }
    }
}

@Composable
private fun VoteChip(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.18f), shape = RoundedCornerShape(50)) {
        Text("♥ $text", color = color, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

// ---------- Elegida ----------

@Composable
private fun Chosen(plan: AdventurePlan, adventure: Adventure, partnerName: String, songs: List<Song>, photo: ImageBitmap?, actions: AdventureActions) {
    val c = MaterialTheme.colorScheme
    var song by remember { mutableStateOf<Song?>(null) }
    val left = remember(adventure.week) {
        val sunday = LocalDate.parse(adventure.week).with(DayOfWeek.SUNDAY)
        ChronoUnit.DAYS.between(LocalDate.now(), sunday).coerceAtLeast(0)
    }
    Ticket(plan, if (left == 0L) "Hoy es el último día de la semana" else "Tienen $left ${if (left == 1L) "día" else "días"} para vivirla")
    SpeechBubble("¡Coincidieron! Cuando la vivan, guarden una foto y la canción que sonó. Yo la pego en sus Recuerdos.")
    PhotoPicker(adventure.week, photo, adventure.hasPhoto, actions)
    if (songs.isNotEmpty()) {
        Text("¿Qué canción les sonó?", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            songs.take(6).forEach { s ->
                val picked = song?.videoId == s.videoId
                Surface(
                    color = if (picked) THREAD_RED.copy(alpha = 0.12f) else c.surfaceContainerHigh, shape = RoundedCornerShape(14.dp),
                    border = if (picked) BorderStroke(1.5.dp, THREAD_RED) else null,
                    modifier = Modifier.fillMaxWidth().clickable { song = if (picked) null else s },
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, null, tint = if (picked) THREAD_RED else c.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(s.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                }
            }
        }
    }
    PillButton("¡La vivimos!", Icons.Default.Celebration, { actions.complete(song) }, Modifier.fillMaxWidth())
    Text("Cualquiera de los dos puede marcarla. A $partnerName le llegará la buena noticia.", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
}

/** El plan elegido como un boleto de entrada, con su borde troquelado. */
@Composable
private fun Ticket(plan: AdventurePlan, footer: String) {
    Column(
        Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(18.dp)).background(PARCHMENT, RoundedCornerShape(18.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SU AVENTURA", color = MAP_INK.copy(alpha = 0.7f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(plan.emoji, style = MaterialTheme.typography.displaySmall)
            Text(plan.title, color = MAP_INK, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontFamily = FontFamily.Serif)
            Text(plan.detail, color = MAP_INK.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        // Muescas a los lados y línea punteada, como un boleto que se puede cortar
        Canvas(Modifier.fillMaxWidth().height(24.dp)) {
            val y = size.height / 2
            drawCircle(Color.Black.copy(alpha = 0.25f), 12f, Offset(0f, y))
            drawCircle(Color.Black.copy(alpha = 0.25f), 12f, Offset(size.width, y))
            drawLine(MAP_INK.copy(alpha = 0.35f), Offset(18f, y), Offset(size.width - 18f, y), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
        Text(footer, color = MAP_INK, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp))
    }
}

@Composable
private fun PhotoPicker(week: String, photo: ImageBitmap?, hasPhoto: Boolean, actions: AdventureActions) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            working = true
            val jpeg = withContext(Dispatchers.IO) { runCatching { compressPhoto(context, uri) }.getOrNull() }
            working = false
            if (jpeg != null) actions.uploadPhoto(week, jpeg)
        }
    }
    val open = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    if (photo != null) {
        Polaroid(photo, null, Modifier.fillMaxWidth().clickable { open() })
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().height(120.dp).clickable { open() },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.AddAPhoto, null, Modifier.size(30.dp), tint = THREAD_RED)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        working -> "Preparando la foto…"
                        hasPhoto -> "Cargando su foto…"
                        else -> "Agregar la foto de su aventura"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

// ---------- Vivida ----------

@Composable
private fun Lived(plan: AdventurePlan, adventure: Adventure, partnerName: String, photo: ImageBitmap?, actions: AdventureActions) {
    val c = MaterialTheme.colorScheme
    var confetti by remember { mutableIntStateOf(1) }
    Box {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (photo != null) Polaroid(photo, "${plan.emoji} ${plan.title}", Modifier.fillMaxWidth())
            else {
                Ticket(plan, "¡Vivida!")
                PhotoPicker(adventure.week, null, adventure.hasPhoto, actions)
            }
            adventure.song?.let { s ->
                Surface(color = c.surfaceContainerHigh, shape = RoundedCornerShape(50)) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, null, tint = THREAD_RED, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(s.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                }
            }
            Text("¿Qué tal estuvo?", style = MaterialTheme.typography.titleMedium)
            HeartRating(adventure.myRating) { actions.rate(adventure.week, it); confetti++ }
            adventure.partnerRating?.let { Text("$partnerName le dio ${"♥".repeat(it)}", style = MaterialTheme.typography.labelLarge, color = WARM) }
            SpeechBubble("¡Lo lograron! Ya está en sus Recuerdos. El lunes les traigo otra.")
        }
        Confetti(confetti, Modifier.matchParentSize())
    }
}

@Composable
private fun HeartRating(value: Int?, onRate: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..5).forEach { n ->
            val on = (value ?: 0) >= n
            Text(
                if (on) "♥" else "♡", color = if (on) THREAD_RED else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.headlineMedium, modifier = Modifier.clickable { onRate(n) }.padding(4.dp),
            )
        }
    }
}

/** La foto como polaroid, un poco girada, con su pie escrito a mano. */
@Composable
fun Polaroid(photo: ImageBitmap, caption: String?, modifier: Modifier = Modifier, tilt: Float = -2f) {
    Surface(color = Color.White, shape = RoundedCornerShape(4.dp), shadowElevation = 6.dp, modifier = modifier.rotate(tilt).padding(horizontal = 8.dp)) {
        Column(Modifier.padding(10.dp)) {
            Image(photo, null, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(2.dp)), contentScale = ContentScale.Crop)
            caption?.let {
                Text(it, color = Color(0xFF3A2A33), fontFamily = FontFamily.Serif, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            }
        }
    }
}

// ---------- Arte ----------

/**
 * El mapa del tesoro: papel antiguo, tres islas (una por plan) y un camino punteado. Mientras votan, Minu
 * espera en el muelle; cuando coinciden, el camino se dibuja hasta la X del plan elegido.
 */
@Composable
fun TreasureMap(adventure: Adventure, pet: MinuPet, modifier: Modifier = Modifier) {
    val draw = remember { Animatable(0f) }
    val target = when {
        adventure.done -> 1f
        adventure.chosen != null -> 0.85f
        else -> 0.25f
    }
    LaunchedEffect(target) { draw.animateTo(target, tween(1400, easing = LinearEasing)) }
    Box(modifier.clip(RoundedCornerShape(20.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(PARCHMENT)
            // Manchas del papel
            val rnd = Random(7)
            repeat(14) { drawCircle(PARCHMENT_DARK.copy(alpha = 0.25f), rnd.nextFloat() * h * 0.12f + 6f, Offset(rnd.nextFloat() * w, rnd.nextFloat() * h)) }
            // Islas
            val islands = listOf(Offset(w * 0.22f, h * 0.62f), Offset(w * 0.52f, h * 0.3f), Offset(w * 0.82f, h * 0.62f))
            islands.forEach { drawOval(Color(0xFFBFD8A8), Offset(it.x - 34f, it.y - 18f), androidx.compose.ui.geometry.Size(68f, 36f)) }
            // Camino desde el muelle (izquierda abajo) hasta la isla del medio y su X
            val path = Path().apply {
                moveTo(w * 0.04f, h * 0.9f)
                cubicTo(w * 0.2f, h * 0.95f, w * 0.18f, h * 0.45f, w * 0.36f, h * 0.5f)
                cubicTo(w * 0.48f, h * 0.55f, w * 0.44f, h * 0.2f, w * 0.52f, h * 0.3f)
            }
            val measure = android.graphics.PathMeasure(path.asAndroidPath(), false)
            val partial = android.graphics.Path()
            measure.getSegment(0f, measure.length * draw.value, partial, true)
            drawPath(partial.asComposePath(), MAP_INK, style = Stroke(4f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))))
            // La X del tesoro
            val x = islands[1]
            val xColor = if (adventure.chosen != null) THREAD_RED else MAP_INK.copy(alpha = 0.4f)
            drawLine(xColor, Offset(x.x - 12f, x.y - 12f), Offset(x.x + 12f, x.y + 12f), strokeWidth = 6f, cap = StrokeCap.Round)
            drawLine(xColor, Offset(x.x + 12f, x.y - 12f), Offset(x.x - 12f, x.y + 12f), strokeWidth = 6f, cap = StrokeCap.Round)
            // Rosa de los vientos
            val rose = Offset(w * 0.9f, h * 0.18f)
            drawCircle(MAP_INK.copy(alpha = 0.5f), 18f, rose, style = Stroke(2f))
            drawLine(MAP_INK, rose - Offset(0f, 24f), rose + Offset(0f, 24f), strokeWidth = 2f)
            drawLine(MAP_INK, rose - Offset(24f, 0f), rose + Offset(24f, 0f), strokeWidth = 2f)
            // Borde quemado
            drawRect(PARCHMENT_DARK, style = Stroke(10f))
        }
        // Minu viaja por el camino
        val mood = if (adventure.done) PetMood.RADIANT else PetMood.HAPPY
        val progress = draw.value
        Box(Modifier.fillMaxSize()) {
            MinuCreature(
                pet.species, mood, pet.stage,
                Modifier.size(64.dp).align(Alignment.BottomStart)
                    .padding(start = (max(0f, progress - 0.2f) * 160).dp, bottom = (progress * 60).dp),
                carrying = adventure.chosen != null && !adventure.done,
                celebrate = if (adventure.done) 1 else 0,
            )
        }
    }
}

/** Confeti de papel de colores que cae una vez cada vez que cambia [trigger]. */
@Composable
fun Confetti(trigger: Int, modifier: Modifier = Modifier) {
    val fall = remember { Animatable(1f) }
    LaunchedEffect(trigger) { fall.snapTo(0f); fall.animateTo(1f, tween(1800, easing = LinearEasing)) }
    val colors = listOf(THREAD_RED, WARM, Color(0xFF7C6CF2), Color(0xFF4CC38A), Color(0xFFFFD27A))
    Canvas(modifier) {
        if (fall.value >= 1f) return@Canvas
        val rnd = Random(trigger)
        repeat(40) {
            val x = rnd.nextFloat() * size.width
            val speed = 0.6f + rnd.nextFloat() * 0.6f
            val y = -20f + fall.value * speed * (size.height + 40f)
            val sway = kotlin.math.sin((fall.value * 6f + it) * 2f) * 12f
            val color = colors[it % colors.size].copy(alpha = 1f - fall.value * 0.6f)
            drawRect(color, Offset(x + sway, y), androidx.compose.ui.geometry.Size(10f, 6f))
        }
    }
}

/** Reduce la foto a 1280 px por lado y JPEG, por debajo de 1,4 MB (el servidor guarda hasta 1,5 MB). */
fun compressPhoto(context: Context, uri: Uri): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1280) sample *= 2
    val decoded = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("No se pudo leer la foto")
    val scale = 1280f / max(decoded.width, decoded.height)
    val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true) else decoded
    var quality = 85
    while (true) {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (out.size() <= 1_400_000 || quality <= 40) return out.toByteArray()
        quality -= 10
    }
}
