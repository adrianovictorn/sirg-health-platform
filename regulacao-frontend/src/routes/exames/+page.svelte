<script lang='ts'>
    import { getApi, postApi } from '$lib/api';
    import UserMenu from '$lib/UserMenu.svelte';
    import { onMount, tick } from 'svelte';
    import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';
    import { toast } from 'svelte-sonner';
    import { listarEspecialidadesCatalogo } from '$lib/especialidadesApi.js';
    import { sugestoesTeclado } from '$lib/sugestoesTeclado.js';
    import { roleVeRota } from '$lib/menuConfig.js';
    import { user } from '$lib/stores/auth.js';

    interface EspecialidadeDTO{
      id: number,
      codigo: string,
      nome: string,
    }

    interface CID {
      id: number,
      codigo: string,
      descricao: string,
    }

    type CheckboxItem = {
      id:number, label:string, value:string, selecionado:boolean
    }

    // Código -> nome do catálogo completo: a lista "aguardando" também mostra consultas.
    let labelMap = $state(new Map<string,string>());
    let exames = $state<EspecialidadeDTO[]>([])

    async function carregarNomesDoCatalogo() {
      try {
        const lista = await listarEspecialidadesCatalogo();
        labelMap = new Map(lista.map((e: EspecialidadeDTO) => [e.codigo, e.nome]));
      } catch (e) {
        console.warn('Falha ao carregar nomes do catálogo de especialidades', e);
      }
    }

    // Sem nome correspondente no catálogo, mostra o código.
    function nomeDoExame(codigo: string | null) {
      return labelMap.get(codigo ?? '') || (codigo || '').replaceAll('_',' ');
    }

    async function carregarExamesDoCatalogo() {
      try {
        const res = await getApi("catalog/especialidades/listar/exames");
        const data = await res.json()

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
  let examesPendentes = $state<any[]>([])
  let erroAoCarregar = $state<string | null>(null);
  let prioridade = $state('NORMAL');
  let directGel = $state(false)

  // Campos só do modo de criação: o acréscimo a uma solicitação existente não os envia.
  let unidadeId = $state<number | null>(null);
  let unidades = $state<{ id: number; nome: string }[]>([]);
  let recemNascido = $state(false);
  let cids = $state<CID[]>([]);
  // CID é opcional aqui: uma linha por CID, com o id escolhido (ou null).
  let cidsSelecionados = $state<(number | null)[]>([null]);
  let termosCid = $state<string[]>(['']);
  let comboCidAberto = $state<boolean[]>([false]);

  function addCid() {
    cidsSelecionados = [...cidsSelecionados, null];
    termosCid = [...termosCid, ''];
    comboCidAberto = [...comboCidAberto, false];
  }

  function removerCid(index: number) {
    cidsSelecionados = cidsSelecionados.filter((_, i) => i !== index);
    termosCid = termosCid.filter((_, i) => i !== index);
    comboCidAberto = comboCidAberto.filter((_, i) => i !== index);
  }

  function normalize(s: string) {
    return (s || '').toString().normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
  }

  function filtrarCidsTermo(t: string) {
    const q = normalize(t);
    const base = q ? cids.filter(c => normalize(c.codigo).includes(q) || normalize(c.descricao).includes(q)) : cids;
    return base.slice(0, 50);
  }

  async function carregarUnidadesECids() {
    const [resUnidades, resCids] = await Promise.all([
      getApi('unidades/ativas').catch(() => null),
      getApi('cid').catch(() => null)
    ]);
    if (resUnidades?.ok) unidades = await resUnidades.json();
    // Sem a lista não há como escolher a unidade, que é obrigatória na criação.
    else toast.error('Não foi possível carregar as unidades de saúde. Recarregue a página.');
    if (resCids?.ok) cids = await resCids.json();
  }

  // Lógica do Combobox
  let valorBusca = $state('');
  let comboboxAberto = $state(false);
  let size = $state(10)
  let page = $state(0)

     const examesParaConsulta = $derived(() => examesDisponiveisParaCheckbox.filter(exame => exame.label.toLocaleLowerCase().includes(termoBusca.toLocaleLowerCase())));
  // Só exibição: alimenta o contador "N exames marcados" ao lado do filtro.
  const totalExamesMarcados = $derived(examesDisponiveisParaCheckbox.filter(exame => exame.selecionado).length);

  

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
  let dataMalote = $state('');
  let observacoes = $state('');
  let inputsReadonly = $state(false);
  let telefone = $state('');
  // Dados cadastrais obrigatórios do paciente (backend valida via @NotBlank).
  let nomePai = $state('');
  let nomeMae = $state('');
  let endereco = $state('');
  // Data da coleta do material: opcional, aplicada aos exames desta submissão.
  let dataColeta = $state('');
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
      }
    ))
    } catch (e: any) {
      erroAoCarregar = e.message;
    }
  }

  onMount(async () => { await Promise.all([carregarExamesDoCatalogo(), carregarListaSolicitacoes(), carregarNomesDoCatalogo(), carregarUnidadesECids()]); });

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
      nomePai = s.nomePai || '';
      nomeMae = s.nomeMae || '';
      endereco = s.endereco || '';
      datanascimento = s.datanascimento ? s.datanascimento.split('T')[0] : '';
      dataMalote = s.dataMalote ? s.dataMalote.split('T')[0] : '';
      telefone = s.telefone || '';
      inputsReadonly = true;
      erros = {};
      erroGeral = '';
      examesDisponiveisParaCheckbox.forEach(ex => ex.selecionado = false);

      examesDaSolicitacaoAtual = s.especialidades || [];

      examesPendentes = examesDaSolicitacaoAtual.filter(e => e.status === 'AGUARDANDO')
    } catch (e: any) {
      toast.error(`Erro ao carregar detalhes: ${e.message}`);
      limparFormularioCompleto();
    }
  }

  // Trava contra envio duplo: o botão fica desabilitado enquanto o POST não volta.
  let enviando = $state(false);

  // Só decide se o atalho aparece; quem autoriza a ficha é o backend.
  const podeAbrirFicha = $derived(roleVeRota($user?.role, '/paciente'));

  // Erros de validação por campo (chave = id do campo) e erro vindo do servidor.
  let erros = $state<Record<string, string>>({});
  let erroGeral = $state('');

  // Mesmas regras que antes eram do `required` do navegador e dos alert(). No
  // acréscimo os dados do paciente são somente leitura e nunca foram validados.
  function validar(totalDeExames: number) {
    const e: Record<string, string> = {};
    if (!idSolicitacao) {
      if (!nomePaciente) e.nomePaciente = 'Informe o nome completo.';
      if (!recemNascido && cpfPaciente.replace(/\D/g, '').length === 0) {
        e.cpfPaciente = 'Informe o CPF, ou marque "Recém-nascido" se ele ainda não tiver CPF emitido.';
      }
      if (!cns) e.cns = 'Informe o Cartão do SUS (CNS).';
      if (!datanascimento) e.datanascimento = 'Informe a data de nascimento.';
      if (!nomeMae) e.nomeMae = 'Informe o nome da mãe.';
      if (!nomePai) e.nomePai = 'Informe o nome do pai.';
      if (!endereco) e.endereco = 'Informe o endereço.';
      if (!unidadeId) e.unidadeId = 'Selecione a unidade de saúde de origem.';
      if (!dataMalote) e.dataMalote = 'Informe a data de recebimento do pedido.';
    }
    if (totalDeExames === 0) e['filtro-exames'] = 'Selecione ao menos um exame.';
    return e;
  }

  // O erro some assim que o campo é corrigido.
  function limparErro(event: Event) {
    const id = (event.target as HTMLElement).id || '';
    const chave = id === 'recemNascido' ? 'cpfPaciente' : id.startsWith('exameChk-') ? 'filtro-exames' : id;
    if (erros[chave]) {
      const resto = { ...erros };
      delete resto[chave];
      erros = resto;
    }
  }

  async function mostrarErroGeral(mensagem: string) {
    erroGeral = mensagem;
    await tick();
    document.getElementById('erro-geral')?.focus();
  }

  async function submeterForm(event: SubmitEvent) {
    event.preventDefault(); // Previne o comportamento padrão do formulário
    if (enviando) return;

    const examesSelecionados = examesDisponiveisParaCheckbox.filter(ex => ex.selecionado);
    const prioridadeDaSolicitacao = prioridade;
    const direcionarParaGel = directGel ? 'GEL' : 'AGUARDANDO'

    erroGeral = '';
    erros = validar(examesSelecionados.length);
    const primeiroInvalido = Object.keys(erros)[0];
    if (primeiroInvalido) {
      await tick();
      document.getElementById(primeiroInvalido)?.focus();
      return;
    }


    
    if (idSolicitacao) {
      const paraAdicionar = examesSelecionados
        // A linha de filtro foi removida daqui
        .map(sel => ({
            especialidadeId: sel.id,
            especialidadeSolicitada: sel.value,
            dataColeta: dataColeta || null,
            status: direcionarParaGel,
            prioridade: prioridadeDaSolicitacao
        }));

      enviando = true;
      try {
        // Um POST por exame, independentes entre si: pode haver sucesso parcial.
        const responses = await Promise.all(paraAdicionar.map(itemPayload =>
          postApi(`solicitacoes/${idSolicitacao}/especialidades`, itemPayload)
        ));
        const algumaFalhou = responses.some(res => !res.ok);
        if (algumaFalhou) {
            await mostrarErroGeral('Erro ao adicionar um ou mais exames.');
        } else {
            toast.success("Exame(s) adicionado(s) com sucesso !")
            await carregarDadosSolicitacao(String(idSolicitacao));
        }
      } catch (e) {
        console.error('Erro ao adicionar exames:', e);
        await mostrarErroGeral('Erro ao adicionar um ou mais exames.');
      } finally {
        enviando = false;
      }

    // MODO CREATE
    } else {
      const payloadNovaSolicitacao = {
        nomePaciente,
        // null, não string vazia: o @CPF do backend trata "" como CPF inválido.
        cpfPaciente: cpfPaciente || null,
        recemNascido,
        unidadeId,
        cids: cidsSelecionados.filter((id) => id !== null),
        cns,
        telefone,
        nomePai,
        nomeMae,
        endereco,
        datanascimento,
        dataMalote,
        observacoes,
        especialidades: examesSelecionados.map(sel => ({
            especialidadeId: sel.id,
            especialidadeSolicitada: sel.value,
            dataColeta: dataColeta || null,
            status: direcionarParaGel,
            prioridade: prioridadeDaSolicitacao
        }))
      };
      
      enviando = true;
      try {
        const res = await postApi('solicitacoes', payloadNovaSolicitacao);
        if (res.ok) {
            toast.success('Nova solicitação criada com sucesso!');
            limparFormularioCompleto()
            await carregarListaSolicitacoes();
        } else {
            const errorData = await res.json().catch(() => ({}));
            await mostrarErroGeral(`Erro ao criar solicitação: ${errorData.message || 'Verifique os dados.'}`);
        }
      } catch (e) {
        console.error('Erro ao criar solicitação:', e);
        await mostrarErroGeral('Não foi possível conectar ao servidor. Verifique sua conexão.');
      } finally {
        enviando = false;
      }
    }
  }

  // Funções auxiliares
  function limparFormularioCompleto() {
    idSolicitacao = null;
    nomePaciente = '';
    cpfPaciente = '';
    cns = '';
    nomePai = '';
    nomeMae = '';
    endereco = '';
    dataColeta = '';
    datanascimento = '';
    dataMalote = '';
    observacoes = '';
    erros = {};
    erroGeral = '';
    unidadeId = null;
    prioridade = 'NORMAL';
    directGel = false;
    recemNascido = false;
    cidsSelecionados = [null];
    termosCid = [''];
    comboCidAberto = [false];
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
    <title>Solicitação de exames e procedimentos</title>
</svelte:head>

<div class="flex min-h-screen bg-gray-100">
    <RoleBasedMenu activePage="/exames" />

  <div class="flex-1 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Solicitação de exames e procedimentos</h1>
          <UserMenu/>
    </header>

    <main class="flex-1 overflow-auto p-4 md:p-6 lg:p-8 space-y-6">
      <section class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-bold text-emerald-800 mb-4 border-b pb-2">Paciente já cadastrado?</h2>
        <label for="combobox" class="block text-sm font-medium text-gray-700">Buscar paciente</label>
        <p id="combobox-dica" class="mb-1 text-xs text-gray-600">Para acrescentar exames a quem já tem solicitação. Deixe em branco para cadastrar um paciente novo.</p>
        <div class="flex items-center gap-3">
          <div class="relative flex-grow">
            <input
              id="combobox"
              type="text"
              autocomplete="off"
              aria-describedby="combobox-dica"
              bind:value={valorBusca}
              onfocus={() => comboboxAberto = true}
              onblur={() => setTimeout(() => { comboboxAberto = false }, 150)}
              use:sugestoesTeclado={{ aberta: comboboxAberto, fechar: () => (comboboxAberto = false) }}
              oninput={(e) => {
               const input = e.currentTarget as HTMLInputElement
               const valorDigitado = input.value
               valorBusca = valorDigitado
               comboboxAberto = true
               carregarListaSolicitacoes()

              }}
              placeholder="Nome ou CPF"
              class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500"
              />

               {#if comboboxAberto && listaDeSolicitacoesParaDropdown.length > 0}
                <ul role="listbox" aria-label="Sugestões" class="absolute z-10 w-full bg-white border border-gray-300 rounded-md mt-1 max-h-60 overflow-y-auto shadow-lg">
                  {#each listaDeSolicitacoesParaDropdown as sol (sol.value)}
                    <li role="none" class="p-0">
                      <button type="button" role="option" aria-selected="false" onmousedown={() => selecionarSolicitacao(sol)} class="w-full text-left p-2 hover:bg-emerald-100 cursor-pointer text-sm aria-selected:bg-emerald-100 aria-selected:ring-2 aria-selected:ring-inset aria-selected:ring-emerald-700">
                        {sol.label}
                      </button>
                    </li>
                  {/each}
                </ul>
              {/if}
          </div>

          {#if idSolicitacao || valorBusca}
            <button type="button" onclick={removerSolicitacaoSelecionada} class="shrink-0 px-3 py-2 text-sm font-medium text-gray-700 border border-gray-300 rounded-md hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-emerald-500">Limpar</button>
          {/if}
        </div>
        {#if erroAoCarregar}
          <p class="text-red-700 text-sm mt-1" role="alert">{erroAoCarregar}</p>
        {/if}
      </section>

      {#if idSolicitacao}
        <div class="bg-emerald-50 border-l-4 border-emerald-500 rounded-r-lg p-4 text-sm text-emerald-900" role="status">
          Acrescentando exames à solicitação nº {idSolicitacao}.
        </div>
      {/if}

      <form onsubmit={submeterForm} oninput={limparErro} onchange={limparErro} novalidate class="space-y-6">
        {#if erroGeral}
          <div id="erro-geral" role="alert" tabindex="-1" class="bg-red-50 border-l-4 border-red-500 rounded-r-lg p-4 text-sm text-red-800 focus:outline-none focus:ring-2 focus:ring-red-500">
            {erroGeral}
          </div>
        {/if}
        <p class="text-sm text-gray-600">Todos os campos são obrigatórios, exceto os marcados como opcionais.</p>

        <section class="bg-white rounded-lg shadow p-6">
          <h2 class="text-lg font-bold text-emerald-800 mb-4 border-b pb-2">Paciente</h2>
          {#if inputsReadonly}
            <p class="mb-4 text-sm text-gray-600">
              Dados da solicitação nº {idSolicitacao}.
              {#if podeAbrirFicha}
                Para alterar, <a href="/paciente/{idSolicitacao}" target="_blank" rel="noopener noreferrer" class="font-medium text-emerald-700 underline hover:text-emerald-900 focus:outline-none focus:ring-2 focus:ring-emerald-500 rounded">abra a ficha do paciente</a> (em nova aba).
              {:else}
                Para alterar, abra a ficha do paciente.
              {/if}
            </p>
          {/if}
          <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div class="md:col-span-2">
              <label for="nomePaciente" class="block text-sm font-medium text-gray-700 mb-1">Nome completo</label>
              <input id="nomePaciente" aria-describedby="nomePaciente-erro" aria-invalid={!!erros.nomePaciente} type="text" autocomplete="off" bind:value={nomePaciente} readonly={inputsReadonly} class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500 aria-invalid:border-red-500 aria-invalid:ring-red-500 read-only:bg-gray-100 read-only:text-gray-600 read-only:cursor-not-allowed" required />
              {#if erros.nomePaciente}<p id="nomePaciente-erro" class="mt-1 text-xs font-medium text-red-700">{erros.nomePaciente}</p>{/if}
            </div>
            <div>
              <label for="cpfPaciente" class="block text-sm font-medium text-gray-700 mb-1">CPF</label>
              <input id="cpfPaciente" aria-describedby="cpfPaciente-erro" aria-invalid={!!erros.cpfPaciente} type="text" inputmode="numeric" autocomplete="off" bind:value={cpfPaciente} oninput={formatarCPF} readonly={inputsReadonly} disabled={recemNascido && !inputsReadonly} placeholder="000.000.000-00" class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500 aria-invalid:border-red-500 aria-invalid:ring-red-500 read-only:bg-gray-100 read-only:text-gray-600 read-only:cursor-not-allowed disabled:bg-gray-100 disabled:text-gray-600 disabled:cursor-not-allowed" required={!recemNascido} />
              {#if erros.cpfPaciente}<p id="cpfPaciente-erro" class="mt-1 text-xs font-medium text-red-700">{erros.cpfPaciente}</p>{/if}
              {#if !inputsReadonly}
                <label class="mt-2 flex items-start gap-2 py-0.5 text-sm text-gray-700 cursor-pointer">
                  <input type="checkbox" id="recemNascido" bind:checked={recemNascido} onchange={() => { if (recemNascido) cpfPaciente = ''; }} class="mt-0.5 h-4 w-4 shrink-0 rounded border-gray-400 text-emerald-600 focus:ring-2 focus:ring-emerald-500 focus:ring-offset-1" />
                  <span>Recém-nascido, ainda sem CPF</span>
                </label>
              {/if}
            </div>
            <div>
              <label for="cns" class="block text-sm font-medium text-gray-700 mb-1">Cartão do SUS (CNS)</label>
              <input id="cns" aria-invalid={!!erros.cns} type="text" inputmode="numeric" autocomplete="off" bind:value={cns} readonly={inputsReadonly} maxlength="15" aria-describedby="cns-dica cns-erro" class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500 aria-invalid:border-red-500 aria-invalid:ring-red-500 read-only:bg-gray-100 read-only:text-gray-600 read-only:cursor-not-allowed" required />
              {#if erros.cns}<p id="cns-erro" class="mt-1 text-xs font-medium text-red-700">{erros.cns}</p>{/if}
              <p id="cns-dica" class="mt-1 text-xs text-gray-600">15 dígitos</p>
            </div>
            <div>
              <label for="datanascimento" class="block text-sm font-medium text-gray-700 mb-1">Data de nascimento</label>
              <input id="datanascimento" aria-describedby="datanascimento-erro" aria-invalid={!!erros.datanascimento} type="date" bind:value={datanascimento} readonly={inputsReadonly} class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500 aria-invalid:border-red-500 aria-invalid:ring-red-500 read-only:bg-gray-100 read-only:text-gray-600 read-only:cursor-not-allowed" required />
              {#if erros.datanascimento}<p id="datanascimento-erro" class="mt-1 text-xs font-medium text-red-700">{erros.datanascimento}</p>{/if}
            </div>
            <div>
              <label for="telefone" class="block text-sm font-medium text-gray-700 mb-1">Telefone <span class="text-gray-500 font-normal">(opcional)</span></label>
              <input id="telefone" type="tel" autocomplete="off" bind:value={telefone} placeholder="(00) 00000-0000" maxlength="20" readonly={inputsReadonly} class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500 read-only:bg-gray-100 read-only:text-gray-600 read-only:cursor-not-allowed" />
            </div>
            <div class="lg:col-start-1 lg:col-span-2">
              <label for="nomeMae" class="block text-sm font-medium text-gray-700 mb-1">Nome da mãe</label>
              <input id="nomeMae" aria-describedby="nomeMae-erro" aria-invalid={!!erros.nomeMae} type="text" autocomplete="off" bind:value={nomeMae} maxlength="150" readonly={inputsReadonly} class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500 aria-invalid:border-red-500 aria-invalid:ring-red-500 read-only:bg-gray-100 read-only:text-gray-600 read-only:cursor-not-allowed" required />
              {#if erros.nomeMae}<p id="nomeMae-erro" class="mt-1 text-xs font-medium text-red-700">{erros.nomeMae}</p>{/if}
            </div>
            <div class="lg:col-span-2">
              <label for="nomePai" class="block text-sm font-medium text-gray-700 mb-1">Nome do pai</label>
              <input id="nomePai" aria-describedby="nomePai-erro" aria-invalid={!!erros.nomePai} type="text" autocomplete="off" bind:value={nomePai} maxlength="150" readonly={inputsReadonly} class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500 aria-invalid:border-red-500 aria-invalid:ring-red-500 read-only:bg-gray-100 read-only:text-gray-600 read-only:cursor-not-allowed" required />
              {#if erros.nomePai}<p id="nomePai-erro" class="mt-1 text-xs font-medium text-red-700">{erros.nomePai}</p>{/if}
            </div>
            <div class="md:col-span-2 lg:col-span-4">
              <label for="endereco" class="block text-sm font-medium text-gray-700">Endereço</label>
              <p id="endereco-dica" class="mb-1 text-xs text-gray-600">Rua, número e bairro</p>
              <input id="endereco" aria-invalid={!!erros.endereco} type="text" autocomplete="off" bind:value={endereco} maxlength="300" aria-describedby="endereco-dica endereco-erro" readonly={inputsReadonly} class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500 aria-invalid:border-red-500 aria-invalid:ring-red-500 read-only:bg-gray-100 read-only:text-gray-600 read-only:cursor-not-allowed" required />
              {#if erros.endereco}<p id="endereco-erro" class="mt-1 text-xs font-medium text-red-700">{erros.endereco}</p>{/if}
            </div>
          </div>
        </section>

        <section class="bg-white rounded-lg shadow p-6">
          <h2 class="text-lg font-bold text-emerald-800 mb-4 border-b pb-2">Dados do pedido</h2>
          <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-x-4 -mb-4">
            {#if !idSolicitacao}
              <div class="lg:col-span-2 row-span-3 grid grid-cols-1 grid-rows-subgrid pb-4">
                <label for="unidadeId" class="block text-sm font-medium text-gray-700 mb-1 self-end">Unidade de saúde de origem</label>
                <select id="unidadeId" aria-describedby="unidadeId-erro" aria-invalid={!!erros.unidadeId} bind:value={unidadeId} class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500 aria-invalid:border-red-500 aria-invalid:ring-red-500" required>
                  <option value={null} disabled selected>Selecione a unidade</option>
                  {#each unidades as u (u.id)}
                    <option value={u.id}>{u.nome}</option>
                  {/each}
                </select>
                {#if erros.unidadeId}<p id="unidadeId-erro" class="mt-1 text-xs font-medium text-red-700">{erros.unidadeId}</p>{/if}
              </div>
            {/if}
            <div class="row-span-3 grid grid-cols-1 grid-rows-subgrid pb-4">
              <label for="dataMalote" class="block text-sm font-medium text-gray-700 mb-1 self-end">Data de recebimento do pedido</label>
              <input id="dataMalote" aria-describedby="dataMalote-erro" aria-invalid={!!erros.dataMalote} type="date" bind:value={dataMalote} readonly={inputsReadonly} class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500 aria-invalid:border-red-500 aria-invalid:ring-red-500 read-only:bg-gray-100 read-only:text-gray-600 read-only:cursor-not-allowed" required />
              {#if erros.dataMalote}<p id="dataMalote-erro" class="mt-1 text-xs font-medium text-red-700">{erros.dataMalote}</p>{/if}
            </div>
            <div class="row-span-3 grid grid-cols-1 grid-rows-subgrid pb-4">
              <label for="dataColeta" class="block text-sm font-medium text-gray-700 mb-1 self-end">
                Data da coleta <span class="text-gray-500 font-normal">(opcional)</span>
              </label>
              <input id="dataColeta" type="date" bind:value={dataColeta} aria-describedby="dataColeta-dica" class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500" />
              <p id="dataColeta-dica" class="mt-1 text-xs text-gray-600">Vale para os exames marcados agora.</p>
            </div>

            {#if !idSolicitacao}
              <div class="md:col-span-2 lg:col-span-4 pb-4">
                <span id="cid-rotulo" class="block text-sm font-medium text-gray-700">CID <span class="text-gray-500 font-normal">(opcional)</span></span>
                <p id="cid-dica" class="mb-1 text-xs text-gray-600">Busque pelo código ou pela descrição.</p>
                <div class="space-y-2">
                  {#each cidsSelecionados as _, i (i)}
                    <div class="flex items-center gap-3">
                      <div class="flex-grow relative">
                        <input
                          type="text"
                          id="cid-{i}"
                          aria-label="CID {i + 1}"
                          aria-describedby="cid-dica"
                          autocomplete="off"
                          class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500"
                          placeholder="Ex.: I10 ou hipertensão"
                          bind:value={termosCid[i]}
                          onfocus={() => comboCidAberto[i] = true}
                          oninput={() => comboCidAberto[i] = true}
                          onblur={() => setTimeout(() => comboCidAberto[i] = false, 150)}
                          use:sugestoesTeclado={{ aberta: comboCidAberto[i], fechar: () => (comboCidAberto[i] = false) }}
                        />
                        {#if comboCidAberto[i] && filtrarCidsTermo(termosCid[i]).length > 0}
                          <ul role="listbox" aria-label="Sugestões" class="absolute z-50 w-full bg-white border border-gray-300 rounded-md mt-1 max-h-60 overflow-y-auto shadow-lg">
                            {#each filtrarCidsTermo(termosCid[i]) as c (c.id)}
                              <li role="none" class="p-0">
                                <button type="button" role="option" aria-selected="false" class="w-full text-left p-2 hover:bg-emerald-100 cursor-pointer text-sm aria-selected:bg-emerald-100 aria-selected:ring-2 aria-selected:ring-inset aria-selected:ring-emerald-700" onmousedown={() => { cidsSelecionados[i] = c.id; termosCid[i] = `${c.codigo} - ${c.descricao}`; comboCidAberto[i] = false; }}>
                                  <strong>{c.codigo}</strong> - {c.descricao}
                                </button>
                              </li>
                            {/each}
                          </ul>
                        {/if}
                      </div>
                      {#if cidsSelecionados.length > 1}
                        <button type="button" onclick={() => removerCid(i)} aria-label="Remover CID {i + 1}" class="shrink-0 px-2 py-2 text-sm font-medium text-red-700 hover:text-red-900 rounded-md focus:outline-none focus:ring-2 focus:ring-red-500">Remover</button>
                      {/if}
                    </div>
                  {/each}
                </div>
                <button type="button" onclick={addCid} class="mt-2 py-1 text-sm font-medium text-emerald-700 hover:text-emerald-900 rounded-md focus:outline-none focus:ring-2 focus:ring-emerald-500">+ Adicionar outro CID</button>
              </div>

              <div class="md:col-span-2 lg:col-span-4 pb-4">
                <label for="observacoes" class="block text-sm font-medium text-gray-700">Observações <span class="text-gray-500 font-normal">(opcional)</span></label>
                <p id="observacoes-dica" class="mb-1 text-xs text-gray-600">Até 500 caracteres.</p>
                <textarea id="observacoes" bind:value={observacoes} rows="3" maxlength="500" aria-describedby="observacoes-dica" class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500"></textarea>
              </div>
            {/if}
          </div>
        </section>

        <section class="bg-white rounded-lg shadow p-6">
          <h2 class="text-lg font-bold text-emerald-800 mb-4 border-b pb-2">Exames solicitados</h2>

          {#if idSolicitacao && examesPendentes.length > 0}
            <div class="mb-4 pb-4 border-b border-gray-200">
              <h3 class="text-sm font-semibold text-gray-700 mb-2">Exames aguardando deste paciente</h3>
              <ul class="flex flex-wrap gap-x-6 gap-y-2 text-sm">
                {#each examesPendentes as exameExistente (exameExistente.especialidadeSolicitada + exameExistente.id)}
                  <li class="flex items-center gap-2">
                    <span class="text-gray-800">{nomeDoExame(exameExistente.especialidadeSolicitada)}</span>
                    <span class="px-2 py-0.5 rounded-full text-xs font-semibold bg-yellow-100 text-yellow-800">Aguardando</span>
                    {#if exameExistente.prioridade === 'URGENTE'}
                      <span class="px-2 py-0.5 rounded-full text-xs font-semibold bg-red-100 text-red-800">Urgente</span>
                    {:else if exameExistente.prioridade === 'EMERGENCIA'}
                      <span class="px-2 py-0.5 rounded-full text-xs font-semibold bg-red-100 text-red-800">Emergência</span>
                    {/if}
                  </li>
                {/each}
              </ul>
            </div>
          {/if}

          <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 items-start">
            <div class="lg:col-span-2">
              <label for="filtro-exames" class="block text-sm font-medium text-gray-700 mb-1">Filtrar exames</label>
              <input id="filtro-exames" aria-describedby="filtro-exames-erro" aria-invalid={!!erros['filtro-exames']} type="text" autocomplete="off" class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500 aria-invalid:border-red-500 aria-invalid:ring-red-500" placeholder="Ex.: hemograma" bind:value={termoBusca}>
              {#if erros['filtro-exames']}<p id="filtro-exames-erro" class="mt-1 text-xs font-medium text-red-700">{erros['filtro-exames']}</p>{/if}
            </div>
            <p class="text-sm text-gray-600 md:pt-9 lg:col-span-2" aria-live="polite">
              Marque um ou mais exames.
              <span class="font-medium text-gray-800">
                {#if totalExamesMarcados === 0}Nenhum exame marcado.{:else if totalExamesMarcados === 1}1 exame marcado.{:else}{totalExamesMarcados} exames marcados.{/if}
              </span>
            </p>
          </div>

          {#if termoBusca && examesParaConsulta().length === 0}
            <div class="mt-4 text-center py-6 px-4 bg-gray-50 rounded-md border border-dashed">
              <p class="text-gray-500">Nenhum exame encontrado.</p>
            </div>
          {/if}
          <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-x-4 gap-y-3 mt-4 max-h-96 overflow-y-auto p-1 pr-2">
            {#each examesParaConsulta() as exameLab, i (exameLab.value)}
              <label for="exameChk-{i}" class="flex items-center gap-2 p-2 border border-gray-200 rounded-md hover:bg-gray-50 transition cursor-pointer">
                <input type="checkbox" id="exameChk-{i}" bind:checked={exameLab.selecionado} class="h-4 w-4 shrink-0 rounded border-gray-400 text-emerald-600 focus:ring-2 focus:ring-emerald-500 focus:ring-offset-1">
                <span class="text-sm text-gray-700 select-none break-words min-w-0">{exameLab.label}</span>
              </label>
            {/each}
          </div>

          <div class="mt-4 pt-4 border-t border-gray-200 grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 items-start">
            <div>
              <label for="prioridade" class="block text-sm font-medium text-gray-700 mb-1">Prioridade</label>
              <select id="prioridade" bind:value={prioridade} aria-describedby="prioridade-dica" class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500">
                <option value="NORMAL">Normal</option>
                <option value="URGENTE">Urgente</option>
              </select>
              <p id="prioridade-dica" class="mt-1 text-xs text-gray-600">Vale para todos os exames marcados.</p>
            </div>
            <div class="md:pt-8 lg:col-span-3">
              <label for="gel-checkbox" class="flex items-start gap-2 py-0.5 text-sm text-gray-700 cursor-pointer">
                <input
                  type="checkbox"
                  id="gel-checkbox"
                  bind:checked={directGel}
                  aria-describedby="gel-dica"
                  class="mt-0.5 h-4 w-4 shrink-0 rounded border-gray-400 text-emerald-600 focus:ring-2 focus:ring-emerald-500 focus:ring-offset-1"
                />
                <span class="font-medium select-none">Enviar para Gel</span>
              </label>
              <p id="gel-dica" class="ml-6 text-xs text-gray-600">Gerenciamento de Especialidades Locais: para especialidades atendidas em outros municípios.</p>
            </div>
          </div>
        </section>

        <div class="flex justify-end">
          <button type="submit" disabled={enviando} aria-busy={enviando} class="w-full md:w-auto bg-emerald-700 text-white px-6 py-2 rounded-md hover:bg-emerald-800 transition-colors shadow disabled:opacity-60 disabled:cursor-not-allowed">
            {#if idSolicitacao}
              {enviando ? 'Adicionando…' : 'Adicionar exames'}
            {:else}
              {enviando ? 'Cadastrando…' : 'Cadastrar solicitação'}
            {/if}
          </button>
        </div>
      </form>
    </main>
  </div>
</div>
