import { localDay } from './duo.js';
import { joinedOn } from './memories.js';
import { petView } from './pet.js';
import { guardLine, offerLine, rehearsal, seeWake, setMindOn, wakeDay } from './mind.js';

/**
 * Rutas de la voz propia de Minu. El celular trae el encargo dentro de GET /duo (minu.mind.task), lo
 * redacta por dentro y lo ofrece aquí; el ensayo sirve para revisar a mano las 30 situaciones.
 */
export function registerMindRoutes(app, ctx) {
  const { db, auth, realtime, clock, fail, duoState, linked, consented } = ctx;
  const guard = [auth, linked, consented];
  const today = (req) => localDay(req.couple.tz, clock());

  // El mensaje del día que redactó mi celular: si pasa las barandas, es de los dos
  app.post('/duo/minu/mind/line', ...guard, async (req, res) => {
    const me = req.user;
    const couple = req.couple;
    const day = today(req);
    const wakes = wakeDay(await joinedOn(db, couple, me));
    const off = couple.mind?.off ?? {};
    if (!wakes || day < wakes || off[String(me._id)] || off[String(me.partnerId)]) {
      return fail(res, 409, 'Minu aún está aprendiendo de ustedes.');
    }
    const result = await offerLine(db, couple, me, day, {
      day: String(req.body?.day || ''), kind: String(req.body?.kind || ''), text: String(req.body?.text ?? '').slice(0, 2000),
    }, clock());
    if (result.error) return fail(res, result.status ?? 409, result.error);
    if (result.accepted) realtime.notify(me.partnerId, { type: 'duo.update' });
    res.json({ accepted: Boolean(result.accepted), reason: result.reason ?? null, duo: await duoState(db, me, clock()) });
  });

  // Encender o apagar "Mensajes personalizados de Minu"
  app.put('/duo/minu/mind', ...guard, async (req, res) => {
    const me = req.user;
    await setMindOn(db, req.couple._id, me, req.body?.on !== false);
    realtime.notify(me.partnerId, { type: 'duo.update' });
    res.json(await duoState(db, me, clock()));
  });

  // Ya vi la celebración del despertar
  app.post('/duo/minu/mind/woke', ...guard, async (req, res) => {
    await seeWake(db, req.couple._id, req.user, clock());
    res.json(await duoState(db, req.user, clock()));
  });

  // Ensayo: las 30 situaciones con su encargo, y la revisión de un texto sin guardarlo
  app.get('/duo/minu/mind/rehearsal', ...guard, async (req, res) => {
    const day = today(req);
    const pet = await petView(db, req.couple, req.user._id, day, null);
    res.json({ cases: rehearsal(pet, day) });
  });

  app.post('/duo/minu/mind/check', ...guard, (req, res) => {
    const verdict = guardLine(String(req.body?.text ?? '').slice(0, 2000));
    res.json({ ok: verdict.ok, text: verdict.text ?? null, reason: verdict.reason ?? null });
  });
}
