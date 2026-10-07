<script lang="ts">
  import { onDestroy, onMount } from "svelte";
  import { afterNavigate, goto } from "$app/navigation";
  import { page } from "$app/state";
  import { toast } from "svelte-sonner";
  import UserMenu from "$lib/UserMenu.svelte";
  import RoleBasedMenu from "$lib/RoleBasedMenu.svelte";
  import LoadingSpinner from "$lib/LoadingSpinner.svelte";
  import { getApi } from "$lib/api";
  import { listarGrupoRelatorio } from "$lib/especialidadesApi.js";
  import {
    listarTetosFinanceiros,
    criarTetosFinanceiros,
    atualizarTetoFinanceiro,
    formatarReais,
    lerValorDigitado,
    valorParaCampo,
    mesAtual
  } from "$lib/custosApi.js";
  import { user } from "$lib/stores/auth.js";

  type Teto = {
    id: number;
    unidadeId: number;
    unidadeNome: string;
    grupoEspecialidadesId: number;
    grupoEspecialidadesNome: string;
    periodo: string;
    valorTotal: number;
    valorUtilizado: number;
    saldoDisponivel: number;
    ativo: boolean;
    version: number;
  };

  type Opcao = { id: number; nome: string; codigo?: string };

  const MES_VALIDO = /^\d{4}-(0[1-9]|1[0-2])$/;

  // Só ADMIN libera e edita teto; GESTOR acompanha. O backend recusa a escrita dos demais.
  const podeEditar = $derived($user?.role === "ADMIN");

  let periodo = $state(MES_VALIDO.test(page.url.searchParams.get("periodo") ?? "") ? page.url.searchParams.get("periodo")! : mesAtual());

  let tetos = $state<Teto[]>([]);
  let carregando = $state(true);
  let cargaInicial = $state(true);
  let erro = $state<string | null>(null);
  let ultimaBusca: symbol | null = null;

  let unidades = $state<Opcao[]>([]);
  let grupos = $state<Opcao[]>([]);

  // ---- Liberação em lote
  let novoGrupoId = $state("");
  let novoValor = $state("");
  let novasUnidades = $state<number[]>([]);
  let liberando = $state(false);
  let erroLiberacao = $state<string | null>(null);

  // ---- Edição de um teto
  let editandoId = $state<number | null>(null);
  let valorEdicao = $state("");
  let salvandoEdicao = $state(false);
  let erroEdicao = $state<string | null>(null);

  const totalLiberado = $derived(tetos.filter((t) => t.ativo).reduce((soma, t) => soma + Number(t.valorTotal), 0));
  const totalUtilizado = $derived(tetos.filter((t) => t.ativo).reduce((soma, t) => soma + Number(t.valorUtilizado), 0));

  // Unidades que já têm teto do grupo escolhido neste mês não podem receber outro.
  const unidadesComTetoDoGrupo = $derived(
    new Set(tetos.filter((t) => String(t.grupoEspecialidadesId) === novoGrupoId).map((t) => t.unidadeId))
  );
  const unidadesLivres = $derived(unidades.filter((u) => !unidadesComTetoDoGrupo.has(u.id)));
  const todasMarcadas = $derived(unidadesLivres.length > 0 && unidadesLivres.every((u) => novasUnidades.includes(u.id)));

  async function buscar() {
    if (!MES_VALIDO.test(periodo)) return;
    carregando = true;
    erro = null;
    editandoId = null;
    const estaBusca = Symbol();
    ultimaBusca = estaBusca;

    goto(periodo !== mesAtual() ? `?periodo=${periodo}` : page.url.pathname, {
      replaceState: true,
      keepFocus: true,
      noScroll: true
    });

    try {
      const lista = await listarTetosFinanceiros(periodo);
      if (ultimaBusca !== estaBusca) return;
      tetos = lista;
      // Marcações de unidades que acabaram de ganhar teto deixam de valer.
      novasUnidades = novasUnidades.filter((id) => !unidadesComTetoDoGrupo.has(id));
    } catch (e) {
      if (ultimaBusca !== estaBusca) return;
      erro = e instanceof Error ? e.message : "Erro inesperado ao carregar os tetos.";
      tetos = [];
    } finally {
      if (ultimaBusca === estaBusca) {
        carregando = false;
        cargaInicial = false;
      }
    }
  }

  function alternarUnidade(id: number) {
    novasUnidades = novasUnidades.includes(id) ? novasUnidades.filter((u) => u !== id) : [...novasUnidades, id];
  }

  function alternarTodas() {
    novasUnidades = todasMarcadas ? [] : unidadesLivres.map((u) => u.id);
  }

  function aoTrocarGrupo() {
    novasUnidades = novasUnidades.filter((id) => !unidadesComTetoDoGrupo.has(id));
    erroLiberacao = null;
  }

  async function liberar(evento: SubmitEvent) {
    evento.preventDefault();
    if (liberando) return;

    const valor = lerValorDigitado(novoValor);
    if (!novoGrupoId) {
      erroLiberacao = "Escolha o grupo de especialidades que debita o teto.";
      return;
    }
    if (valor === null || Number.isNaN(valor)) {
      erroLiberacao = "Informe o valor do teto no formato 1.234,56.";
      return;
    }
    if (novasUnidades.length === 0) {
      erroLiberacao = "Marque ao menos uma unidade.";
      return;
    }

    erroLiberacao = null;
    liberando = true;
    try {
      const criados: Teto[] = await criarTetosFinanceiros({
        unidadeIds: novasUnidades,
        grupoEspecialidadesId: Number(novoGrupoId),
        periodo,
        valorTotal: valor
      });
      toast.success(
        criados.length === 1 ? "Teto liberado para 1 unidade." : `Teto liberado para ${criados.length} unidades.`
      );
      novasUnidades = [];
      novoValor = "";
      await buscar();
    } catch (e) {
      erroLiberacao = e instanceof Error ? e.message : "Não foi possível liberar o teto.";
    } finally {
      liberando = false;
    }
  }

  function editar(t: Teto) {
    editandoId = t.id;
    valorEdicao = valorParaCampo(t.valorTotal);
    erroEdicao = null;
  }

  function cancelarEdicao() {
    editandoId = null;
    erroEdicao = null;
  }

  async function gravar(t: Teto, alteracao: { valorTotal?: number; ativo?: boolean }) {
    salvandoEdicao = true;
    erroEdicao = null;
    try {
      const atualizado: Teto = await atualizarTetoFinanceiro(t.id, {
        valorTotal: alteracao.valorTotal ?? t.valorTotal,
        ativo: alteracao.ativo ?? t.ativo,
        version: t.version
      });
      tetos = tetos.map((item) => (item.id === atualizado.id ? atualizado : item));
      editandoId = null;
      toast.success(`Teto de ${atualizado.unidadeNome} atualizado.`);
    } catch (e) {
      const mensagem = e instanceof Error ? e.message : "Não foi possível salvar o teto.";
      if (editandoId === t.id) erroEdicao = mensagem;
      else toast.error(mensagem);
    } finally {
      salvandoEdicao = false;
    }
  }

  function salvarEdicao(t: Teto) {
    const valor = lerValorDigitado(valorEdicao);
    if (valor === null || Number.isNaN(valor)) {
      erroEdicao = "Informe o valor no formato 1.234,56.";
      return;
    }
    gravar(t, { valorTotal: valor });
  }

  function percentual(t: Teto): number {
    const total = Number(t.valorTotal);
    return total > 0 ? Math.round((Number(t.valorUtilizado) / total) * 100) : Number(t.valorUtilizado) > 0 ? 100 : 0;
  }

  function rotuloMes(mes: string): string {
    const [ano, numero] = mes.split("-").map(Number);
    return new Date(ano, numero - 1, 1).toLocaleDateString("pt-BR", { month: "long", year: "numeric" });
  }

  afterNavigate((navegacao) => {
    if (navegacao.type !== "link") return;
    const mes = page.url.searchParams.get("periodo") ?? "";
    periodo = MES_VALIDO.test(mes) ? mes : mesAtual();
    buscar();
  });

  onMount(async () => {
    buscar();
    listarGrupoRelatorio()
      .then((lista: Opcao[]) => (grupos = (lista ?? []).sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR"))))
      .catch(() => (grupos = []));
    const res = await getApi("unidades/ativas").catch(() => null);
    if (res?.ok) unidades = await res.json();
  });

  onDestroy(() => {
    ultimaBusca = null;
  });
</script>

<svelte:head>
  <title>Tetos Financeiros</title>
</svelte:head>

<div class="flex min-h-screen bg-gray-100">
  <RoleBasedMenu activePage="/custos/tetos" />

  <div class="flex-1 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Tetos Financeiros</h1>
      <UserMenu />
    </header>

    <main class="flex-1 overflow-auto p-4 md:p-6 space-y-6">
      <section class="bg-white rounded-lg shadow p-4 md:p-6" aria-label="Mês do teto">
        <div class="flex flex-col xl:flex-row xl:items-end gap-4 xl:gap-8">
          <div class="sm:max-w-xs xl:w-64 xl:shrink-0">
            <label for="tetos-periodo" class="block text-sm font-semibold text-gray-700 mb-1">Mês</label>
            <input
              id="tetos-periodo"
              type="month"
              bind:value={periodo}
              onchange={buscar}
              class="w-full border border-gray-300 rounded-lg p-2 bg-white text-gray-900 focus:outline-hidden focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500"
            />
          </div>
          <div
            class="flex flex-wrap gap-x-10 gap-y-4 flex-1 border-t border-gray-200 pt-4 xl:border-t-0 xl:pt-0 xl:border-l xl:pl-8"
          >
          <div class="min-w-40">
            <p class="text-sm font-semibold text-gray-600">Liberado no mês</p>
            <p class="text-2xl font-bold text-gray-900 tabular-nums">{formatarReais(totalLiberado)}</p>
          </div>
          <div class="min-w-40">
            <p class="text-sm font-semibold text-gray-600">Utilizado</p>
            <p class="text-2xl font-bold text-gray-900 tabular-nums">{formatarReais(totalUtilizado)}</p>
          </div>
          <div class="min-w-40">
            <p class="text-sm font-semibold text-gray-600">Saldo</p>
            <p
              class={`text-2xl font-bold tabular-nums ${
                totalLiberado - totalUtilizado < 0 ? "text-red-700" : "text-emerald-800"
              }`}
            >
              {formatarReais(totalLiberado - totalUtilizado)}
            </p>
          </div>
          </div>
        </div>
      </section>

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
          O teto limita, em reais, o que a unidade agenda no mês dentro do grupo escolhido. Cada exame agendado
          debita o seu preço e cancelar devolve. Sem saldo, a unidade é bloqueada e vê apenas o aviso de teto
          esgotado, sem valores. Agendamentos feitos pelo administrador ou pelo gestor debitam, mas não são
          bloqueados. Unidade sem teto não tem limite de valor.
        </span>
      </p>

      {#if podeEditar}
        <section class="bg-white rounded-lg shadow p-4 md:p-6 space-y-5" aria-label="Liberar teto">
          <h2 class="text-xl font-bold text-emerald-800">Liberar teto para {rotuloMes(periodo)}</h2>
          <form onsubmit={liberar} class="space-y-5" novalidate>
            <div class="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-4 gap-4 items-end">
              <div class="xl:col-span-2">
                <label for="teto-grupo" class="block text-sm font-semibold text-gray-700">
                  Grupo de especialidades
                </label>
                <p id="teto-grupo-dica" class="text-xs text-gray-600 mb-1">
                  Só as especialidades deste grupo debitam o teto.
                </p>
                <select
                  id="teto-grupo"
                  bind:value={novoGrupoId}
                  onchange={aoTrocarGrupo}
                  aria-describedby="teto-grupo-dica"
                  class="w-full border border-gray-300 rounded-lg p-2 bg-white text-gray-900 focus:outline-hidden focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500"
                >
                  <option value="">Selecione (ex.: Laboratório)</option>
                  {#each grupos as g (g.id)}
                    <option value={String(g.id)}>{g.nome}</option>
                  {/each}
                </select>
              </div>
              <div>
                <label for="teto-valor" class="block text-sm font-semibold text-gray-700 mb-1">
                  Valor por unidade (R$)
                </label>
                <input
                  id="teto-valor"
                  type="text"
                  inputmode="decimal"
                  maxlength="16"
                  bind:value={novoValor}
                  placeholder="Ex.: 5.000,00"
                  class="w-full border border-gray-300 rounded-lg p-2 bg-white text-gray-900 tabular-nums focus:ring-emerald-500 focus:border-emerald-500"
                />
              </div>
            </div>

            <fieldset>
              <legend class="text-sm font-semibold text-gray-700 mb-2">Unidades</legend>
              {#if unidades.length === 0}
                <p class="text-sm text-gray-700">Nenhuma unidade ativa cadastrada.</p>
              {:else if unidadesLivres.length === 0}
                <p class="text-sm text-gray-700">
                  Todas as unidades já têm teto deste grupo em {rotuloMes(periodo)}. Edite os valores na lista abaixo.
                </p>
              {:else}
                <div class="rounded-lg border border-gray-200 p-3 sm:p-4">
                <label
                  class="inline-flex items-center gap-2 py-1 text-sm font-semibold text-gray-800 cursor-pointer"
                >
                  <input
                    type="checkbox"
                    checked={todasMarcadas}
                    onchange={alternarTodas}
                    class="w-4 h-4 rounded border-gray-400 text-emerald-700 focus:ring-emerald-500"
                  />
                  Marcar todas ({unidadesLivres.length})
                </label>
                <div
                  class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4 gap-x-6 gap-y-1 mt-2 pt-3 border-t border-gray-200"
                >
                  {#each unidadesLivres as u (u.id)}
                    <label class="flex items-start gap-2 py-1 text-sm text-gray-800 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={novasUnidades.includes(u.id)}
                        onchange={() => alternarUnidade(u.id)}
                        class="w-4 h-4 mt-0.5 shrink-0 rounded border-gray-400 text-emerald-700 focus:ring-emerald-500"
                      />
                      {u.nome}
                    </label>
                  {/each}
                </div>
                </div>
              {/if}
            </fieldset>

            {#if erroLiberacao}
              <p class="text-sm font-medium text-red-700" role="alert">{erroLiberacao}</p>
            {/if}

            <button
              type="submit"
              disabled={liberando}
              class="px-4 py-2 text-sm font-medium rounded-lg bg-emerald-700 hover:bg-emerald-800 text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:opacity-50 disabled:cursor-not-allowed transition"
            >
              {liberando
                ? "Liberando..."
                : novasUnidades.length > 1
                  ? `Liberar teto para ${novasUnidades.length} unidades`
                  : "Liberar teto"}
            </button>
          </form>
        </section>
      {/if}

      <section class="bg-white rounded-lg shadow-lg p-4 md:p-6 space-y-4" aria-label="Tetos do mês">
        <h2 class="text-xl font-bold text-emerald-800">Tetos de {rotuloMes(periodo)}</h2>

        {#if cargaInicial && carregando}
          <LoadingSpinner mensagem="Carregando os tetos..." />
        {:else if erro}
          <div
            class="bg-red-50 border-l-4 border-red-500 rounded-r-lg p-4 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 text-red-800"
            role="alert"
          >
            <p><strong>Erro ao carregar os tetos:</strong> {erro}</p>
            <button
              type="button"
              onclick={buscar}
              class="shrink-0 self-start sm:self-auto px-3 py-1.5 text-sm font-medium rounded-lg border border-red-300 bg-white text-red-800 hover:bg-red-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600 transition"
            >
              Tentar novamente
            </button>
          </div>
        {:else if tetos.length === 0}
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
              Nenhum teto liberado para {rotuloMes(periodo)}. Sem teto, os agendamentos não são limitados por valor.
            </p>
          </div>
        {:else}
          <div class="overflow-x-auto transition-opacity" class:opacity-60={carregando} aria-busy={carregando}>
            <table class="w-full text-sm">
              <thead>
                <tr class="text-left text-gray-700 border-b border-gray-300">
                  <th scope="col" class="py-2 pr-4 font-semibold min-w-48">Unidade</th>
                  <th scope="col" class="py-2 px-4 font-semibold min-w-32">Grupo</th>
                  <th scope="col" class="py-2 px-4 font-semibold text-right whitespace-nowrap">Teto</th>
                  <th scope="col" class="py-2 px-4 font-semibold text-right whitespace-nowrap">Utilizado</th>
                  <th scope="col" class="py-2 px-4 font-semibold text-right whitespace-nowrap">Saldo</th>
                  <th scope="col" class="py-2 px-4 font-semibold min-w-40">Uso</th>
                  {#if podeEditar}<th scope="col" class="py-2 pl-4 font-semibold"><span class="sr-only">Ações</span></th>{/if}
                </tr>
              </thead>
              <tbody>
                {#each tetos as t (t.id)}
                  {@const uso = percentual(t)}
                  <tr class={`border-b border-gray-200 align-top hover:bg-gray-50 ${t.ativo ? "text-gray-900" : "bg-gray-50 text-gray-600"}`}>
                    <th scope="row" class={`pt-3.5 pb-2 pr-4 font-medium text-left ${t.ativo ? "text-gray-900" : "text-gray-700"}`}>
                      {t.unidadeNome}
                      {#if !t.ativo}
                        <span class="inline-block ml-1 text-xs font-semibold px-1.5 py-0.5 rounded bg-gray-200 text-gray-700 border border-gray-300">Inativo</span>
                      {/if}
                    </th>
                    <td class={`pt-3.5 pb-2 px-4 ${t.ativo ? "text-gray-800" : ""}`}>{t.grupoEspecialidadesNome}</td>
                    <td class={`px-4 text-right tabular-nums whitespace-nowrap ${editandoId === t.id ? "py-2" : "pt-3.5 pb-2"}`}>
                      {#if editandoId === t.id}
                        <label class="sr-only" for={`teto-edicao-${t.id}`}>Novo teto de {t.unidadeNome}</label>
                        <input
                          id={`teto-edicao-${t.id}`}
                          type="text"
                          inputmode="decimal"
                          maxlength="16"
                          bind:value={valorEdicao}
                          aria-invalid={!!erroEdicao}
                          aria-describedby={erroEdicao ? `teto-edicao-erro-${t.id}` : undefined}
                          onkeydown={(evento) => evento.key === "Enter" && salvarEdicao(t)}
                          class={`w-32 border rounded-lg p-1.5 text-sm bg-white text-gray-900 text-right tabular-nums ${
                            erroEdicao
                              ? "border-red-500 focus:ring-red-500 focus:border-red-500"
                              : "border-gray-300 focus:ring-emerald-500 focus:border-emerald-500"
                          }`}
                        />
                        {#if erroEdicao}
                          <p id={`teto-edicao-erro-${t.id}`} class="text-xs font-medium text-red-700 mt-1 text-right whitespace-normal" role="alert">
                            {erroEdicao}
                          </p>
                        {/if}
                      {:else}
                        {formatarReais(t.valorTotal)}
                      {/if}
                    </td>
                    <td class="pt-3.5 pb-2 px-4 text-right tabular-nums whitespace-nowrap">{formatarReais(t.valorUtilizado)}</td>
                    <td
                      class={`pt-3.5 pb-2 px-4 text-right tabular-nums whitespace-nowrap font-semibold ${
                        Number(t.saldoDisponivel) < 0 ? "text-red-700" : "text-emerald-800"
                      }`}
                    >
                      {formatarReais(t.saldoDisponivel)}
                    </td>
                    <td class="pt-4 pb-2 px-4">
                      <div class="flex items-center gap-2">
                      <div
                        class="h-2.5 flex-1 rounded-full bg-gray-200 overflow-hidden"
                        role="progressbar"
                        aria-valuemin="0"
                        aria-valuemax="100"
                        aria-valuenow={Math.min(uso, 100)}
                        aria-label={`Uso do teto de ${t.unidadeNome}`}
                      >
                        <div
                          class={`h-full rounded-full ${
                            uso >= 100 ? "bg-red-600" : uso >= 80 ? "bg-amber-500" : "bg-emerald-600"
                          }`}
                          style={`width: ${Math.min(uso, 100)}%`}
                        ></div>
                      </div>
                      <span class="shrink-0 w-11 text-right text-xs font-semibold text-gray-800 tabular-nums">{uso}%</span>
                      </div>
                      {#if uso > 100}
                        <span class="block text-xs font-semibold text-red-700 mt-1">Ultrapassado</span>
                      {:else if uso >= 80 && uso < 100}
                        <span class="block text-xs font-medium text-amber-800 mt-1">Perto do limite</span>
                      {/if}
                    </td>
                    {#if podeEditar}
                      <td class="py-2 pl-4 whitespace-nowrap">
                        {#if editandoId === t.id}
                          <button
                            type="button"
                            onclick={() => salvarEdicao(t)}
                            disabled={salvandoEdicao}
                            class="px-3 py-1.5 text-sm font-medium rounded-lg border border-transparent bg-emerald-700 hover:bg-emerald-800 text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:opacity-50 disabled:cursor-not-allowed transition"
                          >
                            {salvandoEdicao ? "Salvando..." : "Salvar"}
                          </button>
                          <button
                            type="button"
                            onclick={cancelarEdicao}
                            disabled={salvandoEdicao}
                            class="ml-1 px-3 py-1.5 text-sm font-medium rounded-lg border border-gray-300 bg-white text-gray-700 hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:opacity-50 disabled:cursor-not-allowed transition"
                          >
                            Cancelar
                          </button>
                        {:else}
                          <button
                            type="button"
                            onclick={() => editar(t)}
                            class="px-3 py-1.5 text-sm font-medium rounded-lg border border-gray-300 bg-white text-gray-700 hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 transition"
                          >
                            Alterar valor
                          </button>
                          <button
                            type="button"
                            onclick={() => gravar(t, { ativo: !t.ativo })}
                            disabled={salvandoEdicao}
                            class="ml-1 px-3 py-1.5 text-sm font-medium rounded-lg border border-gray-300 bg-white text-gray-700 hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:opacity-50 disabled:cursor-not-allowed transition"
                          >
                            {t.ativo ? "Desativar" : "Reativar"}
                          </button>
                        {/if}
                      </td>
                    {/if}
                  </tr>
                {/each}
              </tbody>
            </table>
          </div>
        {/if}
      </section>
    </main>
  </div>
</div>
