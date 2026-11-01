import { coupleMemory } from './db.js';

/**
 * Memoria de la pareja, capa 1: hechos cortos y tipados que cada función guarda con remember(). Es la
 * base para que Minu "recuerde" (una canción que se dedicaron, algo bueno que pasó, lo que respondieron)
 * y, más adelante, para que sus mensajes personalizados se armen dentro del celular.
 *
 * Nunca entra aquí lo que se cuenta en espacios de reparación (Buzón, Tregua). Cada hecho guarda quién
 * lo aportó para poder borrarlo cuando esa persona borre sus datos.
 */
export const MEMORY_KINDS = ['answer', 'thanks', 'news', 'song'];

/**
 * Guarda un hecho. Idempotente por (kind, ref): el mismo hecho no se duplica si la llamada se repite.
 * [text] se recorta a 300 caracteres; [data] lleva campos extra pequeños (por ejemplo, la pregunta).
 */
export async function remember(db, key, { kind, by, ref, day, text = '', data = {}, now = new Date() }) {
  if (!MEMORY_KINDS.includes(kind) || !ref || !day) return false;
  const { upsertedCount } = await coupleMemory(db).updateOne(
    { _id: `${key}:${kind}:${ref}` },
    { $setOnInsert: { couple: key, kind, by: by ? String(by) : null, day, text: String(text).slice(0, 300), data, createdAt: now } },
    { upsert: true },
  );
  return upsertedCount > 0;
}

/** Los hechos más recientes de la pareja (opcionalmente de un tipo). */
export async function recall(db, key, { kind, limit = 50 } = {}) {
  const query = { couple: key, ...(kind && { kind }) };
  return coupleMemory(db).find(query).sort({ day: -1, createdAt: -1 }).limit(limit).toArray();
}

/** Al borrar mis datos de Minu: se va todo lo que yo aporté. */
export async function forgetBy(db, key, me) {
  await coupleMemory(db).deleteMany({ couple: key, by: String(me) });
}
