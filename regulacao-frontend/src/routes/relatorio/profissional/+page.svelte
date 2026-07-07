<script lang="ts">
  import { onMount } from "svelte";
  import { getApi } from "$lib/api";
  import Menu from "$lib/Menu.svelte";
  import UserMenu from "$lib/UserMenu.svelte";

  type Unidade = { id: number; nome: string };
  type Profissional = { id: number; nome: string };

  type Detalhe = {
    profissionalId: number;
    profissionalNome: string;
    conselho: string | null;
    numeroRegistro: string | null;
    tipo: "ESPECIALIDADE_MEDICA" | "EXAME_OU_PROCEDIMENTO";
    especialidadeNome: string;
    pacienteNome: string;
    cpfPaciente: string;
    dataSolicitacao: string;
  };

  type Quantitativo = {
    profissionalId: number;
    profissionalNome: string;
    conselho: string | null;
    numeroRegistro: string | null;
    totalNoPeriodo: number;
    consultas: number;
    examesProcedimentos: number;
  };

  function primeiroDiaDoMes() {
    const d = new Date();
    return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().split("T")[0];
  }
  function hoje() {
    return new Date().toISOString().split("T")[0];
  }

  // --- Filtros ---
  let inicio = $state(primeiroDiaDoMes());
  let fim = $state(hoje());
  let unidadeId = $state<string>("");
  let profissionalId = $state<string>("");

  let unidades = $state<Unidade[]>([]);
  let profissionais = $state<Profissional[]>([]);

  // --- Quantitativo ---
  let quantitativo = $state<Quantitativo[]>([]);
  let carregandoQuantitativo = $state(false);
  let exportandoQuantitativo = $state(false);

  // --- Accordion de solicitações por profissional (dentro do Quantitativo) ---
  let profissionalExpandidoId = $state<number | null>(null);
  let solicitacoesPorProfissional = $state<Record<number, Detalhe[]>>({});
  let carregandoSolicitacoesProfissional = $state<Record<number, boolean>>({});

  async function alternarExpansaoProfissional(prof: Quantitativo) {
    if (profissionalExpandidoId === prof.profissionalId) {
      profissionalExpandidoId = null;
      return;
    }
    profissionalExpandidoId = prof.profissionalId;
    if (solicitacoesPorProfissional[prof.profissionalId]) {
      return;
    }
    carregandoSolicitacoesProfissional = { ...carregandoSolicitacoesProfissional, [prof.profissionalId]: true };
    try {
      const params = queryComum();
      params.set("profissionalId", String(prof.profissionalId));
      params.set("page", "0");
      params.set("size", "100");
      const res = await getApi(`relatorio/profissional/detalhado?${params}`);
      const data = res.ok ? await res.json() : { content: [] };
      solicitacoesPorProfissional = { ...solicitacoesPorProfissional, [prof.profissionalId]: data?.content ?? [] };
    } catch {
      solicitacoesPorProfissional = { ...solicitacoesPorProfissional, [prof.profissionalId]: [] };
    } finally {
      carregandoSolicitacoesProfissional = { ...carregandoSolicitacoesProfissional, [prof.profissionalId]: false };
    }
  }

  // --- Detalhado ---
  let detalhes = $state<Detalhe[]>([]);
  let paginaAtual = $state(0);
  let totalPaginas = $state(0);
  let totalElementos = $state(0);
  let carregandoDetalhado = $state(false);
  let exportandoDetalhado = $state(false);

  function tipoLabel(tipo: string) {
    return tipo === "ESPECIALIDADE_MEDICA" ? "Consulta" : "Exame/Procedimento";
  }

  function formatarData(iso: string) {
    if (!iso) return "-";
    const d = new Date(iso);
    return d.toLocaleString("pt-BR", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });
  }

  function queryComum() {
    const params = new URLSearchParams();
    if (inicio) params.set("inicio", inicio);
    if (fim) params.set("fim", fim);
    if (unidadeId) params.set("unidadeId", unidadeId);
    return params;
  }

  async function carregarUnidades() {
    try {
      const res = await getApi("unidades/ativas");
      unidades = res.ok ? await res.json() : [];
    } catch {
      unidades = [];
    }
  }

  async function carregarProfissionais() {
    try {
      const res = await getApi("profissionais/buscar?page=0&size=1000");
      const data = res.ok ? await res.json() : { content: [] };
      profissionais = data?.content ?? [];
    } catch {
      profissionais = [];
    }
  }

  async function buscarQuantitativo() {
    if (!inicio || !fim) {
      alert("Selecione o período (início e fim) para consultar o quantitativo.");
      return;
    }
    carregandoQuantitativo = true;
    try {
      const params = queryComum();
      const res = await getApi(`relatorio/profissional/quantitativo?${params}`);
      quantitativo = res.ok ? await res.json() : [];
    } catch {
      quantitativo = [];
      alert("Erro ao carregar o quantitativo de solicitações.");
    } finally {
      carregandoQuantitativo = false;
    }
  }

  async function buscarDetalhado(pagina = 0) {
    carregandoDetalhado = true;
    try {
      const params = queryComum();
      if (profissionalId) params.set("profissionalId", profissionalId);
      params.set("page", String(pagina));
      params.set("size", "20");
      const res = await getApi(`relatorio/profissional/detalhado?${params}`);
      const data = res.ok ? await res.json() : { content: [], totalPages: 0, totalElements: 0 };
      detalhes = data?.content ?? [];
      totalPaginas = data?.totalPages ?? 0;
      totalElementos = data?.totalElements ?? 0;
      paginaAtual = pagina;
    } catch {
      detalhes = [];
      alert("Erro ao carregar o relatório detalhado.");
    } finally {
      carregandoDetalhado = false;
    }
  }

  function aplicarFiltros() {
    buscarQuantitativo();
    buscarDetalhado(0);
  }

  async function baixarExcel(url: string, filename: string, setLoading: (v: boolean) => void) {
    setLoading(true);
    try {
      const res = await getApi(url);
      if (!res.ok) throw new Error("Falha ao gerar planilha.");
      const blob = await res.blob();
      const objUrl = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = objUrl;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(objUrl);
    } catch (e) {
      console.error(e);
      alert("Erro ao gerar a planilha. Tente novamente.");
    } finally {
      setLoading(false);
    }
  }

  function exportarQuantitativo() {
    if (!inicio || !fim) {
      alert("Selecione o período para exportar o quantitativo.");
      return;
    }
    baixarExcel(
      `relatorio/profissional/quantitativo/excel?${queryComum()}`,
      `quantitativo_solicitacoes_por_profissional_${inicio}_a_${fim}.xlsx`,
      (v) => (exportandoQuantitativo = v)
    );
  }

  function exportarDetalhado() {
    const params = queryComum();
    if (profissionalId) params.set("profissionalId", profissionalId);
    baixarExcel(
      `relatorio/profissional/detalhado/excel?${params}`,
      `solicitacoes_por_profissional.xlsx`,
      (v) => (exportandoDetalhado = v)
    );
  }

  onMount(async () => {
    await Promise.all([carregarUnidades(), carregarProfissionais()]);
    aplicarFiltros();
  });
</script>

<svelte:head>
  <title>Relatório de Solicitações por Profissional</title>
</svelte:head>

<div class="flex min-h-screen bg-gray-100">
  <Menu activePage="/relatorio/profissional" />

  <div class="flex-1 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Relatório de Solicitações por Profissional</h1>
      <UserMenu />
    </header>

    <main class="flex-1 p-6 overflow-auto">
      <div class="max-w-6xl mx-auto space-y-8">

        <!-- Filtros -->
        <section class="bg-white p-4 rounded-lg shadow-md">
          <div class="grid grid-cols-1 md:grid-cols-5 gap-3 items-end">
            <div class="flex flex-col">
              <label class="text-xs text-gray-500 mb-1" for="f-inicio">De</label>
              <input id="f-inicio" type="date" bind:value={inicio} class="border rounded px-2 py-1.5 text-sm" />
            </div>
            <div class="flex flex-col">
              <label class="text-xs text-gray-500 mb-1" for="f-fim">Até</label>
              <input id="f-fim" type="date" bind:value={fim} class="border rounded px-2 py-1.5 text-sm" />
            </div>
            <div class="flex flex-col">
              <label class="text-xs text-gray-500 mb-1" for="f-unidade">Unidade</label>
              <select id="f-unidade" bind:value={unidadeId} class="border rounded px-2 py-1.5 text-sm">
                <option value="">Todas</option>
                {#each unidades as u}
                  <option value={u.id}>{u.nome}</option>
                {/each}
              </select>
            </div>
            <div class="flex flex-col">
              <label class="text-xs text-gray-500 mb-1" for="f-profissional">Profissional (detalhado)</label>
              <select id="f-profissional" bind:value={profissionalId} class="border rounded px-2 py-1.5 text-sm">
                <option value="">Todos</option>
                {#each profissionais as p}
                  <option value={p.id}>{p.nome}</option>
                {/each}
              </select>
            </div>
            <button
              onclick={aplicarFiltros}
              class="px-4 py-2 bg-emerald-700 text-white rounded text-sm hover:bg-emerald-800 transition-colors">
              Filtrar
            </button>
          </div>
        </section>

        <!-- Quantitativo -->
        <section class="bg-white rounded-lg shadow-md p-6">
          <div class="flex items-center justify-between flex-wrap gap-3 mb-4">
            <div>
              <h2 class="text-xl font-bold text-gray-800">Quantitativo por Profissional</h2>
              <p class="text-sm text-gray-500">Total de solicitações no período, separado por consultas e exames/procedimentos.</p>
            </div>
            <button
              onclick={exportarQuantitativo}
              disabled={exportandoQuantitativo || quantitativo.length === 0}
              class="px-4 py-2 bg-teal-600 text-white rounded text-sm hover:bg-teal-700 disabled:opacity-50 transition-colors">
              {exportandoQuantitativo ? "Gerando..." : "Exportar Excel"}
            </button>
          </div>

          {#if carregandoQuantitativo}
            <p class="text-center text-gray-400 py-8">Carregando...</p>
          {:else if quantitativo.length === 0}
            <p class="text-center text-gray-400 py-8">Nenhum dado encontrado para o período selecionado.</p>
          {:else}
            <div class="overflow-x-auto">
              <table class="w-full text-sm">
                <thead>
                  <tr class="bg-gray-50 text-left text-gray-600 uppercase text-xs">
                    <th class="p-2 w-6"></th>
                    <th class="p-2">Profissional</th>
                    <th class="p-2">Conselho/Registro</th>
                    <th class="p-2 text-center">Total no Período</th>
                    <th class="p-2 text-center">Consultas</th>
                    <th class="p-2 text-center">Exames/Procedimentos</th>
                  </tr>
                </thead>
                <tbody>
                  {#each quantitativo as q (q.profissionalId)}
                    <tr
                      class="border-b border-gray-100 hover:bg-gray-50 cursor-pointer"
                      onclick={() => alternarExpansaoProfissional(q)}>
                      <td class="p-2 text-center text-gray-400">
                        {profissionalExpandidoId === q.profissionalId ? "▾" : "▸"}
                      </td>
                      <td class="p-2 font-medium text-gray-800">{q.profissionalNome}</td>
                      <td class="p-2 text-gray-500">{[q.conselho, q.numeroRegistro].filter(Boolean).join(" ") || "-"}</td>
                      <td class="p-2 text-center font-bold text-emerald-700">{q.totalNoPeriodo}</td>
                      <td class="p-2 text-center">{q.consultas}</td>
                      <td class="p-2 text-center">{q.examesProcedimentos}</td>
                    </tr>
                    {#if profissionalExpandidoId === q.profissionalId}
                      <tr class="bg-gray-50">
                        <td colspan="6" class="p-3">
                          {#if carregandoSolicitacoesProfissional[q.profissionalId]}
                            <p class="text-center text-gray-400 py-4">Carregando solicitações...</p>
                          {:else if (solicitacoesPorProfissional[q.profissionalId]?.length ?? 0) === 0}
                            <p class="text-center text-gray-400 py-4">Nenhuma solicitação encontrada para este profissional no período.</p>
                          {:else}
                            <div class="overflow-x-auto">
                              <table class="w-full text-xs bg-white rounded border border-gray-200">
                                <thead>
                                  <tr class="bg-gray-100 text-left text-gray-500 uppercase">
                                    <th class="p-2">Tipo</th>
                                    <th class="p-2">Exame/Procedimento/Consulta</th>
                                    <th class="p-2">Paciente</th>
                                    <th class="p-2">CPF</th>
                                    <th class="p-2">Data da Solicitação</th>
                                  </tr>
                                </thead>
                                <tbody>
                                  {#each solicitacoesPorProfissional[q.profissionalId] as d, i (i)}
                                    <tr class="border-b border-gray-100">
                                      <td class="p-2">{tipoLabel(d.tipo)}</td>
                                      <td class="p-2">{d.especialidadeNome}</td>
                                      <td class="p-2">{d.pacienteNome}</td>
                                      <td class="p-2">{d.cpfPaciente}</td>
                                      <td class="p-2">{formatarData(d.dataSolicitacao)}</td>
                                    </tr>
                                  {/each}
                                </tbody>
                              </table>
                            </div>
                            {#if solicitacoesPorProfissional[q.profissionalId].length >= 100}
                              <p class="text-xs text-gray-400 mt-2">
                                Mostrando as 100 solicitações mais recentes. Use o filtro "Profissional (detalhado)" abaixo para ver a lista completa paginada.
                              </p>
                            {/if}
                          {/if}
                        </td>
                      </tr>
                    {/if}
                  {/each}
                </tbody>
              </table>
            </div>
          {/if}
        </section>

        <!-- Detalhado -->
        <section class="bg-white rounded-lg shadow-md p-6">
          <div class="flex items-center justify-between flex-wrap gap-3 mb-4">
            <div>
              <h2 class="text-xl font-bold text-gray-800">Detalhado por Solicitação</h2>
              <p class="text-sm text-gray-500">Cada linha é uma solicitação (exame, procedimento ou consulta) atribuída a um profissional.</p>
            </div>
            <button
              onclick={exportarDetalhado}
              disabled={exportandoDetalhado || detalhes.length === 0}
              class="px-4 py-2 bg-teal-600 text-white rounded text-sm hover:bg-teal-700 disabled:opacity-50 transition-colors">
              {exportandoDetalhado ? "Gerando..." : "Exportar Excel"}
            </button>
          </div>

          {#if carregandoDetalhado}
            <p class="text-center text-gray-400 py-8">Carregando...</p>
          {:else if detalhes.length === 0}
            <p class="text-center text-gray-400 py-8">Nenhuma solicitação encontrada para os filtros selecionados.</p>
          {:else}
            <div class="overflow-x-auto">
              <table class="w-full text-sm">
                <thead>
                  <tr class="bg-gray-50 text-left text-gray-600 uppercase text-xs">
                    <th class="p-2">Profissional</th>
                    <th class="p-2">Tipo</th>
                    <th class="p-2">Exame/Procedimento/Consulta</th>
                    <th class="p-2">Paciente</th>
                    <th class="p-2">CPF</th>
                    <th class="p-2">Data da Solicitação</th>
                  </tr>
                </thead>
                <tbody>
                  {#each detalhes as d, i (i)}
                    <tr class="border-b border-gray-100 hover:bg-gray-50">
                      <td class="p-2 font-medium text-gray-800">{d.profissionalNome}</td>
                      <td class="p-2">{tipoLabel(d.tipo)}</td>
                      <td class="p-2">{d.especialidadeNome}</td>
                      <td class="p-2">{d.pacienteNome}</td>
                      <td class="p-2">{d.cpfPaciente}</td>
                      <td class="p-2">{formatarData(d.dataSolicitacao)}</td>
                    </tr>
                  {/each}
                </tbody>
              </table>
            </div>

            <div class="flex items-center justify-between mt-4 text-sm text-gray-500">
              <span>{totalElementos} solicitações no total</span>
              <div class="flex items-center gap-2">
                <button
                  onclick={() => buscarDetalhado(paginaAtual - 1)}
                  disabled={paginaAtual === 0}
                  class="px-3 py-1 border rounded disabled:opacity-40">
                  Anterior
                </button>
                <span>Página {paginaAtual + 1} de {Math.max(totalPaginas, 1)}</span>
                <button
                  onclick={() => buscarDetalhado(paginaAtual + 1)}
                  disabled={paginaAtual + 1 >= totalPaginas}
                  class="px-3 py-1 border rounded disabled:opacity-40">
                  Próxima
                </button>
              </div>
            </div>
          {/if}
        </section>

      </div>
    </main>
  </div>
</div>
