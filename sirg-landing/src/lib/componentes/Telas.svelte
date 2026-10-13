<script>
	import { COTAS_ILUSTRATIVAS, FILA_ILUSTRATIVA, PAINEL_ILUSTRATIVO } from '$lib/conteudo/telas.js';
	import { formatarNumero } from '$lib/formato.js';

	// As classes abaixo repetem as das telas reais (regulacao-frontend), para a ilustração
	// ter a cara do sistema: emerald no cabeçalho, cinza no fundo, cartões brancos.
	const FAIXA = {
		emergencia: 'border-l-red-500',
		urgente: 'border-l-amber-500',
		normal: 'border-l-emerald-200'
	};
	const SELO = {
		emergencia: { texto: 'Emergência', classe: 'bg-red-600 text-white' },
		urgente: { texto: 'Urgente', classe: 'border border-amber-300 bg-amber-100 text-amber-900' }
	};
	const PEDIDO = {
		emergencia: 'border-red-200 bg-red-100 text-red-800',
		urgente: 'border-amber-200 bg-amber-100 text-amber-800',
		normal: 'border-emerald-100 bg-emerald-50 text-emerald-800'
	};

	// Mesma regra de /unidade/cotas: a barra fica âmbar a partir de 80% e vermelha em 100%.
	const cotas = COTAS_ILUSTRATIVAS.linhas.map((linha) => {
		const consumo = Math.min(100, Math.round((linha.utilizadas / linha.total) * 100));
		return {
			...linha,
			consumo,
			saldo: linha.total - linha.utilizadas,
			barra: consumo >= 100 ? 'bg-red-500' : consumo >= 80 ? 'bg-amber-500' : 'bg-emerald-600'
		};
	});

	const JANELA =
		'border-musgo/20 flex flex-1 flex-col overflow-hidden rounded-2xl border text-gray-800 shadow-[0_30px_80px_-40px_rgb(6_27_29/0.45)] [font-family:ui-sans-serif,system-ui,sans-serif]';
	// Fundo, respiro e cor do texto entram em cada uso, para não haver duas classes em disputa.
	const CARTAO = 'flex flex-col items-center justify-center rounded-xl text-center shadow-md';
	const TITULO_DE_BLOCO = 'mb-3 text-xs font-semibold tracking-widest uppercase';
</script>

<!-- Cabeçalho da tela, como o das páginas internas, sem menu lateral nem usuário. -->
{#snippet barra(titulo)}
	<div class="flex items-center justify-between gap-3 bg-emerald-700 px-4 py-3 text-white sm:px-5">
		<span class="text-lg font-semibold">{titulo}</span>
		<span class="rounded-full bg-white/15 px-2.5 py-0.5 text-xs font-medium">Exemplo</span>
	</div>
{/snippet}

{#snippet legenda(titulo, texto)}
	<figcaption class="mt-5">
		<h3 class="font-display text-2xl leading-tight font-semibold">{titulo}</h3>
		<p class="text-musgo mt-2 max-w-xl leading-relaxed text-pretty">{texto}</p>
	</figcaption>
{/snippet}

<!--
	Telas remontadas em HTML, nunca capturas: nenhum dado sai de um município. Cada janela é
	uma imagem para leitores de tela (role="img"), descrita em uma frase, para que nomes e
	números fictícios não sejam lidos como se fossem reais. Nada dentro delas recebe foco.
-->
<section id="telas" class="bg-papel-2 py-20 sm:py-28 lg:py-36">
	<div class="conteiner">
		<div data-revelar class="max-w-4xl">
			<p class="rotulo text-mata">O sistema por dentro</p>
			<h2
				class="font-display mt-4 text-4xl leading-[1.05] font-bold tracking-tight text-balance sm:text-5xl lg:text-6xl"
			>
				O que a equipe vê ao abrir o SIRG
			</h2>
			<p class="text-musgo mt-6 max-w-2xl text-lg leading-relaxed text-pretty">
				Três telas do dia a dia: a fila de espera, o painel da gestão e o saldo de cotas de uma
				unidade.
			</p>
			<p class="border-mata text-grafite mt-6 max-w-2xl border-l-2 pl-4 leading-relaxed">
				Exemplos ilustrativos, remontados para esta página. Pacientes, unidades e números são
				fictícios: nenhum dado destas telas vem de um município.
			</p>
		</div>

		<!--
			Em tela larga, fila e painel dividem a primeira linha (7 + 5) e a tabela de cotas ocupa
			a segunda inteira. Abaixo de lg, uma tela por linha. As janelas da mesma linha esticam
			até a mesma altura, para as legendas ficarem alinhadas.
		-->
		<div class="mt-12 grid gap-x-8 gap-y-12 sm:mt-16 lg:grid-cols-12 lg:gap-y-16">
			<figure data-revelar class="flex flex-col lg:col-span-7">
				<div
					role="img"
					aria-label="Ilustração da tela Fila de espera, com dados fictícios: três pacientes sem nome em ordem de espera, cada um com prioridade, unidade e pedidos em aberto."
					class={[JANELA, 'bg-gray-100']}
				>
					{@render barra('Fila de Espera')}

					<div class="flex-1 p-3 sm:p-5">
						<div class="rounded-lg bg-white p-3 shadow-lg sm:p-5">
							<p class="text-xl font-bold text-emerald-800">Pacientes aguardando marcação</p>
							<p class="mt-2 text-sm text-gray-700">
								<strong class="text-gray-900">{FILA_ILUSTRATIVA.total}</strong> pacientes na fila
							</p>

							<div class="mt-4 space-y-3">
								{#each FILA_ILUSTRATIVA.pacientes as paciente (paciente.posicao)}
									{@const selo = SELO[paciente.prioridade]}
									<div
										class={[
											'flex items-start gap-3 rounded-lg border border-l-4 border-gray-200 p-3 sm:gap-4 sm:p-4',
											FAIXA[paciente.prioridade]
										]}
									>
										<span
											class="w-6 shrink-0 text-right text-base font-bold text-emerald-700 tabular-nums sm:w-8 sm:text-lg"
										>
											{paciente.posicao}.
										</span>
										<div class="min-w-0 flex-1 space-y-2">
											<div
												class="flex flex-col gap-x-6 gap-y-1 sm:flex-row sm:items-start sm:justify-between"
											>
												<div class="flex flex-wrap items-center gap-x-3 gap-y-1">
													<span class="text-base font-bold text-gray-800">{paciente.nome}</span>
													{#if selo}
														<span
															class={[
																'rounded px-2 py-0.5 text-xs font-bold tracking-wide uppercase',
																selo.classe
															]}
														>
															{selo.texto}
														</span>
													{/if}
												</div>
												<span class="text-sm text-gray-600 sm:shrink-0 sm:text-right">
													Na fila
													<strong class="text-base font-bold text-emerald-800"
														>{paciente.espera}</strong
													>
												</span>
											</div>

											<p class="text-sm text-gray-800">
												<span class="font-semibold text-gray-600">Unidade:</span>
												{paciente.unidade}
											</p>

											<div class="flex flex-wrap gap-2">
												{#each paciente.pedidos as pedido (pedido.especialidade)}
													<span
														class={[
															'rounded-md border px-2.5 py-1 text-xs',
															PEDIDO[pedido.prioridade]
														]}
													>
														<span class="font-semibold">{pedido.especialidade}</span>
														· {pedido.situacao}
														{#if pedido.prioridade !== 'normal'}
															· <span class="font-bold uppercase"
																>{SELO[pedido.prioridade].texto}</span
															>
														{/if}
														· {pedido.espera}
													</span>
												{/each}
											</div>
										</div>
									</div>
								{/each}
							</div>
						</div>
					</div>
				</div>

				{@render legenda(
					'Fila de espera',
					'Cada paciente aparece uma vez, com todos os pedidos em aberto. Emergência e urgência ficam marcadas na borda e no selo, não só na cor.'
				)}
			</figure>

			<figure data-revelar class="flex flex-col lg:col-span-5">
				<div
					role="img"
					aria-label="Ilustração da tela Painel de controle, com números fictícios: totais de solicitações, alerta de urgência e emergência e pacientes pendentes em três unidades."
					class={[JANELA, 'bg-gray-200']}
				>
					{@render barra('Painel de Controle')}

					<div class="flex-1 space-y-5 p-3 sm:p-5">
						<div>
							<p class={[TITULO_DE_BLOCO, 'text-gray-700']}>Visão Geral</p>
							<div class="grid grid-cols-2 gap-3">
								{#each PAINEL_ILUSTRATIVO.visaoGeral as cartao (cartao.titulo)}
									<div class={[CARTAO, 'bg-white p-3']}>
										{#if cartao.cabecalho}
											<span class="text-sm font-semibold">{cartao.cabecalho}</span>
										{/if}
										<span class="text-sm font-semibold text-emerald-900">{cartao.titulo}</span>
										<span class="text-lg font-bold text-emerald-700">
											{formatarNumero(cartao.valor)}
										</span>
									</div>
								{/each}
							</div>
						</div>

						<div>
							<p class={[TITULO_DE_BLOCO, 'text-gray-700']}>Atenção Imediata</p>
							<div class={[CARTAO, 'bg-red-600 p-3 text-white']}>
								<span class="text-sm font-semibold">{PAINEL_ILUSTRATIVO.alerta.cabecalho}</span>
								<span class="text-sm font-semibold">{PAINEL_ILUSTRATIVO.alerta.titulo}</span>
								<span class="text-lg font-bold">{PAINEL_ILUSTRATIVO.alerta.valor}</span>
							</div>
						</div>

						<div class="rounded-xl bg-emerald-700/30 p-3 sm:p-4">
							<p class={[TITULO_DE_BLOCO, 'text-gray-900']}>Pendentes por Unidade</p>
							<div class="grid grid-cols-3 gap-2 sm:gap-3">
								{#each PAINEL_ILUSTRATIVO.pendentesPorUnidade as item (item.unidade)}
									<div class={[CARTAO, 'bg-white px-1.5 py-3']}>
										<span class="text-xs font-semibold sm:text-sm">{item.unidade}</span>
										<span class="text-xs font-semibold text-emerald-800 sm:text-sm">Pendentes</span>
										<span class="text-lg font-bold text-emerald-700">{item.valor}</span>
									</div>
								{/each}
							</div>
						</div>
					</div>
				</div>

				{@render legenda(
					'Painel de controle',
					'Os totais do município e quantos pacientes cada unidade ainda tem na fila. Dos cartões, um clique leva à lista correspondente.'
				)}
			</figure>

			<figure data-revelar class="flex flex-col lg:col-span-12">
				<div
					role="img"
					aria-label="Ilustração da tela Cotas de uma unidade, com números fictícios: cinco especialidades, cada uma com vagas utilizadas, total, saldo e barra de consumo."
					class={[JANELA, 'bg-gray-200']}
				>
					{@render barra(`Cotas — ${COTAS_ILUSTRATIVAS.unidade}`)}

					<div class="flex-1 p-3 sm:p-5">
						<div class="overflow-hidden rounded-xl border border-gray-100 bg-white shadow-sm">
							<table class="w-full text-sm">
								<thead class="bg-gray-50 text-xs tracking-wider text-gray-700 uppercase">
									<tr>
										<th class="px-3 py-3 text-left sm:px-4">Especialidade / Exame</th>
										<th class="hidden px-4 py-3 text-left sm:table-cell">Período</th>
										<th class="hidden px-4 py-3 text-right sm:table-cell">Utilizadas</th>
										<th class="hidden px-4 py-3 text-right sm:table-cell">Total</th>
										<th class="px-3 py-3 text-right sm:px-4">Saldo</th>
										<th class="w-28 px-3 py-3 text-left sm:w-48 sm:px-4 lg:w-72">Consumo</th>
									</tr>
								</thead>
								<tbody class="divide-y divide-gray-100">
									{#each cotas as cota (cota.especialidade)}
										<tr>
											<td class="px-3 py-3 break-words text-gray-800 sm:px-4">
												{cota.especialidade}
											</td>
											<td class="hidden px-4 py-3 text-gray-700 sm:table-cell">{cota.periodo}</td>
											<td class="hidden px-4 py-3 text-right text-gray-800 sm:table-cell">
												{cota.utilizadas}
											</td>
											<td class="hidden px-4 py-3 text-right text-gray-800 sm:table-cell">
												{cota.total}
											</td>
											<td
												class={[
													'px-3 py-3 text-right font-semibold sm:px-4',
													cota.saldo <= 0 ? 'text-red-600' : 'text-gray-900'
												]}
											>
												{cota.saldo}
											</td>
											<td class="px-3 py-3 sm:px-4">
												<div class="h-2 w-full overflow-hidden rounded-full bg-gray-200">
													<div
														class={['h-full rounded-full', cota.barra]}
														style:width="{cota.consumo}%"
													></div>
												</div>
												{#if cota.saldo <= 0}
													<span class="text-xs text-red-600">esgotada</span>
												{/if}
											</td>
										</tr>
									{/each}
								</tbody>
							</table>
						</div>
					</div>
				</div>

				{@render legenda(
					'Cotas da unidade',
					'O saldo de cada especialidade no período. A barra fica âmbar perto do limite e vermelha quando a cota se esgota, e o agendamento confere esse saldo na hora.'
				)}
			</figure>
		</div>
	</div>
</section>
