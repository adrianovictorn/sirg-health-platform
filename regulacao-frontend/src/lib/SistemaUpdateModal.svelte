<script>
  import { marcarComoLida } from '$lib/notificationsApi.js';

  export let notifications = [];
  export let onDismiss = () => {};

  let index = 0;
  $: notif = notifications[index];
  $: payload = (() => {
    if (!notif?.payload) return null;
    try { return typeof notif.payload === 'string' ? JSON.parse(notif.payload) : notif.payload; }
    catch { return null; }
  })();

  async function entendi() {
    if (notif) {
      try { await marcarComoLida(notif.id); } catch {}
    }
    if (index < notifications.length - 1) {
      index++;
    } else {
      onDismiss();
    }
  }
</script>

{#if notif}
  <div class="fixed inset-0 bg-black/30 backdrop-blur-sm z-50 flex items-center justify-center p-4">
    <div class="bg-white/20 backdrop-blur-md border border-white/30 rounded-xl shadow-2xl max-w-md w-full overflow-hidden">
      <div class="bg-emerald-700/80 px-6 py-4 flex items-center gap-3">
        <svg class="w-6 h-6 text-white flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
        <h2 class="text-white font-semibold text-lg">Atualização do Sistema</h2>
        {#if payload?.versao}
          <span class="ml-auto text-emerald-200 text-sm font-mono">{payload.versao}</span>
        {/if}
      </div>

      <div class="px-6 py-5">
        <p class="text-white font-medium text-base">{notif.resumo}</p>

        {#if payload?.descricao}
          <p class="mt-3 text-white/80 text-sm whitespace-pre-line leading-relaxed">{payload.descricao}</p>
        {/if}

        {#if notifications.length > 1}
          <p class="mt-4 text-xs text-white/60">{index + 1} de {notifications.length} atualizações</p>
        {/if}
      </div>

      <div class="px-6 pb-5 flex justify-end">
        <button
          on:click={entendi}
          class="px-6 py-2 bg-emerald-700 text-white rounded-lg font-medium hover:bg-emerald-800 transition-colors"
        >
          Entendi
        </button>
      </div>
    </div>
  </div>
{/if}
