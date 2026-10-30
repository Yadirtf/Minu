package com.daniel.reproductor.ui.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.daniel.reproductor.ReproductorApp
import com.daniel.reproductor.data.AddResult
import com.daniel.reproductor.data.UserFacingException
import com.daniel.reproductor.data.YouTubeLinks
import com.daniel.reproductor.data.db.Playlist
import com.daniel.reproductor.ui.common.TextInputDialog
import com.daniel.reproductor.ui.theme.AppStyles
import com.daniel.reproductor.ui.theme.ReproductorTheme
import kotlinx.coroutines.launch

/** Recibe links desde el botón "Compartir" de YouTube y pregunta a qué playlist agregarlos. */
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        if (YouTubeLinks.extractVideoId(text) == null) {
            Toast.makeText(this, "No encontré un link de video de YouTube", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val style = AppStyles.byId((application as ReproductorApp).settings.style.value)
        setContent { ReproductorTheme(style) { ShareDialog(link = text) } }
    }

    @Composable
    private fun ShareDialog(link: String) {
        val repo = (application as ReproductorApp).repository
        val scope = rememberCoroutineScope()
        var playlists by remember { mutableStateOf<List<Playlist>?>(null) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var creating by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) { playlists = repo.getPlaylists() }

        fun addTo(playlistId: Long, playlistName: String) {
            busy = true
            scope.launch {
                try {
                    val msg = when (val r = repo.addToPlaylist(playlistId, link)) {
                        is AddResult.Added -> "Agregada a $playlistName: ${r.song.title}"
                        is AddResult.AlreadyInPlaylist -> "Ya estaba en $playlistName"
                    }
                    Toast.makeText(this@ShareActivity, msg, Toast.LENGTH_SHORT).show()
                    finish()
                } catch (e: UserFacingException) {
                    error = e.message
                    busy = false
                }
            }
        }

        if (creating) {
            TextInputDialog(
                title = "Nuevo álbum", label = "Nombre", confirmText = "Crear y agregar",
                onDismiss = { creating = false },
                onConfirm = { name ->
                    creating = false
                    busy = true
                    scope.launch { addTo(repo.createPlaylist(name), name.trim()) }
                },
            )
            return
        }

        AlertDialog(
            onDismissRequest = { if (!busy) finish() },
            title = { Text(if (busy) "Agregando…" else "¿A qué álbum la agrego?") },
            text = {
                when {
                    busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Spacer(Modifier.width(16.dp))
                        Text("Consultando YouTube…")
                    }
                    error != null -> Text(error.orEmpty())
                    else -> Column {
                        LazyColumn(Modifier.heightIn(max = 360.dp)) {
                            items(playlists.orEmpty(), key = { it.id }) { p ->
                                ListItem(
                                    headlineContent = { Text(p.name) },
                                    leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier.clickable { addTo(p.id, p.name) },
                                )
                            }
                        }
                        ListItem(
                            headlineContent = { Text("Nuevo álbum") },
                            leadingContent = { Icon(Icons.Default.Add, null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.padding(top = 4.dp).clickable { creating = true },
                        )
                    }
                }
            },
            confirmButton = {
                if (error != null) TextButton(onClick = { error = null }) { Text("Elegir otra") }
            },
            dismissButton = {
                if (!busy) TextButton(onClick = { finish() }) { Text("Cancelar") }
            },
        )
    }
}
