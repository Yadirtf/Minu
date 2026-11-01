import { couples, promises, toId, unsaid } from './db.js';
import { cleanText } from './daily.js';
import { recordPetEvent } from './pet.js';

/**
 * Confianza y reparación con Minu (entrega 3): hablar de lo difícil sin miedo y volver a encontrarse
 * después de una discusión. Se apoya en Gottman: arranque suave ("siento… cuando… me ayudaría…"), pausas
 * de al menos 20 minutos cuando el cuerpo está activado, intentos de reparación, y confianza que se
 * construye con promesas pequeñas cumplidas.
 *
 * Reglas que no se rompen:
 * - Nada de esto entra a la memoria de la pareja (remember) ni a los mensajes personalizados.
 * - Nada resta energía ni queda como "pelea" en Recuerdos. Solo una promesa cumplida y agradecida suma.
 * - Sin "visto": quien escribe al Buzón nunca sabe si el otro abrió la carta, solo si eligió agradecerla.
 * - Lo que no se cumple se borra solo, sin reproche ni registro.
 */

// ---------- Buzón de lo no dicho ----------

export const UNSAID_LIMITS = { feel: 200, when: 200, need: 200, reply: 300 };
/** Cuánto vive una carta del Buzón (después se borra sola, abierta o no). */
export const UNSAID_DAYS = 30;
export const UNSAID_PER_DAY = 2;

const daysAgo = (now, days) => new Date(now.getTime() - days * 86_400_000);
const hoursAgo = (now, hours) => new Date(now.getTime() - hours * 3_600_000);

/**
 * Una carta guiada en tres partes. Solo una a la vez sin agradecer: lo difícil se habla de a poco, y así
 * nadie recibe una pila de cartas pendientes.
 */
export async function sendUnsaid(db, me, key, today, fields, now = new Date()) {
  const feel = cleanText(fields?.feel, UNSAID_LIMITS.feel);
  const when = cleanText(fields?.when, UNSAID_LIMITS.when);
  const need = cleanText(fields?.need, UNSAID_LIMITS.need);
  if (!feel || !when || !need) return { status: 400, error: 'Completa las tres partes: lo que sientes, cuándo y qué te ayudaría.' };
  const since = daysAgo(now, UNSAID_DAYS);
  const [waiting, sentToday] = await Promise.all([
    unsaid(db).countDocuments({ couple: key, from: me._id, thankedAt: null, createdAt: { $gte: since } }),
    unsaid(db).countDocuments({ couple: key, from: me._id, day: today }),
  ]);
  if (waiting > 0) return { error: 'Minu todavía lleva tu carta anterior. Lo importante se habla de a poco 💛' };
  if (sentToday >= UNSAID_PER_DAY) return { error: 'Por hoy ya fue suficiente. Mañana Minu vuelve a llevar cartas.' };
  const doc = {
    couple: key, from: me._id, to: me.partnerId, fromName: me.displayName, feel, when, need, day: today,
    createdAt: now, openedAt: null, thankedAt: null, reply: null,
  };
  const { insertedId } = await unsaid(db).insertOne(doc);
  return { id: String(insertedId) };
}

/** Abrir una carta que me llegó (cuando yo elija). Devuelve su contenido. */
export async function openUnsaid(db, me, key, id, now = new Date()) {
  const _id = toId(id);
  if (!_id) return { error: 'Esa carta ya no está.' };
  await unsaid(db).updateOne({ _id, couple: key, to: me._id, openedAt: null }, { $set: { openedAt: now } });
  const letter = await unsaid(db).findOne({ _id, couple: key, to: me._id });
  return letter ? { letter } : { error: 'Esa carta ya no está.' };
}

/** "Gracias por contármelo": el primer paso antes de responder, y lo único que sabe quien la escribió. */
export async function thankUnsaid(db, me, key, id, now = new Date()) {
  const _id = toId(id);
  if (!_id) return { error: 'Esa carta ya no está.' };
  const { modifiedCount } = await unsaid(db).updateOne(
    { _id, couple: key, to: me._id, openedAt: { $ne: null }, thankedAt: null },
    { $set: { thankedAt: now } },
  );
  return modifiedCount ? { ok: true } : { error: 'Abre la carta primero (o ya la agradeciste).' };
}

/** Una respuesta, opcional y una sola vez, después de agradecer. */
export async function replyUnsaid(db, me, key, id, rawText, now = new Date()) {
  const text = cleanText(rawText, UNSAID_LIMITS.reply);
  if (!text) return { status: 400, error: `Escribe tu respuesta (hasta ${UNSAID_LIMITS.reply} caracteres).` };
  const _id = toId(id);
  if (!_id) return { error: 'Esa carta ya no está.' };
  const { modifiedCount } = await unsaid(db).updateOne(
    { _id, couple: key, to: me._id, thankedAt: { $ne: null }, reply: null },
    { $set: { reply: { text, at: now } } },
  );
  return modifiedCount ? { ok: true } : { error: 'Primero agradece la carta (o ya respondiste).' };
}

async function unsaidView(db, key, me, now) {
  const since = daysAgo(now, UNSAID_DAYS);
  await unsaid(db).deleteMany({ couple: key, createdAt: { $lt: since } });
  const [incoming, sent] = await Promise.all([
    unsaid(db).find({ couple: key, to: me._id }).sort({ createdAt: -1 }).limit(10).toArray(),
    unsaid(db).find({ couple: key, from: me._id }).sort({ createdAt: -1 }).limit(10).toArray(),
  ]);
  const content = (u) => ({ feel: u.feel, when: u.when, need: u.need });
  return {
    // Lo sellado solo dice que hay una carta y de quién; el contenido llega al abrirla
    incoming: incoming.map((u) => ({
      id: String(u._id),
      fromName: u.fromName,
      at: u.createdAt.getTime(),
      sealed: !u.openedAt,
      ...(u.openedAt && content(u)),
      thanked: Boolean(u.thankedAt),
      reply: u.reply?.text ?? null,
    })),
    // Mis cartas: nunca si la abrieron, solo si eligieron agradecerla y su respuesta
    sent: sent.map((u) => ({ id: String(u._id), at: u.createdAt.getTime(), ...content(u), thanked: Boolean(u.thankedAt), reply: u.reply?.text ?? null })),
  };
}

// ---------- Tregua ----------

export const TRUCE_MINUTES = 20;
export const TRUCE_EXTENSIONS = 2;
export const REPAIR_MAX = 140;
/** Una tregua olvidada se apaga sola después de esto. */
export const TRUCE_HOURS = 12;
/** Cuánto se queda en pantalla el reencuentro antes de desaparecer. */
export const TOGETHER_MINUTES = 30;

const minutesFrom = (date, minutes) => new Date(date.getTime() + minutes * 60_000);

function activeTruce(couple, now) {
  const t = couple?.truce;
  if (!t || t.startedAt < hoursAgo(now, TRUCE_HOURS)) return null;
  if (t.closedAt && t.closedAt < minutesFrom(now, -TOGETHER_MINUTES)) return null;
  return t;
}

/** "Necesito una pausa": cualquiera la puede pedir; si ya hay una en curso, se respeta esa. */
export async function startTruce(db, me, key, now = new Date()) {
  const couple = await couples(db).findOne({ _id: key }, { projection: { truce: 1 } });
  const current = activeTruce(couple, now);
  if (current && !current.closedAt) return { already: true };
  const truce = { by: me._id, byName: me.displayName, startedAt: now, endsAt: minutesFrom(now, TRUCE_MINUTES), extensions: 0, repairs: {}, closedAt: null };
  await couples(db).updateOne({ _id: key }, { $set: { truce } });
  return { started: true };
}

/** Quien pidió la pausa puede pedir 20 minutos más (hasta dos veces) o decir que ya está listo. */
export async function adjustTruce(db, me, key, action, now = new Date()) {
  const couple = await couples(db).findOne({ _id: key }, { projection: { truce: 1 } });
  const t = activeTruce(couple, now);
  if (!t || t.closedAt) return { error: 'No hay una pausa en curso.' };
  if (!t.by.equals(me._id)) return { status: 403, error: 'Solo quien pidió la pausa puede cambiarla.' };
  if (action === 'extend') {
    if (t.extensions >= TRUCE_EXTENSIONS) return { error: 'Ya pediste más tiempo dos veces. Cuando estés listo, Minu los ayuda a reencontrarse.' };
    const base = t.endsAt > now ? t.endsAt : now;
    await couples(db).updateOne({ _id: key }, { $set: { 'truce.endsAt': minutesFrom(base, TRUCE_MINUTES), 'truce.repairs': {} }, $inc: { 'truce.extensions': 1 } });
    return { extended: true };
  }
  if (action === 'ready') {
    if (t.endsAt > now) await couples(db).updateOne({ _id: key }, { $set: { 'truce.endsAt': now } });
    return { ready: true };
  }
  return { status: 400, error: 'Acción inválida.' };
}

/** Al terminar la calma, cada uno elige una frase de reparación. Cuando los dos la mandan, se reencuentran. */
export async function repairTruce(db, me, key, rawText, now = new Date()) {
  const text = cleanText(rawText, REPAIR_MAX);
  if (!text) return { status: 400, error: `Elige o escribe una frase (hasta ${REPAIR_MAX} caracteres).` };
  const couple = await couples(db).findOne({ _id: key }, { projection: { truce: 1 } });
  const t = activeTruce(couple, now);
  if (!t || t.closedAt) return { error: 'No hay una pausa en curso.' };
  if (t.endsAt > now) return { error: 'Todavía es momento de calma. Respira con Minu un poco más.' };
  const field = `truce.repairs.${me._id}`;
  await couples(db).updateOne({ _id: key }, { $set: { [field]: { text, at: now } } });
  const after = await couples(db).findOne({ _id: key }, { projection: { truce: 1 } });
  const together = Object.keys(after.truce?.repairs ?? {}).length >= 2;
  if (together) await couples(db).updateOne({ _id: key, 'truce.closedAt': null }, { $set: { 'truce.closedAt': now } });
  return { together };
}

function truceView(couple, me, now) {
  const t = activeTruce(couple, now);
  if (!t) return null;
  const repairs = t.repairs ?? {};
  const partner = Object.entries(repairs).find(([id]) => id !== String(me._id))?.[1];
  const phase = t.closedAt ? 'together' : t.endsAt > now ? 'calm' : 'repair';
  return {
    mine: t.by.equals(me._id),
    byName: t.byName,
    startedAt: t.startedAt.getTime(),
    endsAt: t.endsAt.getTime(),
    phase,
    canExtend: t.by.equals(me._id) && !t.closedAt && t.extensions < TRUCE_EXTENSIONS,
    myRepair: repairs[String(me._id)]?.text ?? null,
    partnerRepair: partner?.text ?? null,
  };
}

// ---------- Promesas pequeñas ----------

export const PROMISE_MAX = 120;
export const OPEN_PROMISES = 5;
/** Una promesa sin cumplir se borra sola este tiempo después de su hora (sin reproche ni registro). */
export const PROMISE_GRACE_HOURS = 48;
/** Una cumplida se queda unos días para celebrarla y luego deja su lugar. */
export const KEPT_DAYS = 3;
export const PROMISE_AHEAD_DAYS = 30;

export async function makePromise(db, me, key, rawText, dueAt, now = new Date()) {
  const text = cleanText(rawText, PROMISE_MAX);
  if (!text) return { status: 400, error: `Escribe tu promesa (hasta ${PROMISE_MAX} caracteres).` };
  const due = new Date(Number(dueAt));
  if (!Number.isFinite(due.getTime()) || due < minutesFrom(now, -1) || due > minutesFrom(now, PROMISE_AHEAD_DAYS * 1440)) {
    return { status: 400, error: 'Elige para cuándo (dentro de los próximos 30 días).' };
  }
  const open = await promises(db).countDocuments({ couple: key, by: me._id, keptAt: null });
  if (open >= OPEN_PROMISES) return { error: 'Ya tienes varias promesas en camino. Cumple alguna primero 🤞' };
  const doc = { couple: key, by: me._id, byName: me.displayName, text, dueAt: due, createdAt: now, keptAt: null, confirmedAt: null };
  const { insertedId } = await promises(db).insertOne(doc);
  return { id: String(insertedId), text, dueAt: due.getTime() };
}

/** Quien prometió dice "¡Cumplí!". Falta que el otro lo agradezca para que Minu lo celebre. */
export async function keepPromise(db, me, key, id, now = new Date()) {
  const _id = toId(id);
  if (!_id) return { error: 'Esa promesa ya no está.' };
  const p = await promises(db).findOneAndUpdate({ _id, couple: key, by: me._id, keptAt: null }, { $set: { keptAt: now } }, { returnDocument: 'after' });
  return p ? { promise: p } : { error: 'Esa promesa ya no está o ya la marcaste.' };
}

/** El otro confirma con cariño (aunque quien prometió no la haya marcado): suma a Minu y al contador de los dos. */
export async function confirmPromise(db, me, key, today, id, now = new Date()) {
  const _id = toId(id);
  if (!_id) return { error: 'Esa promesa ya no está.' };
  const p = await promises(db).findOneAndUpdate(
    { _id, couple: key, by: me.partnerId, confirmedAt: null },
    [{ $set: { confirmedAt: now, keptAt: { $ifNull: ['$keptAt', now] } } }],
    { returnDocument: 'after' },
  );
  if (!p) return { error: 'Esa promesa ya no está o ya la agradeciste.' };
  await couples(db).updateOne({ _id: key }, { $inc: { promisesKept: 1 } });
  await recordPetEvent(db, key, 'promise', { day: today, ref: String(_id), now });
  return { promise: p };
}

/** Quien prometió puede soltarla (sin dejar rastro), por ejemplo si cambió el plan. */
export async function dropPromise(db, me, key, id) {
  const _id = toId(id);
  if (!_id) return { error: 'Esa promesa ya no está.' };
  const { deletedCount } = await promises(db).deleteOne({ _id, couple: key, by: me._id, confirmedAt: null });
  return deletedCount ? { ok: true } : { error: 'Esa promesa ya no está.' };
}

async function promisesView(db, couple, me, now) {
  const key = couple._id;
  await promises(db).deleteMany({
    couple: key,
    $or: [
      { keptAt: null, dueAt: { $lt: hoursAgo(now, PROMISE_GRACE_HOURS) } },
      { keptAt: { $lt: daysAgo(now, KEPT_DAYS) } },
    ],
  });
  const list = await promises(db).find({ couple: key }).sort({ dueAt: 1 }).limit(20).toArray();
  return {
    kept: couple.promisesKept ?? 0,
    items: list.map((p) => ({
      id: String(p._id),
      text: p.text,
      mine: p.by.equals(me._id),
      byName: p.byName,
      dueAt: p.dueAt.getTime(),
      kept: Boolean(p.keptAt),
      confirmed: Boolean(p.confirmedAt),
    })),
  };
}

// ---------- Estado y borrado ----------

/** Lo de confianza que va dentro de GET /duo (solo cuando Minu ya nació). */
export async function trustView(db, couple, me, now = new Date()) {
  const [letters, promiseState] = await Promise.all([unsaidView(db, couple._id, me, now), promisesView(db, couple, me, now)]);
  return { unsaid: letters, truce: truceView(couple, me, now), promises: promiseState };
}

/** "Borrar mis datos de Minu": mis cartas, mis respuestas, mis promesas y cualquier tregua en curso. */
export async function eraseMyTrust(db, key, me) {
  await Promise.all([
    unsaid(db).deleteMany({ couple: key, from: me._id }),
    unsaid(db).updateMany({ couple: key, to: me._id }, { $set: { reply: null } }),
    promises(db).deleteMany({ couple: key, by: me._id }),
    couples(db).updateOne({ _id: key }, { $unset: { truce: '' } }),
  ]);
}
