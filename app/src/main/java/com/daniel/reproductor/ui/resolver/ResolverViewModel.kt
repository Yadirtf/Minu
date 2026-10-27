package com.daniel.reproductor.ui.resolver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import com.daniel.reproductor.ReproductorApp
import com.daniel.reproductor.data.remote.YouTubeResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AudioOption(val format: String, val bitrateKbps: Int, val url: String)

data class ResolvedVideo(
    val title: String,
    val uploader: String,
    val durationSec: Long,
    val thumbnailUrl: String?,
    val best: AudioOption,
    val all: List<AudioOption>,
)

sealed interface ResolverState {
    data object Idle : ResolverState
    data object Loading : ResolverState
    data class Success(val video: ResolvedVideo) : ResolverState
    data class Error(val message: String) : ResolverState
}

class ResolverViewModel(private val resolver: YouTubeResolver) : ViewModel() {

    private val _state = MutableStateFlow<ResolverState>(ResolverState.Idle)
    val state: StateFlow<ResolverState> = _state.asStateFlow()

    fun resolve(link: String) {
        if (link.isBlank()) return
        _state.value = ResolverState.Loading
        viewModelScope.launch {
            _state.value = try {
                val info = resolver.getInfo(link.trim())
                val best = resolver.bestAudio(info)
                fun opt(s: org.schabi.newpipe.extractor.stream.AudioStream) =
                    AudioOption(s.format?.name ?: "?", s.averageBitrate, s.content)
                ResolverState.Success(
                    ResolvedVideo(
                        title = info.name,
                        uploader = info.uploaderName,
                        durationSec = info.duration,
                        thumbnailUrl = info.thumbnails.maxByOrNull { it.height }?.url,
                        best = opt(best),
                        all = info.audioStreams.map(::opt),
                    )
                )
            } catch (e: Exception) {
                ResolverState.Error(YouTubeResolver.describeError(e))
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ReproductorApp
                ResolverViewModel(app.resolver)
            }
        }
    }
}
