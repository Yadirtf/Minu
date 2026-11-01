import { couples, petEvents } from './db.js';

/**
 * Minu, la mascota de la pareja. Nace cuando los dos aceptan los términos, se alimenta de los gestos que
 * ya existen en Dúo (dedicatorias, retos, misiones…) y de los que vendrán, y crece por meses en Minu.
 *
 * La energía no se guarda: se calcula al consultar a partir de los gestos de los últimos días, igual que
 * la racha (Render duerme el servidor, así que no hay tareas programadas). Minu nunca muere ni enferma:
 * con energía baja solo se duerme o extraña.
 */

export const SPECIES = ['yarn', 'cat', 'bunny', 'dog'];
export const DEFAULT_SPECIES = 'yarn';
export const DEFAULT_NAME = 'Minu';
export const NAME_MAX = 20;

/**
 * Lo que alimenta a Minu. [points] por gesto y [dailyCap] = tope de puntos de esa fuente por pareja y día,
 * para que no se vuelva una máquina de clics. Las funciones nuevas solo agregan su fuente aquí y llaman a
 * recordPetEvent.
 */
export const PET_SOURCES = {
  birth: { points: 50, dailyCap: 50, uncapped: true }, // el empujón de nacer no gasta el tope del día
  dedication: { points: 10, dailyCap: 20 }, // una por persona y día
  rescue: { points: 20, dailyCap: 20 },
  mission: { points: 15, dailyCap: 15 },
  touch: { points: 5, dailyCap: 20 },
  question: { points: 10, dailyCap: 10 },
  thanks: { points: 5, dailyCap: 10 },
  cheer: { points: 5, dailyCap: 10 }, // una buena noticia que recibió una respuesta entusiasta
  adventure: { points: 25, dailyCap: 25 },
  compliment: { points: 5, dailyCap: 5 }, // un cumplido a ciegas que recibió un 💛 de vuelta
  promise: { points: 5, dailyCap: 10 },
};

/** Tope de puntos de un día sumando todas las fuentes. */
export const DAILY_POINTS_CAP = 50;
/** Cada día que pasa, lo que aportó un gesto vale esto multiplicado (baja despacio, nunca de golpe). */
export const DECAY = 0.85;
/** Días que se miran hacia atrás (después de esto un gesto ya casi no pesa). */
export const WINDOW_DAYS = 30;

/** Estados de ánimo, de mayor a menor energía. */
export const MOODS = [
  { id: 'radiant', min: 80 },
  { id: 'happy', min: 50 },
  { id: 'sleepy', min: 20 },
  { id: 'missing', min: 0 },
];

/** Etapas por meses en Minu. Nunca retroceden: lo construido se queda. */
export const STAGES = [
  { id: 'baby', fromMonth: 0 },
  { id: 'young', fromMonth: 3 },
  { id: 'adult', fromMonth: 12 },
  { id: 'wise', fromMonth: 36 },
];

/** Días entre dos fechas "AAAA-MM-DD" (b - a). */
export function daysBetween(a, b) {
  return Math.round((Date.parse(`${b}T00:00:00Z`) - Date.parse(`${a}T00:00:00Z`)) / 86_400_000);
}

/** Meses cumplidos entre dos fechas "AAAA-MM-DD" (el 31 cumple el último día de los meses cortos). */
export function monthsBetween(from, to) {
  if (!from || !to || to < from) return 0;
  const [fy, fm, fd] = from.split('-').map(Number);
  const [ty, tm, td] = to.split('-').map(Number);
  let months = (ty - fy) * 12 + (tm - fm);
  const lastDayOfTo = new Date(Date.UTC(ty, tm, 0)).getUTCDate();
  if (td < Math.min(fd, lastDayOfTo)) months -= 1;
  return Math.max(0, months);
}

export function moodFor(energy) {
  return MOODS.find((m) => energy >= m.min).id;
}

export function stageFor(months) {
  return [...STAGES].reverse().find((s) => months >= s.fromMonth).id;
}

/** Puntos de cada día, con los topes por fuente y por día aplicados. Función pura. */
export function dailyPoints(events) {
  const byDay = new Map();
  for (const e of events) {
    const source = PET_SOURCES[e.kind];
    if (!source) continue;
    const day = byDay.get(e.day) ?? new Map();
    day.set(e.kind, Math.min(source.dailyCap, (day.get(e.kind) ?? 0) + source.points));
    byDay.set(e.day, day);
  }
  const result = new Map();
  for (const [day, kinds] of byDay) {
    let capped = 0;
    let free = 0;
    for (const [kind, points] of kinds) {
      if (PET_SOURCES[kind].uncapped) free += points;
      else capped += points;
    }
    result.set(day, Math.min(DAILY_POINTS_CAP, capped) + free);
  }
  return result;
}

/**
 * Energía, ánimo y etapa de Minu hoy. Función pura.
 * [events]: [{ day: 'AAAA-MM-DD', kind }]; [joinedOn]: desde cuándo usan Minu juntos.
 */
export function evaluatePet({ events, today, joinedOn }) {
  let total = 0;
  for (const [day, points] of dailyPoints(events)) {
    const age = daysBetween(day, today);
    if (age < 0 || age >= WINDOW_DAYS) continue;
    total += points * DECAY ** age;
  }
  const energy = Math.min(100, Math.round(total));
  const months = monthsBetween(joinedOn ?? today, today);
  const stage = stageFor(months);
  const next = STAGES.find((s) => s.fromMonth > months);
  return { energy, mood: moodFor(energy), stage, months, nextStageInMonths: next ? next.fromMonth - months : null };
}

/** Limpia un nombre propuesto: 1 a 20 caracteres, letras, números, espacios y algunos signos. */
export function cleanPetName(value) {
  const name = String(value ?? '').replace(/\s+/g, ' ').trim();
  const length = [...name].length;
  if (length < 1 || length > NAME_MAX) return null;
  if (!/^[\p{L}\p{N}\p{Extended_Pictographic}‍️ .'_-]+$/u.test(name)) return null;
  return name;
}

export function cleanProposal(kind, value) {
  if (kind === 'name') return cleanPetName(value);
  if (kind === 'species') return SPECIES.includes(value) ? value : null;
  return null;
}

/** Crea a Minu si todavía no existe (lo llama terms.js cuando los dos aceptan). */
export async function ensurePet(db, key, day, now = new Date()) {
  const { modifiedCount, upsertedCount } = await couples(db).updateOne(
    { _id: key, pet: { $exists: false } },
    { $set: { pet: { species: DEFAULT_SPECIES, name: DEFAULT_NAME, bornAt: now, proposals: {} } } },
  );
  if (modifiedCount || upsertedCount) await recordPetEvent(db, key, 'birth', { day, now });
}

/**
 * Registra un gesto que alimenta a Minu. Es idempotente: el mismo gesto (fuente, persona, referencia y
 * día) cuenta una sola vez. Sin [day] por día (perDay false) cuenta una sola vez para siempre.
 */
export async function recordPetEvent(db, key, kind, { day, by = null, ref = null, perDay = true, now = new Date() }) {
  if (!PET_SOURCES[kind] || !day) return false;
  const couple = await couples(db).findOne({ _id: key }, { projection: { pet: 1 } });
  if (!couple?.pet) return false; // Minu aún no nace (falta que los dos acepten)
  const id = [key, kind, by ?? '', ref ?? '', perDay ? day : ''].join(':');
  const { upsertedCount } = await petEvents(db).updateOne(
    { _id: id },
    { $setOnInsert: { couple: key, kind, day, by: by && String(by), createdAt: now } },
    { upsert: true },
  );
  return upsertedCount > 0;
}

/** Lo que ve cada uno de Minu. Nunca dice quién aportó cuánto: la energía es de la pareja. */
export async function petView(db, couple, me, today, joinedOn) {
  const pet = couple?.pet;
  if (!pet) return null;
  const from = new Date(Date.parse(`${today}T00:00:00Z`) - WINDOW_DAYS * 86_400_000).toISOString().slice(0, 10);
  const events = await petEvents(db)
    .find({ couple: couple._id, day: { $gte: from, $lte: today } }, { projection: { day: 1, kind: 1 } })
    .toArray();
  const state = evaluatePet({ events, today, joinedOn });
  const proposals = Object.fromEntries(
    Object.entries(pet.proposals ?? {})
      .filter(([, p]) => p)
      .map(([kind, p]) => [kind, { value: p.value, mine: p.by === String(me), byName: p.byName }]),
  );
  return { species: pet.species, name: pet.name, ...state, proposals };
}

/** Uno propone otro nombre u otra mascota; se aplica cuando el otro acepta. */
export async function proposePetChange(db, key, me, kind, rawValue, now = new Date()) {
  const value = cleanProposal(kind, rawValue);
  if (!value) return { error: kind === 'name' ? 'Ese nombre no sirve: usa de 1 a 20 letras o números.' : 'Mascota inválida.' };
  const couple = await couples(db).findOne({ _id: key });
  if (!couple?.pet) return { error: 'Minu aún no ha nacido.' };
  if (couple.pet[kind] === value) return { error: kind === 'name' ? 'Ya se llama así.' : 'Ya es esa mascota.' };
  await couples(db).updateOne(
    { _id: key },
    { $set: { [`pet.proposals.${kind}`]: { value, by: String(me._id), byName: me.displayName, at: now } } },
  );
  return { ok: true };
}

/** Acepta la propuesta del otro (no la propia). Cambiar de mascota conserva energía y etapa. */
export async function acceptPetChange(db, key, me, kind) {
  const couple = await couples(db).findOne({ _id: key });
  const proposal = couple?.pet?.proposals?.[kind];
  if (!proposal) return { error: 'No hay ninguna propuesta pendiente.' };
  if (proposal.by === String(me._id)) return { error: 'Tu pareja es quien debe aceptarla.' };
  await couples(db).updateOne(
    { _id: key, [`pet.proposals.${kind}.at`]: proposal.at },
    { $set: { [`pet.${kind}`]: proposal.value }, $unset: { [`pet.proposals.${kind}`]: '' } },
  );
  return { ok: true, value: proposal.value };
}

/** Rechazar la del otro o retirar la propia. */
export async function dropPetProposal(db, key, kind) {
  const { modifiedCount } = await couples(db).updateOne({ _id: key }, { $unset: { [`pet.proposals.${kind}`]: '' } });
  return modifiedCount > 0;
}

/** Al borrar mis datos: los gestos que registré yo dejan de contar. */
export async function erasePetEventsBy(db, key, me) {
  await petEvents(db).deleteMany({ couple: key, by: String(me) });
}
