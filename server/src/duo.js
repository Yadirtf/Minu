import { capsules, coupleKey, couples, streakDays } from './db.js';

/** Rescates por mes. El día 1 vuelven a ser exactamente estos: no se acumulan. */
export const MONTHLY_RESCUES = 3;
/** Reto 1: media hora escuchando juntos. */
export const LISTEN_GOAL_SEC = 30 * 60;
export const RESCUE_TYPES = new Set(['listen', 'capsule']);

/** "AAAA-MM-DD" en la zona horaria de la pareja. */
export function localDay(timeZone, date = new Date()) {
  try {
    return new Intl.DateTimeFormat('en-CA', { timeZone: timeZone || 'UTC', year: 'numeric', month: '2-digit', day: '2-digit' }).format(date);
  } catch {
    return date.toISOString().slice(0, 10);
  }
}

export function validTimeZone(timeZone) {
  if (!timeZone || typeof timeZone !== 'string' || timeZone.length > 64) return false;
  try {
    new Intl.DateTimeFormat('en', { timeZone });
    return true;
  } catch {
    return false;
  }
}

export function addDays(day, n) {
  const d = new Date(`${day}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + n);
  return d.toISOString().slice(0, 10);
}

export function emptyStreak() {
  return { current: 0, best: 0, lastDay: null, rescue: null, broken: null, saved: null };
}

/**
 * Avanza la racha día por día desde el último contado hasta [today]. Función pura.
 * - Un día cuenta cuando [complete](día) (los dos dedicaron). Hoy cuenta apenas se completa.
 * - Un día olvidado con racha viva abre un rescate para el día siguiente (el día del reto), si es hoy
 *   y quedan rescates en el mes. Si no, el hilo se rompe.
 * - Un rescate que no se cumplió en su día rompe el hilo.
 */
export function evaluateStreak(input, today, complete, rescuesLeft) {
  const s = { ...emptyStreak(), ...input };
  if (s.rescue) {
    if (today <= s.rescue.day) return s;
    s.broken = { on: s.rescue.day, lost: s.current, reason: 'timeout' };
    s.current = 0;
    s.lastDay = s.rescue.day;
    s.rescue = null;
  }
  for (let d = s.lastDay ? addDays(s.lastDay, 1) : today; d <= today; d = addDays(d, 1)) {
    if (complete(d)) {
      s.current += 1;
      s.best = Math.max(s.best, s.current);
      s.lastDay = d;
      continue;
    }
    if (d === today) break; // hoy todavía está en juego
    if (s.current === 0) {
      s.lastDay = d;
      continue;
    }
    const challengeDay = addDays(d, 1);
    if (challengeDay === today && rescuesLeft(today) > 0) {
      s.rescue = { missedDay: d, day: today, type: null, chosenByName: null, listenedSec: 0 };
      return s;
    }
    s.broken = { on: challengeDay, lost: s.current, reason: challengeDay === today ? 'no_rescues' : 'missed' };
    s.current = 0;
    s.lastDay = d;
  }
  return s;
}

/** Reto cumplido: se salvan el día olvidado y el día del reto. */
export function completeRescue(s) {
  const current = s.current + 2;
  return {
    ...s,
    current,
    best: Math.max(s.best, current),
    lastDay: s.rescue.day,
    rescue: null,
    saved: { day: s.rescue.day, type: s.rescue.type },
  };
}

function rescuesLeftFor(couple, day) {
  const r = couple?.rescues;
  return r?.month === day.slice(0, 7) ? Math.max(0, MONTHLY_RESCUES - r.used) : MONTHLY_RESCUES;
}

/**
 * Lee la pareja, pone la racha al día y la guarda si cambió.
 * Devuelve { couple, key, today }.
 */
export async function refreshStreak(db, a, b, now = new Date()) {
  const key = coupleKey(a, b);
  const couple = (await couples(db).findOne({ _id: key })) ?? { _id: key };
  const today = localDay(couple.tz, now);
  const before = couple.streak ?? emptyStreak();
  const from = before.lastDay ? addDays(before.lastDay, 1) : today;
  const days = await streakDays(db).find({ couple: key, day: { $gte: from, $lte: today } }).toArray();
  const complete = new Set(days.filter((d) => d.by.length >= 2).map((d) => d.day));
  const streak = evaluateStreak(before, today, (d) => complete.has(d), (d) => rescuesLeftFor(couple, d));
  if (JSON.stringify(streak) !== JSON.stringify(couple.streak)) {
    await couples(db).updateOne(
      { _id: key },
      { $set: { streak, members: [a, b] }, $setOnInsert: { createdAt: now } },
      { upsert: true },
    );
    couple.streak = streak;
  }
  return { couple, key, today };
}

/** Marca la mitad del día de quien dedicó. */
export async function markDedication(db, me, partner, now = new Date()) {
  const { couple, key, today } = await refreshStreak(db, me, partner, now);
  if (couple.streak.rescue?.day === today) return { blocked: true };
  await streakDays(db).updateOne(
    { _id: `${key}:${today}` },
    { $addToSet: { by: String(me) }, $setOnInsert: { couple: key, day: today } },
    { upsert: true },
  );
  return { blocked: false, ...(await refreshStreak(db, me, partner, now)) };
}

/** Elegir (o cambiar, si nadie ha empezado) el reto del día. Gasta un rescate la primera vez. */
export async function chooseRescue(db, me, type, now = new Date()) {
  const { couple, key, today } = await refreshStreak(db, me._id, me.partnerId, now);
  const rescue = couple.streak.rescue;
  if (!rescue) return { error: 'Hoy no hay ningún reto pendiente.' };
  const capsule = rescue.type === 'capsule' ? await capsules(db).findOne({ _id: `${key}:${today}` }) : null;
  const started = rescue.listenedSec > 0 || Object.keys(capsule?.answers ?? {}).length > 0;
  if (started && rescue.type !== type) return { error: 'El reto ya empezó: no se puede cambiar.' };
  const first = rescue.type === null;
  const month = today.slice(0, 7);
  const used = couple.rescues?.month === month ? couple.rescues.used : 0;
  const update = {
    'streak.rescue.type': type,
    'streak.rescue.chosenByName': me.displayName,
    ...(first && { rescues: { month, used: used + 1 } }),
  };
  await couples(db).updateOne({ _id: key }, { $set: update });
  return { first };
}

/** "Dejar que se rompa": hoy pueden volver a dedicar y empezar el día 1. */
export async function giveUpRescue(db, me, now = new Date()) {
  const { couple, key } = await refreshStreak(db, me._id, me.partnerId, now);
  const s = couple.streak;
  if (!s.rescue) return false;
  const streak = { ...s, broken: { on: s.rescue.day, lost: s.current, reason: 'gave_up' }, current: 0, lastDay: s.rescue.missedDay, rescue: null };
  await couples(db).updateOne({ _id: key }, { $set: { streak } });
  return true;
}

/**
 * Tiempo escuchando juntos (lo mide el servidor en tiempo real). Suma al total de la pareja y,
 * si hoy toca el Reto 1, a su progreso. Devuelve true si con esto salvaron la racha.
 */
export async function addListening(db, a, b, seconds, now = new Date()) {
  if (seconds <= 0) return false;
  const key = coupleKey(a, b);
  await couples(db).updateOne(
    { _id: key },
    { $inc: { listenSec: seconds }, $setOnInsert: { members: [a, b], createdAt: now } },
    { upsert: true },
  );
  const { couple, today } = await refreshStreak(db, a, b, now);
  const rescue = couple.streak.rescue;
  if (rescue?.type !== 'listen' || rescue.day !== today) return false;
  const listenedSec = rescue.listenedSec + seconds;
  if (listenedSec < LISTEN_GOAL_SEC) {
    await couples(db).updateOne({ _id: key }, { $set: { 'streak.rescue.listenedSec': listenedSec } });
    return false;
  }
  await couples(db).updateOne({ _id: key }, { $set: { streak: completeRescue(couple.streak) } });
  return true;
}

/** Lo que ve cada uno de la racha. */
export async function streakView(db, me, now = new Date()) {
  const { couple, key, today } = await refreshStreak(db, me._id, me.partnerId, now);
  const s = couple.streak;
  const todayDoc = await streakDays(db).findOne({ _id: `${key}:${today}` });
  const by = todayDoc?.by ?? [];
  return {
    today,
    count: s.current,
    best: s.best,
    todayMine: by.includes(String(me._id)),
    todayPartner: by.includes(String(me.partnerId)),
    state: s.rescue ? (s.rescue.type ? 'rescuing' : 'at_risk') : 'active',
    rescue: s.rescue && { ...s.rescue, goalSec: LISTEN_GOAL_SEC },
    rescuesLeft: rescuesLeftFor(couple, today),
    broken: s.broken,
    saved: s.saved,
    listenSec: couple.listenSec ?? 0,
  };
}
