<script>
  import { onMount } from 'svelte';

  export let activePage = '';

  let abertoMobile = false;
  let open = '';

  // Booleans derivados (reativos)
  $: isSolicitacaoOpen   = open === 'solicitacao';
  $: isPainelDeGestao    = open === 'gestao';
  $: isPainelAdminOpen   = open === 'admin';
  $: isFilaCompartilhada = open === 'filas';

  // Alterna abrindo UM e fechando os demais
  const toggle = (key) => {
    open = open === key ? '' : key;
  };

  // Classes dinâmicas dos links
  const linkClasses = (path) =>
    `py-2 px-4 rounded transition block ${
      activePage === path ? 'bg-emerald-700' : 'hover:bg-emerald-800'
    }`;


    function abrirMenuMobile(){
      abertoMobile !== abertoMobile 
    }

  // Decide qual seção deve estar aberta com base na rota ativa
  onMount(() => {
    if (['/cadastrar', '/exames'].includes(activePage)) {
      open = 'solicitacao';
    } else if (
      ['/cadastrar/cid', '/listar/cid', '/cadastrar/especialidade', '/cadastrar/grupo-relatorio','/cadastrar/cidade','/cadastrar/cidade/local-agendamento'].includes(activePage)
    ) {
      open = 'gestao';
    } else if (
      ['/admin/cadastrar-usuario', '/admin/listar-usuarios', '/admin/pactos',
       '/admin/municipios', '/admin/notificacoes', '/'].includes(activePage)
    ) {
      open = 'admin';
    } else if (['/filas/minhas', '/filas/compartilhadas'].includes(activePage)) {
      open = 'filas';
    } else {
      open = ''; // nenhum aberto
    }
  });
</script>


<div
  class={`md:hidden fixed top-0 left-0 z-10 h-screen flex items-center shadow-2xl transition-all 
    ${abertoMobile ? "w-64" : "w-10"} 
  ${abertoMobile ? "bg-gray-600" : "hover:bg-gray-600" }`}
>
<!-- Solicitação -->
<div class={`flex flex-col  h-full w-full  text-white justify-start ${abertoMobile ? "block" : "hidden"} gap-2 ml-2` }>
        <h2 class="text-2xl font-bold mb-8 text-center mt-15">SIRG</h2>
        <div>
          
          <button
            on:click={() => toggle('solicitacao')}
            class="w-full text-left py-2 px-4 rounded hover:bg-emerald-800 transition flex justify-between items-center"
          >
            <span>Solicitação</span>
            <svg class="w-4 h-4 transform transition-transform duration-200" class:rotate-180={isSolicitacaoOpen}
              viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
              <path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 0 1 1.06.02L10 10.94l3.71-3.71a.75.75 0 1 1 1.06 1.06l-4.24 4.25a.75.75 0 0 1-1.06 0L5.21 8.29a.75.75 0 0 1 .02-1.08z" clip-rule="evenodd"/>
            </svg>
          </button>
          {#if isSolicitacaoOpen}
            <div class="pl-4 mt-2 space-y-2">
              <a href="/cadastrar" class={linkClasses('/cadastrar')}>Cadastro de Consulta</a>
              <a href="/exames" class={linkClasses('/exames')}>Exame/Procedimento</a>
            </div>
          {/if}
        </div>
        <a href="/indicadores" class={linkClasses('/indicadores')}>Indicadores</a>
        <a href="/agendar" class={linkClasses('/agendar')}>Agendamento</a>
        <a href="/dashboard/procedimentos/data" class={linkClasses('/dashboard/procedimentos/data')}>Agenda do Dia</a>
        <a href="/paciente" class={linkClasses('/paciente')}>Paciente</a>
        <a href="/relatorio" class={linkClasses('/relatorio')}>Relatórios</a>
        <!-- Painel Gerencial -->
        <div>
          <button
            on:click={() => toggle('gestao')}
            class="w-full text-left py-2 px-4 rounded hover:bg-emerald-800 transition flex justify-between items-center"
          >
            <span>Painel Gerencial</span>
            <svg class="w-4 h-4 transform transition-transform duration-200" class:rotate-180={isPainelDeGestao}
              viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
              <path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 0 1 1.06.02L10 10.94l3.71-3.71a.75.75 0 1 1 1.06 1.06l-4.24 4.25a.75.75 0 0 1-1.06 0L5.21 8.29a.75.75 0 0 1 .02-1.08z" clip-rule="evenodd"/>
            </svg>
          </button>
          {#if isPainelDeGestao}
            <div class="pl-4 mt-2 space-y-2">
              <a href="/cadastrar/cid" class={linkClasses('/cadastrar/cid')}>Cadastrar CID</a>
              <a href="/listar/cid" class={linkClasses('/listar/cid')}>Listar CID</a>
              <a href="/cadastrar/especialidade" class={linkClasses('/cadastrar/especialidade')}>Especialidade</a>
              <a href="/cadastrar/grupo-relatorio" class={linkClasses('/cadastrar/grupo-relatorio')}>Grupo Relatório</a>
              <a href="/cadastrar/cidade" class={linkClasses('/cadastrar/cidade')}>Cadastrar Cidade</a>
              <a href="/cadastrar/cidade/local-agendamento" class={linkClasses('/cadastrar/cidade/local-agendamento')}> Local Agendamento</a>
            </div>
          {/if}
        </div>

        <div>
          <button
            on:click={() => toggle('admin')}
            class="w-full text-left py-2 px-4 rounded hover:bg-emerald-800 transition flex justify-between items-center"
          >
            <span>Painel Admin</span>
            <svg class="w-4 h-4 transform transition-transform duration-200" class:rotate-180={isPainelAdminOpen}
              viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
              <path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 0 1 1.06.02L10 10.94l3.71-3.71a.75.75 0 1 1 1.06 1.06l-4.24 4.25a.75.75 0 0 1-1.06 0L5.21 8.29a.75.75 0 0 1 .02-1.08z" clip-rule="evenodd"/>
            </svg>
          </button>
          {#if isPainelAdminOpen}
            <div class="pl-4 mt-2 space-y-2">
              <a href="/admin/cadastrar-usuario" class={linkClasses('/admin/cadastrar-usuario')}>Cadastrar Usuário</a>
              <a href="/admin/listar-usuarios" class={linkClasses('/admin/listar-usuarios')}>Listar Usuário</a>
              <a href="/admin/pactos" class={linkClasses('/admin/pactos')}>Pactos</a>
              <a href="/admin/municipios" class={linkClasses('/admin/municipios')}>Registrar Município</a>
              <a href="/admin/notificacoes" class={linkClasses('/admin/notificacoes')}>Notificações</a>
            </div>
          {/if}
        </div>

        <div>
              <button
                on:click={() => toggle('filas')}
                class="w-full text-left py-2 px-4 rounded hover:bg-emerald-800 transition flex justify-between items-center"
              >
                <span>Filas Compartilhadas</span>
                <svg class="w-4 h-4 transform transition-transform duration-200" class:rotate-180={isFilaCompartilhada}
                  viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                  <path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 0 1 1.06.02L10 10.94l3.71-3.71a.75.75 0 1 1 1.06 1.06l-4.24 4.25a.75.75 0 0 1-1.06 0L5.21 8.29a.75.75 0 0 1 .02-1.08z" clip-rule="evenodd"/>
                </svg>
              </button>
              {#if isFilaCompartilhada}
                <div class="pl-4 mt-2 space-y-2">
                  <a href="/filas/compartilhadas" class={linkClasses('/filas/compartilhadas')}>Solicitações Compartilhadas</a>
                </div>
              {/if}
            </div>
         <div class="px-6 mt-4 text-sm text-emerald-200">
          v1.1 • Adriano Victor, Filipe Ribeiro © 2025
        </div>
</div>
      

      

  <button
    type="button"
    class="pointer-events-auto p-2"
    aria-label={abertoMobile ? "fechar menu" : "abrir menu"}
    on:click={() => (abertoMobile = !abertoMobile)}
  >
    <svg
      class={`w-6 h-6 text-gray-800 dark:text-gray-800 transition-transform duration-200 ease-in-out
        ${abertoMobile ? "rotate-180" : "rotate-0"}`}
      aria-hidden="true"
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 24 24"
      fill="none"
    >
      <path
        stroke="currentColor"
        stroke-linecap="round"
        stroke-linejoin="round"
        stroke-width="2"
        d="m9 5 7 7-7 7"
      />
    </svg>

    
  </button>

</div>

<aside class="hidden w-64 min-h-screen bg-gray-800 text-white md:flex flex-col  shadow-lg">
  <img src="/images/logo7.png" alt="" class="h-20 w-15 m-auto mb-6 mt-5  ">
  <nav class="flex-1 flex flex-col space-y-2 px-6">
    <a href="/dashboard" class={linkClasses('/dashboard')}>Dashboard</a>
    <a href="/indicadores" class={linkClasses('/indicadores')}>Indicadores</a>

    <!-- Solicitação -->
    <div>
      <button
        on:click={() => toggle('solicitacao')}
        class="w-full text-left py-2 px-4 rounded hover:bg-emerald-800 transition flex justify-between items-center"
      >
        <span>Solicitação</span>
        <svg class="w-4 h-4 transform transition-transform duration-200" class:rotate-180={isSolicitacaoOpen}
          viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 0 1 1.06.02L10 10.94l3.71-3.71a.75.75 0 1 1 1.06 1.06l-4.24 4.25a.75.75 0 0 1-1.06 0L5.21 8.29a.75.75 0 0 1 .02-1.08z" clip-rule="evenodd"/>
        </svg>
      </button>
      {#if isSolicitacaoOpen}
        <div class="pl-4 mt-2 space-y-2">
          <a href="/cadastrar" class={linkClasses('/cadastrar')}>Cadastro de Consulta</a>
          <a href="/exames" class={linkClasses('/exames')}>Exame/Procedimento</a>
        </div>
      {/if}
    </div>

    <a href="/agendar" class={linkClasses('/agendar')}>Agendamento</a>
    <a href="/dashboard/procedimentos/data" class={linkClasses('/dashboard/procedimentos/data')}>Agenda do Dia</a>
    <a href="/paciente" class={linkClasses('/paciente')}>Paciente</a>
    <a href="/relatorio" class={linkClasses('/relatorio')}>Relatórios</a>

    <!-- Painel Gerencial -->
    <div>
      <button
        on:click={() => toggle('gestao')}
        class="w-full text-left py-2 px-4 rounded hover:bg-emerald-800 transition flex justify-between items-center"
      >
        <span>Painel Gerencial</span>
        <svg class="w-4 h-4 transform transition-transform duration-200" class:rotate-180={isPainelDeGestao}
          viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 0 1 1.06.02L10 10.94l3.71-3.71a.75.75 0 1 1 1.06 1.06l-4.24 4.25a.75.75 0 0 1-1.06 0L5.21 8.29a.75.75 0 0 1 .02-1.08z" clip-rule="evenodd"/>
        </svg>
      </button>
      {#if isPainelDeGestao}
        <div class="pl-4 mt-2 space-y-2">
          <a href="/cadastrar/cid" class={linkClasses('/cadastrar/cid')}>Cadastrar CID</a>
          <a href="/listar/cid" class={linkClasses('/listar/cid')}>Listar CID</a>
          <a href="/cadastrar/especialidade" class={linkClasses('/cadastrar/especialidade')}>Especialidade</a>
          <a href="/cadastrar/grupo-relatorio" class={linkClasses('/cadastrar/grupo-relatorio')}>Grupo Relatório</a>
          <a href="/cadastrar/cidade" class={linkClasses('/cadastrar/cidade')}>Cadastrar Cidade</a>
          <a href="/cadastrar/cidade/local-agendamento" class={linkClasses('/cadastrar/cidade/local-agendamento')}> Local Agendamento</a>
        </div>
      {/if}
    </div>

    <!-- Painel Admin -->
    <div>
      <button
        on:click={() => toggle('admin')}
        class="w-full text-left py-2 px-4 rounded hover:bg-emerald-800 transition flex justify-between items-center"
      >
        <span>Painel Admin</span>
        <svg class="w-4 h-4 transform transition-transform duration-200" class:rotate-180={isPainelAdminOpen}
          viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 0 1 1.06.02L10 10.94l3.71-3.71a.75.75 0 1 1 1.06 1.06l-4.24 4.25a.75.75 0 0 1-1.06 0L5.21 8.29a.75.75 0 0 1 .02-1.08z" clip-rule="evenodd"/>
        </svg>
      </button>
      {#if isPainelAdminOpen}
        <div class="pl-4 mt-2 space-y-2">
          <a href="/admin/cadastrar-usuario" class={linkClasses('/admin/cadastrar-usuario')}>Cadastrar Usuário</a>
          <a href="/admin/listar-usuarios" class={linkClasses('/admin/listar-usuarios')}>Listar Usuário</a>
          <a href="/admin/pactos" class={linkClasses('/admin/pactos')}>Pactos</a>
          <a href="/admin/municipios" class={linkClasses('/admin/municipios')}>Registrar Município</a>
          <a href="/admin/notificacoes" class={linkClasses('/admin/notificacoes')}>Notificações</a>
        </div>
      {/if}
    </div>

    <!-- Filas Compartilhadas -->
    <div>
      <button
        on:click={() => toggle('filas')}
        class="w-full text-left py-2 px-4 rounded hover:bg-emerald-800 transition flex justify-between items-center"
      >
        <span>Filas Compartilhadas</span>
        <svg class="w-4 h-4 transform transition-transform duration-200" class:rotate-180={isFilaCompartilhada}
          viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 0 1 1.06.02L10 10.94l3.71-3.71a.75.75 0 1 1 1.06 1.06l-4.24 4.25a.75.75 0 0 1-1.06 0L5.21 8.29a.75.75 0 0 1 .02-1.08z" clip-rule="evenodd"/>
        </svg>
      </button>
      {#if isFilaCompartilhada}
        <div class="pl-4 mt-2 space-y-2">
          <a href="/filas/compartilhadas" class={linkClasses('/filas/compartilhadas')}>Solicitações Compartilhadas</a>
        </div>
      {/if}
    </div>
  </nav>

  <div class="px-6 mt-4 text-sm text-emerald-200">
    v1.1 • Adriano Victor, Filipe Ribeiro © 2025
  </div>
</aside>
