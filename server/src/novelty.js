import { adventurePhotos, adventures, cleanSong, compliments, couples, toId } from './db.js';
import { addDays } from './duo.js';
import { seeded } from './seed.js';
import { cleanText } from './daily.js';
import { remember } from './memory.js';
import { recordPetEvent } from './pet.js';
import {
  ADVENTURE_NO_REPEAT_WEEKS, ADVENTURES, dailySuggestion, LOVE_LANGS, LOVE_QUIZ, loveWeights, pickAdventures,
} from './novelty-content.js';

/**
 * Novedad con Minu (entrega 4): cosas nuevas juntos, sorpresas y planes que se parecen a ustedes.
 * - Aventura de la semana: 3 planes cada lunes (uno en casa, uno para salir, uno para crear). Cada uno vota;
 *   cuando coinciden, es su aventura. Al completarla guardan una foto y una canción en Recuerdos.
 * - Cumplidos a ciegas: cada uno escribe 3 cosas que admira del otro y Minu las suelta de una en una, en
 *   días al azar y como máximo una por semana.
 * - Lenguajes del amor: un test opcional que solo ajusta sugerencias. Nunca se muestra como etiqueta.
 *
 * Todo se calcula al consultar (Render duerme el servidor): no hay tareas programadas.
 */

// ---------- Aventura de la semana ----------

/** El lunes de la semana de [day] ("AAAA-MM-DD"). */
export function weekOf(day) {
  const weekday = new Date(`${day}T00:00:00Z`).getUTCDay(); // 0 domingo … 6 sábado
  return addDays(day, -((weekday + 6) % 7));
}

const PLAN_BY_ID = new Map(ADVENTURES.map((p) => [p.id, p]));
const publicPlan = (id) => {
  const p = PLAN_BY_ID.get(id);
  return p && { id: p.id, kind: p.kind, emoji: p.emoji, title: p.title, detail: p.detail, effort: p.effort };
};
export const PHOTO_MAX_BYTES = 1_500_000;
export const ADVENTURE_RESHUFFLES = 1;

/** Pesos combinados de la pareja (los dos lenguajes cuentan por igual). */
function coupleWeights(couple) {
  const all = Object.values(couple?.loveLangs ?? {}).map((l) => l.weights);
  if (!all.length) return {};
  return Object.fromEntries(LOVE_LANGS.map((l) => [l, all.reduce((sum, w) => sum + (w[l] ?? 0), 0) / all.length]));
}

async function freshOptions(db, couple, week, salt = '') {
  const used = await adventures(db)
    .find({ couple: couple._id, week: { $gte: addDays(week, -7 * ADVENTURE_NO_REPEAT_WEEKS) } }, { projection: { options: 1, shown: 1 } })
    .toArray();
  // Cuenta lo que se mostró, aunque lo hayan cambiado por otras propuestas
  const usedIds = new Set(used.flatMap((a) => [...(a.options ?? []), ...(a.shown ?? [])]));
  return pickAdventures({ usedIds, seed: `${couple._id}:${week}${salt}`, weights: coupleWeights(couple) }).map((p) => p.id);
}

/** La aventura de esta semana (la crea el primer día que alguien la mira). */
export async function ensureAdventure(db, couple, today, now = new Date()) {
  const week = weekOf(today);
  const _id = `${couple._id}:${week}`;
  const existing = await adventures(db).findOne({ _id });
  if (existing) return existing;
  const options = await freshOptions(db, couple, week);
  try {
    await adventures(db).insertOne({
      _id, couple: couple._id, week, options, votes: {}, chosen: null, chosenAt: null, reshuffles: 0,
      doneAt: null, doneBy: null, song: null, photoBy: null, ratings: {}, createdAt: now,
    });
  } catch (e) {
    if (e?.code !== 11000) throw e;
  }
  return adventures(db).findOne({ _id });
}

/** Mi voto. Cuando los dos votan lo mismo, esa es su aventura (y se puede cambiar el voto mientras tanto). */
export async function voteAdventure(db, me, couple, today, planId, now = new Date()) {
  const a = await ensureAdventure(db, couple, today, now);
  if (a.chosen) return { error: 'Ya eligieron su aventura de esta semana.' };
  if (!a.options.includes(planId)) return { status: 400, error: 'Ese plan no está entre los de esta semana.' };
  await adventures(db).updateOne({ _id: a._id, chosen: null }, { $set: { [`votes.${me._id}`]: planId } });
  const after = await adventures(db).findOne({ _id: a._id });
  const votes = Object.values(after.votes ?? {});
  const match = votes.length >= 2 && votes.every((v) => v === planId);
  if (match) {
    const { modifiedCount } = await adventures(db).updateOne({ _id: a._id, chosen: null }, { $set: { chosen: planId, chosenAt: now } });
    return { chosen: modifiedCount > 0 };
  }
  return { chosen: false };
}

/** "Otras propuestas": una vez por semana, antes de elegir. Los votos vuelven a cero. */
export async function reshuffleAdventure(db, couple, today, now = new Date()) {
  const a = await ensureAdventure(db, couple, today, now);
  if (a.chosen) return { error: 'Ya eligieron su aventura de esta semana.' };
  if (a.reshuffles >= ADVENTURE_RESHUFFLES) return { error: 'Ya pidieron otras propuestas esta semana. ¡Anímense con alguna!' };
  const options = await freshOptions(db, { ...couple }, a.week, `:otra${a.reshuffles + 1}:${a.options.join(',')}`);
  await adventures(db).updateOne(
    { _id: a._id },
    { $set: { options, votes: {} }, $push: { shown: { $each: a.options } }, $inc: { reshuffles: 1 } },
  );
  return { ok: true };
}

/** ¡La vivimos! Cualquiera de los dos la marca, con una canción opcional. Alimenta a Minu y a Recuerdos. */
export async function completeAdventure(db, me, couple, today, rawSong, now = new Date()) {
  const a = await ensureAdventure(db, couple, today, now);
  if (!a.chosen) return { error: 'Primero elijan juntos la aventura.' };
  const song = rawSong ? cleanSong(rawSong) : null;
  if (rawSong && !song) return { status: 400, error: 'Canción inválida.' };
  const { modifiedCount } = await adventures(db).updateOne(
    { _id: a._id, doneAt: null },
    { $set: { doneAt: now, doneBy: me._id, ...(song && { song }) } },
  );
  if (!modifiedCount) {
    // Ya estaba completada: solo se puede poner o cambiar la canción
    if (song) await adventures(db).updateOne({ _id: a._id }, { $set: { song } });
    return { ok: true };
  }
  await recordPetEvent(db, couple._id, 'adventure', { day: today, ref: a.week, perDay: false, now });
  const p = PLAN_BY_ID.get(a.chosen);
  await remember(db, couple._id, { kind: 'adventure', by: null, ref: a.week, day: today, text: p?.title ?? '', data: { plan: a.chosen }, now });
  return { done: true };
}

/** La foto de la aventura (JPEG ya reducido por la app). Una por aventura; se puede reemplazar. */
export async function saveAdventurePhoto(db, me, couple, week, bytes, now = new Date()) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(week)) return { status: 400, error: 'Semana inválida.' };
  if (!Buffer.isBuffer(bytes) || bytes.length < 100) return { status: 400, error: 'Foto inválida.' };
  if (bytes.length > PHOTO_MAX_BYTES) return { status: 413, error: 'La foto es muy grande.' };
  if (bytes[0] !== 0xff || bytes[1] !== 0xd8) return { status: 400, error: 'La foto debe ser JPEG.' };
  const _id = `${couple._id}:${week}`;
  const a = await adventures(db).findOne({ _id });
  if (!a?.chosen) return { error: 'Primero elijan juntos la aventura.' };
  await adventurePhotos(db).updateOne(
    { _id },
    { $set: { couple: couple._id, week, by: me._id, data: bytes, size: bytes.length, at: now } },
    { upsert: true },
  );
  await adventures(db).updateOne({ _id }, { $set: { photoBy: me._id, photoAt: now } });
  return { ok: true };
}

export async function adventurePhoto(db, couple, week) {
  return adventurePhotos(db).findOne({ _id: `${couple._id}:${week}` });
}

/** Cada uno califica con 1 a 5 corazones (alimenta la memoria: qué planes les gustan). */
export async function rateAdventure(db, me, couple, week, stars) {
  const n = Number(stars);
  if (!Number.isInteger(n) || n < 1 || n > 5) return { status: 400, error: 'Califica de 1 a 5.' };
  const { matchedCount } = await adventures(db).updateOne(
    { _id: `${couple._id}:${week}`, doneAt: { $ne: null } },
    { $set: { [`ratings.${me._id}`]: n } },
  );
  if (!matchedCount) return { error: 'Primero vivan la aventura.' };
  await couples(db).updateOne({ _id: couple._id }, { $set: { [`adventureTaste.${week}`]: n } });
  return { ok: true };
}

export function serializeAdventure(a, me, partner) {
  const v = a.votes ?? {};
  const r = a.ratings ?? {};
  return {
    week: a.week,
    options: a.chosen ? [] : a.options.map(publicPlan).filter(Boolean),
    myVote: v[String(me)] ?? null,
    partnerVote: v[String(partner)] ?? null,
    chosen: a.chosen ? publicPlan(a.chosen) : null,
    canReshuffle: !a.chosen && (a.reshuffles ?? 0) < ADVENTURE_RESHUFFLES,
    done: Boolean(a.doneAt),
    doneAt: a.doneAt?.getTime() ?? null,
    song: a.song ?? null,
    hasPhoto: Boolean(a.photoBy),
    photoAt: a.photoAt?.getTime() ?? null,
    myRating: r[String(me)] ?? null,
    partnerRating: r[String(partner)] ?? null,
  };
}

/** Las aventuras vividas cuya semana empieza en [month] ("AAAA-MM"), para Recuerdos. */
export async function adventuresOf(db, key, me, partner, month) {
  const list = await adventures(db)
    .find({ couple: key, doneAt: { $ne: null }, week: { $gte: `${month}-01`, $lte: `${month}-31` } })
    .sort({ week: 1 })
    .toArray();
  return list.map((a) => serializeAdventure(a, me, partner));
}

// ---------- Cumplidos a ciegas ----------

export const COMPLIMENTS_PER_ROUND = 3;
export const COMPLIMENT_MAX = 200;

/**
 * Días de entrega: el primero entre 1 y 7 días desde hoy, y cada siguiente entre 7 y 13 días después del
 * anterior (como máximo uno por semana, en un día que nadie puede adivinar).
 */
export function releaseDays(today, seed, count = COMPLIMENTS_PER_ROUND) {
  const rand = seeded(seed);
  const days = [];
  let day = addDays(today, 1 + Math.floor(rand() * 7));
  for (let i = 0; i < count; i++) {
    days.push(day);
    day = addDays(day, 7 + Math.floor(rand() * 7));
  }
  return days;
}

/** Mis 3 cumplidos para el otro. Solo cuando ya se entregaron todos los anteriores. */
export async function writeCompliments(db, me, key, today, texts, now = new Date()) {
  const list = Array.isArray(texts) ? texts.map((t) => cleanText(t, COMPLIMENT_MAX)) : [];
  if (list.length !== COMPLIMENTS_PER_ROUND || list.some((t) => !t)) {
    return { status: 400, error: `Escribe ${COMPLIMENTS_PER_ROUND} cosas que admiras (hasta ${COMPLIMENT_MAX} caracteres cada una).` };
  }
  const pending = await compliments(db).countDocuments({ couple: key, by: me._id, releaseOn: { $gt: today } });
  if (pending) return { error: 'Minu todavía guarda tus cumplidos anteriores para soltarlos de sorpresa.' };
  const days = releaseDays(today, `${key}:${me._id}:${today}`);
  await compliments(db).insertMany(
    list.map((text, i) => ({ couple: key, by: me._id, to: me.partnerId, byName: me.displayName, text, releaseOn: days[i], createdAt: now, seenAt: null, heartAt: null })),
  );
  return { ok: true };
}

export async function seeCompliment(db, me, key, today, id, now = new Date()) {
  const _id = toId(id);
  if (!_id) return { error: 'Ese cumplido ya no está.' };
  const { matchedCount } = await compliments(db).updateOne(
    { _id, couple: key, to: me._id, releaseOn: { $lte: today } },
    { $set: { seenAt: now } },
  );
  return matchedCount ? { ok: true } : { error: 'Ese cumplido ya no está.' };
}

/** Un 💛 de vuelta: le cuenta a quien lo escribió que llegó al corazón (y alimenta a Minu). */
export async function heartCompliment(db, me, key, today, id, now = new Date()) {
  const _id = toId(id);
  if (!_id) return { error: 'Ese cumplido ya no está.' };
  const c = await compliments(db).findOneAndUpdate(
    { _id, couple: key, to: me._id, releaseOn: { $lte: today }, heartAt: null },
    { $set: { heartAt: now, seenAt: now } },
    { returnDocument: 'after' },
  );
  if (!c) return { error: 'Ese cumplido ya no está o ya le diste tu 💛.' };
  await recordPetEvent(db, key, 'compliment', { day: today, ref: String(_id), now });
  await remember(db, key, { kind: 'compliment', by: c.by, ref: String(_id), day: today, text: c.text, now });
  return { compliment: c };
}

async function complimentsView(db, key, me, today) {
  const [received, mine] = await Promise.all([
    compliments(db).find({ couple: key, to: me._id }).sort({ releaseOn: -1 }).limit(30).toArray(),
    compliments(db).find({ couple: key, by: me._id }).sort({ releaseOn: -1 }).limit(30).toArray(),
  ]);
  const released = received.filter((c) => c.releaseOn <= today);
  const upcoming = received.filter((c) => c.releaseOn > today).map((c) => c.releaseOn).sort();
  const minePending = mine.filter((c) => c.releaseOn > today).length;
  return {
    // Lo que me llegó (el contenido de lo que aún no se suelta nunca sale del servidor)
    received: released.map((c) => ({ id: String(c._id), text: c.text, fromName: c.byName, day: c.releaseOn, seen: Boolean(c.seenAt), heart: Boolean(c.heartAt) })),
    // Solo el día, para que la app avise "Minu tiene una sorpresa" sin abrirla antes
    nextOn: upcoming[0] ?? null,
    // Lo que escribí: cuántos faltan por soltar y cuáles ya llegaron (con su 💛)
    pending: minePending,
    delivered: mine.filter((c) => c.releaseOn <= today).map((c) => ({ id: String(c._id), text: c.text, day: c.releaseOn, heart: Boolean(c.heartAt) })),
    askNew: minePending === 0,
  };
}

// ---------- Lenguajes del amor ----------

export function loveQuiz() {
  return LOVE_QUIZ.map((q) => ({ id: q.id, a: q.a, b: q.b }));
}

/** Guarda mis respuestas como pesos. Se puede repetir cuando quieran; nunca se devuelve como etiqueta. */
export async function answerLoveQuiz(db, me, key, answers, now = new Date()) {
  if (!Array.isArray(answers) || answers.length !== LOVE_QUIZ.length) return { status: 400, error: 'Responde las 10 preguntas.' };
  const valid = answers.every((a, i) => a === LOVE_QUIZ[i].a.lang || a === LOVE_QUIZ[i].b.lang);
  if (!valid) return { status: 400, error: 'Respuestas inválidas.' };
  await couples(db).updateOne({ _id: key }, { $set: { [`loveLangs.${me._id}`]: { weights: loveWeights(answers), at: now } } });
  return { ok: true };
}

// ---------- Estado y borrado ----------

/** Lo de novedad que va dentro de GET /duo (solo cuando Minu ya nació). */
export async function noveltyView(db, couple, me, today, now = new Date()) {
  const [adventure, praise] = await Promise.all([ensureAdventure(db, couple, today, now), complimentsView(db, couple._id, me, today)]);
  const langs = couple.loveLangs ?? {};
  return {
    adventure: serializeAdventure(adventure, me._id, me.partnerId),
    compliments: praise,
    love: {
      mine: Boolean(langs[String(me._id)]),
      partner: Boolean(langs[String(me.partnerId)]),
      // Qué le llega más a mi pareja hoy, dicho como sugerencia (sin nombrar ningún "lenguaje")
      suggestion: dailySuggestion(langs[String(me.partnerId)]?.weights, `${couple._id}:${me._id}:${today}`),
    },
  };
}

/** "Borrar mis datos de Minu": mis cumplidos, mis votos y calificaciones, mi test y las fotos que subí. */
export async function eraseMyNovelty(db, key, me) {
  await Promise.all([
    compliments(db).deleteMany({ couple: key, by: me._id }),
    compliments(db).updateMany({ couple: key, to: me._id }, { $set: { heartAt: null } }),
    adventures(db).updateMany({ couple: key }, { $unset: { [`votes.${me._id}`]: '', [`ratings.${me._id}`]: '' } }),
    adventurePhotos(db).deleteMany({ couple: key, by: me._id }),
    adventures(db).updateMany({ couple: key, photoBy: me._id }, { $set: { photoBy: null, photoAt: null } }),
    couples(db).updateOne({ _id: key }, { $unset: { [`loveLangs.${me._id}`]: '' } }),
  ]);
}
