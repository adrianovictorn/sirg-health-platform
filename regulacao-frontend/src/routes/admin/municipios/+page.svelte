<script>
  import { onMount } from 'svelte';
  import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';
  import UserMenu from '$lib/UserMenu.svelte';
  import { user } from '$lib/stores/auth.js';
  import { registrarPublico } from '$lib/registryApi.js';

  let nome = '';
  let cnes = '';
  let rabbitQueueName = '';
  let baseUrl = '';
  let loading = false;
  let error = '';
  let success = '';

  async function registrar() {
    error = ''; success = '';
    if (!nome || !cnes || !rabbitQueueName) {
      error = 'Preencha Nome, CNES e Fila (RabbitMQ).';
      return;
    }
    try {
      loading = true;
      await registrarPublico({ nome, cnes, rabbitQueueName, baseUrl });
      success = 'Município registrado com sucesso! Ele aparecerá na lista de disponíveis para convites.';
      nome=''; cnes=''; rabbitQueueName=''; baseUrl='';
    } catch (e) {
      error = e.message || 'Falha ao registrar município.';
    } finally {
      loading = false;
    }
  }
</script>

<svelte:head><title>Registrar Município</title></svelte:head>

<div class="flex min-h-screen bg-gray-100">
  <RoleBasedMenu activePage="/admin/municipios" />
  <div class="flex-1 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Registrar Município</h1>
      {#if $user}<UserMenu />{:else}<div><a href="/login" class="hover:underline">Fazer Login</a></div>{/if}
    </header>

    <main class="flex-1 overflow-x-hidden overflow-y-auto bg-gray-200 p-6">
      <div class="bg-white p-6 rounded-lg shadow-md space-y-4 max-w-3xl">
        {#if error}<div class="text-red-600 text-sm">{error}</div>{/if}
        {#if success}<div class="text-emerald-700 text-sm">{success}</div>{/if}

        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label class="block text-sm text-gray-600 mb-1">Nome (identificador)</label>
            <input class="border rounded p-2 w-full" bind:value={nome} placeholder="CONCEICAO_DO_ALMEIDA" />
            <p class="text-xs text-gray-500 mt-1">Use o identificador canônico usado nas routing keys (sem espaços, maiúsculas e underscores).</p>
          </div>
          <div>
            <label class="block text-sm text-gray-600 mb-1">CNES</label>
            <input class="border rounded p-2 w-full" bind:value={cnes} placeholder="123456" />
          </div>
          <div>
            <label class="block text-sm text-gray-600 mb-1">Fila RabbitMQ (queue)</label>
            <input class="border rounded p-2 w-full" bind:value={rabbitQueueName} placeholder="fila_conceicao_do_almeida" />
            <p class="text-xs text-gray-500 mt-1">A fila deve estar bindada ao exchange regional_topic_exchange.</p>
          </div>
          <div>
            <label class="block text-sm text-gray-600 mb-1">Base URL (opcional)</label>
            <input class="border rounded p-2 w-full" bind:value={baseUrl} placeholder="https://api.seumunicipio.gov.br" />
          </div>
        </div>

        <div>
          <button class="bg-emerald-600 text-white px-4 py-2 rounded disabled:opacity-60" on:click={registrar} disabled={loading}>
            {#if loading}Enviando...{:else}Registrar{/if}
          </button>
        </div>
      </div>
    </main>
  </div>
</div>

