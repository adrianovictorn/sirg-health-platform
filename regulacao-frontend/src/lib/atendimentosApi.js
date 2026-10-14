// Chamadas de /api/atendimentos (tela do operador da unidade executante).
// Só USER e ADMIN têm acesso; o backend é quem barra — ver AtendimentoExecutanteController.
import { getApi, patchApi, patchApiData } from "$lib/api.js";

/** Lê o corpo de erro padrão do backend (`{ message }`) ou cai na mensagem dada. */
async function exigirOk(res, mensagemPadrao) {
  if (res.ok) return res;
  if (res.status === 403) {
    throw new Error("Esta tela é restrita a quem opera os atendimentos.");
  }
  const corpo = await res.json().catch(() => null);
  throw new Error(corpo?.message ?? mensagemPadrao);
}

function query(parametros) {
  const params = new URLSearchParams();
  for (const [chave, valor] of Object.entries(parametros)) {
    if (
      valor !== undefined &&
      valor !== null &&
      valor !== "" &&
      valor !== false
    ) {
      params.set(chave, String(valor));
    }
  }
  const texto = params.toString();
  return texto ? `?${texto}` : "";
}

/** Quantitativos do dia (no escopo do chamador; `semExecutante` = USER sem executante, ADR 0013) e uma linha por unidade solicitante. `grupo` é o código do Grupo de Relatório (opcional). */
export async function carregarResumoAtendimentos({ data, grupo } = {}) {
  const res = await getApi(`atendimentos/resumo${query({ data, grupo })}`);
  await exigirOk(res, "Não foi possível carregar os quantitativos do dia.");
  return res.json();
}

/**
 * Lista nominal do dia, paginada por PACIENTE: cada elemento é um paciente (ficha)
 * com CPF e CNS completos e a lista `itens` dele no dia e na agenda. `status`
 * escolhe os pacientes que têm algum item naquela situação; o bloco vem inteiro.
 * `semUnidade` filtra as solicitações sem unidade vinculada.
 *
 * @param {{ data?: string, grupo?: string, unidadeId?: string | number | null,
 *   semUnidade?: boolean, status?: string, termo?: string, page?: number, size?: number }} [filtros]
 */
export async function listarAtendimentos({
  data,
  grupo,
  unidadeId,
  semUnidade,
  status,
  termo,
  page = 0,
  size = 20,
} = {}) {
  const res = await getApi(
    `atendimentos${query({ data, grupo, unidadeId, semUnidade, status, termo, page, size })}`,
  );
  await exigirOk(res, "Não foi possível carregar a lista de atendimentos.");
  return res.json();
}

/**
 * Agendas (Grupos de Relatório) da tela, já no escopo de quem está logado: o
 * Usuário Padrão recebe só os grupos das suas executantes (lotação ou lista) e,
 * sem nenhuma, lista vazia (ADR 0013); o ADMIN, os grupos ativos direcionados ao
 * hospital. Lista de `{ codigo, nome }`, ordenada.
 */
export async function carregarAgendasAtendimento() {
  const res = await getApi("atendimentos/agendas");
  await exigirOk(res, "Não foi possível carregar as agendas.");
  return res.json();
}

/** Dias que o Relatório de Atendimento cobre no máximo (o backend recusa mais que isso). */
export const PERIODO_MAXIMO_RELATORIO_DIAS = 31;

/**
 * Relatório de Atendimento em Excel (Confirmado, Faltou, Cancelado e Data da
 * Coleta). Nominal, com CPF e CNS completos. Devolve o Blob da planilha.
 */
export async function baixarRelatorioAtendimentos({ inicio, fim, grupo } = {}) {
  const res = await getApi(
    `atendimentos/relatorio${query({ inicio, fim, grupo })}`,
  );
  await exigirOk(res, "Não foi possível gerar o relatório.");
  return res.blob();
}

// ---- Desfecho do atendimento (só o Usuário Padrão; o backend barra os demais).
// Todas devolvem a linha atualizada, no mesmo formato da lista.

async function acao(requisicao, mensagemPadrao) {
  const res = await requisicao;
  // 400/409/404 trazem a causa em `message`; 403 aqui é perfil sem permissão de registrar.
  if (res.status === 403) {
    throw new Error(
      "Só o operador da unidade executante registra o atendimento.",
    );
  }
  await exigirOk(res, mensagemPadrao);
  return res.json();
}

export function confirmarAtendimento(id) {
  return acao(
    patchApi(`atendimentos/${id}/confirmar`),
    "Não foi possível confirmar o atendimento.",
  );
}

/**
 * Confirma vários itens, um de cada vez, pelo mesmo endpoint da confirmação
 * individual: o efeito é idêntico a confirmar um a um, e uma falha não desfaz os
 * que já gravaram. Para no primeiro 401/403 (sessão ou perfil), que valeria para
 * todos. `aoAvancar(feitos, total)` é chamado depois de cada item.
 *
 * Devolve `{ ok: Atendimento[], falhas: { id, mensagem }[] }`.
 *
 * @param {number[]} ids
 * @param {(feitos: number, total: number) => void} [aoAvancar]
 */
export async function confirmarAtendimentosEmLote(ids, aoAvancar = () => {}) {
  const ok = [];
  const falhas = [];
  for (const [indice, id] of ids.entries()) {
    const res = await patchApi(`atendimentos/${id}/confirmar`);
    if (res.status === 401 || res.status === 403) {
      const mensagem =
        res.status === 403
          ? "Só o operador da unidade executante registra o atendimento."
          : "Sessão expirada. Entre novamente.";
      for (const restante of ids.slice(indice)) {
        falhas.push({ id: restante, mensagem });
      }
      break;
    }
    if (res.ok) {
      ok.push(await res.json());
    } else {
      const corpo = await res.json().catch(() => null);
      falhas.push({
        id,
        mensagem: corpo?.message ?? "Não foi possível confirmar.",
      });
    }
    aoAvancar(indice + 1, ids.length);
  }
  return { ok, falhas };
}

/** `observacao` é opcional. */
export function registrarFaltaAtendimento(id, observacao) {
  return acao(
    patchApiData(`atendimentos/${id}/falta`, { observacao }),
    "Não foi possível registrar a falta.",
  );
}

/** `motivo` é obrigatório; o backend recusa vazio com 400. */
export function cancelarAtendimento(id, motivo) {
  return acao(
    patchApiData(`atendimentos/${id}/cancelar`, { motivo }),
    "Não foi possível cancelar o atendimento.",
  );
}

/** `dataColeta` (YYYY-MM-DD) ou `null` para limpar. Só exame/procedimento. */
export function informarDataColetaAtendimento(id, dataColeta) {
  return acao(
    patchApiData(`atendimentos/${id}/data-coleta`, { dataColeta }),
    "Não foi possível salvar a data da coleta.",
  );
}

export function desfazerAtendimento(id) {
  return acao(
    patchApi(`atendimentos/${id}/desfazer`),
    "Não foi possível desfazer o registro.",
  );
}
// "Confirmado" é o status REALIZADO: o rótulo é do atendimento, o valor é o do banco.
export const ROTULO_STATUS_ATENDIMENTO = {
  AGENDADO: "Agendado",
  REALIZADO: "Confirmado",
  FALTOU: "Faltou",
  CANCELADO: "Cancelado",
  AGUARDANDO: "Aguardando",
  RETORNO: "Retorno",
  RETORNO_POLICLINICA: "Retorno policlínica",
  GEL: "GEL",
};

/** Status oferecidos no filtro da lista, na ordem em que o operador os usa. */
export const STATUS_DO_FILTRO = [
  "AGENDADO",
  "REALIZADO",
  "FALTOU",
  "CANCELADO",
];

export const ROTULO_TURNO = {
  MANHA: "Manhã",
  TARDE: "Tarde",
};
