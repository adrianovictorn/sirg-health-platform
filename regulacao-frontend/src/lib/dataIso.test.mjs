// Testes de src/lib/dataIso.js — normalização de `LocalDate` do backend para
// "AAAA-MM-DD". A edição de cota compara a data carregada com a do campo de
// data para decidir se há migração de agendamentos; as duas precisam estar no
// mesmo formato.
//
// Não usa nenhum framework de teste (o projeto não tem um configurado) —
// roda com `node --test src/lib/dataIso.test.mjs`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { paraDataISO } from "./dataIso.js";

test("paraDataISO — array [ano, mes, dia]", () => {
  assert.equal(paraDataISO([2026, 10, 13]), "2026-10-13");
});

test("paraDataISO — mês e dia de 1 dígito ganham zero à esquerda", () => {
  assert.equal(paraDataISO([2026, 9, 6]), "2026-09-06");
  assert.equal(paraDataISO("2026-9-6"), "2026-09-06");
});

test("paraDataISO — elementos de hora no array são ignorados", () => {
  assert.equal(paraDataISO([2026, 10, 13, 0, 0]), "2026-10-13");
});

test("paraDataISO — string ISO passa igual; sufixo de hora é ignorado", () => {
  assert.equal(paraDataISO("2026-10-13"), "2026-10-13");
  assert.equal(paraDataISO("2026-10-13T00:00:00"), "2026-10-13");
});

test("paraDataISO — dia e mês não são trocados nem deslocados", () => {
  assert.equal(paraDataISO([2026, 3, 1]), "2026-03-01");
  assert.equal(paraDataISO("2026-03-01"), "2026-03-01");
});

test("paraDataISO — vazio ou não reconhecido vira string vazia", () => {
  assert.equal(paraDataISO(null), "");
  assert.equal(paraDataISO(undefined), "");
  assert.equal(paraDataISO(""), "");
  assert.equal(paraDataISO([]), "");
  assert.equal(paraDataISO([2026, "x", 1]), "");
  assert.equal(paraDataISO("13/10/2026"), "");
});

test("paraDataISO — array e ISO da mesma data dão o mesmo resultado", () => {
  assert.equal(paraDataISO([2026, 10, 13]), paraDataISO("2026-10-13"));
  assert.notEqual(paraDataISO([2026, 10, 13]), paraDataISO("2026-10-14"));
});
