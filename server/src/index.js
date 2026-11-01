import http from 'node:http';
import { MongoClient } from 'mongodb';
import { createApp } from './app.js';
import { mindWriterFromEnv } from './mind-writer.js';
import { attachRealtime } from './realtime.js';
import { ensureIndexes } from './db.js';
import { createGoogleVerifier, createPush } from './services.js';

const required = ['MONGODB_URI', 'JWT_SECRET'];
const missing = required.filter((k) => !process.env[k]);
if (missing.length) {
  console.error(`Faltan variables de entorno: ${missing.join(', ')}`);
  process.exit(1);
}

const list = (value) => String(value || '').split(',').map((s) => s.trim()).filter(Boolean);

const config = {
  jwtSecret: process.env.JWT_SECRET,
  // Correos que pueden crear cuenta con Google (el tuyo y el de tu pareja)
  allowedEmails: list(process.env.ALLOWED_EMAILS),
  // Opcional: permite crear cuentas con usuario y contraseña usando este código
  registrationCode: process.env.REGISTRATION_CODE || null,
};
if (!config.allowedEmails.length && !config.registrationCode) {
  console.warn('Aviso: sin ALLOWED_EMAILS ni REGISTRATION_CODE nadie podrá crear cuentas nuevas.');
}

const client = new MongoClient(process.env.MONGODB_URI);
await client.connect();
const db = client.db(process.env.MONGODB_DB || 'minu');
await ensureIndexes(db);

const services = {
  verifyGoogle: createGoogleVerifier(list(process.env.GOOGLE_CLIENT_IDS)),
  push: createPush(db, process.env.FIREBASE_SERVICE_ACCOUNT),
  mindWriter: mindWriterFromEnv(),
};
console.log(
  `Google: ${services.verifyGoogle ? 'activado' : 'desactivado'} · ` +
    `Notificaciones: ${services.push.enabled ? 'activadas' : 'desactivadas'} · ` +
    `Voz de Minu: ${services.mindWriter ? services.mindWriter.model : 'banco de frases (falta MIND_API_KEY)'}`,
);

const realtime = { notify: () => false, isOnline: () => false };
const app = createApp(db, config, realtime, services);
const server = http.createServer(app);
Object.assign(realtime, attachRealtime(server, db, config, services.push));

const port = Number(process.env.PORT || 3000);
server.listen(port, () => console.log(`Minu escuchando en el puerto ${port}`));

const shutdown = async () => {
  server.close();
  await client.close();
  process.exit(0);
};
process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
