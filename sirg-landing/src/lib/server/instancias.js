import { env } from '$env/dynamic/private';

// Tudo aqui é configuração privada do servidor: a URL de API de uma instância
// nunca vai para o navegador. A página só recebe `nome`, `uf` e `urlAcesso`.

/**
 * Instâncias do SIRG em produção, na ordem em que aparecem na página.
 * Um município novo entra aqui e no .env — nada mais.
 */
export function instancias() {
	return [
		{
			id: 'sao-felipe',
			nome: 'São Felipe',
			uf: 'BA',
			municipioEsperado: env.SIRG_SAOFELIPE_MUNICIPIO || 'SAO_FELIPE',
			urlApi: env.SIRG_SAOFELIPE_API || 'https://sirgsaofelipe.com',
			urlAcesso: env.SIRG_SAOFELIPE_ACESSO || 'https://sirgsaofelipe.com/login'
		},
		{
			id: 'conceicao-do-almeida',
			nome: 'Conceição do Almeida',
			uf: 'BA',
			municipioEsperado: env.SIRG_CONCEICAO_MUNICIPIO || 'CONCEICAO_DO_ALMEIDA',
			urlApi: env.SIRG_CONCEICAO_API || 'https://sirg.com.br',
			urlAcesso: env.SIRG_CONCEICAO_ACESSO || 'https://sirg.com.br/login'
		}
	];
}
