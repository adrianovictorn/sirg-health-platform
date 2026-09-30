<script>
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { page } from '$app/stores';
  import { getApi, putApi } from '$lib/api.js';
  import Content from '$lib/Content.svelte';
  import { toast } from 'svelte-sonner';

  const cotaId = $page.params.id;

  let unidades = $state([]);
  let grupos = $state([]);
  let especialidades = $state([]);
  let carregando = $state(true);
  let salvando = $state(false);
  let origemAgenda = $state(false);
  let agendaId = $state(null);
  let proximasOcorrencias = $state([]);

  let form = $state({
    titular: 'UNIDADE',
    escopo: 'ESPECIALIDADE',
    grupoUnidadesId: null,
    grupoEspecialidadesId: null,
    unidadeId: null,
    especialidadeId: null,
    tipoPeriodo: 'MENSAL',
    periodo: '',
    dataEspecifica: '',
    quantidadeTotal: 0,
    ativo: true,
    profissionalId: null,
    localAgendamentoId: null,
    horarioDinamico: false,
    tempoMedioAtendimentoMinutos: null,
    horaInicial: '',
    horaFinal: '',
    diasSemana: []
  });

  const DIAS_SEMANA = [
    { sigla: 'SEG', label: 'Segunda' },
    { sigla: 'TER', label: 'Terça' },
    { sigla: 'QUA', label: 'Quarta' },
    { sigla: 'QUI', label: 'Quinta' },
    { sigla: 'SEX', label: 'Sexta' },
    { sigla: 'SAB', label: 'Sábado' },
    { sigla: 'DOM', label: 'Domingo' }
  ];

  function alternarDiaSemana(sigla) {
    form.diasSemana = form.diasSemana.includes(sigla)
      ? form.diasSemana.filter((d) => d !== sigla)
      : [...form.diasSemana, sigla];
  }

  let locaisAgendamento = $state([]);
  let termoBuscaProfissional = $state('');
  let resultadosProfissional = $state([]);
  let profissionalSelecionado = $state(null);
  let buscandoProfissional = $state(false);

  async function carregarLocaisAgendamento() {
    try {
      const res = await getApi('local/agendamento');
      locaisAgendamento = res.ok ? await res.json() : [];
    } catch { /* ignora - combo fica vazio */ }
  }

  async function buscarProfissional() {
    if (!termoBuscaProfissional || termoBuscaProfissional.trim().length < 2) {
      resultadosProfissional = [];
      return;
    }
    buscandoProfissional = true;
    try {
      const res = await getApi(`profissionais/buscar?nome=${encodeURIComponent(termoBuscaProfissional.trim())}&size=10`);
      const pagina = res.ok ? await res.json() : { content: [] };
      resultadosProfissional = pagina.content ?? [];
    } catch {
      resultadosProfissional = [];
    } finally {
      buscandoProfissional = false;
    }
  }

  function selecionarProfissional(p) {
    profissionalSelecionado = p;
    form.profissionalId = p.id;
    termoBuscaProfissional = '';
    resultadosProfissional = [];
  }

  function limparProfissional() {
    profissionalSelecionado = null;
    form.profissionalId = null;
  }

  onMount(async () => {
    await Promise.all([carregarUnidades(), carregarEspecialidades(), carregarGrupos(), carregarLocaisAgendamento(), carregarCota()]);
    carregando = false;
  });

  async function carregarUnidades() {
    try {
      const res = await getApi('unidades/ativas');
      unidades = await res.json();
    } catch { /* ignora - combo fica vazio */ }
  }

  async function carregarGrupos() {
    try {
      const res = await getApi('grupo-relatorio/listar');
      grupos = res.ok ? await res.json() : [];
    } catch { /* ignora - combo fica vazio */ }
  }

  async function carregarEspecialidades() {
    try {
      const res = await getApi('catalog/especialidades/listar');
      especialidades = await res.json();
    } catch { /* ignora - combo fica vazio */ }
  }

  async function carregarCota() {
    try {
      const res = await getApi(`cotas/${cotaId}`);
      if (!res.ok) {
        toast.error('Cota não encontrada.');
        goto('/admin/cotas');
        return;
      }
      const c = await res.json();
      origemAgenda = c.origem === 'AGENDA';
      agendaId = c.agendaId;
      proximasOcorrencias = c.proximasOcorrencias ?? [];
      form = {
        titular: c.grupoUnidadesId ? 'GRUPO' : 'UNIDADE',
        escopo: c.grupoEspecialidadesId ? 'GRUPO_ESPECIALIDADES' : (c.especialidadeId ? 'ESPECIALIDADE' : 'GERAL'),
        grupoUnidadesId: c.grupoUnidadesId,
        grupoEspecialidadesId: c.grupoEspecialidadesId,
        unidadeId: c.unidadeId,
        especialidadeId: c.especialidadeId,
        tipoPeriodo: c.tipoPeriodo,
        periodo: c.periodo ?? new Date().toISOString().slice(0, 7),
        dataEspecifica: c.dataEspecifica ?? new Date().toISOString().slice(0, 10),
        quantidadeTotal: c.quantidadeTotal,
        ativo: c.ativo,
        profissionalId: c.profissionalId ?? null,
        localAgendamentoId: c.localAgendamentoId ?? null,
        horarioDinamico: c.horarioDinamico ?? false,
        tempoMedioAtendimentoMinutos: c.tempoMedioAtendimentoMinutos ?? null,
        horaInicial: c.horaInicial ? c.horaInicial.slice(0, 5) : '',
        horaFinal: c.horaFinal ? c.horaFinal.slice(0, 5) : '',
        diasSemana: c.diasSemana ?? []
      };
      if (c.profissionalId) {
        profissionalSelecionado = { id: c.profissionalId, nome: c.profissionalNome };
      }
    } catch {
      toast.error('Erro ao carregar cota.');
      goto('/admin/cotas');
    }
  }

  const grupoSelecionado = $derived(
    form.escopo === 'GRUPO_ESPECIALIDADES' && form.grupoEspecialidadesId
      ? grupos.find((g) => g.id === Number(form.grupoEspecialidadesId))
      : null
  );

  async function salvar() {
    const porGrupoUnidades = form.titular === 'GRUPO';
    if (porGrupoUnidades && !form.grupoUnidadesId) { toast.error('Selecione o grupo de unidades.'); return; }
    if (!porGrupoUnidades && !form.unidadeId) { toast.error('Selecione a unidade.'); return; }

    const porGrupoEsp = form.escopo === 'GRUPO_ESPECIALIDADES';
    if (porGrupoEsp && !form.grupoEspecialidadesId) { toast.error('Selecione o grupo de especialidades.'); return; }

    const temEspelho = form.profissionalId || form.localAgendamentoId
      || form.horarioDinamico || form.horaInicial || form.horaFinal;
    const temDiasSemana = form.diasSemana.length > 0;

    if (temDiasSemana && form.tipoPeriodo !== 'MENSAL') {
      toast.error('Dias da semana só se aplicam a cota "Mensal" (cota por Data já é um dia específico).');
      return;
    }
    if (temEspelho && form.tipoPeriodo === 'MENSAL' && !temDiasSemana) {
      toast.error('Profissional, local ou horário numa cota Mensal exigem os dias da semana de atendimento.');
      return;
    }
    if (temEspelho && form.tipoPeriodo !== 'DATA' && form.tipoPeriodo !== 'MENSAL') {
      toast.error('Profissional, local ou horário exigem cota "Por Data Específica" ou "Mensal" com dias da semana.');
      return;
    }
    if (form.horarioDinamico && (!form.horaInicial || !form.horaFinal)) {
      toast.error('Horário dinâmico exige hora inicial e hora final, para dividir o período entre as vagas.');
      return;
    }
    if (!form.horarioDinamico && (form.horaInicial ? !form.horaFinal : form.horaFinal)) {
      toast.error('Informe hora inicial e hora final juntas, ou nenhuma das duas.');
      return;
    }

    const payload = {
      unidadeId: porGrupoUnidades ? null : Number(form.unidadeId),
      grupoUnidadesId: porGrupoUnidades ? Number(form.grupoUnidadesId) : null,
      especialidadeId: (!porGrupoEsp && form.especialidadeId) ? Number(form.especialidadeId) : null,
      grupoEspecialidadesId: porGrupoEsp ? Number(form.grupoEspecialidadesId) : null,
      tipoPeriodo: form.tipoPeriodo,
      periodo: form.tipoPeriodo === 'MENSAL' ? form.periodo : null,
      dataEspecifica: form.tipoPeriodo === 'DATA' ? form.dataEspecifica : null,
      quantidadeTotal: Number(form.quantidadeTotal),
      ativo: form.ativo,
      profissionalId: form.profissionalId ? Number(form.profissionalId) : null,
      localAgendamentoId: form.localAgendamentoId ? Number(form.localAgendamentoId) : null,
      horarioDinamico: !!form.horarioDinamico,
      tempoMedioAtendimentoMinutos: form.tempoMedioAtendimentoMinutos
        ? Number(form.tempoMedioAtendimentoMinutos) : null,
      horaInicial: form.horaInicial || null,
      horaFinal: form.horaFinal || null,
      diasSemana: temDiasSemana ? form.diasSemana : null
    };

    salvando = true;
    try {
      const res = await putApi(`cotas/${cotaId}`, payload);
      if (!res.ok) {
        const erro = await res.json().catch(() => ({}));
        toast.error(erro.message ?? 'Erro ao salvar cota.');
        return;
      }
      toast.success('Cota atualizada.');
      goto('/admin/cotas');
    } catch {
      toast.error('Erro ao salvar cota.');
    } finally {
      salvando = false;
    }
  }
</script>

<Content titleH1="Editar Cota" page="/admin/cotas">
  <main class="p-4 md:p-6 lg:p-8 overflow-auto space-y-6">
    {#if carregando}
      <p class="text-gray-500 text-sm">Carregando...</p>
    {:else if origemAgenda}
      <div class="bg-violet-50 border border-violet-200 text-violet-800 rounded-lg p-6 space-y-3 shadow">
        <p class="text-sm">
          Esta cota foi gerada por uma agenda e não pode ser editada diretamente aqui.
          Para alterar vagas, vigência ou horário, edite a agenda de origem.
        </p>
        <div class="flex gap-3">
          <button onclick={() => goto(`/liberacao-agenda/${agendaId}`)}
            class="px-4 py-2 rounded-lg bg-violet-600 text-white text-sm hover:bg-violet-500 transition-colors">
            Ir para a agenda
          </button>
          <button onclick={() => goto('/admin/cotas')}
            class="px-4 py-2 rounded-lg bg-gray-100 text-gray-700 text-sm hover:bg-gray-200 transition-colors">
            Voltar
          </button>
        </div>
      </div>
    {:else}

    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6">
      <section class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-bold text-emerald-800 mb-4 border-b pb-2">Titular da Cota</h2>
        <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Titular da cota *</label>
          <div class="flex gap-4">
            <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
              <input type="radio" bind:group={form.titular} value="UNIDADE" class="accent-emerald-600" />
              Unidade
            </label>
            <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
              <input type="radio" bind:group={form.titular} value="GRUPO" class="accent-emerald-600" />
              Grupo
            </label>
          </div>
        </div>

        {#if form.titular === 'UNIDADE'}
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Unidade *</label>
            <select bind:value={form.unidadeId}
              class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500">
              <option value={null}>Selecionar unidade...</option>
              {#each unidades as u (u.id)}
                <option value={u.id}>{u.nome}</option>
              {/each}
            </select>
          </div>
        {:else}
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Grupo de Unidades *</label>
            <select bind:value={form.grupoUnidadesId}
              class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500">
              <option value={null}>Selecionar grupo...</option>
              {#each grupos as g (g.id)}
                <option value={g.id}>{g.nome}</option>
              {/each}
            </select>
          </div>
        {/if}

        <div class="flex items-center gap-2 pt-2 border-t border-gray-100">
          <input type="checkbox" id="ativoEdit" bind:checked={form.ativo} class="accent-emerald-600" />
          <label for="ativoEdit" class="text-sm text-gray-700">Cota ativa</label>
        </div>
        </div>
      </section>

      <section class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-bold text-emerald-800 mb-4 border-b pb-2">Escopo da Cota</h2>
        <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">O que a cota limita *</label>
          <div class="flex flex-wrap gap-4">
            <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
              <input type="radio" bind:group={form.escopo} value="ESPECIALIDADE" class="accent-emerald-600" />
              Uma especialidade
            </label>
            <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
              <input type="radio" bind:group={form.escopo} value="GRUPO_ESPECIALIDADES" class="accent-emerald-600" />
              Um grupo de especialidades
            </label>
            <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
              <input type="radio" bind:group={form.escopo} value="GERAL" class="accent-emerald-600" />
              Todas (cota geral)
            </label>
          </div>
        </div>

        {#if form.escopo === 'ESPECIALIDADE'}
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Especialidade</label>
            <select bind:value={form.especialidadeId}
              class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500">
              <option value={null}>Selecionar especialidade...</option>
              {#each especialidades as e (e.id)}
                <option value={e.id}>{e.nome}</option>
              {/each}
            </select>
          </div>
        {:else if form.escopo === 'GRUPO_ESPECIALIDADES'}
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Grupo de Especialidades *</label>
            <select bind:value={form.grupoEspecialidadesId}
              class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500">
              <option value={null}>Selecionar grupo...</option>
              {#each grupos as g (g.id)}
                <option value={g.id}>{g.nome}</option>
              {/each}
            </select>
            {#if grupoSelecionado}
              <details class="mt-2 text-xs">
                <summary class="cursor-pointer text-emerald-700 hover:text-emerald-600 select-none">
                  Ver especialidades deste grupo ({grupoSelecionado.especialidades?.length ?? 0})
                </summary>
                <ul class="mt-2 space-y-1 pl-3 border-l border-gray-200 text-gray-700">
                  {#each grupoSelecionado.especialidades ?? [] as e (e.id)}
                    <li>{e.nome}</li>
                  {/each}
                </ul>
              </details>
            {/if}
          </div>
        {:else}
          <p class="text-xs text-gray-500">
            A cota valerá para qualquer especialidade deste titular.
          </p>
        {/if}
        </div>
      </section>
    </div>

      <section class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-bold text-emerald-800 mb-4 border-b pb-2">Período e Quantidade</h2>
        <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-gray-700 mb-1">Tipo de Controle *</label>
          <div class="flex gap-4">
            <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
              <input type="radio" bind:group={form.tipoPeriodo} value="MENSAL" class="accent-emerald-600" />
              Mensal
            </label>
            <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
              <input type="radio" bind:group={form.tipoPeriodo} value="DATA" class="accent-emerald-600" />
              Por Data Específica
            </label>
          </div>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {#if form.tipoPeriodo === 'MENSAL'}
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Período (YYYY-MM) *</label>
              <input type="month" bind:value={form.periodo}
                class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500" />
            </div>
          {:else}
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Data Específica *</label>
              <input type="date" bind:value={form.dataEspecifica}
                class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500" />
            </div>
          {/if}

          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">Quantidade Total *</label>
            <input type="number" min="0" bind:value={form.quantidadeTotal}
              class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500" />
          </div>
        </div>
        </div>
      </section>

      <section class="bg-white rounded-lg shadow p-6">
        <h2 class="text-lg font-bold text-emerald-800 mb-4 border-b pb-2">
          Espelho de Atendimento <span class="text-sm font-normal text-gray-500">(opcional)</span>
        </h2>
        <p class="text-sm text-gray-500 mb-4">
          Profissional, horário e local ficam travados na tela de agendamento da unidade.
        </p>
        <div class="space-y-4">

          {#if form.tipoPeriodo === 'MENSAL'}
            <div class="text-xs text-indigo-700 bg-indigo-50 border border-indigo-200 rounded-lg px-3 py-2">
              Cota mensal não tem uma data única — marque os dias da semana em que o
              profissional atende. O sistema calcula automaticamente as próximas datas
              previstas dentro do mês, para exibir como referência ao agendar — mas o
              saldo continua sendo um só para o mês inteiro, somando todos os dias.
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">Dias da semana de atendimento</label>
              <div class="flex flex-wrap gap-2">
                {#each DIAS_SEMANA as dia (dia.sigla)}
                  <button type="button" onclick={() => alternarDiaSemana(dia.sigla)}
                    class="px-3 py-1.5 rounded-full text-xs font-medium border transition-colors {form.diasSemana.includes(dia.sigla)
                      ? 'bg-emerald-600 text-white border-emerald-600'
                      : 'bg-white text-gray-600 border-gray-300 hover:bg-gray-50'}">
                    {dia.label}
                  </button>
                {/each}
              </div>
              {#if proximasOcorrencias.length > 0}
                <p class="text-xs text-gray-500 mt-2">
                  Próximas datas previstas: {proximasOcorrencias.join(', ')}
                </p>
              {/if}
            </div>
          {/if}

          {#if form.tipoPeriodo !== 'DATA' && form.tipoPeriodo !== 'MENSAL'}
            <p class="text-xs text-amber-600 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2">
              Troque o Tipo de Controle para "Por Data Específica" ou "Mensal" para usar profissional, horário ou local.
            </p>
          {:else}
            <div class="space-y-4">
              <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-1">Profissional</label>
                  {#if profissionalSelecionado}
                    <div class="flex items-center justify-between bg-emerald-50 border border-emerald-200 rounded-lg px-3 py-2 text-sm text-emerald-800">
                      <span>{profissionalSelecionado.nome}</span>
                      <button type="button" onclick={limparProfissional} class="text-emerald-700 hover:text-emerald-900 text-xs">Remover</button>
                    </div>
                  {:else}
                    <input type="text" placeholder="Buscar profissional por nome..."
                      bind:value={termoBuscaProfissional}
                      oninput={buscarProfissional}
                      class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500" />
                    {#if buscandoProfissional}
                      <p class="text-xs text-gray-400 mt-1">Buscando...</p>
                    {:else if resultadosProfissional.length > 0}
                      <ul class="mt-1 border border-gray-200 rounded-lg divide-y divide-gray-100 max-h-40 overflow-y-auto">
                        {#each resultadosProfissional as p (p.id)}
                          <li>
                            <button type="button" onclick={() => selecionarProfissional(p)}
                              class="w-full text-left px-3 py-2 text-sm text-gray-700 hover:bg-gray-50">
                              {p.nome}
                            </button>
                          </li>
                        {/each}
                      </ul>
                    {/if}
                  {/if}
                </div>

                <div>
                  <label class="block text-sm font-medium text-gray-700 mb-1">Local de atendimento</label>
                  <select bind:value={form.localAgendamentoId}
                    class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500">
                    <option value={null}>Sem local definido (a unidade informa ao agendar)</option>
                    {#each locaisAgendamento as loc (loc.id)}
                      <option value={loc.id}>{loc.nomeLocal ?? loc.nome}</option>
                    {/each}
                  </select>
                </div>
              </div>

              <div class="border-t border-gray-100 pt-4">
                <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
                  <input type="checkbox" bind:checked={form.horarioDinamico} class="accent-emerald-600" />
                  Deseja que os horários sejam criados automaticamente para os pacientes?
                </label>
                {#if form.horarioDinamico}
                  <p class="text-xs text-gray-500 mt-1 mb-2">
                    O sistema divide o período abaixo igualmente entre as {form.quantidadeTotal || 'N'} vagas da
                    cota — cada paciente recebe um horário calculado automaticamente ao agendar.
                  </p>
                {/if}
                <div class="mt-2 grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">Hora inicial {form.horarioDinamico ? '*' : ''}</label>
                    <input type="time" bind:value={form.horaInicial}
                      class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500" />
                  </div>
                  <div>
                    <label class="block text-sm font-medium text-gray-700 mb-1">Hora final {form.horarioDinamico ? '*' : ''}</label>
                    <input type="time" bind:value={form.horaFinal}
                      class="w-full border-gray-300 rounded-md shadow-sm focus:border-emerald-500 focus:ring-emerald-500" />
                  </div>
                </div>
                {#if !form.horarioDinamico}
                  <p class="text-xs text-gray-500 mt-2">
                    Sem horário automático: o operador informa a hora manualmente na tela de
                    agendamento; se não informar, este período fica só como referência.
                  </p>
                {/if}
              </div>
            </div>
          {/if}
        </div>
      </section>

      <div class="flex flex-col-reverse sm:flex-row sm:justify-end gap-3">
        <button onclick={() => goto('/admin/cotas')}
          class="px-4 py-2 rounded-lg bg-gray-100 text-gray-700 text-sm hover:bg-gray-200 transition-colors">
          Cancelar
        </button>
        <button onclick={salvar} disabled={salvando}
          class="px-6 py-2 rounded-md bg-emerald-700 text-white text-sm font-medium hover:bg-emerald-800 transition-colors disabled:opacity-50 shadow">
          {salvando ? 'Salvando...' : 'Salvar Cota'}
        </button>
      </div>
    {/if}
  </main>
</Content>
