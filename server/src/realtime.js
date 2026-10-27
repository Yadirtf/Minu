import { WebSocketServer } from 'ws';
import { userFromToken } from './auth.js';
import { users } from './db.js';

/** Mensajes que un celular puede reenviar a su pareja. */
const RELAYED = new Set([
  'listen.invite', // "¿escuchamos juntos?"
  'listen.join', // la pareja se unió
  'listen.leave', // alguien salió de la sesión
  'listen.state', // canción, posición y si está sonando (lo manda quien controla)
  'listen.takeControl', // la otra persona pide el control
  'reaction', // ❤️ 😍 🔥 en vivo
]);

/**
 * Tiempo real por WebSocket en /ws. Autenticación con "Authorization: Bearer <token>"
 * o ?token=. Devuelve notify/isOnline para que la API avise al otro celular.
 */
export function attachRealtime(server, db, config) {
  const wss = new WebSocketServer({ noServer: true, maxPayload: 64 * 1024 });
  const sockets = new Map(); // userId -> Set<WebSocket>

  const isOnline = (userId) => (sockets.get(String(userId))?.size ?? 0) > 0;

  const notify = (userId, message) => {
    const set = sockets.get(String(userId));
    if (!set?.size) return false;
    const data = JSON.stringify({ ...message, serverTime: Date.now() });
    for (const ws of set) if (ws.readyState === ws.OPEN) ws.send(data);
    return true;
  };

  const partnerOf = async (userId) => (await users(db).findOne({ _id: userId }, { projection: { partnerId: 1 } }))?.partnerId;

  server.on('upgrade', async (req, socket, head) => {
    const url = new URL(req.url, 'http://localhost');
    if (url.pathname !== '/ws') return socket.destroy();
    const header = req.headers.authorization || '';
    const token = header.startsWith('Bearer ') ? header.slice(7) : url.searchParams.get('token');
    const user = await userFromToken(db, config, token);
    if (!user) {
      socket.write('HTTP/1.1 401 Unauthorized\r\n\r\n');
      return socket.destroy();
    }
    wss.handleUpgrade(req, socket, head, (ws) => wss.emit('connection', ws, user));
  });

  wss.on('connection', async (ws, user) => {
    const id = user._id.toString();
    const first = !isOnline(id);
    if (!sockets.has(id)) sockets.set(id, new Set());
    sockets.get(id).add(ws);
    ws.alive = true;
    ws.on('pong', () => (ws.alive = true));

    ws.on('message', async (raw) => {
      let msg;
      try {
        msg = JSON.parse(raw.toString());
      } catch {
        return;
      }
      if (msg?.type === 'ping') {
        ws.send(JSON.stringify({ type: 'pong', t: msg.t, serverTime: Date.now() }));
        return;
      }
      if (!RELAYED.has(msg?.type)) return;
      const partner = await partnerOf(user._id).catch(() => null);
      if (!partner) return;
      const { type, state, emoji } = msg;
      notify(partner, { type, state, emoji: typeof emoji === 'string' ? emoji.slice(0, 8) : undefined, from: user.displayName });
    });

    ws.on('close', async () => {
      const set = sockets.get(id);
      set?.delete(ws);
      if (set && set.size === 0) {
        sockets.delete(id);
        const partner = await partnerOf(user._id).catch(() => null);
        if (partner) notify(partner, { type: 'presence', online: false });
      }
    });
    // Los listeners ya están puestos (antes de cualquier await, para no perder mensajes)
    const partnerId = await partnerOf(user._id);
    if (first && partnerId) notify(partnerId, { type: 'presence', online: true });
    if (ws.readyState === ws.OPEN) ws.send(JSON.stringify({ type: 'hello', partnerOnline: partnerId ? isOnline(partnerId) : false, serverTime: Date.now() }));
  });

  // Mantiene viva la conexión (Render corta conexiones inactivas) y limpia las muertas
  const heartbeat = setInterval(() => {
    for (const ws of wss.clients) {
      if (!ws.alive) {
        ws.terminate();
        continue;
      }
      ws.alive = false;
      ws.ping();
    }
  }, 25_000);
  server.on('close', () => clearInterval(heartbeat));

  return { notify, isOnline };
}
