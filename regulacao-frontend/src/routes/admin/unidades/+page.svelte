<script>
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { getApi, patchApi } from '$lib/api.js';
  import Content from '$lib/Content.svelte';
  import { toast } from 'svelte-sonner';

  let unidades = [];
  let loading = true;

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

<Content titleH1="Gestão de Unidades" page="/admin/unidades">
  <main class="p-6 space-y-4">
    <div class="flex justify-end">
      <button on:click={() => goto('/admin/unidades/nova')}
        class="px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-medium transition-colors">
        + Nova Unidade
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
              <th class="px-4 py-3 text-left">Código</th>
              <th class="px-4 py-3 text-left">CNES</th>
              <th class="px-4 py-3 text-left">Telefone</th>
              <th class="px-4 py-3 text-left">Status</th>
              <th class="px-4 py-3 text-left">Ações</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-200">
            {#each unidades as u (u.id)}
              <tr class="hover:bg-gray-50 transition-colors">
                <td class="px-4 py-3 font-medium text-gray-900">{u.nome}</td>
                <td class="px-4 py-3">{u.codigo || '—'}</td>
                <td class="px-4 py-3">{u.cnes || '—'}</td>
                <td class="px-4 py-3">{u.telefone || '—'}</td>
                <td class="px-4 py-3">
                  <span class="px-2 py-0.5 rounded-full text-xs font-medium {u.ativo ? 'bg-emerald-100 text-emerald-700' : 'bg-red-100 text-red-700'}">
                    {u.ativo ? 'Ativa' : 'Inativa'}
                  </span>
                </td>
                <td class="px-4 py-3 flex gap-2">
                  <button on:click={() => goto(`/admin/unidades/${u.id}/editar`)}
                    class="px-3 py-1 rounded bg-blue-50 text-blue-700 hover:bg-blue-100 text-xs transition-colors">
                    Editar
                  </button>
                  <button on:click={() => toggleAtivo(u)}
                    class="px-3 py-1 rounded text-xs transition-colors {u.ativo ? 'bg-red-50 text-red-700 hover:bg-red-100' : 'bg-emerald-50 text-emerald-700 hover:bg-emerald-100'}">
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
</Content>
