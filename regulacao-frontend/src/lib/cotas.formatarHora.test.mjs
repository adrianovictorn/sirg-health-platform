// Caracterização de `formatarHora` (src/lib/cotas.js) — `LocalTime` do backend,
// que chega como array `[h, m, ...]` ou como string "HH:mm[:ss]". A edição de
// cota em /admin/cotas/[id]/editar depende deste comportamento para preencher
// os campos de hora.
//
// Não usa nenhum framework de teste (o projeto não tem um configurado) —
// roda com `node --test src/lib/cotas.formatarHora.test.mjs`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { formatarHora } from "./cotas.js";

test("formatarHora — array ganha zero à esquerda", () => {
  assert.equal(formatarHora([8, 0]), "08:00");
  assert.equal(formatarHora([18, 30]), "18:30");
});

test("formatarHora — array com segundo e nanos descarta o que passa do minuto", () => {
  assert.equal(formatarHora([8, 5, 30]), "08:05");
  assert.equal(formatarHora([8, 5, 30, 123000000]), "08:05");
});

test("formatarHora — string é cortada em HH:mm", () => {
  assert.equal(formatarHora("08:00"), "08:00");
  assert.equal(formatarHora("18:30:00"), "18:30");
});

test("formatarHora — vazio ou não reconhecido vira string vazia", () => {
  assert.equal(formatarHora(null), "");
  assert.equal(formatarHora(undefined), "");
  assert.equal(formatarHora(""), "");
  assert.equal(formatarHora([]), "");
  assert.equal(formatarHora([8, "x"]), "");
  assert.equal(formatarHora("8:0"), "");
});
