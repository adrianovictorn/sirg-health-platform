<script>
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { getApi } from '$lib/api.js';
  import Content from '$lib/Content.svelte';
  import { toast } from 'svelte-sonner';
  import { formatarPeriodoCota, agruparCotasPorAgenda, periodoCobertoPorGrupo } from '$lib/cotas.js';
  import GrupoToggleButton from '$lib/GrupoToggleButton.svelte';
  import GrupoEspecialidadesPainel from '$lib/GrupoEspecialidadesPainel.svelte';

  let cotas = $state([]);
  let unidades = $state([]);
  let grupos = $state([]);
  let agendasPorId = $state({});
  let loading = $state(true);

  let filtroUnidade = $state('');

  // Quais cotas de grupo estão com o painel de especialidades expandido.
  let gruposAbertos = $state({});
  function alternarGrupo(cotaId) {
    gruposAbertos = { ...gruposAbertos, [cotaId]: !gruposAbertos[cotaId] };
  }

  // Quais agendas estão com o grupo de cotas (ocorrência x unidade) expandido.
  let agendasAbertas = $state({});
  function alternarAgenda(agendaId) {
    agendasAbertas = { ...agendasAbertas, [agendaId]: !agendasAbertas[agendaId] };
  }

  onMount(async () => {
    await Promise.all([carregarUnidades(), carregarGrupos(), carregarAgendas()]);
    await carregarCotas();
  });

  async function carregarCotas() {
    loading = true;
    try {
      const res = await getApi('cotas');
      cotas = await res.json();
    } catch {
      toast.error('Erro ao carregar cotas.');
    } finally {
      loading = false;
    }
  }

  async function carregarUnidades() {
    try {
      const res = await getApi('unidades/ativas');
      unidades = await res.json();
    } catch {
      /* ignora - combo fica vazio */
    }
  }

  async function carregarGrupos() {
    try {
      // O agrupamento de unidades reaproveita os Grupos de Relatorio (a unidade
      // aponta para o grupo via unidade.grupoRelatorioId).
      const res = await getApi('grupo-relatorio/listar');
      grupos = res.ok ? await res.json() : [];
    } catch {
      /* ignora - combo fica vazio */
    }
  }

  async function carregarAgendas() {
    try {
      // Só usada para o resumo (executante/profissional) das cotas de origem
      // AGENDA agrupadas abaixo — falha aqui degrada pro fallback "Agenda #id",
      // não quebra a tela.
      const res = await getApi('agendas');
      const lista = res.ok ? await res.json() : [];
      agendasPorId = Object.fromEntries(lista.map((a) => [a.id, a]));
    } catch {
      /* ignora - grupo cai no fallback "Agenda #id" */
    }
  }

  // Ao filtrar por unidade, mostra também as cotas do grupo a que ela pertence:
  // elas limitam essa unidade tanto quanto as cotas próprias.
  const grupoDaUnidadeFiltrada = $derived(
    filtroUnidade ? (unidades.find((u) => u.id === Number(filtroUnidade))?.grupoRelatorioId ?? null) : null
  );

  const cotasFiltradas = $derived(
    filtroUnidade
      ? cotas.filter(
          (c) =>
            c.unidadeId === Number(filtroUnidade) ||
            (grupoDaUnidadeFiltrada && c.grupoUnidadesId === grupoDaUnidadeFiltrada)
        )
      : cotas
  );

  // Uma cota de agenda recorrente materializa uma linha por ocorrência x
  // unidade solicitante — sem agrupar, uma agenda semanal de um mês vira
  // dezenas de linhas soltas na tabela. Aqui elas viram uma linha-resumo por
  // agenda, na posição em que a primeira delas apareceria na lista original;
  // cotas manuais continuam uma linha cada, sem nenhuma mudança de comportamento.
  const linhasExibidas = $derived(construirLinhas(cotasFiltradas));

  function construirLinhas(lista) {
    const { porAgenda } = agruparCotasPorAgenda(lista);
    const vistos = new Set();
    const linhas = [];
    for (const c of lista) {
      if (c.origem === 'AGENDA' && c.agendaId != null) {
        if (vistos.has(c.agendaId)) continue;
        vistos.add(c.agendaId);
        linhas.push({ tipo: 'agenda', agendaId: c.agendaId, cotas: porAgenda.get(c.agendaId) });
      } else {
        linhas.push({ tipo: 'manual', cota: c });
      }
    }
    return linhas;
  }

  function resumoGrupo(cotasDoGrupo) {
    const unidadesDistintas = new Set(cotasDoGrupo.map((c) => c.unidadeId)).size;
    return {
      quantidadeTotal: cotasDoGrupo.reduce((soma, c) => soma + c.quantidadeTotal, 0),
      quantidadeUtilizada: cotasDoGrupo.reduce((soma, c) => soma + c.quantidadeUtilizada, 0),
      saldoDisponivel: cotasDoGrupo.reduce((soma, c) => soma + c.saldoDisponivel, 0),
      unidadesDistintas,
      periodo: periodoCobertoPorGrupo(cotasDoGrupo)
    };
  }
</script>

{#snippet linhaCota(c)}
  <tr class="hover:bg-gray-50 transition-colors">
    <td class="px-4 py-3 font-medium text-gray-900">
      {#if c.grupoUnidadesId}
        <span class="px-2 py-0.5 rounded text-xs font-medium bg-emerald-100 text-emerald-700">Grupo</span>
        <span class="ml-2">{c.grupoUnidadesNome}</span>
      {:else}
        {c.unidadeNome}
      {/if}
    </td>
    <td class="px-4 py-3">
      {#if c.grupoEspecialidadesId}
        <span class="px-2 py-0.5 rounded text-xs font-medium bg-sky-100 text-sky-700">Grupo</span>
        <span class="ml-2">{c.grupoEspecialidadesNome}</span>
        <GrupoToggleButton
          grupo={grupos.find((g) => g.id === c.grupoEspecialidadesId)}
          aberto={!!gruposAbertos[c.id]}
          onToggle={() => alternarGrupo(c.id)}
        />
      {:else if c.especialidadeNome}
        {c.especialidadeNome}
      {:else}
        <span class="text-gray-400">Geral (todas)</span>
      {/if}
      {#if c.profissionalNome || c.horaInicial || c.localAgendamentoNome || (c.diasSemana && c.diasSemana.length > 0)}
        <div class="text-xs text-indigo-700 mt-0.5">
          {#if c.profissionalNome}{c.profissionalNome}{/if}
          {#if c.horaInicial}
            &nbsp;· {c.horaInicial.slice(0,5)}–{c.horaFinal ? c.horaFinal.slice(0,5) : '-'}
          {/if}
          {#if c.localAgendamentoNome}
            &nbsp;· {c.localAgendamentoNome}
          {/if}
          {#if c.diasSemana && c.diasSemana.length > 0}
            &nbsp;· {c.diasSemana.join(', ')}
          {/if}
        </div>
      {/if}
    </td>
    <td class="px-4 py-3">
      <span
        class="px-2 py-0.5 rounded text-xs font-medium {c.tipoPeriodo === 'DATA'
          ? 'bg-blue-100 text-blue-700'
          : 'bg-gray-200 text-gray-600'}"
      >
        {c.tipoPeriodo === 'DATA' ? 'Por Data' : 'Mensal'}
      </span>
    </td>
    <td class="px-4 py-3">{formatarPeriodoCota(c)}</td>
    <td class="px-4 py-3 text-right">{c.quantidadeTotal}</td>
    <td class="px-4 py-3 text-right">{c.quantidadeUtilizada}</td>
    <td class="px-4 py-3 text-right font-semibold {c.saldoDisponivel <= 0 ? 'text-red-600' : 'text-emerald-600'}">
      {c.saldoDisponivel}
    </td>
    <td class="px-4 py-3">
      <span
        class="px-2 py-0.5 rounded-full text-xs font-medium {c.ativo
          ? 'bg-emerald-100 text-emerald-700'
          : 'bg-red-100 text-red-700'}"
      >
        {c.ativo ? 'Ativa' : 'Inativa'}
      </span>
    </td>
    <td class="px-4 py-3">
      {#if c.origem === 'AGENDA'}
        <span class="px-2 py-0.5 rounded text-xs font-medium bg-violet-100 text-violet-700">Agenda</span>
      {:else}
        <span class="px-2 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-500">Manual</span>
      {/if}
    </td>
    <td class="px-4 py-3">
      {#if c.origem === 'AGENDA'}
        <button
          onclick={() => goto(`/liberacao-agenda/${c.agendaId}`)}
          class="px-3 py-1 rounded bg-violet-50 text-violet-700 hover:bg-violet-100 text-xs transition-colors"
        >
          Ver agenda
        </button>
      {:else}
        <button
          onclick={() => goto(`/admin/cotas/${c.id}/editar`)}
          class="px-3 py-1 rounded bg-blue-50 text-blue-700 hover:bg-blue-100 text-xs transition-colors"
        >
          Editar
        </button>
      {/if}
    </td>
  </tr>
{/snippet}

<Content titleH1="Cotas por Unidade" page="/admin/cotas">
  <main class="p-6 space-y-4">
    <div class="flex items-center justify-between gap-4">
      <div class="flex items-center gap-3">
        <select
          bind:value={filtroUnidade}
          class="bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500"
        >
          <option value="">Todas as unidades</option>
          {#each unidades as u (u.id)}
            <option value={u.id}>{u.nome}</option>
          {/each}
        </select>
      </div>
      <button
        onclick={() => goto('/admin/cotas/nova')}
        class="px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-medium transition-colors"
      >
        + Nova Cota
      </button>
    </div>

    {#if loading}
      <p class="text-gray-500 text-sm">Carregando...</p>
    {:else}
      <div class="overflow-x-auto rounded-xl border border-gray-200 bg-white">
        <table class="w-full text-sm text-gray-700">
          <thead class="bg-gray-50 text-gray-500 uppercase text-xs">
            <tr>
              <th class="px-4 py-3 text-left">Titular</th>
              <th class="px-4 py-3 text-left">Especialidade</th>
              <th class="px-4 py-3 text-left">Tipo</th>
              <th class="px-4 py-3 text-left">Período / Data</th>
              <th class="px-4 py-3 text-right">Total</th>
              <th class="px-4 py-3 text-right">Utilizado</th>
              <th class="px-4 py-3 text-right">Saldo</th>
              <th class="px-4 py-3 text-left">Status</th>
              <th class="px-4 py-3 text-left">Origem</th>
              <th class="px-4 py-3 text-left">Ações</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-200">
            {#each linhasExibidas as linha (linha.tipo === 'agenda' ? `agenda-${linha.agendaId}` : `cota-${linha.cota.id}`)}
              {#if linha.tipo === 'manual'}
                {@render linhaCota(linha.cota)}
              {:else}
                {@const agendaInfo = agendasPorId[linha.agendaId]}
                {@const resumo = resumoGrupo(linha.cotas)}
                <tr class="hover:bg-gray-50 transition-colors bg-violet-50/30">
                  <td class="px-4 py-3 font-medium text-gray-900" colspan="2">
                    <button
                      type="button"
                      onclick={() => alternarAgenda(linha.agendaId)}
                      class="inline-flex items-center gap-1.5 text-left"
                      aria-expanded={!!agendasAbertas[linha.agendaId]}
                    >
                      <svg
                        class="w-3.5 h-3.5 text-violet-500 transition-transform duration-150 {agendasAbertas[
                          linha.agendaId
                        ]
                          ? 'rotate-180'
                          : ''}"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke="currentColor"
                        stroke-width="2.5"
                      >
                        <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
                      </svg>
                      <span class="flex flex-col">
                        {#if agendaInfo}
                          <span>{agendaInfo.estabelecimentoExecutanteNome} — {agendaInfo.profissionalNome}</span>
                        {:else}
                          <span>Agenda #{linha.agendaId}</span>
                        {/if}
                        <span class="text-xs font-normal text-gray-500">
                          {linha.cotas.length} cota{linha.cotas.length === 1 ? '' : 's'} · {resumo.unidadesDistintas} unidade{resumo.unidadesDistintas ===
                          1
                            ? ''
                            : 's'}
                        </span>
                      </span>
                    </button>
                  </td>
                  <td class="px-4 py-3">
                    <span class="px-2 py-0.5 rounded text-xs font-medium bg-blue-100 text-blue-700">Por Data</span>
                  </td>
                  <td class="px-4 py-3">{resumo.periodo}</td>
                  <td class="px-4 py-3 text-right">{resumo.quantidadeTotal}</td>
                  <td class="px-4 py-3 text-right">{resumo.quantidadeUtilizada}</td>
                  <td
                    class="px-4 py-3 text-right font-semibold {resumo.saldoDisponivel <= 0
                      ? 'text-red-600'
                      : 'text-emerald-600'}"
                  >
                    {resumo.saldoDisponivel}
                  </td>
                  <td class="px-4 py-3">
                    <span
                      class="px-2 py-0.5 rounded-full text-xs font-medium {agendaInfo?.ativo ?? true
                        ? 'bg-emerald-100 text-emerald-700'
                        : 'bg-red-100 text-red-700'}"
                    >
                      {agendaInfo?.ativo ?? true ? 'Ativa' : 'Inativa'}
                    </span>
                  </td>
                  <td class="px-4 py-3">
                    <span class="px-2 py-0.5 rounded text-xs font-medium bg-violet-100 text-violet-700">Agenda</span>
                  </td>
                  <td class="px-4 py-3">
                    <button
                      onclick={() => goto(`/liberacao-agenda/${linha.agendaId}`)}
                      class="px-3 py-1 rounded bg-violet-50 text-violet-700 hover:bg-violet-100 text-xs transition-colors"
                    >
                      Ver agenda
                    </button>
                  </td>
                </tr>
                {#if agendasAbertas[linha.agendaId]}
                  {#each linha.cotas as c (c.id)}
                    {@render linhaCota(c)}
                  {/each}
                {/if}
              {/if}
              {#if linha.tipo === 'manual' && linha.cota.grupoEspecialidadesId && gruposAbertos[linha.cota.id]}
                <tr>
                  <td colspan="10" class="px-4 pb-4 pt-0">
                    <GrupoEspecialidadesPainel grupo={grupos.find((g) => g.id === linha.cota.grupoEspecialidadesId)} />
                  </td>
                </tr>
              {/if}
            {/each}
          </tbody>
        </table>
      </div>
    {/if}
  </main>
</Content>
