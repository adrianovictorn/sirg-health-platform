<script lang="ts">
  import { getApi } from "$lib/api";
  import Content from "$lib/Content.svelte";
  import { onMount } from "svelte";
  import Chart from "chart.js/auto";

  // --- Data do dia (LocalDate, no fuso local)
  const hoje = new Date().toLocaleDateString("en-CA");

  // --- Contadores do dia
  const paramsDia = new URLSearchParams();
  paramsDia.append("data", hoje);

  let pacientesAgendadosDoDia = $state<number | null>(null);
  let novosPacientesDoDia = $state<number | null>(null);
  let solicitacoesDeEspecialidadeDoDia = $state<number | null>(null);

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
  let chart: Chart | null = null;
  let canvasEl: HTMLCanvasElement;

  async function buscarTop10PorPeriodo() {
    const res = await getApi(`fechamento/total/por/especialidade/por/tempo?${paramsPeriodo.toString()}`);
    const page = await res.json(); // Spring Page
    top10 = (page?.content ?? []) as GrupoTotalDTO[];
  }

  function renderChart() {
    if (!canvasEl) return;

    // destrói caso re-render
    chart?.destroy();

    const labels = top10.map((x) => x.nome);
    const data = top10.map((x) => x.total);

    chart = new Chart(canvasEl, {
      type: "bar",
      data: {
        labels,
        datasets: [
          {
            label: "Agendamentos (Top 10)",
            data,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: true },
          tooltip: { enabled: true },
        },
        scales: {
          x: { ticks: { autoSkip: false } },
          y: { beginAtZero: true },
        },
      },
    });
  }

  onMount(async () => {
    // contadores
    await Promise.all([
      buscarTotalPacientesNovosDoDia(),
      buscarTotalPacientesAgendadosDoDia(),
      buscarTotalDeSolicitacaoEspecialidadeDoDia(),
    ]);

    // gráfico
    await buscarTop10PorPeriodo();
    renderChart();
  });
</script>

<Content titleH1="Indicadores" page="/indicadores">
  <section class="grid grid-cols-1 md:grid-cols-3 p-2 m-5 gap-4 bg-white rounded-lg">
    <h2 class="text-3xl font-bold text-gray-800 col-span-3 mt-4 ml-6">
      Contadores de Produção
    </h2>

    <div class="bg-amber-600 hover:bg-amber-800 rounded-xl border border-gray-400 text-center m-5 p-5 shadow-2xl transition-all duration-200 hover:-translate-y-2 hover:shadow-lg">
      <h3 class="text-white font-bold font-mono text-xl">Total de Agendados do Dia</h3>
      <p class="text-white font-bold text-2xl mt-4">{pacientesAgendadosDoDia ?? "-"}</p>
    </div>

    <div class="bg-emerald-600 hover:bg-emerald-800 rounded-xl border border-gray-400 text-center m-5 p-5 shadow-2xl transition-all duration-200 hover:-translate-y-2 hover:shadow-lg">
      <h3 class="text-white font-bold font-mono text-xl">Total de Pacientes do Dia</h3>
      <p class="text-white font-bold text-2xl mt-4">{novosPacientesDoDia ?? "-"}</p>
    </div>

    <div class="bg-sky-600 hover:bg-sky-800 rounded-xl border border-gray-400 text-center m-5 p-5 shadow-2xl transition-all duration-300 hover:-translate-y-2 hover:shadow-lg">
      <h3 class="text-white font-bold font-mono text-xl">Total de Especialidades Dia</h3>
      <p class="text-white font-bold text-2xl mt-4">{solicitacoesDeEspecialidadeDoDia ?? "-"}</p>
    </div>
  </section>

  <section class="bg-white rounded-lg m-5 p-6 shadow">
    <div class="flex items-center justify-between gap-4 flex-wrap">
      <h2 class="text-2xl font-bold text-gray-800">Top 10 Grupos no Período</h2>
      <p class="text-sm text-gray-600">
        {paramsPeriodo.get("inicio")} até {paramsPeriodo.get("intervalo")} (fim exclusivo)
      </p>
    </div>

    <div class="mt-4 h-[360px]">
      <canvas bind:this={canvasEl}></canvas>
    </div>
  </section>
</Content>
