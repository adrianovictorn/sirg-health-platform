<script>
	import { AUTORES } from '$lib/conteudo/sistema.js';
</script>

<section id="autoria" class="bg-papel py-20 sm:py-28 lg:py-36">
	<div class="conteiner">
		<div data-revelar class="max-w-4xl">
			<p class="rotulo text-mata">Quem faz</p>
			<h2
				class="font-display mt-4 text-4xl leading-[1.05] font-bold tracking-tight text-balance sm:text-5xl lg:text-6xl"
			>
				Feito por quem acompanha a secretaria de perto
			</h2>
			<p class="text-musgo mt-6 max-w-2xl text-lg leading-relaxed text-pretty">
				O SIRG é desenvolvido e mantido em contato direto com quem regula e agenda. Quer levar o
				sistema para o seu município ou conversar sobre o projeto? Escreva por um dos canais abaixo.
			</p>
		</div>

		<!--
			Assinatura sem caixa, sob um fio de tinta que atravessa a seção: nome à esquerda e
			canais de contato à direita em tela larga; abaixo de lg, os canais descem para baixo do nome.
		-->
		{#each AUTORES as autor (autor.nome)}
			<div
				data-revelar
				class="border-tinta mt-12 border-t-2 pt-6 sm:mt-16 lg:flex lg:items-end lg:justify-between lg:gap-12"
			>
				<div>
					<p class="rotulo text-musgo">{autor.papel}</p>
					<p
						class="font-display mt-3 text-3xl leading-tight font-semibold tracking-tight sm:text-4xl lg:text-5xl"
					>
						{autor.nome}
					</p>
				</div>
				{#if autor.links.length}
					<ul class="mt-3 -mb-2 flex flex-wrap gap-x-6 lg:mt-0 lg:shrink-0 lg:gap-x-8">
						{#each autor.links as link (link.href)}
							<li>
								<a
									href={link.href}
									target="_blank"
									rel="noopener noreferrer"
									class="text-mata hover:text-tinta active:text-tinta inline-flex min-h-11 items-center font-semibold underline decoration-2 underline-offset-4 transition-colors hover:decoration-4"
								>
									{link.rotulo}<span class="sr-only"> de {autor.nome} (abre em nova aba)</span>
								</a>
							</li>
						{/each}
					</ul>
				{/if}
			</div>
		{/each}
	</div>
</section>
