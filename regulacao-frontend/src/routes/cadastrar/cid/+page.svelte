<script lang="ts">
	import { goto } from '$app/navigation';
	import { postApi } from '$lib/api';
	import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';
    import { user } from '$lib/stores/auth';
	import UserHome from '$lib/UserHome.svelte';
    import UserMenu from '$lib/UserMenu.svelte';
	import { fly } from 'svelte/transition';

	let novoCodigo: string = '';
	let novaDescricao: string = '';
	let isLoading = false;

	async function cadastrar(event: Event) {
		isLoading = true;
		const payload = {
			codigo: novoCodigo,
			descricao: novaDescricao
		};

		try {
			const res = await postApi('cid', payload); // Endpoint para CIDs

			if (res.ok) {
				// Status 2xx (Sucesso)
				alert('CID cadastrado com sucesso!');
				limparCampos();
				// Opcional: Redirecionar para a página de listagem após o cadastro
				// await goto('/listar/cid');
			} else if (res.status === 409) {
				// 409 Conflict: Código do CID duplicado
				alert('Erro: O código deste CID já existe no sistema.');
			} else {
				// Qualquer outro erro (400, 500, etc.)
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
		novoCodigo = '';
		novaDescricao = '';
	}
</script>

<!-- Estrutura principal da página com Flexbox -->
<div class="flex min-h-screen bg-gray-100">
	
    <!-- Menu Lateral -->
	<RoleBasedMenu activePage={'/cadastrar/cid'} />

	<!-- Área de Conteúdo Principal -->
	<div class="flex-1 flex flex-col overflow-hidden">
		
        <!-- Cabeçalho da Área de Conteúdo (Barra Superior) -->
		<header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
            <h1 class="text-xl font-semibold">Cadastrar CID</h1>
            {#if $user}
                <UserMenu />
            {:else}
                <div>
                <a href="/login" class="hover:underline">Fazer Login</a>
                </div>
            {/if}
            </header>

		<!-- Conteúdo da Página (com scroll) -->
		<main class="flex-1 overflow-x-hidden overflow-y-auto bg-gray-100">
			<div class="p-6 md:p-8" in:fly={{ y: 50, duration: 300, delay: 300 }} out:fly={{ y: 50, duration: 300 }}>
				<h1 class="text-2xl font-semibold text-gray-700 mb-6">Cadastrar Novo CID</h1>

				<!-- Card do Formulário -->
				<div class="bg-white p-6 rounded-lg shadow-md">
					<form on:submit|preventDefault={cadastrar} class="space-y-6">
						
                        <!-- Linha do Formulário -->
						<div class="grid grid-cols-1 md:grid-cols-3 gap-6">
							<!-- Campo Código do CID -->
							<div class="flex flex-col">
								<label for="codigo-cid" class="text-sm font-medium text-gray-700 mb-1">
									Código do CID <span class="text-red-500">*</span>
								</label>
								<input
									type="text"
									id="codigo-cid"
									bind:value={novoCodigo}
									placeholder="Ex: F20.0"
									class="border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500"
									required
								/>
							</div>

							<!-- Campo Descrição -->
							<div class="flex flex-col md:col-span-2">
								<label for="descricao-cid" class="text-sm font-medium text-gray-700 mb-1">
									Descrição <span class="text-red-500">*</span>
								</label>
								<input
									type="text"
									id="descricao-cid"
									bind:value={novaDescricao}
									placeholder="Ex: Esquizofrenia paranoide"
									class="border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500"
									required
								/>
							</div>
						</div>

						<!-- Botões de Ação -->
						<div class="flex justify-end space-x-4 pt-4">
							<button
								type="button"
								on:click={limparCampos}
								class="px-6 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300 transition-colors"
							>
								Limpar
							</button>
							<button
								type="submit"
								disabled={isLoading}
								class="px-6 py-2 bg-emerald-600 text-white rounded-lg hover:bg-emerald-700 transition-colors disabled:bg-emerald-300"
							>
								{#if isLoading}
									Cadastrando...
								{:else}
									Cadastrar CID
								{/if}
							</button>
						</div>
					</form>
				</div>
			</div>
		</main>
	</div>
</div>