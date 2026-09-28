<script>
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { getApi, postApi } from '$lib/api.js';
  import Content from '$lib/Content.svelte';
  import { toast } from 'svelte-sonner';

  const DIAS = ['SEG', 'TER', 'QUA', 'QUI', 'SEX', 'SAB', 'DOM'];

  let executantes = $state([]);
  let profissionaisDoExecutante = $state([]);
  let cbos = $state([]);
  let especialidades = $state([]);
  let grupos = $state([]);
  let unidadesSolicitantes = $state([]);
  let salvando = $state(false);

  const hojeISO = new Date().toISOString().slice(0, 10);

  let form = $state({
    estabelecimentoExecutanteId: null,
    profissionalId: null,
    cboId: null,
    localDescricao: '',
    tipoOferta: 'INDIVIDUAL',
    especialidadeId: null,
    grupoEspecialidadesId: null,
    especialidadeIdsDoGrupo: [],
    vigenciaInicio: hojeISO,
    vigenciaFim: hojeISO,
    diasSemana: [],
    horaInicial: '08:00',
    horaFinal: '12:00',
    observacao: ''
  });

  let distribuicoes = $state([{ unidadeSolicitanteId: null, vagasPorOcorrencia: 1 }]);

  onMount(async () => {
    await Promise.all([carregarExecutantes(), carregarCbos(), carregarEspecialidades(),
      carregarGrupos(), carregarUnidadesSolicitantes()]);
  });

  async function carregarExecutantes() {
    try {
      const res = await getApi('unidades/executantes');
      executantes = await res.json();
    } catch { /* ignora - combo fica vazio */ }
  }

  async function carregarCbos() {
    try {
      const res = await getApi('cbos');
      cbos = await res.json();
    } catch { /* ignora - combo fica vazio */ }
  }

  async function carregarEspecialidades() {
    try {
      const res = await getApi('catalog/especialidades/listar');
      especialidades = await res.json();
    } catch { /* ignora - combo fica vazio */ }
  }

  async function carregarGrupos() {
    try {
      const res = await getApi('grupo-relatorio/listar');
      grupos = res.ok ? await res.json() : [];
    } catch { /* ignora - combo fica vazio */ }
  }

  async function carregarUnidadesSolicitantes() {
    try {
      const res = await getApi('unidades/ativas');
      unidadesSolicitantes = await res.json();
    } catch { /* ignora - combo fica vazio */ }
  }

  async function carregarProfissionaisDoExecutante(executanteId) {
    profissionaisDoExecutante = [];
    form.profissionalId = null;
    if (!executanteId) return;
    try {
      const res = await getApi(`profissionais/executante/${executanteId}/ativos`);
      profissionaisDoExecutante = res.ok ? await res.json() : [];
    } catch { /* ignora - combo fica vazio */ }
  }

  $effect(() => {
    carregarProfissionaisDoExecutante(form.estabelecimentoExecutanteId);
  });

  const grupoSelecionado = $derived(
    form.grupoEspecialidadesId ? grupos.find((g) => g.id === Number(form.grupoEspecialidadesId)) : null
  );

  function alternarDia(dia) {
    form.diasSemana = form.diasSemana.includes(dia)
      ? form.diasSemana.filter((d) => d !== dia)
      : [...form.diasSemana, dia];
  }

  function alternarEspecialidadeDoGrupo(id) {
    form.especialidadeIdsDoGrupo = form.especialidadeIdsDoGrupo.includes(id)
      ? form.especialidadeIdsDoGrupo.filter((e) => e !== id)
      : [...form.especialidadeIdsDoGrupo, id];
  }

  function adicionarDistribuicao() {
    distribuicoes = [...distribuicoes, { unidadeSolicitanteId: null, vagasPorOcorrencia: 1 }];
  }

  function removerDistribuicao(index) {
    distribuicoes = distribuicoes.filter((_, i) => i !== index);
  }

  async function salvar() {
    if (!form.estabelecimentoExecutanteId) { toast.error('Selecione o estabelecimento executante.'); return; }
    if (!form.profissionalId) { toast.error('Selecione o profissional.'); return; }
    if (form.diasSemana.length === 0) { toast.error('Marque ao menos um dia da semana.'); return; }
    if (distribuicoes.some((d) => !d.unidadeSolicitanteId || !d.vagasPorOcorrencia)) {
      toast.error('Preencha unidade e vagas em todas as distribuições, ou remova a linha.');
      return;
    }

    let especialidadeIds;
    if (form.tipoOferta === 'INDIVIDUAL') {
      if (!form.especialidadeId) { toast.error('Selecione a especialidade.'); return; }
      especialidadeIds = [Number(form.especialidadeId)];
    } else {
      if (!form.grupoEspecialidadesId) { toast.error('Selecione o grupo de especialidades.'); return; }
      if (form.especialidadeIdsDoGrupo.length === 0) {
        toast.error('Selecione ao menos um exame do grupo.');
        return;
      }
      especialidadeIds = form.especialidadeIdsDoGrupo;
    }

    const payload = {
      estabelecimentoExecutanteId: Number(form.estabelecimentoExecutanteId),
      profissionalId: Number(form.profissionalId),
      cboId: form.cboId ? Number(form.cboId) : null,
      localAgendamentoId: null,
      localDescricao: form.localDescricao || null,
      tipoOferta: form.tipoOferta,
      grupoEspecialidadesId: form.tipoOferta === 'GRUPO' ? Number(form.grupoEspecialidadesId) : null,
      especialidadeIds,
      vigenciaInicio: form.vigenciaInicio,
      vigenciaFim: form.vigenciaFim,
      diasSemana: form.diasSemana,
      horaInicial: form.horaInicial,
      horaFinal: form.horaFinal,
      observacao: form.observacao || null,
      distribuicoes: distribuicoes.map((d) => ({
        unidadeSolicitanteId: Number(d.unidadeSolicitanteId),
        vagasPorOcorrencia: Number(d.vagasPorOcorrencia)
      }))
    };

    salvando = true;
    try {
      const res = await postApi('agendas', payload);
      if (!res.ok) {
        const erro = await res.json().catch(() => ({}));
        toast.error(erro.message ?? 'Erro ao abrir agenda.');
        return;
      }
      toast.success('Agenda aberta com sucesso.');
      goto('/liberacao-agenda');
    } catch {
      toast.error('Erro ao salvar agenda.');
    } finally {
      salvando = false;
    }
  }
</script>

<Content titleH1="Abrir Agenda" page="/liberacao-agenda">
  <main class="p-6 max-w-3xl">
    <div class="bg-white border border-gray-200 rounded-xl p-6">
      <section class="space-y-4">
        <h2 class="text-xs font-semibold text-gray-500 uppercase tracking-wide">Estabelecimento e profissional</h2>

        <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="block text-xs text-gray-500 mb-1">Estabelecimento executante *</label>
          <select bind:value={form.estabelecimentoExecutanteId}
            class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500">
            <option value={null}>Selecionar...</option>
            {#each executantes as u (u.id)}
              <option value={u.id}>{u.nome}</option>
            {/each}
          </select>
        </div>
        <div>
          <label class="block text-xs text-gray-500 mb-1">Profissional *</label>
          <select bind:value={form.profissionalId} disabled={!form.estabelecimentoExecutanteId}
            class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500 disabled:bg-gray-100">
            <option value={null}>Selecionar...</option>
            {#each profissionaisDoExecutante as p (p.id)}
              <option value={p.id}>{p.nome}</option>
            {/each}
          </select>
          {#if form.estabelecimentoExecutanteId && profissionaisDoExecutante.length === 0}
            <p class="text-xs text-amber-600 mt-1">
              Nenhum profissional com vínculo ativo neste estabelecimento.
            </p>
          {/if}
        </div>
      </div>

      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="block text-xs text-gray-500 mb-1">CBO (ocupação)</label>
          <select bind:value={form.cboId}
            class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500">
            <option value={null}>— Não informado —</option>
            {#each cbos as c (c.id)}
              <option value={c.id}>{c.codigo} — {c.descricao}</option>
            {/each}
          </select>
        </div>
        <div>
          <label class="block text-xs text-gray-500 mb-1">Local do atendimento (opcional)</label>
          <input bind:value={form.localDescricao} placeholder="Sobrescreve o endereço do executante"
            class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
      </div>
      </section>

      <section class="space-y-4 border-t border-gray-200 mt-6 pt-6">
      <h2 class="text-xs font-semibold text-gray-500 uppercase tracking-wide">Procedimento ofertado</h2>

      <div class="flex gap-4">
        <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
          <input type="radio" bind:group={form.tipoOferta} value="INDIVIDUAL" class="accent-emerald-600" />
          Individual (uma especialidade)
        </label>
        <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
          <input type="radio" bind:group={form.tipoOferta} value="GRUPO" class="accent-emerald-600" />
          Grupo (ex.: laboratório)
        </label>
      </div>

      {#if form.tipoOferta === 'INDIVIDUAL'}
        <div>
          <label class="block text-xs text-gray-500 mb-1">Especialidade *</label>
          <select bind:value={form.especialidadeId}
            class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500">
            <option value={null}>Selecionar...</option>
            {#each especialidades as e (e.id)}
              <option value={e.id}>{e.nome}</option>
            {/each}
          </select>
        </div>
      {:else}
        <div>
          <label class="block text-xs text-gray-500 mb-1">Grupo de especialidades *</label>
          <select bind:value={form.grupoEspecialidadesId} onchange={() => (form.especialidadeIdsDoGrupo = [])}
            class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500">
            <option value={null}>Selecionar...</option>
            {#each grupos as g (g.id)}
              <option value={g.id}>{g.nome}</option>
            {/each}
          </select>
        </div>
        {#if grupoSelecionado}
          <div>
            <label class="block text-xs text-gray-500 mb-1">
              Exames ofertados desta agenda * (subconjunto do grupo, saldo único compartilhado)
            </label>
            <div class="max-h-48 overflow-y-auto border border-gray-200 rounded-lg p-2 space-y-1">
              {#each grupoSelecionado.especialidades ?? [] as e (e.id)}
                <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
                  <input type="checkbox"
                    checked={form.especialidadeIdsDoGrupo.includes(e.id)}
                    onchange={() => alternarEspecialidadeDoGrupo(e.id)}
                    class="accent-emerald-600" />
                  {e.nome}
                </label>
              {:else}
                <p class="text-xs text-gray-400">Este grupo não tem especialidades vinculadas.</p>
              {/each}
            </div>
          </div>
        {/if}
      {/if}
      </section>

      <section class="space-y-4 border-t border-gray-200 mt-6 pt-6">
      <h2 class="text-xs font-semibold text-gray-500 uppercase tracking-wide">Vigência, dias e horário</h2>

      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="block text-xs text-gray-500 mb-1">Vigência inicial *</label>
          <input type="date" bind:value={form.vigenciaInicio}
            class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
        <div>
          <label class="block text-xs text-gray-500 mb-1">Vigência final *</label>
          <input type="date" bind:value={form.vigenciaFim}
            class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
      </div>
      <p class="text-xs text-gray-500">Data única: use o mesmo valor nos dois campos.</p>

      <div>
        <label class="block text-xs text-gray-500 mb-1">Dias da semana *</label>
        <div class="flex flex-wrap gap-2">
          {#each DIAS as dia (dia)}
            <button type="button" onclick={() => alternarDia(dia)}
              class="px-3 py-1.5 rounded-lg text-xs font-medium border transition-colors
                {form.diasSemana.includes(dia)
                  ? 'bg-emerald-600 text-white border-emerald-600'
                  : 'bg-white text-gray-600 border-gray-300 hover:bg-gray-50'}">
              {dia}
            </button>
          {/each}
        </div>
      </div>

      <div class="grid grid-cols-2 gap-4">
        <div>
          <label class="block text-xs text-gray-500 mb-1">Horário inicial *</label>
          <input type="time" bind:value={form.horaInicial}
            class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
        <div>
          <label class="block text-xs text-gray-500 mb-1">Horário final *</label>
          <input type="time" bind:value={form.horaFinal}
            class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
      </div>
      </section>

      <section class="space-y-4 border-t border-gray-200 mt-6 pt-6">
      <h2 class="text-xs font-semibold text-gray-500 uppercase tracking-wide">
        Distribuição de vagas por unidade solicitante
      </h2>

      {#each distribuicoes as d, i (i)}
        <div class="flex items-end gap-3">
          <div class="flex-1">
            <label class="block text-xs text-gray-500 mb-1">Unidade solicitante</label>
            <select bind:value={d.unidadeSolicitanteId}
              class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500">
              <option value={null}>Selecionar...</option>
              {#each unidadesSolicitantes as u (u.id)}
                <option value={u.id}>{u.nome}</option>
              {/each}
            </select>
          </div>
          <div class="w-40">
            <label class="block text-xs text-gray-500 mb-1">Vagas por ocorrência</label>
            <input type="number" min="1" bind:value={d.vagasPorOcorrencia}
              class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
          </div>
          {#if distribuicoes.length > 1}
            <button onclick={() => removerDistribuicao(i)}
              class="px-3 py-2 rounded-lg bg-red-50 text-red-600 hover:bg-red-100 text-xs transition-colors">
              Remover
            </button>
          {/if}
        </div>
      {/each}

      <button onclick={adicionarDistribuicao}
        class="px-3 py-1.5 rounded-lg bg-gray-100 text-gray-700 hover:bg-gray-200 text-xs transition-colors">
        + Adicionar unidade
      </button>

      <div>
        <label class="block text-xs text-gray-500 mb-1">Observação</label>
        <textarea bind:value={form.observacao} rows="2"
          class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500"></textarea>
      </div>
      </section>

      <div class="flex justify-end gap-3 border-t border-gray-200 mt-6 pt-6">
        <button onclick={() => goto('/liberacao-agenda')}
          class="px-4 py-2 rounded-lg bg-gray-100 text-gray-700 text-sm hover:bg-gray-200 transition-colors">
          Cancelar
        </button>
        <button onclick={salvar} disabled={salvando}
          class="px-4 py-2 rounded-lg bg-emerald-600 text-white text-sm hover:bg-emerald-500 transition-colors disabled:opacity-50">
          {salvando ? 'Abrindo agenda...' : 'Abrir Agenda'}
        </button>
      </div>
    </div>
  </main>
</Content>
