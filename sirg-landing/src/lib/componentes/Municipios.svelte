<script>
	import CartaoMunicipio from './CartaoMunicipio.svelte';

	let { municipios } = $props();
</script>

<section id="municipios" class="bg-papel py-20 sm:py-28 lg:py-36">
	<div class="conteiner">
		<div data-revelar class="max-w-4xl">
			<p class="rotulo text-mata">Municípios atendidos</p>
			<h2
				class="font-display mt-4 text-4xl leading-[1.05] font-bold tracking-tight text-balance sm:text-5xl lg:text-6xl"
			>
				Números lidos direto dos servidores em produção
			</h2>
			<p class="text-musgo mt-6 max-w-2xl text-lg leading-relaxed text-pretty">
				Cada município roda o SIRG em servidor próprio. Esta página consulta cada um e mostra apenas
				totais do município inteiro: nenhum dado de paciente sai de lá.
			</p>
		</div>

		<div class="mt-12 grid gap-6 sm:mt-16 lg:grid-cols-2 lg:gap-8">
			{#each municipios as municipio (municipio.id)}
				<CartaoMunicipio {municipio} />
			{/each}
		</div>
	</div>
</section>
