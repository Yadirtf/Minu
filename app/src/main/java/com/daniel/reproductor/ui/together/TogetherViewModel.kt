package com.daniel.reproductor.ui.together

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.daniel.reproductor.cloud.Account
import com.daniel.reproductor.cloud.CloudApi
import com.daniel.reproductor.cloud.CloudSync
import com.daniel.reproductor.cloud.Dedication
import com.daniel.reproductor.cloud.ListenSession
import com.daniel.reproductor.cloud.PartnerHub
import com.daniel.reproductor.cloud.PartnerStatus
import com.daniel.reproductor.cloud.Push
import com.daniel.reproductor.cloud.Session
import com.daniel.reproductor.cloud.SyncStatus
import com.daniel.reproductor.data.UserFacingException
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.ui.common.app
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Cuenta en la nube y todo lo de "Juntos". */
class TogetherViewModel(
    private val session: Session,
    private val api: CloudApi,
    private val sync: CloudSync,
    private val hub: PartnerHub,
    private val onSignedIn: suspend () -> Unit = {},
    private val onSigningOut: suspend () -> Unit = {},
) : ViewModel() {

    val account: StateFlow<Account?> = session.account
    val syncStatus: StateFlow<SyncStatus> = session.status
    val partner: StateFlow<PartnerStatus?> = hub.partner
    val dedications: StateFlow<List<Dedication>> = hub.dedications
    val listen: StateFlow<ListenSession> = hub.listen
    val connected: StateFlow<Boolean> = hub.connected

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    var serverUrl: String
        get() = session.serverUrl
        set(value) { session.serverUrl = value }

    /** Ejecuta una acción mostrando "cargando" y el error en un mensaje. */
    private fun action(success: String? = null, block: suspend () -> Unit) = viewModelScope.launch {
        _busy.value = true
        try {
            block()
            success?.let { _messages.send(it) }
        } catch (e: UserFacingException) {
            _messages.send(e.message.orEmpty())
        } finally {
            _busy.value = false
        }
    }

    fun login(username: String, password: String) = action("¡Hola de nuevo! Sincronizando tu música…") {
        session.signIn(api.login(username.trim(), password))
        onSignedIn()
        sync.syncNow()
    }

    fun register(username: String, password: String, displayName: String, inviteCode: String) =
        action("Cuenta creada. Tu música ya está en la nube ☁️") {
            session.signIn(api.register(username.trim(), password, displayName.trim(), inviteCode.trim()))
            onSignedIn()
            sync.syncNow()
        }

    /** Con el token que entrega "Continuar con Google". */
    fun googleLogin(idToken: String) = action("¡Listo! Sincronizando tu música…") {
        session.signIn(api.googleLogin(idToken))
        onSignedIn()
        sync.syncNow()
    }

    fun showMessage(text: String) = viewModelScope.launch { _messages.send(text) }

    fun syncNow() = action("Sincronizado") { sync.syncNow() }

    fun logout() = viewModelScope.launch {
        hub.leaveListen()
        onSigningOut() // este celular deja de recibir notificaciones
        session.signOut()
        _messages.send("Sesión cerrada. Tu música sigue en este celular.")
    }

    fun requestPartner(username: String) = action { hub.requestPartner(username) }
    fun acceptPartner() = action("¡Ahora están vinculados! ❤️") { hub.acceptPartner() }
    fun unlinkPartner() = action { hub.unlinkPartner() }

    fun sendDedication(song: Song, message: String, autoplay: Boolean) = action {
        val delivered = hub.sendDedication(song, message, autoplay)
        _messages.send(
            when {
                delivered && autoplay -> "Está sonando en su celular ahora mismo 💘"
                delivered -> "Dedicatoria enviada 💌"
                else -> "Enviada. La verá cuando abra Minu 💌"
            }
        )
    }

    fun react(dedication: Dedication, emoji: String) = hub.react(dedication, emoji)
    fun play(dedication: Dedication) = hub.playDedication(dedication)
    fun markSeen(dedication: Dedication) = hub.markSeen(dedication)
    fun sendLiveReaction(emoji: String) = hub.sendLiveReaction(emoji)
    fun onSongSaved(song: Song) = hub.notifySaved(song)

    fun inviteListen() = hub.inviteListen()
    fun acceptListen() = hub.acceptListen()
    fun leaveListen() = hub.leaveListen()
    fun takeControl() = hub.takeControl()
    fun refresh() = hub.refresh()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val minu = app
                TogetherViewModel(
                    minu.session, minu.cloudApi, minu.cloudSync, minu.partnerHub,
                    onSignedIn = { Push.register(minu) },
                    onSigningOut = { Push.unregister(minu) },
                )
            }
        }

        val EMOJIS = listOf("❤️", "😍", "🔥", "😘", "💃", "🎶")
    }
}
