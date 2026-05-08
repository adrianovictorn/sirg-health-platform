<script>
    import { getApi, putApi, patchApi } from "$lib/api";
    import Menu from "$lib/Menu.svelte";
    import ModalEditarUsuarios from "$lib/ModalEditarUsuarios.svelte";
    import UserMenu from "$lib/UserMenu.svelte";

    import { onMount } from "svelte";


    let usuarios = $state([]);
    let modalAberto =  $state(false);;
    let usuarioSelecionado = $state(null);

    function abrirModal(user){
        usuarioSelecionado = user;
        modalAberto = true;
        console.log('Passou aqui')
    }

    function fecharModal(){
        modalAberto = false;
    }
async function listarUsuarios() {
    try {
        const res = await getApi("users");
        if (!res.ok) {
            alert('Erro ao receber dados');
            return;
        }
        usuarios = await res.json();
    } catch (e) {
        console.error(e);
    }
}

async function toggleStatus(user) {
    try {
        const res = await patchApi(`users/${user.id}/status`);
        if (res.ok) {
            const updated = await res.json();
            usuarios = usuarios.map((u) => u.id === updated.id ? updated : u);
        } else {
            const data = await res.json().catch(() => ({}));
            alert(data.message || 'Erro ao alterar status do usuário.');
        }
    } catch (e) {
        console.error(e);
        alert('Erro de conexão ao alterar status.');
    }
}

async function atualizarUsuario(userData) {
    try {
        // Faz a requisição para a API
      const res = await putApi(`users/${userData.id}`,userData)

        // Verifica se a requisição foi bem-sucedida
        if (res.ok) {
            // Pega os dados atualizados do usuário que a API retornou
            const usuarioAtualizado = await res.json();

            // Atualiza a lista local de usuários de forma reativa
            // O .map cria um novo array, o que ajuda o Svelte a detectar a mudança
            usuarios = usuarios.map((u) =>
                u.id === usuarioAtualizado.id ? usuarioAtualizado : u
            );
            
            alert('Usuário atualizado com sucesso!');
            fecharModal(); // Fecha o modal após o sucesso

        } else {
            // Se a API retornou um erro (ex: 404, 500)
            alert('Erro ao atualizar usuário.');
            console.error('Erro na resposta da API:', await res.text());
        }

    } catch (err) {
        // Se ocorreu um erro de rede ou outro problema
        console.error("Erro ao tentar atualizar:", err);
        alert("Erro na atualização.");
    }
}

onMount( () => {
    listarUsuarios();
})

</script>

<div class="flex min-h-screen bg-gray-100">
  
  <Menu activePage="/admin/listar-usuarios" />

  <div class="flex-1 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Listagem de Usuários</h1>
      <UserMenu />
    </header>

      <main >
            <ModalEditarUsuarios
                usuario={usuarioSelecionado}
                aberto={modalAberto}
                onClose={fecharModal}
                onSave={atualizarUsuario}
            />
            
       <div class="flex flex-col gap-4 p-6">
        {#each usuarios as users}
            <div class="bg-white rounded-xl shadow-sm border p-5 hover:shadow-md transition-shadow flex items-center gap-4
              {users.ativo ? 'border-gray-200' : 'border-red-200 bg-red-50/30'}">
              <div class="flex-shrink-0">
                {#if users.fotoUrl}
                  <img src={users.fotoUrl} alt="Foto de {users.nome}" class="w-12 h-12 rounded-full object-cover ring-2 {users.ativo ? 'ring-emerald-200' : 'ring-red-200'}" />
                {:else}
                  <div class="w-12 h-12 rounded-full {users.ativo ? 'bg-emerald-100 text-emerald-700 ring-emerald-200' : 'bg-gray-100 text-gray-400 ring-red-200'} flex items-center justify-center font-bold text-lg ring-2">
                    {(users.nome || 'U').charAt(0).toUpperCase()}
                  </div>
                {/if}
              </div>
              <div class="flex-1 min-w-0">
                <div class="flex items-center gap-2 mb-1">
                  <h2 class="text-lg font-semibold {users.ativo ? 'text-emerald-700' : 'text-gray-400'} truncate">{users.nome}</h2>
                  {#if users.ativo}
                    <span class="text-xs font-medium px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-700">Ativo</span>
                  {:else}
                    <span class="text-xs font-medium px-2 py-0.5 rounded-full bg-red-100 text-red-700">Desativado</span>
                  {/if}
                </div>
                <p class="text-gray-600 text-sm"><strong>CPF:</strong> {users.cpf}</p>
                <p class="text-gray-600 text-sm"><strong>Cargo:</strong> {users.role}</p>
              </div>
              <div class="flex-shrink-0 flex gap-2">
                <button
                  class="text-sm px-3 py-2 rounded transition-colors font-medium
                    {users.ativo
                      ? 'text-red-600 border border-red-200 hover:bg-red-50'
                      : 'text-emerald-700 border border-emerald-200 hover:bg-emerald-50'}"
                  on:click={() => toggleStatus(users)}
                  title={users.ativo ? 'Desativar usuário' : 'Reativar usuário'}
                >
                  {users.ativo ? 'Desativar' : 'Reativar'}
                </button>
                <button class="text-sm text-white bg-emerald-600 hover:bg-emerald-700 px-3 py-2 rounded transition-colors" on:click={() => abrirModal(users)}>
                  Editar
                </button>
              </div>
            </div>
        {/each}
        </div>

      
      </main>
  </div>


</div>
