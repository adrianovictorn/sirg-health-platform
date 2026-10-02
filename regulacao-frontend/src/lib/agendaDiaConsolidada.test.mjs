// Testes de src/lib/agendaDiaConsolidada.js — roda com
// `node --test src/lib/agendaDiaConsolidada.test.mjs` (mesmo padrao de cotas.*.test.mjs).
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  achatarConsolidado,
  celulaCsv,
  dataHojeBahia,
  gerarCsv,
} from "./agendaDiaConsolidada.js";

const ind = (n) => ({
  pacientes: n,
  itens: n,
  agendados: n,
  realizados: 0,
  faltasCancelados: 0,
  outros: 0,
});

test("dataHojeBahia — 23h de Salvador ainda e o mesmo dia (UTC ja virou)", () => {
  // 2026-10-02T02:00Z = 2026-10-01 23:00 em America/Bahia (UTC-3)
  assert.equal(dataHojeBahia(new Date("2026-10-02T02:00:00Z")), "2026-10-01");
  assert.equal(dataHojeBahia(new Date("2026-10-02T03:30:00Z")), "2026-10-02");
});

test("achatarConsolidado — uma linha por unidade, grupo e especialidade", () => {
  const view = {
    unidades: [
      {
        nome: "USF 1",
        indicadores: ind(3),
        cotas: [],
        grupos: [
          {
            nome: "Cardiologia",
            indicadores: ind(3),
            cotas: [],
            especialidades: [
              {
                nome: "Ecocardiograma",
                indicadores: ind(3),
                cotas: [{ livres: 6, total: 10 }],
              },
            ],
          },
        ],
      },
    ],
  };
  const linhas = achatarConsolidado(view);
  assert.equal(linhas.length, 3);
  assert.deepEqual(linhas[0].slice(0, 3), ["USF 1", "", ""]);
  assert.deepEqual(linhas[2].slice(0, 3), [
    "USF 1",
    "Cardiologia",
    "Ecocardiograma",
  ]);
  assert.equal(linhas[2][9], "6/10");
});

test("achatarConsolidado — sem dados devolve lista vazia", () => {
  assert.deepEqual(achatarConsolidado(null), []);
  assert.deepEqual(achatarConsolidado({ unidades: [] }), []);
});

test("celulaCsv — protege contra injecao de formula, mas preserva numeros", () => {
  assert.equal(celulaCsv("=SOMA(A1)"), "'=SOMA(A1)");
  assert.equal(celulaCsv("+55"), "'+55");
  assert.equal(celulaCsv("@usuario"), "'@usuario");
  assert.equal(celulaCsv(-1), "-1");
  assert.equal(celulaCsv(null), "");
});

test("celulaCsv — escapa aspas, ponto e virgula e quebra de linha", () => {
  assert.equal(celulaCsv("a;b"), '"a;b"');
  assert.equal(celulaCsv('diz "oi"'), '"diz ""oi"""');
});

test("gerarCsv — BOM, separador ; e quebra CRLF", () => {
  const csv = gerarCsv(["A", "B"], [["x", 1]]);
  assert.ok(csv.startsWith("﻿"));
  assert.equal(csv.slice(1), "A;B\r\nx;1");
});
