/**
 * Cuidado: detección de palabras de riesgo (violencia, miedo, autolesión) en lo que se escribe en los
 * espacios de reparación (Buzón y Tregua). Si algo suena a peligro, Minu deja de jugar y muestra con calma
 * líneas de ayuda profesional a quien lo escribió. Nunca bloquea el envío, nunca avisa a la pareja y nunca
 * guarda que hubo una alerta.
 *
 * Las frases se comparan sin tildes y en minúsculas. Están escritas para no saltar con expresiones
 * cariñosas o exageradas de todos los días ("me muero de risa", "te mato a besos", "me pegó la gripa").
 * Si se agregan frases, agregar también su prueba en test/care.test.js (positivas y negativas).
 */

const SOFT_ENDINGS = '(?! de (la |los |las |el )?(risa|verguenza|pena|amor|ternura|frio|calor|hambre|sueno|ganas|emocion|nervios|celos|envidia|aburrimiento))';

// "Me pegaste la gripa" no es violencia
const NOT_GERMS = '(?! (la|el|mi|tu|tus|una|un) ?(gripa|gripe|virus|resfriado|tos|mania|cancion|costumbre|risa|bostezo|piojos|varicela|covid|susto|grito|chicle|sticker))';

const PATTERNS = {
  selfharm: [
    /\bsuicid/,
    /\b(matarme|suicidarme|quitarme la vida|acabar con mi vida|terminar con mi vida)\b/,
    new RegExp(`\\b(me quiero morir|quiero morirme|quiero morir|ganas de morir(me)?|deseo morir(me)?)\\b${SOFT_ENDINGS}`),
    /\bno quiero (seguir )?viviendo\b|\bno quiero vivir\b/,
    /\b(hacerme dano|lastimarme|cortarme las venas)\b/,
    /\bcortarme\b(?! (el|la|las|los|mi|un|una) )/,
    /\bme voy a matar\b(?! (estudiando|trabajando|de|a|con|en|por|corriendo|haciendo))/,
    /\bmejor (estaria |estar )?muert[oa]\b/,
    /\b(acabar|terminar) con todo\b(?! (el|la|los|las|lo que|mi trabajo|el trabajo))/,
    /\bno vale la pena (vivir|seguir)\b/,
    /\bdesaparecer para siempre\b/,
  ],
  violence: [
    new RegExp(`\\bme (pegaste|pegas|has pegado|habias pegado)\\b${NOT_GERMS}`),
    /\bme (golpeaste|empujaste|ahorcaste|cacheteaste|abofeteaste|pateaste|jalaste el pelo|halaste el pelo)\b/,
    /\bme (has|habias) (golpeado|empujado|ahorcado|cacheteado|pateado)\b/,
    /\bme (golpeas|empujas|ahorcas|cacheteas|pateas)\b/,
    /\b(me|nos) (amenazaste|amenazas|has amenazado)\b/,
    /\bme (forzaste|obligaste|obligas|fuerzas) a (tener|hacer cosas|acostarme|estar contigo)\b/,
    /\b(viol(o|aste|acion)|abuso sexual|abusaste de mi|abusas de mi)\b/,
    /\bte voy a (matar|golpear|hacer dano|lastimar)\b(?! (a|de) (besos|abrazos|cosquillas|risa|amor))/,
    new RegExp(`\\bte voy a pegar\\b${NOT_GERMS}`),
    /\bme (encerraste|encierras|quitaste el celular para que no)\b/,
  ],
  fear: [
    /\b(te tengo miedo|tengo miedo de ti|me das miedo|me asustas cuando)\b/,
    /\bmiedo de (lo que )?(me )?(puedas|vayas a|podrias) hacer\b/,
    /\bno me siento segur[oa] (contigo|en casa|a tu lado)\b/,
    /\btemo por mi vida\b|\bmiedo por mi vida\b/,
  ],
};

/** Texto sin tildes, en minúsculas y con espacios simples (la ñ queda como n). */
export function normalize(text) {
  return String(text ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/** ¿Suena a peligro? Devuelve los tipos detectados (vacío si no). */
export function careCheck(...texts) {
  const text = normalize(texts.join(' \n '));
  if (!text) return { risk: false, kinds: [] };
  const kinds = Object.entries(PATTERNS)
    .filter(([, list]) => list.some((re) => re.test(text)))
    .map(([kind]) => kind);
  return { risk: kinds.length > 0, kinds };
}

/**
 * Líneas de ayuda, empezando por Colombia. La app las muestra con calma; también están siempre a mano en
 * la casita de Minu ("Si algo no está bien").
 */
export const HELP_LINES = {
  country: 'Colombia',
  lines: [
    { name: 'Emergencias', number: '123', note: 'Si estás en peligro ahora mismo.' },
    { name: 'Línea 155', number: '155', note: 'Orientación a mujeres víctimas de violencia. Gratis, 24 horas.' },
    { name: 'Línea 192, opción 4', number: '192', note: 'Apoyo en salud mental del Ministerio de Salud.' },
    { name: 'Línea 106', number: '106', note: 'Te escuchan si estás pasando un momento difícil (Bogotá).' },
  ],
  elsewhere: 'Si estás en otro país, llama al número de emergencias local.',
};
