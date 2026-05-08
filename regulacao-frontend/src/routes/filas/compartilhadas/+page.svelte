<script>
    import { onMount } from 'svelte';
    import { listarPactos, listarFeed, listarFeedEnviadas, claimEvento } from '$lib/pactosApi.js';

    // Estado
    let activeTab = 'recebidas';
    let pactos = [];
    let selectedPactoId = null;
    let feed = [];
    let loading = false;
    let feedEnviadas = [];
    let error = '';
    let statusMapa = new Map();
    let toasts = [];
    function pushToast(msg) {
        const id = Math.random().toString(36).slice(2);
        toasts = [...toasts, { id, msg }];
        setTimeout(() => { toasts = toasts.filter(t => t.id !== id); }, 4000);
    }

    async function carregarPactos() {
        try {
            pactos = await listarPactos();
            if (pactos.length > 0) {
                selectedPactoId = pactos[0].id;
                await carregarFeed();
            }
        } catch (e) {
            error = e.message || 'Erro ao carregar pactos';
        }
    }

    async function carregarFeed() {
        if (!selectedPactoId) return;
        loading = true;
        error = '';
        try {
            feed = await listarFeed(selectedPactoId);
        } catch (e) {
            error = e.message || 'Erro ao carregar feed';
        } finally {
            loading = false;
        }
    }

    async function carregarEnviadas() {
        if (!selectedPactoId) return;
        loading = true;
        error = '';
        try {
            const nova = await listarFeedEnviadas(selectedPactoId);
            // Detecta mudanças de status para notificar visualmente
            for (const item of nova) {
                const prev = statusMapa.get(item.eventoUuid);
                if (prev && prev !== item.status && item.status === 'CONSUMIDO') {
                    pushToast(`Solicitação \"${item.label}\" aceita por ${item.consumidoPorMunicipio || 'destino'}.`);
                }
                statusMapa.set(item.eventoUuid, item.status);
            }
            feedEnviadas = nova;
        } catch (e) {
            error = e.message || 'Erro ao carregar enviadas';
        } finally {
            loading = false;
        }
    }

    async function aceitar(eventoUuid) {
        try {
            const res = await claimEvento(selectedPactoId, eventoUuid);
            if (!res.claimed) {
                alert(res.message || 'Outro município já consumiu');
            } else {
                alert(res.detalhes ? 'Claim aceito. Detalhes recebidos.' : 'Claim aceito. Aguardando detalhes.');
                await carregarFeed();
            }
        } catch (e) {
            alert(e.message || 'Falha ao efetuar claim');
        }
    }

    onMount(carregarPactos);

    // Imports do seu layout
    import { user } from '$lib/stores/auth.js';
    import UserMenu from '$lib/UserMenu.svelte';
    import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';

    function formatDate(dt) {
        if (!dt) return '-';
        try {
            if (Array.isArray(dt) && dt.length >= 3) {
                const [y, mon, day, hh = 0, mm = 0, ss = 0, nanos = 0] = dt;
                const ms = Math.round((nanos || 0) / 1_000_000);
                const d2 = new Date(y, (mon - 1), day, hh, mm, ss, ms);
                return d2.toLocaleString('pt-BR');
            }
            const d3 = new Date(dt);
            if (!isNaN(d3)) return d3.toLocaleString('pt-BR');
        } catch {}
        return String(dt);
    }
</script>

<svelte:head>
    <title>Filas Compartilhadas</title>
</svelte:head>

<div class="flex min-h-screen bg-gray-100">

    <RoleBasedMenu activePage="/filas/compartilhadas" />

    <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
            <h1 class="text-xl font-semibold">Filas Compartilhadas</h1>
            {#if $user}
                <UserMenu />
            {:else}
                <div>
                    <a href="/login" class="hover:underline">Fazer Login</a>
                </div>
            {/if}
        </header>

        <main class="flex-1 overflow-x-hidden overflow-y-auto bg-gray-200 p-6">
            <div class="bg-white p-6 rounded-lg shadow-md">
                
                <div class="flex items-center justify-between mb-4">
                    <div class="flex items-center gap-2">
                        <label class="text-sm text-gray-600">Pacto:</label>
                        <select class="border rounded p-2 text-sm" bind:value={selectedPactoId} on:change={carregarFeed}>
                            {#each pactos as p}
                                <option value={p.id}>{p.nome}</option>
                            {/each}
                        </select>
                    </div>
                    <div class="text-sm text-gray-500" hidden={!loading}>Carregando...</div>
                </div>

                <div class="border-b border-gray-200 mb-4">
                    <nav class="-mb-px flex space-x-8" aria-label="Tabs">
                        <button
                            class="whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm"
                            class:border-emerald-500="{activeTab === 'recebidas'}"
                            class:text-emerald-600="{activeTab === 'recebidas'}"
                            class:border-transparent="{activeTab !== 'recebidas'}"
                            class:text-gray-500="{activeTab !== 'recebidas'}"
                            class:hover:text-gray-700="{activeTab !== 'recebidas'}"
                            class:hover:border-gray-300="{activeTab !== 'recebidas'}"
                            on:click={() => { activeTab = 'recebidas'; carregarFeed(); }}>
                            Solicitações Recebidas
                        </button>
                        <button
                            class="whitespace-nowrap py-4 px-1 border-b-2 font-medium text-sm"
                            class:border-emerald-500="{activeTab === 'enviadas'}"
                            class:text-emerald-600="{activeTab === 'enviadas'}"
                            class:border-transparent="{activeTab !== 'enviadas'}"
                            class:text-gray-500="{activeTab !== 'enviadas'}"
                            class:hover:text-gray-700="{activeTab !== 'enviadas'}"
                            class:hover:border-gray-300="{activeTab !== 'enviadas'}"
                            on:click={() => { activeTab = 'enviadas'; carregarEnviadas(); }}>
                            Solicitações Enviadas
                        </button>
                    </nav>
                </div>

                {#if error}
                    <div class="text-red-600 text-sm mb-4">{error}</div>
                {/if}

                {#if activeTab === 'recebidas'}
                    <div class="overflow-x-auto">
                        <table class="min-w-full bg-white">
                            <thead class="bg-gray-50">
                                <tr>
                                    <th class="py-3 px-4 text-left">Solicitação</th>
                                    <th class="py-3 px-4 text-left">Município de Origem</th>
                                    <th class="py-3 px-4 text-left">Publicado em</th>
                                    <th class="py-3 px-4 text-center">Ações</th>
                                </tr>
                            </thead>
                            <tbody class="text-gray-700">
                                {#if feed.length === 0}
                                    <tr><td class="py-6 px-4 text-gray-500" colspan="4">Nenhuma solicitação no pacto selecionado.</td></tr>
                                {/if}
                                {#each feed as item (item.eventoUuid)}
                                    <tr class="border-b">
                                        <td class="py-3 px-4">{item.label}</td>
                                        <td class="py-3 px-4">{item.municipioOrigem}</td>
                                        <td class="py-3 px-4">{formatDate(item.publishedAt)}</td>
                                        <td class="py-3 px-4 text-center">
                                            <button on:click={() => aceitar(item.eventoUuid)} class="bg-green-500 text-white px-3 py-1 rounded hover:bg-green-600 text-sm">Aceitar</button>
                                        </td>
                                    </tr>
                                {/each}
                            </tbody>
                        </table>
                    </div>
                {/if}

                {#if activeTab === 'enviadas'}
                     <div class="overflow-x-auto">
                        <table class="min-w-full bg-white">
                            <thead class="bg-gray-50">
                                <tr>
                                    <th class="py-3 px-4 text-left">Solicitação</th>
                                    <th class="py-3 px-4 text-left">Publicado em</th>
                                    <th class="py-3 px-4 text-left">Status</th>
                                    <th class="py-3 px-4 text-left">Consumido por</th>
                                    <th class="py-3 px-4 text-left">Consumido em</th>
                                </tr>
                            </thead>
                            <tbody class="text-gray-700">
                                {#if feedEnviadas.length === 0}
                                    <tr><td class="py-6 px-4 text-gray-500" colspan="5">Nenhuma solicitação enviada para o pacto selecionado.</td></tr>
                                {/if}
                                {#each feedEnviadas as item (item.eventoUuid)}
                                    <tr class="border-b">
                                        <td class="py-3 px-4">{item.label}</td>
                                        <td class="py-3 px-4">{formatDate(item.publishedAt)}</td>
                                        <td class="py-3 px-4">
                                            <span class="px-2 py-1 font-semibold leading-tight text-xs rounded-full"
                                                class:text-green-700="{item.status === 'CONSUMIDO'}" class:bg-green-100="{item.status === 'CONSUMIDO'}"
                                                class:text-yellow-700="{item.status === 'PUBLICADO'}" class:bg-yellow-100="{item.status === 'PUBLICADO'}">
                                                {item.status}
                                            </span>
                                        </td>
                                        <td class="py-3 px-4">{item.consumidoPorMunicipio ?? '-'}</td>
                                        <td class="py-3 px-4">{item.consumidoAt ? formatDate(item.consumidoAt) : '-'}</td>
                                    </tr>
                                {/each}
                            </tbody>
                        </table>
                    </div>
                {/if}

                <!-- Toasts -->
                {#if toasts.length > 0}
                <div class="fixed bottom-4 right-4 space-y-2 z-50">
                    {#each toasts as t (t.id)}
                        <div class="bg-emerald-600 text-white px-4 py-2 rounded shadow">{t.msg}</div>
                    {/each}
                </div>
                {/if}

            </div>
        </main>
    </div>
</div>
