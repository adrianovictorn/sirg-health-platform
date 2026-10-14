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
import {
  buildMenuForRole,
  hrefDaAgendaDoGrupo,
  hrefDashboardPorRole,
  organizarItensDoGrupo,
  resolveHref,
  roleVeRota,
} from "./menuConfig.js";

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

/** Como `hrefsVisiveis`, mas com o destino que a role de fato recebe (`hrefByRole`). */
function hrefsVisiveisResolvidos(role) {
  const hrefs = new Set();
  for (const section of buildMenuForRole(role)) {
    for (const item of section.items) {
      if (item.type === "group") {
        for (const link of item.items) hrefs.add(resolveHref(link, role));
      } else {
        hrefs.add(resolveHref(item, role));
      }
    }
  }
  return hrefs;
}

const TODAS_AS_ROLES = [
  "ADMIN",
  "GESTOR",
  "ADMIN_UNIDADE",
  "RECEPCAO",
  "ENFERMEIRO",
  "MEDICO",
  "USER",
  "PACIENTE",
  "COORD_TRANSPORTE",
];

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

// Atendimentos (/atendimentos): tela do operador da unidade executante (USER),
// com o municipio inteiro. Para o USER e o proprio "Dashboard"; o ADMIN tem um
// link dedicado. O backend (/api/atendimentos) so responde a esses dois perfis.
test("Atendimentos: so USER e ADMIN veem /atendimentos no menu", () => {
  for (const role of ["USER", "ADMIN"]) {
    assert.ok(
      hrefsVisiveisResolvidos(role).has("/atendimentos"),
      `${role} deveria ver /atendimentos`,
    );
  }
  for (const role of [
    "GESTOR",
    "ADMIN_UNIDADE",
    "RECEPCAO",
    "ENFERMEIRO",
    "MEDICO",
    "PACIENTE",
    "COORD_TRANSPORTE",
  ]) {
    assert.ok(
      !hrefsVisiveisResolvidos(role).has("/atendimentos"),
      `${role} nao deveria ver /atendimentos`,
    );
  }
});

test("Atendimentos: e o destino do Dashboard do USER (menu e troca de perfil); o do ADMIN nao muda", () => {
  assert.equal(hrefDashboardPorRole("USER"), "/atendimentos");
  assert.equal(hrefDashboardPorRole("ADMIN"), "/dashboard");
  assert.equal(hrefDashboardPorRole("PACIENTE"), "/dashboard/procedimentos");
});

// O grupo dinamico "Agendas" e so do USER, e cada agenda abre a tela de
// Atendimentos filtrada pelo grupo — nao mais /agendas/{grupo}, que filtra pela
// unidade solicitante e ficava vazia para o executante.
test('Grupo "Agendas": so o USER recebe, com os grupos carregados', () => {
  const dinamicos = {
    agendasHospital: [
      { label: "Laboratorio", href: hrefDaAgendaDoGrupo("lab") },
    ],
  };
  for (const role of TODAS_AS_ROLES) {
    const grupo = buildMenuForRole(role, dinamicos)
      .flatMap((section) => section.items)
      .find((item) => item.key === "agendas");
    if (role === "USER") {
      assert.deepEqual(
        grupo.items.map((l) => l.href),
        ["/atendimentos?grupo=lab"],
      );
    } else {
      assert.equal(grupo, undefined, role);
    }
  }
  // Sem grupos carregados, o grupo nao aparece nem para o USER.
  assert.ok(
    !buildMenuForRole("USER")
      .flatMap((section) => section.items)
      .some((item) => item.key === "agendas"),
  );
});

test("Agenda do grupo: abre /atendimentos filtrado, com o codigo escapado", () => {
  assert.equal(hrefDaAgendaDoGrupo("lab"), "/atendimentos?grupo=lab");
  assert.equal(
    hrefDaAgendaDoGrupo("raio x&y"),
    "/atendimentos?grupo=raio%20x%26y",
  );
});

test("Quantitativos: so USER e ADMIN veem, logo depois do item que abre /atendimentos", () => {
  const rota = "/atendimentos/quantitativos";
  for (const role of TODAS_AS_ROLES) {
    const deve = role === "USER" || role === "ADMIN";
    assert.equal(hrefsVisiveisResolvidos(role).has(rota), deve, role);
  }
  for (const role of ["USER", "ADMIN"]) {
    const links = buildMenuForRole(role)
      .flatMap((section) => section.items)
      .filter((item) => item.type !== "group")
      .map((item) => resolveHref(item, role));
    assert.equal(
      links[links.indexOf("/atendimentos") + 1],
      rota,
      `${role}: Quantitativos depois de /atendimentos`,
    );
  }
});

test("USER continua sem ver a Agenda do Dia do ADMIN (/dashboard/procedimentos/data)", () => {
  assert.ok(
    !hrefsVisiveisResolvidos("USER").has("/dashboard/procedimentos/data"),
  );
});

// "Pacientes" virou grupo com "Pacientes" e "Fila de Espera". Quem via
// /paciente continua vendo; GESTOR entra so para a fila (leitura); quem nao
// tem acesso a nenhum dos dois nao ve o grupo.
test('Grupo "Pacientes": clinicos e unidade veem /paciente e /paciente/fila', () => {
  for (const role of [
    "ADMIN",
    "ADMIN_UNIDADE",
    "RECEPCAO",
    "ENFERMEIRO",
    "MEDICO",
  ]) {
    const hrefs = hrefsVisiveis(role);
    assert.ok(hrefs.has("/paciente"), `${role} deveria ver /paciente`);
    assert.ok(
      hrefs.has("/paciente/fila"),
      `${role} deveria ver /paciente/fila`,
    );
  }
});

test('Grupo "Pacientes": GESTOR ve so a Fila de Espera', () => {
  const hrefs = hrefsVisiveis("GESTOR");
  assert.ok(hrefs.has("/paciente/fila"));
  assert.ok(!hrefs.has("/paciente"));
});

test('Grupo "Pacientes": USER, PACIENTE e COORD_TRANSPORTE nao veem nenhum dos dois', () => {
  for (const role of ["USER", "PACIENTE", "COORD_TRANSPORTE"]) {
    const hrefs = hrefsVisiveis(role);
    assert.ok(!hrefs.has("/paciente"), `${role} nao deveria ver /paciente`);
    assert.ok(
      !hrefs.has("/paciente/fila"),
      `${role} nao deveria ver /paciente/fila`,
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

// Custos: valor em reais so para ADMIN e GESTOR. GESTOR acompanha (painel, tetos,
// precos); importar planilha e escrita, so ADMIN. Nenhum perfil de unidade ve esses links.
const ROTAS_DE_CUSTO_LEITURA = [
  "/custos",
  "/custos/tetos",
  "/custos/especialidades",
];

test("Custos: ADMIN e GESTOR veem painel, tetos e precos", () => {
  for (const role of ["ADMIN", "GESTOR"]) {
    const hrefs = hrefsVisiveis(role);
    for (const rota of ROTAS_DE_CUSTO_LEITURA) {
      assert.ok(hrefs.has(rota), `${role} deveria ver ${rota}`);
    }
  }
});

test("Custos: so ADMIN ve a importacao de precos", () => {
  assert.ok(hrefsVisiveis("ADMIN").has("/admin/custos/importar"));
  assert.ok(!hrefsVisiveis("GESTOR").has("/admin/custos/importar"));
});

test("Custos: perfis de unidade e os demais nao veem nenhuma rota de custo", () => {
  for (const role of [
    "ADMIN_UNIDADE",
    "RECEPCAO",
    "ENFERMEIRO",
    "MEDICO",
    "USER",
    "PACIENTE",
    "COORD_TRANSPORTE",
  ]) {
    const hrefs = hrefsVisiveis(role);
    for (const rota of [...ROTAS_DE_CUSTO_LEITURA, "/admin/custos/importar"]) {
      assert.ok(!hrefs.has(rota), `${role} nao deveria ver ${rota}`);
    }
  }
});

// Caracterizacao anterior a mover os links de custo para dentro de
// "Gerenciamento de Unidades": o GESTOR nao tem acesso a nenhuma tela de
// gerenciamento de unidades, e isso nao pode mudar por ele passar a ver o grupo.
test("GESTOR nao ve nenhuma das rotas de gerenciamento de unidades", () => {
  const hrefs = hrefsVisiveis("GESTOR");
  for (const rota of ROTAS_GERENCIAMENTO_UNIDADES) {
    assert.ok(!hrefs.has(rota), `GESTOR nao deveria ver ${rota}`);
  }
});

// Os links de custo sao itens do grupo "Gerenciamento de Unidades", depois dos
// cinco de unidades. Excecao deliberada a regra do cabecalho deste arquivo (que
// nao testa posicao): a composicao deste grupo e exatamente o que o ajuste mudou.
function hrefsDoGrupo(role, key) {
  for (const section of buildMenuForRole(role)) {
    for (const item of section.items) {
      if (item.type === "group" && item.key === key) {
        return item.items.map((link) => link.href);
      }
    }
  }
  return null;
}

test('Grupo "Gerenciamento de Unidades": para o GESTOR, so os tres links de custo', () => {
  assert.deepEqual(
    hrefsDoGrupo("GESTOR", "gerenciamento-unidades"),
    ROTAS_DE_CUSTO_LEITURA,
  );
});

test('Grupo "Gerenciamento de Unidades": para o ADMIN, unidades e depois custos', () => {
  assert.deepEqual(hrefsDoGrupo("ADMIN", "gerenciamento-unidades"), [
    ...ROTAS_GERENCIAMENTO_UNIDADES,
    ...ROTAS_DE_CUSTO_LEITURA,
    "/admin/custos/importar",
  ]);
});

test('O grupo proprio "Custos" nao existe mais para nenhum perfil', () => {
  for (const role of ["ADMIN", ...OUTRAS_ROLES, "COORD_TRANSPORTE"]) {
    assert.equal(hrefsDoGrupo(role, "custos"), null, role);
  }
});

// Terceiro nivel do menu (subgrupos). `organizarItensDoGrupo` so reorganiza para
// exibicao os links que `buildMenuForRole` ja filtrou: nao pode esconder,
// duplicar nem reordenar link, para nenhum perfil e nenhum grupo.
function grupoDe(role, key) {
  for (const section of buildMenuForRole(role)) {
    for (const item of section.items) {
      if (item.type === "group" && item.key === key) return item;
    }
  }
  return null;
}

test("Subgrupos: a organizacao em niveis devolve exatamente os links filtrados, na mesma ordem", () => {
  for (const role of TODAS_AS_ROLES) {
    for (const section of buildMenuForRole(role)) {
      for (const item of section.items) {
        if (item.type !== "group") continue;
        const achatado = organizarItensDoGrupo(item).flatMap((e) =>
          e.type === "subgroup" ? e.items : [e],
        );
        assert.deepEqual(achatado, item.items, `${role} / ${item.key}`);
      }
    }
  }
});

test('Subgrupos: ADMIN ve "Unidades" e "Custos" dentro de "Unidades e Custos"', () => {
  const entradas = organizarItensDoGrupo(
    grupoDe("ADMIN", "gerenciamento-unidades"),
  );
  assert.deepEqual(
    entradas.map((e) => [e.type, e.label, e.items.map((l) => l.href)]),
    [
      ["subgroup", "Unidades", ROTAS_GERENCIAMENTO_UNIDADES],
      [
        "subgroup",
        "Custos",
        [...ROTAS_DE_CUSTO_LEITURA, "/admin/custos/importar"],
      ],
    ],
  );
});

test("Subgrupos: GESTOR, que so enxerga custos, recebe os tres links direto no grupo", () => {
  const entradas = organizarItensDoGrupo(
    grupoDe("GESTOR", "gerenciamento-unidades"),
  );
  assert.deepEqual(
    entradas.map((e) => [e.type, e.href]),
    ROTAS_DE_CUSTO_LEITURA.map((href) => [undefined, href]),
  );
});

test("Subgrupos: nenhum perfil recebe subgrupo vazio ou de um item so", () => {
  for (const role of TODAS_AS_ROLES) {
    for (const section of buildMenuForRole(role)) {
      for (const item of section.items) {
        if (item.type !== "group") continue;
        for (const e of organizarItensDoGrupo(item)) {
          if (e.type === "subgroup") {
            assert.ok(e.items.length >= 2, `${role} / ${e.key}`);
          }
        }
      }
    }
  }
});

test("roleVeRota: coincide com o conjunto de rotas visiveis no menu, para toda role", () => {
  for (const role of TODAS_AS_ROLES) {
    const visiveis = hrefsVisiveis(role);
    for (const href of ["/paciente", "/exames", "/admin/cotas"]) {
      assert.equal(
        roleVeRota(role, href),
        visiveis.has(href),
        `${role} ${href}`,
      );
    }
  }
});

test("roleVeRota: sem role, ou rota fora do menu, nao ve nada", () => {
  assert.equal(roleVeRota(null, "/paciente"), false);
  assert.equal(roleVeRota("ADMIN", "/rota-que-nao-existe"), false);
});
