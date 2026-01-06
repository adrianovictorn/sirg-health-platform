<script lang="ts">
  import { onMount } from "svelte";
  import { getApi } from "$lib/api";
    import Menu from "$lib/Menu.svelte";
    import UserMenu from "$lib/UserMenu.svelte";
    import type { UrgenciaEmergenciaPaciente } from "$lib/type/PendenciasPaciente";

  // --- Estado do Componente (Svelte 5 Runes) ---
  let isLoading = $state(true);
  let error = $state<string | null>(null);
  let solicitacoes = $state<UrgenciaEmergenciaPaciente[]>([]);
  
  let buscar = $state('');
  let size = $state(10)
  let totalPages = $state(0)
  let page = $state(0)
  let totalPendentes = $state(0)
  let paginaAtual = $state(0)


  async function buscarUrgenteseEmergencias() {

    const params = new URLSearchParams()
    params.append("page", String(page))
    params.append("size", String(size))
    params.append("termo", buscar)
    
    try {
      const response = await getApi(`solicitacoes/buscar/por/urgentes?${params.toString()}`); 
      if (!response.ok) {
        throw new Error('Falha ao carregar as solicitações do servidor.');
      }
      const data = await response.json();
      totalPages = data.totalPages
      solicitacoes = data.content
      totalPendentes = data.totalElements
      paginaAtual = page 
  
    } catch (e: any) {
      error = e.message;
    } finally {
      isLoading = false;
    }
  }
  
  // --- Carregamento e Processamento de Dados (Client-Side) ---
  onMount(async () => {
    buscarUrgenteseEmergencias()
  });

 function paginaAnterior(){
  page -= 1
  buscarUrgenteseEmergencias()
 }

 function proximaPagina(){
  page += 1
  buscarUrgenteseEmergencias()
 }
  
</script>


<svelte:head>
    <title>Urgentes</title>
</svelte:head>

<div class="flex min-h-screen bg-gray-100">


  
  <!-- Sidebar navigation -->
  <Menu activePage="/urgentes" />

  <!-- Main content area -->
  <div class="flex-1 flex flex-col">
    <!-- Header -->
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Pacientes Pendentes</h1>
          <UserMenu/>
    </header>

    <!-- Content -->
    <main class="flex-1 overflow-auto p-6">
      <div class="bg-white rounded-lg shadow-lg p-6 space-y-6">
        <!-- Title and search -->
        <div class="flex flex-col md:flex-row md:justify-between md:items-center">
          <h2 class="text-2xl font-bold text-emerald-800 mb-4 md:mb-0">Lista de Pacientes Urgentes</h2>
          <div class="flex w-full md:w-1/2">
            <input
              type="text"
              placeholder="Buscar por nome, CPF, especialidade..."
              bind:value={buscar}
              class="flex-1 border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500"
              oninput={(e) => {
                const input = e.currentTarget as HTMLInputElement
                const valorDigitado = input.value
                buscar = valorDigitado
                buscarUrgenteseEmergencias()
              }}
            />
          </div>
        </div>

        <!-- Feedback de Carregamento e Erro -->
        {#if isLoading}
            <p class="text-center text-gray-500 py-10">Carregando solicitações urgentes...</p>
        {:else if error}
            <p class="text-center text-red-600 bg-red-100 p-4 rounded-lg">Erro ao carregar dados: {error}</p>
        {:else}
            <p class="text-gray-600">Total: {totalPendentes}</p>

            <!-- List items -->
            {#if totalPages === 0}
              <p class="text-center text-gray-500 py-10">
                {#if buscar.trim()}
                    Nenhuma solicitação encontrada para "{buscar}".
                {:else}
                    Nenhuma solicitação pendente no momento.
                {/if}
              </p>
            {:else}
              <ul class="space-y-4">
                {#each solicitacoes as s}
                  <li class="bg-white border border-gray-200 rounded-lg p-4 hover:shadow transition flex">
                    <div class="flex-1">
                      <a href={`/paciente/${s.id}`} class="block hover:underline">
                        <h3 class="text-lg font-bold mb-2">{s.nomePaciente}</h3>
                      </a>
                      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-2 text-sm">
                        <div><span class="font-semibold">CPF:</span> {s.cpfPaciente}</div>
                        <div><span class="font-semibold">USF:</span> {s.usfOrigem}</div>
                        <div><span class="font-semibold">Data Nascimento:</span> {s.dataNascimento}</div>
                      
                        <div class="col-span-full">
                          <span class="font-semibold">Especialidades:</span>
                          <ul class="list-disc list-inside">
                              <li>{s.itens}</li>
                          </ul>
                        </div>
                      </div>
                    </div>
                  </li>
                {/each}
              </ul>

              <!-- Pagination controls -->
              {#if totalPages > 1}
                <div class="flex justify-center items-center space-x-2 mt-6">
                  <button class="px-3 py-1 bg-emerald-600 text-white rounded disabled:opacity-50" type="button" onclick={paginaAnterior} disabled={paginaAtual === 0}>&laquo; Anterior</button>
                  <span class="text-gray-700">Página {page + 1} de {totalPages}</span>
                  <button  class="px-3 py-1 bg-emerald-600 text-white rounded disabled:opacity-50" type="button" onclick={proximaPagina} disabled={paginaAtual + 1 === totalPages}>Próximo &raquo;</button>

                 
                </div>
              {/if}
            {/if}
        {/if}
      </div>
    </main>
  </div>
</div>