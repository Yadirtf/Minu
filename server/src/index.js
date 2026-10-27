import http from 'node:http';
import { MongoClient } from 'mongodb';
import { createApp } from './app.js';
import { attachRealtime } from './realtime.js';
import { ensureIndexes } from './db.js';

const required = ['MONGODB_URI', 'JWT_SECRET', 'REGISTRATION_CODE'];
const missing = required.filter((k) => !process.env[k]);
if (missing.length) {
  console.error(`Faltan variables de entorno: ${missing.join(', ')}`);
  process.exit(1);
}

const config = {
  jwtSecret: process.env.JWT_SECRET,
  registrationCode: process.env.REGISTRATION_CODE,
};

const client = new MongoClient(process.env.MONGODB_URI);
await client.connect();
const db = client.db(process.env.MONGODB_DB || 'minu');
await ensureIndexes(db);

const realtime = { notify: () => false, isOnline: () => false };
const app = createApp(db, config, realtime);
const server = http.createServer(app);
Object.assign(realtime, attachRealtime(server, db, config));

const port = Number(process.env.PORT || 3000);
server.listen(port, () => console.log(`Minu escuchando en el puerto ${port}`));

const shutdown = async () => {
  server.close();
  await client.close();
  process.exit(0);
};
process.on('SIGTERM', shutdown);
process.on('SIGINT', shutdown);
