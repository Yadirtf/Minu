package com.daniel.reproductor.ui.together.minu

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daniel.reproductor.cloud.GratitudeJar
import com.daniel.reproductor.cloud.JarNote
import com.daniel.reproductor.cloud.MinuPet
import com.daniel.reproductor.ui.components.PillButton
import com.daniel.reproductor.ui.together.THREAD_RED
import kotlin.random.Random

private const val THANKS_MAX = 140
private val NOTE_COLORS = listOf(Color(0xFFFFE3EA), Color(0xFFFFF1C9), Color(0xFFE3F1FF), Color(0xFFE8FBE6), Color(0xFFF3E5FF))

/**
 * "Gracias por...": una apreciación al día por persona. Cada una es un papelito de color que cae en el
 * frasco del mes; solo hay espacio para gestos positivos. Al cerrar el mes, el frasco pasa a Recuerdos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MinuJarSheet(pet: MinuPet, jar: GratitudeJar, partnerName: String, onGive: (String) -> Unit, onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var draft by rememberSaveable(jar.month) { mutableStateOf("") }
    var celebrate by remember { mutableIntStateOf(0) }
    // Cuando cae un papelito nuevo, Minu salta
    LaunchedEffect(jar.items.size) { if (jar.mine != null) celebrate++ }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Gracias por…", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                MinuCreature(pet.species, pet.mood, pet.stage, Modifier.size(84.dp), interactive = true, celebrate = celebrate)
                Spacer(Modifier.width(4.dp))
                GratitudeJarArt(jar.items.size, Modifier.size(150.dp, 180.dp))
            }
            SpeechBubble(MinuVoice.jarLine(jar.items.size))

            if (jar.mine == null) {
                OutlinedTextField(
                    value = draft, onValueChange = { if (it.length <= THANKS_MAX) draft = it },
                    modifier = Modifier.fillMaxWidth(), minLines = 2,
                    prefix = { Text("Gracias por ") },
                    placeholder = { Text("cómo me cuidaste hoy") },
                    supportingText = { Text("Una al día · ${draft.length}/$THANKS_MAX") },
                )
                PillButton("Echar al frasco", Icons.Default.Favorite, { if (draft.isNotBlank()) onGive(draft.trim()) }, Modifier.fillMaxWidth())
            } else {
                Text(
                    "Tu gracias de hoy ya está en el frasco. Mañana puedes echar otro papelito.",
                    style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }

            jar.items.forEach { note -> JarNoteCard(note, partnerName) }
            Text(
                "Al cerrar el mes, el frasco se guarda en Recuerdos.",
                style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun JarNoteCard(note: JarNote, partnerName: String) {
    val color = NOTE_COLORS[(note.text.hashCode() and 0x7fffffff) % NOTE_COLORS.size]
    Surface(color = color, shape = RoundedCornerShape(6.dp, 18.dp, 6.dp, 18.dp), shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text("Gracias por ${note.text}", style = MaterialTheme.typography.bodyLarge, color = Color(0xFF2B1B2E))
            Text(
                "${if (note.byMe) "Tú" else partnerName} · ${note.day.takeLast(2).trimStart('0')}",
                style = MaterialTheme.typography.labelSmall, color = Color(0xFF2B1B2E).copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * El frasco de vidrio con tapa y moño rojo. Los papelitos se apilan desde el fondo (cada uno doblado y
 * girado a su manera); el último cae desde arriba con un rebote cuando cambia [count].
 */
@Composable
fun GratitudeJarArt(count: Int, modifier: Modifier = Modifier) {
    val drop = remember { Animatable(1f) }
    var shown by remember { mutableIntStateOf(count) }
    LaunchedEffect(count) {
        if (count > shown) {
            drop.snapTo(0f)
            shown = count
            drop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
        } else shown = count
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val left = w * 0.12f
        val right = w * 0.88f
        val top = h * 0.2f
        val bottom = h * 0.96f
        val glass = Path().apply {
            moveTo(left + w * 0.06f, top)
            lineTo(left, top + h * 0.08f)
            lineTo(left, bottom - h * 0.06f)
            quadraticTo(left, bottom, left + w * 0.08f, bottom)
            lineTo(right - w * 0.08f, bottom)
            quadraticTo(right, bottom, right, bottom - h * 0.06f)
            lineTo(right, top + h * 0.08f)
            lineTo(right - w * 0.06f, top)
            close()
        }
        drawPath(glass, Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.06f), Color.White.copy(alpha = 0.14f))))

        // Papelitos: filas de 4 desde el fondo
        val noteW = (right - left) / 4.6f
        val noteH = noteW * 0.62f
        val visible = count.coerceAtMost(28)
        for (i in 0 until visible) {
            val rnd = Random(i * 7919)
            val row = i / 4
            val col = i % 4
            val restY = bottom - h * 0.05f - noteH * (row + 1) * 0.92f
            val x = left + noteW * 0.35f + col * noteW * 1.02f + rnd.nextFloat() * 6f
            val isLast = i == visible - 1
            val y = if (isLast) top - noteH + (restY - top + noteH) * drop.value else restY
            rotate(rnd.nextFloat() * 40f - 20f, Offset(x + noteW / 2, y + noteH / 2)) {
                drawRoundRect(NOTE_COLORS[i % NOTE_COLORS.size], topLeft = Offset(x, y), size = Size(noteW, noteH), cornerRadius = CornerRadius(4f))
                drawLine(Color.Black.copy(alpha = 0.08f), Offset(x + noteW / 2, y), Offset(x + noteW / 2, y + noteH), strokeWidth = 2f)
            }
        }
        // Brillo del vidrio y contorno
        drawRoundRect(Color.White.copy(alpha = 0.35f), topLeft = Offset(left + w * 0.05f, top + h * 0.12f), size = Size(w * 0.05f, h * 0.45f), cornerRadius = CornerRadius(20f))
        drawPath(glass, Color.White.copy(alpha = 0.55f), style = Stroke(3f))
        // Tapa con moño de hilo rojo
        drawRoundRect(Color(0xFFC98B5B), topLeft = Offset(left + w * 0.02f, top - h * 0.07f), size = Size(right - left - w * 0.04f, h * 0.08f), cornerRadius = CornerRadius(8f))
        drawLine(THREAD_RED, Offset(left, top + h * 0.02f), Offset(right, top + h * 0.02f), strokeWidth = 6f)
        val bow = Offset(right - w * 0.1f, top + h * 0.02f)
        drawCircle(THREAD_RED, 9f, bow + Offset(-10f, -6f), style = Stroke(5f))
        drawCircle(THREAD_RED, 9f, bow + Offset(10f, -6f), style = Stroke(5f))
    }
}
