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
import com.daniel.reproductor.cloud.MindRehearsalCase
import com.daniel.reproductor.cloud.MindVerdict
import com.daniel.reproductor.minu.MindReadiness
import com.daniel.reproductor.ui.components.PillButton
import kotlinx.coroutines.launch

/** Lo que salió en una situación del ensayo: el texto redactado y si pasó las barandas. */
data class RehearsalResult(val raw: String?, val verdict: MindVerdict?, val ms: Long)

/** Cómo corre el ensayo: lo da TogetherViewModel (redactar dentro del celular y revisar en el servidor). */
class RehearsalActions(
    val load: suspend () -> List<MindRehearsalCase>,
    val readiness: suspend () -> MindReadiness,
    val run: suspend (MindRehearsalCase) -> RehearsalResult,
    /** Pide a AICore que descargue lo necesario ahora (en el ensayo lo pide la persona a propósito). */
    val prepare: suspend () -> Boolean = { false },
)

private val PASS = Color(0xFF2E9E5B)

/**
 * El ensayo de las 30 situaciones (#58/#59): se abre con siete toques a la constelación de la casita. Sirve
 * para revisar a mano, en un celular compatible, cómo suena Minu y qué atrapan las barandas. No guarda nada.
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
    var readiness by remember { mutableStateOf<MindReadiness?>(null) }
    val results = remember { mutableStateMapOf<String, RehearsalResult>() }
    var running by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        readiness = actions.readiness()
        cases = runCatching { actions.load() }.getOrDefault(emptyList())
    }
    suspend fun run(case: MindRehearsalCase) {
        running = case.id
        results[case.id] = actions.run(case)
        running = null
    }
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).navigationBarsPadding().padding(horizontal = 20.dp)) {
        Text("Ensayo de Minu", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            when (readiness) {
                MindReadiness.READY -> "Este celular puede redactar. Prueba cada situación y revisa cómo suena."
                MindReadiness.DOWNLOADABLE -> "Este celular puede redactar, pero primero necesita prepararse."
                MindReadiness.PREPARING -> "Preparando este celular… puede tardar unos minutos. Puedes cerrar y volver."
                MindReadiness.UNAVAILABLE -> "Este celular no puede redactar: aquí Minu habla con su banco de frases."
                null -> "Revisando este celular…"
            },
            style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
        )
        if (readiness == MindReadiness.DOWNLOADABLE) {
            PillButton(
                "Preparar este celular", Icons.Default.PlayArrow,
                {
                    readiness = MindReadiness.PREPARING
                    scope.launch { actions.prepare(); readiness = actions.readiness() }
                },
                Modifier.padding(top = 10.dp),
            )
            Text("Es una descarga grande: mejor con Wi-Fi y el celular cargando.", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
        }
        val list = cases
        val passed = results.values.count { it.verdict?.ok == true }
        Row(Modifier.padding(vertical = 10.dp)) {
            PillButton(
                if (running != null) "Probando…" else "Probar las ${list?.size ?: 30}", Icons.Default.PlayArrow,
                { if (running == null && list != null) scope.launch { list.forEach { run(it) } } },
                primary = readiness == MindReadiness.READY,
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
private fun RehearsalRow(case: MindRehearsalCase, result: RehearsalResult?, running: Boolean, onRun: () -> Unit) {
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
                val ok = r.verdict?.ok == true
                Text(
                    r.verdict?.text ?: r.raw ?: "(no redactó nada)",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    when {
                        r.raw == null -> "Sin texto: aquí hablaría el banco · ${r.ms} ms"
                        ok -> "Pasa las barandas · ${r.ms} ms"
                        else -> "No pasa (${r.verdict?.reason ?: "sin revisar"}) · ${r.ms} ms"
                    },
                    style = MaterialTheme.typography.labelMedium, color = if (ok) PASS else c.error,
                )
            }
        }
    }
}
