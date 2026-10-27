<script>
  import { onMount } from 'svelte';
  import { getApi, postApi, putApi, patchApi, deleteByIdApi } from '$lib/api.js';
  import Content from '$lib/Content.svelte';
  import { toast } from 'svelte-sonner';
  import { textoDoContadorSimples } from '$lib/listaPaginada.js';
  import { criarListaPaginada } from '$lib/listaPaginada.svelte.js';
  import Paginacao from '$lib/components/ListaPaginada/Paginacao.svelte';

  // Filtros no servidor (ProfissionalSpecification), via GET /api/profissionais/buscar.
  // O CPF nunca entra na busca nem na URL. `ativo` vai como true/false, como a API espera.
  const ESQUEMA = {
    q: 'texto',
    ativo: ['true', 'false'],
    unidadeId: 'id'
  };

  // `buscar` devolve o Page do Spring (content / totalElements / totalPages).
  const lista = criarListaPaginada({
    esquema: ESQUEMA,
    caminho: 'profissionais/buscar',
    lerPagina: (json) => ({
      itens: json.content ?? [],
      totalElementos: json.totalElements ?? 0,
      totalPaginas: json.totalPages ?? 0
    }),
    mensagemDeErro: 'Erro ao carregar profissionais.',
    aoFalhar: (mensagem) => toast.error(mensagem)
  });

  let unidades = $state.raw([]);
  let showModal = $state(false);
  let editando = $state(null);

  let form = $state({ nome: '', conselho: '', numeroRegistro: '', especialidadeAtuacao: '', telefone: '', unidadeId: null });

  onMount(carregarUnidades);

  async function carregarUnidades() {
    try {
      const res = await getApi('unidades/ativas');
      unidades = await res.json();
    } catch {
      // silencioso
    }
  }

  function abrirModalNovo() {
    editando = null;
    form = { nome: '', conselho: '', numeroRegistro: '', especialidadeAtuacao: '', telefone: '', unidadeId: null };
    showModal = true;
  }

  function abrirModalEditar(p) {
    editando = p;
    form = {
      nome: p.nome,
      conselho: p.conselho || '',
      numeroRegistro: p.numeroRegistro || '',
      especialidadeAtuacao: p.especialidadeAtuacao || '',
      telefone: p.telefone || '',
      unidadeId: p.unidadeId || null
    };
    showModal = true;
  }

  async function salvar() {
    if (!form.nome.trim()) { toast.error('O nome é obrigatório.'); return; }
    const payload = { ...form, unidadeId: form.unidadeId ? Number(form.unidadeId) : null };
    try {
      if (editando) {
        await putApi(`profissionais/${editando.id}`, payload);
        toast.success('Profissional atualizado.');
      } else {
        await postApi('profissionais', payload);
        toast.success('Profissional cadastrado.');
      }
      showModal = false;
      await lista.recarregar();
    } catch {
      toast.error('Erro ao salvar profissional.');
    }
  }

  async function toggleAtivo(p) {
    try {
      await patchApi(`profissionais/${p.id}/status`);
      toast.success(p.ativo ? 'Profissional desativado.' : 'Profissional ativado.');
      await lista.recarregar();
    } catch {
      toast.error('Erro ao alterar status.');
    }
  }

  async function deletar(p) {
    if (!confirm(`Deseja excluir o profissional "${p.nome}"?`)) return;
    try {
      await deleteByIdApi(`profissionais/${p.id}`);
      toast.success('Profissional excluído.');
      await lista.recarregar();
    } catch {
      toast.error('Erro ao excluir profissional.');
    }
  }

  const textoContador = $derived(
    textoDoContadorSimples({
      pagina: lista.paginacao.pagina,
      tamanho: lista.paginacao.tamanho,
      totalElementos: lista.totalElementos,
      filtrado: lista.filtrado,
      singular: 'profissional',
      plural: 'profissionais',
      vazio: 'Nenhum profissional cadastrado'
    })
  );

  // Uma tag por filtro ativo; remover a tag é zerar a chave.
  const tags = $derived(
    [
      lista.filtros.q && { chave: 'q', rotulo: `Busca: ${lista.filtros.q}` },
      lista.filtros.ativo && {
        chave: 'ativo',
        rotulo: `Status: ${lista.filtros.ativo === 'true' ? 'Ativo' : 'Inativo'}`
      },
      lista.filtros.unidadeId && {
        chave: 'unidadeId',
        rotulo: `Unidade: ${unidades.find((u) => String(u.id) === lista.filtros.unidadeId)?.nome ?? `#${lista.filtros.unidadeId}`}`
      }
    ].filter(Boolean)
  );
</script>

<Content titleH1="Profissionais Solicitantes" page="/admin/profissionais">
  <div class="tela-ibm flex-1 min-w-0 p-4 md:p-6 space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-4">
      <h2 class="ibm-titulo-secao">Profissionais</h2>
      <button type="button" onclick={abrirModalNovo} class="ibm-btn ibm-btn--primario">+ Novo Profissional</button>
    </div>

    <section aria-label="Filtros da lista de profissionais" class="ibm-painel p-4 md:p-6 space-y-4">
      <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div class="min-w-0 sm:col-span-2">
          <label for="filtro-busca" class="ibm-rotulo">Buscar</label>
          <input
            id="filtro-busca"
            type="search"
            value={lista.busca}
            maxlength="80"
            autocomplete="off"
            placeholder="Nome, conselho, registro ou especialidade"
            oninput={(e) => lista.definirBusca(e.currentTarget.value)}
            class="ibm-campo w-full"
          />
        </div>
        <div class="min-w-0">
          <label for="filtro-status" class="ibm-rotulo">Status</label>
          <select
            id="filtro-status"
            value={lista.filtros.ativo}
            onchange={(e) => lista.definirFiltro('ativo', e.currentTarget.value)}
            class="ibm-campo w-full"
          >
            <option value="">Todos os status</option>
            <option value="true">Ativo</option>
            <option value="false">Inativo</option>
          </select>
        </div>
        <div class="min-w-0">
          <label for="filtro-unidade" class="ibm-rotulo">Unidade</label>
          <select
            id="filtro-unidade"
            value={lista.filtros.unidadeId}
            onchange={(e) => lista.definirFiltro('unidadeId', e.currentTarget.value)}
            class="ibm-campo w-full"
          >
            <option value="">Todas as unidades</option>
            {#if lista.filtros.unidadeId && !unidades.some((u) => String(u.id) === lista.filtros.unidadeId)}
              <option value={lista.filtros.unidadeId}>Unidade #{lista.filtros.unidadeId}</option>
            {/if}
            {#each unidades as u (u.id)}
              <option value={String(u.id)}>{u.nome}</option>
            {/each}
          </select>
        </div>
      </div>

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
        {lista.falhou ? 'Não foi possível carregar os profissionais.' : 'Carregando profissionais…'}
      </p>
    {:else}
      <div>
        <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
        <div
          class="ibm-tabela-contorno"
          class:ibm-carregando={lista.carregando}
          aria-busy={lista.carregando}
          role="region"
          aria-label="Lista de profissionais, com rolagem horizontal"
          tabindex="0"
        >
          <table class="ibm-tabela w-full">
            <thead>
              <tr>
                <th scope="col" class="text-left">Nome</th>
                <th scope="col" class="text-left">Conselho / Registro</th>
                <th scope="col" class="text-left">Especialidade</th>
                <th scope="col" class="text-left">Unidade</th>
                <th scope="col" class="text-left">Status</th>
                <th scope="col" class="text-left">Ações</th>
              </tr>
            </thead>
            <tbody>
              {#each lista.itens as p (p.id)}
                <tr>
                  <td class="ibm-titular">{p.nome}</td>
                  <td>{p.conselho || '—'} {p.numeroRegistro || ''}</td>
                  <td>{p.especialidadeAtuacao || '—'}</td>
                  <td>{p.unidadeNome || '—'}</td>
                  <td>
                    <span class="ibm-badge {p.ativo ? 'ibm-badge--ok' : 'ibm-badge--erro'}">
                      {p.ativo ? 'Ativo' : 'Inativo'}
                    </span>
                  </td>
                  <td>
                    <div class="flex flex-wrap gap-2">
                      <button type="button" onclick={() => abrirModalEditar(p)} aria-label="Editar {p.nome}" class="ibm-btn ibm-btn--terciario">
                        Editar
                      </button>
                      <button type="button" onclick={() => toggleAtivo(p)} aria-label="{p.ativo ? 'Desativar' : 'Ativar'} {p.nome}"
                        class="ibm-btn {p.ativo ? 'ibm-btn--perigo' : 'ibm-btn--terciario'}">
                        {p.ativo ? 'Desativar' : 'Ativar'}
                      </button>
                      <button type="button" onclick={() => deletar(p)} aria-label="Excluir {p.nome}" class="ibm-btn ibm-btn--perigo">
                        Excluir
                      </button>
                    </div>
                  </td>
                </tr>
              {:else}
                <tr class="ibm-linha-vazia">
                  <td colspan="6" class="ibm-vazio">
                    {#if lista.filtrado}
                      Nenhum profissional corresponde aos filtros.
                      <button type="button" onclick={() => lista.limparFiltros()} class="ibm-btn ibm-btn--fantasma ml-1">
                        Limpar filtros
                      </button>
                    {:else}
                      Nenhum profissional cadastrado.
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

{#if showModal}
  <div class="tela-ibm ibm-modal-fundo" role="presentation">
    <div class="ibm-modal ibm-painel" role="dialog" aria-modal="true" aria-labelledby="modal-titulo">
      <h2 id="modal-titulo" class="ibm-titulo-secao">{editando ? 'Editar Profissional' : 'Novo Profissional'}</h2>

      <div class="space-y-3">
        <div>
          <label for="prof-nome" class="ibm-rotulo">Nome *</label>
          <input id="prof-nome" bind:value={form.nome} class="ibm-campo w-full" />
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <div>
            <label for="prof-conselho" class="ibm-rotulo">Conselho (CRM, COREN…)</label>
            <input id="prof-conselho" bind:value={form.conselho} class="ibm-campo w-full" />
          </div>
          <div>
            <label for="prof-registro" class="ibm-rotulo">Nº Registro</label>
            <input id="prof-registro" bind:value={form.numeroRegistro} class="ibm-campo w-full" />
          </div>
        </div>
        <div>
          <label for="prof-especialidade" class="ibm-rotulo">Especialidade de Atuação</label>
          <input id="prof-especialidade" bind:value={form.especialidadeAtuacao} class="ibm-campo w-full" />
        </div>
        <div>
          <label for="prof-telefone" class="ibm-rotulo">Telefone</label>
          <input id="prof-telefone" bind:value={form.telefone} class="ibm-campo w-full" />
        </div>
        <div>
          <label for="prof-unidade" class="ibm-rotulo">Unidade</label>
          <select id="prof-unidade" bind:value={form.unidadeId} class="ibm-campo w-full">
            <option value={null}>— Nenhuma —</option>
            {#each unidades as u (u.id)}
              <option value={u.id}>{u.nome}</option>
            {/each}
          </select>
        </div>
      </div>

      <div class="flex flex-col-reverse sm:flex-row sm:justify-end gap-3 pt-2">
        <button type="button" onclick={() => (showModal = false)} class="ibm-btn ibm-btn--secundario">Cancelar</button>
        <button type="button" onclick={salvar} class="ibm-btn ibm-btn--primario">Salvar</button>
      </div>
    </div>
  </div>
{/if}

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
  .ibm-btn--secundario {
    border-color: var(--ibm-tinta);
    background: transparent;
    color: var(--ibm-tinta);
  }
  .ibm-btn--secundario:hover {
    background: var(--ibm-tinta);
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
  .ibm-suave-texto {
    color: var(--ibm-tinta-suave);
    font-size: 14px;
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
  .ibm-tabela tbody tr:last-child td {
    border-bottom: 0;
  }
  .ibm-tabela tbody tr:hover > td {
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
  .ibm-badge--ok {
    background: var(--ibm-ok-fundo);
    color: var(--ibm-ok-texto);
  }
  .ibm-badge--erro {
    background: var(--ibm-erro-fundo);
    color: var(--ibm-erro-texto);
  }
  /* Modal de cadastro/edição (profissionais) */
  .ibm-modal-fundo {
    position: fixed;
    inset: 0;
    z-index: 50;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 16px;
    background: rgba(22, 22, 22, 0.6);
    font-family: var(--ibm-fonte);
  }
  .ibm-modal {
    width: 100%;
    max-width: 448px;
    max-height: calc(100vh - 32px);
    overflow-y: auto;
    padding: 24px;
    display: flex;
    flex-direction: column;
    gap: 16px;
    color: var(--ibm-tinta);
    font-size: 14px;
    line-height: 1.29;
    letter-spacing: 0.16px;
  }
</style>
