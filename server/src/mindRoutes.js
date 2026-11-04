import { guardLine, seeWake, setMindOn } from './mind.js';

/**
 * Rutas de la voz propia de Minu. El mensaje del día se redacta solo, en segundo plano, cuando alguien
 * abre Dúo (ver mindView); aquí están el ajuste, la celebración y la revisión de un texto con las barandas.
 */
export function registerMindRoutes(app, ctx) {
  const { db, auth, realtime, clock, duoState, linked, consented } = ctx;
  const guard = [auth, linked, consented];

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

  // Revisa un texto cualquiera con las barandas (no redacta ni guarda)
  app.post('/duo/minu/mind/check', ...guard, (req, res) => {
    const verdict = guardLine(String(req.body?.text ?? '').slice(0, 2000));
    res.json({ ok: verdict.ok, text: verdict.text ?? null, reason: verdict.reason ?? null });
  });
}
