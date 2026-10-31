import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { MongoMemoryServer } from 'mongodb-memory-server';
import { MongoClient } from 'mongodb';
import WebSocket from 'ws';
import { createApp } from '../src/app.js';
import { attachRealtime } from '../src/realtime.js';
import { ensureIndexes } from '../src/db.js';

const config = {
  jwtSecret: 'secreto-de-prueba',
  registrationCode: 'minu-2026',
  allowedEmails: ['daniel@gmail.com', 'ana@gmail.com'],
  loginLimitPerMinute: 1000,
};
let mongo, client, server, base;

// Google falso: el token es "google|sub|correo|nombre"
const verifyGoogle = async (idToken) => {
  const [kind, sub, email, name] = idToken.split('|');
  if (kind !== 'google') throw new Error('token inválido');
  return { sub, email, name };
};
// Notificaciones falsas: se guardan para revisarlas
const pushes = [];
const push = { enabled: true, send: async (userId, data) => (pushes.push({ userId: String(userId), data }), 1) };

before(async () => {
  mongo = await MongoMemoryServer.create();
  client = new MongoClient(mongo.getUri());
  await client.connect();
  const db = client.db('minu-test');
  await ensureIndexes(db);
  const realtime = {};
  server = http.createServer(createApp(db, config, realtime, { verifyGoogle, push }));
  Object.assign(realtime, attachRealtime(server, db, config, push));
  await new Promise((r) => server.listen(0, r));
  base = `http://localhost:${server.address().port}`;
});

after(async () => {
  await new Promise((r) => setTimeout(r, 200)); // deja terminar los avisos de desconexión
  server.closeAllConnections?.();
  await new Promise((r) => server.close(r));
  await client.close();
  await mongo.stop();
});

async function api(method, path, body, token) {
  const res = await fetch(base + path, {
    method,
    headers: { 'content-type': 'application/json', ...(token && { authorization: `Bearer ${token}` }) },
    body: body ? JSON.stringify(body) : undefined,
  });
  return { status: res.status, body: await res.json() };
}

const register = (username, password = 'clave123') =>
  api('POST', '/auth/register', { username, password, displayName: username, inviteCode: config.registrationCode });

/** Conecta un WebSocket y guarda los mensajes que llegan. */
function connect(token) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(base.replace('http', 'ws') + '/ws', { headers: { authorization: `Bearer ${token}` } });
    ws.messages = [];
    ws.on('message', (m) => ws.messages.push(JSON.parse(m.toString())));
    ws.waitFor = (type, ms = 3000) =>
      new Promise((res, rej) => {
        const start = Date.now();
        const tick = () => {
          const i = ws.messages.findIndex((m) => m.type === type);
          if (i >= 0) return res(ws.messages.splice(i, 1)[0]);
          if (Date.now() - start > ms) return rej(new Error(`No llegó ${type}`));
          setTimeout(tick, 20);
        };
        tick();
      });
    ws.on('open', () => resolve(ws));
    ws.on('error', reject);
  });
}

test('registro: pide código de invitación y no permite usuarios repetidos', async () => {
  const bad = await api('POST', '/auth/register', { username: 'intruso', password: 'clave123', inviteCode: 'otro' });
  assert.equal(bad.status, 403);
  const ok = await register('carla');
  assert.equal(ok.status, 201);
  assert.ok(ok.body.token);
  assert.equal(ok.body.user.username, 'carla');
  assert.equal((await register('Carla')).status, 409);
});

test('login y /me', async () => {
  await register('mario');
  assert.equal((await api('POST', '/auth/login', { username: 'mario', password: 'mala' })).status, 401);
  const login = await api('POST', '/auth/login', { username: 'MARIO', password: 'clave123' });
  assert.equal(login.status, 200);
  const me = await api('GET', '/me', null, login.body.token);
  assert.equal(me.body.user.username, 'mario');
  assert.equal(me.body.user.passwordHash, undefined);
  assert.equal((await api('GET', '/me', null, 'token-falso')).status, 401);
});

test('biblioteca: se guarda en la nube y se recupera desde otro celular', async () => {
  const { body } = await register('lucia');
  const empty = await api('GET', '/library', null, body.token);
  assert.equal(empty.body.data, null);

  const data = { version: 1, playlists: [{ uid: 'p1', name: 'Rock', songs: [{ videoId: 'dQw4w9WgXcQ', title: 'X' }] }] };
  const put = await api('PUT', '/library', { data, device: 'Pixel' }, body.token);
  assert.equal(put.status, 200);

  // "Celular nuevo": vuelve a iniciar sesión y descarga
  const login = await api('POST', '/auth/login', { username: 'lucia', password: 'clave123' });
  const got = await api('GET', '/library', null, login.body.token);
  assert.deepEqual(got.body.data, data);
  assert.equal(got.body.updatedAt, put.body.updatedAt);

  assert.equal((await api('PUT', '/library', { data: { nada: true } }, body.token)).status, 400);
});

test('pareja, dedicatorias y tiempo real', async () => {
  const daniel = (await register('daniel')).body.token;
  const novia = (await register('novia')).body.token;

  // Solicitud y aceptación
  const sent = await api('POST', '/partner/request', { username: 'novia' }, daniel);
  assert.equal(sent.body.status, 'sent');
  assert.equal((await api('GET', '/partner', null, novia)).body.status, 'received');

  const wsNovia = await connect(novia);
  const accepted = await api('POST', '/partner/accept', null, novia);
  assert.equal(accepted.body.status, 'linked');
  assert.equal(accepted.body.partner.username, 'daniel');

  // Presencia
  const wsDaniel = await connect(daniel);
  const presence = await wsNovia.waitFor('presence');
  assert.equal(presence.online, true);
  assert.equal((await api('GET', '/partner', null, novia)).body.partner.online, true);

  // Dedicatoria con reproducción inmediata: le llega al instante
  const song = { videoId: 'dQw4w9WgXcQ', title: 'Never Gonna Give You Up', uploader: 'Rick Astley', durationSec: 213 };
  const ded = await api('POST', '/dedications', { song, message: 'Para ti ❤️', autoplay: true }, daniel);
  assert.equal(ded.status, 201);
  assert.equal(ded.body.delivered, true);
  const live = await wsNovia.waitFor('dedication');
  assert.equal(live.dedication.message, 'Para ti ❤️');
  assert.equal(live.dedication.autoplay, true);
  assert.equal(live.dedication.incoming, true);

  // Ella reacciona y él se entera
  const react = await api('POST', `/dedications/${live.dedication.id}/react`, { emoji: '😍' }, novia);
  assert.equal(react.status, 200);
  const update = await wsDaniel.waitFor('dedication.update');
  assert.equal(update.dedication.reactions[0].emoji, '😍');
  assert.equal(update.dedication.reactions[0].mine, false);

  // Buzón
  const inbox = await api('GET', '/dedications', null, novia);
  assert.equal(inbox.body.dedications.length, 1);

  // Escuchar juntos: el estado se reenvía a la pareja
  wsDaniel.send(JSON.stringify({ type: 'listen.state', state: { videoId: 'dQw4w9WgXcQ', positionMs: 42000, playing: true } }));
  const state = await wsNovia.waitFor('listen.state');
  assert.equal(state.state.positionMs, 42000);
  assert.equal(state.from, 'daniel');

  // Reacción en vivo y ping
  wsNovia.send(JSON.stringify({ type: 'reaction', emoji: '🔥' }));
  assert.equal((await wsDaniel.waitFor('reaction')).emoji, '🔥');

  // A quien escucha le gustó la canción y la guardó: el DJ recibe el título
  wsNovia.send(JSON.stringify({ type: 'listen.saved', title: 'Never Gonna Give You Up' }));
  const saved = await wsDaniel.waitFor('listen.saved');
  assert.equal(saved.title, 'Never Gonna Give You Up');
  assert.equal(saved.from, 'novia');
  wsNovia.send(JSON.stringify({ type: 'ping', t: 1 }));
  assert.equal((await wsNovia.waitFor('pong')).t, 1);

  // Al desconectarse, la pareja ve que salió
  wsDaniel.close();
  assert.equal((await wsNovia.waitFor('presence')).online, false);

  // Desvincular
  assert.equal((await api('DELETE', '/partner', null, novia)).body.status, 'none');
  assert.equal((await api('GET', '/partner', null, daniel)).body.status, 'none');
  wsNovia.close();
});

test('websocket sin token es rechazado', async () => {
  await assert.rejects(() => connect('falso'));
});

test('Google: solo los correos permitidos crean cuenta, sin código de invitación', async () => {
  const intruso = await api('POST', '/auth/google', { idToken: 'google|x1|otro@gmail.com|Otro' });
  assert.equal(intruso.status, 403);
  assert.equal((await api('POST', '/auth/google', { idToken: 'falso' })).status, 401);

  const first = await api('POST', '/auth/google', { idToken: 'google|g-daniel|daniel@gmail.com|Daniel Duque' });
  assert.equal(first.status, 201);
  assert.equal(first.body.user.username, 'daniel2'); // 'daniel' ya existía (otra prueba): se agrega un número
  assert.equal(first.body.user.displayName, 'Daniel Duque');

  // La segunda vez solo inicia sesión (misma cuenta)
  const again = await api('POST', '/auth/google', { idToken: 'google|g-daniel|daniel@gmail.com|Daniel Duque' });
  assert.equal(again.status, 200);
  assert.equal(again.body.user.id, first.body.user.id);
  // Una cuenta de Google no tiene contraseña
  assert.equal((await api('POST', '/auth/login', { username: first.body.user.username, password: '' })).status, 401);
});

test('notificaciones: dedicatoria e invitación llegan por push si la app está cerrada', async () => {
  const yo = (await api('POST', '/auth/google', { idToken: 'google|g-yo|daniel@gmail.com|Daniel' })).body;
  const ana = (await api('POST', '/auth/google', { idToken: 'google|g-ana|ana@gmail.com|Ana' })).body;
  assert.equal((await api('POST', '/devices', { token: 'fcm-ana' }, ana.token)).status, 200);

  pushes.length = 0;
  await api('POST', '/partner/request', { username: ana.user.username }, yo.token);
  assert.equal(pushes.at(-1).data.type, 'partner.request');
  assert.equal(pushes.at(-1).userId, ana.user.id);
  await api('POST', '/partner/accept', null, ana.token);
  assert.equal(pushes.at(-1).data.type, 'partner.accepted');

  const song = { videoId: 'dQw4w9WgXcQ', title: 'Para ti', uploader: 'X', durationSec: 200 };
  const ded = await api('POST', '/dedications', { song, message: 'Te quiero', autoplay: true }, yo.token);
  assert.equal(ded.body.delivered, false);
  assert.equal(ded.body.pushed, true);
  const p = pushes.at(-1).data;
  assert.equal(p.type, 'dedication');
  assert.equal(p.videoId, 'dQw4w9WgXcQ');
  assert.equal(p.autoplay, true);
  assert.equal(p.fromName, 'Daniel Duque'); // misma cuenta de Google de la prueba anterior

  // Invitación a escuchar juntos con su app cerrada
  const ws = await connect(yo.token);
  ws.send(JSON.stringify({ type: 'listen.invite' }));
  const start = Date.now();
  while (pushes.at(-1).data.type !== 'listen.invite' && Date.now() - start < 3000) await new Promise((r) => setTimeout(r, 20));
  assert.equal(pushes.at(-1).data.type, 'listen.invite');
  ws.close();
});

test('cuenta de servicio: acepta JSON o base64 y nunca lanza el texto', async () => {
  const { parseServiceAccount } = await import('../src/services.js');
  const account = { type: 'service_account', private_key: 'k', client_email: 'a@b.iam.gserviceaccount.com' };
  assert.deepEqual(parseServiceAccount(JSON.stringify(account)), account);
  assert.deepEqual(parseServiceAccount(Buffer.from(JSON.stringify(account)).toString('base64')), account);
  assert.equal(parseServiceAccount('basura-secreta'), null);
});

test('nuestra fecha: la guarda uno, la ve el otro y sobrevive a desvincularse', async () => {
  const romeo = (await register('romeo')).body.token;
  const julieta = (await register('julieta')).body.token;
  assert.equal((await api('GET', '/duo', null, romeo)).body.linked, false);
  assert.equal((await api('PUT', '/duo/origin', { startedOn: '2025-02-14' }, romeo)).status, 409);

  await api('POST', '/partner/request', { username: 'julieta' }, romeo);
  await api('POST', '/partner/accept', null, julieta);
  assert.equal((await api('GET', '/duo', null, romeo)).body.origin, null);

  assert.equal((await api('PUT', '/duo/origin', { startedOn: '2025-02-30' }, romeo)).status, 400);
  assert.equal((await api('PUT', '/duo/origin', { startedOn: '2999-01-01' }, romeo)).status, 400);
  assert.equal((await api('PUT', '/duo/origin', { startedOn: '2025-02-14', song: { videoId: 'malo' } }, romeo)).status, 400);

  const song = { videoId: 'dQw4w9WgXcQ', title: 'Nuestra canción', uploader: 'Alguien', durationSec: 200, thumbnailUrl: '' };
  const saved = await api('PUT', '/duo/origin', { startedOn: '2025-02-14', song, story: 'En la fiesta, bailando mal a propósito' }, romeo);
  assert.equal(saved.status, 200);
  assert.equal(saved.body.origin.setByMe, true);

  await api('POST', '/dedications', { song }, julieta);
  const seen = (await api('GET', '/duo', null, julieta)).body;
  assert.equal(seen.origin.startedOn, '2025-02-14');
  assert.equal(seen.origin.song.title, 'Nuestra canción');
  assert.equal(seen.origin.setByMe, false);
  assert.equal(seen.origin.setByName, 'romeo');
  assert.equal(seen.stats.dedications, 1);

  await api('DELETE', '/partner', null, romeo);
  await api('POST', '/partner/request', { username: 'julieta' }, romeo);
  await api('POST', '/partner/accept', null, julieta);
  assert.equal((await api('GET', '/duo', null, romeo)).body.origin.startedOn, '2025-02-14');
});
