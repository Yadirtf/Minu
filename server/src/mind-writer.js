/**
 * Quien redacta la voz propia de Minu: cualquier API compatible con OpenAI (/chat/completions). Por
 * defecto Groq con gpt-oss-120b (plan gratis generoso y sin entrenar con los datos). Se cambia de
 * proveedor solo con variables de entorno, sin tocar código:
 *   MIND_API_KEY   llave del proveedor (sin ella, Minu habla con su banco de frases)
 *   MIND_BASE_URL  por defecto https://api.groq.com/openai/v1
 *   MIND_MODEL     por defecto openai/gpt-oss-120b
 */
export const MIND_DEFAULTS = { baseUrl: 'https://api.groq.com/openai/v1', model: 'openai/gpt-oss-120b' };

const SYSTEM = 'Respondes solo con el mensaje pedido, en español latino, sin comillas, sin títulos y sin explicar nada.';

/** Cuánto esperar como máximo dentro de una misma petición cuando el proveedor pide una pausa corta. */
const RETRY_INLINE_MS = 8_000;
/** Pausa por defecto si el proveedor dice "muy rápido" sin decir cuánto esperar. */
const BUSY_DEFAULT_MS = 20_000;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** Lee retry-after (segundos o fecha HTTP) en milisegundos; null si no viene. */
function retryAfterMs(res) {
  const h = res.headers?.get?.('retry-after');
  if (!h) return null;
  const secs = Number(h);
  if (Number.isFinite(secs)) return Math.max(0, secs * 1000);
  const at = Date.parse(h);
  return Number.isFinite(at) ? Math.max(0, at - Date.now()) : null;
}

export function createMindWriter({ apiKey, baseUrl = MIND_DEFAULTS.baseUrl, model = MIND_DEFAULTS.model, fetchImpl = fetch, timeoutMs = 20_000, now = Date.now, wait = sleep } = {}) {
  if (!apiKey) return null;
  const url = `${baseUrl.replace(/\/+$/, '')}/chat/completions`;
  // Los modelos que razonan (gpt-oss) gastan tokens pensando: se les pide poco esfuerzo y un margen justo
  // (los planes gratis cuentan max_tokens en el límite por minuto, así que no conviene inflarlo).
  const reasoning = /gpt-oss/.test(model);
  // Si el proveedor pidió una pausa, nadie le vuelve a escribir hasta que pase (vale para todas las parejas)
  let busyUntil = 0;

  async function once(prompt) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
      const res = await fetchImpl(url, {
        method: 'POST',
        headers: { 'content-type': 'application/json', authorization: `Bearer ${apiKey}` },
        body: JSON.stringify({
          model,
          messages: [{ role: 'system', content: SYSTEM }, { role: 'user', content: prompt }],
          temperature: 0.9,
          max_tokens: reasoning ? 450 : 160,
          ...(reasoning && { reasoning_effort: 'low' }),
        }),
        signal: controller.signal,
      });
      if (res.status === 429) return { text: null, error: 'busy', retryMs: retryAfterMs(res) };
      if (!res.ok) {
        console.warn(`[minu] el redactor respondió ${res.status}`);
        return { text: null, error: 'error' };
      }
      const json = await res.json();
      const text = json?.choices?.[0]?.message?.content;
      return typeof text === 'string' && text.trim() ? { text, error: null } : { text: null, error: 'empty' };
    } catch (e) {
      console.warn(`[minu] el redactor falló: ${e?.name ?? 'error'}`);
      return { text: null, error: 'error' };
    } finally {
      clearTimeout(timer);
    }
  }

  /**
   * Redacta a partir del encargo y dice qué pasó: { text, error } con error null | 'busy' (el proveedor
   * pidió una pausa) | 'empty' | 'error'. Una pausa corta se espera y se reintenta una vez. Nunca lanza.
   */
  async function draft(prompt) {
    if (now() < busyUntil) return { text: null, error: 'busy' };
    let r = await once(prompt);
    if (r.error === 'busy' && r.retryMs != null && r.retryMs <= RETRY_INLINE_MS) {
      await wait(r.retryMs + 250);
      r = await once(prompt);
    }
    if (r.error === 'busy') {
      busyUntil = now() + (r.retryMs ?? BUSY_DEFAULT_MS);
      console.warn('[minu] el redactor pidió una pausa');
    }
    return { text: r.text, error: r.error };
  }

  return {
    model,
    draft,
    /** Solo el texto, o null si el proveedor falló. */
    async write(prompt) {
      return (await draft(prompt)).text;
    },
  };
}

/** Lee la configuración del entorno (Render). */
export function mindWriterFromEnv(env = process.env) {
  return createMindWriter({
    apiKey: env.MIND_API_KEY,
    baseUrl: env.MIND_BASE_URL || MIND_DEFAULTS.baseUrl,
    model: env.MIND_MODEL || MIND_DEFAULTS.model,
  });
}
