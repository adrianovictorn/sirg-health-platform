<script>
	import { DECISOES } from '$lib/conteudo/sistema.js';
</script>

<section id="arquitetura" class="bg-tinta text-papel py-20 sm:py-28 lg:py-36">
	<div class="conteiner">
		<div data-revelar class="max-w-4xl">
			<p class="rotulo text-ceu">Arquitetura</p>
			<h2
				class="font-display mt-4 text-4xl leading-[1.05] font-bold tracking-tight text-balance sm:text-5xl lg:text-6xl"
			>
				Construído como sistema crítico, porque é
			</h2>
			<p class="text-nevoa mt-6 max-w-2xl text-lg leading-relaxed text-pretty">
				Uma unidade de saúde depende do SIRG para atender hoje. As decisões de engenharia partem
				disso.
			</p>
		</div>

		<!-- O desenho e as decisões que ele explica ficam lado a lado. -->
		<div
			class="mt-12 grid gap-12 sm:mt-16 lg:grid-cols-[minmax(0,1.1fr)_minmax(0,0.9fr)] lg:items-start lg:gap-14 xl:gap-20"
		>
			<!-- Diagrama: uma instância por município, ligadas só pela fila. -->
			<figure
				data-revelar
				class="border-tinta-3 bg-tinta-2/60 rounded-2xl border p-5 sm:p-8 lg:sticky lg:top-28 lg:p-6 xl:p-8"
			>
				<div class="grid sm:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] sm:items-center">
					{#each ['Município A', 'Município B'] as nome, i (nome)}
						{#if i === 1}
							<!-- Os traços ligam as duas instâncias: de pé no celular, deitados em tela larga. -->
							<div class="flex flex-col items-center text-center" aria-hidden="true">
								<span class="bg-ceu/60 h-5 w-px sm:hidden"></span>
								<div class="flex items-center">
									<span class="bg-ceu/60 hidden h-px w-4 sm:block xl:w-6"></span>
									<!-- Lado a lado, o rótulo quebra em duas linhas para não tirar largura dos blocos. -->
									<div
										class="border-ceu bg-ceu/10 text-ceu rotulo rounded-full border px-4 py-2 sm:rounded-3xl"
									>
										<span class="sm:block">Fila de</span>
										<span class="sm:block">mensagens</span>
									</div>
									<span class="bg-ceu/60 hidden h-px w-4 sm:block xl:w-6"></span>
								</div>
								<span class="bg-ceu/60 h-5 w-px sm:hidden"></span>
							</div>
						{/if}
						<div class="border-tinta-3 bg-tinta/40 space-y-2 rounded-xl border p-4">
							<p class="rotulo text-folha pb-1">{nome}</p>
							{#each ['Proxy', 'Interface', 'Aplicação', 'Banco de dados'] as camada (camada)}
								<p
									class="bg-tinta border-tinta-3 rounded-lg border px-3 py-2 font-mono text-sm break-words"
								>
									{camada}
								</p>
							{/each}
						</div>
					{/each}
				</div>
				<figcaption class="text-nevoa mt-6 text-sm leading-relaxed text-pretty">
					Cada município tem servidor, aplicação e banco próprios. O único caminho entre dois
					municípios é uma fila de mensagens, usada quando existe pacto entre eles.
				</figcaption>
			</figure>

			<ul class="divide-tinta-3 border-tinta-3 divide-y border-y">
				{#each DECISOES as decisao (decisao.titulo)}
					<li data-revelar class="py-6 sm:py-7">
						<h3 class="font-display text-2xl leading-tight font-semibold">{decisao.titulo}</h3>
						<p class="text-nevoa mt-2.5 max-w-xl leading-relaxed text-pretty">{decisao.texto}</p>
					</li>
				{/each}
			</ul>
		</div>
	</div>
</section>
