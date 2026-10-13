import { test } from 'node:test';
import assert from 'node:assert/strict';

import { criarLeitorDeIndicadores, extrairResumo, normalizarMunicipio } from './indicadores.js';

const SAO_FELIPE = {
	id: 'sao-felipe',
	urlApi: 'https://exemplo.invalid',
	municipioEsperado: 'SAO_FELIPE'
};

function resumoDoBackend(extra = {}) {
	return {
		municipio: 'SAO_FELIPE',
		totais: {
			solicitacoes: 5000,
			agendamentos: 3000,
			pacientesAtendidos: 1200,
			viagensTransporte: null
		},
		catalogo: {
			unidades: 8,
			especialidades: 60,
			nomesEspecialidades: ['Cardiologia', 'Ortopedia']
		},
		sistema: { versao: '1.7', deployEm: '2026-09-25T18:30:00Z' },
		geradoEm: '2026-10-09T12:00:00Z',
		...extra
	};
}

function respostaJson(corpo, status = 200) {
	return { status, json: async () => corpo };
}

/** Relógio e fetch controlados; `chamadas` conta as idas à instância. */
function cenario(responder) {
	const estado = { agora: 1_000_000, chamadas: 0 };
	const leitor = criarLeitorDeIndicadores({
		agora: () => estado.agora,
		fetch: async (...args) => {
			estado.chamadas++;
			return responder(...args);
		}
	});
	return { estado, leitor };
}

const MINUTO = 60 * 1000;

test('normalizarMunicipio ignora acento, caixa e separador', () => {
	assert.equal(normalizarMunicipio('São Felipe'), 'SAO_FELIPE');
	assert.equal(normalizarMunicipio('conceicao-do almeida'), 'CONCEICAO_DO_ALMEIDA');
	assert.equal(normalizarMunicipio(null), '');
});

test('extrairResumo copia só a lista fechada de campos', () => {
	const resumo = extrairResumo(
		resumoDoBackend({
			nomePaciente: 'Fulano',
			totais: { solicitacoes: 5000, agendamentos: 3000, pacientesAtendidos: 1200, fila: 77 },
			catalogo: {
				unidades: 8,
				especialidades: 60,
				nomesEspecialidades: ['Cardiologia'],
				valor: 10
			},
			sistema: { versao: '1.7', deployEm: '2026-09-25T18:30:00Z', host: 'vps-2' }
		}),
		'SAO_FELIPE'
	);

	assert.deepEqual(Object.keys(resumo).sort(), [
		'catalogo',
		'disponivel',
		'geradoEm',
		'sistema',
		'totais'
	]);
	assert.deepEqual(Object.keys(resumo.totais).sort(), [
		'agendamentos',
		'pacientesAtendidos',
		'solicitacoes',
		'viagensTransporte'
	]);
	assert.deepEqual(Object.keys(resumo.catalogo).sort(), [
		'especialidades',
		'nomesEspecialidades',
		'unidades'
	]);
	assert.deepEqual(Object.keys(resumo.sistema).sort(), ['deployEm', 'versao']);
	assert.equal(JSON.stringify(resumo).includes('Fulano'), false);
});

test('extrairResumo descarta valor de tipo errado em vez de repassá-lo', () => {
	const resumo = extrairResumo(
		resumoDoBackend({
			totais: {
				solicitacoes: '5000',
				agendamentos: -1,
				pacientesAtendidos: 1.5,
				viagensTransporte: null
			},
			catalogo: {
				unidades: 8,
				especialidades: 60,
				nomesEspecialidades: ['Cardiologia', 7, { a: 1 }, '']
			},
			sistema: { versao: 17, deployEm: 'ontem' },
			geradoEm: 12345
		}),
		'SAO_FELIPE'
	);

	assert.deepEqual(resumo.totais, {
		solicitacoes: null,
		agendamentos: null,
		pacientesAtendidos: null,
		viagensTransporte: null
	});
	assert.deepEqual(resumo.catalogo.nomesEspecialidades, ['Cardiologia']);
	assert.deepEqual(resumo.sistema, { versao: null, deployEm: null });
	assert.equal(resumo.geradoEm, null);
});

test('resposta de outro município é tratada como indisponível', async () => {
	const { leitor } = cenario(() =>
		respostaJson(resumoDoBackend({ municipio: 'CONCEICAO_DO_ALMEIDA' }))
	);

	assert.deepEqual(await leitor.ler(SAO_FELIPE), { disponivel: false });
});

test('timeout, erro de rede, status diferente de 200 e JSON inválido viram indisponível, sem lançar', async () => {
	const falhas = [
		() => Promise.reject(new DOMException('tempo esgotado', 'TimeoutError')),
		() => Promise.reject(new TypeError('fetch failed')),
		() => respostaJson({ message: 'não encontrado' }, 404),
		() => ({ status: 200, json: async () => Promise.reject(new SyntaxError('JSON')) }),
		() => respostaJson('texto solto')
	];
	for (const falha of falhas) {
		const { leitor } = cenario(falha);
		assert.deepEqual(await leitor.ler(SAO_FELIPE), { disponivel: false });
	}
});

test('a leitura pede o endpoint de resumo com timeout e sem seguir redirecionamento', async () => {
	let pedido;
	const { leitor } = cenario((url, opcoes) => {
		pedido = { url: String(url), opcoes };
		return respostaJson(resumoDoBackend());
	});

	await leitor.ler(SAO_FELIPE);

	assert.equal(pedido.url, 'https://exemplo.invalid/api/transparencia/resumo');
	assert.equal(pedido.opcoes.redirect, 'error');
	assert.ok(pedido.opcoes.signal instanceof AbortSignal);
});

test('sucesso fica 10 minutos em cache', async () => {
	const { estado, leitor } = cenario(() => respostaJson(resumoDoBackend()));

	const primeiro = await leitor.ler(SAO_FELIPE);
	estado.agora += 9 * MINUTO;
	await leitor.ler(SAO_FELIPE);

	assert.equal(primeiro.disponivel, true);
	assert.equal(estado.chamadas, 1);
});

test('vencido há pouco: serve o dado guardado e atualiza em segundo plano', async () => {
	let solicitacoes = 5000;
	const { estado, leitor } = cenario(() =>
		respostaJson(resumoDoBackend({ totais: { solicitacoes, agendamentos: 3000 } }))
	);

	await leitor.ler(SAO_FELIPE);
	estado.agora += 11 * MINUTO;
	solicitacoes = 5001;

	assert.equal((await leitor.ler(SAO_FELIPE)).totais.solicitacoes, 5000);
	await new Promise((resolve) => setImmediate(resolve));
	assert.equal((await leitor.ler(SAO_FELIPE)).totais.solicitacoes, 5001);
	assert.equal(estado.chamadas, 2);
});

test('dado guardado há mais de uma hora não é servido: espera a leitura nova', async () => {
	let solicitacoes = 5000;
	const { estado, leitor } = cenario(() =>
		respostaJson(resumoDoBackend({ totais: { solicitacoes, agendamentos: 3000 } }))
	);

	await leitor.ler(SAO_FELIPE);
	estado.agora += 61 * MINUTO;
	solicitacoes = 5001;

	assert.equal((await leitor.ler(SAO_FELIPE)).totais.solicitacoes, 5001);
});

test('falha fica só 1 minuto em cache, e a instância volta sozinha', async () => {
	let noAr = false;
	const { estado, leitor } = cenario(() =>
		noAr ? respostaJson(resumoDoBackend()) : Promise.reject(new TypeError('fetch failed'))
	);

	assert.equal((await leitor.ler(SAO_FELIPE)).disponivel, false);
	estado.agora += 30 * 1000;
	assert.equal((await leitor.ler(SAO_FELIPE)).disponivel, false);
	assert.equal(estado.chamadas, 1);

	noAr = true;
	estado.agora += 31 * 1000;

	// A primeira visita depois do minuto já vê a instância: falha vencida não é servida.
	assert.equal((await leitor.ler(SAO_FELIPE)).disponivel, true);
	assert.equal(estado.chamadas, 2);
});

test('falha antiga não é servida a quem chega muito depois', async () => {
	let noAr = false;
	const { estado, leitor } = cenario(() =>
		noAr ? respostaJson(resumoDoBackend()) : Promise.reject(new TypeError('fetch failed'))
	);

	await leitor.ler(SAO_FELIPE);
	noAr = true;
	estado.agora += 40 * MINUTO;

	assert.equal((await leitor.ler(SAO_FELIPE)).disponivel, true);
});

test('visitas simultâneas a frio compartilham uma única leitura', async () => {
	const { estado, leitor } = cenario(() => respostaJson(resumoDoBackend()));

	await Promise.all([leitor.ler(SAO_FELIPE), leitor.ler(SAO_FELIPE), leitor.ler(SAO_FELIPE)]);

	assert.equal(estado.chamadas, 1);
});
