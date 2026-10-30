package com.daniel.reproductor.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.daniel.reproductor.data.AppSettings
import com.daniel.reproductor.data.MusicRepository
import com.daniel.reproductor.data.UserFacingException
import com.daniel.reproductor.data.download.DownloadStorage
import com.daniel.reproductor.data.remote.ExtractorUpdateChecker
import com.daniel.reproductor.ui.common.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ajustes: Wi-Fi, carpeta de descargas, respaldo JSON y aviso de versión nueva del extractor. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val repo: MusicRepository,
    private val settings: AppSettings,
    private val downloads: DownloadStorage,
    private val updateChecker: ExtractorUpdateChecker,
    private val contentResolver: ContentResolver,
) : ViewModel() {

    val wifiOnly: StateFlow<Boolean> = settings.wifiOnly
    fun setWifiOnly(value: Boolean) = settings.setWifiOnly(value)

    val autoplay: StateFlow<Boolean> = settings.autoplay
    fun setAutoplay(value: Boolean) = settings.setAutoplay(value)

    /** Carpeta elegida en texto legible ("Memoria interna › Music › Minu"), o null si es la memoria de la app. */
    val downloadFolder: StateFlow<String?> = settings.downloadTree
        .mapLatest { downloads.describeLocation() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Avance mientras se mueven las descargas a su carpeta. */
    val organizing: StateFlow<DownloadStorage.Progress?> = downloads.observeProgress()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun chooseDownloadFolder(tree: Uri) = viewModelScope.launch {
        val msg = try {
            downloads.useFolder(tree)
            "Las descargas se guardarán en una carpeta por álbum"
        } catch (e: SecurityException) {
            "Android no permitió usar esa carpeta. Elige otra."
        }
        _messages.send(msg)
    }

    fun useAppStorage() = viewModelScope.launch {
        downloads.useAppStorage()
        _messages.send("Las descargas vuelven a la memoria de la app")
    }

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** Versión nueva de NewPipe Extractor disponible en GitHub, o null. */
    private val _extractorUpdate = MutableStateFlow<String?>(null)
    val extractorUpdate: StateFlow<String?> = _extractorUpdate.asStateFlow()
    val extractorVersion: String get() = updateChecker.currentVersion

    init {
        viewModelScope.launch { _extractorUpdate.value = updateChecker.newerVersion() }
    }

    fun dismissExtractorUpdate() {
        _extractorUpdate.value?.let(updateChecker::dismiss)
        _extractorUpdate.value = null
    }

    fun exportTo(uri: Uri) = viewModelScope.launch {
        val msg = try {
            val json = repo.exportJson()
            withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
                    ?: error("No se pudo abrir el archivo")
            }
            "Álbumes exportados"
        } catch (e: Exception) {
            "No se pudo exportar: ${e.message}"
        }
        _messages.send(msg)
    }

    fun importFrom(uri: Uri) = viewModelScope.launch {
        val msg = try {
            val json = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                    ?: error("No se pudo abrir el archivo")
            }
            val n = repo.importJson(json)
            if (n == 1) "Se importó 1 playlist" else "Se importaron $n playlists"
        } catch (e: UserFacingException) {
            e.message.orEmpty()
        } catch (e: Exception) {
            "No se pudo importar: ${e.message}"
        }
        _messages.send(msg)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                SettingsViewModel(app.repository, app.settings, app.downloads, app.updateChecker, app.contentResolver)
            }
        }

        fun backupFileName(): String =
            "reproductor-playlists-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json"
    }
}
