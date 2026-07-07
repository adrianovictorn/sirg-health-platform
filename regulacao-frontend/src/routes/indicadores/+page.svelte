<script lang="ts">
  import { getApi } from "$lib/api";
  import Content from "$lib/Content.svelte";
  import { onMount } from "svelte";
  import ChartBase from "$lib/components/ChartBase.svelte";

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

  const MEDALHAS = ['🥇', '🥈', '🥉']

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
    await Promise.all([
      buscarTotalPacientesNovosDoDia(),
      buscarTotalPacientesAgendadosDoDia(),
      buscarTotalDeSolicitacaoEspecialidadeDoDia(),
      buscarTop10PorPeriodo(),
      buscarTop10Pendentes(),
      buscarRankingProfissionais(),
      carregarFiltrosEspera().then(buscarTempoEspera)
    ]);
  });
</script>
<svelte:head>
  <title>Indicadores</title>
</svelte:head>
<Content titleH1="Indicadores" page="/indicadores">
  <section class="grid grid-cols-1  p-2 m-5 gap-4 bg-white rounded-lg  text-center justify-center items-center">
    <h2 class="text-3xl font-bold text-gray-800 mt-4 ml-6">
      Contadores de Produção
    </h2>
    <div class="grid grid-cols-1 items-center m-auto md:grid-cols-3">
    
          <div class="flex flex-col bg-amber-600 hover:bg-amber-800 rounded-xl border border-gray-400 text-center m-5 p-5 shadow-2xl transition-all duration-200 hover:-translate-y-2 hover:shadow-lg">
            <h3 class="text-white font-bold font-mono text-xl">Total de Agendados do Dia</h3>
            <p class="text-white font-bold text-2xl mt-4">{pacientesAgendadosDoDia ?? "-"}</p>
          </div>
      
          <div class=" flex flex-col bg-emerald-600 hover:bg-emerald-800 rounded-xl border border-gray-400 text-center m-5 p-5 shadow-2xl transition-all duration-200 hover:-translate-y-2 hover:shadow-lg">
            <h3 class="text-white font-bold font-mono text-xl">Total de Pacientes do Dia</h3>
            <p class="text-white font-bold text-2xl mt-4">{novosPacientesDoDia ?? "-"}</p>
          </div>
      
          <div class= " flex flex-col bg-sky-600 hover:bg-sky-800 rounded-xl border border-gray-400 text-center m-5 p-5 shadow-2xl transition-all duration-300 hover:-translate-y-2 hover:shadow-lg">
            <h3 class="text-white font-bold font-mono text-xl">Total de Especialidades Dia</h3>
            <p class="text-white font-bold text-2xl mt-4">{solicitacoesDeEspecialidadeDoDia ?? "-"}</p>
          </div>
    </div>
  </section>

  <section class="bg-white rounded-lg m-5 p-6 shadow">
    <div class="flex items-center justify-between gap-4 flex-wrap">
      <h2 class="text-2xl font-bold text-gray-800">Top 10 Grupos no 2025 à 2026</h2>
      <p class="text-sm text-gray-600">
        {paramsPeriodo.get("inicio")} até {paramsPeriodo.get("intervalo")} (fim exclusivo)
      </p>
    </div>
     <ChartBase
    type="pie"
    labels = {labels}
    values = {values}
    title = "Top Grupos do período"
    />

  </section>


  
  
  <section class="bg-white rounded-lg m-5 p-6 shadow">
    <div class="flex items-center justify-between gap-4 flex-wrap">
      <h2 class="text-2xl font-bold text-gray-800">Top 10 Grupos no 2025 à 2026</h2>
      <p class="text-sm text-gray-600">
      </p>
    </div>
    <ChartBase
    type="bar"
    labels = {nomeEspecialidadesPendentes}
    values = {valoresEspecialidadesPendentes}
    title = "Top Grupos do período"
    />
    
  </section>
  <section class="bg-white p-6 m-5 rounded-lg shadow">
      <h2 class="text-2xl font-bold text-gray-800">Top 10 Grupos no Período</h2>
    <form onsubmit={() => buscarTop10PorData(inicio, intervalo)} class="grid grid-cols-1  md:grid-cols-5 md:mt-5 md:gap-3">

      <div class="flex flex-col">
        <label for="">Selecione data inicio</label>
        <input type="date" bind:value={inicio} class="rounded border-gray-300 w-full">
      </div>


      <div class="flex flex-col">
        <label for="">Selecione data intervalo</label>
        <input type="date" bind:value={intervalo} class=" border-gray-300 rounded w-full">
      </div>

      <div class="flex flex-col">
        <label for="">Selecione o tipo do Gráfico</label>
        <select name="" id="" bind:value={tipo} class="rounded border-gray-300 w-full">
          <option value="bar">Barras</option>
          <option value="pie">Pizza</option>
          <option value="line">Gráfico de Linha</option>
          <option value="doughnut">Gráfico de Rosca</option>
        </select>
      </div>

      <div class="col-span-2">
              <button onclick={() => gerarGrafico()} class="text-white md:col-span-2 w-full bg-sky-800 hover:bg-sky-950 rounded flex text-center justify-center m-2 mt-5 p-3 cursor-pointer" >Gerar</button>

      </div>

    </form>

    {#if !exibirGrafico}
      <p>Selecione o período para exibir o Relatório</p>
      {:else}

      {#if valores.length === 0}
        <p class="text-center text-red-600 mt-5">Não há dados para exibir o gráfico</p>

        {:else}
        <ChartBase 
          type = {tipo}
          labels = {nomes}
          values = {valores}
          title = "Top Especialidades no período de {inicio} e {intervalo}"
          />
      {/if}
    {/if}
  </section>

  <!-- ═══════════════════════════════════════════════════════════
       TEMPO DE ESPERA POR ESPECIALIDADE
  ════════════════════════════════════════════════════════════════ -->
  <section class="bg-white rounded-lg m-5 p-6 shadow">
    <div class="flex items-center gap-3 mb-1">
      <svg class="w-6 h-6 text-emerald-700" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <h2 class="text-2xl font-bold text-gray-800">Tempo de Espera por Especialidade</h2>
    </div>
    <p class="text-sm text-gray-500 mb-4">
      Dias entre a data da solicitação e a data do atendimento agendado. Considera apenas solicitações já agendadas no período.
    </p>

    <!-- Filtros -->
    <div class="flex flex-wrap gap-3 mb-5 items-end">
      <div class="flex flex-col">
        <label class="text-xs text-gray-500 mb-1" for="espera-inicio">De</label>
        <input id="espera-inicio" type="date" bind:value={esperaInicio} class="border rounded px-2 py-1 text-sm" />
      </div>
      <div class="flex flex-col">
        <label class="text-xs text-gray-500 mb-1" for="espera-fim">Até</label>
        <input id="espera-fim" type="date" bind:value={esperaFim} class="border rounded px-2 py-1 text-sm" />
      </div>
      <div class="flex flex-col">
        <label class="text-xs text-gray-500 mb-1" for="espera-unidade">Unidade</label>
        <select id="espera-unidade" bind:value={esperaUnidadeId} class="border rounded px-2 py-1.5 text-sm">
          <option value="">Todas</option>
          {#each unidadesEspera as u (u.id)}
            <option value={u.id}>{u.nome}</option>
          {/each}
        </select>
      </div>
      <div class="flex flex-col">
        <label class="text-xs text-gray-500 mb-1" for="espera-especialidade">Especialidade</label>
        <select id="espera-especialidade" bind:value={esperaEspecialidadeId} class="border rounded px-2 py-1.5 text-sm">
          <option value="">Todas</option>
          {#each especialidadesEspera as e (e.id)}
            <option value={e.id}>{e.nome}</option>
          {/each}
        </select>
      </div>
      <button
        onclick={buscarTempoEspera}
        disabled={esperaCarregando}
        class="px-4 py-1.5 bg-emerald-700 text-white rounded text-sm hover:bg-emerald-800 disabled:opacity-50 transition-colors">
        {esperaCarregando ? 'Carregando...' : 'Filtrar'}
      </button>
    </div>

    {#if esperaCarregando}
      <p class="text-center text-gray-400 py-8">Carregando...</p>
    {:else}
      <!-- Indicador principal -->
      <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
        <div class="flex flex-col bg-emerald-700 rounded-xl p-5 text-center shadow">
          <span class="text-white text-sm uppercase tracking-wide">Tempo Médio de Espera</span>
          <span class="text-white font-bold text-3xl mt-2">{formatarDias(esperaGeral?.tempoMedioEsperaDias ?? null)}</span>
        </div>
        <div class="flex flex-col bg-sky-700 rounded-xl p-5 text-center shadow">
          <span class="text-white text-sm uppercase tracking-wide">Menor Espera</span>
          <span class="text-white font-bold text-3xl mt-2">{formatarDias(esperaGeral?.tempoMinimoEsperaDias ?? null)}</span>
        </div>
        <div class="flex flex-col bg-amber-700 rounded-xl p-5 text-center shadow">
          <span class="text-white text-sm uppercase tracking-wide">Maior Espera</span>
          <span class="text-white font-bold text-3xl mt-2">{formatarDias(esperaGeral?.tempoMaximoEsperaDias ?? null)}</span>
        </div>
      </div>
      <p class="text-xs text-gray-400 text-center -mt-4 mb-6">
        Baseado em {esperaGeral?.totalAgendados ?? 0} solicitações já agendadas{esperaEspecialidadeId ? ' para a especialidade selecionada' : ''}.
      </p>

      {#if esperaPorEspecialidade.length === 0}
        <p class="text-center text-gray-400 py-8">Nenhuma solicitação agendada encontrada para os filtros selecionados.</p>
      {:else}
        <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
          <div>
            <h3 class="text-sm font-semibold text-gray-600 uppercase tracking-wide mb-3">Top 10 — Maior Espera Média</h3>
            <ChartBase type="bar" labels={esperaLabels} values={esperaValores} title="Tempo médio de espera (dias)" />
          </div>
          <div>
            <h3 class="text-sm font-semibold text-gray-600 uppercase tracking-wide mb-3">Detalhe por Especialidade</h3>
            <div class="overflow-x-auto max-h-96 overflow-y-auto">
              <table class="w-full text-sm">
                <thead class="sticky top-0 bg-white">
                  <tr class="bg-gray-50 text-left text-gray-600 uppercase text-xs">
                    <th class="p-2">Especialidade</th>
                    <th class="p-2 text-center">Agendados</th>
                    <th class="p-2 text-center">Média</th>
                  </tr>
                </thead>
                <tbody>
                  {#each esperaPorEspecialidade as e (e.especialidadeId)}
                    <tr class="border-b border-gray-100 hover:bg-gray-50">
                      <td class="p-2 text-gray-800">{e.especialidadeNome}</td>
                      <td class="p-2 text-center">{e.totalAgendados}</td>
                      <td class="p-2 text-center font-bold text-emerald-700">{formatarDias(e.tempoMedioEsperaDias)}</td>
                    </tr>
                  {/each}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      {/if}
    {/if}
  </section>

  <!-- ═══════════════════════════════════════════════════════════
       RANK DE PROFISSIONAL SOLICITANTE
  ════════════════════════════════════════════════════════════════ -->
  <section class="bg-white rounded-lg m-5 p-6 shadow">
    <div class="flex items-center gap-3 mb-1">
      <svg class="w-6 h-6 text-emerald-700" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
          d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
      </svg>
      <h2 class="text-2xl font-bold text-gray-800">Rank de Profissionais Solicitantes</h2>
      <a href="/relatorio/profissional" class="ml-auto text-sm text-emerald-700 hover:text-emerald-900 hover:underline whitespace-nowrap">
        Ver relatório completo →
      </a>
    </div>
    <p class="text-sm text-gray-500 mb-4">Identifica quais profissionais mais solicitam exames e quais especialidades predominam. Para filtrar por unidade, exportar em Excel ou ver o detalhe de cada solicitação, use o relatório completo.</p>

    <!-- Filtros -->
    <div class="flex flex-wrap gap-3 mb-5">
      <div class="flex flex-col">
        <label class="text-xs text-gray-500 mb-1">De</label>
        <input type="date" bind:value={rankInicio} class="border rounded px-2 py-1 text-sm" />
      </div>
      <div class="flex flex-col">
        <label class="text-xs text-gray-500 mb-1">Até</label>
        <input type="date" bind:value={rankFim} class="border rounded px-2 py-1 text-sm" />
      </div>
      <div class="flex items-end">
        <button
          onclick={buscarRankingProfissionais}
          disabled={rankingCarregando}
          class="px-4 py-1.5 bg-emerald-700 text-white rounded text-sm hover:bg-emerald-800 disabled:opacity-50 transition-colors">
          {rankingCarregando ? 'Carregando...' : 'Filtrar'}
        </button>
      </div>
    </div>

    {#if rankingProfissionais.length === 0 && !rankingCarregando}
      <p class="text-center text-gray-400 py-8">Nenhum dado encontrado para o período selecionado.</p>
    {:else}
      <!-- CAMADA 1 — Lista ranqueada -->
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div>
          <h3 class="text-sm font-semibold text-gray-600 uppercase tracking-wide mb-3">Lista</h3>
          <div class="space-y-2">
            {#each rankingProfissionais as prof, i (prof.id)}
              <button
                class="w-full text-left flex items-center gap-3 p-3 rounded-lg border transition-all
                  {profissionalSelecionado?.id === prof.id
                    ? 'border-emerald-500 bg-emerald-50 shadow-sm'
                    : 'border-gray-100 hover:border-emerald-300 hover:bg-gray-50'}"
                onclick={() => selecionarProfissional(prof)}>
                <!-- Posição -->
                <span class="text-xl w-8 text-center flex-shrink-0">
                  {i < 3 ? MEDALHAS[i] : `${i + 1}º`}
                </span>
                <!-- Dados -->
                <div class="flex-1 min-w-0">
                  <p class="font-semibold text-gray-800 truncate">{prof.nome}</p>
                  {#if prof.conselho && prof.numeroRegistro}
                    <p class="text-xs text-gray-400">{prof.conselho} {prof.numeroRegistro}</p>
                  {/if}
                </div>
                <!-- Total -->
                <div class="flex-shrink-0 text-right">
                  <span class="text-lg font-bold text-emerald-700">{prof.totalSolicitacoes}</span>
                  <p class="text-xs text-gray-400">solicitações</p>
                </div>
              </button>
            {/each}
          </div>
        </div>

        <!-- CAMADA 2 — Gráfico geral -->
        <div>
          <h3 class="text-sm font-semibold text-gray-600 uppercase tracking-wide mb-3">Gráfico Geral</h3>
          {#if rankValues.length > 0}
            <ChartBase type="bar" labels={rankLabels} values={rankValues} title="Solicitações por Profissional" />
          {/if}
        </div>
      </div>

      <!-- Especialidades do profissional selecionado -->
      {#if profissionalSelecionado}
        <div class="mt-6 pt-5 border-t border-gray-100">
          <h3 class="text-sm font-semibold text-gray-600 uppercase tracking-wide mb-4">
            Especialidades — <span class="text-emerald-700">{profissionalSelecionado.nome}</span>
          </h3>

          {#if especialidadesCarregando}
            <p class="text-sm text-gray-400">Carregando especialidades...</p>
          {:else if especialidadesProfissional.length === 0}
            <p class="text-sm text-gray-400">Nenhuma especialidade registrada para este profissional.</p>
          {:else}
            <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
              <!-- Mini lista de especialidades -->
              <div class="space-y-2">
                {#each especialidadesProfissional as esp, i (esp.especialidadeNome)}
                  <div class="flex items-center gap-2">
                    <span class="text-xs font-mono text-gray-400 w-5">{i + 1}.</span>
                    <div class="flex-1 bg-gray-100 rounded-full h-6 overflow-hidden">
                      <div
                        class="h-full bg-emerald-500 rounded-full flex items-center px-2 transition-all"
                        style="width: {Math.round((esp.total / especialidadesProfissional[0].total) * 100)}%">
                      </div>
                    </div>
                    <span class="text-xs text-gray-600 truncate max-w-[140px]">{esp.especialidadeNome}</span>
                    <span class="text-xs font-bold text-gray-700 w-6 text-right">{esp.total}</span>
                  </div>
                {/each}
              </div>
              <!-- Gráfico de especialidades -->
              <ChartBase type="doughnut" labels={espLabels} values={espValues}
                title="Especialidades de {profissionalSelecionado.nome}" />
            </div>
          {/if}
        </div>
      {:else}
        <p class="mt-4 text-xs text-gray-400 text-center">Clique em um profissional para ver o detalhamento por especialidade.</p>
      {/if}
    {/if}
  </section>
</Content>
