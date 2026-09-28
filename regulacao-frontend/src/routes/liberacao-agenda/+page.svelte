<script>
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { getApi } from '$lib/api.js';
  import Content from '$lib/Content.svelte';
  import { toast } from 'svelte-sonner';

  let agendas = $state([]);
  let loading = $state(true);

  onMount(carregar);

  async function carregar() {
    loading = true;
    try {
      const res = await getApi('agendas');
      agendas = await res.json();
    } catch {
      toast.error('Erro ao carregar agendas.');
    } finally {
      loading = false;
    }
  }

  function formatarDias(diasSemana) {
    return (diasSemana ?? '').split(',').filter(Boolean).join(', ');
  }
</script>

<Content titleH1="Liberação de Agenda" page="/liberacao-agenda">
  <main class="p-6 space-y-4">
    <div class="flex justify-end">
      <button onclick={() => goto('/liberacao-agenda/nova')}
        class="px-4 py-2 rounded-lg bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-medium transition-colors">
        + Abrir Agenda
      </button>
    </div>

    {#if loading}
      <p class="text-gray-500 text-sm">Carregando...</p>
    {:else if agendas.length === 0}
      <p class="text-gray-500 text-sm">Nenhuma agenda cadastrada ainda.</p>
    {:else}
      <div class="overflow-x-auto rounded-xl border border-gray-200 bg-white">
        <table class="w-full text-sm text-gray-700">
          <thead class="bg-gray-50 text-gray-500 uppercase text-xs">
            <tr>
              <th class="px-4 py-3 text-left">Executante</th>
              <th class="px-4 py-3 text-left">Profissional</th>
              <th class="px-4 py-3 text-left">Oferta</th>
              <th class="px-4 py-3 text-left">Vigência</th>
              <th class="px-4 py-3 text-left">Dias</th>
              <th class="px-4 py-3 text-left">Horário</th>
              <th class="px-4 py-3 text-left">Status</th>
              <th class="px-4 py-3 text-left">Ações</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-200">
            {#each agendas as a (a.id)}
              <tr class="hover:bg-gray-50 transition-colors">
                <td class="px-4 py-3 font-medium text-gray-900">{a.estabelecimentoExecutanteNome}</td>
                <td class="px-4 py-3">{a.profissionalNome}</td>
                <td class="px-4 py-3">
                  {#if a.tipoOferta === 'GRUPO'}
                    <span class="px-2 py-0.5 rounded text-xs font-medium bg-sky-100 text-sky-700">Grupo</span>
                    <span class="ml-2">{a.grupoEspecialidadesNome}</span>
                  {:else}
                    {a.especialidades?.[0]?.nome ?? '—'}
                  {/if}
                </td>
                <td class="px-4 py-3">{a.vigenciaInicio} a {a.vigenciaFim}</td>
                <td class="px-4 py-3">{formatarDias(a.diasSemana)}</td>
                <td class="px-4 py-3">{a.horaInicial} – {a.horaFinal}</td>
                <td class="px-4 py-3">
                  <span class="px-2 py-0.5 rounded-full text-xs font-medium {a.ativo ? 'bg-emerald-100 text-emerald-700' : 'bg-red-100 text-red-700'}">
                    {a.ativo ? 'Ativa' : 'Inativa'}
                  </span>
                </td>
                <td class="px-4 py-3">
                  <button onclick={() => goto(`/liberacao-agenda/${a.id}`)}
                    class="px-3 py-1 rounded bg-blue-50 text-blue-700 hover:bg-blue-100 text-xs transition-colors">
                    Detalhes
                  </button>
                </td>
              </tr>
            {/each}
          </tbody>
        </table>
      </div>
    {/if}
  </main>
</Content>
