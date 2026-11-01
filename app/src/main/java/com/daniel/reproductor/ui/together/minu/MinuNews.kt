package com.daniel.reproductor.ui.together.minu

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daniel.reproductor.cloud.GoodNews
import com.daniel.reproductor.cloud.MinuPet
import com.daniel.reproductor.ui.components.PillButton
import com.daniel.reproductor.ui.together.THREAD_RED
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val NEWS_MAX = 280
private const val CHEER_MAX = 200

/**
 * "Te cuento algo bueno": uno comparte una buena noticia y el otro la celebra. Minu sugiere respuestas
 * entusiastas que preguntan y celebran (respuesta activa y constructiva), en vez de un like.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinuNewsSheet(
    pet: MinuPet,
    news: List<GoodNews>,
    partnerName: String,
    cheers: List<String>,
    onShare: (String) -> Unit,
    onCheer: (id: String, text: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val waiting = news.firstOrNull { !it.byMe && it.reply == null }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Te cuento algo bueno", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                MinuCreature(pet.species, pet.mood, pet.stage, Modifier.size(76.dp), interactive = true)
                Spacer(Modifier.width(8.dp))
                SpeechBubble(waiting?.let { MinuVoice.newsCheerHint(partnerName) } ?: MinuVoice.NEWS_HINT, Modifier.weight(1f))
            }

            OutlinedTextField(
                value = draft, onValueChange = { if (it.length <= NEWS_MAX) draft = it },
                modifier = Modifier.fillMaxWidth(), minLines = 2,
                placeholder = { Text("Hoy pasó algo bueno…") },
                supportingText = { Text("${draft.length}/$NEWS_MAX") },
            )
            PillButton(
                "Contárselo a $partnerName", Icons.Default.AutoAwesome,
                { if (draft.isNotBlank()) { onShare(draft.trim()); draft = "" } }, Modifier.fillMaxWidth(),
            )

            if (news.isEmpty()) {
                Text(
                    "Aquí aparecen las buenas noticias de la semana, las tuyas y las de $partnerName.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            news.forEach { item -> NewsCard(item, partnerName, cheers, onCheer) }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NewsCard(item: GoodNews, partnerName: String, cheers: List<String>, onCheer: (String, String) -> Unit) {
    val c = MaterialTheme.colorScheme
    val needsCheer = !item.byMe && item.reply == null
    var custom by rememberSaveable(item.id) { mutableStateOf("") }
    Surface(
        color = c.surfaceContainerHigh, shape = RoundedCornerShape(20.dp),
        border = if (needsCheer) BorderStroke(1.5.dp, THREAD_RED.copy(alpha = 0.6f)) else null,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "${if (item.byMe) "Tú" else item.byName} · ${whenLabel(item.at)}",
                style = MaterialTheme.typography.labelMedium, color = if (item.byMe) c.onSurfaceVariant else THREAD_RED,
            )
            Spacer(Modifier.height(4.dp))
            Text("✨ ${item.text}", style = MaterialTheme.typography.bodyLarge)
            item.reply?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${if (item.byMe) partnerName else "Tú"}: ${it.text}",
                    style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = c.onSurfaceVariant,
                )
            }
            if (item.byMe && item.reply == null) {
                Text("Minu se lo llevó a $partnerName", style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
            if (needsCheer) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cheers.forEach { cheer -> AssistChip(onClick = { onCheer(item.id, cheer) }, label = { Text(cheer) }) }
                }
                OutlinedTextField(
                    value = custom, onValueChange = { if (it.length <= CHEER_MAX) custom = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("O dilo con tus palabras…") },
                    trailingIcon = {
                        IconButton(onClick = { if (custom.isNotBlank()) onCheer(item.id, custom.trim()) }) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Enviar")
                        }
                    },
                )
            }
        }
    }
}

private fun whenLabel(at: Long): String {
    val day = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    return when (day) {
        today -> "hoy"
        today.minusDays(1) -> "ayer"
        else -> "hace ${java.time.temporal.ChronoUnit.DAYS.between(day, today)} días"
    }
}
