import { test } from 'node:test';
import assert from 'node:assert/strict';
import { MIND_KINDS, REHEARSAL } from '../src/mind-content.js';
import { addMonths, buildPrompt, digestFacts, guardLine, pickKind, rehearsal, situationOf, sundayOf, tidyLine, wakeDay } from '../src/mind.js';

test('despertar: un mes desde que llegaron a Minu, con fin de mes y cambio de año', () => {
  assert.equal(wakeDay('2026-10-15'), '2026-11-15');
  assert.equal(addMonths('2026-01-31', 1), '2026-02-28');
  assert.equal(addMonths('2028-01-31', 1), '2028-02-29'); // bisiesto
  assert.equal(addMonths('2026-03-31', 1), '2026-04-30');
  assert.equal(addMonths('2026-12-20', 1), '2027-01-20');
  assert.equal(addMonths('2026-05-31', 12), '2027-05-31');
  assert.equal(wakeDay(null), null);
});

test('resumen semanal: se renueva el domingo', () => {
  assert.equal(sundayOf('2026-11-01'), '2026-11-01'); // domingo
  assert.equal(sundayOf('2026-11-04'), '2026-11-01');
  assert.equal(sundayOf('2026-11-07'), '2026-11-01');
  assert.equal(sundayOf('2026-11-08'), '2026-11-08');
});

test('lo que Minu sabe: frases naturales, sin decir quién aportó qué ni nombrar el test', () => {
  const couple = {
    origin: { startedOn: '2024-06-10', song: { title: 'Coldplay - Yellow' } },
    loveLangs: { a: { weights: { words: 1, time: 0.5, gifts: 0, service: 0, touch: 0 } }, b: { weights: { touch: 0.75, words: 0, time: 0, gifts: 0, service: 0 } } },
  };
  const memory = [
    { kind: 'song', text: 'Bad Bunny - Ojitos Lindos', by: 'a' },
    { kind: 'thanks', text: 'por la sopa de anoche', by: 'b' },
    { kind: 'news', text: '¡Me dieron el trabajo!', by: 'a' },
    { kind: 'adventure', text: 'Atardecer con audífonos' },
    { kind: 'answer', text: 'Una de rock', data: { question: '¿Qué canción te recuerda a mí?' }, by: 'a' },
  ];
  const facts = digestFacts({ couple, memory, joinedOn: '2026-08-01', today: '2026-11-01' });
  const text = facts.join(' ');
  assert.match(text, /junio de 2024 \(2 años\)/);
  assert.match(text, /Yellow/);
  assert.match(text, /Llevan 3 meses/);
  assert.match(text, /sopa de anoche/);
  assert.match(text, /palabras bonitas.*abrazos/);
  assert.doesNotMatch(text, /lenguaje|afirmaci|contacto f/i);
  assert.ok(facts.length <= 10);
  assert.deepEqual(digestFacts({ couple: {}, memory: [], joinedOn: null, today: '2026-11-01' }), []);
});

test('situación de hoy: aniversarios y aventura pendiente', () => {
  const couple = { origin: { startedOn: '2025-11-01' } };
  assert.ok(situationOf({ couple, today: '2026-11-01' }).some((l) => /1 año/.test(l)));
  assert.ok(situationOf({ couple, today: '2026-05-01' }).some((l) => /6 meses/.test(l)));
  const adventure = { chosen: { title: 'Cocinen algo nuevo' }, doneAt: null };
  assert.ok(situationOf({ couple: null, today: '2026-11-03', adventure }).some((l) => /Cocinen algo nuevo/.test(l)));
  assert.equal(situationOf({ couple: null, today: '2026-11-03' })[0], 'Hoy es martes.');
});

test('tipo del día: estable para el mismo día y todos aparecen', () => {
  assert.equal(pickKind('p:2026-11-01:0', 'happy'), pickKind('p:2026-11-01:0', 'happy'));
  const seen = new Set();
  for (let i = 0; i < 200; i++) seen.add(pickKind(`p:${i}`, 'happy'));
  assert.deepEqual([...seen].sort(), Object.keys(MIND_KINDS).sort());
  let plans = 0;
  for (let i = 0; i < 400; i++) if (pickKind(`p:${i}`, 'missing') === 'plan') plans++;
  assert.ok(plans < 80, `planes cuando los extraña: ${plans}`);
});

test('encargo: personalidad, lo que sabe, ejemplos y encargo; corto para el celular', () => {
  const prompt = buildPrompt({
    pet: { name: 'Pelusa', species: 'cat', mood: 'missing' },
    facts: ['Les gusta cocinar juntos.'],
    situation: ['Hoy es domingo.'],
    kind: 'plan',
  });
  assert.match(prompt, /Eres Pelusa, la mascota/);
  assert.match(prompt, /un gatito/);
  assert.match(prompt, /los extrañas/);
  assert.match(prompt, /- Les gusta cocinar juntos\./);
  assert.match(prompt, /Encargo: Propón un plan/);
  assert.ok(prompt.endsWith('Mensaje:'));
});

test('ensayo: 30 situaciones, todas con encargo dentro del límite del celular', () => {
  const cases = rehearsal({ name: 'Minu', species: 'yarn', mood: 'happy' }, '2026-11-01');
  assert.equal(cases.length, 30);
  assert.equal(new Set(REHEARSAL.map((c) => c.id)).size, 30);
  for (const kind of Object.keys(MIND_KINDS)) assert.ok(cases.filter((c) => c.kind === kind).length >= 6, kind);
  // Menos de ~1.000 tokens (el celular admite unos 4.000): margen de sobra
  for (const c of cases) assert.ok(c.prompt.length < 3000, `${c.id}: ${c.prompt.length}`);
  // Los ejemplos del banco pasan sus propias barandas
  for (const kind of Object.values(MIND_KINDS)) for (const e of kind.examples) assert.ok(guardLine(e).ok, e);
});

test('barandas: limpia el formato y acepta lo que suena a Minu', () => {
  assert.equal(tidyLine('Mensaje: "¡Hoy los extraño un poquito! ¿Se dedican una canción?"'), '¡Hoy los extraño un poquito! ¿Se dedican una canción?');
  assert.equal(tidyLine('**Me acordé** de su aventura.\n\nNota: esto es un ejemplo'), 'Me acordé de su aventura.');
  const ok = guardLine('Me acordé de su atardecer con audífonos y me dio un saltito el corazón. ¿Repetimos hoy?');
  assert.ok(ok.ok);
});

test('barandas: rechaza cómo funciona, Buzón, culpa, temas médicos, inglés, enlaces y riesgo', () => {
  const cases = {
    banned: [
      'Como inteligencia artificial, me encanta verlos juntos hoy.',
      'Soy un asistente y hoy les propongo una caminata para los dos.',
      'Hoy podrían escribirse algo en el buzón, que es muy bonito para ustedes.',
      'Ustedes deberían pasar más tiempo juntos, ya casi no se ven.',
      'Si se siente mal, tómese una pastilla y descansen los dos hoy.',
      'A uno le llega el tiempo de calidad, así que hoy hagan un plan.',
      'Ojalá hoy no tengan otra pelea como la de la semana pasada.',
    ],
    language: ['Today you should listen to your favorite song together and smile.'],
    link: ['Escuchen esta canción hoy en https://youtube.com para los dos.'],
    short: ['¡Hola!'],
    leak: ['Lo que sé de ustedes: les gusta cocinar. Mensaje: hoy cocinen juntos.'],
    care: ['Hoy siento que me quiero morir de tanto que los extraño a los dos.'],
  };
  for (const [reason, texts] of Object.entries(cases)) {
    for (const t of texts) assert.equal(guardLine(t).reason, reason, t);
  }
});
