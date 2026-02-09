<script lang="ts">
    import { onMount } from "svelte";
    import { page } from '$app/stores';
    import { goto } from '$app/navigation';
    import { getApi, postApi, putApi, deleteApi } from '$lib/api';
    import { listarEspecialidadesCatalogo } from '$lib/especialidadesApi.js';
    import RoleBasedMenu from "$lib/RoleBasedMenu.svelte";
    import { listarPactos, publicarSolicitacao } from '$lib/pactosApi.js';

    interface CID{
        id: number
        codigo: string
        descricao: string
    }

    // Tipagem básica para itens de especialidade exibidos no paciente
    type EspecialidadeItem = {
        id: number;
        especialidadeSolicitada: string;
        status: string;
        prioridade: string;
        agendamentoId?: number | null;
    };

    let todosOsCids = $state<CID[]>([]); // Guarda a lista completa de CIDs para o dropdown
    let cidParaAdicionar = $state<number | null>(null); // Guarda o ID do CID selecionado no dropdown

    let termoBuscaCid = $state('');
    let comboboxCidAberto = $state(false);

    const cidsFiltrados = $derived (todosOsCids.filter(cidsExistentes => {
        return cidsExistentes.codigo.toLowerCase().includes(termoBuscaCid.toLocaleLowerCase()) || cidsExistentes.descricao.toLowerCase().includes(termoBuscaCid.toLowerCase())
    }))

    function selecionarCid(cid: CID) {
        termoBuscaCid = `${cid.codigo} - ${cid.descricao}`; // Preenche o input com o texto do CID
        cidParaAdicionar = cid.id; // Salva o ID do CID selecionado na sua variável original
        comboboxCidAberto = false; // Fecha a lista de opções
    }

    let cidsAssociados: CID [] = [];
    // --- Estado do Componente com Svelte 5 Runes ---
    let solicitacao = $state<any>(null);
    let agendamentos = $state<any[]>([]);
    let especialidades = $state<any[]>([]); // Estado unificado para especialidades
    let isLoading = $state(true);
    let error = $state<string | null>(null);

    // Encaminhamento para Pacto (UI estado)
    let mostrarModalEncaminhar = $state(false);
    let pactosDisponiveis = $state<any[]>([]);
    let pactoSelecionadoId = $state<number | null>(null);
    let especialidadeSelecionada = $state<any | null>(null);

    async function abrirEncaminhar(espec: any) {
        especialidadeSelecionada = espec;
        try {
            const lista = await listarPactos();
            pactosDisponiveis = lista;
            if (lista.length > 0) pactoSelecionadoId = lista[0].id;
            mostrarModalEncaminhar = true;
        } catch (e:any) {
            alert(e.message || 'Falha ao carregar pactos');
        }
    }

    async function confirmarEncaminhamento() {
        if (!solicitacao || !especialidadeSelecionada || !pactoSelecionadoId) return;
        const label = getNomeEspecialidade(especialidadeSelecionada.especialidadeSolicitada);
        try {
            await publicarSolicitacao(pactoSelecionadoId, {
                solicitacaoLocalId: solicitacao.id,
                label
            });
            alert('Solicitação encaminhada às filas compartilhadas do pacto selecionado.');
            mostrarModalEncaminhar = false;
        } catch (e:any) {
            alert(e.message || 'Falha ao encaminhar solicitação');
        }
    }
    function cancelarEncaminhamento() { mostrarModalEncaminhar = false; }

    let catalogoEspecialidades = $state<any[]>([]);
    let labelMap = $state<Map<string,string>>(new Map()); // codigo -> nome
    let medicasCatalogo = $state<any[]>([]);
    let examesCatalogo = $state<any[]>([]);

    function getNomeEspecialidade(codigoOuEnum: string): string {
        if (!codigoOuEnum) return '';
        const nome = labelMap.get(codigoOuEnum);
        return nome || codigoOuEnum.replace(/_/g, ' ');
    }
    
    function formatarData(dataString: string | null): string {
        if (!dataString) return 'N/A';
        const data = new Date(dataString);
        return data.toLocaleDateString('pt-BR', { timeZone: 'UTC' });
    }

    // Variáveis para o formulário de edição
    let nomePaciente = $state('');
    let cpfPaciente = $state('');
    let cns = $state('');
    let datanascimento = $state('');
    let usfOrigem = $state('');
    let dataMalote = $state('');
    let observacoes = $state('');
    let telefone = $state('');
    let status = $state('');

    // Objeto reativo para a nova especialidade a ser adicionada
    let novaEspecialidadeObj = $state({ especialidadeId: null as number | null, status: 'AGUARDANDO', prioridade: 'NORMAL' });
    let termoBuscaNovaEsp = $state('');
    let comboboxEspAberto = $state(false);
    function normalizeEsp(s: string) {
        return (s || '').toString().normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
    }
    function filtrarEspecialidadesPac(t: string) {
        const q = normalizeEsp(t);
        const base = q
            ? catalogoEspecialidades.filter((e:any) => normalizeEsp(e.nome).includes(q) || normalizeEsp(e.codigo).includes(q))
            : catalogoEspecialidades;
        return base.slice(0, 50);
    }

    function prettyCategoria(cat: string) {
        return cat === 'ESPECIALIDADE_MEDICA' ? 'Consulta' : 'Exame/Procedimento';
    }
    
    // --- Lógica de Carregamento de Dados ---
    onMount(async () => {
        const id = $page.params.id;
      try {
        // Carrega dados da solicitação, agendamentos, CIDs e catálogo de especialidades em paralelo
        const [resSolicitacao, resAgendamentos, resTodosOsCids, listaCatalogo] = await Promise.all([
            getApi(`solicitacoes/${id}`),
            getApi(`agendamentos/solicitacao/${id}`),
            getApi('cid'), // Busca todos os CIDs para o dropdown
            listarEspecialidadesCatalogo().catch(() => [])
        ]);

        if (!resSolicitacao.ok) {
            throw new Error(`Falha ao buscar os dados do paciente: ${await resSolicitacao.text()}`);
        }

        solicitacao = await resSolicitacao.json();
        especialidades = solicitacao.especialidades || [];
        
        // Popula a lista de todos os CIDs para usar no dropdown
        if (resTodosOsCids.ok) {
            todosOsCids = await resTodosOsCids.json();
        } else {
            console.warn('Não foi possível carregar a lista de CIDs.');
        }

        if (resAgendamentos.ok) {
            agendamentos = await resAgendamentos.json();
        } else {
            console.warn('Não foi possível carregar os agendamentos.');
        }

        // Monta catálogo e mapas de rótulos
        if (Array.isArray(listaCatalogo)) {
            catalogoEspecialidades = listaCatalogo;
            labelMap = new Map(listaCatalogo.map((e:any) => [e.codigo, e.nome]));
            medicasCatalogo = listaCatalogo.filter((e:any) => e.categoria === 'ESPECIALIDADE_MEDICA');
            examesCatalogo = listaCatalogo.filter((e:any) => e.categoria === 'EXAME_OU_PROCEDIMENTO');
        }

        // Preenche os campos do formulário (código existente)
        nomePaciente = solicitacao.nomePaciente;
        cpfPaciente = solicitacao.cpfPaciente;
        cns = solicitacao.cns;
        datanascimento = solicitacao.datanascimento;
        usfOrigem = solicitacao.usfOrigem;
        dataMalote = solicitacao.dataMalote;
        observacoes = solicitacao.observacoes;
        telefone = solicitacao.telefone || '';

    } catch (e: any) {
        error = e.message;
    } finally {
        isLoading = false;
    }
});

    // --- Funções de Ação ---

    async function salvarPaciente() {
    if (!solicitacao) return;

    // Mapeia os CIDs associados para enviar apenas a lista de IDs
    const idsDosCids = solicitacao.cids?.map((c: CID) => c.id) || [];

    const payload = { 
        nomePaciente, 
        cpfPaciente, 
        cns, 
        telefone, 
        datanascimento, 
        usfOrigem, 
        dataMalote, 
        observacoes,
        cids: idsDosCids // Envia a lista de IDs de CIDs para o backend
    };
    
    const res = await putApi(`solicitacoes/${solicitacao.id}`, payload);
    if (res.ok) {
        alert('Paciente e CIDs atualizados com sucesso!');
    } else {
        alert('Erro ao atualizar paciente.');
    }
}

    async function adicionarEspecialidade() {
        if (!novaEspecialidadeObj.especialidadeId) {
            alert('Selecione uma especialidade para adicionar.');
            return;
        }
        if (!solicitacao) return;

        const payload = {
            especialidadeId: Number(novaEspecialidadeObj.especialidadeId),
            status: novaEspecialidadeObj.status,
            prioridade: novaEspecialidadeObj.prioridade
        };

        const res = await postApi(`solicitacoes/${solicitacao.id}/especialidades`, payload);
        if (res.ok) {
            alert('Especialidade adicionada com sucesso!');
            location.reload();
        } else {
            alert('Erro ao adicionar especialidade');
        }
    }

    async function removerEspecialidade(especialidadeId: number) {
        if (!confirm('Tem certeza que deseja remover esta especialidade?')) return;
        try {
            const res = await deleteApi(`solicitacoes/especialidades/${especialidadeId}`);
            if (res.ok) {
                alert('Especialidade removida com sucesso!');
                location.reload();
            } else {
                const errorText = await res.text();
                alert(`Erro ao remover especialidade: ${res.status} - ${errorText}`);
            }
        } catch (e: any) {
            alert(`Erro na requisição: ${e.message}`);
        }
    }

    async function removerAgendamento(agendamentoId: number) {
        if (!confirm('Tem certeza que deseja remover este agendamento e desvincular os itens associados?')) return;
        try {
            const res = await deleteApi(`agendamentos/${agendamentoId}`);
            if (res.ok) {
                alert('Agendamento removido com sucesso!');
                location.reload();
            } else {
                const errorText = await res.text();
                alert(`Erro ao remover agendamento: ${res.status} - ${errorText}`);
            }
        } catch (e: any) {
            alert(`Erro na requisição: ${e.message}`);
        }
    }

   

    async function handlePrioridadeChange(especialidadeId: number, event: Event) {
        const novaPrioridade = (event.currentTarget as HTMLSelectElement).value;
        if (!confirm(`Tem certeza que deseja alterar a prioridade para ${novaPrioridade}?`)) {
            location.reload();
            return;
        }
        try {
            const res = await putApi(`especialidades/${especialidadeId}`, { prioridade: novaPrioridade });
            if (res.ok) {
                alert('Prioridade atualizada com sucesso!');
                const index = especialidades.findIndex(e => e.id === especialidadeId);
                if (index !== -1) {
                    especialidades[index].prioridade = novaPrioridade;
                }
            } else {
                const errorText = await res.text();
                alert(`Erro ao atualizar prioridade: ${res.status} - ${errorText}`);
                location.reload();
            }
        } catch (e: any) {
            alert(`Erro na requisição: ${e.message}`);
            location.reload();
        }
    }

    async function salvarCids() {
    if (!solicitacao) return;

    // Pega a lista mais recente de IDs de CIDs do estado local
    const idsDosCids = solicitacao.cids.map((c: CID) => c.id);

    // O payload precisa de todos os campos que o DTO espera,
    // então usamos os valores já existentes no estado.
    const payload = { 
        nomePaciente, cpfPaciente, cns, telefone, 
        datanascimento, usfOrigem, dataMalote, observacoes,
        cids: idsDosCids // A lista atualizada de IDs
    };
    
    try {
        const res = await putApi(`solicitacoes/${solicitacao.id}`, payload);
        if (!res.ok) {
            // Se a API falhar, recarrega a página para reverter a alteração visual
            alert('Erro ao salvar o CID. A página será atualizada.');
            location.reload();
        } else {
            console.log("CID salvo com sucesso!");
        }
    } catch (err) {
        alert('Erro de conexão ao salvar o CID. A página será atualizada.');
        location.reload();
    }
    }

    // Agora, a função `adicionarCid` atualiza a tela e chama `salvarCids`
    async function adicionarCid() {
        if (cidParaAdicionar === null) {
            alert('Selecione um CID para adicionar.');
            return;
        }
        const cidJaExiste = solicitacao.cids.some((c: CID) => c.id === cidParaAdicionar);
        if (cidJaExiste) {
            alert('Este CID já está associado ao paciente.');
            return;
        }
        const cidObj = todosOsCids.find((c) => c.id === cidParaAdicionar);
        if (cidObj) {
            // 1. Atualiza a tela (otimista)
            solicitacao.cids.push(cidObj); 
            cidParaAdicionar = null;

            // 2. Persiste a alteração no banco de dados
            await salvarCids();
        }
    }

    // A função `removerCid` também atualiza a tela e chama `salvarCids`
    async function removerCid(idParaRemover: number) {
        // 1. Atualiza a tela (otimista)
        solicitacao.cids = solicitacao.cids.filter((c: CID) => c.id !== idParaRemover);

        // 2. Persiste a alteração no banco de dados
        await salvarCids();
    }


    let historico: EspecialidadeItem[] = $derived(especialidades as unknown as EspecialidadeItem[]);
    let especPendentes: EspecialidadeItem[] = $derived(
        ((((historico as unknown as EspecialidadeItem[]) || []).filter((e: EspecialidadeItem) => {
            const st = ((e.status ?? '') as string).toString().trim().toUpperCase();
            const semAgendamento = e.agendamentoId == null;
            const ehPendentePorStatus = (st === 'AGUARDANDO' || st === 'RETORNO' || st === 'RETORNO_POLICLINICA');
            const naoFinalizado = (st !== 'CANCELADO' && st !== 'REALIZADO');
            return ehPendentePorStatus || (semAgendamento && naoFinalizado);
        })) as EspecialidadeItem[])
    );

</script>

<svelte:head>
    <title>Paciente - {solicitacao?.nomePaciente || 'Carregando...'}</title>
</svelte:head>

<div class="flex h-screen bg-gray-100">
    <RoleBasedMenu activePage="/paciente" />

    <div class="grid grid-cols-1 md:flex md:flex-1 md:flex-col">
        <header class="bg-emerald-700 text-white p-4 flex justify-between items-center shadow-md">
            {#if isLoading}
                <h1 class="text-xl font-semibold">Carregando Dados do Paciente...</h1>
            {:else if solicitacao}
                <h1 class="text-xl font-semibold">Dados do Paciente</h1>
                <div><span class="font-bold">CPF:</span> {solicitacao.cpfPaciente}</div>
            {/if}
        </header>

        <main class="p-4 md:p-6 lg:p-8 overflow-auto space-y-6">
            {#if isLoading}
                <div class="text-center p-10">Carregando...</div>
            {:else if error}
                <div class="bg-red-100 border-l-4 border-red-500 text-red-700 p-4" role="alert">
                    <p class="font-bold">Erro ao carregar</p>
                    <p>{error}</p>
                </div>
            {:else if solicitacao}
                
                <!-- Seção Editar Paciente -->
                <section class="bg-white rounded-lg shadow p-6">
                    <h2 class="text-lg font-bold text-emerald-800 mb-4 border-b pb-2">Editar Paciente</h2>
                    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                <div class="lg:col-span-2">
                    <label class="block text-sm font-medium text-gray-700 mb-1">Nome</label>
                    <input type="text" bind:value={nomePaciente} class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500" />
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">Cartão do SUS</label>
                    <input type="text" bind:value={cns} class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500" />
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">Telefone</label>
                    <input type="text" bind:value={telefone} class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500" />
                </div>

                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">Data de Nascimento</label>
                    <input type="date" bind:value={datanascimento} class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500" />
                </div>
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">Data Recebimento</label>
                    <input type="date" bind:value={dataMalote} class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500" />
                </div>
                <div class="lg:col-span-2">
                    <label class="block text-sm font-medium text-gray-700 mb-1">USF Origem</label>
                    <input type="text" bind:value={usfOrigem} class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500" />
                </div>
            </div>
                       <div class="mt-6 pt-4 border-t">
                        <h3 class="text-md font-bold text-gray-800 mb-3">CIDs Associados</h3>
                        <div class="flex flex-wrap gap-2 mb-4">
                            {#if solicitacao.cids && solicitacao.cids.length > 0}
                                {#each solicitacao.cids as cid (cid.id)}
                                    <div class="flex items-center bg-emerald-100 text-emerald-800 text-sm font-medium px-3 py-1 rounded-full">
                                        <span>{cid.codigo} - {cid.descricao}</span>
                                        <button onclick={() => removerCid(cid.id)} aria-label="Remover CID" class="ml-2 text-emerald-600 hover:text-emerald-900 font-bold">&times;</button>
                                    </div>
                                {/each}
                            {:else}
                                <p class="text-sm text-gray-500">Nenhum CID associado.</p>
                            {/if}
                        </div>

                       <div class="flex items-end gap-3">
                        <div class="flex-grow relative">
                            <label for="cid-combobox" class="block text-sm font-medium text-gray-700 mb-1">Adicionar novo CID</label>
                            <input 
                                type="text"
                                placeholder="Digite o código ou a descrição..."
                                bind:value={termoBuscaCid}
                                id="cid-combobox"
                                onfocus={() => comboboxCidAberto = true}
                                onblur={() => setTimeout(() => { comboboxCidAberto = false; }, 150)}
                                class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500"
                            />

                                    {#if comboboxCidAberto && cidsFiltrados.length > 0}
                                        <ul class="absolute z-10 w-full bg-white border border-gray-300 rounded-md mt-1 max-h-60 overflow-y-auto shadow-lg">
                                            {#each cidsFiltrados as cid (cid.id)}
                                                <li class="p-0">
                                                    <button type="button" class="w-full text-left p-2 hover:bg-emerald-100 cursor-pointer text-sm" onclick={() => selecionarCid(cid)}>
                                                        <strong>{cid.codigo}</strong> - {cid.descricao}
                                                    </button>
                                                </li>
                                            {/each}
                                        </ul>
                                    {/if}
                                </div>

                                <button onclick={adicionarCid} type="button" class="bg-blue-600 text-white px-4 py-2 rounded-md hover:bg-blue-700 transition-colors shadow-sm">Adicionar</button>
                            </div>
                  <div class="mt-4">
                    <label class="block text-sm font-medium text-gray-700 mb-1">Observações</label>
                    <textarea bind:value={observacoes} rows="3" class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500"></textarea>
                </div>

                <div class="mt-6 flex justify-end">
                    <button onclick={salvarPaciente} class="bg-emerald-700 text-white px-6 py-2 rounded-md hover:bg-emerald-800 transition-colors shadow">
                        Salvar Alterações
                    </button>
                </div>

                </section>

           
                <section class="bg-white rounded-lg shadow p-6">
                    <h2 class="text-lg font-bold text-emerald-800 mb-4 border-b pb-2">Histórico de Procedimentos Concluídos</h2>

                    <!-- Adicionado bloco #if para dar um pai válido para a tag @const -->
                    {#if agendamentos}
                        <!-- Filtra agendamentos para incluir apenas aqueles com itens realizados -->
                        {@const agendamentosConcluidos = agendamentos.map(ag => {
                            return {
                                ...ag,
                                itensRealizados: historico.filter(h => h.agendamentoId === ag.id && h.status === 'REALIZADO')
                            };
                        }).filter(ag => ag.itensRealizados.length > 0)}

                        {#if agendamentosConcluidos.length === 0}
                            <div class="text-center py-8 bg-gray-50 rounded-md">
                                <p class="text-gray-500">Nenhum procedimento concluído encontrado para este paciente.</p>
                            </div>
                        {:else}
                            <div class="space-y-4">
                                {#each agendamentosConcluidos as ag}
                                    <div class="bg-white border border-gray-200 rounded-lg shadow-sm overflow-hidden">
                                        <div class="p-4 bg-gray-50 border-b flex justify-between items-start gap-4">
                                            <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-2 flex-grow">
                                                <div>
                                                    <p class="text-xs font-semibold text-gray-500 uppercase tracking-wider">Local</p>
                                                    <p class="text-base font-medium text-gray-900">{ag.localAgendado.replace(/_/g, ' ')}</p>
                                                </div>
                                                <div>
                                                    <p class="text-xs font-semibold text-gray-500 uppercase tracking-wider">Data</p>
                                                    <p class="text-base font-medium text-gray-900">{formatarData(ag.dataAgendada)}</p>
                                                </div>
                                            </div>
                                        </div>
                                        <div class="p-4">
                                            <h4 class="text-sm font-semibold text-gray-700 mb-2">Itens Realizados:</h4>
                                            <ul class="space-y-2">
                                                {#each ag.itensRealizados as h}
                                                    <li class="flex justify-between items-center text-sm">
                                                        <span class="text-gray-800">{getNomeEspecialidade(h.especialidadeSolicitada)}</span>
                                                        <span class="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-green-100 text-green-800">
                                                            {h.status}
                                                        </span>
                                                    </li>
                                                {/each}
                                            </ul>
                                        </div>
                                    </div>
                                {/each}
                            </div>
                        {/if}
                    {/if}
                </section>
                
                <!-- Seção Histórico de Agendamentos -->
                 <section class="bg-white rounded-lg shadow p-6">
                    <h2 class="text-lg font-bold text-emerald-800 mb-4 border-b pb-2">Histórico de Agendamentos Ativos</h2>

                    {#if agendamentos}
                        {@const agendamentosAtivos = agendamentos.map(ag => ({
                            ...ag,
                            itensAgendados: especialidades.filter(e => e.agendamentoId === ag.id && e.status === 'AGENDADO')
                        })).filter(ag => ag.itensAgendados.length > 0)}

                        {#if agendamentosAtivos.length === 0}
                            <div class="text-center py-8 bg-gray-50 rounded-md">
                                <p class="text-gray-500">Nenhum agendamento ativo encontrado.</p>
                            </div>
                        {:else}
                            <div class="space-y-4">
                                {#each agendamentosAtivos as ag (ag.id)}
                                    <div class="bg-white border border-gray-200 rounded-lg shadow-sm overflow-hidden">
                                        <div class="p-4 bg-gray-50 border-b flex justify-between items-start gap-4">
                                            <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-2 flex-grow">
                                                <div>
                                                    <p class="text-xs font-semibold text-gray-500 uppercase tracking-wider">Local</p>
                                                    <p class="text-base font-medium text-gray-900">{ag.localAgendado.replace(/_/g, ' ')}</p>
                                                </div>
                                                <div>
                                                    <p class="text-xs font-semibold text-gray-500 uppercase tracking-wider">Data</p>
                                                    <p class="text-base font-medium text-gray-900">{formatarData(ag.dataAgendada)}</p>
                                                </div>
                                            </div>
                                            <button onclick={() => removerAgendamento(ag.id)} aria-label="Remover agendamento"
                                                    class="p-2 rounded-full text-gray-400 hover:bg-red-100 hover:text-red-600 transition-colors flex-shrink-0"
                                                    title="Remover Agendamento e Itens Associados">
                                                <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                                                    <path stroke-linecap="round" stroke-linejoin="round" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                                                </svg>
                                            </button>
                                        </div>
                                        <div class="p-4">
                                            <h4 class="text-sm font-semibold text-gray-700 mb-2">Itens Agendados:</h4>
                                            <ul class="space-y-2">
                                                {#each ag.itensAgendados as h (h.id)}
                                                    <li class="flex justify-between items-center text-sm">
                                                        <span class="text-gray-800">{getNomeEspecialidade(h.especialidadeSolicitada)}</span>
                                                        <div class="flex items-center space-x-3">
                                                            <span class="px-2 py-0.5 rounded-full text-xs font-semibold bg-blue-100 text-blue-800">AGENDADO</span>
                                                            <button onclick={() => removerEspecialidade(h.id)} aria-label="Remover especialidade"
                                                                    class="p-2 rounded-full text-gray-400 hover:bg-red-100 hover:text-red-600 transition-colors"
                                                                    title="Desvincular e Remover Especialidade">
                                                                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                                                                    <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
                                                                </svg>
                                                            </button>
                                                        </div>
                                                    </li>
                                                {/each}
                                            </ul>
                                        </div>
                                    </div>
                                {/each}
                            </div>
                        {/if}
                    {/if}
                </section>

                <!-- Seção Especialidades Pendentes -->
                <section class="bg-white rounded-lg shadow p-6">
                    <h2 class="text-lg font-bold text-emerald-800 mb-4 border-b pb-2">Especialidades Pendentes</h2>
                     {#if especPendentes.length > 0}
                        <div class="border border-gray-200 rounded-md">
                            <ul class="divide-y divide-gray-200">
                                {#each especPendentes as e (e.id)}
                                    <li class="p-3 md:flex justify-between items-center hover:bg-gray-50">
                                        <span class="text-gray-800 font-medium ">{getNomeEspecialidade(e.especialidadeSolicitada)}</span>
                                        
                                        <div class="grid grid-cols-1 md:flex gap-1 items-center ">
                                            {#if e.status === 'AGUARDANDO'}
                                                <span class="px-3 py-1 text-xs font-semibold rounded-full bg-yellow-100 text-yellow-800">{e.status}</span>
                                            {/if}
                                           {#if e.status === 'RETORNO' || e.status === 'RETORNO_POLICLINICA'}
                                             <span class="px-3 py-1 text-xs font-semibold rounded-full bg-blue-200 text-blue-800">{e.status}</span>
                                           {/if}
                                            <select bind:value={e.prioridade} 
                                                    onchange={(event) => handlePrioridadeChange(e.id, event)}
                                                    class="text-sm border-gray-300 rounded-md shadow-sm focus:ring-emerald-500 focus:border-emerald-500 transition duration-150 ease-in-out py-1">
                                                <option value="NORMAL">Normal</option>
                                                <option value="URGENTE">Urgente</option>
                                                <option value="EMERGENCIA">Emergência</option>
                                            </select>
                                            <button onclick={() => abrirEncaminhar(e)}
                                                class="px-3 py-1 text-xs rounded bg-emerald-600 text-white hover:bg-emerald-700"
                                                title="Encaminhar para filas compartilhadas">
                                                Encaminhar
                                            </button>
                                            <button onclick={() => removerEspecialidade(e.id)} 
                                                class="p-2 rounded-full text-gray-400 hover:bg-red-100 hover:text-red-600 transition-colors hidden md:block"
                                                title="Remover Especialidade">
                                                <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                                                    <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
                                                </svg>
                                            </button>
                                            <button onclick={() => removerEspecialidade(e.id)} type="button" class="md:hidden px-3 py-1 text-xs rounded bg-red-600 text-white hover:bg-red-700">Remover</button>
                                        </div>
                                    </li>
                                {/each}
                            </ul>
                        </div>
                 {:else}
                    <div class="text-center py-6 px-4 bg-gray-50 rounded-md border border-dashed">
                        <p class="text-gray-500">Nenhuma especialidade pendente.</p>
                    </div>
                 {/if}
                </section>

                {#if mostrarModalEncaminhar}
                <div class="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
                  <div class="bg-white rounded-lg shadow-xl w-full max-w-md p-6 space-y-4">
                    <h3 class="text-lg font-semibold text-gray-800">Encaminhar para Filas Compartilhadas</h3>
                    <p class="text-sm text-gray-700">Deseja encaminhar a solicitação desta especialidade para filas compartilhadas?</p>
                    <div class="space-y-2">
                      <label for="pacto-select" class="block text-sm text-gray-600">Pacto</label>
                      <select id="pacto-select" class="w-full border rounded p-2" bind:value={pactoSelecionadoId}>
                        {#each pactosDisponiveis as p}
                          <option value={p.id}>{p.nome}</option>
                        {/each}
                      </select>
                    </div>
                    <div class="flex justify-end gap-2 pt-2">
                      <button class="px-4 py-2 rounded border" onclick={cancelarEncaminhamento}>Cancelar</button>
                      <button class="px-4 py-2 rounded bg-emerald-600 text-white hover:bg-emerald-700" onclick={confirmarEncaminhamento}>Confirmar</button>
                    </div>
                  </div>
                </div>
                {/if}

    <section class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-bold text-emerald-800 mb-4 border-b pb-2">Histórico de Procedimentos Cancelados</h2>

        {#if agendamentos}
            {@const agendamentosComCancelados = agendamentos.map(ag => ({
                ...ag,
                itensCancelados: especialidades.filter(e => e.agendamentoId === ag.id && e.status === 'CANCELADO')
            })).filter(ag => ag.itensCancelados.length > 0)}

            {#if agendamentosComCancelados.length === 0}
                <div class="text-center py-8 bg-gray-50 rounded-md">
                    <p class="text-gray-500">Nenhum procedimento cancelado encontrado.</p>
                </div>
            {:else}
                <div class="space-y-4">
                    {#each agendamentosComCancelados as ag (ag.id)}
                        <div class="bg-white border border-gray-200 rounded-lg shadow-sm overflow-hidden">
                            
                            <div class="p-4 bg-gray-50 border-b flex justify-between items-start gap-4">
                                <div class="grid grid-cols-1 sm:grid-cols-2 gap-x-6 gap-y-2 flex-grow">
                                    <div>
                                        <p class="text-xs font-semibold text-gray-500 uppercase">Local</p>
                                        <p class="text-base font-medium text-gray-900">{ag.localAgendado.replace(/_/g, ' ')}</p>
                                    </div>
                                    <div>
                                        <p class="text-xs font-semibold text-gray-500 uppercase">Data</p>
                                        <p class="text-base font-medium text-gray-900">{formatarData(ag.dataAgendada)}</p>
                                    </div>
                                </div>
                            </div>

                            <div class="p-4">
                                {#if ag.observacoes}
                                    <div class="mb-4 p-3 bg-yellow-50 border-l-4 border-yellow-400 rounded-r-md">
                                        <p class="text-xs font-bold text-gray-600">Observações do Agendamento:</p>
                                        <p class="text-sm text-gray-800 italic">"{ag.observacoes}"</p>
                                    </div>
                                {/if}

                                <h4 class="text-sm font-semibold text-gray-700 mb-2">Itens Cancelados:</h4>
                                <ul class="space-y-2">
                                    {#each ag.itensCancelados as item (item.id)}
                                        <li class="flex justify-between items-center text-sm">
                                            <span class="text-gray-800">{getNomeEspecialidade(item.especialidadeSolicitada)}</span>
                                            <span class="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-red-100 text-red-800">
                                                CANCELADO
                                            </span>
                                        </li>
                                    {/each}
                                </ul>
                            </div>
                        </div>
                    {/each}
                </div>
            {/if}
        {/if}
    </section>

                <!-- Seção Adicionar Nova Especialidade -->
                <section class="bg-white rounded-lg shadow p-6">
                     <h2 class="text-lg font-bold text-emerald-800 mb-4 border-b pb-2">Adicionar Nova Especialidade</h2>
                    <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                        <div class="lg:col-span-1">
                            <label for="nova-especialidade-input" class="block text-sm font-medium text-gray-700 mb-1">Especialidade</label>
                            <div class="relative">
                                <input 
                                  type="text"
                                  class="w-full border border-gray-300 rounded-md p-2 focus:ring-emerald-500 focus:border-emerald-500"
                                  placeholder="Digite para buscar..."
                                  bind:value={termoBuscaNovaEsp}
                                  id="nova-especialidade-input"
                                  onfocus={() => comboboxEspAberto = true}
                                  oninput={() => comboboxEspAberto = true}
                                  onblur={() => setTimeout(() => comboboxEspAberto = false, 150)}
                                />
                                {#if comboboxEspAberto}
                                  {@const sugestoes = filtrarEspecialidadesPac(termoBuscaNovaEsp)}
                                  {@const consultas = sugestoes.filter((e:any) => e.categoria === 'ESPECIALIDADE_MEDICA')}
                                  {@const exames = sugestoes.filter((e:any) => e.categoria === 'EXAME_OU_PROCEDIMENTO')}
                                  <ul class="absolute z-50 w-full bg-white border border-gray-300 rounded-md mt-1 max-h-80 overflow-y-auto shadow-lg">
                                    {#if sugestoes.length === 0}
                                      <li class="p-2 text-sm text-gray-500 select-none">Nenhuma especialidade encontrada</li>
                                    {:else}
                                      {#if consultas.length > 0}
                                        <li class="px-2 py-1 text-[11px] font-semibold text-gray-500 uppercase tracking-wide">Consultas</li>
                                        {#each consultas as e (e.id)}
                                          <li class="px-2 py-1">
                                            <button type="button" class="w-full text-left px-2 py-2 hover:bg-emerald-50 cursor-pointer text-sm" onclick={() => { novaEspecialidadeObj.especialidadeId = e.id; termoBuscaNovaEsp = e.nome; comboboxEspAberto = false; }}>
                                              <div class="flex items-center justify-between">
                                                <span class="font-medium text-gray-900">{e.nome}</span>
                                                <span class="text-[10px] px-2 py-0.5 rounded-full bg-gray-100 text-gray-700">{prettyCategoria(e.categoria)}</span>
                                              </div>
                                              <div class="text-[11px] text-gray-500">{e.codigo}</div>
                                            </button>
                                          </li>
                                        {/each}
                                      {/if}
                                      {#if exames.length > 0}
                                        <li class="px-2 py-1 text-[11px] font-semibold text-gray-500 uppercase tracking-wide">Exames e Procedimentos</li>
                                        {#each exames as e (e.id)}
                                          <li class="px-2 py-1">
                                            <button type="button" class="w-full text-left px-2 py-2 hover:bg-emerald-50 cursor-pointer text-sm" onclick={() => { novaEspecialidadeObj.especialidadeId = e.id; termoBuscaNovaEsp = e.nome; comboboxEspAberto = false; }}>
                                              <div class="flex items-center justify-between">
                                                <span class="font-medium text-gray-900">{e.nome}</span>
                                                <span class="text-[10px] px-2 py-0.5 rounded-full bg-gray-100 text-gray-700">{prettyCategoria(e.categoria)}</span>
                                              </div>
                                              <div class="text-[11px] text-gray-500">{e.codigo}</div>
                                            </button>
                                          </li>
                                        {/each}
                                      {/if}
                                    {/if}
                                  </ul>
                                {/if}
                              </div>
                        </div>
                        <div>
                            <label for="prioridade-select" class="block text-sm font-medium text-gray-700 mb-1">Prioridade</label>
                            <select id="prioridade-select" bind:value={novaEspecialidadeObj.prioridade} class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500">
                           
                                <option value="NORMAL" disabled>Normal</option>
                                <option value="URGENTE">Urgente</option>
                                <option value="EMERGENCIA">Emergência</option>
                            </select>
                        </div>
                        <div>
                            <label for="retorno-select" class="block text-sm font-medium text-gray-700 mb-1">Em caso de retorno... </label>
                            <select id="retorno-select" bind:value={novaEspecialidadeObj.status} class="w-full border-gray-300 rounded-md shadow-sm">
                                <option value="AGUARDANDO" disabled>Selecione...</option>
                                <option value="RETORNO">Retorno</option>
                                <option value="RETORNO_POLICLINICA">Retorno Policlínica</option>
                            </select>
                        </div>
                    </div>
                    <button onclick={adicionarEspecialidade} class="mt-4 bg-emerald-700 text-white px-6 py-2 rounded-md hover:bg-emerald-800 transition-colors shadow">Adicionar</button>
                </section>
            {/if}
        </main>
    </div>
</div>
