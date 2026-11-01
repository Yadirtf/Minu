package com.daniel.reproductor.ui.together.minu

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daniel.reproductor.cloud.MinuPet
import com.daniel.reproductor.cloud.PetMood
import com.daniel.reproductor.cloud.Truce
import com.daniel.reproductor.cloud.TrucePhase
import com.daniel.reproductor.ui.components.PillButton
import com.daniel.reproductor.ui.theme.LocalAppStyle
import com.daniel.reproductor.ui.together.THREAD_RED
import com.daniel.reproductor.ui.together.WARM
import kotlinx.coroutines.delay

/** Azul de calma para la Tregua: aquí el rojo del hilo descansa. */
private val CALM = Color(0xFF7FA8D9)
private const val REPAIR_MAX = 140

/** Respiración 4-6: cuatro segundos para inhalar, seis para soltar (baja la activación del cuerpo). */
private const val INHALE_MS = 4_000
private const val EXHALE_MS = 6_000

/** La fase real: si el reloj ya llegó, la calma terminó aunque el servidor aún no lo sepa. */
private fun phaseNow(t: Truce, now: Long) = if (t.phase == TrucePhase.CALM && now >= t.endsAt) TrucePhase.REPAIR else t.phase

/** Reloj que avanza cada segundo (solo mientras se ve). */
@Composable
private fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    return now
}

/**
 * Minu respirando: crece al inhalar y se encoge al soltar, con un halo de calma que sigue el mismo
 * ritmo. [breath] 0 = vacío, 1 = lleno. Se usa en la Tregua y en la invitación a pedirla.
 */
@Composable
fun BreathingMinu(pet: MinuPet, modifier: Modifier = Modifier, showCue: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "respirar")
    val breath by transition.animateFloat(
        0f, 0f,
        infiniteRepeatable(
            keyframes {
                durationMillis = INHALE_MS + EXHALE_MS
                0f at 0 using FastOutSlowInEasing
                1f at INHALE_MS using FastOutSlowInEasing
                0f at INHALE_MS + EXHALE_MS
            },
        ),
        label = "aliento",
    )
    val cycle by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(INHALE_MS + EXHALE_MS, easing = LinearEasing), RepeatMode.Restart), label = "ciclo",
    )
    val inhaling = cycle < INHALE_MS.toFloat() / (INHALE_MS + EXHALE_MS)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(190.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val base = size.minDimension * 0.30f
                for (i in 3 downTo 1) {
                    val r = base + i * size.minDimension * 0.05f * (0.5f + breath)
                    drawCircle(CALM.copy(alpha = 0.07f * (4 - i) * (0.6f + breath * 0.4f)), r, center)
                }
                drawCircle(
                    Brush.radialGradient(listOf(CALM.copy(alpha = 0.28f), Color.Transparent), center, base * (1.1f + breath * 0.35f)),
                    base * (1.1f + breath * 0.35f), center,
                )
            }
            MinuCreature(
                pet.species, PetMood.HAPPY, pet.stage,
                Modifier.size(120.dp).graphicsLayer {
                    val s = 0.92f + breath * 0.14f
                    scaleX = s * (1f + breath * 0.02f)
                    scaleY = s
                    translationY = -breath * 6f
                },
            )
        }
        if (showCue) {
            AnimatedContent(inhaling, transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(600)) }, label = "guía") { inhale ->
                Text(
                    if (inhale) "Inhala…" else "Suelta despacio…",
                    style = MaterialTheme.typography.titleMedium, color = CALM, fontStyle = FontStyle.Italic,
                )
            }
        }
    }
}

/** Anillo que se vacía con el tiempo de calma que queda. */
@Composable
private fun CalmRing(remainingMs: Long, totalMs: Long, modifier: Modifier = Modifier) {
    val fraction = if (totalMs <= 0) 0f else (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(72.dp)) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(CALM, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        val total = (remainingMs / 1000).coerceAtLeast(0)
        Text("%d:%02d".format(total / 60, total % 60), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * La invitación a pedir una pausa: explica por qué sirve y que Minu no contará nada de la discusión.
 * Se abre desde el botón "Pausa" de la tarjeta de Minu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinuTruceInvite(pet: MinuPet, partnerName: String, onStart: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("¿Necesitas una pausa?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            BreathingMinu(pet, showCue = false)
            Text(MinuVoice.TRUCE_INVITE, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            Text(
                "A $partnerName le llegará: “Necesito una pausa. Minu cuida 20 minutos de calma y después se reencuentran.”",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            PillButton("Necesito una pausa", Icons.Default.Spa, { onStart() }, Modifier.fillMaxWidth())
            TextButton(onClick = onDismiss) { Text("Ahora no") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * La tarjeta de Minu mientras hay Tregua: Minu deja de jugar y respira. Reemplaza al Toque y a lo del
 * día hasta que se reencuentran. Tocarla abre la Tregua completa.
 */
@Composable
fun TruceCard(pet: MinuPet, truce: Truce, partnerName: String, onOpen: () -> Unit) {
    val now = rememberNow()
    val phase = phaseNow(truce, now)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = LocalAppStyle.current.cardShape,
        border = BorderStroke(1.dp, CALM.copy(alpha = 0.5f)), modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("🕊️ Tregua", style = MaterialTheme.typography.labelLarge, color = CALM)
                Text(
                    when (phase) {
                        TrucePhase.CALM -> if (truce.mine) "Tómate tu tiempo. Yo cuido el silencio." else "$partnerName necesita un momento. Yo cuido el silencio."
                        TrucePhase.REPAIR -> when {
                            truce.partnerRepair != null && truce.myRepair == null -> "$partnerName ya eligió su frase. ¿Y tú?"
                            truce.myRepair != null -> "Le llevé tu frase. Ya casi."
                            else -> "La calma terminó. ¿Se reencuentran?"
                        }
                        TrucePhase.TOGETHER -> MinuVoice.TRUCE_TOGETHER
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Spacer(Modifier.width(12.dp))
            if (phase == TrucePhase.CALM) CalmRing(truce.endsAt - now, truce.endsAt - truce.startedAt)
            else MinuCreature(pet.species, if (phase == TrucePhase.TOGETHER) PetMood.RADIANT else PetMood.HAPPY, pet.stage, Modifier.size(64.dp))
        }
    }
}

/** La Tregua completa: calma con Minu respirando, frases de reparación y el reencuentro. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MinuTruceSheet(
    pet: MinuPet,
    truce: Truce,
    partnerName: String,
    repairs: List<String>,
    onExtend: () -> Unit,
    onReady: () -> Unit,
    onRepair: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val now = rememberNow()
    val phase = phaseNow(truce, now)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (phase) {
                TrucePhase.CALM -> {
                    Text("Un momento de calma", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    BreathingMinu(pet)
                    CalmRing(truce.endsAt - now, truce.endsAt - truce.startedAt)
                    SpeechBubble(MinuVoice.truceCalm(truce))
                    Text(
                        "Mientras tanto, algo que te calme: agua, caminar un poco, una canción. No hace falta pensar en qué decir.",
                        style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
                    )
                    if (truce.mine) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (truce.canExtend) TextButton(onClick = onExtend) { Text("Necesito más tiempo") }
                            TextButton(onClick = onReady) { Text("Ya estoy listo") }
                        }
                    }
                }
                TrucePhase.REPAIR -> RepairStep(pet, truce, partnerName, repairs, onRepair)
                TrucePhase.TOGETHER -> Reunion(pet, truce, partnerName)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepairStep(pet: MinuPet, truce: Truce, partnerName: String, repairs: List<String>, onRepair: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    var own by rememberSaveable { mutableStateOf("") }
    Text("Volver a encontrarse", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    MinuCreature(pet.species, PetMood.HAPPY, pet.stage, Modifier.size(110.dp), interactive = true)
    truce.partnerRepair?.let { RepairNote(partnerName, it, mine = false) }
    val mine = truce.myRepair
    if (mine != null) {
        RepairNote("Tú", mine, mine = true)
        SpeechBubble(MinuVoice.truceWaiting(partnerName))
        return
    }
    SpeechBubble(MinuVoice.TRUCE_REPAIR)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repairs.forEach { phrase ->
            FilterChip(selected = picked == phrase, onClick = { picked = phrase; own = "" }, label = { Text(phrase) })
        }
    }
    OutlinedTextField(
        value = own, onValueChange = { if (it.length <= REPAIR_MAX) { own = it; picked = null } },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        placeholder = { Text("O dilo con tus palabras…") },
        trailingIcon = {
            IconButton(onClick = { own.trim().takeIf { it.isNotEmpty() }?.let(onRepair) }, enabled = own.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, "Enviar")
            }
        },
    )
    PillButton(
        "Llevarle esta frase", Icons.AutoMirrored.Filled.Send,
        { picked?.let(onRepair) }, Modifier.fillMaxWidth(),
        primary = picked != null,
    )
    Text(
        "No hace falta explicar la discusión. Una frase basta para empezar.",
        style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
    )
}

@Composable
private fun RepairNote(who: String, text: String, mine: Boolean) {
    Surface(
        color = if (mine) MaterialTheme.colorScheme.surfaceContainerHighest else CALM.copy(alpha = 0.16f),
        shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(who, style = MaterialTheme.typography.labelMedium, color = if (mine) MaterialTheme.colorScheme.onSurfaceVariant else CALM)
            Text("“$text”", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic)
        }
    }
}

/** El reencuentro: dos corazones que se acercan desde los lados y Minu salta de alegría. */
@Composable
private fun Reunion(pet: MinuPet, truce: Truce, partnerName: String) {
    val meet = remember { Animatable(0f) }
    LaunchedEffect(Unit) { meet.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessVeryLow)) }
    Text("Se reencontraron", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(170.dp)) {
            val y = size.height * 0.28f
            val gap = size.width * 0.38f * (1f - meet.value)
            drawHeart(Offset(size.width / 2 - 18f - gap, y), 22f, THREAD_RED)
            drawHeart(Offset(size.width / 2 + 18f + gap, y), 22f, WARM)
            // El hilo que los une se tensa al encontrarse
            drawLine(THREAD_RED.copy(alpha = 0.6f), Offset(size.width / 2 - 18f - gap, y + 14f), Offset(size.width / 2 + 18f + gap, y + 14f), strokeWidth = 3f)
        }
        MinuCreature(pet.species, PetMood.RADIANT, pet.stage, Modifier.padding(top = 50.dp).size(110.dp), celebrate = if (meet.value > 0.9f) 1 else 0)
    }
    truce.partnerRepair?.let { RepairNote(partnerName, it, mine = false) }
    truce.myRepair?.let { RepairNote("Tú", it, mine = true) }
    SpeechBubble(MinuVoice.TRUCE_TOGETHER)
    Text("Nada de esto se guarda: la pausa no queda en Recuerdos.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHeart(center: Offset, s: Float, color: Color) {
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(center.x, center.y + s * 0.9f)
        cubicTo(center.x - s * 1.4f, center.y - s * 0.1f, center.x - s * 0.7f, center.y - s * 1.2f, center.x, center.y - s * 0.4f)
        cubicTo(center.x + s * 0.7f, center.y - s * 1.2f, center.x + s * 1.4f, center.y - s * 0.1f, center.x, center.y + s * 0.9f)
        close()
    }
    drawPath(path, color)
}
