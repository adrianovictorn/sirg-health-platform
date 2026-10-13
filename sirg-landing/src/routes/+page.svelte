<script>
	import { onMount } from 'svelte';

	import Arquitetura from '$lib/componentes/Arquitetura.svelte';
	import Autoria from '$lib/componentes/Autoria.svelte';
	import Cabecalho from '$lib/componentes/Cabecalho.svelte';
	import Fluxo from '$lib/componentes/Fluxo.svelte';
	import Hero from '$lib/componentes/Hero.svelte';
	import LinhaDoTempo from '$lib/componentes/LinhaDoTempo.svelte';
	import Modulos from '$lib/componentes/Modulos.svelte';
	import Municipios from '$lib/componentes/Municipios.svelte';
	import Rodape from '$lib/componentes/Rodape.svelte';
	import Seo from '$lib/componentes/Seo.svelte';
	import Telas from '$lib/componentes/Telas.svelte';

	let { data } = $props();

	let raiz;

	// GSAP entra só no navegador e fora do carregamento inicial: a página já está
	// completa e legível antes de este import terminar.
	onMount(() => {
		let parar;
		let desmontado = false;

		import('$lib/animacoes/narrativa.js').then(({ iniciarNarrativa }) => {
			if (!desmontado) parar = iniciarNarrativa(raiz);
		});

		return () => {
			desmontado = true;
			parar?.();
		};
	});
</script>

<Seo />

<div bind:this={raiz}>
	<Cabecalho />

	<main id="conteudo">
		<Hero municipios={data.municipios} />
		<Fluxo />
		<Modulos />
		<Telas />
		<Municipios municipios={data.municipios} />
		<LinhaDoTempo />
		<Arquitetura />
		<Autoria />
	</main>

	<Rodape municipios={data.municipios} />
</div>
