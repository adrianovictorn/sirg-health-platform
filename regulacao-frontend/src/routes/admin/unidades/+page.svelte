<script>
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { getApi, patchApi } from '$lib/api.js';
  import Content from '$lib/Content.svelte';
  import { toast } from 'svelte-sonner';
  import { listarUnidadesExecutantes } from '$lib/unidadesExecutantesApi.js';
  import { resumoDaLista } from '$lib/papelUnidade.js';
  import { textoDoContadorSimples } from '$lib/listaPaginada.js';
  import { criarListaPaginada } from '$lib/listaPaginada.svelte.js';
  import Paginacao from '$lib/components/ListaPaginada/Paginacao.svelte';
  import TagPapelUnidade from '$lib/sirg-design/TagPapelUnidade.svelte';

  // Filtros no servidor (UnidadeSpecification). `tipo` é o tipo DECLARADO da unidade;
  // o papel exibido na tag (derivado da parametrização) não é filtro de servidor.
  const ESQUEMA = {
    q: 'texto',
    status: ['ativa', 'inativa'],
    tipo: ['SOLICITANTE', 'EXECUTANTE', 'AMBOS'],
    grupoRelatorioId: 'id'
  };

  const lista = criarListaPaginada({
    esquema: ESQUEMA,
    caminho: 'unidades/pagina',
    mensagemDeErro: 'Erro ao carregar unidades.',
    aoFalhar: (mensagem) => toast.error(mensagem)
  });

  // Opções do filtro de grupo; se a chamada falhar o filtro fica só com "Todos".
  let grupos = $state.raw([]);
  // Executantes configuradas com operadores (V110): desativar uma delas devolve os
  // operadores à visão municipal, então pede confirmação.
  let executantes = $state.raw([]);
  // Tag de papel (ADR 0012): sem o resumo de executantes não dá para saber o papel;
  // a tag some e a tela avisa, em vez de mostrar todas como "solicitante".
  let executantesFalhou = $state(false);

  onMount(() => {
    carregarExecutantes();
    carregarGrupos();
  });

  async function carregarGrupos() {
    try {
      const res = await getApi('grupo-relatorio/listar');
      grupos = res.ok ? await res.json() : [];
    } catch {
      grupos = [];
    }
  }

  async function carregarExecutantes() {
    try {
      executantes = await listarUnidadesExecutantes();
      executantesFalhou = false;
    } catch {
      executantes = [];
      executantesFalhou = true;
    }
  }

  async function toggleAtivo(u) {
    const ex = executantes.find((e) => e.unidadeId === u.id);
    if (u.ativo && ex?.configurada && ex.operadores > 0
      && !confirm(`${u.nome} é uma unidade executante configurada. Ao desativá-la, os operadores vinculados a ela (pela lista ou pela lotação) deixam de ver os atendimentos desta executante. Desativar mesmo assim?`)) {
      return;
    }
    try {
      await patchApi(`unidades/${u.id}/status`);
      toast.success(u.ativo ? 'Unidade desativada.' : 'Unidade ativada.');
      await Promise.all([lista.recarregar(), carregarExecutantes()]);
    } catch {
      toast.error('Erro ao alterar status.');
    }
  }

  const textoContador = $derived(
    textoDoContadorSimples({
      pagina: lista.paginacao.pagina,
      tamanho: lista.paginacao.tamanho,
      totalElementos: lista.totalElementos,
      filtrado: lista.filtrado,
      singular: 'unidade',
      plural: 'unidades',
      vazio: 'Nenhuma unidade cadastrada'
    })
  );

  const ROTULOS = {
    status: { ativa: 'Ativa', inativa: 'Inativa' },
    tipo: { SOLICITANTE: 'Só solicitante', EXECUTANTE: 'Só executante', AMBOS: 'Solicitante e executante' }
  };

  // Uma tag por filtro ativo; remover a tag é zerar a chave.
  const tags = $derived(
    [
      lista.filtros.q && { chave: 'q', rotulo: `Busca: ${lista.filtros.q}` },
      lista.filtros.status && { chave: 'status', rotulo: `Status: ${ROTULOS.status[lista.filtros.status]}` },
      lista.filtros.tipo && { chave: 'tipo', rotulo: `Tipo: ${ROTULOS.tipo[lista.filtros.tipo]}` },
      lista.filtros.grupoRelatorioId && {
        chave: 'grupoRelatorioId',
        rotulo: `Grupo: ${grupos.find((g) => String(g.id) === lista.filtros.grupoRelatorioId)?.nome ?? `#${lista.filtros.grupoRelatorioId}`}`
      }
    ].filter(Boolean)
  );
</script>

<Content titleH1="Gestão de Unidades" page="/admin/unidades">
  <div class="tela-ibm flex-1 min-w-0 p-4 md:p-6 space-y-6">
    <div class="flex flex-wrap items-start justify-between gap-4">
      <div class="min-w-0 flex-1 basis-80">
        <h2 class="ibm-titulo-secao">Unidades cadastradas</h2>
        <p class="ibm-detalhe mt-1">
          O papel reflete a parametrização: a unidade só aparece como executante (ou Ambas) depois de ter local e
          grupo/especialidade configurados.
        </p>
      </div>
      <button type="button" class="ibm-btn ibm-btn--primario" onclick={() => goto('/admin/unidades/nova')}>
        + Nova unidade
      </button>
    </div>

    <section aria-label="Filtros da lista de unidades" class="ibm-painel p-4 md:p-6 space-y-4">
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div class="min-w-0 sm:col-span-2">
          <label for="filtro-busca" class="ibm-rotulo">Buscar</label>
          <input
            id="filtro-busca"
            type="search"
            value={lista.busca}
            maxlength="80"
            autocomplete="off"
            placeholder="Nome, código ou CNES"
            oninput={(e) => lista.definirBusca(e.currentTarget.value)}
            class="ibm-campo w-full"
          />
        </div>
        <div class="min-w-0">
          <label for="filtro-status" class="ibm-rotulo">Status</label>
          <select
            id="filtro-status"
            value={lista.filtros.status}
            onchange={(e) => lista.definirFiltro('status', e.currentTarget.value)}
            class="ibm-campo w-full"
          >
            <option value="">Todos os status</option>
            <option value="ativa">Ativa</option>
            <option value="inativa">Inativa</option>
          </select>
        </div>
        <div class="min-w-0">
          <label for="filtro-tipo" class="ibm-rotulo">Tipo</label>
          <select
            id="filtro-tipo"
            value={lista.filtros.tipo}
            onchange={(e) => lista.definirFiltro('tipo', e.currentTarget.value)}
            class="ibm-campo w-full"
          >
            <option value="">Todos os tipos</option>
            <option value="AMBOS">Solicitante e executante</option>
            <option value="SOLICITANTE">Só solicitante</option>
            <option value="EXECUTANTE">Só executante</option>
          </select>
        </div>
        <div class="min-w-0 lg:col-span-2">
          <label for="filtro-grupo" class="ibm-rotulo">Grupo da unidade</label>
          <select
            id="filtro-grupo"
            value={lista.filtros.grupoRelatorioId}
            onchange={(e) => lista.definirFiltro('grupoRelatorioId', e.currentTarget.value)}
            class="ibm-campo w-full"
          >
            <option value="">Todos os grupos</option>
            {#if lista.filtros.grupoRelatorioId && !grupos.some((g) => String(g.id) === lista.filtros.grupoRelatorioId)}
              <option value={lista.filtros.grupoRelatorioId}>Grupo #{lista.filtros.grupoRelatorioId}</option>
            {/if}
            {#each grupos as g (g.id)}
              <option value={String(g.id)}>{g.nome}</option>
            {/each}
          </select>
        </div>
      </div>

      {#if executantesFalhou}
        <p class="ibm-aviso" role="status">
          Não foi possível carregar a parametrização das executantes; o papel das unidades não está disponível.
        </p>
      {/if}

      <div class="ibm-resumo">
        <p class="ibm-contador" aria-live="polite">
          {#if !lista.carregou}
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
        {#if tags.length > 0 || lista.filtrado}
          <div class="ibm-filtros-ativos">
            {#each tags as tag (tag.chave)}
              <button
                type="button"
                onclick={() => lista.definirFiltro(tag.chave, '')}
                aria-label="Remover filtro: {tag.rotulo}"
                class="ibm-tag"
              >
                <span>{tag.rotulo}</span>
                <span aria-hidden="true">×</span>
              </button>
            {/each}
            {#if lista.filtrado}
              <button type="button" onclick={() => lista.limparFiltros()} class="ibm-btn ibm-btn--fantasma">
                Limpar filtros
              </button>
            {/if}
          </div>
        {/if}
      </div>
    </section>

    {#if !lista.carregou}
      <p class="ibm-suave-texto" role="status">
        {lista.falhou ? 'Não foi possível carregar as unidades.' : 'Carregando unidades…'}
      </p>
    {:else}
      <div>
        <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
        <div
          class="ibm-tabela-contorno"
          class:ibm-carregando={lista.carregando}
          aria-busy={lista.carregando}
          role="region"
          aria-label="Lista de unidades, com rolagem horizontal"
          tabindex="0"
        >
          <table class="ibm-tabela w-full">
            <thead>
              <tr>
                <th scope="col" class="text-left">Nome</th>
                <th scope="col" class="text-left">Papel</th>
                <th scope="col" class="text-left">Código</th>
                <th scope="col" class="text-left">CNES</th>
                <th scope="col" class="text-left">Telefone</th>
                <th scope="col" class="text-left">Status</th>
                <th scope="col" class="text-left">Ações</th>
              </tr>
            </thead>
            <tbody>
              {#each lista.itens as u (u.id)}
                <tr>
                  <th scope="row" class="ibm-titular text-left">{u.nome}</th>
                  <td>
                    {#if executantesFalhou}
                      <span class="ibm-detalhe" title="Papel indisponível">—</span>
                    {:else}
                      <TagPapelUnidade resumo={resumoDaLista(u, executantes)} />
                    {/if}
                  </td>
                  <td>{u.codigo || '—'}</td>
                  <td class="ibm-num">{u.cnes || '—'}</td>
                  <td class="ibm-num">{u.telefone || '—'}</td>
                  <td>
                    <span class="ibm-badge {u.ativo ? 'ibm-badge--ok' : 'ibm-badge--neutro'}">
                      {u.ativo ? 'Ativa' : 'Inativa'}
                    </span>
                  </td>
                  <td>
                    <div class="flex flex-wrap gap-2">
                      <button type="button" class="ibm-btn ibm-btn--terciario"
                        aria-label="Editar {u.nome}"
                        onclick={() => goto(`/admin/unidades/${u.id}/editar`)}>
                        Editar
                      </button>
                      {#if u.tipo !== 'SOLICITANTE'}
                        <button type="button" class="ibm-btn ibm-btn--terciario"
                          aria-label="Parametrizar executante {u.nome}"
                          onclick={() => goto(`/admin/unidades/${u.id}/executante`)}>
                          Parametrizar executante
                        </button>
                      {/if}
                      <button type="button"
                        class="ibm-btn {u.ativo ? 'ibm-btn--perigo' : 'ibm-btn--terciario'}"
                        aria-label="{u.ativo ? 'Desativar' : 'Ativar'} {u.nome}"
                        onclick={() => toggleAtivo(u)}>
                        {u.ativo ? 'Desativar' : 'Ativar'}
                      </button>
                    </div>
                  </td>
                </tr>
              {:else}
                <tr class="ibm-linha-vazia">
                  <td colspan="7" class="ibm-vazio">
                    {#if lista.filtrado}
                      Nenhuma unidade corresponde aos filtros.
                      <button type="button" onclick={() => lista.limparFiltros()} class="ibm-btn ibm-btn--fantasma ml-1">
                        Limpar filtros
                      </button>
                    {:else}
                      Nenhuma unidade cadastrada.
                    {/if}
                  </td>
                </tr>
              {/each}
            </tbody>
          </table>
        </div>
        <Paginacao
          pagina={lista.paginacao.pagina}
          totalPaginas={lista.totalPaginas}
          tamanho={lista.paginacao.tamanho}
          desabilitado={lista.carregando}
          onPagina={lista.irParaPagina}
          onTamanho={lista.definirTamanho}
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
  .tela-ibm {
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
  .ibm-tag:focus-visible {
    outline: 2px solid var(--ibm-acento);
    outline-offset: 2px;
  }
  .ibm-btn--primario {
    background: var(--ibm-acento);
    color: #ffffff;
  }
  .ibm-btn--perigo {
    min-height: 32px;
    padding: 0 12px;
    border-color: var(--ibm-erro-texto);
    background: transparent;
    color: var(--ibm-erro-texto);
  }
  .ibm-btn--perigo:hover {
    background: var(--ibm-erro-texto);
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
    opacity: 0.55;
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
  .ibm-tabela tbody th {
    padding: 8px 16px;
    border-bottom: 1px solid var(--ibm-linha);
    background: var(--ibm-camada);
    vertical-align: middle;
  }
  .ibm-tabela tbody tr:last-child td,
  .ibm-tabela tbody tr:last-child th {
    border-bottom: 0;
  }
  .ibm-tabela tbody tr:hover > td,
  .ibm-tabela tbody tr:hover > th {
    background: var(--ibm-hover);
  }
  .ibm-tabela .ibm-linha-vazia:hover > td {
    background: var(--ibm-camada);
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
  }</style>
