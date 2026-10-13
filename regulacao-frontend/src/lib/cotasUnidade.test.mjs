// Testes de caracterização de src/lib/cotasUnidade.js — congelam o que a tela
// /unidade/cotas fazia INLINE (em `$:` dentro do .svelte) antes de a lógica ser
// extraída e antes de qualquer filtro novo. Cada caso aponta a linha original
// de routes/unidade/cotas/+page.svelte no HEAD.
//
// Sem framework (o projeto não tem um configurado). Rode com caminho explícito:
//   node --test src/lib/cotasUnidade.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  ITENS_POR_PAGINA_PADRAO,
  juntarCotas,
  nomeDaCota,
  percentual,
  corDaBarra,
  situacaoDaCota,
  lerGrupoDaUrl,
  filtrarPorGrupo,
  nomeDoGrupoFiltrado,
  ordenarCotas,
  paginar,
  totalDePaginas,
  TAMANHOS_DE_PAGINA,
  lerFiltrosDaUnidade,
  lerEstadoDaUrl,
  serializarEstado,
  haFiltrosDaUnidade,
  filtrarCotasDaUnidade,
  ordenarComParticao,
  contarParticao,
  filtrosDoCartao,
  cartaoAtivo,
  opcoesDeGrupo,
  opcoesDeEspecialidade,
  numerosDePagina,
} from "./cotasUnidade.js";

function cota(id, o = {}) {
  return {
    id,
    tipoPeriodo: "MENSAL",
    periodo: "2026-09",
    especialidadeNome: null,
    grupoEspecialidadesId: null,
    grupoEspecialidadesNome: null,
    quantidadeTotal: 10,
    quantidadeUtilizada: 4,
    saldoDisponivel: 6,
    ativo: true,
    ...o,
  };
}

const ids = (l) => l.map((c) => c.id);

// ---- linha 102: cotas = [...daUnidade, ...doGrupo]

test("juntarCotas: unidade antes do grupo, sem deduplicar nem alterar as entradas", () => {
  const u = [cota(1), cota(2)];
  const g = [cota(2), cota(3)];
  const r = juntarCotas(u, g);
  assert.deepEqual(ids(r), [1, 2, 2, 3]);
  assert.deepEqual(ids(u), [1, 2]);
  assert.deepEqual(ids(g), [2, 3]);
});

// ---- linhas 125-127

test("nomeDaCota: especialidade, depois grupo de especialidades, depois vazio", () => {
  assert.equal(nomeDaCota(cota(1, { especialidadeNome: "Cardio" })), "Cardio");
  assert.equal(
    nomeDaCota(cota(1, { grupoEspecialidadesNome: "Laboratório" })),
    "Laboratório",
  );
  assert.equal(nomeDaCota(cota(1)), "");
});

// ---- linhas 113-123

test("percentual: total zero conta como 100 e o resultado fica limitado a 100", () => {
  assert.equal(
    percentual(cota(1, { quantidadeTotal: 0, quantidadeUtilizada: 0 })),
    100,
  );
  assert.equal(
    percentual(cota(1, { quantidadeTotal: 10, quantidadeUtilizada: 5 })),
    50,
  );
  assert.equal(
    percentual(cota(1, { quantidadeTotal: 10, quantidadeUtilizada: 15 })),
    100,
  );
  assert.equal(
    percentual(cota(1, { quantidadeTotal: 3, quantidadeUtilizada: 1 })),
    33,
  );
});

test("corDaBarra: vermelho a partir de 100, âmbar a partir de 80, senão emerald", () => {
  assert.equal(
    corDaBarra(cota(1, { quantidadeTotal: 10, quantidadeUtilizada: 10 })),
    "bg-red-500",
  );
  assert.equal(
    corDaBarra(cota(1, { quantidadeTotal: 10, quantidadeUtilizada: 8 })),
    "bg-amber-500",
  );
  assert.equal(
    corDaBarra(cota(1, { quantidadeTotal: 10, quantidadeUtilizada: 7 })),
    "bg-emerald-600",
  );
  assert.equal(
    corDaBarra(cota(1, { quantidadeTotal: 0, quantidadeUtilizada: 0 })),
    "bg-red-500",
  );
});

// ---- linhas 325-329: {#if !c.ativo} inativa {:else if saldo <= 0} esgotada

test("situacaoDaCota: inativa prevalece mesmo com saldo zerado; esgotada inclui saldo negativo", () => {
  assert.equal(
    situacaoDaCota(cota(1, { ativo: false, saldoDisponivel: 0 })),
    "inativa",
  );
  assert.equal(
    situacaoDaCota(cota(1, { ativo: false, saldoDisponivel: 5 })),
    "inativa",
  );
  assert.equal(
    situacaoDaCota(cota(1, { ativo: true, saldoDisponivel: 0 })),
    "esgotada",
  );
  assert.equal(
    situacaoDaCota(cota(1, { ativo: true, saldoDisponivel: -2 })),
    "esgotada",
  );
  assert.equal(
    situacaoDaCota(cota(1, { ativo: true, saldoDisponivel: 1 })),
    "disponivel",
  );
});

// ---- linhas 132-148: ?grupo=

test("lerGrupoDaUrl: ausente é null; valor numérico vira número; lixo vira NaN", () => {
  assert.equal(lerGrupoDaUrl(new URLSearchParams("")), null);
  assert.equal(lerGrupoDaUrl(new URLSearchParams("grupo=5")), 5);
  assert.ok(Number.isNaN(lerGrupoDaUrl(new URLSearchParams("grupo=abc"))));
  assert.equal(lerGrupoDaUrl(new URLSearchParams("grupo=0")), 0);
});

test("filtrarPorGrupo: igualdade exata; não inclui cota geral nem de especialidade", () => {
  const cotas = [
    cota(1, { grupoEspecialidadesId: 5 }),
    cota(2, { grupoEspecialidadesId: 6 }),
    cota(3), // geral
    cota(4, { especialidadeNome: "Cardio" }),
  ];
  assert.deepEqual(ids(filtrarPorGrupo(cotas, 5)), [1]);
});

test("filtrarPorGrupo: null, NaN e 0 não filtram e devolvem a mesma lista", () => {
  const cotas = [cota(1, { grupoEspecialidadesId: 5 }), cota(2)];
  assert.equal(filtrarPorGrupo(cotas, null), cotas);
  assert.equal(filtrarPorGrupo(cotas, Number.NaN), cotas);
  assert.equal(filtrarPorGrupo(cotas, 0), cotas);
});

test("filtrarPorGrupo: grupo que ninguém tem devolve lista vazia", () => {
  assert.deepEqual(
    filtrarPorGrupo([cota(1, { grupoEspecialidadesId: 5 })], 99),
    [],
  );
});

test("nomeDoGrupoFiltrado: cadastro, depois a cota, depois #id; sem filtro é null", () => {
  const grupos = [{ id: 5, nome: "Laboratório" }];
  const cotas = [
    cota(1, { grupoEspecialidadesId: 7, grupoEspecialidadesNome: "Imagem" }),
  ];
  assert.equal(nomeDoGrupoFiltrado(5, grupos, cotas), "Laboratório");
  assert.equal(nomeDoGrupoFiltrado(7, grupos, cotas), "Imagem");
  assert.equal(nomeDoGrupoFiltrado(9, grupos, cotas), "#9");
  assert.equal(nomeDoGrupoFiltrado(null, grupos, cotas), null);
  assert.equal(nomeDoGrupoFiltrado(0, grupos, cotas), null);
});

// ---- linhas 150-160: ordenação

test("ordenarCotas proximidade: mais antiga primeiro; mensal conta como dia 01; sem data vai ao fim", () => {
  const cotas = [
    cota(1, { tipoPeriodo: "DATA", dataEspecifica: "2026-10-05" }),
    cota(2, { tipoPeriodo: "MENSAL", periodo: "2026-09" }),
    cota(3, { tipoPeriodo: "DATA", dataEspecifica: null }),
    cota(4, { tipoPeriodo: "MENSAL", periodo: "2026-10" }),
  ];
  // 2026-09-01 < 2026-10-01 < 2026-10-05 < sem data (9999-99-99)
  assert.deepEqual(ids(ordenarCotas(cotas, "proximidade")), [2, 4, 1, 3]);
});

test("ordenarCotas limite: maior limite primeiro", () => {
  const cotas = [
    cota(1, { quantidadeTotal: 5 }),
    cota(2, { quantidadeTotal: 50 }),
    cota(3, { quantidadeTotal: 20 }),
  ];
  assert.deepEqual(ids(ordenarCotas(cotas, "limite")), [2, 3, 1]);
});

test("ordenarCotas saldo: maior saldo primeiro; negativo vai ao fim", () => {
  const cotas = [
    cota(1, { saldoDisponivel: 0 }),
    cota(2, { saldoDisponivel: 9 }),
    cota(3, { saldoDisponivel: -1 }),
    cota(4, { saldoDisponivel: 3 }),
  ];
  assert.deepEqual(ids(ordenarCotas(cotas, "saldo")), [2, 4, 1, 3]);
});

test("ordenarCotas: desempate pelo nome da cota e estabilidade entre nomes iguais", () => {
  const cotas = [
    cota(1, { especialidadeNome: "Ortopedia", quantidadeTotal: 10 }),
    cota(2, { especialidadeNome: "Cardiologia", quantidadeTotal: 10 }),
    cota(3, { especialidadeNome: "Cardiologia", quantidadeTotal: 10 }),
  ];
  // empate em limite; Cardiologia (2, 3 na ordem original) antes de Ortopedia
  assert.deepEqual(ids(ordenarCotas(cotas, "limite")), [2, 3, 1]);
});

test("ordenarCotas: ordenação desconhecida cai em proximidade; não altera a entrada", () => {
  const cotas = [
    cota(1, { periodo: "2026-10" }),
    cota(2, { periodo: "2026-09" }),
  ];
  const copia = [...cotas];
  assert.deepEqual(ids(ordenarCotas(cotas, "qualquer")), [2, 1]);
  assert.deepEqual(cotas, copia);
});

// ---- linhas 162-170: paginação

test("paginar: 20 por página por padrão e total de páginas arredondado para cima", () => {
  const lista = Array.from({ length: 45 }, (_, i) => cota(i + 1));
  assert.equal(ITENS_POR_PAGINA_PADRAO, 20);
  const p1 = paginar(lista, 1);
  assert.equal(p1.totalPaginas, 3);
  assert.equal(p1.itens.length, 20);
  assert.deepEqual(ids(paginar(lista, 3).itens), [41, 42, 43, 44, 45]);
});

test("paginar: página acima do total é corrigida para a última; lista vazia é 1 página", () => {
  const lista = Array.from({ length: 25 }, (_, i) => cota(i + 1));
  const r = paginar(lista, 99);
  assert.equal(r.paginaAtual, 2);
  assert.equal(r.itens.length, 5);
  const vazia = paginar([], 1);
  assert.equal(vazia.totalPaginas, 1);
  assert.equal(vazia.paginaAtual, 1);
  assert.deepEqual(vazia.itens, []);
});

test("paginar: tamanho exato de uma página não cria página extra", () => {
  const lista = Array.from({ length: 20 }, (_, i) => cota(i + 1));
  assert.equal(paginar(lista, 1).totalPaginas, 1);
});

// ---- Regra comum com o dashboard

test("esgotada da tela é a mesma partição do dashboard (ativo e saldo <= 0)", () => {
  // dashboard/unidade/+page.svelte:105 → c.ativo && c.saldoDisponivel <= 0
  const regraDoDashboard = (c) => c.ativo && c.saldoDisponivel <= 0;
  const amostras = [
    cota(1, { ativo: true, saldoDisponivel: 0 }),
    cota(2, { ativo: true, saldoDisponivel: -3 }),
    cota(3, { ativo: true, saldoDisponivel: 4 }),
    cota(4, { ativo: false, saldoDisponivel: 0 }),
    cota(5, { ativo: false, saldoDisponivel: 7 }),
  ];
  for (const c of amostras) {
    assert.equal(
      situacaoDaCota(c) === "esgotada",
      regraDoDashboard(c),
      `cota ${c.id}`,
    );
  }
});

// ---- Teste diferencial: o código ORIGINAL da página (colado do HEAD) contra a
// versão extraída, sobre dados pseudo-aleatórios com semente fixa.
import { chaveOrdenacaoCota } from "./cotas.js";

function ordenarOriginal(cotasFiltradas, ordenacao) {
  const nomeDaCotaOriginal = (c) =>
    c.especialidadeNome ?? c.grupoEspecialidadesNome ?? "";
  // linhas 150-160 de routes/unidade/cotas/+page.svelte (HEAD)
  return [...cotasFiltradas].sort((a, b) => {
    if (ordenacao === "limite") {
      if (b.quantidadeTotal !== a.quantidadeTotal)
        return b.quantidadeTotal - a.quantidadeTotal;
    } else if (ordenacao === "saldo") {
      if (b.saldoDisponivel !== a.saldoDisponivel)
        return b.saldoDisponivel - a.saldoDisponivel;
    } else {
      const ka = chaveOrdenacaoCota(a),
        kb = chaveOrdenacaoCota(b);
      if (ka !== kb) return ka.localeCompare(kb);
    }
    return nomeDaCotaOriginal(a).localeCompare(nomeDaCotaOriginal(b));
  });
}

function paginarOriginal(cotasOrdenadas, paginaPedida, itensPorPagina) {
  // linhas 162-170 (HEAD)
  const totalPaginas = Math.max(
    1,
    Math.ceil(cotasOrdenadas.length / itensPorPagina),
  );
  let paginaAtual = paginaPedida;
  if (paginaAtual > totalPaginas) paginaAtual = totalPaginas;
  const cotasPaginadas = cotasOrdenadas.slice(
    (paginaAtual - 1) * itensPorPagina,
    paginaAtual * itensPorPagina,
  );
  return { totalPaginas, paginaAtual, cotasPaginadas };
}

function gerador(semente) {
  let s = semente;
  return () => {
    s = (s * 1664525 + 1013904223) % 4294967296;
    return s / 4294967296;
  };
}

function cotaAleatoria(id, r) {
  const nomes = ["Cardiologia", "Ortopedia", "Pediatria", null, "Ginecologia"];
  const total = Math.floor(r() * 30);
  const usado = Math.floor(r() * 35);
  const tipoData = r() < 0.5;
  const mes = String(1 + Math.floor(r() * 12)).padStart(2, "0");
  const dia = String(1 + Math.floor(r() * 28)).padStart(2, "0");
  return {
    id,
    tipoPeriodo: tipoData ? "DATA" : "MENSAL",
    periodo: tipoData ? null : `2026-${mes}`,
    dataEspecifica: tipoData ? (r() < 0.1 ? null : `2026-${mes}-${dia}`) : null,
    especialidadeNome: nomes[Math.floor(r() * nomes.length)],
    grupoEspecialidadesNome: r() < 0.2 ? "Laboratório" : null,
    quantidadeTotal: total,
    quantidadeUtilizada: usado,
    saldoDisponivel: total - usado,
    ativo: r() < 0.8,
  };
}

test("diferencial: ordenarCotas extraída é idêntica à ordenação original em 300 listas", () => {
  const r = gerador(42);
  for (let rodada = 0; rodada < 300; rodada++) {
    const n = Math.floor(r() * 60);
    const lista = Array.from({ length: n }, (_, i) => cotaAleatoria(i + 1, r));
    for (const ordenacao of ["proximidade", "limite", "saldo", "outra"]) {
      assert.deepEqual(
        ids(ordenarCotas(lista, ordenacao)),
        ids(ordenarOriginal(lista, ordenacao)),
        `rodada ${rodada} ordenacao ${ordenacao}`,
      );
    }
  }
});

test("diferencial: paginar extraída é idêntica à paginação original", () => {
  const r = gerador(7);
  for (let rodada = 0; rodada < 300; rodada++) {
    const n = Math.floor(r() * 90);
    const lista = Array.from({ length: n }, (_, i) => cota(i + 1));
    const pagina = 1 + Math.floor(r() * 8);
    const orig = paginarOriginal(lista, pagina, 20);
    const nova = paginar(lista, pagina, 20);
    assert.equal(nova.totalPaginas, orig.totalPaginas);
    assert.equal(nova.paginaAtual, orig.paginaAtual);
    assert.deepEqual(ids(nova.itens), ids(orig.cotasPaginadas));
  }
});

test("totalDePaginas: arredonda para cima e nunca é menor que 1", () => {
  assert.equal(totalDePaginas(0), 1);
  assert.equal(totalDePaginas(1), 1);
  assert.equal(totalDePaginas(20), 1);
  assert.equal(totalDePaginas(21), 2);
  assert.equal(totalDePaginas(45, 10), 5);
});

// ===========================================================================
// Entrega 2: filtros, partição, paginação e URL
// ===========================================================================

const f = (o = {}) => ({
  unidadeId: "",
  especialidadeId: "",
  status: "",
  saldo: "",
  origem: "",
  tipo: "",
  profissionalId: "",
  executanteId: "",
  competencia: "",
  dataDe: "",
  dataAte: "",
  q: "",
  ...o,
});

// ---- URL: chaves que não existem nesta tela

test("lerFiltrosDaUnidade: unidadeId, profissionalId, executanteId e origem nunca saem da URL", () => {
  const params = new URLSearchParams(
    "unidadeId=99&profissionalId=3&executanteId=4&origem=agenda&status=ativa&q=sol",
  );
  const filtros = lerFiltrosDaUnidade(params);
  assert.equal(filtros.unidadeId, "");
  assert.equal(filtros.profissionalId, "");
  assert.equal(filtros.executanteId, "");
  assert.equal(filtros.origem, "");
  assert.equal(filtros.status, "ativa");
  assert.equal(filtros.q, "sol");
});

test("serializarEstado: chaves proibidas nunca são escritas, mesmo se vierem no estado", () => {
  const params = serializarEstado({
    filtros: f({ unidadeId: "99", origem: "agenda", status: "ativa" }),
    grupo: null,
    ordenacao: "proximidade",
    pagina: 1,
    tamanho: 20,
  });
  assert.equal(params.has("unidadeId"), false);
  assert.equal(params.has("origem"), false);
  assert.equal(params.get("status"), "ativa");
});

test("lerEstadoDaUrl: URL vazia é o estado padrão", () => {
  const e = lerEstadoDaUrl(new URLSearchParams(""));
  assert.equal(e.grupo, null);
  assert.equal(e.ordenacao, "proximidade");
  assert.equal(e.pagina, 1);
  assert.equal(e.tamanho, 20);
  assert.equal(haFiltrosDaUnidade(e.filtros, e.grupo), false);
});

test("lerEstadoDaUrl: pagina, tamanho e ordem inválidos caem no padrão", () => {
  const e = lerEstadoDaUrl(
    new URLSearchParams("pagina=0&tamanho=33&ordem=aleatoria"),
  );
  assert.equal(e.pagina, 1);
  assert.equal(e.tamanho, 20);
  assert.equal(e.ordenacao, "proximidade");
  const e2 = lerEstadoDaUrl(new URLSearchParams("pagina=-4&tamanho=abc"));
  assert.equal(e2.pagina, 1);
  assert.equal(e2.tamanho, 20);
  const e3 = lerEstadoDaUrl(new URLSearchParams("pagina=2.5"));
  assert.equal(e3.pagina, 1);
});

test("lerEstadoDaUrl: valores válidos são aceitos", () => {
  const e = lerEstadoDaUrl(
    new URLSearchParams("pagina=3&tamanho=50&ordem=saldo&grupo=7"),
  );
  assert.equal(e.pagina, 3);
  assert.equal(e.tamanho, 50);
  assert.equal(e.ordenacao, "saldo");
  assert.equal(e.grupo, 7);
  assert.deepEqual([...TAMANHOS_DE_PAGINA], [10, 20, 50]);
});

test("?grupo= continua igual: ausente é null, lixo é NaN e não conta como filtro", () => {
  assert.equal(lerEstadoDaUrl(new URLSearchParams("")).grupo, null);
  const lixo = lerEstadoDaUrl(new URLSearchParams("grupo=abc"));
  assert.ok(Number.isNaN(lixo.grupo));
  assert.equal(haFiltrosDaUnidade(lixo.filtros, lixo.grupo), false);
  const cotas = [cota(1, { grupoEspecialidadesId: 5 }), cota(2)];
  assert.deepEqual(
    ids(filtrarCotasDaUnidade(cotas, lixo.filtros, lixo.grupo)),
    [1, 2],
  );
});

test("serializarEstado e lerEstadoDaUrl fazem ida e volta; estado padrão gera URL vazia", () => {
  const estado = {
    filtros: f({
      status: "ativa",
      saldo: "esgotada",
      competencia: "2026-09",
      q: "sol",
    }),
    grupo: 7,
    ordenacao: "limite",
    pagina: 4,
    tamanho: 10,
  };
  const params = serializarEstado(estado);
  assert.deepEqual(
    lerEstadoDaUrl(new URLSearchParams(params.toString())),
    estado,
  );
  const vazio = serializarEstado({
    filtros: f(),
    grupo: null,
    ordenacao: "proximidade",
    pagina: 1,
    tamanho: 20,
  });
  assert.equal(vazio.toString(), "");
});

// ---- Filtros aplicados à lista da unidade

test("filtrarCotasDaUnidade: sem filtro devolve a mesma lista; ?grupo= e filtros combinam com E", () => {
  const cotas = [
    cota(1, { grupoEspecialidadesId: 5, ativo: true, saldoDisponivel: 0 }),
    cota(2, { grupoEspecialidadesId: 5, ativo: true, saldoDisponivel: 3 }),
    cota(3, { grupoEspecialidadesId: 6, ativo: true, saldoDisponivel: 0 }),
  ];
  assert.equal(filtrarCotasDaUnidade(cotas, f(), null), cotas);
  assert.deepEqual(
    ids(filtrarCotasDaUnidade(cotas, f({ saldo: "esgotada" }), 5)),
    [1],
  );
});

test("filtrarCotasDaUnidade: unidadeId vindo no filtro é ignorado (nunca troca de unidade)", () => {
  const cotas = [cota(1, { unidadeId: 1 }), cota(2, { unidadeId: 2 })];
  const r = filtrarCotasDaUnidade(cotas, f({ unidadeId: "2" }), null);
  assert.deepEqual(ids(r), [1, 2]);
});

test("filtrarCotasDaUnidade: grupos não carregados mantêm as cotas de grupo ao filtrar por especialidade", () => {
  const cotas = [
    cota(1, { especialidadeId: 20 }),
    cota(2, { grupoEspecialidadesId: 50 }),
    cota(3),
  ];
  const r = filtrarCotasDaUnidade(cotas, f({ especialidadeId: "10" }), null, {
    grupos: [],
    gruposCarregados: false,
  });
  assert.deepEqual(ids(r), [2, 3]);
});

test("haFiltrosDaUnidade: busca só com espaços não conta; ?grupo= conta", () => {
  assert.equal(haFiltrosDaUnidade(f({ q: "   " }), null), false);
  assert.equal(haFiltrosDaUnidade(f({ status: "ativa" }), null), true);
  assert.equal(haFiltrosDaUnidade(f(), 7), true);
});

// ---- Partição esgotada/inativa e ordenação

const mistura = [
  cota(1, { ativo: false, saldoDisponivel: 4, periodo: "2026-09" }),
  cota(2, { ativo: true, saldoDisponivel: 0, periodo: "2026-08" }),
  cota(3, { ativo: true, saldoDisponivel: 5, periodo: "2026-10" }),
  cota(4, { ativo: false, saldoDisponivel: 0, periodo: "2026-07" }),
  cota(5, { ativo: true, saldoDisponivel: 2, periodo: "2026-06" }),
  cota(6, { ativo: true, saldoDisponivel: -1, periodo: "2026-11" }),
];

test("ordenarComParticao: disponíveis, depois esgotadas, depois inativas; ordenação preservada dentro de cada grupo", () => {
  // proximidade (mais antiga primeiro) dentro de cada grupo
  assert.deepEqual(
    ids(ordenarComParticao(mistura, "proximidade")),
    [5, 3, 2, 6, 4, 1],
  );
});

test("ordenarComParticao: com 'saldo' a ordenação escolhida vale dentro de cada grupo", () => {
  // disponíveis: 3 (5) e 5 (2); esgotadas: 2 (0) e 6 (-1); inativas: 1 (4) e 4 (0)
  assert.deepEqual(
    ids(ordenarComParticao(mistura, "saldo")),
    [3, 5, 2, 6, 1, 4],
  );
});

test("ordenarComParticao: não perde nem duplica cota e não altera a entrada", () => {
  const copia = [...mistura];
  const r = ordenarComParticao(mistura, "limite");
  assert.equal(r.length, mistura.length);
  assert.deepEqual([...ids(r)].sort(), [...ids(mistura)].sort());
  assert.deepEqual(mistura, copia);
});

test("contarParticao: disponíveis + esgotadas + inativas = total", () => {
  const c = contarParticao(mistura, f(), null);
  assert.deepEqual(c, { total: 6, disponiveis: 2, esgotadas: 2, inativas: 2 });
  assert.equal(c.disponiveis + c.esgotadas + c.inativas, c.total);
});

test("contarParticao: ignora status e saldo do filtro, mas respeita os outros filtros", () => {
  const com = mistura.map((c) => ({ ...c, tipoPeriodo: "MENSAL" }));
  com[0] = { ...com[0], tipoPeriodo: "DATA", dataEspecifica: "2026-10-05" };
  const c = contarParticao(com, f({ status: "inativa", tipo: "mensal" }), null);
  // status do filtro é ignorado; tipo=mensal tira a cota 1 (DATA)
  assert.equal(c.total, 5);
  assert.equal(c.inativas, 1);
});

test("filtrosDoCartao e cartaoAtivo: um só estado alimenta cartões e selects", () => {
  assert.deepEqual(filtrosDoCartao("disponiveis"), {
    status: "ativa",
    saldo: "com-saldo",
  });
  assert.deepEqual(filtrosDoCartao("esgotadas"), {
    status: "ativa",
    saldo: "esgotada",
  });
  assert.deepEqual(filtrosDoCartao("inativas"), {
    status: "inativa",
    saldo: "",
  });
  assert.deepEqual(filtrosDoCartao("nenhum"), { status: "", saldo: "" });
  assert.equal(cartaoAtivo(f(filtrosDoCartao("esgotadas"))), "esgotadas");
  assert.equal(cartaoAtivo(f(filtrosDoCartao("inativas"))), "inativas");
  assert.equal(cartaoAtivo(f()), null);
  // saldo=esgotada sozinho (da URL) não marca cartão nenhum
  assert.equal(cartaoAtivo(f({ saldo: "esgotada" })), null);
});

test("clicar num cartão e filtrar devolve exatamente a contagem do cartão", () => {
  const esperado = {
    disponiveis: "disponivel",
    esgotadas: "esgotada",
    inativas: "inativa",
  };
  for (const cartao of ["disponiveis", "esgotadas", "inativas"]) {
    const filtros = f(filtrosDoCartao(cartao));
    const lista = filtrarCotasDaUnidade(mistura, filtros, null);
    assert.equal(
      lista.length,
      contarParticao(mistura, f(), null)[cartao],
      cartao,
    );
    for (const c of lista) {
      assert.equal(
        situacaoDaCota(c),
        esperado[cartao],
        `${cartao} cota ${c.id}`,
      );
    }
  }
});

// ---- Opções e páginas

test("opcoesDeGrupo: nome do cadastro, senão da cota, senão #id; sem repetir; em ordem", () => {
  const cotas = [
    cota(1, { grupoEspecialidadesId: 7, grupoEspecialidadesNome: "Imagem" }),
    cota(2, { grupoEspecialidadesId: 5 }),
    cota(3, { grupoEspecialidadesId: 7, grupoEspecialidadesNome: "Imagem" }),
    cota(4, { grupoEspecialidadesId: 9, grupoEspecialidadesNome: "Zeta" }),
    cota(5),
  ];
  const o = opcoesDeGrupo(cotas, [{ id: 5, nome: "Laboratório" }]);
  assert.deepEqual(
    o.map((g) => g.nome),
    ["Imagem", "Laboratório", "Zeta"],
  );
});

test("opcoesDeEspecialidade: inclui as de dentro dos grupos presentes nas cotas", () => {
  const cotas = [
    cota(1, { especialidadeId: 20, especialidadeNome: "Ortopedia" }),
    cota(2, { grupoEspecialidadesId: 50 }),
  ];
  const grupos = [
    { id: 50, especialidades: [{ id: 10, nome: "Cardiologia" }] },
  ];
  assert.deepEqual(
    opcoesDeEspecialidade(cotas, grupos).map((e) => e.nome),
    ["Cardiologia", "Ortopedia"],
  );
});

test("numerosDePagina: até 7 mostra todas; acima usa reticências e nunca sai de 1..total", () => {
  assert.deepEqual(numerosDePagina(1, 1), [1]);
  assert.deepEqual(numerosDePagina(3, 7), [1, 2, 3, 4, 5, 6, 7]);
  assert.deepEqual(numerosDePagina(1, 12), [1, 2, 3, 4, "…", 12]);
  assert.deepEqual(numerosDePagina(6, 12), [1, "…", 5, 6, 7, "…", 12]);
  assert.deepEqual(numerosDePagina(12, 12), [1, "…", 9, 10, 11, 12]);
  assert.deepEqual(numerosDePagina(4, 12), [1, 2, 3, 4, 5, "…", 12]);
  for (let total = 1; total <= 40; total++) {
    for (let atual = 1; atual <= total; atual++) {
      const r = numerosDePagina(atual, total);
      const nums = r.filter((x) => x !== "…");
      assert.ok(nums.every((n) => Number.isInteger(n) && n >= 1 && n <= total));
      assert.ok(
        nums.includes(atual) && nums.includes(1) && nums.includes(total),
      );
      assert.deepEqual(
        nums,
        [...nums].sort((a, b) => a - b),
      );
    }
  }
});

test("paginação com tamanho escolhido: troca de 20 para 10 recalcula total e fatia", () => {
  const lista = Array.from({ length: 45 }, (_, i) => cota(i + 1));
  assert.equal(totalDePaginas(lista.length, 10), 5);
  assert.deepEqual(ids(paginar(lista, 5, 10).itens), [41, 42, 43, 44, 45]);
  assert.equal(totalDePaginas(lista.length, 50), 1);
});

test("?grupo=-1 é truthy e deixa a lista vazia, como o código original", () => {
  const cotas = [cota(1, { grupoEspecialidadesId: 5 }), cota(2)];
  const e = lerEstadoDaUrl(new URLSearchParams("grupo=-1"));
  assert.equal(e.grupo, -1);
  assert.deepEqual(ids(filtrarCotasDaUnidade(cotas, e.filtros, e.grupo)), []);
});

test("lerEstadoDaUrl: tamanho=20.0 vale 20; tamanho=010 vale 10", () => {
  assert.equal(lerEstadoDaUrl(new URLSearchParams("tamanho=20.0")).tamanho, 20);
  assert.equal(lerEstadoDaUrl(new URLSearchParams("tamanho=010")).tamanho, 10);
});
