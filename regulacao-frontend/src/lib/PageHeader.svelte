<script>
  import UserMenu from '$lib/UserMenu.svelte';
  export let title = '';
</script>

<header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
  <div class="flex items-center gap-3">
    <span class="w-0.5 h-5 rounded-full bg-emerald-400 shrink-0"></span>
    <h1 class="text-lg font-semibold tracking-tight">{title}</h1>
  </div>
  <UserMenu />
</header>
