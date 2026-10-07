<script lang="ts">
  import { getApi } from "$lib/api";
  import Content from "$lib/Content.svelte";
  import { onMount } from "svelte";
  import ChartBase from "$lib/components/ChartBase.svelte";
  import LoadingSpinner from "$lib/LoadingSpinner.svelte";
  import { user } from "$lib/stores/auth.js";
  import PainelGerencial from "./PainelGerencial.svelte";

  type TipoRelatorio = "line" | "bar" | "pie" | "doughnut"

  // --- Ranking de Profissional ---
  interface ProfissionalRanking {
    id: number
    nome: string
    conselho: string
    numeroRegistro: string
    totalSolicitacoes: number
  }

  interface EspecialidadeRanking {
    especialidadeNome: string
    total: number
  }


    interface EspecialidadesPendentes {
      id: number
      nome: string
      total: number
    }
    

  // --- Data do dia (LocalDate, no fuso local)
  const hoje = new Date().toLocaleDateString("en-CA");

  // --- Contadores do dia
  const paramsDia = new URLSearchParams();
  paramsDia.append("data", hoje);

  let pacientesAgendadosDoDia = $state<number | null>(null);
  let novosPacientesDoDia = $state<number | null>(null);
  let solicitacoesDeEspecialidadeDoDia = $state<number | null>(null);
  let inicio = $state<string | null> (null)
  let intervalo = $state<string | null>(null)
  let tipo = $state<TipoRelatorio>("bar")
  let exibirGrafico = $state(false)

  async function buscarTotalPacientesAgendadosDoDia() {
    const res = await getApi(`fechamento/total/agendados/dia?${paramsDia.toString()}`);
    pacientesAgendadosDoDia = await res.json();
  }

  async function buscarTotalPacientesNovosDoDia() {
    const res = await getApi(`fechamento/total/pacientes/novos/dia?${paramsDia.toString()}`);
    novosPacientesDoDia = await res.json();
  }

  async function buscarTotalDeSolicitacaoEspecialidadeDoDia() {
    const res = await getApi(`fechamento/total/solicitacao/especialidade/dia?${paramsDia.toString()}`);
    solicitacoesDeEspecialidadeDoDia = await res.json();
  }

  // --- Período do gráfico (ex.: ano 2025)
  const paramsPeriodo = new URLSearchParams();
  paramsPeriodo.append("inicio", "2025-01-01");
  paramsPeriodo.append("intervalo", "2026-01-01");
  paramsPeriodo.append("page", "0");
  paramsPeriodo.append("size", "10");

  type GrupoTotalDTO = { nome: string; total: number };

  let top10 = $state<GrupoTotalDTO[]>([]);
  let top10PorData = $state<GrupoTotalDTO[]>([]);
  let top10Pendentes = $state<EspecialidadesPendentes[]>([])

  async function buscarTop10PorPeriodo() {
    const res = await getApi(`fechamento/total/por/especialidade/por/tempo?${paramsPeriodo.toString()}`);
    const page = await res.json(); // Spring Page
    top10 = page?.content ?? []
  }

  async function buscarTop10PorData(inicio: string, intervalo: string) {
    const params = new URLSearchParams()
    params.append("inicio",inicio)
    params.append("intervalo", intervalo)
    const res = await getApi(`fechamento/total/por/especialidade/por/tempo?${params.toString()}`)
    const page = await res.json();
    top10PorData = page?.content ?? []

  }

  async function buscarTop10Pendentes() {
    try {
      const res = await getApi(`fechamento/especialidades/pendentes/top10`)
      if(!res.ok){
        alert("Erro ao buscar dados do Rank de Pendentes ")
      }
      
      const data = await res.json()
      top10Pendentes = data

    } catch (error) {
      
    }
    
  }
  
  let labels = $derived(top10.map((x) => x.nome));
  let values = $derived(top10.map((x) => x.total));

  let nomes = $derived(top10PorData.map((x) => x.nome));
  let valores = $derived(top10PorData.map((x) => x.total));
  
  let nomeEspecialidadesPendentes = $derived(top10Pendentes.map((x) => x.nome))
  let valoresEspecialidadesPendentes = $derived(top10Pendentes.map((x) => x.total))
  
  function gerarGrafico(){
    if(inicio === null){
      alert("Selecione uma data de início válida para o período !")
      return
    }
    if(intervalo === null){
      alert("Selecione um intervalo válido para o período")
      return
    }
    if(top10Pendentes.length === 0 || null){
      alert("Não há dados para exibir o gráfico")
      return
    }
    exibirGrafico = true
  }

  // --- Tempo de Espera por Especialidade ---
  interface Unidade { id: number; nome: string }
  interface EspecialidadeOpcao { id: number; nome: string }
  interface TempoEsperaEspecialidade {
    especialidadeId: number
    especialidadeNome: string
    totalAgendados: number
    tempoMedioEsperaDias: number | null
    tempoMinimoEsperaDias: number | null
    tempoMaximoEsperaDias: number | null
  }
  interface TempoEsperaGeral {
    totalAgendados: number
    tempoMedioEsperaDias: number | null
    tempoMinimoEsperaDias: number | null
    tempoMaximoEsperaDias: number | null
  }

  let unidadesEspera = $state<Unidade[]>([])
  let especialidadesEspera = $state<EspecialidadeOpcao[]>([])
  let esperaInicio = $state<string>('')
  let esperaFim = $state<string>('')
  let esperaUnidadeId = $state<string>('')
  let esperaEspecialidadeId = $state<string>('')
  let esperaCarregando = $state(false)
  let esperaGeral = $state<TempoEsperaGeral | null>(null)
  let esperaPorEspecialidade = $state<TempoEsperaEspecialidade[]>([])

  const esperaTop10 = $derived(esperaPorEspecialidade.slice(0, 10))
  const esperaLabels = $derived(esperaTop10.map(e => e.especialidadeNome))
  const esperaValores = $derived(esperaTop10.map(e => Math.round((e.tempoMedioEsperaDias ?? 0) * 10) / 10))

  function formatarDias(v: number | null): string {
    if (v === null || v === undefined) return '-'
    return `${Math.round(v * 10) / 10} dias`
  }

  async function carregarFiltrosEspera() {
    try {
      const [resUnidades, resEspecialidades] = await Promise.all([
        getApi('unidades/ativas'),
        getApi('catalog/especialidades/listar')
      ])
      unidadesEspera = resUnidades.ok ? await resUnidades.json() : []
      especialidadesEspera = resEspecialidades.ok ? await resEspecialidades.json() : []
    } catch {
      unidadesEspera = []
      especialidadesEspera = []
    }
  }

  async function buscarTempoEspera() {
    esperaCarregando = true
    try {
      const paramsBase = new URLSearchParams()
      if (esperaInicio) paramsBase.set('inicio', esperaInicio)
      if (esperaFim) paramsBase.set('fim', esperaFim)
      if (esperaUnidadeId) paramsBase.set('unidadeId', esperaUnidadeId)

      const paramsGeral = new URLSearchParams(paramsBase)
      if (esperaEspecialidadeId) paramsGeral.set('especialidadeId', esperaEspecialidadeId)

      const [resGeral, resPorEspecialidade] = await Promise.all([
        getApi(`fechamento/tempo-espera/geral?${paramsGeral}`),
        getApi(`fechamento/tempo-espera/por-especialidade?${paramsBase}`)
      ])
      esperaGeral = resGeral.ok ? await resGeral.json() : null
      esperaPorEspecialidade = resPorEspecialidade.ok ? await resPorEspecialidade.json() : []
    } catch {
      esperaGeral = null
      esperaPorEspecialidade = []
    } finally {
      esperaCarregando = false
    }
  }

  // --- Ranking Profissional ---
  let rankInicio = $state<string>('')
  let rankFim = $state<string>('')
  let rankingProfissionais = $state<ProfissionalRanking[]>([])
  let rankingCarregando = $state(false)
  let profissionalSelecionado = $state<ProfissionalRanking | null>(null)
  let especialidadesProfissional = $state<EspecialidadeRanking[]>([])
  let especialidadesCarregando = $state(false)

  const rankLabels = $derived(rankingProfissionais.map(p => p.nome.split(' ').slice(0, 2).join(' ')))
  const rankValues = $derived(rankingProfissionais.map(p => p.totalSolicitacoes))
  const espLabels  = $derived(especialidadesProfissional.map(e => e.especialidadeNome))
  const espValues  = $derived(especialidadesProfissional.map(e => e.total))

  // --- Apresentação (somente visual: não altera dados nem filtros) ---
  type ItemLista = { nome: string; total: number }

  // Verdadeiro até a primeira carga terminar: separa "carregando" de "sem dados".
  let cargaInicial = $state(true)

  const hojeExtenso = new Date().toLocaleDateString("pt-BR", { weekday: "long", day: "numeric", month: "long", year: "numeric" })

  // Os indicadores de gestão só existem para administrador e gestor. Para os demais
  // perfis as seções, as âncoras e as chamadas nem são montadas — e, se fossem, o
  // backend responderia 403: a barreira é lá, isto aqui só evita a chamada inútil.
  const veGerenciais = $derived($user?.role === "ADMIN" || $user?.role === "GESTOR")

  const SECOES_GERAIS = [
    { id: "hoje", rotulo: "Hoje" },
    { id: "agendamentos", rotulo: "Agendamentos por grupo" },
    { id: "fila-e-espera", rotulo: "Fila e espera" },
    { id: "profissionais", rotulo: "Profissionais solicitantes" }
  ]
  const SECOES_DE_GESTAO = [
    { id: "gestao-fila", rotulo: "Gestão: fila e operação" },
    { id: "gestao-cotas", rotulo: "Gestão: cotas" },
    { id: "gestao-custos", rotulo: "Gestão: custos" },
    { id: "gestao-whatsapp", rotulo: "Gestão: WhatsApp" }
  ]
  const SECOES = $derived(veGerenciais ? [...SECOES_GERAIS, ...SECOES_DE_GESTAO] : SECOES_GERAIS)

  const CAMPO = "w-full border border-gray-300 rounded-lg p-2 bg-white text-gray-900 focus:outline-hidden focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500"
  const ROTULO = "block text-sm font-semibold text-gray-700 mb-1"
  const BOTAO_PRIMARIO = "inline-flex items-center justify-center min-h-10 px-4 py-2 rounded-lg bg-emerald-700 text-white text-sm font-semibold hover:bg-emerald-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:opacity-60 disabled:cursor-not-allowed transition-colors cursor-pointer"
  const CARTAO = "bg-white rounded-lg shadow p-4 md:p-6 space-y-5"

  function formatarDataBr(iso: string | null): string {
    if (!iso) return ""
    const [ano, mes, dia] = iso.split("-")
    return `${dia}/${mes}/${ano}`
  }

  const formatarNumero = (n: number) => Number(n).toLocaleString("pt-BR")

  async function buscarRankingProfissionais() {
    rankingCarregando = true
    profissionalSelecionado = null
    especialidadesProfissional = []
    try {
      const params = new URLSearchParams({ limite: '10' })
      if (rankInicio) params.set('inicio', rankInicio)
      if (rankFim)    params.set('fim', rankFim)
      const res = await getApi(`fechamento/profissionais/ranking?${params}`)
      rankingProfissionais = res.ok ? await res.json() : []
    } catch { rankingProfissionais = [] }
    finally { rankingCarregando = false }
  }

  async function selecionarProfissional(p: ProfissionalRanking) {
    profissionalSelecionado = p
    especialidadesCarregando = true
    especialidadesProfissional = []
    try {
      const params = new URLSearchParams()
      if (rankInicio) params.set('inicio', rankInicio)
      if (rankFim)    params.set('fim', rankFim)
      const res = await getApi(`fechamento/profissionais/${p.id}/especialidades?${params}`)
      especialidadesProfissional = res.ok ? await res.json() : []
    } catch { especialidadesProfissional = [] }
    finally { especialidadesCarregando = false }
  }

  





  onMount(async () => {
    try {
      await Promise.all([
        buscarTotalPacientesNovosDoDia(),
        buscarTotalPacientesAgendadosDoDia(),
        buscarTotalDeSolicitacaoEspecialidadeDoDia(),
        buscarTop10PorPeriodo(),
        buscarTop10Pendentes(),
        buscarRankingProfissionais(),
        carregarFiltrosEspera().then(buscarTempoEspera)
      ]);
    } finally {
      // Só o estado visual de carregamento; as mesmas buscas, na mesma ordem.
      cargaInicial = false
    }
  });
</script>
<svelte:head>
  <title>Indicadores</title>
</svelte:head>

<!-- Cabeçalho de um grupo de indicadores. Cada grupo é uma <section> independente:
     um bloco novo entra como mais um cartão dentro do grupo, ou como um grupo novo. -->
{#snippet cabecalhoSecao(id: string, titulo: string, descricao: string)}
  <div class="flex flex-wrap items-baseline gap-x-3 gap-y-1">
    <h2 id={`${id}-titulo`} class="text-xl font-bold text-gray-900">{titulo}</h2>
    <p class="text-sm text-gray-700">{descricao}</p>
  </div>
{/snippet}

<!-- Número-chave. `valor` nulo = ainda carregando (na primeira carga) ou indisponível. -->
{#snippet indicador(rotulo: string, valor: string | number | null, detalhe: string, classes: string)}
  <article class={`rounded-lg p-5 border-l-4 ${classes}`}>
    <h3 class="text-sm font-semibold text-gray-700">{rotulo}</h3>
    {#if valor === null && cargaInicial}
      <div class="mt-2 min-h-10 flex items-center">
        <LoadingSpinner tamanho={20} inline mensagem="Carregando..." />
      </div>
    {:else if valor === null}
      <p class="text-4xl font-bold tracking-tight text-gray-900 mt-1" aria-hidden="true">—</p>
      <p class="text-sm font-medium text-amber-800 mt-1">Indisponível no momento.</p>
    {:else}
      <p class="text-4xl font-bold tracking-tight text-gray-900 mt-1 tabular-nums break-words">
        {typeof valor === "number" ? formatarNumero(valor) : valor}
      </p>
    {/if}
    <p class="text-sm text-gray-700 mt-2">{detalhe}</p>
  </article>
{/snippet}

<!-- O canvas do gráfico não tem texto: a descrição vai no rótulo e os valores, na lista ao lado. -->
{#snippet grafico(tipoGrafico: TipoRelatorio, rotulos: string[], numeros: number[], titulo: string, descricao: string)}
  <div role="img" aria-label={descricao}>
    <ChartBase type={tipoGrafico} labels={rotulos} values={numeros} title={titulo} />
  </div>
{/snippet}

<!-- Mesmos valores do gráfico, em texto: nome, total e barra proporcional ao maior da lista. -->
{#snippet listaBarras(itens: ItemLista[], numerada: boolean)}
  {@const maior = Math.max(...itens.map((item) => item.total), 1)}
  <svelte:element this={numerada ? "ol" : "ul"} class="space-y-3">
    {#each itens as item, i (i)}
      <li class="grid grid-cols-[minmax(0,1fr)_auto] items-baseline gap-x-3 gap-y-1 text-sm">
        <span class="text-gray-900 break-words">
          {#if numerada}<span class="text-gray-600 tabular-nums">{i + 1}.</span>{/if}
          {item.nome}
        </span>
        <span class="font-bold text-gray-900 tabular-nums">{formatarNumero(item.total)}</span>
        <span class="col-span-2 block h-2 rounded-full bg-gray-200 overflow-hidden" aria-hidden="true">
          <span
            class="block h-full rounded-full bg-emerald-600"
            style={`width: ${Math.round((item.total / maior) * 100)}%`}
          ></span>
        </span>
      </li>
    {/each}
  </svelte:element>
{/snippet}

{#snippet vazio(mensagem: string)}
  <p class="text-sm text-gray-700 bg-gray-50 border border-dashed border-gray-300 rounded-lg px-4 py-6 text-center">
    {mensagem}
  </p>
{/snippet}

<Content titleH1="Indicadores" page="/indicadores">
  <div class="flex-1 bg-gray-100 p-4 md:p-6 space-y-10">
    <nav aria-label="Seções dos indicadores" class="flex flex-wrap gap-2">
      {#each SECOES as secao (secao.id)}
        <a
          href={`#${secao.id}`}
          class="inline-flex items-center min-h-9 px-3 py-1.5 rounded-full bg-white border border-gray-300 text-sm font-medium text-gray-800 hover:bg-emerald-50 hover:border-emerald-600 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 transition-colors"
        >
          {secao.rotulo}
        </a>
      {/each}
    </nav>

    <!-- ═══════════════════════════════════════════════════════════
         HOJE
    ════════════════════════════════════════════════════════════════ -->
    <section id="hoje" aria-labelledby="hoje-titulo" class="space-y-4 scroll-mt-4">
      {@render cabecalhoSecao("hoje", "Hoje", hojeExtenso)}
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
        {@render indicador(
          "Agendados para hoje",
          pacientesAgendadosDoDia,
          "Solicitações com atendimento marcado para hoje.",
          "bg-white shadow border-l-sky-500"
        )}
        {@render indicador(
          "Pacientes novos hoje",
          novosPacientesDoDia,
          "Solicitações com data de malote de hoje.",
          "bg-white shadow border-l-emerald-600"
        )}
        {@render indicador(
          "Especialidades solicitadas hoje",
          solicitacoesDeEspecialidadeDoDia,
          "Consultas e exames pedidos, pela data de cadastro.",
          "bg-white shadow border-l-amber-400"
        )}
      </div>
    </section>

    <!-- ═══════════════════════════════════════════════════════════
         AGENDAMENTOS POR GRUPO
    ════════════════════════════════════════════════════════════════ -->
    <section id="agendamentos" aria-labelledby="agendamentos-titulo" class="space-y-4 scroll-mt-4">
      {@render cabecalhoSecao(
        "agendamentos",
        "Agendamentos por grupo",
        "Consultas e exames agendados, somados por grupo, pela data marcada para o atendimento."
      )}

      <div class="grid grid-cols-1 xl:grid-cols-2 gap-6 items-start">
        <article class={CARTAO}>
          <div>
            <h3 class="text-lg font-bold text-emerald-800">Período de referência</h3>
            <p class="text-sm text-gray-700 mt-1">
              De {formatarDataBr(paramsPeriodo.get("inicio"))} até {formatarDataBr(paramsPeriodo.get("intervalo"))}. O dia
              final não entra na contagem. Até 10 grupos.
            </p>
          </div>

          {#if top10.length > 0}
            {@render grafico(
              "pie",
              labels,
              values,
              "Itens agendados",
              "Gráfico de pizza dos itens agendados por grupo no período de referência. Os valores estão na lista a seguir."
            )}
            {@render listaBarras(top10, false)}
          {:else if cargaInicial}
            <LoadingSpinner mensagem="Carregando os agendamentos por grupo..." />
          {:else}
            {@render vazio("Nenhum agendamento encontrado no período de referência.")}
          {/if}
        </article>

        <article class={CARTAO}>
          <div>
            <h3 class="text-lg font-bold text-emerald-800">Outro período</h3>
            <p class="text-sm text-gray-700 mt-1">Escolha as datas e o tipo de gráfico. Até 10 grupos.</p>
          </div>

          <form onsubmit={() => buscarTop10PorData(inicio, intervalo)} class="space-y-4">
            <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <div>
                <label for="periodo-inicio" class={ROTULO}>Data inicial</label>
                <input id="periodo-inicio" type="date" bind:value={inicio} class={CAMPO} />
              </div>

              <div>
                <label for="periodo-fim" class={ROTULO}>Data final</label>
                <input
                  id="periodo-fim"
                  type="date"
                  bind:value={intervalo}
                  aria-describedby="periodo-fim-dica"
                  class={CAMPO}
                />
              </div>

              <div>
                <label for="periodo-tipo" class={ROTULO}>Tipo de gráfico</label>
                <select name="" id="periodo-tipo" bind:value={tipo} class={CAMPO}>
                  <option value="bar">Barras</option>
                  <option value="pie">Pizza</option>
                  <option value="line">Linha</option>
                  <option value="doughnut">Rosca</option>
                </select>
              </div>
            </div>

            <p id="periodo-fim-dica" class="text-sm text-gray-700">O dia final não entra na contagem.</p>

            <button onclick={() => gerarGrafico()} class={BOTAO_PRIMARIO}>Gerar gráfico</button>
          </form>

          <div aria-live="polite" class="space-y-5">
            {#if !exibirGrafico}
              {@render vazio("Informe as datas e clique em “Gerar gráfico” para ver os agendamentos do período.")}
            {:else if valores.length === 0}
              {@render vazio("Nenhum agendamento encontrado entre as datas informadas.")}
            {:else}
              {@render grafico(
                tipo,
                nomes,
                valores,
                "Itens agendados",
                `Gráfico dos itens agendados por grupo de ${formatarDataBr(inicio)} até ${formatarDataBr(intervalo)}. Os valores estão na lista a seguir.`
              )}
              {@render listaBarras(top10PorData, false)}
            {/if}
          </div>
        </article>
      </div>
    </section>

    <!-- ═══════════════════════════════════════════════════════════
         FILA E ESPERA
    ════════════════════════════════════════════════════════════════ -->
    <section id="fila-e-espera" aria-labelledby="fila-e-espera-titulo" class="space-y-4 scroll-mt-4">
      {@render cabecalhoSecao(
        "fila-e-espera",
        "Fila e espera",
        "O que ainda aguarda agendamento e quanto tempo o paciente esperou até ser agendado."
      )}

      <article class={CARTAO}>
        <div>
          <h3 class="text-lg font-bold text-emerald-800">Pedidos aguardando por especialidade</h3>
          <p class="text-sm text-gray-700 mt-1">
            As 10 especialidades com menos pedidos aguardando agendamento, da menor para a maior.
          </p>
        </div>

        {#if top10Pendentes.length > 0}
          <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
            {@render grafico(
              "bar",
              nomeEspecialidadesPendentes,
              valoresEspecialidadesPendentes,
              "Pedidos aguardando",
              "Gráfico de barras dos pedidos aguardando agendamento por especialidade. Os valores estão na lista a seguir."
            )}
            {@render listaBarras(top10Pendentes, false)}
          </div>
        {:else if cargaInicial}
          <LoadingSpinner mensagem="Carregando os pedidos aguardando..." />
        {:else}
          {@render vazio("Nenhum pedido aguardando agendamento.")}
        {/if}
      </article>

      <article class={CARTAO}>
        <div>
          <h3 class="text-lg font-bold text-emerald-800">Tempo de espera por especialidade</h3>
          <p class="text-sm text-gray-700 mt-1">
            Dias entre a data da solicitação e a data do atendimento agendado. Considera apenas solicitações já
            agendadas no período.
          </p>
        </div>

        <!-- Filtros -->
        <div class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-5 gap-4 items-end">
          <div>
            <label class={ROTULO} for="espera-inicio">De</label>
            <input id="espera-inicio" type="date" bind:value={esperaInicio} class={CAMPO} />
          </div>
          <div>
            <label class={ROTULO} for="espera-fim">Até</label>
            <input id="espera-fim" type="date" bind:value={esperaFim} class={CAMPO} />
          </div>
          <div>
            <label class={ROTULO} for="espera-unidade">Unidade</label>
            <select id="espera-unidade" bind:value={esperaUnidadeId} class={CAMPO}>
              <option value="">Todas</option>
              {#each unidadesEspera as u (u.id)}
                <option value={u.id}>{u.nome}</option>
              {/each}
            </select>
          </div>
          <div>
            <label class={ROTULO} for="espera-especialidade">Especialidade</label>
            <select id="espera-especialidade" bind:value={esperaEspecialidadeId} class={CAMPO}>
              <option value="">Todas</option>
              {#each especialidadesEspera as e (e.id)}
                <option value={e.id}>{e.nome}</option>
              {/each}
            </select>
          </div>
          <div>
            <button type="button" onclick={buscarTempoEspera} disabled={esperaCarregando} class={BOTAO_PRIMARIO}>
              {esperaCarregando ? "Carregando..." : "Aplicar filtros"}
            </button>
          </div>
        </div>

        {#if esperaCarregando}
          <LoadingSpinner mensagem="Calculando o tempo de espera..." />
        {:else}
          <div class="space-y-2">
            <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
              {@render indicador(
                "Tempo médio de espera",
                formatarDias(esperaGeral?.tempoMedioEsperaDias ?? null),
                "Média entre o pedido e o atendimento.",
                "bg-gray-50 border border-gray-200 border-l-emerald-600"
              )}
              {@render indicador(
                "Menor espera",
                formatarDias(esperaGeral?.tempoMinimoEsperaDias ?? null),
                "Caso agendado mais rápido.",
                "bg-gray-50 border border-gray-200 border-l-sky-500"
              )}
              {@render indicador(
                "Maior espera",
                formatarDias(esperaGeral?.tempoMaximoEsperaDias ?? null),
                "Caso que mais demorou a ser agendado.",
                "bg-gray-50 border border-gray-200 border-l-amber-400"
              )}
            </div>
            <p class="text-sm text-gray-700">
              Baseado em {esperaGeral?.totalAgendados ?? 0} solicitações já agendadas{esperaEspecialidadeId
                ? " para a especialidade selecionada"
                : ""}.
            </p>
          </div>

          {#if esperaPorEspecialidade.length === 0}
            {@render vazio("Nenhuma solicitação agendada encontrada para os filtros selecionados.")}
          {:else}
            <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
              <div class="space-y-3">
                <h4 class="text-xs font-semibold text-gray-700 uppercase tracking-widest">
                  10 maiores esperas médias
                </h4>
                {@render grafico(
                  "bar",
                  esperaLabels,
                  esperaValores,
                  "Tempo médio de espera (dias)",
                  "Gráfico de barras das 10 especialidades com maior tempo médio de espera, em dias. Os valores estão na tabela ao lado."
                )}
              </div>
              <div class="space-y-3">
                <h4 id="espera-tabela-titulo" class="text-xs font-semibold text-gray-700 uppercase tracking-widest">
                  Todas as especialidades
                </h4>
                <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
                <div
                  class="overflow-x-auto max-h-96 overflow-y-auto rounded-lg border border-gray-200 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600"
                  role="region"
                  aria-labelledby="espera-tabela-titulo"
                  tabindex="0"
                >
                  <table class="w-full text-sm">
                    <thead class="sticky top-0 bg-gray-50">
                      <tr class="text-left text-gray-700 border-b border-gray-300">
                        <th scope="col" class="py-2 px-3 font-semibold">Especialidade</th>
                        <th scope="col" class="py-2 px-3 font-semibold text-right whitespace-nowrap">Agendados</th>
                        <th scope="col" class="py-2 px-3 font-semibold text-right whitespace-nowrap">Espera média</th>
                      </tr>
                    </thead>
                    <tbody>
                      {#each esperaPorEspecialidade as e (e.especialidadeId)}
                        <tr class="border-b border-gray-200 last:border-b-0 hover:bg-gray-50">
                          <th scope="row" class="py-2 px-3 font-medium text-gray-900 text-left">
                            {e.especialidadeNome}
                          </th>
                          <td class="py-2 px-3 text-right tabular-nums text-gray-900">{e.totalAgendados}</td>
                          <td class="py-2 px-3 text-right tabular-nums whitespace-nowrap font-bold text-gray-900">
                            {formatarDias(e.tempoMedioEsperaDias)}
                          </td>
                        </tr>
                      {/each}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          {/if}
        {/if}
      </article>
    </section>

    <!-- ═══════════════════════════════════════════════════════════
         PROFISSIONAIS SOLICITANTES
    ════════════════════════════════════════════════════════════════ -->
    <section id="profissionais" aria-labelledby="profissionais-titulo" class="space-y-4 scroll-mt-4">
      {@render cabecalhoSecao(
        "profissionais",
        "Profissionais solicitantes",
        "Quem mais solicita e quais especialidades predominam."
      )}

      <article class={CARTAO}>
        <div class="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
          <h3 class="text-lg font-bold text-emerald-800">Ranking de solicitações</h3>
          <a
            href="/relatorio/profissional"
            class="text-sm font-medium text-emerald-700 hover:underline rounded focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600"
          >
            Ver relatório completo
          </a>
        </div>
        <p class="text-sm text-gray-700">
          Os 10 profissionais com mais solicitações no período. Para filtrar por unidade, exportar em Excel ou ver
          cada solicitação, use o relatório completo.
        </p>

        <!-- Filtros -->
        <div class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-5 gap-4 items-end">
          <div>
            <label class={ROTULO} for="rank-inicio">De</label>
            <input id="rank-inicio" type="date" bind:value={rankInicio} class={CAMPO} />
          </div>
          <div>
            <label class={ROTULO} for="rank-fim">Até</label>
            <input id="rank-fim" type="date" bind:value={rankFim} class={CAMPO} />
          </div>
          <div>
            <button
              type="button"
              onclick={buscarRankingProfissionais}
              disabled={rankingCarregando}
              class={BOTAO_PRIMARIO}
            >
              {rankingCarregando ? "Carregando..." : "Aplicar filtros"}
            </button>
          </div>
        </div>

        {#if rankingProfissionais.length === 0 && !rankingCarregando}
          {@render vazio("Nenhuma solicitação encontrada para o período selecionado.")}
        {:else if rankingProfissionais.length === 0}
          <LoadingSpinner mensagem="Carregando o ranking..." />
        {:else}
          <div class="space-y-6 transition-opacity" class:opacity-60={rankingCarregando} aria-busy={rankingCarregando}>
            <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
              <div class="space-y-3">
                <div>
                  <h4 class="text-xs font-semibold text-gray-700 uppercase tracking-widest">Ranking</h4>
                  <p class="text-sm text-gray-700 mt-1">
                    Selecione um profissional para ver as especialidades que ele mais solicita.
                  </p>
                </div>
                <ol class="space-y-2">
                  {#each rankingProfissionais as prof, i (prof.id)}
                    {@const selecionado = profissionalSelecionado?.id === prof.id}
                    <li>
                      <button
                        type="button"
                        aria-pressed={selecionado}
                        class="w-full text-left flex items-center gap-3 p-3 rounded-lg border cursor-pointer transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600
                          {selecionado
                          ? 'border-emerald-600 bg-emerald-50 ring-1 ring-emerald-600'
                          : 'border-gray-300 bg-white hover:border-emerald-600 hover:bg-gray-50'}"
                        onclick={() => selecionarProfissional(prof)}
                      >
                        <!-- Posição -->
                        <span
                          class="w-8 h-8 shrink-0 rounded-full flex items-center justify-center text-sm font-bold tabular-nums
                            {i < 3 ? 'bg-emerald-700 text-white' : 'bg-gray-100 text-gray-800 border border-gray-300'}"
                        >
                          {i + 1}<span class="sr-only">º lugar</span>
                        </span>
                        <!-- Dados -->
                        <span class="flex-1 min-w-0">
                          <span class="block font-semibold text-gray-900 break-words">{prof.nome}</span>
                          {#if prof.conselho && prof.numeroRegistro}
                            <span class="block text-xs text-gray-700">{prof.conselho} {prof.numeroRegistro}</span>
                          {/if}
                        </span>
                        <!-- Total -->
                        <span class="shrink-0 text-right">
                          <span class="block text-lg font-bold text-gray-900 tabular-nums">
                            {formatarNumero(prof.totalSolicitacoes)}
                          </span>
                          <span class="block text-xs text-gray-700">solicitações</span>
                        </span>
                        <!-- Indicador de seleção (não depende só da cor) -->
                        <svg
                          class="w-5 h-5 shrink-0 {selecionado ? 'text-emerald-700' : 'text-gray-400'}"
                          fill="none"
                          stroke="currentColor"
                          stroke-width="2"
                          viewBox="0 0 24 24"
                          aria-hidden="true"
                        >
                          {#if selecionado}
                            <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
                          {:else}
                            <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7" />
                          {/if}
                        </svg>
                      </button>
                    </li>
                  {/each}
                </ol>
              </div>

              <div class="space-y-3">
                <h4 class="text-xs font-semibold text-gray-700 uppercase tracking-widest">
                  Solicitações por profissional
                </h4>
                {#if rankValues.length > 0}
                  {@render grafico(
                    "bar",
                    rankLabels,
                    rankValues,
                    "Solicitações por profissional",
                    "Gráfico de barras das solicitações por profissional. Os valores estão no ranking ao lado."
                  )}
                {/if}
              </div>
            </div>

            <!-- Especialidades do profissional selecionado -->
            {#if profissionalSelecionado}
              <div class="pt-5 border-t border-gray-200 space-y-4" aria-live="polite">
                <h4 class="text-base font-bold text-gray-900">
                  Especialidades solicitadas por {profissionalSelecionado.nome}
                </h4>

                {#if especialidadesCarregando}
                  <LoadingSpinner mensagem="Carregando as especialidades..." />
                {:else if especialidadesProfissional.length === 0}
                  {@render vazio("Nenhuma especialidade registrada para este profissional no período.")}
                {:else}
                  <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
                    {@render listaBarras(
                      especialidadesProfissional.map((esp) => ({ nome: esp.especialidadeNome, total: esp.total })),
                      true
                    )}
                    {@render grafico(
                      "doughnut",
                      espLabels,
                      espValues,
                      `Especialidades de ${profissionalSelecionado.nome}`,
                      `Gráfico de rosca das especialidades solicitadas por ${profissionalSelecionado.nome}. Os valores estão na lista ao lado.`
                    )}
                  </div>
                {/if}
              </div>
            {/if}
          </div>
        {/if}
      </article>
    </section>

    {#if veGerenciais}
      <PainelGerencial />
    {/if}
  </div>
</Content>
