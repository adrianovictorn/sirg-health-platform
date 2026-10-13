<script>
	import { ANOTACOES, FLUXO, PEDIDOS_ILUSTRATIVOS } from '$lib/conteudo/sistema.js';

	const TONS = {
		folha: 'bg-folha/20 text-mata',
		ceu: 'bg-ceu/20 text-[#0b5f93]',
		alerta: 'bg-alerta/25 text-ocre',
		neutro: 'bg-papel-2 text-musgo'
	};

	// Cabeçalho e linhas usam as mesmas colunas, de largura fixa, para ficarem alinhados
	// entre si. No celular a situação desce para a segunda linha; entre lg e xl o quadro
	// divide a tela com a legenda e a coluna Unidade sai.
	const COLUNAS =
		'grid grid-cols-[3.25rem_minmax(0,1fr)] gap-x-3 gap-y-1.5 px-4 sm:grid-cols-[4.5rem_minmax(0,1.2fr)_minmax(0,1fr)_10.5rem] sm:gap-x-4 sm:px-5 lg:grid-cols-[4.5rem_minmax(0,1fr)_10.5rem] xl:grid-cols-[4.5rem_minmax(0,1.2fr)_minmax(0,1fr)_10.5rem]';

	// Onde cada anotação solta fica sobre a planilha (some com a rolagem).
	const POSICOES = [
		'top-[18%] right-[6%] rotate-3',
		'top-[44%] left-[30%] -rotate-2',
		'bottom-[20%] right-[18%] rotate-2',
		'top-[66%] left-[4%] -rotate-3'
	];
</script>

<!--
	A cena central da página. O HTML está no estado final (a fila organizada);
	narrativa.js anima a partir da "planilha" conforme a rolagem.
-->
<section
	id="fluxo"
	data-cena
	class="bg-papel baixo:pt-22 baixo:pb-6 flex items-center overflow-x-clip py-16 sm:py-24 lg:min-h-svh lg:pt-24 lg:pb-10"
>
	<div
		class="conteiner grid gap-10 sm:gap-12 lg:grid-cols-[minmax(0,0.8fr)_minmax(0,1.2fr)] lg:items-center xl:gap-20"
	>
		<div>
			<p class="rotulo text-mata">Como funciona</p>
			<h2
				class="font-display baixo:mt-3 baixo:text-4xl mt-4 text-4xl leading-tight font-bold tracking-tight text-balance sm:text-5xl"
			>
				Da planilha à fila que anda
			</h2>

			<!-- O número fica numa calha fixa à esquerda; um fio separa as etapas, sem caixa. -->
			<ol class="border-musgo/25 baixo:mt-6 mt-8 max-w-lg border-b sm:mt-10">
				{#each FLUXO as etapa, i (etapa.titulo)}
					<li
						data-cena-etapa
						class="border-musgo/25 baixo:py-3.5 grid grid-cols-[2.5rem_minmax(0,1fr)] gap-x-2 border-t py-5 sm:grid-cols-[3rem_minmax(0,1fr)]"
					>
						<p class="algarismo text-mata baixo:pt-0.5 pt-1.5 text-sm">0{i + 1}</p>
						<div>
							<h3 class="font-display baixo:text-xl text-2xl leading-tight font-semibold">
								{etapa.titulo}
							</h3>
							<p
								class="text-musgo baixo:mt-1 baixo:text-[0.9375rem] baixo:leading-snug mt-2 leading-relaxed"
							>
								{etapa.texto}
							</p>
						</div>
					</li>
				{/each}
			</ol>
		</div>

		<figure data-cena-quadro class="relative">
			<div
				class="border-papel-2 rounded-2xl border bg-white shadow-[0_30px_80px_-40px_rgb(6_27_29/0.45)]"
			>
				<div
					class={[
						'border-papel-2 bg-papel-2/60 text-musgo rotulo items-center rounded-t-2xl border-b py-3',
						COLUNAS
					]}
				>
					<span>Pedido</span>
					<span>Especialidade</span>
					<span class="hidden sm:block lg:hidden xl:block">Unidade</span>
					<span class="hidden sm:block">Situação</span>
				</div>

				<ul>
					{#each PEDIDOS_ILUSTRATIVOS as linha (linha.pedido)}
						<li
							data-cena-linha
							class={[
								'border-papel-2 items-center border-b bg-white py-3.5 last:rounded-b-2xl last:border-b-0',
								COLUNAS
							]}
						>
							<span class="algarismo text-musgo text-sm">{linha.pedido}</span>
							<span class="font-medium break-words">{linha.especialidade}</span>
							<span class="text-musgo hidden text-sm sm:block lg:hidden xl:block"
								>{linha.unidade}</span
							>
							<span
								data-cena-situacao
								class={[
									'col-start-2 justify-self-start rounded-full px-3 py-1 text-xs font-semibold whitespace-nowrap sm:col-start-auto',
									TONS[linha.tom]
								]}
							>
								{linha.situacao}
							</span>
						</li>
					{/each}
				</ul>
			</div>

			<!-- Decoração: invisível por padrão, só aparece no começo da animação. -->
			{#each ANOTACOES as nota, i (nota)}
				<span
					data-cena-nota
					aria-hidden="true"
					class={[
						'pointer-events-none absolute bg-[#ffe98a] px-3 py-1.5 font-mono text-sm text-[#5a4a00] opacity-0 shadow-md',
						POSICOES[i]
					]}
				>
					{nota}
				</span>
			{/each}

			<figcaption class="text-musgo baixo:mt-3 mt-4 text-sm">
				Exemplo ilustrativo. Nenhum dado desta tabela vem de um município.
			</figcaption>
		</figure>
	</div>
</section>
