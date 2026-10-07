// Chamadas de /api/custos (preço, importação, painel e teto financeiro).
// Só ADMIN e GESTOR têm acesso; o backend é quem barra — ver CustoEspecialidadeController,
// CustoPainelController e TetoFinanceiroController.
import { getApi, postApi, putApi, postApiFile } from '$lib/api.js';

/** Lê o corpo de erro padrão do backend (`{ message }`) ou cai na mensagem dada. */
async function exigirOk(res, mensagemPadrao) {
  if (res.ok) return res;
  const corpo = await res.json().catch(() => null);
  throw new Error(corpo?.message ?? mensagemPadrao);
}

function query(parametros) {
  const params = new URLSearchParams();
  for (const [chave, valor] of Object.entries(parametros)) {
    if (valor !== undefined && valor !== null && valor !== '' && valor !== false) {
      params.set(chave, String(valor));
    }
  }
  const texto = params.toString();
  return texto ? `?${texto}` : '';
}

// ---- Preço e código SUS por especialidade

export async function listarCustosEspecialidades({ nome, grupoRelatorioId, somenteSemPreco, page = 0, size = 50 } = {}) {
  const res = await getApi(`custos/especialidades${query({ nome, grupoRelatorioId, somenteSemPreco, page, size })}`);
  await exigirOk(res, 'Não foi possível carregar os preços das especialidades.');
  return res.json();
}

export async function atualizarCustoEspecialidade(id, { codigoSus, valorUnitario }) {
  const res = await putApi(`custos/especialidades/${id}`, { codigoSus, valorUnitario });
  await exigirOk(res, 'Não foi possível salvar o preço.');
  return res.json();
}

// ---- Importação de planilha (prévia → confirmação)

export async function previaImportacaoCustos(arquivo) {
  const formData = new FormData();
  formData.append('arquivo', arquivo);
  const res = await postApiFile('custos/importacao', formData);
  await exigirOk(res, 'Não foi possível ler a planilha.');
  return res.json();
}

export async function confirmarImportacaoCustos(itens) {
  const res = await postApi('custos/importacao/confirmar', { itens });
  await exigirOk(res, 'Não foi possível gravar a importação.');
  return res.json();
}

// ---- Painel de custos

export async function carregarPainelCustos({ unidadeId, grupoRelatorioId, categoria, dataDe, dataAte } = {}) {
  const res = await getApi(`custos/painel${query({ unidadeId, grupoRelatorioId, categoria, dataDe, dataAte })}`);
  await exigirOk(res, 'Não foi possível carregar o painel de custos.');
  return res.json();
}

// ---- Teto financeiro

export async function listarTetosFinanceiros(periodo) {
  const res = await getApi(`custos/tetos${query({ periodo })}`);
  await exigirOk(res, 'Não foi possível carregar os tetos financeiros.');
  return res.json();
}

export async function criarTetosFinanceiros({ unidadeIds, grupoEspecialidadesId, periodo, valorTotal }) {
  const res = await postApi('custos/tetos', { unidadeIds, grupoEspecialidadesId, periodo, valorTotal });
  await exigirOk(res, 'Não foi possível liberar o teto.');
  return res.json();
}

export async function atualizarTetoFinanceiro(id, { valorTotal, ativo, version }) {
  const res = await putApi(`custos/tetos/${id}`, { valorTotal, ativo, version });
  await exigirOk(res, 'Não foi possível salvar o teto.');
  return res.json();
}

// ---- Indicadores de custo exibidos em /indicadores (somente leitura)

/** Tetos ativos dos meses que o período cobre e a série dos últimos 12 meses. */
export async function carregarExecucaoTeto({ unidadeId, de, ate } = {}) {
  const res = await getApi(`custos/indicadores/teto${query({ unidadeId, de, ate })}`);
  await exigirOk(res, 'Não foi possível carregar a execução do teto financeiro.');
  return res.json();
}

export async function carregarCustoFaltas({ unidadeId, de, ate } = {}) {
  const res = await getApi(`custos/indicadores/faltas${query({ unidadeId, de, ate })}`);
  await exigirOk(res, 'Não foi possível carregar o custo de faltas e cancelamentos.');
  return res.json();
}

export async function carregarCoberturaPreco({ unidadeId, de, ate } = {}) {
  const res = await getApi(`custos/indicadores/cobertura${query({ unidadeId, de, ate })}`);
  await exigirOk(res, 'Não foi possível carregar a cobertura de preço.');
  return res.json();
}

/** Últimos 12 meses. Não tem período. */
export async function carregarEvolucaoCusto({ unidadeId } = {}) {
  const res = await getApi(`custos/indicadores/evolucao${query({ unidadeId })}`);
  await exigirOk(res, 'Não foi possível carregar a evolução do custo.');
  return res.json();
}

// ---- Formatação e leitura de valores em reais

const FORMATO_REAIS = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

/** `null`/`undefined` vira travessão: "sem preço" nunca aparece como R$ 0,00. */
export function formatarReais(valor) {
  if (valor === null || valor === undefined || valor === '') return '—';
  const numero = Number(valor);
  return Number.isFinite(numero) ? FORMATO_REAIS.format(numero) : '—';
}

/**
 * Converte o que o operador digitou ("1.234,56", "R$ 2,01", "17.16") em número.
 * Devolve `null` para campo vazio e `NaN` para texto ilegível — quem chama decide a mensagem.
 */
export function lerValorDigitado(texto) {
  if (texto === null || texto === undefined) return null;
  const limpo = String(texto).replace(/R\$/gi, '').replace(/\s/g, '');
  if (!limpo) return null;
  const numerico = limpo.includes(',') ? limpo.replace(/\./g, '').replace(',', '.') : limpo;
  if (!/^\d+(\.\d{1,2})?$/.test(numerico)) return NaN;
  return Number(numerico);
}

/** Valor do backend (número) no formato que o campo de edição mostra: "2,01". */
export function valorParaCampo(valor) {
  if (valor === null || valor === undefined) return '';
  return Number(valor).toFixed(2).replace('.', ',');
}

/** Mês corrente no formato do backend (YYYY-MM). */
export function mesAtual() {
  const hoje = new Date();
  return `${hoje.getFullYear()}-${String(hoje.getMonth() + 1).padStart(2, '0')}`;
}

/** Primeiro e último dia de um mês YYYY-MM, como datas ISO (YYYY-MM-DD). */
export function limitesDoMes(periodo) {
  const [ano, mes] = periodo.split('-').map(Number);
  const ultimoDia = new Date(ano, mes, 0).getDate();
  return { dataDe: `${periodo}-01`, dataAte: `${periodo}-${String(ultimoDia).padStart(2, '0')}` };
}
