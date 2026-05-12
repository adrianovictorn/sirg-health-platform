<script>
  import { getApi } from '$lib/api';
  import { onMount } from 'svelte';

  export let usuario;
  export let aberto = false;
  export let onClose = () => {};
  export let onSave = (data) => {};

  let nome = '';
  let cpf = '';
  let password = '';
  let role = '';
  let unidadeId = null;
  let unidades = [];

  onMount(async () => {
    const res = await getApi('unidades/ativas');
    if (res.ok) unidades = await res.json();
  });

  $: if (aberto) {
    nome = usuario?.nome ?? '';
    cpf = usuario?.cpf ?? '';
    password = '';
    role = usuario?.role ?? '';
    unidadeId = usuario?.unidadeId ?? null;
  }
</script>

{#if aberto}
  <div class="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
    <div class="bg-white p-6 rounded-xl max-w-md w-full shadow-xl">

      <!-- User avatar header -->
      <div class="flex items-center gap-3 mb-5 pb-4 border-b border-gray-100">
        {#if usuario?.fotoUrl}
          <img src={usuario.fotoUrl} alt="Foto de {usuario.nome}" class="w-12 h-12 rounded-full object-cover ring-2 {usuario?.ativo ? 'ring-emerald-200' : 'ring-red-200'}" />
        {:else}
          <div class="w-12 h-12 rounded-full {usuario?.ativo ? 'bg-emerald-100 text-emerald-700' : 'bg-gray-100 text-gray-400'} flex items-center justify-center font-bold text-lg">
            {(usuario?.nome || 'U').charAt(0).toUpperCase()}
          </div>
        {/if}
        <div>
          <div class="flex items-center gap-2">
            <p class="font-semibold text-gray-800">{usuario?.nome}</p>
            {#if usuario?.ativo}
              <span class="text-xs font-medium px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-700">Ativo</span>
            {:else}
              <span class="text-xs font-medium px-2 py-0.5 rounded-full bg-red-100 text-red-700">Desativado</span>
            {/if}
          </div>
          <p class="text-sm text-gray-500">{usuario?.cpf}</p>
        </div>
      </div>

      <div class="space-y-3">
        <div>
          <label for="edit-nome" class="block text-xs font-medium text-gray-600 mb-1">Nome</label>
          <input id="edit-nome" type="text" bind:value={nome} class="border border-gray-300 rounded-md p-2 w-full focus:ring-emerald-500 focus:border-emerald-500" />
        </div>
        <div>
          <label for="edit-cpf" class="block text-xs font-medium text-gray-600 mb-1">CPF</label>
          <input id="edit-cpf" type="text" bind:value={cpf} class="border border-gray-300 rounded-md p-2 w-full focus:ring-emerald-500 focus:border-emerald-500" />
        </div>
        <div>
          <label for="edit-password" class="block text-xs font-medium text-gray-600 mb-1">Nova Senha (opcional)</label>
          <input id="edit-password" type="password" bind:value={password} placeholder="Deixe em branco para não alterar" class="border border-gray-300 rounded-md p-2 w-full focus:ring-emerald-500 focus:border-emerald-500" />
        </div>
        <div>
          <label for="edit-role" class="block text-xs font-medium text-gray-600 mb-1">Cargo</label>
          <select id="edit-role" bind:value={role} class="border border-gray-300 rounded-md p-2 w-full focus:ring-emerald-500 focus:border-emerald-500">
            <option value="ADMIN">Administrador</option>
            <option value="USER">Usuário Padrão</option>
            <option value="ENFERMEIRO">Enfermeiro</option>
            <option value="MEDICO">Médico</option>
            <option value="RECEPCAO">Recepcionista</option>
            <option value="COORD_TRANSPORTE">Coordenador(a) de Transporte</option>
          </select>
        </div>
        <div>
          <label for="edit-unidade" class="block text-xs font-medium text-gray-600 mb-1">Unidade de Saúde</label>
          <select id="edit-unidade" bind:value={unidadeId} class="border border-gray-300 rounded-md p-2 w-full focus:ring-emerald-500 focus:border-emerald-500">
            <option value={null}>— Sem unidade (ADMIN) —</option>
            {#each unidades as u}
              <option value={u.id}>{u.nome}</option>
            {/each}
          </select>
        </div>
      </div>

      <div class="mt-5 flex justify-end gap-2">
        <button
          type="button"
          class="px-4 py-2 rounded-md bg-gray-200 hover:bg-gray-300 text-gray-700 text-sm font-medium transition-colors"
          on:click={onClose}
        >
          Cancelar
        </button>
        <button
          type="button"
          class="px-4 py-2 rounded-md bg-emerald-600 hover:bg-emerald-700 text-white text-sm font-medium transition-colors"
          on:click={() => onSave({ id: usuario.id, nome, cpf, password, role, unidadeId })}
        >
          Salvar
        </button>
      </div>
    </div>
  </div>
{/if}
