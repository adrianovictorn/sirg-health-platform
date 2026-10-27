<script lang="ts">
  import { onMount } from "svelte";
  import { getApi } from '$lib/api';
  import jsPDF from 'jspdf';
  import autoTable from 'jspdf-autotable';
  import RoleBasedMenu from "$lib/RoleBasedMenu.svelte";
  import UserMenu from "$lib/UserMenu.svelte";
  import { user } from '$lib/stores/auth.js';

  type Opcao = { id: number; nome: string };

  type Grupo = {
    codigo: string
    nome: string
    ativo: boolean
  }

  let loadingGel = $state(false);

  type RelatorioCard = {
    label: string
    grupo: string
    color: string
    hover: string
    icon: string
  }

  // "Hoje" no fuso do municipio: toISOString() daria o dia seguinte entre 21h e 0h.
  function hojeNaBahia() {
    return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bahia' }).format(new Date());
  }

  const PERIODO_MAXIMO_DIAS = 31;
  const SEM_EXECUTANTE = 'SEM';

  // --- Estado do Componente ---
  let dataSelecionada = $state(hojeNaBahia());

  // Filtros da planilha de agendados (todos opcionais, exceto a data/periodo).
  let modoData = $state<'dia' | 'periodo'>('dia');
  let dataInicio = $state(hojeNaBahia());
  let dataFim = $state(hojeNaBahia());
  let unidadeSolicitanteId = $state('');
  let unidadeExecutanteId = $state('');
  let profissionalFiltro = $state(''); // '' = todos, SEM_EXECUTANTE ou o id
  let unidades = $state<Opcao[]>([]);
  let executantes = $state<Opcao[]>([]);
  let profissionais = $state<Opcao[]>([]);
  // ADMIN_UNIDADE so ve a propria unidade: o servidor ja forca a lotacao.
  let restritoAUnidade = $derived($user?.role === 'ADMIN_UNIDADE');
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
  
  // Erro com mensagem do servidor (ex.: 403 de escopo por unidade): mostra a mensagem dele.
  async function avisarErroDoServidor(res: Response) {
    const corpo = await res.json().catch(() => null);
    if (typeof corpo?.message !== 'string' || !corpo.message) return false;
    alert(corpo.message);
    return true;
  }

  // ====== Carregamentos ======
  async function carregarGrupoRelatorio() {
    try {
      const res = await getApi('grupo-relatorio/listar')
      const data = await res.json();
      let lista = Array.isArray(data) ? data : (data?.content ?? []);
      
      grupos = lista.filter((g) => g.ativo === true)
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
  
  async function carregarLista(url: string): Promise<Opcao[]> {
    try {
      const res = await getApi(url);
      if (!res.ok) return [];
      const data = await res.json();
      const lista = Array.isArray(data) ? data : (data?.content ?? []);
      return lista.map((x: any) => ({ id: x.id, nome: x.nome }));
    } catch (e) {
      console.warn(`Não foi possível carregar ${url}`, e);
      return [];
    }
  }

  // Combos dos filtros: falha em um deles nao impede gerar a planilha sem ele.
  async function carregarFiltros() {
    const [u, x, p] = await Promise.all([
      carregarLista('unidades/ativas'),
      carregarLista('unidades/executantes'),
      // Sem ativo=true: profissional inativado ainda tem agendamentos no historico.
      carregarLista('profissionais/buscar?page=0&size=1000'),
    ]);
    unidades = u;
    executantes = x;
    profissionais = p;
  }

  // ====== Filtros da planilha de agendados ======
  function erroDoFiltro(): string | null {
    if (modoData === 'dia') {
      return dataSelecionada ? null : 'Por favor, selecione uma data.';
    }
    if (!dataInicio || !dataFim) return 'Informe o início e o fim do período.';
    if (dataFim < dataInicio) return 'O fim do período não pode ser anterior ao início.';
    const dias = (Date.parse(dataFim) - Date.parse(dataInicio)) / 86_400_000;
    if (dias >= PERIODO_MAXIMO_DIAS) {
      return `O período do relatório é de no máximo ${PERIODO_MAXIMO_DIAS} dias.`;
    }
    return null;
  }

  // Mesmos parametros na verificacao e no download: os dois tem de concordar.
  function paramsDoFiltro(card: RelatorioCard) {
    const p = new URLSearchParams({ grupo: card.grupo, label: card.label });
    if (modoData === 'dia') {
      p.set('data', dataSelecionada);
    } else {
      p.set('dataInicio', dataInicio);
      p.set('dataFim', dataFim);
    }
    if (unidadeSolicitanteId && !restritoAUnidade) p.set('unidadeId', unidadeSolicitanteId);
    if (unidadeExecutanteId) p.set('unidadeExecutanteId', unidadeExecutanteId);
    if (profissionalFiltro === SEM_EXECUTANTE) p.set('semExecutante', 'true');
    else if (profissionalFiltro) p.set('profissionalExecutanteId', profissionalFiltro);
    return p.toString();
  }

  function dataBR(iso: string) {
    return new Date(iso + 'T12:00:00').toLocaleDateString('pt-BR');
  }

  function descricaoDoPeriodo() {
    return modoData === 'dia'
      ? `na data ${dataBR(dataSelecionada)}`
      : `no período de ${dataBR(dataInicio)} a ${dataBR(dataFim)}`;
  }

  function sufixoDoArquivo() {
    return modoData === 'dia' ? dataSelecionada : `${dataInicio}_a_${dataFim}`;
  }

  // ====== Exportações ======
  async function gerarPlanilha(card: RelatorioCard) {
    const erro = erroDoFiltro();
    if (erro) {
      alert(erro);
      return;
    }

    loadingType = card.label;

    try {
      const params = paramsDoFiltro(card);
      const checkUrl = `exportar/verificar-dados?${params}`;

      const response = await getApi(checkUrl);
      if (!response.ok) {
        if (await avisarErroDoServidor(response)) return;
        throw new Error("Falha ao verificar dados com o servidor.");
      }

      const result = await response.json();
      if (!result?.dadosDisponiveis) {
        alert(`Nenhum dado encontrado para o relatório de ${card.label} ${descricaoDoPeriodo()} com os filtros escolhidos.`);
        return;
      }

      const safe = sanitizeLabel(card.label);
      const downloadUrl = `exportar/planilha?${params}`;

      await downloadFile(downloadUrl, `planilha_${safe}_${sufixoDoArquivo()}.xlsx`);
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
      if (!response.ok) {
        if (await avisarErroDoServidor(response)) return;
        throw new Error("Falha ao verificar dados de pendências com o servidor.");
      }

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
      if (!res.ok) {
        if (await avisarErroDoServidor(res)) return;
        throw new Error("Falha ao baixar produção do dia.");
      }

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
  
  

  async function gerarPlanilhaGel() {
    loadingGel = true;
    try {
      const res = await getApi('exportar/planilha-gel');
      if (!res.ok) {
        if (await avisarErroDoServidor(res)) return;
        throw new Error('Falha ao gerar planilha GEL.');
      }
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `planilha_gel_${new Date().toISOString().split('T')[0]}.xlsx`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch (error) {
      console.error(error);
      alert('Erro ao gerar planilha GEL. Tente novamente.');
    } finally {
      setTimeout(() => { loadingGel = false; }, 700);
    }
  }

  onMount(() => {
    carregarSolicitacoes()
    carregarGrupoRelatorio()
    carregarFiltros()
  });
  
  function gerarPDFGeral() {
    if (isLoadingData || allSolicitacoes.length === 0) {
      alert('Não há dados carregados para gerar o PDF.');
      return;
    }
    loadingType = 'PDF_GERAL';
    
    const doc = new jsPDF();
    const tableColumn = ["ID", "Paciente", "CPF", "USF", "Recebimento do pedido", "Especialidades Pendentes"];
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
    <RoleBasedMenu activePage="/relatorio" />

  <div class="flex-1 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Gerar Relatórios</h1>
          <UserMenu/>
    </header>

    <main class="flex-1 p-6 overflow-auto">
      <div class="max-w-5xl mx-auto space-y-8">
        
        <section>
          <h2 class="text-xl font-bold text-gray-700 mb-3">Relatórios de Agendados</h2>
          <div class="bg-white p-4 md:p-6 rounded-lg shadow mb-6 space-y-4">
            <div class="border-b pb-2">
              <h3 class="text-base font-semibold text-gray-800">Filtros da planilha</h3>
              <p class="text-sm text-gray-500">Valem para todos os relatórios de agendados abaixo.</p>
            </div>

            <fieldset class="flex flex-col gap-4 lg:flex-row lg:items-end lg:gap-6">
              <legend class="sr-only">Data agendada</legend>
              <div class="flex flex-wrap gap-2">
                <label
                  class="inline-flex items-center gap-2 min-h-10 px-3 py-2 rounded-md border border-gray-300 text-sm text-gray-700 cursor-pointer hover:bg-gray-50 has-[:checked]:border-emerald-600 has-[:checked]:bg-emerald-50 has-[:checked]:text-emerald-800 has-[:checked]:font-medium"
                >
                  <input
                    type="radio"
                    name="modoData"
                    value="dia"
                    bind:group={modoData}
                    class="text-emerald-600 focus:ring-emerald-500"
                  />
                  Um dia
                </label>
                <label
                  class="inline-flex items-center gap-2 min-h-10 px-3 py-2 rounded-md border border-gray-300 text-sm text-gray-700 cursor-pointer hover:bg-gray-50 has-[:checked]:border-emerald-600 has-[:checked]:bg-emerald-50 has-[:checked]:text-emerald-800 has-[:checked]:font-medium"
                >
                  <input
                    type="radio"
                    name="modoData"
                    value="periodo"
                    bind:group={modoData}
                    class="text-emerald-600 focus:ring-emerald-500"
                  />
                  Período (até {PERIODO_MAXIMO_DIAS} dias)
                </label>
              </div>

              {#if modoData === 'dia'}
                <div class="w-full sm:w-48">
                  <label for="data" class="block text-sm font-medium text-gray-700 mb-1">Data agendada</label>
                  <input
                    type="date"
                    id="data"
                    bind:value={dataSelecionada}
                    class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500"
                  />
                </div>
              {:else}
                <div class="grid grid-cols-1 min-[400px]:grid-cols-2 gap-4 sm:flex sm:items-end">
                  <div class="sm:w-48">
                    <label for="data-inicio" class="block text-sm font-medium text-gray-700 mb-1">De</label>
                    <input
                      type="date"
                      id="data-inicio"
                      bind:value={dataInicio}
                      class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500"
                    />
                  </div>
                  <div class="sm:w-48">
                    <label for="data-fim" class="block text-sm font-medium text-gray-700 mb-1">Até</label>
                    <input
                      type="date"
                      id="data-fim"
                      bind:value={dataFim}
                      class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500"
                    />
                  </div>
                </div>
              {/if}
            </fieldset>

            <div class="grid grid-cols-1 gap-4 {restritoAUnidade ? 'md:grid-cols-2' : 'md:grid-cols-3'}">
              {#if !restritoAUnidade}
                <div>
                  <label for="filtro-unidade-solicitante" class="block text-sm font-medium text-gray-700 mb-1">
                    Unidade solicitante
                  </label>
                  <select
                    id="filtro-unidade-solicitante"
                    bind:value={unidadeSolicitanteId}
                    class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500"
                  >
                    <option value="">Todas</option>
                    {#each unidades as u (u.id)}
                      <option value={String(u.id)}>{u.nome}</option>
                    {/each}
                  </select>
                </div>
              {/if}
              <div>
                <label for="filtro-unidade-executante" class="block text-sm font-medium text-gray-700 mb-1">
                  Unidade executante
                </label>
                <select
                  id="filtro-unidade-executante"
                  bind:value={unidadeExecutanteId}
                  class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500"
                >
                  <option value="">Todas</option>
                  {#each executantes as u (u.id)}
                    <option value={String(u.id)}>{u.nome}</option>
                  {/each}
                </select>
              </div>
              <div>
                <label for="filtro-profissional" class="block text-sm font-medium text-gray-700 mb-1">
                  Profissional executante
                </label>
                <select
                  id="filtro-profissional"
                  bind:value={profissionalFiltro}
                  class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500"
                >
                  <option value="">Todos</option>
                  <option value={SEM_EXECUTANTE}>Sem executante</option>
                  {#each profissionais as p (p.id)}
                    <option value={String(p.id)}>{p.nome}</option>
                  {/each}
                </select>
              </div>
            </div>
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
          <h2 class="text-xl font-bold text-gray-700 mb-3">Relatório GEL</h2>
          <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-5">
            <button
              onclick={gerarPlanilhaGel}
              disabled={loadingGel}
              class="p-4 bg-teal-600 text-white rounded-lg shadow-lg hover:bg-teal-700 hover:scale-105 transform transition-all duration-200 flex flex-col items-center justify-center h-32 disabled:bg-gray-400 disabled:cursor-wait disabled:scale-100"
            >
              <svg xmlns="http://www.w3.org/2000/svg" class="h-10 w-10 mb-2" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M9 17v-2m3 2v-4m3 4v-6m2 10H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
              <span class="text-lg font-bold text-center">Excel Procedimentos GEL</span>
              {#if loadingGel}
                <span class="text-xs mt-1 animate-pulse">Gerando...</span>
              {/if}
            </button>
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
