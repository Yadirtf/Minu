package com.daniel.reproductor.ui.together.minu

import com.daniel.reproductor.cloud.MinuPet
import com.daniel.reproductor.cloud.PetMood
import com.daniel.reproductor.cloud.PetSpecies
import com.daniel.reproductor.cloud.PetStage
import java.time.LocalDate

/**
 * La voz de Minu: frases cortas, en primera persona, tiernas y sin culpa. Habla de "ustedes" y nunca
 * señala a uno de los dos. Más adelante estas frases vendrán del banco del servidor; estas son la base
 * y el respaldo sin conexión.
 */
object MinuVoice {

    private val lines = mapOf(
        PetMood.RADIANT to listOf(
            "¡Estoy que brillo! Ustedes dos me llenan de energía ✨",
            "Hoy el hilo suena bonito. ¿Bailamos?",
            "Me encanta cuando se buscan así 💞",
            "Tengo tanta energía que podría tejer otro ovillo.",
        ),
        PetMood.HAPPY to listOf(
            "Hola, ¿qué tal su día?",
            "Estoy contento de verlos por aquí.",
            "Una canción para tu persona me haría ronronear.",
            "Me gusta este rincón de los dos.",
        ),
        PetMood.SLEEPY to listOf(
            "Ando un poco dormilón… ¿le mandamos algo bonito?",
            "Zzz… una dedicatoria me despertaría.",
            "Los extrañé un poquito. ¿Una canción juntos?",
        ),
        PetMood.MISSING to listOf(
            "Hoy los extrañé a los dos. Les guardé este recuerdo.",
            "Estaba mirando sus casetes… ¿se acuerdan de esa canción?",
            "Aquí sigo, cuidando el hilo. Cuando quieran, volvemos a tejer.",
        ),
    )

    /** La frase del día: cambia cada día pero no cada vez que abres la pantalla. */
    fun greeting(pet: MinuPet, today: LocalDate = LocalDate.now()): String {
        val options = lines.getValue(pet.mood)
        return options[(today.toEpochDay() % options.size).toInt()]
    }

    fun moodLabel(mood: PetMood) = when (mood) {
        PetMood.RADIANT -> "Radiante"
        PetMood.HAPPY -> "Contento"
        PetMood.SLEEPY -> "Somnoliento"
        PetMood.MISSING -> "Los extraña"
    }

    fun growth(pet: MinuPet): String {
        val next = pet.nextStageInMonths ?: return "Ya es ${pet.stage.label.lowercase()}: lo que construyeron se queda."
        val nextStage = PetStage.entries.getOrNull(pet.stage.ordinal + 1)?.label?.lowercase() ?: return ""
        return if (next == 1) "El próximo mes será $nextStage." else "En $next meses será $nextStage."
    }

    fun speciesBlurb(species: PetSpecies) = when (species) {
        PetSpecies.YARN -> "El hilo rojo hecho criatura: se teje más grande cuando están cerca."
        PetSpecies.CAT -> "Tierno e independiente: ronronea y se enrosca en el hilo."
        PetSpecies.BUNNY -> "Juguetón y curioso: salta de un celular al otro."
        PetSpecies.DOG -> "Leal y fiestero: mueve la cola y lleva sus dedicatorias en la boca."
    }

    // Momentos especiales (pantallas de racha, mesiversario, aniversario, dedicatorias)
    const val AT_RISK = "Yo sostengo el hilo mientras tanto. ¡Sálvenlo juntos!"
    const val BROKEN = "Aquí sigo. Lo bonito no se pierde: volvamos a tejer."
    const val SAVED = "¡Lo lograron! Sabía que podían 💞"
    const val MONTHSARY = "¡Un mes más juntos! Y yo crezco con ustedes."
    const val ANNIVERSARY = "Les guardé sus cartas todo el año."
    const val DELIVERY = "¡Te traje esto!"
    const val FAREWELL = "Gracias por dejarme acompañarlos. Sus recuerdos se quedan con cada uno."
}
