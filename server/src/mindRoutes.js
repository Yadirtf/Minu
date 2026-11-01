import { localDay } from './duo.js';
import { petView } from './pet.js';
import { guardLine, rehearsal, rehearseCase, seeWake, setMindOn } from './mind.js';

/**
 * Rutas de la voz propia de Minu. El mensaje del día se redacta solo, en segundo plano, cuando alguien
 * abre Dúo (ver mindView); aquí están el ajuste, la celebración y el ensayo de las 30 situaciones.
 */
export function registerMindRoutes(app, ctx) {
  const { db, auth, realtime, clock, duoState, fail, linked, consented } = ctx;
  const guard = [auth, linked, consented];
  const today = (req) => localDay(req.couple.tz, clock());

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

  // Ensayo: las 30 situaciones, redactar una (sin guardar) y revisar un texto cualquiera
  app.get('/duo/minu/mind/rehearsal', ...guard, async (req, res) => {
    const day = today(req);
    const pet = await petView(db, req.couple, req.user._id, day, null);
    res.json({ cases: rehearsal(pet, day).map(({ prompt, ...c }) => c) });
  });

  app.post('/duo/minu/mind/rehearsal/:id', ...guard, async (req, res) => {
    const day = today(req);
    const pet = await petView(db, req.couple, req.user._id, day, null);
    const result = await rehearseCase(pet, day, String(req.params.id));
    if (result.error) return fail(res, result.status, result.error);
    res.json(result);
  });

  app.post('/duo/minu/mind/check', ...guard, (req, res) => {
    const verdict = guardLine(String(req.body?.text ?? '').slice(0, 2000));
    res.json({ ok: verdict.ok, text: verdict.text ?? null, reason: verdict.reason ?? null });
  });
}
