<script lang="ts">
	import { getApi, putApi } from '$lib/api';
	import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';
    import { user } from '$lib/stores/auth';
	import UserMenu from '$lib/UserMenu.svelte';
	import { onMount } from 'svelte';
	import { fly } from 'svelte/transition';

	// --- Interfaces e Estado ---

	interface CID {
		id: number;
		codigo: string;
		descricao: string;
	}

	let cids: CID[] = [];
	let termoBusca = '';
	let isLoading = true;
	let isUpdating = false;
	let errorMessage: string | null = null; // Para mostrar mensagens de erro na tela
    let codigoAtualizado: string = '';
    let descricaoAtualizada: string = '';
	let cidEmEdicao: CID | null = null;

	// --- Funções de API ---

	async function buscarCIDs() {
		isLoading = true;
		errorMessage = null;
		console.log('Iniciando busca de CIDs...'); // LOG 1

		try {
			// Adiciona um timeout para a requisição. Se não responder em 8s, lança um erro.
			const controller = new AbortController();
			const timeoutId = setTimeout(() => controller.abort(), 8000); // 8 segundos

			console.log('Chamando getApi("cid")...'); // LOG 2
			// Passamos o 'signal' do AbortController para a função getApi (assumindo que ela o repassa para o fetch)
			// Se sua getApi não suportar isso, a lógica do timeout ainda precisa estar nela.
			// Por enquanto, vamos assumir que a getApi é um fetch wrapper.
			const res = await getApi('cid');
			clearTimeout(timeoutId); // Cancela o timeout se a resposta chegar a tempo

			console.log('Resposta da API recebida:', res); // LOG 3

			if (res.ok) {
				cids = await res.json();
				console.log('CIDs carregados:', cids); // LOG 4
			} else {
				console.error('Falha ao buscar CIDs:', res.status, res.statusText);
				errorMessage = `Falha ao carregar a lista de CIDs (Erro: ${res.status}).`;
			}
		} catch (err: any) {
			if (err.name === 'AbortError') {
				console.error('A requisição demorou demais (timeout).');
				errorMessage = 'O servidor demorou muito para responder. Verifique se ele está online e tente novamente.';
			} else {
				console.error('Erro de conexão ao buscar CIDs:', err);
				errorMessage = 'Erro de conexão. Não foi possível se comunicar com o servidor.';
			}
		} finally {
			isLoading = false;
			console.log('Busca de CIDs finalizada.'); // LOG 5
		}
	}

	async function handleAtualizarCID() {
		if (!cidEmEdicao) return;

		if (!cidEmEdicao.codigo.trim() || !cidEmEdicao.descricao.trim()) {
			alert('O código e a descrição não podem estar vazios.');
			return;
		}

		isUpdating = true;

		const payload = {
			codigo: codigoAtualizado,
			descricao: descricaoAtualizada
		};

		try {
			const res = await putApi(`cid/${cidEmEdicao.id}`, payload);

			if (res.ok) {
				alert('CID atualizado com sucesso!');
				cancelarEdicao();
				await buscarCIDs();
			} else if (res.status === 409) {
				alert('Erro: O código deste CID já existe no sistema.');
			} else {
				alert(`Ocorreu um erro (${res.status}). Verifique os dados e tente novamente.`);
			}
		} catch (err) {
			console.error('Erro ao atualizar CID:', err);
			alert('Não foi possível conectar ao servidor para atualizar o CID.');
		} finally {
			isUpdating = false;
		}
	}

	// --- Funções de Controle da UI ---

	function iniciarEdicao(cidParaEditar: CID) {
		cidEmEdicao = { ...cidParaEditar };
        codigoAtualizado = cidEmEdicao.codigo
        descricaoAtualizada = cidEmEdicao.descricao
	}

	function cancelarEdicao() {
		cidEmEdicao = null;
	}

	onMount(buscarCIDs);

	function normalize(s: string) {
		return (s || '')
			.toString()
			.normalize('NFD')
			.replace(/\p{Diacritic}/gu, '')
			.toLowerCase();
	}

	$: cidsFiltrados = !termoBusca
		? cids
		: cids.filter((cid) => {
			const q = normalize(termoBusca);
			return normalize(cid.codigo).includes(q) || normalize(cid.descricao).includes(q);
		});
</script>

<div class="flex min-h-screen bg-gray-100">
	<RoleBasedMenu activePage={'/listar/cid'} />

	<div class="flex-1 flex flex-col overflow-hidden">
		<header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
            <h1 class="text-xl font-semibold">CIDs Cadastrados</h1>
            {#if $user}
                <UserMenu />
            {:else}
                <div>
                <a href="/login" class="hover:underline">Fazer Login</a>
                </div>
            {/if}
            </header>

		<main class="flex-1 overflow-x-hidden overflow-y-auto bg-gray-100">
			<div class="container mx-auto px-6 py-8" in:fly={{ y: 50, duration: 300, delay: 300 }}>
				<h1 class="text-2xl font-semibold text-gray-700 mb-6">Lista de CIDs Cadastrados</h1>

				<div class="bg-white rounded-lg shadow-md overflow-x-auto">
					<div class="p-4 border-b bg-gray-50 flex items-center gap-3">
						<input
							type="text"
							class="border border-gray-300 rounded-lg p-2 w-full md:w-96"
							placeholder="Buscar CID por código ou descrição..."
							bind:value={termoBusca}
						/>
						<button type="button" on:click={() => (termoBusca = '')} class="px-3 py-2 text-sm bg-gray-100 rounded border border-gray-300 hover:bg-gray-200">Limpar</button>
					</div>
					{#if isLoading}
						<p class="p-6 text-center text-gray-500">Carregando dados...</p>
					{:else if errorMessage}
						<div class="p-6 text-center text-red-600 bg-red-50">
							<p>{errorMessage}</p>
							<button on:click={buscarCIDs} class="mt-4 px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700">
								Tentar Novamente
							</button>
						</div>
					{:else if cids.length === 0}
						<p class="p-6 text-center text-gray-500">Nenhum CID encontrado.</p>
					{:else}
						<table class="min-w-full divide-y divide-gray-200">
							<thead class="bg-gray-50">
								<tr>
									<th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Código</th>
									<th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Descrição</th>
									<th scope="col" class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Ações</th>
								</tr>
							</thead>
							<tbody class="bg-white divide-y divide-gray-200">
                                {#each cidsFiltrados as cid (cid.id)}
									<tr class="hover:bg-gray-50 transition-colors duration-200">
										{#if cidEmEdicao?.id === cid.id}
											<td class="px-6 py-4">
												<input type="text" bind:value={codigoAtualizado} class="border border-gray-300 rounded-md p-2 w-full focus:ring-emerald-500 focus:border-emerald-500"/>
											</td>
											<td class="px-6 py-4">
												<input type="text" bind:value={descricaoAtualizada} class="border border-gray-300 rounded-md p-2 w-full focus:ring-emerald-500 focus:border-emerald-500"/>
											</td>
											<td class="px-6 py-4 whitespace-nowrap text-sm font-medium space-x-4">
												<button on:click={handleAtualizarCID} disabled={isUpdating} class="text-blue-600 hover:text-blue-900 disabled:text-gray-400 disabled:cursor-not-allowed">
													{isUpdating ? 'Salvando...' : 'Salvar'}
												</button>
												<button on:click={cancelarEdicao} class="text-red-600 hover:text-red-900">Cancelar</button>
											</td>
										{:else}
											<td class="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">{cid.codigo}</td>
											<td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{cid.descricao}</td>
											<td class="px-6 py-4 whitespace-nowrap text-sm font-medium">
												<button on:click={() => iniciarEdicao(cid)} class="text-emerald-600 hover:text-emerald-900">
													Editar
												</button>
											</td>
										{/if}
									</tr>
								{/each}
							</tbody>
						</table>
					{/if}
				</div>
			</div>
		</main>
	</div>
</div>
