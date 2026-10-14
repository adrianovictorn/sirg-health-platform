// Chamadas de /api/unidades-executantes (V110): parametrização da unidade
// executante — grupos e especialidades que ela realiza, locais de agendamento
// ligados a ela e operadores (USER) vinculados. Só ADMIN; o backend é quem barra.
import { getApi, patchApiData, putApi } from "$lib/api.js";

/** Lê o corpo de erro padrão do backend (`{ message }`) ou cai na mensagem dada. */
async function exigirOk(res, mensagemPadrao) {
  if (res.ok) return res;
  if (res.status === 403) {
    throw new Error(
      "A parametrização de executantes é restrita ao administrador.",
    );
  }
  const corpo = await res.json().catch(() => null);
  throw new Error(corpo?.message ?? mensagemPadrao);
}

/** Unidades ativas do tipo Executante ou Ambos, com o tamanho da parametrização e se já está configurada. */
export async function listarUnidadesExecutantes() {
  const res = await getApi("unidades-executantes");
  await exigirOk(res, "Não foi possível carregar as unidades executantes.");
  return res.json();
}

/** Parametrização completa: `{ unidadeId, unidadeNome, tipo, configurada, grupos, especialidades, locais, operadores }`. */
export async function carregarUnidadeExecutante(unidadeId) {
  const res = await getApi(`unidades-executantes/${unidadeId}`);
  await exigirOk(res, "Não foi possível carregar a parametrização da unidade.");
  return res.json();
}

/** Usuários ativos com o perfil Usuário Padrão (USER) — candidatos a operador. */
export async function listarOperadoresDisponiveis() {
  const res = await getApi("unidades-executantes/operadores");
  await exigirOk(res, "Não foi possível carregar os operadores.");
  return res.json();
}

/**
 * Substitui a parametrização inteira. Local já ligado a outra executante é movido
 * para esta. Grupo e especialidade podem estar em mais de uma executante (V111).
 *
 * @param {number|string} unidadeId
 * @param {{ grupoIds: number[], especialidadeIds: number[], localIds: number[], usuarioIds: number[] }} dados
 */
export async function salvarUnidadeExecutante(unidadeId, dados) {
  const res = await putApi(`unidades-executantes/${unidadeId}`, dados);
  await exigirOk(res, "Não foi possível salvar a parametrização.");
  return res.json();
}

/** Liga um local de agendamento a uma executante (move se já estiver em outra); `unidadeId` nulo desliga. */
export async function vincularLocalAExecutante(localId, unidadeId) {
  const res = await patchApiData(`unidades-executantes/locais/${localId}`, {
    unidadeId: unidadeId ?? null,
  });
  await exigirOk(res, "Não foi possível vincular o local.");
}
