/** Números al azar reproducibles a partir de un texto (la misma semilla da siempre lo mismo). */
export function seeded(text) {
  let h = 2166136261;
  for (const ch of text) h = Math.imul(h ^ ch.codePointAt(0), 16777619) >>> 0;
  return () => {
    h = (Math.imul(h, 1103515245) + 12345) >>> 0;
    return h / 2 ** 32;
  };
}
