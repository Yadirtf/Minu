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

    /** La cuenta regresiva mientras aprende de ustedes (nunca dice cómo). */
    fun learning(daysLeft: Int) = when {
        daysLeft <= 0 -> "Hoy ya los conozco un poquito."
        daysLeft == 1 -> "Mañana ya los conoceré mejor."
        daysLeft < 7 -> "En $daysLeft días ya los conoceré mejor. ¡Ya casi!"
        else -> "En $daysLeft días ya los conoceré mejor."
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

    // ---------- Conexión diaria ----------

    /** Respuestas entusiastas de respaldo (el servidor puede mandar otras). */
    val CHEERS = listOf(
        "¡Cuéntame todo! ¿Cómo pasó?",
        "¡Qué orgullo me das!",
        "¡Te lo mereces muchísimo!",
        "¿Y cómo te sentiste?",
        "¡Esto hay que celebrarlo!",
        "Sabía que lo ibas a lograr 💛",
    )

    /** Reacciones cortas a la respuesta de la pareja (más que un emoji). */
    val QUESTION_REACTIONS = listOf("Cuéntame más 💬", "No lo sabía, me encantó", "Me hiciste sonreír", "Yo también 🫶")

    fun levelLabel(level: String) = when (level) {
        "deep" -> "Profunda"
        "personal" -> "Personal"
        else -> "Ligera"
    }

    /** Lo que dice Minu sobre la pregunta según cómo va. */
    fun questionLine(q: com.daniel.reproductor.cloud.DailyQuestion, partnerName: String) = when {
        q.opened && q.myReaction == null -> "¡Se abrió el sobre! Reacciona a lo que contó $partnerName."
        q.opened -> "Me encanta lo que se cuentan. Mañana traigo otra."
        q.mine != null -> "Guardé tu respuesta en un sobre. Se abre cuando $partnerName responda."
        q.partnerDone -> "$partnerName ya respondió y su sobre te espera. ¿Y tú?"
        else -> "Respondan los dos y abro el sobre. Nadie ve nada antes."
    }

    fun touchSent(kind: com.daniel.reproductor.cloud.TouchKind, partnerName: String) = "Le llevo ${kind.sentence} a $partnerName"
    fun touchArrived(kind: com.daniel.reproductor.cloud.TouchKind, fromName: String) = "$fromName te manda ${kind.sentence}"
    fun touchMet(kind: com.daniel.reproductor.cloud.TouchKind, fromName: String) = "¡$fromName te devolvió ${kind.sentence}!"
    const val TOUCH_REPLY_HINT = "Devuélveselo y yo lo llevo:"
    const val TOUCH_MEET = "¡Se encontraron! Así me encanta 💞"

    fun jarLine(count: Int) = when (count) {
        0 -> "El frasco del mes está vacío. ¿Por qué das gracias hoy?"
        1 -> "Ya hay un papelito en el frasco."
        in 2..9 -> "Ya hay $count papelitos en el frasco de este mes."
        else -> "¡$count gracias este mes! El frasco casi no cierra."
    }

    const val NEWS_HINT = "Cuando a uno le pasa algo bueno, celebrarlo juntos lo hace más grande."
    fun newsCheerHint(name: String) = "$name te contó algo bueno. Pregúntale, celebra: eso vale más que un like."

    // Momentos especiales (pantallas de racha, mesiversario, aniversario, dedicatorias)
    const val AT_RISK = "Yo sostengo el hilo mientras tanto. ¡Sálvenlo juntos!"
    const val BROKEN = "Aquí sigo. Lo bonito no se pierde: volvamos a tejer."
    const val SAVED = "¡Lo lograron! Sabía que podían 💞"
    const val MONTHSARY = "¡Un mes más juntos! Y yo crezco con ustedes."
    const val ANNIVERSARY = "Les guardé sus cartas todo el año."
    const val DELIVERY = "¡Te traje esto!"
    const val FAREWELL = "Gracias por dejarme acompañarlos. Sus recuerdos se quedan con cada uno."

    // ---------- Confianza y reparación ----------

    /** Respaldo sin conexión de las líneas de ayuda (el servidor manda las vigentes). */
    val HELP = com.daniel.reproductor.cloud.HelpLines(
        country = "Colombia",
        lines = listOf(
            com.daniel.reproductor.cloud.HelpLine("Emergencias", "123", "Si estás en peligro ahora mismo."),
            com.daniel.reproductor.cloud.HelpLine("Línea 155", "155", "Orientación a mujeres víctimas de violencia. Gratis, 24 horas."),
            com.daniel.reproductor.cloud.HelpLine("Línea 192, opción 4", "192", "Apoyo en salud mental del Ministerio de Salud."),
            com.daniel.reproductor.cloud.HelpLine("Línea 106", "106", "Te escuchan si estás pasando un momento difícil (Bogotá)."),
        ),
        elsewhere = "Si estás en otro país, llama al número de emergencias local.",
    )
    const val CARE_TITLE = "Antes de seguir, quiero cuidarte"
    const val CARE_BODY = "Algo de lo que escribiste suena a que podrías estar pasando por algo difícil o peligroso. " +
        "No tienes que vivirlo solo: hay personas preparadas para escucharte, gratis y en reserva."
    const val CARE_PRIVATE = "Esto no se guarda y nadie más lo ve."

    val REPAIRS = listOf(
        "Tienes razón en una parte", "Empecemos de nuevo", "Te quiero aunque estemos molestos", "Perdón por cómo lo dije",
        "Lo que sientes me importa", "Somos un equipo", "¿Me das un abrazo?", "Quiero entenderte mejor",
    )
    val FEEL_STARTERS = listOf("triste", "sola / solo", "preocupado/a", "poco escuchado/a", "cansado/a", "inseguro/a")
    val NEED_STARTERS = listOf("que me abraces", "que hablemos con calma", "que me avises", "un rato para los dos", "que me escuches sin resolver")

    const val UNSAID_HINT = "Lo difícil también se puede decir con cariño. Yo lo llevo con cuidado y sin prisa."
    fun unsaidArrived(name: String) = "Traje algo importante de $name. Ábrelo cuando tengas un momento tranquilo."
    const val UNSAID_OPENED = "Gracias por leerlo con calma. Antes de responder, cuéntale que lo recibiste."
    fun unsaidThanked(name: String) = "$name agradeció que se lo contaras 💛"
    const val UNSAID_ON_WAY = "Lo llevé con cuidado"

    const val TRUCE_INVITE = "Cuando una conversación se calienta, el cuerpo necesita unos 20 minutos para calmarse. " +
        "Le aviso con cariño y no le cuento nada de lo que pasó."
    fun truceCalm(t: com.daniel.reproductor.cloud.Truce) =
        if (t.mine) "Respira conmigo. No hace falta resolver nada ahora." else "${t.byName} pidió una pausa. Yo cuido este silencio; respira conmigo."
    const val TRUCE_REPAIR = "¿Listos para reencontrarse? Elige una frase y yo se la llevo."
    fun truceWaiting(name: String) = "Le llevé tu frase. Cuando $name esté listo, elegirá la suya."
    const val TRUCE_TOGETHER = "Se reencontraron. Esto también es amor 💞"

    const val PROMISE_HINT = "La confianza se teje con cosas pequeñas cumplidas. Te lo recuerdo un ratito antes, solo a ti."
    fun promisesKept(count: Int) = when (count) {
        0 -> "Cada promesa cumplida es un nudito más en su hilo."
        1 -> "Ya tienen un nudito en su hilo: una promesa cumplida."
        else -> "$count promesas cumplidas, $count nuditos en su hilo."
    }
}
