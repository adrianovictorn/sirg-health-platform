<script>
  import { onMount } from 'svelte';
  import { getApi, postApi, putApi, patchApi, deleteByIdApi } from '$lib/api.js';
  import Content from '$lib/Content.svelte';
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

<Content titleH1="Profissionais Solicitantes" page="/admin/profissionais">
  <main class="p-6 space-y-4">
    <div class="flex items-center justify-between gap-4">
      <input
        bind:value={busca}
        on:input={carregarProfissionais}
        placeholder="Buscar por nome..."
        class="w-64 bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-emerald-500"
      />
      <button on:click={abrirModalNovo}
        class="px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-medium transition-colors">
        + Novo Profissional
      </button>
    </div>

    {#if loading}
      <p class="text-gray-500 text-sm">Carregando...</p>
    {:else}
      <div class="overflow-x-auto rounded-xl border border-gray-200 bg-white">
        <table class="w-full text-sm text-gray-700">
          <thead class="bg-gray-50 text-gray-500 uppercase text-xs">
            <tr>
              <th class="px-4 py-3 text-left">Nome</th>
              <th class="px-4 py-3 text-left">Conselho / Registro</th>
              <th class="px-4 py-3 text-left">Especialidade</th>
              <th class="px-4 py-3 text-left">Unidade</th>
              <th class="px-4 py-3 text-left">Status</th>
              <th class="px-4 py-3 text-left">Ações</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-200">
            {#each profissionais as p (p.id)}
              <tr class="hover:bg-gray-50 transition-colors">
                <td class="px-4 py-3 font-medium text-gray-900">{p.nome}</td>
                <td class="px-4 py-3">{p.conselho || '—'} {p.numeroRegistro || ''}</td>
                <td class="px-4 py-3">{p.especialidadeAtuacao || '—'}</td>
                <td class="px-4 py-3">{p.unidadeNome || '—'}</td>
                <td class="px-4 py-3">
                  <span class="px-2 py-0.5 rounded-full text-xs font-medium {p.ativo ? 'bg-emerald-100 text-emerald-700' : 'bg-red-100 text-red-700'}">
                    {p.ativo ? 'Ativo' : 'Inativo'}
                  </span>
                </td>
                <td class="px-4 py-3 flex gap-2">
                  <button on:click={() => abrirModalEditar(p)}
                    class="px-3 py-1 rounded bg-blue-50 text-blue-700 hover:bg-blue-100 text-xs transition-colors">
                    Editar
                  </button>
                  <button on:click={() => toggleAtivo(p)}
                    class="px-3 py-1 rounded text-xs transition-colors {p.ativo ? 'bg-red-50 text-red-700 hover:bg-red-100' : 'bg-emerald-50 text-emerald-700 hover:bg-emerald-100'}">
                    {p.ativo ? 'Desativar' : 'Ativar'}
                  </button>
                  <button on:click={() => deletar(p)}
                    class="px-3 py-1 rounded bg-red-50 text-red-700 hover:bg-red-100 text-xs transition-colors">
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
</Content>

{#if showModal}
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/60">
    <div class="bg-white border border-gray-200 rounded-2xl p-6 w-full max-w-md shadow-xl space-y-4">
      <h2 class="text-gray-900 font-semibold text-base">{editando ? 'Editar Profissional' : 'Novo Profissional'}</h2>

      <div class="space-y-3">
        <div>
          <label class="block text-xs text-gray-500 mb-1">Nome *</label>
          <input bind:value={form.nome} class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
        <div class="grid grid-cols-2 gap-3">
          <div>
            <label class="block text-xs text-gray-500 mb-1">Conselho (CRM, COREN…)</label>
            <input bind:value={form.conselho} class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
          </div>
          <div>
            <label class="block text-xs text-gray-500 mb-1">Nº Registro</label>
            <input bind:value={form.numeroRegistro} class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
          </div>
        </div>
        <div>
          <label class="block text-xs text-gray-500 mb-1">Especialidade de Atuação</label>
          <input bind:value={form.especialidadeAtuacao} class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
        <div>
          <label class="block text-xs text-gray-500 mb-1">Telefone</label>
          <input bind:value={form.telefone} class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
        <div>
          <label class="block text-xs text-gray-500 mb-1">Unidade</label>
          <select bind:value={form.unidadeId} class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500">
            <option value={null}>— Nenhuma —</option>
            {#each unidades as u (u.id)}
              <option value={u.id}>{u.nome}</option>
            {/each}
          </select>
        </div>
      </div>

      <div class="flex justify-end gap-3 pt-2">
        <button on:click={() => showModal = false}
          class="px-4 py-2 rounded-lg bg-gray-100 text-gray-700 text-sm hover:bg-gray-200 transition-colors">
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
