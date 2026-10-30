package com.daniel.reproductor.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daniel.reproductor.ui.components.ArtworkImage
import com.daniel.reproductor.ui.components.SongRow
import com.daniel.reproductor.ui.theme.LocalAppStyle

/** Franja bajo el reproductor con la canción que sigue. Al tocarla se abre la cola completa. */
@Composable
fun UpNextBar(state: PlayerState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val style = LocalAppStyle.current
    val c = MaterialTheme.colorScheme
    val next = state.upNext.firstOrNull()
    Surface(
        onClick = onOpen,
        shape = style.cardShape,
        color = c.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (next != null) {
                ArtworkImage(next.artwork, Modifier.size(40.dp))
            } else {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, null, Modifier.size(40.dp).padding(8.dp), tint = c.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(style.label("A continuación"), style = MaterialTheme.typography.labelMedium, color = c.primary)
                Text(
                    when {
                        next != null -> "${next.song.title} · ${next.song.uploader}"
                        state.radioOn -> "Buscando canciones parecidas…"
                        else -> "No hay más canciones en la cola"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            if (next == null && state.radioOn) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, "Ver la cola", tint = c.onSurfaceVariant)
            }
        }
    }
}

/** La cola completa: tocar una canción salta a ella, la X la quita. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(state: PlayerState, onDismiss: () -> Unit) {
    val style = LocalAppStyle.current
    val c = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerHigh,
        shape = style.cardShape,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            Text(style.label("A continuación"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
            Row(
                Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (state.radioOn) Icon(Icons.Default.Radio, null, Modifier.size(16.dp), tint = c.primary)
                Text(
                    when {
                        state.radioOn -> "Reproducción automática: se agregan canciones parecidas al acercarse el final"
                        state.upNext.isEmpty() -> "Es la última canción"
                        else -> "${state.upNext.size} ${if (state.upNext.size == 1) "canción" else "canciones"}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = c.onSurfaceVariant,
                )
            }
            NowPlayingRow(state)
            LazyColumn(Modifier.fillMaxWidth()) {
                items(state.upNext, key = { "${it.index}:${it.song.videoId}" }) { entry ->
                    SongRow(
                        song = entry.song,
                        thumbnail = entry.artwork,
                        isCurrent = false,
                        isPlaying = false,
                        onClick = { state.skipTo(entry) },
                        tag = if (entry.suggested) "Sugerida" else null,
                        trailing = {
                            IconButton(onClick = { state.removeFromQueue(entry) }) {
                                Icon(Icons.Default.Close, "Quitar de la cola", tint = c.onSurfaceVariant)
                            }
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun NowPlayingRow(state: PlayerState) {
    val song = state.currentSong() ?: return
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            LocalAppStyle.current.label("Sonando"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        SongRow(
            song = song,
            thumbnail = state.metadata.artworkUri,
            isCurrent = true,
            isPlaying = state.isPlaying,
            onClick = {},
            tag = if (state.suggested) "Sugerida" else null,
        )
        if (state.upNext.isNotEmpty()) {
            Text(
                LocalAppStyle.current.label("Siguientes"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
            )
        }
    }
}
