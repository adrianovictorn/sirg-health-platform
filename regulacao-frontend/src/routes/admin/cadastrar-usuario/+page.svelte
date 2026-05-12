<script lang="ts">
  import { goto } from '$app/navigation';
  import { postApi, getApi } from '$lib/api.js';
  import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';
  import UserMenu from '$lib/UserMenu.svelte';
  import { onMount } from 'svelte';

  // --- ESTADO DO FORMULÁRIO ---
  let nome = '';
  let cpf = '';
  let password = '';
  let passwordConfirm = '';
  let cargo = 'USER';
  let unidadeId: number | null = null;
  let unidades: { id: number; nome: string }[] = [];

  let isLoading = false;
  let errors: { [key: string]: string } = {};

  onMount(async () => {
    const res = await getApi('unidades/ativas');
    if (res.ok) unidades = await res.json();
  });

  // --- FUNÇÕES ---

  // Valida e submete o formulário para a API
  async function cadastrarUsuario() {
    errors = {};
    isLoading = true;

    // Validação de senha no frontend
    if (password !== passwordConfirm) {
      errors.passwordConfirm = 'As senhas não coincidem.';
      isLoading = false;
      return;
    }

    const payload = {
      nome,
      cpf: cpf.replace(/\D/g, ''),
      password,
      cargo,
      unidadeId
    };

    try {
      const res = await postApi('users', payload); // Usa o endpoint /api/users

      if (res.ok) {
        alert('Usuário cadastrado com sucesso!');
        limparCampos();
        goto('/admin/cadastrar-usuario'); // Permanece na página para cadastrar outro
      } else {
        const errorData = await res.json();
        if (errorData.message) {
          alert(`Erro: ${errorData.message}`);
        } else {
          alert(`Erro desconhecido: ${res.status}`);
        }
      }
    } catch (err) {
      alert('Erro de conexão com o servidor.');
      console.error(err);
    } finally {
      isLoading = false;
    }
  }

  function limparCampos() {
    nome = '';
    cpf = '';
    password = '';
    passwordConfirm = '';
    cargo = 'USER';
    unidadeId = null;
  }

  function formatarCPF(e: Event) {
    const target = e.target as HTMLInputElement;
    let d = target.value.replace(/\D/g, '').slice(0, 11);
    d = d.replace(/^(\d{3})(\d)/, '$1.$2').replace(/^(\d{3}\.\d{3})(\d)/, '$1.$2').replace(/(\d{3}\.\d{3}\.\d{3})(\d)/, '$1-$2');
    cpf = d;
  }
</script>

<svelte:head>
    <title>Cadastro de Usuário</title>
</svelte:head>

<div class="flex min-h-screen bg-gray-100">
  <!-- Sidebar -->
  <RoleBasedMenu activePage = "/admin/cadastrar-usuario"/>

  <!-- Main Content -->
  <div class="flex-1 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Gerenciamento de Usuários</h1>
          <UserMenu/>
    </header>

    <main class="flex-1 overflow-auto p-6">
      <div class="bg-white rounded-lg shadow-lg p-6 max-w-2xl mx-auto">
        <h2 class="text-2xl font-bold text-emerald-800 mb-6">Cadastrar Novo Usuário</h2>
        
        <form on:submit|preventDefault={cadastrarUsuario} class="space-y-6">
          
          <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label for="nome" class="block text-sm font-medium text-gray-700 mb-1">Nome Completo</label>
              <input type="text" id="nome" bind:value={nome} class="w-full border border-gray-300 rounded-lg p-2" required />
            </div>
            <div>
              <label for="cpf" class="block text-sm font-medium text-gray-700 mb-1">CPF</label>
              <input type="text" id="cpf" bind:value={cpf} on:input={formatarCPF} class="w-full border border-gray-300 rounded-lg p-2" placeholder="000.000.000-00" required />
            </div>
          </div>
          
          <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label for="password" class="block text-sm font-medium text-gray-700 mb-1">Senha</label>
              <input type="password" id="password" bind:value={password} class="w-full border border-gray-300 rounded-lg p-2" required minlength="6" />
            </div>
            <div>
              <label for="passwordConfirm" class="block text-sm font-medium text-gray-700 mb-1">Confirmar Senha</label>
              <input type="password" id="passwordConfirm" bind:value={passwordConfirm} class="w-full border rounded-lg p-2" required 
                class:border-red-500={errors.passwordConfirm}
                class:border-gray-300={!errors.passwordConfirm}
              />
              {#if errors.passwordConfirm}
                <p class="text-xs text-red-600 mt-1">{errors.passwordConfirm}</p>
              {/if}
            </div>
          </div>

          <div>
            <label for="role" class="block text-sm font-medium text-gray-700 mb-1">Perfil de Acesso</label>
            <select id="role" bind:value={cargo} class="w-full border border-gray-300 rounded-lg p-2">
              <option value="ADMIN">Administrador</option>
              <option value="USER">Usuário Padrão</option>
              <option value="RECEPCAO">Recepcionista</option>
              <option value="ENFERMEIRO">Enfermeiro</option>
              <option value="MEDICO">Medico</option>
              <option value="PACIENTE">Paciente</option>
              <option value="COORD_TRANSPORTE">Coordenador(a) de Transporte</option>
            </select>
          </div>

          <div>
            <label for="unidade" class="block text-sm font-medium text-gray-700 mb-1">Unidade de Saúde</label>
            <select id="unidade" bind:value={unidadeId} class="w-full border border-gray-300 rounded-lg p-2">
              <option value={null}>— Sem unidade (ADMIN) —</option>
              {#each unidades as u}
                <option value={u.id}>{u.nome}</option>
              {/each}
            </select>
          </div>

          <div class="pt-4">
            <button type="submit" disabled={isLoading} class="w-full bg-emerald-800 text-white py-3 rounded-lg hover:bg-emerald-900 transition font-semibold disabled:bg-gray-400">
              {#if isLoading}Cadastrando...{:else}Cadastrar Usuário{/if}
            </button>
          </div>
        </form>
      </div>
    </main>
  </div>
</div>
