// Testes de src/lib/agendamentoParcial.js — a decisão do que fazer com a
// resposta da pré-verificação do agendamento em lote, separada da tela
// (agendar/+page.svelte) para ser testável.
//
// Não usa nenhum framework de teste (o projeto não tem um configurado) —
// roda com `node --test src/lib/agendamentoParcial.test.mjs`.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { analisarVerificacao, montarBodyViaveis } from './agendamentoParcial.js';

const viavel = (codigo) => ({ codigo, nome: codigo, podeAgendar: true, corrigivel: false, motivo: null });
const recusado = (codigo) => ({ codigo, nome: codigo, podeAgendar: false, corrigivel: false, motivo: 'Cota esgotada' });
const corrigivel = (codigo) => ({ codigo, nome: codigo, podeAgendar: false, corrigivel: true, motivo: 'Hora fora' });

test('analisarVerificacao — todos viáveis: TUDO_OK, sem etapa extra', () => {
  const analise = analisarVerificacao({ itens: [viavel('A'), viavel('B')], bloqueioDoLote: null });
  assert.equal(analise.situacao, 'TUDO_OK');
  assert.equal(analise.viaveis.length, 2);
  assert.deepEqual(analise.recusados, []);
});

test('analisarVerificacao — parte de fora: PARCIAL, separando viáveis e recusados', () => {
  const analise = analisarVerificacao({ itens: [viavel('A'), recusado('B'), viavel('C')], bloqueioDoLote: null });
  assert.equal(analise.situacao, 'PARCIAL');
  assert.deepEqual(analise.viaveis.map((i) => i.codigo), ['A', 'C']);
  assert.deepEqual(analise.recusados.map((i) => i.codigo), ['B']);
});

test('analisarVerificacao — nenhum viável: NENHUM', () => {
  assert.equal(analisarVerificacao({ itens: [recusado('A'), recusado('B')] }).situacao, 'NENHUM');
});

test('analisarVerificacao — item corrigível vence qualquer outra situação', () => {
  const analise = analisarVerificacao({
    itens: [viavel('A'), recusado('B'), corrigivel('C')],
    bloqueioDoLote: 'Teto financeiro esgotado'
  });
  assert.equal(analise.situacao, 'CORRIGIR');
  assert.deepEqual(analise.corrigiveis.map((i) => i.codigo), ['C']);
});

test('analisarVerificacao — teto barra os viáveis: BLOQUEADO, mesmo com todos viáveis', () => {
  const analise = analisarVerificacao({ itens: [viavel('A'), viavel('B')], bloqueioDoLote: 'Teto financeiro esgotado' });
  assert.equal(analise.situacao, 'BLOQUEADO');
  assert.equal(analise.bloqueioDoLote, 'Teto financeiro esgotado');
});

test('analisarVerificacao — resposta vazia ou malformada nunca vira TUDO_OK', () => {
  assert.equal(analisarVerificacao(null).situacao, 'NENHUM');
  assert.equal(analisarVerificacao({}).situacao, 'NENHUM');
  assert.equal(analisarVerificacao({ itens: [] }).situacao, 'NENHUM');
});

test('montarBodyViaveis — mantém só os exames viáveis e as escolhas deles', () => {
  const body = {
    examesSelecionados: ['A', 'B', 'C'],
    dataAgendada: '2026-10-08',
    turno: 'MANHA',
    observacoes: 'obs',
    localAgendado: null,
    localAgendamentoId: 7,
    cotasSelecionadas: { A: 1, B: 2 },
    horariosSelecionados: { B: '08:00' },
    profissionaisSelecionados: { C: 9, B: 8 }
  };

  const resultado = montarBodyViaveis(body, ['A', 'C']);

  assert.deepEqual(resultado.examesSelecionados, ['A', 'C']);
  assert.deepEqual(resultado.cotasSelecionadas, { A: 1 });
  assert.equal(resultado.horariosSelecionados, null);
  assert.deepEqual(resultado.profissionaisSelecionados, { C: 9 });
  assert.equal(resultado.dataAgendada, '2026-10-08');
  assert.equal(resultado.localAgendamentoId, 7);
  assert.equal(resultado.observacoes, 'obs');
});

test('montarBodyViaveis — não altera o corpo original e aceita mapas nulos', () => {
  const body = {
    examesSelecionados: ['A', 'B'],
    cotasSelecionadas: null,
    horariosSelecionados: null,
    profissionaisSelecionados: null
  };

  const resultado = montarBodyViaveis(body, ['A']);

  assert.deepEqual(body.examesSelecionados, ['A', 'B']);
  assert.deepEqual(resultado.examesSelecionados, ['A']);
  assert.equal(resultado.cotasSelecionadas, null);
});
