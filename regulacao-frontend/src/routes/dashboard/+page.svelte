<script lang="ts">
  import { onMount } from 'svelte';
  import { user } from '$lib/stores/auth.js';
  import Card from '$lib/Card.svelte';
  import Card2 from '$lib/Card2.svelte';
  import { getApi } from '$lib/api.js';
  import Card3 from '$lib/Card3.svelte';
  import Menu from '$lib/Menu.svelte';
  import UserMenu from '$lib/UserMenu.svelte';

  let resumo: {
    totalSolicitacoes: number;
    totalPendentes: number;
    totalAgendadas: number;
    totalConcluidas: number;
    totalUrgentes: number;
    totalGel: number;
    pendentesPorUnidade: Record<string, number>;
  } | null = null;

  let unidades: { id: number; nome: string }[] = [];
  let isLoading = true;
  let error = '';

  onMount(async () => {
    try {
      const [resResumo, resUnidades] = await Promise.all([
        getApi('solicitacoes/resumo-dashboard'),
        getApi('unidades/ativas')
      ]);

      if (!resResumo.ok) {
        const errorData = await resResumo.text();
        throw new Error(`Falha ao carregar os dados: ${resResumo.status} ${errorData}`);
      }

      resumo = await resResumo.json();
      if (resUnidades.ok) unidades = await resUnidades.json();
    } catch (e: unknown) {
      error = e instanceof Error ? e.message : String(e);
    } finally {
      isLoading = false;
    }
  });

  $: totalDeSolicitacoes = resumo?.totalSolicitacoes ?? 0;
  $: pendentes = resumo?.totalPendentes ?? 0;
  $: agendado = resumo?.totalAgendadas ?? 0;
  $: concluida = resumo?.totalConcluidas ?? 0;
  $: urgencia = resumo?.totalUrgentes ?? 0;
  $: gel = resumo?.totalGel ?? 0;

  const pendentesDaUnidade = (id: number): number => {
    if (!resumo?.pendentesPorUnidade) return 0;
    return resumo.pendentesPorUnidade[String(id)] ?? 0;
  };
</script>

<svelte:head>
  <title>Dashboard</title>
</svelte:head>

{#if isLoading}
  <div class="flex items-center justify-center h-screen">
    <p class="text-xl text-gray-600">Carregando painel de controle...</p>
  </div>
{:else if error}
  <div class="flex items-center justify-center h-screen">
    <p class="text-xl text-red-500">Erro ao carregar os dados: {error}</p>
  </div>
{:else}
  <div class="flex min-h-screen bg-gray-200">
    <Menu activePage="/dashboard" />
    <div class="flex-1 flex flex-col">
      <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
        <h1 class="text-xl font-semibold">Painel de Controle</h1>
        {#if $user}
          <UserMenu />
        {:else}
          <div><a href="/login" class="hover:underline">Fazer Login</a></div>
        {/if}
      </header>

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
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Card3 header="Alertas" title="Urgência / Emergência" value={urgencia} href="/paciente/urgentes" color="danger"/>
              <Card3 header="Procedimentos Externos" title="GEL" value={gel} href="/paciente/gel" color="warning"/>
            </div>
          </section>

          <!-- Pendentes por Unidade -->
          <section class="bg-emerald-700/30 rounded-xl shadow-sm border border-gray-100 p-6">
            <h2 class="text-xs font-semibold text-gray-900 uppercase tracking-widest mb-5">Pendentes por Unidade</h2>

            {#if unidades.length === 0}
              <p class="text-sm text-gray-500">Nenhuma unidade cadastrada.</p>
            {:else}
              <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-4">
                {#each unidades as u}
                  <Card2
                    header={u.nome}
                    title="Pendentes"
                    value={pendentesDaUnidade(u.id)}
                    href={`/unidade/${u.id}`}
                    color="emerald"
                  />
                {/each}
              </div>
            {/if}
          </section>

        </div>
      </main>
    </div>
  </div>
{/if}
