<script lang="ts">
	import { goto } from '$app/navigation';
	import { getApi, postApi } from '$lib/api.js';
	import { listarEspecialidadesMedicas } from '$lib/especialidadesApi.js';
	import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';
	import UserMenu from '$lib/UserMenu.svelte';
	import { onMount, tick } from 'svelte';
	import { toast } from 'svelte-sonner';
	import { sugestoesTeclado } from '$lib/sugestoesTeclado.js';

	interface CID {
		id: number;
		codigo: string;
		descricao: string;
	}
	// --- ESTADO DO FORMULÁRIO ---
	let isLoading = false;

	// **AJUSTE 1: Unificar a lista de CIDs**
	// A lista de CIDs carregada da API. O #each usará esta variável.
	let cids: CID[] = [];
	// Para combobox de CIDs por linha
	let termosCid: string[] = [''];
	let comboCidAberto: boolean[] = [false];

	// Array para guardar os IDs dos CIDs selecionados (um por linha)
	let cidsSelecionados: (number | null)[] = [null];

	// Campos do formulário
	let nomePaciente = '';
	let cpfPaciente = '';
	let recemNascido = false;
	let cns = '';
	let telefone = '';
	// Dados cadastrais obrigatórios do paciente (backend valida via @NotBlank).
	let nomePai = '';
	let nomeMae = '';
	let endereco = '';
	let datanascimento = '';
	let dataMalote = '';
	let observacoes = '';
	let unidadeId: number | null = null;
	let unidades: { id: number; nome: string }[] = [];

	let especialidadesCatalogo: { id: number; codigo: string; nome: string }[] = [];
	let especialidades = [{ especialidadeId: null as number | null, profissionalId: null as number | null, status: 'AGUARDANDO', prioridade: 'NORMAL', termo: '', aberto: false, termoProfissional: '', abertoProfissional: false }];
	let profissionais: { id: number; nome: string; conselho: string; numeroRegistro: string }[] = [];

	// --- FUNÇÕES DO FORMULÁRIO ---

	function addEspecialidade() {
		especialidades = [
			...especialidades,
			{ especialidadeId: null, profissionalId: null, status: 'AGUARDANDO', prioridade: 'NORMAL', termo: '', aberto: false, termoProfissional: '', abertoProfissional: false }
		];
	}

	function addCid() {
		cidsSelecionados = [...cidsSelecionados, null];
		termosCid = [...termosCid, ''];
		comboCidAberto = [...comboCidAberto, false];
	}

	function removerCid(index: number) {
		cidsSelecionados = cidsSelecionados.filter((_, i) => i !== index);
		termosCid = termosCid.filter((_, i) => i !== index);
		comboCidAberto = comboCidAberto.filter((_, i) => i !== index);
	}

	function removerEspecialidade(i: number) {
		especialidades = especialidades.filter((_, idx) => idx !== i);
	}

	async function carregarCIDs() {
		try {
			const res = await getApi('cid');
			if (res.ok) {
				cids = await res.json(); // Carrega os dados na variável correta
			} else {
				toast.error('Falha ao buscar a lista de CIDs do servidor.');
			}
		} catch (err) {
			console.error('Erro ao carregar CIDs:', err);
			toast.error('Falha de conexão ao buscar os dados de CID.');
		}
	}

	// Erros de validação por campo (chave = id do campo) e erro vindo do servidor.
	let erros: Record<string, string> = {};
	let erroGeral = '';

	// Mesmas regras que antes eram do `required` do navegador e dos alert().
	function validar() {
		const e: Record<string, string> = {};
		if (!nomePaciente) e.nomePaciente = 'Informe o nome completo.';
		if (!recemNascido && cpfPaciente.replace(/\D/g, '').length === 0) {
			e.cpfPaciente = 'Informe o CPF, ou marque "Recém-nascido" se ele ainda não tiver CPF emitido.';
		}
		if (!cns) e.cns = 'Informe o Cartão do SUS (CNS).';
		if (!datanascimento) e.datanascimento = 'Informe a data de nascimento.';
		if (!nomeMae) e.nomeMae = 'Informe o nome da mãe.';
		if (!nomePai) e.nomePai = 'Informe o nome do pai.';
		if (!endereco) e.endereco = 'Informe o endereço.';
		if (!unidadeId) e.unidadeId = 'Selecione a unidade de saúde de origem.';
		if (!dataMalote) e.dataMalote = 'Informe a data de recebimento do pedido.';
		if (cidsSelecionados.every((id) => id === null)) e['cid-0'] = 'Informe ao menos um CID.';
		return e;
	}

	// O erro some assim que o campo é corrigido.
	function limparErro(event: Event) {
		const id = (event.target as HTMLElement).id || '';
		const chave = id === 'recemNascido' ? 'cpfPaciente' : id.startsWith('cid-') ? 'cid-0' : id;
		if (erros[chave]) {
			const resto = { ...erros };
			delete resto[chave];
			erros = resto;
		}
	}

	async function mostrarErroGeral(mensagem: string) {
		erroGeral = mensagem;
		await tick();
		document.getElementById('erro-geral')?.focus();
	}

	async function submeterForm() {
		erroGeral = '';
		erros = validar();
		const primeiroInvalido = Object.keys(erros)[0];
		if (primeiroInvalido) {
			await tick();
			document.getElementById(primeiroInvalido)?.focus();
			return;
		}

		isLoading = true;

		const idsDeCidsValidos = cidsSelecionados.filter((id) => id !== null);

		const payload = {
			nomePaciente,
			// null, nao string vazia: @CPF do Bean Validation trata "" como CPF
			// invalido (só null é tratado como "ausente"), o que barrava o RN com 400
			// antes mesmo de chegar na validação condicional do service.
			cpfPaciente: cpfPaciente.replace(/\D/g, '') || null,
			recemNascido,
			cns,
			telefone,
			nomePai,
			nomeMae,
			endereco,
			datanascimento,
			dataMalote,
			observacoes,
			unidadeId,
			especialidades: especialidades
				.filter((e) => e.especialidadeId)
				.map((e) => ({
					especialidadeId: Number(e.especialidadeId),
					profissionalId: e.profissionalId ? Number(e.profissionalId) : null,
					status: e.status,
					prioridade: e.prioridade
				})),
			cids: idsDeCidsValidos
		};

		try {
			const res = await postApi('solicitacoes', payload);

			if (res.ok) {
				toast.success('Solicitação cadastrada com sucesso!');
				limparCampos();
				goto('/cadastrar');
			} else if (res.status === 409) {
				await mostrarErroGeral('Este CPF já tem cadastro. Procure o paciente em Pacientes e acrescente a consulta por lá.');
			} else {
				const corpo = await res.json().catch(() => ({}));
				await mostrarErroGeral(corpo.message || `Ocorreu um erro (${res.status}). Verifique os dados e tente novamente.`);
			}
		} catch (err) {
			console.error('Erro de conexão:', err);
			await mostrarErroGeral('Não foi possível conectar ao servidor. Verifique sua conexão.');
		} finally {
			isLoading = false;
		}
	}

		function limparCampos() {
			nomePaciente = '';
			cpfPaciente = '';
			recemNascido = false;
			cns = '';
			telefone = '';
			nomePai = '';
			nomeMae = '';
			endereco = '';
			datanascimento = '';
			dataMalote = '';
			observacoes = '';
			erros = {};
			erroGeral = '';
			unidadeId = null;
			profissionais = [];
			especialidades = [{ especialidadeId: null, profissionalId: null, status: 'AGUARDANDO', prioridade: 'NORMAL', termo: '', aberto: false, termoProfissional: '', abertoProfissional: false }];
			cidsSelecionados = [null];
			termosCid = [''];
			comboCidAberto = [false];
		}

	function formatarCPF(e: Event) {
		const target = e.target as HTMLInputElement;
		let d = target.value.replace(/\D/g, '').slice(0, 11);
		d = d.replace(/^(\d{3})(\d)/, '$1.$2').replace(/^(\d{3}\.\d{3})(\d)/, '$1.$2').replace(/(\d{3}\.\d{3}\.\d{3})(\d)/, '$1-$2');
		cpfPaciente = d;
	}

	onMount(async () => {
		await Promise.all([
			carregarCIDs(),
			listarEspecialidadesMedicas()
				.then((lista) => (especialidadesCatalogo = lista))
				.catch((e) => console.warn('Falha ao listar especialidades (catálogo):', e)),
			getApi('unidades/ativas')
				.then((r) => r.ok ? r.json() : [])
				.then((lista) => (unidades = lista))
				.catch(() => {}),
			getApi('profissionais/buscar?size=200')
				.then(async (r) => { if (r.ok) { const d = await r.json(); profissionais = d.content || []; } })
				.catch(() => {})
		]);
	});

	function normalize(s: string) {
		return (s || '')
			.toString()
			.normalize('NFD')
			.replace(/[\u0300-\u036f]/g, '')
			.toLowerCase();
	}

	function filtrarCidsTermo(t: string) {
		const q = normalize(t);
		const base = q ? cids.filter(c => normalize(c.codigo).includes(q) || normalize(c.descricao).includes(q)) : cids;
		return base.slice(0, 50);
	}

	function filtrarEspecialidadesTermo(t: string) {
		const q = normalize(t);
		const base = q ? especialidadesCatalogo.filter(e => normalize(e.nome).includes(q) || normalize(e.codigo).includes(q)) : especialidadesCatalogo;
		return base.slice(0, 50);
	}

	function filtrarProfissionaisTermo(t: string) {
		const q = normalize(t);
		return q ? profissionais.filter(p => normalize(p.nome).includes(q)) : profissionais;
	}

</script>

<svelte:head>
	<title>Solicitação de consulta</title>
</svelte:head>

<div class="flex min-h-screen bg-gray-100">
	<RoleBasedMenu activePage="/cadastrar" />

	<div class="flex-1 flex flex-col">
		<header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
			<h1 class="text-xl font-semibold">Solicitação de consulta</h1>
			<UserMenu />
		</header>

		<main class="flex-1 overflow-auto p-4 md:p-6 lg:p-8">
			<form on:submit|preventDefault={submeterForm} on:input={limparErro} on:change={limparErro} novalidate class="space-y-6">
				{#if erroGeral}
				  <div id="erro-geral" role="alert" tabindex="-1" class="bg-red-50 border-l-4 border-red-500 rounded-r-lg p-4 text-sm text-red-800 focus:outline-none focus:ring-2 focus:ring-red-500">
				    {erroGeral}
				  </div>
				{/if}
				<p class="text-sm text-gray-600">Todos os campos são obrigatórios, exceto os marcados como opcionais.</p>

				<section class="bg-white rounded-lg shadow p-6">
					<h2 class="text-lg font-bold text-emerald-800 mb-4 border-b pb-2">Paciente</h2>
					<div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
						<div class="md:col-span-2">
							<label for="nomePaciente" class="block text-sm font-medium text-gray-700 mb-1">Nome completo</label>
							<input id="nomePaciente" aria-describedby="nomePaciente-erro" aria-invalid={!!erros.nomePaciente} type="text" bind:value={nomePaciente} autocomplete="off" class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500 aria-invalid:border-red-500 aria-invalid:ring-red-500" required />
							{#if erros.nomePaciente}<p id="nomePaciente-erro" class="mt-1 text-xs font-medium text-red-700">{erros.nomePaciente}</p>{/if}
						</div>
						<div>
							<label for="cpfPaciente" class="block text-sm font-medium text-gray-700 mb-1">CPF</label>
							<input id="cpfPaciente" aria-describedby="cpfPaciente-erro" aria-invalid={!!erros.cpfPaciente} type="text" inputmode="numeric" autocomplete="off" bind:value={cpfPaciente} on:input={formatarCPF} placeholder="000.000.000-00" disabled={recemNascido} class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500 aria-invalid:border-red-500 aria-invalid:ring-red-500 disabled:bg-gray-100 disabled:text-gray-600 disabled:cursor-not-allowed" required={!recemNascido} />
							{#if erros.cpfPaciente}<p id="cpfPaciente-erro" class="mt-1 text-xs font-medium text-red-700">{erros.cpfPaciente}</p>{/if}
							<label class="mt-2 flex items-start gap-2 py-0.5 text-sm text-gray-700 cursor-pointer">
								<input type="checkbox" id="recemNascido" bind:checked={recemNascido} on:change={() => { if (recemNascido) cpfPaciente = ''; }} class="mt-0.5 h-4 w-4 shrink-0 rounded border-gray-400 text-emerald-600 focus:ring-2 focus:ring-emerald-500 focus:ring-offset-1" />
								<span>Recém-nascido, ainda sem CPF</span>
							</label>
						</div>
						<div>
							<label for="cns" class="block text-sm font-medium text-gray-700 mb-1">Cartão do SUS (CNS)</label>
							<input id="cns" aria-invalid={!!erros.cns} type="text" inputmode="numeric" autocomplete="off" bind:value={cns} maxlength="15" aria-describedby="cns-dica cns-erro" class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500 aria-invalid:border-red-500 aria-invalid:ring-red-500" required />
							{#if erros.cns}<p id="cns-erro" class="mt-1 text-xs font-medium text-red-700">{erros.cns}</p>{/if}
							<p id="cns-dica" class="mt-1 text-xs text-gray-600">15 dígitos</p>
						</div>
						<div>
							<label for="datanascimento" class="block text-sm font-medium text-gray-700 mb-1">Data de nascimento</label>
							<input id="datanascimento" aria-describedby="datanascimento-erro" aria-invalid={!!erros.datanascimento} type="date" bind:value={datanascimento} class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500 aria-invalid:border-red-500 aria-invalid:ring-red-500" required />
							{#if erros.datanascimento}<p id="datanascimento-erro" class="mt-1 text-xs font-medium text-red-700">{erros.datanascimento}</p>{/if}
						</div>
						<div>
							<label for="telefone" class="block text-sm font-medium text-gray-700 mb-1">Telefone <span class="text-gray-500 font-normal">(opcional)</span></label>
							<input id="telefone" type="tel" autocomplete="off" bind:value={telefone} placeholder="(00) 00000-0000" maxlength="15" class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500" />
						</div>
						<div class="lg:col-start-1 lg:col-span-2">
							<label for="nomeMae" class="block text-sm font-medium text-gray-700 mb-1">Nome da mãe</label>
							<input id="nomeMae" aria-describedby="nomeMae-erro" aria-invalid={!!erros.nomeMae} type="text" autocomplete="off" bind:value={nomeMae} maxlength="150" class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500 aria-invalid:border-red-500 aria-invalid:ring-red-500" required />
							{#if erros.nomeMae}<p id="nomeMae-erro" class="mt-1 text-xs font-medium text-red-700">{erros.nomeMae}</p>{/if}
						</div>
						<div class="lg:col-span-2">
							<label for="nomePai" class="block text-sm font-medium text-gray-700 mb-1">Nome do pai</label>
							<input id="nomePai" aria-describedby="nomePai-erro" aria-invalid={!!erros.nomePai} type="text" autocomplete="off" bind:value={nomePai} maxlength="150" class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500 aria-invalid:border-red-500 aria-invalid:ring-red-500" required />
							{#if erros.nomePai}<p id="nomePai-erro" class="mt-1 text-xs font-medium text-red-700">{erros.nomePai}</p>{/if}
						</div>
						<div class="md:col-span-2 lg:col-span-4">
							<label for="endereco" class="block text-sm font-medium text-gray-700">Endereço</label>
							<p id="endereco-dica" class="mb-1 text-xs text-gray-600">Rua, número e bairro</p>
							<input id="endereco" aria-invalid={!!erros.endereco} type="text" autocomplete="off" bind:value={endereco} maxlength="300" aria-describedby="endereco-dica endereco-erro" class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500 aria-invalid:border-red-500 aria-invalid:ring-red-500" required />
							{#if erros.endereco}<p id="endereco-erro" class="mt-1 text-xs font-medium text-red-700">{erros.endereco}</p>{/if}
						</div>
					</div>
				</section>

				<section class="bg-white rounded-lg shadow p-6">
					<h2 class="text-lg font-bold text-emerald-800 mb-4 border-b pb-2">Dados do pedido</h2>
					<div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-x-4 -mb-4">
						<div class="lg:col-span-2 row-span-3 grid grid-cols-1 grid-rows-subgrid pb-4">
							<label for="unidadeId" class="block text-sm font-medium text-gray-700 mb-1 self-end">Unidade de saúde de origem</label>
							<select id="unidadeId" aria-describedby="unidadeId-erro" aria-invalid={!!erros.unidadeId} bind:value={unidadeId} class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500 aria-invalid:border-red-500 aria-invalid:ring-red-500" required>
								<option value={null} disabled selected>Selecione a unidade</option>
								{#each unidades as u (u.id)}
									<option value={u.id}>{u.nome}</option>
								{/each}
							</select>
							{#if erros.unidadeId}<p id="unidadeId-erro" class="mt-1 text-xs font-medium text-red-700">{erros.unidadeId}</p>{/if}
						</div>
						<div class="row-span-3 grid grid-cols-1 grid-rows-subgrid pb-4">
							<label for="dataMalote" class="block text-sm font-medium text-gray-700 mb-1 self-end">Data de recebimento do pedido</label>
							<input id="dataMalote" aria-describedby="dataMalote-erro" aria-invalid={!!erros.dataMalote} type="date" bind:value={dataMalote} class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500 aria-invalid:border-red-500 aria-invalid:ring-red-500" required />
							{#if erros.dataMalote}<p id="dataMalote-erro" class="mt-1 text-xs font-medium text-red-700">{erros.dataMalote}</p>{/if}
						</div>

						<div class="md:col-span-2 lg:col-span-4 pb-4">
							<span id="cid-rotulo" class="block text-sm font-medium text-gray-700">CID</span>
							<p id="cid-dica" class="mb-1 text-xs text-gray-600">Informe ao menos um. Busque pelo código ou pela descrição.</p>
							{#if erros['cid-0']}<p id="cid-erro" class="mb-1 text-xs font-medium text-red-700">{erros['cid-0']}</p>{/if}
							<div class="space-y-2">
								{#each cidsSelecionados as selectedCid, i (i)}
									<div class="flex items-center gap-3">
										<div class="flex-grow relative">
											<input
												type="text"
												id="cid-{i}"
												aria-label="CID {i + 1}"
												aria-describedby="cid-dica cid-erro"
												aria-invalid={i === 0 && !!erros['cid-0']}
												autocomplete="off"
												class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500 aria-invalid:border-red-500 aria-invalid:ring-red-500"
												placeholder="Ex.: I10 ou hipertensão"
												bind:value={termosCid[i]}
												on:focus={() => comboCidAberto[i] = true}
												on:input={() => comboCidAberto[i] = true}
												on:blur={() => setTimeout(() => comboCidAberto[i] = false, 150)}
												use:sugestoesTeclado={{ aberta: comboCidAberto[i], fechar: () => (comboCidAberto[i] = false) }}
											/>
											{#if comboCidAberto[i] && filtrarCidsTermo(termosCid[i]).length > 0}
												<ul role="listbox" aria-label="Sugestões" class="absolute z-50 w-full bg-white border border-gray-300 rounded-md mt-1 max-h-60 overflow-y-auto shadow-lg">
													{#each filtrarCidsTermo(termosCid[i]) as c (c.id)}
														<li role="none" class="p-0">
															<button type="button" role="option" aria-selected="false" class="w-full text-left p-2 hover:bg-emerald-100 cursor-pointer text-sm aria-selected:bg-emerald-100 aria-selected:ring-2 aria-selected:ring-inset aria-selected:ring-emerald-700" on:mousedown={() => { cidsSelecionados[i] = c.id; termosCid[i] = `${c.codigo} - ${c.descricao}`; comboCidAberto[i] = false; }}>
																<strong>{c.codigo}</strong> - {c.descricao}
															</button>
														</li>
													{/each}
												</ul>
											{/if}
										</div>
										{#if cidsSelecionados.length > 1}
											<button type="button" on:click={() => removerCid(i)} aria-label="Remover CID {i + 1}" class="shrink-0 px-2 py-2 text-sm font-medium text-red-700 hover:text-red-900 rounded-md focus:outline-none focus:ring-2 focus:ring-red-500">Remover</button>
										{/if}
									</div>
								{/each}
							</div>
							<button type="button" on:click={addCid} class="mt-2 py-1 text-sm font-medium text-emerald-700 hover:text-emerald-900 rounded-md focus:outline-none focus:ring-2 focus:ring-emerald-500">+ Adicionar outro CID</button>
						</div>

						<div class="md:col-span-2 lg:col-span-4 pb-4">
							<label for="observacoes" class="block text-sm font-medium text-gray-700">Observações <span class="text-gray-500 font-normal">(opcional)</span></label>
							<p id="observacoes-dica" class="mb-1 text-xs text-gray-600">Até 500 caracteres.</p>
							<textarea id="observacoes" bind:value={observacoes} rows="3" maxlength="500" aria-describedby="observacoes-dica" class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500"></textarea>
						</div>
					</div>
				</section>

				<section class="bg-white rounded-lg shadow p-6">
					<h2 class="text-lg font-bold text-emerald-800 mb-4 border-b pb-2">Consultas solicitadas</h2>
					<p id="tipo-pedido-dica" class="mb-4 text-xs text-gray-600">
						Tipo de pedido "Gel": Gerenciamento de Especialidades Locais, para especialidades atendidas em outros municípios.
					</p>
					<div class="space-y-4">
						{#each especialidades as esp, i}
							<div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-[2fr_2fr_1fr_1fr_auto] gap-4 items-end p-4 border border-gray-200 rounded-lg">
								<div>
									<label for="esp-{i}" class="block text-sm font-medium text-gray-700 mb-1">Especialidade</label>
									<div class="relative">
										<input
											type="text"
											id="esp-{i}"
											autocomplete="off"
											class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500"
											placeholder="Digite para buscar"
											bind:value={esp.termo}
											on:focus={() => esp.aberto = true}
											on:input={() => esp.aberto = true}
											on:blur={() => setTimeout(() => esp.aberto = false, 150)}
											use:sugestoesTeclado={{ aberta: esp.aberto, fechar: () => (esp.aberto = false) }}
										/>
										{#if esp.aberto && filtrarEspecialidadesTermo(esp.termo).length > 0}
											<ul role="listbox" aria-label="Sugestões" class="absolute z-50 w-full bg-white border border-gray-300 rounded-md mt-1 max-h-60 overflow-y-auto shadow-lg">
												{#each filtrarEspecialidadesTermo(esp.termo) as e (e.id)}
													<li role="none" class="p-0">
														<button type="button" role="option" aria-selected="false" class="w-full text-left p-2 hover:bg-emerald-100 cursor-pointer text-sm aria-selected:bg-emerald-100 aria-selected:ring-2 aria-selected:ring-inset aria-selected:ring-emerald-700" on:mousedown={() => { esp.especialidadeId = e.id; esp.termo = e.nome; esp.aberto = false; }}>
															{e.nome}
														</button>
													</li>
												{/each}
											</ul>
										{/if}
									</div>
								</div>
								<div>
									<label for="prof-{i}" class="block text-sm font-medium text-gray-700 mb-1">Profissional solicitante <span class="text-gray-500 font-normal">(opcional)</span></label>
									<div class="relative">
										<input
											type="text"
											id="prof-{i}"
											autocomplete="off"
											class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500"
											placeholder="Digite para buscar"
											bind:value={esp.termoProfissional}
											on:focus={() => esp.abertoProfissional = true}
											on:input={() => esp.abertoProfissional = true}
											on:blur={() => setTimeout(() => esp.abertoProfissional = false, 150)}
											use:sugestoesTeclado={{ aberta: esp.abertoProfissional, fechar: () => (esp.abertoProfissional = false) }}
										/>
										{#if esp.abertoProfissional}
											{@const sugestoes = filtrarProfissionaisTermo(esp.termoProfissional).slice(0, 40)}
											<ul role="listbox" aria-label="Sugestões" class="absolute z-50 w-full bg-white border border-gray-300 rounded-md mt-1 max-h-60 overflow-y-auto shadow-lg">
												{#if sugestoes.length === 0}
													<li role="none" class="p-2 text-sm text-gray-500 select-none">Nenhum profissional encontrado</li>
												{:else}
													{#each sugestoes as p (p.id)}
														<li role="none" class="hover:bg-emerald-100 text-sm">
															<button type="button" role="option" aria-selected="false" class="w-full text-left p-2 aria-selected:bg-emerald-100 aria-selected:ring-2 aria-selected:ring-inset aria-selected:ring-emerald-700" on:mousedown={() => { esp.profissionalId = p.id; esp.termoProfissional = p.nome; esp.abertoProfissional = false; }}>
																<div class="font-medium">{p.nome}</div>
																{#if p.conselho && p.numeroRegistro}
																	<div class="text-xs text-gray-500">{p.conselho} {p.numeroRegistro}</div>
																{/if}
															</button>
														</li>
													{/each}
												{/if}
											</ul>
										{/if}
										{#if esp.profissionalId}
											<button type="button" aria-label="Limpar profissional" class="absolute right-1 top-1.5 p-1.5 rounded text-gray-500 hover:text-red-600 focus:outline-none focus:ring-2 focus:ring-emerald-500"
												on:click={() => { esp.profissionalId = null; esp.termoProfissional = ''; }}>
												<svg xmlns="http://www.w3.org/2000/svg" class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12"/></svg>
											</button>
										{/if}
									</div>
								</div>
								<div>
									<label for="tipo-{i}" class="block text-sm font-medium text-gray-700 mb-1">Tipo de pedido</label>
									<select id="tipo-{i}" bind:value={esp.status} aria-describedby="tipo-pedido-dica" class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500">
										<option value="AGUARDANDO">Novo pedido</option>
										<option value="RETORNO">Retorno</option>
										<option value="RETORNO_POLICLINICA">Retorno na policlínica</option>
										<option value="GEL">Gel</option>
									</select>
								</div>
								<div>
									<label for="prio-{i}" class="block text-sm font-medium text-gray-700 mb-1">Prioridade</label>
									<select id="prio-{i}" bind:value={esp.prioridade} class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500">
										<option value="NORMAL">Normal</option>
										<option value="URGENTE">Urgente</option>
									</select>
								</div>
								<button type="button" on:click={() => removerEspecialidade(i)} aria-label="Remover consulta {i + 1}" class="justify-self-start px-2 py-2 text-sm font-medium text-red-700 hover:text-red-900 rounded-md focus:outline-none focus:ring-2 focus:ring-red-500">Remover</button>
							</div>
						{/each}
					</div>
					<button type="button" on:click={addEspecialidade} class="mt-3 py-1 text-sm font-medium text-emerald-700 hover:text-emerald-900 rounded-md focus:outline-none focus:ring-2 focus:ring-emerald-500">+ Adicionar outra consulta</button>
				</section>

				<div class="flex justify-end">
					<button type="submit" disabled={isLoading} aria-busy={isLoading} class="w-full md:w-auto bg-emerald-700 text-white px-6 py-2 rounded-md hover:bg-emerald-800 transition-colors shadow disabled:opacity-60 disabled:cursor-not-allowed">
						{isLoading ? 'Cadastrando…' : 'Cadastrar solicitação'}
					</button>
				</div>
			</form>
		</main>
	</div>
</div>
