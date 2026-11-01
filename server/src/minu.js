import { coupleKey, couples } from './db.js';
import { ensurePet, erasePetEventsBy, petView } from './pet.js';

/**
 * Términos de privacidad de Minu y el estado que ve la app. Cada miembro acepta la versión vigente; las
 * funciones de la mascota solo funcionan cuando los dos aceptaron. Si los términos cambian, se sube
 * TERMS_VERSION y los dos vuelven a aceptar (sin perder nada).
 */
export const TERMS_VERSION = 1;

export function termsView(couple, me, partner) {
  const accepted = (id) => (couple?.terms?.[String(id)]?.version ?? 0) >= TERMS_VERSION;
  const mine = accepted(me);
  const theirs = accepted(partner);
  // Si ya habían aceptado una versión anterior, la app muestra solo lo que cambió
  const update = !mine && Boolean(couple?.terms?.[String(me)]);
  return { version: TERMS_VERSION, mine, partner: theirs, accepted: mine && theirs, update };
}

export function minuAllowed(couple, a, b) {
  return termsView(couple, a, b).accepted;
}

/** Acepto los términos vigentes. Cuando los dos aceptaron, nace Minu (si no existía). */
export async function acceptTerms(db, me, today, now = new Date()) {
  const key = coupleKey(me._id, me.partnerId);
  await couples(db).updateOne(
    { _id: key },
    {
      $set: { [`terms.${me._id}`]: { version: TERMS_VERSION, at: now }, members: [me._id, me.partnerId] },
      $setOnInsert: { createdAt: now },
    },
    { upsert: true },
  );
  const couple = await couples(db).findOne({ _id: key });
  const both = minuAllowed(couple, me._id, me.partnerId);
  if (both) await ensurePet(db, key, today, now);
  return { both };
}

/** Lo de Minu que va dentro de GET /duo: términos y, si los dos aceptaron, la mascota. */
export async function minuState(db, couple, me, today, joinedOn) {
  const terms = termsView(couple, me._id, me.partnerId);
  return { terms, pet: terms.accepted ? await petView(db, couple, me._id, today, joinedOn) : null };
}

/**
 * "Borrar mis datos de Minu": retira mi aceptación (Minu se pausa hasta que vuelva a aceptar) y borra lo
 * que yo aporté. La mascota (nombre y especie) es de los dos y se queda. Cada función nueva que guarde
 * datos personales agrega aquí su borrado.
 */
export async function eraseMyMinuData(db, me) {
  const key = coupleKey(me._id, me.partnerId);
  await couples(db).updateOne({ _id: key }, { $unset: { [`terms.${me._id}`]: '' } });
  await erasePetEventsBy(db, key, me._id);
}
