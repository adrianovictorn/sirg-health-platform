<script lang="ts">
  import { onMount } from "svelte";
  import { getApi } from '$lib/api';
  import jsPDF from 'jspdf';
  import autoTable from 'jspdf-autotable';
  import Menu from "$lib/Menu.svelte";
  import UserMenu from "$lib/UserMenu.svelte";

  type Grupo = {
    codigo: string
    nome: string
  }

  type RelatorioCard = {
    label: string
    grupo: string
    color: string
    hover: string
    icon: string
  }

  // --- Estado do Componente ---
  let dataSelecionada = $state(new Date().toISOString().split('T')[0]);
  let loadingType = $state<string | null>(null);
  let allSolicitacoes = $state<any[]>([]);
  let grupos = $state<Grupo[]>([])
  let isLoadingData = $state(true);



  

  const PALETA = [
    { color: "bg-emerald-600", hover:"hover:bg-emerald-700" },
    { color: "bg-sky-600", hover: "hover:bg-sky-700" },
    { color: "bg-indigo-600", hover: "hover:bg-indigo-700" },
    { color: "bg-fuchsia-600", hover: "hover:bg-fuchsia-700"}
  ] as const;

  const ICONES = [
    // Documento
    `<path stroke-linecap="round" stroke-linejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />`,
    // Lista
    `<path stroke-linecap="round" stroke-linejoin="round" d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5" />`,
    // Janela
    `<path stroke-linecap="round" stroke-linejoin="round" d="M9 4.5v15m6-15v15m-10.875 0h15.75c.621 0 1.125-.504 1.125-1.125V5.625c0-.621-.504-1.125-1.125-1.125H4.125C3.504 4.5 3 5.004 3 5.625v12.75c0 .621.504 1.125 1.125 1.125z" />`,
    // Círculo
    `<path stroke-linecap="round" stroke-linejoin="round" d="M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />`
  ] as const;


  function toCard(g: Grupo, index: number) : RelatorioCard {
    const i = index % 4;
    const cor = PALETA[i];
    const icon = ICONES[i];

    return {
      label: g.nome,
      grupo: g.codigo,
      color: cor.color,
      hover: cor.hover,
      icon
    };
  }

  let cards = $derived(grupos.map((g, i) => toCard(g,i)));

  function sanitizeLabel(label: string) {
    return (label ?? "")
      .trim()
      .replaceAll(/\s+/g, "_")
      .replaceAll(/[^a-zA-Z0-9_\-]/g, "")
      .toLowerCase();
  }

  async function downloadFile(url: string, filename: string) {
    const res = await getApi(url)
    if(!res.ok) throw new Error ("Falha ao baixar arquivo.");
    const blob = await res.blob();
    const objUrl = URL.createObjectURL(blob)

    const a = document.createElement("a");
    a.href = objUrl;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(objUrl);
  }
  
  // ====== Carregamentos ======
  async function carregarGrupoRelatorio() {
    try {
      const res = await getApi('grupo-relatorio/listar')
      const data = await res.json();
      grupos = Array.isArray(data) ? data : (data?.content ?? []);
      if(!res.ok){
        alert("Erro ao buscar dados de Grupo")
      }
    } catch (error) {
      throw new Error("Erro ao estabelecer dados de acesso com o servidor !")  
    }
  }
  
  async function carregarSolicitacoes() {
    try {
      const res = await getApi('solicitacoes?size=10000');
      if (!res.ok) throw new Error('Falha ao carregar dados do servidor.');
      const data = await res.json();
      allSolicitacoes = Array.isArray(data) ? data : (data?.content ?? []);
    } catch (e) {
      alert('Não foi possível carregar os dados das solicitações.');
      console.error(e);
    } finally {
      isLoadingData = false;
    }
    
  }
  
  // ====== Exportações ======
  async function gerarPlanilha(card: RelatorioCard) {
    if (!dataSelecionada) {
      alert("Por favor, selecione uma data.");
      return;
    }

    loadingType = card.label;

    try {
      const checkUrl =
        `exportar/verificar-dados?grupo=${encodeURIComponent(card.grupo)}` +
        `&data=${dataSelecionada}&label=${encodeURIComponent(card.label)}`;

      const response = await getApi(checkUrl);
      if (!response.ok) throw new Error("Falha ao verificar dados com o servidor.");

      const result = await response.json();
      if (!result?.dadosDisponiveis) {
        alert(
          `Nenhum dado encontrado para o relatório de ${card.label} na data ` +
            `${new Date(dataSelecionada + "T12:00:00").toLocaleDateString("pt-BR")}.`
        );
        return;
      }

      const safe = sanitizeLabel(card.label);
      const downloadUrl =
        `exportar/planilha?grupo=${encodeURIComponent(card.grupo)}` +
        `&data=${dataSelecionada}&label=${encodeURIComponent(card.label)}`;

      await downloadFile(downloadUrl, `planilha_${safe}_${dataSelecionada}.xlsx`);
    } catch (error) {
      console.error(`Erro ao gerar planilha de ${card.label}:`, error);
      alert("Ocorreu um erro de comunicação com o servidor. Tente novamente.");
    } finally {
      setTimeout(() => {
        loadingType = null;
      }, 700);
    }
  }

  async function gerarPlanilhaAguardando(card: RelatorioCard) {
    loadingType = `AGUARDANDO_${card.label}`;

    try {
      const checkUrl =
        `exportar/verificar-dados-aguardando?grupo=${encodeURIComponent(card.grupo)}` +
        `&label=${encodeURIComponent(card.label)}`;

      const response = await getApi(checkUrl);
      if (!response.ok) throw new Error("Falha ao verificar dados de pendências com o servidor.");

      const result = await response.json();
      if (!result?.dadosDisponiveis) {
        alert(`Nenhum dado pendente encontrado para o relatório de ${card.label}.`);
        return;
      }

      const safe = sanitizeLabel(card.label);
      const downloadUrl =
        `exportar/planilha-aguardando?grupo=${encodeURIComponent(card.grupo)}` +
        `&label=${encodeURIComponent(card.label)}`;

      await downloadFile(downloadUrl, `planilha_pendentes_${safe}.xlsx`);
    } catch (error) {
      console.error(`Erro ao gerar planilha de pendentes para ${card.label}:`, error);
      alert("Ocorreu um erro de comunicação com o servidor. Tente novamente.");
    } finally {
      setTimeout(() => {
        loadingType = null;
      }, 700);
    }
  }

  
 async function gerarPlanilhaAgendamentosDoDia() {
    try {
      const res = await getApi("relatorio/producao/excel");
      if (!res.ok) throw new Error("Falha ao baixar produção do dia.");

      const blob = await res.blob();
      const url = URL.createObjectURL(blob);

      const a = document.createElement("a");
      a.href = url;
      a.download = "relatorio_producao.xlsx";
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (error) {
      console.error(error);
      alert("Erro ao baixar relatório de produção.");
    }
  }
  
  

  onMount(() => {
    carregarSolicitacoes()
    carregarGrupoRelatorio()
  });
  
  function gerarPDFGeral() {
    if (isLoadingData || allSolicitacoes.length === 0) {
      alert('Não há dados carregados para gerar o PDF.');
      return;
    }
    loadingType = 'PDF_GERAL';
    
    const doc = new jsPDF();
    const tableColumn = ["ID", "Paciente", "CPF", "USF", "Data Malote", "Especialidades Pendentes"];
    const tableRows: any[][] = [];
    
    allSolicitacoes.forEach(sol => {
      const pendentes = sol.especialidades
              .filter((e: any) => e.status === 'AGUARDANDO')
              .map((e: any) => e.especialidadeSolicitada)
              .join(', ');
              
              if (pendentes) {
                const solicitacaoData = [
                  sol.id,
                  sol.nomePaciente,
                  sol.cpfPaciente,
                  sol.usfOrigem,
                  new Date(sol.dataMalote + 'T12:00:00').toLocaleDateString('pt-BR'),
                  pendentes
                ];
                tableRows.push(solicitacaoData);
              }
      });
      
      if(tableRows.length === 0){
          alert("Nenhuma solicitação com pendências encontrada para gerar o relatório.");
          loadingType = null;
          return;
      }

      autoTable(doc, {
          head: [tableColumn],
          body: tableRows,
          startY: 20,
          theme: 'striped',
          headStyles: { fillColor: [22, 160, 133] }
      });
      
      doc.text("Relatório Geral de Solicitações Pendentes", 14, 15);
      doc.save(`relatorio_geral_pendentes_${new Date().toISOString().split('T')[0]}.pdf`);

      setTimeout(() => { loadingType = null; }, 1000);
  }

</script>
<svelte:head>
    <title>Relatórios</title>
</svelte:head>

<div class="flex min-h-screen bg-gray-100">
    <Menu activePage="/exportar" />

  <div class="flex-1 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Gerar Relatórios</h1>
          <UserMenu/>
    </header>

    <main class="flex-1 p-6 overflow-auto">
      <div class="max-w-5xl mx-auto space-y-8">
        
        <section>
          <h2 class="text-xl font-bold text-gray-700 mb-3">Relatórios de Agendados</h2>
          <div class="bg-white p-4 rounded-lg shadow-md mb-6">
            <label for="data" class="block text-base font-semibold text-gray-800 mb-2">
              🗓️ Selecione a data para os relatórios de agendados:
            </label>
            <input
              type="date"
              id="data"
              bind:value={dataSelecionada}
              class="w-full md:w-auto border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500"
            />
          </div>
          <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-5 gap-5">
            {#each cards as relatorio}
              <button 
                onclick={() => gerarPlanilha(relatorio)} 
                disabled={loadingType === relatorio.label} 
                class="p-4 {relatorio.color} text-white rounded-lg shadow-lg {relatorio.hover} hover:scale-105 transform transition-all duration-200 flex flex-col items-center justify-center h-32 disabled:bg-gray-400 disabled:cursor-wait disabled:scale-100"
              >
                <svg xmlns="http://www.w3.org/2000/svg" class="h-10 w-10 mb-2" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                  {@html relatorio.icon}
                </svg>
                <span class="text-lg font-bold text-center">{relatorio.label}</span>
                {#if loadingType === relatorio.label}
                  <span class="text-xs mt-1 animate-pulse">Gerando...</span>
                {/if}
              </button>
            {/each}
          </div>
        </section>

        <section>
          <h2 class="text-xl font-bold text-gray-700 mb-3">Relatórios de Pendentes</h2>
          <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-5 gap-5">
            {#each cards as relatorio}
              <button 
                onclick={() => gerarPlanilhaAguardando(relatorio)} 
                disabled={loadingType === `AGUARDANDO_${relatorio.label}`} 
                class="p-4 bg-gray-500 text-white rounded-lg shadow-lg hover:bg-gray-600 hover:scale-105 transform transition-all duration-200 flex flex-col items-center justify-center h-32 disabled:bg-gray-400 disabled:cursor-wait disabled:scale-100"
              >
                <svg xmlns="http://www.w3.org/2000/svg" class="h-10 w-10 mb-2" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                  {@html relatorio.icon}
                </svg>
                <span class="text-lg font-bold text-center">{relatorio.label}</span>
                {#if loadingType === `AGUARDANDO_${relatorio.label}`}
                  <span class="text-xs mt-1 animate-pulse">Gerando...</span>
                {/if}
              </button>
            {/each}
          </div>
        </section>
        
        <section>
          <h2 class="text-xl font-bold text-gray-700 mb-3">Relatórios Gerais</h2>
          <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-5">
            <button onclick={() => gerarPDFGeral()} disabled={isLoadingData || loadingType === 'PDF_GERAL'} class="p-4 bg-blue-600 text-white rounded-lg shadow-lg hover:bg-blue-700 hover:scale-105 transform transition-all duration-200 flex flex-col items-center justify-center h-32 disabled:bg-gray-400 disabled:cursor-wait disabled:scale-100">
              <svg xmlns="http://www.w3.org/2000/svg" class="h-10 w-10 mb-2" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
              <span class="text-lg font-bold text-center">PDF Geral de Pendentes</span>
              {#if loadingType === 'PDF_GERAL'}<span class="text-xs mt-1 animate-pulse">Gerando...</span>{/if}
            </button>

            <button class="flex flex-col justify-center items-center bg-red-600 rounded-lg py-3 text-white" onclick={gerarPlanilhaAgendamentosDoDia}>
              <svg xmlns="http://www.w3.org/2000/svg" class="h-10 w-10 mb-2" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
              <span class="text-[1.1rem] font-bold text-center"> Excel Agendamentos Do Dia </span>
              {#if loadingType === 'PDF_GERAL'}<span class="text-xs mt-1 animate-pulse">Gerando...</span>{/if}
            </button>
          </div>
        </section>
      </div>
    </main>
    </div>
</div>
