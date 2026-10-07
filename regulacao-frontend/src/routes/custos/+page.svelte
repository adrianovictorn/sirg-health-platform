<script lang="ts">
  import { onDestroy, onMount } from "svelte";
  import { afterNavigate, goto } from "$app/navigation";
  import { page } from "$app/state";
  import UserMenu from "$lib/UserMenu.svelte";
  import RoleBasedMenu from "$lib/RoleBasedMenu.svelte";
  import LoadingSpinner from "$lib/LoadingSpinner.svelte";
  import { getApi } from "$lib/api";
  import { listarGrupoRelatorio } from "$lib/especialidadesApi.js";
  import {
    carregarPainelCustos,
    listarTetosFinanceiros,
    formatarReais,
    mesAtual,
    limitesDoMes
  } from "$lib/custosApi.js";
  import { user } from "$lib/stores/auth.js";

  type Linha = {
    id: number | null;
    nome: string | null;
    codigoSus: string | null;
    estimado: number;
    estimadoItens: number;
    estimadoSemPreco: number;
    agendado: number;
    agendadoItens: number;
    agendadoSemValor: number;
    concluido: number;
    concluidoItens: number;
    concluidoSemValor: number;
  };

  type Painel = { total: Linha; porUnidade: Linha[]; porEspecialidade: Linha[] };

  type Teto = {
    id: number;
    unidadeId: number;
    unidadeNome: string;
    grupoEspecialidadesId: number;
    grupoEspecialidadesNome: string;
    valorTotal: number;
    valorUtilizado: number;
    saldoDisponivel: number;
    ativo: boolean;
  };

  type Opcao = { id: number; nome: string };

  const TIPOS = [
    { valor: "ESPECIALIDADE_MEDICA", rotulo: "Consulta / Especialidade" },
    { valor: "EXAME_OU_PROCEDIMENTO", rotulo: "Exame ou Procedimento" }
  ];
  const LINHAS_INICIAIS_DE_ESPECIALIDADE = 15;

  // ---- Filtros: espelhados na URL (da para recarregar, voltar e compartilhar o link)
  let periodo = $state(mesAtual());
  let unidadeId = $state("");
  let grupoRelatorioId = $state("");
  let categoria = $state("");

  function lerFiltrosDaUrl(params: URLSearchParams) {
    const mes = params.get("periodo") ?? "";
    periodo = /^\d{4}-(0[1-9]|1[0-2])$/.test(mes) ? mes : mesAtual();
    unidadeId = params.get("unidadeId") ?? "";
    grupoRelatorioId = params.get("grupoRelatorioId") ?? "";
    categoria = TIPOS.some((t) => t.valor === params.get("categoria")) ? params.get("categoria")! : "";
  }

  lerFiltrosDaUrl(page.url.searchParams);

  // ---- Dados
  let painel = $state<Painel | null>(null);
  let tetos = $state<Teto[]>([]);
  let carregando = $state(true);
  let cargaInicial = $state(true);
  let erro = $state<string | null>(null);
  let ultimaBusca: symbol | null = null;

  let unidades = $state<Opcao[]>([]);
  let grupos = $state<Opcao[]>([]);
  let mostrarTodasEspecialidades = $state(false);

  const podeEditar = $derived($user?.role === "ADMIN");
  const haFiltros = $derived(!!unidadeId || !!grupoRelatorioId || !!categoria || periodo !== mesAtual());

  // Teto do mes, no mesmo recorte de unidade e grupo do painel. Inativos ficam fora:
  // nao limitam nada.
  const tetosDoFiltro = $derived(
    tetos.filter(
      (t) =>
        t.ativo &&
        (!unidadeId || String(t.unidadeId) === unidadeId) &&
        (!grupoRelatorioId || String(t.grupoEspecialidadesId) === grupoRelatorioId)
    )
  );
  const tetoLiberado = $derived(tetosDoFiltro.reduce((soma, t) => soma + Number(t.valorTotal), 0));
  const tetoUtilizado = $derived(tetosDoFiltro.reduce((soma, t) => soma + Number(t.valorUtilizado), 0));
  const tetoPercentual = $derived(tetoLiberado > 0 ? Math.round((tetoUtilizado / tetoLiberado) * 100) : 0);

  const especialidadesVisiveis = $derived(
    mostrarTodasEspecialidades
      ? (painel?.porEspecialidade ?? [])
      : (painel?.porEspecialidade ?? []).slice(0, LINHAS_INICIAIS_DE_ESPECIALIDADE)
  );

  function parametrosDaUrl(): URLSearchParams {
    const params = new URLSearchParams();
    if (periodo !== mesAtual()) params.set("periodo", periodo);
    if (unidadeId) params.set("unidadeId", unidadeId);
    if (grupoRelatorioId) params.set("grupoRelatorioId", grupoRelatorioId);
    if (categoria) params.set("categoria", categoria);
    return params;
  }

  async function buscar() {
    if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(periodo)) return;
    carregando = true;
    erro = null;
    const estaBusca = Symbol();
    ultimaBusca = estaBusca;

    const naUrl = parametrosDaUrl().toString();
    goto(naUrl ? `?${naUrl}` : page.url.pathname, { replaceState: true, keepFocus: true, noScroll: true });

    try {
      const { dataDe, dataAte } = limitesDoMes(periodo);
      const [dados, tetosDoMes] = await Promise.all([
        carregarPainelCustos({ unidadeId, grupoRelatorioId, categoria, dataDe, dataAte }),
        // O teto e um complemento: se falhar, o painel continua valendo.
        listarTetosFinanceiros(periodo).catch(() => [])
      ]);
      if (ultimaBusca !== estaBusca) return;
      painel = dados;
      tetos = tetosDoMes;
    } catch (e) {
      if (ultimaBusca !== estaBusca) return;
      erro = e instanceof Error ? e.message : "Erro inesperado ao carregar o painel de custos.";
      painel = null;
      tetos = [];
    } finally {
      if (ultimaBusca === estaBusca) {
        carregando = false;
        cargaInicial = false;
      }
    }
  }

  function limparFiltros() {
    periodo = mesAtual();
    unidadeId = "";
    grupoRelatorioId = "";
    categoria = "";
    buscar();
  }

  function rotuloMes(mes: string): string {
    const [ano, numero] = mes.split("-").map(Number);
    return new Date(ano, numero - 1, 1).toLocaleDateString("pt-BR", { month: "long", year: "numeric" });
  }

  const plural = (n: number, um: string, varios: string) => `${n} ${n === 1 ? um : varios}`;

  afterNavigate((navegacao) => {
    if (navegacao.type !== "link") return;
    lerFiltrosDaUrl(page.url.searchParams);
    buscar();
  });

  onMount(async () => {
    buscar();

    // Combos: falha aqui nao impede o painel, so deixa o filtro correspondente vazio.
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
  <title>Painel de Custos</title>
</svelte:head>

{#snippet iconeAviso()}
  <svg
    class="w-4 h-4 shrink-0 mt-0.5"
    fill="none"
    stroke="currentColor"
    stroke-width="2"
    viewBox="0 0 24 24"
    aria-hidden="true"
  >
    <path
      stroke-linecap="round"
      stroke-linejoin="round"
      d="M12 9v4m0 4h.01M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"
    />
  </svg>
{/snippet}

<div class="flex min-h-screen bg-gray-100">
  <RoleBasedMenu activePage="/custos" />

  <div class="flex-1 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Painel de Custos</h1>
      <UserMenu />
    </header>

    <main class="flex-1 overflow-auto p-4 md:p-6 space-y-6">
      <section class="bg-white rounded-lg shadow p-4 md:p-6 space-y-5" aria-label="Filtros do painel de custos">
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

        <div class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-4">
          <div>
            <label for="custos-periodo" class="block text-sm font-semibold text-gray-700 mb-1">Mês</label>
            <input
              id="custos-periodo"
              type="month"
              bind:value={periodo}
              onchange={buscar}
              class="w-full border border-gray-300 rounded-lg p-2 bg-white text-gray-900 focus:outline-hidden focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500"
            />
          </div>

          <div>
            <label for="custos-unidade" class="block text-sm font-semibold text-gray-700 mb-1">Unidade</label>
            <select
              id="custos-unidade"
              bind:value={unidadeId}
              onchange={buscar}
              class="w-full border border-gray-300 rounded-lg p-2 bg-white text-gray-900 focus:outline-hidden focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500"
            >
              <option value="">Todas as unidades</option>
              {#each unidades as u (u.id)}
                <option value={String(u.id)}>{u.nome}</option>
              {/each}
            </select>
          </div>

          <div>
            <label for="custos-grupo" class="block text-sm font-semibold text-gray-700 mb-1">Grupo</label>
            <select
              id="custos-grupo"
              bind:value={grupoRelatorioId}
              onchange={buscar}
              class="w-full border border-gray-300 rounded-lg p-2 bg-white text-gray-900 focus:outline-hidden focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500"
            >
              <option value="">Todos os grupos</option>
              {#each grupos as g (g.id)}
                <option value={String(g.id)}>{g.nome}</option>
              {/each}
            </select>
          </div>

          <div>
            <label for="custos-tipo" class="block text-sm font-semibold text-gray-700 mb-1">Tipo</label>
            <select
              id="custos-tipo"
              bind:value={categoria}
              onchange={buscar}
              class="w-full border border-gray-300 rounded-lg p-2 bg-white text-gray-900 focus:outline-hidden focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500"
            >
              <option value="">Todos</option>
              {#each TIPOS as tipo (tipo.valor)}
                <option value={tipo.valor}>{tipo.rotulo}</option>
              {/each}
            </select>
          </div>
        </div>
      </section>

      {#if cargaInicial && carregando}
        <section class="bg-white rounded-lg shadow p-6">
          <LoadingSpinner mensagem="Carregando o painel de custos..." />
        </section>
      {:else if erro}
        <div
          class="bg-red-50 border-l-4 border-red-500 rounded-r-lg p-4 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 text-red-800"
          role="alert"
        >
          <p><strong>Erro ao carregar o painel:</strong> {erro}</p>
          <button
            type="button"
            onclick={buscar}
            class="shrink-0 self-start sm:self-auto px-3 py-1.5 text-sm font-medium rounded-lg border border-red-300 bg-white text-red-800 hover:bg-red-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600 transition"
          >
            Tentar novamente
          </button>
        </div>
      {:else if painel}
        {@const t = painel.total}
        <div class="space-y-6 transition-opacity" class:opacity-60={carregando} aria-busy={carregando}>
          <section aria-label="Resumo de custos" class="grid grid-cols-1 xl:grid-cols-3 gap-4">
            <article class="bg-white rounded-lg shadow p-5 border-l-4 border-l-amber-400">
              <h2 class="text-sm font-semibold text-gray-600">Custo estimado da fila</h2>
              <p class="text-3xl font-bold tracking-tight text-gray-900 mt-1 tabular-nums break-words">{formatarReais(t.estimado)}</p>
              <p class="text-sm text-gray-600 mt-3">
                {plural(t.estimadoItens, "pedido aguardando", "pedidos aguardando")}, pelo preço atual. Não
                depende do mês.
              </p>
              {#if t.estimadoSemPreco > 0}
                <p class="flex items-start gap-1.5 text-sm font-medium text-amber-800 mt-2">
                  {@render iconeAviso()}
                  {plural(t.estimadoSemPreco, "pedido sem preço ficou", "pedidos sem preço ficaram")} fora do total.
                </p>
              {/if}
            </article>

            <article class="bg-white rounded-lg shadow p-5 border-l-4 border-l-sky-500">
              <h2 class="text-sm font-semibold text-gray-600">Custo agendado</h2>
              <p class="text-3xl font-bold tracking-tight text-gray-900 mt-1 tabular-nums break-words">{formatarReais(t.agendado)}</p>
              <p class="text-sm text-gray-600 mt-3">
                {plural(t.agendadoItens, "item agendado", "itens agendados")} para {rotuloMes(periodo)}, pelo valor
                do dia em que foram marcados.
              </p>
              {#if t.agendadoSemValor > 0}
                <p class="flex items-start gap-1.5 text-sm font-medium text-amber-800 mt-2">
                  {@render iconeAviso()}
                  {plural(t.agendadoSemValor, "item sem valor ficou", "itens sem valor ficaram")} fora do total.
                </p>
              {/if}
            </article>

            <article class="bg-white rounded-lg shadow p-5 border-l-4 border-l-emerald-600">
              <h2 class="text-sm font-semibold text-gray-600">Custo concluído</h2>
              <p class="text-3xl font-bold tracking-tight text-gray-900 mt-1 tabular-nums break-words">{formatarReais(t.concluido)}</p>
              <p class="text-sm text-gray-600 mt-3">
                {plural(t.concluidoItens, "procedimento realizado", "procedimentos realizados")} com data em
                {rotuloMes(periodo)}.
              </p>
              {#if t.concluidoSemValor > 0}
                <p class="flex items-start gap-1.5 text-sm font-medium text-amber-800 mt-2">
                  {@render iconeAviso()}
                  {plural(t.concluidoSemValor, "item sem valor ficou", "itens sem valor ficaram")} fora do total.
                </p>
              {/if}
            </article>
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
              Só entram nos totais as especialidades com preço cadastrado. Agendamentos feitos antes de o preço
              existir não têm valor gravado e ficam fora. O mês considera a data agendada, inclusive para os
              concluídos.
            </span>
          </p>

          <section class="bg-white rounded-lg shadow p-4 md:p-6 space-y-4" aria-label="Teto financeiro do mês">
            <div class="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
              <h2 class="text-xl font-bold text-emerald-800">Teto financeiro de {rotuloMes(periodo)}</h2>
              <a
                href={`/custos/tetos?periodo=${periodo}`}
                class="text-sm font-medium text-emerald-700 hover:underline rounded focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600"
              >
                {podeEditar ? "Liberar e editar tetos" : "Ver tetos por unidade"}
              </a>
            </div>

            {#if tetosDoFiltro.length === 0}
              <p class="text-gray-700">
                Nenhum teto liberado para este mês{haFiltros ? " com os filtros escolhidos" : ""}. Sem teto, os
                agendamentos não são limitados por valor.
              </p>
            {:else}
              <div class="flex flex-wrap gap-x-10 gap-y-4">
                <div class="min-w-40">
                  <p class="text-sm font-semibold text-gray-600">Liberado</p>
                  <p class="text-2xl font-bold text-gray-900 tabular-nums">{formatarReais(tetoLiberado)}</p>
                </div>
                <div class="min-w-40">
                  <p class="text-sm font-semibold text-gray-600">Utilizado</p>
                  <p class="text-2xl font-bold text-gray-900 tabular-nums">{formatarReais(tetoUtilizado)}</p>
                </div>
                <div class="min-w-40">
                  <p class="text-sm font-semibold text-gray-600">Saldo</p>
                  <p
                    class={`text-2xl font-bold tabular-nums ${
                      tetoLiberado - tetoUtilizado < 0 ? "text-red-700" : "text-emerald-800"
                    }`}
                  >
                    {formatarReais(tetoLiberado - tetoUtilizado)}
                  </p>
                </div>
              </div>
              <div>
                <p class="text-sm text-gray-700 mb-1.5">
                  <strong class="text-base font-bold text-gray-900 tabular-nums">{tetoPercentual}%</strong>
                  utilizado em {plural(tetosDoFiltro.length, "teto", "tetos")}.
                </p>
                <div
                  class="h-3 w-full rounded-full bg-gray-200 overflow-hidden"
                  role="progressbar"
                  aria-valuemin="0"
                  aria-valuemax="100"
                  aria-valuenow={Math.min(tetoPercentual, 100)}
                  aria-label="Percentual do teto utilizado"
                >
                  <div
                    class={`h-full rounded-full ${
                      tetoPercentual >= 100 ? "bg-red-600" : tetoPercentual >= 80 ? "bg-amber-500" : "bg-emerald-600"
                    }`}
                    style={`width: ${Math.min(tetoPercentual, 100)}%`}
                  ></div>
                </div>
              </div>
            {/if}
          </section>

          <section class="bg-white rounded-lg shadow p-4 md:p-6 space-y-4" aria-label="Custos por unidade">
            <h2 class="text-xl font-bold text-emerald-800">Por unidade</h2>
            {#if painel.porUnidade.length === 0}
              <p class="text-gray-700">Nenhum pedido, agendamento ou procedimento para os filtros escolhidos.</p>
            {:else}
              <div class="overflow-x-auto">
                <table class="w-full text-sm">
                  <thead>
                    <tr class="text-left text-gray-700 border-b border-gray-300">
                      <th scope="col" class="py-2 pr-4 font-semibold min-w-48">Unidade</th>
                      <th scope="col" class="py-2 px-4 font-semibold text-right whitespace-nowrap w-40">Estimado da fila</th>
                      <th scope="col" class="py-2 px-4 font-semibold text-right whitespace-nowrap w-40">Agendado</th>
                      <th scope="col" class="py-2 pl-4 font-semibold text-right whitespace-nowrap w-40">Concluído</th>
                    </tr>
                  </thead>
                  <tbody>
                    {#each painel.porUnidade as linha (linha.id ?? "sem-unidade")}
                      <tr class="border-b border-gray-200 align-top hover:bg-gray-50">
                        <th scope="row" class="py-2 pr-4 font-medium text-gray-900 text-left">
                          {linha.nome ?? "Sem unidade"}
                        </th>
                        <td class="py-2 px-4 text-right tabular-nums whitespace-nowrap text-gray-900">
                          {formatarReais(linha.estimado)}
                          <span class="block text-xs text-gray-600">{plural(linha.estimadoItens, "pedido", "pedidos")}</span>
                        </td>
                        <td class="py-2 px-4 text-right tabular-nums whitespace-nowrap text-gray-900">
                          {formatarReais(linha.agendado)}
                          <span class="block text-xs text-gray-600">{plural(linha.agendadoItens, "item", "itens")}</span>
                        </td>
                        <td class="py-2 pl-4 text-right tabular-nums whitespace-nowrap text-gray-900">
                          {formatarReais(linha.concluido)}
                          <span class="block text-xs text-gray-600">{plural(linha.concluidoItens, "item", "itens")}</span>
                        </td>
                      </tr>
                    {/each}
                  </tbody>
                  <tfoot>
                    <tr class="font-bold text-gray-900 border-t-2 border-gray-300">
                      <th scope="row" class="py-3 pr-4 text-left">Total</th>
                      <td class="py-2 px-4 text-right tabular-nums whitespace-nowrap text-gray-900">{formatarReais(t.estimado)}</td>
                      <td class="py-2 px-4 text-right tabular-nums whitespace-nowrap text-gray-900">{formatarReais(t.agendado)}</td>
                      <td class="py-2 pl-4 text-right tabular-nums whitespace-nowrap text-gray-900">{formatarReais(t.concluido)}</td>
                    </tr>
                  </tfoot>
                </table>
              </div>
            {/if}
          </section>

          <section class="bg-white rounded-lg shadow p-4 md:p-6 space-y-4" aria-label="Custos por especialidade">
            <div class="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
              <h2 class="text-xl font-bold text-emerald-800">Por especialidade</h2>
              <a
                href="/custos/especialidades"
                class="text-sm font-medium text-emerald-700 hover:underline rounded focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600"
              >
                Ver preços cadastrados
              </a>
            </div>
            {#if painel.porEspecialidade.length === 0}
              <p class="text-gray-700">Nenhum pedido, agendamento ou procedimento para os filtros escolhidos.</p>
            {:else}
              <div class="overflow-x-auto">
                <table class="w-full text-sm">
                  <thead>
                    <tr class="text-left text-gray-700 border-b border-gray-300">
                      <th scope="col" class="py-2 pr-4 font-semibold min-w-48">Especialidade / exame</th>
                      <th scope="col" class="py-2 px-4 font-semibold whitespace-nowrap">Código SUS</th>
                      <th scope="col" class="py-2 px-4 font-semibold text-right whitespace-nowrap w-40">Estimado da fila</th>
                      <th scope="col" class="py-2 px-4 font-semibold text-right whitespace-nowrap w-40">Agendado</th>
                      <th scope="col" class="py-2 pl-4 font-semibold text-right whitespace-nowrap w-40">Concluído</th>
                    </tr>
                  </thead>
                  <tbody>
                    {#each especialidadesVisiveis as linha (linha.id)}
                      {@const semPreco = linha.estimadoSemPreco + linha.agendadoSemValor + linha.concluidoSemValor}
                      <tr class="border-b border-gray-200 align-top hover:bg-gray-50">
                        <th scope="row" class="py-2 pr-4 font-medium text-gray-900 text-left">
                          {linha.nome}
                          {#if semPreco > 0}
                            <span
                              class="inline-block ml-1 text-xs font-semibold px-1.5 py-0.5 rounded bg-amber-100 text-amber-900 border border-amber-300 whitespace-nowrap"
                            >
                              {plural(semPreco, "item sem valor", "itens sem valor")}
                            </span>
                          {/if}
                        </th>
                        <td class="py-2 px-4 tabular-nums whitespace-nowrap text-gray-800">{linha.codigoSus ?? "—"}</td>
                        <td class="py-2 px-4 text-right tabular-nums whitespace-nowrap text-gray-900">
                          {formatarReais(linha.estimado)}
                          <span class="block text-xs text-gray-600">{plural(linha.estimadoItens, "pedido", "pedidos")}</span>
                        </td>
                        <td class="py-2 px-4 text-right tabular-nums whitespace-nowrap text-gray-900">
                          {formatarReais(linha.agendado)}
                          <span class="block text-xs text-gray-600">{plural(linha.agendadoItens, "item", "itens")}</span>
                        </td>
                        <td class="py-2 pl-4 text-right tabular-nums whitespace-nowrap text-gray-900">
                          {formatarReais(linha.concluido)}
                          <span class="block text-xs text-gray-600">{plural(linha.concluidoItens, "item", "itens")}</span>
                        </td>
                      </tr>
                    {/each}
                  </tbody>
                </table>
              </div>
              {#if painel.porEspecialidade.length > LINHAS_INICIAIS_DE_ESPECIALIDADE}
                <button
                  type="button"
                  onclick={() => (mostrarTodasEspecialidades = !mostrarTodasEspecialidades)}
                  aria-expanded={mostrarTodasEspecialidades}
                  class="text-sm font-medium px-3 py-1.5 rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 transition"
                >
                  {mostrarTodasEspecialidades
                    ? `Mostrar só as ${LINHAS_INICIAIS_DE_ESPECIALIDADE} de maior custo`
                    : `Mostrar todas as ${painel.porEspecialidade.length}`}
                </button>
              {/if}
            {/if}
          </section>
        </div>
      {/if}
    </main>
  </div>
</div>
