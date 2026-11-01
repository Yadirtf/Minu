import { test } from 'node:test';
import assert from 'node:assert/strict';
import { cleanPetName, cleanProposal, dailyPoints, evaluatePet, monthsBetween, moodFor, stageFor } from '../src/pet.js';
import { termsView, TERMS_VERSION } from '../src/minu.js';

const ev = (day, kind, n = 1) => Array.from({ length: n }, () => ({ day, kind }));

test('recién nacido: arranca contento, no extrañando', () => {
  const pet = evaluatePet({ events: ev('2026-11-01', 'birth'), today: '2026-11-01', joinedOn: '2026-11-01' });
  assert.equal(pet.energy, 50);
  assert.equal(pet.mood, 'happy');
  assert.equal(pet.stage, 'baby');
});

test('topes: por fuente y por día', () => {
  // 10 toques = 50 puntos, pero el tope de toques es 20
  assert.equal(dailyPoints(ev('2026-11-01', 'touch', 10)).get('2026-11-01'), 20);
  // Todo junto no pasa de 50 al día
  const lots = [...ev('2026-11-01', 'dedication', 2), ...ev('2026-11-01', 'adventure'), ...ev('2026-11-01', 'touch', 4)];
  assert.equal(dailyPoints(lots).get('2026-11-01'), 50);
  // Nacer no gasta el tope del día
  assert.equal(dailyPoints([...ev('2026-11-01', 'birth'), ...ev('2026-11-01', 'dedication')]).get('2026-11-01'), 60);
  // Fuentes desconocidas no cuentan
  assert.equal(dailyPoints(ev('2026-11-01', 'hack', 5)).size, 0);
});

test('los dos dedicando a diario lo dejan radiante; si paran, baja despacio sin llegar a cero de golpe', () => {
  const days = Array.from({ length: 20 }, (_, i) => `2026-10-${String(i + 1).padStart(2, '0')}`);
  const events = days.flatMap((d) => ev(d, 'dedication', 2));
  assert.equal(evaluatePet({ events, today: '2026-10-20', joinedOn: '2026-10-01' }).mood, 'radiant');
  const after3 = evaluatePet({ events, today: '2026-10-23', joinedOn: '2026-10-01' });
  assert.ok(after3.energy >= 50 && after3.energy < 100, `3 días después: ${after3.energy}`);
  const after10 = evaluatePet({ events, today: '2026-10-30', joinedOn: '2026-10-01' });
  assert.equal(after10.mood, 'sleepy');
  const after20 = evaluatePet({ events, today: '2026-11-09', joinedOn: '2026-10-01' });
  assert.equal(after20.mood, 'missing');
  assert.ok(after20.energy >= 0);
});

test('solo uno dedicando: contento, no radiante', () => {
  const days = Array.from({ length: 20 }, (_, i) => `2026-10-${String(i + 1).padStart(2, '0')}`);
  const pet = evaluatePet({ events: days.flatMap((d) => ev(d, 'dedication')), today: '2026-10-20', joinedOn: '2026-10-01' });
  assert.equal(pet.mood, 'happy');
});

test('gestos fuera de la ventana o del futuro no cuentan', () => {
  const pet = evaluatePet({ events: [...ev('2026-01-01', 'adventure'), ...ev('2026-12-01', 'adventure')], today: '2026-11-01', joinedOn: '2026-01-01' });
  assert.equal(pet.energy, 0);
});

test('meses cumplidos y etapas (nunca retroceden)', () => {
  assert.equal(monthsBetween('2026-01-31', '2026-02-28'), 1); // el 31 cumple el último día de febrero
  assert.equal(monthsBetween('2026-01-31', '2026-02-27'), 0);
  assert.equal(monthsBetween('2026-01-15', '2027-01-14'), 11);
  assert.equal(monthsBetween('2026-01-15', '2027-01-15'), 12);
  assert.equal(monthsBetween('2026-05-01', '2026-04-01'), 0);
  assert.equal(stageFor(0), 'baby');
  assert.equal(stageFor(3), 'young');
  assert.equal(stageFor(12), 'adult');
  assert.equal(stageFor(40), 'wise');
  const pet = evaluatePet({ events: [], today: '2026-11-01', joinedOn: '2026-09-01' });
  assert.equal(pet.months, 2);
  assert.equal(pet.nextStageInMonths, 1);
  assert.equal(moodFor(100), 'radiant');
  assert.equal(moodFor(0), 'missing');
});

test('nombres: limpios, cortos y sin símbolos raros', () => {
  assert.equal(cleanPetName('  Pelusa   Roja '), 'Pelusa Roja');
  assert.equal(cleanPetName('Ñoño'), 'Ñoño');
  assert.equal(cleanPetName('Miel 🐶'), 'Miel 🐶');
  assert.equal(cleanPetName(''), null);
  assert.equal(cleanPetName('a'.repeat(21)), null);
  assert.equal(cleanPetName('<script>'), null);
  assert.equal(cleanProposal('species', 'dog'), 'dog');
  assert.equal(cleanProposal('species', 'dragon'), null);
  assert.equal(cleanProposal('color', 'red'), null);
});

test('términos: los dos deben aceptar la versión vigente', () => {
  const couple = { terms: { a: { version: TERMS_VERSION }, b: { version: TERMS_VERSION - 1 } } };
  assert.deepEqual(termsView(couple, 'a', 'b'), { version: TERMS_VERSION, mine: true, partner: false, accepted: false, update: false });
  const fromB = termsView(couple, 'b', 'a');
  assert.equal(fromB.mine, false);
  assert.equal(fromB.update, true); // ya había aceptado antes: ve solo lo que cambió
  assert.equal(termsView({ terms: { a: { version: TERMS_VERSION }, b: { version: TERMS_VERSION } } }, 'a', 'b').accepted, true);
  assert.equal(termsView(null, 'a', 'b').accepted, false);
});
