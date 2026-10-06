<script lang="ts">
  import { onMount } from 'svelte';
  import { user } from '$lib/stores/auth.js';
  import Card from '$lib/Card.svelte';
  import Card2 from '$lib/Card2.svelte';
  import Card3 from '$lib/Card3.svelte';
  import { getApi } from '$lib/api.js';
  import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';
  import UserMenu from '$lib/UserMenu.svelte';
  import GrupoToggleButton from '$lib/GrupoToggleButton.svelte';
  import GrupoEspecialidadesPainel from '$lib/GrupoEspecialidadesPainel.svelte';
  import { formatarPeriodoCota, distanciaDeHoje } from '$lib/cotas.js';

  let resumo: {
    totalSolicitacoes: number;
    totalPendentes: number;
    totalAgendadas: number;
    totalConcluidas: number;
    totalUrgentes: number;
    totalGel: number;
    pendentesPorUnidade: Record<string, number>;
    pacientesPendentes: number;
    pacientesUrgentes: number;
    pacientesPendentesPorUnidade: Record<string, number>;
  } | null = null;

  let unidadeId: number | null = null;
  let unidadeNome = '';
  // Todas as cotas da unidade (+ do grupo de unidades, a que ela pertencer) —
  // filtradas e ordenadas abaixo para mostrar as mais próximas, mensais ou por
  // data específica. Somente-leitura; a gestão é do ADMIN.
  let cotas: any[] = [];
  // Grupos de relatório (usado para "abrir" uma cota de grupo e listar quais
  // especialidades a compõem — mesmo dado já usado em /admin/cotas).
  let grupos: any[] = [];
  let isLoading = true;
  let error = '';

  // Quais cotas de grupo estão com o painel de especialidades expandido —
  // mesmo padrão usado em /unidade/cotas e /admin/cotas.
  let gruposAbertos: Record<number, boolean> = {};
  function alternarGrupo(cotaId: number) {
    gruposAbertos = { ...gruposAbertos, [cotaId]: !gruposAbertos[cotaId] };
  }

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
        // A ausência de cotas não é erro: significa unidade sem limite configurado.
        const resCotas = await getApi(`cotas/unidade/${unidadeId}`);
        const daUnidade = resCotas.ok ? await resCotas.json() : [];

        // A unidade também é limitada pela cota do grupo a que pertence (pool
        // compartilhado) — mesmo padrão usado em /unidade/cotas.
        let doGrupo: any[] = [];
        const resUnidade = await getApi(`unidades/${unidadeId}`);
        if (resUnidade.ok) {
          const unidade = await resUnidade.json();
          if (unidade.grupoRelatorioId) {
            const resGrupo = await getApi(`cotas/grupo-unidades/${unidade.grupoRelatorioId}`);
            if (resGrupo.ok) doGrupo = await resGrupo.json();
          }
        }

        cotas = [...daUnidade, ...doGrupo];

        const resGrupos = await getApi('grupo-relatorio/listar');
        if (resGrupos.ok) grupos = await resGrupos.json();
      }
    } catch (e: unknown) {
      error = e instanceof Error ? e.message : String(e);
    } finally {
      isLoading = false;
    }
  });

  $: totalDeSolicitacoes = resumo?.totalSolicitacoes ?? 0;
  $: agendado = resumo?.totalAgendadas ?? 0;
  $: concluida = resumo?.totalConcluidas ?? 0;
  $: gel = resumo?.totalGel ?? 0;
  // Cards que abrem a Fila de Espera mostram PACIENTES (mesma contagem da fila),
  // para o numero bater com o total da lista aberta.
  $: pendentes = resumo?.pacientesPendentes ?? 0;
  $: urgencia = resumo?.pacientesUrgentes ?? 0;

  // As 5 cotas (de especialidade OU de grupo — mesma regra pras duas) com data
  // mais próxima de agora, sem filtrar por "ainda em aberto": se a unidade só
  // tiver cotas de meses passados, essas ainda devem aparecer aqui.
  $: proximasCotas = [...cotas]
    .sort((a, b) => distanciaDeHoje(a) - distanciaDeHoje(b))
    .slice(0, 5);
  $: cotasEsgotadas = proximasCotas.filter((c) => c.ativo && c.saldoDisponivel <= 0);
  $: pendentesDaMinhaUnidade = (unidadeId && resumo?.pacientesPendentesPorUnidade)
    ? (resumo.pacientesPendentesPorUnidade[String(unidadeId)] ?? pendentes)
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
        <div class="max-w-[1600px] mx-auto space-y-6">

          {#if !unidadeId}
            <div class="bg-amber-50 border-l-4 border-amber-400 rounded-r-lg p-4 text-sm text-amber-900" role="status">
              Sua conta não está vinculada a uma unidade, por isso os números abaixo aparecem zerados.
              Fale com o administrador do sistema para vincular sua conta a uma Unidade de Saúde.
            </div>
          {/if}

          <!-- Visão Geral -->
          <section>
            <h2 class="text-xs font-semibold text-gray-700 uppercase tracking-widest mb-3">Visão Geral</h2>
            <div class="grid grid-cols-2 lg:grid-cols-4 gap-4 rounded-lg">
              <Card title="Total de Solicitações" value={totalDeSolicitacoes} color="emerald-dark"/>
              <Card2 header="Pacientes" title="Pendentes" value={pendentes} href="/paciente/fila?status=AGUARDANDO" color="emerald-dark"/>
              <Card2 header="Solicitações" title="Agendadas" value={agendado} href="/paciente/agendados" color="emerald-dark"/>
              <Card2 header="Solicitações" title="Concluídas" value={concluida} href="/paciente/concluido" color="emerald-dark"/>
            </div>
          </section>

          <!-- Atenção Imediata -->
          <section>
            <h2 class="text-xs font-semibold text-gray-700 uppercase tracking-widest mb-3">Atenção Imediata</h2>
            <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <Card3 header="Alertas" title="Urgência / Emergência" value={urgencia} href="/paciente/fila?prioridade=URGENTE,EMERGENCIA" color="danger"/>
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
                  href="/paciente/fila?status=AGUARDANDO"
                  color="emerald"
                />
              </div>
            </section>
          {/if}

          <!-- Próximas cotas -->
          {#if proximasCotas.length > 0}
            <section>
              <div class="flex items-baseline justify-between mb-3">
                <h2 class="text-xs font-semibold text-gray-700 uppercase tracking-widest">Próximas Cotas</h2>
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
                {#each proximasCotas as c (c.id)}
                  <div class="px-4 py-3">
                    <div class="flex flex-wrap items-center gap-x-1.5 gap-y-1">
                      <span class="text-sm text-gray-800 truncate flex-1 min-w-0">
                        <!-- Cota por grupo de especialidades: mostra o nome do grupo.
                             Sem escopo nenhum e que e "Cota geral (todas)". -->
                        {c.grupoEspecialidadesNome ?? c.especialidadeNome ?? 'Cota geral (todas)'}
                        <span class="text-gray-400">· {formatarPeriodoCota(c)}</span>
                      </span>
                      <span class="text-sm font-semibold shrink-0"
                            class:text-red-600={c.saldoDisponivel <= 0}
                            class:text-gray-900={c.saldoDisponivel > 0}>
                        {c.quantidadeUtilizada}/{c.quantidadeTotal}
                      </span>
                      {#if c.grupoEspecialidadesId}
                        <GrupoToggleButton
                          grupo={grupos.find((g) => g.id === c.grupoEspecialidadesId)}
                          aberto={!!gruposAbertos[c.id]}
                          onToggle={() => alternarGrupo(c.id)}
                        />
                      {/if}
                    </div>
                    {#if c.grupoEspecialidadesId && gruposAbertos[c.id]}
                      <div class="mt-2">
                        <GrupoEspecialidadesPainel
                          grupo={grupos.find((g) => g.id === c.grupoEspecialidadesId)}
                          linkVerTudo={`/unidade/cotas?grupo=${c.grupoEspecialidadesId}`}
                        />
                      </div>
                    {/if}
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
