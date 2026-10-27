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
  if (!serviceAccountJson) return { enabled: false, send: async () => 0 };
  const app = initializeApp({ credential: cert(JSON.parse(serviceAccountJson)) }, 'minu');
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
