// Servidor local con MongoDB en memoria, para probar la app sin Atlas ni Render.
// Uso: node test/dev-server.js [puerto]
import http from 'node:http';
import { MongoMemoryServer } from 'mongodb-memory-server';
import { MongoClient } from 'mongodb';
import { createApp } from '../src/app.js';
import { attachRealtime } from '../src/realtime.js';
import { ensureIndexes } from '../src/db.js';

const port = Number(process.argv[2] || 3999);
const mongo = await MongoMemoryServer.create();
const client = new MongoClient(mongo.getUri());
await client.connect();
const db = client.db('minu-dev');
await ensureIndexes(db);
const config = { jwtSecret: 'dev', registrationCode: 'dev' };
const realtime = {};
const server = http.createServer(createApp(db, config, realtime));
Object.assign(realtime, attachRealtime(server, db, config));
server.listen(port, () => console.log(`Servidor de prueba en http://localhost:${port} (código de invitación: dev)`));
