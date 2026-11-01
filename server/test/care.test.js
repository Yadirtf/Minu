import { test } from 'node:test';
import assert from 'node:assert/strict';
import { careCheck, HELP_LINES, normalize } from '../src/care.js';

test('care: detecta frases de riesgo (autolesión, violencia, miedo)', () => {
  const risky = {
    selfharm: [
      'A veces pienso en el suicidio',
      'Siento que me quiero morir',
      'No quiero seguir viviendo así',
      'Me dan ganas de hacerme daño',
      'Estaría mejor muerta',
      'Quiero acabar con todo',
      'Pienso en cortarme otra vez',
      'Me voy a matar',
    ],
    violence: [
      'Me dolió cuando me pegaste',
      'Ayer me empujaste contra la pared',
      'Me has golpeado dos veces',
      'Me amenazaste con irte con los niños',
      'Me obligaste a tener relaciones',
      'Te voy a matar si vuelves a hacerlo',
      'Me encerraste en el cuarto',
    ],
    fear: ['Te tengo miedo', 'Tengo miedo de lo que puedas hacer', 'No me siento segura contigo', 'Temo por mi vida'],
  };
  for (const [kind, list] of Object.entries(risky)) {
    for (const text of list) {
      const r = careCheck(text);
      assert.ok(r.risk && r.kinds.includes(kind), `debería detectar ${kind}: "${text}"`);
    }
  }
});

test('care: no salta con expresiones cariñosas o exageradas', () => {
  const safe = [
    'Me muero de risa contigo',
    'Me quiero morir de la vergüenza 🙈',
    'Te voy a matar a besos',
    'Me pegaste la gripa jaja',
    'Me pegaste un susto',
    'Me pegas tu canción favorita en la cabeza',
    'Me voy a cortar el pelo mañana',
    'Mañana voy a cortarme el pelo',
    'Me voy a matar estudiando para el parcial',
    'Siento tristeza cuando llegas tarde sin avisar, me ayudaría que me escribas',
    'Me sentí sola cuando no contestaste',
    'Quiero acabar con todo el trabajo hoy para verte',
    'Me lastimó lo que dijiste en la cena',
    'Te extraño mucho',
    '',
  ];
  for (const text of safe) assert.equal(careCheck(text).risk, false, `no debería saltar: "${text}"`);
});

test('care: normaliza tildes y une varios textos; las líneas de ayuda empiezan por Colombia', () => {
  assert.equal(normalize('  ¡Me SIENTO   así!  '), 'me siento asi');
  assert.ok(careCheck('Siento miedo', 'cuando', 'me pegaste').risk);
  assert.equal(HELP_LINES.country, 'Colombia');
  assert.ok(HELP_LINES.lines.some((l) => l.number === '123'));
});
