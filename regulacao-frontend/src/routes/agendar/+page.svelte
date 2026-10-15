<script lang="ts">
  import { onMount, tick } from 'svelte';
  import { getApi, postApi } from '$lib/api';
  import { listarEspecialidadesCatalogo } from '$lib/especialidadesApi.js';
  import { formatarHora } from '$lib/cotas.js';
  import { analisarVerificacao, montarBodyViaveis } from '$lib/agendamentoParcial.js';
  import { separarConsulta, termoParaServidor, termoDaSegundaBusca, casaComConsulta } from '$lib/buscaPaciente.js';
  import { sugestoesTeclado } from '$lib/sugestoesTeclado.js';
  import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';
  import UserMenu from '$lib/UserMenu.svelte';
  import LoadingSpinner from '$lib/LoadingSpinner.svelte';
  import { user as usuarioLogado } from '$lib/stores/auth.js';
  import { get } from 'svelte/store';
  import { gerarComprovantePDF } from '$lib/comprovantePdf';

  interface Cidade {
    id: number;
    nomeCidade: string;
    codigoIBGE: string;
    cep: string;
  }

  interface LocalAgendamento {
    id: number;
    nomeLocal: string;
    endereco: string;
    numero: string;
    cidade?: Cidade | null;
    cidadeId?: number | null;
    cidadeNome?: string | null;
  }

  interface EspecialidadeCatalogo {
    id: number;
    codigo: string;
    nome: string;
    vagas?: number;
  }

  interface EspecialidadeAgendar {
    codigo: string;
    nome?: string | null;
  }

  interface SolicitacaoResumo {
    id: number;
    nomePaciente: string;
    cpfPaciente: string;
    cns: string;
    usfOrigem: string;
    unidadeNome?: string | null;
  }

  interface SolicitacaoDetalhe {
    id: number;
    nomePaciente: string;
    cpfPaciente: string;
    usfOrigem: string;
    unidadeId?: number | null;
    unidadeNome?: string | null;
    cns?: string | null;
    especialidades: EspecialidadeAgendar[];
  }

  let locaisAgendamento = $state<LocalAgendamento[]>([]);
  let solicitacoes = $state<SolicitacaoResumo[]>([]);
  let isLoading = $state(true);
  let error = $state('');
  let buscandoSolicitacoes = $state(false);
  let buscaSequencia = 0;



  let examesSelecionados = $state<string[]>([]);
  let dataAgendada = $state('');
  let turno = $state<'MANHA' | 'TARDE'>('MANHA');
  let localAgendamentoId = $state('');
  let observacoes = $state('');
  let valorBusca = $state('');
  let paginaAtual = $state(0);
  let comboboxAberto = $state(false);
  // true quando o servidor tem mais pendentes além dos 20 devolvidos.
  let haMaisResultados = $state(false);

  let especialidadeLabelMap = new Map<string, string>();
  let catalogoCarregado = false;
  let catalogoEspecialidades = $state<EspecialidadeCatalogo[]>([]);
  let contagemPorEspecialidade = $state<Record<string, { agendados: number; capacidade: number; restante: number }>>({});
  let saldoCotaPorEspecialidade = $state<Record<string, { quantidadeTotal: number | null; quantidadeUtilizada: number | null; saldoDisponivel: number | null }>>({});
  let saldoCotaPorDataEspecialidade = $state<Record<string, { quantidadeTotal: number | null; quantidadeUtilizada: number | null; saldoDisponivel: number | null }>>({});

  // Espelho de atendimento (V92): cotas aplicaveis por especialidade/data, que
  // trazem profissional/horario/local quando a cota os define — a unidade ve
  // isso como referencia travada e nao pode agendar diferente do liberado.
  interface CotaAplicavel {
    id: number;
    profissionalId: number | null;
    profissionalNome: string | null;
    localAgendamentoId: number | null;
    localAgendamentoNome: string | null;
    horarioDinamico: boolean;
    tempoMedioAtendimentoMinutos: number | null;
    horaInicial: string | null;
    horaFinal: string | null;
  }
  let cotasAplicaveisPorEspecialidade = $state<Record<string, CotaAplicavel[]>>({});
  let cotaEscolhidaPorExame = $state<Record<string, number | null>>({});
  // Hora informada manualmente pelo operador (V97/V100) — sempre aceita,
  // inclusive quando a cota resolvida é de horário dinâmico (nesse caso
  // sobrescreve o cálculo automático).
  let horarioManualPorExame = $state<Record<string, string>>({});

  // Profissional que atende (V100, opcional) — sempre disponível, sobrescreve
  // o profissional espelhado pela cota resolvida só para este agendamento.
  interface ProfissionalBusca {
    id: number;
    nome: string;
  }
  let profissionalTermoPorExame = $state<Record<string, string>>({});
  let profissionalResultadosPorExame = $state<Record<string, ProfissionalBusca[]>>({});
  let profissionalBuscandoPorExame = $state<Record<string, boolean>>({});
  let profissionalSelecionadoPorExame = $state<Record<string, ProfissionalBusca | null>>({});
  // true quando o operador já mexeu no campo (seleção ou remoção) — enquanto
  // não mexe, o campo só exibe o profissional da cota, sem gerar override.
  let profissionalTocadoPorExame = $state<Record<string, boolean>>({});

  /** Profissional exibido no campo: o escolhido pelo operador, ou o da cota resolvida como valor inicial. */
  function profissionalExibidoParaExame(codigo: string): ProfissionalBusca | null {
    const chave = codigo.toUpperCase();
    if (profissionalTocadoPorExame[chave]) {
      return profissionalSelecionadoPorExame[chave] ?? null;
    }
    const cota = cotaResolvida(codigo);
    if (cota?.profissionalId) {
      return { id: cota.profissionalId, nome: cota.profissionalNome ?? '' };
    }
    return null;
  }

  async function buscarProfissionalParaExame(codigo: string) {
    const termo = (profissionalTermoPorExame[codigo] ?? '').trim();
    if (termo.length < 2) {
      profissionalResultadosPorExame = { ...profissionalResultadosPorExame, [codigo]: [] };
      return;
    }
    profissionalBuscandoPorExame = { ...profissionalBuscandoPorExame, [codigo]: true };
    try {
      const res = await getApi(`profissionais/buscar?nome=${encodeURIComponent(termo)}&size=10`);
      const pagina = res.ok ? await res.json() : { content: [] };
      profissionalResultadosPorExame = { ...profissionalResultadosPorExame, [codigo]: pagina.content ?? [] };
    } catch {
      profissionalResultadosPorExame = { ...profissionalResultadosPorExame, [codigo]: [] };
    } finally {
      profissionalBuscandoPorExame = { ...profissionalBuscandoPorExame, [codigo]: false };
    }
  }

  function selecionarProfissionalParaExame(codigo: string, p: ProfissionalBusca) {
    const chave = codigo.toUpperCase();
    profissionalSelecionadoPorExame = { ...profissionalSelecionadoPorExame, [chave]: p };
    profissionalTocadoPorExame = { ...profissionalTocadoPorExame, [chave]: true };
    profissionalTermoPorExame = { ...profissionalTermoPorExame, [codigo]: '' };
    profissionalResultadosPorExame = { ...profissionalResultadosPorExame, [codigo]: [] };
  }

  function removerProfissionalParaExame(codigo: string) {
    const chave = codigo.toUpperCase();
    profissionalSelecionadoPorExame = { ...profissionalSelecionadoPorExame, [chave]: null };
    profissionalTocadoPorExame = { ...profissionalTocadoPorExame, [chave]: true };
  }

  /** Cota que efetivamente será usada para o exame — a única com profissional, ou a escolhida entre várias. */
  function cotaResolvida(codigo: string): CotaAplicavel | null {
    const comProfissional = cotasEspecificas(codigo);
    if (comProfissional.length === 1) return comProfissional[0];
    if (comProfissional.length > 1) {
      const escolhidaId = cotaEscolhidaPorExame[codigo.toUpperCase()];
      return comProfissional.find((c) => c.id === escolhidaId) ?? null;
    }
    return null;
  }

  // "Nome - CPF" é o formato em que a tela mostra o paciente; a busca aceita os
  // dois juntos (ver $lib/buscaPaciente.js).
  const consulta = $derived(separarConsulta(valorBusca));
  const solicitacoesFiltradas = $derived(solicitacoes.filter((s) => casaComConsulta(s, consulta)));

  async function carregarCatalogoEspecialidades() {
    if (catalogoCarregado && especialidadeLabelMap.size > 0) {
      return;
    }
    try {
      const lista = (await listarEspecialidadesCatalogo()) as EspecialidadeCatalogo[];
      catalogoEspecialidades = lista;
      especialidadeLabelMap = new Map(lista.map((e) => [e.codigo, e.nome]));
      catalogoCarregado = true;
    } catch (e) {
      console.warn('Falha ao carregar catálogo de especialidades', e);
    }
  }

  function getEspecialidadeLabel(valor: string): string {
    return especialidadeLabelMap.get(valor) || valor.replace(/_/g, ' ');
  }

  function getEspecialidadeVagas(codigo: string): number {
    const especialidade = catalogoEspecialidades.find((e) => e.codigo?.toUpperCase() === codigo?.toUpperCase());
    return especialidade?.vagas ?? 0;
  }

  async function carregarContagemPorEspecialidade(codigo: string) {
    if (!dataAgendada || !codigo) {
      return;
    }

    const chave = codigo.toUpperCase();

    try {
      const params = new URLSearchParams();
      params.append('data', dataAgendada);
      params.append('codigo', chave);

      const res = await getApi(`agendamentos/contagem-por-data-especialidade?${params.toString()}`);
      if (!res.ok) {
        delete contagemPorEspecialidade[chave];
        return;
      }

      const json = await res.json();
      contagemPorEspecialidade = {
        ...contagemPorEspecialidade,
        [chave]: {
          agendados: json.count ?? 0,
          capacidade: json.capacidade ?? 0,
          restante: json.restante ?? (json.capacidade ?? 0) - (json.count ?? 0)
        }
      };
    } catch (error) {
      console.warn('Falha ao carregar contagem por especialidade', error);
    }
  }

  // Saldo do sistema de Cotas por Unidade (mensal) para a especialidade
  // escolhida — atalho para o operador ver, antes de confirmar, se a unidade
  // ainda tem cota disponível no mês do agendamento (distinto do limite simples
  // de `Especialidade.vagas` já mostrado acima).
  async function carregarSaldoCotaPorEspecialidade(codigo: string) {
    if (!dataAgendada || !codigo || !solicitacaoDetalhe?.unidadeId) {
      return;
    }

    const chave = codigo.toUpperCase();
    const especialidadeId = catalogoEspecialidades.find((e) => e.codigo?.toUpperCase() === chave)?.id;
    if (!especialidadeId) {
      return;
    }

    try {
      const params = new URLSearchParams();
      params.append('unidadeId', String(solicitacaoDetalhe.unidadeId));
      params.append('especialidadeId', String(especialidadeId));
      params.append('periodo', dataAgendada.slice(0, 7));
      // V100: com profissional resolvido (cota escolhida entre 2+ compatíveis,
      // ou única aplicável), o saldo exibido passa a ser o daquela cota
      // específica — pools isolados por profissional, sem mistura.
      const profissionalId = cotaResolvida(codigo)?.profissionalId;
      if (profissionalId != null) {
        params.append('profissionalId', String(profissionalId));
      }

      const res = await getApi(`cotas/saldo?${params.toString()}`);
      if (!res.ok) {
        delete saldoCotaPorEspecialidade[chave];
        return;
      }

      const json = await res.json();
      saldoCotaPorEspecialidade = {
        ...saldoCotaPorEspecialidade,
        [chave]: {
          quantidadeTotal: json.quantidadeTotal ?? null,
          quantidadeUtilizada: json.quantidadeUtilizada ?? null,
          saldoDisponivel: json.saldoDisponivel ?? null
        }
      };
    } catch (error) {
      console.warn('Falha ao carregar saldo de cota por especialidade', error);
    }
  }

  // Saldo por DATA específica (complementa o saldo mensal acima): sem isso o
  // operador só via a cota agregada do mês, mesmo quando a unidade tem uma cota
  // cadastrada só para aquele dia.
  async function carregarSaldoCotaPorDataEspecialidade(codigo: string) {
    if (!dataAgendada || !codigo || !solicitacaoDetalhe?.unidadeId) {
      return;
    }

    const chave = codigo.toUpperCase();
    const especialidadeId = catalogoEspecialidades.find((e) => e.codigo?.toUpperCase() === chave)?.id;
    if (!especialidadeId) {
      return;
    }

    try {
      const params = new URLSearchParams();
      params.append('unidadeId', String(solicitacaoDetalhe.unidadeId));
      params.append('especialidadeId', String(especialidadeId));
      params.append('data', dataAgendada);
      const cotaDoExame = cotaResolvida(codigo);
      const profissionalId = cotaDoExame?.profissionalId;
      if (profissionalId != null) {
        params.append('profissionalId', String(profissionalId));
      }
      // V112: cotas irmãs do mesmo dia (mesmo profissional, ou só local/horário) —
      // o saldo da data isola pela cota resolvida, não só pelo profissional.
      if (cotaDoExame != null) {
        params.append('cotaId', String(cotaDoExame.id));
      }

      const res = await getApi(`cotas/saldo-data?${params.toString()}`);
      if (!res.ok) {
        delete saldoCotaPorDataEspecialidade[chave];
        return;
      }

      const json = await res.json();
      saldoCotaPorDataEspecialidade = {
        ...saldoCotaPorDataEspecialidade,
        [chave]: {
          quantidadeTotal: json.quantidadeTotal ?? null,
          quantidadeUtilizada: json.quantidadeUtilizada ?? null,
          saldoDisponivel: json.saldoDisponivel ?? null
        }
      };
    } catch (error) {
      console.warn('Falha ao carregar saldo de cota por data', error);
    }
  }

  // Cotas aplicaveis (com profissional/horario/local, quando houver) para a
  // especialidade/data escolhida — o "espelho" que trava a tela de agendamento.
  async function carregarCotasAplicaveis(codigo: string) {
    if (!dataAgendada || !codigo || !solicitacaoDetalhe?.unidadeId) {
      return;
    }

    const chave = codigo.toUpperCase();
    const especialidadeId = catalogoEspecialidades.find((e) => e.codigo?.toUpperCase() === chave)?.id;
    if (!especialidadeId) {
      return;
    }

    try {
      const params = new URLSearchParams();
      params.append('unidadeId', String(solicitacaoDetalhe.unidadeId));
      params.append('especialidadeId', String(especialidadeId));
      params.append('data', dataAgendada);

      const res = await getApi(`cotas/aplicaveis?${params.toString()}`);
      if (!res.ok) {
        delete cotasAplicaveisPorEspecialidade[chave];
        return;
      }

      const json: CotaAplicavel[] = await res.json();
      cotasAplicaveisPorEspecialidade = { ...cotasAplicaveisPorEspecialidade, [chave]: json };
    } catch (e) {
      console.warn('Falha ao carregar cotas aplicaveis', e);
    }
  }

  /**
   * Cotas "específicas" (V112): têm profissional, local OU horário. Cotas irmãs
   * do mesmo dia diferem num desses campos; a cota geral (sem nenhum) não entra.
   */
  function cotasEspecificas(codigo: string): CotaAplicavel[] {
    return (cotasAplicaveisPorEspecialidade[codigo.toUpperCase()] ?? []).filter(
      (c) => c.profissionalId != null || c.localAgendamentoId != null || c.horaInicial != null
    );
  }

  /**
   * Cota "de referência" para travar/preencher o local (V100): quando há
   * profissional(is) aplicável(is), é a cota RESOLVIDA (a única, ou a
   * escolhida no dropdown quando há mais de uma) — nunca "existe alguma cota
   * com local entre todas as candidatas", que travava o campo vazio quando
   * havia 2+ profissionais com locais definidos e nenhum ainda escolhido.
   * Sem profissional nenhuma aplicável, cai no caso comum de sempre: cota
   * única (sem escolha) ou nenhuma.
   */
  function cotaReferenciaLocal(codigo: string): CotaAplicavel | null {
    if (cotasEspecificas(codigo).length > 0) {
      return cotaResolvida(codigo);
    }
    const todas = cotasAplicaveisPorEspecialidade[codigo.toUpperCase()] ?? [];
    return todas.length === 1 ? todas[0] : null;
  }

  /** true quando o local do agendamento esta travado pela cota resolvida (nao pode ser trocado). */
  const localTravadoPorCota = $derived(
    examesSelecionados.length === 1
      && cotaReferenciaLocal(examesSelecionados[0])?.localAgendamentoId != null
  );

  // Preenche o local automaticamente com o da cota resolvida — reage tanto ao
  // carregar as cotas aplicáveis quanto à troca de profissional no dropdown
  // (cotaEscolhidaPorExame), que antes não disparava esse preenchimento.
  let ultimaCotaReferenciaLocalId = $state<number | null>(null);
  $effect(() => {
    if (examesSelecionados.length !== 1) return;
    const cota = cotaReferenciaLocal(examesSelecionados[0]);
    if (cota?.id !== ultimaCotaReferenciaLocalId) {
      // Trocou de cota de referência (ex.: profissional diferente escolhido no
      // dropdown) — limpa antes de preencher, para não deixar o local do
      // profissional anterior "herdado" quando a nova cota não define nenhum.
      ultimaCotaReferenciaLocalId = cota?.id ?? null;
      localAgendamentoId = cota?.localAgendamentoId != null ? String(cota.localAgendamentoId) : '';
    }
  });

  /** true se a hora manual informada está fora do período liberado pela cota (V99). */
  function horaManualForaDoPeriodo(codigo: string): boolean {
    const hora = horarioManualPorExame[codigo.toUpperCase()];
    const cota = cotaResolvida(codigo);
    if (!hora || !cota?.horaInicial || !cota?.horaFinal) return false;
    return hora < formatarHora(cota.horaInicial) || hora > formatarHora(cota.horaFinal);
  }

  async function atualizarContagemPorEspecialidades() {
    if (!dataAgendada) {
      return;
    }

    for (const codigo of examesSelecionados) {
      await carregarContagemPorEspecialidade(codigo);
      await carregarSaldoCotaPorEspecialidade(codigo);
      await carregarSaldoCotaPorDataEspecialidade(codigo);
      await carregarCotasAplicaveis(codigo);
    }
  }

  function normalizarEspecialidadesParaAgendamento(especialidades: unknown): EspecialidadeAgendar[] {
  if (!Array.isArray(especialidades)) {
    return [];
  }

  const resultado = especialidades
    .map<EspecialidadeAgendar | null>((esp) => {
      if (!esp) return null;

      if (typeof esp === 'object') {
        const asAny = esp as any;
        const especialidadeObj =
          typeof asAny.especialidadeSolicitada === 'object' && asAny.especialidadeSolicitada !== null
            ? asAny.especialidadeSolicitada
            : null;

        const codigoBruto =
          asAny.codigo ??
          asAny.exameCodigo ??
          (especialidadeObj ? especialidadeObj.codigo : asAny.especialidadeSolicitada) ??
          asAny.especialidadeCodigoLegacy ??
          asAny.nome;

        const nomePreferencial = asAny.nome ?? (especialidadeObj ? especialidadeObj.nome : null);

        if (!codigoBruto) {
          return null;
        }

        const codigo = String(codigoBruto);

        const encontrado = catalogoEspecialidades.find(
          (e) => e.codigo?.toLowerCase() === codigo.toLowerCase()
        );

        const nome =
          nomePreferencial ??
          encontrado?.nome ??
          especialidadeLabelMap.get(codigo) ??
          getEspecialidadeLabel(codigo);

        return { codigo, nome }; // compatível com EspecialidadeAgendar
      }

      const valor = String(esp);
      const encontrado = catalogoEspecialidades.find(
        (e) =>
          e.codigo?.toLowerCase() === valor.toLowerCase() ||
          e.nome?.toLowerCase() === valor.toLowerCase()
      );

      const codigo = encontrado?.codigo || valor;
      const nome =
        encontrado?.nome ??
        especialidadeLabelMap.get(codigo) ??
        getEspecialidadeLabel(codigo);

      return { codigo, nome };
    })
    .filter((esp): esp is EspecialidadeAgendar => esp !== null);

  return resultado;
}


  async function buscarPaginaDePendentes(termo: string, page: number) {
    const params = new URLSearchParams({
      termo,
      page: String(page),
      size: '20'
    });

    const response = await getApi(`agendamentos/pendentes/buscar?${params.toString()}`);
    if (!response.ok) {
      const detalhe = await response.text().catch(() => '');
      throw new Error(`Erro ao carregar as solicitações pendentes (${response.status}): ${detalhe || response.statusText}`);
    }
    return response.json();
  }

  async function carregarSolicitacoesPendentes(texto: string = '', page = 0) {
    error = '';
    paginaAtual = page;
    const minhaSequencia = ++buscaSequencia;
    const consultaDaBusca = separarConsulta(texto);
    buscandoSolicitacoes = true;
    try {
      let pageJson = await buscarPaginaDePendentes(termoParaServidor(consultaDaBusca), page);
      let lista: SolicitacaoResumo[] = pageJson.content ?? pageJson;

      // O servidor recebe só o nome e devolve 20 por página. Com nome comum, o
      // paciente do documento digitado pode ter ficado de fora: tenta mais uma
      // vez (ver termoDaSegundaBusca), e o filtro da tela confere o resultado.
      const segundoTermo = termoDaSegundaBusca(consultaDaBusca);
      if (
        segundoTermo &&
        pageJson.last === false &&
        !lista.some((s) => casaComConsulta(s, consultaDaBusca))
      ) {
        if (minhaSequencia !== buscaSequencia) return;
        pageJson = await buscarPaginaDePendentes(segundoTermo, page);
        lista = pageJson.content ?? pageJson;
      }

      if (minhaSequencia === buscaSequencia) {
        solicitacoes = lista;
        haMaisResultados = pageJson.last === false;
      }
    } catch (e: any) {
      console.error('Falha ao buscar pendentes', e);
      if (minhaSequencia === buscaSequencia) {
        error = 'Não foi possível buscar as solicitações. Verifique sua conexão e tente novamente.';
      }
    } finally {
      if (minhaSequencia === buscaSequencia) {
        buscandoSolicitacoes = false;
      }
    }
  }

  let solicitacaoDetalhe = $state<SolicitacaoDetalhe | null>(null);

  // Lote parcial: resposta de POST agendamentos/{id}/verificar e o diálogo que
  // ela abre quando nem todos os itens podem ser agendados.
  type ItemVerificado = { codigo: string; nome: string; podeAgendar: boolean; corrigivel: boolean; motivo: string | null };
  type VerificacaoAgendamento = { itens: ItemVerificado[]; bloqueioDoLote: string | null };
  type DadosDoEnvio = {
    solicitacaoId: SolicitacaoDetalhe['id'];
    nomePaciente: SolicitacaoDetalhe['nomePaciente'];
    cpfPaciente: SolicitacaoDetalhe['cpfPaciente'];
    usfOrigem: SolicitacaoDetalhe['usfOrigem'];
    unidadeNome: SolicitacaoDetalhe['unidadeNome'];
    cns: SolicitacaoDetalhe['cns'];
    dataAgendada: string;
    turno: 'MANHA' | 'TARDE';
    localLabel: string;
    observacoes: string;
  };
  type ConfirmacaoParcial = {
    solicitacaoId: string;
    body: Record<string, unknown>;
    paciente: DadosDoEnvio;
    situacao: 'CORRIGIR' | 'NENHUM' | 'BLOQUEADO' | 'PARCIAL' | 'TUDO_OK';
    viaveis: ItemVerificado[];
    corrigiveis: ItemVerificado[];
    recusados: ItemVerificado[];
    bloqueioDoLote: string | null;
  };
  let verificando = $state(false);
  let gravando = $state(false);
  let confirmacaoParcial = $state<ConfirmacaoParcial | null>(null);
  // Da verificação até a resposta do POST o paciente não pode ser trocado: o
  // que termina de gravar limparia a tela do paciente novo, e o comprovante
  // poderia sair com dados dele.
  const envioEmAndamento = $derived(verificando || gravando || confirmacaoParcial !== null);

  /**
   * Acessibilidade do diálogo de lote parcial — só foco e teclado, nenhuma regra.
   * Ao abrir, o foco vai para o elemento marcado com `data-foco-inicial` (o
   * "Voltar", nunca o confirmar); Tab fica preso dentro do diálogo; Esc chama
   * `aoFechar`; ao fechar, o foco volta para onde estava.
   */
  function dialogoModal(node: HTMLElement, aoFechar: () => void) {
    const focoAnterior = document.activeElement as HTMLElement | null;
    const focaveis = () =>
      Array.from(
        node.querySelectorAll<HTMLElement>('button:not([disabled]), [href], [tabindex]:not([tabindex="-1"])')
      );

    (node.querySelector<HTMLElement>('[data-foco-inicial]') ?? node).focus();

    function aoTeclar(evento: KeyboardEvent) {
      if (evento.key === 'Escape') {
        evento.preventDefault();
        aoFechar();
        return;
      }
      if (evento.key !== 'Tab') return;

      const lista = focaveis();
      if (lista.length === 0) {
        evento.preventDefault();
        node.focus();
        return;
      }
      const primeiro = lista[0];
      const ultimo = lista[lista.length - 1];
      const ativo = document.activeElement;
      if (!node.contains(ativo)) {
        evento.preventDefault();
        primeiro.focus();
      } else if (evento.shiftKey && (ativo === primeiro || ativo === node)) {
        evento.preventDefault();
        ultimo.focus();
      } else if (!evento.shiftKey && ativo === ultimo) {
        evento.preventDefault();
        primeiro.focus();
      }
    }

    document.addEventListener('keydown', aoTeclar);
    return {
      destroy() {
        document.removeEventListener('keydown', aoTeclar);
        if (focoAnterior?.isConnected) focoAnterior.focus();
      }
    };
  }
  let carregandoDetalhe = $state(false);
  let selecaoSequencia = 0;

  /**
   * Tira o paciente selecionado da tela, junto com tudo que dependia dele.
   * O paciente só volta a existir quando o detalhe de uma nova seleção chega —
   * assim o id enviado e o paciente exibido nunca são de pessoas diferentes.
   */
  function limparPacienteSelecionado() {
    selecaoSequencia++;
    carregandoDetalhe = false;
    solicitacaoDetalhe = null;
    examesSelecionados = [];

    // Estado por-exame de uma solicitação anterior não pode vazar para esta —
    // sem isso, profissional/hora escolhidos para um exame do paciente A
    // ficariam pré-selecionados ao trocar para o paciente B, que pode ter o
    // mesmo código de exame pendente.
    cotasAplicaveisPorEspecialidade = {};
    cotaEscolhidaPorExame = {};
    horarioManualPorExame = {};
    profissionalTermoPorExame = {};
    profissionalResultadosPorExame = {};
    profissionalSelecionadoPorExame = {};
    profissionalTocadoPorExame = {};
  }

  async function selecionarSolicitacao(solicitacao: SolicitacaoResumo) {
    if (envioEmAndamento) return;
    // O campo volta a ficar vazio: o paciente escolhido aparece em "Dados do
    // Paciente", e "Nome - CPF" não fica no campo nem no histórico do navegador.
    valorBusca = '';
    comboboxAberto = false;
    limparPacienteSelecionado();

    const minhaSelecao = selecaoSequencia;
    carregandoDetalhe = true;
    try {
      const res = await getApi(`solicitacoes/buscar/${solicitacao.id}`);
      if (!res.ok) {
        const detalhe = await res.text().catch(() => '');
        throw new Error(`Erro ao carregar detalhes da solicitação (${res.status}): ${detalhe || res.statusText}`);
      }

      const detalheResposta = await res.json();
      if (minhaSelecao !== selecaoSequencia) return;
      const especialidadesNormalizadas = normalizarEspecialidadesParaAgendamento(detalheResposta?.especialidades);
      solicitacaoDetalhe = {
        ...detalheResposta,
        especialidades: especialidadesNormalizadas
      };
    } catch (e: any) {
      if (minhaSelecao !== selecaoSequencia) return;
      alert(e.message ?? 'Erro ao carregar detalhes da solicitação');
    } finally {
      if (minhaSelecao === selecaoSequencia) {
        carregandoDetalhe = false;
      }
    }
  }

  async function trocarPaciente() {
    if (envioEmAndamento) return;
    limparPacienteSelecionado();
    valorBusca = '';
    await tick();
    document.getElementById('combobox-agendamento')?.focus();
  }

  // Abrir a lista depende do campo estar com foco ou receber texto — por
  // digitação, colar, ditado ou sugestão do navegador (que pode disparar só
  // `change`).
  let fecharComboboxTimeout: ReturnType<typeof setTimeout> | null = null;

  function abrirCombobox() {
    if (fecharComboboxTimeout) {
      clearTimeout(fecharComboboxTimeout);
      fecharComboboxTimeout = null;
    }
    comboboxAberto = true;
  }

  function agendarFechamentoDoCombobox() {
    if (fecharComboboxTimeout) clearTimeout(fecharComboboxTimeout);
    fecharComboboxTimeout = setTimeout(() => {
      comboboxAberto = false;
      fecharComboboxTimeout = null;
    }, 150);
  }

  function aoPreencherBusca(event: Event) {
    const campo = event.currentTarget as HTMLInputElement;
    valorBusca = campo.value;
    if (document.activeElement === campo) abrirCombobox();
  }

  // Enter no campo de busca nunca envia o agendamento: escolher o paciente e
  // gravar são passos separados. A escolha pela lista é do sugestoesTeclado.
  function aoTeclarNaBusca(event: KeyboardEvent) {
    if (event.key === 'Enter') event.preventDefault();
  }

  async function carregarLocaisAgendamento() {
    try {
      const res = await getApi('local/agendamento');
      if (!res.ok) {
        throw new Error('Erro ao receber dados do servidor!');
      }
      const data: LocalAgendamento[] = await res.json();
      locaisAgendamento = data;

      if (localAgendamentoId && !data.some((loc) => String(loc.id) === String(localAgendamentoId))) {
        localAgendamentoId = '';
      }
    } catch (error) {
      console.error(error);
      alert('Erro ao se conectar ao servidor!');
    }
  }

  $effect(() => {
    if (!dataAgendada || examesSelecionados.length === 0) {
      return;
    }
    atualizarContagemPorEspecialidades();
  });

  // V100: recarrega o saldo (por profissional, quando resolvido) ao trocar a
  // escolha entre cotas compatíveis — sem isso, "Cota do mês"/"Cota da data"
  // continuavam mostrando o valor anterior mesmo depois de escolher o
  // profissional, já que o efeito acima só reage a dataAgendada/exames.
  $effect(() => {
    if (!dataAgendada) return;
    for (const codigo of examesSelecionados) {
      void cotaEscolhidaPorExame[codigo.toUpperCase()]; // registra a dependência reativa
      carregarSaldoCotaPorEspecialidade(codigo);
      carregarSaldoCotaPorDataEspecialidade(codigo);
    }
  });

  onMount(async () => {
    isLoading = true;
    try {
      await Promise.all([
        carregarCatalogoEspecialidades(),
        carregarSolicitacoesPendentes(''),
        carregarLocaisAgendamento()
      ]);
    } finally {
      isLoading = false;
    }
  });

  let buscarTimeout: ReturnType<typeof setTimeout> | null = null;
  $effect(() => {
    const aberto = comboboxAberto;
    const termo = valorBusca;

    if (!aberto) return;

    if (buscarTimeout) {
      clearTimeout(buscarTimeout);
    }

    buscarTimeout = setTimeout(() => {
      carregarSolicitacoesPendentes(termo);
    }, 300);

    return () => {
      if (buscarTimeout) {
        clearTimeout(buscarTimeout);
      }
    };
  });

  function getLocalLabel(value: string) {
    const found = locaisAgendamento.find((loc) => String(loc.id) === String(value));
    if (!found) {
      return 'Local não informado';
    }
    return found.cidadeNome ? `${found.nomeLocal} - ${found.cidadeNome}` : found.nomeLocal;
  }

  async function enviarAgendamento(event: SubmitEvent) {
    event.preventDefault();
    if (verificando) return;

    if (!solicitacaoDetalhe) {
      alert('Por favor, selecione uma solicitação válida.');
      return;
    }
    if (examesSelecionados.length === 0) {
      alert('Selecione pelo menos um exame para agendar.');
      return;
    }
    if (!dataAgendada || !localAgendamentoId) {
      alert('Preencha a data e o local do agendamento.');
      return;
    }

    // Espelho de atendimento (V92): quando ha mais de um profissional compativel
    // para a mesma especialidade/data, a unidade precisa escolher qual — o
    // backend recusa sem essa escolha explicita.
    const cotasSelecionadas: Record<string, number> = {};
    const horariosSelecionados: Record<string, string> = {};
    const profissionaisSelecionados: Record<string, number> = {};
    for (const codigo of examesSelecionados) {
      const chave = codigo.toUpperCase();
      const comProfissional = cotasEspecificas(codigo);
      if (comProfissional.length > 1) {
        const escolhida = cotaEscolhidaPorExame[chave];
        if (!escolhida) {
          alert(`Escolha a cota de atendimento para ${getEspecialidadeLabel(codigo)} — há mais de uma cota compatível.`);
          return;
        }
        cotasSelecionadas[codigo] = escolhida;
      }

      // V99: mesma validação de faixa do backend, para não deixar o operador
      // enviar e só descobrir o erro depois — ver validarHoraDentroDoPeriodoDaCota.
      if (horaManualForaDoPeriodo(codigo)) {
        alert(`Hora fora do período liberado pela cota para ${getEspecialidadeLabel(codigo)}.`);
        return;
      }

      // Hora manual (V97/V100): sempre aceita, inclusive quando a cota
      // resolvida é dinâmica — nesse caso sobrescreve o cálculo automático.
      const horaManual = horarioManualPorExame[chave];
      if (horaManual) {
        horariosSelecionados[codigo] = horaManual;
      }

      // Profissional que atende (V100, opcional) — só envia quando o operador
      // efetivamente mexeu no campo; sem mexer, usa o profissional da cota.
      if (profissionalTocadoPorExame[chave] && profissionalSelecionadoPorExame[chave]) {
        profissionaisSelecionados[codigo] = profissionalSelecionadoPorExame[chave]!.id;
      }
    }

    const localIdNumber = localAgendamentoId ? Number(localAgendamentoId) : null;

    const body: Record<string, unknown> = {
      examesSelecionados,
      dataAgendada,
      observacoes,
      turno,
      localAgendado: null,
      cotasSelecionadas: Object.keys(cotasSelecionadas).length > 0 ? cotasSelecionadas : null,
      horariosSelecionados: Object.keys(horariosSelecionados).length > 0 ? horariosSelecionados : null,
      profissionaisSelecionados: Object.keys(profissionaisSelecionados).length > 0 ? profissionaisSelecionados : null
    };

    body.localAgendamentoId = localIdNumber !== null ? localIdNumber : null;

    // Lote parcial: antes de gravar, pergunta ao backend o que aconteceria com
    // cada item. É só uma consulta — quem decide é o POST. Se ela não responder
    // (rede, backend antigo, sem acesso à unidade), segue direto para o POST,
    // que é o comportamento de sempre.
    const idDaSolicitacao = String(solicitacaoDetalhe.id);
    const examesDoLote = [...examesSelecionados];
    // Fotografia do que está sendo agendado, tirada no clique: o formulário
    // continua editável enquanto a verificação e o POST estão em andamento, e o
    // comprovante tem que sair com o paciente e a data que foram de fato enviados.
    const paciente: DadosDoEnvio = {
      solicitacaoId: solicitacaoDetalhe.id,
      nomePaciente: solicitacaoDetalhe.nomePaciente,
      cpfPaciente: solicitacaoDetalhe.cpfPaciente,
      usfOrigem: solicitacaoDetalhe.usfOrigem,
      unidadeNome: solicitacaoDetalhe.unidadeNome,
      cns: solicitacaoDetalhe.cns,
      dataAgendada,
      turno,
      localLabel: getLocalLabel(localAgendamentoId),
      observacoes
    };
    let verificacao: VerificacaoAgendamento | null = null;
    verificando = true;
    try {
      const resposta = await postApi(`agendamentos/${idDaSolicitacao}/verificar`, body);
      if (resposta.ok) {
        verificacao = await resposta.json();
      }
    } catch {
      /* segue para o POST */
    } finally {
      verificando = false;
    }

    if (!verificacao) {
      gravarAgendamento(idDaSolicitacao, body, examesDoLote, [], paciente);
      return;
    }

    const analise = analisarVerificacao(verificacao);
    if (analise.situacao === 'TUDO_OK') {
      gravarAgendamento(idDaSolicitacao, body, examesDoLote, [], paciente);
      return;
    }
    // Qualquer outra situação exige que o operador leia antes: o diálogo mostra
    // o que entra, o que fica de fora e por quê.
    confirmacaoParcial = { solicitacaoId: idDaSolicitacao, body, paciente, ...analise } as ConfirmacaoParcial;
  }

  /** Operador confirmou: grava só os itens que podem ser agendados. */
  function confirmarAgendamentoParcial() {
    const pendente = confirmacaoParcial;
    if (!pendente || pendente.situacao !== 'PARCIAL') return;
    const codigosViaveis = pendente.viaveis.map((item) => item.codigo);
    confirmacaoParcial = null;
    gravarAgendamento(
      pendente.solicitacaoId,
      montarBodyViaveis(pendente.body, codigosViaveis),
      codigosViaveis,
      pendente.recusados,
      pendente.paciente
    );
  }

  /**
   * Envia o POST e trata o resultado. `examesEnviados` são os códigos que estão
   * neste POST — o comprovante sai SÓ com eles, nunca com o que está marcado na
   * tela, que pode incluir itens que ficaram de fora do lote.
   */
  function gravarAgendamento(
    idDaSolicitacao: string,
    body: Record<string, unknown>,
    examesEnviados: string[],
    itensDeFora: ItemVerificado[],
    enviado: DadosDoEnvio
  ) {
    gravando = true;
    try {
      postApi(`agendamentos/${idDaSolicitacao}`, body).then(async (resposta) => {
        if (resposta.ok) {
          if (itensDeFora.length > 0) {
            alert(
              `Agendamento realizado: ${examesEnviados.length} de ${examesEnviados.length + itensDeFora.length} itens.\n\n`
              + `Continuam na fila, sem agendamento:\n`
              + itensDeFora.map((item) => `• ${getEspecialidadeLabel(item.codigo)}`).join('\n')
            );
          } else {
            alert('Agendamento realizado com sucesso!');
          }

          // Profissional real do paciente (V100): vem da resposta do POST,
          // que já resolve override do operador ou o da cota — evita depender
          // de estado local desatualizado no comprovante.
          const profissionalUnico = await (async () => {
            if (examesEnviados.length !== 1) return null;
            try {
              const dados = await resposta.clone().json();
              const especialidade = (dados.especialidades ?? []).find(
                (e: { codigo: string }) => e.codigo?.toUpperCase() === examesEnviados[0].toUpperCase()
              );
              if (especialidade?.profissionalExecutanteNome) {
                return especialidade.profissionalExecutanteNome as string;
              }
            } catch { /* segue para o fallback abaixo */ }
            return profissionalExibidoParaExame(examesEnviados[0])?.nome
              ?? cotasEspecificas(examesEnviados[0])[0]?.profissionalNome
              ?? null;
          })();

          // Horário real do paciente (V97/V99/V100): vem da resposta do POST, que já
          // inclui a hora calculada (cota dinâmica), sobrescrita ou a hora manual
          // validada — evita depender de estado local desatualizado no comprovante.
          const horarioInfo = await (async () => {
            if (examesEnviados.length !== 1) return null;
            try {
              const dados = await resposta.clone().json();
              const especialidade = (dados.especialidades ?? []).find(
                (e: { codigo: string }) => e.codigo?.toUpperCase() === examesEnviados[0].toUpperCase()
              );
              if (especialidade?.horaAgendada) {
                return formatarHora(especialidade.horaAgendada);
              }
            } catch { /* segue para o fallback abaixo */ }
            const cota = cotaResolvida(examesEnviados[0]);
            if (!cota) return null;
            return cota.horarioDinamico ? null : horarioManualPorExame[examesEnviados[0].toUpperCase()] || null;
          })();

          await gerarComprovantePDF({
            ...enviado,
            examesNomes: examesEnviados.map((exame) => getEspecialidadeLabel(exame)),
            profissionalNome: profissionalUnico,
            agendadoPorNome: get(usuarioLogado)?.nome ?? null,
            horarioInfo
          });

          examesSelecionados = [];
          dataAgendada = '';
          localAgendamentoId = '';
          observacoes = '';
          turno = 'MANHA';
          valorBusca = '';
          solicitacaoDetalhe = null;
          cotasAplicaveisPorEspecialidade = {};
          cotaEscolhidaPorExame = {};
          horarioManualPorExame = {};
          profissionalTermoPorExame = {};
          profissionalResultadosPorExame = {};
          profissionalSelecionadoPorExame = {};
          profissionalTocadoPorExame = {};

          await carregarSolicitacoesPendentes();
          await carregarLocaisAgendamento();
        } else {
          let mensagemErro = '';
          try {
            const texto = await resposta.text();
            mensagemErro = texto ? JSON.parse(texto).message ?? texto : '';
          } catch {
          }

          if (!mensagemErro) {
            mensagemErro = resposta.status === 403
              ? 'Acesso negado. Verifique se você está autenticado e possui permissão para agendar.'
              : 'Verifique os dados e tente novamente.';
          }

          alert(`Erro ao agendar: ${mensagemErro}`);
        }
      }).finally(() => {
        gravando = false;
      });
    } catch (err) {
      gravando = false;
      console.error('Erro na submissão do formulário:', err);
      alert('Erro de conexão. Verifique sua rede e tente novamente.');
    }
  }


  let preparoSelecionado = $state('');

    // textos pré-definidos de preparo
    const PREPAROS: Record<string, string> = {
      USG_ABD_TOTAL: `
    - Levar exames de imagem anteriores;
    - Jejum de 6 horas;
    - Tomar água e não ir ao banheiro antes do exame;
    - Não é necessário o uso de laxantes ou qualquer medicação;`,

      USG_PARTES_MOLES_TIREOIDE: `
    - Levar exames de imagem anteriores;`,

      USG_RINS_PROSTATA: `
    - Levar exames de imagem anteriores;
    - Manter a bexiga cheia para o exame
    (tomar água e não ir ao banheiro antes do exame);`,


    LABORATORIO: `
    - Horário do Laboratório 07:00
    `


    };

    // quando o usuário muda o select:
    function aplicarPreparo() {
      if (preparoSelecionado) {
        observacoes = PREPAROS[preparoSelecionado] ?? '';
      } else {
        observacoes = '';
      }
    }
</script>

<svelte:head>
    <title>Agendamento</title>
</svelte:head>

{#snippet iconeAlerta()}
  <svg class="ag-icone" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path d="M12 9v4m0 4h.01M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
  </svg>
{/snippet}

{#snippet iconeConfirmado()}
  <svg class="ag-icone" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <circle cx="12" cy="12" r="9" />
    <path d="m8.5 12.5 2.5 2.5 4.5-5" />
  </svg>
{/snippet}

<div class="flex min-h-screen bg-gray-100">
  <RoleBasedMenu activePage="/agendar" />

  <div class="flex-1 min-w-0 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Agendar Múltiplos Exames</h1>
          <UserMenu/>
    </header>

    <main class="flex-1 overflow-auto">
      <div class="cotas-ibm p-4 md:p-6 space-y-6">
      {#if isLoading}
        <div class="ibm-painel p-4 md:p-6">
          <LoadingSpinner mensagem="Carregando solicitações..." />
        </div>
      {:else if error && solicitacoes.length === 0}
        <div class="ibm-erro-bloco" role="alert">
          {@render iconeAlerta()}
          <p>
            <strong>Ocorreu um erro:</strong>
            <span>{error}</span>
          </p>
        </div>
      {:else}
          <form class="space-y-6" onsubmit={enviarAgendamento}>
            <!-- 1. Paciente: busca à esquerda, estado da seleção à direita -->
            <section class="ibm-painel p-4 md:p-6" aria-labelledby="agendar-titulo-paciente">
              <h2 id="agendar-titulo-paciente" class="ibm-grupo-titulo">
                <span class="ag-passo">1</span>
                Paciente
              </h2>
              <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 lg:gap-12">
                <div class="min-w-0">
                  <label for="combobox-agendamento" class="ibm-rotulo">
                    Buscar paciente com solicitação pendente
                  </label>
                  <p id="combobox-agendamento-ajuda" class="ibm-dica">
                    Digite o nome ou o CPF e <strong>escolha o paciente na lista</strong>: clique nele ou
                    use as setas ↑ ↓ e Enter. Só digitar não seleciona ninguém.
                  </p>
                  <div class="relative">
                    <svg class="ag-busca-icone" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                      <circle cx="11" cy="11" r="7" />
                      <path d="m20 20-3.5-3.5" />
                    </svg>
                    <input
                      id="combobox-agendamento"
                      type="text"
                      bind:value={valorBusca}
                      use:sugestoesTeclado={{ aberta: comboboxAberto, fechar: () => (comboboxAberto = false) }}
                      oninput={aoPreencherBusca}
                      onchange={aoPreencherBusca}
                      onkeydown={aoTeclarNaBusca}
                      onfocus={abrirCombobox}
                      onclick={abrirCombobox}
                      onblur={agendarFechamentoDoCombobox}
                      autocomplete="off"
                      autocapitalize="off"
                      spellcheck={false}
                      aria-controls="combobox-agendamento-lista"
                      aria-describedby="combobox-agendamento-ajuda"
                      placeholder="Digite o nome ou CPF para buscar..."
                      class="ibm-campo ag-busca w-full"
                    />

                    {#if comboboxAberto}
                      <ul
                        id="combobox-agendamento-lista"
                        role="listbox"
                        aria-label="Solicitações pendentes"
                        class="ag-sugestoes absolute z-10 w-full mt-1 transition-opacity"
                        class:opacity-50={buscandoSolicitacoes}
                      >
                        {#if solicitacoesFiltradas.length === 0 && !buscandoSolicitacoes}
                          <li role="presentation" class="ag-sugestoes-vazio">
                            {@render iconeAlerta()}
                            <span>
                              <strong>Nenhuma solicitação encontrada.</strong>
                              {#if haMaisResultados}
                                Há mais pacientes do que a busca mostra: digite o nome completo ou o CPF.
                              {/if}
                            </span>
                          </li>
                        {/if}
                        {#if solicitacoesFiltradas.length > 0}
                          <li role="presentation" class="ag-sugestoes-cabecalho">
                            Escolha o paciente: clique ou setas ↑ ↓ e Enter
                          </li>
                        {/if}
                        {#each solicitacoesFiltradas as s (s.id)}
                          <li role="presentation" class="ag-sugestoes-item">
                            <button
                              type="button"
                              role="option"
                              aria-selected="false"
                              tabindex="-1"
                              onmousedown={(event) => {
                                // Mantém o foco no campo de busca ao escolher.
                                event.preventDefault();
                                selecionarSolicitacao(s);
                              }}
                              class="ag-sugestao"
                            >
                              <span class="ag-sugestao-nome">{s.nomePaciente}</span>
                              <span class="ag-sugestao-dados">
                                <span><span class="ag-sugestao-rotulo">CPF:</span> {s.cpfPaciente || 'não informado'}</span>
                                <span><span class="ag-sugestao-rotulo">CNS:</span> {s.cns || 'N/A'}</span>
                                <span><span class="ag-sugestao-rotulo">Unidade:</span> {s.unidadeNome || 'Não informado'}</span>
                              </span>
                              <span class="ag-sugestao-acao" aria-hidden="true">Enter para escolher</span>
                            </button>
                          </li>
                        {/each}
                      </ul>
                      {#if buscandoSolicitacoes}
                        <div class="absolute z-20 w-full flex justify-center pt-4 pointer-events-none">
                          <LoadingSpinner tamanho={18} inline />
                        </div>
                      {/if}
                    {/if}
                    <p class="sr-only" aria-live="polite">
                      {#if comboboxAberto}
                        {#if buscandoSolicitacoes}
                          Buscando…
                        {:else if solicitacoesFiltradas.length === 0}
                          Nenhuma solicitação encontrada.
                        {:else}
                          {solicitacoesFiltradas.length} {solicitacoesFiltradas.length === 1 ? 'solicitação encontrada' : 'solicitações encontradas'}. Use as setas e Enter para escolher.
                        {/if}
                      {/if}
                    </p>
                  </div>
                  {#if error && solicitacoes.length > 0}
                    <p class="ibm-erro-texto ag-erro-linha" role="alert">{@render iconeAlerta()} <span>{error}</span></p>
                  {/if}
                </div>

                <div class="min-w-0 ibm-coluna-divisor">
                  <p class="ibm-rotulo">Paciente deste agendamento</p>

                  {#if carregandoDetalhe}
                    <div class="ag-carregando">
                      <LoadingSpinner tamanho={22} inline mensagem="Carregando dados do paciente..." />
                      <p class="ibm-ajuda">Os exames pendentes e os campos de data e local aparecem em seguida.</p>
                    </div>
                  {/if}

                  {#if !carregandoDetalhe && !solicitacaoDetalhe}
                    <div class="ag-vazio">
                      <svg class="ag-vazio-icone" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                        <circle cx="12" cy="8" r="4" />
                        <path d="M4 21a8 8 0 0 1 16 0" stroke-dasharray="3 3" />
                      </svg>
                      <div class="min-w-0">
                        <p class="ag-vazio-titulo">Nenhum paciente selecionado ainda</p>
                        {#if valorBusca.trim()}
                          <p class="ag-vazio-texto">
                            <strong>O texto digitado não seleciona o paciente.</strong> Escolha um nome na
                            lista de sugestões: clique nele ou use as setas ↑ ↓ e Enter.
                          </p>
                        {:else}
                          <p class="ag-vazio-texto">
                            Busque pelo nome ou CPF e escolha um nome na lista de sugestões.
                          </p>
                        {/if}
                        <p class="ag-vazio-texto">
                          Os exames pendentes, a data, o local e o botão de agendar aparecem depois da escolha.
                        </p>
                      </div>
                    </div>
                  {/if}

                  {#if solicitacaoDetalhe}
                    <div
                      class="ag-paciente"
                      role="group"
                      aria-labelledby="agendar-paciente-marca agendar-paciente-nome"
                    >
                      <div class="ag-paciente-topo">
                        <div class="min-w-0">
                          <p id="agendar-paciente-marca" class="ag-paciente-marca">
                            {@render iconeConfirmado()}
                            Paciente selecionado
                          </p>
                          <p id="agendar-paciente-nome" class="ag-paciente-nome">{solicitacaoDetalhe.nomePaciente}</p>
                        </div>
                        <button
                          type="button"
                          onclick={trocarPaciente}
                          disabled={envioEmAndamento}
                          class="ibm-btn ibm-btn--secundario"
                        >
                          Trocar paciente
                        </button>
                      </div>
                      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div class="sm:col-span-2">
                          <label for="campo-nome" class="ibm-rotulo">Nome</label>
                          <input id="campo-nome" type="text" value={solicitacaoDetalhe.nomePaciente} readonly class="ibm-campo w-full" />
                        </div>
                        <div>
                          <label for="campo-cpf" class="ibm-rotulo">CPF</label>
                          <input id="campo-cpf" type="text" value={solicitacaoDetalhe.cpfPaciente} readonly class="ibm-campo w-full" />
                        </div>
                        <div>
                          <label for="campo-cns" class="ibm-rotulo">CNS</label>
                          <input id="campo-cns" type="text" value={solicitacaoDetalhe.cns || 'N/A'} readonly class="ibm-campo w-full" />
                        </div>
                        <div class="sm:col-span-2">
                          <label for="campo-usf" class="ibm-rotulo">Unidade</label>
                          <input id="campo-usf" type="text" value={solicitacaoDetalhe.unidadeNome || solicitacaoDetalhe.usfOrigem || 'Não informado'} readonly class="ibm-campo w-full" />
                        </div>
                      </div>
                    </div>
                  {/if}
                </div>
              </div>
            </section>

            {#if !solicitacaoDetalhe}
              <!-- Estado vazio orientativo: mostra o que vem depois da escolha do paciente -->
              <div class="ag-proximos">
                <p class="ag-proximos-titulo">
                  {carregandoDetalhe
                    ? 'Carregando o restante do formulário...'
                    : 'O restante do formulário aparece depois de escolher o paciente'}
                </p>
                <ol class="ag-proximos-lista">
                  <li>
                    <span class="ag-passo ag-passo--inativo">2</span>
                    <span><strong>Exames e data:</strong> marcar os exames pendentes e informar data e turno.</span>
                  </li>
                  <li>
                    <span class="ag-passo ag-passo--inativo">3</span>
                    <span><strong>Local e orientações:</strong> local do agendamento, preparo e observações.</span>
                  </li>
                  <li>
                    <span class="ag-passo ag-passo--inativo">4</span>
                    <span><strong>Conferir e agendar:</strong> conferir o paciente e confirmar o agendamento.</span>
                  </li>
                </ol>
              </div>
            {/if}

            {#if solicitacaoDetalhe}
              <!-- 2. Exames e data -->
              <section class="ibm-painel p-4 md:p-6" aria-labelledby="agendar-titulo-exames">
                <h2 id="agendar-titulo-exames" class="ibm-grupo-titulo">
                  <span class="ag-passo">2</span>
                  Exames e data
                </h2>
                <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 lg:gap-12">
                  <fieldset class="ibm-conjunto">
                    <legend class="ibm-rotulo">
                      Exames pendentes ({examesSelecionados.length} marcado{examesSelecionados.length === 1 ? '' : 's'})
                    </legend>
                    {#if solicitacaoDetalhe.especialidades.length > 0}
                      <div class="ibm-lista-rolagem ibm-lista-rolagem--alta">
                        {#each solicitacaoDetalhe.especialidades as especialidade (especialidade.codigo)}
                          <label class="ibm-opcao ibm-opcao--linha ag-opcao-exame">
                            <input
                              type="checkbox"
                              value={especialidade.codigo}
                              bind:group={examesSelecionados}
                            />
                            <span>{especialidade.nome || getEspecialidadeLabel(especialidade.codigo)}</span>
                          </label>
                        {/each}
                      </div>
                      <p class="ibm-ajuda">Marque os exames que serão agendados agora.</p>
                    {:else}
                      <p class="ibm-aviso ag-com-icone">{@render iconeAlerta()} <span>Nenhum exame pendente para esta solicitação.</span></p>
                    {/if}
                  </fieldset>

                  {#if solicitacaoDetalhe.especialidades.length > 0}
                    <div class="ibm-coluna-divisor">
                      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
                        <div>
                          <label for="dataAgendada" class="ibm-rotulo">Data do agendamento *</label>
                          <input type="date" id="dataAgendada" bind:value={dataAgendada} class="ibm-campo w-full" aria-describedby="dataAgendada-ajuda" required />
                        </div>
                        <div>
                          <label for="turno" class="ibm-rotulo">Turno</label>
                          <select id="turno" bind:value={turno} class="ibm-campo w-full">
                            <option value="MANHA">Manhã</option>
                            <option value="TARDE">Tarde</option>
                          </select>
                        </div>
                      </div>
                      <p id="dataAgendada-ajuda" class="ibm-ajuda">
                        As vagas e a cota de cada exame marcado são consultadas para esta data.
                      </p>
                    </div>
                  {/if}
                </div>

                {#if examesSelecionados.length > 0}
                  <div class="ibm-divisor ag-vagas">
                    <h3 class="ibm-subtitulo">Contagem de vagas por especialidade selecionada</h3>
                    {#if !dataAgendada}
                      <p class="ibm-info ag-vagas-aviso">
                        Informe a data do agendamento para consultar as vagas e a cota de cada exame.
                      </p>
                    {/if}
                    <div class="space-y-4">
                      {#each examesSelecionados as codigo, indice}
                        {@const rotuloProfissional = `Profissional que atende (opcional${cotaResolvida(codigo) ? ' — substitui o definido pela cota' : ''})`}
                        <div class="ag-exame">
                          <p class="ag-exame-titulo">
                            {getEspecialidadeLabel(codigo)}
                            <span class="ag-exame-codigo">({codigo})</span>
                          </p>

                          <div class="grid grid-cols-1 lg:grid-cols-2 gap-4 lg:gap-12">
                            <div class="space-y-4 min-w-0">
                              <div class="ag-exame-bloco">
                                <p class="ag-exame-grupo">Vagas</p>
                                <p class="ag-linha">Vagas definidas: {getEspecialidadeVagas(codigo) === 0 ? 'Sem limite (0)' : getEspecialidadeVagas(codigo)}</p>
                                {#if contagemPorEspecialidade[codigo.toUpperCase()]}
                                  <p class="ag-linha">Agendados hoje: {contagemPorEspecialidade[codigo.toUpperCase()].agendados}</p>
                                  {#if getEspecialidadeVagas(codigo) === 0}
                                    <span class="ibm-badge ibm-badge--neutro">Sem limite de vagas</span>
                                  {:else}
                                    <p class="ag-linha">Restante: {contagemPorEspecialidade[codigo.toUpperCase()].restante}</p>
                                    {#if contagemPorEspecialidade[codigo.toUpperCase()].restante <= 0}
                                      <span class="ibm-badge ibm-badge--erro">{@render iconeAlerta()} Limite atingido — não é possível agendar mais para esta data</span>
                                    {/if}
                                  {/if}
                                {:else}
                                  <p class="ag-linha ibm-suave">Carregando contagem...</p>
                                {/if}
                              </div>

                              {#if saldoCotaPorEspecialidade[codigo.toUpperCase()] || saldoCotaPorDataEspecialidade[codigo.toUpperCase()]}
                                <div class="ag-exame-bloco">
                                  <p class="ag-exame-grupo">Cota</p>
                                  {#if saldoCotaPorEspecialidade[codigo.toUpperCase()]}
                                    {@const cota = saldoCotaPorEspecialidade[codigo.toUpperCase()]}
                                    {#if cota.quantidadeTotal == null}
                                      <p class="ag-linha ibm-suave">Sem cota mensal configurada.</p>
                                    {:else}
                                      <p class="ag-linha">
                                        Cota do mês: {cota.quantidadeUtilizada}/{cota.quantidadeTotal} — saldo {cota.saldoDisponivel}
                                      </p>
                                      {#if cota.saldoDisponivel !== null && cota.saldoDisponivel <= 0}
                                        <span class="ibm-badge ibm-badge--erro">{@render iconeAlerta()} Cota do mês esgotada</span>
                                      {/if}
                                    {/if}
                                  {/if}
                                  {#if saldoCotaPorDataEspecialidade[codigo.toUpperCase()]}
                                    {@const cotaData = saldoCotaPorDataEspecialidade[codigo.toUpperCase()]}
                                    {#if cotaData.quantidadeTotal != null}
                                      <p class="ag-linha">
                                        Cota da data: {cotaData.quantidadeUtilizada}/{cotaData.quantidadeTotal} — saldo {cotaData.saldoDisponivel}
                                      </p>
                                      {#if cotaData.saldoDisponivel !== null && cotaData.saldoDisponivel <= 0}
                                        <span class="ibm-badge ibm-badge--erro">{@render iconeAlerta()} Cota da data esgotada</span>
                                      {/if}
                                    {/if}
                                  {/if}
                                </div>
                              {/if}
                            </div>

                            <div class="space-y-4 min-w-0 ibm-coluna-divisor">
                              {#if cotasEspecificas(codigo).length === 1}
                                {@const cota = cotasEspecificas(codigo)[0]}
                                <div class="ag-exame-bloco">
                                  <p class="ag-exame-grupo">Atendimento</p>
                                  <div class="ag-atendimento">
                                    {#if cota.profissionalNome}<p><strong>Profissional:</strong> {cota.profissionalNome}</p>{/if}
                                    {#if cota.localAgendamentoNome}<p><strong>Local:</strong> {cota.localAgendamentoNome}</p>{/if}
                                    {#if cota.horarioDinamico}
                                      <p><strong>Horário:</strong> calculado automaticamente ao agendar (período {formatarHora(cota.horaInicial)} às {formatarHora(cota.horaFinal)})</p>
                                    {:else if cota.horaInicial}
                                      <p><strong>Período de referência:</strong> {formatarHora(cota.horaInicial)} às {formatarHora(cota.horaFinal)}</p>
                                    {/if}
                                  </div>
                                </div>
                              {:else if cotasEspecificas(codigo).length > 1}
                                <div class="ag-exame-bloco">
                                  <p class="ag-exame-grupo">Atendimento</p>
                                  <label for="cota-exame-{indice}" class="ibm-rotulo">
                                    Mais de uma cota de atendimento disponível — escolha uma:
                                  </label>
                                  <select id="cota-exame-{indice}" bind:value={cotaEscolhidaPorExame[codigo.toUpperCase()]}
                                    class="ibm-campo w-full">
                                    <option value={null}>Selecionar cota...</option>
                                    {#each cotasEspecificas(codigo) as cota (cota.id)}
                                      <option value={cota.id}>
                                        {cota.profissionalNome ?? 'Sem profissional'}
                                        {#if cota.localAgendamentoNome}{' '}— {cota.localAgendamentoNome}{/if}
                                        {#if cota.horaInicial}{' '}({formatarHora(cota.horaInicial)} às {formatarHora(cota.horaFinal)}){/if}
                                      </option>
                                    {/each}
                                  </select>
                                </div>
                              {/if}
                              <div>
                                <label for="hora-exame-{indice}" class="ibm-rotulo">
                                  Hora do atendimento (opcional{cotaResolvida(codigo)?.horaInicial
                                    ? ` — período de referência: ${formatarHora(cotaResolvida(codigo)?.horaInicial ?? null)} às ${formatarHora(cotaResolvida(codigo)?.horaFinal ?? null)}`
                                    : ''})
                                </label>
                                <input id="hora-exame-{indice}" type="time" bind:value={horarioManualPorExame[codigo.toUpperCase()]}
                                  min={cotaResolvida(codigo)?.horaInicial ? formatarHora(cotaResolvida(codigo)?.horaInicial ?? null) : undefined}
                                  max={cotaResolvida(codigo)?.horaFinal ? formatarHora(cotaResolvida(codigo)?.horaFinal ?? null) : undefined}
                                  aria-invalid={horaManualForaDoPeriodo(codigo)}
                                  aria-describedby={horaManualForaDoPeriodo(codigo) ? `hora-exame-${indice}-erro` : undefined}
                                  class="ibm-campo w-full sm:w-48"
                                  class:ibm-campo--erro={horaManualForaDoPeriodo(codigo)} />
                                {#if cotaResolvida(codigo)?.horarioDinamico}
                                  <p class="ibm-ajuda">
                                    Calculado automaticamente ao confirmar — deixe em branco para manter o cálculo, ou informe um horário para substituí-lo.
                                  </p>
                                {/if}
                                {#if horaManualForaDoPeriodo(codigo)}
                                  <p id="hora-exame-{indice}-erro" class="ibm-erro-texto ag-erro-linha">
                                    {@render iconeAlerta()}
                                    <span>Hora fora do período liberado pela cota ({formatarHora(cotaResolvida(codigo)?.horaInicial ?? null)} às {formatarHora(cotaResolvida(codigo)?.horaFinal ?? null)}).</span>
                                  </p>
                                {/if}
                              </div>

                              <div>
                                {#if profissionalExibidoParaExame(codigo)}
                                  <p id="profissional-exame-{indice}-rotulo" class="ibm-rotulo">{rotuloProfissional}</p>
                                  <div class="ibm-selecionado" role="group" aria-labelledby="profissional-exame-{indice}-rotulo">
                                    <span>{profissionalExibidoParaExame(codigo)?.nome}</span>
                                    <button type="button" onclick={() => removerProfissionalParaExame(codigo)}
                                      class="ibm-btn ibm-btn--fantasma">Remover</button>
                                  </div>
                                {:else}
                                  <label for="profissional-exame-{indice}" class="ibm-rotulo">{rotuloProfissional}</label>
                                  <input id="profissional-exame-{indice}" type="text" placeholder="Buscar profissional por nome..."
                                    bind:value={profissionalTermoPorExame[codigo]}
                                    oninput={() => buscarProfissionalParaExame(codigo)}
                                    class="ibm-campo w-full" />
                                  {#if profissionalBuscandoPorExame[codigo]}
                                    <p class="ibm-ajuda" role="status">Buscando...</p>
                                  {:else if (profissionalResultadosPorExame[codigo] ?? []).length > 0}
                                    <ul class="ibm-resultados">
                                      {#each profissionalResultadosPorExame[codigo] as p (p.id)}
                                        <li>
                                          <button type="button" onclick={() => selecionarProfissionalParaExame(codigo, p)}
                                            class="ibm-resultado">
                                            {p.nome}
                                          </button>
                                        </li>
                                      {/each}
                                    </ul>
                                  {/if}
                                {/if}
                              </div>
                            </div>
                          </div>
                        </div>
                      {/each}
                    </div>
                  </div>
                {/if}
              </section>

              {#if solicitacaoDetalhe.especialidades.length > 0}
                <!-- 3. Local e orientações -->
                <section class="ibm-painel p-4 md:p-6" aria-labelledby="agendar-titulo-local">
                  <h2 id="agendar-titulo-local" class="ibm-grupo-titulo">
                    <span class="ag-passo">3</span>
                    Local e orientações
                  </h2>
                  <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 lg:gap-12">
                    <div class="space-y-6 min-w-0">
                      <div>
                        <label for="localAgendamentoId" class="ibm-rotulo">Local do agendamento *</label>
                        <select id="localAgendamentoId" bind:value={localAgendamentoId} disabled={localTravadoPorCota}
                          aria-describedby={localTravadoPorCota ? 'localAgendamentoId-ajuda' : undefined}
                          class="ibm-campo w-full" required>
                          <option value="" disabled>Selecione o local...</option>
                          {#if locaisAgendamento.length === 0}
                            <option disabled>Nenhum local cadastrado</option>
                          {:else}
                            {#each locaisAgendamento as loc}
                              <option value={loc.id}>
                                {loc.nomeLocal}
                                {#if loc.cidadeNome}
                                  {' '}- {loc.cidadeNome}
                                {/if}
                              </option>
                            {/each}
                          {/if}
                        </select>
                        {#if localTravadoPorCota}
                          <p id="localAgendamentoId-ajuda" class="ibm-ajuda ag-com-icone">
                            <svg class="ag-icone" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                              <rect x="5" y="11" width="14" height="9" rx="1" />
                              <path d="M8 11V7a4 4 0 0 1 8 0v4" />
                            </svg>
                            <span>Local definido pela cota liberada — não pode ser alterado aqui.</span>
                          </p>
                        {/if}
                      </div>

                      <div>
                        <label for="preparo" class="ibm-rotulo">Preparo</label>
                        <select name="" id="preparo" class="ibm-campo w-full" aria-describedby="preparo-ajuda" bind:value={preparoSelecionado} onchange={aplicarPreparo}>
                          <option value="">Selecione...</option>
                          <option value="USG_ABD_TOTAL">Preparo de USG Abdomen Total</option>
                          <option value="USG_PARTES_MOLES_TIREOIDE">Preparo de USG PARTES MOLES E USG TIREOIDE </option>
                          <option value="USG_RINS_PROSTATA">Preparo de USG DE US DE RINS E VIAS URINÁRIAS,
USG DE PRÓSTATA</option>
                            <option value="LABORATORIO">Preparo de Laboratório</option>
                        </select>
                        <p id="preparo-ajuda" class="ibm-ajuda">
                          Ao escolher um preparo, o texto das observações é substituído pelo texto do preparo.
                        </p>
                      </div>
                    </div>

                    <div class="min-w-0 ibm-coluna-divisor">
                      <label for="observacoes" class="ibm-rotulo">Observações</label>
                      <textarea id="observacoes" bind:value={observacoes} rows="6" class="ibm-campo w-full"></textarea>
                    </div>
                  </div>
                </section>

                <!-- 4. Conferir e agendar: repete o paciente (texto, não campo) junto do botão -->
                <section class="ibm-painel p-4 md:p-6" aria-labelledby="agendar-titulo-conferir">
                  <h2 id="agendar-titulo-conferir" class="ibm-grupo-titulo">
                    <span class="ag-passo">4</span>
                    Conferir e agendar
                  </h2>
                  <div class="ag-envio">
                    <div class="ag-envio-paciente">
                      <p class="ag-paciente-marca">
                        {@render iconeConfirmado()}
                        Agendando para
                      </p>
                      <p class="ag-paciente-nome">{solicitacaoDetalhe.nomePaciente}</p>
                      <p class="ag-envio-dados">
                        <span><strong>CPF:</strong> {solicitacaoDetalhe.cpfPaciente || 'não informado'}</span>
                        <span><strong>CNS:</strong> {solicitacaoDetalhe.cns || 'N/A'}</span>
                        <span><strong>Unidade:</strong> {solicitacaoDetalhe.unidadeNome || solicitacaoDetalhe.usfOrigem || 'Não informado'}</span>
                      </p>
                    </div>
                    <div class="ag-envio-acao">
                      {#if examesSelecionados.length === 0}
                        <p class="ibm-ajuda ag-envio-ajuda">Marque pelo menos um exame para liberar o agendamento.</p>
                      {:else}
                        <p class="ibm-ajuda ag-envio-ajuda">
                          {examesSelecionados.length} exame{examesSelecionados.length === 1 ? '' : 's'} marcado{examesSelecionados.length === 1 ? '' : 's'}. Campos com * são obrigatórios.
                        </p>
                      {/if}
                      <button
                        type="submit"
                        class="ibm-btn ibm-btn--grande ibm-btn--primario"
                        disabled={examesSelecionados.length === 0 || verificando}
                      >
                        {verificando ? 'Verificando vagas...' : 'Agendar Exames Selecionados'}
                      </button>
                    </div>
                  </div>
                </section>
              {/if}
            {/if}
          </form>
      {/if}
      </div>
    </main>
  </div>
</div>

<!-- Lote parcial: o que entra, o que fica de fora e por quê. Bloqueia a tela de
     propósito — enquanto está aberto o operador não muda paciente, exames nem data,
     então o que ele confirma é exatamente o que foi verificado. -->
{#if confirmacaoParcial}
  {@const c = confirmacaoParcial}
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-2 sm:p-4">
    <!-- Cabeçalho e rodapé fixos; só o miolo rola. Assim os botões nunca somem,
         mesmo com lista longa ou tela baixa. -->
    <div
      class="flex max-h-[calc(100dvh-1rem)] w-full max-w-4xl flex-col rounded-2xl bg-white shadow-xl focus:outline-none sm:max-h-[calc(100dvh-2rem)]"
      role="alertdialog"
      aria-modal="true"
      aria-labelledby="confirmacao-parcial-titulo"
      aria-describedby="confirmacao-parcial-resumo"
      tabindex="-1"
      use:dialogoModal={() => (confirmacaoParcial = null)}
    >
      <div class="flex shrink-0 items-start gap-3 border-b border-gray-200 px-4 py-4 sm:px-6">
        <span
          class="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-full {c.situacao === 'PARCIAL' ||
          c.situacao === 'CORRIGIR'
            ? 'bg-amber-100 text-amber-800'
            : 'bg-red-100 text-red-800'}"
          aria-hidden="true"
        >
          <svg class="h-6 w-6" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M12 9v4m0 4h.01M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
          </svg>
        </span>
        <div class="min-w-0">
          <h2 id="confirmacao-parcial-titulo" class="text-lg font-semibold text-gray-900">
            {#if c.situacao === 'PARCIAL'}
              Nem todos os itens podem ser agendados
            {:else if c.situacao === 'CORRIGIR'}
              Corrija os itens abaixo para agendar
            {:else if c.situacao === 'BLOQUEADO'}
              Agendamento bloqueado
            {:else}
              Nenhum item pode ser agendado
            {/if}
          </h2>
          <p class="mt-1 text-sm font-medium text-gray-900">Paciente: {c.paciente.nomePaciente}</p>
          <p id="confirmacao-parcial-resumo" class="mt-1 text-sm text-gray-700">
            {#if c.situacao === 'PARCIAL'}
              <strong class="font-semibold text-red-800">
                {c.recusados.length}
                {c.recusados.length === 1 ? 'item fica de fora' : 'itens ficam de fora'}
              </strong>
              e {c.recusados.length === 1 ? 'continua' : 'continuam'} na fila.
              {c.viaveis.length === 1 ? 'Será agendado' : 'Serão agendados'} só {c.viaveis.length} de {c.viaveis.length +
                c.recusados.length}. Leia os motivos antes de confirmar.
            {:else if c.situacao === 'CORRIGIR'}
              Nada foi gravado. Volte, corrija o que está indicado e envie de novo.
            {:else}
              Nada foi gravado.
            {/if}
          </p>
        </div>
      </div>

      <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
      <div
        class="min-h-0 flex-1 space-y-6 overflow-y-auto overscroll-contain px-4 py-4 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-emerald-700 sm:px-6"
        role="group"
        aria-label="Detalhes dos itens"
        tabindex="0"
      >
        {#if c.bloqueioDoLote}
          <div class="rounded-lg border border-l-4 border-red-300 border-l-red-700 bg-red-50 p-4">
            <p class="text-xs font-semibold uppercase tracking-wide text-red-800">Motivo do bloqueio</p>
            <p class="mt-1 text-base font-medium text-red-900">{c.bloqueioDoLote}</p>
          </div>
        {/if}

        {#if c.corrigiveis.length > 0}
          <section aria-labelledby="confirmacao-parcial-corrigir">
            <h3 id="confirmacao-parcial-corrigir" class="flex items-center gap-2 text-sm font-semibold text-amber-900">
              <svg class="h-5 w-5 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <path d="M12 20h9M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4 12.5-12.5z" />
              </svg>
              Precisa de correção ({c.corrigiveis.length})
            </h3>
            <ul class="mt-2 grid grid-cols-1 gap-2 lg:grid-cols-2">
              {#each c.corrigiveis as item (item.codigo)}
                <li class="rounded-lg border border-l-4 border-amber-300 border-l-amber-600 bg-amber-50 p-3">
                  <p class="text-sm font-semibold text-gray-900">{getEspecialidadeLabel(item.codigo)}</p>
                  <p class="mt-1 text-sm text-amber-950">
                    <span class="font-medium">O que corrigir:</span>
                    {item.motivo}
                  </p>
                </li>
              {/each}
            </ul>
          </section>
        {/if}

        {#if c.recusados.length > 0}
          <section aria-labelledby="confirmacao-parcial-fora">
            <h3 id="confirmacao-parcial-fora" class="flex items-center gap-2 text-sm font-semibold text-red-800">
              <svg class="h-5 w-5 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <circle cx="12" cy="12" r="9" />
                <path d="m15 9-6 6m0-6 6 6" />
              </svg>
              {c.situacao === 'PARCIAL' ? 'Ficam de fora e continuam na fila' : 'Não podem ser agendados'} ({c.recusados.length})
            </h3>
            <ul class="mt-2 grid grid-cols-1 gap-2 lg:grid-cols-2">
              {#each c.recusados as item (item.codigo)}
                <li class="rounded-lg border border-l-4 border-red-300 border-l-red-700 bg-red-50 p-3">
                  <p class="text-sm font-semibold text-gray-900">{getEspecialidadeLabel(item.codigo)}</p>
                  <p class="mt-1 text-sm text-red-900">
                    <span class="font-medium">Motivo:</span>
                    {item.motivo}
                  </p>
                </li>
              {/each}
            </ul>
          </section>
        {/if}

        {#if c.situacao === 'PARCIAL'}
          <section aria-labelledby="confirmacao-parcial-entram">
            <h3 id="confirmacao-parcial-entram" class="flex items-center gap-2 text-sm font-semibold text-emerald-800">
              <svg class="h-5 w-5 shrink-0" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                <circle cx="12" cy="12" r="9" />
                <path d="m8.5 12.5 2.5 2.5 4.5-5" />
              </svg>
              Serão agendados ({c.viaveis.length})
            </h3>
            <ul class="mt-2 grid grid-cols-1 gap-2 sm:grid-cols-2 lg:grid-cols-3">
              {#each c.viaveis as item (item.codigo)}
                <li class="rounded-lg border border-emerald-300 bg-emerald-50 px-3 py-2 text-sm font-medium text-gray-900">
                  {getEspecialidadeLabel(item.codigo)}
                </li>
              {/each}
            </ul>
          </section>
        {/if}
      </div>

      <div class="flex shrink-0 flex-col-reverse gap-3 border-t border-gray-200 bg-gray-50 px-4 py-4 sm:flex-row sm:items-center sm:justify-end sm:px-6 rounded-b-2xl">
        <button
          type="button"
          data-foco-inicial
          class="min-h-11 rounded-lg border border-gray-500 bg-white px-5 py-2 font-medium text-gray-800 transition hover:bg-gray-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700"
          onclick={() => (confirmacaoParcial = null)}
        >
          {c.situacao === 'CORRIGIR' ? 'Voltar e corrigir' : 'Voltar'}
        </button>
        {#if c.situacao === 'PARCIAL'}
          <button
            type="button"
            class="min-h-11 rounded-lg bg-emerald-800 px-5 py-2 font-medium text-white transition hover:bg-emerald-900 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-700"
            onclick={confirmarAgendamentoParcial}
          >
            Agendar somente {c.viaveis.length} de {c.viaveis.length + c.recusados.length} itens
          </button>
        {/if}
      </div>
    </div>
  </div>
{/if}

<style>
  /*
   * Mistura SIRG + Carbon (IBM), escopada a esta tela (ADR 0007).
   * ORIGEM: copiado de src/routes/admin/cotas/nova/+page.svelte (quinta cópia; as
   * outras são admin/cotas, admin/cotas/[id]/editar e unidade/cotas). Mesmos nomes
   * de token e de classe. Só vieram as classes que esta tela usa; as de "dias da
   * semana" e "resultado em lote" ficaram de fora. `--ibm-erro-fundo` tem o valor
   * de unidade/cotas. O que é próprio desta tela usa o prefixo `ag-` e fica depois
   * do bloco copiado. Extrair para um arquivo compartilhado é pedido à parte
   * (DESIGN.md, seção 10): ao mudar um token aqui, confira as outras cópias.
   * O acento é o emerald do SIRG. A fonte é uma pilha com fallback: nada é baixado.
   */
  .cotas-ibm {
    --ibm-acento: #047857;
    --ibm-acento-hover: #065f46;
    --ibm-acento-ativo: #064e3b;
    --ibm-acento-suave: #d1fae5;
    --ibm-acento-suave-texto: #065f46;
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
    --ibm-erro-fundo: #ffd7d9;
    --ibm-aviso-fundo: #fcf4d6;
    --ibm-aviso-borda: #f1c21b;

    /* --- Mistura SIRG/IBM: ajuste aqui --- */
    /* Painel: cartão do SIRG (rounded-lg + shadow). IBM: 0 / none / 1px solid var(--ibm-linha) */
    --ibm-raio-painel: 8px;
    --ibm-sombra-painel: 0 1px 3px 0 rgb(0 0 0 / 0.1), 0 1px 2px -1px rgb(0 0 0 / 0.1);
    --ibm-borda-painel: 0;
    /* Botões, listas e avisos: rounded-md do SIRG. IBM: 0 */
    --ibm-raio-controle: 6px;
    /* Campos (cinza com linha inferior): estilo IBM, mantido. SIRG seria 6px */
    --ibm-raio-campo: 0;
    /* Título de painel: SIRG (emerald-800, negrito, border-b). IBM: peso 400, barra lateral */
    --ibm-titulo-cor: #065f46;
    --ibm-titulo-peso: 700;
    --ibm-titulo-tamanho: 18px;
    --ibm-titulo-divisor: 1px solid #e5e7eb;
    /* Botão secundário: claro como no SIRG. IBM: fundo #393939, texto branco */
    --ibm-secundario-fundo: #ffffff;
    --ibm-secundario-texto: #374151;
    --ibm-secundario-hover: #f9fafb;

    /* Só nesta tela: fundo do bloco "paciente selecionado" (emerald-50 do SIRG) */
    --ag-selecionado-fundo: #ecfdf5;

    background: var(--ibm-fundo);
    color: var(--ibm-tinta);
    font-family: var(--ibm-fonte);
    font-size: 14px;
    line-height: 1.29;
    letter-spacing: 0.16px;
  }

  /* Painel: cartão branco (raio, sombra e borda vêm dos tokens --ibm-*-painel) */
  .ibm-painel {
    background: var(--ibm-camada);
    border: var(--ibm-borda-painel);
    border-radius: var(--ibm-raio-painel);
    box-shadow: var(--ibm-sombra-painel);
  }

  /* Título de painel: padrão SIRG (text-lg font-bold emerald-800 + border-b) */
  .ibm-grupo-titulo {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px 12px;
    margin-bottom: 24px;
    padding-bottom: 8px;
    border-bottom: var(--ibm-titulo-divisor);
    font-size: var(--ibm-titulo-tamanho);
    font-weight: var(--ibm-titulo-peso);
    line-height: 1.4;
    color: var(--ibm-titulo-cor);
  }

  /* Subtítulo de grupo dentro de um painel */
  .ibm-subtitulo {
    margin-bottom: 16px;
    font-size: 14px;
    font-weight: 600;
    line-height: 1.43;
    color: var(--ibm-tinta);
  }

  /* Duas colunas no mesmo painel: filete vertical só quando lado a lado */
  @media (min-width: 1024px) {
    .ibm-coluna-divisor {
      padding-left: 23px;
      margin-left: -24px;
      border-left: 1px solid var(--ibm-linha);
    }
  }

  /* Rótulo, ajuda e agrupamento */
  .ibm-rotulo {
    display: block;
    margin-bottom: 8px;
    padding: 0;
    font-size: 12px;
    line-height: 1.33;
    letter-spacing: 0.32px;
    color: var(--ibm-tinta-suave);
  }
  .ibm-ajuda {
    margin-top: 8px;
    font-size: 12px;
    line-height: 1.33;
    letter-spacing: 0.32px;
    color: var(--ibm-tinta-suave);
  }
  .ibm-conjunto {
    min-width: 0;
    padding: 0;
    border: 0;
  }
  .ibm-divisor {
    padding-top: 24px;
    border-top: 1px solid var(--ibm-linha);
  }
  .ibm-suave {
    color: var(--ibm-tinta-suave);
  }
  .ibm-erro-texto {
    color: var(--ibm-erro-texto);
    font-size: 14px;
  }

  /* Campos: fundo cinza, borda inferior de 3:1, foco em contorno de 2 px */
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

  /* Radio e checkbox: alvo de 40 px, marcação emerald (extrapolação do Carbon) */
  .ibm-opcao {
    display: flex;
    align-items: center;
    gap: 8px;
    min-height: 40px;
    cursor: pointer;
    font-size: 14px;
    color: var(--ibm-tinta);
  }
  .ibm-opcao input {
    flex: none;
    width: 20px;
    height: 20px;
    margin: 0;
    border: 1px solid var(--ibm-tinta);
    background-color: var(--ibm-camada);
    color: var(--ibm-acento);
    box-shadow: none;
    cursor: pointer;
  }
  .ibm-opcao input:checked {
    border-color: var(--ibm-acento);
    background-color: currentColor;
  }
  .ibm-opcao input:focus,
  .ibm-opcao input:focus-visible {
    outline: 2px solid var(--ibm-acento);
    outline-offset: 2px;
    box-shadow: none;
  }
  .ibm-opcao--linha {
    padding: 0 16px;
  }
  .ibm-opcao--linha:hover {
    background: var(--ibm-hover);
  }

  /* Listas dentro de formulário */
  .ibm-lista-rolagem {
    max-height: 192px;
    overflow-y: auto;
    border: 1px solid var(--ibm-linha);
    border-radius: var(--ibm-raio-controle);
    background: var(--ibm-camada);
  }

  /* Profissional: selecionado e resultados da busca */
  .ibm-selecionado {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    min-height: 40px;
    padding: 0 8px 0 16px;
    border-radius: var(--ibm-raio-controle);
    background: var(--ibm-acento-suave);
    color: var(--ibm-acento-suave-texto);
  }
  .ibm-resultados {
    margin-top: 4px;
    max-height: 160px;
    overflow-y: auto;
    border: 1px solid var(--ibm-linha);
    border-radius: var(--ibm-raio-controle);
    background: var(--ibm-camada);
  }
  .ibm-resultado {
    display: block;
    width: 100%;
    min-height: 40px;
    padding: 0 16px;
    text-align: left;
    font-family: inherit;
    font-size: 14px;
    color: var(--ibm-tinta);
    cursor: pointer;
  }
  .ibm-resultado:hover {
    background: var(--ibm-hover);
  }
  .ibm-resultado:focus-visible {
    outline: 2px solid var(--ibm-acento);
    outline-offset: -2px;
  }

  /* Mensagens em linha */
  .ibm-info {
    padding: 12px 16px;
    border-left: 4px solid var(--ibm-acento);
    border-radius: 0 var(--ibm-raio-controle) var(--ibm-raio-controle) 0;
    background: var(--ibm-fundo);
    color: var(--ibm-tinta);
    font-size: 14px;
    line-height: 1.43;
  }
  .ibm-aviso {
    padding: 12px 16px;
    border-left: 4px solid var(--ibm-aviso-borda);
    border-radius: 0 var(--ibm-raio-controle) var(--ibm-raio-controle) 0;
    background: var(--ibm-aviso-fundo);
    color: var(--ibm-tinta);
    font-size: 14px;
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

  /* Botões: texto de ação */
  .ibm-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 40px;
    padding: 0 16px;
    border: 1px solid transparent;
    border-radius: var(--ibm-raio-controle);
    font-family: inherit;
    font-size: 14px;
    font-weight: 500;
    letter-spacing: 0.16px;
    white-space: nowrap;
    cursor: pointer;
  }
  .ibm-btn:focus-visible {
    outline: 2px solid var(--ibm-acento);
    outline-offset: 2px;
  }
  .ibm-btn:disabled {
    background: var(--ibm-linha);
    color: var(--ibm-tinta-suave);
    cursor: not-allowed;
  }
  .ibm-btn--grande {
    min-height: 48px;
    padding: 0 24px;
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
    border-color: var(--ibm-borda-campo);
    background: var(--ibm-secundario-fundo);
    color: var(--ibm-secundario-texto);
  }
  .ibm-btn--secundario:hover {
    background: var(--ibm-secundario-hover);
  }
  .ibm-btn--secundario:active {
    background: var(--ibm-hover);
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

  /* ===== Fim do bloco copiado. Daqui em diante: só de /agendar ===== */

  /* Extensões das classes ibm-* que as telas de cotas não precisaram */
  .ibm-dica {
    margin: -4px 0 8px;
    font-size: 12px;
    line-height: 1.5;
    letter-spacing: 0.32px;
    color: var(--ibm-tinta-suave);
  }
  .ibm-dica strong {
    color: var(--ibm-tinta);
    font-weight: 600;
  }
  textarea.ibm-campo {
    display: block;
    padding: 11px 16px;
    line-height: 1.43;
    resize: vertical;
  }
  .ibm-campo:disabled {
    border-bottom-style: dashed;
    color: var(--ibm-tinta-suave);
    cursor: not-allowed;
    opacity: 1;
  }
  /* Somente leitura (dados do paciente): sem cara de campo editável */
  .ibm-campo[readonly] {
    padding: 0 8px;
    border-bottom-color: var(--ibm-linha);
    background-color: rgb(255 255 255 / 0.7);
    font-weight: 600;
    cursor: default;
  }
  /* Erro: linha inferior grossa + ícone e texto ao lado (não só cor) */
  .ibm-campo--erro,
  .ibm-campo--erro:focus {
    border-bottom: 2px solid var(--ibm-erro-texto);
  }
  .ibm-lista-rolagem--alta {
    max-height: 288px;
  }
  .ibm-badge--erro {
    gap: 4px;
    padding: 2px 8px;
    border-color: var(--ibm-erro-texto);
    border-radius: 12px;
    background: var(--ibm-erro-fundo);
    color: var(--ibm-erro-texto);
    font-weight: 600;
    white-space: normal;
  }
  .ibm-erro-bloco {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    padding: 12px 16px;
    border-left: 4px solid var(--ibm-erro-texto);
    border-radius: 0 var(--ibm-raio-controle) var(--ibm-raio-controle) 0;
    background: var(--ibm-erro-fundo);
    color: var(--ibm-tinta);
    font-size: 14px;
    line-height: 1.43;
  }
  .ibm-erro-bloco .ag-icone {
    margin-top: 2px;
    color: var(--ibm-erro-texto);
  }

  /* Ícone de linha e texto com ícone */
  .ag-icone {
    flex: none;
    width: 16px;
    height: 16px;
  }
  .ag-com-icone,
  .ag-erro-linha {
    display: flex;
    align-items: flex-start;
    gap: 8px;
  }
  .ag-erro-linha {
    margin-top: 8px;
    font-weight: 500;
  }
  .ag-com-icone .ag-icone,
  .ag-erro-linha .ag-icone {
    margin-top: 1px;
  }

  /* Número do passo no título de cada painel */
  .ag-passo {
    display: inline-flex;
    flex: none;
    align-items: center;
    justify-content: center;
    width: 24px;
    height: 24px;
    border-radius: 9999px;
    background: var(--ibm-acento);
    color: #ffffff;
    font-size: 13px;
    font-weight: 700;
    line-height: 1;
    letter-spacing: 0;
  }
  .ag-passo--inativo {
    background: var(--ibm-linha);
    color: var(--ibm-tinta-suave);
  }

  /* Busca de paciente: campo mais alto, com lupa */
  .ag-busca {
    min-height: 48px;
    padding-left: 44px;
    font-size: 16px;
  }
  .ag-busca-icone {
    position: absolute;
    top: 14px;
    left: 14px;
    width: 20px;
    height: 20px;
    color: var(--ibm-tinta-suave);
    pointer-events: none;
  }

  /* Lista de sugestões: cada linha traz nome, CPF, CNS e unidade */
  .ag-sugestoes {
    max-height: 320px;
    overflow-y: auto;
    scroll-padding-top: 36px; /* a opção ativa não fica atrás do cabeçalho fixo */
    border: 1px solid var(--ibm-borda-campo);
    border-radius: var(--ibm-raio-controle);
    background: var(--ibm-camada);
    box-shadow:
      0 10px 15px -3px rgb(0 0 0 / 0.1),
      0 4px 6px -4px rgb(0 0 0 / 0.1);
  }
  .ag-sugestoes-cabecalho {
    position: sticky;
    top: 0;
    z-index: 1;
    padding: 8px 16px;
    border-bottom: 1px solid var(--ibm-linha);
    background: var(--ibm-fundo);
    color: var(--ibm-tinta);
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.32px;
  }
  .ag-sugestoes-vazio {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    padding: 12px 16px;
    color: var(--ibm-tinta);
    font-size: 14px;
    line-height: 1.43;
  }
  .ag-sugestoes-vazio .ag-icone {
    margin-top: 2px;
    color: var(--ibm-tinta-suave);
  }
  .ag-sugestoes-item + .ag-sugestoes-item {
    border-top: 1px solid var(--ibm-linha);
  }
  .ag-sugestao {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
    width: 100%;
    padding: 10px 16px 10px 12px;
    border-left: 4px solid transparent;
    background: var(--ibm-camada);
    color: var(--ibm-tinta);
    font-family: inherit;
    text-align: left;
    cursor: pointer;
  }
  .ag-sugestao-nome {
    font-size: 15px;
    font-weight: 500;
    line-height: 1.33;
    overflow-wrap: anywhere;
  }
  .ag-sugestao-dados {
    display: flex;
    flex-wrap: wrap;
    gap: 2px 16px;
    color: var(--ibm-tinta-suave);
    font-size: 13px;
    line-height: 1.38;
    overflow-wrap: anywhere;
  }
  .ag-sugestao-rotulo {
    font-weight: 600;
  }
  .ag-sugestao-acao {
    display: none;
    padding: 2px 8px;
    border: 1px solid var(--ibm-acento);
    border-radius: var(--ibm-raio-controle);
    background: var(--ibm-camada);
    color: var(--ibm-acento-suave-texto);
    font-size: 12px;
    font-weight: 600;
  }
  .ag-sugestao:hover {
    border-left-color: var(--ibm-borda-campo);
    background: var(--ibm-hover);
  }
  /*
   * Opção ativa (setas do teclado): $lib/sugestoesTeclado.js marca o botão com
   * aria-selected="true" em tempo de execução, por isso o :global() no atributo.
   * O destaque não depende só de cor: barra lateral, nome em negrito sublinhado
   * e a etiqueta "Enter para escolher".
   */
  .ag-sugestao:global([aria-selected='true']) {
    border-left-color: var(--ibm-acento);
    background: var(--ibm-acento-suave);
  }
  .ag-sugestao:global([aria-selected='true']) .ag-sugestao-nome {
    font-weight: 700;
    text-decoration: underline;
    text-underline-offset: 3px;
  }
  .ag-sugestao:global([aria-selected='true']) .ag-sugestao-dados {
    color: var(--ibm-tinta);
  }
  .ag-sugestao:global([aria-selected='true']) .ag-sugestao-acao {
    display: inline-flex;
  }

  /* Estado da seleção: carregando, vazio e paciente selecionado */
  .ag-carregando {
    padding: 16px;
    border: 1px solid var(--ibm-linha);
    border-left: 4px solid var(--ibm-acento);
    border-radius: var(--ibm-raio-controle);
    background: var(--ibm-fundo);
  }
  .ag-vazio {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    padding: 16px;
    border: 1px dashed var(--ibm-borda-campo);
    border-radius: var(--ibm-raio-controle);
    background: var(--ibm-fundo);
  }
  .ag-vazio-icone {
    flex: none;
    width: 32px;
    height: 32px;
    color: var(--ibm-tinta-suave);
  }
  .ag-vazio-titulo {
    font-size: 14px;
    font-weight: 600;
    line-height: 1.43;
    color: var(--ibm-tinta);
  }
  .ag-vazio-texto {
    margin-top: 4px;
    font-size: 14px;
    line-height: 1.43;
    color: var(--ibm-tinta-suave);
  }
  .ag-vazio-texto strong {
    color: var(--ibm-tinta);
    font-weight: 600;
  }
  .ag-paciente {
    padding: 16px;
    border: 1px solid var(--ibm-acento);
    border-left-width: 4px;
    border-radius: var(--ibm-raio-controle);
    background: var(--ag-selecionado-fundo);
  }
  .ag-paciente-topo {
    display: flex;
    flex-wrap: wrap;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 16px;
  }
  .ag-paciente-marca {
    display: flex;
    align-items: center;
    gap: 6px;
    color: var(--ibm-acento-suave-texto);
    font-size: 12px;
    font-weight: 700;
    letter-spacing: 0.32px;
    text-transform: uppercase;
  }
  .ag-paciente-nome {
    margin-top: 4px;
    color: var(--ibm-tinta);
    font-size: 18px;
    font-weight: 700;
    line-height: 1.33;
    overflow-wrap: anywhere;
  }

  /* O que vem depois da escolha do paciente */
  .ag-proximos {
    padding: 16px;
    border: 1px dashed var(--ibm-borda-campo);
    border-radius: var(--ibm-raio-painel);
    color: var(--ibm-tinta-suave);
  }
  .ag-proximos-titulo {
    margin-bottom: 12px;
    color: var(--ibm-tinta);
    font-size: 14px;
    font-weight: 600;
    line-height: 1.43;
  }
  .ag-proximos-lista {
    display: grid;
    gap: 8px;
    font-size: 14px;
    line-height: 1.43;
  }
  .ag-proximos-lista li {
    display: flex;
    align-items: flex-start;
    gap: 12px;
  }
  .ag-proximos-lista strong {
    font-weight: 600;
  }
  @media (min-width: 768px) {
    .ag-proximos {
      padding: 24px;
    }
  }

  /* Exames: linha de opção que aceita nome longo */
  .ag-opcao-exame {
    padding-top: 8px;
    padding-bottom: 8px;
    line-height: 1.43;
  }
  .ag-opcao-exame + .ag-opcao-exame {
    border-top: 1px solid var(--ibm-linha);
  }

  /* Vagas, cota e atendimento de cada exame marcado */
  .ag-vagas {
    margin-top: 24px;
  }
  .ag-vagas-aviso {
    margin-bottom: 16px;
  }
  .ag-exame {
    padding: 16px;
    border: 1px solid var(--ibm-linha);
    border-radius: var(--ibm-raio-controle);
    background: var(--ibm-camada);
  }
  .ag-exame-titulo {
    margin-bottom: 16px;
    padding-bottom: 8px;
    border-bottom: 1px solid var(--ibm-linha);
    font-size: 14px;
    font-weight: 600;
    line-height: 1.43;
    overflow-wrap: anywhere;
  }
  .ag-exame-codigo {
    margin-left: 4px;
    color: var(--ibm-tinta-suave);
    font-size: 12px;
    font-weight: 400;
  }
  .ag-exame-bloco {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }
  .ag-exame-bloco .ibm-campo,
  .ag-exame-bloco .ibm-rotulo {
    align-self: stretch;
  }
  .ag-exame-bloco .ibm-rotulo {
    margin-bottom: 4px;
  }
  .ag-exame-grupo {
    color: var(--ibm-tinta-suave);
    font-size: 12px;
    font-weight: 600;
    letter-spacing: 0.32px;
    text-transform: uppercase;
  }
  .ag-linha,
  .ag-atendimento {
    font-size: 14px;
    line-height: 1.43;
  }
  .ag-atendimento strong {
    font-weight: 600;
  }

  /* Rodapé: paciente em texto ao lado do botão de envio */
  .ag-envio {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }
  .ag-envio-paciente {
    min-width: 0;
    padding: 12px 16px;
    border-left: 4px solid var(--ibm-acento);
    border-radius: 0 var(--ibm-raio-controle) var(--ibm-raio-controle) 0;
    background: var(--ag-selecionado-fundo);
  }
  .ag-envio-dados {
    display: flex;
    flex-wrap: wrap;
    gap: 2px 16px;
    margin-top: 4px;
    font-size: 14px;
    line-height: 1.43;
    overflow-wrap: anywhere;
  }
  .ag-envio-dados strong {
    font-weight: 600;
  }
  .ag-envio-acao {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .ag-envio-ajuda {
    margin-top: 0;
  }
  @media (min-width: 768px) {
    .ag-envio {
      flex-direction: row;
      align-items: flex-end;
      justify-content: space-between;
      gap: 24px;
    }
    .ag-envio-paciente {
      flex: 1;
    }
    .ag-envio-acao {
      flex: none;
      align-items: flex-end;
      max-width: 50%;
      text-align: right;
    }
    .ag-envio-acao .ibm-btn {
      min-width: 280px;
    }
  }

  @media (prefers-reduced-motion: no-preference) {
    .ibm-btn,
    .ibm-campo,
    .ibm-resultado,
    .ag-sugestao {
      transition:
        background-color 110ms ease-out,
        border-color 110ms ease-out;
    }
  }
</style>
