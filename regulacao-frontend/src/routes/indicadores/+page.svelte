<script lang="ts">
  import { getApi } from "$lib/api";
  import Content from "$lib/Content.svelte";
  import { onMount } from "svelte";
    import ChartBase from "$lib/components/ChartBase.svelte";

    type TipoRelatorio = "line"|  "bar"| "pie"| "doughnut"


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

  





  onMount(async () => {
    // contadores
    await Promise.all([
      buscarTotalPacientesNovosDoDia(),
      buscarTotalPacientesAgendadosDoDia(),
      buscarTotalDeSolicitacaoEspecialidadeDoDia(),
       buscarTop10PorPeriodo(),
       buscarTop10Pendentes()
    ]);

    // gráfico
   
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
</Content>
