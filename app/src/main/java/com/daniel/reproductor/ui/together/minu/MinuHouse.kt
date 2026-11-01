package com.daniel.reproductor.ui.together.minu

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daniel.reproductor.cloud.MinuPet
import com.daniel.reproductor.cloud.PetMood
import com.daniel.reproductor.cloud.PetProposal
import com.daniel.reproductor.cloud.PetSpecies
import com.daniel.reproductor.ui.components.PillButton
import com.daniel.reproductor.ui.theme.LocalAppStyle
import com.daniel.reproductor.ui.together.THREAD_RED
import com.daniel.reproductor.ui.together.WARM

/** Lo que hace la casita con cada acción (lo conecta TogetherScreen con el ViewModel). */
class MinuHouseActions(
    val propose: (kind: String, value: String) -> Unit,
    val accept: (kind: String) -> Unit,
    val drop: (kind: String) -> Unit,
    val erase: () -> Unit,
)

/**
 * La casita de Minu: cómo está, cuánto ha crecido, su nombre y su forma (que se cambian de común
 * acuerdo), lo que viene pronto y el botón para borrar lo que le contaste.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinuHouse(pet: MinuPet, partnerName: String, actions: MinuHouseActions, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val style = LocalAppStyle.current
    var renaming by rememberSaveable { mutableStateOf(false) }
    var confirmErase by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Minu en su casita
            Column(
                Modifier.fillMaxWidth().clip(style.cardShape)
                    .background(Brush.verticalGradient(listOf(THREAD_RED.copy(alpha = 0.18f), WARM.copy(alpha = 0.10f), c.surfaceContainer)))
                    .padding(vertical = 20.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MinuCreature(pet.species, pet.mood, pet.stage, Modifier.size(150.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(pet.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Icon(
                        Icons.Default.Edit, "Cambiar nombre", tint = c.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp).size(20.dp).clickable { renaming = true },
                    )
                }
                Text(
                    "${pet.species.label} · ${pet.stage.label} · ${MinuVoice.moodLabel(pet.mood)}",
                    style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                SpeechBubble(MinuVoice.greeting(pet))
                Spacer(Modifier.height(14.dp))
                EnergyBar(pet.energy)
                Spacer(Modifier.height(6.dp))
                Text(MinuVoice.growth(pet), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
            }

            // Propuestas pendientes
            pet.nameProposal?.let { ProposalCard("nombre", "“${it.value}”", it, partnerName, "name", actions) }
            pet.speciesProposal?.let {
                ProposalCard("mascota", PetSpecies.from(it.value).label.lowercase(), it, partnerName, "species", actions)
            }

            if (renaming) {
                RenameCard(pet.name, onPropose = { actions.propose("name", it); renaming = false }, onCancel = { renaming = false })
            }

            // Cambiar de forma (se conserva todo lo que ha crecido)
            Text("¿Quién quieren que sea?", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PetSpecies.entries.forEach { species ->
                    val current = species == pet.species
                    val proposed = pet.speciesProposal?.value == species.id
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .border(
                                BorderStroke(if (current) 2.dp else 1.dp, if (current) THREAD_RED else c.outlineVariant),
                                RoundedCornerShape(16.dp),
                            )
                            .clickable(enabled = !current && !proposed) { actions.propose("species", species.id) }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        MinuCreature(species, if (current) pet.mood else PetMood.HAPPY, pet.stage, Modifier.size(56.dp))
                        Text(species.label, style = MaterialTheme.typography.labelMedium)
                        if (proposed) Text("propuesto", style = MaterialTheme.typography.labelSmall, color = THREAD_RED)
                    }
                }
            }
            Text(
                MinuVoice.speciesBlurb(pet.species) + " Si eligen otra forma, se la propongo a $partnerName y conservo todo lo que he crecido.",
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
            )

            // Lo que viene
            Text("Muy pronto en mi casita", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("💬\nPregunta\ndel día", "💌\nBuzón", "🗺️\nAventura", "🕊️\nTregua").forEach { label ->
                    Surface(color = c.surfaceContainerHigh, shape = RoundedCornerShape(16.dp), modifier = Modifier.weight(1f)) {
                        Text(
                            label, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium,
                            color = c.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        )
                    }
                }
            }

            // Privacidad
            TextButton(onClick = { confirmErase = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.DeleteOutline, null)
                Spacer(Modifier.size(8.dp))
                Text("Borrar lo que le conté a ${pet.name}")
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("¿Borrar lo que le contaste?") },
            text = {
                Text(
                    "${pet.name} olvidará lo que aportaste y se pausará hasta que vuelvas a aceptar sus términos. " +
                        "Su nombre y su forma son de los dos, así que se quedan.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmErase = false; actions.erase(); onDismiss() }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
internal fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp), shadowElevation = 3.dp, modifier = modifier) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
    }
}

/** La energía de los dos (sin números por persona). */
@Composable
private fun EnergyBar(energy: Int) {
    val c = MaterialTheme.colorScheme
    val fill by animateFloatAsState(energy / 100f, tween(900), label = "energía")
    Column(Modifier.fillMaxWidth()) {
        Text("Energía de los dos", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(c.surfaceContainerHighest)) {
            Box(
                Modifier.fillMaxWidth(fill.coerceIn(0.02f, 1f)).height(12.dp).clip(RoundedCornerShape(6.dp))
                    .background(Brush.horizontalGradient(listOf(THREAD_RED, WARM))),
            )
        }
    }
}

@Composable
private fun ProposalCard(what: String, value: String, proposal: PetProposal, partnerName: String, kind: String, actions: MinuHouseActions) {
    val c = MaterialTheme.colorScheme
    Surface(color = c.surfaceContainerHigh, shape = LocalAppStyle.current.cardShape, border = BorderStroke(1.dp, THREAD_RED.copy(alpha = 0.4f))) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                if (proposal.mine) "Le propusiste a $partnerName cambiar mi $what a $value."
                else "${proposal.byName} propone cambiar mi $what a $value. ¿Te gusta?",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(10.dp))
            if (proposal.mine) {
                TextButton(onClick = { actions.drop(kind) }) { Text("Retirar propuesta") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Aceptar", Icons.Default.Check, { actions.accept(kind) }, Modifier.weight(1f))
                    PillButton("No", Icons.Default.Close, { actions.drop(kind) }, Modifier.weight(1f), primary = false)
                }
            }
        }
    }
}

@Composable
private fun RenameCard(current: String, onPropose: (String) -> Unit, onCancel: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(current) }
    val valid = text.trim().isNotEmpty() && text.trim().codePointCount(0, text.trim().length) <= 20 && text.trim() != current
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = LocalAppStyle.current.cardShape) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("¿Cómo quieren llamarme?", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text, onValueChange = { if (it.length <= 24) text = it },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Tu pareja tiene que aceptarlo") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onCancel) { Text("Cancelar") }
                TextButton(onClick = { onPropose(text.trim()) }, enabled = valid) { Text("Proponer") }
            }
        }
    }
}
