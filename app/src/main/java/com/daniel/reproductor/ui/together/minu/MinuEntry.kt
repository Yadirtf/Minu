package com.daniel.reproductor.ui.together.minu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daniel.reproductor.cloud.MinuInfo
import com.daniel.reproductor.cloud.PetMood
import com.daniel.reproductor.cloud.PetSpecies
import com.daniel.reproductor.cloud.PetStage
import com.daniel.reproductor.ui.components.PillButton
import com.daniel.reproductor.ui.theme.LocalAppStyle
import com.daniel.reproductor.ui.together.THREAD_RED

/**
 * La tarjeta de Minu bajo el hilo: invita a conocerlo (términos), espera a la pareja, o ya despierto
 * muestra su frase del día y si hay una propuesta esperando tu respuesta. Tocarla abre la casita.
 */
@Composable
fun MinuEntry(minu: MinuInfo, partnerName: String, onOpenTerms: () -> Unit, onOpenHouse: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = LocalAppStyle.current.cardShape
    val pet = minu.pet
    when {
        !minu.terms.mine -> Surface(color = c.surfaceContainerHigh, shape = shape, border = BorderStroke(1.dp, THREAD_RED.copy(alpha = 0.35f))) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                MinuCreature(PetSpecies.YARN, PetMood.SLEEPY, PetStage.BABY, Modifier.size(72.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (minu.terms.update) "Minu tiene algo que contarles" else "Alguien está por despertar en su hilo",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (minu.terms.update) "Actualizamos cómo cuida sus datos. Léelo y acepta para seguir juntos."
                        else "Minu les ayuda a cuidar lo suyo cada día. Despierta cuando los dos lo acepten.",
                        style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    PillButton(if (minu.terms.update) "Ver qué cambió" else "Conocer a Minu", Icons.Default.Favorite, onOpenTerms)
                }
            }
        }
        !minu.terms.partner -> Surface(color = c.surfaceContainerHigh, shape = shape) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                MinuCreature(PetSpecies.YARN, PetMood.SLEEPY, PetStage.BABY, Modifier.size(64.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "Minu despierta cuando $partnerName también acepte. Ya casi 🧶",
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                )
            }
        }
        pet != null -> {
            val waiting = listOfNotNull(pet.nameProposal, pet.speciesProposal).any { !it.mine }
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpenHouse).padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${pet.name} · ${MinuVoice.moodLabel(pet.mood)}",
                        style = MaterialTheme.typography.labelLarge, color = THREAD_RED,
                    )
                    Text(
                        if (waiting) "$partnerName me propuso un cambio. ¿Lo vemos en mi casita?" else MinuVoice.greeting(pet),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
