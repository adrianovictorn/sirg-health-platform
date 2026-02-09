<script lang="ts">
    import { getApi } from "$lib/api";
    import { onMount } from "svelte";
    import NumberFlow from "@number-flow/svelte";

    let totalPacientes = $state<number | null>(null)
    let totalAgendado = $state<number | null>(null)
    let totalEspecialidade = $state<number | null>(null)
    let size = $state<number>(20)
    let page = $state<number>(0)
    let termo = $state<string>("")
    let especialidade = $state<string[]>([])
    let headerWhite = $state<boolean>(false)
    let integracaoTop = 0

    async function buscarEspecialidade() {
      const params = new URLSearchParams()
      params.append("page", String(page))
      params.append("size", String(size))
      params.append("nome", termo)

      try {
        const res = await getApi(`/transparencia/buscar/especialidade?${params.toString()}`)
        const data = await res.json()
        especialidade = await data.content
        if(!res.ok){
          return alert("Erro ao receber dados !")
        }
      } catch (error) {
        throw new Error("Erro ao se conectar ao servidor !")
      }
    }

    async function carregarDadosTransparencia() {
      try {
        const[rPac, rAg, rEs] = await Promise.all([
          getApi("/transparencia/total/pacientes"),
          getApi("/transparencia/total/agendamentos"),
          getApi("/transparencia/total/especialidades")
        ])

        const [pacientes, agendamentos, especialidades] = await Promise.all([
          rPac.json(),
          rAg.json(),
          rEs.json()
        ])

        totalPacientes = pacientes
        totalAgendado = agendamentos
        totalEspecialidade = especialidades
      } catch (error) {
        
      }
    }

   

    onMount(() => {
      carregarDadosTransparencia(),
      buscarEspecialidade()
    })
</script>

<svelte:head>
  <title>SIRG</title>
</svelte:head>

<!--Cabeçalho-->

<header class=" bg-transparent   flex items-center justify-between fixed w-full h-20 px-6 md:px-10 " >
  <div class="grid grid-cols-2 justify-center items-center ">
    <img src="/images/logo8.png" alt="SIRG Logo" class=" h-10 md:h-15  " >
    <p class=" text-xl md:text-2xl text-gray-800 font-bold" >SIRG</p>
  </div>
  <nav class="w-[50%] ">
    <ul class="list-none grid grid-cols-1 md:grid-cols-2 gap-2 md:gap-6   ">
      <div class="flex-col hidden md:flex  ">
        <li class="hidden md:block  md:bg-emerald-600 md:text-white  mt-2 md:py-2 md:rounded-full md:cursor-pointer md:hover:bg-emerald-700 md:transition  md:text-lg text-center ">
          <a href="/paciente/consultar">Consultar Solicitação</a>
        </li>
      </div>
      <div class="grid grid-cols-2 gap-3">
        <div class="flex flex-col ">
          <li class="text-gray-800 font-light hover:text-white md:px-2 md:py-4 rounded-full cursor-pointer hover:bg-gray-700 text-center transition text-sm md:text-lg">
              <a href="#integracao" data-link="integracao">Integração</a>
          </li>
        </div>
        <div class="flex flex-col">
          <li class="text-gray-800 font-light hover:text-white md:px-2 md:py-4 rounded-full cursor-pointer hover:bg-gray-700 transition text-center text-sm md:text-lg">
              <a href="/login">Área Restrita</a>
          </li>
        </div>
      </div>
    </ul>
  </nav>
</header>

<!--MAIN-->

<main class="bg-[url('/images/SIRGMOBILE.svg')] lg:bg-[url('/images/SIRG2.svg')] bg-no-repeat bg-cover bg-center  min-h-screen w-full flex justify-center items-center px-4 md:px-6 py-6">
  <section class="flex flex-col md:flex-row w-full max-w-3xl bg-neutral-50 rounded-3xl shadow-2xl overflow-hidden">
    
    <!-- Coluna texto -->
    <div class="flex-1 p-6 md:p-12 flex flex-col justify-center gap-6 text-center md:text-center">
      <h1 class="text-3xl md:text-4xl font-extrabold text-gray-900 mb-4 leading-snug text-center">
        Bem-vindo ao <span class="text-emerald-600">SIRG</span>
      </h1>
      <p class="text-base md:text-lg text-gray-700 leading-relaxed text-justify">
        O <strong>Sistema Integrado de Regulação e Gestão</strong> foi desenvolvido para facilitar o gerenciamento e o acompanhamento de solicitações e agendamentos na área da saúde. 
        Pacientes podem consultar o status de suas solicitações com total transparência.
      </p>
      <div class="flex flex-col md:flex-row justify-center md:justify-center gap-4 mt-6">
        <a href="/paciente/consultar" class="bg-emerald-600 text-white px-6 py-3 md:px-5 md:py-4 rounded-full text-base md:text-lg font-semibold shadow hover:bg-emerald-700 transition">
          Consultar Solicitação
        </a>
        <a href="/integracao" class="text-emerald-600 px-6 py-3 md:px-8 md:py-4 rounded-full text-base md:text-lg font-semibold border border-emerald-600 hover:bg-emerald-50 transition">
          Saiba Mais (Integração)
        </a>
      </div>
    </div>

    <!-- Coluna imagem -->
   
  </section>

</main>

<!--TRANSPARÊNCIA-->

<section class=" flex justify-center">
  <div class="flex flex-col w-full  p-10 items-center justify-center bg-neutral-100 rounded-lg ">
    <h2 class="text-sky-600 text-3xl font-extralight mt-5">Transparência</h2>
    <p class="text-justify mt-3 text-lg">A Prefeitura de Conceição do Almeida garante a transparência na Central de Marcação, disponibilizando indicadores em tempo real que demonstram a eficiência e a equidade no atendimento à população.</p>
  <div class="grid grid-cols-1 lg:grid-cols-3 w-full mt-5 gap-5 ">

    <div class="flex flex-col text-center bg-white rounded-full p-10 shadow-2xl border-gray-800 text-2xl font-extrabold text-orange-500">
      <label for="" class="text-sky-800">Agendamentos</label>
            <NumberFlow value={totalAgendado} />

    </div>

    <div class="flex flex-col text-center bg-white rounded-full p-10 shadow-2xl border-gray-800 text-2xl font-extrabold text-orange-500">
      <label for=""  class="text-sky-800">Especialidades</label>
            <NumberFlow value={totalEspecialidade} />

    </div>
    <div class="flex flex-col text-center bg-white rounded-full p-10 shadow-2xl border-gray-800 text-2xl font-extrabold text-orange-500">
      <label for="" class="text-sky-800" >Pacientes Cadastrados</label>
      <NumberFlow value={totalPacientes} />
    </div>

    
  </div>
  </div>
</section>

<!-- Especialidades -->
<section class="grid grid-cols-1 items-center justify-center">
  <div class="flex flex-col text-center">
    <form action="" class="flex flex-col" onsubmit={() => buscarEspecialidade()}>

      <label for="" class="text-emerald-700 text-3xl font-extralight mt-10 text-center">Consultar Especialidades Disponíveis</label>
      <div class="bg-emerald-500 mt-5 mb-5 rounded-lg text-white p-5 w-[80%] m-auto text-justify flex flex-col">
        <p class="text-orange-700 font-extrabold justify-center items-center mb-2 text-center w-full ">Aviso Importante ! </p>
        <p class="">
          As especialidades disponíveis podem ser consultadas normalmente. No entanto, alguns exames são de caráter particular e não são ofertados pelo SUS.
          Para obter informações detalhadas sobre quais exames são particulares, procure a Central de Regulação.</p>
      </div>
        <div class="grid grid-cols-[0.8fr_20px] items-center justify-center gap-2 px-3 py-2">
          
        <input type="text" bind:value={termo} placeholder="Digite aqui a especialidade ou exame que deseja consultar" class="border-gray-300 rounded-lg" oninput={
          (e) => {
            const input = e.target as HTMLInputElement
            termo = input.value
            buscarEspecialidade()
          }
        }>
        <button type="submit" aria-label="button">
          <svg class="w-6 h-6 text-gray-800 " aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="none" viewBox="0 0 24 24">
            <path stroke="currentColor" stroke-linecap="round" stroke-width="2" d="m21 21-3.5-3.5M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0Z"/>
          </svg>
        </button>
      </div>
    </form>
  </div>
  <div class="flex flex-col w-[80%] m-auto">
    <table>
      <thead>
        <tr>
          <th>Exames/Especialidades Disponíveis</th>
        </tr>
      </thead>
      <tbody>
        {#each especialidade as esp}
          <tr class="border  border-gray-400 ">
            <td class="p-5 ">{esp}</td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
</section>

<!--INTEGRAÇÃO-->

<section id="integracao" data-section="integracao" class="bg-white w-full min-h-[calc(100vh-5rem)] scroll-mt-24  px-4 md:px-6 py-8">
  <!-- Título / Sumário -->
  <section class="w-full max-w-6xl mx-auto">
    <h1 class="text-3xl md:text-4xl font-extralight text-gray-800">Integração com o SIRG</h1>
    <p class="text-base md:text-lg text-gray-700 mt-2">
      Arquitetura, objetivos e como integrar via HTTP e RabbitMQ.
    </p>
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
        de eventos. Troca via <span class="font-mono">TopicExchange</span>
        (<span class="font-mono">regional_topic_exchange</span>) e filas por município.
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
        <h3 class="font-semibold text-gray-800">1) Cadastro público de município (referência)</h3>
        <p class="text-gray-700 mb-2">
          POST <span class="font-mono">/api/registry/municipios/register-public</span>
        </p>
        <pre class="bg-gray-900 text-gray-100 p-4 rounded-xl overflow-auto text-sm"><code>&#123;
  "nome": "Santo Antônio de Jesus",
  "cnes": "0000000",
  "rabbitQueueName": "SAJ_QUEUE",
  "baseUrl": "https://saj.gov.br/sirg"
&#125;</code></pre>
      </div>

      <div>
        <h3 class="font-semibold text-gray-800">2) Listar pactos públicos</h3>
        <p class="text-gray-700 mb-2">
          GET <span class="font-mono">/api/registry/pactos</span>
        </p>
        <pre class="bg-gray-900 text-gray-100 p-4 rounded-xl overflow-auto text-sm"><code>curl -s http://HOST/api/registry/pactos</code></pre>
      </div>

      <div>
        <h3 class="font-semibold text-gray-800">3) Solicitar ingresso em um pacto (referência)</h3>
        <p class="text-gray-700 mb-2">
          POST <span class="font-mono">/api/registry/pactos/&#123;pactoId&#125;/join-requests</span>
        </p>
        <pre class="bg-gray-900 text-gray-100 p-4 rounded-xl overflow-auto text-sm"><code>&#123;
  "mensagem": "Gostaríamos de participar com a fila da cardiologia."
&#125;</code></pre>
        <p class="text-gray-600 mt-2 text-sm">
          Observação: a instância chamadora usa seu <em>município local</em> para identificar o solicitante.
          Pode requerer autenticação.
        </p>
      </div>

      <div>
        <h3 class="font-semibold text-gray-800">4) Criar pacto (autenticado)</h3>
        <p class="text-gray-700 mb-2">
          POST <span class="font-mono">/api/pactos</span>
        </p>
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
    <p class="text-gray-700 mb-4">
      Exchange: <span class="font-mono">regional_topic_exchange</span>
    </p>

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
        <p class="text-emerald-900 text-sm mt-2">
          Mensagens trafegam como JSON (ex.: <span class="font-mono">PactoEventoResumoDTO</span>,
          <span class="font-mono">PactoJoinRequestMensagemDTO</span>).
        </p>
      </div>
    </div>
  </section>

  <!-- Pactos públicos (somente informativo) -->
  <section id="pactos-publicos" class="w-full max-w-6xl mx-auto mt-6 bg-white rounded-2xl shadow p-6 md:p-8">
    <h2 class="text-xl md:text-2xl font-bold text-gray-800 mb-3">Pactos públicos</h2>
    <p class="text-gray-700">
      Pactos públicos podem ser consultados via endpoint de listagem e utilizados como base para integração
      entre municípios. O fluxo de ingresso e aceite pode ser realizado por endpoints dedicados e/ou por eventos
      via mensageria, conforme a política de autenticação e governança adotada.
    </p>

    <div class="mt-4 bg-gray-50 rounded-xl p-4 border">
      <h3 class="font-semibold text-gray-800">Endpoints relacionados (referência)</h3>
      <ul class="list-disc pl-6 text-gray-700 mt-2 space-y-1">
        <li>GET <span class="font-mono">/api/registry/pactos</span> — listar pactos públicos</li>
        <li>POST <span class="font-mono">/api/registry/pactos/&#123;pactoId&#125;/join-requests</span> — solicitar ingresso</li>
        <li>POST <span class="font-mono">/api/pactos</span> — criar pacto (autenticado)</li>
      </ul>
    </div>
  </section>

  <!-- Cadastro público de município externo (somente informativo) -->
  <section id="cadastro-externo" class="w-full max-w-6xl mx-auto mt-6 bg-white rounded-2xl shadow p-6 md:p-8">
    <h2 class="text-xl md:text-2xl font-bold text-gray-800 mb-3">Cadastro de Município Externo (referência)</h2>
    <p class="text-gray-700 mb-4">
      O cadastro público de município externo serve para anunciar dados básicos (como fila/identificador e URL base),
      facilitando a descoberta por pactos e integrações regionais.
    </p>

    <div class="bg-gray-50 rounded-xl p-4 border">
      <p class="text-gray-700 mb-2">
        POST <span class="font-mono">/api/registry/municipios/register-public</span>
      </p>
      <pre class="bg-gray-900 text-gray-100 p-4 rounded-xl overflow-auto text-sm"><code>&#123;
  "nome": "Meu Município",
  "cnes": "0000000",
  "rabbitQueueName": "MEU_MUNICIPIO_QUEUE",
  "baseUrl": "https://meu.municipio.gov.br/sirg"
&#125;</code></pre>
    </div>
  </section>
</section>



<footer class="bg-gray-900 text-gray-400 text-center py-6 mt-8 px-4">
  <p class="mb-2">Desenvolvido por: Adriano Victor N. Ribeiro & Filipe da Silva Ribeiro</p>

  <div class="flex justify-center gap-5">

    <a href="https://github.com/adrianovictorn" class="cursor-pointer" aria-label="github" target="_blank">
      <svg class="w-6 h-6 text-gray-700 dark:text-white" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="currentColor" viewBox="0 0 24 24">
        <path fill-rule="evenodd" d="M12.006 2a9.847 9.847 0 0 0-6.484 2.44 10.32 10.32 0 0 0-3.393 6.17 10.48 10.48 0 0 0 1.317 6.955 10.045 10.045 0 0 0 5.4 4.418c.504.095.683-.223.683-.494 0-.245-.01-1.052-.014-1.908-2.78.62-3.366-1.21-3.366-1.21a2.711 2.711 0 0 0-1.11-1.5c-.907-.637.07-.621.07-.621.317.044.62.163.885.346.266.183.487.426.647.71.135.253.318.476.538.655a2.079 2.079 0 0 0 2.37.196c.045-.52.27-1.006.635-1.37-2.219-.259-4.554-1.138-4.554-5.07a4.022 4.022 0 0 1 1.031-2.75 3.77 3.77 0 0 1 .096-2.713s.839-.275 2.749 1.05a9.26 9.26 0 0 1 5.004 0c1.906-1.325 2.74-1.05 2.74-1.05.37.858.406 1.828.101 2.713a4.017 4.017 0 0 1 1.029 2.75c0 3.939-2.339 4.805-4.564 5.058a2.471 2.471 0 0 1 .679 1.897c0 1.372-.012 2.477-.012 2.814 0 .272.18.592.687.492a10.05 10.05 0 0 0 5.388-4.421 10.473 10.473 0 0 0 1.313-6.948 10.32 10.32 0 0 0-3.39-6.165A9.847 9.847 0 0 0 12.007 2Z" clip-rule="evenodd"/>
      </svg>
    </a>

    <a href="https://linkedin.com/in/adrianovictornribeiro" aria-label="linkendin" target="_blank">
       <svg class="w-6 h-6 text-gray-700 dark:text-white" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="currentColor" viewBox="0 0 24 24">
        <path fill-rule="evenodd" d="M12.51 8.796v1.697a3.738 3.738 0 0 1 3.288-1.684c3.455 0 4.202 2.16 4.202 4.97V19.5h-3.2v-5.072c0-1.21-.244-2.766-2.128-2.766-1.827 0-2.139 1.317-2.139 2.676V19.5h-3.19V8.796h3.168ZM7.2 6.106a1.61 1.61 0 0 1-.988 1.483 1.595 1.595 0 0 1-1.743-.348A1.607 1.607 0 0 1 5.6 4.5a1.601 1.601 0 0 1 1.6 1.606Z" clip-rule="evenodd"/>
        <path d="M7.2 8.809H4V19.5h3.2V8.809Z"/>
      </svg>

    </a>

    <a href="https://www.instagram.com/adrianovictor.dev" aria-label="Instagram" target="_blank">
      <svg class="w-6 h-6 text-gray-700 dark:text-white" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="none" viewBox="0 0 24 24">
        <path fill="currentColor" fill-rule="evenodd" d="M3 8a5 5 0 0 1 5-5h8a5 5 0 0 1 5 5v8a5 5 0 0 1-5 5H8a5 5 0 0 1-5-5V8Zm5-3a3 3 0 0 0-3 3v8a3 3 0 0 0 3 3h8a3 3 0 0 0 3-3V8a3 3 0 0 0-3-3H8Zm7.597 2.214a1 1 0 0 1 1-1h.01a1 1 0 1 1 0 2h-.01a1 1 0 0 1-1-1ZM12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6Zm-5 3a5 5 0 1 1 10 0 5 5 0 0 1-10 0Z" clip-rule="evenodd"/>
      </svg>
    </a>

  </div>

</footer>
