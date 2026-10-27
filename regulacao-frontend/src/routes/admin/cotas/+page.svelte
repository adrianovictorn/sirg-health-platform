<script>
  import { onMount, onDestroy } from 'svelte';
  import { goto, afterNavigate } from '$app/navigation';
  import { page } from '$app/state';
  import { getApi } from '$lib/api.js';
  import Content from '$lib/Content.svelte';
  import { toast } from 'svelte-sonner';
  import { formatarPeriodoCota, periodoCobertoPorGrupo } from '$lib/cotas.js';
  import {
    FILTROS_VAZIOS,
    lerFiltros,
    serializarFiltros,
    sanitizarFiltro,
    temParametrosDeFiltro,
    haFiltros as temFiltros,
    periodoInvalido,
    tagsAtivas
  } from '$lib/cotasFiltro.js';
  import {
    lerPaginacao,
    montarQuery,
    montarQueryDaApi,
    temParametrosDePaginacao,
    textoDoContador
  } from '$lib/listaPaginada.js';
  import Paginacao from '$lib/components/ListaPaginada/Paginacao.svelte';
  import GrupoToggleButton from '$lib/GrupoToggleButton.svelte';
  import GrupoEspecialidadesPainel from '$lib/GrupoEspecialidadesPainel.svelte';

  // Dados só de leitura, sempre trocados por inteiro: $state.raw evita o proxy
  // profundo do Svelte.
  let resposta = $state.raw(null);
  let unidades = $state.raw([]);
  let grupos = $state.raw([]);
  let agendasPorId = $state.raw({});
  let opcoes = $state.raw({ especialidades: [], profissionais: [], executantes: [] });
  let loading = $state(true);
  let erroAoCarregar = $state(false);

  // Filtro, ordenação e página são decididos no servidor. Falha ao carregar um
  // dado de apoio (unidades, grupos, agendas) só afeta nomes e opções da tela:
  // nenhuma cota some por causa disso.
  let unidadesCarregadas = $state(true);
  let agendasCarregadas = $state(true);

  // Filtros e página espelhados na URL: dá para recarregar, voltar e compartilhar o link.
  let filtros = $state(lerFiltros(page.url.searchParams));
  let paginacao = $state(lerPaginacao(page.url.searchParams));
  // Query efetivamente enviada à API; o $effect abaixo recarrega quando ela muda.
  // A busca textual só entra aqui depois do debounce (ver `definirBusca`).
  let consulta = $state(consultaAtual());

  // Última query guardada na sessão do navegador, para o filtro sobreviver ao
  // voltar de "Nova cota"/"Editar" (essas telas voltam com goto('/admin/cotas')).
  const CHAVE_DA_SESSAO = 'admin-cotas:filtros';
  let temporizadorDaUrl = null;
  let requisicaoAtual = 0;

  function paramsDeFiltroParaApi() {
    const params = serializarFiltros(filtros);
    // Intervalo invertido é ignorado pela tela (o aviso já aparece); a API o recusaria.
    if (periodoInvalido(filtros)) {
      params.delete('dataDe');
      params.delete('dataAte');
    }
    return params;
  }

  function consultaAtual() {
    return montarQueryDaApi(paramsDeFiltroParaApi(), paginacao);
  }

  function guardarNaSessao(texto) {
    try {
      if (texto) sessionStorage.setItem(CHAVE_DA_SESSAO, texto);
      else sessionStorage.removeItem(CHAVE_DA_SESSAO);
    } catch {
      /* sessionStorage indisponível (modo privado etc.): só perde a restauração */
    }
  }

  // Confirma filtros e página: guarda na sessão, escreve na URL e dispara a carga.
  function aplicar() {
    clearTimeout(temporizadorDaUrl);
    const naUrl = montarQuery(serializarFiltros(filtros), paginacao);
    guardarNaSessao(naUrl);
    goto(naUrl ? `?${naUrl}` : page.url.pathname, { replaceState: true, keepFocus: true, noScroll: true });
    consulta = consultaAtual();
  }

  // Mudar qualquer filtro volta para a primeira página.
  function definirFiltro(chave, valor) {
    filtros = { ...filtros, [chave]: sanitizarFiltro(chave, valor) };
    paginacao = { ...paginacao, pagina: 0 };
    aplicar();
  }

  // A busca espera 300 ms de pausa na digitação antes de consultar o servidor.
  function definirBusca(valor) {
    filtros = { ...filtros, q: valor };
    paginacao = { ...paginacao, pagina: 0 };
    clearTimeout(temporizadorDaUrl);
    temporizadorDaUrl = setTimeout(aplicar, 300);
  }

  function limparFiltros() {
    filtros = { ...FILTROS_VAZIOS };
    paginacao = { ...paginacao, pagina: 0 };
    aplicar();
  }

  function irParaPagina(pagina) {
    paginacao = { ...paginacao, pagina };
    aplicar();
  }

  function definirTamanho(tamanho) {
    paginacao = { pagina: 0, tamanho };
    aplicar();
  }

  afterNavigate((navegacao) => {
    const params = page.url.searchParams;
    const veioDeFormulario = navegacao.from?.url.pathname.startsWith('/admin/cotas/');
    if (veioDeFormulario && !temParametrosDeFiltro(params) && !temParametrosDePaginacao(params)) {
      let salvo = '';
      try {
        salvo = sessionStorage.getItem(CHAVE_DA_SESSAO) ?? '';
      } catch {
        /* sem sessionStorage: abre sem filtro */
      }
      if (salvo) {
        const guardado = new URLSearchParams(salvo);
        filtros = lerFiltros(guardado);
        paginacao = lerPaginacao(guardado);
        aplicar();
        return;
      }
    }
    // Clicar no item do menu para a mesma rota limpa os filtros e a página, como em custos.
    if (navegacao.type === 'link') {
      filtros = lerFiltros(params);
      paginacao = lerPaginacao(params);
      guardarNaSessao(montarQuery(serializarFiltros(filtros), paginacao));
      consulta = consultaAtual();
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
  function alternarAgenda(chave) {
    agendasAbertas = { ...agendasAbertas, [chave]: !agendasAbertas[chave] };
  }

  // Quais unidades (blocos) estão recolhidas. Só em memória: começa tudo aberto.
  let blocosFechados = $state({});
  function alternarBloco(chave) {
    blocosFechados = { ...blocosFechados, [chave]: !blocosFechados[chave] };
  }
  function recolherTodas() {
    blocosFechados = Object.fromEntries(blocos.map((b) => [b.chave, true]));
  }
  function expandirTodas() {
    blocosFechados = {};
  }

  onMount(() => {
    carregarUnidades();
    carregarGrupos();
    carregarAgendas();
    carregarOpcoes();
  });

  // Recarrega a página sempre que a consulta muda. Uma resposta atrasada de uma
  // consulta antiga é descartada (contador), senão sobrescreveria a atual.
  $effect(() => {
    carregarCotas(consulta);
  });

  async function carregarCotas(query) {
    const minha = ++requisicaoAtual;
    const obsoleta = () => minha !== requisicaoAtual;
    loading = true;
    erroAoCarregar = false;
    try {
      const res = await getApi(`cotas/pagina?${query}`);
      if (!res.ok) {
        const corpo = await res.json().catch(() => ({}));
        throw new Error(corpo.message ?? 'Erro ao carregar cotas.');
      }
      const dados = await res.json();
      if (obsoleta()) return;
      resposta = dados;
      // Página que deixou de existir (ex.: cotas removidas): volta para a última.
      const ultima = Math.max(dados.pagina.totalPaginas - 1, 0);
      if (dados.pagina.conteudo.length === 0 && paginacao.pagina > ultima) {
        paginacao = { ...paginacao, pagina: ultima };
        aplicar();
      }
    } catch (e) {
      if (obsoleta()) return;
      erroAoCarregar = true;
      toast.error(e.message || 'Erro ao carregar cotas.');
    } finally {
      if (!obsoleta()) loading = false;
    }
  }

  async function carregarOpcoes() {
    try {
      const res = await getApi('cotas/opcoes-filtro');
      if (res.ok) opcoes = await res.json();
    } catch {
      /* selects ficam só com "Todos"; o filtro por texto continua funcionando */
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
    } catch {
      /* ignora - o painel de especialidades do grupo fica sem a lista */
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

  // A regra de cada filtro e a ordenação moram no servidor (CotaUnidadeSpecification
  // e CotaListagemService); `$lib/cotasFiltro.js` cuida só da URL, das tags e da validação.
  const tags = $derived(tagsAtivas(filtros, { unidades, ...opcoes }));
  const filtrosAtivos = $derived(temFiltros(filtros));
  const intervaloInvalido = $derived(periodoInvalido(filtros));

  // O servidor entrega as cotas separadas por titular (unidade ou grupo de unidades) e pagina por
  // titular, então cada bloco chega completo. Dentro do bloco, uma cota de agenda recorrente
  // materializa uma linha por ocorrência — sem agrupar, uma agenda semanal de um mês vira
  // dezenas de linhas soltas — e vira UMA linha-resumo que soma só as cotas desta unidade;
  // cota manual é uma linha com uma única cota.
  const blocos = $derived(
    (resposta?.pagina.conteudo ?? []).map((bloco) => ({
      ...bloco,
      linhas: bloco.itens.map((item) =>
        item.agendaId == null
          ? { tipo: 'manual', cota: item.cotas[0] }
          : {
              tipo: 'agenda',
              agendaId: item.agendaId,
              ativo: item.ativo,
              totalCotasDaAgenda: item.totalCotasDaAgenda,
              cotas: item.cotas
            }
      )
    }))
  );

  const totalUnidades = $derived(resposta?.pagina.totalElementos ?? 0);
  const totalPaginas = $derived(resposta?.pagina.totalPaginas ?? 0);
  const textoContador = $derived(
    resposta
      ? textoDoContador({
          pagina: paginacao.pagina,
          tamanho: paginacao.tamanho,
          totalUnidades,
          totalCotas: resposta.totalCotas,
          totalUnidadesGeral: resposta.totalUnidadesGeral,
          filtrado: filtrosAtivos
        })
      : ''
  );

  function resumoGrupo(cotasDoGrupo) {
    return {
      quantidadeTotal: cotasDoGrupo.reduce((soma, c) => soma + c.quantidadeTotal, 0),
      quantidadeUtilizada: cotasDoGrupo.reduce((soma, c) => soma + c.quantidadeUtilizada, 0),
      saldoDisponivel: cotasDoGrupo.reduce((soma, c) => soma + c.saldoDisponivel, 0),
      periodo: periodoCobertoPorGrupo(cotasDoGrupo)
    };
  }
</script>

{#snippet linhaCota(c)}
  <tr>
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
    <div class="flex flex-wrap items-center justify-between gap-4">
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
          { todos: 'Todos os executantes' }
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

      {#if !unidadesCarregadas || !agendasCarregadas}
        <p class="ibm-aviso" role="status">
          {#if !unidadesCarregadas}
            Não foi possível carregar as unidades: o filtro de unidade fica sem opções, mas as cotas continuam listadas.
          {/if}
          {#if !agendasCarregadas}
            Não foi possível carregar as agendas: as linhas de agenda aparecem como "Agenda #número".
          {/if}
        </p>
      {/if}

      <div class="ibm-resumo">
        <p class="ibm-contador" aria-live="polite">
          {#if loading && !resposta}
            Carregando…
          {:else}
            {#each textoContador.split(' · ') as parte, i (i)}
              {#if i === 0}
                <strong class="ibm-contador-destaque">{parte}</strong>
              {:else}
                <span class="ibm-contador-suave">&nbsp;· {parte}</span>
              {/if}
            {/each}
          {/if}
        </p>
        {#if tags.length > 0 || filtrosAtivos}
          <div class="ibm-filtros-ativos">
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
        {/if}
      </div>
    </section>

    {#if !resposta}
      <p class="ibm-suave-texto">{erroAoCarregar ? 'Não foi possível carregar as cotas.' : 'Carregando...'}</p>
    {:else}
      <div>
      {#if blocos.length > 0}
        <div class="ibm-barra-blocos">
          <button type="button" onclick={recolherTodas} class="ibm-btn ibm-btn--terciario">Recolher todas</button>
          <button type="button" onclick={expandirTodas} class="ibm-btn ibm-btn--terciario">Expandir todas</button>
        </div>
      {/if}
      <!-- A tabela anterior fica na tela (esmaecida) enquanto a nova página chega. -->
      <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
      <div
        class="ibm-tabela-contorno"
        class:ibm-carregando={loading}
        aria-busy={loading}
        role="region"
        aria-label="Lista de cotas, com rolagem horizontal"
        tabindex="0"
      >
        <table class="ibm-tabela w-full">
          <thead>
            <tr>
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
          {#each blocos as bloco (bloco.chave)}
            {@const fechado = !!blocosFechados[bloco.chave]}
            <tbody class="ibm-bloco">
              <tr class="ibm-linha-unidade">
                <td colspan="9">
                  <div class="ibm-unidade-cabecalho">
                    <button
                      type="button"
                      onclick={() => alternarBloco(bloco.chave)}
                      class="ibm-agenda-toggle"
                      aria-expanded={!fechado}
                    >
                      <svg
                        class="ibm-chevron {fechado ? '' : 'ibm-chevron--aberto'}"
                        fill="none"
                        viewBox="0 0 24 24"
                        stroke="currentColor"
                        stroke-width="2.5"
                        aria-hidden="true"
                      >
                        <path stroke-linecap="round" stroke-linejoin="round" d="M19 9l-7 7-7-7" />
                      </svg>
                      <span class="flex flex-col">
                        <span class="ibm-unidade-nome">
                          {#if bloco.tipo === 'GRUPO'}<span class="ibm-badge ibm-badge--neutro">Grupo</span>{/if}
                          {bloco.nome}
                        </span>
                        <span class="ibm-detalhe">
                          {#if filtrosAtivos}
                            {bloco.totalCotas} de {bloco.totalCotasSemFiltro} cotas
                          {:else}
                            {bloco.totalCotas} cota{bloco.totalCotas === 1 ? '' : 's'}
                          {/if}
                        </span>
                      </span>
                    </button>
                    <dl class="ibm-unidade-totais">
                      <div><dt>Total</dt><dd class="ibm-num">{bloco.quantidadeTotal}</dd></div>
                      <div><dt>Utilizado</dt><dd class="ibm-num">{bloco.quantidadeUtilizada}</dd></div>
                      <div>
                        <dt>Saldo das cotas listadas</dt>
                        <dd class="ibm-num ibm-saldo {bloco.saldo <= 0 ? 'ibm-saldo--zero' : 'ibm-saldo--ok'}">
                          {bloco.saldo}
                        </dd>
                      </div>
                    </dl>
                    {#if bloco.tipo === 'UNIDADE'}
                      {#if filtros.unidadeId === String(bloco.id)}
                        <button
                          type="button"
                          onclick={() => definirFiltro('unidadeId', '')}
                          class="ibm-btn ibm-btn--terciario"
                        >
                          Ver todas as unidades
                        </button>
                      {:else}
                        <button
                          type="button"
                          onclick={() => definirFiltro('unidadeId', String(bloco.id))}
                          aria-label="Ver só a unidade {bloco.nome}"
                          class="ibm-btn ibm-btn--terciario"
                        >
                          Ver só esta unidade
                        </button>
                      {/if}
                    {/if}
                  </div>
                </td>
              </tr>
              {#if !fechado}
                {#each bloco.linhas as linha (linha.tipo === 'agenda' ? `agenda-${linha.agendaId}` : `cota-${linha.cota.id}`)}
                  {#if linha.tipo === 'manual'}
                    {@render linhaCota(linha.cota)}
                  {:else}
                    {@const chaveAgenda = `${bloco.chave}|${linha.agendaId}`}
                    {@const agendaInfo = agendasPorId[linha.agendaId]}
                    {@const resumo = resumoGrupo(linha.cotas)}
                    <tr class="ibm-linha-agenda">
                      <td class="ibm-titular">
                        <button
                          type="button"
                          onclick={() => alternarAgenda(chaveAgenda)}
                          class="ibm-agenda-toggle"
                          aria-expanded={!!agendasAbertas[chaveAgenda]}
                        >
                          <svg
                            class="ibm-chevron {agendasAbertas[chaveAgenda] ? 'ibm-chevron--aberto' : ''}"
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
                                {linha.cotas.length} de {linha.totalCotasDaAgenda} cotas
                              {:else}
                                {linha.cotas.length} cota{linha.cotas.length === 1 ? '' : 's'}
                              {/if}
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
                        <span class="ibm-badge {linha.ativo ? 'ibm-badge--ok' : 'ibm-badge--erro'}">
                          {linha.ativo ? 'Ativa' : 'Inativa'}
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
                    {#if agendasAbertas[chaveAgenda]}
                      {#each linha.cotas as c (c.id)}
                        {@render linhaCota(c)}
                      {/each}
                    {/if}
                  {/if}
                  {#if linha.tipo === 'manual' && linha.cota.grupoEspecialidadesId && gruposAbertos[linha.cota.id]}
                    <tr class="ibm-linha-painel">
                      <td colspan="9" class="ibm-celula-painel">
                        <GrupoEspecialidadesPainel
                          variante="ibm"
                          grupo={grupos.find((g) => g.id === linha.cota.grupoEspecialidadesId)}
                        />
                      </td>
                    </tr>
                  {/if}
                {/each}
              {/if}
            </tbody>
          {:else}
            <tbody>
              <tr class="ibm-linha-vazia">
                <td colspan="9" class="ibm-vazio">
                  {#if erroAoCarregar}
                    Não foi possível carregar as cotas.
                  {:else if (resposta?.totalCotasGeral ?? 0) === 0}
                    Nenhuma cota cadastrada.
                  {:else}
                    Nenhuma cota corresponde aos filtros.
                    <button type="button" onclick={limparFiltros} class="ibm-btn ibm-btn--fantasma ml-1">
                      Limpar filtros
                    </button>
                  {/if}
                </td>
              </tr>
            </tbody>
          {/each}
        </table>
      </div>
      <Paginacao
        pagina={paginacao.pagina}
        {totalPaginas}
        tamanho={paginacao.tamanho}
        rotulo="Unidades por página"
        desabilitado={loading}
        onPagina={irParaPagina}
        onTamanho={definirTamanho}
      />
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
  .ibm-resumo {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding-top: 12px;
    border-top: 1px solid var(--ibm-linha);
  }
  .ibm-contador {
    min-height: 20px;
    font-size: 14px;
    color: var(--ibm-tinta);
  }
  .ibm-contador-destaque {
    font-weight: 600;
  }
  .ibm-contador-suave {
    margin-left: 0.25em;
    color: var(--ibm-tinta-suave);
  }
  .ibm-filtros-ativos {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
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
  .ibm-carregando {
    opacity: 0.7;
    transition: opacity 120ms;
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
  .ibm-tabela tbody tr:last-child:not(.ibm-linha-unidade) td {
    border-bottom: 0;
  }
  .ibm-tabela .ibm-linha-agenda > td {
    background: var(--ibm-fundo);
  }
  /* Cotas dentro do bloco ficam recuadas em relação ao cabeçalho da unidade. */
  .ibm-tabela .ibm-bloco tr:not(.ibm-linha-unidade):not(.ibm-linha-painel) > td:first-child {
    padding-left: 28px;
  }
  /* Cabeçalho de cada unidade (bloco): mais forte que a linha-resumo de agenda. */
  .ibm-tabela tbody.ibm-bloco > .ibm-linha-unidade > td {
    padding: 12px 16px;
    background: var(--ibm-cabecalho);
    border-bottom: 1px solid var(--ibm-borda-campo);
    box-shadow: inset 6px 0 0 var(--ibm-acento);
  }
  .ibm-tabela .ibm-linha-unidade:hover > td {
    background: var(--ibm-cabecalho);
  }
  /* Faixa de respiro entre blocos de unidades diferentes. */
  .ibm-bloco + .ibm-bloco .ibm-linha-unidade > td {
    border-top: 16px solid var(--ibm-fundo);
  }
  /*
   * Com rolagem horizontal, o cabeçalho acompanha a borda visível da região
   * (sticky + largura do contêiner), para o nome da unidade nunca sair da tela.
   * O contraste do acento sobre o cinza do cabeçalho exige o tom mais escuro.
   */
  .ibm-tabela-contorno {
    container-type: inline-size;
  }
  .ibm-unidade-cabecalho {
    --ibm-acento: var(--ibm-acento-hover);
    position: sticky;
    left: 0;
    width: calc(100cqw - 34px);
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 8px 24px;
  }
  .ibm-unidade-nome {
    display: inline-flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 4px 8px;
    overflow-wrap: anywhere;
    font-size: 16px;
    font-weight: 600;
  }
  .ibm-unidade-totais {
    display: flex;
    flex-wrap: wrap;
    gap: 4px 24px;
    margin: 0 0 0 auto;
  }
  .ibm-unidade-totais > div {
    display: flex;
    align-items: baseline;
    gap: 6px;
  }
  .ibm-unidade-totais dt {
    font-size: 12px;
    letter-spacing: 0.32px;
    color: var(--ibm-tinta-suave);
  }
  .ibm-unidade-totais dd {
    margin: 0;
    font-weight: 600;
  }
  .ibm-barra-blocos {
    display: flex;
    flex-wrap: wrap;
    justify-content: flex-end;
    gap: 8px;
    margin-bottom: 8px;
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
