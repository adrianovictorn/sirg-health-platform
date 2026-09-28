// Testes de caracterização de src/lib/cotas.js — capturam o comportamento
// esperado de agruparCotasPorAgenda/periodoCobertoPorGrupo ANTES de ligar essa
// lógica em admin/cotas/+page.svelte, para que uma regressão na tela (ex.:
// esconder cota que deveria aparecer) seja detectável separando "a lógica de
// agrupamento está certa" de "a tela está ligando a lógica certo".
//
// Não usa nenhum framework de teste (o projeto não tem um configurado) —
// roda com `node --test src/lib/cotas.agrupamento.test.mjs`.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { agruparCotasPorAgenda, periodoCobertoPorGrupo } from './cotas.js';

function cotaManual(id, overrides = {}) {
  return { id, origem: 'MANUAL', agendaId: null, tipoPeriodo: 'MENSAL', periodo: '2026-09', ...overrides };
}

function cotaAgenda(id, agendaId, overrides = {}) {
  return { id, origem: 'AGENDA', agendaId, tipoPeriodo: 'DATA', dataEspecifica: '2026-10-05', ...overrides };
}

test('agruparCotasPorAgenda — lista vazia devolve os dois grupos vazios', () => {
  const { manuais, porAgenda } = agruparCotasPorAgenda([]);
  assert.deepEqual(manuais, []);
  assert.equal(porAgenda.size, 0);
});

test('agruparCotasPorAgenda — só cotas manuais, nenhuma vira grupo', () => {
  const cotas = [cotaManual(1), cotaManual(2)];
  const { manuais, porAgenda } = agruparCotasPorAgenda(cotas);
  assert.deepEqual(manuais, cotas);
  assert.equal(porAgenda.size, 0);
});

test('agruparCotasPorAgenda — cotas da mesma agenda caem no mesmo grupo, na ordem original', () => {
  const c1 = cotaAgenda(1, 500, { dataEspecifica: '2026-10-05' });
  const c2 = cotaAgenda(2, 500, { dataEspecifica: '2026-10-12' });
  const { manuais, porAgenda } = agruparCotasPorAgenda([c1, c2]);
  assert.deepEqual(manuais, []);
  assert.equal(porAgenda.size, 1);
  assert.deepEqual(porAgenda.get(500), [c1, c2]);
});

test('agruparCotasPorAgenda — agendas diferentes viram grupos separados', () => {
  const c1 = cotaAgenda(1, 500);
  const c2 = cotaAgenda(2, 600);
  const { porAgenda } = agruparCotasPorAgenda([c1, c2]);
  assert.equal(porAgenda.size, 2);
  assert.deepEqual(porAgenda.get(500), [c1]);
  assert.deepEqual(porAgenda.get(600), [c2]);
});

test('agruparCotasPorAgenda — manuais e agenda misturadas, preservando ordem relativa dentro de cada grupo', () => {
  const manual1 = cotaManual(1);
  const agenda1 = cotaAgenda(2, 500);
  const manual2 = cotaManual(3);
  const agenda2 = cotaAgenda(4, 500);
  const { manuais, porAgenda } = agruparCotasPorAgenda([manual1, agenda1, manual2, agenda2]);
  assert.deepEqual(manuais, [manual1, manual2]);
  assert.deepEqual(porAgenda.get(500), [agenda1, agenda2]);
});

test('agruparCotasPorAgenda — origem AGENDA sem agendaId cai em manuais, nao some', () => {
  const orfa = cotaAgenda(1, null);
  const { manuais, porAgenda } = agruparCotasPorAgenda([orfa]);
  assert.deepEqual(manuais, [orfa]);
  assert.equal(porAgenda.size, 0);
});

test('periodoCobertoPorGrupo — grupo vazio cai no traço', () => {
  assert.equal(periodoCobertoPorGrupo([]), '—');
});

test('periodoCobertoPorGrupo — uma unica data', () => {
  const grupo = [cotaAgenda(1, 500, { dataEspecifica: '2026-10-05' })];
  assert.equal(periodoCobertoPorGrupo(grupo), '05/10/2026');
});

test('periodoCobertoPorGrupo — intervalo, independente da ordem de entrada', () => {
  const grupo = [
    cotaAgenda(1, 500, { dataEspecifica: '2026-10-26' }),
    cotaAgenda(2, 500, { dataEspecifica: '2026-10-05' }),
    cotaAgenda(3, 500, { dataEspecifica: '2026-10-12' })
  ];
  assert.equal(periodoCobertoPorGrupo(grupo), '05/10/2026 a 26/10/2026');
});

test('periodoCobertoPorGrupo — datas nao reconhecidas sao ignoradas, nao quebram', () => {
  const grupo = [
    cotaAgenda(1, 500, { dataEspecifica: null }),
    cotaAgenda(2, 500, { dataEspecifica: '2026-10-05' })
  ];
  assert.equal(periodoCobertoPorGrupo(grupo), '05/10/2026');
});
