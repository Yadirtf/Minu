package com.daniel.reproductor.ui.together

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daniel.reproductor.cloud.DuoDates
import com.daniel.reproductor.cloud.DuoInfo
import com.daniel.reproductor.cloud.DuoMilestone
import com.daniel.reproductor.cloud.DuoOrigin
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.ui.components.PillButton
import com.daniel.reproductor.ui.library.LibraryViewModel
import com.daniel.reproductor.ui.theme.LocalAppStyle
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Debajo del hilo rojo: desde cuándo los une y un contador que va rotando entre datos que solo
 * existen en Minu. Sin fecha guardada, invita a guardarla.
 */
@Composable
fun OriginLine(duo: DuoInfo?, onEdit: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val origin = duo?.origin
    val start = DuoDates.parse(origin?.startedOn)
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (start == null) {
            Row(
                Modifier.border(1.dp, THREAD_RED.copy(alpha = 0.4f), LocalAppStyle.current.cardShape).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.CalendarMonth, null, tint = THREAD_RED, modifier = Modifier.size(18.dp))
                Text("  ¿Desde cuándo los une el hilo?", style = MaterialTheme.typography.titleSmall)
            }
            return@Column
        }
        val today = remember { LocalDate.now() }
        val milestone = DuoDates.milestoneOn(start, today)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Juntos desde el ${DuoDates.format(start)}",
                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
            )
            Icon(Icons.Default.Edit, "Cambiar", tint = c.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp).size(14.dp))
        }
        val count = duo?.dedicationCount ?: 0
        val facts = remember(start, count) { originFacts(start, today, count) }
        var index by remember { mutableIntStateOf(0) }
        LaunchedEffect(facts) {
            while (true) {
                delay(4_000)
                index = (index + 1) % facts.size
            }
        }
        AnimatedContent(
            if (milestone != null) DuoDates.title(milestone) else facts[index % facts.size],
            transitionSpec = { (slideInVertically { it / 2 } + fadeIn()) togetherWith (slideOutVertically { -it / 2 } + fadeOut()) },
            label = "facts",
        ) { text ->
            Text(
                text, style = MaterialTheme.typography.titleMedium,
                fontWeight = if (milestone != null) FontWeight.Bold else FontWeight.Medium,
                color = if (milestone != null) THREAD_RED else c.onSurface,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun originFacts(start: LocalDate, today: LocalDate, dedications: Int): List<String> = buildList {
    add(DuoDates.together(start, today))
    val days = DuoDates.days(start, today)
    if (days > 0) add("${DuoDates.plural(days, "día", "días")} desde el primer día")
    if (dedications > 0) add("${DuoDates.plural(dedications, "canción dedicada", "canciones dedicadas")} en Minu")
    DuoDates.next(start, today)?.let { (m, date) ->
        val left = DuoDates.days(today, date)
        val what = when (m) {
            is DuoMilestone.Anniversary -> DuoDates.yearsPhrase(m.years).removePrefix("su ").removePrefix("sus ")
            is DuoMilestone.Monthsary -> DuoDates.plural(m.months, "mes", "meses")
            is DuoMilestone.Days -> "${m.days} días"
        }
        add(if (left == 1L) "Mañana cumplen $what" else "En $left días cumplen $what")
    }
}

/** El ajuste de "nuestra fecha": el día en que empezó, la canción de origen y cómo empezó. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OriginSheet(
    origin: DuoOrigin?,
    partnerName: String,
    lib: LibraryViewModel,
    onDismiss: () -> Unit,
    onSave: (startedOn: String, song: Song?, story: String) -> Unit,
) {
    val style = LocalAppStyle.current
    val c = MaterialTheme.colorScheme
    var date by remember { mutableStateOf(DuoDates.parse(origin?.startedOn)) }
    var song by remember { mutableStateOf(origin?.song) }
    var story by remember { mutableStateOf(origin?.story.orEmpty()) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingSong by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerHigh,
        shape = style.cardShape,
    ) {
        Column(
            Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(style.label("Nuestra fecha"), style = MaterialTheme.typography.titleLarge)
            Text(
                "Es de los dos: si la cambias, $partnerName también la verá. Con ella Minu les recuerda cada mes y cada aniversario.",
                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
            )

            SheetLabel("El día en que empezó todo")
            PillButton(
                date?.let { DuoDates.format(it) } ?: "Elegir el día",
                Icons.Default.CalendarMonth, { pickingDate = true }, Modifier.fillMaxWidth(), primary = date == null,
            )

            SheetLabel("La canción de origen (opcional)")
            Text(
                "La que los representa desde el principio. Sonará en cada aniversario.",
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
            )
            val chosen = song
            if (pickingSong) {
                SongSearch(lib) { song = it; pickingSong = false }
            } else if (chosen != null) {
                SongPick(chosen, lib.thumbnail(chosen)) { pickingSong = true }
                Row {
                    TextButton(onClick = { pickingSong = true }) { Text("Elegir otra") }
                    TextButton(onClick = { song = null }) { Text("Quitar") }
                }
            } else {
                TextButton(onClick = { pickingSong = true }) {
                    Icon(Icons.Default.Search, null)
                    Text("  Buscar en mis canciones")
                }
            }

            SheetLabel("Cómo empezó (opcional)")
            OutlinedTextField(
                value = story, onValueChange = { story = it.take(140) },
                placeholder = { Text("En la fiesta de cumpleaños, bailando mal a propósito…") },
                supportingText = { Text("${story.length}/140") },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                minLines = 2, shape = style.cardShape, modifier = Modifier.fillMaxWidth(),
            )

            PillButton(
                "Guardar nuestra fecha", Icons.Default.Favorite,
                { date?.let { onSave(it.toString(), song, story.trim()) } },
                Modifier.fillMaxWidth(), primary = date != null,
            )
        }
    }

    if (pickingDate) {
        val today = remember { LocalDate.now() }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date?.toUtcMillis(),
            yearRange = 1950..today.year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today.toUtcMillis()
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickingDate = false
                }) { Text("Listo") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancelar") } },
        ) {
            DatePicker(state, title = { Text("¿Qué día empezó todo?", Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }
}

private fun LocalDate.toUtcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

@Composable
private fun SheetLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun SongSearch(lib: LibraryViewModel, onPick: (Song) -> Unit) {
    val library by lib.allSongs.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val results = if (query.isBlank()) library.orEmpty().take(30) else lib.searchLocal(query)
    OutlinedTextField(
        value = query, onValueChange = { query = it },
        placeholder = { Text("Busca en tus canciones") },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        singleLine = true, shape = LocalAppStyle.current.cardShape, modifier = Modifier.fillMaxWidth(),
    )
    LazyColumn(Modifier.heightIn(max = 300.dp)) {
        items(results, key = { it.videoId }) { s -> SongPick(s, lib.thumbnail(s)) { onPick(s) } }
    }
    if (library.isNullOrEmpty()) {
        Text("Primero guarda canciones en tu biblioteca.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
