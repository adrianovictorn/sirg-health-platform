<script lang="ts">
  import { onDestroy, onMount } from "svelte";
  import { toast } from "svelte-sonner";
  import UserMenu from "$lib/UserMenu.svelte";
  import RoleBasedMenu from "$lib/RoleBasedMenu.svelte";
  import LoadingSpinner from "$lib/LoadingSpinner.svelte";
  import { listarGrupoRelatorio } from "$lib/especialidadesApi.js";
  import {
    listarCustosEspecialidades,
    atualizarCustoEspecialidade,
    formatarReais,
    lerValorDigitado,
    valorParaCampo
  } from "$lib/custosApi.js";
  import { user } from "$lib/stores/auth.js";

  type EspecialidadeCusto = {
    id: number;
    codigo: string;
    nome: string;
    categoria: string;
    ativo: boolean;
    grupoRelatorioId: number | null;
    grupoRelatorioNome: string | null;
    codigoSus: string | null;
    valorUnitario: number | null;
    valorOrigem: "MANUAL" | "IMPORTACAO" | null;
    valorAtualizadoEm: number[] | string | null;
    valorAtualizadoPorNome: string | null;
  };

  type Opcao = { id: number; nome: string };
  type Edicao = { codigoSus: string; valor: string; salvando: boolean; erro: string | null };

  const ITENS_POR_PAGINA = 50;
  const ESPERA_DA_BUSCA_MS = 300;
  const ROTULO_ORIGEM = { MANUAL: "Digitado", IMPORTACAO: "Importado" };

  // Só ADMIN altera preço; GESTOR vê a mesma tela sem os campos de edição.
  // O backend recusa o PUT de qualquer outro perfil.
  const podeEditar = $derived($user?.role === "ADMIN");

  let nome = $state("");
  let grupoRelatorioId = $state("");
  let somenteSemPreco = $state(false);

  let especialidades = $state<EspecialidadeCusto[]>([]);
  let totalElements = $state(0);
  let totalPages = $state(0);
  let paginaAtual = $state(1);
  let carregando = $state(true);
  let cargaInicial = $state(true);
  let erro = $state<string | null>(null);
  let ultimaBusca: symbol | null = null;
  let temporizadorBusca: ReturnType<typeof setTimeout> | null = null;

  let grupos = $state<Opcao[]>([]);
  // Rascunho de edição por linha, criado quando a linha é carregada.
  let edicoes = $state<Record<number, Edicao>>({});

  const haFiltros = $derived(!!nome.trim() || !!grupoRelatorioId || somenteSemPreco);

  function rascunhoDe(e: EspecialidadeCusto): Edicao {
    return { codigoSus: e.codigoSus ?? "", valor: valorParaCampo(e.valorUnitario), salvando: false, erro: null };
  }

  function alterada(e: EspecialidadeCusto): boolean {
    const rascunho = edicoes[e.id];
    if (!rascunho) return false;
    return rascunho.codigoSus.trim() !== (e.codigoSus ?? "") || rascunho.valor.trim() !== valorParaCampo(e.valorUnitario);
  }

  async function buscar(pagina: number) {
    if (temporizadorBusca) {
      clearTimeout(temporizadorBusca);
      temporizadorBusca = null;
      pagina = 1;
    }
    paginaAtual = Math.max(pagina, 1);
    carregando = true;
    erro = null;
    const estaBusca = Symbol();
    ultimaBusca = estaBusca;

    try {
      const payload = await listarCustosEspecialidades({
        nome: nome.trim(),
        grupoRelatorioId,
        somenteSemPreco,
        page: paginaAtual - 1,
        size: ITENS_POR_PAGINA
      });
      if (ultimaBusca !== estaBusca) return;
      especialidades = payload.content ?? [];
      totalElements = payload.totalElements ?? especialidades.length;
      totalPages = payload.totalPages ?? 0;
      edicoes = Object.fromEntries(especialidades.map((e) => [e.id, rascunhoDe(e)]));
    } catch (e) {
      if (ultimaBusca !== estaBusca) return;
      erro = e instanceof Error ? e.message : "Erro inesperado ao carregar os preços.";
      especialidades = [];
      totalElements = 0;
      totalPages = 0;
    } finally {
      if (ultimaBusca === estaBusca) {
        carregando = false;
        cargaInicial = false;
      }
    }
  }

  function aoDigitarBusca() {
    if (temporizadorBusca) clearTimeout(temporizadorBusca);
    temporizadorBusca = setTimeout(() => buscar(1), ESPERA_DA_BUSCA_MS);
  }

  function limparFiltros() {
    nome = "";
    grupoRelatorioId = "";
    somenteSemPreco = false;
    buscar(1);
  }

  async function salvar(e: EspecialidadeCusto) {
    const rascunho = edicoes[e.id];
    if (!rascunho || rascunho.salvando) return;

    const valor = lerValorDigitado(rascunho.valor);
    if (Number.isNaN(valor)) {
      rascunho.erro = "Valor inválido. Use o formato 12,34.";
      return;
    }
    const digitos = rascunho.codigoSus.replace(/\D/g, "");
    if (rascunho.codigoSus.trim() && digitos.length !== 10 && digitos.length !== 9) {
      rascunho.erro = "O código SUS tem 10 dígitos.";
      return;
    }

    rascunho.erro = null;
    rascunho.salvando = true;
    try {
      const atualizada: EspecialidadeCusto = await atualizarCustoEspecialidade(e.id, {
        codigoSus: rascunho.codigoSus.trim() || null,
        valorUnitario: valor
      });
      especialidades = especialidades.map((item) => (item.id === atualizada.id ? atualizada : item));
      edicoes[atualizada.id] = rascunhoDe(atualizada);
      toast.success(`Preço de ${atualizada.nome} salvo.`);
    } catch (falha) {
      rascunho.erro = falha instanceof Error ? falha.message : "Não foi possível salvar.";
      rascunho.salvando = false;
    }
  }

  function desfazer(e: EspecialidadeCusto) {
    edicoes[e.id] = rascunhoDe(e);
  }

  function formatarDataHora(data: number[] | string | null): string {
    if (!data) return "";
    if (Array.isArray(data)) {
      const [ano, mes, dia] = data;
      return new Date(ano, mes - 1, dia).toLocaleDateString("pt-BR");
    }
    const [ano, mes, dia] = data.slice(0, 10).split("-").map(Number);
    return new Date(ano, mes - 1, dia).toLocaleDateString("pt-BR");
  }

  onMount(() => {
    buscar(1);
    listarGrupoRelatorio()
      .then((lista: Opcao[]) => (grupos = (lista ?? []).sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR"))))
      .catch(() => (grupos = []));
  });

  onDestroy(() => {
    if (temporizadorBusca) clearTimeout(temporizadorBusca);
    ultimaBusca = null;
  });
</script>

<svelte:head>
  <title>Preços das Especialidades</title>
</svelte:head>

<div class="flex min-h-screen bg-gray-100">
  <RoleBasedMenu activePage="/custos/especialidades" />

  <div class="flex-1 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Preços das Especialidades</h1>
      <UserMenu />
    </header>

    <main class="flex-1 overflow-auto p-4 md:p-6 space-y-6">
      <section class="bg-white rounded-lg shadow p-4 md:p-6 space-y-5" aria-label="Filtros de preços">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h2 class="text-xs font-semibold text-gray-700 uppercase tracking-widest">Filtros</h2>
          <button
            type="button"
            onclick={limparFiltros}
            disabled={!haFiltros}
            class="text-sm font-medium px-3 py-1.5 rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:opacity-50 disabled:cursor-not-allowed transition"
          >
            Limpar filtros
          </button>
        </div>

        <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4 items-end">
          <div>
            <label for="precos-busca" class="block text-sm font-semibold text-gray-700 mb-1">
              Especialidade, exame ou código SUS
            </label>
            <input
              id="precos-busca"
              type="search"
              bind:value={nome}
              oninput={aoDigitarBusca}
              maxlength="100"
              autocomplete="off"
              placeholder="Ex.: hemograma ou 0202020380"
              class="w-full border border-gray-300 rounded-lg p-2 bg-white text-gray-900 focus:ring-emerald-500 focus:border-emerald-500"
            />
          </div>

          <div>
            <label for="precos-grupo" class="block text-sm font-semibold text-gray-700 mb-1">Grupo</label>
            <select
              id="precos-grupo"
              bind:value={grupoRelatorioId}
              onchange={() => buscar(1)}
              class="w-full border border-gray-300 rounded-lg p-2 bg-white text-gray-900 focus:outline-hidden focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500"
            >
              <option value="">Todos os grupos</option>
              {#each grupos as g (g.id)}
                <option value={String(g.id)}>{g.nome}</option>
              {/each}
            </select>
          </div>

          <label class="inline-flex items-center gap-2 min-h-[42px] text-sm font-semibold text-gray-700 cursor-pointer">
            <input
              type="checkbox"
              bind:checked={somenteSemPreco}
              onchange={() => buscar(1)}
              class="w-4 h-4 rounded border-gray-400 text-emerald-700 focus:ring-emerald-500"
            />
            Só as que estão sem preço
          </label>
        </div>
      </section>

      <section class="bg-white rounded-lg shadow-lg p-4 md:p-6 space-y-4" aria-label="Preços por especialidade">
        <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <h2 class="text-xl font-bold text-emerald-800">Preço e código SUS</h2>
          {#if podeEditar}
            <a
              href="/admin/custos/importar"
              class="self-start sm:self-auto shrink-0 inline-flex items-center px-4 py-2 text-sm font-medium rounded-lg bg-emerald-700 hover:bg-emerald-800 text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 transition"
            >
              Importar planilha de preços
            </a>
          {/if}
        </div>

        <p class="flex items-start gap-2 text-xs text-gray-700 bg-gray-50 border border-gray-200 rounded-lg px-3 py-2">
          <svg
            class="w-4 h-4 text-gray-500 shrink-0 mt-px"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
            viewBox="0 0 24 24"
            aria-hidden="true"
          >
            <circle cx="12" cy="12" r="9" />
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 11v5m0-8h.01" />
          </svg>
          <span>
            Os preços só aparecem para administrador e gestor. Alterar um preço vale para os próximos
            agendamentos: o que já foi agendado mantém o valor do dia em que foi marcado.
            {#if !podeEditar}Somente o administrador altera preços.{/if}
          </span>
        </p>

        {#if cargaInicial && carregando}
          <LoadingSpinner mensagem="Carregando os preços..." />
        {:else if erro}
          <div
            class="bg-red-50 border-l-4 border-red-500 rounded-r-lg p-4 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 text-red-800"
            role="alert"
          >
            <p><strong>Erro ao carregar os preços:</strong> {erro}</p>
            <button
              type="button"
              onclick={() => buscar(paginaAtual)}
              class="shrink-0 self-start sm:self-auto px-3 py-1.5 text-sm font-medium rounded-lg border border-red-300 bg-white text-red-800 hover:bg-red-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600 transition"
            >
              Tentar novamente
            </button>
          </div>
        {:else}
          <div class="flex flex-wrap items-center gap-x-3 gap-y-1 min-h-6">
            <p class="text-gray-700" aria-live="polite">
              <strong class="text-gray-900">{totalElements}</strong>
              {totalElements === 1 ? "especialidade" : "especialidades"}
            </p>
            {#if carregando}<LoadingSpinner tamanho={14} inline mensagem="Atualizando..." />{/if}
          </div>

          {#if totalElements === 0}
            <div class="flex flex-col items-center text-center gap-3 py-12 px-4">
              <svg
                class="w-10 h-10 text-gray-400"
                fill="none"
                stroke="currentColor"
                stroke-width="1.5"
                viewBox="0 0 24 24"
                aria-hidden="true"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  d="M9 5H7a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2h-2M9 5a2 2 0 0 0 2 2h2a2 2 0 0 0 2-2M9 5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2"
                />
              </svg>
              <p class="text-gray-700 font-medium">
                {haFiltros
                  ? "Nenhuma especialidade encontrada para os filtros escolhidos."
                  : "Nenhuma especialidade cadastrada."}
              </p>
              {#if haFiltros}
                <button
                  type="button"
                  onclick={limparFiltros}
                  class="px-4 py-2 text-sm font-medium rounded-lg bg-emerald-700 hover:bg-emerald-800 text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 transition"
                >
                  Limpar filtros
                </button>
              {/if}
            </div>
          {:else}
            <div class="overflow-x-auto transition-opacity" class:opacity-60={carregando} aria-busy={carregando}>
              <table class="w-full text-sm">
                <thead>
                  <tr class="text-left text-gray-700 border-b border-gray-300">
                    <th scope="col" class="py-2 pr-4 font-semibold min-w-56">Especialidade / exame</th>
                    <th scope="col" class="py-2 px-4 font-semibold min-w-32">Grupo</th>
                    <th scope="col" class="py-2 px-4 font-semibold whitespace-nowrap">Código SUS</th>
                    <th scope="col" class="py-2 px-4 font-semibold whitespace-nowrap" class:text-right={!podeEditar}>Valor unitário</th>
                    <th scope="col" class="py-2 px-4 font-semibold min-w-40">Origem</th>
                    {#if podeEditar}<th scope="col" class="py-2 pl-4 font-semibold"><span class="sr-only">Ações</span></th>{/if}
                  </tr>
                </thead>
                <tbody>
                  {#each especialidades as e (e.id)}
                    {@const rascunho = edicoes[e.id]}
                    {@const recuo = podeEditar ? "pt-3.5 pb-2" : "py-2.5"}
                    <tr class="border-b border-gray-200 align-top hover:bg-gray-50">
                      <th scope="row" class={`${recuo} pr-4 font-medium text-gray-900 text-left`}>
                        {e.nome}
                        {#if e.ativo === false}
                          <span class="inline-block ml-1 text-xs font-semibold px-1.5 py-0.5 rounded bg-gray-200 text-gray-700">Inativa</span>
                        {/if}
                      </th>
                      <td class={`${recuo} px-4 text-gray-800`}>{e.grupoRelatorioNome ?? "—"}</td>

                      {#if podeEditar && rascunho}
                        <td class="py-2 px-4">
                          <label class="sr-only" for={`sus-${e.id}`}>Código SUS de {e.nome}</label>
                          <input
                            id={`sus-${e.id}`}
                            type="text"
                            inputmode="numeric"
                            maxlength="13"
                            bind:value={rascunho.codigoSus}
                            placeholder="10 dígitos"
                            class="w-36 border border-gray-300 rounded-lg p-1.5 text-sm bg-white text-gray-900 tabular-nums focus:ring-emerald-500 focus:border-emerald-500"
                          />
                        </td>
                        <td class="py-2 px-4">
                          <label class="sr-only" for={`valor-${e.id}`}>Valor unitário de {e.nome}</label>
                          <div class="flex items-center gap-1">
                            <span class="text-gray-600 shrink-0" aria-hidden="true">R$</span>
                            <input
                              id={`valor-${e.id}`}
                              type="text"
                              inputmode="decimal"
                              maxlength="14"
                              bind:value={rascunho.valor}
                              placeholder="Sem preço"
                              aria-invalid={!!rascunho.erro}
                              aria-describedby={rascunho.erro ? `erro-${e.id}` : undefined}
                              onkeydown={(evento) => evento.key === "Enter" && salvar(e)}
                              class={`w-28 border rounded-lg p-1.5 text-sm bg-white text-gray-900 text-right tabular-nums ${
                                rascunho.erro
                                  ? "border-red-500 focus:ring-red-500 focus:border-red-500"
                                  : "border-gray-300 focus:ring-emerald-500 focus:border-emerald-500"
                              }`}
                            />
                          </div>
                          {#if rascunho.erro}
                            <p id={`erro-${e.id}`} class="text-xs font-medium text-red-700 mt-1 max-w-44" role="alert">
                              {rascunho.erro}
                            </p>
                          {/if}
                        </td>
                      {:else}
                        <td class={`${recuo} px-4 tabular-nums whitespace-nowrap text-gray-800`}>{e.codigoSus ?? "—"}</td>
                        <td class={`${recuo} px-4 tabular-nums whitespace-nowrap text-gray-900 ${podeEditar ? "" : "text-right"}`}>
                          {#if e.valorUnitario === null}
                            <span class="text-amber-800 font-medium">Sem preço</span>
                          {:else}
                            {formatarReais(e.valorUnitario)}
                          {/if}
                        </td>
                      {/if}

                      <td class={`${recuo} px-4 text-gray-700`}>
                        {#if e.valorOrigem}
                          {ROTULO_ORIGEM[e.valorOrigem]}
                          <span class="block text-xs text-gray-600">
                            {formatarDataHora(e.valorAtualizadoEm)}{e.valorAtualizadoPorNome
                              ? ` · ${e.valorAtualizadoPorNome}`
                              : ""}
                          </span>
                        {:else}
                          —
                        {/if}
                      </td>

                      {#if podeEditar && rascunho}
                        <td class="py-2 pl-4 whitespace-nowrap min-w-44">
                          <button
                            type="button"
                            onclick={() => salvar(e)}
                            disabled={!alterada(e) || rascunho.salvando}
                            class="px-3 py-1.5 text-sm font-medium rounded-lg border border-transparent bg-emerald-700 hover:bg-emerald-800 text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:bg-gray-200 disabled:text-gray-600 disabled:cursor-not-allowed transition"
                          >
                            {rascunho.salvando ? "Salvando..." : "Salvar"}
                          </button>
                          {#if alterada(e) && !rascunho.salvando}
                            <button
                              type="button"
                              onclick={() => desfazer(e)}
                              class="ml-1 px-3 py-1.5 text-sm font-medium rounded-lg border border-gray-300 bg-white text-gray-700 hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 transition"
                            >
                              Desfazer
                            </button>
                          {/if}
                        </td>
                      {/if}
                    </tr>
                  {/each}
                </tbody>
              </table>
            </div>

            {#if totalPages > 1}
              <nav class="flex flex-wrap justify-center items-center gap-3 mt-6" aria-label="Paginação dos preços">
                <button
                  type="button"
                  onclick={() => buscar(paginaAtual - 1)}
                  disabled={paginaAtual === 1 || carregando}
                  class="px-3 py-1 bg-emerald-700 hover:bg-emerald-800 text-white rounded focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:opacity-50 disabled:cursor-not-allowed transition"
                >
                  &laquo; Anterior
                </button>
                <span class="text-gray-700">Página {paginaAtual} de {totalPages}</span>
                <button
                  type="button"
                  onclick={() => buscar(paginaAtual + 1)}
                  disabled={paginaAtual === totalPages || carregando}
                  class="px-3 py-1 bg-emerald-700 hover:bg-emerald-800 text-white rounded focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:opacity-50 disabled:cursor-not-allowed transition"
                >
                  Próximo &raquo;
                </button>
              </nav>
            {/if}
          {/if}
        {/if}
      </section>
    </main>
  </div>
</div>
