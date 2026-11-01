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
/** Toques de Minu (abrazo, beso, pienso en ti, te extraño) y su respuesta. */
export const touches = (db) => db.collection('touches');
/** Pregunta del día (_id = "<pareja>:AAAA-MM-DD"): respuestas selladas hasta que responden los dos. */
export const questions = (db) => db.collection('questions');
/** Frasco "Gracias por..." (_id = "<pareja>:AAAA-MM-DD:<autor>"): una apreciación por persona al día. */
export const gratitude = (db) => db.collection('gratitude');
/** "Te cuento algo bueno": buenas noticias y la respuesta entusiasta del otro. */
export const goodNews = (db) => db.collection('good_news');
/** Buzón de lo no dicho: cartas guiadas, selladas hasta que quien las recibe elige abrirlas. Viven 30 días. */
export const unsaid = (db) => db.collection('unsaid');
/** Promesas pequeñas: las no cumplidas se borran solas 48 h después de su hora. */
export const promises = (db) => db.collection('promises');
/** Aventura de la semana (_id = "<pareja>:<lunes>"): 3 planes, votos, la elegida y cómo les fue. */
export const adventures = (db) => db.collection('adventures');
/** La foto de cada aventura (JPEG reducido), aparte para no cargarla con el estado de Dúo. */
export const adventurePhotos = (db) => db.collection('adventure_photos');
/** Cumplidos a ciegas: cada uno con su día de entrega al azar (máximo uno por semana). */
export const compliments = (db) => db.collection('compliments');
/** Memoria de la pareja (capa 1): hechos tipados que cada función guarda para que Minu los recuerde. */
export const coupleMemory = (db) => db.collection('couple_memory');
/** El mensaje del día que redactó el celular de uno de los dos (la voz propia de Minu). */
export const mindLines = (db) => db.collection('mind_lines');

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
  await touches(db).createIndex({ couple: 1, day: -1 });
  await questions(db).createIndex({ couple: 1, day: -1 });
  await gratitude(db).createIndex({ couple: 1, month: 1 });
  await goodNews(db).createIndex({ couple: 1, createdAt: -1 });
  await unsaid(db).createIndex({ couple: 1, to: 1, createdAt: -1 });
  await unsaid(db).createIndex({ couple: 1, from: 1, createdAt: -1 });
  await promises(db).createIndex({ couple: 1, dueAt: 1 });
  await adventures(db).createIndex({ couple: 1, week: -1 });
  await compliments(db).createIndex({ couple: 1, to: 1, releaseOn: -1 });
  await compliments(db).createIndex({ couple: 1, by: 1, releaseOn: -1 });
  await mindLines(db).createIndex({ couple: 1, day: -1 });
  await coupleMemory(db).createIndex({ couple: 1, kind: 1, day: -1 });
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
