import { OAuth2Client } from 'google-auth-library';
import { cert, initializeApp } from 'firebase-admin/app';
import { getMessaging } from 'firebase-admin/messaging';
import { users } from './db.js';

/**
 * Verifica el "ID token" que entrega el botón "Continuar con Google" en la app.
 * [clientIds] son los Client ID web de OAuth (Firebase los crea al activar Google).
 */
export function createGoogleVerifier(clientIds) {
  if (!clientIds.length) return null;
  const client = new OAuth2Client();
  return async (idToken) => {
    const ticket = await client.verifyIdToken({ idToken, audience: clientIds });
    const p = ticket.getPayload();
    if (!p?.email || !p.email_verified) throw new Error('Correo de Google no verificado');
    return { sub: p.sub, email: p.email.toLowerCase(), name: p.name || p.given_name || '' };
  };
}

/** Acepta el JSON tal cual o codificado en base64. Devuelve null si no es válido (sin lanzar el texto). */
export function parseServiceAccount(value) {
  const text = String(value).trim();
  const attempts = [text];
  if (!text.startsWith('{')) attempts.push(Buffer.from(text, 'base64').toString('utf8'));
  for (const candidate of attempts) {
    try {
      const json = JSON.parse(candidate);
      if (json?.type === 'service_account' && json.private_key && json.client_email) return json;
    } catch {
      // se prueba el siguiente formato
    }
  }
  return null;
}

const DEAD_TOKEN_ERRORS = new Set([
  'messaging/registration-token-not-registered',
  'messaging/invalid-registration-token',
  'messaging/invalid-argument',
]);

/**
 * Notificaciones push con Firebase Cloud Messaging. Sin la cuenta de servicio no hace nada.
 * Se envían mensajes de "datos" con prioridad alta: la app arma la notificación y puede
 * empezar a reproducir aunque esté cerrada.
 */
export function createPush(db, serviceAccountJson) {
  const disabled = { enabled: false, send: async () => 0 };
  if (!serviceAccountJson) return disabled;
  const account = parseServiceAccount(serviceAccountJson);
  if (!account) {
    // Nunca imprimir el valor: contiene la clave privada
    console.error('FIREBASE_SERVICE_ACCOUNT no es un JSON de cuenta de servicio válido. Notificaciones desactivadas.');
    return disabled;
  }
  const app = initializeApp({ credential: cert(account) }, 'minu');
  const messaging = getMessaging(app);

  return {
    enabled: true,
    async send(userId, data) {
      const user = await users(db).findOne({ _id: userId }, { projection: { fcmTokens: 1 } });
      const tokens = user?.fcmTokens ?? [];
      if (!tokens.length) return 0;
      const payload = Object.fromEntries(
        Object.entries(data).filter(([, v]) => v !== undefined && v !== null).map(([k, v]) => [k, String(v)]),
      );
      try {
        const res = await messaging.sendEachForMulticast({
          tokens,
          data: payload,
          android: { priority: 'high', ttl: 24 * 60 * 60 * 1000 },
        });
        const dead = tokens.filter((_, i) => DEAD_TOKEN_ERRORS.has(res.responses[i]?.error?.code));
        if (dead.length) await users(db).updateOne({ _id: userId }, { $pull: { fcmTokens: { $in: dead } } });
        return res.successCount;
      } catch (e) {
        console.error('No se pudo enviar la notificación', e.message);
        return 0;
      }
    },
  };
}
