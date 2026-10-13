<script>
  import { onMount, onDestroy } from 'svelte';
  import { goto, afterNavigate } from '$app/navigation';
  import { page } from '$app/state';
  import { getApi } from '$lib/api.js';
  import Content from '$lib/Content.svelte';
  import { toast } from 'svelte-sonner';
  import { formatarPeriodoCota, agruparCotasPorAgenda, periodoCobertoPorGrupo } from '$lib/cotas.js';
  import {
    FILTROS_VAZIOS,
    filtrarCotas,
    lerFiltros,
    serializarFiltros,
    sanitizarFiltro,
    temParametrosDeFiltro,
    haFiltros as temFiltros,
    periodoInvalido,
    opcoesDeFiltro,
    tagsAtivas
  } from '$lib/cotasFiltro.js';
  import GrupoToggleButton from '$lib/GrupoToggleButton.svelte';
  import GrupoEspecialidadesPainel from '$lib/GrupoEspecialidadesPainel.svelte';

  // Dados só de leitura, sempre trocados por inteiro: $state.raw evita o proxy
  // profundo do Svelte sobre uma lista que pode ter milhares de cotas.
  let cotas = $state.raw([]);
  let unidades = $state.raw([]);
  let grupos = $state.raw([]);
  let agendasPorId = $state.raw({});
  let loading = $state(true);

  // Falha ao carregar um dado de apoio não esconde cota: o filtro correspondente
  // deixa tudo visível e a tela avisa (ver `filtrarCotas`).
  let unidadesCarregadas = $state(true);
  let gruposCarregados = $state(true);
  let agendasCarregadas = $state(true);

  // Filtros espelhados na URL: dá para recarregar, voltar e compartilhar o link.
  let filtros = $state(lerFiltros(page.url.searchParams));

  // Última query guardada na sessão do navegador, para o filtro sobreviver ao
  // voltar de "Nova cota"/"Editar" (essas telas voltam com goto('/admin/cotas')).
  const CHAVE_DA_SESSAO = 'admin-cotas:filtros';
  let temporizadorDaUrl = null;

  function guardarNaSessao(texto) {
    try {
      if (texto) sessionStorage.setItem(CHAVE_DA_SESSAO, texto);
      else sessionStorage.removeItem(CHAVE_DA_SESSAO);
    } catch {
      /* sessionStorage indisponível (modo privado etc.): só perde a restauração */
    }
  }

  function escreverNaUrl() {
    clearTimeout(temporizadorDaUrl);
    const naUrl = serializarFiltros(filtros).toString();
    guardarNaSessao(naUrl);
    goto(naUrl ? `?${naUrl}` : page.url.pathname, { replaceState: true, keepFocus: true, noScroll: true });
  }

  function definirFiltro(chave, valor) {
    filtros = { ...filtros, [chave]: sanitizarFiltro(chave, valor) };
    escreverNaUrl();
  }

  // A busca filtra a cada tecla; só a escrita na URL espera 300 ms (o Safari
  // limita chamadas seguidas de replaceState).
  function definirBusca(valor) {
    filtros = { ...filtros, q: valor };
    clearTimeout(temporizadorDaUrl);
    temporizadorDaUrl = setTimeout(escreverNaUrl, 300);
  }

  function limparFiltros() {
    filtros = { ...FILTROS_VAZIOS };
    escreverNaUrl();
  }

  afterNavigate((navegacao) => {
    const params = page.url.searchParams;
    const veioDeFormulario = navegacao.from?.url.pathname.startsWith('/admin/cotas/');
    if (veioDeFormulario && !temParametrosDeFiltro(params)) {
      let salvo = '';
      try {
        salvo = sessionStorage.getItem(CHAVE_DA_SESSAO) ?? '';
      } catch {
        /* sem sessionStorage: abre sem filtro */
      }
      if (salvo) {
        filtros = lerFiltros(new URLSearchParams(salvo));
        goto(`?${serializarFiltros(filtros).toString()}`, { replaceState: true, keepFocus: true, noScroll: true });
        return;
      }
    }
    // Clicar no item do menu para a mesma rota limpa os filtros, como em custos.
    if (navegacao.type === 'link') {
      filtros = lerFiltros(params);
      guardarNaSessao(serializarFiltros(filtros).toString());
    }
  });

  onDestroy(() => clearTimeout(temporizadorDaUrl));

  // Quais cotas de grupo estão com o painel de especialidades expandido.
  let gruposAbertos = $state({});
  function alternarGrupo(cotaId) {
    gruposAbertos = { ...gruposAbertos, [cotaId]: !gruposAbertos[cotaId] };
  }

  // Quais agendas estão com o grupo de cotas (ocorrência x unidade) expandido.
  let agendasAbertas = $state({});
  function alternarAgenda(agendaId) {
    agendasAbertas = { ...agendasAbertas, [agendaId]: !agendasAbertas[agendaId] };
  }

  onMount(async () => {
    await Promise.all([carregarUnidades(), carregarGrupos(), carregarAgendas()]);
    await carregarCotas();
  });

  async function carregarCotas() {
    loading = true;
    try {
      const res = await getApi('cotas');
      cotas = await res.json();
    } catch {
      toast.error('Erro ao carregar cotas.');
    } finally {
      loading = false;
    }
  }

  async function carregarUnidades() {
    try {
      const res = await getApi('unidades/ativas');
      const lista = res.ok ? await res.json() : [];
      unidades = Array.isArray(lista) ? lista : [];
      unidadesCarregadas = res.ok;
    } catch {
      /* ignora - combo fica vazio */
      unidadesCarregadas = false;
    }
  }

  async function carregarGrupos() {
    try {
      // O agrupamento de unidades reaproveita os Grupos de Relatorio (a unidade
      // aponta para o grupo via unidade.grupoRelatorioId).
      const res = await getApi('grupo-relatorio/listar');
      grupos = res.ok ? await res.json() : [];
      gruposCarregados = res.ok;
    } catch {
      /* ignora - combo fica vazio */
      gruposCarregados = false;
    }
  }

  async function carregarAgendas() {
    try {
      // Só usada para o resumo (executante/profissional) das cotas de origem
      // AGENDA agrupadas abaixo — falha aqui degrada pro fallback "Agenda #id",
      // não quebra a tela.
      const res = await getApi('agendas');
      const lista = res.ok ? await res.json() : [];
      agendasPorId = Object.fromEntries(lista.map((a) => [a.id, a]));
      agendasCarregadas = res.ok;
    } catch {
      /* ignora - grupo cai no fallback "Agenda #id" */
      agendasCarregadas = false;
    }
  }

  // Ao filtrar por unidade, mostra também as cotas do grupo a que ela pertence:
  // elas limitam essa unidade tanto quanto as cotas próprias. A regra de cada
  // filtro mora em `$lib/cotasFiltro.js` (testada em cotasFiltro.test.mjs).
  const cotasFiltradas = $derived(
    filtrarCotas(cotas, filtros, {
      unidades,
      grupos,
      agendasPorId,
      unidadesCarregadas,
      gruposCarregados,
      agendasCarregadas
    })
  );

  const opcoes = $derived(opcoesDeFiltro(cotas, grupos, agendasPorId));
  const tags = $derived(tagsAtivas(filtros, { unidades, ...opcoes }));
  const filtrosAtivos = $derived(temFiltros(filtros));
  const intervaloInvalido = $derived(periodoInvalido(filtros));

  // Total de cotas de cada agenda sem filtro, para a linha-resumo mostrar
  // "X de Y cotas" quando há filtro e não parecer o saldo da agenda inteira.
  const totalPorAgenda = $derived(
    new Map([...agruparCotasPorAgenda(cotas).porAgenda].map(([id, lista]) => [id, lista.length]))
  );

  // Uma cota de agenda recorrente materializa uma linha por ocorrência x
  // unidade solicitante — sem agrupar, uma agenda semanal de um mês vira
  // dezenas de linhas soltas na tabela. Aqui elas viram uma linha-resumo por
  // agenda, na posição em que a primeira delas apareceria na lista original;
  // cotas manuais continuam uma linha cada, sem nenhuma mudança de comportamento.
  const linhasExibidas = $derived(construirLinhas(cotasFiltradas));

  function construirLinhas(lista) {
    const { porAgenda } = agruparCotasPorAgenda(lista);
    const vistos = new Set();
    const linhas = [];
    for (const c of lista) {
      if (c.origem === 'AGENDA' && c.agendaId != null) {
        if (vistos.has(c.agendaId)) continue;
        vistos.add(c.agendaId);
        linhas.push({ tipo: 'agenda', agendaId: c.agendaId, cotas: porAgenda.get(c.agendaId) });
      } else {
        linhas.push({ tipo: 'manual', cota: c });
      }
    }
    return linhas;
  }

  function resumoGrupo(cotasDoGrupo) {
    const unidadesDistintas = new Set(cotasDoGrupo.map((c) => c.unidadeId)).size;
    return {
      quantidadeTotal: cotasDoGrupo.reduce((soma, c) => soma + c.quantidadeTotal, 0),
      quantidadeUtilizada: cotasDoGrupo.reduce((soma, c) => soma + c.quantidadeUtilizada, 0),
      saldoDisponivel: cotasDoGrupo.reduce((soma, c) => soma + c.saldoDisponivel, 0),
      unidadesDistintas,
      periodo: periodoCobertoPorGrupo(cotasDoGrupo)
    };
  }
</script>

{#snippet linhaCota(c)}
  <tr>
    <td class="ibm-titular">
      {#if c.grupoUnidadesId}
        <span class="ibm-badge ibm-badge--neutro">Grupo</span>
        <span class="ml-2">{c.grupoUnidadesNome}</span>
      {:else}
        {c.unidadeNome}
      {/if}
    </td>
    <td>
      {#if c.grupoEspecialidadesId}
        <span class="ibm-badge ibm-badge--neutro">Grupo</span>
        <span class="ml-2">{c.grupoEspecialidadesNome}</span>
        <GrupoToggleButton
          variante="ibm"
          grupo={grupos.find((g) => g.id === c.grupoEspecialidadesId)}
          aberto={!!gruposAbertos[c.id]}
          onToggle={() => alternarGrupo(c.id)}
        />
      {:else if c.especialidadeNome}
        {c.especialidadeNome}
      {:else}
        <span class="ibm-suave">Geral (todas)</span>
      {/if}
      {#if c.profissionalNome || c.horaInicial || c.localAgendamentoNome || (c.diasSemana && c.diasSemana.length > 0)}
        <div class="ibm-detalhe mt-0.5">
          {#if c.profissionalNome}{c.profissionalNome}{/if}
          {#if c.horaInicial}
            &nbsp;· {c.horaInicial.slice(0,5)}–{c.horaFinal ? c.horaFinal.slice(0,5) : '-'}
          {/if}
          {#if c.localAgendamentoNome}
            &nbsp;· {c.localAgendamentoNome}
          {/if}
          {#if c.diasSemana && c.diasSemana.length > 0}
            &nbsp;· {c.diasSemana.join(', ')}
          {/if}
        </div>
      {/if}
    </td>
    <td>
      <span class="ibm-badge {c.tipoPeriodo === 'DATA' ? 'ibm-badge--neutro' : 'ibm-badge--contorno'}">
        {c.tipoPeriodo === 'DATA' ? 'Por Data' : 'Mensal'}
      </span>
    </td>
    <td>{formatarPeriodoCota(c)}</td>
    <td class="text-right ibm-num">{c.quantidadeTotal}</td>
    <td class="text-right ibm-num">{c.quantidadeUtilizada}</td>
    <td class="text-right ibm-num ibm-saldo {c.saldoDisponivel <= 0 ? 'ibm-saldo--zero' : 'ibm-saldo--ok'}">
      {c.saldoDisponivel}
    </td>
    <td>
      <span class="ibm-badge {c.ativo ? 'ibm-badge--ok' : 'ibm-badge--erro'}">
        {c.ativo ? 'Ativa' : 'Inativa'}
      </span>
    </td>
    <td>
      {#if c.origem === 'AGENDA'}
        <span class="ibm-badge ibm-badge--acento">Agenda</span>
      {:else}
        <span class="ibm-badge ibm-badge--contorno">Manual</span>
      {/if}
    </td>
    <td>
      {#if c.origem === 'AGENDA'}
        <button onclick={() => goto(`/liberacao-agenda/${c.agendaId}`)} class="ibm-btn ibm-btn--terciario">
          Ver agenda
        </button>
      {:else}
        <button onclick={() => goto(`/admin/cotas/${c.id}/editar`)} class="ibm-btn ibm-btn--terciario">
          Editar
        </button>
      {/if}
    </td>
  </tr>
{/snippet}

<Content titleH1="Cotas por Unidade" page="/admin/cotas">
  <div class="cotas-ibm flex-1 min-w-0 p-4 md:p-6 space-y-6">
    <div class="flex items-center justify-between gap-4">
      <h2 class="ibm-titulo-secao">Filtros</h2>
      <button onclick={() => goto('/admin/cotas/nova')} class="ibm-btn ibm-btn--primario">+ Nova Cota</button>
    </div>

    {#snippet campoSelect(id, rotulo, valor, chave, itens, opcoesExtras = {})}
      <div class="min-w-0">
        <label for={id} class="ibm-rotulo">{rotulo}</label>
        <select
          {id}
          value={valor}
          disabled={opcoesExtras.desabilitado}
          onchange={(e) => definirFiltro(chave, e.currentTarget.value)}
          class="ibm-campo w-full"
        >
          <option value="">{opcoesExtras.todos ?? 'Todos'}</option>
          {#if !loading && valor && !itens.some((item) => item.valor === valor)}
            <option value={valor}>Não encontrado (#{valor})</option>
          {/if}
          {#each itens as item (item.valor)}
            <option value={item.valor}>{item.rotulo}</option>
          {/each}
        </select>
      </div>
    {/snippet}

    <section aria-label="Filtros da lista de cotas" class="ibm-painel p-4 md:p-6 space-y-4">
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div class="min-w-0 sm:col-span-2">
          <label for="filtro-busca" class="ibm-rotulo">Buscar</label>
          <input
            id="filtro-busca"
            type="search"
            value={filtros.q}
            maxlength="80"
            autocomplete="off"
            placeholder="Unidade, especialidade, profissional, executante ou local"
            oninput={(e) => definirBusca(e.currentTarget.value)}
            class="ibm-campo w-full"
          />
        </div>
        {@render campoSelect(
          'filtro-unidade',
          'Unidade',
          filtros.unidadeId,
          'unidadeId',
          unidades.map((u) => ({ valor: String(u.id), rotulo: u.nome })),
          { todos: 'Todas as unidades' }
        )}
        {@render campoSelect(
          'filtro-especialidade',
          'Especialidade',
          filtros.especialidadeId,
          'especialidadeId',
          opcoes.especialidades.map((e) => ({ valor: String(e.id), rotulo: e.nome })),
          { todos: 'Todas as especialidades' }
        )}
        {@render campoSelect(
          'filtro-status',
          'Status',
          filtros.status,
          'status',
          [
            { valor: 'ativa', rotulo: 'Ativa' },
            { valor: 'inativa', rotulo: 'Inativa' }
          ],
          { todos: 'Todos os status' }
        )}
        {@render campoSelect(
          'filtro-saldo',
          'Situação do saldo',
          filtros.saldo,
          'saldo',
          [
            { valor: 'com-saldo', rotulo: 'Com saldo' },
            { valor: 'esgotada', rotulo: 'Cota esgotada' }
          ],
          { todos: 'Qualquer saldo' }
        )}
        {@render campoSelect(
          'filtro-origem',
          'Origem',
          filtros.origem,
          'origem',
          [
            { valor: 'manual', rotulo: 'Manual' },
            { valor: 'agenda', rotulo: 'Agenda' }
          ],
          { todos: 'Todas as origens' }
        )}
        {@render campoSelect(
          'filtro-tipo',
          'Tipo',
          filtros.tipo,
          'tipo',
          [
            { valor: 'mensal', rotulo: 'Mensal' },
            { valor: 'data', rotulo: 'Por data' }
          ],
          { todos: 'Todos os tipos' }
        )}
        {@render campoSelect(
          'filtro-profissional',
          'Profissional',
          filtros.profissionalId,
          'profissionalId',
          opcoes.profissionais.map((p) => ({ valor: String(p.id), rotulo: p.nome })),
          { todos: 'Todos os profissionais' }
        )}
        {@render campoSelect(
          'filtro-executante',
          'Executante',
          filtros.executanteId,
          'executanteId',
          opcoes.executantes.map((e) => ({ valor: String(e.id), rotulo: e.nome })),
          { todos: 'Todos os executantes', desabilitado: !agendasCarregadas }
        )}
        <div class="min-w-0">
          <label for="filtro-competencia" class="ibm-rotulo">Competência</label>
          <input
            id="filtro-competencia"
            type="month"
            value={filtros.competencia}
            onchange={(e) => definirFiltro('competencia', e.currentTarget.value)}
            class="ibm-campo w-full"
          />
        </div>
        <div class="min-w-0">
          <label for="filtro-data-de" class="ibm-rotulo">Data a partir de</label>
          <input
            id="filtro-data-de"
            type="date"
            value={filtros.dataDe}
            aria-invalid={intervaloInvalido}
            aria-describedby={intervaloInvalido ? 'filtro-intervalo-erro' : undefined}
            onchange={(e) => definirFiltro('dataDe', e.currentTarget.value)}
            class="ibm-campo w-full"
          />
        </div>
        <div class="min-w-0">
          <label for="filtro-data-ate" class="ibm-rotulo">Data até</label>
          <input
            id="filtro-data-ate"
            type="date"
            value={filtros.dataAte}
            aria-invalid={intervaloInvalido}
            aria-describedby={intervaloInvalido ? 'filtro-intervalo-erro' : undefined}
            onchange={(e) => definirFiltro('dataAte', e.currentTarget.value)}
            class="ibm-campo w-full"
          />
        </div>
      </div>

      {#if intervaloInvalido}
        <p id="filtro-intervalo-erro" class="ibm-erro-texto">
          A data inicial é maior que a data final. O intervalo de datas foi ignorado.
        </p>
      {/if}

      {#if !unidadesCarregadas || !gruposCarregados || !agendasCarregadas}
        <p class="ibm-aviso" role="status">
          {#if !unidadesCarregadas}
            Não foi possível carregar as unidades: as cotas de grupo de unidades permanecem visíveis ao filtrar por unidade.
          {/if}
          {#if !gruposCarregados}
            Não foi possível carregar os grupos: as cotas de grupo de especialidades permanecem visíveis ao filtrar por
            especialidade.
          {/if}
          {#if !agendasCarregadas}
            Não foi possível carregar as agendas: as cotas de agenda permanecem visíveis ao filtrar por profissional, e o
            filtro de executante fica indisponível.
          {/if}
        </p>
      {/if}

      <div class="flex flex-wrap items-center gap-3 min-h-8">
        <p class="ibm-contador" aria-live="polite">
          {#if loading}
            Carregando…
          {:else}
            <span class="font-semibold">{cotasFiltradas.length}</span> de {cotas.length} cota{cotas.length === 1
              ? ''
              : 's'}
          {/if}
        </p>
        {#each tags as tag (tag.chave)}
          <button
            type="button"
            onclick={() => definirFiltro(tag.chave, '')}
            aria-label="Remover filtro: {tag.rotulo}"
            class="ibm-tag"
          >
            <span>{tag.rotulo}</span>
            <span aria-hidden="true">×</span>
          </button>
        {/each}
        {#if filtrosAtivos}
          <button type="button" onclick={limparFiltros} class="ibm-btn ibm-btn--fantasma">Limpar filtros</button>
        {/if}
      </div>
    </section>

    {#if loading}
      <p class="ibm-suave-texto">Carregando...</p>
    {:else}
      <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
      <div class="ibm-tabela-contorno" role="region" aria-label="Lista de cotas, com rolagem horizontal" tabindex="0">
        <table class="ibm-tabela w-full">
          <thead>
            <tr>
              <th class="text-left">Titular</th>
              <th class="text-left">Especialidade</th>
              <th class="text-left">Tipo</th>
              <th class="text-left">Período / Data</th>
              <th class="text-right">Total</th>
              <th class="text-right">Utilizado</th>
              <th class="text-right">Saldo</th>
              <th class="text-left">Status</th>
              <th class="text-left">Origem</th>
              <th class="text-left">Ações</th>
            </tr>
          </thead>
          <tbody>
            {#each linhasExibidas as linha (linha.tipo === 'agenda' ? `agenda-${linha.agendaId}` : `cota-${linha.cota.id}`)}
              {#if linha.tipo === 'manual'}
                {@render linhaCota(linha.cota)}
              {:else}
                {@const agendaInfo = agendasPorId[linha.agendaId]}
                {@const resumo = resumoGrupo(linha.cotas)}
                <tr class="ibm-linha-agenda">
                  <td class="ibm-titular" colspan="2">
                    <button
                      type="button"
                      onclick={() => alternarAgenda(linha.agendaId)}
                      class="ibm-agenda-toggle"
                      aria-expanded={!!agendasAbertas[linha.agendaId]}
                    >
                      <svg
                        class="ibm-chevron {agendasAbertas[linha.agendaId] ? 'ibm-chevron--aberto' : ''}"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke="currentColor"
                        stroke-width="2.5"
                        aria-hidden="true"
                      >
                        <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
                      </svg>
                      <span class="flex flex-col">
                        {#if agendaInfo}
                          <span>{agendaInfo.estabelecimentoExecutanteNome} — {agendaInfo.profissionalNome}</span>
                        {:else}
                          <span>Agenda #{linha.agendaId}</span>
                        {/if}
                        <span class="ibm-detalhe">
                          {#if filtrosAtivos}
                            {linha.cotas.length} de {totalPorAgenda.get(linha.agendaId) ?? linha.cotas.length} cotas
                          {:else}
                            {linha.cotas.length} cota{linha.cotas.length === 1 ? '' : 's'}
                          {/if}
                          · {resumo.unidadesDistintas} unidade{resumo.unidadesDistintas === 1 ? '' : 's'}
                        </span>
                      </span>
                    </button>
                  </td>
                  <td>
                    <span class="ibm-badge ibm-badge--neutro">Por Data</span>
                  </td>
                  <td>{resumo.periodo}</td>
                  <td class="text-right ibm-num">{resumo.quantidadeTotal}</td>
                  <td class="text-right ibm-num">{resumo.quantidadeUtilizada}</td>
                  <td
                    class="text-right ibm-num ibm-saldo {resumo.saldoDisponivel <= 0
                      ? 'ibm-saldo--zero'
                      : 'ibm-saldo--ok'}"
                  >
                    {resumo.saldoDisponivel}
                  </td>
                  <td>
                    <span class="ibm-badge {agendaInfo?.ativo ?? true ? 'ibm-badge--ok' : 'ibm-badge--erro'}">
                      {agendaInfo?.ativo ?? true ? 'Ativa' : 'Inativa'}
                    </span>
                  </td>
                  <td>
                    <span class="ibm-badge ibm-badge--acento">Agenda</span>
                  </td>
                  <td>
                    <button
                      onclick={() => goto(`/liberacao-agenda/${linha.agendaId}`)}
                      class="ibm-btn ibm-btn--terciario"
                    >
                      Ver agenda
                    </button>
                  </td>
                </tr>
                {#if agendasAbertas[linha.agendaId]}
                  {#each linha.cotas as c (c.id)}
                    {@render linhaCota(c)}
                  {/each}
                {/if}
              {/if}
              {#if linha.tipo === 'manual' && linha.cota.grupoEspecialidadesId && gruposAbertos[linha.cota.id]}
                <tr class="ibm-linha-painel">
                  <td colspan="10" class="ibm-celula-painel">
                    <GrupoEspecialidadesPainel
                      variante="ibm"
                      grupo={grupos.find((g) => g.id === linha.cota.grupoEspecialidadesId)}
                    />
                  </td>
                </tr>
              {/if}
            {:else}
              <tr class="ibm-linha-vazia">
                <td colspan="10" class="ibm-vazio">
                  {#if cotas.length === 0}
                    Nenhuma cota cadastrada.
                  {:else}
                    Nenhuma cota corresponde aos filtros.
                    <button type="button" onclick={limparFiltros} class="ibm-btn ibm-btn--fantasma ml-1">
                      Limpar filtros
                    </button>
                  {/if}
                </td>
              </tr>
            {/each}
          </tbody>
        </table>
      </div>
    {/if}
  </div>
</Content>

<style>
  /*
   * Visual inspirado no Carbon (IBM), escopado a esta tela. Nenhum token vaza
   * para o resto do sistema. O acento segue o emerald do SIRG (--ibm-acento) e
   * pode ser trocado num único ponto. A fonte é uma pilha com fallback: nada é
   * baixado nem instalado.
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
    --ibm-fundo: #f4f4f4;
    --ibm-camada: #ffffff;
    --ibm-campo: #f4f4f4;
    --ibm-linha: #e0e0e0;
    --ibm-borda-campo: #8c8c8c;
    --ibm-cabecalho: #e0e0e0;
    --ibm-hover: #ececec;
    --ibm-erro: #da1e28;
    --ibm-erro-texto: #a2191f;
    --ibm-ok-fundo: #a7f0ba;
    --ibm-ok-texto: #0e6027;
    --ibm-erro-fundo: #ffd7d9;
    --ibm-aviso-fundo: #fcf4d6;
    --ibm-aviso-borda: #f1c21b;

    background: var(--ibm-fundo);
    color: var(--ibm-tinta);
    font-family: var(--ibm-fonte);
    font-size: 14px;
    line-height: 1.29;
    letter-spacing: 0.16px;
  }

  /* Títulos e rótulos */
  .ibm-titulo-secao {
    font-size: 20px;
    font-weight: 400;
    line-height: 1.4;
    color: var(--ibm-tinta);
  }
  .ibm-rotulo {
    display: block;
    margin-bottom: 4px;
    font-size: 12px;
    line-height: 1.33;
    letter-spacing: 0.32px;
    color: var(--ibm-tinta-suave);
  }

  /* Painel (camada branca, contorno de 1 px, raio zero) */
  .ibm-painel {
    background: var(--ibm-camada);
    border: 1px solid var(--ibm-linha);
    border-radius: 0;
  }

  /* Campos: fundo cinza, borda inferior de 3:1, foco em contorno de 2 px */
  .ibm-campo {
    min-height: 40px;
    padding: 0 16px;
    border: 1px solid transparent;
    border-bottom: 1px solid var(--ibm-borda-campo);
    border-radius: 0;
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
  .ibm-campo[aria-invalid='true'] {
    outline: 2px solid var(--ibm-erro);
    outline-offset: -2px;
  }
  .ibm-campo:disabled {
    background-color: var(--ibm-linha);
    color: var(--ibm-tinta-suave);
    cursor: not-allowed;
  }

  /* Botões: raio zero, texto de ação */
  .ibm-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 40px;
    padding: 0 16px;
    border: 1px solid transparent;
    border-radius: 0;
    font-family: inherit;
    font-size: 14px;
    font-weight: 400;
    letter-spacing: 0.16px;
    white-space: nowrap;
    cursor: pointer;
  }
  .ibm-btn:focus-visible,
  .ibm-tag:focus-visible,
  .ibm-agenda-toggle:focus-visible {
    outline: 2px solid var(--ibm-acento);
    outline-offset: 2px;
  }
  .ibm-btn--primario {
    background: var(--ibm-acento);
    color: #ffffff;
  }
  .ibm-btn--primario:hover {
    background: var(--ibm-acento-hover);
  }
  .ibm-btn--primario:active {
    background: var(--ibm-acento-ativo);
  }
  .ibm-btn--terciario {
    min-height: 32px;
    padding: 0 12px;
    border-color: var(--ibm-acento);
    background: transparent;
    color: var(--ibm-acento);
  }
  .ibm-btn--terciario:hover {
    background: var(--ibm-acento-hover);
    border-color: var(--ibm-acento-hover);
    color: #ffffff;
  }
  .ibm-btn--terciario:active {
    background: var(--ibm-acento-ativo);
    border-color: var(--ibm-acento-ativo);
    color: #ffffff;
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

  /* Contador, tags de filtro e mensagens */
  .ibm-contador {
    font-size: 14px;
    color: var(--ibm-tinta);
  }
  .ibm-tag {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    min-height: 28px;
    padding: 0 12px;
    border: 0;
    border-radius: 9999px;
    background: var(--ibm-acento-suave);
    color: var(--ibm-acento-suave-texto);
    font-family: inherit;
    font-size: 12px;
    letter-spacing: 0.32px;
    cursor: pointer;
  }
  .ibm-tag:hover {
    background: #a7f3d0;
  }
  .ibm-erro-texto {
    color: var(--ibm-erro-texto);
    font-size: 14px;
  }
  .ibm-aviso {
    padding: 12px 16px;
    border-left: 3px solid var(--ibm-aviso-borda);
    background: var(--ibm-aviso-fundo);
    color: var(--ibm-tinta);
    font-size: 14px;
  }
  .ibm-suave-texto {
    color: var(--ibm-tinta-suave);
    font-size: 14px;
  }
  .ibm-suave {
    color: var(--ibm-tinta-suave);
  }
  .ibm-detalhe {
    font-size: 12px;
    line-height: 1.33;
    letter-spacing: 0.32px;
    color: var(--ibm-tinta-suave);
  }

  /* Tabela (extrapolação do Carbon: cabeçalho cinza, linhas de 1 px, hover cinza) */
  .ibm-tabela-contorno {
    overflow-x: auto;
    background: var(--ibm-camada);
    border: 1px solid var(--ibm-linha);
    border-radius: 0;
  }
  .ibm-tabela-contorno:focus-visible {
    outline: 2px solid var(--ibm-acento);
    outline-offset: -2px;
  }
  .ibm-tabela {
    min-width: 960px;
    font-size: 14px;
  }
  .ibm-tabela th {
    padding: 12px 16px;
    background: var(--ibm-cabecalho);
    color: var(--ibm-tinta);
    font-size: 14px;
    font-weight: 600;
    white-space: nowrap;
  }
  .ibm-tabela td {
    padding: 8px 16px;
    border-bottom: 1px solid var(--ibm-linha);
    vertical-align: middle;
    color: var(--ibm-tinta);
  }
  .ibm-tabela tbody tr:last-child td {
    border-bottom: 0;
  }
  .ibm-tabela .ibm-linha-agenda > td {
    background: var(--ibm-fundo);
  }
  .ibm-tabela .ibm-linha-agenda > td:first-child {
    box-shadow: inset 3px 0 0 var(--ibm-acento);
  }
  .ibm-tabela tbody tr:hover > td {
    background: var(--ibm-hover);
  }
  .ibm-tabela .ibm-linha-painel:hover > td,
  .ibm-tabela .ibm-linha-vazia:hover > td {
    background: var(--ibm-camada);
  }
  .ibm-tabela td.ibm-celula-painel {
    padding: 0 16px 16px 16px;
  }
  .ibm-tabela td.ibm-vazio {
    padding: 48px 16px;
    text-align: center;
    color: var(--ibm-tinta-suave);
  }
  .ibm-titular {
    font-weight: 600;
  }
  .ibm-num {
    font-variant-numeric: tabular-nums;
  }
  .ibm-saldo {
    font-weight: 600;
  }
  .ibm-saldo--ok {
    color: var(--ibm-acento);
  }
  .ibm-saldo--zero {
    color: var(--ibm-erro-texto);
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
  .ibm-badge--contorno {
    background: transparent;
    border-color: var(--ibm-borda-campo);
    color: #393939;
  }
  .ibm-badge--acento {
    background: var(--ibm-acento-suave);
    color: var(--ibm-acento-suave-texto);
  }
  .ibm-badge--ok {
    background: var(--ibm-ok-fundo);
    color: var(--ibm-ok-texto);
  }
  .ibm-badge--erro {
    background: var(--ibm-erro-fundo);
    color: var(--ibm-erro-texto);
  }

  /* Linha-resumo da agenda */
  .ibm-agenda-toggle {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    min-height: 32px;
    padding: 2px 4px;
    text-align: left;
    font: inherit;
    font-weight: 600;
    color: var(--ibm-tinta);
    cursor: pointer;
  }
  .ibm-agenda-toggle .ibm-detalhe {
    font-weight: 400;
  }
  .ibm-chevron {
    width: 14px;
    height: 14px;
    flex: none;
    color: var(--ibm-acento);
  }
  .ibm-chevron--aberto {
    transform: rotate(180deg);
  }
  @media (prefers-reduced-motion: no-preference) {
    .ibm-chevron {
      transition: transform 150ms ease-out;
    }
  }
</style>
