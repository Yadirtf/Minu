package com.daniel.reproductor.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.reproductor.cloud.PetMood
import com.daniel.reproductor.cloud.PetSpecies
import com.daniel.reproductor.cloud.PetStage
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import com.daniel.reproductor.cloud.DailyQuestion
import com.daniel.reproductor.cloud.GratitudeJar
import com.daniel.reproductor.cloud.MinuDaily
import com.daniel.reproductor.cloud.MinuInfo
import com.daniel.reproductor.cloud.MinuPet
import com.daniel.reproductor.cloud.MinuPromise
import com.daniel.reproductor.cloud.MinuTerms
import com.daniel.reproductor.cloud.MinuTrust
import com.daniel.reproductor.cloud.PromiseBoard
import com.daniel.reproductor.cloud.TouchState
import com.daniel.reproductor.cloud.Truce
import com.daniel.reproductor.cloud.TrucePhase
import com.daniel.reproductor.cloud.UnsaidBox
import com.daniel.reproductor.cloud.UnsaidLetter
import com.daniel.reproductor.ui.theme.ReproductorTheme
import com.daniel.reproductor.ui.together.minu.BreathingMinu
import com.daniel.reproductor.ui.together.minu.KnottedThread
import com.daniel.reproductor.ui.together.minu.LetterPaper
import com.daniel.reproductor.ui.together.minu.MinuCareContent
import com.daniel.reproductor.ui.together.minu.MinuCreature
import com.daniel.reproductor.ui.together.minu.MinuEntry
import com.daniel.reproductor.ui.together.minu.MinuVoice
import com.daniel.reproductor.ui.together.minu.SealedEnvelope
import com.daniel.reproductor.ui.together.minu.TruceCard
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Capturas de las cuatro mascotas en cada ánimo y etapa, y una tira de cuadros para revisar el
 * movimiento sin un teléfono. Ejecutar con: ./gradlew testDebugUnitTest -Pscreenshots --tests "*MinuScreenshots*"
 * Las imágenes quedan en app/build/screenshots/minu/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w800dp-h1000dp-xhdpi", application = android.app.Application::class)
class MinuScreenshots {

    @get:Rule val compose = createComposeRule()

    private val bg = Color(0xFF2A1B33)

    @Test
    fun speciesAndMoods() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Column(Modifier.background(bg).padding(12.dp)) {
                PetSpecies.entries.forEach { species ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PetMood.entries.forEach { mood ->
                            Column {
                                MinuCreature(species, mood, PetStage.YOUNG, Modifier.size(180.dp))
                                Text("${species.id} · ${mood.id}", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        listOf(400L, 1300L, 2600L).forEach { ms ->
            compose.mainClock.advanceTimeBy(ms)
            compose.onRoot().captureRoboImage(File("build/screenshots/minu/mascotas_${ms}ms.png").path)
        }
    }

    @Test
    fun stages() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Column(Modifier.background(bg).padding(12.dp)) {
                listOf(PetSpecies.YARN, PetSpecies.DOG).forEach { species ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PetStage.entries.forEach { stage ->
                            Column {
                                MinuCreature(species, PetMood.HAPPY, stage, Modifier.size(180.dp), carrying = stage == PetStage.WISE)
                                Text("${species.id} · ${stage.id}", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(700)
        compose.onRoot().captureRoboImage(File("build/screenshots/minu/etapas.png").path)
    }

    /** Tira de cuadros cada 80 ms de un perrito y un ovillo radiantes: deja ver saltos y aplastamiento. */
    @Test
    fun motionStrip() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Row(Modifier.background(bg).padding(8.dp)) {
                MinuCreature(PetSpecies.DOG, PetMood.RADIANT, PetStage.ADULT, Modifier.size(260.dp))
                MinuCreature(PetSpecies.BUNNY, PetMood.HAPPY, PetStage.ADULT, Modifier.size(260.dp))
                MinuCreature(PetSpecies.CAT, PetMood.SLEEPY, PetStage.ADULT, Modifier.size(260.dp))
            }
        }
        compose.mainClock.advanceTimeBy(500)
        repeat(24) { i ->
            compose.mainClock.advanceTimeBy(80)
            compose.onRoot().captureRoboImage(File("build/screenshots/minu/movimiento_${"%02d".format(i)}.png").path)
        }
    }

    // ---------- Entrega 3: confianza y reparación ----------

    private val pet = MinuPet(PetSpecies.CAT, "Minu", 72, PetMood.HAPPY, PetStage.YOUNG, 4, 8, null, null)
    private val now = System.currentTimeMillis()
    private val trust = MinuTrust(
        unsaid = UnsaidBox(listOf(UnsaidLetter("u1", "Ana", now, true, null, null, null, false, null)), emptyList()),
        truce = null,
        promises = PromiseBoard(
            kept = 5,
            items = listOf(
                MinuPromise("p1", "llamarte a las 8", false, "Ana", now + 3_600_000, kept = true, confirmed = false),
                MinuPromise("p2", "pasar por el pan", true, "Tú", now + 7_200_000, kept = false, confirmed = false),
            ),
        ),
    )
    private val daily = MinuDaily(
        TouchState(emptyList(), null, 10),
        DailyQuestion("2026-11-10", "l001", "¿Qué canción te recuerda a mí?", "light", null, true, null, false, null, null),
        GratitudeJar("2026-11", null, emptyList()),
        emptyList(),
    )

    /** La tarjeta de Minu con la fila de confianza, la Tregua en calma y el Buzón (carta sellada y abierta). */
    @Test
    fun trustScreens() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ReproductorTheme {
                Row(Modifier.background(MaterialTheme.colorScheme.background).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        MinuEntry(MinuInfo(MinuTerms(1, true, true, false), pet, daily, trust), "Ana", {}, {})
                        TruceCard(pet, Truce(false, "Ana", now - 300_000, now + 900_000, TrucePhase.CALM, false, null, null), "Ana") {}
                        TruceCard(pet, Truce(true, "Tú", now - 1_300_000, now - 100_000, TrucePhase.REPAIR, false, null, "Empecemos de nuevo"), "Ana") {}
                        KnottedThread(5, Modifier.fillMaxWidth().size(width = 380.dp, height = 56.dp))
                        Text(MinuVoice.promisesKept(5), color = MaterialTheme.colorScheme.onBackground)
                    }
                    Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SealedEnvelope(Modifier.size(160.dp, 112.dp))
                        LetterPaper("Ana", "un poco sola", "miras el celular en la cena", "que cenemos sin pantallas")
                        BreathingMinu(pet)
                    }
                }
            }
        }
        listOf(800L, 3000L).forEach { ms ->
            compose.mainClock.advanceTimeBy(ms)
            compose.onRoot().captureRoboImage(File("build/screenshots/minu/confianza_${ms}ms.png").path)
        }
    }

    /** La hoja de cuidado: Minu deja de jugar y muestra las líneas de ayuda. */
    @Test
    fun careScreen() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ReproductorTheme {
                Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).width(420.dp)) {
                    MinuCareContent(MinuVoice.HELP, onDismiss = {}, onProceed = {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage(File("build/screenshots/minu/cuidado.png").path)
    }
}
