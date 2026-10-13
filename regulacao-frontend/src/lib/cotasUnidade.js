/**
 * Lógica pura da tela /unidade/cotas ("Cotas da Unidade"), extraída de
 * `routes/unidade/cotas/+page.svelte` para poder ser testada com `node --test`
 * (a página é `lang="ts"` e o ESLint do projeto não analisa esse tipo de arquivo).
 *
 * Esta primeira versão é cópia LITERAL do que a página fazia inline em `$:`:
 * o comportamento não muda. Os testes de `cotasUnidade.test.mjs` congelam esse
 * comportamento antes de qualquer filtro novo ser acrescentado.
 *
 * Não altera `cotas.js` nem `cotasFiltro.js`, que outras telas importam.
 */
import { chaveOrdenacaoCota } from "./cotas.js";
import {
  FILTROS_VAZIOS,
  filtrarCotas,
  lerFiltros,
  serializarFiltros,
  opcoesDeFiltro,
} from "./cotasFiltro.js";

export const ITENS_POR_PAGINA_PADRAO = 20;

/**
 * Cotas da unidade e cotas do grupo de unidades dela, nessa ordem, sem
 * deduplicar. A unidade também é limitada pela cota do grupo (pool
 * compartilhado), por isso as duas aparecem na mesma lista.
 */
export function juntarCotas(daUnidade, doGrupo) {
  return [...daUnidade, ...doGrupo];
}

export function nomeDaCota(c) {
  return c.especialidadeNome ?? c.grupoEspecialidadesNome ?? "";
}

/** Percentual consumido (0 a 100). Total zero conta como 100. */
export function percentual(c) {
  if (!c.quantidadeTotal) return 100;
  return Math.min(
    100,
    Math.round((c.quantidadeUtilizada / c.quantidadeTotal) * 100),
  );
}

export function corDaBarra(c) {
  const p = percentual(c);
  if (p >= 100) return "bg-red-500";
  if (p >= 80) return "bg-amber-500";
  return "bg-emerald-600";
}

/**
 * Situação da cota como a tela mostra: inativa prevalece sobre esgotada.
 * Esgotada = saldo PRÓPRIO da cota <= 0 (não é o saldo efetivo da unidade,
 * que cruza cotas de escopos diferentes).
 * @returns {"inativa" | "esgotada" | "disponivel"}
 */
export function situacaoDaCota(c) {
  if (!c.ativo) return "inativa";
  if (c.saldoDisponivel <= 0) return "esgotada";
  return "disponivel";
}

/**
 * `?grupo=<grupoEspecialidadesId>` — vem do link "ver todas" do dashboard da
 * unidade. Devolve `null` quando o parâmetro não existe; `NaN`/`0` ficam como
 * estão e `filtrarPorGrupo` os trata como "sem filtro" (são falsy).
 */
export function lerGrupoDaUrl(params) {
  return params.has("grupo") ? Number(params.get("grupo")) : null;
}

/** Igualdade exata: não inclui cota geral nem de especialidade. */
export function filtrarPorGrupo(cotas, grupoFiltroId) {
  return grupoFiltroId
    ? cotas.filter((c) => c.grupoEspecialidadesId === grupoFiltroId)
    : cotas;
}

/** Nome para o chip "Filtrando por grupo": cadastro, depois a própria cota, depois #id. */
export function nomeDoGrupoFiltrado(grupoFiltroId, grupos, cotas) {
  if (!grupoFiltroId) return null;
  return (
    grupos.find((g) => g.id === grupoFiltroId)?.nome ??
    cotas.find((c) => c.grupoEspecialidadesId === grupoFiltroId)
      ?.grupoEspecialidadesNome ??
    `#${grupoFiltroId}`
  );
}

/**
 * Ordenação da tela. `proximidade` ordena por `chaveOrdenacaoCota` ASCENDENTE
 * (do mais antigo para o mais novo; o rótulo "Data mais próxima" é impreciso,
 * mas a regra é essa). `limite` e `saldo` ordenam do maior para o menor.
 * Desempate pelo nome da cota; `Array.sort` é estável. Não altera a entrada.
 */
export function ordenarCotas(cotas, ordenacao) {
  return [...cotas].sort((a, b) => {
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
    return nomeDaCota(a).localeCompare(nomeDaCota(b));
  });
}

/** Total de páginas; lista vazia conta como 1 página. */
export function totalDePaginas(
  totalItens,
  itensPorPagina = ITENS_POR_PAGINA_PADRAO,
) {
  return Math.max(1, Math.ceil(totalItens / itensPorPagina));
}

/**
 * Pagina no navegador. A página pedida é corrigida para o intervalo válido
 * (nunca devolve página inexistente) e lista vazia conta como 1 página.
 */
export function paginar(
  lista,
  pagina,
  itensPorPagina = ITENS_POR_PAGINA_PADRAO,
) {
  const totalPaginas = totalDePaginas(lista.length, itensPorPagina);
  const paginaAtual = pagina > totalPaginas ? totalPaginas : pagina;
  return {
    totalPaginas,
    paginaAtual,
    itens: lista.slice(
      (paginaAtual - 1) * itensPorPagina,
      paginaAtual * itensPorPagina,
    ),
  };
}

// ---------------------------------------------------------------------------
// Filtros, partição, paginação e URL (entrega 2)
// ---------------------------------------------------------------------------

export const TAMANHOS_DE_PAGINA = Object.freeze([10, 20, 50]);
export const ORDENACOES = Object.freeze(["proximidade", "limite", "saldo"]);

// Filtros que existem em `cotasFiltro.js` para a lista do ADMIN e que NÃO se
// aplicam a esta tela. `lerFiltros` os aceita da URL; aqui eles são zerados.
// Em especial `unidadeId`: a unidade vem SEMPRE de /users/me, nunca da URL.
const CHAVES_PROIBIDAS = Object.freeze([
  "unidadeId",
  "profissionalId",
  "executanteId",
  "origem",
]);

/** Filtros desta tela, lidos da URL, sem as chaves proibidas. */
export function lerFiltrosDaUnidade(params) {
  const filtros = lerFiltros(params);
  for (const chave of CHAVES_PROIBIDAS) filtros[chave] = "";
  return filtros;
}

/**
 * Estado completo da tela a partir da URL. Valor inválido cai no padrão e nunca
 * quebra a tela. `grupo` mantém o contrato com o dashboard (`?grupo=<id>`).
 */
export function lerEstadoDaUrl(params) {
  const ordem = params.get("ordem") ?? "";
  const pagina = Number(params.get("pagina"));
  const tamanho = Number(params.get("tamanho"));
  return {
    filtros: lerFiltrosDaUnidade(params),
    grupo: lerGrupoDaUrl(params),
    ordenacao: ORDENACOES.includes(ordem) ? ordem : "proximidade",
    pagina: Number.isInteger(pagina) && pagina >= 1 ? pagina : 1,
    tamanho: TAMANHOS_DE_PAGINA.includes(tamanho)
      ? tamanho
      : ITENS_POR_PAGINA_PADRAO,
  };
}

/** Só o que foge do padrão vai para a URL. */
export function serializarEstado(estado) {
  const filtros = { ...FILTROS_VAZIOS, ...estado.filtros };
  for (const chave of CHAVES_PROIBIDAS) filtros[chave] = "";
  const params = serializarFiltros(filtros);
  if (estado.grupo) params.set("grupo", String(estado.grupo));
  if (estado.ordenacao && estado.ordenacao !== "proximidade")
    params.set("ordem", estado.ordenacao);
  if (estado.pagina > 1) params.set("pagina", String(estado.pagina));
  if (estado.tamanho && estado.tamanho !== ITENS_POR_PAGINA_PADRAO)
    params.set("tamanho", String(estado.tamanho));
  return params;
}

/** Algum filtro (de `cotasFiltro.js` ou `?grupo=`) fora do padrão? */
export function haFiltrosDaUnidade(filtros, grupo) {
  const limpos = { ...filtros };
  for (const chave of CHAVES_PROIBIDAS) limpos[chave] = "";
  return (
    !!grupo || Object.values(limpos).some((v) => String(v ?? "").trim() !== "")
  );
}

/** Aplica `?grupo=` (igualdade exata) e depois os filtros da tela. */
export function filtrarCotasDaUnidade(cotas, filtros, grupo, contexto = {}) {
  const limpos = { ...filtros };
  for (const chave of CHAVES_PROIBIDAS) limpos[chave] = "";
  return filtrarCotas(filtrarPorGrupo(cotas, grupo), limpos, contexto);
}

const PESO_DA_SITUACAO = { disponivel: 0, esgotada: 1, inativa: 2 };

/**
 * Ordenação escolhida e, por cima dela, disponíveis primeiro, depois esgotadas
 * e por último inativas. Estável: dentro de cada grupo vale a ordenação.
 */
export function ordenarComParticao(cotas, ordenacao) {
  return ordenarCotas(cotas, ordenacao).sort(
    (a, b) =>
      PESO_DA_SITUACAO[situacaoDaCota(a)] - PESO_DA_SITUACAO[situacaoDaCota(b)],
  );
}

/**
 * Contadores dos três cartões. Contam sobre a lista filtrada por TUDO, exceto
 * `status` e `saldo`: assim os três somam o total e mostram quanto daria se o
 * cartão fosse clicado. Disponíveis + esgotadas + inativas = total.
 */
export function contarParticao(cotas, filtros, grupo, contexto = {}) {
  const base = filtrarCotasDaUnidade(
    cotas,
    { ...filtros, status: "", saldo: "" },
    grupo,
    contexto,
  );
  const contagem = {
    total: base.length,
    disponiveis: 0,
    esgotadas: 0,
    inativas: 0,
  };
  for (const c of base) {
    const situacao = situacaoDaCota(c);
    if (situacao === "inativa") contagem.inativas++;
    else if (situacao === "esgotada") contagem.esgotadas++;
    else contagem.disponiveis++;
  }
  return contagem;
}

const FILTROS_DOS_CARTOES = {
  disponiveis: { status: "ativa", saldo: "com-saldo" },
  esgotadas: { status: "ativa", saldo: "esgotada" },
  inativas: { status: "inativa", saldo: "" },
};

/** `status` e `saldo` que um cartão aplica ao ser clicado. */
export function filtrosDoCartao(cartao) {
  return FILTROS_DOS_CARTOES[cartao] ?? { status: "", saldo: "" };
}

/** Qual cartão está ativo (ou null), derivado de `status` + `saldo`. */
export function cartaoAtivo(filtros) {
  for (const [cartao, alvo] of Object.entries(FILTROS_DOS_CARTOES)) {
    if (filtros.status === alvo.status && (filtros.saldo ?? "") === alvo.saldo)
      return cartao;
  }
  return null;
}

/**
 * Grupos de especialidades que aparecem nas cotas, para o select. Nome: o do
 * cadastro, senão o da própria cota, senão `#id`.
 */
export function opcoesDeGrupo(cotas, grupos = []) {
  const porId = new Map();
  for (const c of cotas) {
    if (c.grupoEspecialidadesId == null || porId.has(c.grupoEspecialidadesId))
      continue;
    const nome =
      grupos.find((g) => g.id === c.grupoEspecialidadesId)?.nome ??
      c.grupoEspecialidadesNome ??
      `#${c.grupoEspecialidadesId}`;
    porId.set(c.grupoEspecialidadesId, { id: c.grupoEspecialidadesId, nome });
  }
  return [...porId.values()].sort((a, b) =>
    a.nome.localeCompare(b.nome, "pt-BR"),
  );
}

/** Opções do select de especialidade (inclui as de dentro dos grupos). */
export function opcoesDeEspecialidade(cotas, grupos = []) {
  return opcoesDeFiltro(cotas, grupos, {}).especialidades;
}

/**
 * Números de página com reticências: `[1, "…", 4, 5, 6, "…", 12]`. Até 7
 * páginas mostra todas. Nunca devolve número fora de 1..total.
 */
export function numerosDePagina(atual, total) {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);
  const corrente = Math.min(Math.max(atual, 1), total);
  const manter = new Set([1, total, corrente - 1, corrente, corrente + 1]);
  if (corrente <= 3) [2, 3, 4].forEach((n) => manter.add(n));
  if (corrente >= total - 2)
    [total - 3, total - 2, total - 1].forEach((n) => manter.add(n));
  const numeros = [...manter]
    .filter((n) => n >= 1 && n <= total)
    .sort((a, b) => a - b);
  const saida = [];
  for (let i = 0; i < numeros.length; i++) {
    if (i > 0) {
      const falta = numeros[i] - numeros[i - 1];
      if (falta === 2) saida.push(numeros[i] - 1);
      else if (falta > 2) saida.push("…");
    }
    saida.push(numeros[i]);
  }
  return saida;
}
