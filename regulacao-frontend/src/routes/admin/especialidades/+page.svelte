<script>
  import { onMount } from 'svelte';
  import { getApi, postApi, putApi } from '$lib/api.js';

  let lista = [];
  let grupos = [];
  let loading = false;
  let erro = '';

  let form = { codigo: '', nome: '', categoria: 'ESPECIALIDADE_MEDICA', ativo: true, grupoRelatorioId: null, vagas: 0 };
  let salvando = false;
  let feedback = '';

  async function carregar() {
    loading = true; erro = '';
    try {
      const res = await getApi('catalog/especialidades');
      if (!res.ok) throw new Error('Falha ao carregar catálogo');
      lista = await res.json();
    } catch (e) {
      erro = e?.message || 'Erro ao carregar catálogo';
    } finally {
      loading = false;
    }
  }

  async function carregarGrupos() {
    try {
      const res = await getApi('grupo-relatorio/listar');
      if (!res.ok) throw new Error('Falha ao carregar grupos');
      grupos = await res.json();
    } catch (e) {
      console.warn('Erro ao carregar grupos:', e);
      grupos = [];
    }
  }

  async function salvarNova() {
    salvando = true; feedback = '';
    try {
      const res = await postApi('catalog/especialidades', form);
      if (!res.ok) throw new Error('Falha ao criar especialidade');
      form = { codigo: '', nome: '', categoria: 'ESPECIALIDADE_MEDICA', ativo: true, grupoRelatorioId: null, vagas: 0 };
      await carregar();
      feedback = 'Especialidade criada com sucesso';
    } catch (e) {
      feedback = e?.message || 'Erro ao salvar';
    } finally {
      salvando = false;
    }
  }

  async function alternarAtivo(e) {
    try {
      const payload = { ativo: !e.ativo };
      const res = await putApi(`catalog/especialidades/${e.id}`, payload);
      if (!res.ok) throw new Error('Falha ao atualizar');
      await carregar();
    } catch (err) {
      alert(err?.message || 'Erro ao atualizar');
    }
  }

  onMount(async () => {
    await Promise.all([carregar(), carregarGrupos()]);
  });
</script>

<svelte:head><title>Catálogo de Especialidades</title></svelte:head>

<main class="p-6 max-w-6xl mx-auto">
  <h1 class="text-2xl font-bold text-gray-800 mb-4">Catálogo de Especialidades</h1>

  <section class="bg-white rounded-lg shadow p-4 mb-6">
    <h2 class="text-lg font-semibold text-gray-700 mb-2">Nova Especialidade</h2>
    <div class="grid grid-cols-1 md:grid-cols-4 gap-3">
      <div>
        <label class="text-sm text-gray-700">Código (opcional)</label>
        <input class="w-full border rounded px-3 py-2" bind:value={form.codigo} placeholder="Ex.: CARDIOLOGISTA" />
      </div>
      <div class="md:col-span-2">
        <label class="text-sm text-gray-700">Nome</label>
        <input class="w-full border rounded px-3 py-2" bind:value={form.nome} placeholder="Ex.: Cardiologista" />
      </div>
      <div>
        <label class="text-sm text-gray-700">Categoria</label>
        <select class="w-full border rounded px-3 py-2" bind:value={form.categoria}>
          <option value="ESPECIALIDADE_MEDICA">Especialidade Médica</option>
          <option value="EXAME_OU_PROCEDIMENTO">Exame ou Procedimento</option>
        </select>
      </div>
    </div>
    <div class="grid grid-cols-1 md:grid-cols-4 gap-3 mt-3">
      <div>
        <label class="text-sm text-gray-700">Grupo de Relatório</label>
        <select class="w-full border rounded px-3 py-2" bind:value={form.grupoRelatorioId}>
          <option value="" disabled selected>Selecione um grupo</option>
          {#each grupos as g}
            <option value={g.id}>{g.nome} ({g.codigo})</option>
          {/each}
        </select>
      </div>
      <div>
        <label class="text-sm text-gray-700">Vagas</label>
        <input type="number" class="w-full border rounded px-3 py-2" bind:value={form.vagas} min="0" />
      </div>
    </div>
    <div class="mt-3 flex items-center gap-3">
      <button class="bg-emerald-600 text-white px-4 py-2 rounded hover:bg-emerald-700" on:click|preventDefault={salvarNova} disabled={salvando}>
        {salvando ? 'Salvando...' : 'Salvar'}
      </button>
      {#if feedback}
        <span class="text-sm text-gray-700">{feedback}</span>
      {/if}
    </div>
  </section>

  <section class="bg-white rounded-lg shadow p-4">
    <h2 class="text-lg font-semibold text-gray-700 mb-2">Lista</h2>
    {#if loading}
      <p>Carregando...</p>
    {:else if erro}
      <p class="text-red-600">{erro}</p>
    {:else}
      <div class="overflow-x-auto">
        <table class="min-w-full border">
          <thead class="bg-gray-100">
            <tr>
              <th class="px-3 py-2 text-left">ID</th>
              <th class="px-3 py-2 text-left">Código</th>
              <th class="px-3 py-2 text-left">Nome</th>
              <th class="px-3 py-2 text-left">Categoria</th>
              <th class="px-3 py-2 text-left">Grupo</th>
              <th class="px-3 py-2 text-left">Vagas</th>
              <th class="px-3 py-2 text-left">Ativo</th>
            </tr>
          </thead>
          <tbody>
            {#each lista as e}
              <tr class="border-t">
                <td class="px-3 py-2">{e.id}</td>
                <td class="px-3 py-2 font-mono text-xs">{e.codigo}</td>
                <td class="px-3 py-2">{e.nome}</td>
                <td class="px-3 py-2">{e.categoria}</td>
                <td class="px-3 py-2">{e.grupoRelatorio?.nome ?? '—'}</td>
                <td class="px-3 py-2">{e.vagas ?? 0}</td>
                <td class="px-3 py-2">
                  <label class="inline-flex items-center gap-2">
                    <input type="checkbox" checked={e.ativo} on:change={() => alternarAtivo(e)} />
                    <span class="text-sm">{e.ativo ? 'Ativa' : 'Inativa'}</span>
                  </label>
                </td>
              </tr>
            {/each}
          </tbody>
        </table>
      </div>
    {/if}
  </section>
</main>

