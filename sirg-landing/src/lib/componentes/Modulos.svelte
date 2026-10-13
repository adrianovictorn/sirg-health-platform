<script>
	import { MODULOS } from '$lib/conteudo/sistema.js';
</script>

<section id="modulos" class="bg-tinta text-papel py-20 sm:py-28 lg:py-36">
	<div class="conteiner">
		<div data-revelar class="max-w-4xl">
			<p class="rotulo text-folha">Módulos</p>
			<h2
				class="font-display mt-4 text-4xl leading-[1.05] font-bold tracking-tight text-balance sm:text-5xl lg:text-6xl"
			>
				O caminho inteiro do pedido, do posto de saúde ao dia da consulta
			</h2>
			<p class="text-nevoa mt-6 max-w-2xl text-lg leading-relaxed text-pretty">
				Cada módulo resolve uma etapa que antes vivia em um arquivo separado. Todos leem e gravam o
				mesmo registro do paciente.
			</p>
		</div>

		<!--
			Um índice, não uma grade de cartões: as etapas do caminho do pedido, na ordem em que
			ele anda, cada uma com os módulos que a resolvem. Um único fio abre cada etapa; dentro
			dela só o espaço separa. Em tela larga a etapa fica à esquerda e os módulos à direita;
			abaixo de lg a etapa sobe para cima dos módulos, e no celular tudo vira uma coluna.
		-->
		<ol class="mt-12 sm:mt-16">
			{#each MODULOS as grupo (grupo.etapa)}
				<li
					data-revelar
					class="border-tinta-3 grid gap-x-8 gap-y-6 border-t py-8 sm:py-10 lg:grid-cols-12 lg:py-12"
				>
					<h3
						class="font-display text-folha text-2xl leading-tight font-semibold tracking-tight text-balance sm:text-3xl lg:col-span-4 lg:pr-6"
					>
						{grupo.etapa}
					</h3>
					<dl class="grid gap-x-8 gap-y-8 sm:grid-cols-2 lg:col-span-8 lg:gap-x-12">
						{#each grupo.modulos as modulo (modulo.id)}
							<div>
								<dt class="font-display text-xl leading-tight font-semibold sm:text-2xl">
									{modulo.titulo}
								</dt>
								<dd class="text-nevoa mt-2.5 max-w-md leading-relaxed text-pretty">
									{modulo.texto}
								</dd>
							</div>
						{/each}
					</dl>
				</li>
			{/each}
		</ol>
	</div>
</section>
