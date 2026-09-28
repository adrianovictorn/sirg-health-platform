// Testes de caracterização de src/lib/cotas.js — capturam o comportamento que
// JÁ FUNCIONA hoje (string ISO, ausência de dado, período mensal tolerante) e
// que NÃO PODE regredir com o ajuste que troca o parsing de `dataEspecifica`
// para também aceitar o formato array `[ano, mes, dia]` vindo do backend.
//
// Não usa nenhum framework de teste (o projeto não tem um configurado) —
// roda com `node --test src/lib/cotas.characterization.test.mjs`.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { formatarPeriodoCota, chaveOrdenacaoCota } from './cotas.js';

test('formatarPeriodoCota — DATA com string ISO simples', () => {
  assert.equal(formatarPeriodoCota({ tipoPeriodo: 'DATA', dataEspecifica: '2026-09-26' }), '26/09/2026');
});

test('formatarPeriodoCota — DATA com string ISO + sufixo de hora', () => {
  assert.equal(
    formatarPeriodoCota({ tipoPeriodo: 'DATA', dataEspecifica: '2026-09-26T00:00:00' }),
    '26/09/2026'
  );
});

test('formatarPeriodoCota — DATA com dia/mês de 1 dígito', () => {
  assert.equal(formatarPeriodoCota({ tipoPeriodo: 'DATA', dataEspecifica: '2026-9-6' }), '06/09/2026');
});

test('formatarPeriodoCota — DATA com data ausente cai no traço', () => {
  assert.equal(formatarPeriodoCota({ tipoPeriodo: 'DATA', dataEspecifica: null }), '—');
  assert.equal(formatarPeriodoCota({ tipoPeriodo: 'DATA', dataEspecifica: undefined }), '—');
});

test('formatarPeriodoCota — MENSAL com período válido', () => {
  assert.equal(formatarPeriodoCota({ tipoPeriodo: 'MENSAL', periodo: '2026-09' }), '09/2026');
});

test('formatarPeriodoCota — MENSAL com mês de 1 dígito (tolerante)', () => {
  assert.equal(formatarPeriodoCota({ tipoPeriodo: 'MENSAL', periodo: '2026-9' }), '09/2026');
});

test('formatarPeriodoCota — MENSAL com período ausente cai no traço', () => {
  assert.equal(formatarPeriodoCota({ tipoPeriodo: 'MENSAL', periodo: null }), '—');
});

test('chaveOrdenacaoCota — DATA com string ISO', () => {
  assert.equal(chaveOrdenacaoCota({ tipoPeriodo: 'DATA', dataEspecifica: '2026-09-26' }), '2026-09-26');
});

test('chaveOrdenacaoCota — MENSAL com período válido vira o 1º dia do mês', () => {
  assert.equal(chaveOrdenacaoCota({ tipoPeriodo: 'MENSAL', periodo: '2026-09' }), '2026-09-01');
});

test('chaveOrdenacaoCota — dado ausente cai no fim da ordenação (9999-99-99)', () => {
  assert.equal(chaveOrdenacaoCota({ tipoPeriodo: 'DATA', dataEspecifica: null }), '9999-99-99');
  assert.equal(chaveOrdenacaoCota({ tipoPeriodo: 'MENSAL', periodo: null }), '9999-99-99');
});

// --- Novo comportamento deste ajuste: dataEspecifica vindo como array ------
// [ano, mes, dia], formato real observado em produção (bug de configuração
// de serialização no backend — ver plano do ajuste). Antes deste fix, os dois
// casos abaixo caíam no fallback bruto ("2026,9,26" / "9999-99-99").

test('formatarPeriodoCota — DATA com dataEspecifica em array [ano, mes, dia]', () => {
  assert.equal(formatarPeriodoCota({ tipoPeriodo: 'DATA', dataEspecifica: [2026, 9, 26] }), '26/09/2026');
});

test('formatarPeriodoCota — DATA com array e dia/mês de 2 dígitos', () => {
  assert.equal(formatarPeriodoCota({ tipoPeriodo: 'DATA', dataEspecifica: [2026, 12, 3] }), '03/12/2026');
});

test('chaveOrdenacaoCota — DATA com dataEspecifica em array [ano, mes, dia]', () => {
  assert.equal(chaveOrdenacaoCota({ tipoPeriodo: 'DATA', dataEspecifica: [2026, 9, 26] }), '2026-09-26');
});

test('formatarPeriodoCota — DATA com array malformado cai no valor bruto, não quebra', () => {
  assert.equal(formatarPeriodoCota({ tipoPeriodo: 'DATA', dataEspecifica: [2026, 'x'] }), '2026,x');
});
