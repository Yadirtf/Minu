package com.daniel.reproductor.ui.together.minu

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daniel.reproductor.cloud.MindKind
import com.daniel.reproductor.cloud.MindLine
import com.daniel.reproductor.cloud.MinuMindInfo
import com.daniel.reproductor.cloud.MinuPet
import com.daniel.reproductor.cloud.PetMood
import com.daniel.reproductor.ui.components.PillButton
import com.daniel.reproductor.ui.theme.LocalAppStyle
import com.daniel.reproductor.ui.together.THREAD_RED
import com.daniel.reproductor.ui.together.WARM
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/*
 * La voz propia de Minu, vista desde la pareja. Mientras aprende de ustedes, cada día que pasa enciende
 * una estrella; al cumplir un mes, las estrellas se unen y dibujan un corazón: desde ahí, algunos de sus
 * mensajes suenan más a ustedes. En ningún lugar se habla de cómo funciona por dentro.
 */

private val NIGHT = Color(0xFF1B1530)
private val STAR = Color(0xFFFFE6A8)

/** Puntos de un corazón (curva clásica) normalizados a 0..1, empezando arriba al centro. */
private fun heartPoint(t: Float): Offset {
    val a = (t * 2 * PI).toFloat()
    val x = 16 * sin(a).pow(3)
    val y = 13 * cos(a) - 5 * cos(2 * a) - 2 * cos(3 * a) - cos(4 * a)
    return Offset(0.5f + x / 36f, 0.47f - y / 36f)
}

/**
 * La constelación de lo aprendido: [total] estrellas sobre un corazón; [learned] encendidas y titilando.
 * [formed] (0..1) traza el hilo que las une, como cuando Minu despierta.
 */
@Composable
fun LearningConstellation(learned: Int, total: Int, formed: Float, modifier: Modifier = Modifier, night: Boolean = true) {
    val inf = rememberInfiniteTransition(label = "estrellas")
    val twinkle by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "titilar")
    val count = total.coerceIn(1, 31)
    val points = remember(count) { List(count) { heartPoint(it / count.toFloat()) } }
    Canvas(modifier.aspectRatio(1.1f)) {
        val w = size.width
        val h = size.height
        if (night) {
            drawRoundRect(
                Brush.radialGradient(listOf(Color(0xFF2E2350), NIGHT), center = Offset(w / 2, h * 0.45f), radius = w * 0.75f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f),
            )
            // Polvo de estrellas de fondo (fijo, pseudoaleatorio)
            repeat(26) { i ->
                val x = ((i * 73 + 17) % 100) / 100f
                val y = ((i * 41 + 29) % 100) / 100f
                drawCircle(Color.White.copy(alpha = 0.10f + 0.12f * ((i % 3) / 2f)), radius = w * 0.004f, center = Offset(x * w, y * h))
            }
        }
        val at = points.map { Offset(it.x * w, it.y * h) }
        // El hilo rojo que une las estrellas
        if (formed > 0f) {
            val path = Path().apply {
                moveTo(at[0].x, at[0].y)
                at.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val partial = Path()
            measure.getSegment(0f, measure.length * formed.coerceIn(0f, 1f), partial, true)
            drawPath(partial, THREAD_RED.copy(alpha = 0.35f), style = Stroke(w * 0.035f, cap = StrokeCap.Round))
            drawPath(partial, Brush.linearGradient(listOf(THREAD_RED, WARM)), style = Stroke(w * 0.012f, cap = StrokeCap.Round))
            if (formed >= 1f) drawPath(path, Brush.radialGradient(listOf(THREAD_RED.copy(alpha = 0.22f), Color.Transparent), center = Offset(w / 2, h * 0.5f), radius = w * 0.4f))
        }
        at.forEachIndexed { i, p ->
            val lit = i < learned
            if (lit) {
                val phase = ((twinkle + i * 0.137f) % 1f)
                val glow = 0.55f + 0.45f * sin(phase * 2 * PI).toFloat()
                drawCircle(STAR.copy(alpha = 0.18f * glow), radius = w * 0.045f, center = p)
                drawCircle(STAR.copy(alpha = 0.6f + 0.4f * glow), radius = w * 0.016f, center = p)
                // Destello en cruz
                val r = w * 0.03f * glow
                drawLine(Color.White.copy(alpha = 0.7f * glow), Offset(p.x - r, p.y), Offset(p.x + r, p.y), strokeWidth = w * 0.004f)
                drawLine(Color.White.copy(alpha = 0.7f * glow), Offset(p.x, p.y - r), Offset(p.x, p.y + r), strokeWidth = w * 0.004f)
            } else {
                drawCircle(Color.White.copy(alpha = 0.18f), radius = w * 0.009f, center = p)
            }
        }
    }
}

/** Cómo presenta Minu cada tipo de mensaje. */
private fun MindKind.label() = when (this) {
    MindKind.PLAN -> "Una idea para hoy"
    MindKind.QUESTION -> "Para conversar"
    MindKind.CHEER -> "Me acordé de ustedes"
    MindKind.DEDICATION -> "Idea para dedicar"
}

private fun MindKind.emoji() = when (this) {
    MindKind.PLAN -> "🌱"
    MindKind.QUESTION -> "💭"
    MindKind.CHEER -> "✨"
    MindKind.DEDICATION -> "🎵"
}

/** Escribe el texto letra por letra, como si Minu lo estuviera pensando. Solo la primera vez que aparece. */
@Composable
fun rememberTypewriter(text: String, charMs: Long = 22L): String {
    var shown by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        delay(350)
        while (shown < text.length) {
            delay(charMs)
            shown = (shown + 1).coerceAtMost(text.length)
        }
    }
    return text.take(shown)
}

/**
 * El mensaje del día con la voz de Minu, en la tarjeta de Dúo. Cada tipo tiene su etiqueta y, la idea de
 * dedicar, un botón directo.
 */
@Composable
fun MindLineCard(line: MindLine, onDedicate: () -> Unit, modifier: Modifier = Modifier, animate: Boolean = true) {
    val c = MaterialTheme.colorScheme
    val text = if (animate) rememberTypewriter(line.text) else line.text
    Surface(
        color = c.surfaceContainerHigh, shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Brush.linearGradient(listOf(THREAD_RED.copy(alpha = 0.6f), WARM.copy(alpha = 0.5f)))),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(line.kind.emoji(), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(6.dp))
                Text(line.kind.label(), style = MaterialTheme.typography.labelLarge, color = THREAD_RED, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            // El espacio final se reserva desde el inicio para que la tarjeta no salte mientras escribe
            Box {
                Text(line.text, style = MaterialTheme.typography.bodyLarge, color = Color.Transparent)
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
            if (line.kind == MindKind.DEDICATION) {
                Spacer(Modifier.height(8.dp))
                PillButton("Dedicar una canción", Icons.Default.MusicNote, onDedicate, primary = false)
            }
        }
    }
}

/** Mientras aprende: una mini constelación y la cuenta regresiva en su voz. */
@Composable
fun LearningRow(mind: MinuMindInfo, petName: String, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LearningConstellation(mind.learned, mind.learnTotal, 0f, Modifier.width(64.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("$petName está aprendiendo de ustedes", style = MaterialTheme.typography.labelLarge, color = THREAD_RED)
            Text(MinuVoice.learning(mind.daysLeft), style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        }
    }
}

/**
 * En la casita: lo que Minu va aprendiendo (o que ya los conoce) y el interruptor de sus mensajes
 * personalizados. Siete toques a la constelación abren el ensayo (para revisar en un celular compatible).
 */
@Composable
fun MindHouseCard(mind: MinuMindInfo, pet: MinuPet, partnerName: String, onToggle: (Boolean) -> Unit, onSecret: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var taps by remember { mutableIntStateOf(0) }
    Surface(color = c.surfaceContainerHigh, shape = LocalAppStyle.current.cardShape) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (mind.awake) "Ya los conozco un poquito" else "Lo que voy aprendiendo", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                LearningConstellation(
                    if (mind.awake) mind.learnTotal else mind.learned, mind.learnTotal, if (mind.awake) 1f else 0f,
                    Modifier.width(118.dp).clickable { taps++; if (taps >= 7) { taps = 0; onSecret() } },
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    when {
                        !mind.awake -> MinuVoice.learning(mind.daysLeft) + " Cada estrella es un día con ustedes."
                        mind.enabled -> "Cada día les escribo algo pensado en lo que comparten: lo que agradecen, sus aventuras, sus canciones."
                        else -> "Mis mensajes personalizados están en pausa. Mientras tanto les hablo con mis frases de siempre."
                    },
                    style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Mensajes personalizados de ${pet.name}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        when {
                            !mind.mineOn -> "Apagados. Si los enciendes y $partnerName también, vuelvo a escribirles."
                            !mind.partnerOn -> "Están en pausa porque $partnerName los apagó. Tú los tienes encendidos."
                            else -> "Se redactan dentro de su celular con lo que comparten aquí. Nunca leo el Buzón."
                        },
                        style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Switch(
                    checked = mind.mineOn, onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedTrackColor = THREAD_RED),
                )
            }
        }
    }
}

/**
 * El contenido de la celebración del despertar: las estrellas se unen en un corazón, Minu salta y les
 * cuenta que ya los conoce un poquito. Aparte del diálogo para poder verlo en capturas.
 */
@Composable
fun MinuWakeContent(pet: MinuPet, mind: MinuMindInfo, partnerName: String, onDone: () -> Unit) {
    val formed = remember { Animatable(0f) }
    var hop by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        delay(500)
        formed.animateTo(1f, tween(2400, easing = FastOutSlowInEasing))
        hop++
    }
    Column(
        Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(NIGHT, Color(0xFF2B1B3D))), RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            LearningConstellation(mind.learnTotal, mind.learnTotal, formed.value, Modifier.fillMaxWidth(), night = false)
            MinuCreature(pet.species, if (formed.value >= 1f) PetMood.RADIANT else PetMood.HAPPY, pet.stage, Modifier.size(110.dp), celebrate = hop)
        }
        Text("¡Ya los conozco un poquito!", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
        Text(
            "Un mes aprendiendo de ti y de $partnerName. Desde hoy, algunos de mis mensajes van a sonar más a ustedes.",
            style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center,
        )
        mind.line?.let { line ->
            Text("“${line.text}”", style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = STAR, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(4.dp))
        PillButton("¡Hola, ${pet.name}!", Icons.Default.Favorite, onDone, Modifier.fillMaxWidth())
        TextButton(onClick = onDone) { Text("Puedes pausarlos cuando quieras en mi casita", color = Color.White.copy(alpha = 0.6f)) }
    }
}

@Composable
fun MinuWakeCelebration(pet: MinuPet, mind: MinuMindInfo, partnerName: String, onDone: () -> Unit) {
    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.padding(20.dp)) { MinuWakeContent(pet, mind, partnerName, onDone) }
    }
}
