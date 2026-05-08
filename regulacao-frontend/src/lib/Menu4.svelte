<script>
  import { onMount } from 'svelte';

  export let activePage = '';

  let abertoMobile = false;
  let open = '';

  $: isTransporteOpen = open === 'transporte';
  $: isPainelGestaoOpen = open === 'gestao';

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

  const transporteAgendamentoRoutes = ['/agendar/transporte', '/consultar/transporte'];
  const gestaoRoutes = [
    '/cadastrar/transporte', '/cadastrar/cidade', '/cadastrar/motorista',
    '/cadastrar/paciente', '/cadastrar/cidade/local-agendamento'
  ];

  onMount(() => {
    if (transporteAgendamentoRoutes.includes(activePage)) open = 'transporte';
    else if (gestaoRoutes.includes(activePage)) open = 'gestao';
  });

  $: {
    if (transporteAgendamentoRoutes.includes(activePage)) open = 'transporte';
    else if (gestaoRoutes.includes(activePage)) open = 'gestao';
  }
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

    <a href="/dashboard" class={link('/dashboard')}>
      <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
        <path stroke-linecap="round" stroke-linejoin="round" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
      </svg>
      Dashboard
    </a>

    <!-- Gestão de Transporte -->
    <div class="pt-3">
      <p class="px-3 mb-1 text-[10px] font-semibold uppercase tracking-widest text-slate-500">Transporte</p>
      <button on:click={() => toggle('transporte')} class={groupBtn('transporte')}>
        <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
        </svg>
        <span class="flex-1 text-left">Gestão de Transporte</span>
        <svg class="w-3.5 h-3.5 transition-transform duration-200 {isTransporteOpen ? 'rotate-180' : ''}" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
        </svg>
      </button>
      {#if isTransporteOpen}
        <div class="mt-1 ml-4 pl-3 border-l border-slate-700 space-y-1">
          <a href="/agendar/transporte" class={sublink('/agendar/transporte')}>
            <span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>
            Agendar Transporte
          </a>
          <a href="/consultar/transporte" class={sublink('/consultar/transporte')}>
            <span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>
            Consultar Transporte
          </a>
        </div>
      {/if}
    </div>

    <!-- Gestão -->
    <div class="pt-3">
      <p class="px-3 mb-1 text-[10px] font-semibold uppercase tracking-widest text-slate-500">Gestão</p>
      <button on:click={() => toggle('gestao')} class={groupBtn('gestao')}>
        <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
          <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
        <span class="flex-1 text-left">Painel Gerencial</span>
        <svg class="w-3.5 h-3.5 transition-transform duration-200 {isPainelGestaoOpen ? 'rotate-180' : ''}" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
        </svg>
      </button>
      {#if isPainelGestaoOpen}
        <div class="mt-1 ml-4 pl-3 border-l border-slate-700 space-y-1">
          <a href="/cadastrar/transporte" class={sublink('/cadastrar/transporte')}>
            <span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>
            Cadastrar Transporte
          </a>
          <a href="/cadastrar/cidade" class={sublink('/cadastrar/cidade')}>
            <span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>
            Cadastrar Cidade
          </a>
          <a href="/cadastrar/motorista" class={sublink('/cadastrar/motorista')}>
            <span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>
            Cadastrar Motorista
          </a>
          <a href="/cadastrar/paciente" class={sublink('/cadastrar/paciente')}>
            <span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>
            Cadastrar Paciente
          </a>
          <a href="/cadastrar/cidade/local-agendamento" class={sublink('/cadastrar/cidade/local-agendamento')}>
            <span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>
            Ponto de Parada
          </a>
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
      <a href="/dashboard" class={link('/dashboard')}>
        <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" /></svg>
        Dashboard
      </a>
      <button on:click={() => toggle('transporte')} class={groupBtn('transporte')}>
        <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" /></svg>
        <span class="flex-1 text-left">Gestão de Transporte</span>
        <svg class="w-3.5 h-3.5 transition-transform {isTransporteOpen ? 'rotate-180' : ''}" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5"><path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" /></svg>
      </button>
      {#if isTransporteOpen}
        <div class="ml-4 pl-3 border-l border-slate-700 space-y-1">
          <a href="/agendar/transporte" class={sublink('/agendar/transporte')}><span class="w-1 h-1 rounded-full bg-slate-500"></span>Agendar Transporte</a>
          <a href="/consultar/transporte" class={sublink('/consultar/transporte')}><span class="w-1 h-1 rounded-full bg-slate-500"></span>Consultar Transporte</a>
        </div>
      {/if}
      <button on:click={() => toggle('gestao')} class={groupBtn('gestao')}>
        <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" /><path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" /></svg>
        <span class="flex-1 text-left">Painel Gerencial</span>
        <svg class="w-3.5 h-3.5 transition-transform {isPainelGestaoOpen ? 'rotate-180' : ''}" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5"><path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" /></svg>
      </button>
      {#if isPainelGestaoOpen}
        <div class="ml-4 pl-3 border-l border-slate-700 space-y-1">
          <a href="/cadastrar/transporte" class={sublink('/cadastrar/transporte')}><span class="w-1 h-1 rounded-full bg-slate-500"></span>Cadastrar Transporte</a>
          <a href="/cadastrar/cidade" class={sublink('/cadastrar/cidade')}><span class="w-1 h-1 rounded-full bg-slate-500"></span>Cadastrar Cidade</a>
          <a href="/cadastrar/motorista" class={sublink('/cadastrar/motorista')}><span class="w-1 h-1 rounded-full bg-slate-500"></span>Cadastrar Motorista</a>
          <a href="/cadastrar/paciente" class={sublink('/cadastrar/paciente')}><span class="w-1 h-1 rounded-full bg-slate-500"></span>Cadastrar Paciente</a>
          <a href="/cadastrar/cidade/local-agendamento" class={sublink('/cadastrar/cidade/local-agendamento')}><span class="w-1 h-1 rounded-full bg-slate-500"></span>Ponto de Parada</a>
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
