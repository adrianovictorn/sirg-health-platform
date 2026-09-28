<script>
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { page } from '$app/stores';
  import { getApi, putApi, postApi, deleteByIdApi } from '$lib/api.js';
  import Content from '$lib/Content.svelte';
  import { toast } from 'svelte-sonner';

  const agendaId = $page.params.id;

  let agenda = $state(null);
  let carregando = $state(true);

  // { distribuicaoUnidadeId: novoValor } enquanto o operador digita
  let edicaoVagas = $state({});

  let remanejo = $state({ ocorrenciaId: null, unidadeOrigemId: null, unidadeDestinoId: null, quantidade: 1 });

  onMount(carregar);

  async function carregar() {
    carregando = true;
    try {
      const res = await getApi(`agendas/${agendaId}`);
      if (!res.ok) {
        toast.error('Agenda não encontrada.');
        goto('/liberacao-agenda');
        return;
      }
      agenda = await res.json();
    } catch {
      toast.error('Erro ao carregar agenda.');
    } finally {
      carregando = false;
    }
  }

  async function salvarVagas(unidadeSolicitanteId) {
    const novoValor = Number(edicaoVagas[unidadeSolicitanteId]);
    if (!novoValor || novoValor <= 0) { toast.error('Informe uma quantidade maior que zero.'); return; }
    try {
      const res = await putApi(`agendas/${agendaId}`, {
        localDescricao: agenda.localDescricao,
        observacao: agenda.observacao,
        ativo: agenda.ativo,
        distribuicoes: [{ unidadeSolicitanteId, vagasPorOcorrencia: novoValor }]
      });
      if (!res.ok) {
        const erro = await res.json().catch(() => ({}));
        toast.error(erro.message ?? 'Erro ao atualizar vagas.');
        return;
      }
      toast.success('Vagas atualizadas.');
      delete edicaoVagas[unidadeSolicitanteId];
      edicaoVagas = { ...edicaoVagas };
      await carregar();
    } catch {
      toast.error('Erro ao atualizar vagas.');
    }
  }

  async function desativarAgenda() {
    try {
      const res = await deleteByIdApi(`agendas/${agendaId}`);
      if (!res.ok) { toast.error('Erro ao desativar agenda.'); return; }
      toast.success('Agenda desativada.');
      await carregar();
    } catch {
      toast.error('Erro ao desativar agenda.');
    }
  }

  async function cancelarOcorrencia(ocorrenciaId) {
    try {
      const res = await postApi(`agendas/${agendaId}/ocorrencias/${ocorrenciaId}/cancelar`, {});
      if (!res.ok) {
        const erro = await res.json().catch(() => ({}));
        toast.error(erro.message ?? 'Erro ao cancelar ocorrência.');
        return;
      }
      toast.success('Ocorrência cancelada. Saldo não utilizado foi fechado.');
      await carregar();
    } catch {
      toast.error('Erro ao cancelar ocorrência.');
    }
  }

  async function remanejarVagas() {
    if (!remanejo.ocorrenciaId || !remanejo.unidadeOrigemId || !remanejo.unidadeDestinoId) {
      toast.error('Preencha ocorrência, unidade de origem e unidade de destino.');
      return;
    }
    if (remanejo.unidadeOrigemId === remanejo.unidadeDestinoId) {
      toast.error('Origem e destino precisam ser unidades diferentes.');
      return;
    }
    try {
      const res = await postApi(`agendas/${agendaId}/remanejar`, {
        ocorrenciaId: Number(remanejo.ocorrenciaId),
        unidadeOrigemId: Number(remanejo.unidadeOrigemId),
        unidadeDestinoId: Number(remanejo.unidadeDestinoId),
        quantidade: Number(remanejo.quantidade)
      });
      if (!res.ok) {
        const erro = await res.json().catch(() => ({}));
        toast.error(erro.message ?? 'Erro ao remanejar vagas.');
        return;
      }
      toast.success('Vagas remanejadas.');
      remanejo = { ocorrenciaId: null, unidadeOrigemId: null, unidadeDestinoId: null, quantidade: 1 };
    } catch {
      toast.error('Erro ao remanejar vagas.');
    }
  }

  const ocorrenciasAbertas = $derived(agenda?.ocorrencias?.filter((o) => o.status === 'ABERTA') ?? []);
</script>

<Content titleH1="Detalhe da Agenda" page="/liberacao-agenda">
  <main class="p-6 max-w-4xl space-y-4">
    {#if carregando}
      <p class="text-gray-500 text-sm">Carregando...</p>
    {:else if agenda}
      <div class="bg-white border border-gray-200 rounded-xl p-6 space-y-3">
        <div class="flex items-center justify-between">
          <h2 class="text-sm font-semibold text-gray-800">Dados da agenda</h2>
          <div class="flex items-center gap-2">
            <span class="px-2 py-0.5 rounded-full text-xs font-medium {agenda.ativo ? 'bg-emerald-100 text-emerald-700' : 'bg-red-100 text-red-700'}">
              {agenda.ativo ? 'Ativa' : 'Inativa'}
            </span>
            {#if agenda.ativo}
              <button onclick={desativarAgenda}
                class="px-3 py-1 rounded bg-red-50 text-red-600 hover:bg-red-100 text-xs transition-colors">
                Desativar agenda
              </button>
            {/if}
          </div>
        </div>
        <dl class="grid grid-cols-2 gap-x-6 gap-y-2 text-sm">
          <div><dt class="text-gray-500 text-xs">Executante</dt><dd class="text-gray-900">{agenda.estabelecimentoExecutanteNome}</dd></div>
          <div><dt class="text-gray-500 text-xs">Profissional</dt><dd class="text-gray-900">{agenda.profissionalNome}</dd></div>
          <div><dt class="text-gray-500 text-xs">CBO</dt><dd class="text-gray-900">{agenda.cboDescricao ?? '—'}</dd></div>
          <div><dt class="text-gray-500 text-xs">Local</dt><dd class="text-gray-900">{agenda.localDescricao ?? agenda.localAgendamentoNome ?? '—'}</dd></div>
          <div>
            <dt class="text-gray-500 text-xs">Oferta</dt>
            <dd class="text-gray-900">
              {#if agenda.tipoOferta === 'GRUPO'}
                Grupo — {agenda.grupoEspecialidadesNome}
                <span class="text-gray-500">({agenda.especialidades.map((e) => e.nome).join(', ')})</span>
              {:else}
                {agenda.especialidades?.[0]?.nome ?? '—'}
              {/if}
            </dd>
          </div>
          <div><dt class="text-gray-500 text-xs">Vigência</dt><dd class="text-gray-900">{agenda.vigenciaInicio} a {agenda.vigenciaFim}</dd></div>
          <div><dt class="text-gray-500 text-xs">Dias</dt><dd class="text-gray-900">{agenda.diasSemana}</dd></div>
          <div><dt class="text-gray-500 text-xs">Horário</dt><dd class="text-gray-900">{agenda.horaInicial} – {agenda.horaFinal}</dd></div>
          {#if agenda.observacao}
            <div class="col-span-2"><dt class="text-gray-500 text-xs">Observação</dt><dd class="text-gray-900">{agenda.observacao}</dd></div>
          {/if}
        </dl>
      </div>

      <div class="bg-white border border-gray-200 rounded-xl p-6 space-y-3">
        <h2 class="text-sm font-semibold text-gray-800">Distribuição de vagas por unidade</h2>
        <div class="divide-y divide-gray-200">
          {#each agenda.distribuicoes as d (d.id)}
            <div class="flex items-center justify-between py-2">
              <span class="text-sm text-gray-800">{d.unidadeSolicitanteNome}</span>
              <div class="flex items-center gap-2">
                <input type="number" min="1"
                  value={edicaoVagas[d.unidadeSolicitanteId] ?? d.vagasPorOcorrencia}
                  oninput={(e) => (edicaoVagas[d.unidadeSolicitanteId] = e.target.value)}
                  class="w-20 bg-white border border-gray-300 rounded-lg px-2 py-1 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
                <span class="text-xs text-gray-500">vagas/ocorrência</span>
                <button onclick={() => salvarVagas(d.unidadeSolicitanteId)}
                  class="px-3 py-1 rounded bg-blue-50 text-blue-700 hover:bg-blue-100 text-xs transition-colors">
                  Salvar
                </button>
              </div>
            </div>
          {/each}
        </div>
        <p class="text-xs text-gray-500">
          Alterar aqui só afeta as ocorrências ainda abertas (não altera datas já canceladas) e
          não permite reduzir abaixo do já utilizado nelas.
        </p>
      </div>

      <div class="bg-white border border-gray-200 rounded-xl p-6 space-y-3">
        <h2 class="text-sm font-semibold text-gray-800">Ocorrências materializadas</h2>
        <div class="overflow-x-auto">
          <table class="w-full text-sm text-gray-700">
            <thead class="bg-gray-50 text-gray-500 uppercase text-xs">
              <tr>
                <th class="px-3 py-2 text-left">Data</th>
                <th class="px-3 py-2 text-left">Horário</th>
                <th class="px-3 py-2 text-left">Status</th>
                <th class="px-3 py-2 text-left">Ações</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-gray-200">
              {#each agenda.ocorrencias as o (o.id)}
                <tr>
                  <td class="px-3 py-2">{o.data}</td>
                  <td class="px-3 py-2">{o.horaInicial} – {o.horaFinal}</td>
                  <td class="px-3 py-2">
                    <span class="px-2 py-0.5 rounded-full text-xs font-medium {o.status === 'ABERTA' ? 'bg-emerald-100 text-emerald-700' : 'bg-red-100 text-red-700'}">
                      {o.status === 'ABERTA' ? 'Aberta' : 'Cancelada'}
                    </span>
                  </td>
                  <td class="px-3 py-2">
                    {#if o.status === 'ABERTA'}
                      <button onclick={() => cancelarOcorrencia(o.id)}
                        class="px-3 py-1 rounded bg-red-50 text-red-600 hover:bg-red-100 text-xs transition-colors">
                        Cancelar
                      </button>
                    {/if}
                  </td>
                </tr>
              {/each}
            </tbody>
          </table>
        </div>
      </div>

      {#if agenda.distribuicoes.length > 1 && ocorrenciasAbertas.length > 0}
        <div class="bg-white border border-gray-200 rounded-xl p-6 space-y-3">
          <h2 class="text-sm font-semibold text-gray-800">Remanejar vagas não utilizadas entre unidades</h2>
          <p class="text-xs text-gray-500">
            Move vagas sobrando de uma ocorrência específica entre as unidades solicitantes desta
            agenda. Vaga não usada não vaza sozinha entre unidades — isto é sempre uma decisão manual.
          </p>
          <div class="grid grid-cols-4 gap-3 items-end">
            <div>
              <label class="block text-xs text-gray-500 mb-1">Ocorrência</label>
              <select bind:value={remanejo.ocorrenciaId}
                class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500">
                <option value={null}>Selecionar...</option>
                {#each ocorrenciasAbertas as o (o.id)}
                  <option value={o.id}>{o.data}</option>
                {/each}
              </select>
            </div>
            <div>
              <label class="block text-xs text-gray-500 mb-1">De</label>
              <select bind:value={remanejo.unidadeOrigemId}
                class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500">
                <option value={null}>Selecionar...</option>
                {#each agenda.distribuicoes as d (d.id)}
                  <option value={d.unidadeSolicitanteId}>{d.unidadeSolicitanteNome}</option>
                {/each}
              </select>
            </div>
            <div>
              <label class="block text-xs text-gray-500 mb-1">Para</label>
              <select bind:value={remanejo.unidadeDestinoId}
                class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500">
                <option value={null}>Selecionar...</option>
                {#each agenda.distribuicoes as d (d.id)}
                  <option value={d.unidadeSolicitanteId}>{d.unidadeSolicitanteNome}</option>
                {/each}
              </select>
            </div>
            <div>
              <label class="block text-xs text-gray-500 mb-1">Quantidade</label>
              <input type="number" min="1" bind:value={remanejo.quantidade}
                class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
            </div>
          </div>
          <div class="flex justify-end">
            <button onclick={remanejarVagas}
              class="px-4 py-2 rounded-lg bg-emerald-600 text-white text-sm hover:bg-emerald-500 transition-colors">
              Remanejar
            </button>
          </div>
        </div>
      {/if}
    {/if}
  </main>
</Content>
