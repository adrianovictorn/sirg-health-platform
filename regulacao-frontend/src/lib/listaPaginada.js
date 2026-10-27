/**
 * Paginação das listas administrativas — lógica pura (sem DOM, sem Svelte), para
 * ser testada com `node --test`. Serve /admin/cotas e as telas que adotarem o
 * mesmo componente (`components/ListaPaginada/Paginacao.svelte`).
 *
 * Na URL a página é 1-based (`pagina=2`) e o tamanho é `tamanho=50`; no código a
 * página é 0-based, como no backend. Valores fora da lista branca voltam ao
 * padrão: a URL vem de fora (link compartilhado, histórico).
 */

export const TAMANHOS_DE_PAGINA = Object.freeze([10, 20, 50]);
export const TAMANHO_PADRAO = 20;

/** Lê `pagina`/`tamanho` de um `URLSearchParams`. Retorna { pagina (0-based), tamanho }. */
export function lerPaginacao(params) {
  const lido = { pagina: 0, tamanho: TAMANHO_PADRAO };
  if (!params || typeof params.get !== "function") return lido;

  const pagina = params.get("pagina") ?? "";
  if (/^\d{1,6}$/.test(pagina) && Number(pagina) >= 1) {
    lido.pagina = Number(pagina) - 1;
  }
  const tamanho = Number(params.get("tamanho") ?? "");
  if (TAMANHOS_DE_PAGINA.includes(tamanho)) lido.tamanho = tamanho;
  return lido;
}

/** Só o que foge do padrão: a URL do estado inicial fica limpa. */
export function serializarPaginacao({
  pagina = 0,
  tamanho = TAMANHO_PADRAO,
} = {}) {
  const params = new URLSearchParams();
  if (pagina > 0) params.set("pagina", String(pagina + 1));
  if (tamanho !== TAMANHO_PADRAO) params.set("tamanho", String(tamanho));
  return params;
}

/** A URL traz algum parâmetro de paginação? */
export function temParametrosDePaginacao(params) {
  return ["pagina", "tamanho"].some(
    (chave) => (params?.get?.(chave) ?? "") !== "",
  );
}

/** Une os parâmetros de filtro e de paginação numa query única (`a=1&b=2`). */
export function montarQuery(paramsDeFiltro, paginacao) {
  const params = new URLSearchParams(paramsDeFiltro);
  for (const [chave, valor] of serializarPaginacao(paginacao)) {
    params.set(chave, valor);
  }
  return params.toString();
}

/** Query da API: página 0-based e tamanho sempre explícitos. */
export function montarQueryDaApi(
  paramsDeFiltro,
  { pagina = 0, tamanho = TAMANHO_PADRAO } = {},
) {
  const params = new URLSearchParams(paramsDeFiltro);
  params.set("page", String(pagina));
  params.set("size", String(tamanho));
  return params.toString();
}

/** Intervalo exibido ("1–20"); 0 itens não tem intervalo. */
export function intervaloExibido({ pagina, tamanho, totalItens }) {
  if (!totalItens) return null;
  const inicio = pagina * tamanho + 1;
  const fim = Math.min((pagina + 1) * tamanho, totalItens);
  if (inicio > totalItens) return null;
  return { inicio, fim };
}

function plural(n, singular, pluralTexto) {
  return n === 1 ? singular : pluralTexto;
}

/**
 * Texto do contador de /admin/cotas. A paginação conta UNIDADES (titulares: unidade ou
 * grupo de unidades), cada uma com todas as suas cotas; o total em cotas aparece entre
 * parênteses para não confundir.
 *
 * Sem filtro:  "Exibindo 1–5 de 12 unidades (174 cotas)"
 * Com filtro:  "Exibindo 1–5 de 8 unidades (31 cotas) · filtrado de 12 unidades no total"
 * Sem resultado: "Nenhum resultado" / "Nenhuma cota cadastrada"
 */
export function textoDoContador({
  pagina,
  tamanho,
  totalUnidades,
  totalCotas,
  totalUnidadesGeral,
  filtrado,
}) {
  if (!filtrado && totalUnidadesGeral === 0) return "Nenhuma cota cadastrada";
  if (totalUnidades === 0) return "Nenhum resultado";

  const intervalo = intervaloExibido({
    pagina,
    tamanho,
    totalItens: totalUnidades,
  });
  const unidades = `${totalUnidades} ${plural(totalUnidades, "unidade", "unidades")}`;
  if (!intervalo) return `Página fora do intervalo · ${unidades}`;

  const cotas = `${totalCotas} ${plural(totalCotas, "cota", "cotas")}`;
  const base = `Exibindo ${intervalo.inicio}–${intervalo.fim} de ${unidades} (${cotas})`;
  if (!filtrado) return base;
  return `${base} · filtrado de ${totalUnidadesGeral} ${plural(totalUnidadesGeral, "unidade", "unidades")} no total`;
}

// ---- Filtros simples (unidades, grupos de relatório, profissionais)

const LIMITE_DA_BUSCA = 80;
const ID = /^\d{1,12}$/;

/**
 * Esquema de filtros de uma tela: { chave: "texto" | "id" | ["valor", ...] }.
 * "texto" é a busca livre (até 80 caracteres), "id" um identificador numérico e
 * uma lista é uma lista branca de valores. Valor fora do esquema é descartado:
 * a URL vem de fora (link compartilhado, histórico) e não pode filtrar por lixo.
 */
export function filtrosVazios(esquema) {
  return Object.fromEntries(Object.keys(esquema).map((chave) => [chave, ""]));
}

function sanitizarValor(regra, valor) {
  const texto = String(valor ?? "");
  if (regra === "texto") return texto.trim().slice(0, LIMITE_DA_BUSCA);
  if (regra === "id") return ID.test(texto) ? texto : "";
  return Array.isArray(regra) && regra.includes(texto) ? texto : "";
}

export function lerFiltrosPorEsquema(params, esquema) {
  const lido = filtrosVazios(esquema);
  if (!params || typeof params.get !== "function") return lido;
  for (const [chave, regra] of Object.entries(esquema)) {
    lido[chave] = sanitizarValor(regra, params.get(chave));
  }
  return lido;
}

/** Valida UM valor com as mesmas regras de `lerFiltrosPorEsquema`. */
export function sanitizarFiltroPorEsquema(esquema, chave, valor) {
  return chave in esquema ? sanitizarValor(esquema[chave], valor) : "";
}

/** Só as chaves preenchidas: a URL do estado "sem filtro" fica limpa. */
export function serializarFiltrosPorEsquema(filtros, esquema) {
  const params = new URLSearchParams();
  for (const chave of Object.keys(esquema)) {
    const v = String(filtros?.[chave] ?? "").trim();
    if (v !== "") params.set(chave, v);
  }
  return params;
}

export function haFiltrosPorEsquema(filtros, esquema) {
  return Object.keys(esquema).some(
    (chave) => String(filtros?.[chave] ?? "").trim() !== "",
  );
}

/** A URL traz algum parâmetro que o esquema reconhece como filtro? */
export function temParametrosPorEsquema(params, esquema) {
  return Object.keys(esquema).some(
    (chave) => (params?.get?.(chave) ?? "") !== "",
  );
}

/**
 * Contador das listas sem total geral. "Exibindo 1–20 de 52 unidades" e, com
 * filtro, "… · filtrado" — sem comparar com um total que a tela não tem.
 */
export function textoDoContadorSimples({
  pagina,
  tamanho,
  totalElementos,
  filtrado,
  singular,
  plural: pl,
  vazio = "Nenhum registro",
}) {
  if (totalElementos === 0)
    return filtrado ? "Nenhum resultado" : vazio;
  const intervalo = intervaloExibido({
    pagina,
    tamanho,
    totalItens: totalElementos,
  });
  if (!intervalo)
    return `Página fora do intervalo · ${totalElementos} ${plural(totalElementos, singular, pl)}`;
  const base = `Exibindo ${intervalo.inicio}–${intervalo.fim} de ${totalElementos} ${plural(totalElementos, singular, pl)}`;
  return filtrado ? `${base} · filtrado` : base;
}
