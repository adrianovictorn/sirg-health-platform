// Abertura do hero (Motion). Só roda no navegador, como attachment da <section>.
// As demais seções continuam com o GSAP, em narrativa.js: nenhum elemento é animado
// pelas duas bibliotecas.
//
// Regra da página, a mesma do narrativa.js: o HTML e o CSS já mostram tudo no estado
// final. Aqui só se anima A PARTIR de um estado inicial, e apenas para quem não pediu
// movimento reduzido. Só `transform` e `opacity` são animados: nada mexe no layout.

import { animate, inView } from 'motion';

// Molas sem ricochete: sistema público de saúde, o movimento chega e para.
const MOLA = { type: 'spring', bounce: 0, visualDuration: 0.5 };
const MOLA_CURTA = { type: 'spring', bounce: 0, visualDuration: 0.3 };

// Roteiro da entrada, em segundos.
const TEMPO = {
	rotulo: 0,
	titulo: 0.08,
	entrePalavras: 0.045,
	moldura: 0.42,
	destaque: 0.5,
	entreDestaque: 0.06,
	soltar: 1.2,
	apoio: 0.95,
	acoes: 1.07,
	painel: 1.19,
	grade: 1.7
};

// Grade viva: intensidade máxima de cada célula e ritmo, em milissegundos.
const BRILHO_AMBIENTE = 0.07;
const BRILHO_RASTRO = 0.12;
const PASSO_DO_CURSOR = 1500;
const PAUSA_ENTRE_LINHAS = 1600;
const AMBIENTE_MINIMO = 2600;
const AMBIENTE_VARIACAO = 2200;

const sortear = (limite) => Math.floor(Math.random() * limite);

/**
 * @param {HTMLElement} secao a <section> do hero
 * @returns {(() => void) | void} desfaz tudo (animações, temporizadores e estilos inline)
 */
export function animarHero(secao) {
	const reduzido = window.matchMedia('(prefers-reduced-motion: reduce)');
	if (reduzido.matches) return;

	const entrada = entrar(secao);
	const pararGrade = gradeViva(secao, entrada.fim);

	const parar = () => {
		entrada.parar();
		pararGrade();
	};

	// Quem ligar o movimento reduzido com a página aberta para tudo na hora.
	const aoMudar = () => reduzido.matches && parar();
	reduzido.addEventListener('change', aoMudar);

	return () => {
		reduzido.removeEventListener('change', aoMudar);
		parar();
	};
}

/**
 * Título em máscara, palavra a palavra; o destaque é digitado dentro de uma célula
 * selecionada, que depois se solta dele e volta para a grade; apoio, ações e painel
 * entram em seguida.
 */
function entrar(secao) {
	const um = (nome) => secao.querySelector(`[data-hero="${nome}"]`);
	const todos = (seletor) => Array.from(secao.querySelectorAll(seletor));

	// O CSS segura o estado inicial só por um instante (ver app.css). Se o conteúdo já
	// está aparecendo, a hidratação chegou tarde: animar agora faria a tela piscar.
	const sentinela = um('apoio');
	if (!sentinela || Number(getComputedStyle(sentinela).opacity) > 0.05) {
		return { fim: 0, parar() {} };
	}

	const blocos = [
		[um('rotulo'), TEMPO.rotulo, 8],
		[um('apoio'), TEMPO.apoio, 16],
		[um('acoes'), TEMPO.acoes, 16],
		[um('painel'), TEMPO.painel, 16]
	].filter(([elemento]) => elemento);
	const palavras = todos('[data-hero="palavra"]');
	const destaque = secao.querySelector('[data-hero-destaque]');
	const palavrasDoDestaque = todos('[data-hero="palavra-destaque"]');
	const moldura = secao.querySelector('[data-hero-moldura]');

	const tocados = [...blocos.map(([elemento]) => elemento), ...palavras, ...palavrasDoDestaque];
	if (destaque) tocados.push(destaque);
	if (moldura) tocados.push(moldura);

	// Estado inicial posto na mesma tarefa em que o CSS de espera é desligado: não há
	// quadro intermediário com o conteúdo visível fora do lugar.
	for (const [elemento, , desvio] of blocos) {
		elemento.style.opacity = '0';
		elemento.style.transform = `translateY(${desvio}px)`;
	}
	for (const palavra of [...palavras, ...palavrasDoDestaque]) {
		palavra.style.transform = 'translateY(135%)';
	}
	if (destaque) destaque.style.transform = 'translateY(0.05em)';
	secao.setAttribute('data-hero-pronto', '');

	const animacoes = [];
	const tocar = (elemento, valores, opcoes) => animacoes.push(animate(elemento, valores, opcoes));

	for (const [elemento, atraso, desvio] of blocos) {
		tocar(
			elemento,
			{ opacity: [0, 1], transform: [`translateY(${desvio}px)`, 'translateY(0px)'] },
			{ ...MOLA, delay: atraso }
		);
	}

	palavras.forEach((palavra, i) =>
		tocar(
			palavra,
			{ transform: ['translateY(135%)', 'translateY(0%)'] },
			{ ...MOLA, delay: TEMPO.titulo + i * TEMPO.entrePalavras }
		)
	);
	palavrasDoDestaque.forEach((palavra, i) =>
		tocar(
			palavra,
			{ transform: ['translateY(135%)', 'translateY(0%)'] },
			{ ...MOLA, delay: TEMPO.destaque + i * TEMPO.entreDestaque }
		)
	);

	// A célula aparece em volta do destaque, espera o texto e cai uma linha de planilha
	// enquanto o texto sobe: os dois se afastam, e é o texto que fica.
	if (moldura) {
		tocar(
			moldura,
			{
				opacity: [0, 1, 1, 0],
				transform: [
					'translateY(0rem)',
					'translateY(0rem)',
					'translateY(0rem)',
					'translateY(2.75rem)'
				]
			},
			{
				duration: TEMPO.soltar + 0.55 - TEMPO.moldura,
				times: [0, 0.18, (TEMPO.soltar - TEMPO.moldura) / (TEMPO.soltar + 0.55 - TEMPO.moldura), 1],
				ease: ['easeOut', 'linear', 'easeIn'],
				delay: TEMPO.moldura
			}
		);
	}
	if (destaque) {
		tocar(
			destaque,
			{ transform: ['translateY(0.05em)', 'translateY(0em)'] },
			{ ...MOLA, delay: TEMPO.soltar }
		);
	}

	// No fim (ou se for interrompida) a entrada devolve os elementos ao CSS da página.
	let limpo = false;
	const limpar = () => {
		if (limpo) return;
		limpo = true;
		for (const elemento of tocados) {
			elemento.style.removeProperty('opacity');
			elemento.style.removeProperty('transform');
		}
	};
	Promise.all(animacoes.map((animacao) => animacao.finished)).then(limpar, limpar);

	return {
		fim: TEMPO.grade,
		parar() {
			for (const animacao of animacoes) animacao.stop();
			limpar();
		}
	};
}

/**
 * A planilha ao fundo continua em uso: células acendem e apagam devagar e, onde sobra
 * um vão entre o título e o texto de apoio (tela larga), uma célula selecionada avança
 * pela linha deixando rastro. Tudo para quando o hero sai da tela ou a aba é escondida.
 */
function gradeViva(secao, atrasoInicial) {
	const luzes = Array.from(secao.querySelectorAll('[data-hero-luz]'));
	const cursor = secao.querySelector('[data-hero-cursor]');
	const topo = secao.querySelector('[data-hero-topo]');
	const base = secao.querySelector('[data-hero-base]');
	if (!luzes.length) return () => {};

	const temporizadores = new Set();
	const animacoes = new Set();
	let naTela = false;
	let medida = null;
	let proximaLuz = 0;
	let espera = atrasoInicial * 1000;

	const ativa = () => naTela && !document.hidden;

	const depois = (ms, acao) => {
		const id = setTimeout(() => {
			temporizadores.delete(id);
			if (ativa()) acao();
		}, ms);
		temporizadores.add(id);
	};

	const tocar = (elemento, valores, opcoes) => {
		const animacao = animate(elemento, valores, opcoes);
		animacoes.add(animacao);
		animacao.finished.then(
			() => animacoes.delete(animacao),
			() => animacoes.delete(animacao)
		);
	};

	const lugar = (coluna, linha) =>
		`translate(${coluna * medida.largura}px, ${linha * medida.altura}px)`;

	const medir = () => {
		const caixa = secao.getBoundingClientRect();
		const largura = luzes[0].offsetWidth;
		const altura = luzes[0].offsetHeight;
		if (!largura || !altura) return null;

		// Linhas inteiras que cabem no vão, com uma folga para não encostar no texto.
		let primeira = 0;
		let ultima = -1;
		if (topo && base) {
			const de = topo.getBoundingClientRect().bottom - caixa.top + 12;
			const ate = base.getBoundingClientRect().top - caixa.top - 12;
			primeira = Math.ceil(de / altura);
			ultima = Math.floor(ate / altura) - 1;
		}

		return {
			largura,
			altura,
			colunas: Math.max(1, Math.floor(caixa.width / largura)),
			linhas: Math.max(1, Math.floor(caixa.height / altura)),
			primeira,
			ultima
		};
	};

	const acender = (coluna, linha, brilho) => {
		const luz = luzes[proximaLuz++ % luzes.length];
		luz.style.transform = lugar(coluna, linha);
		tocar(
			luz,
			{ opacity: [0, brilho, 0] },
			{ duration: 2.4, times: [0, 0.25, 1], ease: 'easeOut' }
		);
	};

	const ambiente = () => {
		acender(sortear(medida.colunas), sortear(medida.linhas), BRILHO_AMBIENTE);
		depois(AMBIENTE_MINIMO + Math.random() * AMBIENTE_VARIACAO, ambiente);
	};

	const caminhar = () => {
		const { primeira, ultima, colunas } = medida;
		// Sem vão (celular e tablet, onde tudo empilha) não há cursor: só o ambiente.
		if (!cursor || ultima - primeira < 1 || colunas < 5) return;

		const linha = primeira + sortear(ultima - primeira + 1);
		const passos = 3 + sortear(3);
		let coluna = sortear(colunas - passos);

		cursor.style.transform = lugar(coluna, linha);
		tocar(cursor, { opacity: [0, 1] }, { duration: 0.3, ease: 'easeOut' });

		const avancar = (restam) => {
			if (!restam) {
				tocar(cursor, { opacity: [1, 0] }, { duration: 0.4, ease: 'easeOut' });
				depois(PAUSA_ENTRE_LINHAS, caminhar);
				return;
			}
			depois(PASSO_DO_CURSOR, () => {
				acender(coluna, linha, BRILHO_RASTRO);
				const de = lugar(coluna, linha);
				coluna += 1;
				tocar(cursor, { transform: [de, lugar(coluna, linha)] }, MOLA_CURTA);
				avancar(restam - 1);
			});
		};
		avancar(passos);
	};

	const parar = () => {
		for (const id of temporizadores) clearTimeout(id);
		temporizadores.clear();
		for (const animacao of animacoes) animacao.stop();
		animacoes.clear();
		for (const elemento of cursor ? [...luzes, cursor] : luzes) elemento.style.opacity = '0';
	};

	const iniciar = () => {
		parar();
		if (!ativa()) return;
		depois(espera, () => {
			espera = 400;
			medida = medir();
			if (!medida) return;
			ambiente();
			caminhar();
		});
	};

	const pararDeObservar = inView(secao, () => {
		naTela = true;
		iniciar();
		return () => {
			naTela = false;
			parar();
		};
	});

	// O vão muda de lugar quando a janela muda de tamanho: mede de novo e recomeça.
	const observador = new ResizeObserver(iniciar);
	observador.observe(secao);
	document.addEventListener('visibilitychange', iniciar);

	return () => {
		pararDeObservar();
		observador.disconnect();
		document.removeEventListener('visibilitychange', iniciar);
		naTela = false;
		parar();
	};
}
