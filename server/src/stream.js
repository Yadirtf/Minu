import vm from 'node:vm';
import { Readable } from 'node:stream';
import { Innertube, Platform } from 'youtubei.js';

/**
 * Audio de YouTube sin el reproductor de YouTube (y por lo tanto sin anuncios), igual que
 * NewPipe en la app: se pide la info del video, se elige el mejor audio y el servidor lo
 * reenvía al navegador. Las URL de googlevideo quedan atadas a la IP de quien las pidió,
 * por eso el audio pasa por aquí en vez de mandarle la URL al navegador.
 *
 * EXPERIMENTAL: sirve para comprobar si YouTube deja hacer esto desde Render.
 */

// Clientes de InnerTube que se prueban en orden. Los primeros suelen dar URL sin cifrar.
export const CLIENTS = ['ANDROID_VR', 'IOS', 'TV', 'WEB_EMBEDDED', 'WEB'];

// Canciones fijas para el diagnóstico público
export const TEST_VIDEOS = ['dQw4w9WgXcQ', 'JGwWNGJdvx8', 'kJQP7kiw5Fk'];

const VIDEO_ID = /^[A-Za-z0-9_-]{11}$/;

// youtubei.js necesita evaluar el código del reproductor de YouTube para descifrar las URL.
// Se corre aislado en un contexto de vm, sin acceso a require, process ni la red, y con límite de tiempo.
Platform.shim.eval = (data) =>
  vm.runInNewContext(`(function () {\n${data.output}\n})()`, { URL, URLSearchParams }, { timeout: 2_000 });

/** Mejor audio solo: m4a primero (lo reproduce cualquier navegador, incluido Safari), luego por bitrate. */
export function pickAudioFormat(formats = []) {
  const audio = formats.filter((f) => f.has_audio && !f.has_video && (f.url || f.signature_cipher || f.cipher));
  audio.sort((a, b) => {
    const mp4 = Number(String(b.mime_type).startsWith('audio/mp4')) - Number(String(a.mime_type).startsWith('audio/mp4'));
    return mp4 || (b.bitrate ?? 0) - (a.bitrate ?? 0);
  });
  return audio[0] ?? null;
}

/** Segundos hasta que vence una URL de googlevideo (parámetro expire), con margen. */
export function secondsLeft(url, now = Date.now()) {
  const expire = Number(new URL(url).searchParams.get('expire'));
  return Number.isFinite(expire) && expire > 0 ? expire - now / 1000 - 300 : 0;
}

export function createStreamer({ clients = CLIENTS, fetchFn = fetch } = {}) {
  let yt = null;
  const cache = new Map(); // videoId -> { url, mime, length, client }

  const innertube = async () => {
    if (!yt) {
      yt = Innertube.create({ retrieve_player: true, generate_session_locally: true }).catch((e) => {
        yt = null;
        throw e;
      });
    }
    return yt;
  };

  /** Prueba un cliente. Devuelve el audio o lanza con el motivo. */
  async function resolveWith(videoId, client) {
    const tube = await innertube();
    const info = await tube.getBasicInfo(videoId, { client });
    const status = info.playability_status?.status;
    if (status !== 'OK') throw new Error(`${status ?? 'SIN_ESTADO'}: ${info.playability_status?.reason ?? ''}`.trim());
    const format = pickAudioFormat(info.streaming_data?.adaptive_formats);
    if (!format) throw new Error('Sin formatos de audio');
    const url = await format.decipher(tube.session.player);
    return { url, mime: format.mime_type, length: Number(format.content_length) || null, client };
  }

  /** Audio de un video, probando los clientes en orden. Usa la caché mientras la URL no venza. */
  async function resolve(videoId, { fresh = false } = {}) {
    if (!VIDEO_ID.test(videoId)) throw new Error('videoId inválido');
    const cached = cache.get(videoId);
    if (!fresh && cached && secondsLeft(cached.url) > 0) return cached;
    const errors = [];
    for (const client of clients) {
      try {
        const audio = await resolveWith(videoId, client);
        if (cache.size > 500) cache.delete(cache.keys().next().value);
        cache.set(videoId, audio);
        return audio;
      } catch (e) {
        errors.push(`${client}: ${e.message}`);
      }
    }
    throw new Error(errors.join(' | '));
  }

  /** Pide a googlevideo el rango que pidió el navegador. */
  const upstream = (audio, range, signal) =>
    fetchFn(audio.url, { headers: range ? { range } : {}, signal, redirect: 'follow' });

  /** Reenvía el audio al navegador, respetando Range para poder adelantar la canción. */
  async function proxy(req, res, videoId) {
    const range = req.get('range');
    const abort = new AbortController();
    res.on('close', () => abort.abort());

    let audio = await resolve(videoId);
    let up = await upstream(audio, range, abort.signal);
    if (up.status === 403 || up.status === 410) {
      // URL vencida o rechazada: se pide otra una vez
      audio = await resolve(videoId, { fresh: true });
      up = await upstream(audio, range, abort.signal);
    }
    if (!up.ok) {
      res.status(502).json({ error: `YouTube respondió ${up.status}.` });
      return;
    }
    res.status(up.status);
    for (const h of ['content-type', 'content-length', 'content-range', 'accept-ranges']) {
      const v = up.headers.get(h);
      if (v) res.set(h, v);
    }
    if (!up.headers.get('accept-ranges')) res.set('accept-ranges', 'bytes');
    res.set('cache-control', 'private, max-age=3600');
    Readable.fromWeb(up.body).on('error', () => res.destroy()).pipe(res);
  }

  /**
   * Diagnóstico: para cada canción de prueba y cada cliente, si se obtuvo la URL y si
   * llegaron bytes de audio de verdad (los primeros 128 KB).
   */
  async function diagnose(videoIds = TEST_VIDEOS) {
    const results = [];
    for (const videoId of videoIds) {
      for (const client of clients) {
        const started = Date.now();
        const row = { videoId, client, ok: false };
        try {
          const audio = await resolveWith(videoId, client);
          row.mime = audio.mime;
          const up = await upstream(audio, 'bytes=0-131071', AbortSignal.timeout(15_000));
          row.httpStatus = up.status;
          row.bytes = up.ok ? (await up.arrayBuffer()).byteLength : 0;
          row.ok = up.ok && row.bytes > 0;
          if (row.ok && !cache.has(videoId)) cache.set(videoId, audio);
        } catch (e) {
          row.error = String(e.message).slice(0, 300);
        }
        row.ms = Date.now() - started;
        results.push(row);
      }
    }
    const working = [...new Set(results.filter((r) => r.ok).map((r) => r.client))];
    return {
      ok: working.length > 0,
      workingClients: working,
      summary: working.length
        ? `Funciona desde este servidor con: ${working.join(', ')}.`
        : 'YouTube no entregó audio a este servidor con ningún cliente.',
      results,
    };
  }

  return { resolve, proxy, diagnose };
}
