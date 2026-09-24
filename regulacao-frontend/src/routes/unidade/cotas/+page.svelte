<script lang="ts">
  import { onMount } from 'svelte';
  import { user } from '$lib/stores/auth.js';
  import { getApi } from '$lib/api.js';
  import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';
  import UserMenu from '$lib/UserMenu.svelte';

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

  let cotas: Cota[] = [];
  let unidadeNome = '';
  let grupoNome: string | null = null;
  let isLoading = true;
  let error = '';

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
    } catch (e: unknown) {
      error = e instanceof Error ? e.message : String(e);
    } finally {
      isLoading = false;
    }
  });

  function periodoLegivel(c: Cota) {
    if (c.tipoPeriodo === 'DATA') {
      return c.dataEspecifica ? new Date(`${c.dataEspecifica}T00:00:00`).toLocaleDateString('pt-BR') : '—';
    }
    if (!c.periodo) return '—';
    const [ano, mes] = c.periodo.split('-');
    return `${mes}/${ano}`;
  }

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

  $: cotasOrdenadas = [...cotas].sort((a, b) => {
    // Esgotadas primeiro: é a informação que muda o que o usuário pode fazer hoje.
    const sa = a.saldoDisponivel, sb = b.saldoDisponivel;
    if (sa !== sb) return sa - sb;
    const nomeA = a.especialidadeNome ?? a.grupoEspecialidadesNome ?? '';
    const nomeB = b.especialidadeNome ?? b.grupoEspecialidadesNome ?? '';
    return nomeA.localeCompare(nomeB);
  });
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
      <div class="max-w-6xl mx-auto space-y-6">

        {#if isLoading}
          <p class="text-gray-600">Carregando cotas...</p>
        {:else if error}
          <div class="bg-red-50 border border-red-200 text-red-700 rounded-lg p-4">{error}</div>
        {:else if cotasOrdenadas.length === 0}
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

          <div class="bg-white rounded-xl shadow-sm border border-gray-100 overflow-x-auto">
            <table class="min-w-full text-sm">
              <thead class="bg-gray-50 text-gray-700 uppercase text-xs tracking-wider">
                <tr>
                  <th class="text-left px-4 py-3">Titular</th>
                  <th class="text-left px-4 py-3">Especialidade / Exame</th>
                  <th class="text-left px-4 py-3">Período</th>
                  <th class="text-right px-4 py-3">Utilizadas</th>
                  <th class="text-right px-4 py-3">Total</th>
                  <th class="text-right px-4 py-3">Saldo</th>
                  <th class="text-left px-4 py-3 w-48">Consumo</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-gray-100">
                {#each cotasOrdenadas as c (c.id)}
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
                      {:else if c.especialidadeNome}
                        {c.especialidadeNome}
                      {:else}
                        <span class="text-gray-500">Cota geral (todas)</span>
                      {/if}
                    </td>
                    <td class="px-4 py-3 text-gray-700">{periodoLegivel(c)}</td>
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
                {/each}
              </tbody>
            </table>
          </div>
        {/if}

      </div>
    </main>
  </div>
</div>
