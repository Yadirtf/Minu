package com.daniel.reproductor.data

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.daniel.reproductor.ui.theme.StyleId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _wifiOnly = MutableStateFlow(prefs.getBoolean(KEY_WIFI_ONLY, false))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    /** Al tocar una canción buscada en YouTube, seguir con su radio en vez de los demás resultados. */
    private val _autoplay = MutableStateFlow(prefs.getBoolean(KEY_AUTOPLAY, true))
    val autoplay: StateFlow<Boolean> = _autoplay.asStateFlow()

    private val _style = MutableStateFlow(
        runCatching { StyleId.valueOf(prefs.getString(KEY_STYLE, null)!!) }.getOrDefault(StyleId.NEON)
    )
    val style: StateFlow<StyleId> = _style.asStateFlow()

    /** Carpeta del dispositivo elegida para las descargas, o null para la memoria privada de la app. */
    private val _downloadTree = MutableStateFlow(prefs.getString(KEY_DOWNLOAD_TREE, null)?.let(Uri::parse))
    val downloadTree: StateFlow<Uri?> = _downloadTree.asStateFlow()

    fun setDownloadTree(value: Uri?) {
        prefs.edit { if (value == null) remove(KEY_DOWNLOAD_TREE) else putString(KEY_DOWNLOAD_TREE, value.toString()) }
        _downloadTree.value = value
    }

    fun setWifiOnly(value: Boolean) {
        prefs.edit { putBoolean(KEY_WIFI_ONLY, value) }
        _wifiOnly.value = value
    }

    fun setAutoplay(value: Boolean) {
        prefs.edit { putBoolean(KEY_AUTOPLAY, value) }
        _autoplay.value = value
    }

    fun setStyle(value: StyleId) {
        prefs.edit { putString(KEY_STYLE, value.name) }
        _style.value = value
    }

    private companion object {
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_STYLE = "style"
        const val KEY_AUTOPLAY = "autoplay"
        const val KEY_DOWNLOAD_TREE = "download_tree"
    }
}
