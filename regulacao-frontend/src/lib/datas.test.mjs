// Testes de src/lib/datas.js — formatação de `LocalDate`/`LocalDateTime` do
// backend, que hoje chegam como array `[ano, mes, dia, hora, minuto, ...]` e
// viravam "Invalid Date" nas telas que faziam `new Date(array)`.
//
// Não usa nenhum framework de teste (o projeto não tem um configurado) —
// roda com `node --test src/lib/datas.test.mjs`.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { formatarData, formatarDataHora } from './datas.js';

test('formatarDataHora — array completo (7 elementos, com nanos)', () => {
  assert.equal(formatarDataHora([2026, 10, 5, 14, 30, 12, 123456000]), '05/10/2026 14:30');
});

test('formatarDataHora — array sem nanos (6) e sem segundo (5)', () => {
  assert.equal(formatarDataHora([2026, 10, 5, 14, 30, 12]), '05/10/2026 14:30');
  assert.equal(formatarDataHora([2026, 10, 5, 14, 30]), '05/10/2026 14:30');
});

test('formatarDataHora — hora e minuto de 1 dígito ganham zero à esquerda', () => {
  assert.equal(formatarDataHora([2026, 1, 5, 8, 5]), '05/01/2026 08:05');
});

test('formatarDataHora — não desloca o dia nas bordas (00:05 e 23:59)', () => {
  assert.equal(formatarDataHora([2026, 10, 5, 0, 5]), '05/10/2026 00:05');
  assert.equal(formatarDataHora([2026, 10, 5, 23, 59, 59, 999000000]), '05/10/2026 23:59');
  assert.equal(formatarDataHora('2026-10-05T23:59:59'), '05/10/2026 23:59');
});

test('formatarDataHora — string ISO com T, com espaço e com fração de segundo', () => {
  assert.equal(formatarDataHora('2026-10-05T14:30:12'), '05/10/2026 14:30');
  assert.equal(formatarDataHora('2026-10-05 14:30'), '05/10/2026 14:30');
  assert.equal(formatarDataHora('2026-10-05T14:30:12.123456'), '05/10/2026 14:30');
});

test('formatarDataHora — data sem hora sai com 00:00', () => {
  assert.equal(formatarDataHora([2026, 10, 5]), '05/10/2026 00:00');
  assert.equal(formatarDataHora('2026-10-05'), '05/10/2026 00:00');
});

test('formatarData — array [ano, mes, dia] de LocalDate', () => {
  assert.equal(formatarData([1980, 5, 12]), '12/05/1980');
});

test('formatarData — não desloca o dia (primeiro e último dia do mês/ano)', () => {
  assert.equal(formatarData([1980, 1, 1]), '01/01/1980');
  assert.equal(formatarData([1980, 12, 31]), '31/12/1980');
  assert.equal(formatarData('1980-01-01'), '01/01/1980');
  assert.equal(formatarData('1980-12-31'), '31/12/1980');
});

test('formatarData — string ISO, com e sem hora, e com 1 dígito', () => {
  assert.equal(formatarData('1980-05-12'), '12/05/1980');
  assert.equal(formatarData('1980-05-12T00:00:00'), '12/05/1980');
  assert.equal(formatarData('1980-5-2'), '02/05/1980');
});

test('formatarData — ignora a hora de um LocalDateTime', () => {
  assert.equal(formatarData([2026, 10, 5, 23, 59, 59]), '05/10/2026');
});

test('data ausente cai no traço', () => {
  for (const v of [null, undefined, '']) {
    assert.equal(formatarData(v), '—');
    assert.equal(formatarDataHora(v), '—');
  }
});

test('valor não reconhecido aparece cru, nunca "Invalid Date"', () => {
  assert.equal(formatarData('12/05/1980'), '12/05/1980');
  assert.equal(formatarDataHora('ontem'), 'ontem');
  assert.equal(formatarData([1980, 'x', 12]), '1980,x,12');
  assert.equal(formatarData([]), '—');
});
