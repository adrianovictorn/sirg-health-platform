<script lang="ts">
  import { onMount } from 'svelte';
  import { page } from '$app/stores';
  import { goto } from '$app/navigation';
  import { user } from '$lib/stores/auth.js';
  import { getApi } from '$lib/api.js';
  import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';
  import UserMenu from '$lib/UserMenu.svelte';
  import GrupoToggleButton from '$lib/GrupoToggleButton.svelte';
  import GrupoEspecialidadesPainel from '$lib/GrupoEspecialidadesPainel.svelte';
  import { formatarPeriodoCota, chaveOrdenacaoCota } from '$lib/cotas.js';

  // Consulta somente-leitura das cotas da própria unidade de lotação.
  // A definição/alteração de cotas continua em /admin/cotas, exclusiva do ADMIN
  // global — aqui o usuário da unidade apenas acompanha o consumo que o limita.
  //
  // O unidadeId nunca vem da URL: é lido de /users/me. Mesmo assim o backend
  // revalida o acesso em /cotas/unidade/{id}, então trocar o id numa chamada
  // direta à API não expõe dados de outra unidade.

  type Cota = {
    id: number;
    unidadeId: number | null;
    unidadeNome: string | null;
    grupoUnidadesId: number | null;
    grupoUnidadesNome: string | null;
    grupoEspecialidadesId: number | null;
    grupoEspecialidadesNome: string | null;
    especialidadeId: number | null;
    especialidadeNome: string | null;
    tipoPeriodo: 'MENSAL' | 'DATA';
    periodo: string | null;
    dataEspecifica: string | null;
    quantidadeTotal: number;
    quantidadeUtilizada: number;
    saldoDisponivel: number;
    ativo: boolean;
  };

  type Grupo = { id: number; nome: string; especialidades: { id: number; nome: string }[] };

  let cotas: Cota[] = [];
  // Grupos de relatório — usado para "abrir" uma cota de grupo e listar as
  // especialidades que a compõem (mesmo dado já usado em /admin/cotas).
  let grupos: Grupo[] = [];
  let unidadeNome = '';
  let grupoNome: string | null = null;
  let isLoading = true;
  let error = '';

  // Quais cotas de grupo estão com o painel de especialidades expandido —
  // por id de cota, não de grupo, já que a mesma cota de grupo só aparece
  // uma vez na tabela. Fica aqui (não dentro de um componente) porque o
  // painel precisa virar uma linha de tabela à parte (colspan), fora da
  // célula "Especialidade / Exame" onde fica o botão.
  let gruposAbertos: Record<number, boolean> = {};
  function alternarGrupo(cotaId: number) {
    gruposAbertos = { ...gruposAbertos, [cotaId]: !gruposAbertos[cotaId] };
  }

  type Ordenacao = 'proximidade' | 'limite' | 'saldo';
  let ordenacao: Ordenacao = 'proximidade';
  let paginaAtual = 1;
  const itensPorPagina = 20;

  onMount(async () => {
    try {
      const resMe = await getApi('users/me');
      if (!resMe.ok) throw new Error(`Falha ao carregar usuário: ${resMe.status}`);
      const me = await resMe.json();

      unidadeNome = me.unidadeNome ?? 'Minha Unidade';

      if (!me.unidadeId) {
        error = 'Seu usuário não possui unidade de lotação vinculada. Solicite o vínculo ao administrador.';
        return;
      }

      const resCotas = await getApi(`cotas/unidade/${me.unidadeId}`);
      if (!resCotas.ok) throw new Error(`Falha ao carregar cotas: ${resCotas.status}`);
      const daUnidade: Cota[] = await resCotas.json();

      // A unidade também é limitada pela cota do grupo a que pertence (pool
      // compartilhado), então ela precisa aparecer aqui — do contrário o usuário
      // veria saldo na própria cota sem entender por que o agendamento é barrado.
      const resUnidade = await getApi(`unidades/${me.unidadeId}`);
      let doGrupo: Cota[] = [];
      if (resUnidade.ok) {
        const unidade = await resUnidade.json();
        if (unidade.grupoRelatorioId) {
          grupoNome = unidade.grupoRelatorioNome ?? null;
          const resGrupo = await getApi(`cotas/grupo-unidades/${unidade.grupoRelatorioId}`);
          if (resGrupo.ok) doGrupo = await resGrupo.json();
        }
      }

      cotas = [...daUnidade, ...doGrupo];

      const resGrupos = await getApi('grupo-relatorio/listar');
      if (resGrupos.ok) grupos = await resGrupos.json();
    } catch (e: unknown) {
      error = e instanceof Error ? e.message : String(e);
    } finally {
      isLoading = false;
    }
  });

  function percentual(c: Cota) {
    if (!c.quantidadeTotal) return 100;
    return Math.min(100, Math.round((c.quantidadeUtilizada / c.quantidadeTotal) * 100));
  }

  function corDaBarra(c: Cota) {
    const p = percentual(c);
    if (p >= 100) return 'bg-red-500';
    if (p >= 80) return 'bg-amber-500';
    return 'bg-emerald-600';
  }

  function nomeDaCota(c: Cota) {
    return c.especialidadeNome ?? c.grupoEspecialidadesNome ?? '';
  }

  // Veio de "Ver lista completa" no card de Próximas Cotas do Dashboard: filtra
  // a listagem para o grupo de especialidades clicado, em vez de criar uma tela
  // nova só para isso.
  $: grupoFiltroId = $page.url.searchParams.has('grupo')
    ? Number($page.url.searchParams.get('grupo'))
    : null;
  $: grupoFiltroNome = grupoFiltroId
    ? (grupos.find((g) => g.id === grupoFiltroId)?.nome
        ?? cotas.find((c) => c.grupoEspecialidadesId === grupoFiltroId)?.grupoEspecialidadesNome
        ?? `#${grupoFiltroId}`)
    : null;

  function limparFiltroGrupo() {
    paginaAtual = 1;
    goto('/unidade/cotas');
  }

  $: cotasFiltradas = grupoFiltroId
    ? cotas.filter((c) => c.grupoEspecialidadesId === grupoFiltroId)
    : cotas;

  $: cotasOrdenadas = [...cotasFiltradas].sort((a, b) => {
    if (ordenacao === 'limite') {
      if (b.quantidadeTotal !== a.quantidadeTotal) return b.quantidadeTotal - a.quantidadeTotal;
    } else if (ordenacao === 'saldo') {
      if (b.saldoDisponivel !== a.saldoDisponivel) return b.saldoDisponivel - a.saldoDisponivel;
    } else {
      const ka = chaveOrdenacaoCota(a), kb = chaveOrdenacaoCota(b);
      if (ka !== kb) return ka.localeCompare(kb);
    }
    return nomeDaCota(a).localeCompare(nomeDaCota(b));
  });

  $: totalPaginas = Math.max(1, Math.ceil(cotasOrdenadas.length / itensPorPagina));
  // Correção passiva de limite (ex.: filtro de grupo reduziu o total e a
  // página atual deixou de existir) — nunca é o que faz os dados aparecerem;
  // trocar a ordenação usa `on:change` abaixo pra voltar à página 1.
  $: if (paginaAtual > totalPaginas) paginaAtual = totalPaginas;
  $: cotasPaginadas = cotasOrdenadas.slice(
    (paginaAtual - 1) * itensPorPagina,
    paginaAtual * itensPorPagina
  );

  function paginaAnterior() {
    if (paginaAtual > 1) paginaAtual -= 1;
  }

  function proximaPagina() {
    if (paginaAtual < totalPaginas) paginaAtual += 1;
  }
</script>

<svelte:head>
  <title>Cotas — {unidadeNome}</title>
</svelte:head>

<div class="flex min-h-screen bg-gray-200">
  <RoleBasedMenu activePage="/unidade/cotas" />
  <div class="flex-1 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Cotas — {unidadeNome}</h1>
      {#if $user}
        <UserMenu />
      {:else}
        <div><a href="/login" class="hover:underline">Fazer Login</a></div>
      {/if}
    </header>

    <main class="flex-1 p-6 overflow-auto">
      <div class="max-w-[1600px] mx-auto space-y-6">

        {#if isLoading}
          <p class="text-gray-600">Carregando cotas...</p>
        {:else if error}
          <div class="bg-red-50 border border-red-200 text-red-700 rounded-lg p-4">{error}</div>
        {:else if cotas.length === 0}
          <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-8 text-center">
            <p class="text-gray-700 font-medium">Nenhuma cota cadastrada para esta unidade.</p>
            <p class="text-sm text-gray-500 mt-1">
              Sem cota configurada não há limite de agendamentos — os cadastros seguem normalmente.
            </p>
          </div>
        {:else}
          {#if grupoNome}
            <div class="bg-emerald-50 border border-emerald-200 text-emerald-900 rounded-lg p-4 text-sm">
              Esta unidade pertence ao grupo <strong>{grupoNome}</strong>. As cotas do grupo são um saldo
              compartilhado entre as unidades membros e também limitam os agendamentos desta unidade.
            </div>
          {/if}

          <div class="flex flex-wrap items-center justify-between gap-3">
            <div>
              {#if grupoFiltroId}
                <span class="inline-flex items-center gap-2 rounded-full bg-sky-100 text-sky-800 pl-3 pr-2 py-1 text-xs font-medium">
                  Filtrando por grupo: {grupoFiltroNome}
                  <button
                    type="button"
                    on:click={limparFiltroGrupo}
                    class="text-sky-600 hover:text-sky-900 rounded-full hover:bg-sky-200 w-4 h-4 flex items-center justify-center leading-none"
                    aria-label="Remover filtro de grupo"
                  >✕</button>
                </span>
              {/if}
            </div>
            <label class="flex items-center gap-2 text-sm text-gray-600">
              Ordenar por:
              <select
                bind:value={ordenacao}
                on:change={() => (paginaAtual = 1)}
                class="border border-gray-300 rounded-lg px-2 py-1.5 text-sm text-gray-800 bg-white focus:outline-none focus:ring-2 focus:ring-emerald-500"
              >
                <option value="proximidade">Data mais próxima</option>
                <option value="limite">Maior limite</option>
                <option value="saldo">Maior saldo disponível</option>
              </select>
            </label>
          </div>

          {#if cotasOrdenadas.length === 0}
            <div class="bg-white rounded-xl shadow-sm border border-gray-100 p-8 text-center">
              <p class="text-gray-700 font-medium">Nenhuma cota encontrada para este grupo.</p>
              <button type="button" on:click={limparFiltroGrupo} class="text-sm text-emerald-700 hover:underline mt-1">
                Limpar filtro
              </button>
            </div>
          {:else}

          <div class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-x-auto">
            <table class="min-w-full text-sm">
              <thead class="bg-gray-50 text-gray-700 uppercase text-xs tracking-wider">
                <tr>
                  <th class="text-left px-4 py-3 min-w-[120px]">Titular</th>
                  <th class="text-left px-4 py-3 min-w-[380px]">Especialidade / Exame</th>
                  <th class="text-left px-4 py-3 min-w-[100px]">Período</th>
                  <th class="text-right px-4 py-3 min-w-[100px]">Utilizadas</th>
                  <th class="text-right px-4 py-3 min-w-[80px]">Total</th>
                  <th class="text-right px-4 py-3 min-w-[80px]">Saldo</th>
                  <th class="text-left px-4 py-3 w-48">Consumo</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-100">
                {#each cotasPaginadas as c (c.id)}
                  <tr class:opacity-50={!c.ativo}>
                    <td class="px-4 py-3">
                      {#if c.grupoUnidadesId}
                        <span class="inline-flex items-center rounded-full bg-emerald-100 text-emerald-800 px-2 py-0.5 text-xs font-medium">
                          Grupo
                        </span>
                        <span class="ml-2 text-gray-700">{c.grupoUnidadesNome}</span>
                      {:else}
                        <span class="inline-flex items-center rounded-full bg-gray-100 text-gray-700 px-2 py-0.5 text-xs font-medium">
                          Unidade
                        </span>
                        <span class="ml-2 text-gray-700">{c.unidadeNome}</span>
                      {/if}
                    </td>
                    <td class="px-4 py-3 text-gray-800">
                      {#if c.grupoEspecialidadesId}
                        <span class="inline-flex items-center rounded-full bg-sky-100 text-sky-800 px-2 py-0.5 text-xs font-medium">
                          Grupo
                        </span>
                        <span class="ml-2">{c.grupoEspecialidadesNome}</span>
                        <GrupoToggleButton
                          grupo={grupos.find((g) => g.id === c.grupoEspecialidadesId)}
                          aberto={!!gruposAbertos[c.id]}
                          onToggle={() => alternarGrupo(c.id)}
                        />
                      {:else if c.especialidadeNome}
                        {c.especialidadeNome}
                      {:else}
                        <span class="text-gray-500">Cota geral (todas)</span>
                      {/if}
                    </td>
                    <td class="px-4 py-3 text-gray-700">{formatarPeriodoCota(c)}</td>
                    <td class="px-4 py-3 text-right text-gray-800">{c.quantidadeUtilizada}</td>
                    <td class="px-4 py-3 text-right text-gray-800">{c.quantidadeTotal}</td>
                    <td class="px-4 py-3 text-right font-semibold"
                        class:text-red-600={c.saldoDisponivel <= 0}
                        class:text-gray-900={c.saldoDisponivel > 0}>
                      {c.saldoDisponivel}
                    </td>
                    <td class="px-4 py-3">
                      <div class="h-2 w-full rounded-full bg-gray-200 overflow-hidden">
                        <div class="h-full rounded-full {corDaBarra(c)}" style="width: {percentual(c)}%"></div>
                      </div>
                      {#if !c.ativo}
                        <span class="text-xs text-gray-500">inativa</span>
                      {:else if c.saldoDisponivel <= 0}
                        <span class="text-xs text-red-600">esgotada</span>
                      {/if}
                    </td>
                  </tr>
                  {#if c.grupoEspecialidadesId && gruposAbertos[c.id]}
                    <tr>
                      <td colspan="7" class="px-4 pb-4 pt-0 bg-gray-50/60">
                        <GrupoEspecialidadesPainel grupo={grupos.find((g) => g.id === c.grupoEspecialidadesId)} />
                      </td>
                    </tr>
                  {/if}
                {/each}
              </tbody>
            </table>
          </div>

          {#if totalPaginas > 1}
            <div class="flex justify-center items-center gap-2">
              <button
                type="button"
                on:click={paginaAnterior}
                disabled={paginaAtual === 1}
                class="px-3 py-1 bg-emerald-600 hover:bg-emerald-800 text-white rounded disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                &laquo; Anterior
              </button>
              <span class="text-sm text-gray-600">Página {paginaAtual} de {totalPaginas}</span>
              <button
                type="button"
                on:click={proximaPagina}
                disabled={paginaAtual === totalPaginas}
                class="px-3 py-1 bg-emerald-600 hover:bg-emerald-800 text-white rounded disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                Próximo &raquo;
              </button>
            </div>
          {/if}
          {/if}
        {/if}

      </div>
    </main>
  </div>
</div>
