<script>
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { user, token, profilePicture, refreshProfilePicture } from '$lib/stores/auth.js';
  import { fadeScale } from '$lib/transitions.js'; // 1. Importe a nova transição

  let isOpen = false;
  let notifOpen = false;
  let notifications = [];
  let notifLoading = false;
  let unreadCount = 0;
  let modalDismissed = false;

  $: updateNotifs = notifications.filter(n => n.tipo === 'ATUALIZACAO_SISTEMA');
  $: showUpdateModal = !modalDismissed && updateNotifs.length > 0;
  import { listarNaoLidas, marcarComoLida, marcarTodasComoLidas } from '$lib/notificationsApi.js';
  import SistemaUpdateModal from '$lib/SistemaUpdateModal.svelte';
  let node;

  function logout() {
    isOpen = false;
    token.set(null);
    goto('/login');
  }

  const handleClickOutside = (event) => {
    if (node && !node.contains(event.target)) {
      isOpen = false;
      notifOpen = false;
    }
  };

  let intervalId;
  onMount(() => {
    document.addEventListener('click', handleClickOutside, true);
    // carrega notificações na montagem
    carregarNotificacoes();
    refreshProfilePicture();
    // polling leve a cada 15s para atualizar badge
    intervalId = setInterval(async () => {
      try {
        const list = await listarNaoLidas();
        unreadCount = Array.isArray(list) ? list.length : 0;
        // Só atualiza a lista completa se o dropdown estiver aberto
        if (notifOpen) notifications = list;
      } catch {}
    }, 15000);
    // também atualiza ao focar a janela
    const onFocus = () => carregarNotificacoes();
    window.addEventListener('focus', onFocus);
    return () => {
      document.removeEventListener('click', handleClickOutside, true);
      window.removeEventListener('focus', onFocus);
      if (intervalId) clearInterval(intervalId);
    };
  });

  async function carregarNotificacoes() {
    try {
      notifLoading = true;
      const list = await listarNaoLidas();
      notifications = list;
      unreadCount = Array.isArray(list) ? list.length : 0;
    } catch (e) { console.error(e); }
    finally { notifLoading = false; }
  }
</script>

{#if showUpdateModal}
  <SistemaUpdateModal notifications={updateNotifs} onDismiss={() => (modalDismissed = true)} />
{/if}

{#if $user}
  <div class="relative" bind:this={node}>
    <div class="flex items-center gap-2">
      <!-- Notification bell -->
      <div class="relative">
        <button on:click={() => { notifOpen = !notifOpen; if (notifOpen) carregarNotificacoes(); }} class="p-2 rounded-full hover:bg-emerald-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-emerald-700 focus:ring-white">
          <svg class="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"/></svg>
        </button>
        {#if unreadCount > 0}
          <span class="absolute -top-1 -right-1 bg-red-500 text-white text-xs rounded-full px-2 py-0.5">{unreadCount}</span>
        {/if}
        {#if notifOpen}
          <div in:fadeScale={{ duration: 150, start: 0.95 }} class="absolute right-0 mt-2 w-80 origin-top-right bg-white rounded-md shadow-lg z-10 ring-1 ring-black ring-opacity-5 focus:outline-none">
            <div class="py-2">
              <div class="px-4 pb-2 flex items-center justify-between border-b">
                <span class="text-sm font-medium text-gray-800">Notificações</span>
                <button class="text-xs text-emerald-700 hover:underline" on:click={async()=>{ await marcarTodasComoLidas(); await carregarNotificacoes(); }}>Marcar todas como lidas</button>
              </div>
              {#if notifLoading}
                <div class="p-4 text-sm text-gray-500">Carregando...</div>
              {:else if notifications.length === 0}
                <div class="p-4 text-sm text-gray-500">Sem novas notificações</div>
              {:else}
                <ul class="max-h-80 overflow-auto">
                  {#each notifications as n (n.id)}
                    <li class="px-4 py-3 text-sm border-b">
                      <div class="text-gray-800">{n.resumo}</div>
                      <div class="mt-1">
                        <a href={n.linkPath || '#'} class="text-emerald-700 hover:underline text-xs" on:click={async()=>{ await marcarComoLida(n.id); }}>Ver mais detalhes</a>
                      </div>
                    </li>
                  {/each}
                </ul>
              {/if}
            </div>
          </div>
        {/if}
      </div>

      <button
      on:click={() => (isOpen = !isOpen)}
      class="flex items-center space-x-3 p-1.5 rounded-full hover:bg-emerald-600 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-offset-emerald-700 focus:ring-white transition-all duration-200"
      aria-haspopup="true"
      aria-expanded={isOpen}
    >
      {#if $profilePicture}
        <img src={$profilePicture} alt="Foto de perfil" class="w-9 h-9 rounded-full object-cover ring-1 ring-white/30" />
      {:else}
        <div class="w-9 h-9 rounded-full bg-emerald-50 flex items-center justify-center text-emerald-800 font-semibold ring-1 ring-white/30">
          {($user.nome || 'U').charAt(0).toUpperCase()}
        </div>
      {/if}
      <span class="hidden md:block font-medium text-white pr-1">{$user.nome}</span>
      <svg class="w-5 h-5 text-white/90 transform transition-transform duration-200" class:rotate-180={isOpen} xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
      </svg>
    </button>
    </div>

    {#if isOpen}
      <div
        in:fadeScale={{ duration: 150, start: 0.95 }}
        class="absolute right-0 mt-2 w-56 origin-top-right bg-white rounded-md shadow-lg z-10 ring-1 ring-black ring-opacity-5 focus:outline-none"
      >
        <div class="py-1">
          <div class="px-4 py-3 border-b border-gray-200">
            <p class="text-sm text-gray-500">Logado como</p>
            <p class="text-sm font-medium text-gray-800 truncate">{$user.nome}</p>
          </div>
          <a href="/perfil" class="flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 transition-colors">
            <svg class="w-5 h-5 mr-3 text-gray-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" /></svg>
            Meu Perfil
          </a>
          <a href="/admin/cadastrar-usuario" class="flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 transition-colors">
            <svg class="w-5 h-5 mr-3 text-gray-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" /><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" /></svg>
            Configurações
          </a>
          <div class="border-t border-gray-200"></div>
          <button on:click={logout} class="w-full text-left flex items-center px-4 py-2 text-sm text-gray-700 hover:bg-red-50 hover:text-red-700 transition-colors">
            <svg class="w-5 h-5 mr-3 text-gray-400" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" /></svg>
            Sair
          </button>
        </div>
      </div>
    {/if}
  </div>
{:else}
  <div>
    <a href="/login" class="hover:underline">Fazer Login</a>
  </div>
{/if}
