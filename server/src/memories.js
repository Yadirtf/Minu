import { capsules, cleanSong, coupleKey, couples, dedications } from './db.js';
import { addDays, completeRescue, localDay, refreshStreak } from './duo.js';
import { recordPetEvent } from './pet.js';

/** Banco de preguntas de la cápsula: cada rescate toma tres que la pareja no haya respondido. */
export const CAPSULE_PROMPTS = [
  'La canción que sonaba en mi cabeza el día que te conocí.',
  'La que te pondría si tuvieras un día difícil.',
  'La que me gustaría que bailemos en 10 años.',
  'La canción que me recuerda tu risa.',
  'La que escucho cuando te extraño.',
  'La que pondría en un viaje largo contigo.',
  'Una canción que describe cómo me siento contigo.',
  'La que me hubiera gustado dedicarte antes de que fuéramos novios.',
  'La canción de una noche que no quiero olvidar.',
  'La que sonaría en la película de nuestra historia.',
  'La que me pone de buen humor y quiero compartir contigo.',
  'Una canción que te queda perfecta.',
  'La que escucharía contigo un domingo por la mañana.',
  'La que pondría para cocinar juntos.',
  'Una canción de mi infancia que quiero que conozcas.',
  'La que me hace pensar en nuestro futuro.',
  'La canción que cantaría a todo pulmón contigo en el carro.',
  'Una canción triste que me gusta y quiero que entiendas por qué.',
  'La que sonaría en nuestra boda (o en una fiesta solo para los dos).',
  'La que te pondría para que te duermas.',
  'Una canción que describe nuestra primera cita.',
  'La que me recuerda un lugar donde estuvimos juntos.',
  'Una canción que nunca te he mostrado y me da pena que oigas.',
  'La que pondría para despertarte con un beso.',
  'Una canción que habla de algo que admiro de ti.',
  'La canción que elegiría si solo pudiera escuchar una contigo hoy.',
  'La que me hace sentir valiente.',
  'Una canción en otro idioma que me encanta.',
  'La que me recuerda una pelea que superamos.',
  'La que sonaría si corriéramos bajo la lluvia.',
  'Una canción para celebrar algo pequeño de hoy.',
  'La que me pone nostálgico de algo que vivimos.',
  'La que escucharía en una noche de estrellas contigo.',
  'Una canción que me hace pensar en tu familia o la mía.',
  'La canción que te dedicaría en nuestro aniversario.',
  'La que pondría en una fiesta con nuestros amigos.',
  'Una canción que me enseñaste tú y ahora es mía también.',
  'La que describe cómo me siento cuando me abrazas.',
  'La que sonaría en un baile lento en la cocina.',
  'Una canción para pedirte perdón sin palabras.',
  'La que me hace pensar en el primer beso.',
  'La que me gustaría escuchar contigo cuando seamos viejitos.',
  'Una canción con una letra que me gustaría haberte escrito.',
  'La que pondría si estuvieras lejos y quisiera que me sintieras cerca.',
  'La que define este mes que estamos viviendo.',
];

/** Elige tres preguntas sin repetir las que ya respondieron (cuando se acaban, vuelve a empezar). */
export async function pickPrompts(db, key, day) {
  const used = new Set((await capsules(db).find({ couple: key }).toArray()).flatMap((c) => c.prompts));
  let pool = CAPSULE_PROMPTS.filter((p) => !used.has(p));
  if (pool.length < 3) pool = [...CAPSULE_PROMPTS];
  // Mezcla determinista por día: los dos ven las mismas preguntas
  let seed = [...`${key}${day}`].reduce((h, ch) => (h * 31 + ch.charCodeAt(0)) >>> 0, 7);
  const picked = [];
  while (picked.length < 3) {
    seed = (seed * 1103515245 + 12345) >>> 0;
    const p = pool.splice(seed % pool.length, 1)[0];
    picked.push(p);
  }
  return picked;
}

/** Crea la cápsula del día si no existe. */
export async function ensureCapsule(db, key, day, now = new Date()) {
  const id = `${key}:${day}`;
  const existing = await capsules(db).findOne({ _id: id });
  if (existing) return existing;
  const doc = { _id: id, couple: key, day, prompts: await pickPrompts(db, key, day), answers: {}, openedAt: null, createdAt: now };
  await capsules(db).updateOne({ _id: id }, { $setOnInsert: doc }, { upsert: true });
  return capsules(db).findOne({ _id: id });
}

/** Lo que ve cada uno: sus respuestas siempre; las del otro solo cuando la cápsula se abrió. */
export function serializeCapsule(c, me, partner) {
  const mine = c.answers?.[String(me)] ?? null;
  const theirs = c.answers?.[String(partner)] ?? null;
  return {
    day: c.day,
    prompts: c.prompts,
    mine,
    partnerDone: Boolean(theirs),
    partner: c.openedAt ? theirs : null,
    openedAt: c.openedAt ? c.openedAt.getTime() : null,
  };
}

/** Respuestas de uno: tres canciones con una frase. Si con esto terminan los dos, se abre (y salva la racha). */
export async function answerCapsule(db, me, body, now = new Date()) {
  const { couple, key, today } = await refreshStreak(db, me._id, me.partnerId, now);
  const rescue = couple.streak.rescue;
  if (rescue?.type !== 'capsule' || rescue.day !== today) return { error: 'Hoy no hay una cápsula abierta.' };
  const list = Array.isArray(body?.answers) ? body.answers : [];
  const answers = list.slice(0, 3).map((a) => ({ song: cleanSong(a?.song), note: String(a?.note || '').trim().slice(0, 200) }));
  if (answers.length !== 3 || answers.some((a) => !a.song)) return { error: 'Elige una canción para cada pregunta.' };

  const capsule = await ensureCapsule(db, key, today, now);
  if (capsule.answers?.[String(me._id)]) return { error: 'Ya sellaste tus respuestas.' };
  await capsules(db).updateOne({ _id: capsule._id }, { $set: { [`answers.${me._id}`]: answers } });
  const updated = await capsules(db).findOne({ _id: capsule._id });
  const both = Object.keys(updated.answers).length >= 2;
  if (both && !updated.openedAt) {
    await capsules(db).updateOne({ _id: capsule._id }, { $set: { openedAt: now } });
    updated.openedAt = now;
    await couples(db).updateOne({ _id: key }, { $set: { streak: completeRescue(couple.streak) } });
    await recordPetEvent(db, key, 'rescue', { day: today, now });
  }
  return { capsule: updated, opened: both };
}

/** Todas las cápsulas abiertas, de la más nueva a la más vieja. */
export async function openedCapsules(db, me) {
  const key = coupleKey(me._id, me.partnerId);
  const list = await capsules(db).find({ couple: key, openedAt: { $ne: null } }).sort({ day: -1 }).limit(100).toArray();
  return list.map((c) => serializeCapsule(c, me._id, me.partnerId));
}

/**
 * Desde cuándo usan Minu como pareja (AAAA-MM-DD local): lo más antiguo entre el registro de la pareja
 * y su primera dedicatoria. Las Cintas del mes empiezan aquí, no en la fecha de inicio de la relación.
 */
export async function joinedOn(db, couple, me) {
  const first = await dedications(db)
    .find({ $or: [{ from: me._id, to: me.partnerId }, { from: me.partnerId, to: me._id }] }, { projection: { createdAt: 1 } })
    .sort({ createdAt: 1 })
    .limit(1)
    .next();
  const times = [couple?.createdAt, first?.createdAt].filter(Boolean).map((d) => d.getTime());
  if (!times.length) return null;
  return localDay(couple?.tz, new Date(Math.min(...times)));
}

/** Cuántas dedicatorias hubo entre los dos cada día local: { 'AAAA-MM-DD': n }. Para numerar las cintas. */
export async function dedicationDays(db, me) {
  const couple = await couples(db).findOne({ _id: coupleKey(me._id, me.partnerId) });
  const list = await dedications(db)
    .find({ $or: [{ from: me._id, to: me.partnerId }, { from: me.partnerId, to: me._id }] }, { projection: { createdAt: 1 } })
    .toArray();
  const days = {};
  for (const d of list) {
    const day = localDay(couple?.tz, d.createdAt);
    days[day] = (days[day] ?? 0) + 1;
  }
  return days;
}

/**
 * La Cinta del mes: las dedicatorias entre los dos con día local en [from, to). Lado A las tuyas,
 * lado B las de tu pareja, en orden.
 */
export async function mixtape(db, me, from, to) {
  const key = coupleKey(me._id, me.partnerId);
  const couple = await couples(db).findOne({ _id: key });
  const tz = couple?.tz;
  const list = await dedications(db)
    .find({
      $or: [{ from: me._id, to: me.partnerId }, { from: me.partnerId, to: me._id }],
      createdAt: { $gte: new Date(`${addDays(from, -1)}T00:00:00Z`), $lt: new Date(`${addDays(to, 1)}T00:00:00Z`) },
    })
    .sort({ createdAt: 1 })
    .toArray();
  const inRange = list.filter((d) => {
    const day = localDay(tz, d.createdAt);
    return day >= from && day < to;
  });
  const track = (d) => ({ song: d.song, message: d.message, day: localDay(tz, d.createdAt) });
  return {
    from,
    to,
    sideA: inRange.filter((d) => d.from.equals(me._id)).map(track),
    sideB: inRange.filter((d) => d.from.equals(me.partnerId)).map(track),
  };
}

/** Misión del mes N: cada uno marca "Lo hicimos"; queda cumplida cuando marcan los dos. */
export async function markMission(db, me, month, done) {
  const key = coupleKey(me._id, me.partnerId);
  const field = `missions.${month}`;
  const op = done ? { $addToSet: { [field]: String(me._id) } } : { $pull: { [field]: String(me._id) } };
  await couples(db).updateOne({ _id: key }, { ...op, $setOnInsert: { members: [me._id, me.partnerId] } }, { upsert: true });
}

export function missionsView(couple, me, partner) {
  return Object.fromEntries(
    Object.entries(couple?.missions ?? {}).map(([month, by]) => [month, { mine: by.includes(String(me)), partner: by.includes(String(partner)) }]),
  );
}
