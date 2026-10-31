package com.daniel.reproductor.ui.together

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.reproductor.cloud.DuoStreak
import com.daniel.reproductor.cloud.StreakBroken
import com.daniel.reproductor.ui.components.PillButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

private val NIGHT = listOf(Color(0xFF0B0820), Color(0xFF1A0E2E), Color(0xFF240C22))
internal val AMBER = Color(0xFFFFB347)
private val GOLD = Color(0xFFFFD36E)
private val AIRMAIL_RED = Color(0xFFE5484D)
private val AIRMAIL_BLUE = Color(0xFF3D6BFF)

/** Qué momento de la racha se muestra a pantalla completa. */
sealed interface StreakMoment {
    /** Ayer el hilo no sonó: hay que elegir (o ver qué eligió tu pareja). */
    data class AtRisk(val streak: DuoStreak) : StreakMoment

    /** El hilo se rompió. */
    data class Broken(val broken: StreakBroken, val best: Int) : StreakMoment

    /** Cumplieron el reto. */
    data class Saved(val count: Int) : StreakMoment
}

/**
 * La racha a pantalla completa, con la metáfora del hilo rojo:
 * - en peligro: el hilo se tensa, las hebras se sueltan y suben dos cartas con los retos;
 * - roto: el hilo se corta despacio y el número cae a una piedrita con el récord;
 * - salvado: las hebras se vuelven a trenzar con chispas doradas y el número sube.
 */
@Composable
fun StreakOverlay(
    moment: StreakMoment,
    partner: String,
    onChoose: (String) -> Unit,
    onGiveUp: () -> Unit,
    onRetie: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { time = (it - start) / 1000f }
    }
    // Una vibración suave por cada hebra que se suelta (o se vuelve a trenzar)
    LaunchedEffect(moment) {
        repeat(5) {
            delay(if (moment is StreakMoment.Saved) 260 else 420)
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    BackHandler(onBack = onDismiss)

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(NIGHT))
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        val stars = remember { List(50) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 6f) } }
        Canvas(Modifier.fillMaxSize()) {
            stars.forEach { (x, y, o) ->
                val tw = (sin(time * 1.4f + o) + 1f) / 2f
                drawCircle(Color.White, radius = 1.dp.toPx() + tw * 1.1.dp.toPx(), center = Offset(size.width * x, size.height * y), alpha = 0.1f + 0.35f * tw)
            }
            when (moment) {
                is StreakMoment.AtRisk -> drawFraying(time, if (moment.streak.rescue?.type != null) AMBER else THREAD_RED)
                is StreakMoment.Broken -> drawSnapped(time)
                is StreakMoment.Saved -> drawBraiding(time)
            }
        }
        when (moment) {
            is StreakMoment.AtRisk -> AtRiskContent(moment.streak, partner, time, onChoose, onGiveUp, onDismiss)
            is StreakMoment.Broken -> BrokenContent(moment, time, onRetie, onDismiss)
            is StreakMoment.Saved -> SavedContent(moment.count, time, onDismiss)
        }
    }
}

private const val THREAD_Y = 0.26f

/** El hilo de lado a lado; [gap] separa las hebras en el centro (0 = trenzado, 1 = suelto). */
private fun DrawScope.thread(time: Float, color: Color, gap: Float, shake: Float, cut: Float = 0f) {
    val y0 = size.height * THREAD_Y
    val w = size.width
    val strands = 5
    for (k in 0 until strands) {
        val spread = (k - (strands - 1) / 2f) * 7.dp.toPx() * gap
        val path = Path()
        var started = false
        for (i in 0..80) {
            val t = i / 80f
            // El corte: cada mitad se aleja hacia su lado y cae un poco
            if (cut > 0f && t in 0.5f - 0.02f..0.5f + 0.02f) { started = false; continue }
            val side = if (t < 0.5f) -1f else 1f
            val bell = sin(Math.PI.toFloat() * t)
            val drift = if (cut > 0f) side * cut * 60.dp.toPx() * bell else 0f
            val drop = if (cut > 0f) cut * 70.dp.toPx() * bell * bell else 0f
            val x = -20f + (w + 40f) * t + drift
            val wobble = shake * 6.dp.toPx() * bell * sin(time * 24f + t * 9f)
            val y = y0 + 22.dp.toPx() * 4f * t * (1f - t) + spread * bell * bell + wobble + drop +
                4.dp.toPx() * bell * sin(time * 2.2f + t * 8f)
            if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
        }
        drawPath(path, color.copy(alpha = 0.18f), style = Stroke(8.dp.toPx(), cap = StrokeCap.Round))
        drawPath(path, color, style = Stroke((1.3f + (1f - gap) * 1.2f).dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawFraying(time: Float, color: Color) {
    // 0–1,2 s tenso y vibrando, luego las hebras se sueltan una a una
    val gap = FastOutSlowInEasing.transform(((time - 1.0f) / 2.2f).coerceIn(0f, 1f))
    val shake = if (time < 1.4f) 1f - time / 1.4f * 0.6f else 0.15f
    thread(time, lerp(THREAD_RED, color, (time / 4f).coerceIn(0f, 1f)), gap, shake)
}

private fun DrawScope.drawSnapped(time: Float) {
    val cut = FastOutSlowInEasing.transform(((time - 0.8f) / 2.4f).coerceIn(0f, 1f))
    thread(time, THREAD_RED.copy(alpha = 1f - 0.4f * cut), gap = 0.6f, shake = if (time < 0.8f) 1f else 0f, cut = cut)
}

private fun DrawScope.drawBraiding(time: Float) {
    val gap = 1f - FastOutSlowInEasing.transform((time / 1.8f).coerceIn(0f, 1f))
    thread(time, lerp(AMBER, GOLD, (time / 1.8f).coerceIn(0f, 1f)), gap, shake = 0f)
    // Chispas doradas que recorren el hilo
    if (time > 1.4f) {
        val y0 = size.height * THREAD_Y
        for (i in 0 until 9) {
            val t = ((time - 1.4f) * 0.45f + i / 9f) % 1f
            val x = size.width * t
            val y = y0 + 22.dp.toPx() * 4f * t * (1f - t)
            drawCircle(GOLD, radius = 3.dp.toPx(), center = Offset(x, y), alpha = 0.9f * sin(Math.PI.toFloat() * t))
            drawCircle(GOLD, radius = 9.dp.toPx(), center = Offset(x, y), alpha = 0.18f * sin(Math.PI.toFloat() * t))
        }
    }
}

private fun fade(time: Float, from: Float, length: Float = 0.6f) = ((time - from) / length).coerceIn(0f, 1f)

@Composable
private fun Content(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp).padding(top = 170.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun BigNumber(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(text, color = color, fontSize = 84.sp, fontWeight = FontWeight.Black, modifier = modifier)
}

@Composable
private fun AtRiskContent(
    streak: DuoStreak,
    partner: String,
    time: Float,
    onChoose: (String) -> Unit,
    onGiveUp: () -> Unit,
    onDismiss: () -> Unit,
) {
    val chosen = streak.rescue?.type
    Content {
        // El número tiembla y pierde color mientras el hilo se suelta
        val drain = fade(time, 1.2f, 2f)
        BigNumber(
            "${streak.count}", lerp(THREAD_RED, Color(0xFF8C7F99), drain * 0.7f).let { if (chosen != null) AMBER else it },
            Modifier.graphicsLayer { translationX = if (time in 1f..2.6f) sin(time * 40f) * 6f else 0f },
        )
        if (chosen != null) {
            Text("En rescate", color = AMBER, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Column(Modifier.graphicsLayer { alpha = fade(time, 2.0f) }, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Ayer el hilo no sonó.", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                if (chosen != null) "${streak.rescue?.chosenByName ?: partner} eligió salvarlo hoy con media hora en la misma canción."
                else "Hoy pueden volver a anudarlo con un reto. Mientras tanto, las dedicatorias esperan a mañana.",
                color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            RescueKnots(left = streak.rescuesLeft, spent = chosen != null)
        }
        Spacer(Modifier.weight(1f))
        // Las dos cartas suben desde abajo
        val rise = FastOutSlowInEasing.transform(fade(time, 2.8f, 0.8f))
        Column(
            Modifier.fillMaxWidth().graphicsLayer { translationY = (1f - rise) * 500f; alpha = rise },
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (chosen != null) {
                PillButton("Vamos a escuchar juntos", Icons.Default.Headphones, onDismiss, Modifier.fillMaxWidth())
            } else {
                ChallengeCard(
                    "Media hora en la misma canción",
                    "Uno invita a escuchar juntos y suman 30 minutos con los dos conectados y la música sonando.",
                    tilt = -2f, enabled = true,
                ) { onChoose("listen") }
                ChallengeCard(
                    "Cápsula de tres canciones",
                    "Tres preguntas, tres canciones cada uno, selladas hasta que los dos terminen. Llega en la próxima versión.",
                    tilt = 1.5f, enabled = false,
                ) {}
                TextButton(onClick = onGiveUp) { Text("Dejar que se rompa", color = Color.White.copy(alpha = 0.6f)) }
            }
        }
    }
}

/** Los rescates del mes como tres nudos: encendidos los que quedan. */
@Composable
private fun RescueKnots(left: Int, spent: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val lit = i < left
            Box(
                Modifier.size(14.dp).clip(CircleShape)
                    .background(if (lit) THREAD_RED else Color.Transparent)
                    .border(1.5.dp, THREAD_RED.copy(alpha = if (lit) 1f else 0.4f), CircleShape),
            )
        }
        Text(
            if (spent) "  rescate usado · quedan ${left} este mes" else "  ${if (left == 1) "queda 1 rescate" else "quedan $left rescates"} este mes",
            color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp,
        )
    }
}

/** Una carta de correo aéreo con el reto. Al tocarla se dobla y se vuelve el nudo provisional. */
@Composable
private fun ChallengeCard(title: String, body: String, tilt: Float, enabled: Boolean, onPick: () -> Unit) {
    val fold = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxWidth()
            .graphicsLayer {
                rotationZ = tilt
                rotationX = fold.value * 80f
                scaleX = 1f - fold.value * 0.6f
                scaleY = 1f - fold.value * 0.6f
                alpha = (if (enabled) 1f else 0.55f) * (1f - fold.value)
            }
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFFFF8EC))
            .border(3.dp, Brush.linearGradient(listOf(AIRMAIL_RED, AIRMAIL_BLUE, AIRMAIL_RED, AIRMAIL_BLUE)), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) {
                scope.launch {
                    fold.animateTo(1f, tween(380, easing = LinearEasing))
                    onPick()
                }
            }
            .padding(18.dp),
    ) {
        Text(title, color = Color(0xFF2B1B2E), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(body, color = Color(0xFF5A4A5E), fontSize = 14.sp, fontStyle = FontStyle.Italic)
    }
}

@Composable
private fun BrokenContent(moment: StreakMoment.Broken, time: Float, onRetie: () -> Unit, onDismiss: () -> Unit) {
    val lost = moment.broken.lost
    Content {
        // El número cae despacio hasta quedar como récord
        val fall = FastOutSlowInEasing.transform(fade(time, 1.4f, 1.6f))
        BigNumber(
            "$lost", THREAD_RED.copy(alpha = 1f - 0.5f * fall),
            Modifier.graphicsLayer { translationY = fall * 120f; scaleX = 1f - 0.45f * fall; scaleY = 1f - 0.45f * fall; rotationZ = fall * -8f },
        )
        Box(
            Modifier.graphicsLayer { alpha = fade(time, 2.6f); translationY = 60f }
                .clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f)).padding(horizontal = 16.dp, vertical = 6.dp),
        ) {
            Text("Récord: ${maxOf(moment.best, lost)} días", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(70.dp))
        Column(Modifier.graphicsLayer { alpha = fade(time, 3.0f) }, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Lo que vivieron en ${daysText(lost)} no se borra.",
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (moment.broken.reason) {
                    "no_rescues" -> "Este mes ya usaron sus 3 rescates. El día 1 vuelven a tener 3."
                    "gave_up" -> "Dejaron que el hilo se rompiera. Hoy mismo pueden volver a atarlo."
                    "timeout" -> "El día del reto terminó sin completarlo."
                    else -> "Pasaron dos días sin dedicarse una canción."
                },
                color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth().graphicsLayer { alpha = fade(time, 3.4f) }, horizontalAlignment = Alignment.CenterHorizontally) {
            PillButton("Volver a atarlo", Icons.Default.Favorite, onRetie, Modifier.fillMaxWidth())
            TextButton(onClick = onDismiss) { Text("Cerrar", color = Color.White.copy(alpha = 0.7f)) }
        }
    }
}

private fun daysText(n: Int) = if (n == 1) "1 día" else "$n días"

@Composable
private fun SavedContent(count: Int, time: Float, onDismiss: () -> Unit) {
    var shown by remember { mutableIntStateOf((count - 2).coerceAtLeast(0)) }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(count) {
        delay(1600)
        while (shown < count) {
            shown += 1
            pop.snapTo(1.35f)
            pop.animateTo(1f, spring(dampingRatio = 0.4f))
            delay(250)
        }
    }
    Content {
        BigNumber("$shown", GOLD, Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value })
        Column(Modifier.graphicsLayer { alpha = fade(time, 2.2f) }, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Salvaron el hilo.", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Se salvaron el día olvidado y el de hoy. Mañana vuelven las dedicatorias.",
                color = Color.White.copy(alpha = 0.8f), fontSize = 15.sp, textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.weight(1f))
        PillButton("Seguir", Icons.Default.Favorite, onDismiss, Modifier.fillMaxWidth().graphicsLayer { alpha = fade(time, 2.6f) })
    }
}
