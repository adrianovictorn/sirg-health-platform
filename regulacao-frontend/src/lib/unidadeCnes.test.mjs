// Testes de caracterização de src/lib/unidadeCnes.js — capturam o
// comportamento da lógica de merge do CNES (extraída do modal original de
// /admin/unidades) antes de ligá-la nas duas telas novas (nova/editar), para
// que uma regressão na integração seja distinguível de uma regressão na
// lógica em si.
//
// Roda com `node --test src/lib/unidadeCnes.test.mjs`.
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  CAMPOS_CNES,
  cnesPertenceAOutraUnidade,
  divergeDosDadosAtuais,
  mesclarCamposCnes,
} from "./unidadeCnes.js";

test('CAMPOS_CNES nunca inclui "nome" (apelido interno, sempre manual)', () => {
  assert.ok(!CAMPOS_CNES.includes("nome"));
  assert.ok(!CAMPOS_CNES.includes("codigo"));
  assert.ok(!CAMPOS_CNES.includes("grupoRelatorioId"));
});

test("cnesPertenceAOutraUnidade — nao cadastrado ainda, false", () => {
  assert.equal(
    cnesPertenceAOutraUnidade({ jaCadastrado: false, unidadeId: null }, null),
    false,
  );
});

test("cnesPertenceAOutraUnidade — cadastrado na propria unidade em edicao, false", () => {
  assert.equal(
    cnesPertenceAOutraUnidade({ jaCadastrado: true, unidadeId: 5 }, 5),
    false,
  );
});

test("cnesPertenceAOutraUnidade — cadastrado em outra unidade, true", () => {
  assert.equal(
    cnesPertenceAOutraUnidade({ jaCadastrado: true, unidadeId: 7 }, 5),
    true,
  );
});

test("cnesPertenceAOutraUnidade — cadastrado em outra unidade, criando unidade nova (sem editando), true", () => {
  assert.equal(
    cnesPertenceAOutraUnidade({ jaCadastrado: true, unidadeId: 7 }, null),
    true,
  );
});

test("divergeDosDadosAtuais — form vazio nunca diverge (nada a comparar)", () => {
  const dados = { razaoSocial: "ABC LTDA", endereco: "Rua X" };
  assert.equal(divergeDosDadosAtuais({}, dados), false);
});

test("divergeDosDadosAtuais — campo igual nao diverge", () => {
  const form = { razaoSocial: "ABC LTDA" };
  const dados = { razaoSocial: "ABC LTDA" };
  assert.equal(divergeDosDadosAtuais(form, dados), false);
});

test("divergeDosDadosAtuais — campo diferente diverge", () => {
  const form = { razaoSocial: "Nome antigo" };
  const dados = { razaoSocial: "Nome novo do CNES" };
  assert.equal(divergeDosDadosAtuais(form, dados), true);
});

test("divergeDosDadosAtuais — so considera os campos de CAMPOS_CNES", () => {
  const form = { nome: "Apelido interno" };
  const dados = { nome: "Nome fantasia diferente" }; // "nome" nao esta em CAMPOS_CNES
  assert.equal(divergeDosDadosAtuais(form, dados), false);
});

test("mesclarCamposCnes — so sobrescreve campos que a busca de fato trouxe", () => {
  const form = {
    nome: "Apelido",
    codigo: "COD1",
    endereco: "Rua antiga",
    telefone: "999",
  };
  const dados = {
    endereco: "Rua nova",
    telefone: null,
    cnpj: "12345678000100",
  };

  const resultado = mesclarCamposCnes(form, dados);

  assert.equal(resultado.nome, "Apelido"); // nunca mexido
  assert.equal(resultado.codigo, "COD1"); // fora de CAMPOS_CNES, intocado
  assert.equal(resultado.endereco, "Rua nova"); // veio da busca
  assert.equal(resultado.telefone, "999"); // busca trouxe null, mantem o que ja tinha
  assert.equal(resultado.cnpj, "12345678000100"); // novo campo aplicado
});

test("mesclarCamposCnes — marca importadoDoCnes = true", () => {
  const resultado = mesclarCamposCnes({}, { razaoSocial: "ABC" });
  assert.equal(resultado.importadoDoCnes, true);
});

test("mesclarCamposCnes — nao muta o form original (retorna objeto novo)", () => {
  const form = { endereco: "Rua antiga" };
  const resultado = mesclarCamposCnes(form, { endereco: "Rua nova" });
  assert.equal(form.endereco, "Rua antiga");
  assert.equal(resultado.endereco, "Rua nova");
});
