// Chamadas de /api/indicadores (indicadores gerenciais de fila, cota e WhatsApp).
// Só ADMIN e GESTOR têm acesso; o backend é quem barra — ver IndicadoresGerenciaisController.
// Os indicadores com valor em reais ficam em custosApi.js (/api/custos/indicadores).
import { getApi } from '$lib/api.js';

/** Lê o corpo de erro padrão do backend (`{ message }`) ou cai na mensagem dada. */
async function exigirOk(res, mensagemPadrao) {
  if (res.ok) return res;
  const corpo = await res.json().catch(() => null);
  throw new Error(corpo?.message ?? mensagemPadrao);
}

function query(parametros) {
  const params = new URLSearchParams();
  for (const [chave, valor] of Object.entries(parametros)) {
    if (valor !== undefined && valor !== null && valor !== '') {
      params.set(chave, String(valor));
    }
  }
  const texto = params.toString();
  return texto ? `?${texto}` : '';
}

async function ler(caminho, parametros, mensagemPadrao) {
  const res = await getApi(`indicadores/${caminho}${query(parametros)}`);
  await exigirOk(res, mensagemPadrao);
  return res.json();
}

// ---- Fila e operação

/** A fila de hoje por faixa de espera. Não tem período. */
export function carregarEnvelhecimentoFila({ unidadeId } = {}) {
  return ler('fila/envelhecimento', { unidadeId }, 'Não foi possível carregar o envelhecimento da fila.');
}

/** Últimos 12 meses. Não tem período. */
export function carregarBalancoFila({ unidadeId } = {}) {
  return ler('fila/balanco', { unidadeId }, 'Não foi possível carregar o balanço da fila.');
}

export function carregarAntecedencia({ unidadeId, de, ate } = {}) {
  return ler('agendamentos/antecedencia', { unidadeId, de, ate }, 'Não foi possível carregar a antecedência dos agendamentos.');
}

// ---- Cotas

export function carregarUtilizacaoCotas({ unidadeId, de, ate } = {}) {
  return ler('cotas/utilizacao', { unidadeId, de, ate }, 'Não foi possível carregar a utilização das cotas.');
}

export function carregarOcupacaoProfissional({ unidadeId, de, ate } = {}) {
  return ler('cotas/ocupacao-profissional', { unidadeId, de, ate }, 'Não foi possível carregar a ocupação por profissional.');
}

// ---- WhatsApp

export function carregarAlcanceWhatsApp({ unidadeId, de, ate } = {}) {
  return ler('whatsapp/alcance', { unidadeId, de, ate }, 'Não foi possível carregar o alcance do WhatsApp.');
}

export function carregarTelefoneInvalido({ unidadeId, de, ate } = {}) {
  return ler('whatsapp/telefone-invalido', { unidadeId, de, ate }, 'Não foi possível carregar os telefones inválidos.');
}
