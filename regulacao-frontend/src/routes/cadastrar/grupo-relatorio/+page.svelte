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



        } catch (error) {
            
        }
    }
</script>
<Content titleH1="Cadastrar Grupo de Relatório">
    <section>
        <div>
            <form onsubmit={cadastrarGrupo}>
                <div>
                    <label for="">Nome:</label>
                    <input type="text" placeholder="Digite o nome do Grupo" bind:value={form.codigo} required>
                </div>
                <div>
                    <label for="">Código:</label>
                    <input type="text" placeholder="Digite o Código de Identificação do Grupo" bind:value={form.nome} required>
                </div>

                <div>
                    <button type="submit">Cadastrar</button>
                </div>
            </form>
        </div>
    </section>

    <section></section>

</Content>