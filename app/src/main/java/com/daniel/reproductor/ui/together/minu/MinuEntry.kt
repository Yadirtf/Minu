package com.daniel.reproductor.ui.together.minu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daniel.reproductor.cloud.MinuDaily
import com.daniel.reproductor.cloud.MinuInfo
import com.daniel.reproductor.cloud.TouchKind
import com.daniel.reproductor.cloud.PetMood
import com.daniel.reproductor.cloud.PetSpecies
import com.daniel.reproductor.cloud.PetStage
import com.daniel.reproductor.ui.components.PillButton
import com.daniel.reproductor.ui.theme.LocalAppStyle
import com.daniel.reproductor.ui.together.THREAD_RED

/** Las hojas de la conexión diaria que se abren desde la tarjeta de Minu. */
enum class MinuDailySheet { QUESTION, JAR, NEWS }

/**
 * La tarjeta de Minu bajo el hilo: invita a conocerlo (términos), espera a la pareja, o ya despierto
 * muestra su frase del día, el Toque y lo de hoy (pregunta, frasco de gracias y buenas noticias).
 * Tocar su frase abre la casita.
 */
@Composable
fun MinuEntry(
    minu: MinuInfo,
    partnerName: String,
    onOpenTerms: () -> Unit,
    onOpenHouse: () -> Unit,
    onTouch: (TouchKind) -> Unit = {},
    onOpenDaily: (MinuDailySheet) -> Unit = {},
) {
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
        pet != null -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Casita de ${pet.name}", tint = c.onSurfaceVariant)
            }
            minu.daily?.let { daily ->
                MinuTouchBar(daily.touch.left, daily.touch.last, partnerName, onTouch)
                DailyTiles(daily, partnerName, onOpenDaily)
            }
        }
    }
}

/** Tres mosaicos de lo de hoy; un puntito rojo marca el que espera algo de ti. */
@Composable
private fun DailyTiles(daily: MinuDaily, partnerName: String, onOpen: (MinuDailySheet) -> Unit) {
    val q = daily.question
    val questionStatus = when {
        q.opened && q.myReaction == null -> "¡Se abrió!" to true
        q.opened -> "Abierta" to false
        q.mine != null -> "Sellada" to false
        q.partnerDone -> "Te espera" to true
        else -> "Responder" to false
    }
    val jarStatus = if (daily.jar.mine == null) "Hoy: dar gracias" else "${daily.jar.items.size} en el frasco"
    val newsWaiting = daily.news.any { !it.byMe && it.reply == null }
    val newsStatus = if (newsWaiting) "$partnerName te contó algo" else "Cuéntale"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DailyTile("💌", "Pregunta del día", questionStatus.first, questionStatus.second, Modifier.weight(1f)) { onOpen(MinuDailySheet.QUESTION) }
        DailyTile("🫙", "Gracias por…", jarStatus, false, Modifier.weight(1f)) { onOpen(MinuDailySheet.JAR) }
        DailyTile("✨", "Algo bueno", newsStatus, newsWaiting, Modifier.weight(1f)) { onOpen(MinuDailySheet.NEWS) }
    }
}

@Composable
private fun DailyTile(emoji: String, title: String, status: String, attention: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Surface(
        color = c.surfaceContainerHigh, shape = RoundedCornerShape(20.dp),
        border = if (attention) BorderStroke(1.5.dp, THREAD_RED.copy(alpha = 0.7f)) else null,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box {
            Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp)) {
                Text(emoji, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(6.dp))
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(status, style = MaterialTheme.typography.labelSmall, color = if (attention) THREAD_RED else c.onSurfaceVariant, maxLines = 2)
            }
            if (attention) Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(9.dp).background(THREAD_RED, CircleShape))
        }
    }
}
