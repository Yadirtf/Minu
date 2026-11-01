import { coupleKey, couples } from './db.js';
import { registerDailyRoutes } from './dailyRoutes.js';
import { registerTrustRoutes } from './trustRoutes.js';
import { registerNoveltyRoutes } from './noveltyRoutes.js';
import { registerMindRoutes } from './mindRoutes.js';
import { localDay } from './duo.js';
import { acceptTerms, eraseMyMinuData, minuAllowed } from './minu.js';
import { acceptPetChange, dropPetProposal, proposePetChange } from './pet.js';

const KINDS = new Set(['name', 'species']);

/**
 * Rutas de Minu (términos y mascota). Todas responden con el estado de Dúo completo para que la app
 * se actualice con una sola llamada. [ctx]: { db, auth, realtime, push, clock, fail, duoState }.
 */
export function registerMinuRoutes(app, ctx) {
  const { db, auth, realtime, push, clock, fail, duoState } = ctx;

  const linked = (req, res, next) => (req.user.partnerId ? next() : fail(res, 409, 'Primero vincula a tu pareja.'));

  /** Las acciones de la mascota necesitan que los dos hayan aceptado los términos vigentes. */
  const consented = async (req, res, next) => {
    const me = req.user;
    const couple = await couples(db).findOne({ _id: coupleKey(me._id, me.partnerId) });
    if (!minuAllowed(couple, me._id, me.partnerId)) {
      return fail(res, 403, 'Minu se despierta cuando los dos acepten cómo cuida sus datos.');
    }
    req.couple = couple;
    next();
  };

  const tell = async (me, type, data = {}) => {
    if (!realtime.notify(me.partnerId, { type: 'duo.update' })) {
      await push.send(me.partnerId, { type, fromName: me.displayName, ...data });
    }
  };

  app.post('/duo/terms/accept', auth, linked, async (req, res) => {
    const me = req.user;
    const couple = await couples(db).findOne({ _id: coupleKey(me._id, me.partnerId) });
    const { both } = await acceptTerms(db, me, localDay(couple?.tz, clock()), clock());
    await tell(me, both ? 'minu.born' : 'minu.terms');
    res.json(await duoState(db, me, clock()));
  });

  // Proponer otro nombre ({ kind: 'name', value }) u otra mascota ({ kind: 'species', value: 'dog' })
  app.post('/duo/pet/proposals', auth, linked, consented, async (req, res) => {
    const me = req.user;
    const kind = String(req.body?.kind || '');
    if (!KINDS.has(kind)) return fail(res, 400, 'Propuesta inválida.');
    const result = await proposePetChange(db, req.couple._id, me, kind, req.body?.value, clock());
    if (result.error) return fail(res, 400, result.error);
    await tell(me, 'minu.proposal', { kind });
    res.json(await duoState(db, me, clock()));
  });

  app.post('/duo/pet/proposals/:kind/accept', auth, linked, consented, async (req, res) => {
    const me = req.user;
    const kind = req.params.kind;
    if (!KINDS.has(kind)) return fail(res, 400, 'Propuesta inválida.');
    const result = await acceptPetChange(db, req.couple._id, me, kind);
    if (result.error) return fail(res, 409, result.error);
    await tell(me, 'minu.accepted', { kind });
    res.json(await duoState(db, me, clock()));
  });

  // Rechazar la propuesta de mi pareja o retirar la mía
  app.delete('/duo/pet/proposals/:kind', auth, linked, consented, async (req, res) => {
    const me = req.user;
    const kind = req.params.kind;
    if (!KINDS.has(kind)) return fail(res, 400, 'Propuesta inválida.');
    if (await dropPetProposal(db, req.couple._id, kind)) realtime.notify(me.partnerId, { type: 'duo.update' });
    res.json(await duoState(db, me, clock()));
  });

  // Conexión diaria: Toque, Pregunta del día, Gracias por..., Te cuento algo bueno
  registerDailyRoutes(app, { ...ctx, linked, consented });
  registerTrustRoutes(app, { ...ctx, linked, consented });
  registerNoveltyRoutes(app, { ...ctx, linked, consented });
  registerMindRoutes(app, { ...ctx, linked, consented });

  // Borrar mis datos de Minu (no requiere haber aceptado: siempre se puede borrar)
  app.delete('/duo/minu/data', auth, linked, async (req, res) => {
    const me = req.user;
    await eraseMyMinuData(db, me);
    realtime.notify(me.partnerId, { type: 'duo.update' });
    res.json(await duoState(db, me, clock()));
  });
}
