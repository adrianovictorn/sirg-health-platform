<script>
  import { onMount } from 'svelte';
  import { user } from '$lib/stores/auth.js';
  import UserMenu from '$lib/UserMenu.svelte';
  import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';
  import { listarNaoLidas, listarLidas, marcarComoLida, marcarTodasComoLidas, criarNotificacaoLocal } from '$lib/notificationsApi.js';

  let loading = false;
  let error = '';
  let notifications = [];
  let tab = 'unread'; // 'unread' | 'read'
  // form de criação
  let tipo = 'LOCAL';
  let resumo = '';
  let linkPath = '';
  let payloadText = '';
  const placeholderJson = '{"chave":"valor"}';

  function formatDate(dt) {
    if (!dt) return '-';
    try {
      if (typeof dt === 'string') {
        const d = new Date(dt);
        if (!isNaN(d)) return d.toLocaleString('pt-BR');
      }
      if (Array.isArray(dt)) {
        const [y, mon, day, hh = 0, mm = 0, ss = 0, nanos = 0] = dt;
        const ms = Math.round((nanos || 0) / 1_000_000);
        const d2 = new Date(y, (mon - 1), day, hh, mm, ss, ms);
        return d2.toLocaleString('pt-BR');
      }
      const d3 = new Date(dt);
      if (!isNaN(d3)) return d3.toLocaleString('pt-BR');
    } catch {}
    return String(dt);
  }

  async function carregar() {
    loading = true; error = '';
    try {
      notifications = tab === 'read' ? await listarLidas() : await listarNaoLidas();
    } catch (e) {
      error = e.message || 'Falha ao carregar notificações';
    } finally { loading = false; }
  }

  async function marcarLida(id) {
    try {
      await marcarComoLida(id);
      await carregar();
    } catch (e) { alert(e.message || 'Falha ao marcar como lida'); }
  }

  async function marcarTodas() {
    try {
      await marcarTodasComoLidas();
      await carregar();
    } catch (e) { alert(e.message || 'Falha ao marcar todas'); }
  }

  $: if ($user) {
    // carrega quando autenticar
    carregar();
  }

  $: if (tab) {
    // troca de aba recarrega
    carregar();
  }

  async function enviarNotificacao(e) {
    e?.preventDefault?.();
    try {
      let payloadObj = undefined;
      if (payloadText && payloadText.trim()) {
        try { payloadObj = JSON.parse(payloadText); }
        catch { return alert('Payload deve ser JSON válido'); }
      }
      await criarNotificacaoLocal({ tipo, resumo, linkPath, payload: payloadObj });
      alert('Notificação criada');
      resumo = ''; linkPath = ''; payloadText = '';
      if (tab === 'unread') await carregar();
    } catch (e) {
      alert(e.message || 'Falha ao criar notificação');
    }
  }
</script>

<svelte:head><title>Admin - Notificações</title></svelte:head>

<div class="flex min-h-screen bg-gray-100">
  <RoleBasedMenu activePage="/admin/notificacoes" />
  <div class="flex-1 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Notificações (Admin)</h1>
      {#if $user}<UserMenu />{:else}<div><a href="/login" class="hover:underline">Fazer Login</a></div>{/if}
    </header>

    <main class="flex-1 overflow-x-hidden overflow-y-auto bg-gray-200 p-6">
      <div class="bg-white p-6 rounded-lg shadow-md">
        {#if !$user}
          <div class="text-gray-700">Faça login para visualizar.</div>
          <a href="/login" class="inline-block mt-2 text-emerald-700 underline">Ir para login</a>
        {:else}
          <div class="flex items-center gap-2 mb-4">
            <div class="inline-flex rounded border overflow-hidden mr-3">
              <button class={`px-3 py-2 ${tab==='unread' ? 'bg-emerald-600 text-white' : 'bg-white'}`} on:click={() => tab = 'unread'}>Não lidas</button>
              <button class={`px-3 py-2 ${tab==='read' ? 'bg-emerald-600 text-white' : 'bg-white'}`} on:click={() => tab = 'read'}>Lidas</button>
            </div>
            <button class="px-3 py-2 bg-gray-100 rounded border" on:click={carregar} disabled={loading}>
              {loading ? 'Carregando...' : 'Recarregar'}
            </button>
            {#if tab==='unread'}
            <button class="px-3 py-2 bg-emerald-600 text-white rounded" on:click={marcarTodas} disabled={loading || notifications.length===0}>
              Marcar todas como lidas
            </button>
            {/if}
            <span class="text-sm text-gray-500">{notifications.length} {tab==='unread' ? 'não lida(s)' : 'lida(s)'}</span>
          </div>
          {#if error}
            <div class="text-red-600 mb-3">{error}</div>
          {/if}

          <div class="bg-gray-50 border rounded p-4 mb-6">
            <h3 class="font-semibold mb-2">Enviar notificação local</h3>
            <form on:submit|preventDefault={enviarNotificacao} class="grid grid-cols-1 md:grid-cols-3 gap-3 items-start">
              <div class="flex flex-col">
                <label class="text-sm text-gray-600">Tipo</label>
                <input class="border rounded p-2" bind:value={tipo} />
              </div>
              <div class="flex flex-col md:col-span-2">
                <label class="text-sm text-gray-600">Resumo</label>
                <input class="border rounded p-2" bind:value={resumo} required />
              </div>
              <div class="flex flex-col">
                <label class="text-sm text-gray-600">Link (opcional)</label>
                <input class="border rounded p-2" bind:value={linkPath} placeholder="/alguma/rota" />
              </div>
              <div class="flex flex-col md:col-span-2">
                <label class="text-sm text-gray-600">Payload JSON (opcional)</label>
                <textarea class="border rounded p-2 h-24" bind:value={payloadText} placeholder={placeholderJson}></textarea>
              </div>
              <div class="flex items-end">
                <button class="px-4 py-2 bg-emerald-600 text-white rounded" type="submit">Enviar</button>
              </div>
            </form>
          </div>

          <div class="overflow-x-auto">
            <table class="min-w-full bg-white">
              <thead class="bg-gray-50">
                <tr>
                  <th class="py-2 px-3 text-left">ID</th>
                  <th class="py-2 px-3 text-left">Tipo</th>
                  <th class="py-2 px-3 text-left">Resumo</th>
                  <th class="py-2 px-3 text-left">Criado em</th>
                  <th class="py-2 px-3 text-left">Ações</th>
                </tr>
              </thead>
              <tbody class="text-gray-700">
                {#if notifications.length === 0}
                  <tr>
                    <td class="py-4 px-3 text-gray-500" colspan="5">Sem notificações {tab==='unread' ? 'não lidas' : 'lidas'}.</td>
                  </tr>
                {/if}
                {#each notifications as n (n.id)}
                  <tr class="border-b">
                    <td class="py-2 px-3">{n.id}</td>
                    <td class="py-2 px-3">{n.tipo}</td>
                    <td class="py-2 px-3">{n.resumo}</td>
                    <td class="py-2 px-3">{formatDate(n.createdAt)}</td>
                    <td class="py-2 px-3">
                      <div class="flex items-center gap-2">
                        {#if n.linkPath}
                          <a href={n.linkPath} class="text-emerald-700 hover:underline text-sm" target="_self">Abrir</a>
                        {/if}
                        {#if tab==='unread'}
                          <button class="text-sm text-gray-700 underline" on:click={() => marcarLida(n.id)}>Marcar como lida</button>
                        {/if}
                      </div>
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
