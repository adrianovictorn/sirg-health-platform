<script lang="ts">
  import { onMount } from "svelte";
  import { getApi, patchApi, putApi } from "$lib/api";
  import Menu from "$lib/Menu.svelte";
  import { toast, Toaster } from 'svelte-sonner';
  import UserMenu from "$lib/UserMenu.svelte";
    import type { PacienteProjection } from "$lib/models/PacienteProjection";


  // --- Estado do Componente (Svelte 5 Runes) ---
  let isLoading = $state(true);
  let error = $state<string | null>(null);
  let solicitacoesPendentes = $state<any[]>([]);
  let size = $state(10)
  let page = $state(0)  
  let termo = $state('');
  let currentPage = $state(1);
  let totalPages = $state(0)
  let pacientes = $state<PacienteProjection[]> ([])
  const itemsPerPage = 10;

  function formatarData(dataString: string | null): string {
    if (!dataString) return 'N/A';
    const data = new Date(dataString);
    data.setDate(data.getDate() + 1);
    return data.toLocaleDateString('pt-BR');
  }

  

  async function carregarSolicitacoes() {
    const params = new URLSearchParams()
    params.append("size", String(size))
    params.append("page", String(page))
    params.append("termo", termo)

    try {
        const response = await getApi(`solicitacoes/buscar/por/agendados?${params.toString()}`); 
        if (!response.ok) {
          throw new Error('Falha ao carregar as solicitações do servidor.');
        }

        let data =  await response.json()
        pacientes = data.content
      } catch (e: any) {
        error = e.message;
      } finally {
        isLoading = false;
      }
  }
    
    onMount(carregarSolicitacoes);

  async function confirmarPresenca(idEspecialidade: number) {
    try {
      const res = await patchApi(`especialidades/${idEspecialidade}/realizado`);

      if (!res.ok) {
        throw new Error('Falha ao confirmar a presença.');
      }
      solicitacoesPendentes = solicitacoesPendentes.map(s => ({
          ...s,
          especialidades: s.especialidades.filter(e => e.id !== idEspecialidade)
      })).filter(s => s.especialidades.some(e => e.status === 'AGENDADO'));

      toast.success('Presença confirmada!'); // (Opcional)
      carregarSolicitacoes()
    } catch (err: any) {
        error = err.message;
        toast.error(err.message); // (Opcional)
    }
  }

  async function faltouPresenca(idEspecialidade: number) {
    try {
      // CORREÇÃO: A URL correta, conforme seu Controller
      const res = await patchApi(`especialidades/${idEspecialidade}/faltou`);

      if (!res.ok) {
        throw new Error('Falha ao registrar a falta.');
      }
      
      // Atualiza a UI para remover o item da lista
       solicitacoesPendentes = solicitacoesPendentes.map(s => ({
          ...s,
          especialidades: s.especialidades.filter(e => e.id !== idEspecialidade)
      })).filter(s => s.especialidades.some(e => e.status === 'AGENDADO'));
      
      toast.success('Falta registrada com sucesso!'); 


      carregarSolicitacoes()
    } catch (err: any) {
        error = err.message;
        toast.error(err.message); // (Opcional)
    }
  }


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
    <title>Agendados</title>
</svelte:head>

<div class="flex min-h-screen bg-gray-100">
  <!-- Sidebar navigation -->
    <Menu activePage="/home" />

  <!-- Main content area -->
  <div class="flex-1 flex flex-col">
    <!-- Header -->
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Pacientes Agendados</h1>
          <UserMenu/>
    </header>

    <!-- Content -->
    <main class="flex-1 overflow-auto p-6">
      <div class="bg-white rounded-lg shadow-lg p-6 space-y-6">
        <!-- Title and search -->
        <div class="flex flex-col md:flex-row md:justify-between md:items-center">
          <h2 class="text-2xl font-bold text-emerald-800 mb-4 md:mb-0">Lista de Pacientes Agendados</h2>
          <div class="flex w-full md:w-1/2">
            <input
              type="text"
              placeholder="Buscar por nome, CPF, especialidade..."
              bind:value={termo} oninput={(e) => {
                let termo = (e.currentTarget as HTMLInputElement).value
                carregarSolicitacoes()}}
              class="flex-1 border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500"
            />
          </div>
        </div>

        <!-- Feedback de Carregamento e Erro -->
        {#if isLoading}
            <p class="text-center text-gray-500 py-10">Carregando solicitações agendadas...</p>
        {:else if error}
            <p class="text-center text-red-600 bg-red-100 p-4 rounded-lg">Erro ao carregar dados: {error}</p>
        {:else}
            <p class="text-gray-600">Total: {pacientes.length}</p>

            <!-- List items -->
            {#if pacientes.length === 0}
              <p class="text-center text-gray-500 py-10">
                {#if termo.trim()}
                    Nenhuma solicitação encontrada para "{termo}".
                {:else}
                    Nenhuma solicitação pendente no momento.
                {/if}
              </p>
            {:else}
              <ul class="space-y-4">
                {#each pacientes as s, idx (s.solicitacaoEspecialidadeId)}
                  <li class="bg-white border border-gray-200 rounded-lg p-4 hover:shadow transition flex">
                    <div class="text-emerald-700 font-bold text-xl mr-4">{(currentPage - 1) * itemsPerPage + idx + 1}.</div>
                    <div class="flex-1">
                      <a href={`/paciente/${s.id}`} class="block hover:underline">
                        <h3 class="text-lg font-bold mb-2">{s.nomePaciente}</h3>
                      </a>
                      <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-2 text-sm">
                        <div><span class="font-semibold">CPF:</span> {s.cpfPaciente}</div>
                        <div><span class="font-semibold">USF:</span> {s.usfOrigem}</div>
                        <div><span class="font-semibold">Data:</span> {formatarData(s.dataNascimento)}</div>
                       
                      <div class="col-span-full mt-2">
                     <span class="font-semibold text-gray-700">Procedimentos Agendados:</span>
  
                      <ul class="list-disc list-inside pl-4 mt-1 space-y-1">
                          <li class="text-gray-600 flex justify-between items-center">
                            
                            <div>
                                  <span class="text-xs py-0.5 bg-emerald-200 w-max rounded">
                                  {s.especialidade}
                                  </span>
                            </div>

                            <div class="flex items-center space-x-2">
                              <button 
                                class="px-2 py-1 text-xs font-medium text-white bg-green-600 rounded-md hover:bg-green-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-green-500 transition-colors"
                                title="Confirmar Presença" onclick={() => confirmarPresenca(s.solicitacaoEspecialidadeId)}
                              >
                                ✓ Realizado
                              </button>
                              <button 
                                class="px-2 py-1 text-xs font-medium text-white bg-red-600 rounded-md hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500 transition-colors"
                                title="Registrar Falta" onclick={() => faltouPresenca(s.solicitacaoEspecialidadeId)}
                              >
                                ✗ Faltou
                              </button>
                            </div>
                            
                          </li>
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
                  <button onclick={prevPage} class="px-3 py-1 bg-emerald-600 hover:bg-emerald-800 cursor-pointer text-white rounded disabled:opacity-50" disabled={currentPage === 1}>&laquo; Anterior</button>
                  <span class="text-gray-700">Página {currentPage} de {totalPages}</span>
                  <button onclick={nextPage} class="px-3 py-1 bg-emerald-600 hover:bg-emerald-800 cursor-pointer text-white rounded disabled:opacity-50" disabled={currentPage === totalPages}>Próximo &raquo;</button>
                </div>
              {/if}
            {/if}
        {/if}
      </div>
    </main>
  </div>
</div>