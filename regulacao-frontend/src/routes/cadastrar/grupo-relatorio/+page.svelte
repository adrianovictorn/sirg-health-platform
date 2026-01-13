<script lang="ts">
    import { postApi } from "$lib/api";
    import Content from "$lib/Content.svelte";

    type formGrupo = {
        codigo: string,
        nome: string
    }

    let form = $state<formGrupo>({
        codigo: "",
        nome: ""
    })

    async function cadastrarGrupo(){
        const payload = {
            codigo: form.codigo,
            nome: form.nome
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


        } catch (error) {
            
        }
    }
</script>
<Content page="/cadastrar/grupo-relatorio" titleH1="Cadastrar Grupo de Relatório">
    <section class="bg-gray-200 h-screen w-full  px-2">
        <div class="bg-white rounded-lg flex items-center p-5 mt-5 ">
            <form onsubmit={cadastrarGrupo} class="grid grid-cols-3 gap-5 w-full">
                <div class="flex flex-col">
                    <label for="" class="text-sm font-medium text-gray-700 mb-1">Nome:</label>
                    <input type="text" placeholder="Digite o nome do Grupo" class="border border-gray-300 rounded-lg p-2" bind:value={form.codigo} required>
                </div>
                <div class="flex flex-col">
                    <label  class="text-sm font-medium text-gray-700 mb-1" for="">Código:</label>
                    <input type="text" class="border border-gray-300 rounded-lg p-2" placeholder="Digite o Código de Identificação do Grupo" bind:value={form.nome} required>
                </div>

                <div class="flex flex-col px-2 mt-5 justify-center">
                    
                    <button type="submit" class= "bg-emerald-700 text-white px-4 py-2 rounded hover:bg-emerald-800">Cadastrar</button>
                </div>
            </form>
        </div>
    </section>

    <section></section>

</Content>