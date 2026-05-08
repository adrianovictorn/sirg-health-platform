<script>
  import { onMount } from 'svelte';

  export let activePage = '';

  let abertoMobile = false;
  let open = '';

  $: isAgendasOpen = open === 'agendas';

  const toggle = (key) => { open = open === key ? '' : key; };

  const link = (path) =>
    activePage === path
      ? 'flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium bg-emerald-500/15 text-emerald-400 border-l-2 border-emerald-400 pl-[10px] transition-all'
      : 'flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium text-slate-300 hover:bg-slate-800 hover:text-white border-l-2 border-transparent transition-all';

  const sublink = (path) =>
    activePage === path
      ? 'flex items-center gap-2 px-3 py-1.5 rounded-md text-sm text-emerald-400 bg-emerald-500/10 font-medium transition-all'
      : 'flex items-center gap-2 px-3 py-1.5 rounded-md text-sm text-slate-400 hover:text-white hover:bg-slate-700/50 transition-all';

  const groupBtn = (key) =>
    `w-full flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-all ${
      open === key ? 'text-white bg-slate-800' : 'text-slate-300 hover:bg-slate-800 hover:text-white'
    }`;

  const agendaRoutes = [
    '/agendas/cardiologista', '/agendas/doppler', '/agendas/eletrocardiograma',
    '/agendas/laboratorio', '/agendas/ortopedista', '/agendas/pediatra',
    '/agendas/raio-x', '/agendas/ultrasom'
  ];

  onMount(() => {
    if (agendaRoutes.includes(activePage) || activePage.startsWith('/agendas')) {
      open = 'agendas';
    }
  });
</script>

<!-- ── DESKTOP SIDEBAR ─────────────────────────────────────────────── -->
<aside class="hidden md:flex w-64 min-h-screen bg-slate-900 flex-col shadow-xl border-r border-slate-800">

  <!-- Logo / Brand -->
  <div class="flex flex-col items-center py-6 px-4 border-b border-slate-800">
    <img src="/images/logo7.png" alt="SIRG" class="h-16 w-auto mb-3 drop-shadow" />
    <span class="text-xs font-semibold tracking-widest text-slate-400 uppercase">Sistema de Regulação</span>
  </div>

  <!-- Nav -->
  <nav class="flex-1 overflow-y-auto px-3 py-4 space-y-1">

    <p class="px-3 mb-1 text-[10px] font-semibold uppercase tracking-widest text-slate-500">Principal</p>

    <a href="/dashboard/procedimentos" class={link('/dashboard/procedimentos')}>
      <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
        <path stroke-linecap="round" stroke-linejoin="round" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
      </svg>
      Dashboard
    </a>

    <a href="/relatorio/hospital" class={link('/relatorio/hospital')}>
      <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
        <path stroke-linecap="round" stroke-linejoin="round" d="M9 17v-2m3 2v-4m3 4v-6m2 10H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
      </svg>
      Relatório
    </a>

    <!-- Agendas -->
    <div class="pt-3">
      <p class="px-3 mb-1 text-[10px] font-semibold uppercase tracking-widest text-slate-500">Agendas</p>
      <button on:click={() => toggle('agendas')} class={groupBtn('agendas')}>
        <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
        </svg>
        <span class="flex-1 text-left">Agendas</span>
        <svg class="w-3.5 h-3.5 transition-transform duration-200 {isAgendasOpen ? 'rotate-180' : ''}" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
        </svg>
      </button>
      {#if isAgendasOpen}
        <div class="mt-1 ml-4 pl-3 border-l border-slate-700 space-y-1">
          <a href="/agendas/cardiologista" class={sublink('/agendas/cardiologista')}><span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>Cardiologista</a>
          <a href="/agendas/doppler" class={sublink('/agendas/doppler')}><span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>Doppler</a>
          <a href="/agendas/eletrocardiograma" class={sublink('/agendas/eletrocardiograma')}><span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>Eletrocardiograma</a>
          <a href="/agendas/laboratorio" class={sublink('/agendas/laboratorio')}><span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>Laboratório</a>
          <a href="/agendas/ortopedista" class={sublink('/agendas/ortopedista')}><span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>Ortopedista</a>
          <a href="/agendas/pediatra" class={sublink('/agendas/pediatra')}><span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>Pediatria</a>
          <a href="/agendas/raio-x" class={sublink('/agendas/raio-x')}><span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>Raio X</a>
          <a href="/agendas/ultrasom" class={sublink('/agendas/ultrasom')}><span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>USG</a>
        </div>
      {/if}
    </div>

  </nav>

  <!-- Footer -->
  <div class="px-4 py-4 border-t border-slate-800 flex items-center gap-2">
    <div class="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></div>
    <span class="text-xs text-slate-500">v1.2 · Adriano Victor, Filipe Ribeiro © 2025</span>
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
      <a href="/dashboard/procedimentos" class={link('/dashboard/procedimentos')}>
        <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" /></svg>
        Dashboard
      </a>
      <a href="/relatorio/hospital" class={link('/relatorio/hospital')}>
        <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M9 17v-2m3 2v-4m3 4v-6m2 10H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" /></svg>
        Relatório
      </a>
      <button on:click={() => toggle('agendas')} class={groupBtn('agendas')}>
        <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" /></svg>
        <span class="flex-1 text-left">Agendas</span>
        <svg class="w-3.5 h-3.5 transition-transform {isAgendasOpen ? 'rotate-180' : ''}" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5"><path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" /></svg>
      </button>
      {#if isAgendasOpen}
        <div class="ml-4 pl-3 border-l border-slate-700 space-y-1">
          <a href="/agendas/cardiologista" class={sublink('/agendas/cardiologista')}><span class="w-1 h-1 rounded-full bg-slate-500"></span>Cardiologista</a>
          <a href="/agendas/doppler" class={sublink('/agendas/doppler')}><span class="w-1 h-1 rounded-full bg-slate-500"></span>Doppler</a>
          <a href="/agendas/raio-x" class={sublink('/agendas/raio-x')}><span class="w-1 h-1 rounded-full bg-slate-500"></span>Raio X</a>
          <a href="/agendas/ultrasom" class={sublink('/agendas/ultrasom')}><span class="w-1 h-1 rounded-full bg-slate-500"></span>USG</a>
        </div>
      {/if}
    </nav>

    <div class="px-4 py-3 border-t border-slate-800">
      <span class="text-xs text-slate-500">v1.2 · © 2025</span>
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
