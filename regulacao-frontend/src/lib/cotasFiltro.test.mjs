// Testes de src/lib/cotasFiltro.js — lógica pura dos filtros de /admin/cotas.
//
// Os testes do filtro de UNIDADE são de caracterização: repetem o que a tela
// fazia inline antes desta mudança (`+page.svelte`, `cotasFiltradas`), para que
// extrair a lógica não altere o que o operador via.
//
// Sem framework (o projeto não tem um configurado):
//   node --test src/lib/cotasFiltro.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  FILTROS_VAZIOS,
  filtrarCotas,
  lerFiltros,
  sanitizarFiltro,
  serializarFiltros,
  haFiltros,
  periodoInvalido,
  temParametrosDeFiltro,
  opcoesDeFiltro,
  tagsAtivas,
} from "./cotasFiltro.js";

function cota(id, o = {}) {
  return {
    id,
    unidadeId: 1,
    grupoUnidadesId: null,
    especialidadeId: null,
    grupoEspecialidadesId: null,
    origem: "MANUAL",
    agendaId: null,
    tipoPeriodo: "MENSAL",
    periodo: "2026-09",
    quantidadeTotal: 10,
    quantidadeUtilizada: 4,
    saldoDisponivel: 6,
    ativo: true,
    ...o,
  };
}

function ids(lista) {
  return lista.map((c) => c.id);
}

const f = (o) => ({ ...FILTROS_VAZIOS, ...o });

// ---- Unidade (caracterização do comportamento anterior)

test("unidade: sem filtro devolve a mesma lista (mesma referência)", () => {
  const cotas = [cota(1), cota(2, { unidadeId: 2 })];
  assert.equal(filtrarCotas(cotas, FILTROS_VAZIOS, {}), cotas);
});

test("unidade: cota própria da unidade", () => {
  const cotas = [cota(1, { unidadeId: 1 }), cota(2, { unidadeId: 2 })];
  const r = filtrarCotas(cotas, f({ unidadeId: "1" }), {
    unidades: [{ id: 1, grupoRelatorioId: null }],
  });
  assert.deepEqual(ids(r), [1]);
});

test("unidade: inclui cota do grupo a que a unidade pertence", () => {
  const cotas = [
    cota(1, { unidadeId: 1 }),
    cota(2, { unidadeId: null, grupoUnidadesId: 7 }),
    cota(3, { unidadeId: null, grupoUnidadesId: 8 }),
    cota(4, { unidadeId: 2 }),
  ];
  const r = filtrarCotas(cotas, f({ unidadeId: "1" }), {
    unidades: [{ id: 1, grupoRelatorioId: 7 }],
  });
  assert.deepEqual(ids(r), [1, 2]);
});

test("unidade: sem grupoRelatorioId só mostra as próprias", () => {
  const cotas = [cota(1), cota(2, { unidadeId: null, grupoUnidadesId: 7 })];
  const r = filtrarCotas(cotas, f({ unidadeId: "1" }), {
    unidades: [{ id: 1 }],
  });
  assert.deepEqual(ids(r), [1]);
});

test("unidade: id que não existe na lista de unidades não casa grupo nenhum", () => {
  const cotas = [
    cota(1, { unidadeId: 1 }),
    cota(2, { unidadeId: null, grupoUnidadesId: 7 }),
  ];
  const r = filtrarCotas(cotas, f({ unidadeId: "99" }), {
    unidades: [{ id: 1, grupoRelatorioId: 7 }],
  });
  assert.deepEqual(ids(r), []);
});

test("unidade: se a lista de unidades não carregou, cotas de grupo continuam visíveis", () => {
  const cotas = [
    cota(1, { unidadeId: 1 }),
    cota(2, { unidadeId: null, grupoUnidadesId: 7 }),
    cota(3, { unidadeId: 2 }),
  ];
  const r = filtrarCotas(cotas, f({ unidadeId: "1" }), {
    unidades: [],
    unidadesCarregadas: false,
  });
  assert.deepEqual(ids(r), [1, 2]);
});

// ---- Status, saldo, origem, tipo

test("status: ativa e inativa", () => {
  const cotas = [cota(1, { ativo: true }), cota(2, { ativo: false })];
  assert.deepEqual(ids(filtrarCotas(cotas, f({ status: "ativa" }))), [1]);
  assert.deepEqual(ids(filtrarCotas(cotas, f({ status: "inativa" }))), [2]);
});

test("saldo: saldo 0 e negativo são esgotada; positivo é com saldo", () => {
  const cotas = [
    cota(1, { saldoDisponivel: 5 }),
    cota(2, { saldoDisponivel: 0 }),
    cota(3, { saldoDisponivel: -1 }),
  ];
  assert.deepEqual(ids(filtrarCotas(cotas, f({ saldo: "com-saldo" }))), [1]);
  assert.deepEqual(ids(filtrarCotas(cotas, f({ saldo: "esgotada" }))), [2, 3]);
});

test("origem: tudo que não é AGENDA conta como manual", () => {
  const cotas = [
    cota(1, { origem: "MANUAL" }),
    cota(2, { origem: "AGENDA", agendaId: 5 }),
    cota(3, { origem: undefined }),
  ];
  assert.deepEqual(ids(filtrarCotas(cotas, f({ origem: "agenda" }))), [2]);
  assert.deepEqual(ids(filtrarCotas(cotas, f({ origem: "manual" }))), [1, 3]);
});

test("tipo: DATA e mensal", () => {
  const cotas = [
    cota(1, { tipoPeriodo: "DATA", dataEspecifica: "2026-10-05" }),
    cota(2),
  ];
  assert.deepEqual(ids(filtrarCotas(cotas, f({ tipo: "data" }))), [1]);
  assert.deepEqual(ids(filtrarCotas(cotas, f({ tipo: "mensal" }))), [2]);
});

// ---- Competência e intervalo

test("competência: mensal e por data casam pelo mês; data ilegível continua visível", () => {
  const cotas = [
    cota(1, { periodo: "2026-09" }),
    cota(2, { periodo: "2026-10" }),
    cota(3, { tipoPeriodo: "DATA", dataEspecifica: "2026-09-15" }),
    cota(4, { tipoPeriodo: "DATA", dataEspecifica: [2026, 9, 30] }),
    cota(5, { tipoPeriodo: "DATA", dataEspecifica: null }),
  ];
  assert.deepEqual(
    ids(filtrarCotas(cotas, f({ competencia: "2026-09" }))),
    [1, 3, 4, 5],
  );
});

test("intervalo: cota por data precisa estar dentro; limites são inclusivos", () => {
  const cotas = [
    cota(1, { tipoPeriodo: "DATA", dataEspecifica: "2026-10-04" }),
    cota(2, { tipoPeriodo: "DATA", dataEspecifica: "2026-10-05" }),
    cota(3, { tipoPeriodo: "DATA", dataEspecifica: "2026-10-12" }),
    cota(4, { tipoPeriodo: "DATA", dataEspecifica: "2026-10-13" }),
  ];
  assert.deepEqual(
    ids(
      filtrarCotas(cotas, f({ dataDe: "2026-10-05", dataAte: "2026-10-12" })),
    ),
    [2, 3],
  );
});

test("intervalo: cota mensal entra se o mês cruza o intervalo", () => {
  const cotas = [
    cota(1, { periodo: "2026-09" }),
    cota(2, { periodo: "2026-10" }),
    cota(3, { periodo: "2026-11" }),
  ];
  assert.deepEqual(
    ids(
      filtrarCotas(cotas, f({ dataDe: "2026-10-20", dataAte: "2026-10-25" })),
    ),
    [2],
  );
  assert.deepEqual(
    ids(filtrarCotas(cotas, f({ dataDe: "2026-10-20" }))),
    [2, 3],
  );
  assert.deepEqual(
    ids(filtrarCotas(cotas, f({ dataAte: "2026-10-01" }))),
    [1, 2],
  );
});

test("intervalo: data inicial maior que a final é ignorada, não zera a lista", () => {
  const cotas = [cota(1), cota(2, { periodo: "2026-12" })];
  const filtros = f({ dataDe: "2026-12-01", dataAte: "2026-01-01" });
  assert.equal(periodoInvalido(filtros), true);
  assert.deepEqual(ids(filtrarCotas(cotas, filtros)), [1, 2]);
});

// ---- Especialidade

const gruposComEspecialidades = [
  {
    id: 50,
    especialidades: [
      { id: 10, nome: "Cardiologia" },
      { id: 11, nome: "Ecocardiograma" },
    ],
  },
  { id: 51, especialidades: [{ id: 20, nome: "Ortopedia" }] },
];

test("especialidade: inclui própria, de grupo que a contém e geral", () => {
  const cotas = [
    cota(1, { especialidadeId: 10 }),
    cota(2, { especialidadeId: 20 }),
    cota(3, { grupoEspecialidadesId: 50 }),
    cota(4, { grupoEspecialidadesId: 51 }),
    cota(5), // geral: sem especialidade e sem grupo
  ];
  const r = filtrarCotas(cotas, f({ especialidadeId: "10" }), {
    grupos: gruposComEspecialidades,
  });
  assert.deepEqual(ids(r), [1, 3, 5]);
});

test("especialidade: grupos não carregados mantêm as cotas de grupo visíveis", () => {
  const cotas = [
    cota(1, { especialidadeId: 20 }),
    cota(2, { grupoEspecialidadesId: 50 }),
    cota(3),
  ];
  const r = filtrarCotas(cotas, f({ especialidadeId: "10" }), {
    grupos: [],
    gruposCarregados: false,
  });
  assert.deepEqual(ids(r), [2, 3]);
});

// ---- Profissional e executante

const agendasPorId = {
  500: {
    id: 500,
    profissionalId: 30,
    profissionalNome: "Dra. Ana",
    estabelecimentoExecutanteId: 70,
    estabelecimentoExecutanteNome: "Clínica Sol",
  },
  501: {
    id: 501,
    profissionalId: 31,
    profissionalNome: "Dr. Bruno",
    estabelecimentoExecutanteId: 71,
    estabelecimentoExecutanteNome: "Hospital Norte",
  },
};

test("profissional: manual pelo campo da cota, agenda pela agenda", () => {
  const cotas = [
    cota(1, { profissionalId: 30 }),
    cota(2, { profissionalId: 31 }),
    cota(3, { origem: "AGENDA", agendaId: 500 }),
    cota(4, { origem: "AGENDA", agendaId: 501 }),
  ];
  const r = filtrarCotas(cotas, f({ profissionalId: "30" }), { agendasPorId });
  assert.deepEqual(ids(r), [1, 3]);
});

test("profissional: agendas não carregadas mantêm as cotas de agenda visíveis", () => {
  const cotas = [
    cota(1, { profissionalId: 30 }),
    cota(2, { profissionalId: 31 }),
    cota(3, { origem: "AGENDA", agendaId: 500 }),
  ];
  const r = filtrarCotas(cotas, f({ profissionalId: "30" }), {
    agendasPorId: {},
    agendasCarregadas: false,
  });
  assert.deepEqual(ids(r), [1, 3]);
});

test("executante: só casa cota de agenda; manual nunca casa", () => {
  const cotas = [
    cota(1, { profissionalId: 30 }),
    cota(2, { origem: "AGENDA", agendaId: 500 }),
    cota(3, { origem: "AGENDA", agendaId: 501 }),
  ];
  const r = filtrarCotas(cotas, f({ executanteId: "70" }), { agendasPorId });
  assert.deepEqual(ids(r), [2]);
});

test("executante: agendas não carregadas mantêm as cotas de agenda visíveis", () => {
  const cotas = [cota(1), cota(2, { origem: "AGENDA", agendaId: 500 })];
  const r = filtrarCotas(cotas, f({ executanteId: "70" }), {
    agendasPorId: {},
    agendasCarregadas: false,
  });
  assert.deepEqual(ids(r), [2]);
});

// ---- Busca textual

test("busca: ignora acento e caixa, e exige todos os termos", () => {
  const cotas = [
    cota(1, { unidadeNome: "UBS São José", especialidadeNome: "Cardiologia" }),
    cota(2, { unidadeNome: "UBS Centro", especialidadeNome: "Cardiologia" }),
    cota(3, { unidadeNome: "UBS São José", especialidadeNome: "Ortopedia" }),
  ];
  assert.deepEqual(ids(filtrarCotas(cotas, f({ q: "sao jose" }))), [1, 3]);
  assert.deepEqual(ids(filtrarCotas(cotas, f({ q: "SÃO JOSÉ cardio" }))), [1]);
  assert.deepEqual(ids(filtrarCotas(cotas, f({ q: "inexistente" }))), []);
});

test("busca: acha cota de agenda pelo nome do executante e do profissional da agenda", () => {
  const cotas = [
    cota(1, { origem: "AGENDA", agendaId: 500, unidadeNome: "UBS Centro" }),
    cota(2, { unidadeNome: "UBS Sul" }),
  ];
  assert.deepEqual(
    ids(filtrarCotas(cotas, f({ q: "clinica sol" }), { agendasPorId })),
    [1],
  );
  assert.deepEqual(
    ids(filtrarCotas(cotas, f({ q: "dra ana" }), { agendasPorId })),
    [1],
  );
});

test("busca: o cache de texto acompanha a troca das agendas carregadas", () => {
  const cotas = [
    cota(1, { origem: "AGENDA", agendaId: 500, unidadeNome: "UBS Centro" }),
  ];
  assert.deepEqual(
    ids(filtrarCotas(cotas, f({ q: "clinica sol" }), { agendasPorId: {} })),
    [],
  );
  assert.deepEqual(
    ids(filtrarCotas(cotas, f({ q: "clinica sol" }), { agendasPorId })),
    [1],
  );
});

// ---- Combinação

test("filtros combinam com E: cada um só pode reduzir o resultado", () => {
  const cotas = [
    cota(1, { ativo: true, saldoDisponivel: 0 }),
    cota(2, { ativo: true, saldoDisponivel: 3 }),
    cota(3, { ativo: false, saldoDisponivel: 0 }),
  ];
  const so = filtrarCotas(cotas, f({ saldo: "esgotada" }));
  const ambos = filtrarCotas(cotas, f({ saldo: "esgotada", status: "ativa" }));
  assert.deepEqual(ids(so), [1, 3]);
  assert.deepEqual(ids(ambos), [1]);
});

test("filtrar não altera a lista de entrada nem a ordem", () => {
  const cotas = [cota(3), cota(1), cota(2)];
  const copia = [...cotas];
  const r = filtrarCotas(cotas, f({ status: "ativa" }));
  assert.deepEqual(cotas, copia);
  assert.deepEqual(ids(r), [3, 1, 2]);
});

// ---- URL

test("lerFiltros: descarta valor fora da lista branca", () => {
  const params = new URLSearchParams(
    "status=ativa&saldo=lixo&unidadeId=abc&especialidadeId=12&competencia=2026-13&dataDe=2026-02-30x&tipo=data",
  );
  const lido = lerFiltros(params);
  assert.equal(lido.status, "ativa");
  assert.equal(lido.saldo, "");
  assert.equal(lido.unidadeId, "");
  assert.equal(lido.especialidadeId, "12");
  assert.equal(lido.competencia, "");
  assert.equal(lido.dataDe, "");
  assert.equal(lido.tipo, "data");
});

test("lerFiltros: limita o tamanho da busca e tolera entrada ausente", () => {
  assert.equal(
    lerFiltros(new URLSearchParams(`q=${"a".repeat(200)}`)).q.length,
    80,
  );
  assert.deepEqual(lerFiltros(null), { ...FILTROS_VAZIOS });
});

test("serializar e ler fazem ida e volta; estado vazio gera URL vazia", () => {
  const filtros = f({
    unidadeId: "3",
    status: "inativa",
    saldo: "esgotada",
    competencia: "2026-09",
    dataDe: "2026-09-01",
    dataAte: "2026-09-30",
    q: "são josé",
  });
  const params = serializarFiltros(filtros);
  assert.deepEqual(lerFiltros(new URLSearchParams(params.toString())), filtros);
  assert.equal(serializarFiltros(FILTROS_VAZIOS).toString(), "");
  assert.equal(haFiltros(FILTROS_VAZIOS), false);
  assert.equal(haFiltros(filtros), true);
  assert.equal(temParametrosDeFiltro(new URLSearchParams("page=2")), false);
  assert.equal(
    temParametrosDeFiltro(new URLSearchParams("status=ativa")),
    true,
  );
});

// ---- Opções e tags

test("opcoesDeFiltro: deriva do que a lista contém, sem repetir, em ordem alfabética", () => {
  const cotas = [
    cota(1, {
      especialidadeId: 20,
      especialidadeNome: "Ortopedia",
      profissionalId: 31,
      profissionalNome: "Dr. Bruno",
    }),
    cota(2, { grupoEspecialidadesId: 50 }),
    cota(3, { origem: "AGENDA", agendaId: 500 }),
    cota(4, { origem: "AGENDA", agendaId: 500 }),
  ];
  const o = opcoesDeFiltro(cotas, gruposComEspecialidades, agendasPorId);
  assert.deepEqual(
    o.especialidades.map((e) => e.nome),
    ["Cardiologia", "Ecocardiograma", "Ortopedia"],
  );
  assert.deepEqual(
    o.profissionais.map((p) => p.nome),
    ["Dr. Bruno", "Dra. Ana"],
  );
  assert.deepEqual(
    o.executantes.map((e) => e.nome),
    ["Clínica Sol"],
  );
});

test("tagsAtivas: um rótulo por filtro; id sem nome conhecido cai em #id", () => {
  const tags = tagsAtivas(
    f({
      unidadeId: "1",
      status: "ativa",
      saldo: "esgotada",
      competencia: "2026-09",
      dataDe: "2026-09-05",
      q: "sol",
    }),
    { unidades: [{ id: 1, nome: "UBS Centro" }] },
  );
  assert.deepEqual(
    tags.map((t) => t.rotulo),
    [
      "Unidade: UBS Centro",
      "Status: Ativa",
      "Saldo: Cota esgotada",
      "Competência: 09/2026",
      "A partir de: 05/09/2026",
      "Busca: sol",
    ],
  );
  assert.deepEqual(
    tags.map((t) => t.chave),
    ["unidadeId", "status", "saldo", "competencia", "dataDe", "q"],
  );
  assert.equal(
    tagsAtivas(f({ unidadeId: "99" }), { unidades: [] })[0].rotulo,
    "Unidade: Unidade #99",
  );
});

test("sanitizarFiltro: valor inválido de um controle vira vazio, válido passa", () => {
  assert.equal(sanitizarFiltro("competencia", "setembro"), "");
  assert.equal(sanitizarFiltro("competencia", "2026-09"), "2026-09");
  assert.equal(sanitizarFiltro("dataDe", "05/10/2026"), "");
  assert.equal(sanitizarFiltro("dataDe", "2026-10-05"), "2026-10-05");
  assert.equal(sanitizarFiltro("status", "lixo"), "");
  assert.equal(sanitizarFiltro("naoExiste", "x"), "");
  assert.equal(sanitizarFiltro("unidadeId", ""), "");
});

test("busca só com espaços não conta como filtro nem gera tag", () => {
  const filtros = f({ q: "   " });
  assert.equal(haFiltros(filtros), false);
  assert.deepEqual(tagsAtivas(filtros), []);
  assert.equal(serializarFiltros(filtros).toString(), "");
  const cotas = [cota(1), cota(2)];
  assert.equal(filtrarCotas(cotas, filtros), cotas);
  assert.equal(tagsAtivas(f({ q: "  sol " }))[0].rotulo, "Busca: sol");
});
