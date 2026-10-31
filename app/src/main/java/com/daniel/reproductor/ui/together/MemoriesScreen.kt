package com.daniel.reproductor.ui.together

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daniel.reproductor.cloud.Capsule
import com.daniel.reproductor.cloud.DuoDates
import com.daniel.reproductor.cloud.DuoInfo
import com.daniel.reproductor.cloud.MissionState
import com.daniel.reproductor.cloud.MixtapeTrack
import com.daniel.reproductor.cloud.SealedLetter
import com.daniel.reproductor.ui.components.ArtworkImage
import com.daniel.reproductor.ui.components.PillButton
import java.time.LocalDate

/**
 * Recuerdos: las Cintas de cada mes, las cápsulas que abrieron al salvar el hilo y las misiones.
 * [startTape] abre directamente la cinta de ese mes (desde el mesiversario).
 */
@Composable
fun MemoriesScreen(
    vm: TogetherViewModel,
    duo: DuoInfo?,
    partnerName: String,
    startTape: Int?,
    playing: Boolean,
    onDismiss: () -> Unit,
) {
    val capsules by vm.memories.collectAsStateWithLifecycle()
    var tape by rememberSaveable { mutableStateOf(startTape) }
    var capsule by remember { mutableStateOf<Capsule?>(null) }
    var writing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadMemories() }
    if (writing) {
        LetterDialog(duo?.letter, partnerName, onDismiss = { writing = false }) { vm.writeLetter(it); writing = false }
    }
    val start = DuoDates.parse(duo?.origin?.startedOn)
    val today = remember { LocalDate.now() }

    FullScreen(onDismiss) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
            val openTape = tape
            val openCapsule = capsule
            when {
                openTape != null && start != null -> {
                    BackHandler { tape = null }
                    MixtapeView(vm, start, openTape, partnerName, playing) { tape = null }
                }
                openCapsule != null -> {
                    BackHandler { capsule = null }
                    ScreenHeader("Cápsula", DuoDates.parse(openCapsule.day)?.let { DuoDates.format(it) }, onBack = { capsule = null })
                    CapsuleReveal(openCapsule, partnerName, onPlay = { vm.playSong(it) }, animate = false)
                }
                else -> {
                    ScreenHeader("Recuerdos", "Lo que su música ha guardado", onDismiss)
                    MemoriesList(
                        start, today, duo, capsules, partnerName, onTape = { tape = it }, onCapsule = { capsule = it }, onMission = vm::markMission,
                        onAnniversary = { vm.showAnniversary(it) }, onLetter = { writing = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoriesList(
    start: LocalDate?,
    today: LocalDate,
    duo: DuoInfo?,
    capsules: List<Capsule>?,
    partnerName: String,
    onTape: (Int) -> Unit,
    onCapsule: (Capsule) -> Unit,
    onMission: (Int, Boolean) -> Unit,
    onAnniversary: (Int) -> Unit,
    onLetter: () -> Unit,
) {
    val months = start?.let { DuoDates.monthsBetween(it, today) } ?: 0
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (start != null) {
            item { Section("Aniversarios") }
            items(duo?.anniversaries.orEmpty().sortedByDescending { it.years }, key = { "a${it.years}" }) { a ->
                MemoryRow(
                    "🎁", if (a.years == 1) "Nuestro primer año" else "${a.years} años",
                    (DuoDates.parse(a.date)?.let { DuoDates.format(it) } ?: a.date) + if (a.opened) "" else " · envuelto, ábranlo juntos",
                ) { onAnniversary(a.years) }
            }
            duo?.letter?.let { letter ->
                item {
                    val opens = DuoDates.parse(letter.opensOn)?.let { DuoDates.format(it) } ?: letter.opensOn
                    MemoryRow(
                        "💌", "Carta para ${DuoDates.yearsPhrase(letter.year)}",
                        (if (letter.mine != null) "Sellada" else "Escríbela") + " · se abre el $opens" +
                            if (letter.partnerWrote) " · $partnerName ya selló la suya" else "",
                        onClick = onLetter,
                    )
                }
            }
        }
        item { Section("Cintas del mes") }
        if (start == null) {
            item { Hint("Guarda en Dúo el día en que empezó todo: cada mes tendrá su cinta con las canciones que se dedicaron.") }
        } else {
            // La del mes en curso (aún grabándose) y las ya terminadas, de la más nueva a la más vieja
            items((months + 1 downTo 1).toList()) { m ->
                val (from, to) = DuoDates.monthRange(start, m)
                TapeRow(m, "${DuoDates.short(from)} – ${DuoDates.short(to.minusDays(1))}", recording = m == months + 1) { onTape(m) }
            }
        }

        item { Section("Cápsulas") }
        when {
            capsules == null -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            capsules.isEmpty() -> item { Hint("Cuando salven el hilo con una cápsula de tres canciones, quedará aquí.") }
            else -> items(capsules, key = { it.day }) { c ->
                MemoryRow("💌", DuoDates.parse(c.day)?.let { DuoDates.format(it) } ?: c.day, c.prompts.firstOrNull().orEmpty()) { onCapsule(c) }
            }
        }

        if (start != null && months >= 1) {
            item { Section("Misiones") }
            items((months downTo 1).toList()) { m ->
                MissionRow(m, duo?.missions?.get(m), partnerName, onMission)
            }
        }
    }
}

/** La carta para el próximo aniversario: se puede reescribir mientras siga sellada. */
@Composable
private fun LetterDialog(letter: SealedLetter?, partnerName: String, onDismiss: () -> Unit, onSeal: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(letter?.mine.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Carta para $partnerName") },
        text = {
            Column {
                Text(
                    "Se abrirá cuando abran juntos su próximo aniversario. Hasta entonces nadie la puede leer.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = text, onValueChange = { text = it.take(3000) }, minLines = 6, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { if (text.isNotBlank()) onSeal(text.trim()) }, enabled = text.isNotBlank()) { Text("Sellar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun Section(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MemoryRow(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 26.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TapeRow(month: Int, range: String, recording: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cassette(Modifier.width(72.dp), month, spin = 0f)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Mes $month", style = MaterialTheme.typography.titleSmall)
            Text(
                if (recording) "$range · grabándose" else range,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MissionRow(month: Int, state: MissionState?, partnerName: String, onMission: (Int, Boolean) -> Unit) {
    val mine = state?.mine == true
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(14.dp),
    ) {
        Text("Mes $month", style = MaterialTheme.typography.labelMedium, color = THREAD_RED)
        Text(DuoDates.mission(month), style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    state?.done == true -> "Cumplida por los dos 🎶"
                    state?.partner == true -> "$partnerName ya la marcó"
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onMission(month, !mine) }) { Text(if (mine) "Desmarcar" else "Lo hicimos") }
        }
    }
}

/** Un casete con la etiqueta del mes. Los carretes giran mientras suena. */
@Composable
internal fun Cassette(modifier: Modifier, month: Int, spin: Float) {
    Box(modifier.aspectRatio(1.6f)) {
        Canvas(Modifier.fillMaxSize()) { drawCassette(spin) }
        Text(
            "MES $month", color = Color(0xFF2B1B2E), fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic,
            fontSize = 11.sp, modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
        )
    }
}

private fun DrawScope.drawCassette(spin: Float) {
    val w = size.width
    val h = size.height
    drawRoundRect(Brush.linearGradient(listOf(Color(0xFF2E2240), Color(0xFF4A2A44))), cornerRadius = CornerRadius(w * 0.06f))
    // Etiqueta de papel con las rayas rojas del hilo
    drawRoundRect(Color(0xFFFFF8EC), topLeft = Offset(w * 0.07f, h * 0.08f), size = Size(w * 0.86f, h * 0.56f), cornerRadius = CornerRadius(w * 0.03f))
    drawRect(THREAD_RED, topLeft = Offset(w * 0.07f, h * 0.36f), size = Size(w * 0.86f, h * 0.03f))
    // Ventana y carretes
    drawRoundRect(Color(0xFF1A1424), topLeft = Offset(w * 0.24f, h * 0.42f), size = Size(w * 0.52f, h * 0.2f), cornerRadius = CornerRadius(h * 0.1f))
    listOf(0.34f, 0.66f).forEach { x ->
        val c = Offset(w * x, h * 0.52f)
        val r = h * 0.085f
        drawCircle(Color(0xFFEDE3D6), radius = r, center = c)
        rotate(spin, pivot = c) {
            for (k in 0 until 6) {
                rotate(k * 60f, pivot = c) { drawRect(Color(0xFF1A1424), topLeft = Offset(c.x - r * 0.12f, c.y - r * 0.9f), size = Size(r * 0.24f, r * 0.45f)) }
            }
        }
        drawCircle(Color(0xFF1A1424), radius = r, center = c, style = Stroke(h * 0.012f))
    }
    // Agujeros de abajo
    drawRoundRect(Color(0xFF1A1424), topLeft = Offset(w * 0.22f, h * 0.78f), size = Size(w * 0.56f, h * 0.14f), cornerRadius = CornerRadius(h * 0.04f))
}

/** La Cinta del mes: el casete, lado A (tus dedicatorias) y lado B (las suyas). */
@Composable
private fun MixtapeView(vm: TogetherViewModel, start: LocalDate, month: Int, partnerName: String, playing: Boolean, onBack: () -> Unit) {
    val (from, to) = remember(start, month) { DuoDates.monthRange(start, month) }
    val tape by vm.mixtape.collectAsStateWithLifecycle()
    LaunchedEffect(from, to) { vm.loadMixtape(from.toString(), to.toString()) }
    val spin by rememberInfiniteTransition(label = "carretes").animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "giro")

    ScreenHeader("Cinta del mes $month", "${DuoDates.short(from)} – ${DuoDates.short(to.minusDays(1))}", onBack)
    val t = tape?.takeIf { it.from == from.toString() }
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Cassette(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp), month, spin = if (playing) spin else 0f)
        }
        if (t == null) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            return@LazyColumn
        }
        val all = t.inOrder.map { it.song }
        if (all.isEmpty()) {
            item { Hint("Esta cinta está en blanco: no hubo dedicatorias en estas fechas.") }
            return@LazyColumn
        }
        item {
            Column {
                Text(
                    "${all.size} canciones · la banda sonora de su mes $month",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                PillButton("Escuchar la cinta", Icons.Default.PlayArrow, { vm.playAll(all) }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                PillButton("Escucharla juntos", Icons.Default.Headphones, { vm.playAll(all); vm.inviteListen() }, Modifier.fillMaxWidth(), primary = false)
            }
        }
        side("Lado A · tus dedicatorias", t.sideA, vm)
        side("Lado B · las de $partnerName", t.sideB, vm)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.side(title: String, tracks: List<MixtapeTrack>, vm: TogetherViewModel) {
    item {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
            HorizontalDivider(Modifier.padding(top = 6.dp))
        }
    }
    if (tracks.isEmpty()) item { Hint("Sin canciones en este lado.") }
    items(tracks) { tr ->
        Row(Modifier.fillMaxWidth().clickable { vm.playSong(tr.song) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtworkImage(tr.song.thumbnailUrl, Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tr.song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(DuoDates.parse(tr.day)?.let { DuoDates.short(it) }, tr.message.takeIf { it.isNotBlank() }?.let { "“$it”" }).joinToString(" · "),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
