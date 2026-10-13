<script>
	import { animarHero } from '$lib/animacoes/hero.js';
	import { formatarNumero } from '$lib/formato.js';

	let { municipios } = $props();

	const noAr = $derived(municipios.filter((municipio) => municipio.disponivel).length);
</script>

<!--
	Palavra do título em máscara: a de fora recorta, a de dentro sobe (Motion, em
	$lib/animacoes/hero.js). O recorte tem folga negativa para não cortar "ç", "g", "p"
	nem os acentos, e não mexe na linha de base como `overflow: hidden` faria.
-->
{#snippet palavra(texto, papel = 'palavra')}
	<span class="inline-block [clip-path:inset(-0.14em_-0.1em_-0.22em)]"
		><span data-hero={papel} class="inline-block">{texto}</span></span
	>
{/snippet}

<section
	id="topo"
	class="bg-tinta text-papel relative overflow-hidden pt-[var(--cabecalho)] [--grade-a:2.75rem] [--grade-l:7.5rem]"
	{@attach animarHero}
>
	<!-- Grade de planilha ao fundo: o que o sistema veio substituir. -->
	<div
		aria-hidden="true"
		class="pointer-events-none absolute inset-0 opacity-[0.07]"
		style="background-image: linear-gradient(var(--color-nevoa) 1px, transparent 1px), linear-gradient(90deg, var(--color-nevoa) 1px, transparent 1px); background-size: var(--grade-l) var(--grade-a); mask-image: linear-gradient(to bottom right, black, transparent 75%);"
	></div>

	<!--
		Grade viva: células que acendem e a célula selecionada que percorre o vão entre o
		título e o apoio. Nascem invisíveis e só o Motion as mostra; sem JavaScript ou com
		movimento reduzido a grade fica parada.
	-->
	<div aria-hidden="true" class="pointer-events-none absolute inset-0">
		{#each { length: 6 }, i (i)}
			<span
				data-hero-luz
				class="bg-folha absolute top-0 left-0 h-[var(--grade-a)] w-[var(--grade-l)] opacity-0"
			></span>
		{/each}
		<span
			data-hero-cursor
			class="border-folha/70 absolute top-0 left-0 h-[calc(var(--grade-a)+1px)] w-[calc(var(--grade-l)+1px)] border-[1.5px] opacity-0"
		>
			<span class="bg-folha absolute -right-1 -bottom-1 size-1.5"></span>
		</span>
	</div>

	<!--
		Cartaz em dois andares: o título ocupa a largura toda no alto; embaixo, o apoio e as
		ações dividem a linha de base com o painel ao vivo. No celular tudo empilha.
	-->
	<div
		class="conteiner relative flex flex-col justify-between gap-12 py-12 sm:gap-14 sm:py-16 lg:min-h-[calc(100svh-var(--cabecalho))] lg:py-16"
	>
		<div data-hero-topo>
			<p data-hero="rotulo" class="rotulo text-folha">Sistema Integrado de Regulação e Gestão</p>

			<!--
				A frase inteira fica no aria-label; as palavras separadas são só a parte visual.
				O destaque não quebra a partir de `sm`, onde cabe em uma linha, e ganha a moldura
				de célula selecionada (com a alça de preenchimento) usada na entrada.
			-->
			<h1
				aria-label="A regulação de saúde do município, fora da planilha."
				class="font-display mt-5 text-[length:clamp(2.75rem,min(7vw,12svh),6.5rem)] leading-[0.95] font-bold tracking-tight text-balance sm:mt-6"
			>
				<span aria-hidden="true">
					{@render palavra('A')}
					{@render palavra('regulação')}
					{@render palavra('de')}
					{@render palavra('saúde')}
					{@render palavra('do')}
					{@render palavra('município,')}
					<span data-hero-destaque class="text-folha relative sm:inline-block sm:whitespace-nowrap">
						<span
							data-hero-moldura
							class="border-folha pointer-events-none absolute -inset-x-[0.14em] -top-[0.04em] -bottom-[0.12em] border-2 opacity-0 max-sm:hidden"
						>
							<span class="bg-folha absolute -right-1.5 -bottom-1.5 size-2.5"></span>
						</span>
						{@render palavra('fora', 'palavra-destaque')}
						{@render palavra('da', 'palavra-destaque')}
						{@render palavra('planilha.', 'palavra-destaque')}
					</span>
				</span>
			</h1>
		</div>

		<div
			data-hero-base
			class="grid gap-10 lg:grid-cols-[minmax(0,1fr)_minmax(0,27rem)] lg:items-end lg:gap-16 xl:gap-24"
		>
			<div>
				<p
					data-hero="apoio"
					class="text-nevoa max-w-2xl text-lg leading-relaxed text-pretty sm:text-xl"
				>
					Solicitação, fila, cota por unidade, agendamento e transporte sanitário de consultas e
					exames especializados em um só sistema. Em produção em municípios do interior da Bahia.
				</p>

				<div data-hero="acoes" class="mt-8 flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:gap-4">
					<a
						href="#municipios"
						class="bg-folha text-tinta hover:bg-papel active:bg-nevoa inline-flex min-h-13 items-center justify-center rounded-full px-7 font-semibold transition-[background-color,scale] duration-200 active:scale-[0.98]"
					>
						Ver municípios atendidos
					</a>
					<a
						href="#fluxo"
						class="border-nevoa/60 hover:border-papel hover:bg-papel/10 active:bg-papel/20 inline-flex min-h-13 items-center justify-center rounded-full border px-7 font-semibold transition-[background-color,border-color,scale] duration-200 active:scale-[0.98]"
					>
						Como funciona
					</a>
				</div>
			</div>

			<!-- Painel ao vivo: o estado real das instâncias, já no primeiro olhar. -->
			<aside
				data-hero="painel"
				class="border-tinta-3 bg-tinta-2/80 rounded-2xl border px-6 pt-5 pb-3 sm:px-7"
				aria-label="Situação das instâncias do SIRG"
			>
				<div class="flex items-center justify-between gap-4">
					<p class="rotulo text-nevoa">Em produção agora</p>
					<p class="algarismo text-papel text-sm">{noAr}/{municipios.length} no ar</p>
				</div>

				<ul class="divide-tinta-3 border-tinta-3 mt-4 divide-y border-y">
					{#each municipios as municipio (municipio.id)}
						<li class="flex items-center justify-between gap-4 py-4">
							<div class="flex min-w-0 items-center gap-3">
								<!-- Cheio e pulsando = no ar; anel vazado = fora: a situação não depende só da cor. -->
								<span
									aria-hidden="true"
									class={[
										'size-2.5 shrink-0 rounded-full',
										municipio.disponivel ? 'pulsa bg-folha' : 'border-alerta border-2'
									]}
								></span>
								<div class="min-w-0">
									<p class="font-display text-xl leading-tight font-semibold break-words">
										{municipio.nome}
									</p>
									<p class="text-nevoa mt-0.5 text-sm">
										{municipio.disponivel ? 'No ar' : 'Temporariamente indisponível'}
									</p>
								</div>
							</div>

							{#if municipio.disponivel && municipio.totais.agendamentos !== null}
								<div class="shrink-0 text-right">
									<p class="algarismo text-2xl">{formatarNumero(municipio.totais.agendamentos)}</p>
									<p class="text-nevoa text-xs">agendamentos</p>
								</div>
							{/if}
						</li>
					{/each}
				</ul>

				<a
					href="#municipios"
					class="group text-ceu hover:text-papel active:text-folha mt-1 inline-flex min-h-11 items-center gap-1.5 text-sm font-medium underline-offset-4 transition-colors hover:underline"
				>
					Ver todos os números
					<span
						aria-hidden="true"
						class="transition-transform duration-200 group-hover:translate-y-0.5 motion-reduce:transition-none"
						>↓</span
					>
				</a>
			</aside>
		</div>
	</div>
</section>
