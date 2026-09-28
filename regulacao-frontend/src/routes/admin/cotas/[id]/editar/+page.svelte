<script>
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { page } from '$app/stores';
  import { getApi, putApi } from '$lib/api.js';
  import Content from '$lib/Content.svelte';
  import { toast } from 'svelte-sonner';

  const cotaId = $page.params.id;

  let unidades = $state([]);
  let grupos = $state([]);
  let especialidades = $state([]);
  let carregando = $state(true);
  let salvando = $state(false);
  let origemAgenda = $state(false);
  let agendaId = $state(null);

  let form = $state({
    titular: 'UNIDADE',
    escopo: 'ESPECIALIDADE',
    grupoUnidadesId: null,
    grupoEspecialidadesId: null,
    unidadeId: null,
    especialidadeId: null,
    tipoPeriodo: 'MENSAL',
    periodo: '',
    dataEspecifica: '',
    quantidadeTotal: 0,
    ativo: true
  });

  onMount(async () => {
    await Promise.all([carregarUnidades(), carregarEspecialidades(), carregarGrupos(), carregarCota()]);
    carregando = false;
  });

  async function carregarUnidades() {
    try {
      const res = await getApi('unidades/ativas');
      unidades = await res.json();
    } catch { /* ignora - combo fica vazio */ }
  }

  async function carregarGrupos() {
    try {
      const res = await getApi('grupo-relatorio/listar');
      grupos = res.ok ? await res.json() : [];
    } catch { /* ignora - combo fica vazio */ }
  }

  async function carregarEspecialidades() {
    try {
      const res = await getApi('catalog/especialidades/listar');
      especialidades = await res.json();
    } catch { /* ignora - combo fica vazio */ }
  }

  async function carregarCota() {
    try {
      const res = await getApi(`cotas/${cotaId}`);
      if (!res.ok) {
        toast.error('Cota não encontrada.');
        goto('/admin/cotas');
        return;
      }
      const c = await res.json();
      origemAgenda = c.origem === 'AGENDA';
      agendaId = c.agendaId;
      form = {
        titular: c.grupoUnidadesId ? 'GRUPO' : 'UNIDADE',
        escopo: c.grupoEspecialidadesId ? 'GRUPO_ESPECIALIDADES' : (c.especialidadeId ? 'ESPECIALIDADE' : 'GERAL'),
        grupoUnidadesId: c.grupoUnidadesId,
        grupoEspecialidadesId: c.grupoEspecialidadesId,
        unidadeId: c.unidadeId,
        especialidadeId: c.especialidadeId,
        tipoPeriodo: c.tipoPeriodo,
        periodo: c.periodo ?? new Date().toISOString().slice(0, 7),
        dataEspecifica: c.dataEspecifica ?? new Date().toISOString().slice(0, 10),
        quantidadeTotal: c.quantidadeTotal,
        ativo: c.ativo
      };
    } catch {
      toast.error('Erro ao carregar cota.');
      goto('/admin/cotas');
    }
  }

  const grupoSelecionado = $derived(
    form.escopo === 'GRUPO_ESPECIALIDADES' && form.grupoEspecialidadesId
      ? grupos.find((g) => g.id === Number(form.grupoEspecialidadesId))
      : null
  );

  async function salvar() {
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
      quantidadeTotal: Number(form.quantidadeTotal),
      ativo: form.ativo
    };

    salvando = true;
    try {
      const res = await putApi(`cotas/${cotaId}`, payload);
      if (!res.ok) {
        const erro = await res.json().catch(() => ({}));
        toast.error(erro.message ?? 'Erro ao salvar cota.');
        return;
      }
      toast.success('Cota atualizada.');
      goto('/admin/cotas');
    } catch {
      toast.error('Erro ao salvar cota.');
    } finally {
      salvando = false;
    }
  }
</script>

<Content titleH1="Editar Cota" page="/admin/cotas">
  <main class="p-6 max-w-2xl">
    {#if carregando}
      <p class="text-gray-500 text-sm">Carregando...</p>
    {:else if origemAgenda}
      <div class="bg-violet-50 border border-violet-200 text-violet-800 rounded-xl p-6 space-y-3">
        <p class="text-sm">
          Esta cota foi gerada por uma agenda e não pode ser editada diretamente aqui.
          Para alterar vagas, vigência ou horário, edite a agenda de origem.
        </p>
        <div class="flex gap-3">
          <button onclick={() => goto(`/liberacao-agenda/${agendaId}`)}
            class="px-4 py-2 rounded-lg bg-violet-600 text-white text-sm hover:bg-violet-500 transition-colors">
            Ir para a agenda
          </button>
          <button onclick={() => goto('/admin/cotas')}
            class="px-4 py-2 rounded-lg bg-gray-100 text-gray-700 text-sm hover:bg-gray-200 transition-colors">
            Voltar
          </button>
        </div>
      </div>
    {:else}
      <div class="bg-white border border-gray-200 rounded-xl p-6 space-y-4">
        <div>
          <label class="block text-xs text-gray-500 mb-1">Titular da cota *</label>
          <div class="flex gap-4">
            <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
              <input type="radio" bind:group={form.titular} value="UNIDADE" class="accent-emerald-600" />
              Unidade
            </label>
            <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
              <input type="radio" bind:group={form.titular} value="GRUPO" class="accent-emerald-600" />
              Grupo
            </label>
          </div>
        </div>

        {#if form.titular === 'UNIDADE'}
          <div>
            <label class="block text-xs text-gray-500 mb-1">Unidade *</label>
            <select bind:value={form.unidadeId}
              class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500">
              <option value={null}>Selecionar unidade...</option>
              {#each unidades as u (u.id)}
                <option value={u.id}>{u.nome}</option>
              {/each}
            </select>
          </div>
        {:else}
          <div>
            <label class="block text-xs text-gray-500 mb-1">Grupo de Unidades *</label>
            <select bind:value={form.grupoUnidadesId}
              class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500">
              <option value={null}>Selecionar grupo...</option>
              {#each grupos as g (g.id)}
                <option value={g.id}>{g.nome}</option>
              {/each}
            </select>
          </div>
        {/if}

        <div>
          <label class="block text-xs text-gray-500 mb-1">O que a cota limita *</label>
          <div class="flex flex-wrap gap-4">
            <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
              <input type="radio" bind:group={form.escopo} value="ESPECIALIDADE" class="accent-emerald-600" />
              Uma especialidade
            </label>
            <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
              <input type="radio" bind:group={form.escopo} value="GRUPO_ESPECIALIDADES" class="accent-emerald-600" />
              Um grupo de especialidades
            </label>
            <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
              <input type="radio" bind:group={form.escopo} value="GERAL" class="accent-emerald-600" />
              Todas (cota geral)
            </label>
          </div>
        </div>

        {#if form.escopo === 'ESPECIALIDADE'}
          <div>
            <label class="block text-xs text-gray-500 mb-1">Especialidade</label>
            <select bind:value={form.especialidadeId}
              class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500">
              <option value={null}>Selecionar especialidade...</option>
              {#each especialidades as e (e.id)}
                <option value={e.id}>{e.nome}</option>
              {/each}
            </select>
          </div>
        {:else if form.escopo === 'GRUPO_ESPECIALIDADES'}
          <div>
            <label class="block text-xs text-gray-500 mb-1">Grupo de Especialidades *</label>
            <select bind:value={form.grupoEspecialidadesId}
              class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500">
              <option value={null}>Selecionar grupo...</option>
              {#each grupos as g (g.id)}
                <option value={g.id}>{g.nome}</option>
              {/each}
            </select>
            {#if grupoSelecionado}
              <details class="mt-2 text-xs">
                <summary class="cursor-pointer text-emerald-700 hover:text-emerald-600 select-none">
                  Ver especialidades deste grupo ({grupoSelecionado.especialidades?.length ?? 0})
                </summary>
                <ul class="mt-2 space-y-1 pl-3 border-l border-gray-200 text-gray-700">
                  {#each grupoSelecionado.especialidades ?? [] as e (e.id)}
                    <li>{e.nome}</li>
                  {/each}
                </ul>
              </details>
            {/if}
          </div>
        {:else}
          <p class="text-xs text-gray-500">
            A cota valerá para qualquer especialidade deste titular.
          </p>
        {/if}

        <div>
          <label class="block text-xs text-gray-500 mb-1">Tipo de Controle *</label>
          <div class="flex gap-4">
            <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
              <input type="radio" bind:group={form.tipoPeriodo} value="MENSAL" class="accent-emerald-600" />
              Mensal
            </label>
            <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
              <input type="radio" bind:group={form.tipoPeriodo} value="DATA" class="accent-emerald-600" />
              Por Data Específica
            </label>
          </div>
        </div>

        {#if form.tipoPeriodo === 'MENSAL'}
          <div>
            <label class="block text-xs text-gray-500 mb-1">Período (YYYY-MM) *</label>
            <input type="month" bind:value={form.periodo}
              class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
          </div>
        {:else}
          <div>
            <label class="block text-xs text-gray-500 mb-1">Data Específica *</label>
            <input type="date" bind:value={form.dataEspecifica}
              class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
          </div>
        {/if}

        <div>
          <label class="block text-xs text-gray-500 mb-1">Quantidade Total *</label>
          <input type="number" min="0" bind:value={form.quantidadeTotal}
            class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>

        <div class="flex items-center gap-2">
          <input type="checkbox" id="ativoEdit" bind:checked={form.ativo} class="accent-emerald-600" />
          <label for="ativoEdit" class="text-sm text-gray-700">Cota ativa</label>
        </div>

        <div class="flex justify-end gap-3 pt-2">
          <button onclick={() => goto('/admin/cotas')}
            class="px-4 py-2 rounded-lg bg-gray-100 text-gray-700 text-sm hover:bg-gray-200 transition-colors">
            Cancelar
          </button>
          <button onclick={salvar} disabled={salvando}
            class="px-4 py-2 rounded-lg bg-emerald-600 text-white text-sm hover:bg-emerald-500 transition-colors disabled:opacity-50">
            {salvando ? 'Salvando...' : 'Salvar'}
          </button>
        </div>
      </div>
    {/if}
  </main>
</Content>
