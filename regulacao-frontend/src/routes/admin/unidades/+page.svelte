<script>
  import { onMount } from 'svelte';
  import { getApi, postApi, putApi, patchApi } from '$lib/api.js';
  import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';
  import UserMenu from '$lib/UserMenu.svelte';
  import { toast } from 'svelte-sonner';

  let unidades = [];
  let loading = true;
  let showModal = false;
  let editando = null;

  let form = { nome: '', codigo: '', cnes: '', telefone: '', endereco: '' };

  onMount(async () => {
    await carregarUnidades();
  });

  async function carregarUnidades() {
    loading = true;
    try {
      const res = await getApi('unidades');
      unidades = await res.json();
    } catch {
      toast.error('Erro ao carregar unidades.');
    } finally {
      loading = false;
    }
  }

  function abrirModalNova() {
    editando = null;
    form = { nome: '', codigo: '', cnes: '', telefone: '', endereco: '' };
    showModal = true;
  }

  function abrirModalEditar(u) {
    editando = u;
    form = { nome: u.nome, codigo: u.codigo || '', cnes: u.cnes || '', telefone: u.telefone || '', endereco: u.endereco || '' };
    showModal = true;
  }

  async function salvar() {
    if (!form.nome.trim()) { toast.error('O nome é obrigatório.'); return; }
    try {
      if (editando) {
        await putApi(`unidades/${editando.id}`, form);
        toast.success('Unidade atualizada.');
      } else {
        await postApi('unidades', form);
        toast.success('Unidade criada.');
      }
      showModal = false;
      await carregarUnidades();
    } catch {
      toast.error('Erro ao salvar unidade.');
    }
  }

  async function toggleAtivo(u) {
    try {
      await patchApi(`unidades/${u.id}/status`);
      toast.success(u.ativo ? 'Unidade desativada.' : 'Unidade ativada.');
      await carregarUnidades();
    } catch {
      toast.error('Erro ao alterar status.');
    }
  }
</script>

<div class="flex min-h-screen bg-slate-950">
  <RoleBasedMenu activePage="/admin/unidades" />

  <div class="flex-1 flex flex-col">
    <header class="flex items-center justify-between px-6 py-4 border-b border-slate-800 bg-slate-900">
      <h1 class="text-lg font-semibold text-white">Gestão de Unidades</h1>
      <UserMenu />
    </header>

    <main class="p-6 space-y-4">
      <div class="flex justify-end">
        <button on:click={abrirModalNova}
          class="px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-medium transition-colors">
          + Nova Unidade
        </button>
      </div>

      {#if loading}
        <p class="text-slate-400 text-sm">Carregando...</p>
      {:else}
        <div class="overflow-x-auto rounded-xl border border-slate-800">
          <table class="w-full text-sm text-slate-300">
            <thead class="bg-slate-800 text-slate-400 uppercase text-xs">
              <tr>
                <th class="px-4 py-3 text-left">Nome</th>
                <th class="px-4 py-3 text-left">Código</th>
                <th class="px-4 py-3 text-left">CNES</th>
                <th class="px-4 py-3 text-left">Telefone</th>
                <th class="px-4 py-3 text-left">Status</th>
                <th class="px-4 py-3 text-left">Ações</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-800">
              {#each unidades as u}
                <tr class="hover:bg-slate-800/40 transition-colors">
                  <td class="px-4 py-3 font-medium text-white">{u.nome}</td>
                  <td class="px-4 py-3">{u.codigo || '—'}</td>
                  <td class="px-4 py-3">{u.cnes || '—'}</td>
                  <td class="px-4 py-3">{u.telefone || '—'}</td>
                  <td class="px-4 py-3">
                    <span class="px-2 py-0.5 rounded-full text-xs font-medium {u.ativo ? 'bg-emerald-500/20 text-emerald-400' : 'bg-red-500/20 text-red-400'}">
                      {u.ativo ? 'Ativa' : 'Inativa'}
                    </span>
                  </td>
                  <td class="px-4 py-3 flex gap-2">
                    <button on:click={() => abrirModalEditar(u)}
                      class="px-3 py-1 rounded bg-blue-600/20 text-blue-400 hover:bg-blue-600/40 text-xs transition-colors">
                      Editar
                    </button>
                    <button on:click={() => toggleAtivo(u)}
                      class="px-3 py-1 rounded text-xs transition-colors {u.ativo ? 'bg-red-600/20 text-red-400 hover:bg-red-600/40' : 'bg-emerald-600/20 text-emerald-400 hover:bg-emerald-600/40'}">
                      {u.ativo ? 'Desativar' : 'Ativar'}
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
      <h2 class="text-white font-semibold text-base">{editando ? 'Editar Unidade' : 'Nova Unidade'}</h2>

      <div class="space-y-3">
        <div>
          <label class="block text-xs text-slate-400 mb-1">Nome *</label>
          <input bind:value={form.nome} class="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
        <div>
          <label class="block text-xs text-slate-400 mb-1">Código</label>
          <input bind:value={form.codigo} class="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
        <div>
          <label class="block text-xs text-slate-400 mb-1">CNES</label>
          <input bind:value={form.cnes} class="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
        <div>
          <label class="block text-xs text-slate-400 mb-1">Telefone</label>
          <input bind:value={form.telefone} class="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
        <div>
          <label class="block text-xs text-slate-400 mb-1">Endereço</label>
          <input bind:value={form.endereco} class="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
      </div>

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
