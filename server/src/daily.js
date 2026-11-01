import { goodNews, gratitude, questions, toId, touches } from './db.js';
import { addDays } from './duo.js';
import { pickQuestion, TOUCH_KINDS } from './content.js';
import { forgetBy, remember } from './memory.js';
import { recordPetEvent } from './pet.js';

/**
 * Conexión diaria con Minu (entrega 2). Se basa en las "ofertas de conexión" de Gottman: lo que acerca a
 * una pareja no es mandar gestos sino que el otro los responda. Por eso Minu se alimenta de gestos
 * respondidos (un toque devuelto, una pregunta que abren los dos, una buena noticia celebrada) y nunca
 * muestra un historial de lo que quedó sin respuesta.
 *
 * Todas las funciones reciben [key] (la pareja) y [today] (su día local) ya calculados por la ruta.
 */

export const TOUCHES_PER_DAY = 10; // por persona, para que no se vuelva spam
export const NEWS_PER_DAY = 3;
export const NEWS_DAYS = 7; // cuánto tiempo se ven las buenas noticias en Dúo
export const LIMITS = { answer: 500, reaction: 120, thanks: 140, news: 280, cheer: 200 };

/** Texto limpio de 1 a [max] caracteres (respeta saltos de línea, pero no más de uno vacío seguido). */
export function cleanText(value, max) {
  const text = String(value ?? '')
    .replace(/\r/g, '')
    .replace(/[ \t]+/g, ' ')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
  const length = [...text].length;
  return length >= 1 && length <= max ? text : null;
}

const isDuplicate = (e) => e?.code === 11000;

// ---------- Toque de Minu ----------

export async function sendTouch(db, me, key, today, kind, now = new Date()) {
  if (!TOUCH_KINDS.includes(kind)) return { error: 'Ese gesto no existe.', status: 400 };
  const sent = await touches(db).countDocuments({ couple: key, from: me._id, day: today });
  if (sent >= TOUCHES_PER_DAY) return { error: 'Minu ya llevó muchos gestos hoy. Mañana sigue 💛' };
  const doc = { couple: key, from: me._id, to: me.partnerId, fromName: me.displayName, kind, day: today, createdAt: now, reply: null };
  const { insertedId } = await touches(db).insertOne(doc);
  return { touch: { ...doc, _id: insertedId } };
}

/** Devolver un toque: solo quien lo recibió, una vez. Es lo que alimenta a Minu. */
export async function replyTouch(db, me, key, today, id, kind, now = new Date()) {
  if (!TOUCH_KINDS.includes(kind)) return { error: 'Ese gesto no existe.', status: 400 };
  const _id = toId(id);
  if (!_id) return { error: 'Ese toque ya no está.' };
  const touch = await touches(db).findOneAndUpdate(
    { _id, couple: key, to: me._id, reply: null },
    { $set: { reply: { kind, at: now } } },
    { returnDocument: 'after' },
  );
  if (!touch) return { error: 'Ese toque ya no está.' };
  await recordPetEvent(db, key, 'touch', { day: today, ref: String(_id), now });
  return { touch };
}

async function touchView(db, key, me, today) {
  const [incoming, last, sent] = await Promise.all([
    touches(db).find({ couple: key, to: me._id, day: today, reply: null }).sort({ createdAt: 1 }).limit(4).toArray(),
    touches(db).find({ couple: key, from: me._id, day: today }).sort({ createdAt: -1 }).limit(1).next(),
    touches(db).countDocuments({ couple: key, from: me._id, day: today }),
  ]);
  return {
    incoming: incoming.map((t) => ({ id: String(t._id), kind: t.kind, fromName: t.fromName, at: t.createdAt.getTime() })),
    // Solo el último que mandé hoy: si ya lo devolvieron, la app celebra. Nunca una lista de "sin respuesta".
    last: last ? { id: String(last._id), kind: last.kind, at: last.createdAt.getTime(), reply: last.reply?.kind ?? null } : null,
    left: Math.max(0, TOUCHES_PER_DAY - sent),
  };
}

// ---------- Pregunta del día ----------

/** La pregunta de hoy para la pareja (la crea si no existe). [stage] decide si ya hay preguntas profundas. */
export async function ensureQuestion(db, key, today, stage, now = new Date()) {
  const _id = `${key}:${today}`;
  const existing = await questions(db).findOne({ _id });
  if (existing) return existing;
  const used = await questions(db)
    .find({ couple: key, day: { $gte: addDays(today, -365) } }, { projection: { qid: 1 } })
    .toArray();
  const q = pickQuestion({ stage, usedIds: new Set(used.map((u) => u.qid)), seed: _id });
  try {
    await questions(db).insertOne({
      _id, couple: key, day: today, qid: q.id, level: q.level, text: q.text, answers: {}, reactions: {}, openedAt: null, createdAt: now,
    });
  } catch (e) {
    if (!isDuplicate(e)) throw e; // la creó el otro al mismo tiempo
  }
  return questions(db).findOne({ _id });
}

/** Mi respuesta queda sellada; cuando responden los dos, se abre para ambos y Minu se alegra. */
export async function answerQuestion(db, me, key, today, stage, rawText, now = new Date()) {
  const text = cleanText(rawText, LIMITS.answer);
  if (!text) return { status: 400, error: `Escribe tu respuesta (hasta ${LIMITS.answer} caracteres).` };
  const q = await ensureQuestion(db, key, today, stage, now);
  const field = `answers.${me._id}`;
  const { modifiedCount } = await questions(db).updateOne({ _id: q._id, [field]: { $exists: false } }, { $set: { [field]: { text, at: now } } });
  if (!modifiedCount) return { error: 'Ya sellaste tu respuesta de hoy.' };
  await remember(db, key, { kind: 'answer', by: me._id, ref: `${today}:${me._id}`, day: today, text, data: { question: q.text, level: q.level }, now });
  const updated = await questions(db).findOne({ _id: q._id });
  let opened = false;
  if (Object.keys(updated.answers).length >= 2) {
    const r = await questions(db).updateOne({ _id: q._id, openedAt: null }, { $set: { openedAt: now } });
    opened = r.modifiedCount > 0;
    if (opened) await recordPetEvent(db, key, 'question', { day: today, now });
  }
  return { opened };
}

/** Reaccionar a lo que respondió el otro (una frase corta, más que un emoji). Se puede cambiar. */
export async function reactQuestion(db, me, key, today, rawText, now = new Date()) {
  const text = cleanText(rawText, LIMITS.reaction);
  if (!text) return { status: 400, error: 'Escribe una reacción corta.' };
  const { matchedCount } = await questions(db).updateOne(
    { _id: `${key}:${today}`, openedAt: { $ne: null } },
    { $set: { [`reactions.${me._id}`]: { text, at: now } } },
  );
  return matchedCount ? { ok: true } : { error: 'La pregunta se abre cuando respondan los dos.' };
}

export function serializeQuestion(q, me, partner) {
  const a = q.answers ?? {};
  const r = q.reactions ?? {};
  const opened = Boolean(q.openedAt);
  return {
    day: q.day,
    id: q.qid,
    text: q.text,
    level: q.level,
    mine: a[String(me)]?.text ?? null,
    partnerDone: Boolean(a[String(partner)]),
    partner: opened ? a[String(partner)]?.text ?? null : null,
    opened,
    myReaction: r[String(me)]?.text ?? null,
    partnerReaction: opened ? r[String(partner)]?.text ?? null : null,
  };
}

/** Las preguntas que ya abrieron, de la más nueva a la más vieja (para releerlas). */
export async function openedQuestions(db, key, me, partner, limit = 60) {
  const list = await questions(db).find({ couple: key, openedAt: { $ne: null } }).sort({ day: -1 }).limit(limit).toArray();
  return list.map((q) => serializeQuestion(q, me, partner));
}

// ---------- Gracias por... ----------

/** Una apreciación al día por persona. Llena el frasco del mes, que luego entra a Recuerdos. */
export async function giveThanks(db, me, key, today, rawText, now = new Date()) {
  const text = cleanText(rawText, LIMITS.thanks);
  if (!text) return { status: 400, error: `Escribe por qué das gracias (hasta ${LIMITS.thanks} caracteres).` };
  try {
    await gratitude(db).insertOne({ _id: `${key}:${today}:${me._id}`, couple: key, day: today, month: today.slice(0, 7), by: me._id, text, createdAt: now });
  } catch (e) {
    if (isDuplicate(e)) return { error: 'Hoy ya dejaste tu gracias. Mañana hay más espacio en el frasco.' };
    throw e;
  }
  await recordPetEvent(db, key, 'thanks', { day: today, by: me._id, now });
  await remember(db, key, { kind: 'thanks', by: me._id, ref: `${today}:${me._id}`, day: today, text, now });
  return { ok: true };
}

/** El frasco de un mes ("AAAA-MM"): solo gestos positivos, del más nuevo al más viejo. */
export async function jarOf(db, key, me, month) {
  const list = await gratitude(db).find({ couple: key, month }).sort({ day: -1, createdAt: -1 }).toArray();
  return list.map((g) => ({ text: g.text, byMe: g.by.equals(me), day: g.day }));
}

// ---------- Te cuento algo bueno ----------

export async function shareNews(db, me, key, today, rawText, now = new Date()) {
  const text = cleanText(rawText, LIMITS.news);
  if (!text) return { status: 400, error: `Cuéntalo en hasta ${LIMITS.news} caracteres.` };
  const count = await goodNews(db).countDocuments({ couple: key, by: me._id, day: today });
  if (count >= NEWS_PER_DAY) return { error: '¡Qué día! Guarda algo para mañana 💛' };
  const { insertedId } = await goodNews(db).insertOne({ couple: key, by: me._id, byName: me.displayName, text, day: today, createdAt: now, reply: null });
  await remember(db, key, { kind: 'news', by: me._id, ref: String(insertedId), day: today, text, now });
  return { id: String(insertedId) };
}

/** Celebrar la buena noticia del otro (respuesta activa y constructiva). Una vez por noticia. */
export async function cheerNews(db, me, key, today, id, rawText, now = new Date()) {
  const text = cleanText(rawText, LIMITS.cheer);
  if (!text) return { status: 400, error: 'Escribe algo para celebrar.' };
  const _id = toId(id);
  if (!_id) return { error: 'Esa noticia ya no está.' };
  const news = await goodNews(db).findOneAndUpdate(
    { _id, couple: key, by: me.partnerId, reply: null },
    { $set: { reply: { text, at: now } } },
    { returnDocument: 'after' },
  );
  if (!news) return { error: 'Esa noticia ya no está o ya la celebraste.' };
  await recordPetEvent(db, key, 'cheer', { day: today, by: me._id, ref: String(_id), now });
  return { ok: true };
}

async function newsView(db, key, me, now) {
  const since = new Date(now.getTime() - NEWS_DAYS * 86_400_000);
  const list = await goodNews(db).find({ couple: key, createdAt: { $gte: since } }).sort({ createdAt: -1 }).limit(10).toArray();
  return list.map((n) => ({
    id: String(n._id),
    text: n.text,
    byMe: n.by.equals(me._id),
    byName: n.byName,
    at: n.createdAt.getTime(),
    reply: n.reply ? { text: n.reply.text, at: n.reply.at.getTime() } : null,
  }));
}

// ---------- Estado y borrado ----------

/** Todo lo del día que va dentro de GET /duo (solo cuando Minu ya nació). */
export async function dailyView(db, key, me, today, stage, now = new Date()) {
  const [touch, question, jarItems, news] = await Promise.all([
    touchView(db, key, me, today),
    ensureQuestion(db, key, today, stage, now),
    jarOf(db, key, me._id, today.slice(0, 7)),
    newsView(db, key, me, now),
  ]);
  return {
    touch,
    question: serializeQuestion(question, me._id, me.partnerId),
    jar: { month: today.slice(0, 7), mine: jarItems.find((g) => g.byMe && g.day === today)?.text ?? null, items: jarItems },
    news,
  };
}

/** "Borrar mis datos de Minu": mis toques, respuestas, gracias, noticias y lo que Minu recordaba de mí. */
export async function eraseMyDaily(db, key, me) {
  await Promise.all([
    touches(db).deleteMany({ couple: key, from: me._id }),
    touches(db).updateMany({ couple: key, to: me._id }, { $set: { reply: null } }),
    questions(db).updateMany({ couple: key }, { $unset: { [`answers.${me._id}`]: '', [`reactions.${me._id}`]: '' } }),
    gratitude(db).deleteMany({ couple: key, by: me._id }),
    goodNews(db).deleteMany({ couple: key, by: me._id }),
    goodNews(db).updateMany({ couple: key, by: me.partnerId }, { $set: { reply: null } }),
    forgetBy(db, key, me._id),
  ]);
}
