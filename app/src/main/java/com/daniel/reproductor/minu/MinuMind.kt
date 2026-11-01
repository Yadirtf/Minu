package com.daniel.reproductor.minu

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.withTimeoutOrNull

/** Si este celular puede redactar con la voz propia de Minu. */
enum class MindReadiness { UNAVAILABLE, DOWNLOADABLE, PREPARING, READY }

/**
 * Quien redacta los mensajes de Minu dentro del celular. Hay dos: el de Gemini Nano (AICore) y el de
 * respaldo, que nunca redacta y deja a Minu hablar con su banco de frases. El resto de la app no sabe cuál
 * le tocó: así un celular sin Nano funciona exactamente igual.
 */
interface MinuMind {
    suspend fun readiness(): MindReadiness

    /** Descarga lo que haga falta (AICore lo hace por su cuenta). true si quedó listo. */
    suspend fun prepare(): Boolean

    /** Redacta a partir del encargo; null si no se pudo (sin conexión con AICore, cuota, fondo...). */
    suspend fun write(prompt: String): String?
}

/** Respaldo: Minu habla con su banco de frases. */
object BankMind : MinuMind {
    override suspend fun readiness() = MindReadiness.UNAVAILABLE
    override suspend fun prepare() = false
    override suspend fun write(prompt: String): String? = null
}

/**
 * Gemini Nano por ML Kit GenAI (Prompt API). Solo funciona con la app en primer plano y tiene una cuota
 * por app: por eso se redacta una vez al día y cualquier error vuelve al banco sin hacer ruido.
 */
class NanoMind : MinuMind {
    private val model: GenerativeModel by lazy { Generation.getClient() }

    override suspend fun readiness(): MindReadiness = runCatching {
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> MindReadiness.READY
            FeatureStatus.DOWNLOADABLE -> MindReadiness.DOWNLOADABLE
            FeatureStatus.DOWNLOADING -> MindReadiness.PREPARING
            else -> MindReadiness.UNAVAILABLE
        }
    }.getOrDefault(MindReadiness.UNAVAILABLE)

    override suspend fun prepare(): Boolean = runCatching {
        var ready = false
        model.download().collect { status ->
            when (status) {
                is DownloadStatus.DownloadCompleted -> ready = true
                is DownloadStatus.DownloadFailed -> ready = false
                else -> Unit
            }
        }
        ready
    }.getOrDefault(false)

    override suspend fun write(prompt: String): String? = runCatching {
        withTimeoutOrNull(WRITE_TIMEOUT_MS) {
            model.generateContent(prompt).candidates.firstOrNull()?.text
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private companion object {
        const val WRITE_TIMEOUT_MS = 30_000L
    }
}

object MinuMinds {
    /** Nano si la librería carga en este celular; si no (o algo falla), el respaldo. */
    fun create(): MinuMind = runCatching<MinuMind> { NanoMind() }.getOrElse { BankMind }

    /** La descarga solo con Wi-Fi (o cualquier red sin datos medidos). */
    fun unmetered(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true
    }.getOrDefault(false)
}
