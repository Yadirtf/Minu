package com.daniel.reproductor.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daniel.reproductor.data.db.Song

/**
 * "Guardar en playlist" para la canción que suena. Lo usan el reproductor completo y la escucha
 * en pareja: la pantalla solo sabe si ya está guardada y qué hacer al tocar.
 */
@Immutable
data class SaveAction(val saved: Boolean, val onClick: () -> Unit)

fun saveIcon(saved: Boolean): ImageVector = if (saved) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline
fun saveLabel(saved: Boolean): String = if (saved) "Guardada · agregar a otra playlist" else "Agregar a playlist"

/** Ícono + / ✓ que cambia con una pequeña animación al guardar. */
@Composable
fun SaveToPlaylistIcon(
    saved: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
    savedTint: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 26.dp,
) {
    AnimatedContent(
        targetState = saved,
        transitionSpec = { (scaleIn(initialScale = 0.5f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.5f) + fadeOut()) },
        label = "save",
        modifier = modifier,
    ) { isSaved ->
        Icon(saveIcon(isSaved), saveLabel(isSaved), tint = if (isSaved) savedTint else tint, modifier = Modifier.size(size))
    }
}

@Composable
fun SaveToPlaylistButton(
    action: SaveAction,
    tint: Color,
    modifier: Modifier = Modifier,
    savedTint: Color = MaterialTheme.colorScheme.primary,
) {
    IconButton(onClick = action.onClick, modifier = modifier) {
        SaveToPlaylistIcon(action.saved, tint, savedTint = savedTint)
    }
}

/** Qué canción se está guardando ahora (null = la hoja está cerrada). */
@Stable
class SaveToPlaylistState {
    var song by mutableStateOf<Song?>(null)
        private set

    fun open(song: Song?) { this.song = song }
    fun close() { song = null }
}

@Composable
fun rememberSaveToPlaylistState() = remember { SaveToPlaylistState() }

/** Muestra la hoja de playlists cuando [state] tiene una canción. */
@Composable
fun SaveToPlaylistHost(state: SaveToPlaylistState, lib: LibraryViewModel, onSaved: (Song) -> Unit = {}) {
    state.song?.let { PlaylistPickerSheet(it, lib, onDismiss = state::close, onSaved = onSaved) }
}
