<script>
  import { onMount } from 'svelte';
  import { getApi, postApi, putApi, patchApi, deleteByIdApi } from '$lib/api.js';
  import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';
  import UserMenu from '$lib/UserMenu.svelte';
  import { toast } from 'svelte-sonner';

  let profissionais = [];
  let unidades = [];
  let loading = true;
  let showModal = false;
  let editando = null;
  let busca = '';

  let form = { nome: '', conselho: '', numeroRegistro: '', especialidadeAtuacao: '', telefone: '', unidadeId: null };

  onMount(async () => {
    await Promise.all([carregarProfissionais(), carregarUnidades()]);
  });

  async function carregarProfissionais() {
    loading = true;
    try {
      const url = busca.trim() ? `profissionais/buscar?nome=${encodeURIComponent(busca)}&size=50` : 'profissionais/buscar?size=50';
      const res = await getApi(url);
      const data = await res.json();
      profissionais = data.content ?? [];
    } catch {
      toast.error('Erro ao carregar profissionais.');
    } finally {
      loading = false;
    }
  }

  async function carregarUnidades() {
    try {
      const res = await getApi('unidades/ativas');
      unidades = await res.json();
    } catch {
      // silencioso
    }
  }

  function abrirModalNovo() {
    editando = null;
    form = { nome: '', conselho: '', numeroRegistro: '', especialidadeAtuacao: '', telefone: '', unidadeId: null };
    showModal = true;
  }

  function abrirModalEditar(p) {
    editando = p;
    form = {
      nome: p.nome,
      conselho: p.conselho || '',
      numeroRegistro: p.numeroRegistro || '',
      especialidadeAtuacao: p.especialidadeAtuacao || '',
      telefone: p.telefone || '',
      unidadeId: p.unidadeId || null
    };
    showModal = true;
  }

  async function salvar() {
    if (!form.nome.trim()) { toast.error('O nome é obrigatório.'); return; }
    const payload = { ...form, unidadeId: form.unidadeId ? Number(form.unidadeId) : null };
    try {
      if (editando) {
        await putApi(`profissionais/${editando.id}`, payload);
        toast.success('Profissional atualizado.');
      } else {
        await postApi('profissionais', payload);
        toast.success('Profissional cadastrado.');
      }
      showModal = false;
      await carregarProfissionais();
    } catch {
      toast.error('Erro ao salvar profissional.');
    }
  }

  async function toggleAtivo(p) {
    try {
      await patchApi(`profissionais/${p.id}/status`);
      toast.success(p.ativo ? 'Profissional desativado.' : 'Profissional ativado.');
      await carregarProfissionais();
    } catch {
      toast.error('Erro ao alterar status.');
    }
  }

  async function deletar(p) {
    if (!confirm(`Deseja excluir o profissional "${p.nome}"?`)) return;
    try {
      await deleteByIdApi(`profissionais/${p.id}`);
      toast.success('Profissional excluído.');
      await carregarProfissionais();
    } catch {
      toast.error('Erro ao excluir profissional.');
    }
  }
</script>

<div class="flex min-h-screen bg-slate-950">
  <RoleBasedMenu activePage="/admin/profissionais" />

  <div class="flex-1 flex flex-col">
    <header class="flex items-center justify-between px-6 py-4 border-b border-slate-800 bg-slate-900">
      <h1 class="text-lg font-semibold text-white">Profissionais Solicitantes</h1>
      <UserMenu />
    </header>

    <main class="p-6 space-y-4">
      <div class="flex items-center justify-between gap-4">
        <input
          bind:value={busca}
          on:input={carregarProfissionais}
          placeholder="Buscar por nome..."
          class="w-64 bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-emerald-500"
        />
        <button on:click={abrirModalNovo}
          class="px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-medium transition-colors">
          + Novo Profissional
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
                <th class="px-4 py-3 text-left">Conselho / Registro</th>
                <th class="px-4 py-3 text-left">Especialidade</th>
                <th class="px-4 py-3 text-left">Unidade</th>
                <th class="px-4 py-3 text-left">Status</th>
                <th class="px-4 py-3 text-left">Ações</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-800">
              {#each profissionais as p}
                <tr class="hover:bg-slate-800/40 transition-colors">
                  <td class="px-4 py-3 font-medium text-white">{p.nome}</td>
                  <td class="px-4 py-3">{p.conselho || '—'} {p.numeroRegistro || ''}</td>
                  <td class="px-4 py-3">{p.especialidadeAtuacao || '—'}</td>
                  <td class="px-4 py-3">{p.unidadeNome || '—'}</td>
                  <td class="px-4 py-3">
                    <span class="px-2 py-0.5 rounded-full text-xs font-medium {p.ativo ? 'bg-emerald-500/20 text-emerald-400' : 'bg-red-500/20 text-red-400'}">
                      {p.ativo ? 'Ativo' : 'Inativo'}
                    </span>
                  </td>
                  <td class="px-4 py-3 flex gap-2">
                    <button on:click={() => abrirModalEditar(p)}
                      class="px-3 py-1 rounded bg-blue-600/20 text-blue-400 hover:bg-blue-600/40 text-xs transition-colors">
                      Editar
                    </button>
                    <button on:click={() => toggleAtivo(p)}
                      class="px-3 py-1 rounded text-xs transition-colors {p.ativo ? 'bg-red-600/20 text-red-400 hover:bg-red-600/40' : 'bg-emerald-600/20 text-emerald-400 hover:bg-emerald-600/40'}">
                      {p.ativo ? 'Desativar' : 'Ativar'}
                    </button>
                    <button on:click={() => deletar(p)}
                      class="px-3 py-1 rounded bg-red-600/20 text-red-400 hover:bg-red-600/40 text-xs transition-colors">
                      Excluir
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
      <h2 class="text-white font-semibold text-base">{editando ? 'Editar Profissional' : 'Novo Profissional'}</h2>

      <div class="space-y-3">
        <div>
          <label class="block text-xs text-slate-400 mb-1">Nome *</label>
          <input bind:value={form.nome} class="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block text-xs text-slate-400 mb-1">Conselho (CRM, COREN…)</label>
            <input bind:value={form.conselho} class="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500" />
          </div>
          <div>
            <label class="block text-xs text-slate-400 mb-1">Nº Registro</label>
            <input bind:value={form.numeroRegistro} class="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500" />
          </div>
        </div>
        <div>
          <label class="block text-xs text-slate-400 mb-1">Especialidade de Atuação</label>
          <input bind:value={form.especialidadeAtuacao} class="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
        <div>
          <label class="block text-xs text-slate-400 mb-1">Telefone</label>
          <input bind:value={form.telefone} class="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
        <div>
          <label class="block text-xs text-slate-400 mb-1">Unidade</label>
          <select bind:value={form.unidadeId} class="w-full bg-slate-800 border border-slate-700 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:ring-2 focus:ring-emerald-500">
            <option value={null}>— Nenhuma —</option>
            {#each unidades as u}
              <option value={u.id}>{u.nome}</option>
            {/each}
          </select>
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
