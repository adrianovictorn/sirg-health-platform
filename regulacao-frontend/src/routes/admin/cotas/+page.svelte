<script>
  import { onMount } from 'svelte';
  import { getApi, postApi, putApi } from '$lib/api.js';
  import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';
  import UserMenu from '$lib/UserMenu.svelte';
  import { toast } from 'svelte-sonner';

  let cotas = [];
  let unidades = [];
  let especialidades = [];
  let loading = true;
  let showModal = false;
  let editando = null;

  let filtroUnidade = '';
  let periodoAtual = new Date().toISOString().slice(0, 7);

  let form = { unidadeId: null, especialidadeId: null, periodo: periodoAtual, quantidadeTotal: 0 };
  let formEditar = { quantidadeTotal: 0, ativo: true };

  onMount(async () => {
    await Promise.all([carregarUnidades(), carregarEspecialidades()]);
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

  async function carregarEspecialidades() {
    try {
      const res = await getApi('catalog/especialidades/listar');
      especialidades = await res.json();
    } catch {}
  }

  $: cotasFiltradas = filtroUnidade
    ? cotas.filter(c => c.unidadeId === Number(filtroUnidade))
    : cotas;

  function abrirModalNova() {
    editando = null;
    form = { unidadeId: null, especialidadeId: null, periodo: periodoAtual, quantidadeTotal: 0 };
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
        if (!form.unidadeId) { toast.error('Selecione a unidade.'); return; }
        const payload = {
          unidadeId: Number(form.unidadeId),
          especialidadeId: form.especialidadeId ? Number(form.especialidadeId) : null,
          periodo: form.periodo,
          quantidadeTotal: Number(form.quantidadeTotal)
        };
        await postApi('cotas', payload);
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
                <th class="px-4 py-3 text-left">Unidade</th>
                <th class="px-4 py-3 text-left">Especialidade</th>
                <th class="px-4 py-3 text-left">Período</th>
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
                  <td class="px-4 py-3 font-medium text-white">{c.unidadeNome}</td>
                  <td class="px-4 py-3">{c.especialidadeNome ?? 'Geral'}</td>
                  <td class="px-4 py-3">{c.periodo}</td>
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
            <span class="text-white font-medium">{editando.unidadeNome}</span>
            {editando.especialidadeNome ? ` · ${editando.especialidadeNome}` : ' · Geral'}
            · <span class="text-slate-300">{editando.periodo}</span>
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
            <label class="block text-xs text-slate-400 mb-1">Unidade *</label>
            <select bind:value={form.unidadeId}
              class="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500">
              <option value={null}>Selecionar unidade...</option>
              {#each unidades as u}
                <option value={u.id}>{u.nome}</option>
              {/each}
            </select>
          </div>
          <div>
            <label class="block text-xs text-slate-400 mb-1">Especialidade (deixe vazio para cota geral)</label>
            <select bind:value={form.especialidadeId}
              class="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500">
              <option value={null}>— Cota Geral —</option>
              {#each especialidades as e}
                <option value={e.id}>{e.nome}</option>
              {/each}
            </select>
          </div>
          <div>
            <label class="block text-xs text-slate-400 mb-1">Período (YYYY-MM) *</label>
            <input type="month" bind:value={form.periodo}
              class="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500" />
          </div>
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
