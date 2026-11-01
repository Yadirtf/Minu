package com.daniel.reproductor.ui.together.minu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.daniel.reproductor.cloud.MinuPet
import com.daniel.reproductor.cloud.SentUnsaid
import com.daniel.reproductor.cloud.UnsaidBox
import com.daniel.reproductor.cloud.UnsaidLetter
import com.daniel.reproductor.ui.components.PillButton
import com.daniel.reproductor.ui.together.THREAD_RED

private const val PART_MAX = 200
private const val REPLY_MAX = 300
private val PAPER = Color(0xFFFFF7EE)
private val PAPER_EDGE = Color(0xFFEBD3BC)
private val INK = Color(0xFF3A2A33)
private val WAX = Color(0xFFC2263F)

/** Lo que hace el Buzón con cada acción (lo conecta TogetherScreen con el ViewModel). */
class UnsaidActions(
    val send: (feel: String, whenText: String, need: String, onSent: () -> Unit) -> Unit,
    val open: (id: String) -> Unit,
    val thank: (id: String) -> Unit,
    val reply: (id: String, text: String) -> Unit,
)

/**
 * Buzón de lo no dicho: una carta guiada en tres partes ("siento… cuando… me ayudaría…", el arranque
 * suave de Gottman) que Minu lleva sellada. Quien la recibe decide cuándo abrirla, y antes de responder
 * dice "Gracias por contármelo". Sin "visto": quien escribe solo sabe si eligieron agradecerla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinuUnsaidSheet(
    pet: MinuPet,
    box: UnsaidBox,
    partnerName: String,
    feelStarters: List<String>,
    needStarters: List<String>,
    actions: UnsaidActions,
    onDismiss: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    // Primero lo que te llegó y aún no agradeces; luego escribir; luego lo de antes
    val pending = box.incoming.firstOrNull { !it.thanked }
    val waitingMine = box.sent.firstOrNull { !it.thanked }
    var justSent by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Buzón de lo no dicho", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            when {
                pending != null -> IncomingLetter(pet, pending, partnerName, actions)
                justSent || waitingMine != null -> OnTheWay(pet, partnerName, flying = justSent)
                else -> Composer(pet, partnerName, feelStarters, needStarters) { feel, whenText, need ->
                    actions.send(feel, whenText, need) { justSent = true }
                }
            }

            val history = box.incoming.filter { it.thanked }
            if (history.isNotEmpty()) {
                Text("Lo que $partnerName te contó", style = MaterialTheme.typography.titleMedium)
                history.forEach { letter -> ReceivedLetter(letter, partnerName, actions) }
            }
            if (box.sent.isNotEmpty()) {
                Text("Lo que le contaste", style = MaterialTheme.typography.titleMedium)
                box.sent.forEach { SentLetter(it, partnerName) }
            }
            Text(
                "Las cartas se borran solas a los 30 días. Minu no las recuerda ni las usa para nada más.",
                style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ---------- Escribir ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Composer(
    pet: MinuPet,
    partnerName: String,
    feelStarters: List<String>,
    needStarters: List<String>,
    onSend: (String, String, String) -> Unit,
) {
    var feel by rememberSaveable { mutableStateOf("") }
    var whenText by rememberSaveable { mutableStateOf("") }
    var need by rememberSaveable { mutableStateOf("") }
    val ready = feel.isNotBlank() && whenText.isNotBlank() && need.isNotBlank()

    Row(verticalAlignment = Alignment.CenterVertically) {
        MinuCreature(pet.species, pet.mood, pet.stage, Modifier.size(76.dp), interactive = true)
        Spacer(Modifier.width(8.dp))
        SpeechBubble(MinuVoice.UNSAID_HINT, Modifier.weight(1f))
    }
    // La carta: papel cálido y tres renglones guiados
    Surface(color = PAPER, shape = RoundedCornerShape(6.dp, 22.dp, 6.dp, 22.dp), shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Para $partnerName,", color = INK.copy(alpha = 0.7f), fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
            LetterLine("Siento…", feel, "cómo te sientes, no lo que el otro hizo") { feel = it }
            StarterChips(feelStarters) { feel = it }
            LetterLine("cuando…", whenText, "la situación, sin “siempre” ni “nunca”") { whenText = it }
            LetterLine("Me ayudaría…", need, "algo concreto que el otro pueda hacer") { need = it }
            StarterChips(needStarters) { need = it }
        }
    }
    PillButton("Que Minu lo lleve", Icons.AutoMirrored.Filled.Send, { if (ready) onSend(feel.trim(), whenText.trim(), need.trim()) }, Modifier.fillMaxWidth(), primary = ready)
    Text(
        "$partnerName decide cuándo abrirla. No sabrás si ya la leyó, solo si eligió agradecerla.",
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LetterLine(label: String, value: String, hint: String, onChange: (String) -> Unit) {
    Column {
        Text(label, color = THREAD_RED, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = value, onValueChange = { if (it.length <= PART_MAX) onChange(it) },
            modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 4,
            placeholder = { Text(hint, color = INK.copy(alpha = 0.4f)) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = INK, fontFamily = FontFamily.Serif),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StarterChips(starters: List<String>, onPick: (String) -> Unit) {
    if (starters.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        starters.forEach { s -> AssistChip(onClick = { onPick(s) }, label = { Text(s, color = INK) }) }
    }
}

/** Después de enviar: la carta sale volando con su sello, y Minu cuenta que la lleva con cuidado. */
@Composable
private fun OnTheWay(pet: MinuPet, partnerName: String, flying: Boolean) {
    val fly = remember { Animatable(if (flying) 0f else 1f) }
    LaunchedEffect(flying) { if (flying) fly.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.BottomCenter) {
            MinuCreature(pet.species, pet.mood, pet.stage, Modifier.size(96.dp), carrying = fly.value < 1f)
            SealedEnvelope(
                Modifier.size(92.dp, 64.dp).graphicsLayer {
                    translationY = -fly.value * 230f
                    translationX = fly.value * 120f
                    rotationZ = -12f * fly.value
                    alpha = 1f - fly.value
                },
            )
        }
        SpeechBubble("${MinuVoice.UNSAID_ON_WAY}. Cuando $partnerName la reciba con calma, te cuento.")
    }
}

// ---------- Recibir ----------

@Composable
private fun IncomingLetter(pet: MinuPet, letter: UnsaidLetter, partnerName: String, actions: UnsaidActions) {
    if (letter.sealed) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                MinuCreature(pet.species, pet.mood, pet.stage, Modifier.size(90.dp), carrying = true)
                SealedEnvelope(Modifier.padding(start = 4.dp, bottom = 8.dp).size(120.dp, 84.dp))
            }
            SpeechBubble(MinuVoice.unsaidArrived(letter.fromName))
            PillButton("Abrirla ahora", Icons.Default.MarkEmailUnread, { actions.open(letter.id) }, Modifier.fillMaxWidth())
            Text(
                "Puedes abrirla cuando quieras. Nadie sabrá cuándo la leíste.",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
        return
    }
    // Abierta: el papel se despliega, y lo primero es agradecer
    val unfold = remember(letter.id) { Animatable(0f) }
    LaunchedEffect(letter.id) { unfold.animateTo(1f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow)) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LetterPaper(
            letter.fromName, letter.feel.orEmpty(), letter.whenText.orEmpty(), letter.need.orEmpty(),
            Modifier.graphicsLayer {
                scaleY = 0.3f + 0.7f * unfold.value
                alpha = unfold.value
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
            },
        )
        SpeechBubble(MinuVoice.UNSAID_OPENED)
        PillButton("Gracias por contármelo", Icons.Default.Favorite, { actions.thank(letter.id) }, Modifier.fillMaxWidth())
        Text(
            "Agradecer no significa estar de acuerdo: significa que lo escuchaste.",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Una carta que ya agradecí: se puede releer y responder una vez (opcional). */
@Composable
private fun ReceivedLetter(letter: UnsaidLetter, partnerName: String, actions: UnsaidActions) {
    var reply by rememberSaveable(letter.id) { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LetterPaper(letter.fromName, letter.feel.orEmpty(), letter.whenText.orEmpty(), letter.need.orEmpty())
        val sent = letter.reply
        if (sent != null) {
            ReplyBubble("Tú", sent)
        } else {
            AnimatedVisibility(true, enter = fadeIn() + expandVertically()) {
                OutlinedTextField(
                    value = reply, onValueChange = { if (it.length <= REPLY_MAX) reply = it },
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                    placeholder = { Text("¿Quieres responderle a $partnerName? (opcional)") },
                    trailingIcon = {
                        IconButton(onClick = { reply.trim().takeIf { it.isNotEmpty() }?.let { actions.reply(letter.id, it) } }, enabled = reply.isNotBlank()) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Enviar")
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SentLetter(letter: SentUnsaid, partnerName: String) {
    val c = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LetterPaper("Tú", letter.feel, letter.whenText, letter.need, compact = true)
        Surface(
            color = if (letter.thanked) THREAD_RED.copy(alpha = 0.10f) else c.surfaceContainerHigh,
            shape = RoundedCornerShape(50), border = if (letter.thanked) BorderStroke(1.dp, THREAD_RED.copy(alpha = 0.4f)) else null,
        ) {
            Text(
                if (letter.thanked) MinuVoice.unsaidThanked(partnerName) else "📮 Entregada con cuidado",
                style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                color = if (letter.thanked) THREAD_RED else c.onSurfaceVariant,
            )
        }
        letter.reply?.let { ReplyBubble(partnerName, it) }
    }
}

@Composable
private fun ReplyBubble(who: String, text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(who, style = MaterialTheme.typography.labelMedium, color = THREAD_RED)
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

/** La carta en su papel: "Siento… cuando… me ayudaría…" con letra de carta y un hilo rojo al margen. */
@Composable
fun LetterPaper(from: String, feel: String, whenText: String, need: String, modifier: Modifier = Modifier, compact: Boolean = false) {
    Surface(color = PAPER, shape = RoundedCornerShape(6.dp, 22.dp, 6.dp, 22.dp), shadowElevation = 2.dp, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Canvas(Modifier.width(18.dp).fillMaxHeight()) {
                // Un hilo rojo cosido al margen
                val x = size.width * 0.55f
                var y = 14f
                while (y < size.height - 10f) {
                    drawLine(THREAD_RED.copy(alpha = 0.75f), Offset(x, y), Offset(x, y + 10f), strokeWidth = 3f)
                    y += 18f
                }
            }
            Column(Modifier.padding(top = 14.dp, bottom = 14.dp, end = 16.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 6.dp)) {
                if (!compact) Text("De $from", color = INK.copy(alpha = 0.6f), fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.labelLarge)
                LetterText("Siento", feel, compact)
                LetterText("cuando", whenText, compact)
                LetterText("Me ayudaría", need, compact)
            }
        }
    }
}

@Composable
private fun LetterText(lead: String, text: String, compact: Boolean) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = THREAD_RED, fontWeight = FontWeight.SemiBold)) { append("$lead ") }
            append(text)
        },
        color = INK, fontFamily = FontFamily.Serif,
        style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
        maxLines = if (compact) 2 else Int.MAX_VALUE,
    )
}

/** Sobre de papel cerrado con un sello de lacre en forma de corazón (con su borde irregular). */
@Composable
fun SealedEnvelope(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            Brush.verticalGradient(listOf(PAPER, lerp(PAPER, PAPER_EDGE, 0.6f))), size = Size(w, h), cornerRadius = CornerRadius(h * 0.1f),
        )
        val flap = Path().apply { moveTo(0f, 0f); lineTo(w / 2f, h * 0.58f); lineTo(w, 0f) }
        drawPath(flap, lerp(PAPER, PAPER_EDGE, 0.25f))
        drawPath(flap, PAPER_EDGE, style = Stroke(2f))
        val bottom = Path().apply { moveTo(0f, h); lineTo(w * 0.42f, h * 0.48f); moveTo(w, h); lineTo(w * 0.58f, h * 0.48f) }
        drawPath(bottom, PAPER_EDGE, style = Stroke(2f))
        // Lacre: un disco irregular con un corazón en relieve
        val center = Offset(w / 2f, h * 0.56f)
        val r = h * 0.2f
        val blob = Path().apply {
            for (i in 0..16) {
                val a = (i / 16f) * 2f * Math.PI.toFloat()
                val wobble = 1f + 0.08f * kotlin.math.sin(a * 5f + 1.3f)
                val p = Offset(center.x + kotlin.math.cos(a) * r * wobble, center.y + kotlin.math.sin(a) * r * wobble)
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(blob, Brush.radialGradient(listOf(lerp(WAX, Color.White, 0.18f), WAX, lerp(WAX, Color.Black, 0.25f)), center - Offset(r * 0.3f, r * 0.3f), r * 1.6f))
        rotate(-8f, center) {
            val s = r * 0.5f
            val heart = Path().apply {
                moveTo(center.x, center.y + s * 0.8f)
                cubicTo(center.x - s * 1.3f, center.y - s * 0.1f, center.x - s * 0.6f, center.y - s * 1.1f, center.x, center.y - s * 0.35f)
                cubicTo(center.x + s * 0.6f, center.y - s * 1.1f, center.x + s * 1.3f, center.y - s * 0.1f, center.x, center.y + s * 0.8f)
                close()
            }
            drawPath(heart, lerp(WAX, Color.Black, 0.3f), style = Stroke(r * 0.12f))
            drawPath(heart, lerp(WAX, Color.White, 0.1f))
        }
    }
}
