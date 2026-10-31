import { test } from 'node:test';
import assert from 'node:assert/strict';
import { addDays, completeRescue, emptyStreak, evaluateStreak, localDay } from '../src/duo.js';

const days = (...list) => (d) => list.includes(d);
const always = () => 3;

test('racha: cuenta los días en que dedicaron los dos, hoy apenas se completa', () => {
  let s = evaluateStreak(emptyStreak(), '2026-03-01', days('2026-03-01'), always);
  assert.equal(s.current, 1);
  s = evaluateStreak(s, '2026-03-02', days(), always);
  assert.equal(s.current, 1); // hoy sigue en juego
  s = evaluateStreak(s, '2026-03-02', days('2026-03-02'), always);
  assert.equal(s.current, 2);
  assert.equal(s.best, 2);
});

test('racha: un día olvidado abre un rescate al día siguiente y el reto salva los dos días', () => {
  const base = { ...emptyStreak(), current: 10, best: 10, lastDay: '2026-03-10' };
  let s = evaluateStreak(base, '2026-03-12', days(), always);
  assert.deepEqual(s.rescue, { missedDay: '2026-03-11', day: '2026-03-12', type: null, chosenByName: null, listenedSec: 0 });
  assert.equal(s.current, 10);
  s = completeRescue({ ...s, rescue: { ...s.rescue, type: 'listen' } });
  assert.equal(s.current, 12);
  assert.equal(s.lastDay, '2026-03-12');
  assert.deepEqual(s.saved, { day: '2026-03-12', type: 'listen' });
  assert.equal(evaluateStreak(s, '2026-03-13', days('2026-03-13'), always).current, 13);
});

test('racha: el reto sin cumplir, dos días seguidos o sin rescates rompen el hilo', () => {
  const base = { ...emptyStreak(), current: 10, best: 12, lastDay: '2026-03-10' };
  const atRisk = evaluateStreak(base, '2026-03-12', days(), always);
  const timeout = evaluateStreak(atRisk, '2026-03-13', days('2026-03-13'), always);
  assert.deepEqual(timeout.broken, { on: '2026-03-12', lost: 10, reason: 'timeout' });
  assert.equal(timeout.current, 1); // el día 13 ya empezó otra racha
  assert.equal(timeout.best, 12);

  const missed = evaluateStreak(base, '2026-03-13', days(), always);
  assert.equal(missed.current, 0);
  assert.equal(missed.broken.reason, 'missed');

  const empty = evaluateStreak(base, '2026-03-12', days(), () => 0);
  assert.equal(empty.rescue, null);
  assert.deepEqual(empty.broken, { on: '2026-03-12', lost: 10, reason: 'no_rescues' });
});

test('racha: sin racha no hay nada que rescatar', () => {
  const s = evaluateStreak({ ...emptyStreak(), lastDay: '2026-03-01' }, '2026-03-05', days(), always);
  assert.equal(s.rescue, null);
  assert.equal(s.broken, null);
  assert.equal(s.lastDay, '2026-03-04');
});

test('fechas: día local de la pareja y meses que cambian', () => {
  assert.equal(localDay('America/Bogota', new Date('2026-03-02T03:00:00Z')), '2026-03-01');
  assert.equal(localDay('Europe/Madrid', new Date('2026-03-02T03:00:00Z')), '2026-03-02');
  assert.equal(addDays('2026-02-28', 1), '2026-03-01');
  assert.equal(addDays('2024-12-31', 1), '2025-01-01');
});
