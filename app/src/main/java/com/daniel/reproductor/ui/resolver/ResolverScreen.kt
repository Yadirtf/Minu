package com.daniel.reproductor.ui.resolver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage

/** Pantalla de prueba de la Fase 2: pegar un link y ver título + URL del audio. */
@Composable
fun ResolverScreen(
    modifier: Modifier = Modifier,
    vm: ResolverViewModel = viewModel(factory = ResolverViewModel.Factory),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var link by rememberSaveable { mutableStateOf("") }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Probar resolver de YouTube", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = link,
            onValueChange = { link = it },
            label = { Text("Link de YouTube") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { vm.resolve(link) },
            enabled = state !is ResolverState.Loading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Resolver") }

        when (val s = state) {
            ResolverState.Idle -> Unit
            ResolverState.Loading -> CircularProgressIndicator()
            is ResolverState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
            is ResolverState.Success -> VideoDetails(s.video)
        }
    }
}

@Composable
private fun VideoDetails(video: ResolvedVideo) {
    val clipboard = LocalClipboardManager.current
    video.thumbnailUrl?.let {
        AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
    }
    Text(video.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text("${video.uploader} · ${formatDuration(video.durationSec)}")
    HorizontalDivider()
    Text("Mejor audio: ${video.best.format} · ${video.best.bitrateKbps} kbps", fontWeight = FontWeight.Bold)
    SelectionContainer {
        Text(video.best.url, style = MaterialTheme.typography.bodySmall, maxLines = 6)
    }
    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(video.best.url)) }) {
        Text("Copiar URL del audio")
    }
    HorizontalDivider()
    Text("Todas las pistas de audio (${video.all.size}):")
    video.all.sortedByDescending { it.bitrateKbps }.forEach {
        Row {
            Text(it.format, Modifier.width(80.dp))
            Spacer(Modifier.width(8.dp))
            Text("${it.bitrateKbps} kbps")
        }
    }
}

private fun formatDuration(sec: Long): String = "%d:%02d".format(sec / 60, sec % 60)
