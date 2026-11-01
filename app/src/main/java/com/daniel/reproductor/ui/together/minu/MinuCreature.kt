package com.daniel.reproductor.ui.together.minu

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.reproductor.cloud.MinuPet
import com.daniel.reproductor.cloud.PetMood
import com.daniel.reproductor.cloud.PetSpecies
import com.daniel.reproductor.cloud.PetStage
import com.daniel.reproductor.ui.together.THREAD_RED
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

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
 * Minu dibujado con volumen (luz arriba a la izquierda, sombra en el piso) y con un cuerpo que se mueve
 * solo (ver [MinuRig]): respira, parpadea a destiempo, mira a su alrededor, ladea la cabeza, mueve las
 * orejas y la cola con retraso y salta con anticipación y aplastamiento. Su ánimo cambia el ritmo:
 * radiante da saltitos y brilla, contento flota, somnoliento cabecea con "z" y cuando extraña mira hacia
 * abajo abrazando un casete de sus recuerdos. Cada etapa lo hace más grande y le da un accesorio.
 *
 * [hop] 0..1 lo hace saltar desde fuera (al entregar algo). Con [interactive] responde al tacto: un
 * toque lo hace brincar y reír ([onPoke]); arrastrar el dedo encima lo acaricia ([onPet]).
 * [celebrate] cambia de valor para pedirle saltitos de alegría.
 */
@Composable
fun MinuCreature(
    species: PetSpecies,
    mood: PetMood,
    stage: PetStage,
    modifier: Modifier = Modifier.size(84.dp),
    hop: Float = 0f,
    carrying: Boolean = false,
    interactive: Boolean = false,
    onPoke: (() -> Unit)? = null,
    onPet: (() -> Unit)? = null,
    celebrate: Int = 0,
) {
    val rig = remember { MinuRig() }
    rig.mood = mood
    LaunchedEffect(rig) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                rig.step((now - last) / 1_000_000_000f)
                last = now
            }
        }
    }
    LaunchedEffect(celebrate) { if (celebrate > 0) rig.celebrate() }
    val poked by rememberUpdatedState(onPoke)
    val petted by rememberUpdatedState(onPet)
    val touch = if (!interactive) Modifier else Modifier.pointerInput(rig) {
        awaitEachGesture {
            val down = awaitFirstDown()
            var moved = 0f
            var stroking = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                moved += (change.position - change.previousPosition).getDistance()
                if (moved > 18f) {
                    if (!stroking) petted?.invoke()
                    stroking = true
                    rig.pet((change.position.x / size.width) * 2f - 1f)
                    change.consume()
                }
            }
            if (stroking) rig.petEnd() else {
                rig.poke()
                poked?.invoke()
            }
        }
    }

    Canvas(modifier.then(touch)) {
        rig.frame // se redibuja en cada paso de la simulación
        val s = size.minDimension
        val scale = when (stage) {
            PetStage.BABY -> 0.78f
            PetStage.YOUNG -> 0.88f
            PetStage.ADULT, PetStage.WISE -> 1f
        }
        val r = s * 0.27f * scale
        val ground = size.height * 0.94f
        val lift = (rig.lift + sin(hop * PI.toFloat()) * 0.22f) * s
        val sx = 1f + rig.squash * 0.55f - rig.breath * 0.4f
        val sy = 1f - rig.squash + rig.breath
        val body = Offset(size.width / 2f, ground - r * sy - lift)
        val pose = Pose(rig.t, rig.tilt, Offset(rig.gazeX, rig.gazeY), rig.lid, rig.ear, rig.wag, rig.mouth, rig.joy)

        // Sombra en el piso: se achica y aclara cuando salta
        val air = (lift / (s * 0.3f)).coerceIn(0f, 1f)
        val shadowW = r * (1.3f - 0.5f * air) * sx
        drawOval(
            Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.32f * (1f - air * 0.5f)), Color.Transparent), center = Offset(body.x, ground), radius = shadowW),
            topLeft = Offset(body.x - shadowW, ground - shadowW * 0.22f), size = Size(shadowW * 2f, shadowW * 0.44f),
        )
        if (mood == PetMood.RADIANT) {
            drawCircle(
                Brush.radialGradient(listOf(GOLD.copy(alpha = 0.32f + 0.06f * sin(rig.t * 3f)), Color.Transparent), center = body, radius = r * 2.1f),
                radius = r * 2.1f, center = body,
            )
        }

        withTransform({ scale(sx, sy, pivot = Offset(body.x, ground - lift)) }) {
            when (species) {
                PetSpecies.YARN -> rotate(pose.tilt * 0.6f, Offset(body.x, body.y + r)) {
                    drawYarn(body, r, pose)
                    val face = body + Offset(0f, -r * 0.05f)
                    drawFace(face, r, mood, pose, species)
                    drawHeadAccessory(stage, species, face, r)
                }
                else -> {
                    val head = body + Offset(0f, -r * 0.55f)
                    val neck = body + Offset(0f, -r * 0.05f)
                    when (species) {
                        PetSpecies.CAT -> drawCat(body, head, neck, r, pose, mood)
                        PetSpecies.BUNNY -> drawBunny(body, head, neck, r, pose, mood)
                        else -> drawDog(body, head, neck, r, pose, mood)
                    }
                    rotate(pose.tilt, neck) {
                        drawFace(head, r * 0.82f, mood, pose, species)
                        drawHeadAccessory(stage, species, head, r * 0.82f)
                    }
                }
            }
            drawChestAccessory(stage, species, body, r)
            if (mood == PetMood.MISSING || carrying) drawHeld(body, r, carrying)
        }

        // Efectos alrededor
        when (mood) {
            PetMood.RADIANT -> for (i in 0 until 5) {
                val a = rig.t * 1.3f + i * (2 * PI / 5).toFloat()
                val at = body + Offset(cos(a), sin(a) * 0.6f) * (r * 1.7f)
                drawSparkle(at, r * 0.13f * (0.6f + 0.4f * sin(rig.t * 4f + i)), GOLD)
            }
            PetMood.SLEEPY -> for (i in 0 until 3) {
                val p = (rig.t / 3.2f + i / 3f) % 1f
                val at = body + Offset(r * (0.8f + 0.5f * p + 0.1f * sin(p * 9f)), -r * (1.1f + 1.2f * p))
                drawZ(at, r * (0.16f + 0.12f * p), Color.White.copy(alpha = (1f - p) * 0.85f))
            }
            else -> Unit
        }
        // Corazones cuando lo acarician
        for ((x, age) in rig.hearts) {
            val at = body + Offset(x * r, -r * (1.2f + age * 1.6f))
            drawHeartShape(at, r * (0.22f + 0.1f * age), Color(0xFFFF4D8D).copy(alpha = (1f - age) * 0.9f))
        }
    }
}

/** La pose de un instante, ya calculada por el rig. */
private class Pose(
    val t: Float,
    val tilt: Float,
    val gaze: Offset,
    val lid: Float,
    val ear: Float,
    val wag: Float,
    val mouth: Float,
    val joy: Float,
)

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

private fun DrawScope.drawYarn(c: Offset, r: Float, pose: Pose) {
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
    // La punta suelta del hilo: se mece como una colita
    val sway = pose.wag / 30f
    val path = Path().apply {
        moveTo(c.x + r * 0.85f, c.y + r * 0.4f)
        quadraticTo(c.x + r * (1.5f + 0.1f * sway), c.y + r * (0.3f + 0.2f * sway), c.x + r * (1.35f + 0.15f * sway), c.y + r * 0.95f)
    }
    drawPath(path, THREAD_RED, style = Stroke(r * 0.09f, cap = StrokeCap.Round))
}

private fun DrawScope.drawCat(c: Offset, head: Offset, neck: Offset, r: Float, pose: Pose, mood: PetMood) {
    val fur = PetSpecies.CAT.fur()
    // Cola: la punta se mueve más que la base (como un látigo suave)
    val w = pose.wag / 24f
    val tail = Path().apply {
        moveTo(c.x + r * 0.7f, c.y + r * 0.5f)
        cubicTo(
            c.x + r * 1.6f, c.y + r * 0.4f,
            c.x + r * (1.2f + 0.1f * w), c.y - r * (0.6f + 0.15f * w),
            c.x + r * (1.5f + 0.3f * w), c.y - r * (0.9f - 0.1f * abs(w)),
        )
    }
    drawPath(tail, lerp(fur, Color.Black, 0.15f), style = Stroke(r * 0.22f, cap = StrokeCap.Round))
    ball(c + Offset(0f, r * 0.25f), r * 0.85f, fur, rx = 1.05f, ry = 0.9f)
    rotate(pose.tilt, neck) {
        val droop = if (mood == PetMood.MISSING || mood == PetMood.SLEEPY) 0.15f else 0f
        for (side in listOf(-1f, 1f)) {
            rotate(side * pose.ear * 0.6f, head + Offset(side * r * 0.5f, -r * 0.45f)) {
                val ear = Path().apply {
                    moveTo(head.x + side * r * 0.25f, head.y - r * 0.55f)
                    lineTo(head.x + side * r * (0.72f + droop), head.y - r * (1.05f - droop * 2))
                    lineTo(head.x + side * r * 0.75f, head.y - r * 0.25f)
                    close()
                }
                drawPath(ear, lerp(fur, Color.Black, 0.12f))
                val inner = Path().apply {
                    moveTo(head.x + side * r * 0.36f, head.y - r * 0.55f)
                    lineTo(head.x + side * r * (0.68f + droop), head.y - r * (0.93f - droop * 2))
                    lineTo(head.x + side * r * 0.68f, head.y - r * 0.38f)
                    close()
                }
                drawPath(inner, Color(0xFFFFB3C7).copy(alpha = 0.8f))
            }
        }
        ball(head, r * 0.82f, fur)
        // Bigotes: tiemblan un poquito al respirar
        val twitch = sin(pose.t * 5f) * r * 0.01f
        for (side in listOf(-1f, 1f)) for (k in 0..1) {
            drawLine(
                INK.copy(alpha = 0.5f), head + Offset(side * r * 0.35f, r * (0.18f + k * 0.12f)),
                head + Offset(side * r * 0.95f, r * (0.08f + k * 0.2f) + twitch), strokeWidth = r * 0.03f, cap = StrokeCap.Round,
            )
        }
    }
    // Collar de hilo rojo
    drawArc(THREAD_RED, 20f, 140f, false, topLeft = c - Offset(r * 0.6f, r * 0.45f), size = Size(r * 1.2f, r * 0.6f), style = Stroke(r * 0.12f, cap = StrokeCap.Round))
}

private fun DrawScope.drawBunny(c: Offset, head: Offset, neck: Offset, r: Float, pose: Pose, mood: PetMood) {
    val fur = PetSpecies.BUNNY.fur()
    ball(c + Offset(0f, r * 0.25f), r * 0.85f, fur, rx = 1.05f, ry = 0.9f)
    // Colita de algodón
    ball(c + Offset(r * 0.78f, r * 0.55f), r * 0.2f, fur)
    rotate(pose.tilt, neck) {
        val droop = if (mood == PetMood.MISSING || mood == PetMood.SLEEPY) 35f else 6f
        for (side in listOf(-1f, 1f)) {
            // Cada oreja se mueve con su propio retraso: la de la izquierda un poco después
            val angle = side * (12f + droop) + pose.ear * (if (side < 0) 1f else 0.7f)
            rotate(angle, head + Offset(side * r * 0.3f, -r * 0.5f)) {
                val ear = head + Offset(side * r * 0.3f, -r * 1.25f)
                ball(ear, r * 0.24f, fur, rx = 1f, ry = 2.6f)
                drawOval(Color(0xFFFFB3C7), topLeft = ear - Offset(r * 0.1f, r * 0.45f), size = Size(r * 0.2f, r * 0.9f))
            }
        }
        ball(head, r * 0.82f, fur)
        // Naricita que se mueve
        val sniff = 1f + 0.12f * sin(pose.t * 9f).coerceAtLeast(0f)
        drawOval(Color(0xFFFF8FA3), topLeft = head + Offset(-r * 0.06f * sniff, r * 0.1f), size = Size(r * 0.12f * sniff, r * 0.08f))
    }
    // Bufanda de hilo rojo con su puntita
    drawArc(THREAD_RED, 10f, 160f, false, topLeft = c - Offset(r * 0.65f, r * 0.5f), size = Size(r * 1.3f, r * 0.65f), style = Stroke(r * 0.18f, cap = StrokeCap.Round))
    drawLine(THREAD_RED, c + Offset(r * 0.35f, -r * 0.05f), c + Offset(r * (0.55f + pose.wag / 400f), r * 0.45f), strokeWidth = r * 0.14f, cap = StrokeCap.Round)
}

private fun DrawScope.drawDog(c: Offset, head: Offset, neck: Offset, r: Float, pose: Pose, mood: PetMood) {
    val fur = PetSpecies.DOG.fur()
    rotate(pose.wag * 1.3f, c + Offset(r * 0.7f, r * 0.3f)) {
        drawLine(lerp(fur, Color.Black, 0.12f), c + Offset(r * 0.7f, r * 0.3f), c + Offset(r * 1.3f, -r * 0.3f), strokeWidth = r * 0.2f, cap = StrokeCap.Round)
    }
    ball(c + Offset(0f, r * 0.25f), r * 0.85f, fur, rx = 1.05f, ry = 0.9f)
    rotate(pose.tilt, neck) {
        ball(head, r * 0.82f, fur)
        // Orejas caídas que rebotan
        val earColor = lerp(fur, Color.Black, 0.3f)
        for (side in listOf(-1f, 1f)) {
            val base = if (mood == PetMood.MISSING) 5f else 20f
            rotate(side * (base + pose.ear * 0.8f), head + Offset(side * r * 0.6f, -r * 0.5f)) {
                ball(head + Offset(side * r * 0.75f, -r * 0.05f), r * 0.26f, earColor, rx = 1f, ry = 1.8f)
            }
        }
        // Hocico
        ball(head + Offset(0f, r * 0.32f), r * 0.32f, lerp(fur, Color.White, 0.55f), rx = 1.2f, ry = 0.85f)
        drawOval(INK, topLeft = head + Offset(-r * 0.1f, r * 0.15f), size = Size(r * 0.2f, r * 0.13f))
        if (mood == PetMood.RADIANT || mood == PetMood.HAPPY || pose.joy > 0.3f) {
            // Lengüita que entra y sale con la respiración
            val tongue = 0.18f + 0.06f * sin(pose.t * 6f)
            drawOval(Color(0xFFFF7A93), topLeft = head + Offset(-r * 0.08f, r * 0.48f), size = Size(r * 0.16f, r * tongue))
        }
    }
    // Collar rojo con placa dorada
    drawArc(THREAD_RED, 20f, 140f, false, topLeft = c - Offset(r * 0.6f, r * 0.45f), size = Size(r * 1.2f, r * 0.6f), style = Stroke(r * 0.13f, cap = StrokeCap.Round))
    drawCircle(GOLD, r * 0.09f, c + Offset(0f, r * 0.17f))
}

private fun DrawScope.drawFace(face: Offset, r: Float, mood: PetMood, pose: Pose, species: PetSpecies) {
    val eyeY = face.y - r * 0.08f
    val gap = r * 0.36f
    val eyeR = r * 0.13f
    val look = Offset(pose.gaze.x * eyeR * 0.45f, pose.gaze.y * eyeR * 0.4f)
    for (side in listOf(-1f, 1f)) {
        val eye = Offset(face.x + side * gap, eyeY) + look * 0.6f
        when {
            pose.joy > 0.55f || (mood == PetMood.RADIANT && pose.lid < 0.5f) -> // ojitos felices ^ ^
                drawArc(INK, 200f, 140f, false, topLeft = eye - Offset(eyeR, eyeR * 0.4f), size = Size(eyeR * 2, eyeR * 1.6f), style = Stroke(r * 0.07f, cap = StrokeCap.Round))
            pose.lid > 0.85f -> // cerrados
                drawArc(INK, 10f, 160f, false, topLeft = eye - Offset(eyeR, eyeR * 0.6f), size = Size(eyeR * 2, eyeR * 1.2f), style = Stroke(r * 0.06f, cap = StrokeCap.Round))
            else -> {
                // Ojo que se cierra de arriba hacia abajo según el párpado
                val open = 1f - pose.lid
                val h = eyeR * 2f * open.coerceAtLeast(0.12f)
                drawOval(INK, topLeft = Offset(eye.x - eyeR, eye.y + eyeR - h), size = Size(eyeR * 2f, h))
                if (open > 0.35f) drawCircle(Color.White, eyeR * 0.38f, eye + Offset(-eyeR * 0.3f, -eyeR * 0.35f) + look * 0.5f)
            }
        }
    }
    // Cachetes: se sonrojan más cuando lo acarician
    val blush = 0.45f + 0.35f * pose.joy
    val cheek = if (species == PetSpecies.YARN) Color.White.copy(alpha = 0.25f + 0.2f * pose.joy) else Color(0xFFFF8FA3).copy(alpha = blush)
    for (side in listOf(-1f, 1f)) drawCircle(cheek, r * (0.1f + 0.02f * pose.joy), Offset(face.x + side * r * 0.58f, eyeY + r * 0.2f))
    // Boca: se abre al bostezar o reír
    val mouth = Offset(face.x, eyeY + r * 0.25f)
    if (pose.mouth > 0.15f) {
        val o = pose.mouth
        drawOval(INK, topLeft = mouth - Offset(r * 0.08f * (0.6f + o * 0.5f), r * 0.02f), size = Size(r * 0.16f * (0.6f + o * 0.5f), r * 0.22f * o))
        return
    }
    when {
        mood == PetMood.RADIANT || pose.joy > 0.55f ->
            drawArc(INK, 0f, 180f, true, topLeft = mouth - Offset(r * 0.14f, r * 0.08f), size = Size(r * 0.28f, r * 0.22f))
        mood == PetMood.HAPPY ->
            drawArc(INK, 20f, 140f, false, topLeft = mouth - Offset(r * 0.12f, r * 0.1f), size = Size(r * 0.24f, r * 0.16f), style = Stroke(r * 0.05f, cap = StrokeCap.Round))
        mood == PetMood.SLEEPY -> drawOval(INK, topLeft = mouth - Offset(r * 0.05f, 0f), size = Size(r * 0.1f, r * 0.08f))
        else -> drawArc(INK, 200f, 140f, false, topLeft = mouth - Offset(r * 0.09f, -r * 0.02f), size = Size(r * 0.18f, r * 0.1f), style = Stroke(r * 0.045f, cap = StrokeCap.Round))
    }
}

/** Accesorio en el pecho: corazón (joven) o moño dorado (adulto, salvo el ovillo que lo lleva arriba). */
private fun DrawScope.drawChestAccessory(stage: PetStage, species: PetSpecies, body: Offset, r: Float) {
    val chest = if (species == PetSpecies.YARN) body + Offset(r * 0.55f, r * 0.55f) else body + Offset(0f, r * 0.18f)
    when {
        stage == PetStage.YOUNG -> drawHeartShape(chest, r * 0.32f, Color(0xFFFF4D8D))
        stage == PetStage.ADULT && species != PetSpecies.YARN -> drawBow(chest, r)
        else -> Unit
    }
}

/** Accesorio en la cabeza (gira con ella): moño del ovillo adulto o lentes redondos del sabio. */
private fun DrawScope.drawHeadAccessory(stage: PetStage, species: PetSpecies, face: Offset, faceR: Float) {
    when {
        stage == PetStage.ADULT && species == PetSpecies.YARN -> drawBow(face + Offset(0f, -faceR * 0.95f), faceR)
        stage == PetStage.WISE -> {
            val eyeY = face.y - faceR * 0.08f
            for (side in listOf(-1f, 1f)) {
                drawCircle(GOLD, faceR * 0.2f, Offset(face.x + side * faceR * 0.36f, eyeY), style = Stroke(faceR * 0.05f))
            }
            drawLine(GOLD, Offset(face.x - faceR * 0.16f, eyeY), Offset(face.x + faceR * 0.16f, eyeY), strokeWidth = faceR * 0.05f)
        }
        else -> Unit
    }
}

private fun DrawScope.drawBow(at: Offset, r: Float) {
    for (side in listOf(-1f, 1f)) {
        val wing = Path().apply {
            moveTo(at.x, at.y)
            lineTo(at.x + side * r * 0.32f, at.y - r * 0.16f)
            lineTo(at.x + side * r * 0.32f, at.y + r * 0.16f)
            close()
        }
        drawPath(wing, GOLD)
    }
    drawCircle(lerp(GOLD, Color.Black, 0.2f), r * 0.07f, at)
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
