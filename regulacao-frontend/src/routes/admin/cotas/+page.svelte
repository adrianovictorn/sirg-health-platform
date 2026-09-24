<script>
  import { onMount } from 'svelte';
  import { getApi, postApi, putApi } from '$lib/api.js';
  import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';
  import UserMenu from '$lib/UserMenu.svelte';
  import { toast } from 'svelte-sonner';

  let cotas = [];
  let unidades = [];
  let grupos = [];
  let especialidades = [];
  let loading = true;
  let showModal = false;
  let editando = null;

  let filtroUnidade = '';
  let periodoAtual = new Date().toISOString().slice(0, 7);
  let hojeISO = new Date().toISOString().slice(0, 10);

  // A cota tem duas dimensões independentes:
  //   TITULAR — de quem é: uma unidade OU um grupo de unidades (pool compartilhado).
  //   ESCOPO  — o que limita: uma especialidade, OU um grupo de especialidades
  //             (saldo único entre todas elas), OU nada (cota geral).
  // O backend recusa titular ausente/duplicado e escopo duplicado.
  let form = {
    titular: 'UNIDADE',
    escopo: 'ESPECIALIDADE',
    grupoUnidadesId: null,
    grupoEspecialidadesId: null,
    unidadeId: null,
    especialidadeId: null,
    tipoPeriodo: 'MENSAL',
    periodo: periodoAtual,
    dataEspecifica: hojeISO,
    quantidadeTotal: 0
  };
  let formEditar = { quantidadeTotal: 0, ativo: true };

  onMount(async () => {
    await Promise.all([carregarUnidades(), carregarEspecialidades(), carregarGrupos()]);
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
    } catch {}
  }

  async function carregarGrupos() {
    try {
      // O agrupamento de unidades reaproveita os Grupos de Relatorio (a unidade
      // aponta para o grupo via unidade.grupoRelatorioId).
      const res = await getApi('grupo-relatorio/listar');
      grupos = res.ok ? await res.json() : [];
    } catch {}
  }

  async function carregarEspecialidades() {
    try {
      const res = await getApi('catalog/especialidades/listar');
      especialidades = await res.json();
    } catch {}
  }

  // Ao filtrar por unidade, mostra também as cotas do grupo a que ela pertence:
  // elas limitam essa unidade tanto quanto as cotas próprias.
  $: grupoDaUnidadeFiltrada = filtroUnidade
    ? (unidades.find(u => u.id === Number(filtroUnidade))?.grupoRelatorioId ?? null)
    : null;

  $: cotasFiltradas = filtroUnidade
    ? cotas.filter(c => c.unidadeId === Number(filtroUnidade)
        || (grupoDaUnidadeFiltrada && c.grupoUnidadesId === grupoDaUnidadeFiltrada))
    : cotas;

  function formatarPeriodo(c) {
    if (c.tipoPeriodo === 'DATA') return c.dataEspecifica ?? '-';
    return c.periodo ?? '-';
  }

  function abrirModalNova() {
    editando = null;
    form = {
      titular: 'UNIDADE',
      escopo: 'ESPECIALIDADE',
      grupoUnidadesId: null,
      grupoEspecialidadesId: null,
      unidadeId: null,
      especialidadeId: null,
      tipoPeriodo: 'MENSAL',
      periodo: periodoAtual,
      dataEspecifica: hojeISO,
      quantidadeTotal: 0
    };
    showModal = true;
  }

  function abrirModalEditar(c) {
    editando = c;
    formEditar = { quantidadeTotal: c.quantidadeTotal, ativo: c.ativo };
    showModal = true;
  }

  async function salvar() {
    try {
      if (editando) {
        await putApi(`cotas/${editando.id}`, formEditar);
        toast.success('Cota atualizada.');
      } else {
        const porGrupoUnidades = form.titular === 'GRUPO';
        if (porGrupoUnidades && !form.grupoUnidadesId) { toast.error('Selecione o grupo de unidades.'); return; }
        if (!porGrupoUnidades && !form.unidadeId) { toast.error('Selecione a unidade.'); return; }

        const porGrupoEsp = form.escopo === 'GRUPO_ESPECIALIDADES';
        if (porGrupoEsp && !form.grupoEspecialidadesId) { toast.error('Selecione o grupo de especialidades.'); return; }

        const payload = {
          unidadeId: porGrupoUnidades ? null : Number(form.unidadeId),
          grupoUnidadesId: porGrupoUnidades ? Number(form.grupoUnidadesId) : null,
          especialidadeId: (!porGrupoEsp && form.especialidadeId) ? Number(form.especialidadeId) : null,
          grupoEspecialidadesId: porGrupoEsp ? Number(form.grupoEspecialidadesId) : null,
          tipoPeriodo: form.tipoPeriodo,
          periodo: form.tipoPeriodo === 'MENSAL' ? form.periodo : null,
          dataEspecifica: form.tipoPeriodo === 'DATA' ? form.dataEspecifica : null,
          quantidadeTotal: Number(form.quantidadeTotal)
        };
        const res = await postApi('cotas', payload);
        if (!res.ok) {
          const erro = await res.json().catch(() => ({}));
          toast.error(erro.message ?? 'Erro ao criar cota.');
          return;
        }
        toast.success('Cota criada.');
      }
      showModal = false;
      await carregarCotas();
    } catch {
      toast.error('Erro ao salvar cota.');
    }
  }
</script>

<div class="flex min-h-screen bg-slate-950">
  <RoleBasedMenu activePage="/admin/cotas" />

  <div class="flex-1 flex flex-col">
    <header class="flex items-center justify-between px-6 py-4 border-b border-slate-800 bg-slate-900">
      <h1 class="text-lg font-semibold text-white">Cotas por Unidade</h1>
      <UserMenu />
    </header>

    <main class="p-6 space-y-4">
      <div class="flex items-center justify-between gap-4">
        <div class="flex items-center gap-3">
          <select bind:value={filtroUnidade}
            class="bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500">
            <option value="">Todas as unidades</option>
            {#each unidades as u}
              <option value={u.id}>{u.nome}</option>
            {/each}
          </select>
        </div>
        <button on:click={abrirModalNova}
          class="px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-medium transition-colors">
          + Nova Cota
        </button>
      </div>

      {#if loading}
        <p class="text-slate-400 text-sm">Carregando...</p>
      {:else}
        <div class="overflow-x-auto rounded-xl border border-slate-800">
          <table class="w-full text-sm text-slate-300">
            <thead class="bg-slate-800 text-slate-400 uppercase text-xs">
              <tr>
                <th class="px-4 py-3 text-left">Titular</th>
                <th class="px-4 py-3 text-left">Especialidade</th>
                <th class="px-4 py-3 text-left">Tipo</th>
                <th class="px-4 py-3 text-left">Período / Data</th>
                <th class="px-4 py-3 text-right">Total</th>
                <th class="px-4 py-3 text-right">Utilizado</th>
                <th class="px-4 py-3 text-right">Saldo</th>
                <th class="px-4 py-3 text-left">Status</th>
                <th class="px-4 py-3 text-left">Ações</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-800">
              {#each cotasFiltradas as c}
                <tr class="hover:bg-slate-800/40 transition-colors">
                  <td class="px-4 py-3 font-medium text-white">
                    {#if c.grupoUnidadesId}
                      <span class="px-2 py-0.5 rounded text-xs font-medium bg-emerald-500/20 text-emerald-300">Grupo</span>
                      <span class="ml-2">{c.grupoUnidadesNome}</span>
                    {:else}
                      {c.unidadeNome}
                    {/if}
                  </td>
                  <td class="px-4 py-3">
                    {#if c.grupoEspecialidadesId}
                      <span class="px-2 py-0.5 rounded text-xs font-medium bg-sky-500/20 text-sky-300">Grupo</span>
                      <span class="ml-2">{c.grupoEspecialidadesNome}</span>
                    {:else if c.especialidadeNome}
                      {c.especialidadeNome}
                    {:else}
                      <span class="text-slate-500">Geral (todas)</span>
                    {/if}
                  </td>
                  <td class="px-4 py-3">
                    <span class="px-2 py-0.5 rounded text-xs font-medium {c.tipoPeriodo === 'DATA' ? 'bg-blue-500/20 text-blue-300' : 'bg-slate-600/40 text-slate-300'}">
                      {c.tipoPeriodo === 'DATA' ? 'Por Data' : 'Mensal'}
                    </span>
                  </td>
                  <td class="px-4 py-3">{formatarPeriodo(c)}</td>
                  <td class="px-4 py-3 text-right">{c.quantidadeTotal}</td>
                  <td class="px-4 py-3 text-right">{c.quantidadeUtilizada}</td>
                  <td class="px-4 py-3 text-right font-semibold {c.saldoDisponivel <= 0 ? 'text-red-400' : 'text-emerald-400'}">
                    {c.saldoDisponivel}
                  </td>
                  <td class="px-4 py-3">
                    <span class="px-2 py-0.5 rounded-full text-xs font-medium {c.ativo ? 'bg-emerald-500/20 text-emerald-400' : 'bg-red-500/20 text-red-400'}">
                      {c.ativo ? 'Ativa' : 'Inativa'}
                    </span>
                  </td>
                  <td class="px-4 py-3">
                    <button on:click={() => abrirModalEditar(c)}
                      class="px-3 py-1 rounded bg-blue-600/20 text-blue-400 hover:bg-blue-600/40 text-xs transition-colors">
                      Editar
                    </button>
                  </td>
                </tr>
              {/each}
            </tbody>
          </table>
        </div>
      {/if}
    </main>
  </div>
</div>

{#if showModal}
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/60">
    <div class="bg-slate-900 border border-slate-700 rounded-2xl p-6 w-full max-w-md shadow-2xl space-y-4">
      <h2 class="text-white font-semibold text-base">{editando ? 'Editar Cota' : 'Nova Cota'}</h2>

      {#if editando}
        <div class="space-y-3">
          <p class="text-slate-400 text-sm">
            <span class="text-white font-medium">{editando.grupoUnidadesNome ?? editando.unidadeNome}</span>
            {editando.grupoEspecialidadesNome ? ` · Grupo ${editando.grupoEspecialidadesNome}` : (editando.especialidadeNome ? ` · ${editando.especialidadeNome}` : ' · Geral')}
            · <span class="text-slate-300">{formatarPeriodo(editando)}</span>
          </p>
          <div>
            <label class="block text-xs text-slate-400 mb-1">Quantidade Total</label>
            <input type="number" min="0" bind:value={formEditar.quantidadeTotal}
              class="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500" />
          </div>
          <div class="flex items-center gap-2">
            <input type="checkbox" id="ativoEdit" bind:checked={formEditar.ativo} class="accent-emerald-500" />
            <label for="ativoEdit" class="text-sm text-slate-300">Cota ativa</label>
          </div>
        </div>
      {:else}
        <div class="space-y-3">
          <div>
            <label class="block text-xs text-slate-400 mb-1">Titular da cota *</label>
            <div class="flex gap-4">
              <label class="flex items-center gap-2 text-sm text-slate-300 cursor-pointer">
                <input type="radio" bind:group={form.titular} value="UNIDADE" class="accent-emerald-500" />
                Unidade
              </label>
              <label class="flex items-center gap-2 text-sm text-slate-300 cursor-pointer">
                <input type="radio" bind:group={form.titular} value="GRUPO" class="accent-emerald-500" />
                Grupo
              </label>
            </div>
          </div>

          {#if form.titular === 'UNIDADE'}
            <div>
              <label class="block text-xs text-slate-400 mb-1">Unidade *</label>
              <select bind:value={form.unidadeId}
                class="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500">
                <option value={null}>Selecionar unidade...</option>
                {#each unidades as u}
                  <option value={u.id}>{u.nome}</option>
                {/each}
              </select>
            </div>
          {:else}
            <div>
              <label class="block text-xs text-slate-400 mb-1">Grupo de Unidades *</label>
              <select bind:value={form.grupoUnidadesId}
                class="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500">
                <option value={null}>Selecionar grupo...</option>
                {#each grupos as g}
                  <option value={g.id}>{g.nome}</option>
                {/each}
              </select>
              <p class="text-xs text-slate-500 mt-1">
                Saldo compartilhado entre as unidades do grupo — consumido por ordem de chegada.
              </p>
            </div>
          {/if}
          <div>
            <label class="block text-xs text-slate-400 mb-1">O que a cota limita *</label>
            <div class="flex flex-wrap gap-4">
              <label class="flex items-center gap-2 text-sm text-slate-300 cursor-pointer">
                <input type="radio" bind:group={form.escopo} value="ESPECIALIDADE" class="accent-emerald-500" />
                Uma especialidade
              </label>
              <label class="flex items-center gap-2 text-sm text-slate-300 cursor-pointer">
                <input type="radio" bind:group={form.escopo} value="GRUPO_ESPECIALIDADES" class="accent-emerald-500" />
                Um grupo de especialidades
              </label>
              <label class="flex items-center gap-2 text-sm text-slate-300 cursor-pointer">
                <input type="radio" bind:group={form.escopo} value="GERAL" class="accent-emerald-500" />
                Todas (cota geral)
              </label>
            </div>
          </div>

          {#if form.escopo === 'ESPECIALIDADE'}
            <div>
              <label class="block text-xs text-slate-400 mb-1">Especialidade</label>
              <select bind:value={form.especialidadeId}
                class="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500">
                <option value={null}>Selecionar especialidade...</option>
                {#each especialidades as e}
                  <option value={e.id}>{e.nome}</option>
                {/each}
              </select>
            </div>
          {:else if form.escopo === 'GRUPO_ESPECIALIDADES'}
            <div>
              <label class="block text-xs text-slate-400 mb-1">Grupo de Especialidades *</label>
              <select bind:value={form.grupoEspecialidadesId}
                class="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500">
                <option value={null}>Selecionar grupo...</option>
                {#each grupos as g}
                  <option value={g.id}>{g.nome}</option>
                {/each}
              </select>
              <p class="text-xs text-slate-500 mt-1">
                Evita cadastrar uma cota por especialidade. O saldo é <strong>único e
                compartilhado</strong> entre todas as especialidades do grupo.
              </p>
            </div>
          {:else}
            <p class="text-xs text-slate-500">
              A cota valerá para qualquer especialidade deste titular.
            </p>
          {/if}
          <div>
            <label class="block text-xs text-slate-400 mb-1">Tipo de Controle *</label>
            <div class="flex gap-4">
              <label class="flex items-center gap-2 text-sm text-slate-300 cursor-pointer">
                <input type="radio" bind:group={form.tipoPeriodo} value="MENSAL" class="accent-emerald-500" />
                Mensal
              </label>
              <label class="flex items-center gap-2 text-sm text-slate-300 cursor-pointer">
                <input type="radio" bind:group={form.tipoPeriodo} value="DATA" class="accent-emerald-500" />
                Por Data Específica
              </label>
            </div>
          </div>
          {#if form.tipoPeriodo === 'MENSAL'}
            <div>
              <label class="block text-xs text-slate-400 mb-1">Período (YYYY-MM) *</label>
              <input type="month" bind:value={form.periodo}
                class="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500" />
            </div>
          {:else}
            <div>
              <label class="block text-xs text-slate-400 mb-1">Data Específica *</label>
              <input type="date" bind:value={form.dataEspecifica}
                class="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500" />
            </div>
          {/if}
          <div>
            <label class="block text-xs text-slate-400 mb-1">Quantidade Total *</label>
            <input type="number" min="0" bind:value={form.quantidadeTotal}
              class="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500" />
          </div>
        </div>
      {/if}

      <div class="flex justify-end gap-3 pt-2">
        <button on:click={() => showModal = false}
          class="px-4 py-2 rounded-lg bg-slate-700 text-slate-300 text-sm hover:bg-slate-600 transition-colors">
          Cancelar
        </button>
        <button on:click={salvar}
          class="px-4 py-2 rounded-lg bg-emerald-600 text-white text-sm hover:bg-emerald-500 transition-colors">
          Salvar
        </button>
      </div>
    </div>
  </div>
{/if}
