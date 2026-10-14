// Papel da unidade (ADR 0012). Roda com `node --test src/lib/papelUnidade.test.mjs`.
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  papelDaUnidade,
  parametrizacaoIncompleta,
  resumoDaLista,
  resumoDaParametrizacao,
} from "./papelUnidade.js";

test("tag: combinação de tipo e configurada", () => {
  const casos = [
    [{ tipo: "SOLICITANTE", configurada: false }, "Unidade solicitante"],
    [{ tipo: "EXECUTANTE", configurada: false }, "Unidade solicitante"],
    [{ tipo: "AMBOS", configurada: false }, "Unidade solicitante"],
    [{ tipo: null, configurada: false }, "Unidade solicitante"],
    [{ tipo: "EXECUTANTE", configurada: true }, "Unidade executante"],
    [{ tipo: "AMBOS", configurada: true }, "Ambas"],
    // Dado impossível (configurada exige tipo EXECUTANTE/AMBOS): cai em solicitante.
    [{ tipo: "SOLICITANTE", configurada: true }, "Unidade solicitante"],
    [{ tipo: null, configurada: true }, "Unidade solicitante"],
    [null, "Unidade solicitante"],
    [undefined, "Unidade solicitante"],
  ];
  for (const [resumo, rotulo] of casos) {
    assert.equal(papelDaUnidade(resumo).rotulo, rotulo, JSON.stringify(resumo));
  }
  assert.equal(papelDaUnidade({ tipo: "AMBOS", configurada: true }).papel, "AMBAS");
});

test("incompleta: qualquer vínculo (inclusive só operador) em unidade ativa e não-solicitante não configurada", () => {
  const base = { ativo: true, tipo: "AMBOS", configurada: false, grupos: 0, especialidades: 0, locais: 0, operadores: 0 };
  assert.equal(parametrizacaoIncompleta(base), false, "sem vínculo nenhum");
  for (const campo of ["grupos", "especialidades", "locais", "operadores"]) {
    assert.equal(parametrizacaoIncompleta({ ...base, [campo]: 1 }), true, `só ${campo}`);
  }
  assert.equal(parametrizacaoIncompleta({ ...base, locais: 1, configurada: true }), false, "configurada");
  assert.equal(parametrizacaoIncompleta({ ...base, locais: 1, ativo: false }), false, "inativa");
  assert.equal(parametrizacaoIncompleta({ ...base, locais: 1, tipo: "SOLICITANTE" }), false, "solicitante");
  assert.equal(parametrizacaoIncompleta({ ...base, locais: 1, tipo: "EXECUTANTE" }), true, "executante");
  assert.equal(parametrizacaoIncompleta(null), false);
});

test("lista: unidade ausente do resumo de executantes não está configurada nem incompleta", () => {
  const executantes = [
    { unidadeId: 1, configurada: true, grupos: 1, especialidades: 0, locais: 1, operadores: 2 },
    { unidadeId: 2, configurada: false, grupos: 0, especialidades: 0, locais: 1, operadores: 0 },
  ];
  const r1 = resumoDaLista({ id: 1, ativo: true, tipo: "AMBOS" }, executantes);
  assert.equal(papelDaUnidade(r1).rotulo, "Ambas");
  assert.equal(parametrizacaoIncompleta(r1), false);

  const r2 = resumoDaLista({ id: 2, ativo: true, tipo: "EXECUTANTE" }, executantes);
  assert.equal(papelDaUnidade(r2).rotulo, "Unidade solicitante");
  assert.equal(parametrizacaoIncompleta(r2), true);

  const ausente = resumoDaLista({ id: 3, ativo: false, tipo: "AMBOS" }, executantes);
  assert.equal(papelDaUnidade(ausente).rotulo, "Unidade solicitante");
  assert.equal(parametrizacaoIncompleta(ausente), false);
  assert.equal(papelDaUnidade(resumoDaLista({ id: 1, ativo: true, tipo: "AMBOS" }, null)).rotulo, "Unidade solicitante");
});

test("lista e parametrização dão o mesmo resultado para dados equivalentes", () => {
  const view = {
    tipo: "EXECUTANTE",
    configurada: false,
    grupos: [{ id: 1 }],
    especialidades: [],
    locais: [],
    operadores: [{ id: 9 }],
  };
  const daParametrizacao = resumoDaParametrizacao(view, true);
  const daLista = resumoDaLista({ id: 5, ativo: true, tipo: "EXECUTANTE" }, [
    { unidadeId: 5, configurada: false, grupos: 1, especialidades: 0, locais: 0, operadores: 1 },
  ]);
  assert.deepEqual(daParametrizacao, daLista);
  assert.equal(papelDaUnidade({ ...view, configurada: true }).rotulo, "Unidade executante");
});
