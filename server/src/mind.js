import { addDays } from './duo.js';
import { careCheck, normalize } from './care.js';
import { couples, mindLines } from './db.js';
import { recall } from './memory.js';
import { daysBetween, monthsBetween } from './pet.js';
import { seeded } from './seed.js';
import {
  ATTEMPTS_PER_DAY, BANNED, ENGLISH_HINTS, KIND_IDS, LINE_MAX, LINE_MIN, MIND_KINDS, MOOD_SELF, PERSONA, REHEARSAL,
  SPANISH_HINTS, SPECIES_SELF,
} from './mind-content.js';

/**
 * Minu aprende de ustedes (capa 3). El servidor no redacta nada: arma el encargo (personalidad + lo que
 * Minu sabe de la pareja + la situación de hoy) y el celular de uno de los dos lo redacta por dentro. El
 * texto vuelve aquí, pasa las barandas y queda como el mensaje del día de los dos. Si nada pasa, Minu
 * habla con su banco de frases y nadie nota la diferencia.
 */

const isDuplicate = (e) => e?.code === 11000;

// ---------- Fechas ----------

/** Suma meses a "AAAA-MM-DD"; el 31 cae en el último día de los meses cortos (31 ene + 1 = 28/29 feb). */
export function addMonths(day, n) {
  const [y, m, d] = day.split('-').map(Number);
  const index = y * 12 + (m - 1) + n;
  const ty = Math.floor(index / 12);
  const tm = index % 12;
  const last = new Date(Date.UTC(ty, tm + 1, 0)).getUTCDate();
  return `${ty}-${String(tm + 1).padStart(2, '0')}-${String(Math.min(d, last)).padStart(2, '0')}`;
}

/** Minu despierta al cumplir un mes desde que la pareja llegó a Minu (no desde que empezó la relación). */
export function wakeDay(joinedOn) {
  return joinedOn ? addMonths(joinedOn, 1) : null;
}

/** El domingo de la semana de [day] (o el mismo día si es domingo): ahí se renueva lo que Minu sabe. */
export function sundayOf(day) {
  return addDays(day, -new Date(`${day}T00:00:00Z`).getUTCDay());
}

const WEEKDAYS = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];
const MONTHS = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];

// ---------- Lo que Minu sabe (resumen semanal) ----------

const quote = (text, max = 80) => {
  const clean = String(text ?? '').replace(/\s+/g, ' ').trim();
  return `"${clean.length > max ? `${clean.slice(0, max - 1).trimEnd()}…` : clean}"`;
};

const LOVE_PHRASE = {
  words: 'las palabras bonitas', time: 'los ratos juntos', gifts: 'los detalles', service: 'que le echen una mano', touch: 'los abrazos',
};

function topLang(weights) {
  if (!weights) return null;
  const [lang, value] = Object.entries(weights).sort((a, b) => b[1] - a[1])[0] ?? [];
  return value > 0 ? lang : null;
}

/**
 * Hechos cortos y en frases naturales a partir de la memoria de la pareja (capa 1). Nunca incluye lo del
 * Buzón ni la Tregua (no entran a la memoria) ni dice quién aportó qué. Función pura para poder probarla.
 */
export function digestFacts({ couple, memory, joinedOn, today }) {
  const facts = [];
  const origin = couple?.origin;
  if (origin?.startedOn) {
    const [y, m] = origin.startedOn.split('-').map(Number);
    const months = monthsBetween(origin.startedOn, today);
    const span = months >= 12 ? `${Math.floor(months / 12)} año${months >= 24 ? 's' : ''}` : `${months} mes${months === 1 ? '' : 'es'}`;
    facts.push(`Están juntos desde ${MONTHS[m - 1]} de ${y} (${span}).`);
  }
  if (origin?.song?.title) facts.push(`Su canción del inicio: ${quote(origin.song.title, 60)}.`);
  if (joinedOn) {
    const months = monthsBetween(joinedOn, today);
    if (months >= 1) facts.push(`Llevan ${months} mes${months === 1 ? '' : 'es'} cuidándome en Minu.`);
  }
  const of = (kind, n) => memory.filter((f) => f.kind === kind && f.text).slice(0, n);
  const songs = of('song', 4);
  if (songs.length) facts.push(`Se dedicaron hace poco: ${songs.map((f) => quote(f.text, 50)).join(', ')}.`);
  const adventures = of('adventure', 2);
  if (adventures.length) facts.push(`Vivieron la aventura ${adventures.map((f) => quote(f.text, 60)).join(' y ')}.`);
  const thanks = of('thanks', 3);
  if (thanks.length) facts.push(`Se dieron gracias ${thanks.map((f) => quote(f.text, 60)).join('; ')}.`);
  const news = of('news', 2);
  if (news.length) facts.push(`Buenas noticias que se contaron: ${news.map((f) => quote(f.text, 70)).join('; ')}.`);
  const answer = of('answer', 1)[0];
  if (answer?.data?.question) facts.push(`A la pregunta ${quote(answer.data.question, 70)} alguien respondió ${quote(answer.text, 70)}.`);
  const langs = Object.values(couple?.loveLangs ?? {}).map((l) => topLang(l.weights)).filter(Boolean);
  if (langs.length === 2) facts.push(`A uno de ustedes le llegan mucho ${LOVE_PHRASE[langs[0]]}; al otro, ${LOVE_PHRASE[langs[1]]}.`);
  else if (langs.length === 1) facts.push(`A uno de ustedes le llegan mucho ${LOVE_PHRASE[langs[0]]}.`);
  return facts.slice(0, 10);
}

/** Lo que Minu sabe esta semana: se arma una vez por semana (los domingos) y se guarda en la pareja. */
export async function ensureDigest(db, couple, joinedOn, today, now = new Date()) {
  const week = sundayOf(today);
  if (couple.mind?.digest?.week === week) return couple.mind.digest.facts;
  const memory = await recall(db, couple._id, { limit: 60 });
  const facts = digestFacts({ couple, memory, joinedOn, today });
  await couples(db).updateOne({ _id: couple._id }, { $set: { 'mind.digest': { week, facts, at: now } } });
  return facts;
}

// ---------- El encargo del día ----------

/** Cosas de hoy que vale la pena que Minu sepa (aniversario, aventura pendiente...). */
export function situationOf({ couple, today, adventure }) {
  const lines = [`Hoy es ${WEEKDAYS[new Date(`${today}T00:00:00Z`).getUTCDay()]}.`];
  const started = couple?.origin?.startedOn;
  if (started && started.slice(8) === today.slice(8) && started < today) {
    const months = monthsBetween(started, today);
    if (months % 12 === 0) lines.push(`¡Hoy cumplen ${months / 12} año${months === 12 ? '' : 's'} juntos!`);
    else lines.push(`Hoy cumplen ${months} meses juntos.`);
  }
  if (adventure?.chosen && !adventure.doneAt) lines.push(`Esta semana eligieron la aventura ${quote(adventure.chosen.title, 60)} y aún no la viven.`);
  return lines;
}

/**
 * El tipo de mensaje de hoy, al azar pero estable para el mismo día. Si Minu los extraña prefiere ánimo
 * o una dedicatoria (algo fácil de responder) antes que un plan.
 */
export function pickKind(seed, mood) {
  const rand = seeded(seed);
  const weights = { plan: 3, question: 3, cheer: 3, dedication: 2 };
  if (mood === 'missing' || mood === 'sleepy') Object.assign(weights, { plan: 1, cheer: 4, dedication: 3 });
  const total = Object.values(weights).reduce((a, b) => a + b, 0);
  let roll = rand() * total;
  for (const kind of KIND_IDS) {
    roll -= weights[kind];
    if (roll < 0) return kind;
  }
  return 'cheer';
}

/** El encargo completo, en español y corto (el celular tiene poco espacio para leer). */
export function buildPrompt({ pet, facts, situation, kind }) {
  const recipe = MIND_KINDS[kind] ?? MIND_KINDS.cheer;
  const persona = PERSONA.replace('{name}', pet?.name ?? 'Minu').replace('{species}', SPECIES_SELF[pet?.species] ?? SPECIES_SELF.yarn);
  return [
    persona,
    MOOD_SELF[pet?.mood] ?? MOOD_SELF.happy,
    '',
    'Lo que sé de ustedes:',
    ...(facts.length ? facts : ['Todavía los estoy conociendo.']).map((f) => `- ${f}`),
    ...situation.map((s) => `- ${s}`),
    '',
    'Así hablo (ejemplos de tono, no los copies):',
    ...recipe.examples.map((e) => `- ${e}`),
    '',
    `Encargo: ${recipe.ask}`,
    'Mensaje:',
  ].join('\n');
}

// ---------- Barandas ----------

/** Deja el texto limpio (sin comillas, etiquetas ni formato) en un solo párrafo. */
export function tidyLine(raw) {
  let text = String(raw ?? '').replace(/\r/g, '').trim();
  text = text.split(/\n\s*\n/)[0];
  text = text
    .split('\n')
    .map((l) => l.replace(/^\s*[-*•]\s+/, '').trim())
    .join(' ')
    .replace(/\*\*|__|`|#+\s/g, '')
    .replace(/^(minu|mensaje|respuesta)\s*:\s*/i, '')
    .replace(/\s+/g, ' ')
    .trim();
  const wrapped = /^["“«'](.*)["”»']$/.exec(text);
  if (wrapped) text = wrapped[1].trim();
  // Si se pasó de largo, se queda con las frases completas que quepan
  if (text.length > LINE_MAX) {
    const cut = text.slice(0, LINE_MAX);
    const end = Math.max(cut.lastIndexOf('. '), cut.lastIndexOf('! '), cut.lastIndexOf('? '));
    text = end > LINE_MIN ? cut.slice(0, end + 1) : text;
  }
  return text;
}

/**
 * ¿Puede decirlo Minu? Revisa largo, idioma, palabras prohibidas (cómo funciona, Buzón, culpa, temas
 * médicos o legales, etiquetas del test), enlaces, restos del encargo y el detector de riesgo.
 */
export function guardLine(raw) {
  const text = tidyLine(raw);
  if (text.length < LINE_MIN) return { ok: false, reason: 'short' };
  if (text.length > LINE_MAX) return { ok: false, reason: 'long' };
  if (/[{}<>]|lo que s[eé] de ustedes|encargo:|mensaje:|así hablo/i.test(text)) return { ok: false, reason: 'leak' };
  if (/https?:|www\.|@|\d{6,}/i.test(text)) return { ok: false, reason: 'link' };
  const plain = normalize(text);
  if (BANNED.some((re) => re.test(plain))) return { ok: false, reason: 'banned' };
  if (careCheck(text).risk) return { ok: false, reason: 'care' };
  const words = plain.split(' ');
  const es = words.filter((w) => SPANISH_HINTS.includes(w)).length;
  const en = words.filter((w) => ENGLISH_HINTS.includes(w)).length;
  if (es < 2 || en * 2 > es) return { ok: false, reason: 'language' };
  return { ok: true, text };
}

// ---------- Estado y acciones ----------

/**
 * Lo que la app ve de la voz de Minu: si ya despertó (o cuánto falta), si los mensajes personalizados
 * están encendidos para los dos, el mensaje de hoy y, si aún no hay, el encargo para redactarlo.
 */
export async function mindView(db, couple, me, today, joinedOn, pet, { adventure = null, now = new Date() } = {}) {
  const wakesOn = wakeDay(joinedOn);
  const awake = Boolean(wakesOn && today >= wakesOn);
  const off = couple.mind?.off ?? {};
  const mineOn = !off[String(me._id)];
  const partnerOn = !off[String(me.partnerId)];
  const enabled = mineOn && partnerOn;
  const learnTotal = wakesOn ? daysBetween(joinedOn, wakesOn) : 30;
  const learned = joinedOn ? Math.min(learnTotal, Math.max(0, daysBetween(joinedOn, today))) : 0;
  const base = {
    awake, wakesOn, daysLeft: awake || !wakesOn ? 0 : daysBetween(today, wakesOn), learned, learnTotal,
    mineOn, partnerOn, enabled, celebrate: false, line: null, task: null,
  };
  if (!awake || !enabled) return base;

  const doc = await mindLines(db).findOne({ _id: `${couple._id}:${today}` });
  const line = doc?.text ? { day: today, kind: doc.kind, text: doc.text } : null;
  const celebrate = !couple.mind?.woke?.[String(me._id)];
  let task = null;
  if (!line && (doc?.attempts ?? 0) < ATTEMPTS_PER_DAY) {
    const kind = pickKind(`${couple._id}:${today}:${doc?.attempts ?? 0}`, pet?.mood);
    const facts = await ensureDigest(db, couple, joinedOn, today, now);
    task = { day: today, kind, prompt: buildPrompt({ pet, facts, situation: situationOf({ couple, today, adventure }), kind }) };
  }
  return { ...base, celebrate, line, task };
}

/**
 * El celular de uno ofrece el mensaje de hoy. Si pasa las barandas, queda para los dos (gana el primero
 * que llegue); si no, cuenta como intento y la app sigue con el banco.
 */
export async function offerLine(db, couple, me, today, { day, kind, text }, now = new Date()) {
  if (day !== today) return { error: 'Ese mensaje ya no es de hoy.', status: 409 };
  if (!KIND_IDS.includes(kind)) return { error: 'Tipo de mensaje inválido.', status: 400 };
  const id = `${couple._id}:${today}`;
  const doc = await mindLines(db).findOne({ _id: id });
  if (doc?.text) return { accepted: false, already: true };
  if ((doc?.attempts ?? 0) >= ATTEMPTS_PER_DAY) return { accepted: false, reason: 'attempts' };
  const verdict = guardLine(text);
  if (!verdict.ok) {
    await mindLines(db).updateOne(
      { _id: id },
      { $inc: { attempts: 1 }, $push: { rejected: verdict.reason }, $setOnInsert: { couple: couple._id, day: today } },
      { upsert: true },
    );
    return { accepted: false, reason: verdict.reason };
  }
  try {
    await mindLines(db).updateOne(
      { _id: id, text: null },
      { $set: { couple: couple._id, day: today, kind, text: verdict.text, by: String(me._id), at: now } },
      { upsert: true },
    );
  } catch (e) {
    if (!isDuplicate(e)) throw e;
    return { accepted: false, already: true };
  }
  return { accepted: true };
}

/** Enciendo o apago los mensajes personalizados (si cualquiera los apaga, los dos vuelven al banco). */
export async function setMindOn(db, key, me, on) {
  const field = `mind.off.${me._id}`;
  await couples(db).updateOne({ _id: key }, on ? { $unset: { [field]: '' } } : { $set: { [field]: true } });
}

/** Ya vi la celebración del despertar. */
export async function seeWake(db, key, me, now = new Date()) {
  await couples(db).updateOne({ _id: key }, { $set: { [`mind.woke.${me._id}`]: now } });
}

/** Al borrar mis datos: los mensajes que redactó mi celular y lo que Minu sabía (se vuelve a armar). */
export async function eraseMyMind(db, key, me) {
  await Promise.all([
    mindLines(db).deleteMany({ couple: key, by: String(me._id) }),
    couples(db).updateOne({ _id: key }, { $unset: { 'mind.digest': '', [`mind.woke.${me._id}`]: '' } }),
  ]);
}

/** Las 30 situaciones de ensayo con el encargo que recibiría el celular en cada una. */
export function rehearsal(pet, today) {
  return REHEARSAL.map((c) => ({
    id: c.id,
    kind: c.kind,
    mood: c.mood,
    facts: c.facts,
    prompt: buildPrompt({ pet: { ...pet, mood: c.mood }, facts: c.facts, situation: situationOf({ couple: null, today }), kind: c.kind }),
  }));
}
