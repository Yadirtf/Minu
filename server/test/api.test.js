import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { MongoMemoryServer } from 'mongodb-memory-server';
import { MongoClient } from 'mongodb';
import WebSocket from 'ws';
import { createApp } from '../src/app.js';
import { attachRealtime } from '../src/realtime.js';
import { ensureIndexes } from '../src/db.js';
import { addListening } from '../src/duo.js';

const config = {
  jwtSecret: 'secreto-de-prueba',
  registrationCode: 'minu-2026',
  allowedEmails: ['daniel@gmail.com', 'ana@gmail.com'],
  loginLimitPerMinute: 1000,
};
let mongo, client, server, base;
// Reloj que las pruebas de la racha pueden mover (null = la hora real)
let fakeNow = null;

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
  server = http.createServer(createApp(db, config, realtime, { verifyGoogle, push, clock: () => fakeNow ?? new Date() }));
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
    headers: {
      'content-type': 'application/json',
      'x-timezone': 'America/Bogota',
      ...(token && { authorization: `Bearer ${token}` }),
    },
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

test('racha: los dos dedican, se olvidan, eligen el reto, lo cumplen escuchando juntos', async () => {
  const a = (await register('tristan')).body.token;
  const b = (await register('isolda')).body.token;
  await api('POST', '/partner/request', { username: 'isolda' }, a);
  await api('POST', '/partner/accept', null, b);
  const song = { videoId: 'dQw4w9WgXcQ', title: 'Una', uploader: 'X', durationSec: 200, thumbnailUrl: '' };
  const at = (day) => (fakeNow = new Date(`${day}T15:00:00Z`)); // 10:00 en Bogotá
  const streak = async (token) => (await api('GET', '/duo', null, token)).body.streak;

  try {
    at('2026-03-10');
    await api('POST', '/dedications', { song }, a);
    let s = await streak(b);
    assert.equal(s.count, 0);
    assert.equal(s.todayPartner, true);
    assert.equal(s.todayMine, false);
    await api('POST', '/dedications', { song }, b);
    assert.equal((await streak(a)).count, 1);

    at('2026-03-11'); // solo dedica uno: el día queda por rescatar
    await api('POST', '/dedications', { song }, a);

    at('2026-03-12');
    s = await streak(b);
    assert.equal(s.state, 'at_risk');
    assert.equal(s.rescue.missedDay, '2026-03-11');
    assert.equal(s.rescuesLeft, 3);
    assert.equal((await api('POST', '/dedications', { song }, a)).status, 409);
    assert.equal((await api('POST', '/duo/rescue', { type: 'nada' }, b)).status, 400);

    const chosen = await api('POST', '/duo/rescue', { type: 'listen' }, b);
    assert.equal(chosen.body.streak.state, 'rescuing');
    assert.equal(chosen.body.streak.rescue.chosenByName, 'isolda');
    assert.equal(chosen.body.streak.rescuesLeft, 2);

    const db = client.db('minu-test');
    const ids = await db.collection('users').find({ username: { $in: ['tristan', 'isolda'] } }).toArray();
    assert.equal(await addListening(db, ids[0]._id, ids[1]._id, 20 * 60, fakeNow), false);
    assert.equal((await streak(a)).rescue.listenedSec, 20 * 60);
    assert.equal(await addListening(db, ids[0]._id, ids[1]._id, 10 * 60, fakeNow), true);
    s = await streak(a);
    assert.equal(s.state, 'active');
    assert.equal(s.count, 3);
    assert.deepEqual(s.saved, { day: '2026-03-12', type: 'listen' });
    assert.equal(s.listenSec, 30 * 60);

    // Se vuelven a olvidar y esta vez dejan que se rompa: pueden empezar el día 1 ese mismo día
    at('2026-03-14');
    assert.equal((await streak(a)).state, 'at_risk');
    s = (await api('POST', '/duo/rescue/giveup', null, a)).body.streak;
    assert.equal(s.count, 0);
    assert.deepEqual(s.broken, { on: '2026-03-14', lost: 3, reason: 'gave_up' });
    await api('POST', '/dedications', { song }, a);
    await api('POST', '/dedications', { song }, b);
    s = await streak(b);
    assert.equal(s.count, 1);
    assert.equal(s.best, 3);
    assert.equal(s.rescuesLeft, 2);

    at('2026-04-01'); // mes nuevo: vuelven a ser 3, no se acumulan
    assert.equal((await streak(a)).rescuesLeft, 3);
  } finally {
    fakeNow = null;
  }
});

test('cápsula: salva la racha cuando los dos sellan sus tres canciones; cinta del mes y misiones', async () => {
  const a = (await register('frida')).body.token;
  const b = (await register('diego')).body.token;
  await api('POST', '/partner/request', { username: 'diego' }, a);
  await api('POST', '/partner/accept', null, b);
  const song = (id, title) => ({ videoId: id, title, uploader: 'X', durationSec: 200, thumbnailUrl: '' });
  const at = (day) => (fakeNow = new Date(`${day}T15:00:00Z`));
  try {
    at('2026-05-01');
    await api('POST', '/dedications', { song: song('aaaaaaaaaaa', 'De Lucía'), message: 'para ti' }, a);
    await api('POST', '/dedications', { song: song('bbbbbbbbbbb', 'De Mateo') }, b);
    at('2026-05-02');
    await api('POST', '/dedications', { song: song('ccccccccccc', 'Otra de Lucía') }, a);

    // Cinta del mes: lado A las mías, lado B las suyas
    const tape = (await api('GET', '/duo/mixtape?from=2026-05-01&to=2026-06-01', null, a)).body;
    assert.deepEqual(tape.sideA.map((t) => t.song.title), ['De Lucía', 'Otra de Lucía']);
    assert.deepEqual(tape.sideB.map((t) => t.song.title), ['De Mateo']);
    assert.equal(tape.sideA[0].message, 'para ti');
    assert.equal((await api('GET', '/duo/mixtape?from=2026-06-01&to=2026-05-01', null, a)).status, 400);
    // Las cintas empiezan cuando llegaron a Minu, con su primera dedicatoria
    assert.equal((await api('GET', '/duo', null, b)).body.joinedOn, '2026-05-01');
    assert.deepEqual((await api('GET', '/duo/memories', null, a)).body.days, { '2026-05-01': 2, '2026-05-02': 1 });

    at('2026-05-03'); // Mateo no dedicó el 2: toca reto
    assert.equal((await api('GET', '/duo/capsule', null, a)).body.capsule, null);
    await api('POST', '/duo/rescue', { type: 'capsule' }, b);
    const open = (await api('GET', '/duo/capsule', null, a)).body.capsule;
    assert.equal(open.prompts.length, 3);
    assert.equal(new Set(open.prompts).size, 3);
    assert.equal(open.partnerDone, false);

    const answers = [song('ddddddddddd', '1'), song('eeeeeeeeeee', '2'), song('fffffffffff', '3')].map((s) => ({ song: s, note: 'porque sí' }));
    assert.equal((await api('POST', '/duo/capsule', { answers: answers.slice(0, 2) }, a)).status, 409);
    const sealed = (await api('POST', '/duo/capsule', { answers }, a)).body.capsule;
    assert.equal(sealed.mine.length, 3);
    assert.equal(sealed.openedAt, null);
    assert.equal((await api('POST', '/duo/capsule', { answers }, a)).status, 409); // ya sellada
    assert.equal((await api('POST', '/duo/rescue', { type: 'listen' }, a)).status, 409); // ya empezó
    // Mateo no ve las de Lucía hasta terminar
    const forB = (await api('GET', '/duo/capsule', null, b)).body.capsule;
    assert.equal(forB.partnerDone, true);
    assert.equal(forB.partner, null);

    const opened = (await api('POST', '/duo/capsule', { answers }, b)).body.capsule;
    assert.ok(opened.openedAt);
    assert.equal(opened.partner.length, 3);
    const duo = (await api('GET', '/duo', null, a)).body;
    assert.equal(duo.streak.count, 3);
    assert.deepEqual(duo.streak.saved, { day: '2026-05-03', type: 'capsule' });
    assert.equal((await api('GET', '/duo/memories', null, b)).body.capsules.length, 1);

    // Misión del mes: queda cumplida cuando marcan los dos
    let m = (await api('POST', '/duo/missions/3', { done: true }, a)).body.missions;
    assert.deepEqual(m['3'], { mine: true, partner: false });
    m = (await api('POST', '/duo/missions/3', { done: true }, b)).body.missions;
    assert.deepEqual(m['3'], { mine: true, partner: true });
    assert.equal((await api('POST', '/duo/missions/0', { done: true }, b)).status, 400);
  } finally {
    fakeNow = null;
  }
});

test('aniversario: se abre con el abrazo de los dos, cuenta su año, abre las cartas y elige la canción del año', async () => {
  const a = (await register('penelope')).body.token;
  const b = (await register('ulises')).body.token;
  await api('POST', '/partner/request', { username: 'ulises' }, a);
  await api('POST', '/partner/accept', null, b);
  const song = (id, title) => ({ videoId: id, title, uploader: 'X', durationSec: 200, thumbnailUrl: '' });
  const at = (day) => (fakeNow = new Date(`${day}T15:00:00Z`)); // 10:00 en Bogotá
  try {
    at('2025-01-05');
    await api('PUT', '/duo/origin', { startedOn: '2024-06-10', story: 'En el bus, compartiendo audífonos' }, a);
    await api('POST', '/dedications', { song: song('aaaaaaaaaaa', 'La nuestra'), message: 'la primera' }, a);
    fakeNow = new Date('2025-01-05T16:00:00Z');
    await api('POST', '/dedications', { song: song('aaaaaaaaaaa', 'La nuestra') }, b);
    at('2025-01-06');
    await api('POST', '/dedications', { song: song('bbbbbbbbbbb', 'Otra') }, a);

    at('2025-03-01');
    const sealed = (await api('PUT', '/duo/letter', { text: 'Querido Ulises…' }, a)).body.letter;
    assert.deepEqual(sealed, { year: 1, opensOn: '2025-06-10', mine: 'Querido Ulises…', partnerWrote: false });
    await api('PUT', '/duo/letter', { text: 'Querida Penélope…' }, b);
    assert.equal((await api('GET', '/duo', null, a)).body.letter.partnerWrote, true);

    at('2025-06-09');
    assert.equal((await api('GET', '/duo/anniversary/1', null, a)).status, 409);

    at('2025-06-10');
    let gift = (await api('GET', '/duo/anniversary/1', null, a)).body.anniversary;
    assert.equal(gift.openedAt, null);
    assert.equal(gift.canOpenSolo, false);
    assert.equal(gift.story, null);
    assert.equal((await api('POST', '/duo/anniversary/1/open', null, a)).status, 409); // antes de las 8 p. m.

    gift = (await api('POST', '/duo/anniversary/1/hug', null, a)).body.anniversary;
    assert.equal(gift.openedAt, null);
    assert.equal((await api('GET', '/duo/anniversary/1', null, b)).body.anniversary.partnerHugging, true);
    const opened = (await api('POST', '/duo/anniversary/1/hug', null, b)).body.anniversary;
    assert.ok(opened.openedAt);

    const story = opened.story;
    assert.equal(story.originStory, 'En el bus, compartiendo audífonos');
    assert.equal(story.first.message, 'la primera');
    assert.equal(story.first.fromMe, false);
    assert.deepEqual(story.dedications, { total: 3, mine: 1 });
    assert.deepEqual(story.top.map((t) => [t.song.title, t.count]), [['La nuestra', 2], ['Otra', 1]]);
    assert.deepEqual(story.letters.map((l) => l.text).sort(), ['Querida Penélope…', 'Querido Ulises…']);

    // Canción del año: si no coinciden, gana la más dedicada de las dos
    assert.equal((await api('POST', '/duo/anniversary/1/vote', { videoId: 'ccccccccccc' }, a)).status, 409);
    await api('POST', '/duo/anniversary/1/vote', { videoId: 'bbbbbbbbbbb' }, a);
    const voted = (await api('POST', '/duo/anniversary/1/vote', { videoId: 'aaaaaaaaaaa' }, b)).body.anniversary.story.vote;
    assert.equal(voted.mine, 'aaaaaaaaaaa');
    assert.equal(voted.partner, 'bbbbbbbbbbb');
    assert.equal(voted.song.title, 'La nuestra');

    const duo = (await api('GET', '/duo', null, a)).body;
    assert.deepEqual(duo.anniversaries, [{ years: 1, date: '2025-06-10', opened: true, beforeMinu: false }]);
    assert.equal(duo.letter.year, 2);
    assert.equal(duo.letter.mine, null);
  } finally {
    fakeNow = null;
  }
});

test('aniversario: los que vivieron antes de llegar a Minu se marcan y no se abren', async () => {
  const a = (await register('amelie')).body.token;
  const b = (await register('nino')).body.token;
  await api('POST', '/partner/request', { username: 'nino' }, a);
  await api('POST', '/partner/accept', null, b);
  const song = { videoId: 'aaaaaaaaaaa', title: 'La nuestra', uploader: 'X', durationSec: 200, thumbnailUrl: '' };
  try {
    fakeNow = new Date('2026-10-20T15:00:00Z');
    await api('POST', '/dedications', { song }, a);
    await api('PUT', '/duo/origin', { startedOn: '2019-11-05' }, a);
    fakeNow = new Date('2026-11-06T15:00:00Z'); // la sesión vence a los 30 días: no ir más lejos
    const duo = (await api('GET', '/duo', null, b)).body;
    assert.equal(duo.joinedOn, '2026-10-20');
    assert.equal(duo.anniversaries.length, 7);
    assert.ok(duo.anniversaries.slice(0, 6).every((x) => x.beforeMinu));
    assert.equal(duo.anniversaries[6].beforeMinu, false); // 5 nov 2026: el primero en Minu
    assert.equal((await api('GET', '/duo/anniversary/3', null, a)).status, 409);
    assert.equal((await api('POST', '/duo/anniversary/3/hug', null, a)).status, 409);
    assert.equal((await api('GET', '/duo/anniversary/7', null, a)).status, 200);
  } finally {
    fakeNow = null;
  }
});

test('Minu: nace cuando los dos aceptan los términos, se alimenta de dedicatorias y se renombra de común acuerdo', async () => {
  const a = (await register('minu_paris')).body.token;
  const b = (await register('minu_helena')).body.token;
  await api('POST', '/partner/request', { username: 'minu_helena' }, a);
  await api('POST', '/partner/accept', null, b);
  const song = { videoId: 'bbbbbbbbbbb', title: 'Para ti', uploader: 'X', durationSec: 200, thumbnailUrl: '' };
  try {
    fakeNow = new Date('2026-11-01T15:00:00Z');
    let duo = (await api('GET', '/duo', null, a)).body;
    assert.deepEqual(duo.minu.terms, { version: 1, mine: false, partner: false, accepted: false, update: false });
    assert.equal(duo.minu.pet, null);
    // Sin aceptar, la mascota no responde
    assert.equal((await api('POST', '/duo/pet/proposals', { kind: 'name', value: 'Pelusa' }, a)).status, 403);

    duo = (await api('POST', '/duo/terms/accept', null, a)).body;
    assert.equal(duo.minu.terms.mine, true);
    assert.equal(duo.minu.terms.partner, false);
    assert.equal(duo.minu.pet, null); // falta la otra mitad
    duo = (await api('GET', '/duo', null, b)).body;
    assert.equal(duo.minu.terms.partner, true);

    duo = (await api('POST', '/duo/terms/accept', null, b)).body;
    assert.equal(duo.minu.terms.accepted, true);
    assert.equal(duo.minu.pet.name, 'Minu');
    assert.equal(duo.minu.pet.species, 'yarn');
    assert.equal(duo.minu.pet.stage, 'baby');
    const born = duo.minu.pet.energy;
    assert.equal(born, 50);
    // Aceptar de nuevo no hace nacer otra vez a Minu
    await api('POST', '/duo/terms/accept', null, b);

    // Dedicar alimenta (una vez por persona al día) y nunca dice quién aportó
    await api('POST', '/dedications', { song }, a);
    await api('POST', '/dedications', { song }, a);
    duo = (await api('GET', '/duo', null, b)).body;
    assert.equal(duo.minu.pet.energy, born + 10);
    await api('POST', '/dedications', { song }, b);
    duo = (await api('GET', '/duo', null, a)).body;
    assert.equal(duo.minu.pet.energy, born + 20);
    assert.equal(duo.minu.pet.mine, undefined);

    // Renombrar: uno propone, el otro acepta
    assert.equal((await api('POST', '/duo/pet/proposals', { kind: 'name', value: '<b>' }, a)).status, 400);
    duo = (await api('POST', '/duo/pet/proposals', { kind: 'name', value: '  Pelusa  ' }, a)).body;
    assert.deepEqual(duo.minu.pet.proposals.name, { value: 'Pelusa', mine: true, byName: 'minu_paris' });
    assert.equal((await api('POST', '/duo/pet/proposals/name/accept', null, a)).status, 409); // no la propia
    duo = (await api('POST', '/duo/pet/proposals/name/accept', null, b)).body;
    assert.equal(duo.minu.pet.name, 'Pelusa');
    assert.equal(duo.minu.pet.proposals.name, undefined);

    // Cambiar de mascota conserva la energía; también se puede rechazar
    await api('POST', '/duo/pet/proposals', { kind: 'species', value: 'cat' }, b);
    duo = (await api('DELETE', '/duo/pet/proposals/species', null, a)).body;
    assert.equal(duo.minu.pet.species, 'yarn');
    await api('POST', '/duo/pet/proposals', { kind: 'species', value: 'dog' }, b);
    duo = (await api('POST', '/duo/pet/proposals/species/accept', null, a)).body;
    assert.equal(duo.minu.pet.species, 'dog');
    assert.equal(duo.minu.pet.energy, born + 20);
    assert.equal((await api('POST', '/duo/pet/proposals', { kind: 'species', value: 'dragon' }, a)).status, 400);

    // Un mes después crece y la energía bajó (sin morir)
    fakeNow = new Date('2026-11-26T15:00:00Z');
    duo = (await api('GET', '/duo', null, a)).body;
    assert.ok(duo.minu.pet.energy < born, `energía ${duo.minu.pet.energy}`);
    assert.ok(['sleepy', 'missing'].includes(duo.minu.pet.mood));

    // Borrar mis datos: Minu se pausa hasta que vuelva a aceptar; la mascota se queda
    duo = (await api('DELETE', '/duo/minu/data', null, a)).body;
    assert.equal(duo.minu.terms.mine, false);
    assert.equal(duo.minu.pet, null);
    assert.equal((await api('POST', '/duo/pet/proposals', { kind: 'name', value: 'Otra' }, b)).status, 403);
    duo = (await api('POST', '/duo/terms/accept', null, a)).body;
    assert.equal(duo.minu.pet.name, 'Pelusa');
    assert.equal(duo.minu.pet.species, 'dog');
  } finally {
    fakeNow = null;
  }
});

test('Minu: conexión diaria (toque, pregunta del día, gracias por y buenas noticias)', async () => {
  const a = (await register('minu_lucia')).body.token;
  const b = (await register('minu_tomas')).body.token;
  await api('POST', '/partner/request', { username: 'minu_tomas' }, a);
  await api('POST', '/partner/accept', null, b);
  try {
    fakeNow = new Date('2026-11-03T15:00:00Z');
    // Sin términos aceptados no hay conexión diaria
    assert.equal((await api('POST', '/duo/touch', { kind: 'hug' }, a)).status, 403);
    await api('POST', '/duo/terms/accept', null, a);
    let duo = (await api('POST', '/duo/terms/accept', null, b)).body;
    const daily0 = duo.minu.daily;
    assert.ok(daily0.question.text.length > 5);
    assert.equal(daily0.question.mine, null);
    assert.notEqual(daily0.question.level, 'deep'); // Minu es cría: nada profundo todavía
    assert.equal(daily0.touch.left, 10);
    assert.deepEqual(daily0.touch.incoming, []);
    assert.deepEqual(daily0.jar, { month: '2026-11', mine: null, items: [] });
    assert.deepEqual(daily0.news, []);
    const born = duo.minu.pet.energy;

    // Toque: mandarlo no alimenta; devolverlo sí
    assert.equal((await api('POST', '/duo/touch', { kind: 'dragon' }, a)).status, 400);
    pushes.length = 0;
    duo = (await api('POST', '/duo/touch', { kind: 'hug' }, a)).body;
    assert.equal(duo.minu.pet.energy, born);
    assert.equal(duo.minu.daily.touch.last.reply, null);
    assert.equal(duo.minu.daily.touch.left, 9);
    assert.ok(pushes.some((p) => p.data.type === 'minu.touch' && p.data.kind === 'hug' && p.data.fromName === 'minu_lucia'));
    duo = (await api('GET', '/duo', null, b)).body;
    const [incoming] = duo.minu.daily.touch.incoming;
    assert.equal(incoming.kind, 'hug');
    assert.equal(incoming.fromName, 'minu_lucia');
    assert.equal((await api('POST', `/duo/touch/${incoming.id}/reply`, { kind: 'kiss' }, a)).status, 409); // no el propio
    duo = (await api('POST', `/duo/touch/${incoming.id}/reply`, { kind: 'kiss' }, b)).body;
    assert.deepEqual(duo.minu.daily.touch.incoming, []);
    assert.equal(duo.minu.pet.energy, born + 5);
    assert.equal((await api('POST', `/duo/touch/${incoming.id}/reply`, { kind: 'kiss' }, b)).status, 409);
    duo = (await api('GET', '/duo', null, a)).body;
    assert.equal(duo.minu.daily.touch.last.reply, 'kiss');

    // Pregunta del día: sellada hasta que responden los dos
    duo = (await api('POST', '/duo/question/answer', { text: '  Pizza   con piña  ' }, a)).body;
    assert.equal(duo.minu.daily.question.mine, 'Pizza con piña');
    assert.equal(duo.minu.daily.question.opened, false);
    duo = (await api('GET', '/duo', null, b)).body;
    assert.equal(duo.minu.daily.question.partnerDone, true);
    assert.equal(duo.minu.daily.question.partner, null);
    assert.equal(duo.minu.daily.question.text, daily0.question.text); // la misma para los dos
    assert.equal((await api('POST', '/duo/question/react', { text: 'Cuéntame más' }, b)).status, 409);
    assert.equal((await api('POST', '/duo/question/answer', { text: '' }, b)).status, 400);
    duo = (await api('POST', '/duo/question/answer', { text: 'Arepa' }, b)).body;
    assert.equal(duo.minu.daily.question.opened, true);
    assert.equal(duo.minu.daily.question.partner, 'Pizza con piña');
    assert.equal(duo.minu.pet.energy, born + 15);
    assert.equal((await api('POST', '/duo/question/answer', { text: 'Otra' }, a)).status, 400);
    await api('POST', '/duo/question/react', { text: 'Cuéntame más 💬' }, b);
    duo = (await api('GET', '/duo', null, a)).body;
    assert.equal(duo.minu.daily.question.partner, 'Arepa');
    assert.equal(duo.minu.daily.question.partnerReaction, 'Cuéntame más 💬');
    assert.equal((await api('GET', '/duo/questions', null, a)).body.questions.length, 1);

    // Gracias por...: una al día por persona
    duo = (await api('POST', '/duo/thanks', { text: 'Por el café de esta mañana' }, a)).body;
    assert.equal(duo.minu.daily.jar.mine, 'Por el café de esta mañana');
    assert.equal(duo.minu.pet.energy, born + 20);
    assert.equal((await api('POST', '/duo/thanks', { text: 'Otra' }, a)).status, 409);
    duo = (await api('GET', '/duo', null, b)).body;
    assert.deepEqual(duo.minu.daily.jar.items, [{ text: 'Por el café de esta mañana', byMe: false, day: '2026-11-03' }]);
    assert.equal((await api('GET', '/duo/jar?month=2026-11', null, b)).body.items.length, 1);
    assert.equal((await api('GET', '/duo/jar?month=nov', null, b)).status, 400);

    // Te cuento algo bueno: el otro celebra (una vez), y eso alimenta
    duo = (await api('POST', '/duo/news', { text: '¡Me dieron el ascenso!' }, b)).body;
    const news = duo.minu.daily.news[0];
    assert.equal(news.byMe, true);
    assert.equal((await api('POST', `/duo/news/${news.id}/cheer`, { text: 'Yo' }, b)).status, 409); // no la propia
    duo = (await api('POST', `/duo/news/${news.id}/cheer`, { text: '¡Qué orgullo me das!' }, a)).body;
    assert.equal(duo.minu.daily.news[0].reply.text, '¡Qué orgullo me das!');
    assert.equal(duo.minu.daily.news[0].byMe, false);
    assert.equal(duo.minu.pet.energy, born + 25);
    assert.equal((await api('POST', `/duo/news/${news.id}/cheer`, { text: 'Otra vez' }, a)).status, 409);

    // Banco de frases para la app
    assert.ok((await api('GET', '/minu/content', null, a)).body.cheers.length >= 5);

    // Límite de toques por día
    for (let i = 0; i < 9; i++) await api('POST', '/duo/touch', { kind: 'thinking' }, a);
    assert.equal((await api('POST', '/duo/touch', { kind: 'miss' }, a)).status, 429);

    // Al día siguiente: pregunta nueva y los toques de ayer ya no se muestran
    fakeNow = new Date('2026-11-04T15:00:00Z');
    duo = (await api('GET', '/duo', null, b)).body;
    assert.notEqual(duo.minu.daily.question.id, daily0.question.id);
    assert.deepEqual(duo.minu.daily.touch.incoming, []);
    assert.equal(duo.minu.daily.jar.items.length, 1); // el frasco es del mes

    // Borrar mis datos se lleva mis gracias, respuestas y noticias
    await api('DELETE', '/duo/minu/data', null, a);
    await api('POST', '/duo/terms/accept', null, a);
    assert.equal((await api('GET', '/duo/jar?month=2026-11', null, b)).body.items.length, 0);
    const [old] = (await api('GET', '/duo/questions', null, b)).body.questions;
    assert.equal(old.partner, null);
    assert.equal(old.mine, 'Arepa');
  } finally {
    fakeNow = null;
  }
});

test('Minu: confianza y reparación (Buzón, Tregua, Promesas y cuidado)', async () => {
  const a = (await register('minu_sara')).body.token;
  const b = (await register('minu_leo')).body.token;
  await api('POST', '/partner/request', { username: 'minu_leo' }, a);
  await api('POST', '/partner/accept', null, b);
  const minuOf = async (token) => (await api('GET', '/duo', null, token)).body.minu;
  try {
    fakeNow = new Date('2026-11-10T15:00:00Z');
    assert.equal((await api('POST', '/duo/truce', null, a)).status, 403); // sin términos, nada
    await api('POST', '/duo/terms/accept', null, a);
    let duo = (await api('POST', '/duo/terms/accept', null, b)).body;
    assert.deepEqual(duo.minu.trust.unsaid, { incoming: [], sent: [] });
    assert.equal(duo.minu.trust.truce, null);
    assert.deepEqual(duo.minu.trust.promises, { kept: 0, items: [] });
    const born = duo.minu.pet.energy;

    // Cuidado: responde solo a quien escribe y no guarda nada
    const risky = (await api('POST', '/minu/care', { texts: ['Siento miedo', 'cuando me empujaste', 'que hablemos'] }, a)).body;
    assert.equal(risky.risk, true);
    assert.equal(risky.help.country, 'Colombia');
    const calm = (await api('POST', '/minu/care', { texts: ['Me muero de risa contigo'] }, a)).body;
    assert.deepEqual(calm, { risk: false, kinds: [], help: null });

    // Buzón: las tres partes, sellado, sin "visto" y nunca en la memoria
    assert.equal((await api('POST', '/duo/unsaid', { feel: 'triste', when: '', need: 'algo' }, a)).status, 400);
    pushes.length = 0;
    const letter = { feel: 'un poco sola', when: 'miras el celular en la cena', need: 'que cenemos sin pantallas' };
    duo = (await api('POST', '/duo/unsaid', letter, a)).body;
    const [sent] = duo.minu.trust.unsaid.sent;
    assert.equal(sent.feel, 'un poco sola');
    assert.equal(sent.thanked, false);
    assert.equal('opened' in sent || 'openedAt' in sent, false);
    const notice = pushes.find((p) => p.data.type === 'minu.unsaid');
    assert.ok(notice);
    assert.equal(JSON.stringify(notice.data).includes('sola'), false); // la notificación no lleva el contenido
    assert.equal((await api('POST', '/duo/unsaid', letter, a)).status, 429); // una a la vez
    let minuB = await minuOf(b);
    const [sealed] = minuB.trust.unsaid.incoming;
    assert.equal(sealed.sealed, true);
    assert.equal(sealed.feel, undefined);
    assert.equal(sealed.fromName, 'minu_sara');
    assert.equal((await api('POST', `/duo/unsaid/${sealed.id}/thanks`, null, b)).status, 409); // primero abrirla
    assert.equal((await api('POST', `/duo/unsaid/${sealed.id}/open`, null, a)).status, 409); // no la propia
    pushes.length = 0;
    duo = (await api('POST', `/duo/unsaid/${sealed.id}/open`, null, b)).body;
    assert.equal(pushes.length, 0); // abrir no avisa a nadie
    assert.equal(duo.minu.trust.unsaid.incoming[0].need, 'que cenemos sin pantallas');
    assert.equal((await minuOf(a)).trust.unsaid.sent[0].thanked, false);
    assert.equal((await api('POST', `/duo/unsaid/${sealed.id}/reply`, { text: 'Tienes razón' }, b)).status, 409); // primero agradecer
    duo = (await api('POST', `/duo/unsaid/${sealed.id}/thanks`, null, b)).body;
    assert.equal(duo.minu.trust.unsaid.incoming[0].thanked, true);
    await api('POST', `/duo/unsaid/${sealed.id}/reply`, { text: 'Tienes razón. Desde hoy, cena sin celular 💛' }, b);
    let minuA = await minuOf(a);
    assert.equal(minuA.trust.unsaid.sent[0].thanked, true);
    assert.equal(minuA.trust.unsaid.sent[0].reply, 'Tienes razón. Desde hoy, cena sin celular 💛');
    assert.equal(minuA.pet.energy, born); // el Buzón no se juega por puntos
    const memory = await client.db('minu-test').collection('couple_memory').find({ text: /sola|pantallas|cena/ }).toArray();
    assert.deepEqual(memory, []);

    // Tregua: 20 minutos de calma, frases de reparación, nada queda guardado ni resta
    pushes.length = 0;
    duo = (await api('POST', '/duo/truce', null, b)).body;
    let truce = duo.minu.trust.truce;
    assert.equal(truce.phase, 'calm');
    assert.equal(truce.mine, true);
    assert.equal(truce.endsAt - truce.startedAt, 20 * 60_000);
    assert.ok(pushes.some((p) => p.data.type === 'minu.truce'));
    truce = (await minuOf(a)).trust.truce;
    assert.equal(truce.mine, false);
    assert.equal(truce.canExtend, false);
    assert.equal((await api('POST', '/duo/truce/extend', null, a)).status, 403); // solo quien la pidió
    assert.equal((await api('POST', '/duo/truce/repair', { text: 'Empecemos de nuevo' }, a)).status, 409); // aún es calma
    duo = (await api('POST', '/duo/truce/extend', null, b)).body;
    assert.equal(duo.minu.trust.truce.endsAt - duo.minu.trust.truce.startedAt, 40 * 60_000);
    fakeNow = new Date('2026-11-10T15:41:00Z');
    assert.equal((await minuOf(a)).trust.truce.phase, 'repair');
    duo = (await api('POST', '/duo/truce/repair', { text: 'Empecemos de nuevo' }, a)).body;
    assert.equal(duo.minu.trust.truce.myRepair, 'Empecemos de nuevo');
    assert.equal((await minuOf(b)).trust.truce.partnerRepair, 'Empecemos de nuevo');
    duo = (await api('POST', '/duo/truce/repair', { text: 'Te quiero aunque estemos molestos' }, b)).body;
    assert.equal(duo.minu.trust.truce.phase, 'together');
    assert.equal(duo.minu.pet.energy, born);
    fakeNow = new Date('2026-11-10T16:20:00Z');
    assert.equal((await minuOf(a)).trust.truce, null); // el reencuentro se va solo

    // Promesas: recordatorio solo para quien promete; el otro agradece y Minu celebra
    const due = Date.parse('2026-11-10T20:00:00Z');
    assert.equal((await api('POST', '/duo/promises', { text: 'Te llamo a las 3', dueAt: Date.parse('2026-11-01T00:00:00Z') }, a)).status, 400);
    pushes.length = 0;
    duo = (await api('POST', '/duo/promises', { text: 'Te llamo a las 3', dueAt: due }, a)).body;
    const [promise] = duo.minu.trust.promises.items;
    assert.deepEqual({ ...promise, id: undefined }, { id: undefined, text: 'Te llamo a las 3', mine: true, byName: 'minu_sara', dueAt: due, kept: false, confirmed: false });
    assert.ok(pushes.some((p) => p.data.type === 'minu.promise' && p.data.text === 'Te llamo a las 3'));
    assert.equal((await api('POST', `/duo/promises/${promise.id}/kept`, null, b)).status, 409); // solo quien prometió
    await api('POST', `/duo/promises/${promise.id}/kept`, null, a);
    assert.equal((await api('POST', `/duo/promises/${promise.id}/confirm`, null, a)).status, 409); // no la propia
    duo = (await api('POST', `/duo/promises/${promise.id}/confirm`, null, b)).body;
    assert.equal(duo.minu.trust.promises.kept, 1);
    assert.equal(duo.minu.trust.promises.items[0].confirmed, true);
    assert.equal(duo.minu.pet.energy, born + 5);
    assert.equal((await api('POST', `/duo/promises/${promise.id}/confirm`, null, b)).status, 409);

    // Una promesa que no se cumple se borra sola a las 48 h, sin rastro ni reproche
    await api('POST', '/duo/promises', { text: 'Lavo los platos', dueAt: Date.parse('2026-11-10T23:00:00Z') }, b);
    fakeNow = new Date('2026-11-12T20:00:00Z');
    assert.equal((await minuOf(a)).trust.promises.items.length, 2);
    fakeNow = new Date('2026-11-13T23:30:00Z');
    minuA = await minuOf(a);
    assert.deepEqual(minuA.trust.promises.items, []); // la cumplida ya pasó su celebración y la otra se fue sola
    assert.equal(minuA.trust.promises.kept, 1);
    assert.equal(minuA.pet.mood.length > 0, true);

    // Borrar mis datos se lleva mis cartas y promesas
    await api('POST', '/duo/promises', { text: 'Te escribo al salir', dueAt: Date.parse('2026-11-14T01:00:00Z') }, a);
    await api('DELETE', '/duo/minu/data', null, a);
    await api('POST', '/duo/terms/accept', null, a);
    minuB = await minuOf(b);
    assert.deepEqual(minuB.trust.unsaid.incoming, []);
    assert.deepEqual(minuB.trust.promises.items, []);
  } finally {
    fakeNow = null;
  }
});

test('Minu: novedad (Aventura de la semana, Cumplidos a ciegas y lenguajes del amor)', async () => {
  const a = (await register('minu_vale')).body.token;
  const b = (await register('minu_juan')).body.token;
  await api('POST', '/partner/request', { username: 'minu_juan' }, a);
  await api('POST', '/partner/accept', null, b);
  const minuOf = async (token) => (await api('GET', '/duo', null, token)).body.minu;
  try {
    fakeNow = new Date('2026-11-11T15:00:00Z'); // miércoles
    assert.equal((await api('POST', '/duo/adventure/vote', { planId: 'a001' }, a)).status, 403);
    await api('POST', '/duo/terms/accept', null, a);
    let duo = (await api('POST', '/duo/terms/accept', null, b)).body;
    let adv = duo.minu.novelty.adventure;
    assert.equal(adv.week, '2026-11-09');
    assert.deepEqual(adv.options.map((o) => o.kind), ['home', 'out', 'create']);
    assert.equal(adv.chosen, null);
    assert.equal(adv.canReshuffle, true);
    assert.deepEqual(duo.minu.novelty.love, { mine: false, partner: false, suggestion: null });
    const born = duo.minu.pet.energy;

    // Otras propuestas: una vez, y cambian los planes
    duo = (await api('POST', '/duo/adventure/reshuffle', null, a)).body;
    const options = duo.minu.novelty.adventure.options;
    assert.notDeepEqual(options.map((o) => o.id), adv.options.map((o) => o.id));
    assert.equal((await api('POST', '/duo/adventure/reshuffle', null, a)).status, 409);

    // Votos: se elige cuando coinciden
    assert.equal((await api('POST', '/duo/adventure/vote', { planId: 'nope' }, a)).status, 400);
    duo = (await api('POST', '/duo/adventure/vote', { planId: options[0].id }, a)).body;
    assert.equal(duo.minu.novelty.adventure.myVote, options[0].id);
    duo = (await api('POST', '/duo/adventure/vote', { planId: options[1].id }, b)).body;
    assert.equal(duo.minu.novelty.adventure.partnerVote, options[0].id);
    assert.equal(duo.minu.novelty.adventure.chosen, null);
    pushes.length = 0;
    duo = (await api('POST', '/duo/adventure/vote', { planId: options[0].id }, b)).body;
    adv = duo.minu.novelty.adventure;
    assert.equal(adv.chosen.id, options[0].id);
    assert.deepEqual(adv.options, []);
    assert.ok(pushes.some((p) => p.data.type === 'minu.adventure.chosen'));

    // Foto (JPEG) y completar con canción: +25 y a Recuerdos
    const jpeg = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.alloc(400, 7)]);
    const put = await fetch(`${base}/duo/adventures/${adv.week}/photo`, { method: 'PUT', headers: { 'content-type': 'image/jpeg', authorization: `Bearer ${a}` }, body: jpeg });
    assert.equal(put.status, 200);
    const bad = await fetch(`${base}/duo/adventures/${adv.week}/photo`, { method: 'PUT', headers: { 'content-type': 'image/jpeg', authorization: `Bearer ${a}` }, body: Buffer.alloc(400, 1) });
    assert.equal(bad.status, 400);
    const got = await fetch(`${base}/duo/adventures/${adv.week}/photo`, { headers: { authorization: `Bearer ${b}` } });
    assert.equal(got.status, 200);
    assert.deepEqual(Buffer.from(await got.arrayBuffer()), jpeg);
    const song = { videoId: 'dQw4w9WgXcQ', title: 'Nuestra canción', uploader: 'Alguien', durationSec: 200, thumbnailUrl: '' };
    duo = (await api('POST', '/duo/adventure/done', { song }, b)).body;
    adv = duo.minu.novelty.adventure;
    assert.equal(adv.done, true);
    assert.equal(adv.hasPhoto, true);
    assert.equal(adv.song.title, 'Nuestra canción');
    assert.equal(duo.minu.pet.energy, born + 25);
    duo = (await api('POST', '/duo/adventure/done', null, a)).body;
    assert.equal(duo.minu.pet.energy, born + 25); // una sola vez
    assert.equal((await api('POST', `/duo/adventures/${adv.week}/rate`, { stars: 9 }, a)).status, 400);
    duo = (await api('POST', `/duo/adventures/${adv.week}/rate`, { stars: 5 }, a)).body;
    assert.equal(duo.minu.novelty.adventure.myRating, 5);
    const month = (await api('GET', '/duo/adventures?month=2026-11', null, b)).body.items;
    assert.equal(month.length, 1);
    assert.equal(month[0].partnerRating, 5);

    // La semana siguiente: planes nuevos, sin repetir los ya vistos
    fakeNow = new Date('2026-11-16T15:00:00Z');
    const next = (await minuOf(a)).novelty.adventure;
    assert.equal(next.week, '2026-11-16');
    const seen = new Set([...adv.options.map((o) => o.id), ...options.map((o) => o.id)]);
    assert.ok(next.options.every((o) => !seen.has(o.id)));

    // Cumplidos a ciegas: el otro no ve nada hasta el día de la sorpresa
    assert.equal((await api('POST', '/duo/compliments', { texts: ['Tu risa', 'Tu paciencia'] }, a)).status, 400);
    pushes.length = 0;
    duo = (await api('POST', '/duo/compliments', { texts: ['Tu risa', 'Tu paciencia', 'Cómo cuidas a tu familia'] }, a)).body;
    assert.equal(duo.minu.novelty.compliments.pending, 3);
    assert.equal(duo.minu.novelty.compliments.askNew, false);
    assert.equal(pushes.length, 0); // es sorpresa
    assert.equal((await api('POST', '/duo/compliments', { texts: ['x', 'y', 'z'] }, a)).status, 409);
    let praise = (await minuOf(b)).novelty.compliments;
    assert.deepEqual(praise.received, []);
    assert.ok(praise.nextOn > '2026-11-16' && praise.nextOn <= '2026-11-23');
    assert.equal(JSON.stringify(praise).includes('risa'), false);
    fakeNow = new Date(`${praise.nextOn}T15:00:00Z`);
    praise = (await minuOf(b)).novelty.compliments;
    assert.equal(praise.received.length, 1);
    const [first] = praise.received;
    assert.equal(first.seen, false);
    assert.equal(first.fromName, 'minu_vale');
    const before = (await minuOf(b)).pet.energy;
    duo = (await api('POST', `/duo/compliments/${first.id}/heart`, null, b)).body;
    assert.equal(duo.minu.novelty.compliments.received[0].heart, true);
    assert.equal(duo.minu.pet.energy, before + 5);
    assert.equal((await api('POST', `/duo/compliments/${first.id}/heart`, null, b)).status, 409);
    assert.equal((await minuOf(a)).novelty.compliments.delivered[0].heart, true);

    // Lenguajes del amor: solo sugerencias, nunca una etiqueta
    const quiz = (await api('GET', '/minu/content', null, a)).body.loveQuiz;
    assert.equal(quiz.length, 10);
    assert.equal((await api('POST', '/duo/love', { answers: ['touch'] }, b)).status, 400);
    duo = (await api('POST', '/duo/love', { answers: quiz.map((q) => q.b.lang) }, b)).body;
    assert.deepEqual({ mine: duo.minu.novelty.love.mine, partner: duo.minu.novelty.love.partner }, { mine: true, partner: false });
    const love = (await minuOf(a)).novelty.love;
    assert.equal(love.partner, true);
    assert.equal(typeof love.suggestion, 'string');
    assert.equal(JSON.stringify(love).match(/touch|words|gifts|service|weights/), null);

    // Borrar mis datos se lleva mis cumplidos y mi test
    await api('DELETE', '/duo/minu/data', null, b);
    await api('POST', '/duo/terms/accept', null, b);
    assert.equal((await minuOf(a)).novelty.love.partner, false);
    await api('DELETE', '/duo/minu/data', null, a);
    await api('POST', '/duo/terms/accept', null, a);
    assert.deepEqual((await minuOf(b)).novelty.compliments.received, []);
  } finally {
    fakeNow = null;
  }
});

test('Minu: voz propia (despierta al mes, encargo del día, barandas, ajuste y ensayo)', async () => {
  try {
    fakeNow = new Date('2027-01-31T15:00:00Z');
    const a = (await register('mind_sara')).body.token;
    const b = (await register('mind_leo')).body.token;
    await api('POST', '/partner/request', { username: 'mind_leo' }, a);
    await api('POST', '/partner/accept', null, b);
    await api('POST', '/duo/terms/accept', null, a);
    let duo = (await api('POST', '/duo/terms/accept', null, b)).body;
    let mind = duo.minu.mind;
    // Llegaron el 31 de enero: despierta el 28 de febrero (fin de mes)
    assert.equal(mind.awake, false);
    assert.equal(mind.wakesOn, '2027-02-28');
    assert.equal(mind.daysLeft, 28);
    assert.equal(mind.task, null);
    assert.equal(mind.enabled, true);
    const offer = (token, body) => api('POST', '/duo/minu/mind/line', body, token);
    assert.equal((await offer(a, { day: '2027-01-31', kind: 'plan', text: 'Hola' })).status, 409);

    // Despierta: celebración, encargo con lo que sabe de ellos y sin palabras prohibidas
    fakeNow = new Date('2027-02-28T15:00:00Z');
    mind = (await api('GET', '/duo', null, a)).body.minu.mind;
    assert.equal(mind.awake, true);
    assert.equal(mind.celebrate, true);
    assert.equal(mind.line, null);
    assert.equal(mind.task.day, '2027-02-28');
    assert.match(mind.task.prompt, /Eres Minu/);
    assert.ok(mind.task.prompt.endsWith('Mensaje:'));

    // Un intento que no pasa las barandas cuenta, pero no queda
    let res = (await offer(a, { day: mind.task.day, kind: mind.task.kind, text: 'Como inteligencia artificial, hoy los veo muy bien a los dos.' })).body;
    assert.deepEqual({ accepted: res.accepted, reason: res.reason }, { accepted: false, reason: 'banned' });
    assert.equal(res.duo.minu.mind.line, null);
    assert.ok(res.duo.minu.mind.task);
    assert.equal((await offer(a, { day: '2027-02-27', kind: 'plan', text: 'x' })).status, 409);
    assert.equal((await offer(a, { day: '2027-02-28', kind: 'nope', text: 'x' })).status, 400);

    // El que pasa es de los dos y ya no hay encargo
    const text = 'Me acordé de que hoy es un buen día para bailar en la sala. ¿Se animan los dos?';
    res = (await offer(a, { day: '2027-02-28', kind: 'plan', text: `"${text}"` })).body;
    assert.equal(res.accepted, true);
    assert.deepEqual(res.duo.minu.mind.line, { day: '2027-02-28', kind: 'plan', text });
    assert.equal(res.duo.minu.mind.task, null);
    mind = (await api('GET', '/duo', null, b)).body.minu.mind;
    assert.equal(mind.line.text, text);
    res = (await offer(b, { day: '2027-02-28', kind: 'cheer', text: 'Otro mensaje bonito para los dos de hoy, con mucho cariño.' })).body;
    assert.equal(res.accepted, false);
    assert.equal(res.duo.minu.mind.line.text, text);

    // Celebración vista
    mind = (await api('POST', '/duo/minu/mind/woke', null, b)).body.minu.mind;
    assert.equal(mind.celebrate, false);
    assert.equal((await api('GET', '/duo', null, a)).body.minu.mind.celebrate, true);

    // Si uno apaga los mensajes personalizados, los dos vuelven al banco (sin error)
    mind = (await api('PUT', '/duo/minu/mind', { on: false }, b)).body.minu.mind;
    assert.deepEqual({ mineOn: mind.mineOn, enabled: mind.enabled, line: mind.line, task: mind.task }, { mineOn: false, enabled: false, line: null, task: null });
    mind = (await api('GET', '/duo', null, a)).body.minu.mind;
    assert.deepEqual({ partnerOn: mind.partnerOn, enabled: mind.enabled }, { partnerOn: false, enabled: false });
    assert.equal((await offer(a, { day: '2027-02-28', kind: 'plan', text })).status, 409);
    mind = (await api('PUT', '/duo/minu/mind', { on: true }, b)).body.minu.mind;
    assert.equal(mind.enabled, true);
    assert.equal(mind.line.text, text);

    // Máximo de intentos por día: después Minu usa su banco
    fakeNow = new Date('2027-03-01T15:00:00Z');
    for (let i = 0; i < 3; i++) await offer(a, { day: '2027-03-01', kind: 'cheer', text: 'Today is a good day for both of you to dance together.' });
    mind = (await api('GET', '/duo', null, a)).body.minu.mind;
    assert.equal(mind.task, null);
    assert.equal(mind.line, null);
    assert.equal((await offer(a, { day: '2027-03-01', kind: 'cheer', text })).body.reason, 'attempts');

    // Ensayo: 30 situaciones y revisión sin guardar
    const cases = (await api('GET', '/duo/minu/mind/rehearsal', null, a)).body.cases;
    assert.equal(cases.length, 30);
    assert.ok(cases.every((c) => c.prompt.includes('Encargo:')));
    const check = (await api('POST', '/duo/minu/mind/check', { text: 'Soy un asistente que los quiere mucho a los dos.' }, a)).body;
    assert.deepEqual(check, { ok: false, text: null, reason: 'banned' });

    // Borrar mis datos se lleva lo que redactó mi celular
    fakeNow = new Date('2027-02-28T16:00:00Z');
    await api('DELETE', '/duo/minu/data', null, a);
    await api('POST', '/duo/terms/accept', null, a);
    assert.equal((await api('GET', '/duo', null, b)).body.minu.mind.line, null);
  } finally {
    fakeNow = null;
  }
});
