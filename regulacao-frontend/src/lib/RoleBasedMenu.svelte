<script>
  import { onMount } from 'svelte';
  import { user } from '$lib/stores/auth.js';
  import { buildMenuForRole, resolveHref, CHEVRON_DOWN } from '$lib/menuConfig.js';

  export let activePage = '';

  let abertoMobile = false;
  let open = '';

  const toggle = (key) => { open = open === key ? '' : key; };

  $: role = $user?.role ?? null;
  $: sections = buildMenuForRole(role);

  const link = (href) =>
    activePage === href
      ? 'flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium bg-emerald-500/15 text-emerald-400 border-l-2 border-emerald-400 pl-[10px] transition-all'
      : 'flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium text-slate-300 hover:bg-slate-800 hover:text-white border-l-2 border-transparent transition-all';

  const sublink = (href) =>
    activePage === href
      ? 'flex items-center gap-2 px-3 py-1.5 rounded-md text-sm text-emerald-400 bg-emerald-500/10 font-medium transition-all'
      : 'flex items-center gap-2 px-3 py-1.5 rounded-md text-sm text-slate-400 hover:text-white hover:bg-slate-700/50 transition-all';

  const groupBtn = (key) =>
    `w-full flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-all ${
      open === key ? 'text-white bg-slate-800' : 'text-slate-300 hover:bg-slate-800 hover:text-white'
    }`;

  // Abre automaticamente o grupo que contém a página atual, sem listas de rotas hardcoded.
  $: {
    const grupoAtivo = sections
      .flatMap((s) => s.items)
      .find((item) => item.type === 'group' && item.items.some((l) => resolveHref(l, role) === activePage));
    if (grupoAtivo) open = grupoAtivo.key;
  }
</script>

{#if role}
  <!-- ── DESKTOP SIDEBAR ─────────────────────────────────────────────── -->
  <aside class="hidden md:flex w-64 min-h-screen bg-slate-900 flex-col shadow-xl border-r border-slate-800">
    <div class="flex flex-col items-center py-6 px-4 border-b border-slate-800">
      <img src="/images/logo7.png" alt="SIRG" class="h-16 w-auto mb-3 drop-shadow" />
      <span class="text-xs font-semibold tracking-widest text-slate-400 uppercase">Sistema de Regulação</span>
    </div>

    <nav class="flex-1 overflow-y-auto px-3 py-4 space-y-1">
      {#each sections as section}
        <div class="pt-3 first:pt-0">
          <p class="px-3 mb-1 text-[10px] font-semibold uppercase tracking-widest text-slate-500">{section.label}</p>
          {#each section.items as item}
            {#if item.type === 'group'}
              <button on:click={() => toggle(item.key)} class="{groupBtn(item.key)} mt-1">
                <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                  {#each item.icon as d}<path stroke-linecap="round" stroke-linejoin="round" {d} />{/each}
                </svg>
                <span class="flex-1 text-left">{item.label}</span>
                <svg class="w-3.5 h-3.5 transition-transform duration-200 {open === item.key ? 'rotate-180' : ''}" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
                  <path stroke-linecap="round" stroke-linejoin="round" d={CHEVRON_DOWN} />
                </svg>
              </button>
              {#if open === item.key}
                <div class="mt-1 ml-4 pl-3 border-l border-slate-700 space-y-1">
                  {#each item.items as sub}
                    <a href={resolveHref(sub, role)} class={sublink(resolveHref(sub, role))}>
                      <span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>
                      {sub.label}
                    </a>
                  {/each}
                </div>
              {/if}
            {:else}
              <a href={resolveHref(item, role)} class={link(resolveHref(item, role))}>
                <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                  {#each item.icon as d}<path stroke-linecap="round" stroke-linejoin="round" {d} />{/each}
                </svg>
                {item.label}
              </a>
            {/if}
          {/each}
        </div>
      {/each}
    </nav>

    <div class="px-4 py-4 border-t border-slate-800 flex items-center gap-2">
      <div class="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></div>
      <span class="text-xs text-slate-500">Adriano Victor, Filipe Ribeiro © 2025</span>
    </div>
  </aside>

  <!-- ── MOBILE SIDEBAR ──────────────────────────────────────────────── -->
  <div class="md:hidden fixed top-0 left-0 z-20 h-screen flex shadow-2xl transition-all duration-300 {abertoMobile ? 'w-64' : 'w-10'}">
    <div class="flex flex-col h-full bg-slate-900 text-white overflow-hidden transition-all duration-300 {abertoMobile ? 'w-64 opacity-100' : 'w-0 opacity-0'}">
      <div class="flex flex-col items-center py-5 border-b border-slate-800">
        <img src="/images/logo7.png" alt="SIRG" class="h-12 w-auto mb-2" />
        <span class="text-xs font-semibold tracking-widest text-slate-400 uppercase">SIRG</span>
      </div>

      <nav class="flex-1 overflow-y-auto px-3 py-3 space-y-1">
        {#each sections as section}
          <div class="pt-3 first:pt-0">
            <p class="px-3 mb-1 text-[10px] font-semibold uppercase tracking-widest text-slate-500">{section.label}</p>
            {#each section.items as item}
              {#if item.type === 'group'}
                <button on:click={() => toggle(item.key)} class="{groupBtn(item.key)} mt-1">
                  <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                    {#each item.icon as d}<path stroke-linecap="round" stroke-linejoin="round" {d} />{/each}
                  </svg>
                  <span class="flex-1 text-left">{item.label}</span>
                  <svg class="w-3.5 h-3.5 transition-transform {open === item.key ? 'rotate-180' : ''}" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
                    <path stroke-linecap="round" stroke-linejoin="round" d={CHEVRON_DOWN} />
                  </svg>
                </button>
                {#if open === item.key}
                  <div class="ml-4 pl-3 border-l border-slate-700 space-y-1">
                    {#each item.items as sub}
                      <a href={resolveHref(sub, role)} class={sublink(resolveHref(sub, role))}>
                        <span class="w-1 h-1 rounded-full bg-slate-500"></span>
                        {sub.label}
                      </a>
                    {/each}
                  </div>
                {/if}
              {:else}
                <a href={resolveHref(item, role)} class={link(resolveHref(item, role))}>
                  <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                    {#each item.icon as d}<path stroke-linecap="round" stroke-linejoin="round" {d} />{/each}
                  </svg>
                  {item.label}
                </a>
              {/if}
            {/each}
          </div>
        {/each}
      </nav>

      <div class="px-4 py-3 border-t border-slate-800">
        <span class="text-xs text-slate-500">© 2025</span>
      </div>
    </div>

    <button
      type="button"
      on:click={() => (abertoMobile = !abertoMobile)}
      aria-label={abertoMobile ? 'Fechar menu' : 'Abrir menu'}
      class="flex items-center justify-center w-10 h-full bg-slate-900 hover:bg-slate-800 border-r border-slate-800 transition-colors"
    >
      <svg class="w-4 h-4 text-slate-400 transition-transform duration-300 {abertoMobile ? 'rotate-180' : ''}" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
        <path stroke-linecap="round" stroke-linejoin="round" d="m9 5 7 7-7 7" />
      </svg>
    </button>
  </div>
{/if}
