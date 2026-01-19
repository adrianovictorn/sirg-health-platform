<script>
  import { page } from '$app/stores';

  // Não precisamos mais do 'activePage' como prop,
  // podemos ler a URL diretamente da store '$page' do SvelteKit,
  // que é sempre atualizada na navegação.
  
  // A declaração reativa `$: ` garante que `isAgendaOpen`
  // será recalculado sempre que a URL ($page.url.pathname) mudar.
  $: isAgendaOpen = $page.url.pathname.startsWith('/agendas');

  // Variável para o estado manual do toggle (não é mais necessária, mas mantida para o botão)
  // A lógica de toggle agora é implícita pela reatividade.
</script>

<aside class="w-64 bg-gray-800 text-white flex flex-col py-8 shadow-lg">
  <h2 class="text-2xl font-bold text-center mb-8">SIRG</h2>
  <nav class="flex-1 flex flex-col space-y-2 px-6">
    <a href="/dashboard/procedimentos" class="py-2 px-4 rounded transition" class:bg-emerald-700={$page.url.pathname === '/dashboard/procedimentos'} class:hover:bg-emerald-800={$page.url.pathname !== '/dashboard/procedimentos'}>Dashboard</a>
    
    <div>
      <a href="/agendas/cardiologista" class="w-full text-left py-2 px-4 rounded hover:bg-emerald-800 transition flex justify-between items-center">
        <span>Agendas</span>
        <span class="transform transition-transform duration-200" class:rotate-180={isAgendaOpen}>▼</span>
      </a>
      
      {#if isAgendaOpen}
      <div class="pl-4 mt-2 space-y-2">
        <a href="/agendas/cardiologista" class="block py-2 px-4 rounded transition" class:bg-emerald-700={$page.url.pathname === '/agendas/cardiologista'} class:hover:bg-emerald-800={$page.url.pathname !== '/agendas/cardiologista'}>Cardiologista</a>
        <a href="/agendas/doppler" class="block py-2 px-4 rounded transition" class:bg-emerald-700={$page.url.pathname === '/agendas/doppler'} class:hover:bg-emerald-800={$page.url.pathname !== '/agendas/doppler'}>Doppler</a>
        <a href="/agendas/eletrocardiograma" class="block py-2 px-4 rounded transition" class:bg-emerald-700={$page.url.pathname === '/agendas/eletrocardiograma'} class:hover:bg-emerald-800={$page.url.pathname !== '/agendas/eletrocardiograma'}>Eletrocardiograma</a>
        <a href="/agendas/laboratorio" class="block py-2 px-4 rounded transition" class:bg-emerald-700={$page.url.pathname === '/agendas/laboratorio'} class:hover:bg-emerald-800={$page.url.pathname !== '/agendas/laboratorio'}>Laboratório</a>
        <a href="/agendas/ortopedista" class="block py-2 px-4 rounded transition" class:bg-emerald-700={$page.url.pathname === '/agendas/ortopedista'} class:hover:bg-emerald-800={$page.url.pathname !== '/agendas/ortopedista'}>Ortopedista</a>
        <a href="/agendas/pediatra" class="block py-2 px-4 rounded transition" class:bg-emerald-700={$page.url.pathname === '/agendas/pediatra'} class:hover:bg-emerald-800={$page.url.pathname !== '/agendas/pediatra'}>Pediatria</a>
        <a href="/agendas/raio-x" class="block py-2 px-4 rounded transition" class:bg-emerald-700={$page.url.pathname === '/agendas/raio-x'} class:hover:bg-emerald-800={$page.url.pathname !== '/agendas/raio-x'}>Raio X</a>
        <a href="/agendas/ultrasom" class="block py-2 px-4 rounded transition" class:bg-emerald-700={$page.url.pathname === '/agendas/ultrasom'} class:hover:bg-emerald-800={$page.url.pathname !== '/agendas/ultrasom'}>USG</a>
      </div>
      {/if}
    </div>
    <a href="/relatorio/hospital" class="py-2 px-4 rounded transition" class:bg-emerald-700={$page.url.pathname === 'relatorio/hospital'} class:hover:bg-emerald-800={$page.url.pathname !== '/relatorio/hospital'}>Relatório</a>
  </nav>
  <div class="px-6 mt-4 text-sm text-emerald-200">v1.1 • Adriano Victor, Filipe Ribeiro © 2025</div>
</aside>