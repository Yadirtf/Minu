package com.daniel.reproductor.ui.together

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.reproductor.cloud.DuoStreak
import com.daniel.reproductor.cloud.HubEvent
import com.daniel.reproductor.cloud.PartnerInfo
import com.daniel.reproductor.ui.theme.LocalAppStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** El rojo del hilo que une a la pareja. */
internal val THREAD_RED = Color(0xFFFF3B5C)
private val THREAD_GLOW = Color(0xFFFF8FA3)
internal val WARM = Color(0xFFFF9A62)
private val STREAK_GOLD = Color(0xFFFFC857)

private class Flight(val id: Long, val emoji: String, val fromMe: Boolean)

/** Una luz desenfocada del fondo. [depth] 0..1: más cerca, más grande y más paralaje. */
private class Bokeh(val x: Float, val y: Float, val depth: Float, val tint: Int)
private class Burst(val id: Long, val at: Offset)

/**
 * El hilo rojo: tu burbuja y la de tu pareja unidas por un hilo que vibra con la música.
 *
 * - Mantén presionado el corazón del centro: late cada vez más fuerte y al soltar le llegan los latidos.
 * - Arrastra tu burbuja hasta la suya: se abrazan (🫂) y el hilo rebota al volver.
 * - Toca el hilo: vibra como una cuerda.
 * - Cada reacción viaja por el hilo, de quien la manda a quien la recibe.
 *
 * Sin pareja ([partner] null) el hilo queda punteado esperando a alguien.
 */
@Composable
fun DuoHero(
    me: String,
    partner: PartnerInfo?,
    playing: Boolean,
    events: Flow<HubEvent>,
    onHeartbeat: (Int) -> Unit,
    onHug: () -> Unit,
    modifier: Modifier = Modifier,
    streak: DuoStreak? = null,
) {
    val c = MaterialTheme.colorScheme
    val style = LocalAppStyle.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val linked = partner != null
    val heartbeat by rememberUpdatedState(onHeartbeat)
    val hug by rememberUpdatedState(onHug)

    val orbR = with(density) { 38.dp.toPx() }
    val sagPx = with(density) { 26.dp.toPx() }
    val knotTouch = with(density) { 36.dp.toPx() }
    val threadTouch = with(density) { 30.dp.toPx() }
    val pluckPx = with(density) { 16.dp.toPx() }
    val musicPx = with(density) { 6.dp.toPx() }
    val idlePx = with(density) { 1.5.dp.toPx() }
    val labelGap = with(density) { 44.dp.toPx() }
    val dotGap = with(density) { 18.dp.toPx() }

    var box by remember { mutableStateOf(IntSize.Zero) }
    var pull by remember { mutableStateOf(Offset.Zero) } // cuánto arrastraste tu burbuja
    var holding by remember { mutableStateOf(false) }
    var hold by remember { mutableFloatStateOf(0f) } // 0..1, fuerza del latido
    val pluck = remember { Animatable(0f) }
    val partnerPull = remember { Animatable(0f) } // su burbuja se acerca cuando te abraza
    val myBump = remember { Animatable(1f) }
    val partnerBump = remember { Animatable(1f) }
    val flights = remember { mutableStateListOf<Flight>() }
    val bursts = remember { mutableStateListOf<Burst>() }
    val knotScale by animateFloatAsState(
        if (holding) 1f + hold * 0.9f else 1f,
        spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow), label = "nudo",
    )

    val inf = rememberInfiniteTransition(label = "duo")
    val phase by inf.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "fase")
    val breathe by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "respira")
    val float by inf.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "flotar")
    val drift by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "deriva")
    val stars = remember { List(18) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat() * 2f * PI.toFloat()) } }
    // Luces desenfocadas a distintas profundidades: las grandes están "cerca" y se mueven más
    val bokeh = remember { List(9) { Bokeh(Random.nextFloat(), Random.nextFloat(), 0.25f + Random.nextFloat() * 0.75f, it % 3) } }
    val bobPx = with(density) { 4.dp.toPx() }

    fun myHome() = Offset(box.width * 0.2f, box.height * 0.42f)
    fun partnerHome() = Offset(box.width * 0.8f, box.height * 0.42f)
    // Las burbujas flotan un poco, cada una a su ritmo
    fun myPos() = myHome() + pull + Offset(0f, bobPx * sin(float))
    fun partnerPos() = partnerHome() + (myHome() - partnerHome()) * partnerPull.value + Offset(0f, bobPx * sin(float + 2.2f))
    fun sag(a: Offset, b: Offset): Float {
        val rest = (partnerHome() - myHome()).getDistance().coerceAtLeast(1f)
        val len = (b - a).getDistance().coerceAtLeast(1f)
        return (sagPx * rest / len).coerceAtMost(sagPx * 2.2f)
    }
    fun wave() = (if (playing) musicPx * (0.7f + 0.3f * sin(phase * 2f)) else idlePx) + pluck.value
    fun knot(): Offset {
        val a = myPos(); val b = partnerPos()
        return threadPoint(a, b, 0.5f, sag(a, b), wave(), phase)
    }
    fun twang(strength: Float = 1f) = scope.launch {
        pluck.snapTo(pluckPx * strength)
        pluck.animateTo(0f, spring(dampingRatio = 0.12f, stiffness = Spring.StiffnessMediumLow))
    }
    fun bump(which: Animatable<Float, AnimationVector1D>) = scope.launch {
        which.snapTo(1.22f)
        which.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMedium))
    }

    // Lo que llega en vivo (tuyo o de tu pareja) viaja por el hilo
    LaunchedEffect(events, linked) {
        if (!linked) return@LaunchedEffect
        events.collect { e ->
            if (e !is HubEvent.LiveReaction) return@collect
            flights += Flight(System.nanoTime(), e.emoji, e.fromMe)
            if (!e.fromMe && e.emoji == TogetherViewModel.HUG) launch {
                partnerPull.animateTo(0.32f, tween(380, easing = FastOutSlowInEasing))
                partnerPull.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessLow))
            }
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(250.dp)
            // Al arrastrar tu burbuja la tarjeta se inclina hacia donde la llevas, como una ventana con fondo
            .graphicsLayer {
                if (box.width > 0) {
                    rotationY = (pull.x / box.width).coerceIn(-1f, 1f) * 8f
                    rotationX = -(pull.y / box.height).coerceIn(-1f, 1f) * 6f
                }
                cameraDistance = 16f * density
            }
            .clip(style.cardShape)
            .background(Brush.verticalGradient(listOf(THREAD_RED.copy(alpha = 0.16f), c.primary.copy(alpha = 0.10f), c.surfaceContainer)))
            .onSizeChanged { box = it }
            .pointerInput(linked) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val at = down.position
                    when {
                        // Tu burbuja: se arrastra; si toca la suya, es un abrazo
                        linked && (at - myPos()).getDistance() < orbR * 1.3f -> {
                            down.consume()
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            var close = false
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) { change.consume(); break }
                                pull += change.positionChange()
                                change.consume()
                                val nowClose = (myPos() - partnerPos()).getDistance() < orbR * 2.3f
                                if (nowClose && !close) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                close = nowClose
                            }
                            if (close) {
                                hug()
                                bursts += Burst(System.nanoTime(), (myPos() + partnerPos()) / 2f)
                            }
                            val from = pull
                            scope.launch {
                                Animatable(from, Offset.VectorConverter).animateTo(
                                    Offset.Zero, spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMediumLow),
                                ) { pull = value }
                            }
                            twang(if (close) 1.2f else 0.7f)
                        }
                        // El corazón: mientras lo presionas late más fuerte
                        linked && (at - knot()).getDistance() < knotTouch -> {
                            down.consume()
                            holding = true
                            val beating = scope.launch {
                                val start = withFrameMillis { it }
                                var beats = 0
                                while (true) {
                                    val t = withFrameMillis { it } - start
                                    hold = (t / 2400f).coerceAtMost(1f)
                                    // Late más rápido mientras más lo sostienes
                                    val b = (t / (520 - 260 * hold)).toInt()
                                    if (b > beats) {
                                        beats = b
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                }
                            }
                            var released = false
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (!change.pressed) { released = true; break }
                            }
                            beating.cancel()
                            if (released) {
                                heartbeat(1 + (hold * 4).roundToInt())
                                bursts += Burst(System.nanoTime(), knot())
                                twang(0.5f + hold)
                            }
                            holding = false
                            hold = 0f
                        }
                        // Cerca del hilo: vibra como una cuerda
                        else -> {
                            val a = myPos(); val b = partnerPos()
                            val s = sag(a, b)
                            val near = (0..24).any { i -> (threadPoint(a, b, i / 24f, s, 0f, 0f) - at).getDistance() < threadTouch }
                            if (near) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                twang()
                            }
                        }
                    }
                }
            },
    ) {
        // Fondo: estrellitas que titilan, el hilo y el corazón
        Canvas(Modifier.fillMaxSize()) {
            if (box == IntSize.Zero) return@Canvas
            // Profundidad: lo lejano se mueve poco al inclinar, lo cercano mucho
            drawRect(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), center = Offset(size.width / 2f, size.height * 0.35f), radius = size.width * 0.6f))
            stars.forEach { (x, y, o) ->
                val twinkle = (sin(phase + o) + 1f) / 2f
                val at = Offset(size.width * x, size.height * y) - pull * 0.04f
                drawCircle(Color.White, radius = 1.2.dp.toPx() + twinkle * 1.dp.toPx(), center = at, alpha = 0.10f + 0.25f * twinkle)
            }
            bokeh.forEach { l ->
                val y = ((l.y - drift * (0.3f + l.depth * 0.5f)) % 1f + 1f) % 1f
                val r = (6 + 18 * l.depth).dp.toPx()
                val at = Offset(size.width * l.x, size.height * (1.1f * y - 0.05f)) - pull * (0.06f + 0.12f * l.depth)
                val tint = when (l.tint) { 0 -> THREAD_RED; 1 -> WARM; else -> Color.White }
                val glow = if (playing) 1.6f else 1f
                drawCircle(
                    Brush.radialGradient(listOf(tint.copy(alpha = (0.10f + 0.10f * (1f - l.depth)) * glow), Color.Transparent), center = at, radius = r),
                    radius = r, center = at,
                )
            }
            // Sombras de las burbujas en el "piso": se encogen cuando la burbuja sube
            val floorY = box.height * 0.42f + orbR + 16.dp.toPx()
            listOf(myPos(), partnerPos()).forEachIndexed { i, p ->
                if (i == 1 && !linked) return@forEachIndexed
                val lift = ((floorY - orbR - 16.dp.toPx()) - p.y) / (orbR * 2f) // >0 si sube
                val w = orbR * 1.5f * (1f - 0.25f * lift.coerceIn(-1f, 1f))
                val center = Offset(p.x, floorY)
                drawOval(
                    Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.28f), Color.Transparent), center = center, radius = w),
                    topLeft = center - Offset(w, w * 0.28f), size = Size(w * 2f, w * 0.56f),
                )
            }
            val a = myPos(); val b = partnerPos()
            val s = sag(a, b)
            val w = wave()
            val path = Path()
            for (i in 0..48) {
                val p = threadPoint(a, b, i / 48f, s, w, phase)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            if (linked) {
                // La racha se nota en el hilo: brilla más con los días, hebras doradas a los 100, todo dorado al año
                val days = streak?.count ?: 0
                val color = when {
                    streak?.inDanger == true -> AMBER
                    days >= 365 -> STREAK_GOLD
                    else -> THREAD_RED
                }
                val glow = 0.30f + 0.25f * (days.coerceAtMost(30) / 30f)
                drawCord(path, color, glow, (10 + 4 * (days.coerceAtMost(30) / 30f)).dp.toPx(), phase)
                if (days in 100 until 365) {
                    drawPath(path, STREAK_GOLD, style = Stroke(1.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), phase * 6f)))
                }
                val k = threadPoint(a, b, 0.5f, s, w, phase)
                val beat = if (holding) 1f + 0.12f * sin(phase * 4f) else 1f + 0.05f * breathe
                drawCircle(THREAD_GLOW, radius = 22.dp.toPx() * knotScale, center = k, alpha = 0.18f + 0.3f * hold)
                drawHeart(k, 26.dp.toPx() * knotScale * beat, THREAD_RED)
            } else {
                drawPath(
                    path, THREAD_RED.copy(alpha = 0.55f),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f), -phase * 12f)),
                )
                drawCircle(
                    THREAD_RED.copy(alpha = 0.5f), radius = orbR, center = b,
                    style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), phase * 8f)),
                )
            }
            // Si tu pareja está en Minu, su burbuja irradia
            if (partner?.online == true) {
                drawCircle(THREAD_RED, radius = orbR * (1.05f + 0.4f * breathe), center = b, alpha = 0.35f * (1f - breathe), style = Stroke(2.dp.toPx()))
            }
        }

        // Burbujas
        Orb(me, Brush.linearGradient(listOf(c.primary, c.tertiary)), fade = 1f, scale = myBump.value) { myPos() }
        if (partner != null) {
            Orb(
                partner.displayName, Brush.linearGradient(listOf(THREAD_RED, WARM)),
                fade = if (partner.online) 1f else 0.6f, scale = partnerBump.value,
            ) { partnerPos() }
        } else {
            Label("?", 30, c.onSurfaceVariant) { partnerHome() + Offset(0f, -orbR * 0.45f) }
        }
        Label("Tú", 13, c.onSurface) { myPos() + Offset(0f, labelGap) }
        Label(partner?.displayName ?: "Tu persona", 13, c.onSurface) { partnerPos() + Offset(0f, labelGap) }
        if (linked && streak != null) {
            // El número de la racha bajo el nudo y, bajo cada nombre, si ya dedicó hoy
            val numberColor = if (streak.inDanger) AMBER else THREAD_RED
            Label(if (streak.count == 1) "1 día" else "${streak.count} días", 15, numberColor) { knot() + Offset(0f, dotGap * 1.6f) }
            if (!streak.inDanger) {
                Label(if (streak.todayMine) "● dedicaste" else "○ te falta", 11, if (streak.todayMine) THREAD_RED else c.onSurfaceVariant) {
                    myPos() + Offset(0f, labelGap + dotGap)
                }
                Label(if (streak.todayPartner) "● dedicó" else "○ le falta", 11, if (streak.todayPartner) THREAD_RED else c.onSurfaceVariant) {
                    partnerPos() + Offset(0f, labelGap + dotGap)
                }
            }
        }

        // Destellos de corazones (abrazos y latidos)
        bursts.toList().forEach { burst ->
            key(burst.id) {
                val p = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    p.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
                    bursts.remove(burst)
                }
                Canvas(Modifier.fillMaxSize()) {
                    val dist = 74.dp.toPx() * p.value
                    for (i in 0 until 10) {
                        val ang = i * (2 * PI / 10).toFloat() + 0.3f
                        val at = burst.at + Offset(cos(ang), sin(ang)) * dist
                        drawHeart(at, 12.dp.toPx() * (1f - 0.4f * p.value), if (i % 2 == 0) THREAD_RED else WARM, alpha = 1f - p.value)
                    }
                }
            }
        }

        // Reacciones viajando por el hilo
        flights.toList().forEach { f ->
            key(f.id) {
                val p = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    p.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
                    bump(if (f.fromMe) partnerBump else myBump)
                    if (!f.fromMe) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    flights.remove(f)
                }
                val half = with(density) { 22.dp.toPx() }
                val hop = with(density) { 26.dp.toPx() }
                Box(
                    Modifier
                        .offset {
                            val a = myPos(); val b = partnerPos()
                            val t = if (f.fromMe) p.value else 1f - p.value
                            val pt = threadPoint(a, b, t, sag(a, b), 0f, 0f)
                            IntOffset((pt.x - half).roundToInt(), (pt.y - half - hop * sin(PI.toFloat() * p.value)).roundToInt())
                        }
                        .size(44.dp)
                        .graphicsLayer {
                            val s = 0.7f + 0.6f * sin(PI.toFloat() * p.value)
                            scaleX = s; scaleY = s
                            alpha = if (p.value > 0.85f) (1f - p.value) / 0.15f else 1f
                        },
                    contentAlignment = Alignment.Center,
                ) { Text(f.emoji, fontSize = 28.sp) }
            }
        }

        Text(
            if (linked) "Mantén el ❤ para mandar latidos · Lleva tu burbuja a la suya para abrazar"
            else "Tu hilo espera a alguien",
            style = MaterialTheme.typography.labelMedium,
            color = c.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

/** Una burbuja con la inicial, centrada en [center]. */
@Composable
private fun Orb(name: String, brush: Brush, fade: Float, scale: Float, center: () -> Offset) {
    val half = with(LocalDensity.current) { 38.dp.toPx() }
    Box(
        Modifier
            .offset { val p = center(); IntOffset((p.x - half).roundToInt(), (p.y - half).roundToInt()) }
            .size(76.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = fade }
            .clip(CircleShape)
            .background(brush)
            // Una esfera: sombra hacia abajo a la derecha y un brillo arriba a la izquierda
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)),
                        center = Offset(size.width * 0.38f, size.height * 0.34f), radius = size.width * 0.78f,
                    ),
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0f)),
                        center = Offset(size.width * 0.34f, size.height * 0.26f), radius = size.width * 0.26f,
                    ),
                    radius = size.width * 0.26f, center = Offset(size.width * 0.34f, size.height * 0.26f),
                )
            }
            .border(2.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.25f))), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).uppercase().ifEmpty { "♥" }, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold,
            style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.4f), Offset(0f, 3f), 6f)),
        )
    }
}

/** Texto corto centrado horizontalmente en [anchor] (su borde superior queda en anchor.y). */
@Composable
private fun Label(text: String, sizeSp: Int, color: Color, anchor: () -> Offset) {
    val half = with(LocalDensity.current) { 60.dp.toPx() }
    Box(
        Modifier
            .offset { val p = anchor(); IntOffset((p.x - half).roundToInt(), p.y.roundToInt()) }
            .width(120.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text, color = color, fontSize = sizeSp.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

/**
 * Punto del hilo en [t] (0 = tu burbuja, 1 = la suya): cuelga [sag] px en el centro y ondula
 * [wave] px a lo largo, más en el medio que en las puntas, como una cuerda de guitarra.
 */
internal fun threadPoint(a: Offset, b: Offset, t: Float, sag: Float, wave: Float, phase: Float): Offset {
    val dir = b - a
    val len = dir.getDistance().coerceAtLeast(1f)
    val normal = Offset(-dir.y / len, dir.x / len)
    val bell = sin(PI.toFloat() * t)
    val ripple = wave * bell * sin(PI.toFloat() * 3f * t + phase)
    return a + dir * t + Offset(0f, sag * 4f * t * (1f - t)) + normal * ripple
}

/** Un corazón con volumen: luz arriba a la izquierda, rojo profundo abajo, un brillo y su sombra. */
internal fun DrawScope.drawHeart(center: Offset, size: Float, color: Color, alpha: Float = 1f) {
    val a = alpha.coerceIn(0f, 1f)
    if (a <= 0f) return
    val s = size / 2f
    val x = center.x
    val y = center.y
    val path = Path().apply {
        moveTo(x, y + s * 0.9f)
        cubicTo(x - s * 1.7f, y - s * 0.05f, x - s * 0.75f, y - s * 1.35f, x, y - s * 0.45f)
        cubicTo(x + s * 0.75f, y - s * 1.35f, x + s * 1.7f, y - s * 0.05f, x, y + s * 0.9f)
        close()
    }
    translate(top = s * 0.22f) { drawPath(path, Color.Black, alpha = 0.2f * a) }
    drawPath(
        path,
        Brush.radialGradient(
            listOf(lerp(color, Color.White, 0.55f), color, lerp(color, Color.Black, 0.5f)),
            center = Offset(x - s * 0.45f, y - s * 0.6f), radius = s * 2.1f,
        ),
        alpha = a,
    )
    drawOval(Color.White, topLeft = Offset(x - s * 0.88f, y - s * 0.82f), size = Size(s * 0.48f, s * 0.28f), alpha = 0.6f * a)
}

/**
 * El hilo como un cordón de verdad: sombra en el fondo, borde oscuro, hebras torcidas que corren
 * y un brillo arriba. [phase] mueve las hebras (0..2π da una vuelta sin saltos).
 */
private fun DrawScope.drawCord(path: Path, color: Color, glowAlpha: Float, glowWidth: Float, phase: Float) {
    val round = StrokeCap.Round
    translate(top = 9.dp.toPx()) {
        drawPath(path, Color.Black.copy(alpha = 0.07f), style = Stroke(12.dp.toPx(), cap = round))
        drawPath(path, Color.Black.copy(alpha = 0.12f), style = Stroke(4.dp.toPx(), cap = round))
    }
    drawPath(path, THREAD_GLOW.copy(alpha = glowAlpha), style = Stroke(glowWidth, cap = round))
    drawPath(path, lerp(color, Color.Black, 0.5f), style = Stroke(5.5.dp.toPx(), cap = round))
    drawPath(path, color, style = Stroke(4.dp.toPx(), cap = round))
    val period = 8.dp.toPx()
    drawPath(
        path, lerp(color, Color.Black, 0.28f),
        style = Stroke(
            4.dp.toPx(), cap = StrokeCap.Butt,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(period * 0.35f, period * 0.65f), -(phase / (2f * PI.toFloat())) * period * 2f),
        ),
    )
    translate(top = -1.dp.toPx()) {
        drawPath(path, Color.White.copy(alpha = 0.55f), style = Stroke(1.2.dp.toPx(), cap = round))
    }
}
