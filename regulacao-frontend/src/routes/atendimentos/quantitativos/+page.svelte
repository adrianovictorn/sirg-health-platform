<script lang="ts">
    import { goto } from '$app/navigation';
    import { page } from '$app/state';
    import Content from '$lib/Content.svelte';
    import { getApi } from '$lib/api.js';
    import { dataHojeBahia } from '$lib/agendaDiaConsolidada.js';
    import { carregarResumoAtendimentos } from '$lib/atendimentosApi.js';
    import { formatarData } from '$lib/datas.js';
    import { onMount } from 'svelte';

    /**
     * Quantitativos — os numeros do dia da tela de Atendimentos, numa tela propria:
     * os cinco totais do municipio e a contagem por unidade solicitante. Somente
     * leitura. Clicar numa unidade abre /atendimentos ja filtrado por ela, na mesma
     * data e agenda. O backend (api/atendimentos/resumo) so responde a USER e ADMIN.
     *
     * `?grupo=<codigo>` restringe a um Grupo de Relatorio; `?data=` abre outro dia.
     */

    type GrupoOpcao = { codigo: string; nome: string };
    type Contagem = {
        pacientes: number;
        itens: number;
        agendados: number;
        confirmados: number;
        faltas: number;
        cancelados: number;
    };
    type UnidadeResumo = { id: number | null; nome: string; contagem: Contagem };
    type Resumo = { data: string; totais: Contagem; unidades: UnidadeResumo[] };

    const INTERVALO_ATUALIZACAO_MS = 60_000;
    const ROTA = '/atendimentos/quantitativos';

    const dataDaUrl = page.url.searchParams.get('data') ?? '';
    let data = $state(/^\d{4}-\d{2}-\d{2}$/.test(dataDaUrl) ? dataDaUrl : dataHojeBahia());

    // O grupo vive na URL, como em /atendimentos.
    const grupo = $derived(page.url.searchParams.get('grupo') ?? '');
    let grupos = $state<GrupoOpcao[]>([]);
    const nomeDoGrupo = $derived(grupos.find((g) => g.codigo === grupo)?.nome ?? grupo);

    let resumo = $state<Resumo | null>(null);
    let carregando = $state(true);
    let atualizando = $state(false);
    let erro = $state<string | null>(null);
    let ultimaAtualizacao = $state<Date | null>(null);
    let token = 0;

    const dataPorExtenso = $derived.by(() => {
        if (!data) return '';
        const [ano, mes, dia] = data.split('-').map(Number);
        return new Intl.DateTimeFormat('pt-BR', {
            weekday: 'long',
            day: 'numeric',
            month: 'long',
            year: 'numeric',
            timeZone: 'UTC'
        }).format(new Date(Date.UTC(ano, mes - 1, dia)));
    });

    const ehHoje = $derived(data === dataHojeBahia());

    const cartoes = $derived.by(() => {
        const t = resumo?.totais;
        if (!t) return [];
        return [
            { rotulo: 'Pacientes', valor: t.pacientes, borda: 'border-gray-400' },
            { rotulo: 'Agendados', valor: t.agendados, borda: 'border-blue-500' },
            { rotulo: 'Confirmados', valor: t.confirmados, borda: 'border-emerald-600' },
            { rotulo: 'Faltas', valor: t.faltas, borda: 'border-amber-500' },
            { rotulo: 'Cancelados', valor: t.cancelados, borda: 'border-red-500' }
        ];
    });

    function chaveDaUnidade(u: UnidadeResumo): string {
        return u.id === null ? 'sem-unidade' : String(u.id);
    }

    /** /atendimentos ja filtrado pela unidade, na mesma data e agenda. */
    function hrefDaUnidade(u: UnidadeResumo): string {
        const params = new URLSearchParams();
        if (grupo) params.set('grupo', grupo);
        params.set('data', data);
        if (u.id === null) params.set('semUnidade', 'true');
        else params.set('unidadeId', String(u.id));
        return `/atendimentos?${params.toString()}`;
    }

    async function carregar(silencioso = false) {
        const meu = ++token;
        if (silencioso) atualizando = true;
        else carregando = true;
        try {
            const corpo: Resumo = await carregarResumoAtendimentos({ data, grupo });
            if (meu !== token) return; // resposta de uma consulta ja superada
            resumo = corpo;
            ultimaAtualizacao = new Date();
            erro = null;
        } catch (e) {
            if (meu !== token) return;
            erro = e instanceof Error ? e.message : 'Erro ao conectar ao servidor.';
        } finally {
            if (meu === token) {
                carregando = false;
                atualizando = false;
            }
        }
    }

    function irParaHoje() {
        data = dataHojeBahia();
    }

    function escolherGrupo(codigo: string) {
        goto(codigo ? `${ROTA}?grupo=${encodeURIComponent(codigo)}` : ROTA, { keepFocus: true, noScroll: true });
    }

    // Mesma fonte e mesmo criterio do menu "Agendas".
    async function carregarGrupos() {
        try {
            const res = await getApi('grupo-relatorio/listar');
            if (!res.ok) return;
            const lista: { codigo: string; nome: string; ativo: boolean; direcionadoHospital: boolean }[] = await res.json();
            grupos = lista
                .filter((g) => g.direcionadoHospital && g.ativo)
                .map((g) => ({ codigo: g.codigo, nome: g.nome }))
                .sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));
        } catch {
            // Sem a lista, o seletor so mostra o grupo da URL.
        }
    }

    // Campo de data limpo: nao consulta e mantem o ultimo resultado.
    $effect(() => {
        if (!data) return;
        grupo;
        carregar(false);
    });

    // Atualizacao automatica dos numeros, pausada com a aba oculta.
    onMount(() => {
        carregarGrupos();
        const id = setInterval(() => {
            if (document.visibilityState === 'visible' && data) carregar(true);
        }, INTERVALO_ATUALIZACAO_MS);
        return () => clearInterval(id);
    });
</script>

<svelte:head>
    <title>Quantitativos</title>
</svelte:head>

<Content titleH1="Quantitativos" page={ROTA}>
    <div class="cotas-ibm min-w-0 flex-1 space-y-6 p-4 md:p-6">
        <header class="at-cabecalho">
            <div class="min-w-0">
                <h2 class="at-titulo">
                    {grupo ? `${nomeDoGrupo} — quantitativos do dia` : 'Quantitativos do dia — todas as agendas'}
                </h2>
                <p class="at-data">{dataPorExtenso}</p>
                <p class="at-atualizacao" aria-live="polite">
                    {#if ultimaAtualizacao}
                        Atualizado às {ultimaAtualizacao.toLocaleTimeString('pt-BR')}
                        {#if atualizando}<span class="at-atualizando">· atualizando…</span>{/if}
                    {/if}
                </p>
            </div>
            <div class="at-controles">
                <label class="at-campo-grupo">
                    <span class="ibm-rotulo">Agenda</span>
                    <select value={grupo} onchange={(e) => escolherGrupo(e.currentTarget.value)} class="ibm-campo w-full">
                        <option value="">Todas</option>
                        {#each grupos as g (g.codigo)}<option value={g.codigo}>{g.nome}</option>{/each}
                        {#if grupo && !grupos.some((g) => g.codigo === grupo)}<option value={grupo}>{grupo}</option>{/if}
                    </select>
                </label>
                <label class="at-campo-grupo">
                    <span class="ibm-rotulo">Data</span>
                    <input type="date" bind:value={data} class="ibm-campo w-full" />
                </label>
                <button type="button" class="ibm-btn ibm-btn--secundario" onclick={irParaHoje} disabled={ehHoje}>
                    Hoje
                </button>
            </div>
        </header>

        {#if carregando && !resumo}
            <div class="ibm-painel at-carregando p-6" role="status">
                <span class="inline-block h-5 w-5 animate-spin rounded-full border-2 border-emerald-600 border-t-transparent" aria-hidden="true"></span>
                Carregando quantitativos…
            </div>
        {:else if erro && !resumo}
            <div class="at-erro" role="alert">
                <span>{erro}</span>
                <button type="button" class="ibm-btn ibm-btn--secundario" onclick={() => carregar(false)}>Tentar novamente</button>
            </div>
        {:else if resumo}
            {#if erro}
                <div class="ibm-aviso" role="alert">
                    <span>Não foi possível atualizar agora ({erro}). Mostrando os últimos dados carregados.</span>
                    <button type="button" class="ibm-btn ibm-btn--fantasma" onclick={() => carregar(true)}>Tentar novamente</button>
                </div>
            {/if}

            <section aria-labelledby="totais-titulo">
                <h3 id="totais-titulo" class="at-secao-titulo">Totais do dia</h3>
                <dl class="at-cartoes">
                    {#each cartoes as c (c.rotulo)}
                        <div class="at-cartao {c.borda}" data-cartao={c.rotulo}>
                            <dt class="at-cartao-rotulo"><span class="at-marca" aria-hidden="true"></span>{c.rotulo}</dt>
                            <dd class="at-cartao-numero">{c.valor}</dd>
                        </div>
                    {/each}
                </dl>
            </section>

            <section aria-labelledby="unidades-titulo" class="ibm-painel">
                <div class="at-painel-topo">
                    <h3 id="unidades-titulo" class="ibm-grupo-titulo">Por unidade solicitante</h3>
                    <p class="at-topo-nota">Selecione uma unidade para abrir a lista de pacientes dela.</p>
                </div>
                <div class="at-tabela-contorno">
                    <table class="at-tabela at-tabela--resumo">
                        <caption class="sr-only">Quantitativos de atendimento por unidade solicitante em {formatarData(data)}</caption>
                        <thead>
                            <tr>
                                <th scope="col">Unidade</th>
                                <th scope="col" class="at-num">Pacientes</th>
                                <th scope="col" class="at-num">Agendados</th>
                                <th scope="col" class="at-num">Confirmados</th>
                                <th scope="col" class="at-num">Faltas</th>
                                <th scope="col" class="at-num">Cancelados</th>
                            </tr>
                        </thead>
                        <tbody>
                            {#each resumo.unidades as u (chaveDaUnidade(u))}
                                <tr>
                                    <th scope="row">
                                        <a class="at-unidade" href={hrefDaUnidade(u)} aria-label={`Ver pacientes de ${u.nome}`}>{u.nome}</a>
                                    </th>
                                    <td class="at-num">{u.contagem.pacientes}</td>
                                    <td class="at-num">{u.contagem.agendados}</td>
                                    <td class="at-num">{u.contagem.confirmados}</td>
                                    <td class="at-num">{u.contagem.faltas}</td>
                                    <td class="at-num">{u.contagem.cancelados}</td>
                                </tr>
                            {/each}
                        </tbody>
                        <tfoot>
                            <tr>
                                <th scope="row">Município</th>
                                <td class="at-num">{resumo.totais.pacientes}</td>
                                <td class="at-num">{resumo.totais.agendados}</td>
                                <td class="at-num">{resumo.totais.confirmados}</td>
                                <td class="at-num">{resumo.totais.faltas}</td>
                                <td class="at-num">{resumo.totais.cancelados}</td>
                            </tr>
                        </tfoot>
                    </table>
                </div>
            </section>
        {/if}
    </div>
</Content>

<style>
    /*
     * Mistura SIRG + Carbon (IBM), "mais SIRG", escopada a esta tela. Copia
     * podada do <style> de /atendimentos (que copia /admin/cotas/nova), so com o
     * que esta tela usa: cabecalho do dia, cartoes de totais e tabela por
     * unidade. Decisao: docs/decisoes/0009-visual-misto-com-mais-sirg-em-atendimentos.md.
     * Para voltar ao misto de `nova`, troque os valores da secao "Mais SIRG"
     * pelos indicados em "nova". A fonte e uma pilha com fallback.
     */
    .cotas-ibm {
        --ibm-acento: #047857;
        --ibm-fonte: 'IBM Plex Sans', 'Helvetica Neue', Arial, sans-serif;

        --ibm-tinta: #161616;
        --ibm-tinta-suave: #525252;
        --ibm-fundo: #f3f4f6; /* SIRG: gray-100 (IBM: #f4f4f4) */
        --ibm-camada: #ffffff;
        --ibm-campo: #f4f4f4;
        --ibm-linha: #e0e0e0;
        --ibm-borda-campo: #8c8c8c;
        --ibm-aviso-fundo: #fcf4d6;
        --ibm-aviso-borda: #f1c21b;

        /* --- Mistura SIRG/IBM (igual a `nova`) --- */
        --ibm-raio-painel: 8px;
        --ibm-sombra-painel: 0 1px 3px 0 rgb(0 0 0 / 0.1), 0 1px 2px -1px rgb(0 0 0 / 0.1);
        --ibm-raio-controle: 6px;
        --ibm-raio-campo: 0;
        --ibm-titulo-cor: #065f46;
        --ibm-titulo-peso: 700;
        --ibm-titulo-tamanho: 18px;

        /* --- Mais SIRG: emerald nas superficies (so neste modulo) --- */
        --sirg-50: #ecfdf5;
        --sirg-100: #d1fae5;
        --sirg-200: #a7f3d0;
        --sirg-600: #059669;
        --sirg-700: #047857;
        --sirg-800: #065f46;
        --sirg-900: #064e3b;
        /* nova: transparente / 1px solid #e5e7eb */
        --at-topo-fundo: var(--sirg-50);
        --at-topo-divisor: 1px solid var(--sirg-200);
        /* nova: #e0e0e0 (cabecalho cinza) / #161616 */
        --at-tabela-cabecalho: var(--sirg-100);
        --at-tabela-cabecalho-texto: var(--sirg-900);
        --at-tabela-cabecalho-linha: 2px solid var(--sirg-600);
        /* nova: #ececec */
        --at-linha-hover: var(--sirg-50);
        /* nova: #525252, peso 400 */
        --at-rotulo-cor: var(--sirg-800);
        --at-rotulo-peso: 600;
        /* nova: borda #8c8c8c, texto #374151, hover #f9fafb */
        --ibm-secundario-borda: var(--sirg-700);
        --ibm-secundario-texto: var(--sirg-800);
        --ibm-secundario-hover: var(--sirg-50);

        /* Situacao do atendimento: marca dos cartoes (3:1 sobre branco) */
        --st-agendado-cor: #1d4ed8;
        --st-realizado-cor: #047857;
        --st-faltou-cor: #b45309;
        --st-cancelado-cor: #b91c1c;

        background: var(--ibm-fundo);
        color: var(--ibm-tinta);
        font-family: var(--ibm-fonte);
        font-size: 14px;
        line-height: 1.29;
        letter-spacing: 0.16px;
    }

    /* Cabecalho do dia: faixa emerald clara com filete, titulo e controles */
    .at-cabecalho {
        --ibm-campo: #ffffff;
        display: flex;
        flex-wrap: wrap;
        align-items: flex-end;
        justify-content: space-between;
        gap: 16px 24px;
        padding: 16px;
        border: 1px solid var(--sirg-200);
        border-left: 6px solid var(--sirg-700);
        border-radius: var(--ibm-raio-painel);
        background: var(--sirg-50);
    }
    .at-titulo {
        font-size: 20px;
        font-weight: 700;
        line-height: 1.3;
        color: var(--sirg-900);
        text-wrap: balance;
    }
    .at-data {
        margin-top: 2px;
        font-size: 14px;
        line-height: 1.43;
        color: var(--ibm-tinta);
    }
    .at-data::first-letter {
        text-transform: uppercase;
    }
    .at-atualizacao {
        min-height: 16px;
        margin-top: 4px;
        font-size: 12px;
        line-height: 1.33;
        letter-spacing: 0.32px;
        color: var(--ibm-tinta-suave);
    }
    .at-atualizando {
        font-weight: 600;
        color: var(--sirg-800);
    }
    .at-controles {
        display: flex;
        flex-wrap: wrap;
        align-items: flex-end;
        gap: 12px;
    }
    @media (max-width: 639px) {
        .at-controles {
            width: 100%;
        }
        .at-controles .at-campo-grupo {
            flex: 1 1 9rem;
        }
    }

    /* Painel: cartao branco do SIRG, com faixa de topo emerald */
    .ibm-painel {
        overflow: hidden;
        border-radius: var(--ibm-raio-painel);
        background: var(--ibm-camada);
        box-shadow: var(--ibm-sombra-painel);
    }
    .at-painel-topo {
        display: flex;
        flex-wrap: wrap;
        align-items: baseline;
        justify-content: space-between;
        gap: 4px 16px;
        padding: 12px 16px;
        border-bottom: var(--at-topo-divisor);
        background: var(--at-topo-fundo);
    }
    .ibm-grupo-titulo {
        font-size: var(--ibm-titulo-tamanho);
        font-weight: var(--ibm-titulo-peso);
        line-height: 1.4;
        color: var(--ibm-titulo-cor);
    }
    .at-topo-nota {
        font-size: 12px;
        line-height: 1.33;
        letter-spacing: 0.32px;
        color: var(--ibm-tinta-suave);
    }
    .at-secao-titulo {
        margin-bottom: 8px;
        font-size: 14px;
        font-weight: 700;
        line-height: 1.43;
        color: var(--ibm-titulo-cor);
    }

    /* Rotulo e campo: campo IBM (cinza, linha inferior), rotulo na cor do SIRG */
    .at-campo-grupo {
        display: flex;
        flex-direction: column;
        min-width: 0;
    }
    .ibm-rotulo {
        display: block;
        margin-bottom: 8px;
        font-size: 12px;
        font-weight: var(--at-rotulo-peso);
        line-height: 1.33;
        letter-spacing: 0.32px;
        color: var(--at-rotulo-cor);
    }
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
    .ibm-campo:hover {
        border-bottom-color: var(--sirg-700);
    }
    .ibm-campo:focus,
    .ibm-campo:focus-visible {
        outline: 2px solid var(--ibm-acento);
        outline-offset: -2px;
        border-color: transparent;
        border-bottom-color: var(--ibm-borda-campo);
        box-shadow: none;
    }

    /* Botoes */
    .ibm-btn {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 6px;
        min-height: 40px;
        padding: 0 16px;
        border: 1px solid transparent;
        border-radius: var(--ibm-raio-controle);
        font-family: inherit;
        font-size: 14px;
        font-weight: 600;
        letter-spacing: 0.16px;
        white-space: nowrap;
        cursor: pointer;
        touch-action: manipulation;
    }
    .ibm-btn:focus-visible {
        outline: 2px solid var(--ibm-acento);
        outline-offset: 2px;
    }
    .ibm-btn--secundario {
        border-color: var(--ibm-secundario-borda);
        background: var(--ibm-camada);
        color: var(--ibm-secundario-texto);
    }
    .ibm-btn--secundario:hover:not(:disabled) {
        background: var(--ibm-secundario-hover);
    }
    .ibm-btn--fantasma {
        padding: 0 8px;
        background: transparent;
        color: var(--sirg-800);
        text-decoration: underline;
        text-underline-offset: 3px;
    }
    .ibm-btn--fantasma:hover:not(:disabled) {
        background: var(--sirg-100);
    }
    .ibm-btn:disabled {
        border-color: transparent;
        background: var(--ibm-linha);
        color: var(--ibm-tinta-suave);
        text-decoration: none;
        cursor: not-allowed;
    }

    /* Mensagens: carregando, erro e aviso */
    .at-carregando {
        display: flex;
        align-items: center;
        gap: 12px;
        color: var(--ibm-tinta);
    }
    .at-erro,
    .ibm-aviso {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        justify-content: space-between;
        gap: 8px 16px;
        padding: 12px 16px;
        border-left: 4px solid var(--st-cancelado-cor);
        border-radius: 0 var(--ibm-raio-controle) var(--ibm-raio-controle) 0;
        background: #fef2f2;
        color: #991b1b;
        font-size: 14px;
        line-height: 1.43;
    }
    .ibm-aviso {
        border-left-color: var(--ibm-aviso-borda);
        background: var(--ibm-aviso-fundo);
        color: var(--ibm-tinta);
    }

    /* Marca de situacao: forma diferente por situacao, para nao depender so de cor.
       Confirmado = circulo cheio; agendado = circulo vazado; falta = losango;
       cancelado = quadrado. */
    .at-marca {
        flex: none;
        width: 10px;
        height: 10px;
        background: currentColor;
    }

    /* Totais do dia: filete lateral na cor da situacao (classe `border-*` vinda
       do script), marca e numero */
    .at-cartoes {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(9rem, 1fr));
        gap: 12px;
    }
    .at-cartao {
        display: flex;
        flex-direction: column;
        gap: 4px;
        padding: 12px 16px;
        border-style: solid;
        border-width: 0 0 0 6px;
        border-radius: var(--ibm-raio-controle);
        background: var(--ibm-camada);
        box-shadow: var(--ibm-sombra-painel);
    }
    .at-cartao-rotulo {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 14px;
        font-weight: 600;
        color: var(--ibm-tinta);
    }
    .at-cartao-numero {
        font-size: 30px;
        font-weight: 700;
        line-height: 1.1;
        font-variant-numeric: tabular-nums;
        color: var(--ibm-tinta);
    }
    .at-cartao[data-cartao='Pacientes'] {
        background: var(--sirg-50);
    }
    .at-cartao[data-cartao='Pacientes'] .at-marca {
        display: none;
    }
    .at-cartao[data-cartao='Pacientes'] .at-cartao-numero,
    .at-cartao[data-cartao='Confirmados'] .at-cartao-numero {
        color: var(--sirg-800);
    }
    .at-cartao[data-cartao='Agendados'] .at-marca {
        border: 2px solid var(--st-agendado-cor);
        border-radius: 9999px;
        background: transparent;
    }
    .at-cartao[data-cartao='Confirmados'] .at-marca {
        border-radius: 9999px;
        color: var(--st-realizado-cor);
    }
    .at-cartao[data-cartao='Faltas'] .at-marca {
        color: var(--st-faltou-cor);
        transform: rotate(45deg) scale(0.85);
    }
    .at-cartao[data-cartao='Cancelados'] .at-marca {
        color: var(--st-cancelado-cor);
    }

    /* Tabela por unidade: cabecalho e rodape emerald, linhas de 1 px */
    .at-tabela-contorno {
        overflow-x: auto;
    }
    .at-tabela {
        width: 100%;
        border-collapse: collapse;
        font-size: 14px;
        text-align: left;
    }
    .at-tabela--resumo {
        min-width: 40rem;
    }
    .at-tabela thead th {
        padding: 10px 12px;
        border-bottom: var(--at-tabela-cabecalho-linha);
        background: var(--at-tabela-cabecalho);
        color: var(--at-tabela-cabecalho-texto);
        font-size: 12px;
        font-weight: 600;
        line-height: 1.33;
        letter-spacing: 0.32px;
        white-space: nowrap;
    }
    .at-tabela tbody th,
    .at-tabela tbody td {
        padding: 4px 12px;
        border-bottom: 1px solid var(--ibm-linha);
        vertical-align: middle;
        color: var(--ibm-tinta);
    }
    .at-tabela tbody tr:last-child > th,
    .at-tabela tbody tr:last-child > td {
        border-bottom: 0;
    }
    .at-tabela tbody tr:hover > th,
    .at-tabela tbody tr:hover > td {
        background: var(--at-linha-hover);
    }
    .at-tabela tfoot th,
    .at-tabela tfoot td {
        padding: 10px 12px;
        border-top: 2px solid var(--sirg-700);
        background: var(--sirg-50);
        color: var(--sirg-900);
        font-weight: 700;
    }
    .at-num {
        text-align: right;
        font-variant-numeric: tabular-nums;
    }

    /* Unidade: link para /atendimentos filtrado. Sublinhado sempre visivel
       (e o significante do link), alvo de 40 px */
    .at-unidade {
        display: inline-flex;
        align-items: center;
        min-height: 40px;
        margin-left: -8px;
        padding: 0 8px;
        border-radius: var(--ibm-raio-controle);
        color: var(--sirg-800);
        font-weight: 600;
        text-decoration: underline;
        text-decoration-color: var(--sirg-200);
        text-decoration-thickness: 2px;
        text-underline-offset: 4px;
        touch-action: manipulation;
    }
    .at-unidade:hover {
        color: var(--sirg-900);
        text-decoration-color: currentColor;
    }
    .at-unidade:focus-visible {
        outline: 2px solid var(--ibm-acento);
        outline-offset: 2px;
    }

    @media (prefers-reduced-motion: no-preference) {
        .ibm-btn,
        .ibm-campo {
            transition:
                background-color 110ms ease-out,
                border-color 110ms ease-out;
        }
    }
</style>
