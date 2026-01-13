<script>
    import RoleBasedMenu from "./RoleBasedMenu.svelte";
    import UserMenu from "./UserMenu.svelte";
    export let titleH1 
    export let page

</script>
<div class="flex min-h-screen bg-gray-100">
  <RoleBasedMenu activePage= {page} />
  <div class="flex-1 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">{titleH1}</h1>
      <UserMenu />
    </header>

    <main class="flex">
        <slot></slot>
    </main>
  </div>
</div>