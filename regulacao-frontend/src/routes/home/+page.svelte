<script>
  import { user } from '$lib/stores/auth.js';
  import UserMenu from '$lib/UserMenu.svelte';
  import AdminHome from '$lib/AdminHome.svelte';
  import UserHome from '$lib/UserHome.svelte';
  import RoleBasedMenu from '$lib/RoleBasedMenu.svelte'; // 1. Importe o novo componente
</script>

<svelte:head>
    <title>Dashboard</title>
</svelte:head>

<div class="flex min-h-screen bg-gray-100">
  
  <RoleBasedMenu activePage="" />

  <div class="flex-1 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Painel de Controle</h1>
      {#if $user}
        <UserMenu />
      {:else}
        <div>
          <a href="/login" class="hover:underline">Fazer Login</a>
        </div>
      {/if}
    </header>

    {#if $user}
      {#if $user.role === 'ADMIN'}
        <AdminHome />
      {:else}
        <UserHome />
      {/if}
    {/if}
  </div>
</div>