<script>
	import { formatarDataHora, formatarNumero } from '$lib/formato.js';

	let { municipio } = $props();

	// null = total suprimido pela instância (abaixo do limiar): o indicador não aparece.
	const indicadores = $derived(
		municipio.disponivel
			? [
					{ rotulo: 'Solicitações registradas', valor: municipio.totais.solicitacoes },
					{ rotulo: 'Agendamentos', valor: municipio.totais.agendamentos },
					{ rotulo: 'Pacientes com agendamento', valor: municipio.totais.pacientesAtendidos },
					{ rotulo: 'Viagens de transporte', valor: municipio.totais.viagensTransporte },
					{ rotulo: 'Unidades de saúde', valor: municipio.catalogo.unidades },
					{ rotulo: 'Especialidades e exames', valor: municipio.catalogo.especialidades }
				].filter((indicador) => indicador.valor !== null)
			: []
	);

	const nomes = $derived(municipio.disponivel ? municipio.catalogo.nomesEspecialidades : []);
</script>

<!--
	Três andares fixos, nos dois estados: quem é (nome e situação), o que há para ver
	(números ou o aviso) e o que fazer (acesso), este sempre colado na base do cartão.
-->
<article
	data-revelar
	class={[
		'flex flex-col rounded-2xl border p-6 sm:p-8 xl:p-10',
		municipio.disponivel
			? 'border-papel-2 bg-white shadow-[0_24px_60px_-48px_rgb(6_27_29/0.5)]'
			: 'border-musgo/30 bg-white/60'
	]}
	aria-labelledby="municipio-{municipio.id}"
>
	<!-- Rótulo e situação na mesma linha, nome embaixo: o selo fica no mesmo lugar em todo cartão, seja o nome curto ou longo. -->
	<header>
		<div class="flex flex-wrap items-center justify-between gap-x-4 gap-y-3">
			<p class="rotulo text-musgo">Município · {municipio.uf}</p>

			{#if municipio.disponivel}
				<p
					class="bg-folha/20 text-mata flex items-center gap-2 rounded-full px-4 py-1.5 text-sm font-semibold"
				>
					<span aria-hidden="true" class="pulsa bg-mata size-2 rounded-full"></span>
					No ar
				</p>
			{:else}
				<p
					class="bg-alerta/25 text-ocre flex items-center gap-2 rounded-full px-4 py-1.5 text-sm font-semibold"
				>
					<!-- Anel vazado: a situação não depende só da cor do ponto. -->
					<span aria-hidden="true" class="border-ocre size-2.5 rounded-full border-2"></span>
					Temporariamente indisponível
				</p>
			{/if}
		</div>

		<h3
			id="municipio-{municipio.id}"
			class="font-display mt-4 text-3xl leading-tight font-bold tracking-tight break-words sm:text-4xl"
		>
			{municipio.nome}
		</h3>
	</header>

	{#if municipio.disponivel}
		<!-- Um cartão pode ter menos de seis números (ou nenhum): a grade só existe se houver o que mostrar. -->
		{#if indicadores.length}
			<dl
				class="border-papel-2 mt-6 grid grid-cols-2 gap-x-6 gap-y-7 border-t pt-7 sm:mt-8 sm:grid-cols-3 sm:gap-y-9 sm:pt-8 lg:grid-cols-2 xl:grid-cols-3"
			>
				{#each indicadores as indicador (indicador.rotulo)}
					<div class="flex min-w-0 flex-col">
						<dt class="text-musgo mt-1.5 text-sm leading-snug">{indicador.rotulo}</dt>
						<dd
							class="algarismo text-tinta order-first text-3xl leading-none sm:text-4xl"
							data-contar={indicador.valor}
						>
							{formatarNumero(indicador.valor)}
						</dd>
					</div>
				{/each}
			</dl>
		{/if}

		{#if nomes.length}
			<details class="border-papel-2 group mt-7 border-t pt-2 sm:mt-8">
				<summary
					class="text-mata hover:text-tinta flex min-h-11 cursor-pointer list-none items-center justify-between gap-3 font-semibold transition-colors marker:content-none [&::-webkit-details-marker]:hidden"
				>
					<span class="group-open:hidden">Ver especialidades e exames ofertados</span>
					<span class="hidden group-open:inline">Ocultar especialidades e exames</span>
					<span
						aria-hidden="true"
						class="bg-folha/20 grid size-8 shrink-0 place-items-center rounded-full transition-transform duration-200 group-open:rotate-180 motion-reduce:transition-none"
						>↓</span
					>
				</summary>
				<!--
					A lista real pode ter centenas de itens: rola dentro de uma caixa de altura fixa,
					alcançável pelo teclado (tabindex) para quem não usa mouse.
				-->
				<!-- svelte-ignore a11y_no_noninteractive_tabindex -->
				<div
					tabindex="0"
					role="group"
					aria-label="Especialidades e exames ofertados em {municipio.nome}"
					class="border-papel-2 bg-papel mt-2 max-h-72 overflow-y-auto overscroll-contain rounded-xl border px-4 py-3"
				>
					<ul class="grid gap-x-6 sm:grid-cols-2 lg:grid-cols-1 xl:grid-cols-2">
						{#each nomes as nome, i (i)}
							<li class="text-grafite py-1.5 text-sm leading-snug break-words">{nome}</li>
						{/each}
					</ul>
				</div>
			</details>
		{/if}

		<!-- mt-auto: botão e nota ficam alinhados na base, mesmo com cartões de alturas diferentes. -->
		<div class="mt-auto pt-7 sm:pt-8">
			<a
				href={municipio.urlAcesso}
				rel="noopener"
				class="group/acesso bg-tinta text-papel hover:bg-mata active:bg-tinta-3 flex min-h-13 items-center justify-center gap-2 rounded-full px-7 py-3 text-center font-semibold transition-[background-color,scale] duration-200 active:scale-[0.98] sm:inline-flex"
			>
				Acessar o SIRG de {municipio.nome}
				<span
					aria-hidden="true"
					class="transition-transform duration-200 group-hover/acesso:translate-x-1 motion-reduce:transition-none"
					>→</span
				>
			</a>

			{#if municipio.geradoEm}
				<p class="text-musgo mt-5 text-sm leading-relaxed">
					Números de {formatarDataHora(municipio.geradoEm)}.
				</p>
			{/if}
		</div>
	{:else}
		<!-- Aviso em caixa, com o mesmo anel do selo: explica o vazio em vez de só mostrá-lo. -->
		<div
			class="bg-alerta/15 text-grafite mt-6 flex gap-3.5 rounded-xl px-5 py-4 sm:mt-8 sm:px-6 sm:py-5"
		>
			<span
				aria-hidden="true"
				class="border-ocre mt-[0.4375rem] size-2.5 shrink-0 rounded-full border-2"
			></span>
			<p class="max-w-xl leading-relaxed text-pretty">
				O servidor deste município não respondeu agora. Os números e o acesso voltam a aparecer aqui
				sozinhos, assim que ele estiver disponível.
			</p>
		</div>

		<!-- Sem href de propósito: um link que não abre seria pior que nenhum. -->
		<div class="mt-auto pt-7 sm:pt-8">
			<a
				role="link"
				aria-disabled="true"
				class="border-musgo/50 bg-papel-2/70 text-musgo flex min-h-13 cursor-not-allowed items-center justify-center gap-2 rounded-full border border-dashed px-7 py-3 text-center font-semibold sm:inline-flex"
			>
				Acesso indisponível no momento
			</a>
		</div>
	{/if}
</article>
