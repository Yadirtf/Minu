import { HELP_LINES } from './care.js';
/**
 * Banco de contenido de Minu: preguntas del día, gestos del Toque y frases de respuesta entusiasta.
 * Vive en el servidor para poder crecer sin publicar otra APK. Cada pregunta tiene un id estable (no se
 * reutiliza nunca): así el motor sabe cuáles ya vio cada pareja aunque el texto se corrija.
 *
 * Niveles (de la ciencia de la cercanía de Aron: la intimidad crece al revelarse poco a poco):
 * - light: ligeras y divertidas, para cualquier día.
 * - personal: piden contar algo propio.
 * - deep: profundas; se desbloquean cuando Minu ya es joven (3 meses juntos en Minu).
 *
 * Reglas de redacción: español neutro, en segunda persona hacia la pareja o primera persona propia, sin
 * culpa, sin comparar a los dos y sin temas que obliguen a discutir.
 */
export const CONTENT_VERSION = 1;

export const LEVELS = ['light', 'personal', 'deep'];
/** Desde qué etapa de Minu aparece cada nivel. */
export const LEVEL_MIN_STAGE = { light: 'baby', personal: 'baby', deep: 'young' };
/** Qué tan seguido sale cada nivel cuando está disponible. */
export const LEVEL_WEIGHT = { light: 5, personal: 4, deep: 2 };

const light = [
  '¿Qué canción pondrías para empezar un domingo perfecto conmigo?',
  'Si fuéramos un dúo musical, ¿cómo nos llamaríamos?',
  '¿Cuál es la comida que siempre te pone de buen humor?',
  'Si pudiéramos teletransportarnos esta noche a cenar, ¿a dónde iríamos?',
  '¿Qué superpoder tonto te gustaría tener?',
  '¿Cuál es tu snack secreto que nadie más entiende?',
  'Si nuestra historia fuera una película, ¿qué género sería?',
  '¿Qué emoji usas más conmigo y por qué crees que es ese?',
  '¿Qué animal crees que sería yo y por qué?',
  '¿Cuál fue lo más chistoso que te pasó esta semana?',
  'Si tuvieras un día libre mañana, ¿qué harías primero?',
  '¿Qué serie verías otra vez desde cero conmigo?',
  '¿Prefieres playa al amanecer o montaña al atardecer?',
  '¿Qué palabra o frase digo mucho?',
  'Si pudieras aprender una habilidad en una noche, ¿cuál sería?',
  '¿Cuál es tu bebida favorita para un día lluvioso?',
  '¿Qué canción te sabes completa aunque te dé pena admitirlo?',
  'Si abriéramos un pequeño negocio juntos, ¿qué venderíamos?',
  '¿Cuál es tu sonido favorito?',
  '¿Qué olor te transporta a tu infancia?',
  'Si tuviéramos una mascota de verdad, ¿qué nombre le pondrías?',
  '¿Qué harías con una tarde entera sin celular?',
  '¿Cuál es el mejor regalo barato que has recibido?',
  '¿Qué plan sencillo te hace sentir muy feliz?',
  '¿Qué canción pondrías para limpiar la casa a todo volumen?',
  'Si pudieras cenar con cualquier personaje de ficción, ¿quién sería?',
  '¿Cuál es tu postre favorito de todos los tiempos?',
  '¿Qué lugar de tu ciudad te gustaría mostrarme?',
  '¿Qué es algo que siempre te hace reír, aunque lo hayas visto mil veces?',
  'Si tuvieras que comer un solo plato por un mes, ¿cuál sería?',
  '¿Qué estación del año va más contigo?',
  '¿Cuál es tu forma favorita de pasar una noche en casa?',
  '¿Qué invento te parece genial y poco valorado?',
  'Si escribieras un libro, ¿de qué trataría?',
  '¿Qué juego de mesa o videojuego te gustaría jugar conmigo?',
  '¿Qué es lo primero que haces al despertar?',
  'Si fuéramos de viaje en carretera, ¿qué no puede faltar?',
  '¿Cuál fue tu caricatura favorita de niño o niña?',
  '¿Qué color describe tu día de hoy?',
  '¿Qué harías si ganaras un premio pequeño, como para un antojo grande?',
  '¿Cuál es tu hora favorita del día?',
  '¿Qué tradición rara te gustaría que inventáramos?',
  '¿Qué canción te pone a bailar sin pensarlo?',
  'Si pudieras vivir una semana en otra época, ¿cuál elegirías?',
  '¿Qué app usas más y qué dice eso de ti?',
  '¿Qué es lo más espontáneo que has hecho?',
  '¿Cuál es tu frase de película favorita?',
  'Si fuéramos personajes de un videojuego, ¿qué poder tendría cada uno?',
  '¿Qué te gustaría cocinar juntos algún día?',
  '¿Qué apodo me pondrías que todavía no me has dicho?',
  '¿Qué cosa pequeña te alegró hoy?',
  '¿Cuál es el mejor concierto al que has ido o al que irías?',
  'Si pudiéramos pasar un día en cualquier ciudad del mundo, ¿cuál sería?',
  '¿Qué canción describe tu semana?',
  '¿Qué harías en una cita perfecta que cueste casi nada?',
  '¿Qué objeto tienes desde hace años y no piensas botar?',
  '¿Qué es algo que te encanta y casi nadie sabe?',
  '¿Cuál es tu manera favorita de recibir buenas noticias?',
  'Si tuvieras que describirme con tres canciones, ¿cuáles serían?',
  '¿Qué foto nuestra te gusta más y por qué?',
  '¿Qué te gustaría que hiciéramos este fin de semana?',
  '¿Qué palabra en otro idioma te encanta cómo suena?',
  '¿Cuál fue el último antojo que tuviste?',
  'Si fueras un sabor de helado, ¿cuál serías?',
  '¿Qué deporte te gustaría probar conmigo?',
  '¿Qué recuerdo de esta semana guardarías en un frasco?',
  '¿Qué canción pondrías en nuestra fiesta de los 80 años?',
  '¿Qué serie o película te recuerda a nosotros?',
  '¿Qué es lo más bonito que viste hoy?',
  '¿Qué plato tuyo me cocinarías para impresionarme?',
];

const personal = [
  '¿Qué es algo que te hizo sentir orgulloso u orgullosa este mes?',
  '¿En qué momento del día te acuerdas más de mí?',
  '¿Qué te gustaría que te preguntara más seguido?',
  '¿Qué recuerdo de tu infancia te sigue sacando una sonrisa?',
  '¿Qué fue lo primero que te llamó la atención de mí?',
  '¿Qué gesto mío te hace sentir querido o querida?',
  '¿Qué meta pequeña tienes para este mes?',
  '¿Qué te ayuda a calmarte cuando tienes un día pesado?',
  '¿Qué aprendiste de ti este año?',
  '¿Qué persona te inspira y por qué?',
  '¿Qué es algo que te gustaría hacer más seguido conmigo?',
  '¿Cuál es un sueño que tenías de niño o niña y todavía recuerdas?',
  '¿Qué día nuestro repetirías tal cual?',
  '¿Qué es algo que te da paz?',
  '¿Qué canción te recuerda una etapa importante de tu vida?',
  '¿Qué te gustaría que supiera de tu familia?',
  '¿Qué es algo que estás esperando con ilusión?',
  '¿Cómo te gusta que te cuiden cuando estás enfermo o enferma?',
  '¿Qué valoras más en una amistad?',
  '¿Qué fue lo mejor de tu semana?',
  '¿Qué te gustaría aprender este año?',
  '¿Qué palabra describe cómo te sientes con nosotros últimamente?',
  '¿Qué lugar te hace sentir en casa?',
  '¿Qué cosa sencilla puedo hacer mañana para alegrarte el día?',
  '¿Qué consejo te darías a ti hace cinco años?',
  '¿Qué te hace sentir más tú?',
  '¿Qué momento nuestro te gustaría volver a vivir?',
  '¿Qué te gustaría que celebráramos más?',
  '¿Qué hábito estás tratando de construir?',
  '¿Qué te emociona de nuestro futuro?',
  '¿Qué fue lo más amable que alguien hizo por ti recientemente?',
  '¿Qué te gustaría que hiciéramos cuando estemos lejos?',
  '¿Cómo sabes que tuviste un buen día?',
  '¿Qué es algo que te cuesta pedir?',
  '¿Qué historia de tu vida casi nunca cuentas?',
  '¿Qué te gustaría que recordáramos de este año?',
  '¿Qué te hace sentir valiente?',
  '¿Qué parte de tu rutina disfrutas más?',
  '¿Qué es algo mío que te hace reír?',
  '¿Qué te gustaría que hiciera cuando estás triste?',
  '¿Cuál es tu recuerdo favorito con tus amigos?',
  '¿Qué te gustaría que nuestro hogar tuviera algún día?',
  '¿Qué cosa nueva descubriste de mí hace poco?',
  '¿Qué te gustaría intentar aunque te dé un poco de miedo?',
  '¿Qué es lo que más disfrutas de hablar conmigo?',
  '¿Qué es algo que te gustaría agradecerle a tu yo de antes?',
  '¿Qué tipo de detalle te derrite?',
  '¿Qué te gustaría que hiciéramos en nuestro próximo aniversario?',
  '¿Qué música escuchabas cuando nos conocimos?',
  '¿Qué cosa de tu trabajo o estudio te gustaría que entendiera mejor?',
  '¿Qué te motiva a levantarte en la mañana?',
  '¿Qué te hace sentir escuchado o escuchada?',
  '¿Qué tradición de tu familia te gustaría mantener?',
  '¿Cuál fue un momento en que te sentiste muy cerca de mí?',
  '¿Qué te gustaría que te dijera más seguido?',
  '¿Qué lugar te gustaría que conociéramos juntos antes de fin de año?',
  '¿Qué cosa de ti te gusta más hoy que hace un año?',
  '¿Qué es algo que te preocupa y quieres soltar un poco?',
  '¿Qué pequeño ritual nuestro te encanta?',
  '¿Qué te gustaría que hiciéramos distinto esta semana para descansar mejor?',
];

const deep = [
  '¿Qué es algo que nunca te he preguntado y te gustaría contarme?',
  '¿Qué miedo tuyo te gustaría que entendiera mejor?',
  '¿En qué momento sentiste que podías confiar en mí de verdad?',
  '¿Qué significa para ti sentirte amado o amada?',
  '¿Qué heridas del pasado crees que todavía te cuidan de más?',
  '¿Qué te gustaría que construyéramos juntos en los próximos cinco años?',
  '¿Cómo te imaginas un día normal nuestro dentro de diez años?',
  '¿Qué aprendiste de las relaciones de tus papás o de quienes te criaron?',
  '¿Qué es lo que más te costó decirme alguna vez?',
  '¿Qué te gustaría que nunca cambiara entre nosotros?',
  '¿Qué parte de ti sientes que todavía no conozco?',
  '¿Qué necesitas de mí cuando discutimos?',
  '¿Qué es algo que admiras de cómo enfrento las cosas?',
  '¿Qué te gustaría sanar este año?',
  '¿Qué significa para ti un hogar?',
  '¿Qué momento difícil te hizo crecer más?',
  '¿De qué te sientes más agradecido o agradecida en la vida?',
  '¿Qué promesa te gustaría que nos hiciéramos?',
  '¿Qué te gustaría que la gente recordara de ti?',
  '¿Cuándo te has sentido más orgulloso u orgullosa de nosotros?',
  '¿Qué cosa te gustaría perdonarte?',
  '¿Qué te da miedo perder?',
  '¿Qué te gustaría que hiciéramos mejor como equipo?',
  '¿Qué sueño tuyo te gustaría que apoyara más?',
  '¿Cuál es una creencia tuya que cambió con los años?',
  '¿Qué te hace sentir seguro o segura conmigo?',
  '¿Cómo te gustaría que celebráramos los logros del otro?',
  '¿Qué es lo más valiente que has hecho por amor?',
  '¿Qué parte de tu historia te gustaría que conociera mejor?',
  '¿Qué es algo que te gustaría escuchar de mí cuando dudas de ti?',
  '¿Cómo quieres que cuidemos nuestro tiempo juntos cuando la vida se ponga ocupada?',
  '¿Qué cosa pequeña hago que significa mucho para ti?',
  '¿Qué te gustaría que nuestros amigos dijeran de nosotros?',
  '¿Qué lección te dejó la relación más importante antes de mí?',
  '¿Qué significa para ti la confianza?',
  '¿Qué te gustaría pedirme y no te has animado?',
  '¿Qué te hace sentir libre dentro de una relación?',
  '¿Qué momento nuestro te gustaría contarle a alguien dentro de muchos años?',
  '¿Qué te gustaría que hiciéramos cuando uno de los dos esté pasando un mal momento?',
  '¿Qué es lo que más esperas de nuestro futuro?',
];

const make = (prefix, level, list) => list.map((text, i) => ({ id: `${prefix}${String(i + 1).padStart(3, '0')}`, level, text }));

/** Todas las preguntas. Para agregar: sumar al final de su lista (nunca reordenar ni borrar). */
export const QUESTIONS = [...make('l', 'light', light), ...make('p', 'personal', personal), ...make('d', 'deep', deep)];

const STAGE_ORDER = ['baby', 'young', 'adult', 'wise'];
const reached = (stage, min) => STAGE_ORDER.indexOf(stage) >= STAGE_ORDER.indexOf(min);

/** Número pseudoaleatorio estable a partir de un texto: los dos ven lo mismo el mismo día. */
export function seeded(text) {
  let h = 2166136261;
  for (const ch of text) h = Math.imul(h ^ ch.codePointAt(0), 16777619) >>> 0;
  return () => {
    h = (Math.imul(h, 1103515245) + 12345) >>> 0;
    return h / 2 ** 32;
  };
}

/**
 * Motor de variedad (versión 1): elige una pregunta que la pareja no haya visto en los últimos 12 meses,
 * de un nivel permitido por la etapa de Minu, sorteando el nivel por peso. Si se acaban, vuelve a
 * usar las más antiguas. Determinista por [seed].
 */
export function pickQuestion({ stage = 'baby', usedIds = new Set(), seed }) {
  const rand = seeded(seed);
  const levels = LEVELS.filter((l) => reached(stage, LEVEL_MIN_STAGE[l]));
  const fresh = (level) => QUESTIONS.filter((q) => q.level === level && !usedIds.has(q.id));
  let candidates = levels.filter((l) => fresh(l).length);
  if (!candidates.length) {
    // Ya las vieron todas: se permite repetir, empezando por el nivel con más preguntas
    const all = QUESTIONS.filter((q) => levels.includes(q.level));
    return all[Math.floor(rand() * all.length)];
  }
  const total = candidates.reduce((sum, l) => sum + LEVEL_WEIGHT[l], 0);
  let roll = rand() * total;
  const level = candidates.find((l) => (roll -= LEVEL_WEIGHT[l]) < 0) ?? candidates[0];
  const pool = fresh(level);
  return pool[Math.floor(rand() * pool.length)];
}

/** Los cuatro gestos del Toque. */
export const TOUCH_KINDS = ['hug', 'kiss', 'thinking', 'miss'];

/**
 * Respuestas entusiastas sugeridas (respuesta activa-constructiva de Shelly Gable): preguntan, celebran y
 * piden detalles, en vez de un "qué bien" o un like. La app las ofrece como atajos.
 */
export const CHEERS = [
  '¡Cuéntame todo! ¿Cómo pasó?',
  '¡Qué orgullo me das!',
  '¡Te lo mereces muchísimo!',
  '¿Y cómo te sentiste?',
  '¡Esto hay que celebrarlo!',
  'Sabía que lo ibas a lograr 💛',
  '¡Me alegra el día saberlo!',
  '¿Qué fue lo mejor de todo?',
];

/** Reacciones cortas a la respuesta de la pareja en la Pregunta del día (más que un emoji). */
export const QUESTION_REACTIONS = [
  'Cuéntame más 💬',
  'No lo sabía, me encantó',
  'Me hiciste sonreír',
  'Yo también 🫶',
  'Quiero que hablemos de esto',
];

/** Lo que la app descarga para tener a mano sin conexión (no incluye las preguntas: serían spoilers). */
/**
 * Frases de reparación para el final de la Tregua (intentos de reparación de Gottman): reconocer, bajar la
 * guardia, volver a empezar. Ninguna pide explicar la pelea.
 */
export const REPAIR_PHRASES = [
  'Tienes razón en una parte',
  'Empecemos de nuevo',
  'Te quiero aunque estemos molestos',
  'Perdón por cómo lo dije',
  'Lo que sientes me importa',
  'Somos un equipo',
  '¿Me das un abrazo?',
  'Quiero entenderte mejor',
];

/** Arranques suaves para el Buzón ("siento… cuando… me ayudaría…"): ideas, nunca obligatorias. */
export const UNSAID_STARTERS = {
  feel: ['triste', 'sola / solo', 'preocupado/a', 'poco escuchado/a', 'cansado/a', 'inseguro/a'],
  need: ['que me abraces', 'que hablemos con calma', 'que me avises', 'un rato para los dos', 'que me escuches sin resolver'],
};

export function contentBundle() {
  return {
    version: CONTENT_VERSION,
    touchKinds: TOUCH_KINDS,
    cheers: CHEERS,
    questionReactions: QUESTION_REACTIONS,
    repairs: REPAIR_PHRASES,
    unsaidStarters: UNSAID_STARTERS,
    help: HELP_LINES,
  };
}
