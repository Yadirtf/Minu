package com.daniel.reproductor.ui.together.minu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daniel.reproductor.cloud.PetMood
import com.daniel.reproductor.cloud.PetSpecies
import com.daniel.reproductor.cloud.PetStage
import com.daniel.reproductor.ui.components.PillButton
import com.daniel.reproductor.ui.theme.LocalAppStyle
import com.daniel.reproductor.ui.together.THREAD_RED
import com.daniel.reproductor.ui.together.WARM
import kotlinx.coroutines.launch

private class TermsPage(val title: String, val body: List<String>, val species: PetSpecies, val mood: PetMood)

/**
 * Antes de que Minu despierte, cada uno lee en tres tarjetas para qué es, qué se guarda y sus derechos,
 * y acepta. Minu despierta cuando los dos aceptaron. Con [update] (ya había aceptado una versión
 * anterior) el título lo dice: lo que sigue es la versión vigente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinuTermsSheet(partnerName: String, update: Boolean, onAccept: () -> Unit, onDismiss: () -> Unit) {
    val pages = listOf(
        TermsPage(
            if (update) "Actualizamos cómo cuido de ustedes" else "Hola, soy Minu",
            listOf(
                "Vivo en su hilo rojo y mi trabajo es ayudarles a quererse bonito todos los días.",
                "Les recuerdo responderse, apreciarse y hacer cosas nuevas juntos para que la rutina no apague nada.",
                "No soy terapia ni un juez: nunca tomo partido ni llevo cuentas de quién hace más.",
            ),
            PetSpecies.YARN, PetMood.RADIANT,
        ),
        TermsPage(
            "Qué guardo y quién lo ve",
            listOf(
                "Sus dedicatorias, respuestas y recuerdos son de los dos y solo los ven ustedes dos.",
                "Lo que se sella solo se abre cuando ambos responden.",
                "Nadie ve su ubicación, sus horas de conexión ni si leyeron algo.",
                "Aprendo de lo que comparten para acompañarlos mejor. Para escribir mi mensaje del día envío un resumen corto (nunca el Buzón ni la Tregua) a un servicio de redacción externo, que no lo guarda ni lo usa para entrenar.",
            ),
            PetSpecies.CAT, PetMood.HAPPY,
        ),
        TermsPage(
            "Sus derechos",
            listOf(
                "Pueden desvincularse cuando quieran.",
                "Pueden borrar lo que me contaron desde mi casita, en cualquier momento.",
                "Pueden apagar mis mensajes personalizados en Privacidad.",
                "Despierto cuando los dos acepten. $partnerName también verá estas tarjetas.",
            ),
            PetSpecies.DOG, PetMood.HAPPY,
        ),
    )
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    val style = LocalAppStyle.current

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
            HorizontalPager(pager, Modifier.fillMaxWidth()) { index ->
                val page = pages[index]
                Column(
                    Modifier.fillMaxWidth()
                        .clip(style.cardShape)
                        .background(Brush.verticalGradient(listOf(THREAD_RED.copy(alpha = 0.16f), WARM.copy(alpha = 0.08f), c.surfaceContainer)))
                        .heightIn(min = 420.dp)
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    MinuCreature(page.species, page.mood, PetStage.YOUNG, Modifier.size(120.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(page.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    page.body.forEach { line ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Text("🧶  ", style = MaterialTheme.typography.bodyMedium)
                            Text(line, style = MaterialTheme.typography.bodyLarge, color = c.onSurface)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(pages.size) { i ->
                    Box(
                        Modifier.padding(4.dp).size(if (pager.currentPage == i) 10.dp else 7.dp).clip(CircleShape)
                            .background(if (pager.currentPage == i) THREAD_RED else c.outlineVariant),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            if (pager.currentPage < pages.lastIndex) {
                PillButton(
                    "Siguiente", Icons.AutoMirrored.Filled.ArrowForward,
                    { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    Modifier.fillMaxWidth(),
                )
            } else {
                PillButton("Acepto, despierta Minu", Icons.Default.Favorite, onAccept, Modifier.fillMaxWidth())
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Ahora no") }
            Spacer(Modifier.height(8.dp))
        }
    }
}
