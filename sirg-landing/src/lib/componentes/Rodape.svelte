<script>
	let { municipios } = $props();
</script>

<footer class="border-tinta-3 bg-tinta text-nevoa border-t pt-14 pb-10 sm:pt-20">
	<div
		class="conteiner grid gap-10 md:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)] md:gap-16 lg:gap-24"
	>
		<div class="max-w-md">
			<div class="text-papel flex items-center gap-3">
				<img src="/logo-sirg.png" alt="" width="44" height="44" class="size-11" loading="lazy" />
				<span class="font-display text-2xl font-bold tracking-tight">SIRG</span>
			</div>
			<p class="mt-5 leading-relaxed text-pretty">
				Sistema Integrado de Regulação e Gestão. Regulação e marcação de consultas e exames
				especializados para municípios.
			</p>
		</div>

		<nav aria-label="Acesso por município">
			<p class="rotulo text-papel">Acesso por município</p>
			<ul class="divide-tinta-3 border-tinta-3 mt-4 divide-y border-y">
				{#each municipios as municipio (municipio.id)}
					<li>
						{#if municipio.disponivel}
							<a
								href={municipio.urlAcesso}
								rel="noopener"
								class="group text-papel hover:text-folha active:text-folha flex min-h-12 items-center justify-between gap-4 py-2 font-medium transition-colors"
							>
								{municipio.nome}
								<span
									aria-hidden="true"
									class="transition-transform duration-200 group-hover:translate-x-1 motion-reduce:transition-none"
									>→</span
								>
							</a>
						{:else}
							<span class="flex min-h-12 items-center py-2"
								>{municipio.nome} (temporariamente indisponível)</span
							>
						{/if}
					</li>
				{/each}
			</ul>
		</nav>
	</div>

	<div class="conteiner mt-12 text-sm sm:mt-16">
		<p class="border-tinta-3 border-t pt-6 leading-relaxed">
			Os números desta página são totais por município, atualizados a cada hora. Nenhum dado de
			paciente é publicado aqui.
		</p>
	</div>
</footer>
