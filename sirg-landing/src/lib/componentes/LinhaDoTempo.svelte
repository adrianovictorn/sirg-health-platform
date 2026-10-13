<script>
	import { HISTORICO } from '$lib/conteudo/historico.js';
</script>

<section id="historia" class="bg-papel-2/60 py-20 sm:py-28 lg:py-36">
	<div class="conteiner grid gap-12 xl:grid-cols-[minmax(0,0.75fr)_minmax(0,1.25fr)] xl:gap-20">
		<div data-revelar class="max-w-4xl xl:sticky xl:top-28 xl:self-start">
			<p class="rotulo text-mata">História</p>
			<h2
				class="font-display mt-4 text-4xl leading-[1.05] font-bold tracking-tight text-balance sm:text-5xl"
			>
				De um cadastro de pedidos a uma plataforma de regulação
			</h2>
			<p class="text-musgo mt-6 max-w-md text-lg leading-relaxed text-pretty">
				O SIRG cresceu junto com a rotina das secretarias que o usam. Cada etapa abaixo respondeu a
				uma necessidade real do atendimento.
			</p>
		</div>

		<!--
			A partir de sm a data ganha uma calha própria à esquerda do trilho (data | trilho | marco),
			para a cronologia ser lida de cima a baixo sem entrar nos textos. No celular a data volta
			para cima do título e o trilho encosta na margem. --calha guarda a largura da calha.
		-->
		<div class="relative [--calha:0rem] sm:[--calha:11.5rem]" data-trilho-lista>
			<div
				aria-hidden="true"
				class="bg-musgo/25 absolute top-2 bottom-2 left-[calc(var(--calha)+5px)] w-0.5"
			>
				<div data-trilho class="bg-mata size-full"></div>
			</div>

			<ol class="space-y-10 sm:space-y-12">
				{#each HISTORICO as marco (marco.titulo)}
					<li
						data-revelar
						class="relative pl-8 sm:grid sm:grid-cols-[var(--calha)_minmax(0,1fr)] sm:pl-0"
					>
						<span
							aria-hidden="true"
							class="border-mata bg-papel absolute top-1.5 left-[var(--calha)] size-3 rounded-full border-2"
						></span>
						<p
							class="rotulo text-musgo flex flex-wrap items-center gap-x-3 gap-y-1.5 sm:flex-col sm:items-end sm:pt-1 sm:pr-6 sm:text-right"
						>
							{marco.quando}
							{#if marco.rascunho}
								<span
									class="bg-alerta/30 text-ocre rounded-full px-2.5 py-0.5 tracking-normal normal-case"
								>
									rascunho
								</span>
							{/if}
						</p>
						<div class="mt-2 sm:mt-0 sm:pl-9">
							<h3 class="font-display text-2xl leading-tight font-semibold sm:text-3xl">
								{marco.titulo}
							</h3>
							<p class="text-musgo mt-3 max-w-xl text-lg leading-relaxed text-pretty">
								{marco.texto}
							</p>
						</div>
					</li>
				{/each}
			</ol>
		</div>
	</div>
</section>
