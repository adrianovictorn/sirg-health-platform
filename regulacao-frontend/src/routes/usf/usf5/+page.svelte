<script lang="ts">
    import { onMount } from "svelte";
    import UserMenu from "$lib/UserMenu.svelte";
    import RoleBasedMenu from "$lib/RoleBasedMenu.svelte";
    import type { PacientePendentes } from "$lib/type/PendenciasPaciente";
    import { getApi } from "$lib/api";

    let isLoading = $state(true);
    let error = $state<string | null>(null);

    let usfOrigem = $state("USF05")
    let page = $state(0)
    let size = $state(10)
    let totalPages = $state(0)
    let termo = $state("")

    let solicitacoes = $state<PacientePendentes[]>([])


    async function listarSolicitacoes() {

      const params = new URLSearchParams()
      params.append("page", String(page))
      params.append("size", String(size))
      params.append("usfOrigem", usfOrigem)
      params.append("termo", termo)

      try {
        const res = await getApi(`solicitacoes/buscar/por/status/usf?${params.toString()}`)

        if(!res.ok){
          alert("Erro ao receber dados do servidor !")
        }
        
        const data = await res.json()
        solicitacoes = data.content
        totalPages = data.totalPages

      } catch (error) {
        alert(`Error: ${error}`)
      } finally {
        isLoading = false;
      }
    }
    
    function formatarData(data: [number, number,number] | null): string {
      if (!data) return 'N/A';
      const [ano, mes, dia] = data
      const d = new Date(ano, mes - 1, dia);
      return d.toLocaleDateString('pt-BR');
    }

    onMount(async () => {
      listarSolicitacoes()    
    })

  </script>

  <svelte:head>
      <title>{usfOrigem.toUpperCase()}</title>
  </svelte:head>

<div class="flex min-h-screen bg-gray-100">
  <!-- Sidebar navigation -->
     <RoleBasedMenu activePage="/home" />


  <!-- Main content area -->
  <div class="flex-1 flex flex-col">
    <!-- Header -->
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Pacientes Pendentes - {usfOrigem}</h1>
      <UserMenu />
    </header>


    <!-- Content -->
    <main class="flex-1 overflow-auto p-6">
      <div class="bg-white rounded-lg shadow-lg p-6 space-y-6">

      <!-- Title and search -->
      <div class="flex flex-col md:flex-row md:justify-between md:items-center gap-4">
        <h2 class="text-2xl font-bold text-emerald-800">
          Lista de Pacientes Pendentes ({usfOrigem})
        </h2>

        <div class="flex w-full md:w-1/2">
          <input
            type="text"
            placeholder="Buscar por nome, CPF, especialidade..."
            class="flex-1 border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500" bind:value={termo} oninput={(e) => {
              const input = e.currentTarget as HTMLInputElement
              const valorDigitado = input.value
              termo = valorDigitado
              listarSolicitacoes()
            }}
          />
        </div>
      </div>

      <!-- Feedback de Carregamento e Erro -->
      {#if isLoading}
        <p class="text-center text-gray-500 py-10">Carregando solicitações...</p>

      {:else if error}
        <p class="text-center text-red-600 bg-red-100 p-4 rounded-lg">
          Erro ao carregar dados: {error}
        </p>

      {:else}
        <p class="text-gray-600">
          Total: {solicitacoes.length}
        </p>

        {#if solicitacoes.length === 0}
          <p class="text-center text-gray-500 py-10">
            Nenhuma solicitação pendente para a {usfOrigem} no momento.
          </p>
        {:else}
          <!-- List items -->
          <ul class="space-y-4">
            {#each solicitacoes as s (s.id)}
              <li class="bg-white border border-gray-200 rounded-lg p-4 hover:shadow transition flex">
                <div class="text-emerald-700 font-bold text-xl mr-4"></div>

                <div class="flex-1">
                  <a href={`/paciente/${s.id}`} class="block hover:underline">
                    <h3 class="text-lg font-bold mb-2">{s.nomePaciente}</h3>
                  </a>

                  <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-2 text-sm">
                    <div><span class="font-semibold">CPF:</span> {s.cpfPaciente}</div>
                    <div><span class="font-semibold">Data:</span> {formatarData(s.dataNascimento)}</div>
                    <div><span class="font-semibold">CNS:</span> {s.cns}</div>

                    <div class="col-span-full">
                      <span class="font-semibold">Especialidades:</span> {s.especialidades}
                    </div>
                  </div>
                </div>
              </li>
            {/each}
          </ul>
                

              <!-- Pagination controls -->
              {#if totalPages > 1}
              <div class="flex justify-center items-center space-x-2 mt-6">
                <button
                  class="px-3 py-1 bg-emerald-600 text-white rounded disabled:opacity-50"
                  disabled={page === 0}
                  onclick={() => { page -= 1; listarSolicitacoes(); }}
                >
                  &laquo; Anterior
                </button>

                <span class="text-gray-700">
                  Página {page + 1} de {totalPages}
                </span>

                <button
                  class="px-3 py-1 bg-emerald-600 text-white rounded disabled:opacity-50"
                  disabled={page + 1 >= totalPages}
                  onclick={() => { page += 1; listarSolicitacoes(); }}
                >
                  Próximo &raquo;
                </button>
              </div>
              {/if}
            {/if}
        {/if}
      </div>
    </main>
  </div>
</div>