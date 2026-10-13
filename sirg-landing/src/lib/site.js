import { env } from '$env/dynamic/public';

export const TITULO = 'SIRG — Sistema Integrado de Regulação e Gestão';
export const DESCRICAO =
	'O SIRG organiza a regulação de consultas e exames especializados do município: solicitação, fila, cota por unidade, agendamento e transporte sanitário em um só sistema. Em produção no interior da Bahia.';

export function urlDoSite() {
	return (env.PUBLIC_SITE_URL || 'http://localhost:5174').replace(/\/+$/, '');
}

/** Só `true` explícito libera a indexação: o padrão protege o rascunho do histórico. */
export function indexavel() {
	return env.PUBLIC_INDEXAVEL === 'true';
}
