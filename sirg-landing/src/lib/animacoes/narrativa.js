// Animações de rolagem da landing (GSAP + ScrollTrigger). Só roda no navegador:
// é importado dinamicamente pelo +page.svelte depois da montagem.
//
// Regra da página: o HTML e o CSS já mostram tudo no estado final. Aqui só se
// anima A PARTIR de um estado inicial, e apenas para quem não pediu movimento
// reduzido. Sem JavaScript, ou com movimento reduzido, nada fica escondido.

import { gsap } from 'gsap';
import { ScrollTrigger } from 'gsap/ScrollTrigger';

import { formatarNumero } from '../formato.js';

/**
 * @param {HTMLElement} raiz elemento que contém a página
 * @returns {() => void} desfaz tudo (estilos inline, pins e gatilhos)
 */
export function iniciarNarrativa(raiz) {
	gsap.registerPlugin(ScrollTrigger);

	const midia = gsap.matchMedia(raiz);

	midia.add(
		// `largo` prende a cena do Fluxo. Exige também altura: abaixo de 620px a legenda
		// compacta (variante `baixo` em app.css) não cabe inteira, e a cena rola solta.
		{
			movimento: '(prefers-reduced-motion: no-preference)',
			largo: '(min-width: 1024px) and (min-height: 620px)'
		},
		(contexto) => {
			const { movimento, largo } = contexto.conditions;
			if (!movimento) return;

			revelarAoRolar();
			planilhaViraFila(largo);
			trilhoDoHistorico();
			const restaurarNumeros = contarNumeros();

			// Abrir a lista de especialidades muda a altura da página: os gatilhos abaixo
			// dela precisam ser medidos de novo. "toggle" não borbulha, daí a captura.
			const medirDeNovo = () => ScrollTrigger.refresh();
			raiz.addEventListener('toggle', medirDeNovo, true);

			return () => {
				raiz.removeEventListener('toggle', medirDeNovo, true);
				restaurarNumeros();
			};
		}
	);

	return () => midia.revert();
}

/** Blocos marcados com data-revelar sobem e aparecem quando entram na tela. */
function revelarAoRolar() {
	const blocos = gsap.utils.toArray('[data-revelar]');
	if (!blocos.length) return;

	gsap.set(blocos, { opacity: 0, y: 28 });
	ScrollTrigger.batch(blocos, {
		start: 'top 88%',
		once: true,
		onEnter: (visiveis) =>
			gsap.to(visiveis, { opacity: 1, y: 0, duration: 0.8, ease: 'power3.out', stagger: 0.08 })
	});
}

/**
 * A cena central: as linhas começam tortas e cobertas de anotações (a planilha)
 * e, com a rolagem, alinham-se e ganham a situação de cada pedido (a fila).
 * Em tela larga a seção fica presa enquanto a cena acontece.
 */
function planilhaViraFila(largo) {
	const cena = document.querySelector('[data-cena]');
	const quadro = cena?.querySelector('[data-cena-quadro]');
	if (!cena || !quadro) return;

	const linhas = gsap.utils.toArray('[data-cena-linha]', cena);
	const notas = gsap.utils.toArray('[data-cena-nota]', cena);
	const situacoes = gsap.utils.toArray('[data-cena-situacao]', cena);
	const etapas = gsap.utils.toArray('[data-cena-etapa]', cena);

	const desvios = [-34, 22, -12, 40, -26, 16];
	const giros = [-2.2, 1.6, -1, 2.4, -1.8, 1.2];

	const linhaDoTempo = gsap.timeline({
		defaults: { ease: 'power2.inOut' },
		scrollTrigger: largo
			? { trigger: cena, start: 'top top', end: '+=140%', pin: true, scrub: 0.6 }
			: { trigger: quadro, start: 'top 78%', end: 'bottom 55%', scrub: 0.6 }
	});

	linhaDoTempo
		.from(
			linhas,
			{
				x: (i) => desvios[i % desvios.length],
				rotation: (i) => giros[i % giros.length],
				opacity: 0.5,
				duration: 1,
				stagger: 0.06
			},
			0
		)
		.fromTo(
			notas,
			{ opacity: 1, scale: 1 },
			{ opacity: 0, scale: 0.7, duration: 0.5, stagger: 0.1 },
			0.15
		)
		.from(situacoes, { opacity: 0, scale: 0.6, duration: 0.4, stagger: 0.07 }, 0.8);

	// A legenda acompanha, uma etapa acesa por vez. Só em tela larga, onde legenda e
	// quadro ficam lado a lado; no celular a legenda é lida antes de o quadro aparecer.
	if (largo && etapas.length === 3) {
		linhaDoTempo
			.fromTo(etapas[1], { opacity: 0.3 }, { opacity: 1, duration: 0.3 }, 0.45)
			.to(etapas[0], { opacity: 0.3, duration: 0.3 }, 0.45)
			.fromTo(etapas[2], { opacity: 0.3 }, { opacity: 1, duration: 0.3 }, 1.15)
			.to(etapas[1], { opacity: 0.3, duration: 0.3 }, 1.15);
	}
}

/** O trilho da linha do tempo se preenche conforme a leitura avança. */
function trilhoDoHistorico() {
	const trilho = document.querySelector('[data-trilho]');
	const lista = document.querySelector('[data-trilho-lista]');
	if (!trilho || !lista) return;

	gsap.from(trilho, {
		scaleY: 0,
		transformOrigin: 'top',
		ease: 'none',
		scrollTrigger: { trigger: lista, start: 'top 70%', end: 'bottom 60%', scrub: 0.4 }
	});
}

/**
 * Indicadores contam de zero até o valor quando entram na tela. O HTML já traz o
 * número final; a função devolvida o restaura se a animação for desfeita no meio.
 */
function contarNumeros() {
	const numeros = gsap.utils.toArray('[data-contar]');

	for (const elemento of numeros) {
		const fim = Number(elemento.dataset.contar);
		if (!Number.isFinite(fim)) continue;

		const conta = { valor: 0 };
		gsap.to(conta, {
			valor: fim,
			duration: 1.6,
			ease: 'power2.out',
			scrollTrigger: { trigger: elemento, start: 'top 92%', once: true },
			onUpdate: () => (elemento.textContent = formatarNumero(Math.round(conta.valor)))
		});
	}

	return () => {
		for (const elemento of numeros) {
			const fim = Number(elemento.dataset.contar);
			if (Number.isFinite(fim)) elemento.textContent = formatarNumero(fim);
		}
	};
}
