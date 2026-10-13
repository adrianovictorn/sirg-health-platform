<script>
  // Painel de especialidades de um grupo expandido — companheiro de
  // `GrupoToggleButton.svelte`. Renderizado por quem chama SÓ enquanto o
  // grupo está aberto (dentro de um `{#if}`), então o estado de paginação
  // interna (`pagina`) começa sempre em 1 a cada abertura, sem precisar de
  // lógica extra pra resetar.
  //
  // Formato de mini-tabela com cabeçalho, só com a coluna "Especialidade /
  // Exame": uma cota de grupo tem saldo ÚNICO COMPARTILHADO entre todas as
  // especialidades (ver CotaUnidadeService) — não existe período/utilizada/
  // total/saldo por especialidade, só o agregado do grupo inteiro, que já
  // aparece na linha da cota. Mostrar essas colunas vazias por especialidade
  // sugeriria um saldo individual que não existe.
  export let grupo = null; // { id, nome, especialidades: [{ id, nome }] } — vem de GET /grupo-relatorio/listar
  export let itensPorPagina = 10;
  export let linkVerTudo = null; // href opcional de "ver na tela de Cotas da Unidade"; sempre visível quando informado
  export let escuro = false; // /admin/cotas usa tema escuro (slate); os outros 2 usos ficam com o padrão claro
  // 'padrao' mantém o visual atual. 'ibm' (/admin/cotas) lê as variáveis --ibm-* herdadas da tela.
  export let variante = 'padrao';

  let pagina = 1;

  $: especialidades = grupo?.especialidades ?? [];
  $: totalPaginas = Math.max(1, Math.ceil(especialidades.length / itensPorPagina));
  $: paginaAtual = Math.min(pagina, totalPaginas);
  $: exibidas = especialidades.slice((paginaAtual - 1) * itensPorPagina, paginaAtual * itensPorPagina);
  // Até 7 páginas mostra todos os números; acima disso, os 5 primeiros + "…" pro final.
  $: numerosDePagina = totalPaginas <= 7
    ? Array.from({ length: totalPaginas }, (_, i) => i + 1)
    : [1, 2, 3, 4, 5];
</script>

{#if grupo && variante === 'ibm'}
  <div class="pi-painel">
    <div class="pi-titulo">Especialidade / Exame</div>

    <div>
      {#each exibidas as e, i (e.id)}
        <div class="pi-item {i % 2 === 1 ? 'pi-item--zebra' : ''}">{e.nome}</div>
      {:else}
        <div class="pi-item pi-item--vazio">Nenhuma especialidade vinculada a este grupo ainda.</div>
      {/each}
    </div>

    {#if totalPaginas > 1}
      <div class="pi-paginacao">
        <button
          type="button"
          on:click={() => (pagina = paginaAtual - 1)}
          disabled={paginaAtual === 1}
          class="pi-pag"
          aria-label="Página anterior"
        >‹</button>
        {#each numerosDePagina as n (n)}
          <button
            type="button"
            on:click={() => (pagina = n)}
            class="pi-pag {n === paginaAtual ? 'pi-pag--ativa' : ''}"
            aria-label="Página {n}"
            aria-current={n === paginaAtual ? 'page' : undefined}
          >{n}</button>
        {/each}
        {#if totalPaginas > 7}
          <span class="pi-reticencias">…</span>
          <button
            type="button"
            on:click={() => (pagina = totalPaginas)}
            class="pi-pag {totalPaginas === paginaAtual ? 'pi-pag--ativa' : ''}"
            aria-label="Página {totalPaginas}"
            aria-current={totalPaginas === paginaAtual ? 'page' : undefined}
          >{totalPaginas}</button>
        {/if}
        <button
          type="button"
          on:click={() => (pagina = paginaAtual + 1)}
          disabled={paginaAtual === totalPaginas}
          class="pi-pag"
          aria-label="Próxima página"
        >›</button>
      </div>
    {/if}

    {#if linkVerTudo}
      <div class="pi-rodape">
        <a href={linkVerTudo} class="pi-link">Ver na tela de Cotas da Unidade</a>
      </div>
    {/if}
  </div>
{:else if grupo}
  <div class="text-xs rounded-lg border overflow-hidden {escuro ? 'border-slate-700' : 'border-gray-200'}">
    <div
      class="px-3 py-2 text-[11px] font-semibold uppercase tracking-wider {escuro
        ? 'text-slate-400 bg-slate-800/70'
        : 'text-gray-500 bg-gray-100'}"
    >
      Especialidade / Exame
    </div>

    <div>
      {#each exibidas as e, i (e.id)}
        <div
          class="px-3 py-1.5 {escuro ? 'text-slate-300' : 'text-gray-700'}
                 {i % 2 === 1 ? (escuro ? 'bg-slate-800/40' : 'bg-gray-50') : (escuro ? 'bg-slate-900' : 'bg-white')}"
        >
          {e.nome}
        </div>
      {:else}
        <div class="px-3 py-1.5 {escuro ? 'text-slate-500 bg-slate-900' : 'text-gray-400 bg-white'}">
          Nenhuma especialidade vinculada a este grupo ainda.
        </div>
      {/each}
    </div>

    {#if totalPaginas > 1}
      <div class="flex items-center gap-1 px-3 py-2 border-t {escuro ? 'border-slate-700' : 'border-gray-200'}">
        <button
          type="button"
          on:click={() => (pagina = paginaAtual - 1)}
          disabled={paginaAtual === 1}
          class="w-5 h-5 flex items-center justify-center rounded disabled:opacity-30 disabled:hover:bg-transparent
                 {escuro ? 'text-slate-400 hover:bg-slate-700' : 'text-gray-500 hover:bg-gray-100'}"
          aria-label="Página anterior"
        >‹</button>
        {#each numerosDePagina as n (n)}
          <button
            type="button"
            on:click={() => (pagina = n)}
            class="w-5 h-5 flex items-center justify-center rounded
                   {n === paginaAtual ? 'bg-emerald-600 text-white' : (escuro ? 'text-slate-400 hover:bg-slate-700' : 'text-gray-500 hover:bg-gray-100')}"
          >{n}</button>
        {/each}
        {#if totalPaginas > 7}
          <span class="px-0.5 {escuro ? 'text-slate-500' : 'text-gray-400'}">…</span>
          <button
            type="button"
            on:click={() => (pagina = totalPaginas)}
            class="w-5 h-5 flex items-center justify-center rounded
                   {totalPaginas === paginaAtual ? 'bg-emerald-600 text-white' : (escuro ? 'text-slate-400 hover:bg-slate-700' : 'text-gray-500 hover:bg-gray-100')}"
          >{totalPaginas}</button>
        {/if}
        <button
          type="button"
          on:click={() => (pagina = paginaAtual + 1)}
          disabled={paginaAtual === totalPaginas}
          class="w-5 h-5 flex items-center justify-center rounded disabled:opacity-30 disabled:hover:bg-transparent
                 {escuro ? 'text-slate-400 hover:bg-slate-700' : 'text-gray-500 hover:bg-gray-100'}"
          aria-label="Próxima página"
        >›</button>
      </div>
    {/if}

    {#if linkVerTudo}
      <div class="px-3 py-2 border-t {escuro ? 'border-slate-700' : 'border-gray-200'}">
        <a href={linkVerTudo} class="hover:underline {escuro ? 'text-emerald-400' : 'text-emerald-700'}">
          Ver na tela de Cotas da Unidade
        </a>
      </div>
    {/if}
  </div>
{/if}

<style>
  .pi-painel {
    border: 1px solid var(--ibm-linha, #e0e0e0);
    border-radius: 0;
    background: var(--ibm-camada, #ffffff);
    font-size: 14px;
    letter-spacing: 0.16px;
  }
  .pi-titulo {
    padding: 8px 16px;
    background: var(--ibm-cabecalho, #e0e0e0);
    color: var(--ibm-tinta, #161616);
    font-size: 14px;
    font-weight: 600;
  }
  .pi-item {
    padding: 8px 16px;
    background: var(--ibm-camada, #ffffff);
    color: var(--ibm-tinta, #161616);
  }
  .pi-item--zebra {
    background: var(--ibm-fundo, #f4f4f4);
  }
  .pi-item--vazio {
    color: var(--ibm-tinta-suave, #525252);
  }
  .pi-paginacao {
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 8px 16px;
    border-top: 1px solid var(--ibm-linha, #e0e0e0);
  }
  .pi-pag {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 28px;
    height: 28px;
    border-radius: 0;
    color: var(--ibm-tinta, #161616);
    font-size: 14px;
    cursor: pointer;
  }
  .pi-pag:hover:not(:disabled) {
    background: var(--ibm-linha, #e0e0e0);
  }
  .pi-pag:disabled {
    color: var(--ibm-borda-campo, #8c8c8c);
    cursor: not-allowed;
  }
  .pi-pag--ativa,
  .pi-pag--ativa:hover:not(:disabled) {
    background: var(--ibm-acento, #047857);
    color: #ffffff;
  }
  .pi-pag:focus-visible,
  .pi-link:focus-visible {
    outline: 2px solid var(--ibm-acento, #047857);
    outline-offset: 2px;
  }
  .pi-reticencias {
    padding: 0 4px;
    color: var(--ibm-tinta-suave, #525252);
  }
  .pi-rodape {
    padding: 8px 16px;
    border-top: 1px solid var(--ibm-linha, #e0e0e0);
  }
  .pi-link {
    color: var(--ibm-acento, #047857);
    text-decoration: underline;
    text-underline-offset: 3px;
  }
</style>
