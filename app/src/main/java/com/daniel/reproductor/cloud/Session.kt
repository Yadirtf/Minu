package com.daniel.reproductor.cloud

import android.content.Context
import androidx.core.content.edit
import com.daniel.reproductor.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Account(val username: String, val displayName: String, val token: String)

data class SyncStatus(
    val lastSyncAt: Long = 0,
    val error: String? = null,
    val running: Boolean = false,
)

/** Sesión de la cuenta en la nube, guardada en el almacenamiento privado de la app. */
class Session(context: Context) {
    private val prefs = context.getSharedPreferences("cloud", Context.MODE_PRIVATE)

    private val _account = MutableStateFlow(readAccount())
    val account: StateFlow<Account?> = _account.asStateFlow()

    private val _status = MutableStateFlow(SyncStatus(lastSyncAt = prefs.getLong(KEY_LAST_SYNC_AT, 0)))
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** URL del servidor en Render. Por defecto la de gradle.properties; se puede cambiar en la app. */
    var serverUrl: String
        get() = prefs.getString(KEY_SERVER, null)?.takeIf { it.isNotBlank() } ?: BuildConfig.SERVER_URL
        set(value) = prefs.edit { putString(KEY_SERVER, value.trim().trimEnd('/')) }

    /** updatedAt (del servidor) de la última copia que este celular subió o bajó. */
    var syncedServerVersion: Long
        get() = prefs.getLong(KEY_SERVER_VERSION, 0)
        set(value) = prefs.edit { putLong(KEY_SERVER_VERSION, value) }

    /** Hay cambios locales que todavía no se subieron. */
    var dirty: Boolean
        get() = prefs.getBoolean(KEY_DIRTY, false)
        set(value) = prefs.edit { putBoolean(KEY_DIRTY, value) }

    /** Primera sincronización después de iniciar sesión: se une en vez de reemplazar. */
    var needsMerge: Boolean
        get() = prefs.getBoolean(KEY_NEEDS_MERGE, false)
        set(value) = prefs.edit { putBoolean(KEY_NEEDS_MERGE, value) }

    fun signIn(account: Account) {
        prefs.edit {
            putString(KEY_USER, account.username)
            putString(KEY_NAME, account.displayName)
            putString(KEY_TOKEN, account.token)
            putLong(KEY_SERVER_VERSION, 0)
            putBoolean(KEY_NEEDS_MERGE, true)
        }
        _account.value = account
    }

    fun signOut() {
        prefs.edit {
            remove(KEY_USER); remove(KEY_NAME); remove(KEY_TOKEN)
            remove(KEY_SERVER_VERSION); remove(KEY_DIRTY); remove(KEY_NEEDS_MERGE); remove(KEY_LAST_SYNC_AT)
        }
        _account.value = null
        _status.value = SyncStatus()
    }

    fun setRunning(running: Boolean) {
        _status.value = _status.value.copy(running = running)
    }

    fun recordSync(error: String?) {
        val now = System.currentTimeMillis()
        if (error == null) prefs.edit { putLong(KEY_LAST_SYNC_AT, now) }
        _status.value = SyncStatus(
            lastSyncAt = if (error == null) now else _status.value.lastSyncAt,
            error = error,
            running = false,
        )
    }

    private fun readAccount(): Account? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        return Account(prefs.getString(KEY_USER, "").orEmpty(), prefs.getString(KEY_NAME, "").orEmpty(), token)
    }

    private companion object {
        const val KEY_SERVER = "server_url"
        const val KEY_USER = "username"
        const val KEY_NAME = "display_name"
        const val KEY_TOKEN = "token"
        const val KEY_SERVER_VERSION = "server_version"
        const val KEY_DIRTY = "dirty"
        const val KEY_NEEDS_MERGE = "needs_merge"
        const val KEY_LAST_SYNC_AT = "last_sync_at"
    }
}
