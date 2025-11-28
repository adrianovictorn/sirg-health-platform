<script>
  import { onMount } from 'svelte';

  // Props para o componente
  export let activePage = ''; // Recebe a URL da página ativa

  // Controle para o submenu de Solicitação
  let isSolicitacaoOpen = false;
  let isPainelDeGestao = false

  function toggleSolicitacao() {
    isSolicitacaoOpen = !isSolicitacaoOpen;
  }

   function togglePainelGestao(){
    isPainelDeGestao = !isPainelDeGestao;
  }

  // Lógica para manter o submenu aberto se a página ativa for uma de suas filhas
  onMount(() => {
    if (activePage === '/cadastrar' || activePage === '/exames') {
      isSolicitacaoOpen = true;
    }
    if(activePage ==='/cadastrar/cid' || activePage === '/listar/cid'){
      isPainelDeGestao = true;
    }
  });
</script>

<aside class="w-64 bg-gray-800 text-white flex flex-col py-8 shadow-lg min-h-screen">
  <h2 class="text-2xl font-bold text-center mb-8">SIRG</h2>
  <nav class="flex-1 flex flex-col space-y-2 px-6">
    <a href="/dashboard/unidade" class="py-2 px-4 rounded transition" class:bg-emerald-700={activePage === '/dashboard/unidade'} class:hover:bg-emerald-800={activePage !== '/dashboard/unidade'}>Dashboard</a>

    <div>
      <button on:click={toggleSolicitacao} class="w-full text-left py-2 px-4 rounded hover:bg-emerald-800 transition flex justify-between items-center">
        <span>Solicitação</span>
        <span class="transform transition-transform duration-200" class:rotate-180={isSolicitacaoOpen}>▼</span>
      </button>
      {#if isSolicitacaoOpen}
        <div class="pl-4 mt-2 space-y-2">
          <a href="/cadastrar" class="block py-2 px-4 rounded transition" class:bg-emerald-700={activePage === '/cadastrar'} class:hover:bg-emerald-800={activePage !== '/cadastrar'}>Cadastro de Consulta</a>
     
        </div>
      {/if}
    </div>   
    <a href="/paciente" class="py-2 px-4 rounded hover:bg-emerald-800" class:bg-emerald-700={activePage === '/paciente'}>Paciente</a>

      <div>
        <button on:click={togglePainelGestao} class="w-full text-left py-2 px-4 rounded hover:bg-emerald-800 transition flex justify-between items-center">
          <span>Painel Gerencional</span>
          <span class="transform transition-transform duration-200" class:rotate-180={isSolicitacaoOpen}>▼</span>
        </button>
      {#if isPainelDeGestao}
        <div class="pl-4 mt-2 space-y-2">
            <a href="/cadastrar/cid" class=" block py-2 px-4 rounded hover:bg-emerald-800 transition" class:bg-emerald-700={activePage === '/cadastrar/cid'}>Cadastrar CID</a>
            <a href="/listar/cid" class="block py-2 px-4 rounded hover:bg-emerald-800 transition" class:bg-emerald-700={activePage === '/listar/cid'}>Listar CID</a>
        </div>
      {/if}
      </div>
   
  <div class="px-6 mt-auto text-sm text-emerald-200 ">v1.2 • Adriano Victor, Filipe Ribeiro © 2025</div>
</aside>