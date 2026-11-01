import { careCheck, HELP_LINES } from './care.js';
import { localDay } from './duo.js';
import {
  adjustTruce, confirmPromise, dropPromise, keepPromise, makePromise, openUnsaid, repairTruce, replyUnsaid, sendUnsaid, startTruce, thankUnsaid,
} from './trust.js';

/**
 * Rutas de confianza y reparación (Buzón de lo no dicho, Tregua, Promesas pequeñas). Se montan desde
 * registerMinuRoutes con sus mismos guardias. Las que cambian algo responden con el estado de Dúo.
 * Los avisos al otro nunca llevan el contenido del Buzón.
 */
export function registerTrustRoutes(app, ctx) {
  const { db, auth, realtime, push, clock, fail, duoState, linked, consented } = ctx;
  const guard = [auth, linked, consented];

  const day = (req) => {
    const now = clock();
    return { key: req.couple._id, today: localDay(req.couple.tz, now), now };
  };

  /** En vivo solo hace falta refrescar; si la app está cerrada, una notificación. */
  const tell = async (me, pushData) => {
    if (!realtime.notify(me.partnerId, { type: 'duo.update' })) {
      await push.send(me.partnerId, { ...pushData, fromName: me.displayName });
    }
  };

  const reply = async (req, res, result, status = 409) => {
    if (result.error) return fail(res, result.status ?? status, result.error);
    res.json(await duoState(db, req.user, clock()));
  };

  /**
   * ¿Suena a peligro? La app pregunta antes de enviar algo del Buzón o de la Tregua. No se guarda nada:
   * ni el texto ni que hubo una alerta. Solo responde a quien lo escribió.
   */
  app.post('/minu/care', auth, (req, res) => {
    const texts = Array.isArray(req.body?.texts) ? req.body.texts.slice(0, 5).map((t) => String(t ?? '').slice(0, 1000)) : [];
    const check = careCheck(...texts);
    res.json({ ...check, help: check.risk ? HELP_LINES : null });
  });

  // ----- Buzón de lo no dicho -----
  app.post('/duo/unsaid', ...guard, async (req, res) => {
    const me = req.user;
    const { key, today, now } = day(req);
    const result = await sendUnsaid(db, me, key, today, req.body, now);
    if (!result.error) await tell(me, { type: 'minu.unsaid' });
    await reply(req, res, result, 429);
  });

  app.post('/duo/unsaid/:id/open', ...guard, async (req, res) => {
    const { key, now } = day(req);
    await reply(req, res, await openUnsaid(db, req.user, key, req.params.id, now)); // abrir no avisa a nadie
  });

  app.post('/duo/unsaid/:id/thanks', ...guard, async (req, res) => {
    const me = req.user;
    const { key, now } = day(req);
    const result = await thankUnsaid(db, me, key, req.params.id, now);
    if (!result.error) await tell(me, { type: 'minu.unsaid.thanks' });
    await reply(req, res, result);
  });

  app.post('/duo/unsaid/:id/reply', ...guard, async (req, res) => {
    const me = req.user;
    const { key, now } = day(req);
    const result = await replyUnsaid(db, me, key, req.params.id, req.body?.text, now);
    if (!result.error) await tell(me, { type: 'minu.unsaid.reply' });
    await reply(req, res, result);
  });

  // ----- Tregua -----
  app.post('/duo/truce', ...guard, async (req, res) => {
    const me = req.user;
    const { key, now } = day(req);
    const result = await startTruce(db, me, key, now);
    if (result.started) await tell(me, { type: 'minu.truce' });
    await reply(req, res, result);
  });

  for (const action of ['extend', 'ready']) {
    app.post(`/duo/truce/${action}`, ...guard, async (req, res) => {
      const me = req.user;
      const { key, now } = day(req);
      const result = await adjustTruce(db, me, key, action, now);
      if (!result.error) await tell(me, { type: `minu.truce.${action}` });
      await reply(req, res, result);
    });
  }

  app.post('/duo/truce/repair', ...guard, async (req, res) => {
    const me = req.user;
    const { key, now } = day(req);
    const result = await repairTruce(db, me, key, req.body?.text, now);
    if (!result.error) await tell(me, { type: result.together ? 'minu.truce.together' : 'minu.truce.repair', text: String(req.body?.text || '').slice(0, 140) });
    await reply(req, res, result);
  });

  // ----- Promesas pequeñas -----
  app.post('/duo/promises', ...guard, async (req, res) => {
    const me = req.user;
    const { key, now } = day(req);
    const result = await makePromise(db, me, key, req.body?.text, req.body?.dueAt, now);
    if (!result.error) await tell(me, { type: 'minu.promise', text: result.text });
    await reply(req, res, result, 429);
  });

  app.post('/duo/promises/:id/kept', ...guard, async (req, res) => {
    const me = req.user;
    const { key, now } = day(req);
    const result = await keepPromise(db, me, key, req.params.id, now);
    if (!result.error) await tell(me, { type: 'minu.promise.kept', text: result.promise.text });
    await reply(req, res, result);
  });

  app.post('/duo/promises/:id/confirm', ...guard, async (req, res) => {
    const me = req.user;
    const { key, today, now } = day(req);
    const result = await confirmPromise(db, me, key, today, req.params.id, now);
    if (!result.error) await tell(me, { type: 'minu.promise.thanks', text: result.promise.text });
    await reply(req, res, result);
  });

  app.delete('/duo/promises/:id', ...guard, async (req, res) => {
    const { key } = day(req);
    await reply(req, res, await dropPromise(db, req.user, key, req.params.id));
  });
}
