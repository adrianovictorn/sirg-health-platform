<script lang='ts'>
    import { getApi, postApi } from '$lib/api';
    import UserMenu from '$lib/UserMenu.svelte';
    import { onMount } from 'svelte';
    import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';

    interface EspecialidadeDTO{
      id: number,
      codigo: string,
      nome: string,
    }

    type CheckboxItem = {
      id:number, label:string, value:string, selecionado:boolean
    }

    let labelMap = new Map<string,string>();
    let exames = $state<EspecialidadeDTO[]>([])

    async function carregarExamesDoCatalogo() {
      try {
        const res = await getApi("catalog/especialidades/listar/exames");
        console.log(`RES RECEBIDO EM CATALOGO: ${res}`)
        const data = await res.json()
        console.log(`DATA RECEBIDA EM CATALOGO: ${data}`)

        exames = data
        
        examesDisponiveisParaCheckbox = exames.map((e) => ({
          id: e.id,
          label: e.nome,
          value: e.codigo,
          selecionado: false
        })) ;

      } catch (e) {
        console.warn('Falha ao carregar catálogo de exames', e);
      }
    }

 
  let termoBusca = $state('');
  let examesDisponiveisParaCheckbox = $state<CheckboxItem[]>([])
  let listaDeSolicitacoesParaDropdown = $state<any[]>([]);
  let erroAoCarregar = $state<string | null>(null);
  let isUrgente = $state(false);

  // Lógica do Combobox
  let valorBusca = $state('');
  let comboboxAberto = $state(false);
  let size = $state(10)
  let page = $state(0)

     const examesParaConsulta = $derived(() => examesDisponiveisParaCheckbox.filter(exame => exame.label.toLocaleLowerCase().includes(termoBusca.toLocaleLowerCase())));

  

  function selecionarSolicitacao(solicitacao) {
    if(!solicitacao){
      limparFormularioCompleto()
      return;
    }
    valorBusca = solicitacao.label;
    carregarDadosSolicitacao(solicitacao.value);
    comboboxAberto = false;
  }

  // Estado do formulário
  let idSolicitacao = $state<number | null>(null);
  let nomePaciente = $state('');
  let cpfPaciente = $state('');
  let cns = $state('');
  let datanascimento = $state('');
  let usfOrigem = $state('');
  let dataMalote = $state('');
  let observacoes = $state('');
  let inputsReadonly = $state(false);
  let telefone = $state('');
  let examesDaSolicitacaoAtual = $state<any[]>([]);

  // --- Lógica de Carregamento e Envio ---
//
  async function carregarListaSolicitacoes() {
    try {

      const params = new URLSearchParams()
      params.append("page", String(page))
      params.append("size", String(size))
      params.append("termo", valorBusca)

      const response = await getApi(`solicitacoes/buscar/por/nome/cpf?${params.toString()}`);
      if (!response.ok) {
        throw new Error('Falha ao buscar a lista de solicitações.');
      }
      const data = await response.json();
      listaDeSolicitacoesParaDropdown = data.content.map(s => ({
        value: s.id,
        label: `${s.nomePaciente} - (CPF: ${s.cpfPaciente || 'N/A'})`
      }))

      console.log(listaDeSolicitacoesParaDropdown)
    } catch (e: any) {
      erroAoCarregar = e.message;
    }
  }

  onMount(async () => { await Promise.all([carregarExamesDoCatalogo(), carregarListaSolicitacoes()]); });

  async function carregarDadosSolicitacao(solicitacaoIdParam: string | null) {
    if (!solicitacaoIdParam) {
      limparFormularioCompleto();
      return;
    }
    try {
      const res = await getApi(`solicitacoes/${solicitacaoIdParam}`);
      if (!res.ok) throw new Error('Solicitação não encontrada.');
      
      const s = await res.json();
      idSolicitacao = s.id;
      nomePaciente = s.nomePaciente || '';
      cpfPaciente = s.cpfPaciente || '';
      cns = s.cns || '';
      datanascimento = s.datanascimento ? s.datanascimento.split('T')[0] : '';
      usfOrigem = s.usfOrigem || '';
      dataMalote = s.dataMalote ? s.dataMalote.split('T')[0] : '';
      telefone = s.telefone || '';
      inputsReadonly = true;
      examesDisponiveisParaCheckbox.forEach(ex => ex.selecionado = false);

      examesDaSolicitacaoAtual = s.especialidades?.map((esp: any) => ({
        ...esp,
        label: labelMap.get(esp.especialidadeSolicitada) || (esp.especialidadeSolicitada || '').replaceAll('_',' ')
      })) || [];

    } catch (e: any) {
      alert(`Erro ao carregar detalhes: ${e.message}`);
      limparFormularioCompleto();
    }
  }

  function submeterForm(event: SubmitEvent) {
    event.preventDefault(); // Previne o comportamento padrão do formulário

    const examesSelecionados = examesDisponiveisParaCheckbox.filter(ex => ex.selecionado);
    const prioridadeDaSolicitacao = isUrgente ? 'URGENTE' : 'NORMAL';


    
    if (idSolicitacao) {
      const paraAdicionar = examesSelecionados
        // A linha de filtro foi removida daqui
        .map(sel => ({
            especialidadeId: sel.id,
            status: 'AGUARDANDO',
            prioridade: prioridadeDaSolicitacao
        }));

      if (paraAdicionar.length === 0) {
        alert("Nenhum novo exame selecionado para adicionar.");
        return;
      }
      
      const promises = paraAdicionar.map(itemPayload => 
        postApi(`solicitacoes/${idSolicitacao}/especialidades`, itemPayload)
      );

      Promise.all(promises).then(async responses => {
        const algumaFalhou = responses.some(res => !res.ok);
        if (algumaFalhou) {
            alert(`Erro ao adicionar um ou mais exames.`);
        } else {
            alert('Exames adicionados com sucesso!');
            await carregarDadosSolicitacao(String(idSolicitacao));
        }
      });

    // MODO CREATE
    } else {
      if (examesSelecionados.length === 0) {
        alert("Selecione ao menos um exame para criar a solicitação.");
        return;
      }

      const prioridadeDaSolicitacao = isUrgente ? 'URGENTE' : 'NORMAL';
      const payloadNovaSolicitacao = {
        usfOrigem, 
        nomePaciente,
        cpfPaciente,
        cns, 
        telefone,
        datanascimento, 
        dataMalote, 
        observacoes,
        especialidades: examesSelecionados.map(sel => ({
            especialidadeId: sel.id,
            especialidadeSolicitada: sel.value,
            status: 'AGUARDANDO',
            prioridade: prioridadeDaSolicitacao
        }))
      };
      
      postApi('solicitacoes', payloadNovaSolicitacao).then(async res => {
        if (res.ok) {
            alert('Nova solicitação criada com sucesso!');
            limparFormularioCompleto()
            await carregarListaSolicitacoes();
        } else {
            const errorData = await res.json().catch(() => ({}));
            alert(`Erro ao criar solicitação: ${errorData.message || 'Verifique os dados.'}`);
        }
      });
    }
  }

  // Funções auxiliares
  function limparFormularioCompleto() {
    idSolicitacao = null;
    nomePaciente = '';
    cpfPaciente = '';
    cns = '';
    datanascimento = '';
    usfOrigem = '';
    dataMalote = '';
    observacoes = '';
    inputsReadonly = false;
    examesDisponiveisParaCheckbox.forEach(ex => ex.selecionado = false);
    examesDaSolicitacaoAtual = [];
    valorBusca = '';
    telefone = '';
  }

  function removerSolicitacaoSelecionada(){
    selecionarSolicitacao(null)
  }

  function formatarCPF(e: Event) {
    const target = e.target as HTMLInputElement;
    let value = target.value.replace(/\D/g, '').slice(0, 11);
    value = value.replace(/^(\d{3})(\d)/, '$1.$2').replace(/^(\d{3}\.\d{3})(\d)/, '$1.$2').replace(/(\d{3}\.\d{3}\.\d{3})(\d)/, '$1-$2');
    cpfPaciente = value;
  }


</script>

<svelte:head>
    <title>Exame/Procedimento</title>
</svelte:head>

<div class="flex min-h-screen bg-gray-100">
    <RoleBasedMenu activePage="/exames" />

  <div class="flex-1 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Solicitação de Exames</h1>
          <UserMenu/>
    </header>

    <main class="flex-1 overflow-auto p-6">
      <div class="bg-white rounded-lg shadow-lg p-6">
        
        <div class="">
          
          <label for="selectSolicitacao" class="block text-lg font-medium text-gray-700 mb-2">Carregar Solicitação Existente:</label>
          <div class=" relative grid grid-cols-[1fr_auto] items-center gap-2">
            <div class="relative">
              <input
                id="combobox"
                type="text"
                bind:value={valorBusca}
                onfocus={() => comboboxAberto = true}
                onblur={() => setTimeout(() => { comboboxAberto = false }, 150)}
                oninput={(e) => {
                 const input = e.currentTarget as HTMLInputElement
                 const valorDigitado = input.value
                 valorBusca = valorDigitado
                 carregarListaSolicitacoes()
                 
                }}
                placeholder="Digite para buscar ou selecione uma solicitação"
                class="border border-gray-300 rounded-lg p-3 w-full focus:ring-emerald-500 focus:border-emerald-500 text-base"
              
                />

                 {#if comboboxAberto && listaDeSolicitacoesParaDropdown.length > 0}
                  <ul class="absolute z-10 w-full bg-white border border-gray-300 rounded-lg mt-1 max-h-60 overflow-y-auto">
                    {#each listaDeSolicitacoesParaDropdown as sol (sol.value)}
                      <li 
                        onmousedown={() => selecionarSolicitacao(sol)}
                        class="p-3 hover:bg-emerald-100 cursor-pointer"
                      >
                        {sol.label}
                      </li>

                      {/each}
                    </ul>
                {/if}
            </div>

              <button type="button" aria-label="Remover" onclick={removerSolicitacaoSelecionada}>
                <svg class="w-6 h-6 text-gray-800 dark:text-white" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="none" viewBox="0 0 24 24">
                <path stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18 17.94 6M18 18 6.06 6"/>
                </svg>
            </button>

          
          </div>
          {#if erroAoCarregar}
            <p class="text-red-500 text-sm mt-1">{erroAoCarregar}</p>
          {/if}
        </div>

        <h2 class="text-2xl font-bold text-emerald-800 mb-6 border-b pb-3">
          {#if idSolicitacao}Adicionar Exames à Solicitação ID: {idSolicitacao}{:else}Nova Solicitação com Exames{/if}
        </h2>

        <form onsubmit={submeterForm} class="space-y-8">
          
          <fieldset class="border border-gray-300 p-4 rounded-lg">
            <legend class="text-xl font-semibold text-gray-700 px-2">Dados do Paciente</legend>
            <div class="grid grid-cols-1 lg:grid-cols-5 gap-6 mt-4">
              <div class="flex flex-col">
                <label for="nomePaciente" class="text-sm font-medium text-gray-700 mb-1">Nome</label>
                <input id="nomePaciente" type="text" placeholder="Nome do Paciente" bind:value={nomePaciente} readonly={inputsReadonly} class:bg-gray-100={inputsReadonly} class="border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500" required />
              </div>
              <div class="flex flex-col">
                <label for="cpfPaciente" class="text-sm font-medium text-gray-700 mb-1">CPF</label>
                <input id="cpfPaciente" type="text" bind:value={cpfPaciente} oninput={formatarCPF} readonly={inputsReadonly} class:bg-gray-100={inputsReadonly} placeholder="000.000.000-00" class="border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500" required />
              </div>
              <div class="flex flex-col">
                <label for="cns" class="text-sm font-medium text-gray-700 mb-1">CNS</label>
                <input id="cns" type="text" bind:value={cns} readonly={inputsReadonly} class:bg-gray-100={inputsReadonly} placeholder="Cartão do SUS" maxlength="15" class="border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500" required />
              </div>
              <div class="flex flex-col">
                <label for="datanascimento" class="text-sm font-medium text-gray-700 mb-1">Data de Nasc.</label>
                <input id="datanascimento" type="date" bind:value={datanascimento} readonly={inputsReadonly} class:bg-gray-100={inputsReadonly} class="border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500" required />
              </div>
              <div>
                <label for="telefone" class="text-sm font-medium text-gray-700 mb-1">Telefone</label>
                <input id="telefone" type="text" bind:value={telefone} placeholder="Tel: (00) 0 0000-0000" maxlength="20" readonly={inputsReadonly} class:bg-gray-100={inputsReadonly} class="border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500"  >
              </div>
            </div>
          </fieldset>

          <fieldset class="border border-gray-300 p-4 rounded-lg">
            <legend class="text-xl font-semibold text-gray-700 px-2">Detalhes da Solicitação</legend>
             <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mt-4">
                <div class="flex flex-col">
                    <label for="dataMalote" class="text-sm font-medium text-gray-700 mb-1">Data Recebimento</label>
                    <input id="dataMalote" type="date" bind:value={dataMalote} readonly={inputsReadonly} class:bg-gray-100={inputsReadonly} class="border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500" required />
                </div>
                <div class="flex flex-col">
                    <label for="usfOrigem" class="text-sm font-medium text-gray-700 mb-1">USF Origem</label>
                    <select id="usfOrigem" bind:value={usfOrigem} disabled={inputsReadonly} class:bg-gray-100={inputsReadonly} class="border border-gray-300 rounded-lg p-2 focus:ring-emerald-500 focus:border-emerald-500" required>
                        <option value="" disabled>Selecione...</option>
                        {#each ['USF01','USF02','USF03','USF04','USF05','USF06'] as u}
                            <option value={u}>{u}</option>
                        {/each}
                    </select>
                </div>
            </div>
          
          </fieldset>
          
          {#if idSolicitacao && examesDaSolicitacaoAtual.length > 0}
            <fieldset class="border border-gray-300 p-4 rounded-lg">
                <legend class="text-xl font-semibold text-gray-700 px-2">Exames Já Presentes na Solicitação (ID: {idSolicitacao})</legend>
                <ul class="list-disc pl-5 mt-2 text-sm">
                    {#each examesDaSolicitacaoAtual as exameExistente (exameExistente.especialidadeSolicitada + exameExistente.id)}
                        <li>
                            {exameExistente.label} 
                            (Status: {exameExistente.status}, Prioridade: {exameExistente.prioridade})
                        </li>
                    {/each}
                </ul>
            </fieldset>
          {/if}

          <fieldset class="border border-gray-300 p-4 rounded-lg">
            <legend class="text-xl font-semibold text-gray-700 px-2">
              {#if idSolicitacao}Adicionar Novos Exames à Solicitação{:else}Selecionar Exames para Nova Solicitação{/if}
            </legend>
            <input type="text" class="rounded  w-full border-gray-300 p-2" placeholder="Digite o exame aqui para agilizar a seleção..." bind:value={termoBusca}>
            <p class="text-xs text-gray-500 mb-3">Marque os exames desejados. Eles serão adicionados com status "AGUARDANDO" e prioridade "NORMAL".</p>
            <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-x-4 gap-y-3 mt-4 max-h-96 overflow-y-auto pr-2">
              {#each examesParaConsulta() as exameLab, i (exameLab.value)}
                <label for="exameChk-{i}" class="flex items-center space-x-2 p-2 border border-gray-200 rounded-md hover:bg-gray-50 transition cursor-pointer">
                  <input type="checkbox" id="exameChk-{i}" bind:checked={exameLab.selecionado} class="form-checkbox h-4 w-4 text-emerald-600 rounded focus:ring-emerald-500">
                  <span class="text-sm text-gray-700 select-none">{exameLab.label}</span>
                </label>
              {/each}
            </div>
          </fieldset>


         
          <fieldset>
            <div class="flex items-center justify-center my-6">
            <label for="urgency-checkbox" class="flex items-center space-x-2 p-2 border border-gray-200 rounded-md hover:bg-gray-50 transition cursor-pointer">
              <input 
                type="checkbox" 
                id="urgency-checkbox" 
                bind:checked={isUrgente}
                class="form-checkbox h-5 w-5 text-emerald-600 rounded focus:ring-emerald-500"
              />
              <span class="text-base font-medium text-gray-700 select-none">Marcar como Urgente</span>
            </label>
          </div>
          </fieldset>
        

          <button type="submit" class="w-full bg-emerald-700 text-white py-3 rounded-lg hover:bg-emerald-800 transition text-lg font-semibold">
            {#if idSolicitacao}Enviar{:else}Criar Nova Solicitação com Exames{/if}
          </button>
        </form>
      </div>
    </main>
  </div>
</div>

<style>
  .form-checkbox {
    appearance: none;
    background-color: #fff;
    border: 1px solid #ccc;
    border-radius: 0.25rem;
    display: inline-block;
    height: 1.1em; /* Ajustado para melhor alinhamento */
    margin-right: 0.5em;
    position: relative;
    vertical-align: middle;
    width: 1.1em; /* Ajustado para melhor alinhamento */
    cursor: pointer;
    flex-shrink: 0; /* Para não encolher dentro do flex container */
  }
  .form-checkbox:checked {
    background-color: #10b981; /* emerald-600 */
    border-color: #059669; /* emerald-700 */
  }
  .form-checkbox:checked::before {
    content: '✓';
    color: white;
    font-size: 0.8em; /* Ajustado */
    font-weight: bold;
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    line-height: 1;
  }
  select:disabled, input:read-only {
    cursor: not-allowed;
    background-color: #f3f4f6; /* bg-gray-100 */
  }
  /* Para garantir que o texto ao lado do checkbox não quebre estranhamente */
  label.flex span {
    flex-grow: 1;
    word-break: break-word;
  }
</style>
