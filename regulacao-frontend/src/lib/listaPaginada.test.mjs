import { test } from "node:test";
import assert from "node:assert/strict";
import {
  lerPaginacao,
  serializarPaginacao,
  temParametrosDePaginacao,
  montarQuery,
  montarQueryDaApi,
  intervaloExibido,
  textoDoContador,
} from "./listaPaginada.js";

const q = (texto) => new URLSearchParams(texto);

test("lerPaginacao: padrão quando a URL não traz nada", () => {
  assert.deepEqual(lerPaginacao(q("")), { pagina: 0, tamanho: 20 });
  assert.deepEqual(lerPaginacao(null), { pagina: 0, tamanho: 20 });
});

test("lerPaginacao: página é 1-based na URL e 0-based no código", () => {
  assert.deepEqual(lerPaginacao(q("pagina=3&tamanho=50")), {
    pagina: 2,
    tamanho: 50,
  });
});

test("lerPaginacao: lixo volta ao padrão", () => {
  assert.deepEqual(lerPaginacao(q("pagina=0&tamanho=7")), {
    pagina: 0,
    tamanho: 20,
  });
  assert.deepEqual(lerPaginacao(q("pagina=-2&tamanho=abc")), {
    pagina: 0,
    tamanho: 20,
  });
  assert.deepEqual(lerPaginacao(q("pagina=1.5")), { pagina: 0, tamanho: 20 });
});

test("serializarPaginacao: estado inicial gera URL limpa", () => {
  assert.equal(serializarPaginacao({ pagina: 0, tamanho: 20 }).toString(), "");
  assert.equal(
    serializarPaginacao({ pagina: 2, tamanho: 10 }).toString(),
    "pagina=3&tamanho=10",
  );
});

test("temParametrosDePaginacao", () => {
  assert.equal(temParametrosDePaginacao(q("pagina=2")), true);
  assert.equal(temParametrosDePaginacao(q("q=abc")), false);
});

test("montarQuery une filtros e paginação", () => {
  assert.equal(
    montarQuery(q("status=ativa"), { pagina: 1, tamanho: 20 }),
    "status=ativa&pagina=2",
  );
  assert.equal(montarQuery(q(""), { pagina: 0, tamanho: 20 }), "");
});

test("montarQueryDaApi sempre manda page e size", () => {
  assert.equal(
    montarQueryDaApi(q("q=a"), { pagina: 0, tamanho: 20 }),
    "q=a&page=0&size=20",
  );
});

test("intervaloExibido", () => {
  assert.deepEqual(
    intervaloExibido({ pagina: 0, tamanho: 20, totalItens: 52 }),
    { inicio: 1, fim: 20 },
  );
  assert.deepEqual(
    intervaloExibido({ pagina: 2, tamanho: 20, totalItens: 52 }),
    { inicio: 41, fim: 52 },
  );
  assert.equal(
    intervaloExibido({ pagina: 0, tamanho: 20, totalItens: 0 }),
    null,
  );
  assert.equal(
    intervaloExibido({ pagina: 9, tamanho: 20, totalItens: 52 }),
    null,
  );
});

test("contador sem filtro mostra unidades e cotas", () => {
  assert.equal(
    textoDoContador({
      pagina: 0,
      tamanho: 5,
      totalUnidades: 12,
      totalCotas: 174,
      totalUnidadesGeral: 12,
      filtrado: false,
    }),
    "Exibindo 1–5 de 12 unidades (174 cotas)",
  );
});

test("contador com filtro explica o total geral", () => {
  assert.equal(
    textoDoContador({
      pagina: 0,
      tamanho: 5,
      totalUnidades: 8,
      totalCotas: 31,
      totalUnidadesGeral: 12,
      filtrado: true,
    }),
    "Exibindo 1–5 de 8 unidades (31 cotas) · filtrado de 12 unidades no total",
  );
});

test("contador no singular", () => {
  assert.equal(
    textoDoContador({
      pagina: 0,
      tamanho: 20,
      totalUnidades: 1,
      totalCotas: 1,
      totalUnidadesGeral: 1,
      filtrado: false,
    }),
    "Exibindo 1–1 de 1 unidade (1 cota)",
  );
});

test("contador da última página", () => {
  assert.equal(
    textoDoContador({
      pagina: 2,
      tamanho: 5,
      totalUnidades: 12,
      totalCotas: 40,
      totalUnidadesGeral: 12,
      filtrado: false,
    }),
    "Exibindo 11–12 de 12 unidades (40 cotas)",
  );
});

test("contador vazio: sem cotas versus sem resultado", () => {
  assert.equal(
    textoDoContador({
      pagina: 0,
      tamanho: 20,
      totalUnidades: 0,
      totalCotas: 0,
      totalUnidadesGeral: 0,
      filtrado: false,
    }),
    "Nenhuma cota cadastrada",
  );
  assert.equal(
    textoDoContador({
      pagina: 0,
      tamanho: 20,
      totalUnidades: 0,
      totalCotas: 0,
      totalUnidadesGeral: 12,
      filtrado: true,
    }),
    "Nenhum resultado",
  );
});

// ---- Filtros por esquema

import {
  filtrosVazios,
  lerFiltrosPorEsquema,
  sanitizarFiltroPorEsquema,
  serializarFiltrosPorEsquema,
  haFiltrosPorEsquema,
  temParametrosPorEsquema,
  textoDoContadorSimples,
} from "./listaPaginada.js";

const ESQUEMA = { q: "texto", status: ["ativa", "inativa"], unidadeId: "id" };

test("filtrosVazios segue as chaves do esquema", () => {
  assert.deepEqual(filtrosVazios(ESQUEMA), {
    q: "",
    status: "",
    unidadeId: "",
  });
});

test("lerFiltrosPorEsquema descarta valor fora do esquema", () => {
  assert.deepEqual(
    lerFiltrosPorEsquema(
      q("q=%20ana%20&status=talvez&unidadeId=12abc"),
      ESQUEMA,
    ),
    {
      q: "ana",
      status: "",
      unidadeId: "",
    },
  );
  assert.deepEqual(
    lerFiltrosPorEsquema(q("status=inativa&unidadeId=7"), ESQUEMA),
    {
      q: "",
      status: "inativa",
      unidadeId: "7",
    },
  );
  assert.equal(
    lerFiltrosPorEsquema(q(`q=${"a".repeat(200)}`), ESQUEMA).q.length,
    80,
  );
});

test("sanitizarFiltroPorEsquema ignora chave desconhecida", () => {
  assert.equal(sanitizarFiltroPorEsquema(ESQUEMA, "status", "ativa"), "ativa");
  assert.equal(sanitizarFiltroPorEsquema(ESQUEMA, "status", "x"), "");
  assert.equal(sanitizarFiltroPorEsquema(ESQUEMA, "cpf", "123"), "");
});

test("serializarFiltrosPorEsquema só leva o preenchido", () => {
  assert.equal(
    serializarFiltrosPorEsquema(
      { q: "", status: "ativa", unidadeId: " " },
      ESQUEMA,
    ).toString(),
    "status=ativa",
  );
});

test("haFiltrosPorEsquema: busca só com espaços não é filtro", () => {
  assert.equal(
    haFiltrosPorEsquema({ q: "   ", status: "", unidadeId: "" }, ESQUEMA),
    false,
  );
  assert.equal(
    haFiltrosPorEsquema({ q: "a", status: "", unidadeId: "" }, ESQUEMA),
    true,
  );
});

test("temParametrosPorEsquema", () => {
  assert.equal(temParametrosPorEsquema(q("status=ativa"), ESQUEMA), true);
  assert.equal(temParametrosPorEsquema(q("pagina=2"), ESQUEMA), false);
});

test("textoDoContadorSimples", () => {
  const base = {
    pagina: 0,
    tamanho: 20,
    singular: "unidade",
    plural: "unidades",
    vazio: "Nenhuma unidade cadastrada",
  };
  assert.equal(
    textoDoContadorSimples({ ...base, totalElementos: 52, filtrado: false }),
    "Exibindo 1–20 de 52 unidades",
  );
  assert.equal(
    textoDoContadorSimples({ ...base, totalElementos: 52, filtrado: true }),
    "Exibindo 1–20 de 52 unidades · filtrado",
  );
  assert.equal(
    textoDoContadorSimples({ ...base, totalElementos: 1, filtrado: false }),
    "Exibindo 1–1 de 1 unidade",
  );
  assert.equal(
    textoDoContadorSimples({ ...base, totalElementos: 0, filtrado: true }),
    "Nenhum resultado",
  );
  assert.equal(
    textoDoContadorSimples({ ...base, totalElementos: 0, filtrado: false }),
    "Nenhuma unidade cadastrada",
  );
});
