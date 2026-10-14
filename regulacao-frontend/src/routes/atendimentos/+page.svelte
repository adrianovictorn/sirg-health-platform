<script lang="ts">
    import { goto } from '$app/navigation';
    import { page } from '$app/state';
    import Content from '$lib/Content.svelte';
    import { dataHojeBahia } from '$lib/agendaDiaConsolidada.js';
    import { hrefDaAgendaDoGrupo } from '$lib/menuConfig.js';
    import { user } from '$lib/stores/auth.js';
    import {
        PERIODO_MAXIMO_RELATORIO_DIAS,
        ROTULO_STATUS_ATENDIMENTO,
        ROTULO_TURNO,
        STATUS_DO_FILTRO,
        baixarRelatorioAtendimentos,
        cancelarAtendimento,
        carregarAgendasAtendimento,
        carregarResumoAtendimentos,
        confirmarAtendimentosEmLote,
        desfazerAtendimento,
        informarDataColetaAtendimento,
        listarAtendimentos,
        registrarFaltaAtendimento
    } from '$lib/atendimentosApi.js';
    import { formatarData } from '$lib/datas.js';
    import { onMount } from 'svelte';
    import { toast } from 'svelte-sonner';

    /**
     * Atendimentos — tela de trabalho do operador da unidade executante (Usuario
     * Padrao): a lista do dia, um bloco por paciente, sem filtro obrigatorio por
     * unidade. Os quantitativos ficam em /atendimentos/quantitativos. O backend
     * (api/atendimentos/**) so responde a USER e ADMIN.
     *
     * URL: `?grupo=<codigo>` restringe a um Grupo de Relatorio (os itens do menu
     * "Agendas" apontam para ca); `data`, `unidadeId` e `semUnidade` abrem a tela ja
     * filtrada — e o que a tela de Quantitativos usa ao clicar numa unidade.
     */

    type GrupoOpcao = { codigo: string; nome: string };

    type UnidadeOpcao = { id: number | null; nome: string };
    // semExecutante: USER sem executante configurada (ADR 0013) — a lista vem vazia
    // porque ele nao ve nada, nao porque o dia esta vazio.
    type Resumo = { data: string; unidades: UnidadeOpcao[]; semExecutante: boolean };
    type Atendimento = {
        id: number;
        solicitacaoId: number;
        especialidadeNome: string | null;
        categoria: string | null;
        status: string;
        turno: string | null;
        horaAgendada: string | null;
        dataColeta: string | null;
        observacao: string | null;
        registradoPorNome: string | null;
        registradoEm: string | null;
        podeDesfazer: boolean;
    };
    /** Um paciente (ficha) do dia, com CPF e CNS completos (ADR 0010), e os itens dele. */
    type Paciente = {
        solicitacaoId: number;
        nomePaciente: string;
        cpf: string | null;
        cns: string | null;
        dataNascimento: string | null;
        unidadeNome: string | null;
        itens: Atendimento[];
    };
    type Desfecho = 'falta' | 'cancelar';

    // Pacientes por pagina (cada um pode ter varios itens).
    const TAMANHO_PAGINA = 20;
    const ESPERA_DA_BUSCA_MS = 350;
    // Valor do filtro de unidade para as solicitacoes sem unidade vinculada.
    const SEM_UNIDADE = 'sem-unidade';

    // Filtros que podem chegar pela URL (vindos de Quantitativos). Lidos uma vez:
    // depois disso a tela e dona do proprio estado. Valor invalido e ignorado.
    const parametros = page.url.searchParams;
    const dataDaUrl = parametros.get('data') ?? '';
    const unidadeDaUrl = parametros.get('unidadeId') ?? '';

    let data = $state(/^\d{4}-\d{2}-\d{2}$/.test(dataDaUrl) ? dataDaUrl : dataHojeBahia());

    // O grupo vive na URL, nao em estado local: o menu "Agendas" navega para ca
    // trocando so a query string, e a tela precisa acompanhar.
    const grupo = $derived(page.url.searchParams.get('grupo') ?? '');
    let grupos = $state<GrupoOpcao[]>([]);
    const nomeDoGrupo = $derived(grupos.find((g) => g.codigo === grupo)?.nome ?? grupo);
    // No menu, as agendas so existem para o USER; os demais ficam no item da tela.
    const paginaAtiva = $derived(grupo && $user?.role === 'USER' ? hrefDaAgendaDoGrupo(grupo) : '/atendimentos');

    // O resumo do dia so alimenta as opcoes do seletor de unidade: os numeros
    // estao em /atendimentos/quantitativos.
    let resumo = $state<Resumo | null>(null);

    let filtroUnidade = $state<string>(
        parametros.get('semUnidade') === 'true' ? SEM_UNIDADE : /^\d+$/.test(unidadeDaUrl) ? unidadeDaUrl : ''
    );
    let filtroStatus = $state<string>('');
    let busca = $state('');
    let termo = $state('');

    let pacientes = $state<Paciente[]>([]);
    let pagina = $state(0);
    let totalPaginas = $state(0);
    let totalPacientes = $state(0);
    let carregandoItens = $state(true);
    let erroItens = $state<string | null>(null);

    let tokenResumo = 0;
    let tokenItens = 0;

    // So o Usuario Padrao registra o atendimento; o ADMIN acompanha a tela. O
    // backend aplica a mesma regra — aqui e so para nao oferecer o que da 403.
    const podeRegistrar = $derived($user?.role === 'USER');
    // Uma acao por vez: o id do item com requisicao em andamento, ou o marcador
    // do lote (`lote-<solicitacaoId>`) enquanto "Confirmar marcados" roda.
    let ocupado = $state<number | string | null>(null);
    // Itens AGENDADO vem marcados para "Confirmar marcados"; aqui ficam as excecoes.
    let desmarcados = $state<Record<number, true>>({});
    // Progresso do lote em andamento, para o operador ver que nao travou.
    let progressoLote = $state<{ solicitacaoId: number; feitos: number; total: number } | null>(null);
    // Falta e cancelamento pedem texto antes de gravar: painel aberto numa linha.
    let painel = $state<{ id: number; tipo: Desfecho } | null>(null);
    let textoDoPainel = $state('');
    // Data da coleta em edicao, por item (o valor gravado so muda ao salvar).
    let coletas = $state<Record<number, string>>({});

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
    const temFiltro = $derived(filtroUnidade !== '' || filtroStatus !== '' || termo !== '');
    const temSemUnidade = $derived(!!resumo?.unidades.some((u) => u.id === null));
    // Unidade que chegou pela URL e ainda nao esta nas opcoes (o resumo nao
    // carregou, ou a unidade nao tem atendimento): o seletor mostra mesmo assim.
    const unidadeForaDasOpcoes = $derived(
        filtroUnidade !== '' && filtroUnidade !== SEM_UNIDADE && !resumo?.unidades.some((u) => String(u.id) === filtroUnidade)
    );

    function chaveDaUnidade(u: UnidadeOpcao): string {
        return u.id === null ? SEM_UNIDADE : String(u.id);
    }

    /** So para as opcoes do seletor de unidade. Falhar aqui nao impede a lista. */
    async function carregarResumo() {
        const meu = ++tokenResumo;
        try {
            const corpo: Resumo = await carregarResumoAtendimentos({ data, grupo });
            if (meu !== tokenResumo) return; // resposta de uma consulta ja superada
            resumo = corpo;
        } catch {
            // Sem opcoes novas, o seletor fica com as ultimas carregadas.
        }
    }

    async function carregarItens(numeroPagina: number) {
        const meu = ++tokenItens;
        carregandoItens = true;
        erroItens = null;
        // A lista vai trocar: painel, datas em edicao e marcacoes pertenciam as linhas antigas.
        painel = null;
        textoDoPainel = '';
        coletas = {};
        desmarcados = {};
        try {
            const corpo = await listarAtendimentos({
                data,
                grupo,
                unidadeId: filtroUnidade !== '' && filtroUnidade !== SEM_UNIDADE ? filtroUnidade : null,
                semUnidade: filtroUnidade === SEM_UNIDADE,
                status: filtroStatus,
                termo,
                page: numeroPagina,
                size: TAMANHO_PAGINA
            });
            if (meu !== tokenItens) return;
            pacientes = corpo.content ?? [];
            pagina = corpo.number ?? numeroPagina;
            totalPaginas = corpo.totalPages ?? 0;
            totalPacientes = corpo.totalElements ?? 0;
        } catch (e) {
            if (meu !== tokenItens) return;
            erroItens = e instanceof Error ? e.message : 'Erro ao conectar ao servidor.';
            pacientes = [];
            totalPaginas = 0;
            totalPacientes = 0;
        } finally {
            if (meu === tokenItens) carregandoItens = false;
        }
    }

    // ------------------------------------------------------------------
    // Desfecho do atendimento
    // ------------------------------------------------------------------

    /** Troca, dentro do bloco do paciente, os itens pela versao que o servidor devolveu. */
    function substituirItens(atualizados: Atendimento[]) {
        const porId = new Map(atualizados.map((a) => [a.id, a]));
        pacientes = pacientes.map((p) =>
            p.itens.some((i) => porId.has(i.id)) ? { ...p, itens: p.itens.map((i) => porId.get(i.id) ?? i) } : p
        );
    }

    function nomeDe(item: Atendimento): string {
        return pacientes.find((p) => p.solicitacaoId === item.solicitacaoId)?.nomePaciente ?? 'paciente';
    }

    /** Roda a acao de um item e troca a linha pela resposta do servidor. */
    async function executar(item: Atendimento, chamada: () => Promise<Atendimento>, sucesso: string) {
        if (ocupado !== null) return;
        ocupado = item.id;
        try {
            const atualizado = await chamada();
            substituirItens([atualizado]);
            delete coletas[item.id];
            painel = null;
            textoDoPainel = '';
            toast.success(sucesso);
        } catch (e) {
            // 409: outro operador mexeu no item antes. A mensagem do servidor explica.
            toast.error(e instanceof Error ? e.message : 'Erro ao conectar ao servidor.');
        } finally {
            ocupado = null;
        }
    }

    /** Itens AGENDADO do paciente que estao marcados (o padrao e marcado). */
    function marcadosDe(p: Paciente): Atendimento[] {
        return p.itens.filter((i) => i.status === 'AGENDADO' && !desmarcados[i.id]);
    }

    function alternarMarcado(item: Atendimento, marcado: boolean) {
        if (marcado) delete desmarcados[item.id];
        else desmarcados[item.id] = true;
    }

    /**
     * Confirma os itens marcados do paciente, um de cada vez. O que gravou fica
     * gravado; o que falhou (outro operador ja tinha mexido, por exemplo) e listado,
     * e a pagina e recarregada porque aquele item esta defasado na tela.
     */
    async function confirmarMarcados(p: Paciente) {
        const alvo = marcadosDe(p);
        if (ocupado !== null || alvo.length === 0) return;
        ocupado = `lote-${p.solicitacaoId}`;
        progressoLote = { solicitacaoId: p.solicitacaoId, feitos: 0, total: alvo.length };
        try {
            const { ok, falhas } = await confirmarAtendimentosEmLote(
                alvo.map((i) => i.id),
                (feitos: number, total: number) => (progressoLote = { solicitacaoId: p.solicitacaoId, feitos, total })
            );
            if (ok.length > 0) {
                substituirItens(ok);
                toast.success(
                    ok.length === 1
                        ? `1 atendimento de ${p.nomePaciente} confirmado.`
                        : `${ok.length} atendimentos de ${p.nomePaciente} confirmados.`
                );
            }
            if (falhas.length > 0) {
                const nomeDoItem = (id: number) => alvo.find((i) => i.id === id)?.especialidadeNome ?? `item ${id}`;
                toast.error(
                    `Não confirmado${falhas.length > 1 ? 's' : ''}: ` +
                        falhas.map((f: { id: number; mensagem: string }) => `${nomeDoItem(f.id)} — ${f.mensagem}`).join('; ')
                );
                await carregarItens(pagina);
            }
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Erro ao conectar ao servidor.');
            await carregarItens(pagina);
        } finally {
            ocupado = null;
            progressoLote = null;
        }
    }

    function abrirPainel(item: Atendimento, tipo: Desfecho) {
        painel = { id: item.id, tipo };
        textoDoPainel = '';
    }

    function fecharPainel() {
        painel = null;
        textoDoPainel = '';
    }

    function gravarPainel(item: Atendimento) {
        if (!painel) return;
        const texto = textoDoPainel.trim();
        if (painel.tipo === 'cancelar') {
            if (!texto) return;
            executar(item, () => cancelarAtendimento(item.id, texto), `${item.especialidadeNome ?? 'Atendimento'} de ${nomeDe(item)} cancelado.`);
        } else {
            executar(item, () => registrarFaltaAtendimento(item.id, texto || null), `Falta de ${nomeDe(item)} registrada.`);
        }
    }

    function desfazer(item: Atendimento) {
        executar(item, () => desfazerAtendimento(item.id), `Registro de ${nomeDe(item)} desfeito.`);
    }

    function salvarColeta(item: Atendimento) {
        const valor = coletas[item.id] ?? '';
        executar(
            item,
            () => informarDataColetaAtendimento(item.id, valor || null),
            valor ? 'Data da coleta salva.' : 'Data da coleta removida.'
        );
    }

    /** A coleta so e editavel enquanto o atendimento esta agendado ou confirmado. */
    function coletaEditavel(item: Atendimento): boolean {
        return podeRegistrar && item.categoria === 'EXAME_OU_PROCEDIMENTO' && (item.status === 'AGENDADO' || item.status === 'REALIZADO');
    }

    /** Instante gravado pelo servidor (UTC), exibido no horario da Bahia. */
    function formatarRegistro(instante: string | null): string {
        if (!instante) return '';
        const d = new Date(instante);
        if (Number.isNaN(d.getTime())) return '';
        return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short', timeZone: 'America/Bahia' }).format(d);
    }

    // ------------------------------------------------------------------
    // Relatorio de Atendimento (Excel)
    // ------------------------------------------------------------------

    let relatorioInicio = $state(dataHojeBahia());
    let relatorioFim = $state(dataHojeBahia());
    let gerandoRelatorio = $state(false);

    /** Dias do periodo, contando o primeiro e o ultimo; 0 se alguma data estiver vazia. */
    const diasDoRelatorio = $derived.by(() => {
        if (!relatorioInicio || !relatorioFim) return 0;
        const [ai, mi, di] = relatorioInicio.split('-').map(Number);
        const [af, mf, df] = relatorioFim.split('-').map(Number);
        return Math.round((Date.UTC(af, mf - 1, df) - Date.UTC(ai, mi - 1, di)) / 86_400_000) + 1;
    });
    const erroDoPeriodo = $derived.by(() => {
        if (!relatorioInicio || !relatorioFim) return 'Informe o início e o fim do período.';
        if (diasDoRelatorio < 1) return 'O fim do período não pode ser anterior ao início.';
        if (diasDoRelatorio > PERIODO_MAXIMO_RELATORIO_DIAS) return `O período é de no máximo ${PERIODO_MAXIMO_RELATORIO_DIAS} dias.`;
        return null;
    });

    async function gerarRelatorio() {
        if (erroDoPeriodo || gerandoRelatorio) return;
        gerandoRelatorio = true;
        try {
            const blob = await baixarRelatorioAtendimentos({ inicio: relatorioInicio, fim: relatorioFim, grupo });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `relatorio_atendimentos_${relatorioInicio}_a_${relatorioFim}.xlsx`;
            document.body.appendChild(a);
            a.click();
            a.remove();
            URL.revokeObjectURL(url);
            toast.success('Relatório gerado.');
        } catch (e) {
            toast.error(e instanceof Error ? e.message : 'Erro ao conectar ao servidor.');
        } finally {
            gerandoRelatorio = false;
        }
    }

    function irParaPagina(n: number) {
        if (n >= 0 && n < totalPaginas) carregarItens(n);
    }

    function limparFiltros() {
        filtroUnidade = '';
        filtroStatus = '';
        busca = '';
        termo = '';
    }

    function irParaHoje() {
        data = dataHojeBahia();
    }

    function escolherGrupo(codigo: string) {
        goto(codigo ? hrefDaAgendaDoGrupo(codigo) : '/atendimentos', { keepFocus: true, noScroll: true });
    }

    // Mesma fonte do menu "Agendas" (/api/atendimentos/agendas), ja no escopo de
    // quem esta logado. Se falhar, o seletor so mostra o grupo da URL.
    async function carregarGrupos() {
        try {
            const lista: GrupoOpcao[] = await carregarAgendasAtendimento();
            grupos = lista.map((g) => ({ codigo: g.codigo, nome: g.nome }));
        } catch {
            // Sem a lista, o filtro por grupo continua funcionando pelo menu.
        }
    }

    // Trocar a data pode tirar "Sem unidade" das opcoes: sem isto o filtro seguiria
    // aplicado com o seletor em branco.
    $effect(() => {
        if (resumo && filtroUnidade === SEM_UNIDADE && !temSemUnidade) filtroUnidade = '';
    });

    // A busca so vira consulta depois de uma pausa na digitacao.
    $effect(() => {
        const digitado = busca.trim();
        const id = setTimeout(() => (termo = digitado), ESPERA_DA_BUSCA_MS);
        return () => clearTimeout(id);
    });

    // Campo de data limpo: nao consulta (o backend assumiria "hoje" e a tela
    // mostraria outra coisa) e mantem o ultimo resultado ate haver data valida.
    $effect(() => {
        if (!data) return;
        grupo;
        carregarResumo();
    });

    $effect(() => {
        if (!data) return;
        grupo;
        filtroUnidade;
        filtroStatus;
        termo;
        carregarItens(0);
    });

    // A lista nao se atualiza sozinha: trocaria debaixo do operador.
    onMount(() => {
        carregarGrupos();
    });

    // So exibicao: pontua CPF (000.000.000-00) e CNS (000 0000 0000 0000) para a
    // leitura; o valor guardado nao muda. Fora do tamanho esperado, mostra como veio.
    function exibirCpf(cpf: string | null): string {
        if (!cpf) return '—';
        const d = cpf.replace(/\D/g, '');
        return d.length === 11 ? `${d.slice(0, 3)}.${d.slice(3, 6)}.${d.slice(6, 9)}-${d.slice(9)}` : cpf;
    }

    function exibirCns(cns: string | null): string {
        if (!cns) return '—';
        const d = cns.replace(/\D/g, '');
        return d.length === 15 ? `${d.slice(0, 3)} ${d.slice(3, 7)} ${d.slice(7, 11)} ${d.slice(11)}` : cns;
    }

    function classeDoStatus(status: string): string {
        if (status === 'REALIZADO') return 'bg-emerald-100 text-emerald-900';
        if (status === 'AGENDADO') return 'bg-blue-100 text-blue-900';
        if (status === 'FALTOU') return 'bg-amber-100 text-amber-900';
        if (status === 'CANCELADO') return 'bg-red-100 text-red-900';
        return 'bg-gray-200 text-gray-800';
    }
</script>

<svelte:head>
    <title>Atendimentos</title>
</svelte:head>

<Content titleH1="Atendimentos" page={paginaAtiva}>
    <div class="cotas-ibm min-w-0 flex-1 space-y-6 p-4 md:p-6">
        <header class="at-cabecalho">
            <div class="min-w-0">
                <h2 class="at-titulo">
                    {grupo ? `${nomeDoGrupo} — atendimentos do dia` : 'Atendimentos do dia — todas as agendas'}
                </h2>
                <p class="at-data">{dataPorExtenso}</p>
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

        <!-- Lista nominal: um bloco por paciente -->
        <section aria-labelledby="lista-titulo" class="ibm-painel">
            <div class="at-painel-topo">
                <h3 id="lista-titulo" class="ibm-grupo-titulo">
                    Pacientes do dia
                    <span class="at-contador">{totalPacientes}</span>
                </h3>
                {#if podeRegistrar}
                    <p class="at-topo-nota">Marque o que foi feito e confirme por paciente. Falta e cancelamento são registrados item a item.</p>
                {/if}
            </div>
            <form class="at-filtros grid grid-cols-1 gap-4 p-4 sm:grid-cols-2 lg:grid-cols-[2fr_1fr_1fr_auto]" onsubmit={(e) => e.preventDefault()} aria-label="Filtros da lista de pacientes">
                <label class="at-campo-grupo">
                    <span class="ibm-rotulo">Buscar paciente</span>
                    <input type="search" bind:value={busca} placeholder="Nome, CPF ou CNS completo" autocomplete="off" class="ibm-campo w-full" />
                </label>
                <label class="at-campo-grupo">
                    <span class="ibm-rotulo">Unidade solicitante</span>
                    <select bind:value={filtroUnidade} class="ibm-campo w-full">
                        <option value="">Todas</option>
                        {#each resumo?.unidades ?? [] as u (chaveDaUnidade(u))}
                            {#if u.id !== null}<option value={String(u.id)}>{u.nome}</option>{/if}
                        {/each}
                        {#if unidadeForaDasOpcoes}<option value={filtroUnidade}>Unidade selecionada</option>{/if}
                        {#if temSemUnidade || filtroUnidade === SEM_UNIDADE}<option value={SEM_UNIDADE}>Sem unidade</option>{/if}
                    </select>
                </label>
                <label class="at-campo-grupo">
                    <span class="ibm-rotulo">Situação</span>
                    <select bind:value={filtroStatus} class="ibm-campo w-full">
                        <option value="">Todas</option>
                        {#each STATUS_DO_FILTRO as s (s)}<option value={s}>Com algum {ROTULO_STATUS_ATENDIMENTO[s].toLowerCase()}</option>{/each}
                    </select>
                </label>
                <div class="flex items-end">
                    <button type="button" class="ibm-btn ibm-btn--fantasma" onclick={limparFiltros} disabled={!temFiltro && busca === ''}>
                        Limpar filtros
                    </button>
                </div>
            </form>

            <div class="p-4" aria-live="polite">
                {#if carregandoItens && pacientes.length === 0}
                    <p class="at-carregando" role="status">
                        <span class="inline-block h-4 w-4 animate-spin rounded-full border-2 border-emerald-600 border-t-transparent" aria-hidden="true"></span>
                        Carregando pacientes…
                    </p>
                {:else if erroItens}
                    <div class="at-erro" role="alert">
                        <span>{erroItens}</span>
                        <button type="button" class="ibm-btn ibm-btn--secundario" onclick={() => carregarItens(pagina)}>Tentar novamente</button>
                    </div>
                {:else if pacientes.length === 0 && resumo?.semExecutante}
                    <p class="at-vazio" role="status">
                        Seu usuário não está ligado a nenhuma unidade executante configurada. Procure o administrador.
                    </p>
                {:else if pacientes.length === 0}
                    <p class="at-vazio">
                        {temFiltro
                            ? 'Nenhum paciente encontrado com os filtros escolhidos.'
                            : 'Nenhum atendimento agendado para esta data.'}
                    </p>
                {:else}
                    <ul class="at-pacientes {carregandoItens ? 'opacity-60' : ''}">
                        {#each pacientes as p (p.solicitacaoId)}
                            {@const marcados = marcadosDe(p)}
                            {@const agendados = p.itens.filter((i) => i.status === 'AGENDADO')}
                            {@const emLote = ocupado === `lote-${p.solicitacaoId}`}
                            <li class="at-paciente" class:at-paciente--lote={emLote} aria-labelledby="paciente-{p.solicitacaoId}">
                                <div class="at-paciente-topo">
                                    <div class="min-w-0">
                                        <h4 id="paciente-{p.solicitacaoId}" class="at-paciente-nome">{p.nomePaciente}</h4>
                                        <dl class="at-paciente-dados">
                                            <div><dt>Nascimento</dt><dd>{formatarData(p.dataNascimento)}</dd></div>
                                            <div><dt>CPF</dt><dd class="at-doc">{exibirCpf(p.cpf)}</dd></div>
                                            <div><dt>CNS</dt><dd class="at-doc">{exibirCns(p.cns)}</dd></div>
                                            <div><dt>Unidade solicitante</dt><dd>{p.unidadeNome ?? 'Sem unidade'}</dd></div>
                                        </dl>
                                    </div>
                                    {#if podeRegistrar && agendados.length > 0}
                                        <div class="at-paciente-lote">
                                            <button type="button" class="ibm-btn ibm-btn--primario at-acao" onclick={() => confirmarMarcados(p)} disabled={ocupado !== null || marcados.length === 0} aria-busy={emLote ? 'true' : undefined} aria-label={`Confirmar ${marcados.length} atendimento(s) marcado(s) de ${p.nomePaciente}`}>
                                                {#if emLote}
                                                    <span class="at-girando" aria-hidden="true"></span>
                                                {:else}
                                                    <svg class="at-icone" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="3" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" /></svg>
                                                {/if}
                                                {emLote && progressoLote ? `Confirmando ${progressoLote.feitos} de ${progressoLote.total}…` : `Confirmar marcados (${marcados.length})`}
                                            </button>
                                            {#if !emLote && marcados.length < agendados.length}
                                                <p class="at-paciente-lote-nota">
                                                    {marcados.length === 0 ? 'Nenhum item marcado' : `${agendados.length - marcados.length} desmarcado(s) fica(m) agendado(s)`}
                                                </p>
                                            {/if}
                                        </div>
                                    {:else if podeRegistrar}
                                        <p class="at-paciente-concluido">Nenhum item a confirmar</p>
                                    {/if}
                                </div>

                                <div class="at-tabela-contorno">
                                    <table class="at-tabela at-tabela--nominal" class:at-tabela--acoes={podeRegistrar}>
                                        <caption class="sr-only">Atendimentos de {p.nomePaciente} em {formatarData(data)}</caption>
                                        <thead>
                                            <tr>
                                                {#if podeRegistrar}<th scope="col" class="at-col-marca"><span class="sr-only">Marcar como feito</span></th>{/if}
                                                <th scope="col">Procedimento</th>
                                                <th scope="col">Turno/Hora</th>
                                                <th scope="col">Data da coleta</th>
                                                <th scope="col">Situação</th>
                                                {#if podeRegistrar}<th scope="col">Se não foi feito</th>{/if}
                                            </tr>
                                        </thead>
                                        <tbody>
                                            {#each p.itens as i (i.id)}
                                                {@const desmarcado = podeRegistrar && i.status === 'AGENDADO' && !!desmarcados[i.id]}
                                                <tr class="at-linha" class:at-linha--desmarcada={desmarcado} class:at-linha--fora={filtroStatus !== '' && i.status !== filtroStatus} data-status={i.status}>
                                                    {#if podeRegistrar}
                                                        <td class="at-col-marca">
                                                            {#if i.status === 'AGENDADO'}
                                                                <label class="at-marcar-alvo">
                                                                    <input type="checkbox" class="at-marcar" checked={!desmarcados[i.id]} onchange={(e) => alternarMarcado(i, e.currentTarget.checked)} disabled={ocupado !== null} aria-label={`Marcar ${i.especialidadeNome ?? 'item'} de ${p.nomePaciente} como feito`} />
                                                                </label>
                                                            {/if}
                                                        </td>
                                                    {/if}
                                                    <th scope="row">
                                                        {i.especialidadeNome ?? '—'}
                                                        {#if desmarcado}<span class="at-fora-do-lote">Fora da confirmação</span>{/if}
                                                    </th>
                                                    <td class="whitespace-nowrap">{[i.turno ? (ROTULO_TURNO[i.turno] ?? i.turno) : null, i.horaAgendada?.slice(0, 5)].filter(Boolean).join(' · ') || '—'}</td>
                                                    <td class="whitespace-nowrap">
                                                        {#if i.categoria !== 'EXAME_OU_PROCEDIMENTO'}
                                                            <span class="ibm-suave">Não se aplica</span>
                                                        {:else if coletaEditavel(i)}
                                                            {@const emEdicao = coletas[i.id] ?? i.dataColeta ?? ''}
                                                            <span class="flex items-center gap-2">
                                                                <input type="date" value={emEdicao} oninput={(e) => (coletas[i.id] = e.currentTarget.value)} aria-label={`Data da coleta de ${i.especialidadeNome ?? 'item'} de ${p.nomePaciente}`} disabled={ocupado !== null} class="ibm-campo ibm-campo--compacto" />
                                                                {#if emEdicao !== (i.dataColeta ?? '')}
                                                                    <button type="button" class="ibm-btn ibm-btn--compacto ibm-btn--primario at-acao" onclick={() => salvarColeta(i)} disabled={ocupado !== null}>Salvar</button>
                                                                {/if}
                                                            </span>
                                                        {:else}
                                                            {formatarData(i.dataColeta)}
                                                        {/if}
                                                    </td>
                                                    <td>
                                                        <span class="at-badge {classeDoStatus(i.status)}" data-status={i.status}><span class="at-marca" aria-hidden="true"></span>{ROTULO_STATUS_ATENDIMENTO[i.status] ?? i.status}</span>
                                                        {#if i.observacao}
                                                            <p class="at-obs"><span class="font-semibold">{i.status === 'CANCELADO' ? 'Motivo' : 'Observação'}:</span> {i.observacao}</p>
                                                        {/if}
                                                        {#if i.registradoEm}
                                                            <p class="at-registro">{i.registradoPorNome ?? '—'}, {formatarRegistro(i.registradoEm)}</p>
                                                        {/if}
                                                    </td>
                                                    {#if podeRegistrar}
                                                        <td>
                                                            <div class="at-acoes">
                                                                {#if i.status === 'AGENDADO'}
                                                                    <button type="button" class="ibm-btn ibm-btn--compacto ibm-btn--falta at-acao" onclick={() => abrirPainel(i, 'falta')} disabled={ocupado !== null} aria-expanded={painel?.id === i.id && painel.tipo === 'falta'} aria-label={`Registrar falta em ${i.especialidadeNome ?? 'item'} de ${p.nomePaciente}`}>Faltou</button>
                                                                    <button type="button" class="ibm-btn ibm-btn--compacto ibm-btn--cancela at-acao" onclick={() => abrirPainel(i, 'cancelar')} disabled={ocupado !== null} aria-expanded={painel?.id === i.id && painel.tipo === 'cancelar'} aria-label={`Cancelar ${i.especialidadeNome ?? 'item'} de ${p.nomePaciente}`}>Cancelar</button>
                                                                {:else if i.podeDesfazer}
                                                                    <button type="button" class="ibm-btn ibm-btn--compacto ibm-btn--neutro at-acao" onclick={() => desfazer(i)} disabled={ocupado !== null} aria-label={`Desfazer registro de ${i.especialidadeNome ?? 'item'} de ${p.nomePaciente}`}>Desfazer</button>
                                                                {:else}
                                                                    <span class="ibm-suave">—</span>
                                                                {/if}
                                                            </div>
                                                        </td>
                                                    {/if}
                                                </tr>
                                                {#if painel?.id === i.id}
                                                    {@const cancelando = painel.tipo === 'cancelar'}
                                                    <tr class="at-linha-painel" data-tipo={painel.tipo}>
                                                        <td colspan="6">
                                                            <form class="flex max-w-3xl flex-col gap-2" onsubmit={(e) => { e.preventDefault(); gravarPainel(i); }}>
                                                                <label class="at-campo-grupo">
                                                                    <span class="at-painel-rotulo">{cancelando ? `Motivo do cancelamento de ${i.especialidadeNome ?? 'item'} (obrigatório)` : `Observação da falta em ${i.especialidadeNome ?? 'item'} (opcional)`}</span>
                                                                    <!-- svelte-ignore a11y_autofocus -->
                                                                    <textarea bind:value={textoDoPainel} rows="2" maxlength="500" autofocus required={cancelando} placeholder={cancelando ? 'Ex.: aparelho em manutenção, profissional ausente' : 'Ex.: paciente avisou que não viria'} class="ibm-campo w-full"></textarea>
                                                                </label>
                                                                <p class="at-painel-nota">
                                                                    Não registre aqui dados clínicos do paciente. {cancelando ? 'A vaga continua ocupada e o paciente não é avisado automaticamente.' : 'A vaga continua ocupada.'}
                                                                </p>
                                                                <div class="flex flex-wrap gap-2">
                                                                    <button type="submit" class="ibm-btn {cancelando ? 'ibm-btn--cancela-cheio' : 'ibm-btn--falta-cheio'}" disabled={ocupado !== null || (cancelando && !textoDoPainel.trim())}>
                                                                        {cancelando ? 'Cancelar atendimento' : 'Registrar falta'}
                                                                    </button>
                                                                    <button type="button" class="ibm-btn ibm-btn--neutro" onclick={fecharPainel}>Voltar</button>
                                                                </div>
                                                            </form>
                                                        </td>
                                                    </tr>
                                                {/if}
                                            {/each}
                                        </tbody>
                                    </table>
                                </div>
                            </li>
                        {/each}
                    </ul>
                    {#if totalPaginas > 1}
                        <nav class="at-paginacao" aria-label="Paginação da lista de pacientes">
                            <button type="button" class="ibm-btn ibm-btn--secundario" onclick={() => irParaPagina(pagina - 1)} disabled={pagina === 0}>Anterior</button>
                            <span>Página {pagina + 1} de {totalPaginas}</span>
                            <button type="button" class="ibm-btn ibm-btn--secundario" onclick={() => irParaPagina(pagina + 1)} disabled={pagina + 1 >= totalPaginas}>Próxima</button>
                        </nav>
                    {/if}
                {/if}
            </div>
        </section>

        <!-- Relatorio de Atendimento -->
        <section aria-labelledby="relatorio-titulo" class="ibm-painel">
            <div class="at-painel-topo">
                <h3 id="relatorio-titulo" class="ibm-grupo-titulo">Relatório de Atendimento</h3>
            </div>
            <div class="space-y-4 p-4">
                <p class="at-texto-apoio">
                    Planilha Excel com os atendimentos confirmados, as faltas e os cancelamentos do período, com a data da coleta
                    {#if grupo}— somente da agenda {nomeDoGrupo}{:else}— de todas as agendas{/if}.
                </p>
                <form class="flex flex-wrap items-end gap-4" onsubmit={(e) => { e.preventDefault(); gerarRelatorio(); }}>
                    <label class="at-campo-grupo">
                        <span class="ibm-rotulo">De</span>
                        <input type="date" bind:value={relatorioInicio} required aria-invalid={erroDoPeriodo ? 'true' : undefined} aria-describedby="relatorio-ajuda" class="ibm-campo" />
                    </label>
                    <label class="at-campo-grupo">
                        <span class="ibm-rotulo">Até</span>
                        <input type="date" bind:value={relatorioFim} required aria-invalid={erroDoPeriodo ? 'true' : undefined} aria-describedby="relatorio-ajuda" class="ibm-campo" />
                    </label>
                    <button type="submit" class="ibm-btn ibm-btn--primario" disabled={!!erroDoPeriodo || gerandoRelatorio}>
                        <svg class="at-icone" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" d="M12 4v12m0 0l-4-4m4 4l4-4M5 20h14" /></svg>
                        {gerandoRelatorio ? 'Gerando…' : 'Baixar Excel'}
                    </button>
                    <p id="relatorio-ajuda" class="at-ajuda" aria-live="polite">
                        <span class:at-ajuda-erro={!!erroDoPeriodo}>{erroDoPeriodo ?? `Período de ${diasDoRelatorio} dia(s), no máximo ${PERIODO_MAXIMO_RELATORIO_DIAS}.`}</span>
                        A planilha traz nome, CPF e CNS completos dos pacientes: guarde e compartilhe com cuidado.
                    </p>
                </form>
            </div>
        </section>
    </div>
</Content>

<style>
    /*
     * Mistura SIRG + Carbon (IBM), escopada a esta tela. Copia de
     * /admin/cotas/nova (mesmos nomes de token e de classe `ibm-*`), so com as
     * classes que esta tela usa, mais as `at-*` (cabecalho do dia, lista
     * agrupada por paciente, acoes de registro). Decisao:
     * docs/decisoes/0009-visual-misto-com-mais-sirg-em-atendimentos.md.
     * /atendimentos/quantitativos tem uma copia podada deste bloco.
     *
     * Diferenca para as outras telas mistas: aqui o emerald do SIRG sai do
     * acento e entra tambem nas superficies (tokens `--sirg-*`): faixa de topo
     * dos paineis, cabecalho das tabelas, hover de linha, rotulo dos campos e
     * botao secundario. Para voltar ao misto de `nova`, troque os valores da
     * secao "Mais SIRG" pelos indicados em "nova".
     * A fonte e uma pilha com fallback: nada e baixado.
     */
    .cotas-ibm {
        --ibm-acento: #047857;
        --ibm-acento-hover: #065f46;
        --ibm-acento-ativo: #064e3b;
        --ibm-fonte: 'IBM Plex Sans', 'Helvetica Neue', Arial, sans-serif;

        --ibm-tinta: #161616;
        --ibm-tinta-suave: #525252;
        --ibm-fundo: #f3f4f6; /* SIRG: gray-100 (IBM: #f4f4f4) */
        --ibm-camada: #ffffff;
        --ibm-campo: #f4f4f4;
        --ibm-linha: #e0e0e0;
        --ibm-borda-campo: #8c8c8c;
        --ibm-hover: #ececec;
        --ibm-erro-texto: #a2191f;

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

        /* Situacao do atendimento: filete da linha (3:1 sobre branco) */
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
    .at-texto-apoio {
        font-size: 14px;
        line-height: 1.43;
        color: var(--ibm-tinta-suave);
    }
    .at-contador {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        min-width: 28px;
        min-height: 22px;
        margin-left: 4px;
        padding: 0 8px;
        border-radius: 9999px;
        background: var(--sirg-700);
        color: #ffffff;
        font-size: 12px;
        font-weight: 600;
        font-variant-numeric: tabular-nums;
        vertical-align: middle;
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
    textarea.ibm-campo {
        padding: 8px 16px;
        line-height: 1.43;
    }
    .ibm-campo--compacto {
        padding: 0 8px;
    }
    .ibm-campo::placeholder {
        color: var(--ibm-tinta-suave);
        opacity: 1;
    }
    .ibm-campo:hover:not(:disabled) {
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
    .ibm-campo[aria-invalid='true'] {
        border-bottom: 2px solid var(--st-cancelado-cor);
    }
    .ibm-campo:disabled {
        opacity: 0.55;
        cursor: progress;
    }
    .ibm-suave {
        color: var(--ibm-tinta-suave);
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
    .ibm-btn--compacto {
        padding: 0 12px;
        font-size: 13px;
    }
    .ibm-btn--primario {
        background: var(--ibm-acento);
        color: #ffffff;
    }
    .ibm-btn--primario:hover:not(:disabled) {
        background: var(--ibm-acento-hover);
    }
    .ibm-btn--primario:active:not(:disabled) {
        background: var(--ibm-acento-ativo);
    }
    .ibm-btn--secundario {
        border-color: var(--ibm-secundario-borda);
        background: var(--ibm-camada);
        color: var(--ibm-secundario-texto);
    }
    .ibm-btn--secundario:hover:not(:disabled) {
        background: var(--ibm-secundario-hover);
    }
    .ibm-btn--neutro {
        border-color: var(--ibm-borda-campo);
        background: var(--ibm-camada);
        color: #374151;
    }
    .ibm-btn--neutro:hover:not(:disabled) {
        background: var(--ibm-hover);
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
    /* Falta (ambar) e cancelamento (vermelho): contorno na linha, cheio no painel */
    .ibm-btn--falta {
        border-color: var(--st-faltou-cor);
        background: var(--ibm-camada);
        color: #78350f;
    }
    .ibm-btn--falta:hover:not(:disabled) {
        background: #fef3c7;
    }
    .ibm-btn--cancela {
        border-color: var(--st-cancelado-cor);
        background: var(--ibm-camada);
        color: #991b1b;
    }
    .ibm-btn--cancela:hover:not(:disabled) {
        background: #fee2e2;
    }
    .ibm-btn--falta-cheio {
        background: var(--st-faltou-cor);
        color: #ffffff;
    }
    .ibm-btn--falta-cheio:hover:not(:disabled) {
        background: #92400e;
    }
    .ibm-btn--cancela-cheio {
        background: var(--st-cancelado-cor);
        color: #ffffff;
    }
    .ibm-btn--cancela-cheio:hover:not(:disabled) {
        background: #991b1b;
    }
    .ibm-btn[aria-expanded='true'] {
        box-shadow: inset 0 0 0 1px currentColor;
        text-decoration: underline;
        text-underline-offset: 3px;
    }
    /* Desabilitado: cinza. Na lista, onde todos travam juntos durante uma
       gravacao, so esmaece (sem trocar de cor), para a lista nao piscar. */
    .ibm-btn:disabled:not(.at-acao) {
        border-color: transparent;
        background: var(--ibm-linha);
        color: var(--ibm-tinta-suave);
        text-decoration: none;
        cursor: not-allowed;
    }
    .at-acao:disabled {
        opacity: 0.55;
        cursor: progress;
    }
    /* O botao do lote em andamento nao esmaece: e ele que mostra o progresso */
    .at-acao[aria-busy='true']:disabled {
        opacity: 1;
    }
    .at-icone {
        flex: none;
        width: 14px;
        height: 14px;
    }
    .at-girando {
        flex: none;
        width: 14px;
        height: 14px;
        border: 2px solid currentColor;
        border-top-color: transparent;
        border-radius: 9999px;
    }

    /* Mensagens: carregando, erro e lista vazia */
    .at-carregando {
        display: flex;
        align-items: center;
        gap: 12px;
        color: var(--ibm-tinta);
    }
    .at-erro {
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
    .at-vazio {
        padding: 24px;
        border: 1px dashed var(--ibm-borda-campo);
        border-radius: var(--ibm-raio-controle);
        background: var(--ibm-fundo);
        color: var(--ibm-tinta-suave);
        text-align: center;
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
    [data-status='REALIZADO'] > .at-marca {
        border-radius: 9999px;
    }
    [data-status='AGENDADO'] > .at-marca {
        border: 2px solid currentColor;
        border-radius: 9999px;
        background: transparent;
    }
    [data-status='FALTOU'] > .at-marca {
        transform: rotate(45deg) scale(0.85);
    }

    /* Lista nominal: filtros e um bloco por paciente */
    .at-filtros {
        border-bottom: 1px solid var(--ibm-linha);
    }
    .at-pacientes {
        display: flex;
        flex-direction: column;
        gap: 12px;
    }
    /* Bloco do paciente: borda de 1 px e filete emerald (sem sombra, para nao
       virar cartao dentro de cartao) */
    .at-paciente {
        overflow: hidden;
        border: 1px solid var(--ibm-linha);
        border-left: 4px solid var(--sirg-700);
        border-radius: var(--ibm-raio-controle);
        background: var(--ibm-camada);
    }
    .at-paciente--lote {
        border-color: var(--sirg-600);
        border-left-color: var(--sirg-700);
        box-shadow: 0 0 0 1px var(--sirg-600);
    }
    .at-paciente-topo {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        justify-content: space-between;
        gap: 12px 24px;
        padding: 12px 16px;
    }
    .at-paciente-nome {
        font-size: 16px;
        font-weight: 700;
        line-height: 1.4;
        color: var(--ibm-tinta);
        overflow-wrap: anywhere;
    }
    .at-paciente-dados {
        display: flex;
        flex-wrap: wrap;
        gap: 4px 24px;
        margin-top: 4px;
    }
    .at-paciente-dados > div {
        display: flex;
        align-items: baseline;
        gap: 6px;
        min-width: 0;
    }
    .at-paciente-dados dt {
        font-size: 12px;
        font-weight: 600;
        letter-spacing: 0.32px;
        color: var(--ibm-tinta-suave);
    }
    .at-paciente-dados dd {
        font-size: 14px;
        line-height: 1.43;
        color: var(--ibm-tinta);
    }
    .at-doc {
        font-variant-numeric: tabular-nums;
        white-space: nowrap;
    }
    .at-paciente-lote {
        display: flex;
        flex-direction: column;
        align-items: flex-end;
        gap: 4px;
    }
    .at-paciente-lote .ibm-btn {
        min-width: 13rem;
        min-height: 44px;
    }
    .at-paciente-lote-nota,
    .at-paciente-concluido {
        font-size: 12px;
        line-height: 1.33;
        letter-spacing: 0.32px;
        color: var(--ibm-tinta-suave);
    }
    @media (max-width: 639px) {
        .at-paciente-lote {
            width: 100%;
            align-items: stretch;
        }
    }

    /* Tabela de itens do paciente: rola na horizontal dentro do proprio bloco */
    .at-tabela-contorno {
        overflow-x: auto;
        border-top: 1px solid var(--ibm-linha);
    }
    .at-tabela {
        width: 100%;
        border-collapse: collapse;
        font-size: 14px;
        text-align: left;
    }
    .at-tabela--nominal {
        min-width: 36rem;
    }
    .at-tabela--acoes {
        min-width: 48rem;
    }
    /* Cabecalho repetido em cada bloco: mais baixo que o das tabelas de resumo,
       ainda na cor do modulo */
    .at-tabela thead th {
        padding: 6px 12px;
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
        padding: 6px 12px;
        border-bottom: 1px solid var(--ibm-linha);
        vertical-align: middle;
        color: var(--ibm-tinta);
    }
    .at-tabela tbody th {
        font-weight: 600;
    }
    .at-tabela tbody tr:last-child > th,
    .at-tabela tbody tr:last-child > td {
        border-bottom: 0;
    }
    .at-tabela tbody tr:hover > th,
    .at-tabela tbody tr:hover > td {
        background: var(--at-linha-hover);
    }

    /* Caixa de selecao: a celula inteira e o alvo (40 px) */
    .at-tabela .at-col-marca {
        width: 48px;
        padding: 0 4px;
        text-align: center;
    }
    .at-marcar-alvo {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 40px;
        height: 40px;
        border-radius: var(--ibm-raio-controle);
        cursor: pointer;
    }
    .at-marcar-alvo:hover {
        background: var(--sirg-100);
    }
    .at-marcar {
        width: 20px;
        height: 20px;
        accent-color: var(--sirg-700);
        cursor: pointer;
    }
    .at-marcar:focus-visible {
        outline: 2px solid var(--ibm-acento);
        outline-offset: 2px;
    }
    .at-marcar:disabled {
        cursor: progress;
    }

    /* Situacao: filete lateral na cor da situacao, na celula do procedimento */
    .at-linha {
        --linha-filete: var(--ibm-borda-campo);
    }
    .at-linha[data-status='AGENDADO'] {
        --linha-filete: var(--st-agendado-cor);
    }
    .at-linha[data-status='REALIZADO'] {
        --linha-filete: var(--st-realizado-cor);
    }
    .at-linha[data-status='FALTOU'] {
        --linha-filete: var(--st-faltou-cor);
    }
    .at-linha[data-status='CANCELADO'] {
        --linha-filete: var(--st-cancelado-cor);
    }
    .at-tabela .at-linha > th {
        padding-left: 16px;
        box-shadow: inset 4px 0 0 var(--linha-filete);
    }
    /* Item desmarcado: filete tracejado e o texto "Fora da confirmacao" */
    .at-tabela .at-linha--desmarcada > th {
        box-shadow: none;
        background-image: repeating-linear-gradient(to bottom, var(--ibm-borda-campo) 0 4px, transparent 4px 8px);
        background-repeat: no-repeat;
        background-size: 4px 100%;
    }
    .at-fora-do-lote {
        display: block;
        font-size: 12px;
        font-weight: 400;
        letter-spacing: 0.32px;
        color: var(--ibm-tinta-suave);
    }
    /* Item que nao casa com o filtro de situacao: texto secundario (contraste
       ainda AA), sem esconder */
    .at-tabela .at-linha--fora > th,
    .at-tabela .at-linha--fora > td {
        color: var(--ibm-tinta-suave);
        font-weight: 400;
    }

    .at-badge {
        display: inline-flex;
        align-items: center;
        gap: 6px;
        min-height: 24px;
        padding: 0 10px;
        border-radius: 9999px;
        font-size: 12px;
        font-weight: 600;
        letter-spacing: 0.32px;
        white-space: nowrap;
    }
    .at-obs {
        max-width: 20rem;
        margin-top: 4px;
        font-size: 12px;
        line-height: 1.4;
        color: var(--ibm-tinta);
        overflow-wrap: anywhere;
    }
    .at-registro {
        margin-top: 2px;
        font-size: 12px;
        line-height: 1.4;
        color: var(--ibm-tinta-suave);
    }
    .at-acoes {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
    }

    /* Painel de falta/cancelamento: linha logo abaixo, na cor do desfecho */
    .at-linha-painel {
        --painel-cor: var(--st-faltou-cor);
        --painel-fundo: #fffbeb;
    }
    .at-linha-painel[data-tipo='cancelar'] {
        --painel-cor: var(--st-cancelado-cor);
        --painel-fundo: #fef2f2;
    }
    .at-tabela tbody tr.at-linha-painel > td {
        padding: 12px 16px 16px;
        background: var(--painel-fundo);
        box-shadow: inset 4px 0 0 var(--painel-cor);
    }
    .at-linha-painel .ibm-campo {
        background-color: #ffffff;
    }
    .at-painel-rotulo {
        margin-bottom: 8px;
        font-size: 14px;
        font-weight: 600;
        line-height: 1.43;
        color: var(--ibm-tinta);
    }
    .at-painel-nota {
        font-size: 12px;
        line-height: 1.4;
        letter-spacing: 0.32px;
        color: var(--ibm-tinta-suave);
    }

    .at-paginacao {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 8px;
        margin-top: 12px;
        font-size: 14px;
    }

    /* Relatorio: ajuda do periodo; o erro ganha peso e cor, alem do proprio texto */
    .at-ajuda {
        flex-basis: 100%;
        font-size: 12px;
        line-height: 1.4;
        letter-spacing: 0.32px;
        color: var(--ibm-tinta-suave);
    }
    .at-ajuda-erro {
        font-weight: 600;
        color: var(--ibm-erro-texto);
    }

    @media (prefers-reduced-motion: no-preference) {
        .ibm-btn,
        .ibm-campo,
        .at-marcar-alvo {
            transition:
                background-color 110ms ease-out,
                border-color 110ms ease-out;
        }
        .at-girando {
            animation: at-girar 0.8s linear infinite;
        }
    }
    @keyframes at-girar {
        to {
            transform: rotate(360deg);
        }
    }
</style>
