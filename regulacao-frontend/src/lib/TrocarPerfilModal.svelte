<script>
  import { user, perfisDisponiveis, trocarPerfil } from '$lib/stores/auth.js';
  import { ROTULO_PERFIL, hrefDashboardPorRole } from '$lib/menuConfig.js';

  export let onClose = () => {};

  let trocando = false;
  let erro = '';

  /**
   * Depois da troca a página é recarregada de propósito: boa parte das telas
   * carrega dados no onMount conforme o perfil, e continuar na mesma tela com
   * o perfil novo deixaria em cima dados que o perfil atual talvez nem possa
   * ver. Vai direto para o dashboard do novo perfil (mesmo destino do item
   * "Dashboard" do menu lateral) em vez de recarregar a tela atual.
   */
  async function selecionarPerfil(perfil) {
    if (perfil === $user?.role || trocando) return;
    trocando = true;
    erro = '';
    try {
      await trocarPerfil(perfil);
      window.location.assign(hrefDashboardPorRole(perfil));
    } catch (e) {
      erro = e.message;
      trocando = false;
    }
  }

  function onKeydown(event) {
    if (event.key === 'Escape' && !trocando) onClose();
  }
</script>

<svelte:window on:keydown={onKeydown} />

<div class="fixed inset-0 bg-black/40 backdrop-blur-sm z-50 flex items-center justify-center p-4">
  <div class="bg-white rounded-xl shadow-2xl max-w-md w-full overflow-hidden">
    <div class="bg-emerald-700 px-6 py-4 flex items-center gap-3">
      <svg class="w-6 h-6 text-white flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 7h12m0 0l-4-4m4 4l-4 4M16 17H4m0 0l4 4m-4-4l4-4" />
      </svg>
      <h2 class="text-white font-semibold text-lg">Trocar Perfil</h2>
      <button
        on:click={onClose}
        disabled={trocando}
        class="ml-auto text-white/80 hover:text-white disabled:opacity-50"
        aria-label="Fechar"
      >
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" /></svg>
      </button>
    </div>

    <div class="px-6 py-5">
      <p class="text-sm text-gray-500 mb-4">Selecione o perfil com o qual deseja acessar o sistema.</p>

      {#if erro}
        <p class="text-sm text-red-600 bg-red-50 rounded-lg px-3 py-2 mb-4">{erro}</p>
      {/if}

      <div class="space-y-2">
        {#each $perfisDisponiveis as perfil (perfil)}
          {@const ativo = perfil === $user?.role}
          <button
            type="button"
            disabled={trocando}
            on:click={() => selecionarPerfil(perfil)}
            class="w-full flex items-center justify-between px-4 py-3 rounded-lg border text-left transition-colors disabled:opacity-50 disabled:cursor-not-allowed
                   {ativo ? 'border-emerald-600 bg-emerald-50' : 'border-gray-200 hover:border-emerald-300 hover:bg-gray-50'}"
          >
            <span class="text-sm font-medium {ativo ? 'text-emerald-800' : 'text-gray-700'}">
              {ROTULO_PERFIL[perfil] ?? perfil}
            </span>
            {#if ativo}
              <span class="text-xs font-medium text-emerald-700 flex items-center gap-1">
                <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
                </svg>
                Em uso
              </span>
            {:else if trocando}
              <span class="text-xs text-gray-400">Aguarde...</span>
            {/if}
          </button>
        {/each}
      </div>
    </div>

    <div class="px-6 pb-5 flex justify-end">
      <button
        on:click={onClose}
        disabled={trocando}
        class="px-5 py-2 text-sm text-gray-600 hover:text-gray-800 disabled:opacity-50 transition-colors"
      >
        Cancelar
      </button>
    </div>
  </div>
</div>
