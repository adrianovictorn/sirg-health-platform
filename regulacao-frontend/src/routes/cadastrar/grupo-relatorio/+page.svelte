<script lang="ts">
    import { getApi, patchApi, patchApiData, postApi } from "$lib/api";
    import Content from "$lib/Content.svelte";
    import { listarGrupoRelatorio } from "$lib/especialidadesApi";
    import { onMount } from "svelte";

    interface GrupoRelatorio {
        id: number
        codigo: string
        nome: string
        ativo: boolean
        direcionadoHospital: boolean
    }
    type formGrupo = {
        codigo: string,
        nome: string,
        ativo: boolean,
        direcionadoHospital: boolean
    }

    let form = $state<formGrupo>({
        codigo: "",
        nome: "",
        ativo: true,
        direcionadoHospital: false
    })

    let idSelecionado = $state(0)
    let modoEdicao = $state(false)
    let Grupos = $state<GrupoRelatorio[]>([])

    async function buscarGrupos() {
        try {
            const res = await getApi('grupo-relatorio/listar')
            const data = await res.json();
            Grupos = data

            if(!res.ok){
                throw new Error('Erro ao receber informações do Grupo');
            }
        } catch (error) {
            throw new Error("Erro ao se conectar ao Servidor !");
            
        }
    }

    async function cadastrarGrupo(){
        const payload = {
            codigo: form.codigo,
            nome: form.nome,
            direcionadoHospital: form.direcionadoHospital
        }

        try {
            const res = await postApi(`grupo-relatorio/cadastrar`, payload)

            if(res.ok){
                alert("Novo Grupo Cadastrado com Sucesso !")
            }else{
                alert("Erro ao enviar dados ao servidor !");
            }

            form.codigo = ""
            form.nome = ""
            form.direcionadoHospital = false
            buscarGrupos()

        } catch (error) {
            
        }
    }

    async function ativarOrDesativarGrupo(id: number) {
        try {
            const res = await patchApi(`grupo-relatorio/ativarOrDesativar/${id}`)
            if(res.ok){
                alert("Grupo Relatório atualizado com sucesso !");
            }
            buscarGrupos()
        } catch (error) {
            
        }
        
    }

    async function atualizarGrupo(id: number) {
        const payload = {
            codigo: form.codigo,
            nome: form.nome,
            ativo: form.ativo,
            direcionadoHospital: form.direcionadoHospital
        }
        try{
            const res = await patchApiData(`grupo-relatorio/atualizar/${id}`, payload)

            if(!res.ok){
                throw new Error('Erro ao enviar dados de atualizar')
            }

            if(res.ok){
                alert("Paciente atualizado com sucesso !")
            }
            form.codigo = ""
            form.nome = ""
            form.direcionadoHospital = false
            modoEdicao = false
            buscarGrupos()
        }catch(error){
            throw new Error('Erro ao estabelecer conexão com o servidor !')
        }
    }

    function editarSelecionado(grupo: GrupoRelatorio){
        modoEdicao = true
        idSelecionado = grupo.id
        form.nome = grupo.nome
        form.codigo = grupo.codigo 
        form.direcionadoHospital = grupo.direcionadoHospital

    }

    onMount(() => {
        buscarGrupos()
    })
</script>
<Content page="/cadastrar/grupo-relatorio" titleH1="Cadastrar Grupo de Relatório">
    <section class="bg-gray-200 w-full  px-2">
        <div class="bg-white rounded-lg flex items-center p-5 mt-5 ">
            <form onsubmit={cadastrarGrupo} class="grid grid-cols-4 gap-5 w-full" class:grid-cols-5={modoEdicao}>
                <div class="flex flex-col">
                    <label for="" class="text-sm font-medium text-gray-700 mb-1">Nome:</label>
                    <input type="text" placeholder="Digite o nome do Grupo" class="border border-gray-300 rounded-lg p-2" bind:value={form.nome} required>
                </div>
                <div class="flex flex-col">
                    <label  class="text-sm font-medium text-gray-700 mb-1" for="">Código:</label>
                    <input type="text" class="border border-gray-300 rounded-lg p-2" placeholder="Digite o Código de Identificação do Grupo" bind:value={form.codigo} required>
                </div>
                <div class="flex flex-col">
                    <label  class="text-sm font-medium text-gray-700 mb-1" for="">Direcionado ao Hospital:</label>
                    <select name="" id="" bind:value={form.direcionadoHospital} class="border border-gray-300 rounded-lg p-2">
                        <option value={true}>Sim</option>
                        <option value={false}>Não</option>
                    </select>
                </div>
                {#if modoEdicao}
                     <div class="flex flex-col">
                        <label  class="text-sm font-medium text-gray-700 mb-1" for="">Ativo:</label>
                        <select name="" id="" bind:value={form.ativo} class="border border-gray-300 rounded-lg p-2">
                            <option value={true}>Ativo</option>
                            <option value={false}>Inativo</option>
                        </select>
                    </div>
                {/if}

                <div class="flex flex-col px-2 mt-5 justify-center">
                    
                    {#if modoEdicao}
                    <button type="button" onclick={()=> atualizarGrupo(idSelecionado)} class= "bg-blue-700 text-white px-4 py-2 rounded hover:bg-blue-800 cursor-pointer">Atualizar</button>
                    {:else}
                    <button type="submit" class= "bg-emerald-700 text-white px-4 py-2 rounded hover:bg-emerald-800 cursor-pointer">Cadastrar</button>

                    {/if}
                </div>
            </form>
        </div>
    </section>

    <section class= "px-2">

        <div class="bg-white p-10 mt-5 rounded-lg flex ">
            <table class="w-full text-left text-sm">
                <thead>
                  <tr class="border-b grid grid-cols-6">
                    <th class="py-2 px-2">Id</th>
                    <th class="py-2 px-2">Nome</th>
                    <th class="py-2 px-2">Código</th>
                    <th class="py-2 px-2">Ativo</th>
                    <th class="py-2 px-2">Direcionado ao Hospital</th>
                    <th class="py-2 px-2 text-end">Ações</th>
                  </tr>
                </thead>

                <tbody>
                    {#each Grupos as grupo }
                    <tr class="border-b grid grid-cols-6">
                            <td class="py-2 px-2">{grupo.id}</td>
                            <td class="py-2 px-2">{grupo.nome}</td>
                            <td class="py-2 px-2">{grupo.codigo}</td>
                            <td class="py-2 px-2">
                            {#if grupo.ativo}
                                Ativo
                                {:else}
                                Inativo
                            {/if}
                            </td>
                            <td class="py-2 px-2 text-center">
                                {#if grupo.direcionadoHospital}
                                    Sim
                                {:else}
                                    Não
                                {/if}
                            </td>
                            <td class="py-2 px-2 text-end">
                                <div>
                                <button type="button" aria-label="editar" title="Editar" class="cursor-pointer" onclick={() => editarSelecionado(grupo)}>
                                    <svg class="w-6 h-6 text-gray-800 dark:text-gray-800" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="none" viewBox="0 0 24 24">
                                    <path stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M18 5V4a1 1 0 0 0-1-1H8.914a1 1 0 0 0-.707.293L4.293 7.207A1 1 0 0 0 4 7.914V20a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-5M9 3v4a1 1 0 0 1-1 1H4m11.383.772 2.745 2.746m1.215-3.906a2.089 2.089 0 0 1 0 2.953l-6.65 6.646L9 17.95l.739-3.692 6.646-6.646a2.087 2.087 0 0 1 2.958 0Z"/>
                                    </svg>
                                </button>
                                        {#if grupo.ativo}
                                        <button type="button" onclick={() => ativarOrDesativarGrupo(grupo.id)} aria-label="inativar" title="Desativar especialidade" class="cursor-pointer">
                                            <svg class="text-green-800 dark:text-green-800 w-6 h-6" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="currentColor" viewBox="0 0 24 24">
                                            <path fill-rule="evenodd" d="M4 4a2 2 0 1 0 0 4h16a2 2 0 1 0 0-4H4Zm0 6h16v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-8Zm10.707 5.707a1 1 0 0 0-1.414-1.414l-.293.293V12a1 1 0 1 0-2 0v2.586l-.293-.293a1 1 0 0 0-1.414 1.414l2 2a1 1 0 0 0 1.414 0l2-2Z" clip-rule="evenodd"/>
                                            </svg>
                                        </button>
                                        {:else}
                                        <button type="button" onclick={() => ativarOrDesativarGrupo(grupo.id)} aria-label="inativar" title="Ativar especialidade" >
                                            <svg class=" text-red-800 dark:text-red-800 w-6 h-6" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="currentColor" viewBox="0 0 24 24">
                                            <path fill-rule="evenodd" d="M4 4a2 2 0 1 0 0 4h16a2 2 0 1 0 0-4H4Zm0 6h16v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-8Zm10.707 5.707a1 1 0 0 0-1.414-1.414l-.293.293V12a1 1 0 1 0-2 0v2.586l-.293-.293a1 1 0 0 0-1.414 1.414l2 2a1 1 0 0 0 1.414 0l2-2Z" clip-rule="evenodd"/>
                                            </svg>
                                        </button>
                                        {/if}
                                        </div>
                                    </td>
                            </tr>
                    {/each}
                </tbody>
            </table>

        </div>
    </section>

</Content>
