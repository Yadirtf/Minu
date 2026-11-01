package com.daniel.reproductor.ui.together.minu

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import com.daniel.reproductor.cloud.PetMood
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * El "cuerpo" de Minu: una pequeña simulación física que corre a la velocidad de la pantalla y decide
 * cómo se mueve en cada instante. Nada está en bucle fijo:
 *
 * - Resortes amortiguados para la cabeza, la mirada, las orejas y el aplastamiento, así cada movimiento
 *   acelera, se pasa un poquito y se asienta, como un cuerpo de verdad.
 * - Saltos con gravedad, anticipación (se agacha antes) y aplastamiento al caer.
 * - Movimiento secundario: las orejas y la cola reaccionan con retraso a lo que hace el cuerpo.
 * - Parpadeos a destiempo (a veces dobles) y una agenda de gestos espontáneos según el ánimo: mirar a un
 *   lado, ladear la cabeza, sacudir una oreja, bostezar, suspirar o dar saltitos de alegría.
 * - Responde al tacto: un toque lo hace brincar y reír; acariciarlo le cierra los ojos de gusto.
 *
 * Unidades: [lift] en fracciones del tamaño del lienzo; ángulos en grados; [gazeX]/[gazeY] de -1 a 1.
 */
@Stable
internal class MinuRig(private val random: Random = Random.Default) {
    /** Cambia en cada cuadro: el lienzo lo lee para volver a dibujarse. */
    var frame by mutableLongStateOf(0L)
        private set

    var mood: PetMood = PetMood.HAPPY

    // ---- Lo que lee el dibujo ----
    var t = 0f; private set
    val lift get() = liftY + hover
    val squash get() = squashS.x
    val breath get() = sin(t * 2f * PI.toFloat() / breathPeriod()) * breathDepth()
    val tilt get() = tiltS.x
    val gazeX get() = gazeXS.x
    val gazeY get() = gazeYS.x
    val ear get() = earS.x
    val wag get() = wagAmp.x * sin(t * 2f * PI.toFloat() * wagHz())
    val mouth get() = mouthS.x
    val joy get() = joyS.x
    val lid: Float
        get() = when {
            joy > 0.55f -> 0f // con los ojitos felices (^ ^) no se dibujan párpados
            else -> max(baseLid(), blinkLid())
        }
    /** Corazones que flotan cuando lo acarician: (x relativo -1..1, edad 0..1). */
    val hearts: List<Pair<Float, Float>> get() = heartList

    // ---- Estado interno ----
    private val squashS = Spring(stiffness = 340f, damping = 13f)
    private val tiltS = Spring(stiffness = 55f, damping = 9f)
    private val gazeXS = Spring(stiffness = 90f, damping = 14f)
    private val gazeYS = Spring(stiffness = 90f, damping = 14f)
    private val earS = Spring(stiffness = 150f, damping = 7f)
    private val wagAmp = Spring(stiffness = 20f, damping = 8f)
    private val mouthS = Spring(stiffness = 60f, damping = 12f)
    private val joyS = Spring(stiffness = 30f, damping = 10f)

    private var liftY = 0f
    private var liftV = 0f
    private var airborne = false
    private var crouchLeft = 0f
    private var pendingHop = 0f
    private var hopsQueued = 0

    private var tiltTarget = 0f
    private var gazeTarget = 0f to 0f
    private var mouthTarget = 0f
    private var joyTarget = 0f
    private var wagBoost = 0f
    private var behaviorLeft = 0f
    private var nextIdle = 1.2f
    private var nextBlink = 1.5f
    private var blinkAge = -1f
    private var doubleBlink = false
    private var dozeCycle = 0f
    private var petting = false
    private var nextHeart = 0f
    private val heartList = mutableListOf<Pair<Float, Float>>()
    private var hover = 0f

    /** Avanza la simulación [dt] segundos (se limita para que una pausa no lo haga saltar). */
    fun step(dt: Float) {
        val h = min(dt, 1f / 30f)
        t += h
        idle(h)
        blink(h)
        physics(h)
        hearts(h)
        frame++
    }

    // ---------- Acciones desde fuera ----------

    /** Un toque: brinca, se ríe y te mira. */
    fun poke() {
        hop(1.15f)
        mouthTarget = 0.7f
        gazeTarget = 0f to -0.1f
        behaviorLeft = 0.5f
        earS.kick(-260f)
    }

    /** Lo están acariciando (x de -1 a 1 sobre su cuerpo): se inclina hacia la mano y cierra los ojos. */
    fun pet(x: Float) {
        petting = true
        joyTarget = 1f
        tiltTarget = (x * 14f).coerceIn(-14f, 14f)
        gazeTarget = 0f to 0f
        wagBoost = 1f
        behaviorLeft = 0.6f
    }

    fun petEnd() {
        petting = false
        joyTarget = 0f
        wagBoost = 0f
        tiltTarget = 0f
    }

    /** Mira hacia un punto (-1..1). */
    fun lookAt(x: Float, y: Float) {
        gazeTarget = x.coerceIn(-1f, 1f) to y.coerceIn(-1f, 1f)
        behaviorLeft = 1.2f
    }

    /** Saltitos de celebración. */
    fun celebrate(times: Int = 3) {
        hopsQueued = times - 1
        hop(1.25f)
        mouthTarget = 0.6f
        wagBoost = 1f
        behaviorLeft = 1.4f
    }

    fun hop(strength: Float = 1f) {
        if (airborne || crouchLeft > 0f) return
        crouchLeft = 0.09f // se agacha antes de saltar
        pendingHop = strength
    }

    // ---------- Simulación ----------

    private fun physics(h: Float) {
        // Salto: anticipación, vuelo con gravedad y aterrizaje que aplasta
        var squashTarget = 0f
        if (crouchLeft > 0f) {
            crouchLeft -= h
            squashTarget = 0.16f
            if (crouchLeft <= 0f) {
                liftV = 1.05f * pendingHop
                airborne = true
            }
        }
        if (airborne) {
            liftV -= GRAVITY * h
            liftY += liftV * h
            squashTarget = -(liftV * 0.07f).coerceIn(-0.06f, 0.09f) // se estira al subir
            if (liftY <= 0f) {
                liftY = 0f
                airborne = false
                squashS.kick(abs(liftV) * 3.2f)
                liftV = 0f
                if (hopsQueued > 0) {
                    hopsQueued--
                    hop(1.1f)
                }
            }
        }
        // Flotar suave cuando está contento (sin saltar)
        val hoverTarget = if (mood == PetMood.HAPPY && !airborne) (sin(t * 2.4f) + 1f) * 0.012f else 0f
        hover += (hoverTarget - hover) * min(1f, h * 6f)

        squashS.step(squashTarget, h)
        tiltS.step(tiltTarget + if (mood == PetMood.MISSING) 6f else 0f, h)
        gazeXS.step(gazeTarget.first, h)
        gazeYS.step(gazeTarget.second + if (mood == PetMood.MISSING) 0.55f else 0f, h)
        // Orejas: siguen al cuerpo con retraso (al subir se van hacia atrás, al caer rebotan)
        earS.step(-liftV * 22f + tiltS.x * 0.4f + baseEar(), h)
        wagAmp.step(baseWag() + wagBoost * 18f, h)
        mouthS.step(mouthTarget, h)
        joyS.step(joyTarget, h)
    }

    private fun idle(h: Float) {
        if (behaviorLeft > 0f) {
            behaviorLeft -= h
            if (behaviorLeft <= 0f && !petting) {
                // Vuelve a su postura de descanso
                tiltTarget = 0f
                gazeTarget = 0f to 0f
                mouthTarget = 0f
                wagBoost = 0f
            }
        }
        nextIdle -= h
        if (nextIdle > 0f || petting || behaviorLeft > 0f) return
        nextIdle = when (mood) {
            PetMood.RADIANT -> 1.0f + random.nextFloat() * 1.6f
            PetMood.HAPPY -> 2.2f + random.nextFloat() * 3f
            PetMood.SLEEPY -> 4f + random.nextFloat() * 4f
            PetMood.MISSING -> 3.5f + random.nextFloat() * 3.5f
        }
        val roll = random.nextFloat()
        when (mood) {
            PetMood.RADIANT -> when {
                roll < 0.45f -> { hopsQueued = random.nextInt(0, 2); hop(1f + random.nextFloat() * 0.3f) }
                roll < 0.65f -> look()
                roll < 0.8f -> { tiltTarget = sign() * 12f; behaviorLeft = 0.9f }
                else -> { wagBoost = 1f; earS.kick(sign() * 300f); behaviorLeft = 1f }
            }
            PetMood.HAPPY -> when {
                roll < 0.35f -> look()
                roll < 0.55f -> { tiltTarget = sign() * (8f + random.nextFloat() * 8f); behaviorLeft = 1.2f }
                roll < 0.72f -> { earS.kick(sign() * 320f); behaviorLeft = 0.4f }
                roll < 0.9f -> hop(0.8f)
                else -> yawn()
            }
            PetMood.SLEEPY -> when {
                roll < 0.4f -> yawn()
                roll < 0.7f -> { tiltTarget = sign() * 10f; behaviorLeft = 2f } // cabeceo
                else -> look()
            }
            PetMood.MISSING -> when {
                roll < 0.5f -> { gazeTarget = sign() * 0.3f to 0.2f; tiltTarget = sign() * 6f; behaviorLeft = 1.6f } // suspira
                roll < 0.8f -> look()
                else -> { earS.kick(sign() * 140f); behaviorLeft = 0.5f }
            }
        }
    }

    private fun look() {
        gazeTarget = (random.nextFloat() * 2f - 1f) to (random.nextFloat() * 0.8f - 0.4f)
        tiltTarget = gazeTarget.first * 6f
        behaviorLeft = 0.8f + random.nextFloat() * 0.9f
    }

    private fun yawn() {
        mouthTarget = 1f
        tiltTarget = sign() * 5f
        behaviorLeft = 1.3f
    }

    private fun blink(h: Float) {
        if (blinkAge >= 0f) {
            blinkAge += h
            if (blinkAge > BLINK_TIME) {
                blinkAge = if (doubleBlink) 0f.also { doubleBlink = false } else -1f
            }
            return
        }
        nextBlink -= h
        if (nextBlink <= 0f) {
            blinkAge = 0f
            doubleBlink = random.nextFloat() < 0.18f
            nextBlink = 2f + random.nextFloat() * 3.5f
        }
        dozeCycle += h
    }

    /** Párpado de un parpadeo: cierra rápido (70 ms), espera, abre más lento. */
    private fun blinkLid(): Float {
        if (blinkAge < 0f) return 0f
        return when {
            blinkAge < 0.07f -> blinkAge / 0.07f
            blinkAge < 0.11f -> 1f
            else -> 1f - (blinkAge - 0.11f) / (BLINK_TIME - 0.11f)
        }.coerceIn(0f, 1f)
    }

    /** Párpado de base según el ánimo: dormilón se le cierran solos y a ratos se despierta. */
    private fun baseLid(): Float = when (mood) {
        PetMood.SLEEPY -> 0.62f + 0.38f * ((sin(t * 0.7f) + 1f) / 2f)
        PetMood.MISSING -> 0.28f
        else -> 0f
    }

    private fun hearts(h: Float) {
        for (i in heartList.indices.reversed()) {
            val (x, age) = heartList[i]
            val next = age + h / 1.4f
            if (next >= 1f) heartList.removeAt(i) else heartList[i] = x to next
        }
        if (joy > 0.5f) {
            nextHeart -= h
            if (nextHeart <= 0f && heartList.size < 6) {
                heartList += (random.nextFloat() * 1.4f - 0.7f) to 0f
                nextHeart = 0.32f + random.nextFloat() * 0.2f
            }
        }
    }

    private fun breathPeriod() = when (mood) {
        PetMood.RADIANT -> 1.6f
        PetMood.HAPPY -> 2.6f
        PetMood.SLEEPY -> 4.2f
        PetMood.MISSING -> 3.6f
    }

    private fun breathDepth() = when (mood) {
        PetMood.SLEEPY -> 0.045f
        else -> 0.022f
    }

    private fun baseEar() = when (mood) {
        PetMood.SLEEPY, PetMood.MISSING -> 14f
        else -> 0f
    }

    private fun baseWag() = when (mood) {
        PetMood.RADIANT -> 24f
        PetMood.HAPPY -> 14f
        PetMood.SLEEPY -> 3f
        PetMood.MISSING -> 2f
    } + joy * 14f

    private fun wagHz() = when (mood) {
        PetMood.RADIANT -> 2.6f
        PetMood.HAPPY -> 1.6f
        PetMood.SLEEPY -> 0.4f
        PetMood.MISSING -> 0.35f
    } + joy * 1.6f

    private fun sign() = if (random.nextBoolean()) 1f else -1f

    private companion object {
        const val GRAVITY = 5.2f
        const val BLINK_TIME = 0.23f
    }
}

/** Resorte amortiguado (masa 1): se mueve hacia su meta, se pasa un poco y se asienta. */
internal class Spring(private val stiffness: Float, private val damping: Float) {
    var x = 0f; private set
    private var v = 0f

    fun step(target: Float, h: Float) {
        v += (stiffness * (target - x) - damping * v) * h
        x += v * h
    }

    /** Un empujón de velocidad (un golpe, una sacudida). */
    fun kick(velocity: Float) { v += velocity }
}
