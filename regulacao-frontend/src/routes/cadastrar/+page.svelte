<script lang="ts">
	import { goto } from '$app/navigation';
	import { getApi, postApi } from '$lib/api.js';
	import { listarEspecialidadesMedicas } from '$lib/especialidadesApi.js';
	import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';
	import UserMenu from '$lib/UserMenu.svelte';
	import { onMount } from 'svelte';

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
	let cns = '';
	let telefone = '';
	let datanascimento = '';
	let usfOrigem = '';
	let dataMalote = '';
	let observacoes = '';

	let especialidadesCatalogo: { id: number; codigo: string; nome: string }[] = [];
	let especialidades = [{ especialidadeId: null as number | null, status: 'AGUARDANDO', prioridade: 'NORMAL', termo: '', aberto: false }];

	// --- FUNÇÕES DO FORMULÁRIO ---

	function addEspecialidade() {
		especialidades = [
			...especialidades,
			{ especialidadeId: null, status: 'AGUARDANDO', prioridade: 'NORMAL', termo: '', aberto: false }
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
				console.log('CIDs carregados com sucesso:', cids);
			} else {
				alert('Falha ao buscar a lista de CIDs do servidor.');
			}
		} catch (err) {
			console.error('Erro ao carregar CIDs:', err);
			alert('Falha de conexão ao buscar os dados de CID.');
		}
	}

	async function submeterForm() {
		isLoading = true;

		const idsDeCidsValidos = cidsSelecionados.filter((id) => id !== null);

		if (idsDeCidsValidos.length === 0) {
			alert('Por favor, selecione pelo menos um CID.');
			isLoading = false;
			return;
		}

		const payload = {
			nomePaciente,
			cpfPaciente: cpfPaciente.replace(/\D/g, ''),
			cns,
			telefone,
			datanascimento,
			usfOrigem,
			dataMalote,
			observacoes,
			especialidades: especialidades
				.filter((e) => e.especialidadeId)
				.map((e) => ({
					especialidadeId: Number(e.especialidadeId),
					status: e.status,
					prioridade: e.prioridade
				})),
			cids: idsDeCidsValidos // Envia o array de IDs
		};

		try {
			const res = await postApi('solicitacoes', payload);

			if (res.ok) {
				alert('Solicitação cadastrada com sucesso!');
				limparCampos();
				goto('/cadastrar');
			} else if (res.status === 409) {
				alert('CPF já cadastrado, consulte o módulo Paciente');
			} else {
				alert(`Ocorreu um erro (${res.status}). Verifique os dados e tente novamente.`);
			}
		} catch (err) {
			console.error('Erro de conexão:', err);
			alert('Não foi possível conectar ao servidor. Verifique sua conexão.');
		} finally {
			isLoading = false;
		}
	}

		function limparCampos() {
			nomePaciente = '';
			cpfPaciente = '';
			cns = '';
			telefone = '';
			datanascimento = '';
			usfOrigem = '';
			dataMalote = '';
			observacoes = '';
			especialidades = [{ especialidadeId: null, status: 'AGUARDANDO', prioridade: 'NORMAL', termo: '', aberto: false }];
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
				.catch((e) => console.warn('Falha ao listar especialidades (catálogo):', e))
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
</script>

<svelte:head>
	<title>Consulta</title>
</svelte:head>

<div class="flex min-h-screen bg-gray-100">
	<RoleBasedMenu activePage="/cadastrar" />

	<div class="flex-1 flex flex-col">
		<header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
			<h1 class="text-xl font-semibold">Cadastro de Solicitações</h1>
			<UserMenu />
		</header>

		<main class="flex-1 overflow-auto p-6">
			<div class="bg-white rounded-lg shadow-lg p-6">
				<h2 class="text-2xl font-bold text-emerald-800 mb-6">Cadastro de Solicitação</h2>
				<form on:submit|preventDefault={submeterForm} class="space-y-6">
					<div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
						<div class="flex flex-col">
							<label class="text-sm font-medium text-gray-700 mb-1">Data do Recebimento</label>
							<input type="date" bind:value={dataMalote} class="border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500" required />
						</div>
						<div class="flex flex-col">
							<label class="text-sm font-medium text-gray-700 mb-1">USF Origem</label>
							<select bind:value={usfOrigem} class="border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500" required>
								<option value="" disabled>Selecione...</option>
								{#each ['USF01', 'USF02', 'USF03', 'USF04', 'USF05', 'USF06', 'HMCA'] as u}
									<option value={u}>{u}</option>
								{/each}
							</select>
						</div>
					</div>

					<div class="grid grid-row-5  lg:grid-cols-5 gap-4">
						<div class="flex flex-col">
							<label class="text-sm font-medium text-gray-700 mb-1">Nome do Paciente</label>
							<input type="text" bind:value={nomePaciente} class="border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500" required />
						</div>
						<div class="flex flex-col">
							<label class="text-sm font-medium text-gray-700 mb-1">CPF</label>
							<input type="text" bind:value={cpfPaciente} on:input={formatarCPF} placeholder="000.000.000-00" class="border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500" required />
						</div>
						<div class="flex flex-col">
							<label class="text-sm font-medium text-gray-700 mb-1">CNS</label>
							<input type="text" bind:value={cns} placeholder="Digite o cartão do SUS" maxlength="15" class="border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500" required />
						</div>
						<div class="flex flex-col">
							<label class="text-sm font-medium text-gray-700 mb-1">Telefone</label>
							<input type="text" bind:value={telefone} placeholder="(00)0 0000-0000" maxlength="15" class="border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500" />
						</div>
						<div class="flex flex-col">
							<label class="text-sm font-medium text-gray-700 mb-1">Data de Nascimento</label>
							<input type="date" bind:value={datanascimento} class="border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500" required />
						</div>

						<div class="flex flex-col lg:col-span-5">
							<label class="text-sm font-medium text-gray-700 mb-1">CIDs</label>
								<div class="space-y-2">
									{#each cidsSelecionados as selectedCid, i (i)}
										<div class="flex items-center gap-4">
											<div class="flex-grow relative">
												<label class="sr-only">CID</label>
												<input
													type="text"
													class="w-full border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500"
													placeholder="Digite o código ou descrição..."
													bind:value={termosCid[i]}
													on:focus={() => comboCidAberto[i] = true}
													on:input={() => comboCidAberto[i] = true}
													on:blur={() => setTimeout(() => comboCidAberto[i] = false, 150)}
												/>
												{#if comboCidAberto[i] && filtrarCidsTermo(termosCid[i]).length > 0}
													<ul class="absolute z-50 w-full bg-white border border-gray-300 rounded-md mt-1 max-h-60 overflow-y-auto shadow-lg">
														{#each filtrarCidsTermo(termosCid[i]) as c (c.id)}
															<li class="p-2 hover:bg-emerald-100 cursor-pointer text-sm" on:mousedown={() => { cidsSelecionados[i] = c.id; termosCid[i] = `${c.codigo} - ${c.descricao}`; comboCidAberto[i] = false; }}>
																<strong>{c.codigo}</strong> - {c.descricao}
															</li>
														{/each}
													</ul>
												{/if}
											</div>
											{#if cidsSelecionados.length > 1}
												<button type="button" on:click={() => removerCid(i)} class="text-red-500 hover:text-red-700 font-medium">✕</button>
											{/if}
										</div>
									{/each}
								</div>
							<button type="button" on:click={addCid} class="mt-2 text-emerald-600 hover:text-emerald-800 font-medium self-start">+ Adicionar CID</button>
						</div>
					</div>

					<div class="flex flex-col">
						<label class="text-sm font-medium text-gray-700 mb-1">Observações</label>
						<textarea bind:value={observacoes} rows="4" maxlength="500" class="border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500"></textarea>
					</div>

					<div>
						<h3 class="text-lg font-semibold text-gray-800 mb-4">Especialidades</h3>
								<div class="space-y-4">
									{#each especialidades as esp, i}
										<div class="grid grid-cols-1 md:grid-cols-4 gap-4 items-center p-4 border border-gray-200 rounded-lg">
											<div class="relative">
												<input
													type="text"
													class="border-gray-300 rounded-lg p-2 w-full"
													placeholder="Digite para buscar a especialidade..."
													bind:value={esp.termo}
													on:focus={() => esp.aberto = true}
													on:input={() => esp.aberto = true}
													on:blur={() => setTimeout(() => esp.aberto = false, 150)}
												/>
												{#if esp.aberto && filtrarEspecialidadesTermo(esp.termo).length > 0}
													<ul class="absolute z-50 w-full bg-white border border-gray-300 rounded-md mt-1 max-h-60 overflow-y-auto shadow-lg">
														{#each filtrarEspecialidadesTermo(esp.termo) as e (e.id)}
															<li class="p-2 hover:bg-emerald-100 cursor-pointer text-sm" on:mousedown={() => { esp.especialidadeId = e.id; esp.termo = e.nome; esp.aberto = false; }}>
																{e.nome}
															</li>
														{/each}
													</ul>
												{/if}
											</div>
											<select bind:value={esp.status} class="border-gray-300 rounded-lg p-2">
												<option value="AGUARDANDO">Aguardando</option>
												<option value="RETORNO">Retorno</option>
												<option value="RETORNO_POLICLINICA">Retorno Policlinica</option>
												<option value="GEL">Gel</option>
											</select>
											<select bind:value={esp.prioridade} class="border-gray-300 rounded-lg p-2">
												<option value="NORMAL">Normal</option>
												<option value="URGENTE">Urgente</option>
											</select>
											<button type="button" on:click={() => removerEspecialidade(i)} class="text-red-500 hover:text-red-700 font-medium">✕ Remover</button>
										</div>
									{/each}
								</div>
						<button type="button" on:click={addEspecialidade} class="mt-2 text-emerald-600 hover:text-emerald-800 font-medium">+ Adicionar Especialidade</button>
					</div>

					<button type="submit" class="w-full bg-emerald-800 text-white py-3 rounded-lg hover:bg-emerald-900 transition">Enviar</button>
				</form>
			</div>
		</main>
	</div>
</div>
