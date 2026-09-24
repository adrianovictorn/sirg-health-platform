<script lang="ts">
  import { onMount } from 'svelte';
  import { user } from '$lib/stores/auth.js';
  import Card from '$lib/Card.svelte';
  import Card2 from '$lib/Card2.svelte';
  import Card3 from '$lib/Card3.svelte';
  import { getApi } from '$lib/api.js';
  import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';
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

  let unidadeId: number | null = null;
  let unidadeNome = '';
  // Cotas do mês corrente da própria unidade — o dado da unidade que mais muda
  // o que o usuário consegue fazer hoje. Somente-leitura; a gestão é do ADMIN.
  let cotasDoMes: any[] = [];
  let isLoading = true;
  let error = '';

  onMount(async () => {
    try {
      const [resResumo, resMe] = await Promise.all([
        getApi('solicitacoes/resumo-dashboard'),
        getApi('users/me')
      ]);

      if (!resResumo.ok) throw new Error(`Falha ao carregar dados: ${resResumo.status}`);
      if (!resMe.ok) throw new Error(`Falha ao carregar usuário: ${resMe.status}`);

      resumo = await resResumo.json();
      const me = await resMe.json();
      unidadeId = me.unidadeId ?? null;
      unidadeNome = me.unidadeNome ?? 'Minha Unidade';

      if (unidadeId) {
        const periodo = new Date().toISOString().slice(0, 7);
        const resCotas = await getApi(`cotas/unidade/${unidadeId}/periodo/${periodo}`);
        // A ausência de cotas não é erro: significa unidade sem limite configurado.
        if (resCotas.ok) cotasDoMes = await resCotas.json();
      }
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
  $: cotasEsgotadas = cotasDoMes.filter((c) => c.ativo && c.saldoDisponivel <= 0);
  $: pendentesDaMinhaUnidade = (unidadeId && resumo?.pendentesPorUnidade)
    ? (resumo.pendentesPorUnidade[String(unidadeId)] ?? pendentes)
    : pendentes;
</script>

<svelte:head>
  <title>Dashboard — {unidadeNome}</title>
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
    <RoleBasedMenu activePage="/dashboard/unidade" />
    <div class="flex-1 flex flex-col">
      <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
        <h1 class="text-xl font-semibold">Painel de Controle — {unidadeNome}</h1>
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

          <!-- Minha Unidade -->
          {#if unidadeId}
            <section class="bg-emerald-700/30 rounded-xl shadow-sm border border-gray-100 p-6">
              <h2 class="text-xs font-semibold text-gray-900 uppercase tracking-widest mb-5">Minha Unidade</h2>
              <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                <Card2
                  header={unidadeNome}
                  title="Pendentes"
                  value={pendentesDaMinhaUnidade}
                  href={`/unidade/${unidadeId}`}
                  color="emerald"
                />
              </div>
            </section>
          {/if}

          <!-- Cotas do mês -->
          {#if cotasDoMes.length > 0}
            <section>
              <div class="flex items-baseline justify-between mb-3">
                <h2 class="text-xs font-semibold text-gray-700 uppercase tracking-widest">Cotas do Mês</h2>
                <a href="/unidade/cotas" class="text-xs text-emerald-800 hover:underline">ver todas</a>
              </div>

              {#if cotasEsgotadas.length > 0}
                <div class="mb-3 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-800">
                  {cotasEsgotadas.length}
                  {cotasEsgotadas.length === 1 ? 'cota esgotada' : 'cotas esgotadas'} —
                  novos agendamentos serão bloqueados até haver saldo.
                </div>
              {/if}

              <div class="bg-white rounded-xl shadow-sm border border-gray-100 divide-y divide-gray-100">
                {#each cotasDoMes as c (c.id)}
                  <div class="flex items-center justify-between gap-4 px-4 py-3">
                    <span class="text-sm text-gray-800 truncate">
                      {c.especialidadeNome ?? 'Cota geral (todas)'}
                    </span>
                    <span class="text-sm font-semibold shrink-0"
                          class:text-red-600={c.saldoDisponivel <= 0}
                          class:text-gray-900={c.saldoDisponivel > 0}>
                      {c.quantidadeUtilizada}/{c.quantidadeTotal}
                    </span>
                  </div>
                {/each}
              </div>
            </section>
          {/if}

        </div>
      </main>
    </div>
  </div>
{/if}
