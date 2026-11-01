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

export function createMindWriter({ apiKey, baseUrl = MIND_DEFAULTS.baseUrl, model = MIND_DEFAULTS.model, fetchImpl = fetch, timeoutMs = 20_000 } = {}) {
  if (!apiKey) return null;
  const url = `${baseUrl.replace(/\/+$/, '')}/chat/completions`;
  // Los modelos que razonan (gpt-oss) gastan tokens pensando: se les pide poco esfuerzo y más margen
  const reasoning = /gpt-oss/.test(model);
  return {
    model,
    /** Redacta a partir del encargo. Devuelve el texto o null si el proveedor falló (nunca lanza). */
    async write(prompt) {
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
            max_tokens: reasoning ? 700 : 160,
            ...(reasoning && { reasoning_effort: 'low' }),
          }),
          signal: controller.signal,
        });
        if (!res.ok) {
          console.warn(`[minu] el redactor respondió ${res.status}`);
          return null;
        }
        const json = await res.json();
        const text = json?.choices?.[0]?.message?.content;
        return typeof text === 'string' && text.trim() ? text : null;
      } catch (e) {
        console.warn(`[minu] el redactor falló: ${e?.name ?? 'error'}`);
        return null;
      } finally {
        clearTimeout(timer);
      }
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
