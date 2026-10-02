// Testes de caracterização de src/lib/menuConfig.js — não existia nenhum teste
// cobrindo a árvore de menu antes deste ajuste (reorganização em "Gerenciamento
// de Unidades" + ocultar "Liberação de Agenda"). Capturam os INVARIANTES de
// acesso por role que não podem regredir com a reorganização: quem via uma
// rota antes continua vendo depois, e ninguém ganha acesso novo por engano.
//
// Não testam a posição/rótulo exato dos itens no menu (isso é o que o ajuste
// muda de propósito) — só o conjunto de rotas visível por role, que é o que
// importa para controle de acesso via navegação.
//
// Roda com `node --test src/lib/menuConfig.characterization.test.mjs`.
import { test } from "node:test";
import assert from "node:assert/strict";
import { buildMenuForRole } from "./menuConfig.js";

/** Todos os hrefs visíveis para uma role, achatando grupos e links soltos. */
function hrefsVisiveis(role) {
  const hrefs = new Set();
  for (const section of buildMenuForRole(role)) {
    for (const item of section.items) {
      if (item.type === "group") {
        for (const link of item.items) {
          if (link.href) hrefs.add(link.href);
        }
      } else if (item.href) {
        hrefs.add(item.href);
      }
    }
  }
  return hrefs;
}

const ROTAS_GERENCIAMENTO_UNIDADES = [
  "/admin/cotas",
  "/admin/unidades",
  "/cadastrar/cidade/local-agendamento",
  "/cadastrar/cidade",
  "/admin/profissionais",
];

const OUTRAS_ROLES = [
  "GESTOR",
  "ADMIN_UNIDADE",
  "RECEPCAO",
  "ENFERMEIRO",
  "MEDICO",
  "USER",
  "PACIENTE",
];

test('ADMIN ve todas as rotas que hoje formam "Gerenciamento de Unidades", onde quer que estejam no menu', () => {
  const hrefs = hrefsVisiveis("ADMIN");
  for (const rota of ROTAS_GERENCIAMENTO_UNIDADES) {
    assert.ok(hrefs.has(rota), `ADMIN deveria ver ${rota}`);
  }
});

test("COORD_TRANSPORTE ve /cadastrar/cidade", () => {
  assert.ok(hrefsVisiveis("COORD_TRANSPORTE").has("/cadastrar/cidade"));
});

test('COORD_TRANSPORTE ve /cadastrar/cidade/local-agendamento (hoje como "Ponto de Parada")', () => {
  assert.ok(
    hrefsVisiveis("COORD_TRANSPORTE").has(
      "/cadastrar/cidade/local-agendamento",
    ),
  );
});

test("Nenhuma role fora ADMIN ve as rotas administrativas de unidade/profissional/cota", () => {
  for (const role of OUTRAS_ROLES) {
    const hrefs = hrefsVisiveis(role);
    assert.ok(
      !hrefs.has("/admin/unidades"),
      `${role} nao deveria ver /admin/unidades`,
    );
    assert.ok(
      !hrefs.has("/admin/profissionais"),
      `${role} nao deveria ver /admin/profissionais`,
    );
    assert.ok(
      !hrefs.has("/admin/cotas"),
      `${role} nao deveria ver /admin/cotas`,
    );
  }
});

test("COORD_TRANSPORTE nao ganha acesso as rotas administrativas de unidade/profissional/cota", () => {
  const hrefs = hrefsVisiveis("COORD_TRANSPORTE");
  assert.ok(!hrefs.has("/admin/unidades"));
  assert.ok(!hrefs.has("/admin/profissionais"));
  assert.ok(!hrefs.has("/admin/cotas"));
});

// "Agenda do Dia" (/dashboard/procedimentos/data) e a tela de quantitativos por
// unidade (v1.6), usada pelos operadores das unidades. A visao consolidada
// (todas as unidades) e embutida nessa mesma pagina e restrita ao ADMIN ativo
// DENTRO da pagina — o item de menu nao pode sumir das unidades por causa dela.
test('Perfis clinicos e de unidade continuam vendo "Agenda do Dia" (/dashboard/procedimentos/data)', () => {
  for (const role of [
    "ADMIN",
    "ADMIN_UNIDADE",
    "RECEPCAO",
    "ENFERMEIRO",
    "MEDICO",
  ]) {
    assert.ok(
      hrefsVisiveis(role).has("/dashboard/procedimentos/data"),
      `${role} deveria ver /dashboard/procedimentos/data`,
    );
  }
});

test("Nenhuma role ve /liberacao-agenda depois de oculta (ADMIN incluido)", () => {
  for (const role of ["ADMIN", ...OUTRAS_ROLES, "COORD_TRANSPORTE"]) {
    assert.ok(
      !hrefsVisiveis(role).has("/liberacao-agenda"),
      `${role} nao deveria ver /liberacao-agenda`,
    );
  }
});
