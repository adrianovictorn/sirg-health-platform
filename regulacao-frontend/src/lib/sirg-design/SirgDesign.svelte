<script>
    /**
     * sirg-design — fonte única do visual "mais SIRG" (mistura SIRG + Carbon/IBM,
     * nível de /atendimentos, ADR 0009) para as telas que o adotam pela casca
     * (ADR 0012: telas de Unidade). Envolva o conteúdo da página, DENTRO de <Content>:
     *
     *   <Content ...><SirgDesign> ... </SirgDesign></Content>
     *
     * Por que `:global` aqui (única exceção autorizada pelo ADR 0012): o bloco de estilo de um
     * componente Svelte não alcança o markup que outro componente renderiza. Toda regra
     * é presa à raiz `.sirg-design`, que leva o hash deste componente — o estilo só
     * vale DENTRO da casca e não vaza para outras telas.
     *
     * Classes com prefixo `sd-` e tokens `--sd-*`: não reaproveite `ibm-*`/`at-*`/
     * `cotas-ibm`, que continuam copiados nas 7 telas mistas (migração em pedido à
     * parte). Valores copiados de src/routes/atendimentos/+page.svelte (bloco de estilo).
     * A fonte é uma pilha com fallback: nada é baixado.
     */
    let { children } = $props();
</script>

<div class="sirg-design">
    {@render children?.()}
</div>

<style>
    .sirg-design {
        --sd-acento: #047857;
        --sd-acento-hover: #065f46;
        --sd-acento-ativo: #064e3b;
        --sd-fonte: 'IBM Plex Sans', 'Helvetica Neue', Arial, sans-serif;

        --sd-tinta: #161616;
        --sd-tinta-suave: #525252;
        --sd-fundo: #f3f4f6;
        --sd-camada: #ffffff;
        --sd-campo: #f4f4f4;
        --sd-linha: #e0e0e0;
        --sd-borda-campo: #8c8c8c;
        --sd-hover: #ececec;

        --sd-raio-painel: 8px;
        --sd-sombra-painel: 0 1px 3px 0 rgb(0 0 0 / 0.1), 0 1px 2px -1px rgb(0 0 0 / 0.1);
        --sd-raio-controle: 6px;
        --sd-raio-campo: 0;
        --sd-titulo-cor: #065f46;
        --sd-titulo-peso: 700;
        --sd-titulo-tamanho: 18px;

        /* Mais SIRG: emerald nas superfícies */
        --sd-sirg-50: #ecfdf5;
        --sd-sirg-100: #d1fae5;
        --sd-sirg-200: #a7f3d0;
        --sd-sirg-600: #059669;
        --sd-sirg-700: #047857;
        --sd-sirg-800: #065f46;
        --sd-sirg-900: #064e3b;
        --sd-topo-fundo: var(--sd-sirg-50);
        --sd-topo-divisor: 1px solid var(--sd-sirg-200);
        --sd-tabela-cabecalho: var(--sd-sirg-100);
        --sd-tabela-cabecalho-texto: var(--sd-sirg-900);
        --sd-tabela-cabecalho-linha: 2px solid var(--sd-sirg-600);
        --sd-linha-hover: var(--sd-sirg-50);
        --sd-rotulo-cor: var(--sd-sirg-800);
        --sd-rotulo-peso: 600;
        --sd-secundario-borda: var(--sd-sirg-700);
        --sd-secundario-texto: var(--sd-sirg-800);
        --sd-secundario-hover: var(--sd-sirg-50);

        /* Situação (texto + marca; 3:1 sobre branco) — cores já em uso */
        --sd-ok-cor: #047857;
        --sd-ok-fundo: #d1fae5;
        --sd-info-cor: #1d4ed8;
        --sd-info-fundo: #dbeafe;
        --sd-aviso-cor: #b45309;
        --sd-aviso-texto: #78350f;
        --sd-aviso-fundo: #fffbeb;
        --sd-erro-cor: #b91c1c;
        --sd-erro-texto: #991b1b;
        --sd-erro-fundo: #fef2f2;
        --sd-neutro-cor: #525252;
        --sd-neutro-fundo: #f3f4f6;

        /* Ocupa a altura do <main> flex-col do Content (sem faixa de outro cinza abaixo). */
        flex: 1 1 auto;
        min-width: 0;
        background: var(--sd-fundo);
        color: var(--sd-tinta);
        font-family: var(--sd-fonte);
        font-size: 14px;
        line-height: 1.29;
        letter-spacing: 0.16px;
    }

    /* Painel com faixa de topo emerald */
    .sirg-design :global(.sd-painel) {
        overflow: hidden;
        border-radius: var(--sd-raio-painel);
        background: var(--sd-camada);
        box-shadow: var(--sd-sombra-painel);
    }
    .sirg-design :global(.sd-painel-topo) {
        display: flex;
        flex-wrap: wrap;
        align-items: baseline;
        justify-content: space-between;
        gap: 4px 16px;
        padding: 12px 16px;
        border-bottom: var(--sd-topo-divisor);
        background: var(--sd-topo-fundo);
    }
    .sirg-design :global(.sd-painel-corpo) {
        padding: 16px;
    }
    .sirg-design :global(.sd-titulo) {
        font-size: var(--sd-titulo-tamanho);
        font-weight: var(--sd-titulo-peso);
        line-height: 1.4;
        color: var(--sd-titulo-cor);
    }
    .sirg-design :global(.sd-nota) {
        font-size: 12px;
        line-height: 1.33;
        letter-spacing: 0.32px;
        color: var(--sd-tinta-suave);
    }
    .sirg-design :global(.sd-texto-apoio) {
        font-size: 14px;
        line-height: 1.43;
        color: var(--sd-tinta-suave);
    }
    .sirg-design :global(.sd-contador) {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        min-width: 28px;
        min-height: 22px;
        padding: 0 8px;
        border-radius: 9999px;
        background: var(--sd-sirg-700);
        color: #ffffff;
        font-size: 12px;
        font-weight: 600;
        font-variant-numeric: tabular-nums;
    }

    /* Cabeçalho da tela: faixa emerald clara com filete */
    .sirg-design :global(.sd-cabecalho) {
        display: flex;
        flex-wrap: wrap;
        align-items: flex-end;
        justify-content: space-between;
        gap: 16px 24px;
        padding: 16px;
        border: 1px solid var(--sd-sirg-200);
        border-left: 6px solid var(--sd-sirg-700);
        border-radius: var(--sd-raio-painel);
        background: var(--sd-sirg-50);
    }

    /* Rótulo e campo: campo IBM (cinza, linha inferior), rótulo na cor do SIRG */
    .sirg-design :global(.sd-campo-grupo) {
        display: flex;
        flex-direction: column;
        min-width: 0;
    }
    .sirg-design :global(.sd-rotulo) {
        display: block;
        margin-bottom: 8px;
        font-size: 12px;
        font-weight: var(--sd-rotulo-peso);
        line-height: 1.33;
        letter-spacing: 0.32px;
        color: var(--sd-rotulo-cor);
    }
    .sirg-design :global(.sd-campo) {
        width: 100%;
        min-height: 40px;
        padding: 0 16px;
        border: 1px solid transparent;
        border-bottom: 1px solid var(--sd-borda-campo);
        border-radius: var(--sd-raio-campo);
        background-color: var(--sd-campo);
        box-shadow: none;
        color: var(--sd-tinta);
        font-family: inherit;
        font-size: 14px;
        letter-spacing: 0.16px;
    }
    .sirg-design :global(select.sd-campo) {
        padding-right: 40px;
    }
    .sirg-design :global(.sd-campo::placeholder) {
        color: var(--sd-tinta-suave);
        opacity: 1;
    }
    .sirg-design :global(.sd-campo:hover:not(:disabled)) {
        border-bottom-color: var(--sd-sirg-700);
    }
    .sirg-design :global(.sd-campo:focus),
    .sirg-design :global(.sd-campo:focus-visible) {
        outline: 2px solid var(--sd-acento);
        outline-offset: -2px;
        border-color: transparent;
        border-bottom-color: var(--sd-borda-campo);
        box-shadow: none;
    }
    .sirg-design :global(.sd-campo[aria-invalid='true']) {
        border-bottom: 2px solid var(--sd-erro-cor);
    }
    .sirg-design :global(.sd-campo:disabled) {
        opacity: 0.55;
        cursor: not-allowed;
    }
    .sirg-design :global(.sd-ajuda) {
        margin-top: 4px;
        font-size: 12px;
        line-height: 1.33;
        letter-spacing: 0.32px;
        color: var(--sd-tinta-suave);
    }

    /* Caixa de seleção na cor do acento */
    .sirg-design :global(.sd-check) {
        width: 16px;
        height: 16px;
        accent-color: var(--sd-acento);
    }

    /* Botões */
    .sirg-design :global(.sd-btn) {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 6px;
        min-height: 40px;
        padding: 0 16px;
        border: 1px solid transparent;
        border-radius: var(--sd-raio-controle);
        font-family: inherit;
        font-size: 14px;
        font-weight: 600;
        letter-spacing: 0.16px;
        white-space: nowrap;
        cursor: pointer;
        touch-action: manipulation;
    }
    .sirg-design :global(.sd-btn:focus-visible) {
        outline: 2px solid var(--sd-acento);
        outline-offset: 2px;
    }
    .sirg-design :global(.sd-btn--compacto) {
        min-height: 32px;
        padding: 0 12px;
        font-size: 13px;
    }
    .sirg-design :global(.sd-btn--primario) {
        background: var(--sd-acento);
        color: #ffffff;
    }
    .sirg-design :global(.sd-btn--primario:hover:not(:disabled)) {
        background: var(--sd-acento-hover);
    }
    .sirg-design :global(.sd-btn--primario:active:not(:disabled)) {
        background: var(--sd-acento-ativo);
    }
    .sirg-design :global(.sd-btn--secundario) {
        border-color: var(--sd-secundario-borda);
        background: var(--sd-camada);
        color: var(--sd-secundario-texto);
    }
    .sirg-design :global(.sd-btn--secundario:hover:not(:disabled)) {
        background: var(--sd-secundario-hover);
    }
    .sirg-design :global(.sd-btn--neutro) {
        border-color: var(--sd-borda-campo);
        background: var(--sd-camada);
        color: #374151;
    }
    .sirg-design :global(.sd-btn--neutro:hover:not(:disabled)) {
        background: var(--sd-hover);
    }
    .sirg-design :global(.sd-btn--perigo) {
        border-color: var(--sd-erro-cor);
        background: var(--sd-camada);
        color: var(--sd-erro-texto);
    }
    .sirg-design :global(.sd-btn--perigo:hover:not(:disabled)) {
        background: #fee2e2;
    }
    .sirg-design :global(.sd-btn:disabled) {
        border-color: transparent;
        background: var(--sd-linha);
        color: var(--sd-tinta-suave);
        cursor: not-allowed;
    }

    /* Avisos (texto + filete; nunca só cor) */
    .sirg-design :global(.sd-aviso) {
        padding: 12px 16px;
        border-left: 4px solid var(--sd-aviso-cor);
        border-radius: 0 var(--sd-raio-controle) var(--sd-raio-controle) 0;
        background: var(--sd-aviso-fundo);
        color: var(--sd-aviso-texto);
        font-size: 14px;
        line-height: 1.43;
    }
    .sirg-design :global(.sd-erro) {
        padding: 12px 16px;
        border-left: 4px solid var(--sd-erro-cor);
        border-radius: 0 var(--sd-raio-controle) var(--sd-raio-controle) 0;
        background: var(--sd-erro-fundo);
        color: var(--sd-erro-texto);
        font-size: 14px;
        line-height: 1.43;
    }
    .sirg-design :global(.sd-vazio) {
        padding: 24px;
        border: 1px dashed var(--sd-borda-campo);
        border-radius: var(--sd-raio-controle);
        background: var(--sd-fundo);
        color: var(--sd-tinta-suave);
        text-align: center;
    }

    /* Tabela: cabeçalho na cor do módulo, hover emerald claro */
    .sirg-design :global(.sd-tabela-contorno) {
        overflow-x: auto;
    }
    .sirg-design :global(.sd-tabela) {
        width: 100%;
        border-collapse: collapse;
        font-size: 14px;
        text-align: left;
    }
    .sirg-design :global(.sd-tabela thead th) {
        padding: 8px 12px;
        border-bottom: var(--sd-tabela-cabecalho-linha);
        background: var(--sd-tabela-cabecalho);
        color: var(--sd-tabela-cabecalho-texto);
        font-size: 12px;
        font-weight: 600;
        line-height: 1.33;
        letter-spacing: 0.32px;
        white-space: nowrap;
    }
    .sirg-design :global(.sd-tabela tbody th),
    .sirg-design :global(.sd-tabela tbody td) {
        padding: 8px 12px;
        border-bottom: 1px solid var(--sd-linha);
        vertical-align: middle;
        color: var(--sd-tinta);
    }
    .sirg-design :global(.sd-tabela tbody th) {
        font-weight: 600;
    }
    .sirg-design :global(.sd-tabela tbody tr:last-child > th),
    .sirg-design :global(.sd-tabela tbody tr:last-child > td) {
        border-bottom: 0;
    }
    .sirg-design :global(.sd-tabela tbody tr:hover > th),
    .sirg-design :global(.sd-tabela tbody tr:hover > td) {
        background: var(--sd-linha-hover);
    }

    /* Selo de situação: texto + marca de forma (círculo cheio = ok; vazado = neutro;
       losango = aviso; quadrado = erro). */
    .sirg-design :global(.sd-selo) {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        padding: 2px 8px;
        border-radius: 9999px;
        font-size: 12px;
        font-weight: 600;
        line-height: 1.5;
        white-space: nowrap;
    }
    .sirg-design :global(.sd-selo::before) {
        content: '';
        flex: none;
        width: 8px;
        height: 8px;
        background: currentColor;
    }
    .sirg-design :global(.sd-selo--ok) {
        background: var(--sd-ok-fundo);
        color: var(--sd-sirg-800);
    }
    .sirg-design :global(.sd-selo--ok::before) {
        border-radius: 9999px;
    }
    .sirg-design :global(.sd-selo--neutro) {
        background: var(--sd-neutro-fundo);
        color: var(--sd-neutro-cor);
    }
    .sirg-design :global(.sd-selo--neutro::before) {
        border: 2px solid currentColor;
        border-radius: 9999px;
        background: transparent;
    }
    .sirg-design :global(.sd-selo--aviso) {
        background: var(--sd-aviso-fundo);
        color: var(--sd-aviso-texto);
    }
    .sirg-design :global(.sd-selo--aviso::before) {
        transform: rotate(45deg) scale(0.85);
    }
    .sirg-design :global(.sd-selo--erro) {
        background: var(--sd-erro-fundo);
        color: var(--sd-erro-texto);
    }
</style>
