<script lang="ts">
    import { getApi } from '$lib/api';
    import {
        COLUNAS_EXPORTACAO,
        achatarConsolidado,
        dataHojeBahia,
        gerarCsv
    } from '$lib/agendaDiaConsolidada.js';
    import { onMount } from 'svelte';

    /**
     * Agenda do Dia consolidada — visao do ADMIN global: todas as unidades numa
     * tela, organizada em unidade -> grupo -> especialidade, com indicadores em
     * cards. Somente leitura. O backend (api/agenda-dia/**) so responde ao ADMIN.
     */

    type Indicadores = {
        pacientes: number;
        itens: number;
        agendados: number;
        realizados: number;
        faltasCancelados: number;
        outros: number;
    };
    type CotaDia = {
        id: number;
        tipo: 'DIA' | 'DIA_SEMANA' | 'MES';
        escopo: string;
        escopoNome: string;
        compartilhada: boolean;
        total: number;
        utilizada: number;
        livres: number;
    };
    type EspecialidadeNo = { id: number; nome: string; indicadores: Indicadores; cotas: CotaDia[] };
    type GrupoNo = {
        id: number | null;
        codigo: string | null;
        nome: string;
        indicadores: Indicadores;
        cotas: CotaDia[];
        especialidades: EspecialidadeNo[];
    };
    type UnidadeNo = {
        id: number | null;
        nome: string;
        indicadores: Indicadores;
        cotas: CotaDia[];
        grupos: GrupoNo[];
    };
    type Consolidado = {
        data: string;
        totais: Indicadores;
        gruposGeral: GrupoNo[];
        unidades: UnidadeNo[];
    };
    type ItemNominal = {
        id: number;
        nomePaciente: string;
        cpfMascarado: string | null;
        cnsMascarado: string | null;
        dataNascimento: string | null;
        unidadeNome: string | null;
        usfOrigem: string | null;
        especialidadeNome: string;
        status: string;
        turno: string | null;
        horaAgendada: string | null;
    };
    type Opcao = { id: number; nome: string };
    type Selecao = { unidadeId: number | null; especialidadeId: number; unidadeNome: string; especialidadeNome: string };

    const INTERVALO_ATUALIZACAO_MS = 60_000;
    const TAMANHO_PAGINA = 20;

    let data = $state(dataHojeBahia());
    let filtroUnidade = $state<number | null>(null);
    let filtroGrupo = $state<number | null>(null);
    let filtroEspecialidade = $state<number | null>(null);

    let consolidado = $state<Consolidado | null>(null);
    let carregando = $state(true);
    let atualizando = $state(false);
    let erro = $state<string | null>(null);
    let ultimaAtualizacao = $state<Date | null>(null);

    // Catalogo dos filtros: uniao de tudo que ja apareceu, para as opcoes nao
    // encolherem quando um filtro e aplicado.
    let catalogoUnidades = $state<Opcao[]>([]);
    let catalogoGrupos = $state<Opcao[]>([]);
    let catalogoEspecialidades = $state<Opcao[]>([]);

    // Lista nominal — so depois de escolher uma especialidade.
    let selecao = $state<Selecao | null>(null);
    let itens = $state<ItemNominal[]>([]);
    let pagina = $state(0);
    let totalPaginas = $state(0);
    let totalItens = $state(0);
    let carregandoItens = $state(false);
    let erroItens = $state<string | null>(null);

    let exportando = $state(false);

    let tokenRequisicao = 0;
    let tokenItens = 0;

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

    const vazio = $derived(!!consolidado && consolidado.totais.itens === 0);

    function mesclar(catalogo: Opcao[], novos: Opcao[]): Opcao[] {
        const mapa = new Map(catalogo.map((o) => [o.id, o]));
        for (const o of novos) mapa.set(o.id, o);
        return [...mapa.values()].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR'));
    }

    function atualizarCatalogos(c: Consolidado) {
        const unidades: Opcao[] = [];
        const grupos: Opcao[] = [];
        const especialidades: Opcao[] = [];
        for (const u of c.unidades) {
            if (u.id !== null) unidades.push({ id: u.id, nome: u.nome });
            for (const g of u.grupos) {
                if (g.id !== null) grupos.push({ id: g.id, nome: g.nome });
                for (const e of g.especialidades) especialidades.push({ id: e.id, nome: e.nome });
            }
        }
        catalogoUnidades = mesclar(catalogoUnidades, unidades);
        catalogoGrupos = mesclar(catalogoGrupos, grupos);
        catalogoEspecialidades = mesclar(catalogoEspecialidades, especialidades);
    }

    async function carregar(silencioso = false) {
        const meu = ++tokenRequisicao;
        if (silencioso) atualizando = true;
        else carregando = true;
        try {
            const params = new URLSearchParams({ data });
            if (filtroUnidade !== null) params.append('unidadeId', String(filtroUnidade));
            if (filtroGrupo !== null) params.append('grupoId', String(filtroGrupo));
            if (filtroEspecialidade !== null) params.append('especialidadeId', String(filtroEspecialidade));

            const res = await getApi(`agenda-dia/consolidado?${params.toString()}`);
            if (meu !== tokenRequisicao) return; // resposta de uma consulta ja superada
            if (res.status === 403) throw new Error('Esta visão é restrita ao administrador.');
            if (!res.ok) throw new Error('Não foi possível carregar a agenda do dia.');
            const corpo: Consolidado = await res.json();
            consolidado = corpo;
            atualizarCatalogos(corpo);
            ultimaAtualizacao = new Date();
            erro = null;
        } catch (e) {
            if (meu !== tokenRequisicao) return;
            erro = e instanceof Error ? e.message : 'Erro ao conectar ao servidor.';
        } finally {
            if (meu === tokenRequisicao) {
                carregando = false;
                atualizando = false;
            }
        }
    }

    async function carregarItens(alvo: Selecao, numeroPagina: number) {
        const meu = ++tokenItens;
        carregandoItens = true;
        erroItens = null;
        try {
            const params = new URLSearchParams({
                data,
                especialidadeId: String(alvo.especialidadeId),
                page: String(numeroPagina),
                size: String(TAMANHO_PAGINA)
            });
            if (alvo.unidadeId === null) params.append('semUnidade', 'true');
            else params.append('unidadeId', String(alvo.unidadeId));

            const res = await getApi(`agenda-dia/consolidado/pacientes?${params.toString()}`);
            if (meu !== tokenItens) return;
            if (!res.ok) throw new Error('Não foi possível carregar a lista de pacientes.');
            const corpo = await res.json();
            itens = corpo.content ?? [];
            pagina = corpo.number ?? numeroPagina;
            totalPaginas = corpo.totalPages ?? 0;
            totalItens = corpo.totalElements ?? 0;
        } catch (e) {
            if (meu !== tokenItens) return;
            erroItens = e instanceof Error ? e.message : 'Erro ao conectar ao servidor.';
            itens = [];
        } finally {
            if (meu === tokenItens) carregandoItens = false;
        }
    }

    function abrirLista(unidade: UnidadeNo, especialidade: EspecialidadeNo) {
        selecao = {
            unidadeId: unidade.id,
            especialidadeId: especialidade.id,
            unidadeNome: unidade.nome,
            especialidadeNome: especialidade.nome
        };
        carregarItens(selecao, 0);
    }

    function fecharLista() {
        tokenItens++;
        selecao = null;
        itens = [];
    }

    function irParaPagina(n: number) {
        if (selecao && n >= 0 && n < totalPaginas) carregarItens(selecao, n);
    }

    function limparFiltros() {
        filtroUnidade = null;
        filtroGrupo = null;
        filtroEspecialidade = null;
    }

    // Recarrega ao mudar data/filtros. A lista nominal e fechada porque pertence a
    // um (unidade, especialidade, data) que deixou de ser o que a tela mostra.
    $effect(() => {
        // Campo de data limpo: nao consulta (o backend assumiria "hoje" e a tela
        // mostraria outra coisa) e mantem o ultimo resultado ate haver data valida.
        if (!data) return;
        filtroUnidade;
        filtroGrupo;
        filtroEspecialidade;
        fecharLista();
        carregar(false);
    });

    // Atualizacao automatica: so o agregado (leve), nunca a lista nominal, e
    // pausada com a aba oculta.
    onMount(() => {
        const id = setInterval(() => {
            if (document.visibilityState === 'visible') carregar(true);
        }, INTERVALO_ATUALIZACAO_MS);
        return () => clearInterval(id);
    });

    // ------------------------------------------------------------------
    // Exportacao (somente agregado — sem dado de paciente)
    // ------------------------------------------------------------------

    function baixar(blob: Blob, nome: string) {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = nome;
        a.click();
        URL.revokeObjectURL(url);
    }

    function exportarCsv() {
        if (!consolidado) return;
        const csv = gerarCsv(COLUNAS_EXPORTACAO, achatarConsolidado(consolidado));
        baixar(new Blob([csv], { type: 'text/csv;charset=utf-8' }), `agenda-do-dia-${data}.csv`);
    }

    async function exportarPdf() {
        if (!consolidado) return;
        exportando = true;
        try {
            const [{ jsPDF }, { default: autoTable }] = await Promise.all([
                import('jspdf'),
                import('jspdf-autotable')
            ]);
            const doc = new jsPDF({ unit: 'pt', format: 'a4', orientation: 'landscape' });
            doc.setFontSize(14);
            doc.text(`Agenda do Dia — ${dataPorExtenso}`, 40, 40);
            autoTable(doc, {
                startY: 56,
                head: [COLUNAS_EXPORTACAO],
                body: achatarConsolidado(consolidado).map((l) => l.map((c) => String(c ?? ''))),
                styles: { fontSize: 8 },
                headStyles: { fillColor: [13, 114, 68] }
            });
            doc.save(`agenda-do-dia-${data}.pdf`);
        } finally {
            exportando = false;
        }
    }

    const ROTULO_STATUS: Record<string, string> = {
        AGENDADO: 'Agendado',
        REALIZADO: 'Realizado',
        CANCELADO: 'Faltou/Cancelado',
        FALTOU: 'Faltou/Cancelado',
        RETORNO: 'Retorno',
        RETORNO_POLICLINICA: 'Retorno policlínica'
    };

    function rotuloCota(c: CotaDia): string {
        const tipo = c.tipo === 'MES' ? 'cota do mês' : c.tipo === 'DIA_SEMANA' ? 'cota mensal, atende neste dia' : 'vagas do dia';
        return `${c.livres} livres de ${c.total} (${tipo}${c.compartilhada ? ', compartilhada' : ''})`;
    }

    function formatarData(iso: string | null): string {
        if (!iso) return '—';
        const [a, m, d] = iso.split('-');
        return `${d}/${m}/${a}`;
    }
</script>


{#snippet cota(c: CotaDia, rotulo: string)}
    {#if c.livres <= 0}
        <p class="flex items-center gap-1.5 rounded-md bg-red-50 px-2 py-1 text-xs font-semibold text-red-800">
            <span aria-hidden="true">⛔</span>
            <span>{rotulo}: cota esgotada ({c.utilizada} de {c.total})</span>
        </p>
    {:else}
        <p class="flex items-center gap-1.5 rounded-md bg-emerald-50 px-2 py-1 text-xs font-medium text-emerald-800">
            <span aria-hidden="true">✓</span>
            <span>{rotulo}: {rotuloCota(c)}</span>
        </p>
    {/if}
{/snippet}

{#snippet metricas(i: Indicadores)}
    <dl class="grid grid-cols-3 gap-2 text-center">
        <div class="rounded-md bg-blue-50 px-1 py-1.5">
            <dd class="text-lg font-bold leading-none text-blue-800">{i.agendados}</dd>
            <dt class="mt-1 text-xs text-blue-800">Agendados</dt>
        </div>
        <div class="rounded-md bg-green-50 px-1 py-1.5">
            <dd class="text-lg font-bold leading-none text-green-800">{i.realizados}</dd>
            <dt class="mt-1 text-xs text-green-800">Realizados</dt>
        </div>
        <div class="rounded-md bg-red-50 px-1 py-1.5">
            <dd class="text-lg font-bold leading-none text-red-800">{i.faltasCancelados}</dd>
            <dt class="mt-1 text-xs text-red-800">Faltas/Canc.</dt>
        </div>
    </dl>
{/snippet}

<section aria-labelledby="agenda-consolidada-titulo" class="m-5 space-y-6">
    <header class="flex flex-wrap items-end justify-between gap-4">
        <div>
            <h2 id="agenda-consolidada-titulo" class="text-2xl font-semibold text-gray-800">
                Agenda do Dia — todas as unidades
            </h2>
            <p class="text-gray-600 capitalize">{dataPorExtenso}</p>
            <p class="min-h-4 text-xs text-gray-600" aria-live="polite">
                {#if ultimaAtualizacao}
                    Atualizado às {ultimaAtualizacao.toLocaleTimeString('pt-BR')}
                    {#if atualizando}<span class="font-medium text-emerald-700">· atualizando…</span>{/if}
                {/if}
            </p>
        </div>
        <div class="flex flex-wrap gap-2">
            <button type="button" class="min-h-10 rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-sm transition-colors hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:cursor-not-allowed disabled:opacity-50" onclick={exportarCsv} disabled={!consolidado || vazio}>
                Exportar CSV
            </button>
            <button type="button" class="min-h-10 rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 shadow-sm transition-colors hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:cursor-not-allowed disabled:opacity-50" onclick={exportarPdf} disabled={!consolidado || vazio || exportando}>
                {exportando ? 'Gerando PDF…' : 'Exportar PDF'}
            </button>
        </div>
    </header>

    <form class="grid grid-cols-1 gap-4 rounded-lg bg-white p-4 shadow sm:grid-cols-2 lg:grid-cols-5" onsubmit={(e) => e.preventDefault()} aria-label="Filtros da agenda do dia">
        <label class="flex flex-col gap-1 text-sm font-medium text-gray-700">
            Data
            <input type="date" bind:value={data} class="min-h-10 w-full rounded-md border-gray-300 shadow-sm focus:border-emerald-500 focus:ring-emerald-500" />
        </label>
        <label class="flex flex-col gap-1 text-sm font-medium text-gray-700">
            Unidade
            <select bind:value={filtroUnidade} class="min-h-10 w-full rounded-md border-gray-300 shadow-sm focus:border-emerald-500 focus:ring-emerald-500">
                <option value={null}>Todas</option>
                {#each catalogoUnidades as o (o.id)}<option value={o.id}>{o.nome}</option>{/each}
            </select>
        </label>
        <label class="flex flex-col gap-1 text-sm font-medium text-gray-700">
            Grupo
            <select bind:value={filtroGrupo} class="min-h-10 w-full rounded-md border-gray-300 shadow-sm focus:border-emerald-500 focus:ring-emerald-500">
                <option value={null}>Todos</option>
                {#each catalogoGrupos as o (o.id)}<option value={o.id}>{o.nome}</option>{/each}
            </select>
        </label>
        <label class="flex flex-col gap-1 text-sm font-medium text-gray-700">
            Especialidade
            <select bind:value={filtroEspecialidade} class="min-h-10 w-full rounded-md border-gray-300 shadow-sm focus:border-emerald-500 focus:ring-emerald-500">
                <option value={null}>Todas</option>
                {#each catalogoEspecialidades as o (o.id)}<option value={o.id}>{o.nome}</option>{/each}
            </select>
        </label>
        <div class="flex items-end">
            <button type="button" class="min-h-10 rounded-md px-3 py-2 text-sm font-medium text-emerald-800 hover:bg-emerald-50 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600" onclick={limparFiltros}>
                Limpar filtros
            </button>
        </div>
    </form>

    {#if carregando && !consolidado}
        <div class="flex items-center gap-3 rounded-lg bg-white p-6 text-gray-600 shadow" role="status">
            <span class="inline-block h-5 w-5 animate-spin rounded-full border-2 border-emerald-600 border-t-transparent" aria-hidden="true"></span>
            Carregando agenda do dia…
        </div>
    {:else if erro && !consolidado}
        <div class="flex flex-wrap items-center justify-between gap-3 rounded-r-lg border-l-4 border-red-500 bg-red-50 p-4 text-sm text-red-800" role="alert">
            <span>{erro}</span>
            <button type="button" class="min-h-10 rounded-md border border-red-300 bg-white px-4 py-2 font-semibold text-red-800 hover:bg-red-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600" onclick={() => carregar(false)}>Tentar novamente</button>
        </div>
    {:else if consolidado}
        {#if erro}
            <div class="flex flex-wrap items-center justify-between gap-3 rounded-r-lg border-l-4 border-amber-400 bg-amber-50 p-3 text-sm text-amber-900" role="alert">
                <span>Não foi possível atualizar agora ({erro}). Mostrando os últimos dados carregados.</span>
                <button type="button" class="min-h-10 rounded-md px-3 py-2 font-medium text-amber-900 underline hover:bg-amber-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-amber-600" onclick={() => carregar(true)}>Tentar novamente</button>
            </div>
        {/if}

        <!-- Indicadores gerais -->
        <section aria-labelledby="totais-titulo">
            <h3 id="totais-titulo" class="mb-2 text-sm font-semibold uppercase tracking-wide text-gray-600">Totais do dia</h3>
            <div class="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-6">
                {#each [['Pacientes', consolidado.totais.pacientes, 'border-gray-300'], ['Itens agendados no dia', consolidado.totais.itens, 'border-gray-300'], ['Agendados', consolidado.totais.agendados, 'border-blue-500'], ['Realizados', consolidado.totais.realizados, 'border-green-600'], ['Faltas/Cancelados', consolidado.totais.faltasCancelados, 'border-red-500'], ['Outros status', consolidado.totais.outros, 'border-amber-400']] as [rotulo, valor, borda]}
                    <div class="rounded-lg border-t-4 bg-white p-4 shadow {borda}">
                        <p class="text-3xl font-bold text-gray-800">{valor}</p>
                        <p class="text-sm text-gray-600">{rotulo}</p>
                    </div>
                {/each}
            </div>
        </section>

        {#if vazio}
            <p class="rounded-lg bg-gray-50 p-8 text-center text-gray-600 shadow-sm">
                Nenhum atendimento agendado para esta data com os filtros escolhidos.
            </p>
        {/if}

        <!-- Por grupo (todas as unidades) -->
        {#if consolidado.gruposGeral.length > 0}
            <section aria-labelledby="grupos-titulo">
                <h3 id="grupos-titulo" class="mb-2 text-sm font-semibold uppercase tracking-wide text-gray-600">Por grupo — todas as unidades</h3>
                <div class="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5">
                    {#each consolidado.gruposGeral as g (g.id ?? 'sem-grupo')}
                        <div class="rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
                            <p class="text-sm font-medium text-gray-700">{g.nome}</p>
                            <p class="mt-1 text-2xl font-bold text-gray-800">{g.indicadores.itens} <span class="text-sm font-normal text-gray-600">item(ns)</span></p>
                            <p class="text-xs text-gray-600">{g.indicadores.pacientes} paciente(s)</p>
                        </div>
                    {/each}
                </div>
            </section>
        {/if}

        <!-- Por unidade -> grupo -> especialidade -->
        {#each consolidado.unidades as u (u.id ?? 'sem-unidade')}
            <section class="overflow-hidden rounded-lg border border-gray-200 bg-white shadow" aria-label={`Unidade ${u.nome}`}>
                <div class="space-y-3 border-b bg-gray-50 p-4">
                    <div class="flex flex-wrap items-center justify-between gap-2">
                        <h3 class="text-lg font-semibold text-gray-800">{u.nome}</h3>
                        <ul class="flex flex-wrap gap-2 text-xs font-semibold">
                            <li class="rounded-full bg-gray-200 px-2.5 py-0.5 text-gray-800">{u.indicadores.pacientes} paciente(s)</li>
                            <li class="rounded-full bg-blue-100 px-2.5 py-0.5 text-blue-800">{u.indicadores.agendados} agendado(s)</li>
                            <li class="rounded-full bg-green-100 px-2.5 py-0.5 text-green-800">{u.indicadores.realizados} realizado(s)</li>
                            <li class="rounded-full bg-red-100 px-2.5 py-0.5 text-red-800">{u.indicadores.faltasCancelados} falta(s)/cancelado(s)</li>
                        </ul>
                    </div>
                    {#if u.cotas.length > 0}
                        <div class="flex flex-wrap gap-2">
                            {#each u.cotas as c (c.id)}{@render cota(c, 'Cota geral')}{/each}
                        </div>
                    {/if}
                </div>

                <div class="space-y-6 p-4">
                    {#if u.grupos.length === 0}
                        <p class="text-sm text-gray-600">Sem atendimentos neste dia.</p>
                    {/if}

                    {#each u.grupos as g (g.id ?? 'sem-grupo')}
                        <div>
                            <div class="flex flex-wrap items-center gap-x-3 gap-y-1">
                                <h4 class="font-semibold text-gray-700">{g.nome}</h4>
                                <span class="text-sm text-gray-600">{g.indicadores.itens} item(ns)</span>
                                {#each g.cotas as c (c.id)}{@render cota(c, 'Cota do grupo')}{/each}
                            </div>
                            <div class="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
                                {#each g.especialidades as e (e.id)}
                                    {@const ativa = selecao?.especialidadeId === e.id && selecao?.unidadeId === u.id}
                                    <button type="button" class="flex min-h-11 flex-col gap-2 rounded-lg border bg-white p-3 text-left shadow-sm transition-colors hover:border-emerald-500 hover:bg-emerald-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 {ativa ? 'border-emerald-600 ring-2 ring-emerald-600' : 'border-gray-200'}" onclick={() => abrirLista(u, e)} aria-label={`Ver pacientes de ${e.nome} em ${u.nome}`} aria-pressed={ativa}>
                                        <span class="font-medium text-gray-800">{e.nome}</span>
                                        {@render metricas(e.indicadores)}
                                        <span class="text-xs text-gray-600">{e.indicadores.pacientes} paciente(s) · {e.indicadores.itens} item(ns){#if e.indicadores.outros > 0} · {e.indicadores.outros} outro(s){/if}</span>
                                        {#each e.cotas as c (c.id)}{@render cota(c, 'Vagas')}{/each}
                                        <span class="mt-auto text-xs font-medium text-emerald-800">Ver pacientes →</span>
                                    </button>
                                {/each}
                            </div>
                        </div>
                    {/each}
                </div>
            </section>
        {/each}

        <!-- Lista nominal (sob demanda) -->
        {#if selecao}
            <section class="rounded-lg border-2 border-emerald-600 bg-white p-4 shadow-lg" aria-live="polite" aria-label="Pacientes da especialidade selecionada">
                <div class="flex flex-wrap items-center justify-between gap-2">
                    <h3 class="font-semibold text-gray-800">
                        {selecao.especialidadeNome} — {selecao.unidadeNome}
                        <span class="rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-semibold text-emerald-800">{totalItens}</span>
                    </h3>
                    <button type="button" class="min-h-10 rounded-md border border-gray-300 bg-white px-4 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600" onclick={fecharLista}>Fechar lista</button>
                </div>
                <p class="mt-1 text-xs text-gray-600">CPF e CNS aparecem mascarados.</p>

                {#if carregandoItens}
                    <p class="mt-3 flex items-center gap-3 text-gray-600" role="status">
                        <span class="inline-block h-4 w-4 animate-spin rounded-full border-2 border-emerald-600 border-t-transparent" aria-hidden="true"></span>
                        Carregando pacientes…
                    </p>
                {:else if erroItens}
                    <p class="mt-3 rounded-md bg-red-50 p-3 text-sm text-red-800" role="alert">{erroItens}</p>
                {:else if itens.length === 0}
                    <p class="mt-3 rounded-md bg-gray-50 p-6 text-center text-gray-600">Nenhum paciente encontrado.</p>
                {:else}
                    <div class="mt-3 overflow-x-auto rounded-md border border-gray-200">
                        <table class="w-full min-w-[44rem] text-left text-sm">
                            <caption class="sr-only">Pacientes de {selecao.especialidadeNome} em {selecao.unidadeNome}</caption>
                            <thead class="bg-gray-50 text-xs uppercase tracking-wide text-gray-600">
                                <tr>
                                    <th scope="col" class="px-3 py-2">Paciente</th>
                                    <th scope="col" class="px-3 py-2">Nascimento</th>
                                    <th scope="col" class="px-3 py-2">CPF</th>
                                    <th scope="col" class="px-3 py-2">CNS</th>
                                    <th scope="col" class="px-3 py-2">Origem</th>
                                    <th scope="col" class="px-3 py-2">Turno/Hora</th>
                                    <th scope="col" class="px-3 py-2">Status</th>
                                </tr>
                            </thead>
                            <tbody class="divide-y divide-gray-200">
                                {#each itens as i (i.id)}
                                    <tr class="hover:bg-gray-50">
                                        <th scope="row" class="px-3 py-2 font-medium text-gray-800">{i.nomePaciente}</th>
                                        <td class="whitespace-nowrap px-3 py-2">{formatarData(i.dataNascimento)}</td>
                                        <td class="whitespace-nowrap px-3 py-2 font-mono">{i.cpfMascarado ?? '—'}</td>
                                        <td class="whitespace-nowrap px-3 py-2 font-mono">{i.cnsMascarado ?? '—'}</td>
                                        <td class="px-3 py-2">{i.usfOrigem ?? '—'}</td>
                                        <td class="whitespace-nowrap px-3 py-2">{[i.turno, i.horaAgendada].filter(Boolean).join(' · ') || '—'}</td>
                                        <td class="px-3 py-2">
                                            <span class="inline-block whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-semibold {i.status === 'REALIZADO' ? 'bg-green-100 text-green-800' : i.status === 'AGENDADO' ? 'bg-blue-100 text-blue-800' : i.status === 'CANCELADO' || i.status === 'FALTOU' ? 'bg-red-100 text-red-800' : 'bg-amber-100 text-amber-900'}">{ROTULO_STATUS[i.status] ?? i.status}</span>
                                        </td>
                                    </tr>
                                {/each}
                            </tbody>
                        </table>
                    </div>
                    {#if totalPaginas > 1}
                        <nav class="mt-3 flex items-center justify-between gap-2 text-sm" aria-label="Paginação da lista de pacientes">
                            <button type="button" class="min-h-10 rounded-md border border-gray-300 bg-white px-4 py-2 font-medium text-gray-700 hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:cursor-not-allowed disabled:opacity-50" onclick={() => irParaPagina(pagina - 1)} disabled={pagina === 0}>Anterior</button>
                            <span class="text-gray-700">Página {pagina + 1} de {totalPaginas}</span>
                            <button type="button" class="min-h-10 rounded-md border border-gray-300 bg-white px-4 py-2 font-medium text-gray-700 hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:cursor-not-allowed disabled:opacity-50" onclick={() => irParaPagina(pagina + 1)} disabled={pagina + 1 >= totalPaginas}>Próxima</button>
                        </nav>
                    {/if}
                {/if}
            </section>
        {/if}
    {/if}
</section>
