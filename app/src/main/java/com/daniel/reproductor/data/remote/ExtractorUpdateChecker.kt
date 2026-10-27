package com.daniel.reproductor.data.remote

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Revisa (máximo una vez al día) si hay una versión nueva de NewPipe Extractor en GitHub.
 * Es lo primero que se rompe cuando YouTube cambia algo.
 */
class ExtractorUpdateChecker(
    context: Context,
    private val client: OkHttpClient,
    val currentVersion: String,
) {
    private val prefs = context.getSharedPreferences("extractor_update", Context.MODE_PRIVATE)

    /** Devuelve la versión nueva si existe y el usuario no la ha descartado. */
    suspend fun newerVersion(): String? {
        val now = System.currentTimeMillis()
        val latest = if (now - prefs.getLong(KEY_CHECKED_AT, 0) < DAY_MS) {
            prefs.getString(KEY_LATEST, null)
        } else {
            fetchLatest()?.also { prefs.edit { putString(KEY_LATEST, it); putLong(KEY_CHECKED_AT, now) } }
        } ?: return null

        return latest.takeIf { isNewer(it, currentVersion) && it != prefs.getString(KEY_DISMISSED, null) }
    }

    fun dismiss(version: String) = prefs.edit { putString(KEY_DISMISSED, version) }

    private suspend fun fetchLatest(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) null else JSONObject(r.body.string()).optString("tag_name").ifBlank { null }
            }
        }.getOrNull()
    }

    companion object {
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/TeamNewPipe/NewPipeExtractor/releases/latest"
        private const val KEY_CHECKED_AT = "checked_at"
        private const val KEY_LATEST = "latest"
        private const val KEY_DISMISSED = "dismissed"
        private const val DAY_MS = 24 * 60 * 60 * 1000L

        /** Compara versiones tipo "v0.26.5". */
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.removePrefix("v").split('.', '-').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate)
            val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
