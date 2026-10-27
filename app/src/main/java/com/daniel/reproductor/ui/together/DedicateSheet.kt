package com.daniel.reproductor.ui.together

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.ui.components.ArtworkImage
import com.daniel.reproductor.ui.components.PillButton
import com.daniel.reproductor.ui.library.LibraryViewModel
import com.daniel.reproductor.ui.player.PlayerState
import com.daniel.reproductor.ui.theme.LocalAppStyle

/** Elegir canción, escribir un mensaje y (si está conectada) hacerla sonar en su celular. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DedicateSheet(
    lib: LibraryViewModel,
    player: PlayerState?,
    partnerOnline: Boolean,
    partnerName: String,
    onDismiss: () -> Unit,
    onSend: (Song, String, Boolean) -> Unit,
) {
    val library by lib.allSongs.collectAsStateWithLifecycle()
    val style = LocalAppStyle.current
    var query by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var autoplay by remember { mutableStateOf(partnerOnline) }

    // La canción que suena ahora va primero
    val current = player?.currentVideoId?.let { id ->
        library.orEmpty().firstOrNull { it.videoId == id } ?: Song(
            videoId = id,
            title = player.metadata.title?.toString().orEmpty(),
            uploader = player.metadata.artist?.toString().orEmpty(),
            durationSec = player.durationMs / 1000,
            thumbnailUrl = player.metadata.artworkUri?.toString()?.takeIf { it.startsWith("http") }.orEmpty(),
        )
    }
    var selected by remember { mutableStateOf(current) }
    val results = if (query.isBlank()) library.orEmpty().take(30) else lib.searchLocal(query)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = style.cardShape,
    ) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Text(style.label("Dedicar a $partnerName"), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            val chosen = selected
            if (chosen == null) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    placeholder = { Text("Busca en tus canciones") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true, shape = style.cardShape, modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(Modifier.heightIn(max = 380.dp).padding(top = 8.dp)) {
                    items(results, key = { it.videoId }) { song -> SongPick(song, lib.thumbnail(song)) { selected = song } }
                }
                if (library.isNullOrEmpty()) {
                    Text("Primero guarda canciones en tu biblioteca.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                SongPick(chosen, lib.thumbnail(chosen)) {}
                TextButton(onClick = { selected = null }) { Text("Elegir otra canción") }
                OutlinedTextField(
                    value = message, onValueChange = { message = it.take(500) },
                    label = { Text("Mensaje (opcional)") },
                    placeholder = { Text("Esta me recuerda a ti…") },
                    minLines = 2, shape = style.cardShape, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Que suene ya en su celular", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (partnerOnline) "$partnerName tiene Minu abierta: la escuchará al instante."
                            else "$partnerName no tiene Minu abierta: la verá en su buzón al abrirla.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = autoplay, onCheckedChange = { autoplay = it })
                }
                Spacer(Modifier.height(16.dp))
                PillButton("Enviar dedicatoria", Icons.Default.Send, { onSend(chosen, message.trim(), autoplay) }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SongPick(song: Song, thumbnail: Any, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkImage(thumbnail, Modifier.size(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            Text(song.uploader, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
