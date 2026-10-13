<script>
  import { onMount, tick } from 'svelte';
  import { goto } from '$app/navigation';
  import { page } from '$app/stores';
  import { getApi, postApi, putApi } from '$lib/api.js';
  import { formatarHora } from '$lib/cotas.js';
  import { paraDataISO } from '$lib/dataIso.js';
  import { formatarData } from '$lib/datas.js';
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

  // Período como veio do servidor: decide se a edição só mudou a data de uma cota por data
  // (caso em que há pergunta de migração dos agendamentos) e o que o PUT ainda precisa levar.
  let periodoOriginal = $state(null);
  let payloadOriginal = $state(null);
  let dialogoMigracao = $state(null); // { previa, dataAnterior, dataNova, payload }
  let erroMigracao = $state('');
  let migrando = $state(false);
  let dialogoEl = $state(null);

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
      // O backend manda LocalDate como array; o campo de data e a comparação em salvar() precisam de AAAA-MM-DD.
      const dataISO = paraDataISO(c.dataEspecifica);
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
        dataEspecifica: dataISO || new Date().toISOString().slice(0, 10),
        quantidadeTotal: c.quantidadeTotal,
        ativo: c.ativo,
        profissionalId: c.profissionalId ?? null,
        localAgendamentoId: c.localAgendamentoId ?? null,
        horarioDinamico: c.horarioDinamico ?? false,
        tempoMedioAtendimentoMinutos: c.tempoMedioAtendimentoMinutos ?? null,
        horaInicial: formatarHora(c.horaInicial),
        horaFinal: formatarHora(c.horaFinal),
        diasSemana: c.diasSemana ?? []
      };
      if (c.profissionalId) {
        profissionalSelecionado = { id: c.profissionalId, nome: c.profissionalNome };
      }
      periodoOriginal = {
        tipoPeriodo: c.tipoPeriodo,
        periodo: c.periodo ?? null,
        dataEspecifica: dataISO || null
      };
      payloadOriginal = montarPayload();
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

    const payload = montarPayload();

    // Só a data de uma cota por data mudou: se ela tem agendamentos, pergunta se migra.
    const soDataMudou = periodoOriginal
      && form.tipoPeriodo === 'DATA'
      && periodoOriginal.tipoPeriodo === 'DATA'
      && form.dataEspecifica
      && form.dataEspecifica !== periodoOriginal.dataEspecifica;

    salvando = true;
    try {
      if (soDataMudou) {
        const resPrevia = await getApi(
          `cotas/${cotaId}/migracao-data/previa?dataEspecifica=${encodeURIComponent(form.dataEspecifica)}`
        );
        if (!resPrevia.ok) {
          const erro = await resPrevia.json().catch(() => ({}));
          toast.error(erro.message ?? 'Erro ao verificar os agendamentos da cota.');
          return;
        }
        const previa = await resPrevia.json();
        if (previa.agendamentos > 0) {
          await abrirDialogoMigracao({
            previa,
            dataAnterior: periodoOriginal.dataEspecifica,
            dataNova: form.dataEspecifica,
            payload
          });
          return;
        }
      }
      await atualizarCota(cotaId, payload);
    } catch {
      toast.error('Erro ao salvar cota.');
    } finally {
      salvando = false;
    }
  }

  function montarPayload() {
    const porGrupoUnidades = form.titular === 'GRUPO';
    const porGrupoEsp = form.escopo === 'GRUPO_ESPECIALIDADES';
    const temDiasSemana = form.diasSemana.length > 0;
    return {
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
  }

  /** PUT da cota; o 409 do backend (ex.: mensal com agendamentos) aparece como mensagem. */
  async function atualizarCota(id, payload) {
    const res = await putApi(`cotas/${id}`, payload);
    if (!res.ok) {
      const erro = await res.json().catch(() => ({}));
      toast.error(erro.message ?? 'Erro ao salvar cota.');
      return false;
    }
    toast.success('Cota atualizada.');
    goto('/admin/cotas');
    return true;
  }

  // --- Migração dos agendamentos ao mudar a data ------------------------------

  async function abrirDialogoMigracao(dados) {
    erroMigracao = '';
    dialogoMigracao = dados;
    await tick();
    dialogoEl?.showModal();
  }

  function fecharDialogoMigracao() {
    if (dialogoEl?.open) dialogoEl.close();
    dialogoMigracao = null;
    erroMigracao = '';
  }

  /** Esc fecha (equivale a "Não"), exceto enquanto a migração está em andamento. */
  function aoCancelarDialogo(evento) {
    if (migrando) evento.preventDefault();
  }

  async function confirmarMigracao() {
    if (!dialogoMigracao || migrando) return;
    const { dataNova, payload } = dialogoMigracao;
    migrando = true;
    erroMigracao = '';
    try {
      const res = await postApi(`cotas/${cotaId}/migrar-data`, { dataEspecifica: dataNova });
      if (!res.ok) {
        const erro = await res.json().catch(() => ({}));
        erroMigracao = erro.message ?? 'Erro ao migrar os agendamentos. Nada foi alterado.';
        return;
      }
      const resultado = await res.json();
      migrando = false; // antes de fechar: onclose reabre o diálogo enquanto migrando
      fecharDialogoMigracao();

      if (resultado.unificada) {
        toast.success('Cotas unificadas. Revise a quantidade.');
        goto('/admin/cotas');
        return;
      }

      // A migração já gravou a nova data: o que sobrou de diferente vai no PUT.
      periodoOriginal = { ...periodoOriginal, dataEspecifica: dataNova };
      const outrosCamposMudaram = JSON.stringify({ ...payload, dataEspecifica: null })
        !== JSON.stringify({ ...payloadOriginal, dataEspecifica: null });
      if (!outrosCamposMudaram) {
        toast.success(`${resultado.agendamentosMigrados} agendamento(s) migrado(s) para ${formatarData(dataNova)}.`);
        goto('/admin/cotas');
        return;
      }
      payloadOriginal = { ...payloadOriginal, dataEspecifica: dataNova };
      salvando = true;
      try {
        const salvou = await atualizarCota(resultado.cotaResultanteId, payload);
        if (!salvou) {
          toast.error('Os agendamentos foram migrados, mas as demais alterações não foram salvas.');
        }
      } catch {
        // O diálogo já fechou: o erro precisa aparecer fora dele, e a data nova já vale como original.
        toast.error('Os agendamentos foram migrados, mas as demais alterações não foram salvas.');
      } finally {
        salvando = false;
      }
    } catch {
      erroMigracao = 'Erro ao migrar os agendamentos.';
    } finally {
      migrando = false;
    }
  }
</script>


<Content titleH1="Editar Cota" page="/admin/cotas">
  <div class="cotas-ibm flex-1 min-w-0 p-4 md:p-6 space-y-6">
    {#if carregando}
      <p class="ibm-suave-texto" role="status">Carregando...</p>
    {:else if origemAgenda}
      <section class="ibm-painel p-4 md:p-6 space-y-6">
        <p class="ibm-info">
          Esta cota foi gerada por uma agenda e não pode ser editada diretamente aqui.
          Para alterar vagas, vigência ou horário, edite a agenda de origem.
        </p>
        <div class="ibm-rodape">
          <button onclick={() => goto('/admin/cotas')} class="ibm-btn ibm-btn--grande ibm-btn--secundario">
            Voltar
          </button>
          <button onclick={() => goto(`/liberacao-agenda/${agendaId}`)} class="ibm-btn ibm-btn--grande ibm-btn--primario">
            Ir para a agenda
          </button>
        </div>
      </section>
    {:else}
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
            {:else}
              <div>
                <label for="cota-grupo-unidades" class="ibm-rotulo">Grupo de unidades *</label>
                <select id="cota-grupo-unidades" bind:value={form.grupoUnidadesId} class="ibm-campo w-full">
                  <option value={null}>Selecionar grupo...</option>
                  {#each grupos as g (g.id)}
                    <option value={g.id}>{g.nome}</option>
                  {/each}
                </select>
              </div>
            {/if}

            <div class="ibm-divisor">
              <label class="ibm-opcao">
                <input type="checkbox" id="ativoEdit" bind:checked={form.ativo} />
                Cota ativa
              </label>
            </div>
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
                  bind:value={form.grupoEspecialidadesId}
                  class="ibm-campo w-full"
                >
                  <option value={null}>Selecionar grupo...</option>
                  {#each grupos as g (g.id)}
                    <option value={g.id}>{g.nome}</option>
                  {/each}
                </select>
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
          Profissional, horário e local ficam travados na tela de agendamento da unidade.
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
                    {#if proximasOcorrencias.length > 0}
                      <p class="ibm-ajuda">
                        Próximas datas previstas: {proximasOcorrencias.map((d) => formatarData(d)).join(', ')}
                      </p>
                    {/if}
                  </fieldset>
                {/if}

                <div class="space-y-4">
                  <div>
                    <label class="ibm-opcao">
                      <input type="checkbox" bind:checked={form.horarioDinamico} aria-describedby="cota-horario-ajuda" />
                      Deseja que os horários sejam criados automaticamente para os pacientes?
                    </label>
                    {#if form.horarioDinamico}
                      <p id="cota-horario-ajuda" class="ibm-ajuda">
                        O sistema divide o período abaixo igualmente entre as {form.quantidadeTotal || 'N'} vagas da
                        cota — cada paciente recebe um horário calculado automaticamente ao agendar.
                      </p>
                    {/if}
                    {#if !form.horarioDinamico}
                      <p id="cota-horario-ajuda" class="ibm-ajuda">
                        Sem horário automático: o operador informa a hora manualmente na tela de
                        agendamento; se não informar, este período fica só como referência.
                      </p>
                    {/if}
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
        <button onclick={salvar} disabled={salvando} class="ibm-btn ibm-btn--grande ibm-btn--primario">
          {salvando ? 'Salvando...' : 'Salvar Cota'}
        </button>
      </div>

      <dialog
        bind:this={dialogoEl}
        class="ibm-dialogo"
        aria-labelledby="migracao-titulo"
        aria-describedby="migracao-descricao"
        aria-busy={migrando}
        oncancel={aoCancelarDialogo}
        onclose={() => {
          // Esc repetido pode fechar sem disparar "cancel": durante a migração, reabre.
          if (migrando) { dialogoEl?.showModal(); return; }
          dialogoMigracao = null;
          erroMigracao = '';
        }}
      >
        {#if dialogoMigracao}
          {@const previa = dialogoMigracao.previa}
          <div class="ibm-dialogo-caixa">
            <header class="ibm-dialogo-topo">
              <svg
                class="ibm-dialogo-icone"
                fill="none"
                viewBox="0 0 24 24"
                stroke="currentColor"
                stroke-width="2"
                aria-hidden="true"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  d="M12 9v4m0 4h.01M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"
                />
              </svg>
              <h2 id="migracao-titulo" class="ibm-dialogo-titulo">Migrar os agendamentos para a nova data?</h2>
            </header>

            <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
            <div class="ibm-dialogo-corpo" role="region" aria-label="Detalhes da migração" tabindex="0">
              <div id="migracao-descricao" class="ibm-resumo">
                <p class="ibm-resumo-total">
                  <strong class="ibm-resumo-numero">{previa.agendamentos}</strong>
                  <span>
                    <span class="ibm-resumo-legenda">
                      {previa.agendamentos === 1 ? 'agendamento nesta cota' : 'agendamentos nesta cota'}
                    </span>
                    <span class="ibm-resumo-itens">
                      ({previa.itens} {previa.itens === 1 ? 'item' : 'itens'})
                    </span>
                  </span>
                </p>
                <p class="ibm-resumo-datas">
                  <span class="ibm-resumo-data">
                    <span class="ibm-resumo-rotulo">Data atual</span>
                    <span class="ibm-resumo-valor">{formatarData(dialogoMigracao.dataAnterior)}</span>
                  </span>
                  <svg
                    class="ibm-resumo-seta"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                    stroke-width="2"
                    aria-hidden="true"
                  >
                    <path stroke-linecap="round" stroke-linejoin="round" d="M5 12h14M13 6l6 6-6 6" />
                  </svg>
                  <span class="sr-only">passa para</span>
                  <span class="ibm-resumo-data">
                    <span class="ibm-resumo-rotulo">Nova data</span>
                    <span class="ibm-resumo-valor ibm-resumo-valor--nova">{formatarData(dialogoMigracao.dataNova)}</span>
                  </span>
                </p>
              </div>

              <div>
                <h3 class="ibm-rotulo">Itens por situação</h3>
                <dl class="ibm-situacoes">
                  <div class="ibm-situacao">
                    <dt>Agendados</dt>
                    <dd>{previa.itensAgendados}</dd>
                  </div>
                  <div class="ibm-situacao">
                    <dt>Realizados</dt>
                    <dd>{previa.itensRealizados}</dd>
                  </div>
                  <div class="ibm-situacao">
                    <dt>Cancelados ou faltosos</dt>
                    <dd>{previa.itensCanceladosOuFaltosos}</dd>
                  </div>
                </dl>
              </div>

              <div class="ibm-avisos">
                <h3 class="ibm-avisos-titulo">
                  <svg
                    class="ibm-avisos-icone"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                    stroke-width="2"
                    aria-hidden="true"
                  >
                    <path
                      stroke-linecap="round"
                      stroke-linejoin="round"
                      d="M12 9v4m0 4h.01M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"
                    />
                  </svg>
                  Antes de confirmar
                </h3>
                <ul class="ibm-avisos-lista">
                  {#if previa.existeCotaNoDestino}
                    <li class="ibm-avisos-item">
                      <strong class="ibm-avisos-assunto">Cota já existente na nova data</strong>
                      Já existe uma cota igual em {formatarData(dialogoMigracao.dataNova)}
                      (total {previa.destinoQuantidadeTotal}, utilizadas {previa.destinoQuantidadeUtilizada}).
                      As duas serão unificadas: as quantidades são somadas e esta cota deixa de existir.
                      Revise a quantidade depois.
                    </li>
                  {/if}
                  {#if previa.divergente}
                    <li class="ibm-avisos-item">
                      <strong class="ibm-avisos-assunto">Contagem diferente da cota</strong>
                      Esta cota registra {previa.quantidadeUtilizada} vaga(s) usada(s), mas a contagem acima inclui
                      agendamentos que não consumiram esta cota (por exemplo, marcados pela administração).
                      Todos serão migrados.
                    </li>
                  {/if}
                  <li class="ibm-avisos-item">
                    <strong class="ibm-avisos-assunto">WhatsApp</strong>
                    Pacientes com atendimento futuro agendado recebem aviso de remarcação por WhatsApp.
                  </li>
                  <li class="ibm-avisos-item">
                    <strong class="ibm-avisos-assunto">Transporte sanitário</strong>
                    O transporte sanitário não é ajustado: confira os agendamentos de transporte dessa data.
                  </li>
                  {#if previa.solicitacoesFederadas > 0}
                    <li class="ibm-avisos-item">
                      <strong class="ibm-avisos-assunto">Outros municípios</strong>
                      {previa.solicitacoesFederadas} solicitação(ões) são de pacientes de outros municípios, que
                      recebem aviso da remarcação.
                    </li>
                  {/if}
                </ul>
              </div>

              <ul class="ibm-escolhas">
                <li><strong>Sim:</strong> os agendamentos passam para a nova data e a cota vai junto.</li>
                <li><strong>Não:</strong> nada é salvo e a data da cota não muda.</li>
              </ul>
            </div>

            <footer class="ibm-dialogo-base">
              {#if erroMigracao}
                <p class="ibm-erro ibm-dialogo-erro" role="alert">
                  <svg
                    class="ibm-dialogo-erro-icone"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                    stroke-width="2"
                    aria-hidden="true"
                  >
                    <circle cx="12" cy="12" r="9" />
                    <path stroke-linecap="round" stroke-linejoin="round" d="M12 8v4m0 4h.01" />
                  </svg>
                  <span>{erroMigracao}</span>
                </p>
              {/if}

              <div class="ibm-rodape ibm-dialogo-rodape">
                <!-- svelte-ignore a11y_autofocus -->
                <button type="button" onclick={fecharDialogoMigracao} disabled={migrando} autofocus
                  class="ibm-btn ibm-btn--grande ibm-btn--secundario">
                  Não
                </button>
                <button type="button" onclick={confirmarMigracao} disabled={migrando}
                  class="ibm-btn ibm-btn--grande ibm-btn--primario">
                  {#if migrando}<span class="ibm-giro" aria-hidden="true"></span>{/if}
                  {migrando ? 'Migrando...' : 'Sim, migrar'}
                </button>
              </div>
            </footer>
          </div>
        {/if}
      </dialog>
    {/if}
  </div>
</Content>

<style>
  /*
   * Mistura SIRG + Carbon (IBM), escopada a esta tela. Cópia do subconjunto usado
   * de /admin/cotas/nova (mesmos tokens, classes e valores). Mesmos nomes de token e de
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
  .ibm-suave-texto {
    color: var(--ibm-tinta-suave);
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

  .ibm-erro {
    padding: 12px 16px;
    border-left: 4px solid #da1e28;
    border-radius: 0 var(--ibm-raio-controle) var(--ibm-raio-controle) 0;
    background: #fff1f1;
    color: var(--ibm-tinta);
    font-size: 14px;
  }
  .ibm-avisos-lista {
    list-style: none;
  }

  /* Diálogo de migração dos agendamentos (nativo, modal) */
  .ibm-dialogo {
    /* Ícone de atenção: âmbar escuro, 3:1 ou mais sobre branco e sobre --ibm-aviso-fundo */
    --ibm-aviso-tinta: #8e6a00;
    --ibm-erro-tinta: #da1e28;
    --ibm-dialogo-respiro: 16px;

    width: min(36rem, calc(100vw - 32px));
    max-width: calc(100vw - 32px);
    max-height: calc(100vh - 32px);
    max-height: calc(100dvh - 32px);
    margin: auto;
    padding: 0;
    overflow: hidden;
    border: 0;
    border-radius: var(--ibm-raio-painel);
    background: var(--ibm-camada);
    color: var(--ibm-tinta);
    font-family: var(--ibm-fonte);
    font-size: 14px;
    line-height: 1.43;
    letter-spacing: 0.16px;
    box-shadow: 0 10px 25px rgb(0 0 0 / 0.25);
  }
  @media (min-width: 640px) {
    .ibm-dialogo {
      --ibm-dialogo-respiro: 24px;
    }
  }
  .ibm-dialogo::backdrop {
    background: rgb(22 22 22 / 0.6);
  }

  /* Título e ações ficam fixos; só o corpo rola. Assim o foco inicial em "Não"
     não desloca o resumo para fora da vista em telas baixas. */
  .ibm-dialogo-caixa {
    display: flex;
    flex-direction: column;
    max-height: inherit;
  }
  .ibm-dialogo-topo {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    flex: none;
    padding: var(--ibm-dialogo-respiro) var(--ibm-dialogo-respiro) 16px;
    border-bottom: 1px solid var(--ibm-linha);
  }
  .ibm-dialogo-icone {
    flex: none;
    width: 24px;
    height: 24px;
    color: var(--ibm-aviso-tinta);
  }
  .ibm-dialogo-titulo {
    font-size: var(--ibm-titulo-tamanho);
    font-weight: var(--ibm-titulo-peso);
    line-height: 1.33;
    color: var(--ibm-tinta);
  }
  .ibm-dialogo-corpo {
    display: flex;
    flex-direction: column;
    gap: 24px;
    flex: 1 1 auto;
    min-height: 0;
    padding: var(--ibm-dialogo-respiro);
    overflow-y: auto;
  }
  .ibm-dialogo-corpo:focus-visible {
    outline: 2px solid var(--ibm-acento);
    outline-offset: -2px;
  }
  .ibm-dialogo-base {
    display: flex;
    flex-direction: column;
    gap: 16px;
    flex: none;
    padding: 16px var(--ibm-dialogo-respiro) var(--ibm-dialogo-respiro);
    border-top: 1px solid var(--ibm-linha);
    background: var(--ibm-camada);
  }
  .ibm-dialogo-rodape .ibm-btn {
    gap: 8px;
  }
  .ibm-dialogo-erro {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    line-height: 1.43;
  }
  .ibm-dialogo-erro-icone {
    flex: none;
    width: 20px;
    height: 20px;
    color: var(--ibm-erro-tinta);
  }

  /* Resumo: o que se lê primeiro (quantos agendamentos, de qual data para qual) */
  .ibm-resumo {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 16px 32px;
    padding: 16px;
    border-radius: var(--ibm-raio-controle);
    background: var(--ibm-fundo);
  }
  .ibm-resumo-total {
    display: flex;
    flex-wrap: wrap;
    align-items: baseline;
    gap: 4px 12px;
  }
  .ibm-resumo-numero {
    font-size: 40px;
    font-weight: 600;
    line-height: 1;
    letter-spacing: 0;
    font-variant-numeric: tabular-nums;
  }
  .ibm-resumo-legenda {
    display: block;
    font-weight: 600;
  }
  .ibm-resumo-itens {
    display: block;
    color: var(--ibm-tinta-suave);
  }
  .ibm-resumo-datas {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-end;
    gap: 8px 12px;
  }
  .ibm-resumo-data {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  .ibm-resumo-rotulo {
    font-size: 12px;
    line-height: 1.33;
    letter-spacing: 0.32px;
    color: var(--ibm-tinta-suave);
  }
  .ibm-resumo-valor {
    font-size: 20px;
    line-height: 1.2;
    letter-spacing: 0;
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
    color: var(--ibm-tinta-suave);
  }
  .ibm-resumo-valor--nova {
    font-weight: 600;
    color: var(--ibm-tinta);
  }
  .ibm-resumo-seta {
    flex: none;
    width: 20px;
    height: 20px;
    margin-bottom: 2px;
    color: var(--ibm-tinta-suave);
  }

  /* Itens por situação: linhas no celular, três colunas a partir de 480 px */
  .ibm-situacoes {
    display: grid;
    grid-template-columns: minmax(0, 1fr);
    border-top: 1px solid var(--ibm-linha);
  }
  .ibm-situacao {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 16px;
    padding: 8px 0;
    border-bottom: 1px solid var(--ibm-linha);
  }
  .ibm-situacao dt {
    color: var(--ibm-tinta-suave);
  }
  .ibm-situacao dd {
    font-size: 16px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }
  @media (min-width: 480px) {
    .ibm-situacoes {
      grid-template-columns: repeat(3, minmax(0, 1fr));
      border-top: 0;
    }
    .ibm-situacao {
      flex-direction: column;
      justify-content: flex-start;
      gap: 4px;
      padding: 0 16px;
      border-bottom: 0;
      border-left: 1px solid var(--ibm-linha);
    }
    .ibm-situacao:first-child {
      padding-left: 0;
      border-left: 0;
    }
    .ibm-situacao dt {
      font-size: 12px;
      line-height: 1.33;
      letter-spacing: 0.32px;
    }
    .ibm-situacao dd {
      font-size: 20px;
      line-height: 1.2;
    }
  }

  /* Avisos: um só bloco de atenção, um assunto por linha */
  .ibm-avisos {
    padding: 16px;
    border-left: 4px solid var(--ibm-aviso-borda);
    border-radius: 0 var(--ibm-raio-controle) var(--ibm-raio-controle) 0;
    background: var(--ibm-aviso-fundo);
  }
  .ibm-avisos-titulo {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    font-weight: 600;
    line-height: 1.43;
  }
  .ibm-avisos-icone {
    flex: none;
    width: 20px;
    height: 20px;
    color: var(--ibm-aviso-tinta);
  }
  .ibm-avisos-item {
    margin-top: 12px;
    padding-top: 12px;
    border-top: 1px solid var(--ibm-aviso-borda);
  }
  .ibm-avisos-assunto {
    display: block;
    margin-bottom: 2px;
    font-weight: 600;
  }

  /* O que cada botão faz, logo acima das ações */
  .ibm-escolhas {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  /* Indicador de andamento dentro do botão (o texto "Migrando..." continua visível) */
  .ibm-giro {
    flex: none;
    width: 16px;
    height: 16px;
    border: 2px solid currentColor;
    border-right-color: transparent;
    border-radius: 50%;
  }
  @keyframes ibm-giro {
    to {
      transform: rotate(360deg);
    }
  }
  @media (prefers-reduced-motion: no-preference) {
    .ibm-giro {
      animation: ibm-giro 700ms linear infinite;
    }
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
  .ibm-btn--secundario:hover:not(:disabled) {
    background: var(--ibm-secundario-hover);
  }
  .ibm-btn--secundario:active:not(:disabled) {
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
