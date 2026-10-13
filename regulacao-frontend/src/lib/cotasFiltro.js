/**
 * Filtros da tela /admin/cotas — lógica pura (sem DOM, sem Svelte), para poder
 * ser testada com `node --test` e para não mexer em `cotas.js`, que outras
 * telas importam (agendar, dashboard/unidade, unidade/cotas).
 *
 * Regra geral: cada filtro olha a COTA (a "filha"), nunca a linha-resumo de
 * agenda. A tela filtra antes de agrupar, então o resumo soma só as cotas que
 * sobraram, como já era com o filtro de unidade.
 *
 * Regra de segurança: um filtro nunca esconde cota só porque um dado de apoio
 * (unidades, grupos, agendas) não carregou. Nesse caso a cota continua visível
 * e a tela avisa — o filtro de unidade antigo fazia as cotas de grupo sumirem
 * em silêncio quando `unidades/ativas` falhava.
 */
import { chaveOrdenacaoCota } from "./cotas.js";

// Valor devolvido por `chaveOrdenacaoCota` quando a data não é reconhecida.
const SEM_DATA = "9999-99-99";

export const FILTROS_VAZIOS = Object.freeze({
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
});

export const CHAVES_DE_FILTRO = Object.keys(FILTROS_VAZIOS);

const VALORES_DE_LISTA = {
  status: ["ativa", "inativa"],
  saldo: ["com-saldo", "esgotada"],
  origem: ["manual", "agenda"],
  tipo: ["mensal", "data"],
};

const CHAVES_DE_ID = [
  "unidadeId",
  "especialidadeId",
  "profissionalId",
  "executanteId",
];
const ID = /^\d{1,12}$/;
const COMPETENCIA = /^\d{4}-(0[1-9]|1[0-2])$/;
const DATA = /^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/;
const LIMITE_DA_BUSCA = 80;

function normalizarBusca(texto) {
  return String(texto ?? "")
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .trim();
}

/**
 * Lê os filtros de um `URLSearchParams` (ou de qualquer objeto com `get`).
 * Valor fora da lista branca é descartado: a URL vem de fora (link
 * compartilhado, histórico) e não pode quebrar a tela nem filtrar por lixo.
 */
export function lerFiltros(params) {
  const lido = { ...FILTROS_VAZIOS };
  if (!params || typeof params.get !== "function") return lido;

  for (const chave of Object.keys(VALORES_DE_LISTA)) {
    const v = params.get(chave) ?? "";
    if (VALORES_DE_LISTA[chave].includes(v)) lido[chave] = v;
  }
  for (const chave of CHAVES_DE_ID) {
    const v = params.get(chave) ?? "";
    if (ID.test(v)) lido[chave] = v;
  }
  const competencia = params.get("competencia") ?? "";
  if (COMPETENCIA.test(competencia)) lido.competencia = competencia;
  for (const chave of ["dataDe", "dataAte"]) {
    const v = params.get(chave) ?? "";
    if (DATA.test(v)) lido[chave] = v;
  }
  lido.q = String(params.get("q") ?? "")
    .trim()
    .slice(0, LIMITE_DA_BUSCA);
  return lido;
}

/** Só as chaves fora do padrão: a URL do estado "sem filtro" fica limpa. */
export function serializarFiltros(filtros) {
  const params = new URLSearchParams();
  for (const chave of CHAVES_DE_FILTRO) {
    const v = filtros?.[chave];
    if (v !== undefined && v !== null && String(v).trim() !== "")
      params.set(chave, String(v).trim());
  }
  return params;
}

/**
 * Valida UM valor com as mesmas regras de `lerFiltros` e devolve "" se for
 * inválido. Serve para o que vem de um controle do navegador: `<input
 * type="month">` vira campo de texto no Firefox desktop, e digitar lixo não
 * pode zerar a lista nem gerar uma tag quebrada.
 */
export function sanitizarFiltro(chave, valor) {
  if (!CHAVES_DE_FILTRO.includes(chave)) return "";
  const params = new URLSearchParams();
  if (valor !== undefined && valor !== null) params.set(chave, String(valor));
  return lerFiltros(params)[chave];
}

/** A URL traz algum parâmetro que a tela reconhece como filtro? */
export function temParametrosDeFiltro(params) {
  return CHAVES_DE_FILTRO.some((chave) => (params?.get?.(chave) ?? "") !== "");
}

export function haFiltros(filtros) {
  // Busca só com espaços não é filtro: não mostra tag nem "Limpar filtros".
  return CHAVES_DE_FILTRO.some(
    (chave) => String(filtros?.[chave] ?? "").trim() !== "",
  );
}

/** Data inicial maior que a final: a tela avisa e o intervalo é ignorado. */
export function periodoInvalido(filtros) {
  return !!(
    filtros?.dataDe &&
    filtros?.dataAte &&
    filtros.dataDe > filtros.dataAte
  );
}

// ---- Busca textual

const cacheDeTexto = new WeakMap();

function textoDeBusca(c, agendasPorId) {
  const emCache = cacheDeTexto.get(c);
  if (emCache && emCache.agendas === agendasPorId) return emCache.texto;
  const agenda = c.agendaId != null ? agendasPorId?.[c.agendaId] : null;
  const texto = normalizarBusca(
    [
      c.unidadeNome,
      c.grupoUnidadesNome,
      c.especialidadeNome,
      c.grupoEspecialidadesNome,
      c.profissionalNome,
      c.localAgendamentoNome,
      agenda?.profissionalNome,
      agenda?.estabelecimentoExecutanteNome,
    ]
      .filter(Boolean)
      .join(" "),
  );
  cacheDeTexto.set(c, { agendas: agendasPorId, texto });
  return texto;
}

// ---- Filtro

/**
 * `contexto`: { unidades, grupos, agendasPorId, unidadesCarregadas,
 * gruposCarregados, agendasCarregadas }. Um flag `false` significa "a chamada
 * falhou"; ausente conta como carregado.
 *
 * Sem nenhum filtro devolve a MESMA lista recebida (mesma referência).
 */
export function filtrarCotas(cotas, filtros, contexto = {}) {
  const f = { ...FILTROS_VAZIOS, ...filtros };
  if (!haFiltros(f)) return cotas;

  const unidades = contexto.unidades ?? [];
  const grupos = contexto.grupos ?? [];
  const agendasPorId = contexto.agendasPorId ?? {};
  const unidadesCarregadas = contexto.unidadesCarregadas !== false;
  const gruposCarregados = contexto.gruposCarregados !== false;
  const agendasCarregadas = contexto.agendasCarregadas !== false;
  const predicados = [];

  if (f.unidadeId) {
    // Mostra também as cotas do grupo a que a unidade pertence: elas limitam a
    // unidade tanto quanto as próprias. Regra herdada da tela original.
    const id = Number(f.unidadeId);
    const grupoDaUnidade =
      unidades.find((u) => u.id === id)?.grupoRelatorioId ?? null;
    predicados.push(
      (c) =>
        c.unidadeId === id ||
        (!!grupoDaUnidade && c.grupoUnidadesId === grupoDaUnidade) ||
        (!unidadesCarregadas && c.grupoUnidadesId != null),
    );
  }

  if (f.especialidadeId) {
    // Escolhida a especialidade E, ficam: as cotas dela, as de grupo que a
    // contenham e as cotas gerais (sem especialidade), porque todas incidem
    // juntas sobre E — esconder a geral faria E parecer sem teto.
    const id = Number(f.especialidadeId);
    const especialidadesDoGrupo = new Map(
      grupos.map((g) => [
        g.id,
        new Set((g.especialidades ?? []).map((e) => e.id)),
      ]),
    );
    predicados.push((c) => {
      if (c.especialidadeId === id) return true;
      if (c.grupoEspecialidadesId != null) {
        if (!gruposCarregados) return true;
        return (
          especialidadesDoGrupo.get(c.grupoEspecialidadesId)?.has(id) ?? false
        );
      }
      return c.especialidadeId == null;
    });
  }

  if (f.status) {
    const querAtiva = f.status === "ativa";
    predicados.push((c) => !!c.ativo === querAtiva);
  }

  if (f.saldo) {
    // Saldo PRÓPRIO da cota (quantidadeTotal - quantidadeUtilizada), não o
    // saldo efetivo da unidade, que cruza cotas de escopos diferentes.
    const querEsgotada = f.saldo === "esgotada";
    predicados.push(
      (c) => Number(c.saldoDisponivel ?? 0) <= 0 === querEsgotada,
    );
  }

  if (f.origem) {
    const querAgenda = f.origem === "agenda";
    predicados.push((c) => (c.origem === "AGENDA") === querAgenda);
  }

  if (f.tipo) {
    const querData = f.tipo === "data";
    predicados.push((c) => (c.tipoPeriodo === "DATA") === querData);
  }

  if (f.profissionalId) {
    // Cota de agenda não traz o profissional: ele mora na agenda.
    const id = Number(f.profissionalId);
    predicados.push((c) => {
      if (c.origem === "AGENDA" && c.agendaId != null) {
        if (!agendasCarregadas) return true;
        return (
          (agendasPorId[c.agendaId]?.profissionalId ?? c.profissionalId) === id
        );
      }
      return c.profissionalId === id;
    });
  }

  if (f.executanteId) {
    // O executante só existe na agenda; cota manual nunca casa.
    const id = Number(f.executanteId);
    predicados.push((c) => {
      if (c.origem === "AGENDA" && c.agendaId != null) {
        if (!agendasCarregadas) return true;
        return agendasPorId[c.agendaId]?.estabelecimentoExecutanteId === id;
      }
      return false;
    });
  }

  if (f.competencia) {
    // Cota com data não reconhecida continua visível: não esconder dado legado.
    predicados.push((c) => {
      const chave = chaveOrdenacaoCota(c);
      return chave === SEM_DATA || chave.slice(0, 7) === f.competencia;
    });
  }

  if ((f.dataDe || f.dataAte) && !periodoInvalido(f)) {
    // Cota por data: a data precisa estar no intervalo. Cota mensal cobre o mês
    // inteiro: entra se o mês cruza o intervalo. Só comparação de texto
    // "YYYY-MM-DD", sem `Date`, pelo mesmo motivo de `cotas.js`.
    const mesDe = f.dataDe.slice(0, 7);
    const mesAte = f.dataAte.slice(0, 7);
    predicados.push((c) => {
      const chave = chaveOrdenacaoCota(c);
      if (chave === SEM_DATA) return true;
      if (c.tipoPeriodo === "DATA") {
        if (f.dataDe && chave < f.dataDe) return false;
        if (f.dataAte && chave > f.dataAte) return false;
        return true;
      }
      const mes = chave.slice(0, 7);
      if (f.dataDe && mes < mesDe) return false;
      if (f.dataAte && mes > mesAte) return false;
      return true;
    });
  }

  if (f.q) {
    const termos = normalizarBusca(f.q).split(/\s+/).filter(Boolean);
    if (termos.length > 0) {
      predicados.push((c) => {
        const texto = textoDeBusca(c, agendasPorId);
        return termos.every((t) => texto.includes(t));
      });
    }
  }

  if (predicados.length === 0) return cotas;
  return cotas.filter((c) => predicados.every((p) => p(c)));
}

// ---- Opções dos selects

function porNome(a, b) {
  return a.nome.localeCompare(b.nome, "pt-BR");
}

function adicionar(mapa, id, nome) {
  if (id == null || !nome) return;
  if (!mapa.has(id)) mapa.set(id, { id, nome });
}

/**
 * Opções dos selects, derivadas do que a lista de fato contém — não do cadastro
 * inteiro —, para não oferecer um filtro que sempre devolve zero resultados.
 */
export function opcoesDeFiltro(cotas, grupos = [], agendasPorId = {}) {
  const especialidades = new Map();
  const profissionais = new Map();
  const executantes = new Map();
  const gruposPorId = new Map(grupos.map((g) => [g.id, g]));

  for (const c of cotas) {
    adicionar(especialidades, c.especialidadeId, c.especialidadeNome);
    if (c.grupoEspecialidadesId != null) {
      for (const e of gruposPorId.get(c.grupoEspecialidadesId)
        ?.especialidades ?? []) {
        adicionar(especialidades, e.id, e.nome);
      }
    }
    adicionar(profissionais, c.profissionalId, c.profissionalNome);
    if (c.agendaId != null) {
      const agenda = agendasPorId[c.agendaId];
      adicionar(
        profissionais,
        agenda?.profissionalId,
        agenda?.profissionalNome,
      );
      adicionar(
        executantes,
        agenda?.estabelecimentoExecutanteId,
        agenda?.estabelecimentoExecutanteNome,
      );
    }
  }

  return {
    especialidades: [...especialidades.values()].sort(porNome),
    profissionais: [...profissionais.values()].sort(porNome),
    executantes: [...executantes.values()].sort(porNome),
  };
}

// ---- Tags de filtro ativo

function formatarData(iso) {
  const [ano, mes, dia] = iso.split("-");
  return `${dia}/${mes}/${ano}`;
}

/**
 * Uma tag por filtro ativo: { chave, rotulo }. Remover a tag é zerar `chave`.
 * `opcoes`: { unidades, especialidades, profissionais, executantes }, listas de
 * { id, nome }. Id sem nome conhecido cai em "#id" em vez de sumir.
 */
export function tagsAtivas(filtros, opcoes = {}) {
  const f = { ...FILTROS_VAZIOS, ...filtros };
  const nome = (lista, id, rotulo) =>
    (lista ?? []).find((o) => String(o.id) === String(id))?.nome ??
    `${rotulo} #${id}`;
  const tags = [];

  if (f.unidadeId) {
    tags.push({
      chave: "unidadeId",
      rotulo: `Unidade: ${nome(opcoes.unidades, f.unidadeId, "Unidade")}`,
    });
  }
  if (f.especialidadeId) {
    tags.push({
      chave: "especialidadeId",
      rotulo: `Especialidade: ${nome(opcoes.especialidades, f.especialidadeId, "Especialidade")}`,
    });
  }
  if (f.status) {
    tags.push({
      chave: "status",
      rotulo: `Status: ${f.status === "ativa" ? "Ativa" : "Inativa"}`,
    });
  }
  if (f.saldo) {
    tags.push({
      chave: "saldo",
      rotulo: `Saldo: ${f.saldo === "esgotada" ? "Cota esgotada" : "Com saldo"}`,
    });
  }
  if (f.origem) {
    tags.push({
      chave: "origem",
      rotulo: `Origem: ${f.origem === "agenda" ? "Agenda" : "Manual"}`,
    });
  }
  if (f.tipo) {
    tags.push({
      chave: "tipo",
      rotulo: `Tipo: ${f.tipo === "data" ? "Por data" : "Mensal"}`,
    });
  }
  if (f.profissionalId) {
    tags.push({
      chave: "profissionalId",
      rotulo: `Profissional: ${nome(opcoes.profissionais, f.profissionalId, "Profissional")}`,
    });
  }
  if (f.executanteId) {
    tags.push({
      chave: "executanteId",
      rotulo: `Executante: ${nome(opcoes.executantes, f.executanteId, "Executante")}`,
    });
  }
  if (f.competencia) {
    const [ano, mes] = f.competencia.split("-");
    tags.push({ chave: "competencia", rotulo: `Competência: ${mes}/${ano}` });
  }
  if (f.dataDe)
    tags.push({
      chave: "dataDe",
      rotulo: `A partir de: ${formatarData(f.dataDe)}`,
    });
  if (f.dataAte)
    tags.push({ chave: "dataAte", rotulo: `Até: ${formatarData(f.dataAte)}` });
  if (f.q.trim()) tags.push({ chave: "q", rotulo: `Busca: ${f.q.trim()}` });
  return tags;
}
