<script>
  /**
   * Rodapé de paginação no padrão IBM (Carbon): "Itens por página", intervalo
   * exibido e Anterior/Próxima. Não busca dados: só emite `onPagina` e `onTamanho`.
   * `pagina` é 0-based. Cores vêm de custom properties herdadas da tela (--ibm-*),
   * com fallback, para funcionar dentro de qualquer casca sem `:global`.
   */
  import { TAMANHOS_DE_PAGINA } from '$lib/listaPaginada.js';

  let {
    pagina,
    totalPaginas,
    tamanho,
    tamanhos = TAMANHOS_DE_PAGINA,
    rotulo = 'Itens por página',
    desabilitado = false,
    onPagina,
    onTamanho
  } = $props();

  const ultima = $derived(Math.max(totalPaginas - 1, 0));
  const temAnterior = $derived(pagina > 0);
  const temProxima = $derived(pagina < ultima);
</script>

<nav class="paginacao" aria-label="Paginação">
  <div class="paginacao__tamanho">
    <label for="paginacao-tamanho">{rotulo}</label>
    <select
      id="paginacao-tamanho"
      value={String(tamanho)}
      disabled={desabilitado}
      onchange={(e) => onTamanho(Number(e.currentTarget.value))}
    >
      {#each tamanhos as opcao (opcao)}
        <option value={String(opcao)}>{opcao}</option>
      {/each}
    </select>
  </div>

  <p class="paginacao__posicao" aria-live="polite">
    Página <strong>{Math.min(pagina + 1, Math.max(totalPaginas, 1))}</strong> de <strong>{Math.max(totalPaginas, 1)}</strong>
  </p>

  <div class="paginacao__botoes">
    <button type="button" disabled={desabilitado || !temAnterior} onclick={() => onPagina(pagina - 1)}>
      Anterior
    </button>
    <button type="button" disabled={desabilitado || !temProxima} onclick={() => onPagina(pagina + 1)}>
      Próxima
    </button>
  </div>
</nav>

<style>
  .paginacao {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: space-between;
    gap: 12px 24px;
    min-height: 48px;
    padding: 0 0 0 16px;
    border: 1px solid var(--ibm-linha, #e0e0e0);
    border-top: 0;
    background: var(--ibm-camada, #ffffff);
    color: var(--ibm-tinta, #161616);
    font-size: 14px;
    letter-spacing: 0.16px;
  }
  .paginacao__tamanho {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .paginacao__tamanho label,
  .paginacao__posicao {
    color: var(--ibm-tinta-suave, #525252);
    margin: 0;
  }
  .paginacao select {
    min-height: 32px;
    padding: 0 32px 0 12px;
    border: 1px solid transparent;
    border-bottom: 1px solid var(--ibm-borda-campo, #8c8c8c);
    border-radius: 0;
    background-color: var(--ibm-campo, #f4f4f4);
    color: inherit;
    font: inherit;
  }
  .paginacao select:focus-visible,
  .paginacao button:focus-visible {
    outline: 2px solid var(--ibm-acento, #047857);
    outline-offset: -2px;
  }
  .paginacao__botoes {
    display: flex;
    align-self: stretch;
  }
  .paginacao button {
    min-width: 96px;
    padding: 0 16px;
    border: 0;
    border-left: 1px solid var(--ibm-linha, #e0e0e0);
    border-radius: 0;
    background: transparent;
    color: var(--ibm-acento, #047857);
    font: inherit;
    cursor: pointer;
  }
  .paginacao button:hover:not(:disabled) {
    background: var(--ibm-hover, #ececec);
  }
  .paginacao button:disabled {
    color: var(--ibm-tinta-suave, #525252);
    opacity: 0.5;
    cursor: not-allowed;
  }

  @media (max-width: 640px) {
    .paginacao {
      padding-left: 0;
    }
    .paginacao__tamanho,
    .paginacao__posicao {
      padding-left: 16px;
    }
    .paginacao select {
      min-height: 40px;
    }
    .paginacao__tamanho {
      padding-top: 8px;
    }
    .paginacao__posicao {
      padding-bottom: 4px;
    }
    .paginacao__botoes {
      width: 100%;
    }
    .paginacao button {
      flex: 1;
      min-height: 48px;
      border-top: 1px solid var(--ibm-linha, #e0e0e0);
    }
  }
</style>
