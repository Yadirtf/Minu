import express from 'express';
import bcrypt from 'bcryptjs';
import { cleanSong, coupleKey, couples, dedications, libraries, publicUser, toId, users } from './db.js';
import { requireAuth, signToken } from './auth.js';

const USERNAME = /^[a-z0-9_.]{3,24}$/;
const EMOJIS = new Set(['❤️', '😍', '🔥', '😘', '💃', '🎶']);

/**
 * API REST de Minu. [realtime] permite avisar al otro celular al instante
 * (notify devuelve true si estaba conectado).
 */
export function createApp(db, config, realtime, services = {}) {
  const push = services.push ?? { send: async () => 0 };
  const verifyGoogle = services.verifyGoogle ?? null;
  const allowed = new Set((config.allowedEmails ?? []).map((e) => e.toLowerCase()));
  const app = express();
  app.disable('x-powered-by');
  app.use(express.json({ limit: '3mb' }));

  const auth = requireAuth(db, config);
  const loginLimiter = rateLimiter(config.loginLimitPerMinute ?? 10, 60_000);

  app.get('/', (_req, res) => res.json({ app: 'minu', ok: true }));
  app.get('/health', (_req, res) => res.json({ ok: true }));

  // ---------- Cuentas ----------

  app.post('/auth/register', loginLimiter, async (req, res) => {
    const { username, password, displayName, inviteCode } = req.body ?? {};
    if (!config.registrationCode) return fail(res, 403, 'El registro con contraseña está desactivado. Usa "Continuar con Google".');
    if (inviteCode !== config.registrationCode) return fail(res, 403, 'Código de invitación incorrecto.');
    const name = String(username || '').trim().toLowerCase();
    if (!USERNAME.test(name)) return fail(res, 400, 'El usuario debe tener de 3 a 24 letras, números, punto o guion bajo.');
    if (typeof password !== 'string' || password.length < 6) return fail(res, 400, 'La contraseña debe tener al menos 6 caracteres.');

    const user = {
      username: name,
      displayName: String(displayName || name).trim().slice(0, 40) || name,
      passwordHash: await bcrypt.hash(password, 10),
      createdAt: new Date(),
      partnerId: null,
      partnerRequestTo: null,
    };
    try {
      const { insertedId } = await users(db).insertOne(user);
      user._id = insertedId;
    } catch (e) {
      if (e.code === 11000) return fail(res, 409, 'Ese usuario ya existe.');
      throw e;
    }
    res.status(201).json({ token: signToken(user, config), user: publicUser(user) });
  });

  app.post('/auth/login', loginLimiter, async (req, res) => {
    const { username, password } = req.body ?? {};
    const user = await users(db).findOne({ username: String(username || '').trim().toLowerCase() });
    if (!user?.passwordHash || !(await bcrypt.compare(String(password || ''), user.passwordHash))) {
      return fail(res, 401, 'Usuario o contraseña incorrectos.');
    }
    res.json({ token: signToken(user, config), user: publicUser(user) });
  });

  // Inicio de sesión con Google: solo los correos de ALLOWED_EMAILS pueden crear cuenta
  app.post('/auth/google', loginLimiter, async (req, res) => {
    if (!verifyGoogle) return fail(res, 503, 'El inicio con Google no está configurado en el servidor.');
    let google;
    try {
      google = await verifyGoogle(String(req.body?.idToken || ''));
    } catch {
      return fail(res, 401, 'No se pudo verificar tu cuenta de Google.');
    }
    let user = await users(db).findOne({ googleSub: google.sub });
    if (!user) {
      user = await users(db).findOne({ email: google.email });
      if (user) await users(db).updateOne({ _id: user._id }, { $set: { googleSub: google.sub } });
    }
    if (!user) {
      if (!allowed.has(google.email)) {
        return fail(res, 403, `El correo ${google.email} no está autorizado. Agrégalo a ALLOWED_EMAILS en Render.`);
      }
      user = {
        username: await freeUsername(db, google.email),
        displayName: (google.name || google.email.split('@')[0]).slice(0, 40),
        email: google.email,
        googleSub: google.sub,
        passwordHash: null,
        createdAt: new Date(),
        partnerId: null,
        partnerRequestTo: null,
      };
      user._id = (await users(db).insertOne(user)).insertedId;
      res.status(201);
    }
    res.json({ token: signToken(user, config), user: publicUser(user) });
  });

  // Celulares que reciben notificaciones (token de Firebase Cloud Messaging)
  app.post('/devices', auth, async (req, res) => {
    const token = String(req.body?.token || '');
    if (!token || token.length > 4096) return fail(res, 400, 'Token inválido.');
    await users(db).updateOne({ _id: req.user._id }, { $pull: { fcmTokens: token } });
    await users(db).updateOne({ _id: req.user._id }, { $push: { fcmTokens: { $each: [token], $slice: -5 } } });
    res.json({ ok: true });
  });

  app.delete('/devices', auth, async (req, res) => {
    await users(db).updateOne({ _id: req.user._id }, { $pull: { fcmTokens: String(req.body?.token || '') } });
    res.json({ ok: true });
  });

  app.get('/me', auth, (req, res) => res.json({ user: publicUser(req.user) }));

  // ---------- Biblioteca (respaldo en la nube) ----------

  app.get('/library', auth, async (req, res) => {
    const doc = await libraries(db).findOne({ _id: req.user._id });
    res.json({ data: doc?.data ?? null, updatedAt: doc?.updatedAt?.getTime() ?? 0 });
  });

  app.put('/library', auth, async (req, res) => {
    const { data } = req.body ?? {};
    if (!data || typeof data !== 'object' || !Array.isArray(data.playlists)) {
      return fail(res, 400, 'Biblioteca inválida.');
    }
    const updatedAt = new Date();
    await libraries(db).updateOne(
      { _id: req.user._id },
      { $set: { data, updatedAt, device: String(req.body.device || '').slice(0, 80) } },
      { upsert: true },
    );
    res.json({ updatedAt: updatedAt.getTime() });
  });

  // ---------- Pareja ----------

  app.get('/partner', auth, async (req, res) => res.json(await partnerStatus(db, req.user, realtime)));

  app.post('/partner/request', auth, async (req, res) => {
    const me = req.user;
    if (me.partnerId) return fail(res, 409, 'Ya tienes una pareja vinculada.');
    const target = await users(db).findOne({ username: String(req.body?.username || '').trim().toLowerCase() });
    if (!target) return fail(res, 404, 'No existe ese usuario.');
    if (target._id.equals(me._id)) return fail(res, 400, 'No puedes vincularte contigo mismo.');
    if (target.partnerId) return fail(res, 409, 'Esa persona ya tiene pareja vinculada.');

    if (target.partnerRequestTo?.equals(me._id)) {
      await link(db, me._id, target._id); // ya te había invitado: quedan vinculados
      await push.send(target._id, { type: 'partner.accepted', fromName: me.displayName });
    } else {
      await users(db).updateOne({ _id: me._id }, { $set: { partnerRequestTo: target._id } });
      await push.send(target._id, { type: 'partner.request', fromName: me.displayName });
    }
    realtime.notify(target._id, { type: 'partner.update' });
    res.json(await partnerStatus(db, await users(db).findOne({ _id: me._id }), realtime));
  });

  app.post('/partner/accept', auth, async (req, res) => {
    const me = req.user;
    const requester = await users(db).findOne({ partnerRequestTo: me._id, partnerId: null });
    if (!requester || me.partnerId) return fail(res, 404, 'No tienes invitaciones pendientes.');
    await link(db, me._id, requester._id);
    if (!realtime.notify(requester._id, { type: 'partner.update' })) {
      await push.send(requester._id, { type: 'partner.accepted', fromName: me.displayName });
    }
    res.json(await partnerStatus(db, await users(db).findOne({ _id: me._id }), realtime));
  });

  app.delete('/partner', auth, async (req, res) => {
    const me = req.user;
    const other = me.partnerId ?? me.partnerRequestTo;
    await users(db).updateOne({ _id: me._id }, { $set: { partnerId: null, partnerRequestTo: null } });
    // Rechazar una invitación recibida también la borra
    await users(db).updateMany(
      { $or: [{ partnerId: me._id }, { partnerRequestTo: me._id }] },
      { $set: { partnerId: null, partnerRequestTo: null } },
    );
    if (other) realtime.notify(other, { type: 'partner.update' });
    res.json(await partnerStatus(db, await users(db).findOne({ _id: me._id }), realtime));
  });

  // ---------- Dedicatorias ----------

  app.get('/dedications', auth, async (req, res) => {
    const me = req.user._id;
    const list = await dedications(db)
      .find({ $or: [{ to: me }, { from: me }] })
      .sort({ createdAt: -1 })
      .limit(100)
      .toArray();
    res.json({ dedications: list.map((d) => serializeDedication(d, me)) });
  });

  app.post('/dedications', auth, async (req, res) => {
    const me = req.user;
    if (!me.partnerId) return fail(res, 409, 'Primero vincula a tu pareja.');
    const song = cleanSong(req.body?.song);
    if (!song) return fail(res, 400, 'Canción inválida.');
    const dedication = {
      from: me._id,
      to: me.partnerId,
      fromName: me.displayName,
      song,
      message: String(req.body?.message || '').slice(0, 500),
      autoplay: Boolean(req.body?.autoplay),
      createdAt: new Date(),
      seenAt: null,
      reactions: [],
    };
    const { insertedId } = await dedications(db).insertOne(dedication);
    dedication._id = insertedId;
    const delivered = realtime.notify(me.partnerId, {
      type: 'dedication',
      dedication: serializeDedication(dedication, me.partnerId),
    });
    // Si su app no está abierta le llega como notificación (y suena si pediste reproducir ya)
    const pushed = delivered ? 0 : await push.send(me.partnerId, {
      type: 'dedication',
      id: insertedId.toString(),
      fromName: me.displayName,
      message: dedication.message,
      autoplay: dedication.autoplay,
      ...song,
    });
    res.status(201).json({ dedication: serializeDedication(dedication, me._id), delivered, pushed: pushed > 0 });
  });

  app.post('/dedications/:id/seen', auth, async (req, res) => {
    const id = toId(req.params.id);
    if (!id) return fail(res, 404, 'No encontrada.');
    const { modifiedCount } = await dedications(db).updateOne(
      { _id: id, to: req.user._id, seenAt: null },
      { $set: { seenAt: new Date() } },
    );
    if (modifiedCount) {
      const d = await dedications(db).findOne({ _id: id });
      realtime.notify(d.from, { type: 'dedication.update', dedication: serializeDedication(d, d.from) });
    }
    res.json({ ok: true });
  });

  app.post('/dedications/:id/react', auth, async (req, res) => {
    const id = toId(req.params.id);
    const emoji = String(req.body?.emoji || '');
    if (!id || !EMOJIS.has(emoji)) return fail(res, 400, 'Reacción inválida.');
    const d = await dedications(db).findOneAndUpdate(
      { _id: id, $or: [{ to: req.user._id }, { from: req.user._id }] },
      { $push: { reactions: { $each: [{ by: req.user._id, emoji, at: new Date() }], $slice: -30 } } },
      { returnDocument: 'after' },
    );
    if (!d) return fail(res, 404, 'No encontrada.');
    const other = d.from.equals(req.user._id) ? d.to : d.from;
    realtime.notify(other, { type: 'dedication.update', dedication: serializeDedication(d, other) });
    res.json({ dedication: serializeDedication(d, req.user._id) });
  });

  // ---------- Nuestra fecha (Dúo) ----------

  app.get('/duo', auth, async (req, res) => {
    const me = req.user;
    if (!me.partnerId) return res.json({ linked: false, origin: null, stats: null });
    res.json(await duoState(db, me));
  });

  // Día en que empezó la relación, la canción de origen y cómo empezó. Es de los dos: cualquiera la cambia.
  app.put('/duo/origin', auth, async (req, res) => {
    const me = req.user;
    if (!me.partnerId) return fail(res, 409, 'Primero vincula a tu pareja.');
    const startedOn = String(req.body?.startedOn || '');
    if (!validDay(startedOn)) return fail(res, 400, 'Fecha inválida.');
    const rawSong = req.body?.song;
    const song = rawSong ? cleanSong(rawSong) : null;
    if (rawSong && !song) return fail(res, 400, 'Canción inválida.');
    const origin = {
      startedOn,
      song,
      story: String(req.body?.story || '').trim().slice(0, 140),
      setBy: me._id,
      setByName: me.displayName,
      setAt: new Date(),
    };
    const key = coupleKey(me._id, me.partnerId);
    await couples(db).updateOne(
      { _id: key },
      { $set: { origin, members: [me._id, me.partnerId] }, $setOnInsert: { createdAt: new Date() } },
      { upsert: true },
    );
    if (!realtime.notify(me.partnerId, { type: 'duo.update' })) {
      await push.send(me.partnerId, { type: 'duo.origin', fromName: me.displayName });
    }
    res.json(await duoState(db, me));
  });

  app.use((err, _req, res, _next) => {
    if (err.type === 'entity.too.large') return fail(res, 413, 'Los datos son demasiado grandes.');
    if (err.type === 'entity.parse.failed') return fail(res, 400, 'JSON inválido.');
    console.error(err);
    fail(res, 500, 'Error del servidor.');
  });

  return app;
}

/** Usuario a partir del correo (daniel.r@gmail.com -> daniel.r), sin repetir. */
async function freeUsername(db, email) {
  let base = email.split('@')[0].toLowerCase().replace(/[^a-z0-9_.]/g, '').slice(0, 20);
  if (base.length < 3) base = (base + 'minu').slice(0, 20);
  let candidate = base;
  for (let i = 2; await users(db).findOne({ username: candidate }); i++) candidate = `${base}${i}`;
  return candidate;
}

async function link(db, a, b) {
  await users(db).updateOne({ _id: a }, { $set: { partnerId: b, partnerRequestTo: null } });
  await users(db).updateOne({ _id: b }, { $set: { partnerId: a, partnerRequestTo: null } });
}

export async function partnerStatus(db, me, realtime) {
  if (me.partnerId) {
    const partner = await users(db).findOne({ _id: me.partnerId });
    return { status: 'linked', partner: { ...publicUser(partner), online: realtime.isOnline(partner._id) } };
  }
  if (me.partnerRequestTo) {
    const target = await users(db).findOne({ _id: me.partnerRequestTo });
    return { status: 'sent', partner: publicUser(target) };
  }
  const requester = await users(db).findOne({ partnerRequestTo: me._id, partnerId: null });
  if (requester) return { status: 'received', partner: publicUser(requester) };
  return { status: 'none', partner: null };
}

async function duoState(db, me) {
  const couple = await couples(db).findOne({ _id: coupleKey(me._id, me.partnerId) });
  const o = couple?.origin;
  const between = { $or: [{ from: me._id, to: me.partnerId }, { from: me.partnerId, to: me._id }] };
  return {
    linked: true,
    origin: o
      ? {
          startedOn: o.startedOn,
          song: o.song ?? null,
          story: o.story ?? '',
          setByName: o.setByName,
          setByMe: o.setBy.equals(me._id),
          setAt: o.setAt.getTime(),
        }
      : null,
    stats: { dedications: await dedications(db).countDocuments(between) },
  };
}

/** "AAAA-MM-DD" que existe en el calendario, desde 1950 y no más allá de mañana (por las zonas horarias). */
function validDay(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  if (Number.isNaN(date.getTime()) || date.toISOString().slice(0, 10) !== value) return false;
  return value >= '1950-01-01' && date.getTime() <= Date.now() + 36 * 3600_000;
}

function serializeDedication(d, viewer) {
  return {
    id: d._id.toString(),
    incoming: d.to.equals(viewer),
    fromName: d.fromName,
    song: d.song,
    message: d.message,
    autoplay: d.autoplay,
    createdAt: d.createdAt.getTime(),
    seenAt: d.seenAt ? d.seenAt.getTime() : null,
    reactions: (d.reactions ?? []).map((r) => ({ emoji: r.emoji, mine: r.by.equals(viewer), at: r.at.getTime() })),
  };
}

function fail(res, status, error) {
  return res.status(status).json({ error });
}

/** Límite simple por IP para evitar fuerza bruta en el inicio de sesión. */
function rateLimiter(max, windowMs) {
  const hits = new Map();
  return (req, res, next) => {
    const now = Date.now();
    const key = req.ip;
    const entry = hits.get(key);
    if (!entry || now - entry.start > windowMs) {
      hits.set(key, { start: now, count: 1 });
      return next();
    }
    if (++entry.count > max) return fail(res, 429, 'Demasiados intentos. Espera un minuto.');
    next();
  };
}
