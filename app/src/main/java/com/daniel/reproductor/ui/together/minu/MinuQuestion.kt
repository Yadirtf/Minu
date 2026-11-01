package com.daniel.reproductor.ui.together.minu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AssistChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daniel.reproductor.cloud.DailyQuestion
import com.daniel.reproductor.cloud.MinuPet
import com.daniel.reproductor.ui.components.PillButton
import com.daniel.reproductor.ui.theme.LocalAppStyle
import com.daniel.reproductor.ui.together.THREAD_RED
import com.daniel.reproductor.ui.together.WARM

private const val ANSWER_MAX = 500

/**
 * La Pregunta del día: cada uno responde y su respuesta queda sellada en un sobre. Cuando responden los
 * dos, el sobre se abre (con animación) y aparecen ambas respuestas; Minu invita a reaccionar con algo
 * más que un emoji. Abajo se pueden releer las que ya abrieron.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MinuQuestionSheet(
    pet: MinuPet,
    question: DailyQuestion,
    partnerName: String,
    reactions: List<String>,
    past: List<DailyQuestion>?,
    onAnswer: (String) -> Unit,
    onReact: (String) -> Unit,
    onLoadPast: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val style = LocalAppStyle.current
    var draft by rememberSaveable(question.day) { mutableStateOf("") }
    var showPast by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MinuCreature(pet.species, pet.mood, pet.stage, Modifier.size(76.dp), interactive = true)
                Spacer(Modifier.width(8.dp))
                SpeechBubble(MinuVoice.questionLine(question, partnerName), Modifier.weight(1f))
            }

            // La pregunta, en una tarjeta de papel con el sobre
            Column(
                Modifier.fillMaxWidth().clip(style.cardShape)
                    .background(Brush.verticalGradient(listOf(THREAD_RED.copy(alpha = 0.14f), WARM.copy(alpha = 0.08f), c.surfaceContainer)))
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(color = THREAD_RED.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
                    Text(
                        "Pregunta ${MinuVoice.levelLabel(question.level).lowercase()}", color = THREAD_RED,
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(question.text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Envelope(opened = question.opened, sealed = question.mine != null || question.partnerDone)
            }

            when {
                question.mine == null -> {
                    OutlinedTextField(
                        value = draft, onValueChange = { if (it.length <= ANSWER_MAX) draft = it },
                        modifier = Modifier.fillMaxWidth(), minLines = 3,
                        placeholder = { Text("Escribe con calma, solo lo verá $partnerName…") },
                        supportingText = { Text("${draft.length}/$ANSWER_MAX · Se abre cuando respondan los dos") },
                    )
                    PillButton("Sellar mi respuesta", Icons.Default.Lock, { if (draft.isNotBlank()) onAnswer(draft.trim()) }, Modifier.fillMaxWidth())
                }
                !question.opened -> AnswerCard("Lo que respondiste", question.mine, mine = true, reaction = null)
                else -> {
                    // Las respuestas aparecen cuando la solapa del sobre ya se abrió
                    val reveal = remember { MutableTransitionState(false).apply { targetState = true } }
                    AnimatedVisibility(
                        visibleState = reveal,
                        enter = fadeIn(tween(500, delayMillis = 650)) + slideInVertically(tween(500, delayMillis = 650)) { it / 3 },
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AnswerCard(partnerName, question.partner ?: "", mine = false, reaction = question.myReaction?.let { "Tú: $it" })
                            if (question.myReaction == null) {
                                Text("¿Qué le dices?", style = MaterialTheme.typography.titleSmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    reactions.forEach { r -> AssistChip(onClick = { onReact(r) }, label = { Text(r) }) }
                                }
                            }
                            AnswerCard("Tú", question.mine, mine = true, reaction = question.partnerReaction?.let { "$partnerName: $it" })
                        }
                    }
                }
            }

            TextButton(onClick = { showPast = !showPast; if (showPast) onLoadPast() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showPast) "Ocultar lo que se han contado" else "Lo que se han contado")
            }
            if (showPast) {
                val list = past?.filter { it.day != question.day }
                when {
                    list == null -> Text("Buscando sus sobres…", color = c.onSurfaceVariant)
                    list.isEmpty() -> Text("Aquí van a quedar las preguntas que abran juntos.", color = c.onSurfaceVariant)
                    else -> list.forEach { q -> PastQuestion(q, partnerName) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AnswerCard(title: String, text: String, mine: Boolean, reaction: String?) {
    val c = MaterialTheme.colorScheme
    Surface(
        color = if (mine) c.surfaceContainerHigh else c.surfaceContainerHighest,
        shape = RoundedCornerShape(20.dp),
        border = if (mine) null else BorderStroke(1.dp, THREAD_RED.copy(alpha = 0.45f)),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = if (mine) c.onSurfaceVariant else THREAD_RED)
            Spacer(Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.bodyLarge)
            reaction?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = c.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PastQuestion(q: DailyQuestion, partnerName: String) {
    val c = MaterialTheme.colorScheme
    Surface(color = c.surfaceContainerHigh, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(q.text, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            q.mine?.let { Text("Tú: $it", style = MaterialTheme.typography.bodyMedium) }
            q.partner?.let { Text("$partnerName: $it", style = MaterialTheme.typography.bodyMedium, color = THREAD_RED) }
            Text(q.day, style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * El sobre de la pregunta: cerrado con un sello de corazón mientras falta alguien; al abrirse, la solapa
 * gira hacia atrás y asoman dos papelitos.
 */
@Composable
private fun Envelope(opened: Boolean, sealed: Boolean) {
    val flap = remember { Animatable(1f) }
    LaunchedEffect(opened) {
        if (opened) flap.animateTo(-1f, tween(700, delayMillis = 200, easing = FastOutSlowInEasing)) else flap.snapTo(1f)
    }
    val paper = Color(0xFFFFF4E8)
    val edge = Color(0xFFE9CDB4)
    Canvas(Modifier.size(150.dp, 104.dp)) {
        val w = size.width
        val h = size.height
        val top = h * 0.3f
        val f = flap.value
        // Papelitos que salen al abrir
        val out = ((-f).coerceAtLeast(0f))
        if (out > 0f) {
            for ((i, color) in listOf(Color.White, Color(0xFFFFE3EA)).withIndex()) {
                val px = w * (0.22f + i * 0.3f)
                drawRoundRect(
                    color, topLeft = Offset(px, top - h * 0.35f * out + i * 4f), size = Size(w * 0.26f, h * 0.5f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f),
                )
            }
        }
        // Cuerpo del sobre
        drawRoundRect(
            Brush.verticalGradient(listOf(paper, lerp(paper, edge, 0.5f))), topLeft = Offset(0f, top), size = Size(w, h - top),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f),
        )
        val sides = Path().apply {
            moveTo(0f, top); lineTo(w / 2f, top + (h - top) * 0.55f); lineTo(w, top)
            moveTo(0f, h); lineTo(w * 0.42f, top + (h - top) * 0.45f)
            moveTo(w, h); lineTo(w * 0.58f, top + (h - top) * 0.45f)
        }
        drawPath(sides, edge, style = Stroke(2.5f))
        // Solapa: f=1 cerrada (apunta abajo), f=-1 abierta (apunta arriba)
        val tip = top + (h - top) * 0.55f * f
        val flapPath = Path().apply { moveTo(0f, top); lineTo(w / 2f, tip); lineTo(w, top); close() }
        drawPath(flapPath, if (f > 0f) lerp(paper, edge, 0.25f) else lerp(paper, edge, 0.55f))
        drawPath(flapPath, edge, style = Stroke(2.5f))
        // Sello de corazón mientras está cerrado
        if (sealed && f > 0.2f) {
            val center = Offset(w / 2f, tip - 4f)
            drawCircle(THREAD_RED, 15f, center)
            val heart = Path().apply {
                val s = 8f
                moveTo(center.x, center.y + s * 0.5f)
                cubicTo(center.x - s, center.y - s * 0.2f, center.x - s * 0.4f, center.y - s, center.x, center.y - s * 0.35f)
                cubicTo(center.x + s * 0.4f, center.y - s, center.x + s, center.y - s * 0.2f, center.x, center.y + s * 0.5f)
                close()
            }
            drawPath(heart, Color.White.copy(alpha = 0.9f))
        }
    }
}
