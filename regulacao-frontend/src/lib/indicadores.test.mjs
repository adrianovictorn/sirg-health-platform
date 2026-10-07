// Testes de src/lib/indicadores.js — as contas e os rótulos dos indicadores
// gerenciais, separados da tela (indicadores/PainelGerencial.svelte).
//
// Não usa nenhum framework de teste (o projeto não tem um configurado) —
// roda com `node --test src/lib/indicadores.test.mjs`.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  percentual,
  textoPercentual,
  percentualDoTeto,
  situacaoDoTeto,
  rotuloMes,
  rotuloMotivo,
  rotuloPrioridade,
  textoDias
} from './indicadores.js';

test('percentual — arredonda para inteiro', () => {
  assert.equal(percentual(1, 3), 33);
  assert.equal(percentual(2, 3), 67);
  assert.equal(percentual(0, 10), 0);
  assert.equal(percentual(10, 10), 100);
});

test('percentual — sem total é null, não 0: "nada a medir" não é "0%"', () => {
  assert.equal(percentual(0, 0), null);
  assert.equal(percentual(5, 0), null);
  assert.equal(percentual(5, null), null);
  assert.equal(percentual(5, undefined), null);
  assert.equal(textoPercentual(percentual(0, 0)), '—');
  assert.equal(textoPercentual(percentual(0, 4)), '0%');
});

test('percentualDoTeto — mesma conta do painel de custos, aceitando valores em texto', () => {
  assert.equal(percentualDoTeto({ valorTotal: 100, valorUtilizado: 85 }), 85);
  assert.equal(percentualDoTeto({ valorTotal: '200.00', valorUtilizado: '250.00' }), 125);
  assert.equal(percentualDoTeto({ valorTotal: 0, valorUtilizado: 0 }), null);
});

test('situacaoDoTeto — atenção a partir de 80%, esgotado em 100%, acima quando passa', () => {
  assert.equal(situacaoDoTeto({ valorTotal: 100, valorUtilizado: 79.99 }), 'NORMAL');
  assert.equal(situacaoDoTeto({ valorTotal: 100, valorUtilizado: 80 }), 'ATENCAO');
  assert.equal(situacaoDoTeto({ valorTotal: 100, valorUtilizado: 99.99 }), 'ATENCAO');
  assert.equal(situacaoDoTeto({ valorTotal: 100, valorUtilizado: 100 }), 'ESGOTADO');
  assert.equal(situacaoDoTeto({ valorTotal: '100.00', valorUtilizado: '100.01' }), 'ACIMA');
  assert.equal(situacaoDoTeto({ valorTotal: 0, valorUtilizado: 0 }), 'NORMAL');
  // Nada liberado e mesmo assim houve débito: não pode aparecer como "dentro do teto".
  assert.equal(situacaoDoTeto({ valorTotal: 0, valorUtilizado: 4.11 }), 'ACIMA');
});

test('rotuloMes — AAAA-MM vira mês/ano curto', () => {
  assert.equal(rotuloMes('2026-10'), 'out/26');
  assert.equal(rotuloMes('2025-01'), 'jan/25');
  assert.equal(rotuloMes('2026-13'), '2026-13');
  assert.equal(rotuloMes(null), '');
});

test('rotuloMotivo — código conhecido vira texto; desconhecido aparece como veio', () => {
  assert.equal(rotuloMotivo('TELEFONE_INVALIDO'), 'Telefone inválido ou sem DDD');
  assert.equal(rotuloMotivo('MOTIVO_NOVO'), 'MOTIVO_NOVO');
});

test('rotuloPrioridade — sem prioridade tem rótulo próprio', () => {
  assert.equal(rotuloPrioridade('URGENTE'), 'Urgente');
  assert.equal(rotuloPrioridade(null), 'Sem prioridade');
});

test('textoDias — uma casa, singular e sem medida', () => {
  assert.equal(textoDias(5.5), '5,5 dias');
  assert.equal(textoDias(1), '1 dia');
  assert.equal(textoDias(0), '0 dias');
  assert.equal(textoDias(null), '—');
});
