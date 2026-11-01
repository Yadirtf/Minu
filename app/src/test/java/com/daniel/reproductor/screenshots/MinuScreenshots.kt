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
import com.daniel.reproductor.cloud.Adventure
import com.daniel.reproductor.cloud.AdventurePlan
import com.daniel.reproductor.cloud.Compliment
import com.daniel.reproductor.cloud.ComplimentBox
import com.daniel.reproductor.cloud.Dedication
import com.daniel.reproductor.cloud.LoveOption
import com.daniel.reproductor.cloud.LoveQuestion
import com.daniel.reproductor.cloud.LoveState
import com.daniel.reproductor.cloud.MinuNovelty
import com.daniel.reproductor.cloud.PlanKind
import com.daniel.reproductor.data.db.Song
import com.daniel.reproductor.ui.together.minu.AdventureActions
import com.daniel.reproductor.ui.together.minu.ComplimentActions
import com.daniel.reproductor.ui.together.minu.GiftBox
import com.daniel.reproductor.ui.together.minu.MinuAdventureContent
import com.daniel.reproductor.ui.together.minu.MinuComplimentsContent
import com.daniel.reproductor.ui.together.minu.MinuLoveQuizContent
import com.daniel.reproductor.ui.together.minu.MinuRouletteContent
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.daniel.reproductor.cloud.MindKind
import com.daniel.reproductor.cloud.MindLine
import com.daniel.reproductor.cloud.MinuMindInfo
import com.daniel.reproductor.ui.together.minu.LearningConstellation
import com.daniel.reproductor.ui.together.minu.MindHouseCard
import com.daniel.reproductor.ui.together.minu.MindLineCard
import com.daniel.reproductor.ui.together.minu.MindThinkingCard
import com.daniel.reproductor.ui.together.minu.MinuWakeContent
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

    // ---------- Entrega 4: novedad ----------

    private val plans = listOf(
        AdventurePlan("a003", PlanKind.HOME, "🍝", "Cocinen algo que nunca han probado", "Elijan una receta de otro país y cocínenla juntos, con su música de fondo.", 2),
        AdventurePlan("a104", PlanKind.OUT, "🌅", "Atardecer con audífonos", "Busquen un lugar alto, compartan audífonos y miren caer el sol.", 1),
        AdventurePlan("a207", PlanKind.CREATE, "🎨", "Retrátense sin mirar", "Dibújense el uno al otro sin ver el papel. Prohibido reírse (imposible).", 1),
    )
    private fun adventure(myVote: String? = null, partnerVote: String? = null, chosen: AdventurePlan? = null, done: Boolean = false, rating: Int? = null) =
        Adventure("2026-11-09", plans, myVote, partnerVote, chosen, true, done, if (done) now else null, null, false, null, rating, null)
    private val noAdventure = AdventureActions({}, {}, {}, { _, _ -> }, { _, _ -> }, {})
    private val songs = listOf(
        Song("v1", "Coldplay - Yellow", "Coldplay", 266, ""),
        Song("v2", "Bad Bunny - Ojitos Lindos", "Bad Bunny", 258, ""),
    )
    private val novelty = MinuNovelty(
        adventure(partnerVote = "a104"),
        ComplimentBox(listOf(Compliment("c1", "Cómo te ríes de mis chistes malos aunque sean malísimos.", "Ana", "2026-11-12", false, false)), "2026-11-20", 2, emptyList(), false),
        LoveState(true, true, "Hoy dile algo concreto que admiras de Ana: las palabras cuentan."),
    )

    /** La tarjeta con la fila de novedad y la Aventura en sus tres momentos: votar, elegida y vivida. */
    @Test
    @Config(qualifiers = "w1680dp-h1450dp-hdpi")
    fun adventureScreens() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ReproductorTheme {
                Row(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        MinuEntry(MinuInfo(MinuTerms(1, true, true, false), pet, daily, trust, novelty), "Ana", {}, {})
                    }
                    Column(Modifier.width(400.dp).size(width = 400.dp, height = 1400.dp)) {
                        MinuAdventureContent(pet, adventure(myVote = "a003", partnerVote = "a104"), "Ana", songs, null, noAdventure)
                    }
                    Column(Modifier.width(400.dp).size(width = 400.dp, height = 1400.dp)) {
                        MinuAdventureContent(pet, adventure("a104", "a104", plans[1]), "Ana", songs, null, noAdventure)
                    }
                    Column(Modifier.width(400.dp).size(width = 400.dp, height = 1400.dp)) {
                        MinuAdventureContent(pet, adventure("a104", "a104", plans[1], done = true, rating = 4), "Ana", songs, null, noAdventure)
                    }
                }
            }
        }
        listOf(400L, 2500L).forEach { ms ->
            compose.mainClock.advanceTimeBy(ms)
            compose.onRoot().captureRoboImage(File("build/screenshots/minu/aventura_${ms}ms.png").path)
        }
    }

    /** La Ruleta de citas en reposo y después de girar (con la cita que salió). */
    @Test
    fun rouletteScreen() {
        compose.mainClock.autoAdvance = false
        val dedications = songs.mapIndexed { i, s -> Dedication("d$i", i % 2 == 0, "Ana", s, "Para ti, siempre", false, now - i * 86_400_000L, null, emptyList()) }
        compose.setContent {
            ReproductorTheme {
                Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).size(width = 420.dp, height = 1000.dp)) {
                    MinuRouletteContent(pet, "Ana", dedications, songs[0], partnerOnline = true, onPlay = { _, _ -> })
                }
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage(File("build/screenshots/minu/ruleta_reposo.png").path)
        compose.onNodeWithText("¡Girar!").performClick()
        compose.mainClock.advanceTimeBy(1500)
        compose.onRoot().captureRoboImage(File("build/screenshots/minu/ruleta_girando.png").path)
        compose.mainClock.advanceTimeBy(4000)
        compose.onRoot().captureRoboImage(File("build/screenshots/minu/ruleta_cita.png").path)
    }

    /** Cumplidos a ciegas (la cajita abriéndose, el bolsillo y escribir otros tres) y el test opcional. */
    @Test
    @Config(qualifiers = "w1300dp-h1350dp-hdpi")
    fun complimentsAndQuiz() {
        compose.mainClock.autoAdvance = false
        val quiz = listOf(
            LoveQuestion("q1", LoveOption("Que me digas lo que admiras de mí", "words"), LoveOption("Una tarde entera solo para los dos", "time")),
            LoveQuestion("q2", LoveOption("Un detalle pequeño que te recordó a mí", "gifts"), LoveOption("Un abrazo largo sin decir nada", "touch")),
        )
        compose.setContent {
            ReproductorTheme {
                Row(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.size(width = 400.dp, height = 1300.dp)) {
                        MinuComplimentsContent(pet, novelty.compliments, "Ana", ComplimentActions({}, {}, {}))
                    }
                    Column(Modifier.size(width = 400.dp, height = 1300.dp)) {
                        MinuComplimentsContent(
                            pet, ComplimentBox(emptyList(), null, 0, emptyList(), askNew = true), "Ana", ComplimentActions({}, {}, {}),
                        )
                    }
                    Column(Modifier.size(width = 400.dp, height = 1300.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        MinuLoveQuizContent(pet, "Ana", quiz, onFinish = {}, onDone = {})
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            GiftBox(0f, Modifier.size(110.dp))
                            GiftBox(0.5f, Modifier.size(110.dp))
                            GiftBox(1f, Modifier.size(110.dp))
                        }
                    }
                }
            }
        }
        listOf(400L, 2500L).forEach { ms ->
            compose.mainClock.advanceTimeBy(ms)
            compose.onRoot().captureRoboImage(File("build/screenshots/minu/cumplidos_${ms}ms.png").path)
        }
    }

    // ---------- Entrega 5: voz propia ----------

    private fun mind(awake: Boolean, learned: Int = 30, line: MindLine? = null, mineOn: Boolean = true, partnerOn: Boolean = true) = MinuMindInfo(
        awake = awake, wakesOn = "2026-12-01", daysLeft = if (awake) 0 else 30 - learned, learned = learned, learnTotal = 30,
        mineOn = mineOn, partnerOn = partnerOn, enabled = mineOn && partnerOn, celebrate = awake, line = line, writing = false,
    )

    /** La tarjeta con el mensaje del día, la cuenta regresiva, la casita y la celebración del despertar. */
    @Test
    @Config(qualifiers = "w1300dp-h1350dp-hdpi")
    fun mindScreens() {
        compose.mainClock.autoAdvance = false
        val cheer = MindLine("2026-12-01", MindKind.CHEER, "Me acordé de su atardecer con audífonos y todavía me dura el saltito en el corazón. ¡Qué bonito se ven juntos!")
        val dedication = MindLine("2026-12-02", MindKind.DEDICATION, "Su canción del inicio me tiene tarareando. ¿Quién se anima hoy a dedicar una que suene a ese primer día?")
        compose.setContent {
            ReproductorTheme {
                Row(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.width(400.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        MinuEntry(MinuInfo(MinuTerms(1, true, true, false), pet, daily, null, null, mind(true, line = cheer)), "Ana", {}, {})
                        MindLineCard(dedication, onDedicate = {}, animate = false)
                        MindThinkingCard("Minu")
                        MinuEntry(MinuInfo(MinuTerms(1, true, true, false), pet, daily, null, null, mind(false, learned = 18)), "Ana", {}, {})
                    }
                    Column(Modifier.width(400.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        MindHouseCard(mind(false, learned = 18), pet, "Ana", {}, {})
                        MindHouseCard(mind(true), pet, "Ana", {}, {})
                        MindHouseCard(mind(true, partnerOn = false), pet, "Ana", {}, {})
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            LearningConstellation(3, 30, 0f, Modifier.width(120.dp))
                            LearningConstellation(30, 30, 0.5f, Modifier.width(120.dp))
                            LearningConstellation(30, 30, 1f, Modifier.width(120.dp))
                        }
                    }
                    Column(Modifier.width(420.dp)) {
                        MinuWakeContent(pet, mind(true, line = cheer), "Ana", onDone = {})
                    }
                }
            }
        }
        listOf(600L, 3500L).forEach { ms ->
            compose.mainClock.advanceTimeBy(ms)
            compose.onRoot().captureRoboImage(File("build/screenshots/minu/voz_${ms}ms.png").path)
        }
    }
}
