<script lang="ts">
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { user, token } from '$lib/stores/auth.js';
  import Card from '$lib/Card.svelte';
  import Card2 from '$lib/Card2.svelte';
  import { getApi } from '$lib/api.js'; // Importa nosso helper que já envia o token!
  import Card3 from '$lib/Card3.svelte';
  import Menu from '$lib/Menu.svelte';
    import UserMenu from '$lib/UserMenu.svelte';


  // Variáveis de estado para controlar a UI
  let resumo: {
    totalSolicitacoes: number;
    totalPendentes: number;
    totalAgendadas: number;
    totalConcluidas: number;
    totalUrgentes: number;
    totalGel: number;
    pendentesPorUsf: Record<string, number>;
  } | null = null;
  let isLoading = true; // Começa como 'true' para mostrar a mensagem de carregando
  let error = '';

  // Função para fazer logout
  function logout() {
    token.set(null); 
    goto('/login');  
  }

  // Esta função será executada apenas no navegador, após o componente ser montado
  onMount(async () => {
    try {
      // Usa nosso helper 'getApi' que automaticamente anexa o token JWT
      const response = await getApi('solicitacoes/resumo-dashboard');

      if (!response.ok) {
        // Se o token for inválido ou o servidor der outro erro, captura a mensagem.
        const errorData = await response.text();
        throw new Error(`Falha ao carregar os dados: ${response.status} ${errorData}`);
      }
      
      resumo = await response.json();
    } catch (e) {
      error = e.message;
    } finally {
      // Ao final, independentemente de sucesso ou erro, para de carregar.
      isLoading = false;
    }
  });

  $: totalDeSolicitacoes = resumo?.totalSolicitacoes ?? 0;
  $: pendentes = resumo?.totalPendentes ?? 0;
  $: agendado = resumo?.totalAgendadas ?? 0;
  $: concluida = resumo?.totalConcluidas ?? 0;
  $: urgencia = resumo?.totalUrgentes ?? 0; 
  $: gel = resumo?.totalGel ?? 0;
  
  const filtarPendentesPorUnidade = (unidade) => {
    if (!resumo || !resumo.pendentesPorUsf) return 0;
    return resumo.pendentesPorUsf[unidade] ?? 0;
  };
</script>

<svelte:head>
    <title>Dashboard</title>
</svelte:head>

<!-- O HTML agora é condicional com base no estado de carregamento -->
{#if isLoading}
  <div class="flex items-center justify-center h-screen">
    <p class="text-xl text-gray-600">Carregando painel de controle...</p>
  </div>
{:else if error}
  <div class="flex items-center justify-center h-screen">
    <p class="text-xl text-red-500">Erro ao carregar os dados: {error}</p>
  </div>
{:else}
  <!-- O seu layout original é renderizado aqui somente após os dados serem carregados -->
  <div class="flex min-h-screen bg-gray-200">
    <!-- Sidebar -->

  <Menu activePage="/dashboard" />    <!-- Main Content -->
    <div class="flex-1 flex flex-col">
      <!-- Header com boas-vindas e botão de logout -->
      <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
        <h1 class="text-xl font-semibold">Painel de Controle</h1>
        {#if $user}
          <UserMenu/>
        {:else}
          <div>
            <a href="/login" class="hover:underline">Fazer Login</a>
          </div>
        {/if}
      </header>

      <!-- Dashboard Cards -->
      <main class="flex-1 p-6 overflow-auto">
        <div class="max-w-7xl mx-auto space-y-6">

          <!-- Visão Geral -->
          <section>
            <h2 class="text-xs font-semibold text-gray-700 uppercase tracking-widest mb-3">Visão Geral</h2>
            <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 rounded-lg">
              <Card title="Total de Solicitações" value={totalDeSolicitacoes} color="emerald-dark"/>
              <Card2 header="Solicitações" title="Pendentes" value={pendentes} href="/usf" color="emerald-dark"/>
              <Card2 header="Solicitações" title="Agendadas" value={agendado} href="/paciente/agendados" color="emerald-dark"/>
              <Card2 header="Solicitações" title="Concluídas" value={concluida} href="/paciente/concluido" color="emerald-dark"/>
            </div>
          </section>

          <!-- Atenção Imediata -->
          <section>
            <h2 class="text-xs font-semibold text-gray-700 uppercase tracking-widest mb-3">Atenção Imediata</h2>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4  ">
              <Card3 header="Alertas" title="Urgência / Emergência" value={urgencia} href="/paciente/urgentes" color="danger"/>
              <Card3  header="Procedimentos Externos" title="GEL" value={gel} href="/paciente/gel" color="warning"/>
            </div>
          </section>

          <!-- Pendentes por USF -->
          <section class="bg-emerald-700/30 rounded-xl shadow-sm border border-gray-100 p-6">
            <h2 class="text-xs font-semibold text-gray-900 uppercase tracking-widest mb-5">Pendentes por USF</h2>

            <div class="mb-5">
              <h3 class="text-sm font-semibold text-gray-700 mb-3 flex items-center gap-1.5">
                <span class="inline-block w-2 h-2 rounded-full bg-emerald-500"></span>
                Sede
              </h3>
              <div class="grid grid-cols-2 gap-4">
                <Card2 header="USF 01" title="Pendentes" value={filtarPendentesPorUnidade('USF01')} href="/usf/usf1" color="emerald"/>
                <Card2 header="USF 02" title="Pendentes" value={filtarPendentesPorUnidade('USF02')} href="/usf/usf2" color="emerald"/>
              </div>
            </div>

            <div>
              <h3 class="text-sm font-semibold text-gray-700 mb-3 flex items-center gap-1.5">
                <span class="inline-block w-2 h-2 rounded-full bg-emerald-400"></span>
                Zona Rural
              </h3>
              <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
                <Card2 header="USF 03" title="Pendentes" value={filtarPendentesPorUnidade('USF03')} href="/usf/usf3" color="emerald"/>
                <Card2 header="USF 04" title="Pendentes" value={filtarPendentesPorUnidade('USF04')} href="/usf/usf4" color="emerald"/>
                <Card2 header="USF 05" title="Pendentes" value={filtarPendentesPorUnidade('USF05')} href="/usf/usf5" color="emerald"/>
                <Card2 header="USF 06" title="Pendentes" value={filtarPendentesPorUnidade('USF06')} href="/usf/usf6" color="emerald"/>
              </div>
            </div>
          </section>

        </div>
      </main>
    </div>
  </div>
{/if}
