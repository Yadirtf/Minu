import express from 'express';
import { localDay } from './duo.js';
import {
  adventurePhoto, adventuresOf, answerLoveQuiz, completeAdventure, heartCompliment, PHOTO_MAX_BYTES, rateAdventure,
  reshuffleAdventure, saveAdventurePhoto, seeCompliment, voteAdventure, writeCompliments,
} from './novelty.js';

/**
 * Rutas de la novedad (Aventura de la semana, Cumplidos a ciegas, Lenguajes del amor). Se montan desde
 * registerMinuRoutes con sus mismos guardias. Las que cambian algo responden con el estado de Dúo.
 */
export function registerNoveltyRoutes(app, ctx) {
  const { db, auth, realtime, push, clock, fail, duoState, linked, consented } = ctx;
  const guard = [auth, linked, consented];

  const day = (req) => {
    const now = clock();
    return { today: localDay(req.couple.tz, now), now };
  };

  const tell = async (me, pushData) => {
    if (!realtime.notify(me.partnerId, { type: 'duo.update' })) {
      await push.send(me.partnerId, { ...pushData, fromName: me.displayName });
    }
  };

  const reply = async (req, res, result, status = 409) => {
    if (result.error) return fail(res, result.status ?? status, result.error);
    res.json(await duoState(db, req.user, clock()));
  };

  const validWeek = (week) => /^\d{4}-\d{2}-\d{2}$/.test(week);

  // ----- Aventura de la semana -----
  app.post('/duo/adventure/vote', ...guard, async (req, res) => {
    const me = req.user;
    const { today, now } = day(req);
    const result = await voteAdventure(db, me, req.couple, today, String(req.body?.planId || ''), now);
    if (!result.error) await tell(me, { type: result.chosen ? 'minu.adventure.chosen' : 'minu.adventure.vote' });
    await reply(req, res, result);
  });

  app.post('/duo/adventure/reshuffle', ...guard, async (req, res) => {
    const me = req.user;
    const { today, now } = day(req);
    const result = await reshuffleAdventure(db, req.couple, today, now);
    if (!result.error) await tell(me, { type: 'minu.adventure.reshuffle' });
    await reply(req, res, result);
  });

  app.post('/duo/adventure/done', ...guard, async (req, res) => {
    const me = req.user;
    const { today, now } = day(req);
    const result = await completeAdventure(db, me, req.couple, today, req.body?.song ?? null, now);
    if (result.done) await tell(me, { type: 'minu.adventure.done' });
    await reply(req, res, result);
  });

  app.post('/duo/adventures/:week/rate', ...guard, async (req, res) => {
    if (!validWeek(req.params.week)) return fail(res, 400, 'Semana inválida.');
    await reply(req, res, await rateAdventure(db, req.user, req.couple, req.params.week, req.body?.stars));
  });

  // La foto llega como JPEG crudo (la app ya la reduce)
  app.put(
    '/duo/adventures/:week/photo',
    ...guard,
    express.raw({ type: ['image/jpeg', 'application/octet-stream'], limit: PHOTO_MAX_BYTES + 1024 }),
    async (req, res) => {
      const me = req.user;
      const result = await saveAdventurePhoto(db, me, req.couple, req.params.week, req.body, clock());
      if (!result.error) await tell(me, { type: 'minu.adventure.photo' });
      await reply(req, res, result, 400);
    },
  );

  app.get('/duo/adventures/:week/photo', ...guard, async (req, res) => {
    if (!validWeek(req.params.week)) return fail(res, 400, 'Semana inválida.');
    const photo = await adventurePhoto(db, req.couple, req.params.week);
    if (!photo) return fail(res, 404, 'Sin foto.');
    res.set({ 'content-type': 'image/jpeg', 'cache-control': 'private, max-age=86400' });
    res.end(photo.data.buffer ?? photo.data);
  });

  // Las aventuras vividas de un mes (?month=AAAA-MM), para Recuerdos
  app.get('/duo/adventures', ...guard, async (req, res) => {
    const month = String(req.query.month || '');
    if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month)) return fail(res, 400, 'Mes inválido.');
    const me = req.user;
    res.json({ month, items: await adventuresOf(db, req.couple._id, me._id, me.partnerId, month) });
  });

  // ----- Cumplidos a ciegas -----
  app.post('/duo/compliments', ...guard, async (req, res) => {
    const { today, now } = day(req);
    await reply(req, res, await writeCompliments(db, req.user, req.couple._id, today, req.body?.texts, now)); // el otro no sabe nada hasta el día de la sorpresa
  });

  app.post('/duo/compliments/:id/seen', ...guard, async (req, res) => {
    const { today, now } = day(req);
    await reply(req, res, await seeCompliment(db, req.user, req.couple._id, today, req.params.id, now));
  });

  app.post('/duo/compliments/:id/heart', ...guard, async (req, res) => {
    const me = req.user;
    const { today, now } = day(req);
    const result = await heartCompliment(db, me, req.couple._id, today, req.params.id, now);
    if (!result.error) await tell(me, { type: 'minu.compliment.heart', text: result.compliment.text.slice(0, 120) });
    await reply(req, res, result);
  });

  // ----- Lenguajes del amor -----
  app.post('/duo/love', ...guard, async (req, res) => {
    await reply(req, res, await answerLoveQuiz(db, req.user, req.couple._id, req.body?.answers, clock()));
  });
}
