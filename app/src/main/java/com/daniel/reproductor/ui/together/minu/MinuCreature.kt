package com.daniel.reproductor.ui.together.minu

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.reproductor.cloud.MinuPet
import com.daniel.reproductor.cloud.PetMood
import com.daniel.reproductor.cloud.PetSpecies
import com.daniel.reproductor.cloud.PetStage
import com.daniel.reproductor.ui.together.THREAD_RED
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val GOLD = Color(0xFFFFC857)
private val INK = Color(0xFF2B1B2E)

/** Color de cada mascota (el hilo rojo lo llevan todas). */
internal fun PetSpecies.fur(): Color = when (this) {
    PetSpecies.YARN -> THREAD_RED
    PetSpecies.CAT -> Color(0xFFFFB067)
    PetSpecies.BUNNY -> Color(0xFFF4ECF7)
    PetSpecies.DOG -> Color(0xFFC98B5B)
}

/**
 * Minu dibujado con volumen (luz arriba a la izquierda, sombra en el piso) y animado según su ánimo:
 * radiante salta y brilla, contento flota y parpadea, somnoliento respira lento con "z", y cuando
 * extraña mira hacia abajo abrazando un casete de sus recuerdos. Cada etapa lo hace un poco más grande
 * y le da un accesorio (corazón, moño, lentes). [hop] 0..1 lo hace saltar desde fuera (al entregar algo).
 */
@Composable
fun MinuCreature(
    species: PetSpecies,
    mood: PetMood,
    stage: PetStage,
    modifier: Modifier = Modifier.size(84.dp),
    hop: Float = 0f,
    carrying: Boolean = false,
) {
    val inf = rememberInfiniteTransition(label = "minu")
    val speed = when (mood) {
        PetMood.RADIANT -> 900
        PetMood.HAPPY -> 1800
        PetMood.SLEEPY -> 3600
        PetMood.MISSING -> 3000
    }
    val t by inf.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(speed, easing = LinearEasing)), label = "t")
    val slow by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "slow")
    // Parpadeo a ratos (no cuando duerme)
    var blink by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(mood) {
        while (mood != PetMood.SLEEPY) {
            delay(1800L + Random.nextLong(2600))
            blink = 1f; delay(120); blink = 0f
        }
    }

    Canvas(modifier) {
        val s = size.minDimension
        val scale = when (stage) {
            PetStage.BABY -> 0.78f
            PetStage.YOUNG -> 0.88f
            PetStage.ADULT, PetStage.WISE -> 1f
        }
        val r = s * 0.27f * scale
        val ground = size.height * 0.94f
        val bounce = when (mood) {
            PetMood.RADIANT -> abs(sin(t)) * s * 0.10f
            PetMood.HAPPY -> (sin(t) + 1f) * s * 0.02f
            PetMood.SLEEPY -> 0f
            PetMood.MISSING -> 0f
        } + sin(hop * PI.toFloat()) * s * 0.22f
        val breathe = if (mood == PetMood.SLEEPY) 1f + 0.04f * sin(t) else 1f
        val squash = if (mood == PetMood.RADIANT) 1f - 0.06f * (1f - abs(sin(t))) else 1f
        val body = Offset(size.width / 2f, ground - r * squash - bounce)

        // Sombra en el piso: se achica cuando salta
        val shadowW = r * (1.3f - 0.5f * (bounce / (s * 0.3f)).coerceIn(0f, 1f))
        drawOval(
            Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent), center = Offset(body.x, ground), radius = shadowW),
            topLeft = Offset(body.x - shadowW, ground - shadowW * 0.22f), size = Size(shadowW * 2f, shadowW * 0.44f),
        )
        if (mood == PetMood.RADIANT) {
            drawCircle(
                Brush.radialGradient(listOf(GOLD.copy(alpha = 0.35f), Color.Transparent), center = body, radius = r * 2.1f),
                radius = r * 2.1f, center = body,
            )
        }

        withTransform({ scale(breathe, squash * breathe, pivot = Offset(body.x, ground)) }) {
            when (species) {
                PetSpecies.YARN -> drawYarn(body, r, t)
                PetSpecies.CAT -> drawCat(body, r, t, mood)
                PetSpecies.BUNNY -> drawBunny(body, r, t, mood)
                PetSpecies.DOG -> drawDog(body, r, t, mood)
            }
            val face = when (species) {
                PetSpecies.YARN -> body + Offset(0f, -r * 0.05f)
                else -> body + Offset(0f, -r * 0.55f)
            }
            val faceR = if (species == PetSpecies.YARN) r else r * 0.82f
            drawFace(face, faceR, mood, blink, species)
            drawAccessory(stage, species, body, face, r, faceR)
            if (mood == PetMood.MISSING || carrying) drawHeld(body, r, carrying)
        }

        // Efectos alrededor
        when (mood) {
            PetMood.RADIANT -> for (i in 0 until 5) {
                val a = t + i * (2 * PI / 5).toFloat()
                val at = body + Offset(cos(a), sin(a) * 0.6f) * (r * 1.7f)
                drawSparkle(at, r * 0.13f * (0.6f + 0.4f * sin(t * 2 + i)), GOLD)
            }
            PetMood.SLEEPY -> for (i in 0 until 3) {
                val p = (slow + i / 3f) % 1f
                val at = body + Offset(r * (0.8f + 0.5f * p), -r * (1.1f + 1.2f * p))
                drawZ(at, r * (0.16f + 0.12f * p), Color.White.copy(alpha = (1f - p) * 0.85f))
            }
            else -> Unit
        }
    }
}

/** Minu con un globo de diálogo al lado: para las pantallas de racha, mesiversario, aniversario… */
@Composable
fun MinuCompanion(pet: MinuPet, line: String, modifier: Modifier = Modifier, mood: PetMood = pet.mood) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.widthIn(max = 220.dp)
                .shadow(6.dp, RoundedCornerShape(18.dp))
                .background(Color.White, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(line, color = INK, fontSize = 14.sp, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
        }
        MinuCreature(pet.species, mood, pet.stage, Modifier.size(78.dp))
    }
}

// ---------- Dibujo ----------

/** Esfera con luz arriba a la izquierda y un brillo: da el volumen "3D". */
private fun DrawScope.ball(center: Offset, radius: Float, base: Color, rx: Float = 1f, ry: Float = 1f) {
    val light = lerp(base, Color.White, 0.45f)
    val dark = lerp(base, Color.Black, 0.35f)
    val brush = Brush.radialGradient(
        0f to light, 0.45f to base, 1f to dark,
        center = center + Offset(-radius * 0.35f, -radius * 0.45f), radius = radius * 1.5f,
    )
    drawOval(brush, topLeft = center - Offset(radius * rx, radius * ry), size = Size(radius * 2 * rx, radius * 2 * ry))
    drawOval(
        Color.White.copy(alpha = 0.35f),
        topLeft = center + Offset(-radius * 0.55f * rx, -radius * 0.7f * ry),
        size = Size(radius * 0.5f * rx, radius * 0.28f * ry),
    )
}

private fun DrawScope.drawYarn(c: Offset, r: Float, t: Float) {
    ball(c, r, THREAD_RED)
    val strand = lerp(THREAD_RED, Color.White, 0.25f)
    // Vueltas de hilo sobre la esfera
    for (i in 0 until 5) {
        rotate(-35f + i * 18f, c) {
            drawArc(
                strand.copy(alpha = 0.55f), startAngle = 200f, sweepAngle = 140f, useCenter = false,
                topLeft = c - Offset(r * 0.95f, r * (0.35f + i * 0.12f)), size = Size(r * 1.9f, r * (0.7f + i * 0.24f)),
                style = Stroke(r * 0.07f, cap = StrokeCap.Round),
            )
        }
    }
    // La punta suelta del hilo, que se mece
    val path = Path().apply {
        moveTo(c.x + r * 0.85f, c.y + r * 0.4f)
        quadraticTo(c.x + r * 1.5f, c.y + r * (0.3f + 0.15f * sin(t)), c.x + r * 1.35f, c.y + r * 0.95f)
    }
    drawPath(path, THREAD_RED, style = Stroke(r * 0.09f, cap = StrokeCap.Round))
}

private fun DrawScope.drawCat(c: Offset, r: Float, t: Float, mood: PetMood) {
    val fur = PetSpecies.CAT.fur()
    // Cola que se mueve
    val tail = Path().apply {
        moveTo(c.x + r * 0.7f, c.y + r * 0.5f)
        cubicTo(c.x + r * 1.6f, c.y + r * 0.4f, c.x + r * 1.2f, c.y - r * (0.6f + 0.2f * sin(t)), c.x + r * (1.5f + 0.15f * sin(t)), c.y - r * 0.9f)
    }
    drawPath(tail, lerp(fur, Color.Black, 0.15f), style = Stroke(r * 0.22f, cap = StrokeCap.Round))
    ball(c + Offset(0f, r * 0.25f), r * 0.85f, fur, rx = 1.05f, ry = 0.9f)
    val head = c + Offset(0f, -r * 0.55f)
    val earDroop = if (mood == PetMood.MISSING || mood == PetMood.SLEEPY) 0.15f else 0f
    for (side in listOf(-1f, 1f)) {
        val ear = Path().apply {
            moveTo(head.x + side * r * 0.25f, head.y - r * 0.55f)
            lineTo(head.x + side * r * (0.72f + earDroop), head.y - r * (1.05f - earDroop * 2))
            lineTo(head.x + side * r * 0.75f, head.y - r * 0.25f)
            close()
        }
        drawPath(ear, lerp(fur, Color.Black, 0.12f))
    }
    ball(head, r * 0.82f, fur)
    // Collar de hilo rojo
    drawArc(THREAD_RED, 20f, 140f, false, topLeft = c - Offset(r * 0.6f, r * 0.45f), size = Size(r * 1.2f, r * 0.6f), style = Stroke(r * 0.12f, cap = StrokeCap.Round))
    // Bigotes
    for (side in listOf(-1f, 1f)) for (k in 0..1) {
        drawLine(
            INK.copy(alpha = 0.5f), head + Offset(side * r * 0.35f, r * (0.18f + k * 0.12f)),
            head + Offset(side * r * 0.95f, r * (0.08f + k * 0.2f)), strokeWidth = r * 0.03f, cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawBunny(c: Offset, r: Float, t: Float, mood: PetMood) {
    val fur = PetSpecies.BUNNY.fur()
    val head = c + Offset(0f, -r * 0.55f)
    val droop = if (mood == PetMood.MISSING || mood == PetMood.SLEEPY) 35f else 6f * sin(t)
    for (side in listOf(-1f, 1f)) {
        rotate(side * (12f + droop), head + Offset(side * r * 0.3f, -r * 0.5f)) {
            val ear = head + Offset(side * r * 0.3f, -r * 1.25f)
            ball(ear, r * 0.24f, fur, rx = 1f, ry = 2.6f)
            drawOval(Color(0xFFFFB3C7), topLeft = ear - Offset(r * 0.1f, r * 0.45f), size = Size(r * 0.2f, r * 0.9f))
        }
    }
    ball(c + Offset(0f, r * 0.25f), r * 0.85f, fur, rx = 1.05f, ry = 0.9f)
    ball(head, r * 0.82f, fur)
    // Bufanda de hilo rojo con su puntita
    drawArc(THREAD_RED, 10f, 160f, false, topLeft = c - Offset(r * 0.65f, r * 0.5f), size = Size(r * 1.3f, r * 0.65f), style = Stroke(r * 0.18f, cap = StrokeCap.Round))
    drawLine(THREAD_RED, c + Offset(r * 0.35f, -r * 0.05f), c + Offset(r * (0.55f + 0.05f * sin(t)), r * 0.45f), strokeWidth = r * 0.14f, cap = StrokeCap.Round)
}

private fun DrawScope.drawDog(c: Offset, r: Float, t: Float, mood: PetMood) {
    val fur = PetSpecies.DOG.fur()
    val wag = when (mood) {
        PetMood.RADIANT -> 30f * sin(t * 2f)
        PetMood.HAPPY -> 18f * sin(t)
        else -> 4f * sin(t)
    }
    rotate(wag, c + Offset(r * 0.7f, r * 0.3f)) {
        drawLine(lerp(fur, Color.Black, 0.12f), c + Offset(r * 0.7f, r * 0.3f), c + Offset(r * 1.3f, -r * 0.3f), strokeWidth = r * 0.2f, cap = StrokeCap.Round)
    }
    ball(c + Offset(0f, r * 0.25f), r * 0.85f, fur, rx = 1.05f, ry = 0.9f)
    val head = c + Offset(0f, -r * 0.55f)
    ball(head, r * 0.82f, fur)
    // Orejas caídas
    val earColor = lerp(fur, Color.Black, 0.3f)
    for (side in listOf(-1f, 1f)) {
        rotate(side * (if (mood == PetMood.MISSING) 5f else 20f + 5f * sin(t)), head + Offset(side * r * 0.6f, -r * 0.5f)) {
            ball(head + Offset(side * r * 0.75f, -r * 0.05f), r * 0.26f, earColor, rx = 1f, ry = 1.8f)
        }
    }
    // Hocico
    ball(head + Offset(0f, r * 0.32f), r * 0.32f, lerp(fur, Color.White, 0.55f), rx = 1.2f, ry = 0.85f)
    drawOval(INK, topLeft = head + Offset(-r * 0.1f, r * 0.15f), size = Size(r * 0.2f, r * 0.13f))
    if (mood == PetMood.RADIANT || mood == PetMood.HAPPY) {
        drawOval(Color(0xFFFF7A93), topLeft = head + Offset(-r * 0.08f, r * 0.48f), size = Size(r * 0.16f, r * 0.2f))
    }
    // Collar rojo con placa dorada
    drawArc(THREAD_RED, 20f, 140f, false, topLeft = c - Offset(r * 0.6f, r * 0.45f), size = Size(r * 1.2f, r * 0.6f), style = Stroke(r * 0.13f, cap = StrokeCap.Round))
    drawCircle(GOLD, r * 0.09f, c + Offset(0f, r * 0.17f))
}

private fun DrawScope.drawFace(face: Offset, r: Float, mood: PetMood, blink: Float, species: PetSpecies) {
    val eyeY = face.y - r * 0.08f + if (mood == PetMood.MISSING) r * 0.08f else 0f
    val gap = r * 0.36f
    val eyeR = r * 0.13f
    for (side in listOf(-1f, 1f)) {
        val eye = Offset(face.x + side * gap, eyeY)
        when {
            mood == PetMood.SLEEPY || blink > 0f ->
                drawArc(INK, 10f, 160f, false, topLeft = eye - Offset(eyeR, eyeR * 0.6f), size = Size(eyeR * 2, eyeR * 1.2f), style = Stroke(r * 0.06f, cap = StrokeCap.Round))
            mood == PetMood.RADIANT ->
                drawArc(INK, 200f, 140f, false, topLeft = eye - Offset(eyeR, eyeR * 0.4f), size = Size(eyeR * 2, eyeR * 1.6f), style = Stroke(r * 0.07f, cap = StrokeCap.Round))
            else -> {
                drawCircle(INK, eyeR, eye)
                val look = if (mood == PetMood.MISSING) Offset(0f, eyeR * 0.35f) else Offset(-eyeR * 0.3f, -eyeR * 0.35f)
                drawCircle(Color.White, eyeR * 0.38f, eye + look)
            }
        }
    }
    // Cachetes
    val cheek = if (species == PetSpecies.YARN) Color.White.copy(alpha = 0.25f) else Color(0xFFFF8FA3).copy(alpha = 0.45f)
    for (side in listOf(-1f, 1f)) drawCircle(cheek, r * 0.1f, Offset(face.x + side * r * 0.58f, eyeY + r * 0.2f))
    // Boca
    val mouth = Offset(face.x, eyeY + r * 0.25f)
    when (mood) {
        PetMood.RADIANT -> drawArc(INK, 0f, 180f, true, topLeft = mouth - Offset(r * 0.14f, r * 0.08f), size = Size(r * 0.28f, r * 0.22f))
        PetMood.HAPPY -> drawArc(INK, 20f, 140f, false, topLeft = mouth - Offset(r * 0.12f, r * 0.1f), size = Size(r * 0.24f, r * 0.16f), style = Stroke(r * 0.05f, cap = StrokeCap.Round))
        PetMood.SLEEPY -> drawOval(INK, topLeft = mouth - Offset(r * 0.05f, 0f), size = Size(r * 0.1f, r * 0.08f))
        PetMood.MISSING -> drawLine(INK, mouth - Offset(r * 0.08f, 0f), mouth + Offset(r * 0.08f, 0f), strokeWidth = r * 0.05f, cap = StrokeCap.Round)
    }
}

/** Un accesorio por etapa: corazón (joven), moño (adulto), lentes redondos (sabio). */
private fun DrawScope.drawAccessory(stage: PetStage, species: PetSpecies, body: Offset, face: Offset, r: Float, faceR: Float) {
    val chest = if (species == PetSpecies.YARN) body + Offset(r * 0.55f, r * 0.55f) else body + Offset(0f, r * 0.18f)
    when (stage) {
        PetStage.BABY -> Unit
        PetStage.YOUNG -> drawHeartShape(chest, r * 0.32f, Color(0xFFFF4D8D))
        PetStage.ADULT -> {
            val bow = if (species == PetSpecies.YARN) face + Offset(0f, -faceR * 0.95f) else chest
            for (side in listOf(-1f, 1f)) {
                val wing = Path().apply {
                    moveTo(bow.x, bow.y)
                    lineTo(bow.x + side * r * 0.32f, bow.y - r * 0.16f)
                    lineTo(bow.x + side * r * 0.32f, bow.y + r * 0.16f)
                    close()
                }
                drawPath(wing, GOLD)
            }
            drawCircle(lerp(GOLD, Color.Black, 0.2f), r * 0.07f, bow)
        }
        PetStage.WISE -> {
            val eyeY = face.y - faceR * 0.08f
            for (side in listOf(-1f, 1f)) {
                drawCircle(GOLD, faceR * 0.2f, Offset(face.x + side * faceR * 0.36f, eyeY), style = Stroke(faceR * 0.05f))
            }
            drawLine(GOLD, Offset(face.x - faceR * 0.16f, eyeY), Offset(face.x + faceR * 0.16f, eyeY), strokeWidth = faceR * 0.05f)
        }
    }
}

/** Lo que sostiene: un casete de sus recuerdos (cuando extraña) o una carta (cuando entrega algo). */
private fun DrawScope.drawHeld(body: Offset, r: Float, letter: Boolean) {
    val at = body + Offset(-r * 0.05f, r * 0.45f)
    val w = r * 0.9f
    val h = r * 0.58f
    if (letter) {
        drawRoundRect(Color.White, topLeft = at - Offset(w / 2, h / 2), size = Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.06f))
        val flap = Path().apply {
            moveTo(at.x - w / 2, at.y - h / 2); lineTo(at.x, at.y + h * 0.05f); lineTo(at.x + w / 2, at.y - h / 2)
        }
        drawPath(flap, Color(0xFFE5484D), style = Stroke(r * 0.05f))
        drawHeartShape(at + Offset(0f, h * 0.15f), r * 0.14f, Color(0xFFE5484D))
    } else {
        drawRoundRect(Color(0xFF3A2D4F), topLeft = at - Offset(w / 2, h / 2), size = Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.08f))
        drawRoundRect(Color(0xFFFFD36E), topLeft = at - Offset(w * 0.4f, h * 0.38f), size = Size(w * 0.8f, h * 0.32f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.04f))
        for (side in listOf(-1f, 1f)) drawCircle(Color.White.copy(alpha = 0.85f), h * 0.13f, at + Offset(side * w * 0.2f, h * 0.15f))
    }
}

private fun DrawScope.drawHeartShape(center: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y + size * 0.35f)
        cubicTo(center.x - size * 0.9f, center.y - size * 0.25f, center.x - size * 0.35f, center.y - size * 0.85f, center.x, center.y - size * 0.35f)
        cubicTo(center.x + size * 0.35f, center.y - size * 0.85f, center.x + size * 0.9f, center.y - size * 0.25f, center.x, center.y + size * 0.35f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawSparkle(at: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(at.x, at.y - size * 2)
        quadraticTo(at.x, at.y, at.x + size * 2, at.y)
        quadraticTo(at.x, at.y, at.x, at.y + size * 2)
        quadraticTo(at.x, at.y, at.x - size * 2, at.y)
        quadraticTo(at.x, at.y, at.x, at.y - size * 2)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawZ(at: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(at.x - size, at.y - size); lineTo(at.x + size, at.y - size)
        lineTo(at.x - size, at.y + size); lineTo(at.x + size, at.y + size)
    }
    drawPath(path, color, style = Stroke(size * 0.35f, cap = StrokeCap.Round))
}
