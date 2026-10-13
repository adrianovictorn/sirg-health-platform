<script>
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { getApi, postApi } from '$lib/api.js';
  import Content from '$lib/Content.svelte';
  import { toast } from 'svelte-sonner';

  let unidades = $state([]);
  let grupos = $state([]);
  let especialidades = $state([]);
  let salvando = $state(false);

  const periodoAtual = new Date().toISOString().slice(0, 7);
  const hojeISO = new Date().toISOString().slice(0, 10);

  let form = $state({
    titular: 'UNIDADE',
    escopo: 'ESPECIALIDADE',
    grupoUnidadesId: null,
    grupoEspecialidadesId: null,
    unidadeId: null,
    especialidadeId: null,
    tipoPeriodo: 'MENSAL',
    periodo: periodoAtual,
    dataEspecifica: hojeISO,
    quantidadeTotal: 0,
    // Espelho de atendimento (opcional, V92): cota DATA usa a data única como
    // referência; cota MENSAL (V95) usa diasSemana para o mesmo papel.
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

  // Busca de profissional (tabela inteira, sem filtro de unidade — Profissional != Usuario).
  let termoBuscaProfissional = $state('');
  let resultadosProfissional = $state([]);
  let profissionalSelecionado = $state(null);
  let buscandoProfissional = $state(false);
  let locaisAgendamento = $state([]);

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

  async function carregarLocaisAgendamento() {
    try {
      const res = await getApi('local/agendamento');
      locaisAgendamento = res.ok ? await res.json() : [];
    } catch { /* ignora - combo fica vazio */ }
  }

  // Titular = VARIAS_UNIDADES: cria a mesma regra (escopo/período/quantidade)
  // para N unidades de uma vez, sem mudar o backend — dispara um POST /api/cotas
  // por unidade selecionada e reporta sucesso/falha individualmente (best-effort:
  // uma unidade com conflito não deve travar a criação das demais).
  let unidadesSelecionadas = $state([]);
  let resultadosReplicacao = $state(null);

  function alternarUnidadeSelecionada(id) {
    unidadesSelecionadas = unidadesSelecionadas.includes(id)
      ? unidadesSelecionadas.filter((u) => u !== id)
      : [...unidadesSelecionadas, id];
  }

  onMount(async () => {
    await Promise.all([carregarUnidades(), carregarEspecialidades(), carregarGrupos(), carregarLocaisAgendamento()]);
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

  const grupoSelecionado = $derived(
    form.escopo === 'GRUPO_ESPECIALIDADES' && form.grupoEspecialidadesId
      ? grupos.find((g) => g.id === Number(form.grupoEspecialidadesId))
      : null
  );

  /** Campos comuns (escopo, período, quantidade) — iguais para os 3 modos de titular. */
  function construirPayloadEscopoPeriodo() {
    const porGrupoEsp = form.escopo === 'GRUPO_ESPECIALIDADES';
    if (porGrupoEsp && !form.grupoEspecialidadesId) {
      toast.error('Selecione o grupo de especialidades.');
      return null;
    }

    const temEspelho = form.profissionalId || form.localAgendamentoId
      || form.horarioDinamico || form.horaInicial || form.horaFinal;
    const temDiasSemana = form.diasSemana.length > 0;

    if (temDiasSemana && form.tipoPeriodo !== 'MENSAL') {
      toast.error('Dias da semana só se aplicam a cota "Mensal" (cota por Data já é um dia específico).');
      return null;
    }
    if (temEspelho && form.tipoPeriodo === 'MENSAL' && !temDiasSemana) {
      toast.error('Profissional, local ou horário numa cota Mensal exigem os dias da semana de atendimento.');
      return null;
    }
    if (temEspelho && form.tipoPeriodo !== 'DATA' && form.tipoPeriodo !== 'MENSAL') {
      toast.error('Profissional, local ou horário exigem cota "Por Data Específica" ou "Mensal" com dias da semana.');
      return null;
    }
    if (form.horarioDinamico && (!form.horaInicial || !form.horaFinal)) {
      toast.error('Horário dinâmico exige hora inicial e hora final, para dividir o período entre as vagas.');
      return null;
    }
    if (!form.horarioDinamico && (form.horaInicial ? !form.horaFinal : form.horaFinal)) {
      toast.error('Informe hora inicial e hora final juntas, ou nenhuma das duas.');
      return null;
    }

    return {
      especialidadeId: !porGrupoEsp && form.especialidadeId ? Number(form.especialidadeId) : null,
      grupoEspecialidadesId: porGrupoEsp ? Number(form.grupoEspecialidadesId) : null,
      tipoPeriodo: form.tipoPeriodo,
      periodo: form.tipoPeriodo === 'MENSAL' ? form.periodo : null,
      dataEspecifica: form.tipoPeriodo === 'DATA' ? form.dataEspecifica : null,
      quantidadeTotal: Number(form.quantidadeTotal),
      profissionalId: form.profissionalId ? Number(form.profissionalId) : null,
      localAgendamentoId: form.localAgendamentoId ? Number(form.localAgendamentoId) : null,
      horarioDinamico: !!form.horarioDinamico,
      tempoMedioAtendimentoMinutos: form.tempoMedioAtendimentoMinutos
        ? Number(form.tempoMedioAtendimentoMinutos) : null,
      horaInicial: form.horaInicial || null,
      horaFinal: form.horaFinal || null,
      diasSemana: temDiasSemana ? form.diasSemana : null
    };
  }

  async function salvar() {
    const porGrupoUnidades = form.titular === 'GRUPO';
    if (porGrupoUnidades && !form.grupoUnidadesId) { toast.error('Selecione o grupo de unidades.'); return; }
    if (!porGrupoUnidades && !form.unidadeId) { toast.error('Selecione a unidade.'); return; }

    const base = construirPayloadEscopoPeriodo();
    if (!base) return;

    const payload = {
      unidadeId: porGrupoUnidades ? null : Number(form.unidadeId),
      grupoUnidadesId: porGrupoUnidades ? Number(form.grupoUnidadesId) : null,
      ...base
    };

    salvando = true;
    try {
      const res = await postApi('cotas', payload);
      if (!res.ok) {
        const erro = await res.json().catch(() => ({}));
        toast.error(erro.message ?? 'Erro ao criar cota.');
        return;
      }
      toast.success('Cota criada.');
      goto('/admin/cotas');
    } catch {
      toast.error('Erro ao salvar cota.');
    } finally {
      salvando = false;
    }
  }

  /**
   * Replica a mesma regra (escopo/período/quantidade) para N unidades — um
   * POST /api/cotas por unidade, em paralelo, sem mudar o backend nem o
   * constraint ck_cota_titular_exclusivo (cada linha criada continua com
   * exatamente 1 titular). Best-effort: uma unidade que falhar (ex. cota
   * duplicada) não impede a criação das demais.
   */
  async function salvarVarias() {
    if (unidadesSelecionadas.length === 0) {
      toast.error('Selecione ao menos uma unidade.');
      return;
    }
    const base = construirPayloadEscopoPeriodo();
    if (!base) return;

    salvando = true;
    resultadosReplicacao = null;
    try {
      const respostas = await Promise.allSettled(
        unidadesSelecionadas.map(async (unidadeId) => {
          const payload = { unidadeId, grupoUnidadesId: null, ...base };
          const res = await postApi('cotas', payload);
          if (!res.ok) {
            const erro = await res.json().catch(() => ({}));
            throw new Error(erro.message ?? 'Erro ao criar cota.');
          }
        })
      );

      const resultados = respostas.map((resultado, i) => {
        const unidadeId = unidadesSelecionadas[i];
        const unidadeNome = unidades.find((u) => u.id === unidadeId)?.nome ?? `Unidade #${unidadeId}`;
        return resultado.status === 'fulfilled'
          ? { unidadeId, unidadeNome, ok: true }
          : { unidadeId, unidadeNome, ok: false, mensagem: resultado.reason?.message ?? 'Erro desconhecido.' };
      });

      resultadosReplicacao = resultados;
      const sucesso = resultados.filter((r) => r.ok).length;
      if (sucesso === resultados.length) {
        toast.success(`Cota criada para as ${sucesso} unidades selecionadas.`);
      } else if (sucesso === 0) {
        toast.error('Nenhuma cota foi criada — veja os erros abaixo.');
      } else {
        toast.error(`${sucesso} de ${resultados.length} unidades tiveram cota criada — veja os detalhes abaixo.`);
      }
    } finally {
      salvando = false;
    }
  }

  function aoClicarSalvar() {
    return form.titular === 'VARIAS_UNIDADES' ? salvarVarias() : salvar();
  }
</script>

<Content titleH1="Nova Cota" page="/admin/cotas">
  <div class="cotas-ibm flex-1 min-w-0 p-4 md:p-6 space-y-6">
    <section class="ibm-painel p-4 md:p-6" aria-labelledby="cota-titulo-titular">
      <h2 id="cota-titulo-titular" class="ibm-grupo-titulo">Titular e escopo</h2>
      <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 lg:gap-12">
        <div class="space-y-6">
          <fieldset class="ibm-conjunto">
            <legend class="ibm-rotulo">Titular da cota *</legend>
            <div class="ibm-opcoes">
              <label class="ibm-opcao">
                <input type="radio" bind:group={form.titular} value="UNIDADE" />
                Unidade
              </label>
              <label class="ibm-opcao">
                <input type="radio" bind:group={form.titular} value="GRUPO" />
                Grupo
              </label>
              <label class="ibm-opcao">
                <input type="radio" bind:group={form.titular} value="VARIAS_UNIDADES" />
                Várias unidades
              </label>
            </div>
          </fieldset>

          {#if form.titular === 'UNIDADE'}
            <div>
              <label for="cota-unidade" class="ibm-rotulo">Unidade *</label>
              <select id="cota-unidade" bind:value={form.unidadeId} class="ibm-campo w-full">
                <option value={null}>Selecionar unidade...</option>
                {#each unidades as u (u.id)}
                  <option value={u.id}>{u.nome}</option>
                {/each}
              </select>
            </div>
          {:else if form.titular === 'GRUPO'}
            <div>
              <label for="cota-grupo-unidades" class="ibm-rotulo">Grupo de unidades *</label>
              <select
                id="cota-grupo-unidades"
                aria-describedby="cota-grupo-unidades-ajuda"
                bind:value={form.grupoUnidadesId}
                class="ibm-campo w-full"
              >
                <option value={null}>Selecionar grupo...</option>
                {#each grupos as g (g.id)}
                  <option value={g.id}>{g.nome}</option>
                {/each}
              </select>
              <p id="cota-grupo-unidades-ajuda" class="ibm-ajuda">
                Saldo compartilhado entre as unidades do grupo — consumido por ordem de chegada.
              </p>
            </div>
          {:else}
            <fieldset class="ibm-conjunto">
              <legend class="ibm-rotulo">
                Unidades * ({unidadesSelecionadas.length} selecionada{unidadesSelecionadas.length === 1 ? '' : 's'})
              </legend>
              <div class="ibm-lista-rolagem" aria-describedby="cota-varias-unidades-ajuda">
                {#each unidades as u (u.id)}
                  <label class="ibm-opcao ibm-opcao--linha">
                    <input
                      type="checkbox"
                      checked={unidadesSelecionadas.includes(u.id)}
                      onchange={() => alternarUnidadeSelecionada(u.id)}
                    />
                    {u.nome}
                  </label>
                {:else}
                  <p class="ibm-suave-texto ibm-lista-vazia">Nenhuma unidade ativa cadastrada.</p>
                {/each}
              </div>
              <p id="cota-varias-unidades-ajuda" class="ibm-ajuda">
                Cria uma cota independente para cada unidade selecionada, com a mesma
                especialidade/grupo, período e quantidade — cada cota conta o próprio saldo.
                Se alguma falhar (ex.: já existe cota para aquela unidade), as demais continuam
                sendo criadas normalmente.
              </p>
            </fieldset>
          {/if}

          {#if resultadosReplicacao}
            <div class="ibm-resultado-lote" role="status">
              <p class="ibm-resultado-lote-titulo">Resultado da criação em lote</p>
              <ul>
                {#each resultadosReplicacao as r (r.unidadeId)}
                  <li class="ibm-resultado-lote-item">
                    <span>{r.unidadeNome}</span>
                    {#if r.ok}
                      <span class="ibm-badge ibm-badge--ok">Criada</span>
                    {:else}
                      <span class="ibm-erro-texto ibm-resultado-lote-erro">{r.mensagem}</span>
                    {/if}
                  </li>
                {/each}
              </ul>
            </div>
          {/if}
        </div>

        <div class="space-y-6 ibm-coluna-divisor">
          <fieldset class="ibm-conjunto">
            <legend class="ibm-rotulo">O que a cota limita *</legend>
            <div class="ibm-opcoes">
              <label class="ibm-opcao">
                <input type="radio" bind:group={form.escopo} value="ESPECIALIDADE" />
                Uma especialidade
              </label>
              <label class="ibm-opcao">
                <input type="radio" bind:group={form.escopo} value="GRUPO_ESPECIALIDADES" />
                Um grupo de especialidades
              </label>
              <label class="ibm-opcao">
                <input type="radio" bind:group={form.escopo} value="GERAL" />
                Todas (cota geral)
              </label>
            </div>
          </fieldset>

          {#if form.escopo === 'ESPECIALIDADE'}
            <div>
              <label for="cota-especialidade" class="ibm-rotulo">Especialidade</label>
              <select id="cota-especialidade" bind:value={form.especialidadeId} class="ibm-campo w-full">
                <option value={null}>Selecionar especialidade...</option>
                {#each especialidades as e (e.id)}
                  <option value={e.id}>{e.nome}</option>
                {/each}
              </select>
            </div>
          {:else if form.escopo === 'GRUPO_ESPECIALIDADES'}
            <div>
              <label for="cota-grupo-especialidades" class="ibm-rotulo">Grupo de especialidades *</label>
              <select
                id="cota-grupo-especialidades"
                aria-describedby="cota-grupo-especialidades-ajuda"
                bind:value={form.grupoEspecialidadesId}
                class="ibm-campo w-full"
              >
                <option value={null}>Selecionar grupo...</option>
                {#each grupos as g (g.id)}
                  <option value={g.id}>{g.nome}</option>
                {/each}
              </select>
              <p id="cota-grupo-especialidades-ajuda" class="ibm-ajuda">
                Evita cadastrar uma cota por especialidade. O saldo é <strong>único e
                compartilhado</strong> entre todas as especialidades do grupo.
              </p>
              {#if grupoSelecionado}
                <div class="ibm-sublista">
                  <p id="cota-grupo-especialidades-lista" class="ibm-rotulo">
                    Especialidades deste grupo ({grupoSelecionado.especialidades?.length ?? 0})
                  </p>
                  <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
                  <div
                    class="ibm-lista-rolagem"
                    role="region"
                    aria-labelledby="cota-grupo-especialidades-lista"
                    tabindex="0"
                  >
                    <ul>
                      {#each grupoSelecionado.especialidades ?? [] as e (e.id)}
                        <li class="ibm-lista-item">{e.nome}</li>
                      {:else}
                        <li class="ibm-lista-item ibm-suave">Nenhuma especialidade vinculada a este grupo ainda.</li>
                      {/each}
                    </ul>
                  </div>
                </div>
              {/if}
            </div>
          {:else}
            <p class="ibm-ajuda">
              A cota valerá para qualquer especialidade deste titular.
            </p>
          {/if}
        </div>
      </div>
    </section>

    <section class="ibm-painel p-4 md:p-6" aria-labelledby="cota-titulo-periodo">
      <h2 id="cota-titulo-periodo" class="ibm-grupo-titulo">Período e quantidade</h2>
      <div class="space-y-6">
        <fieldset class="ibm-conjunto">
          <legend class="ibm-rotulo">Tipo de controle *</legend>
          <div class="ibm-opcoes">
            <label class="ibm-opcao">
              <input type="radio" bind:group={form.tipoPeriodo} value="MENSAL" />
              Mensal
            </label>
            <label class="ibm-opcao">
              <input type="radio" bind:group={form.tipoPeriodo} value="DATA" />
              Por Data Específica
            </label>
          </div>
        </fieldset>

        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {#if form.tipoPeriodo === 'MENSAL'}
            <div>
              <label for="cota-periodo" class="ibm-rotulo">Mês de referência *</label>
              <input id="cota-periodo" type="month" bind:value={form.periodo} class="ibm-campo w-full" />
            </div>
          {:else}
            <div>
              <label for="cota-data" class="ibm-rotulo">Data específica *</label>
              <input id="cota-data" type="date" bind:value={form.dataEspecifica} class="ibm-campo w-full" />
            </div>
          {/if}

          <div>
            <label for="cota-quantidade" class="ibm-rotulo">Quantidade total *</label>
            <input
              id="cota-quantidade"
              type="number"
              min="0"
              inputmode="numeric"
              bind:value={form.quantidadeTotal}
              class="ibm-campo w-full"
            />
          </div>
        </div>
      </div>
    </section>

    <section class="ibm-painel p-4 md:p-6" aria-labelledby="cota-titulo-espelho">
      <h2 id="cota-titulo-espelho" class="ibm-grupo-titulo">
        Espelho de atendimento <span class="ibm-badge ibm-badge--neutro ibm-grupo-titulo-marca">opcional</span>
      </h2>
      <p class="ibm-texto-apoio">
        Profissional, horário e local ficam travados na tela de agendamento da unidade —
        ela não poderá agendar diferente do que for definido aqui. Deixe em branco para uma
        cota geral, sem profissional definido (ex.: laboratório).
      </p>
      <div class="space-y-6">
        {#if form.tipoPeriodo !== 'DATA' && form.tipoPeriodo !== 'MENSAL'}
          <p class="ibm-aviso">
            Troque o Tipo de Controle para "Por Data Específica" ou "Mensal" para usar profissional, horário ou local.
          </p>
        {:else}
          <div class="space-y-6">
            <div>
              <h3 class="ibm-subtitulo">Quem atende e onde</h3>
              <div class="grid grid-cols-1 lg:grid-cols-2 gap-4">
                <div>
                  {#if profissionalSelecionado}
                    <p class="ibm-rotulo">Profissional</p>
                    <div class="ibm-selecionado">
                      <span>{profissionalSelecionado.nome}</span>
                      <button type="button" onclick={limparProfissional} class="ibm-btn ibm-btn--fantasma">Remover</button>
                    </div>
                  {:else}
                    <label for="cota-profissional" class="ibm-rotulo">Profissional</label>
                    <input
                      id="cota-profissional"
                      type="text"
                      placeholder="Buscar profissional por nome..."
                      bind:value={termoBuscaProfissional}
                      oninput={buscarProfissional}
                      class="ibm-campo w-full"
                    />
                    {#if buscandoProfissional}
                      <p class="ibm-ajuda" role="status">Buscando...</p>
                    {:else if resultadosProfissional.length > 0}
                      <ul class="ibm-resultados">
                        {#each resultadosProfissional as p (p.id)}
                          <li>
                            <button type="button" onclick={() => selecionarProfissional(p)} class="ibm-resultado">
                              {p.nome}
                            </button>
                          </li>
                        {/each}
                      </ul>
                    {/if}
                  {/if}
                </div>

                <div>
                  <label for="cota-local" class="ibm-rotulo">Local de atendimento</label>
                  <select id="cota-local" bind:value={form.localAgendamentoId} class="ibm-campo w-full">
                    <option value={null}>Sem local definido (a unidade informa ao agendar)</option>
                    {#each locaisAgendamento as loc (loc.id)}
                      <option value={loc.id}>{loc.nomeLocal ?? loc.nome}</option>
                    {/each}
                  </select>
                </div>
              </div>
            </div>

            <div class="ibm-divisor space-y-6">
              <h3 class="ibm-subtitulo">Quando atende</h3>

              {#if form.tipoPeriodo === 'MENSAL'}
                <div class="ibm-info">
                  Cota mensal não tem uma data única — marque os dias da semana em que o
                  profissional atende. O sistema calcula automaticamente as próximas datas
                  previstas dentro do mês, para exibir como referência ao agendar — mas o
                  saldo continua sendo um só para o mês inteiro, somando todos os dias.
                </div>

                <fieldset class="ibm-conjunto">
                  <legend class="ibm-rotulo">Dias da semana de atendimento</legend>
                  <div class="ibm-dias">
                    {#each DIAS_SEMANA as dia (dia.sigla)}
                      {@const marcado = form.diasSemana.includes(dia.sigla)}
                      <button
                        type="button"
                        onclick={() => alternarDiaSemana(dia.sigla)}
                        aria-pressed={marcado}
                        class="ibm-dia"
                      >
                        {#if marcado}
                          <svg
                            class="ibm-dia-marca"
                            fill="none"
                            viewBox="0 0 24 24"
                            stroke="currentColor"
                            stroke-width="3"
                            aria-hidden="true"
                          >
                            <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
                          </svg>
                        {/if}
                        {dia.label}
                      </button>
                    {/each}
                  </div>
                </fieldset>
              {/if}

              <div class="space-y-4">
                <div>
                  <label class="ibm-opcao">
                    <input type="checkbox" bind:checked={form.horarioDinamico} aria-describedby="cota-horario-ajuda" />
                    Deseja que os horários sejam criados automaticamente para os pacientes?
                  </label>
                  <p id="cota-horario-ajuda" class="ibm-ajuda">
                    {#if form.horarioDinamico}
                      O sistema divide o período abaixo igualmente entre as {form.quantidadeTotal || 'N'} vagas da
                      cota — cada paciente recebe um horário calculado automaticamente ao agendar.
                    {:else}
                      Sem horário automático: o operador informa a hora manualmente na tela de
                      agendamento; se não informar, este período fica só como referência.
                    {/if}
                  </p>
                </div>
                <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <div>
                    <label for="cota-hora-inicial" class="ibm-rotulo">Hora inicial {form.horarioDinamico ? '*' : ''}</label>
                    <input id="cota-hora-inicial" type="time" bind:value={form.horaInicial} class="ibm-campo w-full" />
                  </div>
                  <div>
                    <label for="cota-hora-final" class="ibm-rotulo">Hora final {form.horarioDinamico ? '*' : ''}</label>
                    <input id="cota-hora-final" type="time" bind:value={form.horaFinal} class="ibm-campo w-full" />
                  </div>
                </div>
              </div>
            </div>
          </div>
        {/if}
      </div>
    </section>

    <div class="ibm-rodape">
      <button onclick={() => goto('/admin/cotas')} class="ibm-btn ibm-btn--grande ibm-btn--secundario">
        Cancelar
      </button>
      <button onclick={aoClicarSalvar} disabled={salvando} class="ibm-btn ibm-btn--grande ibm-btn--primario">
        {salvando ? 'Salvando...' : 'Salvar Cota'}
      </button>
    </div>
  </div>
</Content>

<style>
  /*
   * Mistura SIRG + Carbon (IBM), escopada a esta tela. Mesmos nomes de token e de
   * classe de /admin/cotas (lista); os valores marcados "SIRG" abaixo divergem
   * da lista de propósito. A mistura é controlada pelos tokens da seção
   * "Mistura SIRG/IBM": para voltar ao IBM puro, troque cada um pelo valor
   * indicado em "IBM". Campos, rótulos e tipografia continuam IBM; moldura
   * (painel, título, botões, fundo) segue o resto do SIRG (DESIGN.md, seções 2 a 6).
   * O acento é o emerald do SIRG. A fonte é uma pilha com fallback: nada é baixado.
   */
  .cotas-ibm {
    --ibm-acento: #047857;
    --ibm-acento-hover: #065f46;
    --ibm-acento-ativo: #064e3b;
    --ibm-acento-suave: #d1fae5;
    --ibm-acento-suave-texto: #065f46;
    --ibm-fonte: 'IBM Plex Sans', 'Helvetica Neue', Arial, sans-serif;

    --ibm-tinta: #161616;
    --ibm-tinta-suave: #525252;
    --ibm-fundo: #f3f4f6; /* SIRG: gray-100 (IBM: #f4f4f4) */
    --ibm-camada: #ffffff;
    --ibm-campo: #f4f4f4;
    --ibm-linha: #e0e0e0;
    --ibm-borda-campo: #8c8c8c;
    --ibm-hover: #ececec;
    --ibm-erro-texto: #a2191f;
    --ibm-ok-fundo: #d1fae5; /* SIRG: emerald-100 (IBM: #a7f0ba) */
    --ibm-ok-texto: #065f46; /* SIRG: emerald-800 (IBM: #0e6027) */
    --ibm-aviso-fundo: #fcf4d6;
    --ibm-aviso-borda: #f1c21b;

    /* --- Mistura SIRG/IBM: ajuste aqui --- */
    /* Painel: cartão do SIRG (rounded-lg + shadow). IBM: 0 / none / 1px solid var(--ibm-linha) */
    --ibm-raio-painel: 8px;
    --ibm-sombra-painel: 0 1px 3px 0 rgb(0 0 0 / 0.1), 0 1px 2px -1px rgb(0 0 0 / 0.1);
    --ibm-borda-painel: 0;
    /* Botões, listas e avisos: rounded-md do SIRG. IBM: 0 */
    --ibm-raio-controle: 6px;
    /* Campos (cinza com linha inferior): estilo IBM, mantido. SIRG seria 6px */
    --ibm-raio-campo: 0;
    /* Título de painel: SIRG (emerald-800, negrito, border-b). IBM: peso 400, barra lateral */
    --ibm-titulo-cor: #065f46;
    --ibm-titulo-peso: 700;
    --ibm-titulo-tamanho: 18px;
    --ibm-titulo-divisor: 1px solid #e5e7eb;
    /* Botão secundário: Cancelar claro como no SIRG. IBM: fundo #393939, texto branco */
    --ibm-secundario-fundo: #ffffff;
    --ibm-secundario-texto: #374151;
    --ibm-secundario-hover: #f9fafb;

    background: var(--ibm-fundo);
    color: var(--ibm-tinta);
    font-family: var(--ibm-fonte);
    font-size: 14px;
    line-height: 1.29;
    letter-spacing: 0.16px;
  }

  /* Painel: cartão branco (raio, sombra e borda vêm dos tokens --ibm-*-painel) */
  .ibm-painel {
    background: var(--ibm-camada);
    border: var(--ibm-borda-painel);
    border-radius: var(--ibm-raio-painel);
    box-shadow: var(--ibm-sombra-painel);
  }

  /* Título de painel: padrão SIRG (text-lg font-bold emerald-800 + border-b) */
  .ibm-grupo-titulo {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px 12px;
    margin-bottom: 24px;
    padding-bottom: 8px;
    border-bottom: var(--ibm-titulo-divisor);
    font-size: var(--ibm-titulo-tamanho);
    font-weight: var(--ibm-titulo-peso);
    line-height: 1.4;
    color: var(--ibm-titulo-cor);
  }
  .ibm-grupo-titulo-marca {
    font-size: 12px;
    font-weight: 400;
  }

  /* Subtítulo de grupo dentro de um painel (ex.: "Quem atende e onde") */
  .ibm-subtitulo {
    margin-bottom: 16px;
    font-size: 14px;
    font-weight: 600;
    line-height: 1.43;
    color: var(--ibm-tinta);
  }

  /* Duas colunas no mesmo painel: filete vertical só quando lado a lado */
  @media (min-width: 1024px) {
    .ibm-coluna-divisor {
      padding-left: 23px;
      margin-left: -24px;
      border-left: 1px solid var(--ibm-linha);
    }
  }

  /* Rótulo, ajuda e agrupamento */
  .ibm-rotulo {
    display: block;
    margin-bottom: 8px;
    padding: 0;
    font-size: 12px;
    line-height: 1.33;
    letter-spacing: 0.32px;
    color: var(--ibm-tinta-suave);
  }
  .ibm-ajuda {
    margin-top: 8px;
    font-size: 12px;
    line-height: 1.33;
    letter-spacing: 0.32px;
    color: var(--ibm-tinta-suave);
  }
  .ibm-texto-apoio {
    margin: -8px 0 24px;
    font-size: 14px;
    line-height: 1.43;
    color: var(--ibm-tinta-suave);
  }
  .ibm-conjunto {
    min-width: 0;
    padding: 0;
    border: 0;
  }
  .ibm-divisor {
    padding-top: 24px;
    border-top: 1px solid var(--ibm-linha);
  }
  .ibm-suave {
    color: var(--ibm-tinta-suave);
  }
  .ibm-suave-texto {
    color: var(--ibm-tinta-suave);
    font-size: 14px;
  }
  .ibm-erro-texto {
    color: var(--ibm-erro-texto);
    font-size: 14px;
  }

  /* Campos: fundo cinza, borda inferior de 3:1, foco em contorno de 2 px */
  .ibm-campo {
    min-height: 40px;
    padding: 0 16px;
    border: 1px solid transparent;
    border-bottom: 1px solid var(--ibm-borda-campo);
    border-radius: var(--ibm-raio-campo);
    background-color: var(--ibm-campo);
    box-shadow: none;
    color: var(--ibm-tinta);
    font-family: inherit;
    font-size: 14px;
    letter-spacing: 0.16px;
  }
  select.ibm-campo {
    padding-right: 40px;
  }
  .ibm-campo::placeholder {
    color: var(--ibm-tinta-suave);
    opacity: 1;
  }
  .ibm-campo:focus,
  .ibm-campo:focus-visible {
    outline: 2px solid var(--ibm-acento);
    outline-offset: -2px;
    border-color: transparent;
    border-bottom-color: var(--ibm-borda-campo);
    box-shadow: none;
  }

  /* Radio e checkbox: alvo de 40 px, marcação emerald (extrapolação do Carbon) */
  .ibm-opcoes {
    display: flex;
    flex-wrap: wrap;
    gap: 0 24px;
  }
  .ibm-opcao {
    display: flex;
    align-items: center;
    gap: 8px;
    min-height: 40px;
    cursor: pointer;
    font-size: 14px;
    color: var(--ibm-tinta);
  }
  .ibm-opcao input {
    flex: none;
    width: 20px;
    height: 20px;
    margin: 0;
    border: 1px solid var(--ibm-tinta);
    background-color: var(--ibm-camada);
    color: var(--ibm-acento);
    box-shadow: none;
    cursor: pointer;
  }
  .ibm-opcao input:checked {
    border-color: var(--ibm-acento);
    background-color: currentColor;
  }
  .ibm-opcao input:focus,
  .ibm-opcao input:focus-visible {
    outline: 2px solid var(--ibm-acento);
    outline-offset: 2px;
    box-shadow: none;
  }
  .ibm-opcao--linha {
    padding: 0 16px;
  }
  .ibm-opcao--linha:hover {
    background: var(--ibm-hover);
  }

  /* Listas dentro de formulário (unidades, especialidades do grupo) */
  .ibm-lista-rolagem {
    max-height: 192px;
    overflow-y: auto;
    border: 1px solid var(--ibm-linha);
    border-radius: var(--ibm-raio-controle);
    background: var(--ibm-camada);
  }
  .ibm-lista-rolagem:focus-visible {
    outline: 2px solid var(--ibm-acento);
    outline-offset: -2px;
  }
  .ibm-lista-vazia {
    padding: 12px 16px;
  }
  .ibm-lista-item {
    padding: 10px 16px;
    border-bottom: 1px solid var(--ibm-linha);
    font-size: 14px;
  }
  .ibm-lista-item:last-child {
    border-bottom: 0;
  }
  .ibm-sublista {
    margin-top: 16px;
  }
  .ibm-sublista .ibm-rotulo {
    margin-bottom: 8px;
  }

  /* Resultado da criação em lote */
  .ibm-resultado-lote {
    overflow: hidden;
    border: 1px solid var(--ibm-linha);
    border-radius: var(--ibm-raio-controle);
  }
  .ibm-resultado-lote-titulo {
    padding: 8px 16px;
    background: var(--ibm-linha);
    font-size: 14px;
    font-weight: 600;
  }
  .ibm-resultado-lote-item {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 8px 16px;
    border-top: 1px solid var(--ibm-linha);
  }
  .ibm-resultado-lote-item:first-child {
    border-top: 0;
  }
  .ibm-resultado-lote-erro {
    font-size: 12px;
    text-align: right;
  }

  /* Dias da semana: botões de alternância; selecionado = fundo emerald + marca */
  .ibm-dias {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
  .ibm-dia {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    min-width: 96px;
    min-height: 40px;
    padding: 0 16px;
    border: 1px solid var(--ibm-borda-campo);
    border-radius: var(--ibm-raio-controle);
    background: var(--ibm-camada);
    color: var(--ibm-tinta);
    font-family: inherit;
    font-size: 14px;
    letter-spacing: 0.16px;
    cursor: pointer;
  }
  .ibm-dia:hover {
    background: var(--ibm-hover);
  }
  .ibm-dia[aria-pressed='true'] {
    border-color: var(--ibm-acento);
    background: var(--ibm-acento);
    color: #ffffff;
  }
  .ibm-dia[aria-pressed='true']:hover {
    border-color: var(--ibm-acento-hover);
    background: var(--ibm-acento-hover);
  }
  .ibm-dia:focus-visible {
    outline: 2px solid var(--ibm-acento);
    outline-offset: 2px;
  }
  .ibm-dia-marca {
    width: 14px;
    height: 14px;
    flex: none;
  }

  /* Profissional: selecionado e resultados da busca */
  .ibm-selecionado {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    min-height: 40px;
    padding: 0 8px 0 16px;
    border-radius: var(--ibm-raio-controle);
    background: var(--ibm-acento-suave);
    color: var(--ibm-acento-suave-texto);
  }
  .ibm-resultados {
    margin-top: 4px;
    max-height: 160px;
    overflow-y: auto;
    border: 1px solid var(--ibm-linha);
    border-radius: var(--ibm-raio-controle);
    background: var(--ibm-camada);
  }
  .ibm-resultado {
    display: block;
    width: 100%;
    min-height: 40px;
    padding: 0 16px;
    text-align: left;
    font-family: inherit;
    font-size: 14px;
    color: var(--ibm-tinta);
    cursor: pointer;
  }
  .ibm-resultado:hover {
    background: var(--ibm-hover);
  }
  .ibm-resultado:focus-visible {
    outline: 2px solid var(--ibm-acento);
    outline-offset: -2px;
  }

  /* Mensagens em linha */
  .ibm-info {
    padding: 12px 16px;
    border-left: 4px solid var(--ibm-acento);
    border-radius: 0 var(--ibm-raio-controle) var(--ibm-raio-controle) 0;
    background: var(--ibm-fundo);
    color: var(--ibm-tinta);
    font-size: 14px;
    line-height: 1.43;
  }
  .ibm-aviso {
    padding: 12px 16px;
    border-left: 4px solid var(--ibm-aviso-borda);
    border-radius: 0 var(--ibm-raio-controle) var(--ibm-raio-controle) 0;
    background: var(--ibm-aviso-fundo);
    color: var(--ibm-tinta);
    font-size: 14px;
  }

  /* Badges (pílula, como o Carbon faz para status) */
  .ibm-badge {
    display: inline-flex;
    align-items: center;
    min-height: 20px;
    padding: 0 8px;
    border: 1px solid transparent;
    border-radius: 9999px;
    font-size: 12px;
    font-weight: 400;
    line-height: 1.33;
    letter-spacing: 0.32px;
    white-space: nowrap;
  }
  .ibm-badge--neutro {
    background: var(--ibm-linha);
    color: var(--ibm-tinta);
  }
  .ibm-badge--ok {
    background: var(--ibm-ok-fundo);
    color: var(--ibm-ok-texto);
  }

  /* Botões: raio zero, texto de ação */
  .ibm-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 40px;
    padding: 0 16px;
    border: 1px solid transparent;
    border-radius: var(--ibm-raio-controle);
    font-family: inherit;
    font-size: 14px;
    font-weight: 500;
    letter-spacing: 0.16px;
    white-space: nowrap;
    cursor: pointer;
  }
  .ibm-btn:focus-visible {
    outline: 2px solid var(--ibm-acento);
    outline-offset: 2px;
  }
  .ibm-btn:disabled {
    background: var(--ibm-linha);
    color: var(--ibm-tinta-suave);
    cursor: not-allowed;
  }
  .ibm-btn--grande {
    min-height: 48px;
    padding: 0 24px;
  }
  .ibm-btn--primario {
    background: var(--ibm-acento);
    color: #ffffff;
  }
  .ibm-btn--primario:hover:not(:disabled) {
    background: var(--ibm-acento-hover);
  }
  .ibm-btn--primario:active:not(:disabled) {
    background: var(--ibm-acento-ativo);
  }
  .ibm-btn--secundario {
    border-color: var(--ibm-borda-campo);
    background: var(--ibm-secundario-fundo);
    color: var(--ibm-secundario-texto);
  }
  .ibm-btn--secundario:hover {
    background: var(--ibm-secundario-hover);
  }
  .ibm-btn--secundario:active {
    background: var(--ibm-hover);
  }
  .ibm-btn--fantasma {
    min-height: 32px;
    padding: 0 8px;
    background: transparent;
    color: var(--ibm-acento);
    text-decoration: underline;
    text-underline-offset: 3px;
  }
  .ibm-btn--fantasma:hover {
    background: var(--ibm-hover);
    color: var(--ibm-acento-hover);
  }

  /* Rodapé do formulário: ação primária dominante, à direita */
  .ibm-rodape {
    display: flex;
    flex-direction: column-reverse;
    gap: 8px;
  }
  @media (min-width: 640px) {
    .ibm-rodape {
      flex-direction: row;
      justify-content: flex-end;
    }
  }

  @media (prefers-reduced-motion: no-preference) {
    .ibm-btn,
    .ibm-dia,
    .ibm-campo,
    .ibm-resultado {
      transition:
        background-color 110ms ease-out,
        border-color 110ms ease-out;
    }
  }
</style>
