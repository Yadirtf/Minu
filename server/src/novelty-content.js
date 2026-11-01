import { seeded } from './seed.js';

/**
 * Contenido de la novedad (entrega 4): planes para la Aventura de la semana y el test de lenguajes del
 * amor. Hacer cosas nuevas juntos sube la satisfacción (autoexpansión de Aron): por eso los planes son
 * nuevos y un poco retadores, nunca "ver una película" a secas.
 *
 * Los ids son estables: nunca reordenar ni borrar (el motor de variedad recuerda cuáles ya salieron).
 * Para agregar, usar el siguiente id libre.
 *
 * [kind]: home (en casa), out (salir), create (crear algo). Cada semana trae uno de cada uno.
 * [langs]: lenguajes del amor que más toca (pesan al elegir según el test de la pareja).
 * [effort]: 1 tranquilo, 2 con ganas, 3 reto.
 */
export const LOVE_LANGS = ['words', 'time', 'gifts', 'service', 'touch'];

const plan = (id, kind, emoji, title, detail, langs, effort = 2) => ({ id, kind, emoji, title, detail, langs, effort });

export const ADVENTURES = [
  // En casa
  plan('a001', 'home', '🍜', 'Cocinen un plato de un país que no conozcan', 'Elijan el país con los ojos cerrados en un mapa y busquen una receta típica.', ['time', 'service'], 2),
  plan('a002', 'home', '🕯️', 'Cena a oscuras', 'Solo velas, sin celulares. Cada uno cuenta un recuerdo de infancia por plato.', ['time', 'words'], 1),
  plan('a003', 'home', '💃', 'Clase de baile en la sala', 'Busquen un tutorial de un ritmo que ninguno sepa bailar y aprendan los pasos básicos.', ['touch', 'time'], 2),
  plan('a004', 'home', '🏕️', 'Campamento en la sala', 'Fuerte de cobijas, linterna, snacks y cuentos de miedo (o de amor).', ['time', 'touch'], 1),
  plan('a005', 'home', '🧩', 'Rompecabezas de 500 piezas en una noche', 'Con música de fondo y una regla: nadie mira el celular hasta terminar.', ['time'], 3),
  plan('a006', 'home', '🎤', 'Karaoke de las canciones de su historia', 'Cada uno elige 3 canciones que le recuerden al otro y las cantan sin pena.', ['words', 'time'], 2),
  plan('a007', 'home', '💆', 'Spa en casa', 'Mascarillas, masajes de 10 minutos por turnos y una bebida rica.', ['touch', 'service'], 1),
  plan('a008', 'home', '🎲', 'Torneo de juegos de mesa', 'Tres juegos distintos; quien pierda prepara el desayuno del día siguiente.', ['time'], 2),
  plan('a009', 'home', '🍳', 'Desayuno sorpresa al revés', 'Cada uno prepara el desayuno que cree que el otro más disfrutaría, sin decirse nada.', ['service', 'gifts'], 2),
  plan('a010', 'home', '📼', 'Noche de sus primeros videos', 'Vean fotos y videos de cuando se conocieron y cuéntense qué pensaban en ese momento.', ['words', 'time'], 1),
  plan('a011', 'home', '🌍', 'Viaje imaginario', 'Planeen con detalle el viaje de sus sueños: destino, presupuesto y día por día.', ['time', 'words'], 1),
  plan('a012', 'home', '🧁', 'Concurso de postres', 'Mismo presupuesto, misma hora, cada uno su postre. Jueces: ustedes mismos.', ['time', 'service'], 2),
  plan('a013', 'home', '🌙', 'Noche de preguntas difíciles', 'Turnos de preguntas que nunca se han hecho; se vale pasar una vez.', ['words', 'time'], 2),
  plan('a014', 'home', '🪴', 'Adopten una planta', 'Elijan una planta, pónganle nombre y decidan quién la cuida qué día.', ['service', 'gifts'], 1),
  plan('a015', 'home', '🎬', 'Película en otro idioma con subtítulos', 'De un país del que nunca hayan visto cine. Después, cada uno la califica.', ['time'], 1),
  // Salir
  plan('a101', 'out', '🌅', 'Ver el amanecer juntos', 'Pongan la alarma temprano y busquen un lugar alto. Lleven algo caliente para tomar.', ['time', 'touch'], 3),
  plan('a102', 'out', '🚌', 'Ruta al azar', 'Súbanse al primer bus que pase y bájense en una parada que no conozcan. Exploren una hora.', ['time'], 3),
  plan('a103', 'out', '🥾', 'Caminata a un mirador nuevo', 'Uno que ninguno haya visitado. Fotos obligatorias en la cima.', ['time', 'touch'], 3),
  plan('a104', 'out', '🛒', 'Reto de mercado: cena con 30 mil', 'Vayan a una plaza de mercado y armen una cena completa con ese presupuesto.', ['service', 'time'], 2),
  plan('a105', 'out', '🎨', 'Museo o galería que no conozcan', 'Cada uno elige la obra que más le recuerda al otro y explica por qué.', ['words', 'time'], 2),
  plan('a106', 'out', '🍦', 'Ruta de heladerías', 'Tres heladerías, un sabor raro en cada una. Voten el ganador.', ['time', 'gifts'], 1),
  plan('a107', 'out', '📚', 'Librería con regalo a ciegas', 'Cada uno elige un libro para el otro sin que lo vea; se intercambian en el café.', ['gifts', 'words'], 2),
  plan('a108', 'out', '🧺', 'Picnic en un parque nuevo', 'Cada uno lleva una sorpresa para comer que el otro no espere.', ['time', 'gifts'], 1),
  plan('a109', 'out', '🎳', 'Algo que ninguno haya probado', 'Bolos, escalada, patinaje, tejo… lo que les dé un poco de nervios.', ['time', 'touch'], 3),
  plan('a110', 'out', '🎶', 'Música en vivo', 'Un bar o concierto pequeño de un género que no escuchen normalmente.', ['time'], 2),
  plan('a111', 'out', '📸', 'Cacería de fotos', 'Una lista de 10 cosas para fotografiar (algo rojo, un perro feliz, una puerta bonita…).', ['time'], 2),
  plan('a112', 'out', '🌳', 'Voluntariado juntos', 'Una mañana en un refugio de animales, un comedor o una jornada de siembra.', ['service', 'time'], 3),
  plan('a113', 'out', '🚲', 'Paseo en bici por la ciclovía', 'Un recorrido nuevo, con parada para jugo de fruta a mitad de camino.', ['time', 'touch'], 2),
  plan('a114', 'out', '🌮', 'Comida callejera de otro barrio', 'Elijan un barrio que no conozcan y prueben tres puestos distintos.', ['time'], 2),
  plan('a115', 'out', '⭐', 'Noche de estrellas', 'Busquen un lugar con poca luz, una cobija y una app de constelaciones.', ['touch', 'time'], 2),
  // Crear algo
  plan('a201', 'create', '✉️', 'Cartas para abrir en un año', 'Escríbanse una carta cada uno y guárdenla sellada hasta el próximo aniversario.', ['words', 'gifts'], 1),
  plan('a202', 'create', '🎨', 'Píntense el uno al otro', 'Retratos en 15 minutos, sin borrar. Después los cuelgan aunque queden chistosos.', ['time', 'words'], 2),
  plan('a203', 'create', '📒', 'Álbum de la semana', 'Impriman 10 fotos recientes y armen una página con notas escritas a mano.', ['gifts', 'words'], 2),
  plan('a204', 'create', '🎧', 'Playlist con historia', 'Una canción por cada mes que llevan juntos, con una frase de por qué.', ['words', 'gifts'], 2),
  plan('a205', 'create', '🧶', 'Manualidad para los dos', 'Pulseras, una vela, cerámica en frío… algo que quede en casa.', ['gifts', 'time'], 2),
  plan('a206', 'create', '🎥', 'Corto de un minuto', 'Graben un mini corto sobre cómo se conocieron. Ustedes son actores y directores.', ['time', 'words'], 3),
  plan('a207', 'create', '🗺️', 'Mapa de su historia', 'Dibujen un mapa con los lugares importantes de su relación y lo que pasó en cada uno.', ['words', 'time'], 2),
  plan('a208', 'create', '🍞', 'Pan casero desde cero', 'Masa, espera y horno. Mientras leva, se cuentan algo que nunca se hayan contado.', ['service', 'time'], 3),
  plan('a209', 'create', '📝', 'Lista de 20 sueños juntos', 'Cosas grandes y pequeñas que quieren vivir. Elijan una para la próxima aventura.', ['words', 'time'], 1),
  plan('a210', 'create', '🎁', 'Regalo hecho a mano con 10 mil', 'Cada uno crea un regalo para el otro con poco presupuesto y mucha imaginación.', ['gifts', 'service'], 2),
  plan('a211', 'create', '🌱', 'Huerta en una ventana', 'Siembren tres hierbas en macetas y decidan qué cocinarán cuando crezcan.', ['service', 'time'], 2),
  plan('a212', 'create', '💌', 'Frasco de 30 razones', 'Cada uno escribe 15 razones por las que quiere al otro en papelitos doblados.', ['words', 'gifts'], 1),
  plan('a213', 'create', '🎹', 'Componer una canción', 'Una estrofa y un coro sobre ustedes, con la melodía que salga. Grábenla.', ['words', 'time'], 3),
  plan('a214', 'create', '🧳', 'Cápsula del tiempo', 'Metan objetos y notas en una caja y escriban en la tapa cuándo abrirla.', ['gifts', 'words'], 2),
  plan('a215', 'create', '📷', 'Sesión de fotos temática', 'Elijan una época (los 80, los 2000…) y disfrácense con lo que haya en casa.', ['time', 'touch'], 2),
];

export const ADVENTURE_KINDS = ['home', 'out', 'create'];
/** Semanas en las que un plan no se repite. */
export const ADVENTURE_NO_REPEAT_WEEKS = 52;

/**
 * Los 3 planes de la semana: uno en casa, uno para salir y uno para crear, sin repetir lo del último año.
 * [weights]: suma de los lenguajes del amor de la pareja (0..1 cada uno); los planes que tocan sus
 * lenguajes pesan más, pero ninguno queda en cero (para que siempre haya sorpresa).
 */
export function pickAdventures({ usedIds = new Set(), seed, weights = {} }) {
  const rand = seeded(seed);
  const weightOf = (p) => 1 + p.langs.reduce((sum, l) => sum + (weights[l] ?? 0), 0) * 1.5;
  return ADVENTURE_KINDS.map((kind) => {
    let pool = ADVENTURES.filter((p) => p.kind === kind && !usedIds.has(p.id));
    if (!pool.length) pool = ADVENTURES.filter((p) => p.kind === kind); // ya salieron todas: se repite
    const total = pool.reduce((sum, p) => sum + weightOf(p), 0);
    let roll = rand() * total;
    return pool.find((p) => (roll -= weightOf(p)) < 0) ?? pool[pool.length - 1];
  });
}

/**
 * Test de lenguajes del amor: 10 pares (cada combinación de dos lenguajes, una vez). Sin etiquetas: el
 * resultado solo ajusta qué planes y gestos sugiere Minu. Es una guía para conversar, no ciencia exacta.
 */
const pair = (id, a, aLang, b, bLang) => ({ id, a: { text: a, lang: aLang }, b: { text: b, lang: bLang } });
export const LOVE_QUIZ = [
  pair('q1', 'Que me digas lo orgulloso que estás de mí', 'words', 'Una tarde entera solo para los dos', 'time'),
  pair('q2', 'Un detalle inesperado que me hiciste', 'gifts', 'Que me digas "te quiero" sin motivo', 'words'),
  pair('q3', 'Que me ayudes con algo que me estresa', 'service', 'Un mensaje bonito en medio del día', 'words'),
  pair('q4', 'Un abrazo largo cuando llego', 'touch', 'Que me escribas lo que admiras de mí', 'words'),
  pair('q5', 'Un paseo sin celulares', 'time', 'Un regalito que me recuerde a ti', 'gifts'),
  pair('q6', 'Que cocines para mí', 'service', 'Una conversación larga sin prisa', 'time'),
  pair('q7', 'Ir de la mano por la calle', 'touch', 'Hacer algo nuevo juntos', 'time'),
  pair('q8', 'Una sorpresa envuelta con cariño', 'gifts', 'Que te encargues de algo para que yo descanse', 'service'),
  pair('q9', 'Un masaje después de un día largo', 'touch', 'Una flor o una nota en mi almohada', 'gifts'),
  pair('q10', 'Que me ayudes sin que te lo pida', 'service', 'Acurrucarnos viendo algo', 'touch'),
];

/** Pesos 0..1 por lenguaje a partir de las respuestas (un lenguaje por pregunta). */
export function loveWeights(answers) {
  const counts = Object.fromEntries(LOVE_LANGS.map((l) => [l, 0]));
  for (const a of answers) if (a in counts) counts[a] += 1;
  return Object.fromEntries(LOVE_LANGS.map((l) => [l, counts[l] / 4])); // cada lenguaje aparece en 4 preguntas
}

/**
 * Una sugerencia del día para mí según lo que le llega más a mi pareja (nunca se nombra el lenguaje).
 * Sin test, null.
 */
const SUGGESTIONS = {
  words: ['Hoy una frase bonita le llegaría directo al corazón.', 'Dile algo que admiras de esa persona. Le encantará escucharlo.'],
  time: ['Hoy un rato sin celulares juntos valdría oro.', 'Proponle una caminata corta, solo ustedes dos.'],
  gifts: ['Un detalle pequeño hoy, aunque sea una nota, le haría sonreír.', 'Guárdale algo rico para cuando llegue.'],
  service: ['Encárgate hoy de algo que le quite peso de encima.', 'Una ayuda sin que te la pida le diría mucho.'],
  touch: ['Hoy un abrazo largo le llegaría bonito.', 'Tómale la mano un ratico hoy, sin motivo.'],
};

export function dailySuggestion(weights, seed) {
  if (!weights) return null;
  const rand = seeded(seed);
  const ranked = LOVE_LANGS.map((l) => [l, weights[l] ?? 0]).sort((x, y) => y[1] - x[1]);
  // Casi siempre lo que más le llega, a veces el segundo: para que no sea siempre lo mismo
  const lang = rand() < 0.7 ? ranked[0][0] : ranked[1][0];
  const options = SUGGESTIONS[lang];
  return options[Math.floor(rand() * options.length)];
}
