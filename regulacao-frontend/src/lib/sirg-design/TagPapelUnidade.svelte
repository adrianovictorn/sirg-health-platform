<script>
    /**
     * Tag de papel da unidade (ADR 0012) — só exibição. Recebe o resumo normalizado
     * por `resumoDaLista`/`resumoDaParametrizacao` ($lib/papelUnidade.js).
     *
     * Texto + marca de forma, nunca só cor:
     *   Unidade solicitante -> círculo vazado (cinza)
     *   Unidade executante  -> quadrado (emerald)
     *   Ambas               -> círculo cheio (emerald)
     * e, quando houver, o indicador "parametrização incompleta" (losango âmbar).
     *
     * Cores via custom properties do sirg-design (atravessam componentes sem
     * `:global`); fora da casca usa os mesmos valores como fallback.
     */
    import { papelDaUnidade, parametrizacaoIncompleta } from '$lib/papelUnidade.js';

    let { resumo } = $props();

    const papel = $derived(papelDaUnidade(resumo));
    const incompleta = $derived(parametrizacaoIncompleta(resumo));
</script>

<span class="tag-papel-grupo">
    <span class="tag-papel" data-papel={papel.papel}>{papel.rotulo}</span>
    {#if incompleta}
        <span class="tag-papel tag-papel--incompleta" title="Há vínculos, mas falta local ou grupo/especialidade para a executante valer.">
            Parametrização incompleta
        </span>
    {/if}
</span>

<style>
    .tag-papel-grupo {
        display: inline-flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 6px;
    }
    .tag-papel {
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
    .tag-papel::before {
        content: '';
        flex: none;
        width: 8px;
        height: 8px;
        background: currentColor;
    }
    .tag-papel[data-papel='SOLICITANTE'] {
        background: var(--sd-neutro-fundo, #f3f4f6);
        color: var(--sd-neutro-cor, #525252);
    }
    .tag-papel[data-papel='SOLICITANTE']::before {
        border: 2px solid currentColor;
        border-radius: 9999px;
        background: transparent;
    }
    .tag-papel[data-papel='EXECUTANTE'] {
        border: 1px solid var(--sd-sirg-700, #047857);
        background: var(--sd-camada, #ffffff);
        color: var(--sd-sirg-800, #065f46);
    }
    .tag-papel[data-papel='AMBAS'] {
        background: var(--sd-ok-fundo, #d1fae5);
        color: var(--sd-sirg-800, #065f46);
    }
    .tag-papel[data-papel='AMBAS']::before {
        border-radius: 9999px;
    }
    .tag-papel--incompleta {
        background: var(--sd-aviso-fundo, #fffbeb);
        color: var(--sd-aviso-texto, #78350f);
        font-weight: 500;
    }
    .tag-papel--incompleta::before {
        transform: rotate(45deg) scale(0.85);
    }
</style>
