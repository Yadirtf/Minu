package com.daniel.reproductor.ui.together.minu

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daniel.reproductor.cloud.Compliment
import com.daniel.reproductor.cloud.ComplimentBox
import com.daniel.reproductor.cloud.LoveQuestion
import com.daniel.reproductor.cloud.MinuPet
import com.daniel.reproductor.cloud.PetMood
import com.daniel.reproductor.ui.components.PillButton
import com.daniel.reproductor.ui.together.THREAD_RED
import com.daniel.reproductor.ui.together.WARM
import kotlin.math.cos
import kotlin.math.sin

private const val COMPLIMENT_MAX = 200
private val GOLD = Color(0xFFFFD27A)

class ComplimentActions(val write: (List<String>) -> Unit, val see: (String) -> Unit, val heart: (String) -> Unit)

/**
 * Cumplidos a ciegas: cada uno le cuenta a Minu tres cosas que admira del otro, y Minu las suelta de una en
 * una, en días que nadie sabe (como máximo una por semana). Cuando llega una, es una cajita de regalo que
 * se abre; un 💛 de vuelta le cuenta a quien la escribió que llegó al corazón.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinuComplimentsSheet(pet: MinuPet, box: ComplimentBox, partnerName: String, actions: ComplimentActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        MinuComplimentsContent(pet, box, partnerName, actions)
    }
}

/** El contenido de la hoja, aparte para poder verlo en capturas. */
@Composable
fun MinuComplimentsContent(pet: MinuPet, box: ComplimentBox, partnerName: String, actions: ComplimentActions) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Cumplidos a ciegas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        box.surprise?.let { GiftReveal(pet, it, actions) }

        if (box.askNew) Composer(pet, partnerName, actions)
        else Pocket(pet, box.pending, partnerName)

        val opened = box.received.filter { it.seen }
        if (opened.isNotEmpty()) {
            Text("Lo que $partnerName admira de ti", style = MaterialTheme.typography.titleMedium)
            opened.forEach { ComplimentCard(it, actions) }
        }
        if (box.delivered.isNotEmpty()) {
            Text("Lo que ya le llegó de ti", style = MaterialTheme.typography.titleMedium)
            box.delivered.forEach { d ->
                Surface(color = c.surfaceContainerHigh, shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("“${d.text}”", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, modifier = Modifier.weight(1f))
                        if (d.heart) Text("💛", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Composer(pet: MinuPet, partnerName: String, actions: ComplimentActions) {
    val texts = remember { mutableStateListOf("", "", "") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        MinuCreature(pet.species, pet.mood, pet.stage, Modifier.size(76.dp), interactive = true)
        Spacer(Modifier.width(8.dp))
        SpeechBubble("Cuéntame 3 cosas que admiras de $partnerName. Yo se las doy de sorpresa, una por una, en días que nadie sabe.", Modifier.weight(1f))
    }
    val hints = listOf("Cómo te hace reír…", "Algo que hace por los demás…", "Una cualidad que te inspira…")
    texts.indices.forEach { i ->
        OutlinedTextField(
            value = texts[i], onValueChange = { if (it.length <= COMPLIMENT_MAX) texts[i] = it },
            modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 3,
            leadingIcon = { Text(listOf("✨", "🌟", "💫")[i]) },
            placeholder = { Text(hints[i]) },
        )
    }
    val ready = texts.all { it.isNotBlank() }
    PillButton("Guardar en el bolsillo de Minu", Icons.Default.AutoAwesome, { if (ready) actions.write(texts.map { it.trim() }) }, Modifier.fillMaxWidth(), primary = ready)
    Text(
        "$partnerName no sabrá nada hasta el día de cada sorpresa.",
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Mientras Minu guarda los cumplidos: un bolsillo con estrellitas que asoman (una por cada pendiente). */
@Composable
private fun Pocket(pet: MinuPet, pending: Int, partnerName: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.BottomCenter) {
                MinuCreature(pet.species, PetMood.HAPPY, pet.stage, Modifier.size(84.dp), carrying = pending > 0)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("✨".repeat(pending.coerceIn(0, 3)).ifEmpty { "🌙" }, style = MaterialTheme.typography.titleLarge)
                Text(
                    if (pending > 0) "Guardo $pending ${if (pending == 1) "cumplido tuyo" else "cumplidos tuyos"} para $partnerName. Shhh, es sorpresa."
                    else "Ya le di todos. Pronto te pido nuevos.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** La cajita de regalo: se sacude, se abre la tapa con un estallido de luz y aparece el cumplido. */
@Composable
private fun GiftReveal(pet: MinuPet, compliment: Compliment, actions: ComplimentActions) {
    var opened by rememberSaveable(compliment.id) { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val lid = remember { Animatable(0f) }
    LaunchedEffect(compliment.id) {
        // Se sacude un poco para invitar a tocarla
        repeat(3) {
            shake.animateTo(1f, tween(80)); shake.animateTo(-1f, tween(120)); shake.animateTo(0f, tween(80))
            kotlinx.coroutines.delay(700)
        }
    }
    LaunchedEffect(opened) { if (opened) lid.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
    Surface(color = THREAD_RED.copy(alpha = 0.08f), shape = RoundedCornerShape(26.dp), border = BorderStroke(1.dp, THREAD_RED.copy(alpha = 0.35f))) {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (opened) "De ${compliment.fromName}, con cariño" else "Minu tiene algo para ti", style = MaterialTheme.typography.titleMedium, color = THREAD_RED)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(180.dp, 150.dp).clickable(enabled = !opened) { opened = true; actions.see(compliment.id) }) {
                GiftBox(lid.value, Modifier.size(170.dp, 140.dp).graphicsLayer { rotationZ = shake.value * 4f })
            }
            if (opened) {
                Text(
                    "“${compliment.text}”", style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif,
                    textAlign = TextAlign.Center, modifier = Modifier.graphicsLayer { alpha = lid.value; translationY = (1f - lid.value) * 30f },
                )
                PillButton("💛 Me llegó al corazón", Icons.Default.Favorite, { actions.heart(compliment.id) }, Modifier.fillMaxWidth())
            } else {
                Text("Tócala para abrirla", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ComplimentCard(c: Compliment, actions: ComplimentActions) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("“${c.text}”", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, modifier = Modifier.weight(1f))
            if (c.heart) Text("💛", style = MaterialTheme.typography.titleMedium)
            else TextButton(onClick = { actions.heart(c.id) }) { Text("💛") }
        }
    }
}

/** Una caja de regalo con moño de hilo rojo; [open] 0..1 levanta la tapa y suelta rayos dorados. */
@Composable
fun GiftBox(open: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val boxTop = h * 0.45f
        val boxLeft = w * 0.18f
        val boxW = w * 0.64f
        // Rayos de luz al abrir
        if (open > 0f) {
            val center = Offset(w / 2, boxTop)
            repeat(10) { i ->
                val a = Math.toRadians(200.0 + i * 14.0)
                val len = h * 0.55f * open
                drawLine(GOLD.copy(alpha = 0.7f * open), center, Offset(center.x + cos(a).toFloat() * len, center.y + sin(a).toFloat() * len), strokeWidth = 5f)
            }
            drawCircle(Brush.radialGradient(listOf(GOLD.copy(alpha = 0.6f * open), Color.Transparent), center, h * 0.4f), h * 0.4f, center)
        }
        // Caja
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFF6B84), THREAD_RED)), Offset(boxLeft, boxTop), Size(boxW, h * 0.5f), CornerRadius(12f))
        drawRect(Color.White.copy(alpha = 0.85f), Offset(w / 2 - 9f, boxTop), Size(18f, h * 0.5f))
        // Tapa que sube y gira
        val lift = open * h * 0.35f
        rotate(-25f * open, Offset(boxLeft, boxTop - lift)) {
            drawRoundRect(Color(0xFFFF8BA0), Offset(boxLeft - w * 0.04f, boxTop - h * 0.14f - lift), Size(boxW + w * 0.08f, h * 0.15f), CornerRadius(10f))
            drawRect(Color.White.copy(alpha = 0.85f), Offset(w / 2 - 9f, boxTop - h * 0.14f - lift), Size(18f, h * 0.15f))
            // Moño
            val bow = Offset(w / 2, boxTop - h * 0.16f - lift)
            drawOval(Color.White, Offset(bow.x - 34f, bow.y - 18f), Size(32f, 22f))
            drawOval(Color.White, Offset(bow.x + 2f, bow.y - 18f), Size(32f, 22f))
            drawCircle(Color(0xFFFFE3EA), 8f, bow)
        }
    }
}

// ---------- Lenguajes del amor ----------

/**
 * El test opcional: 10 pares, se elige el que más te llega. Al final no hay etiqueta ("eres de…"): Minu
 * solo dice que lo usará para proponer planes y gestos. Aclara que es una guía para conversar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinuLoveQuizSheet(pet: MinuPet, partnerName: String, quiz: List<LoveQuestion>, onFinish: (List<String>) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        MinuLoveQuizContent(pet, partnerName, quiz, onFinish, onDone = onDismiss)
    }
}

/** El contenido de la hoja, aparte para poder verlo en capturas. */
@Composable
fun MinuLoveQuizContent(pet: MinuPet, partnerName: String, quiz: List<LoveQuestion>, onFinish: (List<String>) -> Unit, onDone: () -> Unit) {
    val answers = remember { mutableStateListOf<String>() }
    var done by remember { mutableStateOf(false) }
    var hop by remember { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("¿Qué te hace sentir que te quieren?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        MinuCreature(pet.species, if (done) PetMood.RADIANT else PetMood.HAPPY, pet.stage, Modifier.size(96.dp), celebrate = hop)
        if (quiz.isEmpty()) {
            SpeechBubble("Necesito conexión para traer las preguntas. Inténtalo en un momento.")
            Spacer(Modifier.height(16.dp))
            return@Column
        }
        if (done) {
            SpeechBubble("¡Gracias! Lo usaré para proponerles planes y gestos que les lleguen a los dos.")
            Text(
                "Es una guía para conversar, no ciencia exacta. Pregúntale a $partnerName: ¿qué gesto mío te llegó más esta semana?",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            PillButton("Listo", Icons.Default.Favorite, onDone, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            return@Column
        }
        val index = answers.size
        // Puntitos de progreso
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            quiz.indices.forEach { i ->
                Box(Modifier.size(if (i == index) 10.dp else 7.dp).background(if (i < index) THREAD_RED else MaterialTheme.colorScheme.outlineVariant, CircleShape))
            }
        }
        Text("Si tuvieras que elegir…", style = MaterialTheme.typography.titleMedium)
        AnimatedContent(
            index,
            transitionSpec = { (slideInHorizontally { it } + fadeIn()) togetherWith (slideOutHorizontally { -it } + fadeOut()) },
            label = "pregunta",
        ) { i ->
            val q = quiz[i.coerceAtMost(quiz.lastIndex)]
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(q.a, q.b).forEachIndexed { k, option ->
                    Surface(
                        color = if (k == 0) THREAD_RED.copy(alpha = 0.10f) else WARM.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.fillMaxWidth().clickable {
                            answers.add(option.lang)
                            hop++
                            if (answers.size == quiz.size) { done = true; onFinish(answers.toList()) }
                        },
                    ) {
                        Text(option.text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 18.dp, vertical = 22.dp).fillMaxWidth())
                    }
                }
            }
        }
        Text("Pregunta ${index + 1} de ${quiz.size} · Sin respuestas correctas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (index > 0) TextButton(onClick = { answers.removeAt(answers.lastIndex) }) { Text("Volver a la anterior") }
        Spacer(Modifier.height(12.dp))
    }
}
