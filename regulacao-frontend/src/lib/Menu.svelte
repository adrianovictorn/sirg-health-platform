<script>
  import { onMount } from 'svelte';

  export let activePage = '';

  let abertoMobile = false;
  let open = '';

  $: isSolicitacaoOpen   = open === 'solicitacao';
  $: isPainelDeGestao    = open === 'gestao';
  $: isPainelAdminOpen   = open === 'admin';
  $: isFilaCompartilhada = open === 'filas';

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

  onMount(() => {
    if (['/cadastrar', '/exames'].includes(activePage)) {
      open = 'solicitacao';
    } else if (['/cadastrar/cid', '/listar/cid', '/cadastrar/especialidade', '/cadastrar/grupo-relatorio', '/cadastrar/cidade', '/cadastrar/cidade/local-agendamento'].includes(activePage)) {
      open = 'gestao';
    } else if (['/admin/cadastrar-usuario', '/admin/listar-usuarios', '/admin/pactos', '/admin/municipios', '/admin/notificacoes', '/admin/unidades', '/admin/profissionais', '/admin/cotas', '/'].includes(activePage)) {
      open = 'admin';
    } else if (['/filas/minhas', '/filas/compartilhadas'].includes(activePage)) {
      open = 'filas';
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

    <!-- Principal -->
    <p class="px-3 mb-1 text-[10px] font-semibold uppercase tracking-widest text-slate-500">Principal</p>

    <a href="/dashboard" class={link('/dashboard')}>
      <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
        <path stroke-linecap="round" stroke-linejoin="round" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" />
      </svg>
      Dashboard
    </a>

    <a href="/indicadores" class={link('/indicadores')}>
      <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
        <path stroke-linecap="round" stroke-linejoin="round" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
      </svg>
      Indicadores
    </a>

    <a href="/agendar" class={link('/agendar')}>
      <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
        <path stroke-linecap="round" stroke-linejoin="round" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" />
      </svg>
      Agendamento
    </a>

    <a href="/dashboard/procedimentos/data" class={link('/dashboard/procedimentos/data')}>
      <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
        <path stroke-linecap="round" stroke-linejoin="round" d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2M9 5a2 2 0 002 2h2a2 2 0 002-2M9 5a2 2 0 012-2h2a2 2 0 012 2" />
      </svg>
      Agenda do Dia
    </a>

    <a href="/paciente" class={link('/paciente')}>
      <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
        <path stroke-linecap="round" stroke-linejoin="round" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" />
      </svg>
      Pacientes
    </a>

    <a href="/relatorio" class={link('/relatorio')}>
      <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
        <path stroke-linecap="round" stroke-linejoin="round" d="M9 17v-2m3 2v-4m3 4v-6m2 10H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
      </svg>
      Relatórios
    </a>

    <a href="/relatorio/profissional" class={link('/relatorio/profissional')}>
      <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
        <path stroke-linecap="round" stroke-linejoin="round" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
      </svg>
      Solicitações por Profissional
    </a>

    <!-- Solicitação -->
    <div class="pt-3">
      <p class="px-3 mb-1 text-[10px] font-semibold uppercase tracking-widest text-slate-500">Solicitação</p>
      <button on:click={() => toggle('solicitacao')} class={groupBtn('solicitacao')}>
        <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
        </svg>
        <span class="flex-1 text-left">Solicitação</span>
        <svg class="w-3.5 h-3.5 transition-transform duration-200 {isSolicitacaoOpen ? 'rotate-180' : ''}" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
        </svg>
      </button>
      {#if isSolicitacaoOpen}
        <div class="mt-1 ml-4 pl-3 border-l border-slate-700 space-y-1">
          <a href="/cadastrar" class={sublink('/cadastrar')}>
            <span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>
            Cadastro de Consulta
          </a>
          <a href="/exames" class={sublink('/exames')}>
            <span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>
            Exame / Procedimento
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
        <svg class="w-3.5 h-3.5 transition-transform duration-200 {isPainelDeGestao ? 'rotate-180' : ''}" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
        </svg>
      </button>
      {#if isPainelDeGestao}
        <div class="mt-1 ml-4 pl-3 border-l border-slate-700 space-y-1">
          <a href="/cadastrar/cid" class={sublink('/cadastrar/cid')}>
            <span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>
            Cadastrar CID
          </a>
          <a href="/listar/cid" class={sublink('/listar/cid')}>
            <span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>
            Listar CID
          </a>
          <a href="/cadastrar/especialidade" class={sublink('/cadastrar/especialidade')}>
            <span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>
            Especialidade
          </a>
          <a href="/cadastrar/grupo-relatorio" class={sublink('/cadastrar/grupo-relatorio')}>
            <span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>
            Grupo Relatório
          </a>
          <a href="/cadastrar/cidade" class={sublink('/cadastrar/cidade')}>
            <span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>
            Cadastrar Cidade
          </a>
          <a href="/cadastrar/cidade/local-agendamento" class={sublink('/cadastrar/cidade/local-agendamento')}>
            <span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>
            Local de Agendamento
          </a>
        </div>
      {/if}

      <!-- Admin -->
      <button on:click={() => toggle('admin')} class="{groupBtn('admin')} mt-1">
        <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M9 12l2 2 4-4m5.618-4.016A11.955 11.955 0 0112 2.944a11.955 11.955 0 01-8.618 3.04A12.02 12.02 0 003 9c0 5.591 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.042-.133-2.052-.382-3.016z" />
        </svg>
        <span class="flex-1 text-left">Painel Admin</span>
        <svg class="w-3.5 h-3.5 transition-transform duration-200 {isPainelAdminOpen ? 'rotate-180' : ''}" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
        </svg>
      </button>
      {#if isPainelAdminOpen}
        <div class="mt-1 ml-4 pl-3 border-l border-slate-700 space-y-1">
          <a href="/admin/cadastrar-usuario" class={sublink('/admin/cadastrar-usuario')}>
            <span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>
            Cadastrar Usuário
          </a>
          <a href="/admin/listar-usuarios" class={sublink('/admin/listar-usuarios')}>
            <span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>
            Listar Usuários
          </a>
          <a href="/admin/pactos" class={sublink('/admin/pactos')}>
            <span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>
            Pactos
          </a>
          <a href="/admin/municipios" class={sublink('/admin/municipios')}>
            <span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>
            Registrar Município
          </a>
          <a href="/admin/notificacoes" class={sublink('/admin/notificacoes')}>
            <span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>
            Notificações
          </a>
          <a href="/admin/unidades" class={sublink('/admin/unidades')}>
            <span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>
            Unidades
          </a>
          <a href="/admin/profissionais" class={sublink('/admin/profissionais')}>
            <span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>
            Profissionais
          </a>
          <a href="/admin/cotas" class={sublink('/admin/cotas')}>
            <span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>
            Cotas por Unidade
          </a>
        </div>
      {/if}

      <!-- Filas -->
      <button on:click={() => toggle('filas')} class="{groupBtn('filas')} mt-1">
        <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="M4 6h16M4 10h16M4 14h16M4 18h16" />
        </svg>
        <span class="flex-1 text-left">Filas Compartilhadas</span>
        <svg class="w-3.5 h-3.5 transition-transform duration-200 {isFilaCompartilhada ? 'rotate-180' : ''}" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
        </svg>
      </button>
      {#if isFilaCompartilhada}
        <div class="mt-1 ml-4 pl-3 border-l border-slate-700 space-y-1">
          <a href="/filas/compartilhadas" class={sublink('/filas/compartilhadas')}>
            <span class="w-1 h-1 rounded-full bg-slate-500 shrink-0"></span>
            Solicitações Compartilhadas
          </a>
        </div>
      {/if}
    </div>

  </nav>

  <!-- Footer -->
  <div class="px-4 py-4 border-t border-slate-800 flex items-center gap-2">
    <div class="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></div>
    <span class="text-xs text-slate-500">v1.1 · Adriano Victor, Filipe Ribeiro © 2025</span>
  </div>

</aside>

<!-- ── MOBILE SIDEBAR ──────────────────────────────────────────────── -->
<div class="md:hidden fixed top-0 left-0 z-20 h-screen flex shadow-2xl transition-all duration-300 {abertoMobile ? 'w-64' : 'w-10'}">

  <!-- Panel -->
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
      <a href="/indicadores" class={link('/indicadores')}>
        <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" /></svg>
        Indicadores
      </a>
      <a href="/agendar" class={link('/agendar')}>
        <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M8 7V3m8 4V3m-9 8h10M5 21h14a2 2 0 002-2V7a2 2 0 00-2-2H5a2 2 0 00-2 2v12a2 2 0 002 2z" /></svg>
        Agendamento
      </a>
      <a href="/paciente" class={link('/paciente')}>
        <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z" /></svg>
        Pacientes
      </a>
      <a href="/relatorio" class={link('/relatorio')}>
        <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M9 17v-2m3 2v-4m3 4v-6m2 10H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" /></svg>
        Relatórios
      </a>
      <a href="/relatorio/profissional" class={link('/relatorio/profissional')}>
        <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" /></svg>
        Solicitações por Profissional
      </a>

      <button on:click={() => toggle('solicitacao')} class={groupBtn('solicitacao')}>
        <svg class="w-4 h-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" /></svg>
        <span class="flex-1 text-left">Solicitação</span>
        <svg class="w-3.5 h-3.5 transition-transform {isSolicitacaoOpen ? 'rotate-180' : ''}" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5"><path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" /></svg>
      </button>
      {#if isSolicitacaoOpen}
        <div class="ml-4 pl-3 border-l border-slate-700 space-y-1">
          <a href="/cadastrar" class={sublink('/cadastrar')}><span class="w-1 h-1 rounded-full bg-slate-500"></span>Cadastro de Consulta</a>
          <a href="/exames" class={sublink('/exames')}><span class="w-1 h-1 rounded-full bg-slate-500"></span>Exame / Procedimento</a>
        </div>
      {/if}
    </nav>

    <div class="px-4 py-3 border-t border-slate-800">
      <span class="text-xs text-slate-500">v1.1 · © 2025</span>
    </div>
  </div>

  <!-- Toggle button -->
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
