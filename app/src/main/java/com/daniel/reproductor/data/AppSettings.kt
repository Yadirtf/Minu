package com.daniel.reproductor.data

import android.content.Context
import androidx.core.content.edit
import com.daniel.reproductor.ui.theme.StyleId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _wifiOnly = MutableStateFlow(prefs.getBoolean(KEY_WIFI_ONLY, false))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly.asStateFlow()

    private val _style = MutableStateFlow(
        runCatching { StyleId.valueOf(prefs.getString(KEY_STYLE, null)!!) }.getOrDefault(StyleId.NEON)
    )
    val style: StateFlow<StyleId> = _style.asStateFlow()

    fun setWifiOnly(value: Boolean) {
        prefs.edit { putBoolean(KEY_WIFI_ONLY, value) }
        _wifiOnly.value = value
    }

    fun setStyle(value: StyleId) {
        prefs.edit { putString(KEY_STYLE, value.name) }
        _style.value = value
    }

    private companion object {
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_STYLE = "style"
    }
}
