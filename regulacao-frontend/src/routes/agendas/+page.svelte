<script lang="ts">
    import { getApi, postApi, putApi } from "$lib/api";
    import Menu2 from "$lib/Menu2.svelte";
    import UserMenu from "$lib/UserMenu.svelte";
    import { onMount } from "svelte";

    let solicitacaosAgendadas = $state([]);
    let erro = $state(null);
    let carregando = $state(true);

async function carregarSolicitacoes() {
       try{
          const res = await getApi(`/solicitacoes`);
          if(!res.ok){
              throw new Error(`Falha ao receber dados`)
          }
          const todasSolicitacoes = await res.json();

          solicitacaosAgendadas = todasSolicitacoes.filter(s => s.especialidades.some(e => e.status === 'AGENDADO'))
       } catch(e){
        erro = e.message;
       }

       carregando = false;

}

async function confirmarSolicitacao(especialidadeId: number, novoStatus: 'REALIZADO' | 'CANCELADO'){
  try{ 
    const res = await putApi(`especialidades/${especialidadeId}`, {status: novoStatus})

    if(!res.ok){
      throw new Error('Erro ao atualizar status');
    }

    await carregarSolicitacoes();

  }catch(e){
    alert(e.message);
  }
}

onMount(()=> {

  carregarSolicitacoes();
})



</script>



<svelte:head>
    <title>Dashboard</title>
</svelte:head>


  <!-- O seu layout original é renderizado aqui somente após os dados serem carregados -->
  <div class="flex min-h-screen bg-gray-100">
    <!-- Sidebar -->
     
    <Menu2></Menu2>
  
   <!-- Main Content -->
    <div class="flex-1 flex flex-col">

      <!-- Header com boas-vindas e botão de logout -->
  
  
      <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
        <h1 class="text-xl font-semibold">Painel</h1>
        <UserMenu></UserMenu>
      </header>

      <!-- Dashboard Cards -->
      <main class="flex-1 p-6 overflow-auto">       

        {#if !carregando}

        {#if solicitacaosAgendadas && solicitacaosAgendadas.length > 0}
        <div class="space-y-4">
            {#each solicitacaosAgendadas as s}
            <div class="rounded shadow bg-white p-4">
              <p>Nome  do Paciente: {s.nomePaciente}</p>
              <div class="flex">
                <p class="font-bold">CPF:{s.cpfPaciente}</p>
                <p>USF: {s.usfOrigem}</p>
              </div>

              {#each s.especialidades as e}
              <div>
                <label >Especialidades:</label>
                <span>{e.especialidadeSolicitada}</span>
              </div>
              
              <div class="flex flex-row-reverse">
                                <button on:click={() => confirmarSolicitacao(e.id, 'CANCELADO')} class="rounded-lg bg-red-600 hover:bg-red-700 text-white px-3 py-1 cursor-pointer">Faltou</button>

                <button on:click={() => confirmarSolicitacao(e.id, 'REALIZADO')} class="rounded-lg bg-green-600 hover:bg-green-700 text-white px-3 py-1 cursor-pointer">Confirmar</button>
              </div>
              {/each}
            </div>
            {/each}
        </div>
         
        {:else}
          <p>Nenhuma solicitação encontrada</p>
        {/if}
        
          {:else}
          <p>Carregando painel...</p>  
        {/if}
        

         
     </main>
    
    </div>
  </div>

