import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { ObjectId } from 'mongodb';
import { Platform } from 'youtubei.js';
import { createApp } from '../src/app.js';
import { signToken } from '../src/auth.js';
import { pickAudioFormat, secondsLeft } from '../src/stream.js';

const config = { jwtSecret: 'secreto-de-prueba', registrationCode: 'minu-2026', loginLimitPerMinute: 1000, diagnoseLimitPerMinute: 1000 };
let server, base, token;

// Base de datos falsa: solo hace falta encontrar al usuario del token
const user = { _id: new ObjectId(), username: 'daniel', displayName: 'Daniel' };
const db = { collection: () => ({ findOne: async ({ _id }) => (_id?.equals(user._id) ? user : null) }) };

// Streamer falso: no sale a internet
const streamer = {
  diagnose: async () => ({ ok: true, workingClients: ['IOS'], summary: 'ok', results: [] }),
  proxy: async (req, res, videoId) => {
    res.status(req.get('range') ? 206 : 200).set('content-type', 'audio/mp4').send(`audio:${videoId}:${req.get('range') ?? ''}`);
  },
};

before(async () => {
  server = http.createServer(createApp(db, config, { notify: () => false, isOnline: () => false }, { streamer }));
  await new Promise((r) => server.listen(0, r));
  base = `http://localhost:${server.address().port}`;
  token = signToken(user, config);
});

after(async () => {
  server.closeAllConnections?.();
  await new Promise((r) => server.close(r));
});

test('elige audio m4a antes que opus, y el de mayor bitrate', () => {
  const formats = [
    { has_audio: true, has_video: true, mime_type: 'video/mp4', bitrate: 900, url: 'v' },
    { has_audio: true, has_video: false, mime_type: 'audio/webm; codecs="opus"', bitrate: 160, url: 'o' },
    { has_audio: true, has_video: false, mime_type: 'audio/mp4; codecs="mp4a.40.2"', bitrate: 48, url: 'a1' },
    { has_audio: true, has_video: false, mime_type: 'audio/mp4; codecs="mp4a.40.2"', bitrate: 128, url: 'a2' },
  ];
  assert.equal(pickAudioFormat(formats).url, 'a2');
  assert.equal(pickAudioFormat([]), null);
});

test('las URL de googlevideo vencen con margen', () => {
  const now = 1_000_000_000_000;
  assert.ok(secondsLeft(`https://x.googlevideo.com/videoplayback?expire=${now / 1000 + 3600}`, now) > 3000);
  assert.ok(secondsLeft(`https://x.googlevideo.com/videoplayback?expire=${now / 1000 + 60}`, now) <= 0);
  assert.equal(secondsLeft('https://x.googlevideo.com/videoplayback', now), 0);
});

test('el código del reproductor corre aislado, sin acceso a process', () => {
  assert.equal(Platform.shim.eval({ output: 'return typeof process + "/" + typeof require' }), 'undefined/undefined');
  assert.equal(Platform.shim.eval({ output: 'return { n: "abc".split("").reverse().join("") }' }).n, 'cba');
  assert.throws(() => Platform.shim.eval({ output: 'while (true) {}' }), /timed out/);
});

test('diagnóstico público', async () => {
  const res = await fetch(`${base}/stream/test`);
  assert.equal(res.status, 200);
  assert.deepEqual((await res.json()).workingClients, ['IOS']);
});

test('audio: pide sesión, acepta ?token= y reenvía Range', async () => {
  assert.equal((await fetch(`${base}/stream/dQw4w9WgXcQ`)).status, 401);
  assert.equal((await fetch(`${base}/stream/malo?token=${token}`)).status, 400);
  const res = await fetch(`${base}/stream/dQw4w9WgXcQ?token=${token}`, { headers: { range: 'bytes=0-99' } });
  assert.equal(res.status, 206);
  assert.equal(await res.text(), 'audio:dQw4w9WgXcQ:bytes=0-99');
  const header = await fetch(`${base}/stream/dQw4w9WgXcQ`, { headers: { authorization: `Bearer ${token}` } });
  assert.equal(header.status, 200);
});
