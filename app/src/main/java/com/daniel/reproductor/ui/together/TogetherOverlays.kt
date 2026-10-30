package com.daniel.reproductor.ui.together

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.reproductor.cloud.Dedication
import com.daniel.reproductor.cloud.HubEvent
import com.daniel.reproductor.cloud.ListenSession
import com.daniel.reproductor.ui.components.ArtworkImage
import com.daniel.reproductor.ui.components.PillButton
import com.daniel.reproductor.ui.components.glow
import com.daniel.reproductor.ui.theme.LocalAppStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Pantalla que aparece cuando tu pareja te dedica una canción. */
@Composable
fun IncomingDedicationOverlay(
    dedication: Dedication,
    playingNow: Boolean,
    onListen: () -> Unit,
    onLater: () -> Unit,
    onReact: (String) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val style = LocalAppStyle.current
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0.96f, 1.04f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "scale",
    )
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "spin",
    )
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f))
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        FloatingHearts()
        Surface(
            color = c.surfaceContainerHigh, shape = style.cardShape,
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("💌", fontSize = 36.sp)
                Text(
                    "${dedication.fromName} te dedicó una canción",
                    style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Box(contentAlignment = Alignment.Center) {
                    Box(Modifier.size(150.dp).glow(c.primary, 30.dp, 0.5f))
                    ArtworkImage(
                        dedication.song.thumbnailUrl,
                        Modifier.size(170.dp).scale(pulse).rotate(if (style.artShape == CircleShape) spin else 0f),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(dedication.song.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(dedication.song.uploader, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                if (dedication.message.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "“${dedication.message}”",
                        style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TogetherViewModel.EMOJIS.take(5).forEach { emoji ->
                        Text(emoji, fontSize = 26.sp, modifier = Modifier.clip(CircleShape).clickable { onReact(emoji) }.padding(6.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (playingNow) {
                    Text("Está sonando ahora 🎶", color = c.primary, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    PillButton("Cerrar", Icons.Default.Headphones, onLater, Modifier.fillMaxWidth())
                } else {
                    PillButton("Escuchar ahora", Icons.Default.PlayArrow, onListen, Modifier.fillMaxWidth())
                    TextButton(onClick = onLater) { Text("Después") }
                }
            }
        }
    }
}

/** Corazones que suben por la pantalla. */
@Composable
private fun FloatingHearts() {
    val hearts = remember { List(14) { Triple(Random.nextFloat(), Random.nextInt(0, 2400), Random.nextFloat() * 0.6f + 0.7f) } }
    val t by rememberInfiniteTransition(label = "hearts").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "t",
    )
    BoxWithConstraints(Modifier.fillMaxSize()) {
        hearts.forEach { (x, delayMs, size) ->
            val p = ((t * 4000 + delayMs) % 4000) / 4000f
            Text(
                "❤", color = Color(0xFFFF4D8D).copy(alpha = (1 - p) * 0.8f), fontSize = (18 * size).sp,
                modifier = Modifier.offset(x = maxWidth * x, y = maxHeight * (1 - p)),
            )
        }
    }
}

/**
 * Emojis que vuelan cuando alguien reacciona (en los dos celulares). Con [suppress] en true no se
 * muestran: en la pestaña Dúo las reacciones ya viajan por el hilo.
 */
@Composable
fun FloatingReactions(events: Flow<HubEvent>, suppress: () -> Boolean = { false }) {
    val items = remember { mutableStateListOf<Pair<Long, String>>() }
    LaunchedEffect(events) {
        events.collect { e -> if (e is HubEvent.LiveReaction && !suppress()) items += System.nanoTime() to e.emoji }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        items.toList().forEach { (id, emoji) ->
            key(id) {
                val progress = remember { Animatable(0f) }
                val x = remember { 0.2f + Random.nextFloat() * 0.6f }
                LaunchedEffect(Unit) {
                    launch { progress.animateTo(1f, tween(2200)) }.join()
                    items.removeAll { it.first == id }
                }
                val p = progress.value
                Text(
                    emoji, fontSize = 48.sp,
                    modifier = Modifier
                        .offset(x = maxWidth * x - 24.dp, y = maxHeight * (0.85f - 0.6f * p))
                        .scale(0.6f + p)
                        .alpha(1f - p * p),
                )
            }
        }
    }
}

/** Franja "Escuchando juntos" encima del mini reproductor. */
@Composable
fun ListenBanner(session: ListenSession, partnerName: String, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(c.primary, c.secondary)))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Headphones, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            when {
                session.active && session.iAmDj -> "Escuchando juntos con $partnerName · Tú controlas"
                session.active -> "Escuchando juntos · $partnerName controla"
                else -> "Esperando a $partnerName…"
            },
            color = Color.White, style = MaterialTheme.typography.labelLarge, maxLines = 1,
        )
    }
}
