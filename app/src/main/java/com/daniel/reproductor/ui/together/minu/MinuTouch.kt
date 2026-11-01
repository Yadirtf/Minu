package com.daniel.reproductor.ui.together.minu

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.reproductor.cloud.IncomingTouch
import com.daniel.reproductor.cloud.MinuPet
import com.daniel.reproductor.cloud.PetMood
import com.daniel.reproductor.cloud.TouchKind
import com.daniel.reproductor.ui.together.THREAD_RED
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * La barra del Toque: cuatro gestos que Minu lleva volando a tu pareja. Debajo, cómo va el último que
 * mandaste hoy (nunca una lista de los que quedaron sin respuesta).
 */
@Composable
fun MinuTouchBar(
    left: Int,
    lastSent: com.daniel.reproductor.cloud.SentTouch?,
    partnerName: String,
    onSend: (TouchKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TouchKind.entries.forEach { kind -> GestureButton(kind, enabled = left > 0) { onSend(kind) } }
        }
        val status = when {
            left == 0 -> "Hoy ya volé mucho. Mañana llevo más 💛"
            lastSent?.reply != null -> "$partnerName te devolvió ${lastSent.reply.emoji}"
            lastSent != null -> "Le llevé ${lastSent.kind.sentence} ${lastSent.kind.emoji}"
            else -> null
        }
        status?.let {
            Text(
                it, style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.Center,
            )
        }
    }
}

/** Botón redondo de un gesto: al tocarlo se aplasta y rebota. */
@Composable
internal fun GestureButton(kind: TouchKind, enabled: Boolean = true, size: Int = 54, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val press = remember { Animatable(1f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(if (enabled) 1f else 0.4f)) {
        Box(
            Modifier.size(size.dp).scale(press.value)
                .background(Brushes.gesture(c.surfaceContainerHighest), CircleShape)
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    scope.launch {
                        press.snapTo(0.82f)
                        press.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
                    }
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) { Text(kind.emoji, fontSize = (size * 0.46f).sp) }
        Text(kind.label, style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

private object Brushes {
    fun gesture(base: Color) = androidx.compose.ui.graphics.Brush.radialGradient(
        listOf(androidx.compose.ui.graphics.lerp(base, Color.White, 0.18f), base),
    )
}

/**
 * Minu sale volando con tu gesto: se agacha, salta y se va en arco hacia arriba, cada vez más pequeño.
 * Dura poco más de un segundo y no bloquea nada al terminar.
 */
@Composable
fun MinuTouchFlight(pet: MinuPet, kind: TouchKind, partnerName: String, onEnd: () -> Unit) {
    val progress = remember { Animatable(0f) }
    val end by rememberUpdatedState(onEnd)
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(1700, easing = LinearEasing))
        end()
    }
    val p = progress.value
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        // Primero se prepara en el piso (0..0.25) y luego vuela en arco
        val fly = ((p - 0.25f) / 0.75f).coerceIn(0f, 1f)
        val ease = FastOutSlowInEasing.transform(fly)
        val x = w * (0.5f + 0.55f * ease) - 60.dp
        val y = h * (0.62f - 0.75f * ease) - 60.dp - (sin(fly * PI.toFloat()) * 60f).dp
        val scale = 1f - 0.55f * ease
        Box(Modifier.offset(x, y).size(120.dp).graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = 18f * ease }) {
            MinuCreature(pet.species, PetMood.RADIANT, pet.stage, Modifier.fillMaxSize(), hop = if (p < 0.25f) p / 0.25f * 0.5f else 0f)
            Text(kind.emoji, fontSize = 34.sp, modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp))
        }
        if (p < 0.85f) {
            Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp), shadowElevation = 6.dp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = h * 0.18f).alpha((1f - p * 1.1f).coerceIn(0f, 1f)),
            ) {
                Text(
                    MinuVoice.touchSent(kind, partnerName), style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/**
 * Llega un toque: Minu baja volando con el gesto, aterriza con un rebote y te pide devolverlo.
 * Devolver es lo que alimenta a Minu; "Más tarde" lo deja para luego sin culpas.
 */
@Composable
fun MinuTouchArrival(pet: MinuPet, touch: IncomingTouch, onReply: (TouchKind) -> Unit, onLater: () -> Unit) {
    val arrive = remember(touch.id) { Animatable(0f) }
    var celebrate by remember { mutableIntStateOf(0) }
    LaunchedEffect(touch.id) {
        arrive.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 120f))
        celebrate++
    }
    val a = arrive.value
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f * a.coerceIn(0f, 1f)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
    ) {
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.offset(x = ((1f - a) * 160).dp, y = ((1f - a) * -420).dp)) {
                MinuCreature(pet.species, PetMood.RADIANT, pet.stage, Modifier.size(170.dp), carrying = false, celebrate = celebrate)
                Text(
                    touch.kind.emoji, fontSize = 46.sp,
                    modifier = Modifier.align(Alignment.TopEnd).graphicsLayer { rotationZ = -12f + 10f * sin(a * 6f) },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                MinuVoice.touchArrived(touch.kind, touch.fromName), color = Color.White,
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                modifier = Modifier.alpha(a.coerceIn(0f, 1f)),
            )
            Spacer(Modifier.height(20.dp))
            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(MinuVoice.TOUCH_REPLY_HINT, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TouchKind.entries.forEach { kind -> GestureButton(kind, size = 56) { onReply(kind) } }
                    }
                }
            }
            TextButton(onClick = onLater, modifier = Modifier.navigationBarsPadding()) { Text("Más tarde", color = Color.White) }
        }
    }
}

/**
 * Los dos gestos se encuentran sobre Minu: cada uno entra por un lado, chocan y estallan corazones
 * mientras Minu salta de alegría. Se cierra solo.
 */
@Composable
fun MinuTouchMeet(pet: MinuPet, mine: TouchKind, theirs: TouchKind, line: String, onEnd: () -> Unit) {
    val meet = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    var celebrate by remember { mutableIntStateOf(0) }
    val end by rememberUpdatedState(onEnd)
    LaunchedEffect(Unit) {
        meet.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        celebrate++
        launch { burst.animateTo(1f, tween(1100)) }
        delay(2400)
        end()
    }
    val m = meet.value
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { end() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(260.dp, 200.dp), contentAlignment = Alignment.BottomCenter) {
                MinuCreature(pet.species, PetMood.RADIANT, pet.stage, Modifier.size(150.dp), celebrate = celebrate)
                // Corazones del estallido
                if (burst.value > 0f) {
                    for (i in 0 until 8) {
                        val angle = i * (2 * PI / 8).toFloat()
                        val dist = 30f + 90f * burst.value
                        Text(
                            "💗", fontSize = 18.sp,
                            modifier = Modifier.align(Alignment.TopCenter)
                                .offset(x = (kotlin.math.cos(angle) * dist).dp, y = (40f + sin(angle) * dist * 0.7f).dp)
                                .alpha(1f - burst.value),
                        )
                    }
                }
                Text(theirs.emoji, fontSize = 40.sp, modifier = Modifier.align(Alignment.TopCenter).offset(x = (-110 * (1f - m) - 18).dp, y = 20.dp))
                Text(mine.emoji, fontSize = 40.sp, modifier = Modifier.align(Alignment.TopCenter).offset(x = (110 * (1f - m) + 18).dp, y = 20.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                line, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp),
            )
            Text("+energía para ${pet.name}", color = THREAD_RED.copy(alpha = 0.95f), style = MaterialTheme.typography.labelLarge)
        }
    }
}
