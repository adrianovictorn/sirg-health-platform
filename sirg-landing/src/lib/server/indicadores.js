// Leitura dos indicadores públicos de cada instância do SIRG, sempre no servidor.
// Este módulo não importa nada do SvelteKit de propósito: é testado com node --test.

const TIMEOUT_MS = 3000;
const VALIDADE_SUCESSO_MS = 10 * 60 * 1000;
// Curta, para o cartão de uma instância que voltou ao ar reaparecer logo.
const VALIDADE_FALHA_MS = 60 * 1000;
// Além disto o dado guardado não é mais servido enquanto revalida: o visitante espera a leitura.
const IDADE_MAXIMA_MS = 60 * 60 * 1000;
const MAX_ESPECIALIDADES = 2000;

const INDISPONIVEL = Object.freeze({ disponivel: false });

/** "São Felipe", "sao_felipe" e "SAO FELIPE" são o mesmo município. */
export function normalizarMunicipio(valor) {
	return String(valor ?? '')
		.normalize('NFD')
		.replace(/\p{M}/gu, '')
		.toUpperCase()
		.replace(/[^A-Z0-9]+/g, '_')
		.replace(/^_+|_+$/g, '');
}

function contagem(valor) {
	return Number.isSafeInteger(valor) && valor >= 0 ? valor : null;
}

function instante(valor) {
	if (typeof valor !== 'string') return null;
	const data = new Date(valor);
	return Number.isNaN(data.getTime()) ? null : data.toISOString();
}

function texto(valor, maximo) {
	return typeof valor === 'string' && valor.trim() ? valor.trim().slice(0, maximo) : null;
}

/**
 * Lista fechada de campos: copia um a um, com checagem de tipo, e nunca repassa
 * o JSON da instância. Um campo novo no backend não chega à página por aqui.
 * Devolve null quando a resposta não é o resumo do município esperado.
 */
export function extrairResumo(json, municipioEsperado) {
	if (!json || typeof json !== 'object') return null;
	if (normalizarMunicipio(json.municipio) !== normalizarMunicipio(municipioEsperado)) return null;

	const totais = json.totais && typeof json.totais === 'object' ? json.totais : {};
	const catalogo = json.catalogo && typeof json.catalogo === 'object' ? json.catalogo : {};
	const sistema = json.sistema && typeof json.sistema === 'object' ? json.sistema : {};
	const nomes = Array.isArray(catalogo.nomesEspecialidades) ? catalogo.nomesEspecialidades : [];

	return {
		disponivel: true,
		totais: {
			solicitacoes: contagem(totais.solicitacoes),
			agendamentos: contagem(totais.agendamentos),
			pacientesAtendidos: contagem(totais.pacientesAtendidos),
			viagensTransporte: contagem(totais.viagensTransporte)
		},
		catalogo: {
			unidades: contagem(catalogo.unidades),
			especialidades: contagem(catalogo.especialidades),
			nomesEspecialidades: nomes
				.map((nome) => texto(nome, 120))
				.filter(Boolean)
				.slice(0, MAX_ESPECIALIDADES)
		},
		sistema: {
			versao: texto(sistema.versao, 20),
			deployEm: instante(sistema.deployEm)
		},
		geradoEm: instante(json.geradoEm)
	};
}

/**
 * @param {{ fetch?: typeof fetch, agora?: () => number }} [deps]
 */
export function criarLeitorDeIndicadores({ fetch: buscar = fetch, agora = Date.now } = {}) {
	/** @type {Map<string, { dados: object, lidoEm: number, validoAte: number }>} */
	const cache = new Map();
	/** @type {Map<string, Promise<object>>} */
	const emAndamento = new Map();

	async function consultar(instancia) {
		try {
			const resposta = await buscar(new URL('/api/transparencia/resumo', instancia.urlApi), {
				headers: { accept: 'application/json' },
				redirect: 'error',
				signal: AbortSignal.timeout(TIMEOUT_MS)
			});
			// Instância no ar mas em versão anterior ao endpoint responde 404: também é "indisponível".
			if (resposta.status !== 200) return INDISPONIVEL;
			return extrairResumo(await resposta.json(), instancia.municipioEsperado) ?? INDISPONIVEL;
		} catch {
			return INDISPONIVEL;
		}
	}

	function revalidar(instancia) {
		let leitura = emAndamento.get(instancia.id);
		if (!leitura) {
			leitura = consultar(instancia)
				.then((dados) => {
					const lidoEm = agora();
					const validade = dados.disponivel ? VALIDADE_SUCESSO_MS : VALIDADE_FALHA_MS;
					cache.set(instancia.id, { dados, lidoEm, validoAte: lidoEm + validade });
					return dados;
				})
				.finally(() => emAndamento.delete(instancia.id));
			emAndamento.set(instancia.id, leitura);
		}
		return leitura;
	}

	/** Nunca rejeita: instância fora do ar devolve `{ disponivel: false }`. */
	async function ler(instancia) {
		const guardado = cache.get(instancia.id);
		if (!guardado) return revalidar(instancia);

		const momento = agora();
		if (momento < guardado.validoAte) return guardado.dados;
		// Falha vencida nunca é servida: o próximo visitante espera a leitura (até o timeout)
		// e já vê a instância que voltou, em vez de um "indisponível" de minutos atrás.
		if (!guardado.dados.disponivel) return revalidar(instancia);
		if (momento - guardado.lidoEm > IDADE_MAXIMA_MS) return revalidar(instancia);

		// Vencido há pouco: serve o que tem e atualiza em segundo plano, para o visitante não esperar.
		revalidar(instancia);
		return guardado.dados;
	}

	return { ler };
}
