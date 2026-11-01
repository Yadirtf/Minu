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
import com.daniel.reproductor.ui.together.minu.MinuCreature
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
}
