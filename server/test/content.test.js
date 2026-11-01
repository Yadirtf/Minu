import { test } from 'node:test';
import assert from 'node:assert/strict';
import { CHEERS, pickQuestion, QUESTIONS } from '../src/content.js';
import { cleanText } from '../src/daily.js';

test('banco: ids únicos, textos sin repetir y en los tres niveles', () => {
  assert.equal(new Set(QUESTIONS.map((q) => q.id)).size, QUESTIONS.length);
  assert.equal(new Set(QUESTIONS.map((q) => q.text)).size, QUESTIONS.length);
  for (const level of ['light', 'personal', 'deep']) assert.ok(QUESTIONS.some((q) => q.level === level), level);
  assert.ok(QUESTIONS.length >= 150, `${QUESTIONS.length} preguntas`);
  assert.ok(QUESTIONS.every((q) => q.text.length <= 140 && q.text.trim() === q.text));
  assert.ok(CHEERS.length >= 5);
});

test('motor de variedad: no repite en un año, es estable por día y guarda lo profundo para después', () => {
  // Mismo seed, misma pregunta (los dos ven la misma)
  assert.deepEqual(pickQuestion({ seed: 'a_b:2026-11-01' }), pickQuestion({ seed: 'a_b:2026-11-01' }));
  // Una cría nunca recibe preguntas profundas
  const used = new Set();
  for (let i = 0; i < 130; i++) {
    const q = pickQuestion({ stage: 'baby', usedIds: used, seed: `x:${i}` });
    assert.notEqual(q.level, 'deep');
    assert.ok(!used.has(q.id), `repitió ${q.id}`);
    used.add(q.id);
  }
  // Ya joven, sí aparecen
  const levels = new Set(Array.from({ length: 200 }, (_, i) => pickQuestion({ stage: 'young', seed: `y:${i}` }).level));
  assert.ok(levels.has('deep'));
  // Si vieron todas, igual hay pregunta
  const all = new Set(QUESTIONS.map((q) => q.id));
  assert.ok(pickQuestion({ stage: 'wise', usedIds: all, seed: 'z' }).text);
});

test('cleanText recorta espacios, limita largo y conserva un salto de línea', () => {
  assert.equal(cleanText('  hola   mundo  ', 20), 'hola mundo');
  assert.equal(cleanText('a\n\n\n\nb', 20), 'a\n\nb');
  assert.equal(cleanText('   ', 20), null);
  assert.equal(cleanText('x'.repeat(21), 20), null);
  assert.equal(cleanText('💛'.repeat(20), 20), '💛'.repeat(20));
});
