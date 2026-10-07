// Indicadores gerenciais — parte pura usada por routes/indicadores/PainelGerencial.svelte:
// contas e rótulos que a tela mostra, separados dela para serem testáveis.
//
// As chamadas HTTP ficam em indicadoresApi.js e custosApi.js.

/**
 * Percentual inteiro de `parte` sobre `total`, ou null quando não há total.
 * Null (e não 0) de propósito: "0%" e "não há o que medir" são leituras
 * diferentes, e a tela mostra "—" no segundo caso.
 */
export function percentual(parte, total) {
  const t = Number(total);
  if (!Number.isFinite(t) || t <= 0) return null;
  return Math.round((Number(parte) / t) * 100);
}

/** Texto do percentual, com "—" quando não há o que medir. */
export function textoPercentual(valor) {
  return valor === null || valor === undefined ? '—' : `${valor}%`;
}

/**
 * Utilização do teto financeiro, pela mesma conta do painel de custos
 * (utilizado / liberado, arredondado). Null quando nada foi liberado.
 */
export function percentualDoTeto(teto) {
  return percentual(teto?.valorUtilizado, teto?.valorTotal);
}

/**
 * Situação de um teto para a gestão: 'ACIMA' (passou do liberado — agendamento
 * de administrador ou gestor debita sem ser barrado), 'ESGOTADO' (100%),
 * 'ATENCAO' (80% ou mais) ou 'NORMAL'.
 */
export function situacaoDoTeto(teto) {
  const total = Number(teto?.valorTotal);
  const utilizado = Number(teto?.valorUtilizado);
  // Teto zerado com débito: nada foi liberado e ainda assim houve gasto.
  if (!Number.isFinite(total) || total <= 0) return utilizado > 0 ? 'ACIMA' : 'NORMAL';
  if (utilizado > total) return 'ACIMA';
  if (utilizado === total) return 'ESGOTADO';
  return utilizado / total >= 0.8 ? 'ATENCAO' : 'NORMAL';
}

const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];

/** "2026-10" → "out/26". Devolve o próprio texto se não estiver no formato. */
export function rotuloMes(mes) {
  const partes = /^(\d{4})-(\d{2})$/.exec(mes ?? '');
  if (!partes) return mes ?? '';
  const nome = MESES[Number(partes[2]) - 1];
  return nome ? `${nome}/${partes[1].slice(2)}` : mes;
}

const MOTIVOS = {
  SEM_TELEFONE: 'Paciente sem telefone no cadastro',
  TELEFONE_INVALIDO: 'Telefone inválido ou sem DDD',
  OPT_OUT: 'Paciente pediu para não receber mensagens',
  ENVIO_DESLIGADO: 'Envio desligado pelo administrador',
  NAO_CONFIGURADO: 'WhatsApp não configurado',
  LIMITE_DIARIO: 'Limite diário de mensagens atingido',
  FORA_DA_LISTA_DE_TESTE: 'Fora da lista de números de teste',
  AGENDAMENTO_REMOVIDO: 'Agendamento removido antes do envio',
  SUBSTITUIDO_POR_REMARCACAO: 'Substituída por mensagem de remarcação',
  PACIENTE_DE_OUTRO_MUNICIPIO: 'Paciente de outro município',
  DATA_PASSADA: 'Data do atendimento já passou',
  SEM_MOTIVO: 'Motivo não registrado'
};

/** Motivo de não envio em texto. Código desconhecido aparece como veio, para não esconder nada. */
export function rotuloMotivo(codigo) {
  return MOTIVOS[codigo] ?? codigo ?? '';
}

const PRIORIDADES = { EMERGENCIA: 'Emergência', URGENTE: 'Urgente', NORMAL: 'Normal' };

export function rotuloPrioridade(codigo) {
  if (!codigo) return 'Sem prioridade';
  return PRIORIDADES[codigo] ?? codigo;
}

/** Dias com uma casa ("5,5 dias"), ou "—" quando não há medida. */
export function textoDias(valor) {
  if (valor === null || valor === undefined) return '—';
  const arredondado = Math.round(Number(valor) * 10) / 10;
  return `${arredondado.toLocaleString('pt-BR')} ${arredondado === 1 ? 'dia' : 'dias'}`;
}
