<script lang="ts">
  import { onDestroy, onMount } from "svelte";
  import { getApi } from "$lib/api";
  import RoleBasedMenu from "$lib/RoleBasedMenu.svelte";
  import UserMenu from "$lib/UserMenu.svelte";

  type PacienteResumo = {
    solicitacaoId: number;
    nomePaciente: string;
    cpfPaciente: string;
    usfOrigem: string;
  };

  type PacientePage = {
    content: PacienteResumo[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
  };

  let isLoading = $state(true);
  let error = $state<string | null>(null);
  let usingServerPagination = $state(true);
  let allPacientes = $state<PacienteResumo[]>([]);
  let totalPacientes = $state(0);
  let totalPages = $state(0);
  let currentPage = $state(1);
  const itemsPerPage = 20;
  let pacientes = $derived(
    usingServerPagination
      ? allPacientes
      : allPacientes.slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage)
  );
  let buscar = $state("");
  let searchTimeout: ReturnType<typeof setTimeout> | null = null;
  let lastFetchToken: symbol | null = null;

  const DEBOUNCE_MS = 300;

  function triggerFetch(pageToLoad: number) {
    const safePage = Math.max(pageToLoad, 1);
    currentPage = safePage;
    carregarPacientes(safePage);
  }

  async function carregarPacientes(pageToLoad: number) {
    isLoading = true;
    error = null;
    const token = Symbol();
    lastFetchToken = token;

    const params = new URLSearchParams({
      page: String(Math.max(pageToLoad - 1, 0)),
      size: String(itemsPerPage)
    });

    const termo = buscar.trim();
    if (termo) {
      params.set("search", termo);
    }

    try {
      const response = await getApi(`solicitacoes/pacientes/gel?${params.toString()}`);

      if (!response.ok) {
        throw new Error("Falha ao carregar a lista de pacientes.");
      }

      const payload: PacientePage | PacienteResumo[] = await response.json();

      if (lastFetchToken !== token) {
        return;
      }

      if (Array.isArray(payload)) {
        usingServerPagination = false;
        allPacientes = payload;
        totalPacientes = payload.length;
        const paginas = payload.length > 0 ? Math.ceil(payload.length / itemsPerPage) : 0;
        totalPages = paginas;
        currentPage = paginas > 0 ? Math.min(currentPage, paginas) : 1;
      } else {
        usingServerPagination = true;
        allPacientes = payload.content ?? [];
        totalPacientes = payload.totalElements ?? allPacientes.length;
        const paginas = payload.totalPages ?? (allPacientes.length > 0 ? 1 : 0);
        totalPages = paginas;
        const paginaAtual = (payload.number ?? Math.max(pageToLoad - 1, 0)) + 1;
        currentPage = paginas > 0 ? Math.min(paginaAtual, paginas) : 1;
      }
    } catch (err) {
      if (lastFetchToken !== token) {
        return;
      }

      error = err instanceof Error ? err.message : "Erro inesperado ao carregar pacientes.";
      allPacientes = [];
      usingServerPagination = true;
      totalPacientes = 0;
      totalPages = 0;
      currentPage = 1;
    } finally {
      if (lastFetchToken === token) {
        isLoading = false;
      }
    }
  }

  function handleSearch(event: Event) {
    const target = event.target as HTMLInputElement;
    buscar = target.value;

    if (searchTimeout) {
      clearTimeout(searchTimeout);
    }

    searchTimeout = setTimeout(() => {
      triggerFetch(1);
    }, DEBOUNCE_MS);
  }

  function prevPage() {
    if (currentPage <= 1 || isLoading) {
      return;
    }

    if (usingServerPagination) {
      triggerFetch(currentPage - 1);
    } else {
      currentPage = currentPage - 1;
    }
  }

  function nextPage() {
    if (currentPage >= totalPages || isLoading) {
      return;
    }

    if (usingServerPagination) {
      triggerFetch(currentPage + 1);
    } else {
      currentPage = currentPage + 1;
    }
  }

  onMount(() => {
    triggerFetch(1);
  });

  onDestroy(() => {
    if (searchTimeout) {
      clearTimeout(searchTimeout);
    }
    lastFetchToken = null;
  });
</script>

<svelte:head>
  <title>Pacientes</title>
</svelte:head>

<div class="flex min-h-screen bg-gray-100">
  <RoleBasedMenu activePage="/paciente" />

  <div class="flex-1 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Pacientes</h1>
      <UserMenu />
    </header>

    <main class="flex-1 overflow-auto p-6">
      <div class="bg-white rounded-lg shadow-lg p-6 space-y-6">
        <div class="flex flex-col md:flex-row md:justify-between md:items-center">
          <h2 class="text-2xl font-bold text-emerald-800 mb-4 md:mb-0">Lista de Pacientes Cadastrados</h2>
          <div class="flex w-full md:w-1/2">
            <input
              type="text"
              placeholder="Buscar por nome, CPF, USF..."
              bind:value={buscar}
              oninput={handleSearch}
              class="flex-1 border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500"
            />
          </div>
        </div>

        {#if isLoading}
          <div class="text-center text-gray-500 py-10">
            <p>Carregando pacientes...</p>
          </div>
        {:else if error}
          <div class="text-center text-red-600 bg-red-100 p-4 rounded-lg">
            <p><strong>Erro ao carregar dados:</strong> {error}</p>
          </div>
        {:else}
          <p class="text-gray-600">Total de pacientes encontrados: {totalPacientes}</p>

          {#if totalPacientes === 0}
            {#if buscar.trim()}
              <p class="text-center text-gray-500 py-10">Nenhum paciente encontrado para "{buscar}".</p>
            {:else}
              <p class="text-center text-gray-500 py-10">Nenhum paciente cadastrado no sistema.</p>
            {/if}
          {:else}
            <ul class="space-y-4">
              {#each pacientes as p, idx (p.cpfPaciente)}
                <li class="bg-white border border-gray-200 rounded-lg p-4 hover:shadow-md transition flex items-center">
                  <div class="text-emerald-700 font-bold text-xl mr-4">
                    {(currentPage - 1) * itemsPerPage + idx + 1}.
                  </div>
                  <div class="flex-1">
                    <a
                      href={`/paciente/${p.solicitacaoId}`}
                      class="block hover:underline"
                      title="Ver detalhes e histórico do paciente"
                    >
                      <h3 class="text-lg font-bold text-gray-800 mb-2">{p.nomePaciente}</h3>
                    </a>
                    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-2 text-sm">
                      <div><span class="font-semibold text-gray-600">CPF:</span> {p.cpfPaciente}</div>
                      <div><span class="font-semibold text-gray-600">USF:</span> {p.usfOrigem}</div>
                    </div>
                  </div>
                </li>
              {/each}
            </ul>

            {#if totalPages > 1}
              <div class="flex justify-center items-center space-x-2 mt-6">
                <button
                  onclick={prevPage}
                  class="px-3 py-1 bg-emerald-600 hover:bg-emerald-800 cursor-pointer text-white rounded disabled:opacity-50 disabled:cursor-not-allowed transition"
                  disabled={currentPage === 1 || isLoading}
                >
                  &laquo; Anterior
                </button>
                <span class="text-gray-700">
                  Página {currentPage} de {totalPages}
                </span>
                <button
                  onclick={nextPage}
                  class="px-3 py-1 bg-emerald-600 hover:bg-emerald-800 cursor-pointer text-white rounded disabled:opacity-50 disabled:cursor-not-allowed transition"
                  disabled={currentPage === totalPages || isLoading}
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
