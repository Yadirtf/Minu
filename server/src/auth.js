import jwt from 'jsonwebtoken';
import { toId, users } from './db.js';

export function signToken(user, config) {
  return jwt.sign({ sub: user._id.toString() }, config.jwtSecret, { expiresIn: '365d' });
}

/** Devuelve el usuario del token o null. */
export async function userFromToken(db, config, token) {
  if (!token) return null;
  try {
    const { sub } = jwt.verify(token, config.jwtSecret);
    const id = toId(sub);
    return id ? await users(db).findOne({ _id: id }) : null;
  } catch {
    return null;
  }
}

/** [allowQuery]: acepta también ?token= (el reproductor de audio del navegador no manda cabeceras). */
export function requireAuth(db, config, { allowQuery = false } = {}) {
  return async (req, res, next) => {
    const header = req.get('authorization') || '';
    const token = header.startsWith('Bearer ') ? header.slice(7) : allowQuery ? String(req.query.token || '') || null : null;
    const user = await userFromToken(db, config, token);
    if (!user) return res.status(401).json({ error: 'Sesión vencida. Inicia sesión otra vez.' });
    req.user = user;
    next();
  };
}
