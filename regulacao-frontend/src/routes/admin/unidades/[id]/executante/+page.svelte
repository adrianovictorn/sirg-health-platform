<script lang="ts">
    import { goto } from '$app/navigation';
    import { page } from '$app/state';
    import Content from '$lib/Content.svelte';
    import LoadingSpinner from '$lib/LoadingSpinner.svelte';
    import { getApi } from '$lib/api.js';
    import { listarEspecialidadesCatalogo, listarGrupoRelatorio } from '$lib/especialidadesApi.js';
    import {
        carregarUnidadeExecutante,
        listarOperadoresDisponiveis,
        salvarUnidadeExecutante
    } from '$lib/unidadesExecutantesApi.js';
    import { resumoDaParametrizacao } from '$lib/papelUnidade.js';
    import SirgDesign from '$lib/sirg-design/SirgDesign.svelte';
    import TagPapelUnidade from '$lib/sirg-design/TagPapelUnidade.svelte';
    import { onMount } from 'svelte';
    import { toast } from 'svelte-sonner';

    /**
     * Parametrizacao da unidade executante (V110): o que ela realiza (grupos
     * inteiros + especialidades avulsas), em quais locais de agendamento atende e
     * quais operadores (USER) a representam. Com ao menos um local e um grupo ou
     * especialidade, a executante fica "configurada" e os operadores vinculados
     * passam a ver so os atendimentos dela em /atendimentos.
     *
     * Grupo e especialidade avulsa podem estar em mais de uma executante (V111,
     * ADR 0013): quem desempata e o local do agendamento. Local ja ligado a outra
     * executante e movido para esta ao salvar.
     */

    type Grupo = { id: number; codigo: string; nome: string; ativo: boolean };
    type Especialidade = { id: number; nome: string; ativo: boolean; grupoRelatorio: { id: number; nome: string } | null };
    type Local = { id: number; nomeLocal: string; cidadeNome: string | null; unidadeExecutanteId: number | null; unidadeExecutanteNome: string | null };
    // `invalido`: vinculado, mas perdeu o perfil USER ou foi desativado — aparece para ser desmarcado.
    type Operador = { id: number; nome: string; unidadeLotacao: string | null; invalido?: boolean };

    const unidadeId = Number(page.params.id);

    let carregando = $state(true);
    let salvando = $state(false);
    let unidadeNome = $state('');
    let configurada = $state(false);

    let grupos = $state<Grupo[]>([]);
    let especialidades = $state<Especialidade[]>([]);
    let locais = $state<Local[]>([]);
    let operadores = $state<Operador[]>([]);

    let grupoIds = $state<number[]>([]);
    let especialidadeIds = $state<number[]>([]);
    let localIds = $state<number[]>([]);
    let usuarioIds = $state<number[]>([]);

    let filtroEspecialidade = $state('');
    let filtroLocal = $state('');
    let filtroOperador = $state('');

    const contem = (texto: string | null | undefined, termo: string) =>
        (texto ?? '').toLocaleLowerCase('pt-BR').includes(termo.trim().toLocaleLowerCase('pt-BR'));

    // Especialidade cujo grupo inteiro ja esta marcado: coberta, nao precisa ser avulsa.
    const cobertaPorGrupo = (e: Especialidade) => !!e.grupoRelatorio && grupoIds.includes(e.grupoRelatorio.id);

    const especialidadesVisiveis = $derived(
        especialidades.filter((e) => contem(e.nome, filtroEspecialidade) || contem(e.grupoRelatorio?.nome, filtroEspecialidade))
    );
    const locaisVisiveis = $derived(locais.filter((l) => contem(l.nomeLocal, filtroLocal) || contem(l.cidadeNome, filtroLocal)));
    const operadoresVisiveis = $derived(
        operadores.filter((o) => contem(o.nome, filtroOperador) || contem(o.unidadeLotacao, filtroOperador))
    );

    const compararNomes = (a: string | null, b: string | null) => (a ?? '').localeCompare(b ?? '', 'pt-BR');

    let resumoPapel = $state<ReturnType<typeof resumoDaParametrizacao> | null>(null);

    const ficaraConfigurada = $derived(localIds.length > 0 && (grupoIds.length > 0 || especialidadeIds.length > 0));

    onMount(carregar);

    async function carregar() {
        carregando = true;
        try {
            const [config, listaGrupos, listaEspecialidades, resLocais, listaOperadores, resUnidade] = await Promise.all([
                carregarUnidadeExecutante(unidadeId),
                listarGrupoRelatorio(),
                listarEspecialidadesCatalogo(),
                getApi('local/agendamento'),
                listarOperadoresDisponiveis(),
                getApi(`unidades/${unidadeId}`)
            ]);
            if (!resLocais.ok) throw new Error('Não foi possível carregar os locais de agendamento.');
            const listaLocais: Local[] = await resLocais.json();

            unidadeNome = config.unidadeNome;
            // Tag de papel (ADR 0012): estado salvo. Sem a unidade, assume ativa
            // (`configurada` ja considera ativo; afeta so o indicador de incompleta).
            const unidade = resUnidade.ok ? await resUnidade.json() : null;
            resumoPapel = resumoDaParametrizacao(config, unidade ? unidade.ativo !== false : true);
            configurada = config.configurada;
            grupoIds = config.grupos.map((g: { id: number }) => g.id);
            especialidadeIds = config.especialidades.map((e: { id: number }) => e.id);
            localIds = config.locais.map((l: { id: number }) => l.id);
            usuarioIds = config.operadores.map((o: { id: number }) => o.id);

            // Inativos so aparecem se ja estiverem marcados, para poderem ser desmarcados.
            grupos = (listaGrupos as Grupo[])
                .filter((g) => g.ativo || grupoIds.includes(g.id))
                .sort((a, b) => compararNomes(a.nome, b.nome));
            especialidades = (listaEspecialidades as Especialidade[])
                .filter((e) => e.ativo || especialidadeIds.includes(e.id))
                .sort((a, b) => compararNomes(a.nome, b.nome));
            locais = listaLocais.sort((a, b) => compararNomes(a.nomeLocal, b.nomeLocal));
            const invalidos: Operador[] = config.operadores
                .filter((o: { podeOperar: boolean }) => !o.podeOperar)
                .map((o: { id: number; nome: string }) => ({ id: o.id, nome: o.nome, unidadeLotacao: null, invalido: true }));
            operadores = [...invalidos, ...(listaOperadores as Operador[]).filter((o) => !invalidos.some((i) => i.id === o.id))];
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Erro ao carregar a parametrização.');
            goto('/admin/unidades');
        } finally {
            carregando = false;
        }
    }

    function alternar(lista: number[], id: number): number[] {
        return lista.includes(id) ? lista.filter((x) => x !== id) : [...lista, id];
    }

    async function salvar() {
        salvando = true;
        try {
            // Avulsa coberta por grupo marcado nao vai: o backend tambem a descartaria.
            const avulsas = especialidadeIds.filter((id) => {
                const e = especialidades.find((x) => x.id === id);
                return !e || !cobertaPorGrupo(e);
            });
            const salvo = await salvarUnidadeExecutante(unidadeId, {
                grupoIds,
                especialidadeIds: avulsas,
                localIds,
                usuarioIds
            });
            toast.success(
                salvo.configurada
                    ? 'Parametrização salva. Os operadores vinculados passam a ver só os atendimentos desta executante.'
                    : 'Parametrização salva. Ainda incompleta: os operadores não veem atendimentos desta executante.'
            );
            await carregar();
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Erro ao salvar a parametrização.');
        } finally {
            salvando = false;
        }
    }
</script>

<Content titleH1="Parametrizar unidade executante" page="/admin/unidades">
    <SirgDesign>
        <div class="space-y-6 p-4 md:p-6 lg:p-8">
            {#if carregando}
                <LoadingSpinner mensagem="Carregando a parametrização..." />
            {:else}
                <section aria-labelledby="titulo-unidade" class="sd-cabecalho">
                    <div class="min-w-0 space-y-1">
                        <p class="sd-nota">Unidade executante</p>
                        <h2 id="titulo-unidade" class="sd-titulo break-words">{unidadeNome}</h2>
                        <p class="sd-texto-apoio">
                            {grupoIds.length} grupo(s) · {especialidadeIds.length} especialidade(s) avulsa(s) ·
                            {localIds.length} local(is) · {usuarioIds.length} operador(es)
                        </p>
                    </div>
                    <div class="flex flex-wrap items-center gap-2">
                        {#if resumoPapel}<TagPapelUnidade resumo={resumoPapel} />{/if}
                        <span class="sd-selo {configurada ? 'sd-selo--ok' : 'sd-selo--aviso'}">
                            {configurada ? 'Configurada' : 'Incompleta'}
                        </span>
                    </div>
                </section>

                {#if !ficaraConfigurada}
                    <p class="sd-aviso">
                        <strong class="font-semibold">Parametrização incompleta.</strong>
                        Para restringir os operadores, marque ao menos um local de agendamento e ao menos um grupo ou
                        especialidade. Enquanto estiver incompleta, eles não veem os atendimentos desta executante.
                    </p>
                {/if}

                <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
                    <section aria-labelledby="sec-grupos" class="sd-painel">
                        <div class="sd-painel-topo ex-topo">
                            <div class="min-w-0">
                                <h3 id="sec-grupos" class="sd-titulo">Grupos realizados</h3>
                                <p class="sd-nota">Todas as especialidades do grupo passam a ser desta executante.</p>
                                <p class="sd-nota">
                                    Um grupo ou especialidade pode estar em mais de uma executante. Nesse caso, o
                                    agendamento sem local não aparece para os operadores de nenhuma delas.
                                </p>
                            </div>
                            <span class="sd-contador">{grupoIds.length} marcado(s)</span>
                        </div>
                        <div class="sd-painel-corpo">
                            <ul aria-labelledby="sec-grupos" class="ex-lista">
                                {#each grupos as g (g.id)}
                                    <li>
                                        <label class="ex-item">
                                            <input
                                                type="checkbox"
                                                checked={grupoIds.includes(g.id)}
                                                onchange={() => (grupoIds = alternar(grupoIds, g.id))}
                                                class="sd-check shrink-0"
                                            />
                                            <span class="min-w-0 flex-1">{g.nome}</span>
                                            {#if !g.ativo}<span class="sd-nota shrink-0">(inativo)</span>{/if}
                                        </label>
                                    </li>
                                {:else}
                                    <li class="ex-vazio">Nenhum grupo cadastrado.</li>
                                {/each}
                            </ul>
                        </div>
                    </section>

                    <section aria-labelledby="sec-especialidades" class="sd-painel">
                        <div class="sd-painel-topo ex-topo">
                            <div class="min-w-0">
                                <h3 id="sec-especialidades" class="sd-titulo">Especialidades avulsas</h3>
                                <p class="sd-nota">Para quando a executante realiza só parte de um grupo.</p>
                            </div>
                            <span class="sd-contador">{especialidadeIds.length} marcada(s)</span>
                        </div>
                        <div class="sd-painel-corpo space-y-3">
                            <div class="sd-campo-grupo">
                                <label for="filtro-especialidade" class="sd-rotulo">Filtrar por especialidade ou grupo</label>
                                <input
                                    id="filtro-especialidade"
                                    type="search"
                                    bind:value={filtroEspecialidade}
                                    autocomplete="off"
                                    class="sd-campo"
                                />
                            </div>
                            <ul aria-labelledby="sec-especialidades" class="ex-lista">
                                {#each especialidadesVisiveis as e (e.id)}
                                    {@const coberta = cobertaPorGrupo(e)}
                                    <li>
                                        <label class="ex-item" class:ex-item--coberta={coberta}>
                                            <input
                                                type="checkbox"
                                                checked={coberta || especialidadeIds.includes(e.id)}
                                                disabled={coberta}
                                                onchange={() => (especialidadeIds = alternar(especialidadeIds, e.id))}
                                                class="sd-check shrink-0"
                                            />
                                            <span class="min-w-0 flex-1">{e.nome}</span>
                                            <span class="sd-nota shrink-0 text-right">
                                                {coberta ? 'incluída pelo grupo' : (e.grupoRelatorio?.nome ?? 'sem grupo')}
                                            </span>
                                        </label>
                                    </li>
                                {:else}
                                    <li class="ex-vazio">Nenhuma especialidade encontrada.</li>
                                {/each}
                            </ul>
                        </div>
                    </section>

                    <section aria-labelledby="sec-locais" class="sd-painel">
                        <div class="sd-painel-topo ex-topo">
                            <div class="min-w-0">
                                <h3 id="sec-locais" class="sd-titulo">Locais de agendamento</h3>
                                <p class="sd-nota">
                                    Onde esta executante atende. Um local marcado aqui que já esteja em outra executante é
                                    movido para esta.
                                </p>
                            </div>
                            <span class="sd-contador">{localIds.length} marcado(s)</span>
                        </div>
                        <div class="sd-painel-corpo space-y-3">
                            <div class="sd-campo-grupo">
                                <label for="filtro-local" class="sd-rotulo">Filtrar por local ou cidade</label>
                                <input
                                    id="filtro-local"
                                    type="search"
                                    bind:value={filtroLocal}
                                    autocomplete="off"
                                    class="sd-campo"
                                />
                            </div>
                            <ul aria-labelledby="sec-locais" class="ex-lista">
                                {#each locaisVisiveis as l (l.id)}
                                    {@const deOutra = l.unidadeExecutanteId != null && l.unidadeExecutanteId !== unidadeId}
                                    <li>
                                        <label class="ex-item">
                                            <input
                                                type="checkbox"
                                                checked={localIds.includes(l.id)}
                                                onchange={() => (localIds = alternar(localIds, l.id))}
                                                class="sd-check shrink-0"
                                            />
                                            <span class="min-w-0 flex-1">{l.nomeLocal}</span>
                                            {#if deOutra}
                                                {#if localIds.includes(l.id)}
                                                    <span class="sd-selo sd-selo--aviso shrink-0">
                                                        será movido de {l.unidadeExecutanteNome}
                                                    </span>
                                                {:else}
                                                    <span class="sd-nota shrink-0 text-right">em {l.unidadeExecutanteNome}</span>
                                                {/if}
                                            {:else if l.cidadeNome}
                                                <span class="sd-nota shrink-0 text-right">{l.cidadeNome}</span>
                                            {/if}
                                        </label>
                                    </li>
                                {:else}
                                    <li class="ex-vazio">Nenhum local encontrado.</li>
                                {/each}
                            </ul>
                        </div>
                    </section>

                    <section aria-labelledby="sec-operadores" class="sd-painel">
                        <div class="sd-painel-topo ex-topo">
                            <div class="min-w-0">
                                <h3 id="sec-operadores" class="sd-titulo">Operadores</h3>
                                <p class="sd-nota">
                                    Usuários com perfil Usuário Padrão que atendem nesta executante. Um operador pode estar
                                    em mais de uma.
                                </p>
                            </div>
                            <span class="sd-contador">{usuarioIds.length} marcado(s)</span>
                        </div>
                        <div class="sd-painel-corpo space-y-3">
                            <div class="sd-campo-grupo">
                                <label for="filtro-operador" class="sd-rotulo">Filtrar por nome ou lotação</label>
                                <input
                                    id="filtro-operador"
                                    type="search"
                                    bind:value={filtroOperador}
                                    autocomplete="off"
                                    class="sd-campo"
                                />
                            </div>
                            <ul aria-labelledby="sec-operadores" class="ex-lista">
                                {#each operadoresVisiveis as o (o.id)}
                                    <li>
                                        <label class="ex-item">
                                            <input
                                                type="checkbox"
                                                checked={usuarioIds.includes(o.id)}
                                                onchange={() => (usuarioIds = alternar(usuarioIds, o.id))}
                                                class="sd-check shrink-0"
                                            />
                                            <span class="min-w-0 flex-1">{o.nome}</span>
                                            {#if o.invalido}
                                                <span class="sd-selo sd-selo--erro ex-selo-quebra shrink-0">
                                                    sem perfil Usuário Padrão ou inativo — desmarque
                                                </span>
                                            {:else if o.unidadeLotacao}
                                                <span class="sd-nota shrink-0 text-right">{o.unidadeLotacao}</span>
                                            {/if}
                                        </label>
                                    </li>
                                {:else}
                                    <li class="ex-vazio">Nenhum usuário com perfil Usuário Padrão.</li>
                                {/each}
                            </ul>
                        </div>
                    </section>
                </div>

                <div class="ex-barra sticky bottom-4 z-10 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <p aria-live="polite">
                        <span class="sd-selo {ficaraConfigurada ? 'sd-selo--ok' : 'sd-selo--aviso'} ex-selo-quebra">
                            {ficaraConfigurada
                                ? 'Ao salvar, a parametrização ficará completa.'
                                : 'Ao salvar, a parametrização ficará incompleta.'}
                        </span>
                    </p>
                    <div class="flex flex-col-reverse gap-3 sm:flex-row">
                        <button type="button" onclick={() => goto('/admin/unidades')} class="sd-btn sd-btn--neutro">
                            Voltar
                        </button>
                        <button
                            type="button"
                            onclick={salvar}
                            disabled={salvando}
                            aria-busy={salvando}
                            class="sd-btn sd-btn--primario"
                        >
                            {salvando ? 'Salvando...' : 'Salvar parametrização'}
                        </button>
                    </div>
                </div>
            {/if}
        </div>
    </SirgDesign>
</Content>

<style>
    /* Topo dos painéis: contador alinhado ao título, não à base do texto de apoio. */
    .sd-painel-topo.ex-topo {
        align-items: flex-start;
        flex-wrap: nowrap;
    }
    .sd-painel-topo.ex-topo :global(.sd-contador) {
        flex: none;
        white-space: nowrap;
    }

    /* Lista de marcação: contorno de 1 px, rolagem própria (todos os itens seguem acessíveis). */
    .ex-lista {
        max-height: 20rem;
        overflow-y: auto;
        border: 1px solid var(--sd-linha);
    }
    .ex-lista > li + li {
        border-top: 1px solid var(--sd-linha);
    }
    .ex-item {
        display: flex;
        align-items: center;
        gap: 12px;
        min-height: 40px;
        padding: 8px 12px;
        cursor: pointer;
    }
    .ex-item:hover {
        background: var(--sd-linha-hover);
    }
    .ex-item:has(:checked) {
        background: var(--sd-sirg-50);
        box-shadow: inset 3px 0 0 var(--sd-sirg-700);
    }
    .ex-item:has(:focus-visible) {
        outline: 2px solid var(--sd-acento);
        outline-offset: -2px;
    }
    .ex-item--coberta,
    .ex-item--coberta:hover,
    .ex-item--coberta:has(:checked) {
        background: var(--sd-fundo);
        box-shadow: none;
        color: var(--sd-tinta-suave);
        cursor: not-allowed;
    }
    .ex-vazio {
        padding: 16px 12px;
        color: var(--sd-tinta-suave);
    }
    /* Selo com frase longa: pode quebrar linha em vez de estourar a largura. */
    .sd-selo.ex-selo-quebra {
        white-space: normal;
    }

    /* Barra de ações fixa ao rolar: fundo branco e filete emerald no topo. */
    .ex-barra {
        padding: 16px;
        border-top: 2px solid var(--sd-sirg-600);
        border-radius: var(--sd-raio-painel);
        background: var(--sd-camada);
        box-shadow: 0 10px 15px -3px rgb(0 0 0 / 0.1), 0 4px 6px -4px rgb(0 0 0 / 0.1);
    }
</style>
