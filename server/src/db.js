import { ObjectId } from 'mongodb';

export const users = (db) => db.collection('users');
export const libraries = (db) => db.collection('libraries');
export const dedications = (db) => db.collection('dedications');

export async function ensureIndexes(db) {
  await users(db).createIndex({ username: 1 }, { unique: true });
  await users(db).createIndex({ partnerRequestTo: 1 });
  await users(db).createIndex({ googleSub: 1 }, { unique: true, partialFilterExpression: { googleSub: { $type: 'string' } } });
  await users(db).createIndex({ email: 1 }, { partialFilterExpression: { email: { $type: 'string' } } });
  await dedications(db).createIndex({ to: 1, createdAt: -1 });
  await dedications(db).createIndex({ from: 1, createdAt: -1 });
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
