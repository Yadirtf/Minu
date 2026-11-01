package com.daniel.reproductor.ui.together.minu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daniel.reproductor.cloud.MindRehearsal
import com.daniel.reproductor.cloud.MindRehearsalCase
import com.daniel.reproductor.ui.components.PillButton
import kotlinx.coroutines.launch

/** Cómo corre el ensayo: lo da TogetherViewModel (el servidor redacta y revisa, sin guardar). */
class RehearsalActions(
    val load: suspend () -> List<MindRehearsalCase>,
    val run: suspend (MindRehearsalCase) -> MindRehearsal,
)

private val PASS = Color(0xFF2E9E5B)

/** Qué quiere decir cada motivo de las barandas, en palabras. */
private fun reasonText(reason: String?) = when (reason) {
    "banned" -> "dijo algo que Minu no dice"
    "language" -> "no salió en español"
    "care" -> "sonó a riesgo"
    "short" -> "muy corto"
    "long" -> "muy largo"
    "link" -> "traía un enlace"
    "leak" -> "copió parte del encargo"
    "empty" -> "el servicio no respondió"
    "no_writer" -> "falta la llave del servicio en el servidor"
    null -> "sin revisar"
    else -> reason
}

/**
 * El ensayo de las 30 situaciones (#58/#59): se abre con siete toques a la constelación de la casita. Sirve
 * para revisar a mano cómo suena Minu y qué atrapan las barandas. No guarda nada.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinuRehearsalSheet(actions: RehearsalActions, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        MinuRehearsalContent(actions)
    }
}

@Composable
fun MinuRehearsalContent(actions: RehearsalActions) {
    val c = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var cases by remember { mutableStateOf<List<MindRehearsalCase>?>(null) }
    val results = remember { mutableStateMapOf<String, MindRehearsal>() }
    var running by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { cases = runCatching { actions.load() }.getOrDefault(emptyList()) }
    suspend fun run(case: MindRehearsalCase) {
        running = case.id
        runCatching { actions.run(case) }.onSuccess { results[case.id] = it }
        running = null
    }
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).navigationBarsPadding().padding(horizontal = 20.dp)) {
        Text("Ensayo de Minu", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Prueba cada situación y revisa cómo suena. Usa la cuota diaria del servicio, así que no hace falta repetirlas mucho.",
            style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
        )
        val list = cases
        val passed = results.values.count { it.ok }
        Row(Modifier.padding(vertical = 10.dp)) {
            PillButton(
                if (running != null) "Probando…" else "Probar las ${list?.size ?: 30}", Icons.Default.PlayArrow,
                { if (running == null && list != null) scope.launch { list.forEach { run(it) } } },
            )
            Spacer(Modifier.weight(1f))
            if (results.isNotEmpty()) Text("$passed de ${results.size} pasaron", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
        }
        if (list == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list.orEmpty(), key = { it.id }) { case ->
                RehearsalRow(case, results[case.id], running == case.id) { scope.launch { run(case) } }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun RehearsalRow(case: MindRehearsalCase, result: MindRehearsal?, running: Boolean, onRun: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(color = c.surfaceContainerHigh, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row {
                Text("${case.id} · ${case.kind.id} · ${case.mood}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onRun, enabled = !running) { Text(if (running) "…" else "Probar") }
            }
            Text(case.facts.joinToString(" ").ifBlank { "(sin recuerdos aún)" }, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            result?.let { r ->
                Spacer(Modifier.height(6.dp))
                Text(r.text ?: r.raw ?: "(no redactó nada)", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (r.ok) "Pasa las barandas · ${r.ms} ms" else "No pasa: ${reasonText(r.reason)} · ${r.ms} ms",
                    style = MaterialTheme.typography.labelMedium, color = if (r.ok) PASS else c.error,
                )
            }
        }
    }
}
