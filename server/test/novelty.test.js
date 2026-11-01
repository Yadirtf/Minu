import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ADVENTURES, dailySuggestion, LOVE_QUIZ, loveWeights, pickAdventures } from '../src/novelty-content.js';
import { releaseDays, weekOf } from '../src/novelty.js';
import { daysBetween } from '../src/pet.js';

test('aventuras: ids únicos y una de cada tipo por semana, sin repetir lo usado', () => {
  assert.equal(new Set(ADVENTURES.map((p) => p.id)).size, ADVENTURES.length);
  const picks = pickAdventures({ seed: 'pareja:2026-11-02' });
  assert.deepEqual(picks.map((p) => p.kind), ['home', 'out', 'create']);
  assert.deepEqual(pickAdventures({ seed: 'pareja:2026-11-02' }), picks); // determinista
  const used = new Set(ADVENTURES.filter((p) => p.kind === 'home').slice(0, -1).map((p) => p.id));
  const [home] = pickAdventures({ seed: 'x', usedIds: used });
  assert.ok(!used.has(home.id)); // el único plan de casa sin usar
});

test('aventuras: los lenguajes del amor de la pareja pesan, pero nunca dejan a nadie en cero', () => {
  const touchy = { touch: 1, time: 0, words: 0, gifts: 0, service: 0 };
  let withTouch = 0;
  let plain = 0;
  for (let i = 0; i < 300; i++) {
    withTouch += pickAdventures({ seed: `s${i}`, weights: touchy }).filter((p) => p.langs.includes('touch')).length;
    plain += pickAdventures({ seed: `s${i}` }).filter((p) => p.langs.includes('touch')).length;
  }
  assert.ok(withTouch > plain * 1.2, `${withTouch} vs ${plain}`);
});

test('lenguajes del amor: cada lenguaje aparece 4 veces y los pesos van de 0 a 1', () => {
  const count = {};
  for (const q of LOVE_QUIZ) for (const l of [q.a.lang, q.b.lang]) count[l] = (count[l] ?? 0) + 1;
  assert.deepEqual(Object.values(count), [4, 4, 4, 4, 4]);
  const all = LOVE_QUIZ.map((q) => (q.a.lang === 'touch' || q.b.lang === 'touch' ? 'touch' : q.a.lang));
  const w = loveWeights(all);
  assert.equal(w.touch, 1);
  assert.ok(Object.values(w).every((v) => v >= 0 && v <= 1));
  assert.equal(dailySuggestion(null, 'x'), null);
  assert.match(dailySuggestion(w, 'dia-1'), /\w/);
});

test('cumplidos: como máximo uno por semana, el primero en los próximos 7 días; semanas empiezan en lunes', () => {
  for (let i = 0; i < 50; i++) {
    const days = releaseDays('2026-11-10', `s${i}`);
    const first = daysBetween('2026-11-10', days[0]);
    assert.ok(first >= 1 && first <= 7);
    for (let j = 1; j < days.length; j++) {
      const gap = daysBetween(days[j - 1], days[j]);
      assert.ok(gap >= 7 && gap <= 13, `gap ${gap}`);
    }
  }
  assert.equal(weekOf('2026-11-02'), '2026-11-02'); // lunes
  assert.equal(weekOf('2026-11-08'), '2026-11-02'); // domingo
  assert.equal(weekOf('2026-11-10'), '2026-11-09');
});
