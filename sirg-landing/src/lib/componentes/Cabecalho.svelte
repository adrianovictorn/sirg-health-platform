<script>
	const NAVEGACAO = [
		{ rotulo: 'Como funciona', href: '#fluxo' },
		{ rotulo: 'Módulos', href: '#modulos' },
		{ rotulo: 'Telas', href: '#telas' },
		{ rotulo: 'Municípios', href: '#municipios' },
		{ rotulo: 'História', href: '#historia' },
		{ rotulo: 'Arquitetura', href: '#arquitetura' }
	];
</script>

<a
	href="#conteudo"
	class="bg-folha text-tinta fixed top-3 left-3 z-50 inline-flex min-h-11 -translate-y-24 items-center rounded px-4 font-semibold focus:translate-y-0"
>
	Pular para o conteúdo
</a>

<!-- A altura total está em --cabecalho (app.css): mude as duas juntas. -->
<header
	class="border-tinta-3/60 bg-tinta/90 text-papel fixed inset-x-0 top-0 z-40 border-b backdrop-blur-md"
>
	<div class="conteiner flex h-14 items-center justify-between gap-4 lg:h-16 lg:gap-6">
		<a
			href="#topo"
			class="flex min-h-11 items-center gap-2.5 rounded-lg"
			aria-label="SIRG, início da página"
		>
			<img src="/logo-sirg.png" alt="" width="36" height="36" class="size-9" fetchpriority="high" />
			<span class="font-display text-xl font-bold tracking-tight">SIRG</span>
		</a>

		<nav aria-label="Seções da página" class="hidden lg:block">
			<ul class="flex items-center gap-8 text-sm">
				{#each NAVEGACAO as item (item.href)}
					<li>
						<a
							href={item.href}
							class="text-nevoa hover:text-papel active:text-folha inline-flex min-h-11 items-center underline-offset-8 transition-colors hover:underline"
							>{item.rotulo}</a
						>
					</li>
				{/each}
			</ul>
		</nav>

		<a
			href="#municipios"
			class="bg-folha text-tinta hover:bg-papel active:bg-nevoa inline-flex min-h-11 items-center rounded-full px-5 text-sm font-semibold whitespace-nowrap transition-[background-color,scale] duration-200 active:scale-[0.98]"
		>
			Acessar o sistema
		</a>
	</div>

	<!--
		Abaixo de lg as seções viram uma faixa que rola de lado: sem botão de menu,
		sem estado e sem JavaScript. Só uma das duas navegações existe por vez.
	-->
	<!--
		A borda direita esmaece para avisar que a faixa continua (o último item fica fora da
		tela em 375px). O respiro no fim (after) deixa esse item rolar para fora do esmaecido.
	-->
	<nav aria-label="Seções da página" class="border-tinta-3/60 border-t lg:hidden">
		<ul
			class="conteiner flex h-11 [scrollbar-width:none] items-center gap-6 overflow-x-auto overscroll-x-contain [mask-image:linear-gradient(to_right,black_calc(100%-3.5rem),transparent)] text-sm whitespace-nowrap after:w-8 after:shrink-0 after:content-[''] [&::-webkit-scrollbar]:hidden"
		>
			{#each NAVEGACAO as item (item.href)}
				<li>
					<a
						href={item.href}
						class="text-nevoa hover:text-papel active:text-folha inline-flex h-11 items-center -outline-offset-2 transition-colors"
						>{item.rotulo}</a
					>
				</li>
			{/each}
		</ul>
	</nav>
</header>
