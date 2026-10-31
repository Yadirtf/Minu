package com.daniel.reproductor.ui.together

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.reproductor.cloud.DuoDates
import com.daniel.reproductor.cloud.DuoMilestone
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.ui.components.PillButton
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

private val NIGHT = listOf(Color(0xFF0B0820), Color(0xFF1A0E2E), Color(0xFF2A0F2A))
private val GOLD = Color(0xFFFFC857)

/**
 * Lo que aparece al abrir Minu en una fecha especial: el hilo se dibuja de lado a lado, aparecen
 * sus nudos (uno por mes del año en curso; dorados en el aniversario) y luego el número.
 */
@Composable
fun MilestoneOverlay(
    milestone: DuoMilestone,
    me: String,
    partner: String,
    story: String,
    song: Song?,
    onPlay: (Song) -> Unit,
    onDismiss: () -> Unit,
    onOpenTape: (Int) -> Unit = {},
) {
    val haptics = LocalHapticFeedback.current
    val anniversary = milestone is DuoMilestone.Anniversary
    val thread = if (anniversary) GOLD else THREAD_RED
    // Nudos en el hilo: los meses del año que va (en el aniversario, los 12)
    val knots = when (milestone) {
        is DuoMilestone.Anniversary -> 12
        is DuoMilestone.Monthsary -> (milestone.months - 1) % 12 + 1
        is DuoMilestone.Days -> 0
    }
    var time by remember { mutableFloatStateOf(0f) }
    val reveal = remember { Animatable(0f) }
    val particles = remember { List(26) { Particle(Random.nextFloat(), Random.nextFloat() * 4f, 0.6f + Random.nextFloat() * 0.8f, Random.nextBoolean()) } }
    val stars = remember { List(60) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 6f) } }

    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { time = (it - start) / 1000f }
    }
    LaunchedEffect(Unit) {
        delay((DRAW_SECONDS * 1000).toLong() - 200)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (anniversary && song != null) onPlay(song) // el aniversario empieza con su canción
        reveal.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 120f))
    }
    BackHandler(onBack = onDismiss)

    val drawn = FastOutSlowInEasing.transform((time / DRAW_SECONDS).coerceIn(0f, 1f))
    val textAlpha = ((time - DRAW_SECONDS) / 0.8f).coerceIn(0f, 1f)

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(NIGHT))
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        Canvas(Modifier.fillMaxSize()) {
            stars.forEach { (x, y, o) ->
                val twinkle = (sin(time * 1.6f + o) + 1f) / 2f
                drawCircle(Color.White, radius = 1.dp.toPx() + twinkle * 1.2.dp.toPx(), center = Offset(size.width * x, size.height * y), alpha = 0.12f + 0.4f * twinkle)
            }
            // El hilo: se dibuja de izquierda a derecha y luego ondula como una cuerda
            val y0 = size.height * 0.24f
            val a = Offset(-20f, y0)
            val b = Offset(size.width + 20f, y0)
            val wave = 10.dp.toPx() * (0.4f + 0.6f * reveal.value.coerceIn(0f, 1f))
            fun at(t: Float) = threadPoint(a, b, t, 26.dp.toPx(), wave, time * 2.2f)
            val path = Path()
            val steps = (64 * drawn).toInt().coerceAtLeast(1)
            for (i in 0..steps) {
                val p = at(i / 64f)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, thread.copy(alpha = 0.28f), style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, thread, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            // Los nudos aparecen cuando el hilo pasa por ellos; el último (el de hoy) late
            for (k in 1..knots) {
                val t = 0.08f + 0.84f * (k - 1) / 11f
                if (drawn < t) continue
                val today = k == knots
                val pop = if (today) 1f + 0.5f * reveal.value * (1f + 0.15f * sin(time * 5f)) else 1f
                drawCircle(thread, radius = 4.dp.toPx() * pop, center = at(t))
                if (today) drawCircle(thread, radius = 11.dp.toPx() * pop, center = at(t), alpha = 0.25f)
            }
            // Días redondos: un corazón en el hilo por cada 100 días (100 = 1, 500 = 5, 1000 = 10)
            if (milestone is DuoMilestone.Days) {
                val hearts = (milestone.days / 100).toInt().coerceIn(1, 10)
                for (h in 0 until hearts) {
                    val t = if (hearts == 1) 0.5f else 0.1f + 0.8f * h / (hearts - 1)
                    if (drawn < t) continue
                    val beat = 1f + 0.12f * sin(time * 4f + h)
                    drawHeart(at(t), (if (hearts == 1) 30.dp else 16.dp).toPx() * (0.6f + 0.4f * reveal.value) * beat, if (milestone.days >= 1000) GOLD else thread)
                }
            }
            // Corazones y destellos que suben cuando aparece el número
            if (reveal.value > 0.05f) {
                val since = time - DRAW_SECONDS
                particles.forEach { p ->
                    val life = ((since - p.delay) * p.speed / 3.2f)
                    if (life !in 0f..1f) return@forEach
                    val pos = Offset(size.width * (p.x + 0.04f * sin(life * 9f + p.x * 6f)), size.height * (1.02f - life * 0.9f))
                    val alpha = (1f - life) * 0.9f
                    if (p.heart) drawHeart(pos, 14.dp.toPx(), if (anniversary) GOLD else THREAD_RED, alpha)
                    else drawCircle(Color.White, radius = 2.dp.toPx(), center = pos, alpha = alpha)
                }
            }
        }

        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp).padding(top = 200.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val (number, unit) = when (milestone) {
                is DuoMilestone.Anniversary -> "${milestone.years}" to if (milestone.years == 1) "año juntos" else "años juntos"
                is DuoMilestone.Monthsary -> "${milestone.months}" to if (milestone.months == 1) "mes juntos" else "meses juntos"
                is DuoMilestone.Days -> "${milestone.days}" to "días juntos"
            }
            Text(
                number, color = thread, fontSize = 104.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.graphicsLayer {
                    val s = reveal.value
                    scaleX = 0.3f + 0.7f * s
                    scaleY = 0.3f + 0.7f * s
                    alpha = s.coerceIn(0f, 1f)
                },
            )
            Text(unit, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.graphicsLayer { alpha = textAlpha })
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.graphicsLayer { alpha = textAlpha; translationY = (1f - textAlpha) * 40f },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    when (milestone) {
                        is DuoMilestone.Anniversary -> "$me y $partner, hoy es ${DuoDates.yearsPhrase(milestone.years)} juntos."
                        is DuoMilestone.Monthsary -> "$me y $partner, su hilo tiene un nudo nuevo."
                        is DuoMilestone.Days -> "$me y $partner, ${milestone.days} días desde el primer día."
                    },
                    color = Color.White.copy(alpha = 0.9f), fontSize = 17.sp, textAlign = TextAlign.Center,
                )
                if (anniversary && story.isNotBlank()) {
                    Text("“$story”", color = GOLD, fontSize = 17.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center)
                }
                Column(
                    Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp)).padding(16.dp),
                ) {
                    Text("Idea para hoy", color = thread, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(DuoDates.idea(milestone), color = Color.White, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            Column(
                Modifier.fillMaxWidth().graphicsLayer { alpha = textAlpha },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (milestone is DuoMilestone.Monthsary) {
                    PillButton("Escuchar la Cinta del mes ${milestone.months}", Icons.Default.PlayArrow, { onOpenTape(milestone.months) }, Modifier.fillMaxWidth())
                    if (song != null) TextButton(onClick = { onPlay(song) }) { Text("Escuchar nuestra canción", color = Color.White) }
                    TextButton(onClick = onDismiss) { Text("Seguir", color = Color.White) }
                } else if (song != null && !anniversary) {
                    PillButton("Escuchar nuestra canción", Icons.Default.PlayArrow, { onPlay(song) }, Modifier.fillMaxWidth())
                    TextButton(onClick = onDismiss) { Text("Seguir", color = Color.White) }
                } else {
                    PillButton(if (anniversary && song != null) "Seguir con nuestra canción" else "Seguir", Icons.Default.Favorite, onDismiss, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private const val DRAW_SECONDS = 1.8f

private class Particle(val x: Float, val delay: Float, val speed: Float, val heart: Boolean)
