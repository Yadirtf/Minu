package com.daniel.reproductor.ui.together

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daniel.reproductor.cloud.Capsule
import com.daniel.reproductor.cloud.CapsuleAnswer
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.ui.components.ArtworkImage
import com.daniel.reproductor.ui.components.PillButton
import com.daniel.reproductor.ui.library.LibraryViewModel
import com.daniel.reproductor.ui.theme.LocalAppStyle

private val PAPER = Color(0xFFFFF8EC)
private val INK = Color(0xFF2B1B2E)
private val INK_SOFT = Color(0xFF6A5A6E)
private val WAX = Color(0xFFB3202F)

/** Pantalla completa sobre la app, para la cápsula, los recuerdos y la cinta. */
@Composable
internal fun FullScreen(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
    }
}

@Composable
internal fun ScreenHeader(title: String, subtitle: String?, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
        Column(Modifier.weight(1f)) {
            Text(LocalAppStyle.current.label(title), style = MaterialTheme.typography.titleLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/**
 * Reto 2: la cápsula de tres canciones. Cada uno responde en secreto tres preguntas con una canción
 * y una frase; las respuestas quedan selladas hasta que responden los dos y entonces se abre.
 */
@Composable
fun CapsuleScreen(vm: TogetherViewModel, lib: LibraryViewModel, partnerName: String, onDismiss: () -> Unit) {
    val capsule by vm.capsule.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadCapsule() }
    FullScreen(onDismiss) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 16.dp)) {
            ScreenHeader("Cápsula de tres canciones", "Para salvar el hilo hoy", onDismiss)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            val c = capsule
            when {
                c == null -> Text(
                    "Cargando la cápsula… Si hoy no toca este reto, aquí no hay nada que responder.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp),
                )
                c.openedAt != null -> CapsuleReveal(c, partnerName, onPlay = { vm.playSong(it) })
                c.mine != null -> SealedWaiting(c, partnerName)
                else -> CapsuleForm(c, lib, partnerName) { vm.sendCapsule(it) }
            }
        }
    }
}

@Composable
private fun CapsuleForm(capsule: Capsule, lib: LibraryViewModel, partnerName: String, onSeal: (List<CapsuleAnswer>) -> Unit) {
    val songs = remember { mutableStateListOf<Song?>(null, null, null) }
    val notes = remember { mutableStateListOf("", "", "") }
    var picking by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Responde con una canción y una frase. $partnerName no verá nada hasta que los dos terminen; " +
                (if (capsule.partnerDone) "ya selló las suyas y te está esperando." else "puede estar respondiendo ahora mismo."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        capsule.prompts.forEachIndexed { i, prompt ->
            PaperCard {
                Text("${i + 1}. $prompt", color = INK, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(10.dp))
                val song = songs[i]
                if (picking == i) {
                    SongSearch(lib) { songs[i] = it; picking = null }
                } else if (song != null) {
                    SongPick(song, lib.thumbnail(song)) { picking = i }
                } else {
                    TextButton(onClick = { picking = i }) { Text("Elegir la canción") }
                }
                OutlinedTextField(
                    value = notes[i], onValueChange = { notes[i] = it.take(200) },
                    placeholder = { Text("Por qué esta…") }, minLines = 1,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic, color = INK),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        val ready = songs.all { it != null }
        PillButton(
            if (ready) "Sellar mis respuestas" else "Faltan ${songs.count { it == null }} canciones",
            Icons.Default.Lock,
            { if (ready) onSeal(songs.mapIndexed { i, s -> CapsuleAnswer(s!!, notes[i].trim()) }) },
            Modifier.fillMaxWidth(), primary = ready,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PaperCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PAPER)
            .border(1.dp, WAX.copy(alpha = 0.25f), RoundedCornerShape(14.dp)).padding(16.dp),
    ) { content() }
}

/** Ya sellaste: un sobre lacrado esperando la otra mitad. */
@Composable
private fun SealedWaiting(capsule: Capsule, partnerName: String) {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(120.dp).clip(RoundedCornerShape(60.dp))
                .background(Brush.radialGradient(listOf(Color(0xFFE0474F), WAX))),
            contentAlignment = Alignment.Center,
        ) { Text("M", color = Color.White.copy(alpha = 0.85f), fontSize = 54.sp, fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic) }
        Spacer(Modifier.height(24.dp))
        Text("Tus tres canciones están selladas", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            if (capsule.partnerDone) "Abriendo…" else "La cápsula se abrirá en los dos celulares cuando $partnerName responda las suyas.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
    }
}

/** La cápsula abierta: pregunta por pregunta, tu canción y la suya, apareciendo una tras otra. */
@Composable
internal fun CapsuleReveal(capsule: Capsule, partnerName: String, onPlay: (Song) -> Unit, animate: Boolean = true, scroll: Boolean = true) {
    Column(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        capsule.prompts.forEachIndexed { i, prompt ->
            val show = remember { Animatable(if (animate) 0f else 1f) }
            LaunchedEffect(Unit) { show.animateTo(1f, tween(600, delayMillis = 350 * i, easing = FastOutSlowInEasing)) }
            Column(Modifier.graphicsLayer { alpha = show.value; translationY = (1f - show.value) * 60f }) {
                PaperCard {
                    Text("${i + 1}. $prompt", color = INK, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    capsule.mine?.getOrNull(i)?.let { Answer("Tú", it, onPlay) }
                    capsule.partner?.getOrNull(i)?.let { Answer(partnerName, it, onPlay) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Answer(who: String, answer: CapsuleAnswer, onPlay: (Song) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).clickable { onPlay(answer.song) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(answer.song.thumbnailUrl, Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(who, color = WAX, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(answer.song.title, color = INK, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (answer.note.isNotBlank()) Text("“${answer.note}”", color = INK_SOFT, fontStyle = FontStyle.Italic, fontSize = 14.sp)
        }
        Icon(Icons.Default.PlayArrow, "Escuchar", tint = WAX)
    }
}
