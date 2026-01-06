<svelte:head>
  <title>SIRG</title>
</svelte:head>

<header class="bg-gray-800 shadow-lg flex items-center justify-between h-20 px-6 md:px-10">
  <div class="flex items-center space-x-4">
    <img src="/images/logo.png" alt="SIRG Logo" class="h-20 w-auto">
    <p class="text-white text-2xl font-bold tracking-wide">SIRG</p>
  </div>
  <nav>
    <ul class="list-none flex flex-wrap gap-4 md:gap-6">
      <li class="hidden md:block  md:bg-emerald-600 md:text-white md:px-4 md:py-2 md:rounded-full md:cursor-pointer md:hover:bg-emerald-700 md:transition  md:text-lg shadow">
        <a href="/paciente/consultar">Consultar Solicitação</a>
      </li>
      <li class="text-white px-4 py-2 rounded-full cursor-pointer hover:bg-gray-700 transition text-sm md:text-lg">
        <a href="/integracao">Integração</a>
      </li>
      <li class="text-white px-4 py-2 rounded-full cursor-pointer hover:bg-gray-700 transition text-sm md:text-lg">
        <a href="/login">Área Restrita</a>
      </li>
    </ul>
  </nav>
</header>

<main class="bg-gray-100 w-full min-h-[calc(100vh-5rem)] flex justify-center items-center px-4 md:px-6 py-6">
  <section class="flex flex-col md:flex-row w-full max-w-6xl bg-white rounded-3xl shadow-2xl overflow-hidden">
    
    <!-- Coluna texto -->
    <div class="flex-1 p-6 md:p-12 flex flex-col justify-center gap-6 text-center md:text-left">
      <h1 class="text-3xl md:text-4xl font-extrabold text-gray-800 mb-4 leading-snug">
        Bem-vindo ao <span class="text-emerald-600">SIRG</span>
      </h1>
      <p class="text-base md:text-lg text-gray-700 leading-relaxed text-justify">
        O <strong>Sistema Integrado de Regulação e Gestão</strong> foi desenvolvido para facilitar o gerenciamento e o acompanhamento de solicitações e agendamentos na área da saúde. 
        Pacientes podem consultar o status de suas solicitações com total transparência.
      </p>
      <div class="flex flex-col md:flex-row justify-center md:justify-start gap-4 mt-6">
        <a href="/paciente/consultar" class="bg-emerald-600 text-white px-6 py-3 md:px-8 md:py-4 rounded-full text-base md:text-lg font-semibold shadow hover:bg-emerald-700 transition">
          Consultar Solicitação
        </a>
        <a href="/integracao" class="text-emerald-600 px-6 py-3 md:px-8 md:py-4 rounded-full text-base md:text-lg font-semibold border border-emerald-600 hover:bg-emerald-50 transition">
          Saiba Mais (Integração)
        </a>
      </div>
    </div>

    <!-- Coluna imagem -->
    <div class="flex-1 p-6 md:p-8 flex items-center justify-center bg-gradient-to-br from-emerald-100 to-emerald-200">
      <img src="/images/person.png" alt="Imagem SIRG" class="h-64 md:h-96 w-auto object-contain">
    </div>
  </section>
</main>

<footer class="bg-gray-900 text-gray-400 text-center py-6 mt-8 px-4">
  <p class="mb-2">Desenvolvido por: Adriano Victor N. Ribeiro & Filipe da Silva Ribeiro</p>
</footer>
