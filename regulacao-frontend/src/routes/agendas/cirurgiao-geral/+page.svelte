<script lang="ts">
    import { getApi, putApi } from "$lib/api";
    import Menu2 from "$lib/Menu2.svelte";
    import UserMenu from "$lib/UserMenu.svelte";
    import { onMount } from "svelte";

    // Variáveis de estado
    let solicitacoesDeHoje = $state([]);
    let erro = $state(null);
    let carregando = $state(true);
    let total = $state(0);
    let dataHoje = $state(getHojeFormatado());

    const especialidadesDoPainel = ['CIRURGIAO_GERAL'];

    function getHojeFormatado() {
        const hoje = new Date();
        const ano = hoje.getFullYear();
        const mes = String(hoje.getMonth() + 1).padStart(2, '0');
        const dia = String(hoje.getDate()).padStart(2, '0');
        return `${ano}-${mes}-${dia}`;
    }

    async function carregarSolicitacoes() {
        carregando = true;
        erro = null;
        try {
            const res = await getApi('solicitacoes?size=1000');
            if (!res.ok) {
                throw new Error('Falha ao carregar as solicitações');
            }
            const solicitacoes = await res.json();
            const todasSolicitacoes = await solicitacoes.content;
            const hoje = getHojeFormatado();
            
            solicitacoesDeHoje = todasSolicitacoes.filter(solicitacao =>
                solicitacao.especialidades.some(especialidade => {
                    const agendamento = solicitacao.agendamentos.find(ag => ag.id === especialidade.agendamentoId);
                    return (
                        especialidade.status === 'AGENDADO' &&
                        especialidadesDoPainel.includes(especialidade.especialidadeSolicitada) &&
                        agendamento && agendamento.dataAgendada === hoje
                    );
                })
            );
            
            total = solicitacoesDeHoje.length

        } catch (e) {
            erro = e.message;
        } finally {
            carregando = false;
        }
    }

    async function confirmarSolicitacao(especialidadeId: number, novoStatus: 'REALIZADO' | 'CANCELADO') {
        const acao = novoStatus === 'REALIZADO' ? 'confirmar a presença' : 'marcar a falta';
        if (confirm(`Tem certeza que deseja ${acao} deste paciente?`)) {
            try {
                const res = await putApi(`especialidades/${especialidadeId}`, { status: novoStatus });
                if (!res.ok) { throw new Error('Erro ao atualizar o status'); }
                await carregarSolicitacoes();
            } catch (e) {
                alert(e.message);
            }
        }
    }
    
    function formatarData(dataString) {
        if (!dataString) return 'Data não informada';
        const [ano, mes, dia] = dataString.split('-');
        return `${dia}/${mes}/${ano}`;
    }

    onMount(() => {
        carregarSolicitacoes();
    });

</script>

<svelte:head>
    <title>Agenda de Ortopedista</title>
</svelte:head>

<div class="flex min-h-screen bg-gray-100">
    <Menu2/>
    <div class="flex-1 flex flex-col">
        <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
            <h1 class="text-xl font-semibold">Agenda do Dia - Ortopedista</h1>
            <UserMenu />
        </header>

        <main class="flex-1 p-6 overflow-auto">
            {#if carregando}
                <p class="text-center text-gray-500 animate-pulse">Carregando painel...</p>
            {:else if erro}
                <p class="text-red-500 text-center">Erro: {erro}</p>
            {:else if solicitacoesDeHoje.length === 0}
                <div class="text-center p-10 bg-white rounded-lg shadow-sm">
                    <h2 class="text-xl font-semibold text-gray-700">Nenhum paciente para hoje.</h2>
                    <p class="text-gray-500 mt-2">Não há agendamentos para as especialidades selecionadas na data de hoje.</p>
                </div>
            {:else}
                <div class="py-3">
                    <h2><b>Total de Atendimentos previsto para hoje:</b> {total}</h2>
                    <h2><b>Data do Atendimento:</b> {formatarData(dataHoje)}</h2>
                </div>

                <div class="bg-white rounded-lg shadow-md">
                    <div class="grid grid-cols-12 gap-4 p-4 border-b font-bold text-emerald-800 bg-emerald-100 rounded-t-lg">
                        <div class="col-span-4">Paciente</div>
                        <div class="col-span-5">Detalhes do Agendamento</div>
                        <div class="col-span-3 text-right">Ações</div>
                    </div>

                    <div class="flex flex-col">
                        {#each solicitacoesDeHoje as s (s.id)}
                            <div class="grid grid-cols-12 gap-4 p-4 items-start border-t">
                                <div class="col-span-4">
                                    <p class="font-semibold text-gray-800">{s.nomePaciente}</p>
                                    <p class="text-sm text-gray-500 font-mono">CPF: {s.cpfPaciente}</p>
                                    <p class="text-sm text-gray-500 font-mono">CNS: {s.cns}</p>
                                </div>
                                
                                <div class="col-span-8 grid grid-cols-8 gap-4">
                                    {#each s.especialidades as especialidade (especialidade.id)}
                                        {@const agendamento = s.agendamentos.find(ag => ag.id === especialidade.agendamentoId)}

                                        {#if especialidade.status === 'AGENDADO' && especialidadesDoPainel.includes(especialidade.especialidadeSolicitada) && agendamento?.dataAgendada === getHojeFormatado()}
                                            <div class="col-span-5 py-2">
                                                <p class="font-medium text-gray-700 capitalize">{(especialidade.especialidadeSolicitada.replace(/_/g, ' ')).toLowerCase()}</p>
                                                <p class="text-sm text-blue-600">{formatarData(agendamento.dataAgendada)}</p>
                                            </div>
                                            <div class="col-span-3 flex justify-end items-center gap-2 py-2">
                                                <button on:click={() => confirmarSolicitacao(especialidade.id, 'REALIZADO')} class="rounded-md bg-blue-600 hover:bg-blue-700 text-white px-3 py-1.5 text-sm font-semibold shadow-sm transition-colors">Confirmar</button>
                                                <button on:click={() => confirmarSolicitacao(especialidade.id, 'CANCELADO')} class="rounded-md bg-red-600 hover:bg-red-700 text-white px-3 py-1.5 text-sm font-semibold shadow-sm transition-colors">Faltou</button>
                                            </div>
                                        {/if}
                                    {/each}
                                </div>
                            </div>
                        {/each}
                    </div>
                </div>
            {/if}
        </main>
    </div>
</div>