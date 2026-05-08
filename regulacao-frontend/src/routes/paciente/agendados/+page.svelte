<script lang="ts">
  import { onDestroy, onMount } from "svelte";
  import { getApi, patchApi } from "$lib/api";
  import type { PacienteProjection } from "$lib/models/PacienteProjection";
  import Menu from "$lib/Menu.svelte";
  import UserMenu from "$lib/UserMenu.svelte";
  import { toast } from "svelte-sonner";

  type PacientePage = {
    content: PacienteProjection[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
  };

  let isLoading = $state(true);
  let isInitialLoad = $state(true);
  let error = $state<string | null>(null);
  let usingServerPagination = $state(true);
  let allPacientes = $state<PacienteProjection[]>([]);
  let totalElements = $state(0);
  let totalPages = $state(0);
  let currentPage = $state(1);
  const itemsPerPage = 20;

  let pacientes = $derived(
    usingServerPagination
      ? allPacientes
      : allPacientes.slice((currentPage - 1) * itemsPerPage, currentPage * itemsPerPage)
  );

  let termo = $state("");
  let searchTimeout: ReturnType<typeof setTimeout> | null = null;
  let lastFetchToken: symbol | null = null;

  const DEBOUNCE_MS = 300;

  function formatarData(dataString: string | null): string {
    if (!dataString) return "N/A";
    const data = new Date(dataString);
    data.setDate(data.getDate() + 1);
    return data.toLocaleDateString("pt-BR");
  }

  function triggerFetch(pageToLoad: number) {
    currentPage = Math.max(pageToLoad, 1);
    carregarAgendados(currentPage);
  }

  async function carregarAgendados(pageToLoad: number) {
    isLoading = true;
    error = null;
    const token = Symbol();
    lastFetchToken = token;

    const params = new URLSearchParams({
      page: String(Math.max(pageToLoad - 1, 0)),
      size: String(itemsPerPage),
      termo: termo.trim()
    });

    try {
      const res = await getApi(`solicitacoes/buscar/por/agendados?${params.toString()}`);

      if (!res.ok) throw new Error("Falha ao carregar a lista de agendados.");

      const payload: PacientePage | PacienteProjection[] = await res.json();

      if (lastFetchToken !== token) return;

      if (Array.isArray(payload)) {
        usingServerPagination = false;
        allPacientes = payload;
        totalElements = payload.length;
        const paginas = payload.length > 0 ? Math.ceil(payload.length / itemsPerPage) : 0;
        totalPages = paginas;
        currentPage = paginas > 0 ? Math.min(currentPage, paginas) : 1;
      } else {
        usingServerPagination = true;
        allPacientes = payload.content ?? [];
        totalElements = payload.totalElements ?? allPacientes.length;
        const paginas = payload.totalPages ?? (allPacientes.length > 0 ? 1 : 0);
        totalPages = paginas;
        const paginaAtual = (payload.number ?? Math.max(pageToLoad - 1, 0)) + 1;
        currentPage = paginas > 0 ? Math.min(paginaAtual, paginas) : 1;
      }
    } catch (err) {
      if (lastFetchToken !== token) return;
      error = err instanceof Error ? err.message : "Erro inesperado ao carregar agendados.";
      allPacientes = [];
      usingServerPagination = true;
      totalElements = 0;
      totalPages = 0;
      currentPage = 1;
    } finally {
      if (lastFetchToken === token) {
        isLoading = false;
        isInitialLoad = false;
      }
    }
  }

  function handleSearch(event: Event) {
    termo = (event.target as HTMLInputElement).value;
    if (searchTimeout) clearTimeout(searchTimeout);
    searchTimeout = setTimeout(() => triggerFetch(1), DEBOUNCE_MS);
  }

  function prevPage() {
    if (currentPage <= 1 || isLoading) return;
    usingServerPagination ? triggerFetch(currentPage - 1) : (currentPage -= 1);
  }

  function nextPage() {
    if (currentPage >= totalPages || isLoading) return;
    usingServerPagination ? triggerFetch(currentPage + 1) : (currentPage += 1);
  }

  async function confirmarPresenca(idEspecialidade: number) {
    await toast.promise(
      (async () => {
        const res = await patchApi(`especialidades/${idEspecialidade}/realizado`);
        if (!res.ok) throw new Error("Falha ao confirmar a presença.");
        await carregarAgendados(currentPage);
      })(),
      {
        loading: "Confirmando...",
        success: "Presença confirmada!",
        error: "Falha ao confirmar a presença."
      }
    );
  }

  async function faltouPresenca(idEspecialidade: number) {
    await toast.promise(
      (async () => {
        const res = await patchApi(`especialidades/${idEspecialidade}/faltou`);
        if (!res.ok) throw new Error("Falha ao registrar a falta.");
        await carregarAgendados(currentPage);
      })(),
      {
        loading: "Registrando...",
        success: "Falta registrada com sucesso!",
        error: "Falha ao registrar a falta."
      }
    );
  }

  onMount(() => {
    toast.promise(carregarAgendados(1), {
      loading: "Carregando agendados...",
      success: "Dados carregados!",
      error: "Erro ao carregar os dados."
    });
  });

  onDestroy(() => {
    if (searchTimeout) clearTimeout(searchTimeout);
    lastFetchToken = null;
  });
</script>

<svelte:head>
  <title>Pacientes Agendados</title>
</svelte:head>

<div class="flex min-h-screen bg-gray-100">
  <Menu activePage="/paciente/agendados" />

  <div class="flex-1 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Pacientes Agendados</h1>
      <UserMenu />
    </header>

    <main class="flex-1 overflow-auto p-6">
      <div class="bg-white rounded-lg shadow-lg p-6 space-y-6">
        <div class="flex flex-col md:flex-row md:justify-between md:items-center">
          <h2 class="text-2xl font-bold text-emerald-800 mb-4 md:mb-0">Lista de Pacientes Agendados</h2>
          <div class="flex w-full md:w-1/2">
            <input
              type="text"
              placeholder="Buscar por nome, CPF, especialidade..."
              bind:value={termo}
              oninput={handleSearch}
              class="flex-1 border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500"
            />
          </div>
        </div>

        {#if isInitialLoad && isLoading}
          <div class="text-center text-gray-500 py-10">
            <p>Carregando agendados...</p>
          </div>
        {:else if error}
          <div class="text-center text-red-600 bg-red-100 p-4 rounded-lg">
            <p><strong>Erro ao carregar dados:</strong> {error}</p>
          </div>
        {:else}
          <p class="text-gray-600">
            Total: {totalElements}
            {#if isLoading}<span class="text-sm text-emerald-600 ml-2">Atualizando...</span>{/if}
          </p>

          {#if totalElements === 0}
            <p class="text-center text-gray-500 py-10">
              {#if termo.trim()}
                Nenhum agendamento encontrado para "{termo}".
              {:else}
                Nenhum paciente agendado no momento.
              {/if}
            </p>
          {:else}
            <ul class="space-y-4">
              {#each pacientes as s, idx (s.solicitacaoEspecialidadeId)}
                <li class="bg-white border border-gray-200 rounded-lg p-4 hover:shadow-md transition flex items-start">
                  <div class="text-emerald-700 font-bold text-xl mr-4">
                    {(currentPage - 1) * itemsPerPage + idx + 1}.
                  </div>
                  <div class="flex-1">
                    <a href={`/paciente/${s.id}`} class="block hover:underline">
                      <h3 class="text-lg font-bold text-gray-800 mb-2">{s.nomePaciente}</h3>
                    </a>
                    <div class="grid grid-cols-1 md:grid-cols-3 gap-2 text-sm mb-3">
                      <div><span class="font-semibold text-gray-600">CPF:</span> {s.cpfPaciente}</div>
                      <div><span class="font-semibold text-gray-600">USF:</span> {s.usfOrigem}</div>
                      <div><span class="font-semibold text-gray-600">Nascimento:</span> {formatarData(s.dataNascimento)}</div>
                    </div>

                    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                      <span class="text-xs px-2 py-1 rounded bg-emerald-100 text-emerald-800 font-medium w-fit">
                        {s.especialidade}
                      </span>
                      <div class="flex gap-2">
                        <button
                          onclick={() => confirmarPresenca(s.solicitacaoEspecialidadeId)}
                          class="px-3 py-1.5 text-xs font-medium text-white bg-green-600 rounded-md hover:bg-green-700 focus:outline-none focus:ring-2 focus:ring-offset-1 focus:ring-green-500 transition-colors"
                        >
                          ✓ Realizado
                        </button>
                        <button
                          onclick={() => faltouPresenca(s.solicitacaoEspecialidadeId)}
                          class="px-3 py-1.5 text-xs font-medium text-white bg-red-600 rounded-md hover:bg-red-700 focus:outline-none focus:ring-2 focus:ring-offset-1 focus:ring-red-500 transition-colors"
                        >
                          ✗ Faltou
                        </button>
                      </div>
                    </div>
                  </div>
                </li>
              {/each}
            </ul>

            {#if totalPages > 1}
              <div class="flex justify-center items-center space-x-2 mt-6">
                <button
                  onclick={prevPage}
                  disabled={currentPage === 1 || isLoading}
                  class="px-3 py-1 bg-emerald-600 hover:bg-emerald-800 text-white rounded disabled:opacity-50 disabled:cursor-not-allowed transition"
                >
                  &laquo; Anterior
                </button>
                <span class="text-gray-700">Página {currentPage} de {totalPages}</span>
                <button
                  onclick={nextPage}
                  disabled={currentPage === totalPages || isLoading}
                  class="px-3 py-1 bg-emerald-600 hover:bg-emerald-800 text-white rounded disabled:opacity-50 disabled:cursor-not-allowed transition"
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
