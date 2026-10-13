<script>
    import { API_BASE_URL } from '$lib/config';

    let termoBusca = ''; // Onde o CPF será digitado
    let resultados = []; // Array que armazenará os objetos de solicitação retornados pela API.
    let mensagemErro = ''; // Exibe mensagens de erro para o usuário.
    let carregando = false; // Flag para indicar que uma busca está em andamento.

    // Função para formatar CPF, garantindo a remoção de caracteres não numéricos
    function formatarCpf(valor) {
        if (!valor) return '';
        
        // CORREÇÃO: Remove tudo que não for dígito
        let valorNumerico = valor.replace(/\D/g, '');

        // Limita o CPF a 11 dígitos
        if (valorNumerico.length > 11) {
            valorNumerico = valorNumerico.substring(0, 11);
        }

        // Aplica a máscara para exibição
        return valorNumerico.replace(/(\d{3})(\d)/, '$1.$2')
                          .replace(/(\d{3})(\d)/, '$1.$2')
                          .replace(/(\d{3})(\d{1,2})$/, '$1-$2');
    }

    async function buscarSolicitacoes() {
        mensagemErro = '';
        resultados = [];
        carregando = true;

        if (!termoBusca) {
            mensagemErro = 'Por favor, insira o seu CPF para realizar a consulta.';
            carregando = false;
            return;
        }

        // CORREÇÃO: Limpa o CPF de qualquer pontuação antes de enviar para a API
        const cpfLimpo = termoBusca.replace(/\D/g, '');

        if (cpfLimpo.length !== 11) {
            mensagemErro = 'CPF inválido. Por favor, digite 11 dígitos.';
            carregando = false;
            return;
        }
        
        const url = `${API_BASE_URL}/api/solicitacoes/public/cpf/${cpfLimpo}`;

        try {
            const response = await fetch(url);

            if (!response.ok) {
                if (response.status === 404) {
                    mensagemErro = 'Nenhuma solicitação encontrada para o CPF informado. Verifique os dados.';
                } else if (response.status === 500) {
                    mensagemErro = 'Erro interno do servidor. Por favor, tente novamente mais tarde.';
                } else {
                    mensagemErro = `Erro na consulta: ${response.status} - ${response.statusText}`;
                }
                resultados = [];
                return;
            }

            const data = await response.json();

            if (data && data.length > 0) {
                resultados = data;
            } else {
                mensagemErro = 'Nenhuma solicitação encontrada para o CPF informado. Verifique os dados.';
                resultados = [];
            }

        } catch (error) {
            console.error('Erro ao buscar solicitações:', error);
            mensagemErro = 'Ocorreu um erro inesperado. Verifique sua conexão ou tente novamente.';
            resultados = [];
        } finally {
            carregando = false;
        }
    }

    function handleCpfInput(event) {
        termoBusca = formatarCpf(event.target.value);
    }
</script>

<svelte:head>
    <title>Consultar</title>
</svelte:head>

<header class="bg-gray-800 shadow-lg flex items-center justify-between h-20 px-6 md:px-10">
  <div class="flex items-center space-x-4">
    <a href="/"><img src="/images/logo.png" alt="SIRG Logo" class="h-20 w-auto"></a>
    <p class="text-white text-2xl font-bold tracking-wide">SIRG</p>
  </div>
  <nav>
    <ul class="list-none flex flex-wrap gap-4 md:gap-6">
      
      <li class="text-white px-4 py-2 rounded-full cursor-pointer hover:bg-gray-700 transition text-sm md:text-lg">
        <a href="/login">Área Restrita</a>
      </li>
    </ul>
  </nav>
</header>

<main class="bg-gray-100 w-full min-h-[calc(100vh-5rem)] flex justify-center items-center px-4 md:px-6 py-6">
  <section class="flex flex-col w-full max-w-6xl bg-white rounded-3xl shadow-2xl overflow-hidden p-6 md:p-10">
    <div class="flex flex-col justify-center gap-6 text-center">
        <h1 class="text-3xl md:text-4xl font-extrabold text-gray-800 mb-2 leading-snug">
            Consulta de <span class="text-emerald-600">Solicitações</span>
        </h1>
        <p class="text-base md:text-lg text-gray-700 leading-relaxed max-w-prose mx-auto mb-6">
            Digite seu CPF para acompanhar o status de suas solicitações e agendamentos no sistema.
        </p>

        <div class="w-full max-w-sm mx-auto mb-8">
            <label for="cpfBusca" class="block text-base font-medium text-gray-700 mb-2 text-left">
                CPF do Paciente (apenas números)
            </label>
            <input
                type="text"
                id="cpfBusca"
                bind:value={termoBusca}
                on:input={handleCpfInput}
                placeholder="Ex: 123.456.789-00"
                class="block w-full px-4 py-3 border border-gray-300 rounded-md shadow-sm text-base focus:ring-emerald-500 focus:border-emerald-500 transition-colors"
                maxlength="14" />
        </div>

        <button
            on:click={buscarSolicitacoes}
            disabled={carregando}
            class="inline-flex items-center justify-center px-8 py-4 border border-transparent text-lg font-semibold rounded-full shadow-lg text-white bg-emerald-600 hover:bg-emerald-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-emerald-500 disabled:opacity-50 disabled:cursor-not-allowed transition duration-200 ease-in-out mx-auto"
        >
            {#if carregando}
                <svg class="animate-spin -ml-1 mr-3 h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                Buscando...
            {:else}
                <svg class="-ml-1 mr-2 h-5 w-5" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                    <path fill-rule="evenodd" d="M9 3.5a5.5 5.5 0 100 11 5.5 5.5 0 000-11zM2 9a7 7 0 1112.452 4.391l3.328 3.329a.75.75 0 11-1.06 1.06l-3.329-3.328A7 7 0 012 9z" clip-rule="evenodd" />
                </svg>
                Consultar
            {/if}
        </button>

        {#if mensagemErro}
            <div class="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded-md relative w-full mt-8 max-w-lg mx-auto" role="alert">
                <strong class="font-bold">Erro:</strong>
                <span class="block sm:inline">{mensagemErro}</span>
            </div>
        {/if}
    </div>

    {#if resultados.length > 0}
        <h2 class="text-xl md:text-2xl font-semibold text-gray-800 mt-10 mb-6 self-start mx-auto w-full max-w-6xl">
            Resultados da Consulta
        </h2>
        
        <div class="hidden md:block w-full bg-white rounded-xl shadow-lg border border-gray-100 overflow-x-auto">
            <table class="min-w-full divide-y divide-gray-200">
                <thead class="bg-gradient-to-r from-emerald-50 to-emerald-100">
                    <tr>
                        <th scope="col" class="px-4 py-3 text-left text-xs font-medium text-gray-600 uppercase tracking-wider">ID</th>
                        <th scope="col" class="px-4 py-3 text-left text-xs font-medium text-gray-600 uppercase tracking-wider">Paciente</th>
                        <th scope="col" class="px-4 py-3 text-left text-xs font-medium text-gray-600 uppercase tracking-wider">USF</th>
                        <th scope="col" class="px-4 py-3 text-left text-xs font-medium text-gray-600 uppercase tracking-wider">Malote</th>
                        <th scope="col" class="px-4 py-3 text-left text-xs font-medium text-gray-600 uppercase tracking-wider">Especialidades/Status</th>
                    </tr>
                </thead>
                <tbody class="bg-white divide-y divide-gray-100">
                    {#each resultados as solicitacao (solicitacao.id)}
                        <tr class="hover:bg-emerald-50 transition duration-100 ease-in-out">
                            <td class="px-4 py-3 whitespace-nowrap text-sm font-medium text-gray-900">X</td>
                            <td class="px-4 py-3 whitespace-nowrap text-sm text-gray-800">{solicitacao.nomePaciente}</td>
                            <td class="px-4 py-3 whitespace-nowrap text-sm text-gray-700">{solicitacao.usfOrigem}</td>
                            <td class="px-4 py-3 whitespace-nowrap text-sm text-gray-700">{solicitacao.dataMalote}</td>
                            <td class="px-4 py-3 text-sm text-gray-600">
                                <ul class="list-disc list-inside space-y-1">
                                    {#each solicitacao.especialidades as especialidade}
                                        <li>
                                            <span class="font-semibold text-gray-800">{especialidade.especialidadeSolicitada}</span>:
                                            <span class="font-medium {especialidade.status === 'AGENDADO' ? 'text-green-600' : especialidade.status === 'AGUARDANDO' ? 'text-yellow-600' : 'text-red-600'}">
                                                {especialidade.status}
                                            </span>
                                            {#if especialidade.status === 'AGENDADO' && especialidade.localAgendado && especialidade.dataAgendada}
                                                <br class="sm:hidden">
                                                <span class="text-xs text-gray-500">
                                                    (Local: {especialidade.localAgendado}, Data: {especialidade.dataAgendada})
                                                </span>
                                            {/if}
                                            <span class="text-xs text-gray-400"> (Prioridade: {especialidade.prioridade})</span>
                                        </li>
                                    {/each}
                                </ul>
                            </td>
                        </tr>
                    {/each}
                </tbody>
            </table>
        </div>

        <div class="md:hidden w-full space-y-4">
            {#each resultados as solicitacao (solicitacao.id)}
                <div class="bg-white rounded-xl shadow-lg border border-gray-100 p-4 relative overflow-hidden transform transition-transform duration-300 hover:scale-[1.02]">
                    <div class="absolute top-0 right-0 bg-emerald-600 text-white text-xs font-semibold px-4 py-1 rounded-bl-xl shadow-md">
                        # {solicitacao.id}
                    </div>

                    <p class="text-xl font-extrabold text-gray-900 mb-2 leading-tight">
                        {solicitacao.nomePaciente}
                    </p>
                    
                    <div class="text-sm text-gray-700 mb-4 space-y-1">
                        <div class="flex justify-between items-center">
                            <span class="font-semibold text-gray-800">USF Origem:</span>
                            <span>{solicitacao.usfOrigem}</span>
                        </div>
                        <div class="flex justify-between items-center">
                            <span class="font-semibold text-gray-800">Data Malote:</span>
                            <span>{solicitacao.dataMalote}</span>
                        </div>
                    </div>

                    <h3 class="text-base font-bold text-emerald-700 border-t border-emerald-100 pt-3 mt-3 mb-2">Especialidades Solicitadas:</h3>
                    <ul class="space-y-3">
                        {#each solicitacao.especialidades as especialidade}
                            <li class="bg-emerald-50 p-3 rounded-lg border border-emerald-100 shadow-sm flex flex-col gap-1">
                                <p class="text-sm font-bold text-gray-900">{especialidade.especialidadeSolicitada}</p>
                                <div class="flex items-center justify-between text-sm">
                                    <span class="font-semibold text-gray-700">Status:</span>
                                    <span class="font-extrabold px-3 py-1 rounded-full text-xs
                                        {especialidade.status === 'AGENDADO' ? 'bg-green-100 text-green-700' : 
                                        especialidade.status === 'AGUARDANDO' ? 'bg-yellow-100 text-yellow-700' : 
                                        'bg-red-100 text-red-700'}">
                                        {especialidade.status}
                                    </span>
                                </div>
                                {#if especialidade.status === 'AGENDADO' && especialidade.localAgendado && especialidade.dataAgendada}
                                    <p class="text-xs text-gray-600">
                                        <span class="font-semibold">Local:</span> {especialidade.localAgendado}
                                    </p>
                                    <p class="text-xs text-gray-600">
                                        <span class="font-semibold">Data:</span> {especialidade.dataAgendada}
                                    </p>
                                {/if}
                                <p class="text-xs text-gray-500">
                                    <span class="font-semibold">Prioridade:</span> {especialidade.prioridade}
                                </p>
                            </li>
                        {/each}
                    </ul>
                </div>
            {/each}
        </div>
    {/if}
</section>
</main>

<footer class="bg-gray-900 text-gray-400 text-center py-6 mt-8 px-4">
  <p class="mb-2">Desenvolvido por: Adriano Victor N. Ribeiro</p>

  </footer>