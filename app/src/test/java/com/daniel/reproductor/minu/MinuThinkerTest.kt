package com.daniel.reproductor.minu

import com.daniel.reproductor.cloud.MindKind
import com.daniel.reproductor.cloud.MindOffer
import com.daniel.reproductor.cloud.MindTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Cuándo redacta Minu y cuándo se queda con su banco (sin celular ni servidor reales). */
class MinuThinkerTest {

    private class FakeMind(var state: MindReadiness, val reply: String? = "Me acordé de ustedes y me dio un saltito el corazón.") : MinuMind {
        var writes = 0
        var prepares = 0
        override suspend fun readiness() = state
        override suspend fun prepare(): Boolean { prepares++; return true }
        override suspend fun write(prompt: String): String? { writes++; return reply }
    }

    private val task = MindTask("2027-02-28", MindKind.CHEER, "Eres Minu…\nMensaje:")

    private fun thinker(mind: MinuMind, offers: MutableList<String>, wifi: Boolean = true) =
        MinuThinker(mind, CoroutineScope(Dispatchers.Unconfined), { _, text -> offers += text; MindOffer(true, null, null) }, { wifi })

    @Test
    fun redactaYOfreceCuandoEstaListo() = runBlocking {
        val offers = mutableListOf<String>()
        val mind = FakeMind(MindReadiness.READY)
        thinker(mind, offers).think(task)
        assertEquals(listOf("Me acordé de ustedes y me dio un saltito el corazón."), offers)
    }

    @Test
    fun sinNanoNoRedactaNiOfrece() = runBlocking {
        val offers = mutableListOf<String>()
        val mind = FakeMind(MindReadiness.UNAVAILABLE)
        assertNull(thinker(mind, offers).think(task))
        assertEquals(0, mind.writes)
        assertEquals(emptyList<String>(), offers)
    }

    @Test
    fun descargaSoloConWifi() = runBlocking {
        val offers = mutableListOf<String>()
        val mind = FakeMind(MindReadiness.DOWNLOADABLE)
        thinker(mind, offers, wifi = false).think(task)
        assertEquals(0, mind.prepares)
        thinker(mind, offers, wifi = true).think(task)
        assertEquals(1, mind.prepares)
        assertEquals(0, mind.writes)
    }

    @Test
    fun maximoTresIntentosPorDia() = runBlocking {
        val offers = mutableListOf<String>()
        val mind = FakeMind(MindReadiness.READY, reply = null) // nunca logra redactar
        val t = thinker(mind, offers)
        repeat(5) { t.think(task) }
        assertEquals(MinuThinker.MAX_TRIES_PER_DAY, mind.writes)
        t.think(task.copy(day = "2027-03-01")) // otro día, otra oportunidad
        assertEquals(MinuThinker.MAX_TRIES_PER_DAY + 1, mind.writes)
    }

    @Test
    fun respaldoNuncaRedacta() = runBlocking {
        assertEquals(MindReadiness.UNAVAILABLE, BankMind.readiness())
        assertNull(BankMind.write("hola"))
    }
}
