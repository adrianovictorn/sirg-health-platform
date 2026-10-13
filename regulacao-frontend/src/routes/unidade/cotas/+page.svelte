<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { page } from '$app/state';
  import { goto, afterNavigate } from '$app/navigation';
  import { user } from '$lib/stores/auth.js';
  import { getApi } from '$lib/api.js';
  import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';
  import UserMenu from '$lib/UserMenu.svelte';
  import GrupoToggleButton from '$lib/GrupoToggleButton.svelte';
  import GrupoEspecialidadesPainel from '$lib/GrupoEspecialidadesPainel.svelte';
  import { formatarPeriodoCota } from '$lib/cotas.js';
  import { FILTROS_VAZIOS, sanitizarFiltro, tagsAtivas, periodoInvalido } from '$lib/cotasFiltro.js';
  import {
    TAMANHOS_DE_PAGINA,
    juntarCotas,
    percentual,
    corDaBarra,
    situacaoDaCota,
    nomeDoGrupoFiltrado,
    lerEstadoDaUrl,
    serializarEstado,
    haFiltrosDaUnidade,
    filtrarCotasDaUnidade,
    ordenarComParticao,
    contarParticao,
    filtrosDoCartao,
    cartaoAtivo,
    opcoesDeGrupo,
    opcoesDeEspecialidade,
    numerosDePagina,
    paginar,
    totalDePaginas
  } from '$lib/cotasUnidade.js';

  // Consulta somente-leitura das cotas da própria unidade de lotação.
  // A definição/alteração de cotas continua em /admin/cotas, exclusiva do ADMIN
  // global — aqui o usuário da unidade apenas acompanha o consumo que o limita.
  //
  // O unidadeId nunca vem da URL: é lido de /users/me. Mesmo assim o backend
  // revalida o acesso em /cotas/unidade/{id}, então trocar o id numa chamada
  // direta à API não expõe dados de outra unidade. Pelo mesmo motivo, os filtros
  // `unidadeId`, `profissionalId`, `executanteId` e `origem` que existem na
  // lista do ADMIN são descartados aqui (ver `lerEstadoDaUrl`).

  type Cota = {
    id: number;
    unidadeId: number | null;
    unidadeNome: string | null;
    grupoUnidadesId: number | null;
    grupoUnidadesNome: string | null;
    grupoEspecialidadesId: number | null;
    grupoEspecialidadesNome: string | null;
    especialidadeId: number | null;
    especialidadeNome: string | null;
    tipoPeriodo: 'MENSAL' | 'DATA';
    periodo: string | null;
    dataEspecifica: string | null;
    quantidadeTotal: number;
    quantidadeUtilizada: number;
    saldoDisponivel: number;
    ativo: boolean;
    profissionalNome: string | null;
    localAgendamentoNome: string | null;
    horaInicial: string | null;
    horaFinal: string | null;
    diasSemana: string[] | null;
  };

  type Grupo = { id: number; nome: string; especialidades: { id: number; nome: string }[] };

  // Listas só de leitura, sempre trocadas por inteiro: $state.raw evita o proxy
  // profundo do Svelte sobre uma lista que pode crescer sem limite.
  let cotas = $state.raw<Cota[]>([]);
  // Grupos de relatório — usado para "abrir" uma cota de grupo e listar as
  // especialidades que a compõem (mesmo dado já usado em /admin/cotas).
  let grupos = $state.raw<Grupo[]>([]);
  let unidadeNome = $state('');
  let grupoNome = $state<string | null>(null);
  let isLoading = $state(true);
  let error = $state('');

  // Dado de apoio que falhou não esconde cota: o filtro correspondente deixa
  // tudo visível e a tela avisa.
  let gruposCarregados = $state(true);
  let cotasDoGrupoIndisponiveis = $state(false);

  // Quais cotas de grupo estão com o painel de especialidades expandido —
  // por id de cota, não de grupo, já que a mesma cota de grupo só aparece
  // uma vez na tabela. Fica aqui (não dentro de um componente) porque o
  // painel precisa virar uma linha de tabela à parte (colspan), fora da
  // célula "Especialidade / Exame" onde fica o botão.
  let gruposAbertos = $state<Record<number, boolean>>({});
  function alternarGrupo(cotaId: number) {
    gruposAbertos = { ...gruposAbertos, [cotaId]: !gruposAbertos[cotaId] };
  }

  // A URL é a fonte única do estado (filtros, ordem, página, tamanho e o
  // `?grupo=` do dashboard): dá para recarregar, voltar e compartilhar o link.
  // Só o texto da busca fica num $state local, para filtrar a cada tecla e
  // escrever na URL com atraso.
  const estado = $derived(lerEstadoDaUrl(page.url.searchParams));
  let buscaLocal = $state(lerEstadoDaUrl(page.url.searchParams).filtros.q);
  const filtros = $derived({ ...estado.filtros, q: buscaLocal });
  let temporizadorDaUrl: ReturnType<typeof setTimeout> | null = null;

  onMount(async () => {
    try {
      const resMe = await getApi('users/me');
      if (!resMe.ok) throw new Error(`Falha ao carregar usuário: ${resMe.status}`);
      const me = await resMe.json();

      unidadeNome = me.unidadeNome ?? 'Minha Unidade';

      if (!me.unidadeId) {
        error = 'Seu usuário não possui unidade de lotação vinculada. Solicite o vínculo ao administrador.';
        return;
      }

      const resCotas = await getApi(`cotas/unidade/${me.unidadeId}`);
      if (!resCotas.ok) throw new Error(`Falha ao carregar cotas: ${resCotas.status}`);
      const daUnidade: Cota[] = await resCotas.json();

      // A unidade também é limitada pela cota do grupo a que pertence (pool
      // compartilhado), então ela precisa aparecer aqui — do contrário o usuário
      // veria saldo na própria cota sem entender por que o agendamento é barrado.
      const resUnidade = await getApi(`unidades/${me.unidadeId}`);
      let doGrupo: Cota[] = [];
      if (resUnidade.ok) {
        const unidade = await resUnidade.json();
        if (unidade.grupoRelatorioId) {
          grupoNome = unidade.grupoRelatorioNome ?? null;
          const resGrupo = await getApi(`cotas/grupo-unidades/${unidade.grupoRelatorioId}`);
          if (resGrupo.ok) doGrupo = await resGrupo.json();
          else cotasDoGrupoIndisponiveis = true;
        }
      }

      cotas = juntarCotas(daUnidade, doGrupo);

      const resGrupos = await getApi('grupo-relatorio/listar');
      if (resGrupos.ok) grupos = await resGrupos.json();
      else gruposCarregados = false;
    } catch (e: unknown) {
      error = e instanceof Error ? e.message : String(e);
    } finally {
      isLoading = false;
    }
  });

  onDestroy(() => {
    if (temporizadorDaUrl) clearTimeout(temporizadorDaUrl);
  });

  // Navegação de fora (menu, link do dashboard, voltar/avançar do navegador)
  // reaproveita a busca da URL; as escritas desta própria tela são `goto` e
  // não precisam ser relidas.
  afterNavigate((navegacao) => {
    if (navegacao.type !== 'goto') {
      // Voltar/avançar não pode ser seguido por uma escrita atrasada da busca.
      if (temporizadorDaUrl) clearTimeout(temporizadorDaUrl);
      buscaLocal = estado.filtros.q;
    }
  });

  // Veio de "Ver lista completa" no card de Próximas Cotas do Dashboard: filtra
  // a listagem para o grupo de especialidades clicado, em vez de criar uma tela
  // nova só para isso.
  const grupoFiltroNome = $derived(nomeDoGrupoFiltrado(estado.grupo, grupos, cotas));

  const contexto = $derived({ grupos, gruposCarregados });
  const cotasFiltradas = $derived(filtrarCotasDaUnidade(cotas, filtros, estado.grupo, contexto));
  const cotasOrdenadas = $derived(ordenarComParticao(cotasFiltradas, estado.ordenacao));
  const contagem = $derived(contarParticao(cotas, filtros, estado.grupo, contexto));
  const cartaoAtual = $derived(cartaoAtivo(filtros));
  const temFiltros = $derived(haFiltrosDaUnidade(filtros, estado.grupo));
  const intervaloInvalido = $derived(periodoInvalido(filtros));

  const totalPaginas = $derived(totalDePaginas(cotasOrdenadas.length, estado.tamanho));
  // Correção passiva de limite (ex.: filtro reduziu o total e a página da URL
  // deixou de existir). Derivado, não $effect: não escreve estado, sem laço.
  const paginaExibida = $derived(Math.min(estado.pagina, totalPaginas));
  const cotasPaginadas = $derived(paginar(cotasOrdenadas, paginaExibida, estado.tamanho).itens);
  const numeros = $derived(numerosDePagina(paginaExibida, totalPaginas));
  const primeiraDaPagina = $derived(
    cotasOrdenadas.length === 0 ? 0 : (paginaExibida - 1) * estado.tamanho + 1
  );
  const ultimaDaPagina = $derived(Math.min(cotasOrdenadas.length, paginaExibida * estado.tamanho));

  const opcoesEspecialidade = $derived(opcoesDeEspecialidade(cotas, grupos));
  const opcoesGrupo = $derived(opcoesDeGrupo(cotas, grupos));
  const tags = $derived(tagsAtivas(filtros, { especialidades: opcoesEspecialidade }));

  const CARTOES = [
    { chave: 'disponiveis', rotulo: 'Disponíveis' },
    { chave: 'esgotadas', rotulo: 'Esgotadas' },
    { chave: 'inativas', rotulo: 'Inativas' }
  ] as const;

  function escreverNaUrl(parcial: Record<string, unknown> = {}) {
    if (temporizadorDaUrl) clearTimeout(temporizadorDaUrl);
    const proximo = {
      filtros,
      grupo: estado.grupo,
      ordenacao: estado.ordenacao,
      pagina: estado.pagina,
      tamanho: estado.tamanho,
      ...parcial
    };
    const naUrl = serializarEstado(proximo).toString();
    goto(naUrl ? `?${naUrl}` : page.url.pathname, { replaceState: true, keepFocus: true, noScroll: true });
  }

  // Mudar filtro, ordem, cartão ou tamanho volta para a página 1.
  function definirFiltro(chave: string, valor: string) {
    escreverNaUrl({
      filtros: { ...filtros, [chave]: sanitizarFiltro(chave, valor) },
      pagina: 1
    });
  }

  function definirBusca(valor: string) {
    buscaLocal = valor;
    if (temporizadorDaUrl) clearTimeout(temporizadorDaUrl);
    temporizadorDaUrl = setTimeout(
      () => escreverNaUrl({ filtros: { ...filtros, q: valor }, pagina: 1 }),
      300
    );
  }

  function definirGrupo(valor: string) {
    const id = Number(valor);
    escreverNaUrl({ grupo: valor && id ? id : null, pagina: 1 });
  }

  function aplicarCartao(cartao: 'disponiveis' | 'esgotadas' | 'inativas') {
    const alvo = cartaoAtual === cartao ? { status: '', saldo: '' } : filtrosDoCartao(cartao);
    escreverNaUrl({ filtros: { ...filtros, ...alvo }, pagina: 1 });
  }

  function limparFiltros() {
    buscaLocal = '';
    escreverNaUrl({ filtros: { ...FILTROS_VAZIOS }, grupo: null, pagina: 1 });
  }

  function limparFiltroGrupo() {
    escreverNaUrl({ grupo: null, pagina: 1 });
  }

  function irParaPagina(numero: number) {
    escreverNaUrl({ pagina: Math.min(Math.max(1, numero), totalPaginas) });
  }
</script>

<svelte:head>
  <title>Cotas — {unidadeNome}</title>
</svelte:head>

{#snippet campoSelect(
  id: string,
  rotulo: string,
  valor: string,
  aoMudar: (v: string) => void,
  itens: { valor: string; rotulo: string }[],
  todos: string | null
)}
  <div class="min-w-0">
    <label for={id} class="ibm-rotulo">{rotulo}</label>
    <select
      {id}
      value={valor}
      onchange={(e) => aoMudar(e.currentTarget.value)}
      class="ibm-campo w-full"
    >
      {#if todos !== null}
        <option value="">{todos}</option>
      {/if}
      {#each itens as item (item.valor)}
        <option value={item.valor}>{item.rotulo}</option>
      {/each}
    </select>
  </div>
{/snippet}

<div class="flex min-h-screen bg-gray-200">
  <RoleBasedMenu activePage="/unidade/cotas" />
  <div class="flex-1 min-w-0 flex flex-col">
    <header class="bg-emerald-700 text-white shadow py-4 pr-4 pl-12 md:p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Cotas — {unidadeNome}</h1>
      {#if $user}
        <UserMenu />
      {:else}
        <div><a href="/login" class="hover:underline">Fazer Login</a></div>
      {/if}
    </header>

    <main class="cotas-ibm flex-1 py-4 pr-4 pl-12 md:p-6 overflow-auto">
      <div class="max-w-[1600px] mx-auto space-y-6">

        {#if isLoading}
          <p class="ibm-suave-texto">Carregando cotas...</p>
        {:else if error}
          <div class="ibm-erro">{error}</div>
        {:else if cotas.length === 0}
          <div class="ibm-painel ibm-vazio-bloco">
            <p class="ibm-vazio-titulo">Nenhuma cota cadastrada para esta unidade.</p>
            <p class="ibm-suave-texto mt-1">
              Sem cota configurada não há limite de agendamentos — os cadastros seguem normalmente.
            </p>
          </div>
        {:else}
          {#if grupoNome}
            <div class="ibm-info">
              Esta unidade pertence ao grupo <strong>{grupoNome}</strong>. As cotas do grupo são um saldo
              compartilhado entre as unidades membros e também limitam os agendamentos desta unidade.
              {#if cotasDoGrupoIndisponiveis}
                <p class="ibm-aviso mt-2" role="status">
                  Não foi possível carregar as cotas do grupo: a lista abaixo mostra só as cotas da unidade.
                </p>
              {/if}
            </div>
          {/if}

          {#if !gruposCarregados}
            <p class="ibm-aviso" role="status">
              Não foi possível carregar os grupos de especialidades: as cotas de grupo permanecem visíveis ao
              filtrar por especialidade.
            </p>
          {/if}

          <section aria-label="Situação das cotas">
            <div class="grid grid-cols-1 lg:grid-cols-3 gap-3">
              {#each CARTOES as cartao (cartao.chave)}
                <button
                  type="button"
                  aria-pressed={cartaoAtual === cartao.chave}
                  onclick={() => aplicarCartao(cartao.chave)}
                  data-situacao={cartao.chave}
                  class="ibm-cartao"
                >
                  <span class="ibm-cartao-topo">
                    <span class="ibm-marca" aria-hidden="true"></span>
                    <span class="ibm-cartao-rotulo">{cartao.rotulo}</span>
                  </span>
                  <span class="ibm-cartao-numero">{contagem[cartao.chave]}</span>
                  <span class="ibm-cartao-dica" aria-hidden="true">
                    {cartaoAtual === cartao.chave ? '✓ Mostrando só estas' : 'Mostrar só estas'}
                  </span>
                </button>
              {/each}
            </div>
            <p class="ibm-ajuda">
              Esgotada = saldo da própria cota zerado. Confira o período de cada cota.
            </p>
          </section>

          <section aria-label="Filtros da lista de cotas" class="ibm-painel ibm-filtros">
            <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-x-4 gap-y-4">
              <div class="min-w-0 sm:col-span-2">
                <label for="filtro-busca" class="ibm-rotulo">Buscar</label>
                <input
                  id="filtro-busca"
                  type="search"
                  value={buscaLocal}
                  maxlength="80"
                  autocomplete="off"
                  placeholder="Especialidade, grupo, profissional ou local"
                  oninput={(e) => definirBusca(e.currentTarget.value)}
                  class="ibm-campo w-full"
                />
              </div>
              {@render campoSelect(
                'filtro-especialidade',
                'Especialidade',
                filtros.especialidadeId,
                (v) => definirFiltro('especialidadeId', v),
                opcoesEspecialidade.map((e) => ({ valor: String(e.id), rotulo: e.nome })),
                'Todas as especialidades'
              )}
              {@render campoSelect(
                'filtro-grupo',
                'Grupo de especialidades',
                estado.grupo ? String(estado.grupo) : '',
                definirGrupo,
                opcoesGrupo.map((g) => ({ valor: String(g.id), rotulo: g.nome })),
                'Todos os grupos'
              )}
              {@render campoSelect(
                'filtro-status',
                'Status',
                filtros.status,
                (v) => definirFiltro('status', v),
                [
                  { valor: 'ativa', rotulo: 'Ativa' },
                  { valor: 'inativa', rotulo: 'Inativa' }
                ],
                'Todos os status'
              )}
              {@render campoSelect(
                'filtro-saldo',
                'Situação do saldo',
                filtros.saldo,
                (v) => definirFiltro('saldo', v),
                [
                  { valor: 'com-saldo', rotulo: 'Com saldo' },
                  { valor: 'esgotada', rotulo: 'Cota esgotada' }
                ],
                'Qualquer saldo'
              )}
              {@render campoSelect(
                'filtro-tipo',
                'Tipo',
                filtros.tipo,
                (v) => definirFiltro('tipo', v),
                [
                  { valor: 'mensal', rotulo: 'Mensal' },
                  { valor: 'data', rotulo: 'Por data' }
                ],
                'Todos os tipos'
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
                  onchange={(e) => definirFiltro('dataAte', e.currentTarget.value)}
                  class="ibm-campo w-full"
                />
              </div>
              {@render campoSelect(
                'filtro-ordenacao',
                'Ordenar por',
                estado.ordenacao,
                (v) => escreverNaUrl({ ordenacao: v || 'proximidade', pagina: 1 }),
                [
                  { valor: 'proximidade', rotulo: 'Período (mais antigo primeiro)' },
                  { valor: 'limite', rotulo: 'Maior limite' },
                  { valor: 'saldo', rotulo: 'Maior saldo disponível' }
                ],
                null
              )}
            </div>

            {#if intervaloInvalido}
              <p class="ibm-aviso" role="status">
                A data inicial é maior que a data final. O intervalo de datas foi ignorado.
              </p>
            {/if}

            <div class="ibm-resumo">
              <p class="ibm-contador" aria-live="polite">
                <span class="font-semibold tabular-nums">{cotasFiltradas.length}</span> de
                <span class="tabular-nums">{cotas.length}</span> cota{cotas.length === 1 ? '' : 's'}
              </p>
              {#if estado.grupo}
                <span class="ibm-tag ibm-tag--removivel">
                  Filtrando por grupo: {grupoFiltroNome}
                  <button
                    type="button"
                    onclick={limparFiltroGrupo}
                    class="ibm-tag-remover"
                    aria-label="Remover filtro de grupo"
                  >✕</button>
                </span>
              {/if}
              {#each tags as tag (tag.chave)}
                <button
                  type="button"
                  onclick={() => (tag.chave === 'q' ? definirBusca('') : definirFiltro(tag.chave, ''))}
                  aria-label="Remover filtro: {tag.rotulo}"
                  class="ibm-tag"
                >
                  <span>{tag.rotulo}</span>
                  <span aria-hidden="true">×</span>
                </button>
              {/each}
              {#if temFiltros}
                <button type="button" onclick={limparFiltros} class="ibm-btn ibm-btn--fantasma">
                  Limpar filtros
                </button>
              {/if}
            </div>
          </section>

          {#if cotasOrdenadas.length === 0}
            <div class="ibm-painel ibm-vazio-bloco">
              <p class="ibm-vazio-titulo">Nenhuma cota encontrada com os filtros atuais.</p>
              <button type="button" onclick={limparFiltros} class="ibm-btn ibm-btn--fantasma mt-2">
                Limpar filtros
              </button>
            </div>
          {:else}

          <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
          <div class="ibm-tabela-contorno" role="region" aria-label="Lista de cotas, com rolagem horizontal" tabindex="0">
            <table class="ibm-tabela min-w-full">
              <thead>
                <tr>
                  <th class="text-left min-w-[120px]">Titular</th>
                  <th class="text-left min-w-[300px]">Especialidade / Exame</th>
                  <th class="text-left min-w-[160px]">Profissional / Horário / Local</th>
                  <th class="text-left min-w-[90px]">Período</th>
                  <th class="text-right min-w-[100px]">Utilizadas</th>
                  <th class="text-right min-w-[80px]">Total</th>
                  <th class="text-right min-w-[80px]">Saldo</th>
                  <th class="text-left w-48 min-w-[160px]">Consumo</th>
                </tr>
              </thead>
              <tbody>
                {#each cotasPaginadas as c (c.id)}
                  {@const situacao = situacaoDaCota(c)}
                  <tr class="ibm-linha" data-situacao={situacao}>
                    <td>
                      {#if c.grupoUnidadesId}
                        <span class="ibm-badge ibm-badge--contorno">Grupo</span>
                        <span class="ml-2">{c.grupoUnidadesNome}</span>
                      {:else}
                        <span class="ibm-badge ibm-badge--neutro">Unidade</span>
                        <span class="ml-2">{c.unidadeNome}</span>
                      {/if}
                    </td>
                    <td>
                      {#if c.grupoEspecialidadesId}
                        <span class="ibm-badge ibm-badge--acento">Grupo</span>
                        <span class="ml-2">{c.grupoEspecialidadesNome}</span>
                        <GrupoToggleButton
                          grupo={grupos.find((g) => g.id === c.grupoEspecialidadesId)}
                          aberto={!!gruposAbertos[c.id]}
                          onToggle={() => alternarGrupo(c.id)}
                          variante="ibm"
                        />
                      {:else if c.especialidadeNome}
                        {c.especialidadeNome}
                      {:else}
                        <span class="ibm-suave">Cota geral (todas)</span>
                      {/if}
                    </td>
                    <td class="ibm-detalhe">
                      {#if c.profissionalNome || c.horaInicial || c.localAgendamentoNome || (c.diasSemana && c.diasSemana.length > 0)}
                        <div>{c.profissionalNome ?? 'Profissional: não informado'}</div>
                        {#if c.horaInicial}<div>{c.horaInicial.slice(0,5)}–{c.horaFinal ? c.horaFinal.slice(0,5) : '-'}</div>{/if}
                        {#if c.localAgendamentoNome}<div class="ibm-suave">{c.localAgendamentoNome}</div>{/if}
                        {#if c.diasSemana && c.diasSemana.length > 0}<div class="ibm-suave">{c.diasSemana.join(', ')}</div>{/if}
                      {:else}
                        <span class="ibm-suave">-</span>
                      {/if}
                    </td>
                    <td class="ibm-num">{formatarPeriodoCota(c)}</td>
                    <td class="ibm-num text-right">{c.quantidadeUtilizada}</td>
                    <td class="ibm-num text-right">{c.quantidadeTotal}</td>
                    <td class="ibm-num ibm-saldo text-right"
                        class:ibm-saldo--zero={c.saldoDisponivel <= 0}
                        class:ibm-saldo--ok={c.saldoDisponivel > 0}>
                      {c.saldoDisponivel}
                    </td>
                    <td>
                      <div class="ibm-barra">
                        <div class="ibm-barra-fill {corDaBarra(c)}" style="width: {percentual(c)}%"></div>
                      </div>
                      <span
                        class="ibm-situacao"
                        data-situacao={situacao}
                        title={situacao === 'esgotada' ? 'Saldo da própria cota zerado. Confira o período.' : undefined}
                      >
                        <span class="ibm-marca" aria-hidden="true"></span>
                        {situacao === 'inativa' ? 'Inativa' : situacao === 'esgotada' ? 'Esgotada' : 'Disponível'}
                      </span>
                    </td>
                  </tr>
                  {#if c.grupoEspecialidadesId && gruposAbertos[c.id]}
                    <tr class="ibm-linha-painel" data-situacao={situacao}>
                      <td colspan="8" class="ibm-celula-painel">
                        <GrupoEspecialidadesPainel
                          grupo={grupos.find((g) => g.id === c.grupoEspecialidadesId)}
                          variante="ibm"
                        />
                      </td>
                    </tr>
                  {/if}
                {/each}
              </tbody>
            </table>
          </div>

          <nav aria-label="Paginação das cotas" class="ibm-paginacao">
            <p class="ibm-suave-texto" aria-live="polite">
              Mostrando <span class="tabular-nums">{primeiraDaPagina}</span> a
              <span class="tabular-nums">{ultimaDaPagina}</span> de
              <span class="tabular-nums">{cotasOrdenadas.length}</span>
            </p>

            {#if totalPaginas > 1}
              <div class="ibm-paginas">
                <button
                  type="button"
                  onclick={() => irParaPagina(paginaExibida - 1)}
                  disabled={paginaExibida === 1}
                  class="ibm-btn ibm-btn--secundario"
                >
                  &laquo; Anterior
                </button>
                {#each numeros as n, i (i)}
                  {#if n === '…'}
                    <span class="ibm-suave px-1" aria-hidden="true">…</span>
                  {:else}
                    <button
                      type="button"
                      onclick={() => irParaPagina(Number(n))}
                      aria-label="Página {n}"
                      aria-current={n === paginaExibida ? 'page' : undefined}
                      class="ibm-pagina"
                    >
                      {n}
                    </button>
                  {/if}
                {/each}
                <button
                  type="button"
                  onclick={() => irParaPagina(paginaExibida + 1)}
                  disabled={paginaExibida === totalPaginas}
                  class="ibm-btn ibm-btn--secundario"
                >
                  Próximo &raquo;
                </button>
              </div>
            {/if}

            <div class="ibm-por-pagina">
              <label for="tamanho-pagina" class="ibm-suave-texto">Por página</label>
              <select
                id="tamanho-pagina"
                value={String(estado.tamanho)}
                onchange={(e) => escreverNaUrl({ tamanho: Number(e.currentTarget.value), pagina: 1 })}
                class="ibm-campo"
              >
                {#each TAMANHOS_DE_PAGINA as tamanho (tamanho)}
                  <option value={String(tamanho)}>{tamanho}</option>
                {/each}
              </select>
            </div>
          </nav>
          {/if}
        {/if}

      </div>
    </main>
  </div>
</div>

<style>
  /*
   * Origem: cópia do bloco <style> de /admin/cotas/nova e /admin/cotas/[id]/editar
   * (visual "Mistura SIRG + Carbon/IBM"), reduzida ao que esta tela usa e escopada
   * a .cotas-ibm. Mesmos nomes e valores de token; para ajustar a mistura, altere
   * os tokens marcados "SIRG" (IBM puro entre parênteses). Acento emerald do SIRG,
   * nunca azul IBM. A fonte é uma pilha com fallback: nada é baixado.
   * Específico desta tela: cartões e badges de situação (disponível, esgotada,
   * inativa) e o filete lateral das linhas.
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
    --ibm-cabecalho: #e0e0e0;
    --ibm-borda-campo: #8c8c8c;
    --ibm-hover: #ececec;
    --ibm-erro-texto: #a2191f;
    --ibm-erro-fundo: #ffd7d9;
    --ibm-aviso-fundo: #fcf4d6;
    --ibm-aviso-borda: #f1c21b;

    /* Painel: cartão do SIRG (rounded-lg + shadow). IBM: 0 / none / 1px solid var(--ibm-linha) */
    --ibm-raio-painel: 8px;
    --ibm-sombra-painel: 0 1px 3px 0 rgb(0 0 0 / 0.1), 0 1px 2px -1px rgb(0 0 0 / 0.1);
    --ibm-borda-painel: 0;
    /* Botões, listas e avisos: rounded-md do SIRG. IBM: 0 */
    --ibm-raio-controle: 6px;
    /* Campos (cinza com linha inferior): estilo IBM, mantido. SIRG seria 6px */
    --ibm-raio-campo: 0;
    /* Botão secundário: claro como no SIRG. IBM: fundo #393939, texto branco */
    --ibm-secundario-fundo: #ffffff;
    --ibm-secundario-texto: #374151;
    --ibm-secundario-hover: #f9fafb;

    /* Identidade de cada situação (cartão, badge, filete): sempre com texto e marca */
    --sit-disponivel-cor: #047857;
    --sit-disponivel-fundo: #d1fae5;
    --sit-disponivel-texto: #065f46;
    --sit-esgotada-cor: #a2191f;
    --sit-esgotada-fundo: #ffd7d9;
    --sit-esgotada-texto: #a2191f;
    --sit-inativa-cor: #6f6f6f;
    --sit-inativa-fundo: #e0e0e0;
    --sit-inativa-texto: #393939;

    background: var(--ibm-fundo);
    color: var(--ibm-tinta);
    font-family: var(--ibm-fonte);
    font-size: 14px;
    line-height: 1.29;
    letter-spacing: 0.16px;
  }

  .ibm-painel {
    background: var(--ibm-camada);
    border: var(--ibm-borda-painel);
    border-radius: var(--ibm-raio-painel);
    box-shadow: var(--ibm-sombra-painel);
  }
  .ibm-filtros {
    padding: 24px;
  }
  .ibm-vazio-bloco {
    padding: 48px 16px;
    text-align: center;
  }
  .ibm-vazio-titulo {
    font-size: 16px;
    font-weight: 600;
  }

  .ibm-rotulo {
    display: block;
    margin-bottom: 8px;
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
  .ibm-suave {
    color: var(--ibm-tinta-suave);
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

  /* Mensagens em linha */
  .ibm-info {
    padding: 12px 16px;
    border-left: 4px solid var(--ibm-acento);
    border-radius: 0 var(--ibm-raio-controle) var(--ibm-raio-controle) 0;
    background: var(--ibm-camada);
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
    border-left: 4px solid var(--ibm-erro-texto);
    border-radius: 0 var(--ibm-raio-controle) var(--ibm-raio-controle) 0;
    background: var(--ibm-erro-fundo);
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
    background: #e0f2fe;
    color: #075985;
  }

  /* Botões */
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
  .ibm-btn--secundario {
    border-color: var(--ibm-borda-campo);
    background: var(--ibm-secundario-fundo);
    color: var(--ibm-secundario-texto);
  }
  .ibm-btn--secundario:hover:not(:disabled) {
    background: var(--ibm-secundario-hover);
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

  /* Marca de situação: forma diferente por situação, para não depender só de cor */
  .ibm-marca {
    flex: none;
    width: 10px;
    height: 10px;
    background: var(--ibm-tinta-suave);
  }
  [data-situacao='disponivel'] .ibm-marca,
  [data-situacao='disponiveis'] .ibm-marca {
    background: var(--sit-disponivel-cor);
    border-radius: 9999px;
  }
  [data-situacao='esgotada'] .ibm-marca,
  [data-situacao='esgotadas'] .ibm-marca {
    background: var(--sit-esgotada-cor);
    transform: rotate(45deg) scale(0.9);
  }
  [data-situacao='inativa'] .ibm-marca,
  [data-situacao='inativas'] .ibm-marca {
    border: 2px solid var(--sit-inativa-cor);
    border-radius: 9999px;
    background: transparent;
  }

  /* Cartões de situação: filtro principal da tela */
  .ibm-cartao {
    display: grid;
    grid-template-columns: 1fr auto;
    grid-template-areas:
      'topo numero'
      'dica numero';
    align-items: center;
    gap: 4px 16px;
    min-height: 88px;
    padding: 12px 16px;
    border: 2px solid var(--ibm-linha);
    border-left: 8px solid var(--cartao-cor);
    border-radius: var(--ibm-raio-controle);
    background: var(--ibm-camada);
    box-shadow: var(--ibm-sombra-painel);
    color: var(--ibm-tinta);
    font-family: inherit;
    text-align: left;
    cursor: pointer;
  }
  .ibm-cartao[data-situacao='disponiveis'] {
    --cartao-cor: var(--sit-disponivel-cor);
    --cartao-fundo: var(--sit-disponivel-fundo);
    --cartao-texto: var(--sit-disponivel-texto);
  }
  .ibm-cartao[data-situacao='esgotadas'] {
    --cartao-cor: var(--sit-esgotada-cor);
    --cartao-fundo: var(--sit-esgotada-fundo);
    --cartao-texto: var(--sit-esgotada-texto);
  }
  .ibm-cartao[data-situacao='inativas'] {
    --cartao-cor: var(--sit-inativa-cor);
    --cartao-fundo: var(--sit-inativa-fundo);
    --cartao-texto: var(--sit-inativa-texto);
  }
  .ibm-cartao:hover {
    background: var(--ibm-secundario-hover);
  }
  .ibm-cartao[aria-pressed='true'] {
    border-color: var(--cartao-cor);
    background: var(--cartao-fundo);
  }
  .ibm-cartao:focus-visible {
    outline: 2px solid var(--ibm-acento);
    outline-offset: 2px;
  }
  .ibm-cartao-topo {
    grid-area: topo;
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .ibm-cartao-rotulo {
    font-size: 16px;
    font-weight: 600;
    color: var(--cartao-texto);
  }
  .ibm-cartao-numero {
    grid-area: numero;
    font-size: 36px;
    font-weight: 600;
    line-height: 1;
    font-variant-numeric: tabular-nums;
    color: var(--cartao-texto);
  }
  .ibm-cartao-dica {
    grid-area: dica;
    font-size: 12px;
    letter-spacing: 0.32px;
    color: var(--ibm-tinta-suave);
  }
  .ibm-cartao[aria-pressed='true'] .ibm-cartao-dica {
    font-weight: 600;
    color: var(--cartao-texto);
  }

  /* Resumo dos filtros: contador, tags e "Limpar filtros" */
  .ibm-resumo {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px 12px;
    min-height: 32px;
    margin-top: 16px;
    padding-top: 16px;
    border-top: 1px solid var(--ibm-linha);
  }
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
  .ibm-tag:focus-visible,
  .ibm-tag-remover:focus-visible,
  .ibm-pagina:focus-visible {
    outline: 2px solid var(--ibm-acento);
    outline-offset: 2px;
  }
  .ibm-tag--removivel {
    padding-right: 2px;
    cursor: default;
  }
  .ibm-tag--removivel:hover {
    background: var(--ibm-acento-suave);
  }
  .ibm-tag-remover {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    border-radius: 9999px;
    line-height: 1;
    cursor: pointer;
  }
  .ibm-tag-remover:hover {
    background: #a7f3d0;
  }

  /* Tabela: cabeçalho cinza, linhas de 1 px, filete lateral por situação */
  .ibm-tabela-contorno {
    overflow-x: auto;
    background: var(--ibm-camada);
    border: 1px solid var(--ibm-linha);
    border-radius: var(--ibm-raio-controle);
  }
  .ibm-tabela-contorno:focus-visible {
    outline: 2px solid var(--ibm-acento);
    outline-offset: -2px;
  }
  .ibm-tabela {
    font-size: 14px;
  }
  .ibm-tabela th {
    padding: 12px;
    background: var(--ibm-cabecalho);
    color: var(--ibm-tinta);
    font-size: 14px;
    font-weight: 600;
    line-height: 1.3;
  }
  .ibm-tabela td {
    padding: 12px;
    border-bottom: 1px solid var(--ibm-linha);
    vertical-align: middle;
    color: var(--ibm-tinta);
  }
  .ibm-tabela tbody tr:last-child td {
    border-bottom: 0;
  }
  .ibm-detalhe {
    font-size: 12px;
    letter-spacing: 0.32px;
    line-height: 1.5;
  }
  .ibm-num {
    font-variant-numeric: tabular-nums;
  }
  .ibm-saldo {
    font-weight: 600;
  }
  .ibm-tabela td.ibm-saldo--ok {
    color: var(--ibm-acento);
  }
  .ibm-tabela td.ibm-saldo--zero {
    color: var(--ibm-erro-texto);
  }

  .ibm-linha {
    --linha-fundo: var(--ibm-camada);
    --linha-filete: var(--sit-disponivel-cor);
  }
  .ibm-linha > td {
    background: var(--linha-fundo);
  }
  .ibm-linha > td:first-child {
    padding-left: 16px;
    box-shadow: inset 4px 0 0 var(--linha-filete);
  }
  .ibm-linha:hover > td {
    background: var(--ibm-hover);
  }
  .ibm-linha[data-situacao='esgotada'] {
    --linha-fundo: #fff5f5;
    --linha-filete: var(--sit-esgotada-cor);
  }
  .ibm-linha[data-situacao='inativa'] {
    --linha-fundo: #f4f4f4;
    --linha-filete: var(--sit-inativa-cor);
  }
  /* Inativa: texto em tom neutro escuro (contraste mantido), sem opacidade */
  .ibm-linha[data-situacao='inativa'] > td,
  .ibm-tabela .ibm-linha[data-situacao='inativa'] > td.ibm-saldo {
    color: #393939;
  }
  .ibm-linha[data-situacao='inativa'] .ibm-suave {
    color: var(--ibm-tinta-suave);
  }
  .ibm-linha[data-situacao='inativa'] .ibm-barra-fill {
    background: var(--sit-inativa-cor);
  }

  /* Separador entre os três grupos: a lista já vem particionada; a primeira
     linha de cada situação nova ganha um filete superior mais grosso. */
  .ibm-tabela tbody tr:not([data-situacao='esgotada']) + tr[data-situacao='esgotada'] > td,
  .ibm-tabela tbody tr:not([data-situacao='inativa']) + tr[data-situacao='inativa'] > td {
    border-top: 2px solid var(--ibm-borda-campo);
  }

  .ibm-linha-painel > td {
    background: var(--ibm-fundo);
  }
  .ibm-tabela td.ibm-celula-painel {
    padding: 0 16px 16px 16px;
  }

  /* Consumo: barra e badge de situação */
  .ibm-barra {
    height: 8px;
    width: 100%;
    overflow: hidden;
    border-radius: 9999px;
    background: var(--ibm-linha);
  }
  .ibm-barra-fill {
    height: 100%;
    border-radius: 9999px;
  }
  .ibm-situacao {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    min-height: 20px;
    margin-top: 8px;
    padding: 0 8px;
    border-radius: 9999px;
    background: var(--ibm-linha);
    color: var(--ibm-tinta);
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.32px;
    white-space: nowrap;
  }
  .ibm-situacao[data-situacao='disponivel'] {
    background: var(--sit-disponivel-fundo);
    color: var(--sit-disponivel-texto);
  }
  .ibm-situacao[data-situacao='esgotada'] {
    background: var(--sit-esgotada-fundo);
    color: var(--sit-esgotada-texto);
  }
  .ibm-situacao[data-situacao='inativa'] {
    background: var(--sit-inativa-fundo);
    color: var(--sit-inativa-texto);
  }

  /* Paginação */
  .ibm-paginacao {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 12px 16px;
  }
  .ibm-paginas {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 4px;
  }
  .ibm-pagina {
    min-width: 40px;
    min-height: 40px;
    padding: 0 8px;
    border: 1px solid var(--ibm-borda-campo);
    border-radius: var(--ibm-raio-controle);
    background: var(--ibm-camada);
    color: var(--ibm-tinta);
    font-family: inherit;
    font-size: 14px;
    font-variant-numeric: tabular-nums;
    cursor: pointer;
  }
  .ibm-pagina:hover {
    background: var(--ibm-hover);
  }
  .ibm-pagina[aria-current='page'] {
    border-color: var(--ibm-acento);
    background: var(--ibm-acento);
    color: #ffffff;
    font-weight: 600;
  }
  .ibm-por-pagina {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  @media (prefers-reduced-motion: no-preference) {
    .ibm-btn,
    .ibm-campo,
    .ibm-cartao,
    .ibm-pagina,
    .ibm-tag {
      transition:
        background-color 110ms ease-out,
        border-color 110ms ease-out;
    }
  }
</style>
