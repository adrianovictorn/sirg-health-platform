<script lang=ts>
    import { page } from "$app/state";
    import { getApi, patchApi, putApi } from "$lib/api";
    import Content from "$lib/Content.svelte";
    import { onMount } from "svelte";


    type Page<T> = {
        content: T[];
        totalPages: number
        totalElements: number
        number: number
        size: number
    }

    type PainelEspecialidadeProjection = {
        solicitacaoId: number
        cpfPaciente: string
        usfOrigem: string
        dataNascimento: string
        cns: string
        nomePaciente: string
        especialidades: string
        solicitacaoEspecialidadeId: number
        
    }

    const grupo = $derived(() => page.params.especialidade)
    const data = $derived(() => page.url.searchParams.get("data") ?? new Date().toISOString().slice(0,10))
    let carregando = $state(false)
    let pacientes = $state<PainelEspecialidadeProjection[]>([])
    let pageData = $state<Page<PainelEspecialidadeProjection> | null>(null)
    let pageIndex = $state(0)
    let pageSize = $state(10)
    let adicionarObservacao = $state(false)
    let observacaoFaltou = $state('')
    let idSelecionado = $state(0)
    

    async function  listarPacientesAgendadosPorDataEGrupoELocal(grupo: string, data: string) {
        const params = new URLSearchParams()
        params.append("page", String(pageIndex))
        params.append("size", String(pageSize))
        params.append("grupo", grupo)
        params.append("data", data)

        try {
            const res = await getApi(`especialidades/listar/pacientes/por/grupo?${params.toString()}`)
            if(!res.ok){
                throw new Error("Erro ao receber dados de Paciente !");
            }
            const json: Page<PainelEspecialidadeProjection> = await res.json()
            pageData = json
            pacientes = json.content ?? []
        } catch (error) {
            throw new Error("Erro ao fazer conexão com o servidor !");
        }
    }

    async function pacienteCompareceu(id: number) {
        try {
            const res = await patchApi(`especialidades/${id}/realizado`)

            if(res.ok){
                alert("Paciente atualizado com sucesso !")
            }
            const g = grupo()
            const d = data()
            listarPacientesAgendadosPorDataEGrupoELocal(g,d)
        } catch (error) {
            throw new Error("Erro ao estabelecer conexão com o servidor !")
        }
    }

    async function pacienteFaltou(id: number) {
        const payload = { observacao:  observacaoFaltou }
       try{
        const res = await putApi(`especialidades/${id}/faltou`,payload)
        if(!res.ok){
            throw new Error("Erro ao enviar dados de atualização")
        }
        
        idSelecionado = 0
        observacaoFaltou = ""

        if(res.ok){
            alert("Paciente atualizado com sucesso !")
        }

        const g = grupo()
        const d = data()
        listarPacientesAgendadosPorDataEGrupoELocal(g,d)
       }catch(error){
        throw new Error("Erro ao estabelecer conexão com o servidor !")
       }
    }

    function abrirObservacao(paciente: PainelEspecialidadeProjection){
        const id = paciente.solicitacaoEspecialidadeId;
        const confirmar = confirm("Deseja incluir uma observação para esse paciente ?")
        if(confirmar === true){
        idSelecionado = id
        observacaoFaltou = "";
        return;
       }else{
        idSelecionado = id
        pacienteFaltou(idSelecionado)
       }
    }

    function cancelarObservacao(){
        idSelecionado = 0
    }

    function proximaPagina(){
        pageIndex += 1
    }

    function paginaAnterior(){
        if(pageIndex <= pageData.totalPages && pageIndex >= 1){
            pageIndex -= 1
        }
    }

    function irParaPagina(pagina: number){
        pageIndex = pagina -1
    }

    function formatarData(data?: string | number[] | Date | null) {
        if (data == null) return "";

        if (data instanceof Date) {
            return new Intl.DateTimeFormat("pt-BR").format(data);
        }

        if (Array.isArray(data)) {
            const [y, m, d] = data;
            const date = new Date(Date.UTC(y, m - 1, d));
            return new Intl.DateTimeFormat("pt-BR", { timeZone: "UTC" }).format(date);
        }

        const s = String(data).trim(); 

        const base = s.includes("T") ? s.slice(0, 10) : s;
        const sep = base.includes("-") ? "-" : ",";

        const [y, m, d] = base.split(sep);

        const date = new Date(Date.UTC(+y, +m - 1, +d));

        return new Intl.DateTimeFormat("pt-BR", { timeZone: "UTC" }).format(date);
    }


    onMount(() => {
        const g =grupo()
        const d = data()
        listarPacientesAgendadosPorDataEGrupoELocal(g,d)
    })

    $effect(() => {
        const g = grupo()
        const d = data()
        if(!g) return

        listarPacientesAgendadosPorDataEGrupoELocal(g,d)
    })
</script>
<svelte:head>
    
    <title>Agenda de {grupo().toLowerCase()}</title>
</svelte:head>

<Content titleH1={`Lista de Pacientes - ${grupo()}`} page="">

    <main class="flex-1 p-6 overflow-auto">
            {#if carregando}
                <p class="text-center text-gray-500 animate-pulse">Carregando painel...</p>
            {:else if pacientes.length === 0}
                <div class="text-center p-10 bg-white rounded-lg shadow-sm">
                    <h2 class="text-xl font-semibold text-gray-700">Nenhum paciente para hoje.</h2>
                    <p class="text-gray-500 mt-2">Não há agendamentos para as especialidades selecionadas na data de hoje.</p>
                </div>
            {:else}
                <div class="py-3">
                    <h2><b>Total de Atendimentos previsto para hoje:</b> {pageData.totalElements}</h2>
                    <h2><b>Data do Atendimento: </b> {formatarData(data())} </h2>
                </div>

                <div class="bg-white rounded-lg shadow-md">
                    <div class="grid grid-cols-12 gap-4 p-4 border-b font-bold text-emerald-800 bg-emerald-100 rounded-t-lg">
                        <div class="col-span-4">Paciente</div>
                        <div class="col-span-5">Detalhes do Agendamento</div>
                        <div class="col-span-3 text-right">Ações</div>
                    </div>

                    <div class="flex flex-col">
                        {#each pacientes as s (s.solicitacaoId)}
                            <div class="grid grid-cols-12 gap-4 p-4 items-start border-t">
                                <div class="col-span-10">
                                    <div class="grid grid-cols-3">
                                        <p class="font-semibold text-gray-800">{s.nomePaciente}</p>
                                        <p class="text-sm text-gray-500 font-mono"> <span class="text-gray-800 font-semibold">Data Nascimento:</span> {formatarData(s.dataNascimento)}</p>
                                        <p class="text-sm text-gray-500 font-mono"> <span class="text-gray-800 font-semibold">CNS:</span> {s.cns}</p>
                                    </div>
                                    <div class="grid grid-cols-3">
                                        <p class="text-sm text-gray-500 font-mono"><span class="text-gray-800 font-semibold">CPF:</span> {s.cpfPaciente}</p>
                                        <p class="text-sm text-gray-500 font-mono"> <span class="text-gray-800 font-semibold">Especialidade:</span> {s.especialidades}</p>
                                        <p class="text-sm text-gray-500 font-mono"><span class="text-gray-800 font-semibold">USF Origem:</span> {s.usfOrigem}</p>
                                    </div>
                                    {#if idSelecionado === s.solicitacaoEspecialidadeId}
                                    <div class="grid grid-cols-1 mt-2">
                                        <p class="text-sm text-gray-800 font-semibold ">Observação:</p>
                                        <textarea name="" id="" class="rounded-lg mt-1" bind:value={observacaoFaltou}></textarea>
                                    </div>
                                    {/if}
                                </div>
                                <div class="col-span-2 grid grid-cols-8 gap-4">
                                    <div class="col-span-5 py-2">
                                    </div>
                                    <div class="col-span-3 flex justify-end items-center gap-2 py-2">
                                        <button type="button" onclick={() => pacienteCompareceu(s.solicitacaoEspecialidadeId)} class="bg-blue-600 hover:bg-blue-800  text-white p-2 rounded-lg cursor-pointer" hidden={idSelecionado === s.solicitacaoEspecialidadeId}>Confirmar</button>
                                        <button type="button" onclick={() => abrirObservacao(s)} class="bg-red-600 hover:bg-red-800 text-white p-2 rounded-lg cursor-pointer" hidden={idSelecionado === s.solicitacaoEspecialidadeId}>Faltou</button>
                                        <button type="button" onclick={() => pacienteFaltou(s.solicitacaoEspecialidadeId)} class="bg-green-600 hover:bg-green-800 text-white p-2 rounded-lg cursor-pointer" hidden={idSelecionado !== s.solicitacaoEspecialidadeId}>Salvar</button>
                                        <button type="button" onclick={() => cancelarObservacao()} class="bg-red-600 hover:bg-red-800 text-white p-2 rounded-lg cursor-pointer" aria-label="Cancelar" hidden={idSelecionado !== s.solicitacaoEspecialidadeId}>
                                            <svg class="w-6 h-6 text-gray-800 dark:text-white" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="none" viewBox="0 0 24 24">
                                                <path stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18 17.94 6M18 18 6.06 6"/>
                                            </svg>

                                        </button>
                                    </div>
                                </div>
                            </div>
                        {/each}
                    </div>
                    <div class="bg-emerald-100 text-emerald-800 font-bold rounded-b-lg text-center flex gap-2 justify-center p-2">
                        <button type="button" onclick={paginaAnterior} hidden={pageIndex <=0}>Anterior</button>
                        <button type="button" >{pageIndex + 1} de</button>
                        <button type="button" onclick={() => irParaPagina(pageData.totalPages)}>{pageData.totalPages}</button>
                        <button type="button" onclick={proximaPagina} hidden={pageIndex +1 >= pageData.totalPages}>Próximo</button>
                    </div>
                </div>
            {/if}
        </main>
        
</Content>