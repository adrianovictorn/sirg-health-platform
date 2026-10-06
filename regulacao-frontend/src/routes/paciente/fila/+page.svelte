<script lang="ts">
  import { onDestroy, onMount } from "svelte";
  import { afterNavigate, goto } from "$app/navigation";
  import { page } from "$app/state";
  import UserMenu from "$lib/UserMenu.svelte";
  import RoleBasedMenu from "$lib/RoleBasedMenu.svelte";
  import LoadingSpinner from "$lib/LoadingSpinner.svelte";
  import { getApi } from "$lib/api";
  import { listarEspecialidadesCatalogo } from "$lib/especialidadesApi.js";
  import { user } from "$lib/stores/auth.js";

  // O backend serializa datas como array ([ano, mes, dia, ...]); aceita string ISO
  // tambem, para nao depender da configuracao do Jackson.
  type DataApi = number[] | string | null;

  type ItemFila = {
    id: number;
    especialidadeNome: string | null;
    categoria: string | null;
    status: string;
    prioridade: string | null;
    dataCadastro: DataApi;
    diasEspera: number;
  };

  type PacienteFila = {
    solicitacaoId: number;
    nomePaciente: string;
    cpfPaciente: string | null;
    cns: string | null;
    dataNascimento: DataApi;
    unidadeId: number | null;
    unidadeNome: string | null;
    entradaMaisAntiga: DataApi;
    diasEspera: number;
    itens: ItemFila[];
  };

  type EspecialidadeCatalogo = { id: number; nome: string; categoria: string; ativo?: boolean };
  type UnidadeOpcao = { id: number; nome: string };

  const STATUS = [
    { valor: "AGUARDANDO", rotulo: "Pendente" },
    { valor: "RETORNO", rotulo: "Retorno" },
    { valor: "RETORNO_POLICLINICA", rotulo: "Retorno Policlínica" }
  ];
  const PRIORIDADES = [
    { valor: "NORMAL", rotulo: "Normal" },
    { valor: "URGENTE", rotulo: "Urgente" },
    { valor: "EMERGENCIA", rotulo: "Emergência" }
  ];
  const TIPOS = [
    { valor: "ESPECIALIDADE_MEDICA", rotulo: "Consulta / Especialidade" },
    { valor: "EXAME_OU_PROCEDIMENTO", rotulo: "Exame ou Procedimento" }
  ];
  const FAIXAS_DE_ESPERA = [30, 60, 90];
  const ITENS_POR_PAGINA = 20;

  // ---- Filtros: vem da URL (e o que os cards do dashboard passam)
  let especialidadeId = $state("");
  let categoria = $state("");
  let statusSelecionados = $state<string[]>([]);
  let prioridadesSelecionadas = $state<string[]>([]);
  let unidadeId = $state("");
  let esperaMinimaDias = $state("");
  let dataDe = $state("");
  let dataAte = $state("");
  let ordem = $state("ANTIGOS");

  // Busca livre por nome, CPF ou CNS. De proposito NAO vai para a URL (ao
  // contrario dos filtros acima): nome e CPF de paciente nao devem ficar em
  // historico do navegador nem em link compartilhado. Recarregar limpa a busca.
  let termo = $state("");
  let temporizadorBusca: ReturnType<typeof setTimeout> | null = null;
  const ESPERA_DA_BUSCA_MS = 300;

  function lerFiltrosDaUrl(params: URLSearchParams) {
    const lista = (chave: string, validos: string[]) =>
      (params.get(chave) ?? "")
        .split(",")
        .map((v) => v.trim().toUpperCase())
        .filter((v) => validos.includes(v));

    especialidadeId = params.get("especialidadeId") ?? "";
    categoria = TIPOS.some((t) => t.valor === params.get("categoria")) ? params.get("categoria")! : "";
    statusSelecionados = lista("status", STATUS.map((s) => s.valor));
    prioridadesSelecionadas = lista("prioridade", PRIORIDADES.map((p) => p.valor));
    unidadeId = params.get("unidadeId") ?? "";
    esperaMinimaDias = FAIXAS_DE_ESPERA.includes(Number(params.get("esperaMinimaDias")))
      ? params.get("esperaMinimaDias")!
      : "";
    dataDe = params.get("dataDe") ?? "";
    dataAte = params.get("dataAte") ?? "";
    ordem = params.get("ordem") === "RECENTES" ? "RECENTES" : "ANTIGOS";
  }

  lerFiltrosDaUrl(page.url.searchParams);

  // ---- Dados
  let pacientes = $state<PacienteFila[]>([]);
  let totalElements = $state(0);
  let totalPages = $state(0);
  let paginaAtual = $state(1);
  let carregando = $state(true);
  let cargaInicial = $state(true);
  let erro = $state<string | null>(null);
  let ultimaBusca: symbol | null = null;

  let especialidades = $state<EspecialidadeCatalogo[]>([]);
  let unidades = $state<UnidadeOpcao[]>([]);
  let semUnidadeDeLotacao = $state(false);

  // Quem ve todas as unidades (perfil ATIVO). So decide o que a tela oferece:
  // o escopo de verdade e imposto pelo servidor.
  const veTodasAsUnidades = $derived($user?.role === "ADMIN" || $user?.role === "GESTOR");

  const especialidadesDoTipo = $derived(
    especialidades
      .filter((e) => !categoria || e.categoria === categoria)
      .sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR"))
  );

  const haFiltros = $derived(
    !!especialidadeId ||
      !!categoria ||
      statusSelecionados.length > 0 ||
      prioridadesSelecionadas.length > 0 ||
      (!!unidadeId && veTodasAsUnidades) ||
      !!esperaMinimaDias ||
      !!dataDe ||
      !!dataAte ||
      !!termo.trim()
  );

  const periodoInvalido = $derived(!!dataDe && !!dataAte && dataDe > dataAte);

  function parametros(pagina: number): URLSearchParams {
    const params = new URLSearchParams();
    if (especialidadeId) params.set("especialidadeId", especialidadeId);
    if (categoria) params.set("categoria", categoria);
    if (statusSelecionados.length) params.set("status", statusSelecionados.join(","));
    if (prioridadesSelecionadas.length) params.set("prioridade", prioridadesSelecionadas.join(","));
    if (unidadeId && veTodasAsUnidades) params.set("unidadeId", unidadeId);
    if (esperaMinimaDias) params.set("esperaMinimaDias", esperaMinimaDias);
    if (dataDe) params.set("dataDe", dataDe);
    if (dataAte) params.set("dataAte", dataAte);
    if (ordem !== "ANTIGOS") params.set("ordem", ordem);
    if (pagina > 1) params.set("page", String(pagina));
    return params;
  }

  async function buscar(pagina: number) {
    // Qualquer busca nova substitui a que estava agendada pela digitacao. Se havia
    // uma agendada, o termo mudou e a pagina pedida (ex.: "proxima") ja nao vale.
    if (temporizadorBusca) {
      clearTimeout(temporizadorBusca);
      temporizadorBusca = null;
      pagina = 1;
    }
    if (periodoInvalido) return;
    paginaAtual = Math.max(pagina, 1);
    carregando = true;
    erro = null;
    const estaBusca = Symbol();
    ultimaBusca = estaBusca;

    // A URL espelha os filtros: da para recarregar, voltar e compartilhar o link.
    const naUrl = parametros(paginaAtual).toString();
    goto(naUrl ? `?${naUrl}` : page.url.pathname, { replaceState: true, keepFocus: true, noScroll: true });

    const params = parametros(1);
    const termoLimpo = termo.trim();
    if (termoLimpo) params.set("termo", termoLimpo);
    params.set("page", String(paginaAtual - 1));
    params.set("size", String(ITENS_POR_PAGINA));

    try {
      const res = await getApi(`fila-espera?${params.toString()}`);
      if (ultimaBusca !== estaBusca) return;
      if (!res.ok) {
        const corpo = await res.json().catch(() => null);
        throw new Error(corpo?.message ?? "Não foi possível carregar a fila de espera.");
      }
      const payload = await res.json();
      if (ultimaBusca !== estaBusca) return;
      pacientes = payload.content ?? [];
      totalElements = payload.totalElements ?? pacientes.length;
      totalPages = payload.totalPages ?? 0;
    } catch (e) {
      if (ultimaBusca !== estaBusca) return;
      erro = e instanceof Error ? e.message : "Erro inesperado ao carregar a fila de espera.";
      pacientes = [];
      totalElements = 0;
      totalPages = 0;
    } finally {
      if (ultimaBusca === estaBusca) {
        carregando = false;
        cargaInicial = false;
      }
    }
  }

  function aplicar() {
    buscar(1);
  }

  function aoDigitarBusca() {
    if (temporizadorBusca) clearTimeout(temporizadorBusca);
    temporizadorBusca = setTimeout(() => buscar(1), ESPERA_DA_BUSCA_MS);
  }

  function aoTrocarTipo() {
    // Especialidade escolhida deixa de valer se nao pertence ao tipo novo.
    if (especialidadeId && !especialidadesDoTipo.some((e) => String(e.id) === especialidadeId)) {
      especialidadeId = "";
    }
    aplicar();
  }

  function alternar(lista: string[], valor: string): string[] {
    return lista.includes(valor) ? lista.filter((v) => v !== valor) : [...lista, valor];
  }

  function limparFiltros() {
    especialidadeId = "";
    categoria = "";
    statusSelecionados = [];
    prioridadesSelecionadas = [];
    unidadeId = "";
    esperaMinimaDias = "";
    dataDe = "";
    dataAte = "";
    ordem = "ANTIGOS";
    termo = "";
    aplicar();
  }

  function formatarData(data: DataApi): string {
    if (!data) return "—";
    if (Array.isArray(data)) {
      const [ano, mes, dia] = data;
      return new Date(ano, mes - 1, dia).toLocaleDateString("pt-BR");
    }
    const [ano, mes, dia] = data.slice(0, 10).split("-").map(Number);
    return new Date(ano, mes - 1, dia).toLocaleDateString("pt-BR");
  }

  function textoEspera(dias: number): string {
    if (dias <= 0) return "hoje";
    return dias === 1 ? "há 1 dia" : `há ${dias} dias`;
  }

  const rotuloStatus = (valor: string) => STATUS.find((s) => s.valor === valor)?.rotulo ?? valor;
  const rotuloPrioridade = (valor: string | null) => PRIORIDADES.find((p) => p.valor === valor)?.rotulo ?? null;

  function classePrioridade(prioridade: string | null): string {
    if (prioridade === "EMERGENCIA") return "bg-red-100 text-red-800 border-red-200";
    if (prioridade === "URGENTE") return "bg-amber-100 text-amber-800 border-amber-200";
    return "bg-emerald-50 text-emerald-800 border-emerald-100";
  }

  // Clicar num link para esta mesma rota (o item do menu, por exemplo) nao remonta
  // a pagina: sem isto a URL mudava e os filtros antigos continuavam aplicados.
  afterNavigate((navegacao) => {
    if (navegacao.type !== "link") return;
    termo = "";
    lerFiltrosDaUrl(page.url.searchParams);
    buscar(Math.max(Number(page.url.searchParams.get("page")) || 1, 1));
  });

  onMount(async () => {
    paginaAtual = Math.max(Number(page.url.searchParams.get("page")) || 1, 1);
    buscar(paginaAtual);

    // Combos: falha aqui nao impede a lista, so deixa o filtro correspondente vazio.
    listarEspecialidadesCatalogo()
      .then((lista: EspecialidadeCatalogo[]) => (especialidades = (lista ?? []).filter((e) => e.ativo !== false)))
      .catch(() => (especialidades = []));

    if (veTodasAsUnidades) {
      const res = await getApi("unidades/ativas").catch(() => null);
      if (res?.ok) unidades = await res.json();
    } else {
      const res = await getApi("users/me").catch(() => null);
      if (res?.ok) {
        const me = await res.json();
        semUnidadeDeLotacao = !me.unidadeId;
      }
    }
  });

  onDestroy(() => {
    if (temporizadorBusca) clearTimeout(temporizadorBusca);
    ultimaBusca = null;
  });
</script>

<svelte:head>
  <title>Fila de Espera</title>
</svelte:head>

<div class="flex min-h-screen bg-gray-100">
  <RoleBasedMenu activePage="/paciente/fila" />

  <div class="flex-1 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Fila de Espera</h1>
      <UserMenu />
    </header>

    <main class="flex-1 overflow-auto p-4 md:p-6 space-y-6">
      {#if semUnidadeDeLotacao}
        <div
          class="bg-amber-50 border-l-4 border-amber-400 rounded-r-lg p-4 flex items-start gap-3 text-sm text-amber-900"
          role="status"
        >
          <svg
            class="w-5 h-5 text-amber-600 shrink-0 mt-0.5"
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
          <p>
            Sua conta não está vinculada a uma unidade, por isso a fila aparece vazia. Fale com o administrador do
            sistema para vincular sua conta a uma Unidade de Saúde.
          </p>
        </div>
      {/if}

      <section class="bg-white rounded-lg shadow p-4 md:p-6 space-y-5" aria-label="Filtros da fila de espera">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h2 class="text-xs font-semibold text-gray-700 uppercase tracking-widest">Filtros</h2>
          <button
            type="button"
            onclick={limparFiltros}
            disabled={!haFiltros && ordem === "ANTIGOS"}
            class="text-sm font-medium px-3 py-1.5 rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:opacity-50 disabled:cursor-not-allowed transition"
          >
            Limpar filtros
          </button>
        </div>

        <div>
          <label for="fila-busca" class="block text-sm font-semibold text-gray-700 mb-1">Buscar paciente</label>
          <input
            id="fila-busca"
            type="search"
            bind:value={termo}
            oninput={aoDigitarBusca}
            maxlength="100"
            autocomplete="off"
            placeholder="Nome, CPF ou CNS"
            class="w-full border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500"
          />
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-6 gap-4">
          <div>
            <label for="fila-tipo" class="block text-sm font-semibold text-gray-700 mb-1">Tipo de solicitação</label>
            <select
              id="fila-tipo"
              bind:value={categoria}
              onchange={aoTrocarTipo}
              class="w-full border border-gray-300 rounded-lg p-2 bg-white text-gray-900 focus:outline-hidden focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500"
            >
              <option value="">Todos</option>
              {#each TIPOS as tipo (tipo.valor)}
                <option value={tipo.valor}>{tipo.rotulo}</option>
              {/each}
            </select>
          </div>

          <div>
            <label for="fila-especialidade" class="block text-sm font-semibold text-gray-700 mb-1">
              Especialidade / exame
            </label>
            <select
              id="fila-especialidade"
              bind:value={especialidadeId}
              onchange={aplicar}
              class="w-full border border-gray-300 rounded-lg p-2 bg-white text-gray-900 focus:outline-hidden focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500"
            >
              <option value="">Todas</option>
              {#each especialidadesDoTipo as e (e.id)}
                <option value={String(e.id)}>{e.nome}</option>
              {/each}
            </select>
          </div>

          {#if veTodasAsUnidades}
            <div>
              <label for="fila-unidade" class="block text-sm font-semibold text-gray-700 mb-1">Unidade</label>
              <select
                id="fila-unidade"
                bind:value={unidadeId}
                onchange={aplicar}
                class="w-full border border-gray-300 rounded-lg p-2 bg-white text-gray-900 focus:outline-hidden focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500"
              >
                <option value="">Todas as unidades</option>
                {#each unidades as u (u.id)}
                  <option value={String(u.id)}>{u.nome}</option>
                {/each}
              </select>
            </div>
          {/if}

          <div>
            <label for="fila-espera" class="block text-sm font-semibold text-gray-700 mb-1">Tempo de espera</label>
            <select
              id="fila-espera"
              bind:value={esperaMinimaDias}
              onchange={aplicar}
              class="w-full border border-gray-300 rounded-lg p-2 bg-white text-gray-900 focus:outline-hidden focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500"
            >
              <option value="">Qualquer tempo</option>
              {#each FAIXAS_DE_ESPERA as dias (dias)}
                <option value={String(dias)}>Mais de {dias} dias</option>
              {/each}
            </select>
          </div>

          <div>
            <label for="fila-data-de" class="block text-sm font-semibold text-gray-700 mb-1">Cadastrado a partir de</label>
            <input
              id="fila-data-de"
              type="date"
              bind:value={dataDe}
              onchange={aplicar}
              max={dataAte || undefined}
              aria-invalid={periodoInvalido}
              aria-describedby={periodoInvalido ? "fila-periodo-erro" : undefined}
              class={`w-full border rounded-lg p-2 bg-white text-gray-900 focus:outline-hidden focus:ring-2 ${
                periodoInvalido
                  ? "border-red-500 focus:ring-red-500 focus:border-red-500"
                  : "border-gray-300 focus:ring-emerald-500 focus:border-emerald-500"
              }`}
            />
          </div>

          <div>
            <label for="fila-data-ate" class="block text-sm font-semibold text-gray-700 mb-1">Cadastrado até</label>
            <input
              id="fila-data-ate"
              type="date"
              bind:value={dataAte}
              onchange={aplicar}
              min={dataDe || undefined}
              aria-invalid={periodoInvalido}
              aria-describedby={periodoInvalido ? "fila-periodo-erro" : undefined}
              class={`w-full border rounded-lg p-2 bg-white text-gray-900 focus:outline-hidden focus:ring-2 ${
                periodoInvalido
                  ? "border-red-500 focus:ring-red-500 focus:border-red-500"
                  : "border-gray-300 focus:ring-emerald-500 focus:border-emerald-500"
              }`}
            />
          </div>
        </div>

        {#if periodoInvalido}
          <p id="fila-periodo-erro" class="text-sm font-medium text-red-700" role="alert">
            A data inicial do período não pode ser posterior à data final.
          </p>
        {/if}

        <div class="grid grid-cols-1 lg:grid-cols-2 gap-x-6 gap-y-4 pt-4 border-t border-gray-200">
          <fieldset>
            <legend class="text-sm font-semibold text-gray-700 mb-2">Status</legend>
            <div class="flex flex-wrap gap-2">
              {#each STATUS as s (s.valor)}
                {@const ativo = statusSelecionados.includes(s.valor)}
                <button
                  type="button"
                  aria-pressed={ativo}
                  onclick={() => {
                    statusSelecionados = alternar(statusSelecionados, s.valor);
                    aplicar();
                  }}
                  class={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-medium border transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 ${
                    ativo
                      ? "bg-emerald-700 text-white border-emerald-700 hover:bg-emerald-800"
                      : "bg-white text-gray-700 border-gray-400 hover:bg-gray-50"
                  }`}
                >
                  {#if ativo}
                    <svg
                      class="w-4 h-4 shrink-0"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="3"
                      viewBox="0 0 24 24"
                      aria-hidden="true"
                    >
                      <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
                    </svg>
                  {/if}
                  {s.rotulo}
                </button>
              {/each}
            </div>
            <p class="text-xs text-gray-600 mt-2">Sem nenhum marcado, a fila traz os três.</p>
          </fieldset>

          <fieldset>
            <legend class="text-sm font-semibold text-gray-700 mb-2">Prioridade</legend>
            <div class="flex flex-wrap gap-2">
              {#each PRIORIDADES as p (p.valor)}
                {@const ativo = prioridadesSelecionadas.includes(p.valor)}
                <button
                  type="button"
                  aria-pressed={ativo}
                  onclick={() => {
                    prioridadesSelecionadas = alternar(prioridadesSelecionadas, p.valor);
                    aplicar();
                  }}
                  class={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-medium border transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 ${
                    ativo
                      ? "bg-emerald-700 text-white border-emerald-700 hover:bg-emerald-800"
                      : "bg-white text-gray-700 border-gray-400 hover:bg-gray-50"
                  }`}
                >
                  {#if ativo}
                    <svg
                      class="w-4 h-4 shrink-0"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="3"
                      viewBox="0 0 24 24"
                      aria-hidden="true"
                    >
                      <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
                    </svg>
                  {/if}
                  {p.rotulo}
                </button>
              {/each}
            </div>
            <p class="text-xs text-gray-600 mt-2">Sem nenhuma marcada, a fila traz todas.</p>
          </fieldset>
        </div>
      </section>

      <section class="bg-white rounded-lg shadow-lg p-4 md:p-6 space-y-4" aria-label="Pacientes na fila">
        <div class="flex flex-col sm:flex-row sm:items-end sm:justify-between gap-3">
          <h2 class="text-2xl font-bold text-emerald-800">Pacientes aguardando marcação</h2>
          <div class="flex flex-col sm:flex-row sm:items-center gap-1 sm:gap-2">
            <label for="fila-ordem" class="text-sm font-semibold text-gray-700 whitespace-nowrap">Ordenar por</label>
            <select
              id="fila-ordem"
              bind:value={ordem}
              onchange={aplicar}
              class="w-full sm:w-auto border border-gray-300 rounded-lg p-2 bg-white text-gray-900 focus:outline-hidden focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500"
            >
              <option value="ANTIGOS">Mais antigos primeiro</option>
              <option value="RECENTES">Mais recentes primeiro</option>
            </select>
          </div>
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
            A espera é contada a partir da data em que o pedido foi cadastrado no sistema. Em pedidos antigos e em
            pedidos de retorno, a espera real pode ser maior do que a exibida.
          </span>
        </p>

        {#if cargaInicial && carregando}
          <LoadingSpinner mensagem="Carregando a fila de espera..." />
        {:else if erro}
          <div
            class="bg-red-50 border-l-4 border-red-500 rounded-r-lg p-4 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 text-red-800"
            role="alert"
          >
            <p><strong>Erro ao carregar a fila:</strong> {erro}</p>
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
              {totalElements === 1 ? "paciente na fila" : "pacientes na fila"}
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
                {#if haFiltros}
                  Nenhum paciente encontrado para os filtros escolhidos.
                {:else}
                  Nenhum paciente aguardando marcação no momento.
                {/if}
              </p>
              {#if haFiltros}
                <button
                  type="button"
                  onclick={limparFiltros}
                  class="px-4 py-2 text-sm font-medium rounded-lg bg-emerald-600 hover:bg-emerald-800 text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 transition"
                >
                  Limpar filtros
                </button>
              {/if}
            </div>
          {:else}
            <ul class="space-y-3 transition-opacity" class:opacity-60={carregando} aria-busy={carregando}>
              {#each pacientes as p, idx (p.solicitacaoId)}
                {@const temEmergencia = p.itens.some((i) => i.prioridade === "EMERGENCIA")}
                {@const temUrgente = p.itens.some((i) => i.prioridade === "URGENTE")}
                <li
                  class={`border border-gray-200 border-l-4 rounded-lg p-4 hover:shadow-md transition flex items-start gap-3 sm:gap-4 ${
                    temEmergencia ? "border-l-red-500" : temUrgente ? "border-l-amber-500" : "border-l-emerald-200"
                  }`}
                >
                  <div
                    class="text-emerald-700 font-bold text-base sm:text-xl w-8 sm:w-10 shrink-0 text-right tabular-nums"
                  >
                    {(paginaAtual - 1) * ITENS_POR_PAGINA + idx + 1}.
                  </div>
                  <div class="flex-1 min-w-0 space-y-3">
                    <div class="flex flex-col md:flex-row md:items-start md:justify-between gap-x-6 gap-y-1">
                      <div class="flex flex-wrap items-center gap-x-3 gap-y-1 min-w-0">
                        <a
                          href={`/paciente/${p.solicitacaoId}`}
                          class="rounded hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600"
                        >
                          <h3 class="text-lg font-bold text-gray-800 break-words">{p.nomePaciente}</h3>
                        </a>
                        {#if temEmergencia}
                          <span
                            class="text-xs font-bold uppercase tracking-wide px-2 py-0.5 rounded bg-red-600 text-white"
                          >
                            Emergência
                          </span>
                        {:else if temUrgente}
                          <span
                            class="text-xs font-bold uppercase tracking-wide px-2 py-0.5 rounded bg-amber-100 text-amber-900 border border-amber-300"
                          >
                            Urgente
                          </span>
                        {/if}
                      </div>
                      <p class="text-sm text-gray-600 md:text-right md:shrink-0">
                        Na fila
                        <strong class="text-base font-bold text-emerald-800">{textoEspera(p.diasEspera)}</strong>
                        <span class="whitespace-nowrap">(desde {formatarData(p.entradaMaisAntiga)})</span>
                      </p>
                    </div>

                    <div class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-x-4 gap-y-1 text-sm text-gray-800">
                      <div><span class="font-semibold text-gray-600">CPF:</span> {p.cpfPaciente || "—"}</div>
                      <div><span class="font-semibold text-gray-600">CNS:</span> {p.cns || "—"}</div>
                      <div>
                        <span class="font-semibold text-gray-600">Nascimento:</span>
                        {formatarData(p.dataNascimento)}
                      </div>
                      <div>
                        <span class="font-semibold text-gray-600">Unidade:</span>
                        {p.unidadeNome ?? "Sem unidade"}
                      </div>
                    </div>

                    <ul class="flex flex-wrap gap-2" aria-label="Pedidos na fila">
                      {#each p.itens as item (item.id)}
                        <li class={`text-xs px-2.5 py-1 rounded-md border ${classePrioridade(item.prioridade)}`}>
                          <span class="font-semibold">{item.especialidadeNome ?? "Pedido sem especialidade"}</span>
                          · {rotuloStatus(item.status)}
                          {#if item.prioridade && item.prioridade !== "NORMAL"}
                            · <span class="font-bold uppercase">{rotuloPrioridade(item.prioridade)}</span>
                          {/if}
                          · {textoEspera(item.diasEspera)}
                        </li>
                      {/each}
                    </ul>
                  </div>
                </li>
              {/each}
            </ul>

            {#if totalPages > 1}
              <nav class="flex justify-center items-center space-x-2 mt-6" aria-label="Paginação da fila de espera">
                <button
                  type="button"
                  onclick={() => buscar(paginaAtual - 1)}
                  disabled={paginaAtual === 1 || carregando}
                  class="px-3 py-1 bg-emerald-600 hover:bg-emerald-800 text-white rounded focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:opacity-50 disabled:cursor-not-allowed transition"
                >
                  &laquo; Anterior
                </button>
                <span class="text-gray-700">Página {paginaAtual} de {totalPages}</span>
                <button
                  type="button"
                  onclick={() => buscar(paginaAtual + 1)}
                  disabled={paginaAtual === totalPages || carregando}
                  class="px-3 py-1 bg-emerald-600 hover:bg-emerald-800 text-white rounded focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:opacity-50 disabled:cursor-not-allowed transition"
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
