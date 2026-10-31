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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daniel.reproductor.cloud.Capsule
import com.daniel.reproductor.cloud.DuoDates
import com.daniel.reproductor.cloud.DuoInfo
import com.daniel.reproductor.cloud.DuoPrefs
import com.daniel.reproductor.cloud.Memories
import com.daniel.reproductor.cloud.MissionState
import com.daniel.reproductor.cloud.MixtapeTrack
import com.daniel.reproductor.cloud.SealedLetter
import com.daniel.reproductor.ui.components.ArtworkImage
import com.daniel.reproductor.ui.components.PillButton
import java.time.LocalDate

/**
 * Una Cinta del mes en Recuerdos. [month] es el mes de la relación (cuenta desde el día en que empezó todo);
 * [from]..[to) son las fechas que graba, recortadas al día en que llegaron a Minu si la relación es anterior.
 */
private data class Tape(
    val month: Int,
    val from: LocalDate,
    val to: LocalDate,
    val partial: Boolean,
    val recording: Boolean,
    val songs: Int?,
    val capsules: List<Capsule>,
)

/** Desde qué fechas se graban cintas: la relación cuenta para el número del mes, Minu para lo que se guarda. */
private class TapeCalendar(val start: LocalDate, joined: LocalDate?, today: LocalDate) {
    /** El día desde el que hay cintas (nunca antes del inicio de la relación). */
    val joined: LocalDate = joined?.takeIf { it.isAfter(start) } ?: start

    /** El mes que está grabándose ahora. */
    val current = DuoDates.monthsBetween(start, today) + 1

    /** La primera cinta: el mes de la relación en el que llegaron a Minu. */
    val first = (DuoDates.monthsBetween(start, this.joined) + 1).coerceAtMost(current)

    /** Las misiones se revelan cuando el mes termina (en el mesiversario). */
    fun missionUnlocked(month: Int) = month < current

    fun tape(month: Int, memories: Memories?): Tape {
        val (rangeFrom, to) = DuoDates.monthRange(start, month)
        val from = maxOf(rangeFrom, joined)
        val songs = memories?.dedicationsByDay?.entries
            ?.filter { (day, _) -> DuoDates.parse(day)?.let { !it.isBefore(from) && it.isBefore(to) } == true }
            ?.sumOf { it.value }
        val capsules = memories?.capsules.orEmpty().filter { c ->
            DuoDates.parse(c.day)?.let { !it.isBefore(from) && it.isBefore(to) } == true
        }
        return Tape(month, from, to, partial = from != rangeFrom, recording = month == current, songs = songs, capsules = capsules)
    }

    /** De la más nueva (la que se está grabando) a la más vieja. */
    fun tapes(memories: Memories?) = (current downTo first).map { tape(it, memories) }
}

private fun range(from: LocalDate, toExclusive: LocalDate) = "${DuoDates.short(from)} – ${DuoDates.short(toExclusive.minusDays(1))}"

/**
 * Recuerdos: las Cintas de cada mes en tarjetas por año, y dentro de cada cinta los retos de ese mes
 * (la misión y las cápsulas con las que salvaron el hilo). [startTape] abre directamente la cinta de ese mes
 * (desde el mesiversario).
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
    val memories by vm.memories.collectAsStateWithLifecycle()
    var tape by rememberSaveable { mutableStateOf(startTape) }
    var capsule by remember { mutableStateOf<Capsule?>(null) }
    var writing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadMemories() }
    if (writing) {
        LetterDialog(duo?.letter, partnerName, onDismiss = { writing = false }) { vm.writeLetter(it); writing = false }
    }
    val start = DuoDates.parse(duo?.origin?.startedOn)
    val today = remember { LocalDate.now() }
    val calendar = start?.let { TapeCalendar(it, DuoDates.parse(duo?.joinedOn), today) }

    FullScreen(onDismiss) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 16.dp)) {
            val openTape = tape
            val openCapsule = capsule
            when {
                openCapsule != null -> {
                    BackHandler { capsule = null }
                    ScreenHeader("Cápsula", DuoDates.parse(openCapsule.day)?.let { DuoDates.format(it) }, onBack = { capsule = null })
                    CapsuleReveal(openCapsule, partnerName, onPlay = { vm.playSong(it) }, animate = false)
                }
                openTape != null && calendar != null -> {
                    BackHandler { tape = null }
                    MonthView(
                        vm, calendar.tape(openTape, memories), memories != null, calendar, duo?.missions?.get(openTape), partnerName, playing,
                        onMission = vm::markMission, onCapsule = { capsule = it }, onBack = { tape = null },
                    )
                }
                else -> {
                    ScreenHeader("Recuerdos", "Lo que su música ha guardado", onDismiss)
                    MemoriesGrid(
                        calendar, duo, memories, partnerName, onTape = { tape = it }, onCapsule = { capsule = it },
                        onAnniversary = { vm.showAnniversary(it) }, onLetter = { writing = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoriesGrid(
    calendar: TapeCalendar?,
    duo: DuoInfo?,
    memories: Memories?,
    partnerName: String,
    onTape: (Int) -> Unit,
    onCapsule: (Capsule) -> Unit,
    onAnniversary: (Int) -> Unit,
    onLetter: () -> Unit,
) {
    val tapes = calendar?.tapes(memories).orEmpty()
    val byYear = tapes.groupBy { it.from.year }
    val years = byYear.keys.sortedDescending()
    // -1: aún no eligen (se muestra el año más reciente); 0: todos los años
    var picked by rememberSaveable { mutableIntStateOf(-1) }
    val year = when {
        picked == 0 -> null
        picked in years -> picked
        else -> years.firstOrNull()
    }
    val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (calendar != null) specialDates(duo, partnerName, onAnniversary, onLetter)

        item(span = full) { Section("Cintas del mes") }
        if (calendar == null) {
            item(span = full) { Hint("Guarda en Dúo el día en que empezó todo: cada mes tendrá su cinta con las canciones que se dedicaron.") }
            // Sin fecha no hay meses: las cápsulas quedan sueltas
            memories?.capsules?.let { list ->
                if (list.isNotEmpty()) {
                    item(span = full) { Section("Cápsulas") }
                    list.forEach { c -> item(span = full, key = c.day) { CapsuleRow(c, onCapsule) } }
                }
            }
            return@LazyVerticalGrid
        }

        if (calendar.first > 1) item(span = full) { JoinedNote(calendar) }

        if (years.size > 1) {
            item(span = full) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { YearChip("Todos", year == null) { picked = 0 } }
                    items(years) { y -> YearChip("$y", year == y) { picked = y } }
                }
            }
        }

        years.filter { year == null || it == year }.forEach { y ->
            val list = byYear.getValue(y)
            item(span = full, key = "y$y") {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                    Text("$y", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        DuoDates.plural(list.size, "cinta", "cintas"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            list.forEach { t ->
                item(key = "t${t.month}") { TapeCard(t, duo?.missions?.get(t.month), calendar.missionUnlocked(t.month)) { onTape(t.month) } }
            }
        }
    }
}

/** Aniversarios y la carta sellada, en una fila que se desliza de lado para no alargar la pantalla. */
private fun LazyGridScope.specialDates(duo: DuoInfo?, partnerName: String, onAnniversary: (Int) -> Unit, onLetter: () -> Unit) {
    val anniversaries = duo?.anniversaries.orEmpty().sortedByDescending { it.years }
    val letter = duo?.letter
    if (anniversaries.isEmpty() && letter == null) return
    item(span = { GridItemSpan(maxLineSpan) }) {
        Column {
            Section("Fechas especiales")
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                letter?.let { l ->
                    item {
                        val opens = DuoDates.parse(l.opensOn)?.let { DuoDates.short(it) } ?: l.opensOn
                        SmallCard(
                            "💌", "Carta para ${DuoDates.yearsPhrase(l.year)}",
                            (if (l.mine != null) "Sellada" else "Escríbela") + " · se abre el $opens" +
                                if (l.partnerWrote) " · $partnerName ya selló la suya" else "",
                            onClick = onLetter,
                        )
                    }
                }
                items(anniversaries, key = { "a${it.years}" }) { a ->
                    SmallCard(
                        "🎁", if (a.years == 1) "Nuestro primer año" else "${a.years} años",
                        (DuoDates.parse(a.date)?.let { DuoDates.format(it) } ?: a.date) + if (a.opened) "" else " · envuelto, ábranlo juntos",
                    ) { onAnniversary(a.years) }
                }
            }
        }
    }
}

@Composable
private fun SmallCard(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier.width(190.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick).padding(12.dp),
    ) {
        Text(emoji, fontSize = 22.sp)
        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun YearChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = THREAD_RED,
            selectedLabelColor = Color.White,
        ),
    )
}

/**
 * Por qué la primera cinta no es la del mes 1: la fecha de la relación cuenta para los mesiversarios,
 * aniversarios y el número de cada cinta, pero las canciones solo existen desde que llegaron a Minu.
 * Se explica una vez completa; luego queda una línea para volver a leerla.
 */
@Composable
private fun JoinedNote(calendar: TapeCalendar) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(DuoPrefs.seen(context, "tapes_note") == null) }
    if (!open) {
        Text(
            "ⓘ ¿Por qué la primera cinta es la del mes ${calendar.first}?",
            style = MaterialTheme.typography.bodySmall, color = THREAD_RED,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { open = true }.padding(vertical = 4.dp),
        )
        return
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(THREAD_RED.copy(alpha = 0.12f)).padding(14.dp),
    ) {
        Text("💞 Juntos desde el ${DuoDates.format(calendar.start)}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Esa fecha es la de su historia: con ella cuentan sus mesiversarios, aniversarios y el número de cada cinta.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Las cintas guardan las canciones que se dedican en Minu, así que empezaron a grabarse el " +
                "${DuoDates.format(calendar.joined)}, cuando llegaron aquí. Por eso la primera es la del mes ${calendar.first} " +
                "y no verán meses vacíos de antes.",
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(
            onClick = { DuoPrefs.setSeen(context, "tapes_note", "1"); open = false },
            modifier = Modifier.align(Alignment.End),
        ) { Text("Entendido") }
    }
}

/** La tarjeta de una cinta: el casete, sus fechas y lo que guarda ese mes. */
@Composable
private fun TapeCard(t: Tape, mission: MissionState?, missionUnlocked: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick).padding(10.dp),
    ) {
        Cassette(Modifier.fillMaxWidth(), t.month, spin = 0f)
        Spacer(Modifier.height(8.dp))
        Text(range(t.from, t.to), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
        val details = listOfNotNull(
            t.songs?.let { if (it == 0 && !t.recording) "En blanco" else "🎵 $it" },
            t.capsules.size.takeIf { it > 0 }?.let { "💌 $it" },
            if (missionUnlocked) if (mission?.done == true) "✅ misión" else "🎯 misión" else null,
        )
        Text(
            details.joinToString("  "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (t.recording) {
            Text("● Grabándose", style = MaterialTheme.typography.labelSmall, color = THREAD_RED, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CapsuleRow(c: Capsule, onCapsule: (Capsule) -> Unit) {
    MemoryRow(
        "💌", "Cápsula del " + (DuoDates.parse(c.day)?.let { DuoDates.format(it) } ?: c.day), c.prompts.firstOrNull().orEmpty(),
    ) { onCapsule(c) }
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


/**
 * Un mes de recuerdos: la Cinta del mes (lado A tus dedicatorias, lado B las suyas) y los retos de ese mes,
 * la misión y las cápsulas con las que salvaron el hilo.
 */
@Composable
private fun MonthView(
    vm: TogetherViewModel,
    t: Tape,
    loaded: Boolean,
    calendar: TapeCalendar,
    mission: MissionState?,
    partnerName: String,
    playing: Boolean,
    onMission: (Int, Boolean) -> Unit,
    onCapsule: (Capsule) -> Unit,
    onBack: () -> Unit,
) {
    val tape by vm.mixtape.collectAsStateWithLifecycle()
    LaunchedEffect(t.from, t.to) { vm.loadMixtape(t.from.toString(), t.to.toString()) }
    val spin by rememberInfiniteTransition(label = "carretes").animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "giro")

    ScreenHeader("Cinta del mes ${t.month}", range(t.from, t.to) + " ${t.from.year}" + if (t.recording) " · grabándose" else "", onBack)
    val mix = tape?.takeIf { it.from == t.from.toString() }
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Cassette(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp), t.month, spin = if (playing) spin else 0f)
        }
        if (t.partial) {
            item { Hint("Esta cinta empezó a grabarse el ${DuoDates.format(t.from)}, cuando llegaron a Minu.") }
        }
        val all = mix?.inOrder?.map { it.song }.orEmpty()
        when {
            mix == null -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            all.isEmpty() -> item {
                Hint(if (t.recording) "Esta cinta se está grabando: cada canción que se dediquen este mes quedará aquí." else "Esta cinta está en blanco: no hubo dedicatorias en estas fechas.")
            }
            else -> item {
                Column {
                    Text(
                        "${all.size} canciones · la banda sonora de su mes ${t.month}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    PillButton("Escuchar la cinta", Icons.Default.PlayArrow, { vm.playAll(all) }, Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    PillButton("Escucharla juntos", Icons.Default.Headphones, { vm.playAll(all); vm.inviteListen() }, Modifier.fillMaxWidth(), primary = false)
                }
            }
        }

        // Los retos del mes, junto a su cinta
        item {
            Column {
                Text("Retos del mes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                HorizontalDivider(Modifier.padding(top = 6.dp))
            }
        }
        item {
            if (calendar.missionUnlocked(t.month)) {
                MissionRow(t.month, mission, partnerName, onMission)
            } else {
                MemoryRow("🎯", "Misión del mes ${t.month}", "Se revela el ${DuoDates.format(t.to)}, en su mesiversario") {}
            }
        }
        when {
            !loaded -> item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            t.capsules.isEmpty() -> item {
                Hint(if (t.recording) "Si el hilo se deshilacha y lo salvan con una cápsula, quedará aquí." else "Este mes no tuvieron que salvar el hilo 💪")
            }
            else -> items(t.capsules, key = { it.day }) { c -> CapsuleRow(c, onCapsule) }
        }

        if (mix != null && all.isNotEmpty()) {
            side("Lado A · tus dedicatorias", mix.sideA, vm)
            side("Lado B · las de $partnerName", mix.sideB, vm)
        }
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
