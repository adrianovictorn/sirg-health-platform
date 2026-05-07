<script lang="ts">
  import { onMount } from "svelte";
  import { getApi } from "$lib/api";

  import UserMenu from "$lib/UserMenu.svelte";
  import RoleBasedMenu from "$lib/RoleBasedMenu.svelte";
    import type { PacienteProjection } from "$lib/models/PacienteProjection";
    import { toast } from "svelte-sonner";

  // --- Estado do Componente (Svelte 5 Runes) ---
  let isLoading = $state(true);
  let error = $state<string | null>(null);
  let size = $state(10)
  let page = $state(0)
  let solicitacoes = $state<PacienteProjection[]>([]);
  
  let buscar = $state("");
  let currentPage = $state(1);

  

   function formatarData(dataString: string | null): string {
    if (!dataString) return 'N/A';
    const data = new Date(dataString);
    data.setDate(data.getDate() + 1);
    return data.toLocaleDateString('pt-BR');
  }
  
  async function buscarConcluido() {
    let params = new URLSearchParams()
    params.append("size", String(size))
    params.append("page", String(page))
    params.append("termo", buscar)

    try {
      const res = await getApi(`solicitacoes/buscar/por/concluido?${params.toString()}`)
      if(res.ok){
        const data = await res.json()
        solicitacoes = data.content
        console.log(solicitacoes)
        isLoading = false
      }
    } catch (error) {
      alert("Erro ao buscar solicitações")
    }

    
  }

  onMount(() => {
    toast.promise(buscarConcluido(), {
      loading: 'Carregando concluídos...',
      success: 'Dados carregados !',
      error: 'Erro ao carregar os dados'
    })
    buscarConcluido()
  }
  );

 
  
  // CORREÇÃO: Trocado 'filtrados' por 'filtradas' para corresponder ao nome da variável.
  let totalPages = $derived(Math.ceil(solicitacoes.length / size));
  let paged = $derived(solicitacoes.slice((currentPage - 1) * size, currentPage * size));

  // Efeito para ajustar a página atual se a filtragem mudar
  $effect(() => {
      if (totalPages > 0 && currentPage > totalPages) {
          currentPage = totalPages;
      }
  });
  
  function prevPage() { if (currentPage > 1) currentPage--; }
  function nextPage() { if (currentPage < totalPages) currentPage++; }
</script>

<svelte:head>
    <title>Pendentes</title>
</svelte:head>
<div class="flex min-h-screen bg-gray-100">

  
  <!-- Sidebar navigation -->
   <RoleBasedMenu activePage="/home" />

  <!-- Main content area -->
  <div class="flex-1 flex flex-col">
    <!-- Header -->
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Pacientes Concluídos</h1>
          <UserMenu/>
    </header>

    <!-- Content -->
    <main class="flex-1 overflow-auto p-6">
      <div class="bg-white rounded-lg shadow-lg p-6 space-y-6">
        <!-- Title and search -->
        <div class="flex flex-col md:flex-row md:justify-between md:items-center">
          <h2 class="text-2xl font-bold text-emerald-800 mb-4 md:mb-0">Lista de Pacientes Concluídos </h2>
          <div class="flex w-full md:w-1/2">
            <input
              type="text"
              placeholder="Buscar por nome, CPF, especialidade..."
              bind:value={buscar}
               oninput={(e) => { 
                const buscar = (e.currentTarget as HTMLInputElement).value
                buscarConcluido()
               }}
              class="flex-1 border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500"
            />
          </div>
        </div>

        <!-- Feedback de Carregamento e Erro -->
        {#if isLoading}
            <p class="text-center text-gray-500 py-10">Carregando solicitações pendentes...</p>
        {:else if error}
            <p class="text-center text-red-600 bg-red-100 p-4 rounded-lg">Erro ao carregar dados: {error}</p>
        {:else}
            <p class="text-gray-600">Total: {solicitacoes.length}</p>

            <!-- List items -->
            {#if solicitacoes.length === 0}
              <p class="text-center text-gray-500 py-10">
                {#if buscar.trim()}
                    Nenhuma solicitação encontrada para "{buscar}".
                {:else}
                    Nenhuma solicitação pendente no momento.
                {/if}
              </p>
            {:else}
              <ul class="space-y-4">
                {#each paged as s, idx (s.solicitacaoEspecialidadeId)}
                  <li class="bg-white border border-gray-200 rounded-lg p-4 hover:shadow transition flex">
                    <div class="text-emerald-700 font-bold text-xl mr-4">{(currentPage - 1) * size + idx + 1}.</div>
                    <div class="flex-1">
                      <a href={`/paciente/${s.id}`} class="block hover:underline">
                        <h3 class="text-lg font-bold mb-2">{s.nomePaciente}</h3>
                      </a>
                      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-2 text-sm">
                        <div><span class="font-semibold">CPF:</span> {s.cpfPaciente}</div>
                        <div><span class="font-semibold">USF:</span> {s.usfOrigem}</div>
                        <div><span class="font-semibold">Data:</span> {formatarData(s.dataNascimento)}</div>
                   
                        <div class="col-span-full"><span class="font-semibold">Especialidades Realizadas:

                        </span> 
                        <div class="grid grid-cols-1 gap-2">
                          <div class="bg-emerald-600 text-white rounded w-full p-2">
                            {s.especialidade}
                          </div>
                        </div>
                        </div>
                      </div>
                    </div>
                  </li>
                {/each}
              </ul>

              <!-- Pagination controls -->
              {#if totalPages > 1}
                <div class="flex justify-center items-center space-x-2 mt-6">
                  <button onclick={prevPage} class="px-3 py-1 bg-emerald-600 text-white rounded disabled:opacity-50" disabled={currentPage === 1}>&laquo; Anterior</button>
                  <span class="text-gray-700">Página {currentPage} de {totalPages}</span>
                  <button onclick={nextPage} class="px-3 py-1 bg-emerald-600 text-white rounded disabled:opacity-50" disabled={currentPage === totalPages}>Próximo &raquo;</button>
                </div>
              {/if}
            {/if}
        {/if}
      </div>
    </main>
  </div>
</div>