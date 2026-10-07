<script lang="ts">
  // Gráfico com várias séries sobre o mesmo eixo (ex.: novos x agendados x concluídos
  // por mês). ChartBase só aceita uma série; este componente existe para não mexer nele,
  // que é usado pelos gráficos já em produção.
  import Chart from "chart.js/auto";
  import { onDestroy, onMount } from "svelte";

  type Serie = { rotulo: string; valores: number[]; cor: string };

  let {
    tipo = "bar",
    rotulos = [],
    series = [],
    altura = 280,
    // Formata os valores do eixo e da dica (ex.: reais). Sem isso, número pt-BR.
    formatar = (valor: number) => Number(valor).toLocaleString("pt-BR")
  }: {
    tipo?: "bar" | "line";
    rotulos?: string[];
    series?: Serie[];
    altura?: number;
    formatar?: (valor: number) => string;
  } = $props();

  let canvas: HTMLCanvasElement;
  let chart: Chart | null = null;
  let tipoMontado = "";

  function conjuntos() {
    return series.map((serie) => ({
      label: serie.rotulo,
      data: [...serie.valores],
      backgroundColor: serie.cor,
      borderColor: serie.cor,
      borderWidth: tipo === "line" ? 2 : 0,
      tension: 0.3
    }));
  }

  function montar() {
    const ctx = canvas?.getContext("2d");
    if (!ctx) return;
    chart?.destroy();
    chart = new Chart(ctx, {
      type: tipo,
      data: { labels: [...rotulos], datasets: conjuntos() },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: "index", intersect: false },
        plugins: {
          legend: { display: true, position: "bottom" },
          tooltip: {
            callbacks: {
              label: (item) => `${item.dataset.label}: ${formatar(Number(item.parsed.y))}`
            }
          }
        },
        scales: {
          y: { beginAtZero: true, ticks: { callback: (valor) => formatar(Number(valor)) } }
        }
      }
    });
    tipoMontado = tipo;
  }

  onMount(montar);

  // Dados novos: atualiza o gráfico que já existe; só remonta se o tipo mudou.
  $effect(() => {
    const novosRotulos = [...rotulos];
    const novosConjuntos = conjuntos();
    if (!chart) return;
    if (tipo !== tipoMontado) {
      montar();
      return;
    }
    chart.data.labels = novosRotulos;
    chart.data.datasets = novosConjuntos;
    chart.update();
  });

  onDestroy(() => {
    chart?.destroy();
    chart = null;
  });
</script>

<div style={`height:${altura}px`}>
  <canvas bind:this={canvas}></canvas>
</div>
