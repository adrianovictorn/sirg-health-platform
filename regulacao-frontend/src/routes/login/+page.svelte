<script lang="ts">
  // --- IMPORTS ---
  import { goto } from '$app/navigation';
  import { postApi } from '$lib/api.js';
  import { token } from '$lib/stores/auth.js'; // Importa nossa store de autenticação

  // --- ESTADO DO COMPONENTE ---
  let cpf = ''; // Trocamos 'email' por 'cpf'
  let password = '';
  let error = '';
  let errorType = ''; // 'disabled' | 'credentials' | 'generic'
  let loading = false;
  let showPassword = false;

  // --- FUNÇÃO DE LOGIN ---
async function handleLogin() {
  loading = true;
  error = '';
  errorType = '';

  try {
    const response = await postApi('auth/login', { cpf, password });

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      if (response.status === 403) {
        errorType = 'disabled';
        throw new Error(errorData.message || 'Conta desativada. Entre em contato com o administrador.');
      }
      errorType = 'credentials';
      throw new Error(errorData.message || 'CPF ou senha inválidos.');
    }

    const data = await response.json();
    token.set(data.token);
    goto('/home');
  } catch (e: any) {
    error = e.message;
  } finally {
    loading = false;
  }
}
</script>

<svelte:head>
    <title>Login - SIRG</title>
</svelte:head>

<div class="flex items-center justify-center min-h-screen bg-gradient-to-br from-gray-100 to-emerald-100 p-4">

  <div class="w-full max-w-md p-8 space-y-8 bg-white/70 backdrop-blur-xl rounded-2xl shadow-2xl border border-white/20">
    
    <div class="text-center">
      <div class="inline-block p-3 bg-emerald-700 rounded-full mb-4">
        <svg class="w-8 h-8 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.622 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.602-3.751m-.225-4.45c-.322-.16-.649-.297-.99-.402M19.95 10.63c.24-.263.468-.536.682-.824" />
        </svg>
      </div>
      <h1 class="text-3xl font-bold text-gray-900">SIRG</h1>
      <p class="mt-2 text-gray-600">Acesse o painel de controle</p>
    </div>

    <form class="space-y-6" on:submit|preventDefault={handleLogin}>
      
      <div>
        <label for="cpf" class="text-sm font-semibold text-gray-700">CPF</label>
        <div class="relative mt-1">
          <span class="absolute inset-y-0 left-0 flex items-center pl-3 pointer-events-none">
            <svg class="w-5 h-5 text-gray-400" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
              <path d="M10 8a3 3 0 100-6 3 3 0 000 6zM3.465 14.493a1.23 1.23 0 002.46 0A7.5 7.5 0 0010 12.5a7.5 7.5 0 004.075 1.993 1.23 1.23 0 002.46 0A10.502 10.502 0 0010 15c-4.418 0-8.268-2.926-9.535-7.007a1.23 1.23 0 002.46 0A7.5 7.5 0 0010 12.5a7.5 7.5 0 00-6.535 1.993z" />
            </svg>
          </span>
          <input id="cpf" type="text" bind:value={cpf} required class="pl-10 block w-full px-4 py-2.5 text-gray-800 bg-white/50 border border-gray-300 rounded-lg focus:border-emerald-500 focus:ring-emerald-500" placeholder="Digite seu CPF" />
        </div>
      </div>

      <div>
        <label for="password" class="text-sm font-semibold text-gray-700">Senha</label>
        <div class="relative mt-1">
            <span class="absolute inset-y-0 left-0 flex items-center pl-3 pointer-events-none">
              <svg class="w-5 h-5 text-gray-400" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                <path fill-rule="evenodd" d="M10 1a4.5 4.5 0 00-4.5 4.5V9H5a2 2 0 00-2 2v6a2 2 0 002 2h10a2 2 0 002-2v-6a2 2 0 00-2-2h-.5V5.5A4.5 4.5 0 0010 1zm3 8V5.5a3 3 0 10-6 0V9h6z" clip-rule="evenodd" />
              </svg>
            </span>
            <input id="password" type={showPassword ? 'text' : 'password'} bind:value={password} required class="pl-10 pr-10 block w-full px-4 py-2.5 text-gray-800 bg-white/50 border border-gray-300 rounded-lg focus:border-emerald-500 focus:ring-emerald-500" placeholder="••••••••" />
            <button type="button" on:click={() => showPassword = !showPassword} class="absolute inset-y-0 right-0 flex items-center px-3 text-gray-500 hover:text-emerald-600">
              {#if showPassword}
                <svg class="w-5 h-5" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M3.707 2.293a1 1 0 00-1.414 1.414l14 14a1 1 0 001.414-1.414l-1.473-1.473A10.014 10.014 0 0019.542 10C18.27 6.957 15.176 5 12 5c-1.258 0-2.447.316-3.527.882l-2.162-2.162zM12 15a3 3 0 110-6 3 3 0 010 6z" clip-rule="evenodd" /><path d="M2 10a9.954 9.954 0 013.373-6.627l1.458 1.458A8.01 8.01 0 004.458 10c1.272 3.043 4.366 5 7.542 5 1.257 0 2.447-.316 3.527-.882l1.458 1.458A9.955 9.955 0 012 10z" /></svg>
              {:else}
                <svg class="w-5 h-5" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor"><path d="M10 12.5a2.5 2.5 0 100-5 2.5 2.5 0 000 5z" /><path fill-rule="evenodd" d="M.664 10.59a1.651 1.651 0 010-1.18l.879-.679a1.651 1.651 0 012.073.228l.679.879a1.651 1.651 0 002.073.228l.879-.679a1.651 1.651 0 012.073.228l.679.879a1.651 1.651 0 002.073.228l.879-.679a1.651 1.651 0 012.073.228l.679.879a1.651 1.651 0 002.073.228l.879-.679a1.651 1.651 0 010 1.18l-.879.679a1.651 1.651 0 01-2.073-.228l-.679-.879a1.651 1.651 0 00-2.073-.228l-.879.679a1.651 1.651 0 01-2.073-.228l-.679-.879a1.651 1.651 0 00-2.073-.228l-.879.679a1.651 1.651 0 01-2.073-.228l-.679-.879A1.651 1.651 0 00.664 10.59zM10 15.5a5.5 5.5 0 100-11 5.5 5.5 0 000 11z" clip-rule="evenodd" /></svg>
              {/if}
            </button>
        </div>
      </div>

      {#if error}
        {#if errorType === 'disabled'}
          <div class="flex items-start gap-2 p-3 rounded-lg bg-amber-50 border border-amber-200">
            <svg class="w-5 h-5 text-amber-500 shrink-0 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
            </svg>
            <p class="text-sm text-amber-800">{error}</p>
          </div>
        {:else}
          <p class="text-sm text-red-600 text-center">{error}</p>
        {/if}
      {/if}

      <div class="pt-2">
        <button type="submit" disabled={loading} class="w-full px-4 py-3 font-bold text-white bg-emerald-700 rounded-lg shadow-lg hover:bg-emerald-800 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-emerald-500 disabled:bg-gray-500 transition-all duration-300">
          {#if loading}
            <span class="animate-pulse">Entrando...</span>
          {:else}
            Entrar
          {/if}
        </button>
      </div>

      <div class="text-center">
        <a href="#" class="text-sm text-emerald-600 hover:underline">Esqueceu a senha?</a>
      </div>
    </form>
  </div>
</div>
