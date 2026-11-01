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
 * Minu aprende de ustedes (capa 3). El servidor arma el encargo (personalidad + lo que Minu sabe de la
 * pareja + la situación de hoy), lo manda al redactor configurado (mind-writer.js), revisa el texto con
 * las barandas y lo guarda como el mensaje del día de los dos. Si nada pasa, Minu habla con su banco de
 * frases y nadie nota la diferencia.
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

// ---------- Redacción (en el servidor, en segundo plano) ----------

/**
 * Quién redacta y a quién avisar cuando hay mensaje nuevo. Lo configura createApp una vez (con la llave
 * del proveedor); sin redactor, Minu habla siempre con su banco de frases y nadie nota la diferencia.
 */
const mindConfig = { writer: null, notify: () => {} };
const inFlight = new Map();

export function configureMind({ writer = null, notify = () => {} } = {}) {
  mindConfig.writer = writer;
  mindConfig.notify = notify;
}

/** Espera a que terminen las redacciones en curso (para las pruebas y un apagado ordenado). */
export async function mindIdle() {
  await Promise.allSettled([...inFlight.values()]);
}

/** Pide un borrador al redactor; acepta redactores simples que solo tienen write(). */
async function draftWith(writer, prompt) {
  if (typeof writer.draft === 'function') return writer.draft(prompt);
  return { text: await writer.write(prompt), error: null };
}

/**
 * Redacta el mensaje del día de la pareja: hasta ATTEMPTS_PER_DAY intentos, cada uno revisado por las
 * barandas. El primero que pasa queda para los dos. Devuelve true si quedó un mensaje.
 */
export async function composeLine(db, writer, { couple, today, joinedOn, pet, adventure, now = new Date() }) {
  const id = `${couple._id}:${today}`;
  const facts = await ensureDigest(db, couple, joinedOn, today, now);
  const situation = situationOf({ couple, today, adventure });
  for (;;) {
    const doc = await mindLines(db).findOne({ _id: id });
    if (doc?.text) return true;
    const attempt = doc?.attempts ?? 0;
    if (attempt >= ATTEMPTS_PER_DAY) return false;
    const kind = pickKind(`${couple._id}:${today}:${attempt}`, pet?.mood);
    const { text: raw, error } = await draftWith(writer, buildPrompt({ pet, facts, situation, kind }));
    // Si el proveedor pidió una pausa no se gasta un intento: la próxima vez que abran Dúo se vuelve a probar
    if (error === 'busy') return false;
    const verdict = raw ? guardLine(raw) : { ok: false, reason: 'empty' };
    if (!verdict.ok) {
      await mindLines(db).updateOne(
        { _id: id },
        { $inc: { attempts: 1 }, $push: { rejected: verdict.reason }, $setOnInsert: { couple: couple._id, day: today } },
        { upsert: true },
      );
      continue;
    }
    try {
      await mindLines(db).updateOne(
        { _id: id, text: null },
        { $set: { couple: couple._id, day: today, kind, text: verdict.text, at: now } },
        { upsert: true },
      );
    } catch (e) {
      if (!isDuplicate(e)) throw e;
    }
    return true;
  }
}

/** Lanza la redacción de hoy si no hay una en curso para esta pareja; avisa a los dos al terminar. */
function composeSoon(db, args) {
  const key = `${args.couple._id}:${args.today}`;
  if (inFlight.has(key) || !mindConfig.writer) return;
  const job = composeLine(db, mindConfig.writer, args)
    .then((ok) => { if (ok) mindConfig.notify(args.couple.members ?? []); })
    .catch((e) => console.warn(`[minu] no se pudo redactar: ${e?.message ?? e}`))
    .finally(() => inFlight.delete(key));
  inFlight.set(key, job);
}

// ---------- Estado y acciones ----------

/**
 * Lo que la app ve de la voz de Minu: si ya despertó (o cuánto falta), si los mensajes personalizados
 * están encendidos para los dos y el mensaje de hoy. Si aún no hay, lo empieza a redactar en segundo
 * plano ([writing]) y avisa por tiempo real cuando esté.
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
    mineOn, partnerOn, enabled, celebrate: false, line: null, writing: false,
  };
  if (!awake || !enabled) return base;

  const doc = await mindLines(db).findOne({ _id: `${couple._id}:${today}` });
  const line = doc?.text ? { day: today, kind: doc.kind, text: doc.text } : null;
  const celebrate = !couple.mind?.woke?.[String(me._id)];
  const canWrite = !line && (doc?.attempts ?? 0) < ATTEMPTS_PER_DAY && Boolean(mindConfig.writer);
  if (canWrite) composeSoon(db, { couple, today, joinedOn, pet, adventure, now });
  return { ...base, celebrate, line, writing: canWrite };
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

/** Al borrar mis datos: lo que Minu sabía y los mensajes que salieron de ahí (se vuelve a armar). */
export async function eraseMyMind(db, key, me) {
  await Promise.all([
    mindLines(db).deleteMany({ couple: key }),
    couples(db).updateOne({ _id: key }, { $unset: { 'mind.digest': '', [`mind.woke.${me._id}`]: '' } }),
  ]);
}

/** Ensaya una situación con el redactor configurado: el texto crudo, el veredicto y cuánto tardó. */
export async function rehearseCase(pet, today, id) {
  const c = rehearsal(pet, today).find((r) => r.id === id);
  if (!c) return { error: 'Situación desconocida.', status: 404 };
  if (!mindConfig.writer) return { raw: null, ok: false, text: null, reason: 'no_writer', ms: 0 };
  const start = Date.now();
  const { text: raw, error } = await draftWith(mindConfig.writer, c.prompt);
  const verdict = raw ? guardLine(raw) : { ok: false, reason: error === 'busy' ? 'busy' : 'empty' };
  return { raw, ok: verdict.ok, text: verdict.text ?? null, reason: verdict.reason ?? null, ms: Date.now() - start };
}

/** Las 30 situaciones de ensayo con el encargo que recibiría el redactor en cada una. */
export function rehearsal(pet, today) {
  return REHEARSAL.map((c) => ({
    id: c.id,
    kind: c.kind,
    mood: c.mood,
    facts: c.facts,
    prompt: buildPrompt({ pet: { ...pet, mood: c.mood }, facts: c.facts, situation: situationOf({ couple: null, today }), kind: c.kind }),
  }));
}
