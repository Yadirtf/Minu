import { capsules, coupleKey, couples, dedications, letters } from './db.js';
import { addDays, localDay } from './duo.js';
import { serializeCapsule } from './memories.js';

/** Cuánto puede separarse el abrazo de los dos (cada celular lo repite mientras lo mantienen). */
export const HUG_WINDOW_MS = 6000;
/** Desde esta hora local del día del aniversario se puede abrir sin el otro. */
export const SOLO_HOUR = 20;

/** El aniversario [years] de [start] ("AAAA-MM-DD"). Un 29 de febrero se celebra el 28 si el año no es bisiesto. */
export function anniversaryDate(start, years) {
  const [y, m, d] = start.split('-').map(Number);
  const year = y + years;
  const last = new Date(Date.UTC(year, m, 0)).getUTCDate();
  return `${year}-${String(m).padStart(2, '0')}-${String(Math.min(d, last)).padStart(2, '0')}`;
}

/** Años que ya se cumplieron a [today]. */
export function yearsDone(start, today) {
  let n = 0;
  while (anniversaryDate(start, n + 1) <= today) n += 1;
  return n;
}

function localHour(timeZone, date) {
  try {
    return Number(new Intl.DateTimeFormat('en-US', { timeZone: timeZone || 'UTC', hour: 'numeric', hourCycle: 'h23' }).format(date));
  } catch {
    return date.getUTCHours();
  }
}

/** Lee la pareja y comprueba que el aniversario [years] ya llegó. */
async function load(db, me, years, now) {
  const key = coupleKey(me._id, me.partnerId);
  const couple = await couples(db).findOne({ _id: key });
  const start = couple?.origin?.startedOn;
  if (!start) return { error: 'Primero guarden el día en que empezó todo.' };
  const today = localDay(couple.tz, now);
  const date = anniversaryDate(start, years);
  if (date > today) return { error: 'Ese aniversario todavía no llega.' };
  return { key, couple, start, today, date, state: couple.anniversaries?.[years] ?? {} };
}

/** Lista de aniversarios cumplidos, para Dúo y Recuerdos. */
export function anniversariesView(couple, today) {
  const start = couple?.origin?.startedOn;
  if (!start) return [];
  return Array.from({ length: yearsDone(start, today) }, (_, i) => {
    const years = i + 1;
    return { years, date: anniversaryDate(start, years), opened: Boolean(couple.anniversaries?.[years]?.openedAt) };
  });
}

/**
 * Un abrazo en el hilo: cada uno mantiene presionada su burbuja y el celular lo repite cada pocos
 * segundos. Si el otro también está abrazando, el aniversario se abre para los dos.
 */
export async function hug(db, me, years, now = new Date()) {
  const ctx = await load(db, me, years, now);
  if (ctx.error) return ctx;
  if (ctx.state.openedAt) return { opened: true };
  await couples(db).updateOne({ _id: ctx.key }, { $set: { [`anniversaries.${years}.hugs.${me._id}`]: now } });
  const theirs = ctx.state.hugs?.[String(me.partnerId)];
  const together = theirs && Math.abs(now - theirs) <= HUG_WINDOW_MS;
  if (together) {
    await couples(db).updateOne({ _id: ctx.key }, { $set: { [`anniversaries.${years}.openedAt`]: now, [`anniversaries.${years}.openedBy`]: 'hug' } });
  }
  return { opened: Boolean(together) };
}

/** "Abrirlo solo": desde las 8 p. m. del día del aniversario, o cualquier día después. */
export async function openSolo(db, me, years, now = new Date()) {
  const ctx = await load(db, me, years, now);
  if (ctx.error) return ctx;
  if (ctx.state.openedAt) return { opened: true };
  if (ctx.date === ctx.today && localHour(ctx.couple.tz, now) < SOLO_HOUR) return { error: 'Se puede abrir solo desde las 8 p. m.' };
  await couples(db).updateOne({ _id: ctx.key }, { $set: { [`anniversaries.${years}.openedAt`]: now, [`anniversaries.${years}.openedBy`]: String(me._id) } });
  return { opened: true };
}

/** Dedicatorias entre los dos con día local en [from, to), de la más vieja a la más nueva. */
async function dedicationsBetween(db, me, tz, from, to) {
  const list = await dedications(db)
    .find({
      $or: [{ from: me._id, to: me.partnerId }, { from: me.partnerId, to: me._id }],
      createdAt: { $gte: new Date(`${addDays(from, -1)}T00:00:00Z`), $lt: new Date(`${addDays(to, 1)}T00:00:00Z`) },
    })
    .sort({ createdAt: 1 })
    .toArray();
  return list.filter((d) => {
    const day = localDay(tz, d.createdAt);
    return day >= from && day < to;
  });
}

/** Las 5 canciones más dedicadas del año, con cuántas veces. */
function topSongs(list) {
  const counts = new Map();
  for (const d of list) {
    const entry = counts.get(d.song.videoId) ?? { song: d.song, count: 0 };
    entry.count += 1;
    counts.set(d.song.videoId, entry);
  }
  return [...counts.values()].sort((a, b) => b.count - a.count).slice(0, 5);
}

/** Todo lo del aniversario [years]: estado del regalo y, si ya se abrió, las historias del año. */
export async function anniversaryView(db, me, years, now = new Date()) {
  const ctx = await load(db, me, years, now);
  if (ctx.error) return ctx;
  const { key, couple, start, today, date, state } = ctx;
  const theirs = state.hugs?.[String(me.partnerId)];
  const base = {
    years,
    date,
    openedAt: state.openedAt ? state.openedAt.getTime() : null,
    partnerHugging: Boolean(theirs && now - theirs <= HUG_WINDOW_MS),
    canOpenSolo: date < today || localHour(couple.tz, now) >= SOLO_HOUR,
    story: null,
  };
  if (!state.openedAt) return base;

  const from = anniversaryDate(start, years - 1);
  const list = await dedicationsBetween(db, me, couple.tz, from, date);
  const first = list[0];
  const top = topSongs(list);
  const caps = await capsules(db).find({ couple: key, openedAt: { $ne: null }, day: { $gte: from, $lt: date } }).sort({ day: 1 }).toArray();
  const opened = await letters(db).find({ couple: key, year: years }).toArray();
  const votes = state.votes ?? {};
  return {
    ...base,
    story: {
      from,
      originStory: couple.origin.story ?? '',
      originSong: couple.origin.song ?? null,
      first: first ? { song: first.song, message: first.message, fromMe: first.from.equals(me._id), day: localDay(couple.tz, first.createdAt) } : null,
      dedications: { total: list.length, mine: list.filter((d) => d.from.equals(me._id)).length },
      top: top.map((t) => ({ song: t.song, count: t.count })),
      listenSec: couple.listenSec ?? 0,
      bestStreak: couple.streak?.best ?? 0,
      saves: couple.streak?.saves ?? 0,
      capsules: caps.map((c) => serializeCapsule(c, me._id, me.partnerId)),
      letters: opened.map((l) => ({ fromMe: l.from.equals(me._id), text: l.text })),
      vote: { mine: votes[String(me._id)] ?? null, partner: votes[String(me.partnerId)] ?? null, song: state.song ?? null },
    },
  };
}

/**
 * Voto por la canción del año entre las 5 más dedicadas. Cuando votan los dos: si coinciden, esa;
 * si no, la que más se dedicaron de las dos.
 */
export async function voteSong(db, me, years, videoId, now = new Date()) {
  const ctx = await load(db, me, years, now);
  if (ctx.error) return ctx;
  if (!ctx.state.openedAt) return { error: 'Primero abran el aniversario.' };
  const from = anniversaryDate(ctx.start, years - 1);
  const top = topSongs(await dedicationsBetween(db, me, ctx.couple.tz, from, ctx.date));
  if (!top.some((t) => t.song.videoId === videoId)) return { error: 'Elige una de sus canciones más dedicadas.' };
  const votes = { ...(ctx.state.votes ?? {}), [String(me._id)]: videoId };
  const update = { [`anniversaries.${years}.votes`]: votes };
  const theirs = votes[String(me.partnerId)];
  if (theirs) {
    const winner = top.find((t) => t.song.videoId === theirs || t.song.videoId === videoId);
    update[`anniversaries.${years}.song`] = winner.song;
  }
  await couples(db).updateOne({ _id: ctx.key }, { $set: update });
  return { decided: Boolean(theirs) };
}

/** Carta para el próximo aniversario. Se puede reescribir mientras siga sellada. */
export async function writeLetter(db, me, text, now = new Date()) {
  const key = coupleKey(me._id, me.partnerId);
  const couple = await couples(db).findOne({ _id: key });
  const start = couple?.origin?.startedOn;
  if (!start) return { error: 'Primero guarden el día en que empezó todo.' };
  const body = String(text || '').trim().slice(0, 3000);
  if (!body) return { error: 'La carta está vacía.' };
  const year = yearsDone(start, localDay(couple.tz, now)) + 1;
  await letters(db).updateOne(
    { _id: `${key}:${year}:${me._id}` },
    { $set: { text: body, updatedAt: now }, $setOnInsert: { couple: key, year, from: me._id, createdAt: now } },
    { upsert: true },
  );
  return { year };
}

/** Mi carta sellada para el próximo aniversario (y si el otro ya escribió la suya, sin mostrarla). */
export async function sealedLetters(db, me, now = new Date()) {
  const key = coupleKey(me._id, me.partnerId);
  const couple = await couples(db).findOne({ _id: key });
  const start = couple?.origin?.startedOn;
  if (!start) return null;
  const year = yearsDone(start, localDay(couple.tz, now)) + 1;
  const list = await letters(db).find({ couple: key, year }).toArray();
  const mine = list.find((l) => l.from.equals(me._id));
  return {
    year,
    opensOn: anniversaryDate(start, year),
    mine: mine?.text ?? null,
    partnerWrote: list.some((l) => l.from.equals(me.partnerId)),
  };
}
