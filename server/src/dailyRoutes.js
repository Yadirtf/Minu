import { contentBundle } from './content.js';
import {
  answerQuestion, cheerNews, giveThanks, jarOf, openedQuestions, reactQuestion, replyTouch, sendTouch, shareNews,
} from './daily.js';
import { localDay } from './duo.js';
import { joinedOn } from './memories.js';
import { monthsBetween, stageFor } from './pet.js';

/**
 * Rutas de la conexión diaria (Toque, Pregunta del día, Gracias por..., Te cuento algo bueno). Se montan
 * desde registerMinuRoutes con sus mismos guardias: pareja vinculada y los dos con los términos aceptados.
 * Las que cambian algo responden con el estado de Dúo completo.
 */
export function registerDailyRoutes(app, ctx) {
  const { db, auth, realtime, push, clock, fail, duoState, linked, consented } = ctx;
  const guard = [auth, linked, consented];

  /** Pareja, día local y "ahora" de la petición. */
  const day = (req) => {
    const now = clock();
    return { key: req.couple._id, today: localDay(req.couple.tz, now), now };
  };

  /** Avisa al otro: en vivo si tiene la app abierta (con [live] para animar) o con una notificación. */
  const tell = async (me, live, pushData) => {
    if (!realtime.notify(me.partnerId, { ...live, fromName: me.displayName })) {
      await push.send(me.partnerId, { ...pushData, fromName: me.displayName });
    }
  };

  const reply = async (req, res, result, status = 400) => {
    if (result.error) return fail(res, result.status ?? status, result.error);
    res.json(await duoState(db, req.user, clock()));
  };

  // El banco de frases para tener a mano sin conexión (respuestas entusiastas, reacciones…)
  app.get('/minu/content', auth, (req, res) => res.json(contentBundle()));

  // ----- Toque -----
  app.post('/duo/touch', ...guard, async (req, res) => {
    const me = req.user;
    const { key, today, now } = day(req);
    const result = await sendTouch(db, me, key, today, String(req.body?.kind || ''), now);
    if (!result.error) {
      const id = String(result.touch._id);
      await tell(me, { type: 'minu.touch', id, kind: result.touch.kind }, { type: 'minu.touch', id, kind: result.touch.kind });
    }
    await reply(req, res, result, 429);
  });

  app.post('/duo/touch/:id/reply', ...guard, async (req, res) => {
    const me = req.user;
    const { key, today, now } = day(req);
    const result = await replyTouch(db, me, key, today, req.params.id, String(req.body?.kind || ''), now);
    if (!result.error) {
      const data = { type: 'minu.touch.reply', id: req.params.id, kind: result.touch.reply.kind };
      await tell(me, data, data);
    }
    await reply(req, res, result, 409);
  });

  // ----- Pregunta del día -----
  app.post('/duo/question/answer', ...guard, async (req, res) => {
    const me = req.user;
    const { key, today, now } = day(req);
    const months = monthsBetween((await joinedOn(db, req.couple, me)) ?? today, today);
    const result = await answerQuestion(db, me, key, today, stageFor(months), req.body?.text, now);
    if (!result.error) {
      const type = result.opened ? 'minu.question.open' : 'minu.question';
      await tell(me, { type: 'duo.update' }, { type });
    }
    await reply(req, res, result);
  });

  app.post('/duo/question/react', ...guard, async (req, res) => {
    const me = req.user;
    const { key, today, now } = day(req);
    const result = await reactQuestion(db, me, key, today, req.body?.text, now);
    if (!result.error) await tell(me, { type: 'duo.update' }, { type: 'minu.question.react' });
    await reply(req, res, result, 409);
  });

  app.get('/duo/questions', ...guard, async (req, res) => {
    const me = req.user;
    res.json({ questions: await openedQuestions(db, req.couple._id, me._id, me.partnerId) });
  });

  // ----- Gracias por... -----
  app.post('/duo/thanks', ...guard, async (req, res) => {
    const me = req.user;
    const { key, today, now } = day(req);
    const result = await giveThanks(db, me, key, today, req.body?.text, now);
    if (!result.error) await tell(me, { type: 'duo.update' }, { type: 'minu.thanks' });
    await reply(req, res, result, 409);
  });

  // El frasco de un mes ya cerrado, para Recuerdos (?month=AAAA-MM)
  app.get('/duo/jar', ...guard, async (req, res) => {
    const month = String(req.query.month || '');
    if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month)) return fail(res, 400, 'Mes inválido.');
    res.json({ month, items: await jarOf(db, req.couple._id, req.user._id, month) });
  });

  // ----- Te cuento algo bueno -----
  app.post('/duo/news', ...guard, async (req, res) => {
    const me = req.user;
    const { key, today, now } = day(req);
    const result = await shareNews(db, me, key, today, req.body?.text, now);
    if (!result.error) await tell(me, { type: 'duo.update' }, { type: 'minu.news', text: String(req.body?.text || '').slice(0, 120) });
    await reply(req, res, result, 429);
  });

  app.post('/duo/news/:id/cheer', ...guard, async (req, res) => {
    const me = req.user;
    const { key, today, now } = day(req);
    const result = await cheerNews(db, me, key, today, req.params.id, req.body?.text, now);
    if (!result.error) await tell(me, { type: 'duo.update' }, { type: 'minu.cheer' });
    await reply(req, res, result, 409);
  });
}
