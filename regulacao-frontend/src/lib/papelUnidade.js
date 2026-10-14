// Papel da unidade (tag nas telas de Unidade, ADR 0012) — SÓ EXIBIÇÃO: nada é
// gravado e o `tipo` da unidade continua sendo escolhido à mão.
//
// A tag reflete o que a unidade faz de fato, não só o tipo declarado:
//   - executante NÃO configurada (qualquer tipo)  -> "Unidade solicitante"
//   - configurada + tipo EXECUTANTE                -> "Unidade executante"
//   - configurada + tipo AMBOS                     -> "Ambas"
// "Configurada" é a definição do ADR 0011 (ativa, tipo EXECUTANTE/AMBOS, ao menos
// um local e ao menos um grupo ou especialidade) e vem pronta do backend.
//
// "Parametrização incompleta" é o aviso para o administrador terminar: unidade
// ativa e não-solicitante, ainda não configurada, com algum vínculo (grupo,
// especialidade, local ou operador). Operador vinculado a executante incompleta
// não vê nada em Atendimentos (ADR 0013) — por isso ele conta.

export const PAPEIS_UNIDADE = {
  SOLICITANTE: { papel: "SOLICITANTE", rotulo: "Unidade solicitante" },
  EXECUTANTE: { papel: "EXECUTANTE", rotulo: "Unidade executante" },
  AMBAS: { papel: "AMBAS", rotulo: "Ambas" },
};

/**
 * @param {{ tipo?: string | null, configurada?: boolean } | null | undefined} resumo
 * @returns {{ papel: string, rotulo: string }}
 */
export function papelDaUnidade(resumo) {
  if (resumo?.configurada) {
    if (resumo.tipo === "EXECUTANTE") return PAPEIS_UNIDADE.EXECUTANTE;
    if (resumo.tipo === "AMBOS") return PAPEIS_UNIDADE.AMBAS;
  }
  // Inclui o dado impossível "configurada com tipo SOLICITANTE" (defesa).
  return PAPEIS_UNIDADE.SOLICITANTE;
}

/**
 * @param {{ ativo?: boolean, tipo?: string | null, configurada?: boolean,
 *   grupos?: number, especialidades?: number, locais?: number, operadores?: number } | null | undefined} resumo
 */
export function parametrizacaoIncompleta(resumo) {
  if (!resumo || resumo.configurada) return false;
  if (!resumo.ativo || resumo.tipo === "SOLICITANTE") return false;
  const vinculos =
    (resumo.grupos ?? 0) +
    (resumo.especialidades ?? 0) +
    (resumo.locais ?? 0) +
    (resumo.operadores ?? 0);
  return vinculos > 0;
}

/**
 * Resumo da unidade a partir da lista de Unidades (`GET /api/unidades`) e do
 * resumo de executantes (`GET /api/unidades-executantes`, que só traz as ativas
 * do tipo EXECUTANTE/AMBOS). Unidade ausente dele não está configurada.
 *
 * @param {{ id: number, ativo: boolean, tipo?: string | null }} unidade
 * @param {Array<{ unidadeId: number, configurada: boolean, grupos: number,
 *   especialidades: number, locais: number, operadores: number }>} executantes
 */
export function resumoDaLista(unidade, executantes) {
  const ex = (executantes ?? []).find((e) => e.unidadeId === unidade.id);
  return {
    ativo: !!unidade.ativo,
    tipo: unidade.tipo ?? null,
    configurada: !!ex?.configurada,
    grupos: ex?.grupos ?? 0,
    especialidades: ex?.especialidades ?? 0,
    locais: ex?.locais ?? 0,
    operadores: ex?.operadores ?? 0,
  };
}

/**
 * Resumo da unidade a partir da parametrização (`GET /api/unidades-executantes/{id}`).
 * `ativo` vem da unidade (o endpoint não o devolve; `configurada` já o considera).
 *
 * @param {{ tipo?: string | null, configurada: boolean, grupos?: unknown[],
 *   especialidades?: unknown[], locais?: unknown[], operadores?: unknown[] }} view
 * @param {boolean} ativo
 */
export function resumoDaParametrizacao(view, ativo) {
  return {
    ativo: !!ativo,
    tipo: view?.tipo ?? null,
    configurada: !!view?.configurada,
    grupos: view?.grupos?.length ?? 0,
    especialidades: view?.especialidades?.length ?? 0,
    locais: view?.locais?.length ?? 0,
    operadores: view?.operadores?.length ?? 0,
  };
}
