package com.daniel.reproductor.minu

import com.daniel.reproductor.cloud.DuoInfo
import com.daniel.reproductor.cloud.MindOffer
import com.daniel.reproductor.cloud.MindTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Decide cuándo redactar el mensaje del día: solo si hay encargo, este celular puede y aún no lo intentó
 * demasiadas veces hoy. Si Nano falta pero se puede descargar, lo prepara con Wi-Fi para mañana. Nunca
 * bloquea a nadie: si algo no sale, Minu sigue con su banco de frases.
 */
class MinuThinker(
    private val mind: MinuMind,
    private val scope: CoroutineScope,
    private val offer: suspend (MindTask, String) -> MindOffer,
    private val unmetered: () -> Boolean,
) {
    private val busy = Mutex()
    private var triesDay: String? = null
    private var tries = 0
    private var preparing: Job? = null

    /** Intenta redactar [task]; devuelve el nuevo estado de Dúo si el servidor respondió. */
    suspend fun think(task: MindTask): DuoInfo? {
        if (!busy.tryLock()) return null
        try {
            if (triesDay != task.day) { triesDay = task.day; tries = 0 }
            if (tries >= MAX_TRIES_PER_DAY) return null
            when (mind.readiness()) {
                MindReadiness.READY -> Unit
                MindReadiness.DOWNLOADABLE -> { prepareSoon(); return null }
                MindReadiness.PREPARING, MindReadiness.UNAVAILABLE -> return null
            }
            tries++
            val text = mind.write(task.prompt) ?: return null
            return runCatching { offer(task, text).duo }.getOrNull()
        } finally {
            busy.unlock()
        }
    }

    private fun prepareSoon() {
        if (preparing?.isActive == true || !unmetered()) return
        preparing = scope.launch { mind.prepare() }
    }

    companion object {
        /** Igual que el servidor: si ninguno de tres pasa las barandas, hoy habla el banco. */
        const val MAX_TRIES_PER_DAY = 3
    }
}
