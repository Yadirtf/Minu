import { ObjectId } from 'mongodb';

export const users = (db) => db.collection('users');
export const libraries = (db) => db.collection('libraries');
export const dedications = (db) => db.collection('dedications');
/** Una por pareja (_id = coupleKey): fecha de inicio y todo lo que comparten en Dúo. */
export const couples = (db) => db.collection('couples');
/** Un documento por pareja y día (_id = "<pareja>:AAAA-MM-DD"): quiénes dedicaron ese día. */
export const streakDays = (db) => db.collection('streak_days');
/** Cápsulas de tres canciones (_id = "<pareja>:AAAA-MM-DD"): respuestas selladas hasta que responden los dos. */
export const capsules = (db) => db.collection('capsules');
/** Cartas para el próximo aniversario (_id = "<pareja>:<año>:<autor>"): selladas hasta que lo abren. */
export const letters = (db) => db.collection('letters');
/** Gestos que alimentan a Minu (_id determinista = el mismo gesto cuenta una vez). */
export const petEvents = (db) => db.collection('pet_events');

/** Llave de la pareja: los dos ids ordenados, así sobrevive si se desvinculan y vuelven. */
export const coupleKey = (a, b) => [String(a), String(b)].sort().join('_');

export async function ensureIndexes(db) {
  await users(db).createIndex({ username: 1 }, { unique: true });
  await users(db).createIndex({ partnerRequestTo: 1 });
  await users(db).createIndex({ googleSub: 1 }, { unique: true, partialFilterExpression: { googleSub: { $type: 'string' } } });
  await users(db).createIndex({ email: 1 }, { partialFilterExpression: { email: { $type: 'string' } } });
  await dedications(db).createIndex({ to: 1, createdAt: -1 });
  await dedications(db).createIndex({ from: 1, createdAt: -1 });
  await streakDays(db).createIndex({ couple: 1, day: 1 });
  await capsules(db).createIndex({ couple: 1, day: -1 });
  await letters(db).createIndex({ couple: 1, year: 1 });
  await petEvents(db).createIndex({ couple: 1, day: 1 });
}

export function toId(value) {
  try {
    return typeof value === 'string' ? new ObjectId(value) : value;
  } catch {
    return null;
  }
}

/** Datos públicos de un usuario (nunca el hash de la contraseña). */
export function publicUser(user) {
  if (!user) return null;
  return { id: user._id.toString(), username: user.username, displayName: user.displayName };
}

/** Datos de una canción enviados por el cliente, limpios y con tamaño acotado. */
export function cleanSong(song) {
  if (!song || typeof song !== 'object') return null;
  const videoId = String(song.videoId || '');
  if (!/^[A-Za-z0-9_-]{11}$/.test(videoId)) return null;
  return {
    videoId,
    title: String(song.title || '').slice(0, 300),
    uploader: String(song.uploader || '').slice(0, 200),
    durationSec: Number.isFinite(Number(song.durationSec)) ? Math.max(0, Math.floor(Number(song.durationSec))) : 0,
    thumbnailUrl: String(song.thumbnailUrl || '').slice(0, 1000),
  };
}
