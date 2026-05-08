<script>
  import { onMount } from 'svelte';
  import { fly } from 'svelte/transition';
  import { user, profilePicture, refreshProfilePicture } from '$lib/stores/auth.js';
  import { getApi, putApi, postApiFile, deleteByIdApi } from '$lib/api.js';
  import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';
  import UserMenu from '$lib/UserMenu.svelte';

  let isEditing = false;
  let nomeUsuario = $user?.nome || '';
  let isMounted = false;

  let uploading = false;
  let uploadError = '';
  let saving = false;
  let saveError = '';
  let saveSuccess = false;

  let fileInput;
  let previewUrl = $profilePicture;

  // Keep previewUrl in sync with the store
  $: previewUrl = $profilePicture;

  let currentUserId = null;

  onMount(async () => {
    isMounted = true;
    await refreshProfilePicture();
    // Fetch current user id
    try {
      const res = await getApi('users/me');
      if (res.ok) {
        const data = await res.json();
        currentUserId = data.id;
      }
    } catch (e) {
      console.error(e);
    }
  });

  function handleFileSelect(event) {
    const file = event.target.files?.[0];
    if (!file) return;

    const allowed = ['image/jpeg', 'image/png', 'image/gif', 'image/webp'];
    if (!allowed.includes(file.type)) {
      uploadError = 'Tipo de arquivo não permitido. Use JPEG, PNG, GIF ou WebP.';
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      uploadError = 'Arquivo excede o tamanho máximo de 5 MB.';
      return;
    }

    uploadError = '';
    // Show local preview
    const reader = new FileReader();
    reader.onload = (e) => { previewUrl = e.target.result; };
    reader.readAsDataURL(file);

    uploadFoto(file);
  }

  async function uploadFoto(file) {
    if (!currentUserId) return;
    uploading = true;
    uploadError = '';
    try {
      const formData = new FormData();
      formData.append('file', file);
      const res = await postApiFile(`users/${currentUserId}/foto`, formData);
      if (res.ok) {
        const updated = await res.json();
        profilePicture.set(updated.fotoUrl);
      } else {
        const text = await res.text();
        uploadError = text || 'Erro ao enviar foto.';
        previewUrl = $profilePicture; // revert preview
      }
    } catch (e) {
      uploadError = 'Erro de conexão ao enviar foto.';
      previewUrl = $profilePicture;
    } finally {
      uploading = false;
    }
  }

  async function removerFoto() {
    if (!currentUserId) return;
    uploading = true;
    uploadError = '';
    try {
      const res = await deleteByIdApi(`users/${currentUserId}/foto`);
      if (res.ok) {
        profilePicture.set(null);
      } else {
        uploadError = 'Erro ao remover foto.';
      }
    } catch (e) {
      uploadError = 'Erro de conexão ao remover foto.';
    } finally {
      uploading = false;
    }
  }

  async function handleSaveChanges() {
    if (!currentUserId) return;
    saving = true;
    saveError = '';
    saveSuccess = false;
    try {
      const res = await putApi(`users/${currentUserId}`, {
        nome: nomeUsuario,
        cpf: $user.cpf,
        password: '',
        role: $user.role
      });
      if (res.ok) {
        saveSuccess = true;
        isEditing = false;
        setTimeout(() => saveSuccess = false, 3000);
      } else {
        saveError = 'Erro ao salvar alterações.';
      }
    } catch (e) {
      saveError = 'Erro de conexão.';
    } finally {
      saving = false;
    }
  }
</script>

<svelte:head>
    <title>Perfil</title>
</svelte:head>

<div class="flex min-h-screen bg-gray-50 font-sans">
  <RoleBasedMenu activePage="/perfil" />

  <div class="flex-1 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between z-10">
      <h1 class="text-xl font-semibold">Meu Perfil</h1>
      <UserMenu />
    </header>

    <main class="flex-1 p-4 md:p-8 overflow-auto">
      {#if isMounted}
        <div class="max-w-3xl mx-auto" in:fly={{ y: 20, duration: 500, delay: 100 }}>
          <div class="bg-white rounded-lg shadow-xl overflow-hidden">
            <div class="bg-emerald-500 h-32" />

            <div class="p-6 md:p-8 relative">
              <!-- Avatar section -->
              <div class="absolute -mt-24">
                <div class="relative group">
                  {#if previewUrl}
                    <img
                      src={previewUrl}
                      alt="Foto de perfil"
                      class="w-28 h-28 rounded-full object-cover ring-4 ring-white"
                    />
                  {:else}
                    <div class="w-28 h-28 rounded-full bg-emerald-100 flex items-center justify-center text-emerald-600 text-5xl font-bold ring-4 ring-white">
                      {($user?.nome || 'U').charAt(0).toUpperCase()}
                    </div>
                  {/if}

                  <!-- Upload overlay -->
                  <button
                    type="button"
                    disabled={uploading}
                    on:click={() => fileInput.click()}
                    class="absolute inset-0 rounded-full bg-black/50 flex flex-col items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity cursor-pointer"
                    title="Alterar foto"
                  >
                    {#if uploading}
                      <svg class="w-6 h-6 text-white animate-spin" fill="none" viewBox="0 0 24 24">
                        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/>
                        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z"/>
                      </svg>
                    {:else}
                      <svg class="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 9a2 2 0 012-2h.93a2 2 0 001.664-.89l.812-1.22A2 2 0 0110.07 4h3.86a2 2 0 011.664.89l.812 1.22A2 2 0 0018.07 7H19a2 2 0 012 2v9a2 2 0 01-2 2H5a2 2 0 01-2-2V9z"/>
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 13a3 3 0 11-6 0 3 3 0 016 0z"/>
                      </svg>
                      <span class="text-white text-xs mt-1">Alterar</span>
                    {/if}
                  </button>

                  <input
                    bind:this={fileInput}
                    type="file"
                    accept="image/jpeg,image/png,image/gif,image/webp"
                    class="hidden"
                    on:change={handleFileSelect}
                  />
                </div>
              </div>

              <div class="flex justify-between items-start pt-8">
                <div class="ml-32">
                  <h2 class="text-3xl font-bold text-gray-800">{$user?.nome}</h2>
                  <p class="text-sm text-gray-500 mt-1">
                    Nível de Acesso:
                    <span class="font-semibold bg-emerald-100 text-emerald-800 py-0.5 px-2 rounded-full">{$user?.role}</span>
                  </p>
                  {#if $profilePicture}
                    <button
                      type="button"
                      disabled={uploading}
                      on:click={removerFoto}
                      class="mt-2 text-xs text-red-500 hover:text-red-700 hover:underline disabled:opacity-50"
                    >
                      Remover foto
                    </button>
                  {/if}
                </div>
                <div>
                  {#if !isEditing}
                    <button
                      on:click={() => { isEditing = true; nomeUsuario = $user?.nome || ''; }}
                      class="flex items-center px-4 py-2 text-sm font-medium text-white bg-emerald-600 rounded-lg hover:bg-emerald-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-emerald-500 transition-all duration-200 shadow-sm"
                    >
                      <svg class="w-4 h-4 mr-2" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.536L16.732 3.732z"/>
                      </svg>
                      Editar
                    </button>
                  {/if}
                </div>
              </div>

              {#if uploadError}
                <p class="mt-4 text-sm text-red-600 bg-red-50 border border-red-200 rounded px-3 py-2">{uploadError}</p>
              {/if}
              {#if saveSuccess}
                <p class="mt-4 text-sm text-emerald-700 bg-emerald-50 border border-emerald-200 rounded px-3 py-2">Perfil atualizado com sucesso!</p>
              {/if}
              {#if saveError}
                <p class="mt-4 text-sm text-red-600 bg-red-50 border border-red-200 rounded px-3 py-2">{saveError}</p>
              {/if}

              <hr class="my-8 border-gray-200" />

              <form on:submit|preventDefault={handleSaveChanges} class="space-y-8">
                <div class="relative">
                  <label for="nome" class="absolute -top-2.5 left-2 z-10 inline-block bg-white px-1 text-xs font-medium text-gray-600">
                    Nome Completo
                  </label>
                  <div class="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3">
                    <svg class="h-5 w-5 text-gray-400" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                      <path fill-rule="evenodd" d="M10 9a3 3 0 100-6 3 3 0 000 6zm-7 9a7 7 0 1114 0H3z" clip-rule="evenodd"/>
                    </svg>
                  </div>
                  <input
                    type="text"
                    id="nome"
                    bind:value={nomeUsuario}
                    disabled={!isEditing}
                    class="w-full pl-10 pr-4 py-2.5 border border-gray-300 rounded-md bg-gray-50 focus:ring-emerald-500 focus:border-emerald-500 transition disabled:opacity-70 disabled:bg-gray-100"
                  />
                </div>

                <div class="relative">
                  <label for="cpf" class="absolute -top-2.5 left-2 z-10 inline-block bg-white px-1 text-xs font-medium text-gray-600">
                    CPF
                  </label>
                  <div class="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3">
                    <svg class="h-5 w-5 text-gray-400" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor">
                      <path d="M4 4a2 2 0 00-2 2v1h16V6a2 2 0 00-2-2H4z"/>
                      <path fill-rule="evenodd" d="M18 9H2v5a2 2 0 002 2h12a2 2 0 002-2V9zM4 13a1 1 0 011-1h1a1 1 0 110 2H5a1 1 0 01-1-1zm3 1a1 1 0 100-2 1 1 0 000 2zm3-1a1 1 0 11-2 0 1 1 0 012 0zm3 1a1 1 0 100-2 1 1 0 000 2z" clip-rule="evenodd"/>
                    </svg>
                  </div>
                  <input
                    type="text"
                    id="cpf"
                    value={$user?.cpf}
                    disabled
                    class="w-full pl-10 pr-4 py-2.5 border border-gray-300 rounded-md bg-gray-200 text-gray-500 cursor-not-allowed"
                  />
                </div>

                {#if isEditing}
                  <div class="flex justify-end space-x-4 pt-4">
                    <button
                      type="button"
                      on:click={() => { isEditing = false; saveError = ''; }}
                      class="px-6 py-2 text-sm font-medium text-gray-700 bg-gray-200 rounded-lg hover:bg-gray-300 transition-colors"
                    >
                      Cancelar
                    </button>
                    <button
                      type="submit"
                      disabled={saving}
                      class="px-6 py-2 text-sm font-medium text-white bg-emerald-600 rounded-lg hover:bg-emerald-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-emerald-500 transition-colors disabled:opacity-50"
                    >
                      {saving ? 'Salvando...' : 'Salvar Alterações'}
                    </button>
                  </div>
                {/if}
              </form>

              <p class="mt-6 text-xs text-gray-400 text-center">
                Formatos aceitos: JPEG, PNG, GIF, WebP · Tamanho máximo: 5 MB
              </p>
            </div>
          </div>
        </div>
      {/if}
    </main>
  </div>
</div>
