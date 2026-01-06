<script>
  import { onMount } from 'svelte';
  import { getApi, postApi } from '$lib/api.js';
  import { solicitarIngresso } from '$lib/pactosApi.js';

  let pactos = [];
  let pactosLoading = false;
  let pactosError = '';

  // Formulário: Solicitar ingresso em pacto público
  let selectedPactoId = '';
  let joinMensagem = '';
  let joinFeedback = '';
  let joinLoading = false;

  // Formulário: Cadastro público de município externo
  let mNome = '';
  let mCnes = '';
  let mQueue = '';
  let mBaseUrl = '';
  let mFeedback = '';
  let mLoading = false;

  onMount(async () => {
    await carregarPactosPublicos();
  });

  async function carregarPactosPublicos() {
    pactosLoading = true;
    pactosError = '';
    try {
      const res = await getApi('registry/pactos');
      if (!res.ok) throw new Error('Falha ao carregar pactos públicos');
      pactos = await res.json();
    } catch (e) {
      pactosError = e?.message || 'Erro ao listar pactos';
    } finally {
      pactosLoading = false;
    }
  }

  async function enviarJoinRequest() {
    joinFeedback = '';
    if (!selectedPactoId) {
      joinFeedback = 'Selecione um pacto para solicitar ingresso.';
      return;
    }
    try {
      joinLoading = true;
      const resp = await solicitarIngresso(Number(selectedPactoId), joinMensagem || 'Solicitação de ingresso');
      joinFeedback = `Solicitação enviada. Token: ${resp?.token || 'gerado'}`;
      joinMensagem = '';
    } catch (e) {
      // Se não autenticado, o backend pode exigir login para este endpoint
      if (e?.message && /401|403/.test(e.message)) {
        joinFeedback = 'Requer autenticação para solicitar ingresso.';
      } else {
        joinFeedback = e?.message || 'Falha ao enviar solicitação';
      }
    } finally {
      joinLoading = false;
    }
  }

  async function cadastrarMunicipioExterno() {
    mFeedback = '';
    if (!mNome || !mCnes || !mQueue) {
      mFeedback = 'Preencha Nome, CNES e Nome da Fila.';
      return;
    }
    try {
      mLoading = true;
      const payload = {
        nome: mNome,
        cnes: mCnes,
        rabbitQueueName: mQueue,
        baseUrl: mBaseUrl || null
      };
      const res = await postApi('registry/municipios/register-public', payload);
      if (!res.ok) throw new Error('Falha no cadastro público');
      const data = await res.json();
      mFeedback = `Município cadastrado: ${data?.nome || mNome}.`;
      mNome = mCnes = mQueue = mBaseUrl = '';
    } catch (e) {
      mFeedback = e?.message || 'Erro ao cadastrar município';
    } finally {
      mLoading = false;
    }
  }
</script>

<svelte:head>
  <title>Integração | SIRG</title>
</svelte:head>

<header class="bg-gray-800 shadow-lg flex items-center justify-between h-20 px-6 md:px-10">
  <div class="flex items-center space-x-4">
    <a href="/"><img src="/images/logo.png" alt="SIRG Logo" class="h-20 w-auto" /></a>
    <p class="text-white text-2xl font-bold tracking-wide">SIRG</p>
  </div>
  <nav>
    <ul class="list-none flex flex-wrap gap-4 md:gap-6">
      <li class="text-white px-4 py-2 rounded-full cursor-pointer hover:bg-gray-700 transition text-sm md:text-lg">
        <a href="/">Início</a>
      </li>
      <li class="hidden md:block bg-emerald-600 text-white px-4 py-2 rounded-full cursor-pointer hover:bg-emerald-700 transition text-sm md:text-lg shadow">
        <a href="/paciente/consultar">Consultar Solicitação</a>
      </li>
      <li class="text-white px-4 py-2 rounded-full cursor-pointer hover:bg-gray-700 transition text-sm md:text-lg">
        <a href="/login">Área Restrita</a>
      </li>
    </ul>
  </nav>
</header>

<main class="bg-gray-50 w-full min-h-[calc(100vh-5rem)] px-4 md:px-6 py-8">
  <!-- Título / Sumário -->
  <section class="w-full max-w-6xl mx-auto">
    <h1 class="text-3xl md:text-4xl font-extrabold text-gray-800">Integração com o SIRG</h1>
    <p class="text-base md:text-lg text-gray-700 mt-2">Arquitetura, objetivos e como integrar via HTTP e RabbitMQ.</p>
  </section>

  <!-- Objetivos e Arquitetura -->
  <section class="w-full max-w-6xl mx-auto mt-6 grid grid-cols-1 md:grid-cols-2 gap-6">
    <div class="bg-white rounded-2xl shadow p-6 md:p-8">
      <h2 class="text-xl md:text-2xl font-bold text-gray-800 mb-3">Objetivos</h2>
      <ul class="list-disc pl-6 space-y-2 text-gray-700">
        <li>Compartilhar capacidade regional por meio de pactos entre municípios.</li>
        <li>Publicar e consumir solicitações de forma segura e rastreável.</li>
        <li>Integrar sistemas externos via HTTP e mensageria RabbitMQ.</li>
        <li>Reduzir tempo de espera e aumentar a eficiência na regulação.</li>
      </ul>
    </div>

    <div class="bg-white rounded-2xl shadow p-6 md:p-8">
      <h2 class="text-xl md:text-2xl font-bold text-gray-800 mb-3">Arquitetura</h2>
      <p class="text-gray-700 mb-4">
        Backend Spring Boot, Frontend SvelteKit e <span class="font-semibold">RabbitMQ</span> para distribuição
        de eventos. Troca via <span class="font-mono">TopicExchange</span> (<span class="font-mono">regional_topic_exchange</span>)
        e filas por município.
      </p>
      <div class="grid grid-cols-1 gap-4">
        <div class="bg-emerald-50 rounded-xl p-4 border border-emerald-100">
          <h3 class="font-semibold text-emerald-800">Filas compartilhadas</h3>
          <ul class="list-disc pl-6 text-emerald-900 mt-2 space-y-1">
            <li><span class="font-mono">encaminhamento.&lt;MUNICIPIO&gt;.pacto.&lt;ID&gt;.nova</span>: novas solicitações publicadas.</li>
            <li><span class="font-mono">evento-claim-aceite.&lt;ORIGEM&gt;.pacto.&lt;ID&gt;</span>: confirmação de consumo.</li>
            <li><span class="font-mono">convite/convite-aceite</span> e <span class="font-mono">ingresso/ingresso-aceite</span>: convites e ingressos.</li>
            <li><span class="font-mono">agendamento-externo</span>: sinalização de agendamentos externos.</li>
          </ul>
        </div>
        <div class="bg-gray-50 rounded-xl p-4 border">
          <h3 class="font-semibold text-gray-800">Configuração local</h3>
          <ul class="list-disc pl-6 text-gray-700 mt-2 space-y-1">
            <li>Queue: <span class="font-mono">app.municipio.queue-name</span>.</li>
            <li>Identificador (routing keys): <span class="font-mono">app.municipio.nome-identificador</span>.</li>
            <li>Exchange central: <span class="font-mono">regional_topic_exchange</span>.</li>
          </ul>
        </div>
      </div>
    </div>
  </section>

  <!-- Integração HTTP -->
  <section id="integracao-http" class="w-full max-w-6xl mx-auto mt-6 bg-white rounded-2xl shadow p-6 md:p-8">
    <h2 class="text-xl md:text-2xl font-bold text-gray-800 mb-6">Integração HTTP</h2>

    <div class="space-y-6">
      <div>
        <h3 class="font-semibold text-gray-800">1) Cadastro público de município</h3>
        <p class="text-gray-700 mb-2">POST <span class="font-mono">/api/registry/municipios/register-public</span></p>
        <pre class="bg-gray-900 text-gray-100 p-4 rounded-xl overflow-auto text-sm"><code>&#123;
  "nome": "Santo Antônio de Jesus",
  "cnes": "0000000",
  "rabbitQueueName": "SAJ_QUEUE",
  "baseUrl": "https://saj.gov.br/sirg"
&#125;</code></pre>
      </div>

      <div>
        <h3 class="font-semibold text-gray-800">2) Listar pactos públicos</h3>
        <p class="text-gray-700 mb-2">GET <span class="font-mono">/api/registry/pactos</span></p>
        <pre class="bg-gray-900 text-gray-100 p-4 rounded-xl overflow-auto text-sm"><code>curl -s http://HOST/api/registry/pactos</code></pre>
      </div>

      <div>
        <h3 class="font-semibold text-gray-800">3) Solicitar ingresso em um pacto</h3>
        <p class="text-gray-700 mb-2">POST <span class="font-mono">/api/registry/pactos/&#123;pactoId&#125;/join-requests</span></p>
        <pre class="bg-gray-900 text-gray-100 p-4 rounded-xl overflow-auto text-sm"><code>&#123;
  "mensagem": "Gostaríamos de participar com a fila da cardiologia."
&#125;</code></pre>
        <p class="text-gray-600 mt-2 text-sm">Observação: a instância chamadora usa seu <em>município local</em> para identificar o solicitante. Pode requerer autenticação.</p>
      </div>

      <div>
        <h3 class="font-semibold text-gray-800">4) Criar pacto (autenticado)</h3>
        <p class="text-gray-700 mb-2">POST <span class="font-mono">/api/pactos</span></p>
        <pre class="bg-gray-900 text-gray-100 p-4 rounded-xl overflow-auto text-sm"><code>&#123;
  "nome": "Pacto Regional de Oftalmologia",
  "descricao": "Compartilhamento de consultas e exames de oftalmologia"
&#125;</code></pre>
      </div>
    </div>
  </section>

  <!-- Integração via Mensageria -->
  <section id="integracao-mensageria" class="w-full max-w-6xl mx-auto mt-6 bg-white rounded-2xl shadow p-6 md:p-8">
    <h2 class="text-xl md:text-2xl font-bold text-gray-800 mb-3">Mensageria (RabbitMQ)</h2>
    <p class="text-gray-700 mb-4">Exchange: <span class="font-mono">regional_topic_exchange</span></p>
    <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
      <div class="bg-gray-50 rounded-xl p-4 border">
        <h3 class="font-semibold text-gray-800">Bindings por município</h3>
        <ul class="list-disc pl-6 text-gray-700 mt-2 space-y-1">
          <li><span class="font-mono">encaminhamento.&lt;MEU_MUNICIPIO&gt;.#</span></li>
          <li><span class="font-mono">convite.&lt;MEU_MUNICIPIO&gt;.#</span> e <span class="font-mono">convite-aceite.&lt;MEU_MUNICIPIO&gt;.#</span></li>
          <li><span class="font-mono">ingresso.&lt;MEU_MUNICIPIO&gt;.#</span> e <span class="font-mono">ingresso-aceite.&lt;MEU_MUNICIPIO&gt;.#</span></li>
          <li><span class="font-mono">evento-claim-aceite.&lt;MEU_MUNICIPIO&gt;.#</span></li>
        </ul>
      </div>
      <div class="bg-emerald-50 rounded-xl p-4 border border-emerald-100">
        <h3 class="font-semibold text-emerald-800">Exemplos de routing key</h3>
        <pre class="bg-emerald-900/90 text-emerald-50 p-3 rounded-lg overflow-auto text-xs"><code>encaminhamento.SAJ.pacto.42.nova
evento-claim-aceite.SAJ.pacto.42
convite.FEIRA_DE_SANTANA.pacto.7
ingresso-aceite.SAJ.pacto.7</code></pre>
        <p class="text-emerald-900 text-sm mt-2">Mensagens trafegam como JSON (ex.: <span class="font-mono">PactoEventoResumoDTO</span>, <span class="font-mono">PactoJoinRequestMensagemDTO</span>).</p>
      </div>
    </div>
  </section>

  <!-- Pactos públicos (visualização e join) -->
  <section id="pactos-publicos" class="w-full max-w-6xl mx-auto mt-6 bg-white rounded-2xl shadow p-6 md:p-8">
    <h2 class="text-xl md:text-2xl font-bold text-gray-800 mb-3">Pactos públicos</h2>
    {#if pactosLoading}
      <p class="text-gray-600">Carregando pactos...</p>
    {:else if pactosError}
      <p class="text-red-600">{pactosError}</p>
    {:else}
      <div class="overflow-x-auto">
        <table class="min-w-full border rounded-xl overflow-hidden">
          <thead class="bg-gray-100 text-left">
            <tr>
              <th class="px-4 py-2">ID</th>
              <th class="px-4 py-2">Nome</th>
              <th class="px-4 py-2">Descrição</th>
            </tr>
          </thead>
          <tbody>
            {#each pactos as p}
              <tr class="border-t">
                <td class="px-4 py-2">{p.id}</td>
                <td class="px-4 py-2">{p.nome}</td>
                <td class="px-4 py-2">{p.descricao}</td>
              </tr>
            {/each}
            {#if !pactos || pactos.length === 0}
              <tr><td colspan="3" class="px-4 py-4 text-gray-500">Nenhum pacto disponível.</td></tr>
            {/if}
          </tbody>
        </table>
      </div>
    {/if}

    <div class="mt-6 grid grid-cols-1 md:grid-cols-3 gap-4 items-end">
      <div>
        <label class="block text-sm text-gray-700 mb-1">Selecionar pacto</label>
        <select class="w-full border rounded-lg px-3 py-2" bind:value={selectedPactoId}>
          <option value="">-- escolha um pacto --</option>
          {#each pactos as p}
            <option value={p.id}>{p.nome} (#{p.id})</option>
          {/each}
        </select>
      </div>
      <div class="md:col-span-2">
        <label class="block text-sm text-gray-700 mb-1">Mensagem (opcional)</label>
        <input class="w-full border rounded-lg px-3 py-2" placeholder="Ex.: Temos disponibilidade em cardiologia" bind:value={joinMensagem} />
      </div>
      <div class="md:col-span-3 flex items-center gap-3">
        <button class="bg-emerald-600 text-white px-5 py-2 rounded-lg hover:bg-emerald-700 disabled:opacity-50" on:click|preventDefault={enviarJoinRequest} disabled={joinLoading}>
          {joinLoading ? 'Enviando...' : 'Solicitar ingresso'}
        </button>
        {#if joinFeedback}
          <span class="text-sm text-gray-700">{joinFeedback}</span>
        {/if}
      </div>
    </div>
  </section>

  <!-- Cadastro público de município externo -->
  <section id="cadastro-externo" class="w-full max-w-6xl mx-auto mt-6 bg-white rounded-2xl shadow p-6 md:p-8">
    <h2 class="text-xl md:text-2xl font-bold text-gray-800 mb-3">Cadastro de Município Externo</h2>
    <p class="text-gray-700 mb-4">Use este formulário para anunciar seu município e facilitar a descoberta por outros pactos.</p>
    <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
      <div>
        <label class="block text-sm text-gray-700 mb-1">Nome do Município</label>
        <input class="w-full border rounded-lg px-3 py-2" bind:value={mNome} placeholder="Ex.: Santo Antônio de Jesus" />
      </div>
      <div>
        <label class="block text-sm text-gray-700 mb-1">CNES</label>
        <input class="w-full border rounded-lg px-3 py-2" bind:value={mCnes} placeholder="0000000" />
      </div>
      <div>
        <label class="block text-sm text-gray-700 mb-1">Nome da Fila (RabbitMQ)</label>
        <input class="w-full border rounded-lg px-3 py-2" bind:value={mQueue} placeholder="EX.: SAJ_QUEUE" />
      </div>
      <div>
        <label class="block text-sm text-gray-700 mb-1">Base URL (opcional)</label>
        <input class="w-full border rounded-lg px-3 py-2" bind:value={mBaseUrl} placeholder="https://meu.municipio.gov.br/sirg" />
      </div>
    </div>
    <div class="mt-4 flex items-center gap-3">
      <button class="bg-emerald-600 text-white px-5 py-2 rounded-lg hover:bg-emerald-700 disabled:opacity-50" on:click|preventDefault={cadastrarMunicipioExterno} disabled={mLoading}>
        {mLoading ? 'Enviando...' : 'Cadastrar município'}
      </button>
      {#if mFeedback}
        <span class="text-sm text-gray-700">{mFeedback}</span>
      {/if}
    </div>
  </section>
</main>

<footer class="bg-gray-900 text-gray-400 text-center py-6 mt-8 px-4">
  <p class="mb-2">Desenvolvido por: Adriano Victor N. Ribeiro & Filipe da Silva Ribeiro</p>
</footer>

