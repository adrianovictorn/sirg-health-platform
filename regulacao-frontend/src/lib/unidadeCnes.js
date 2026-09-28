/**
 * Lógica de merge da busca por CNES no cadastro de Unidade — extraída do
 * modal original (agora dividido em /admin/unidades/nova e
 * /admin/unidades/[id]/editar) para ser compartilhada entre as duas telas e
 * testável sem precisar montar um componente Svelte.
 */

// Campos que a busca por CNES pode preencher. "nome" fica de fora de
// propósito — é o apelido interno da unidade, nunca sobrescrito pela busca.
export const CAMPOS_CNES = [
  "razaoSocial",
  "nomeFantasia",
  "cnpj",
  "endereco",
  "numero",
  "bairro",
  "cep",
  "telefone",
  "email",
];

/**
 * True quando o CNES buscado já pertence a outra unidade (não a que está
 * sendo criada/editada agora) — evita que o operador preencha tudo e só
 * descubra o conflito ao clicar "Salvar".
 */
export function cnesPertenceAOutraUnidade(dados, unidadeIdAtual) {
  return !!(
    dados.jaCadastrado &&
    dados.unidadeId &&
    dados.unidadeId !== unidadeIdAtual
  );
}

/** True quando algum campo já preenchido no form diverge do valor vindo da busca. */
export function divergeDosDadosAtuais(form, dados) {
  return CAMPOS_CNES.some(
    (campo) => dados[campo] && form[campo] && form[campo] !== dados[campo],
  );
}

/**
 * Aplica os campos vindos da busca por CNES sobre o form atual — só os
 * campos que a API retornou (não zera nada que já estava preenchido e a
 * busca não trouxe), e marca importadoDoCnes para o backend carimbar
 * sincronizadoCnesEm.
 */
export function mesclarCamposCnes(form, dados) {
  const atualizado = { ...form };
  for (const campo of CAMPOS_CNES) {
    if (dados[campo]) atualizado[campo] = dados[campo];
  }
  atualizado.importadoDoCnes = true;
  return atualizado;
}
