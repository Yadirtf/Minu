package com.daniel.reproductor.ui.together.minu

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daniel.reproductor.cloud.MinuPet
import com.daniel.reproductor.cloud.MinuPromise
import com.daniel.reproductor.cloud.PromiseBoard
import com.daniel.reproductor.ui.components.PillButton
import com.daniel.reproductor.ui.together.THREAD_RED
import com.daniel.reproductor.ui.together.WARM
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.sin

private const val PROMISE_MAX = 120

class PromiseActions(
    val make: (text: String, dueAt: Long, onMade: () -> Unit) -> Unit,
    val keep: (id: String) -> Unit,
    val confirm: (id: String) -> Unit,
    val drop: (id: String) -> Unit,
)

/**
 * Promesas pequeñas: "te llamo a las 8", "paso por el pan". Minu se lo recuerda solo a quien prometió;
 * cuando se cumple, el otro lo agradece y su hilo gana un nudito. Las que no se cumplen se van solas,
 * sin reproche: aquí nunca hay una lista de lo que faltó.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinuPromisesSheet(pet: MinuPet, board: PromiseBoard, partnerName: String, actions: PromiseActions, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var celebrate by remember { mutableIntStateOf(0) }
    LaunchedEffect(board.kept) { if (board.kept > 0) celebrate++ }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Promesas pequeñas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                MinuCreature(pet.species, pet.mood, pet.stage, Modifier.size(76.dp), interactive = true, celebrate = celebrate)
                Spacer(Modifier.width(8.dp))
                SpeechBubble(MinuVoice.PROMISE_HINT, Modifier.weight(1f))
            }
            KnottedThread(board.kept, Modifier.fillMaxWidth().height(56.dp))
            Text(MinuVoice.promisesKept(board.kept), style = MaterialTheme.typography.labelLarge, color = THREAD_RED, modifier = Modifier.align(Alignment.CenterHorizontally))

            PromiseComposer(partnerName, actions)

            val (mine, theirs) = board.items.partition { it.mine }
            if (theirs.isNotEmpty()) {
                Text("Lo que $partnerName te prometió", style = MaterialTheme.typography.titleMedium)
                theirs.forEach { PromiseCard(it, partnerName, actions) }
            }
            if (mine.isNotEmpty()) {
                Text("Lo que tú prometiste", style = MaterialTheme.typography.titleMedium)
                mine.forEach { PromiseCard(it, partnerName, actions) }
            }
            Text(
                "Si una promesa no se cumple, se borra sola a los dos días. Sin reproches: mañana es otra oportunidad.",
                style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Para cuándo: atajos de todos los días y una hora a elección. */
private enum class DueChoice(val label: String) { HOUR("En 1 hora"), TONIGHT("Esta noche"), TOMORROW("Mañana"), PICK("Elegir hora") }

private fun dueFor(choice: DueChoice, picked: LocalTime?, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? = when (choice) {
    DueChoice.HOUR -> now.plusHours(1)
    DueChoice.TONIGHT -> now.toLocalDate().atTime(20, 0).let { if (it.isAfter(now.plusMinutes(10))) it else now.plusHours(1) }
    DueChoice.TOMORROW -> now.toLocalDate().plusDays(1).atTime(9, 0)
    DueChoice.PICK -> picked?.let { t -> now.toLocalDate().atTime(t).let { if (it.isAfter(now)) it else it.plusDays(1) } }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PromiseComposer(partnerName: String, actions: PromiseActions) {
    var text by rememberSaveable { mutableStateOf("") }
    var choice by rememberSaveable { mutableStateOf(DueChoice.TONIGHT) }
    var picked by remember { mutableStateOf<LocalTime?>(null) }
    var picking by remember { mutableStateOf(false) }
    val due = dueFor(choice, picked)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = text, onValueChange = { if (it.length <= PROMISE_MAX) text = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                prefix = { Text("Te prometo ") },
                placeholder = { Text("llamarte a las 8") },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DueChoice.entries.forEach { w ->
                    FilterChip(
                        selected = choice == w,
                        onClick = { choice = w; if (w == DueChoice.PICK) picking = true },
                        label = { Text(if (w == DueChoice.PICK && picked != null) picked!!.format(TIME) else w.label) },
                    )
                }
            }
            due?.let { Text("Te lo recuerdo ${relative(it.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())}, unos minutos antes.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            PillButton(
                "Prometérselo a $partnerName", Icons.Default.Favorite,
                {
                    val at = due?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                    if (text.isNotBlank() && at != null) actions.make(text.trim(), at) { text = "" }
                },
                Modifier.fillMaxWidth(), primary = text.isNotBlank() && due != null,
            )
        }
    }
    if (picking) {
        val state = rememberTimePickerState(initialHour = picked?.hour ?: 20, initialMinute = picked?.minute ?: 0)
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("¿A qué hora?") },
            text = { TimePicker(state) },
            confirmButton = { TextButton(onClick = { picked = LocalTime.of(state.hour, state.minute); picking = false }) { Text("Listo") } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun PromiseCard(p: MinuPromise, partnerName: String, actions: PromiseActions) {
    val c = MaterialTheme.colorScheme
    val past = p.dueAt < System.currentTimeMillis()
    val attention = !p.mine && !p.confirmed && p.kept
    Surface(
        color = if (p.confirmed) THREAD_RED.copy(alpha = 0.08f) else c.surfaceContainerHigh,
        shape = RoundedCornerShape(6.dp, 22.dp, 22.dp, 22.dp),
        border = if (attention) BorderStroke(1.5.dp, THREAD_RED.copy(alpha = 0.6f)) else null,
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (p.confirmed) "🪢" else "🤞", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (p.mine) "Te prometo ${p.text}" else "${p.byName} te prometió ${p.text}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    // Pasada la hora no se marca nada en rojo: solo se deja de mostrar la hora
                    Text(
                        when {
                            p.confirmed -> "¡Cumplida! Un nudito más en su hilo"
                            p.kept && p.mine -> "Le avisé a $partnerName que cumpliste"
                            p.kept -> "Dice que ya lo cumplió"
                            past -> "Cuando puedas"
                            else -> relative(p.dueAt).replaceFirstChar { it.uppercase() }
                        },
                        style = MaterialTheme.typography.labelMedium, color = if (p.confirmed) THREAD_RED else c.onSurfaceVariant,
                    )
                }
            }
            when {
                p.confirmed -> Unit
                p.mine && !p.kept -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("¡Cumplí!", Icons.Default.Check, { actions.keep(p.id) }, Modifier.weight(1f))
                    TextButton(onClick = { actions.drop(p.id) }) { Text("Soltarla") }
                }
                !p.mine -> PillButton(
                    if (p.kept) "Gracias, ¡lo cumpliste! 💛" else "¿Ya lo cumplió? Agradécele 💛",
                    Icons.Default.Favorite, { actions.confirm(p.id) }, Modifier.fillMaxWidth(), primary = p.kept,
                )
            }
        }
    }
}

/**
 * Su hilo con un nudito por cada promesa cumplida (hasta 12 visibles). El último nudo se aprieta con un
 * rebote cuando aparece.
 */
@Composable
fun KnottedThread(count: Int, modifier: Modifier = Modifier) {
    val pop = remember { Animatable(1f) }
    var shown by remember { mutableIntStateOf(count) }
    LaunchedEffect(count) {
        if (count > shown) {
            shown = count
            pop.snapTo(0f)
            pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow))
        } else shown = count
    }
    Canvas(modifier) {
        val w = size.width
        val mid = size.height / 2f
        val wave = { x: Float -> mid + sin(x / w * 2f * Math.PI.toFloat() * 1.5f) * size.height * 0.16f }
        val thread = Path().apply {
            moveTo(0f, wave(0f))
            var x = 0f
            while (x <= w) { lineTo(x, wave(x)); x += 6f }
        }
        drawPath(thread, THREAD_RED.copy(alpha = 0.85f), style = Stroke(4f, cap = StrokeCap.Round))
        val visible = count.coerceAtMost(12)
        if (visible == 0) return@Canvas
        val step = w / (visible + 1)
        for (i in 1..visible) {
            val x = step * i
            val y = wave(x)
            val last = i == visible
            val r = 7f * (if (last) 0.4f + 0.6f * pop.value else 1f)
            // Un nudo: un lazo apretado alrededor del hilo, con su cuenta al centro
            rotate(-35f, Offset(x, y)) {
                drawOval(
                    THREAD_RED, Offset(x - r * 1.5f, y - r), androidx.compose.ui.geometry.Size(r * 3f, r * 2f),
                    style = Stroke(3f, cap = StrokeCap.Round),
                )
            }
            drawCircle(THREAD_RED, r * 0.85f, Offset(x, y))
            drawCircle(WARM.copy(alpha = 0.9f), r * 0.35f, Offset(x - r * 0.25f, y - r * 0.25f))
        }
    }
}

private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.forLanguageTag("es-CO"))

/** "hoy a las 8:00 p. m.", "mañana a las 9:00 a. m." o "el viernes a las…". */
private fun relative(at: Long): String {
    val dt = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    val time = dt.toLocalTime().format(TIME)
    return when (dt.toLocalDate()) {
        today -> "hoy a las $time"
        today.plusDays(1) -> "mañana a las $time"
        else -> "el ${dt.format(DateTimeFormatter.ofPattern("EEEE d", Locale.forLanguageTag("es-CO")))} a las $time"
    }
}
