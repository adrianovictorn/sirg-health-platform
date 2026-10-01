<script lang="ts">
  import { onMount } from 'svelte';
  import { getApi, postApi } from '$lib/api';
  import { listarEspecialidadesCatalogo } from '$lib/especialidadesApi.js';
  import { formatarHora } from '$lib/cotas.js';
  import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';
  import UserMenu from '$lib/UserMenu.svelte';
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



  let solicitacaoId = $state('');
  let examesSelecionados = $state<string[]>([]);
  let dataAgendada = $state('');
  let turno = $state<'MANHA' | 'TARDE'>('MANHA');
  let localAgendamentoId = $state('');
  let observacoes = $state('');
  let valorBusca = $state('');
  let paginaAtual = $state(0);
  let comboboxAberto = $state(false);

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
    const comProfissional = cotasComProfissional(codigo);
    if (comProfissional.length === 1) return comProfissional[0];
    if (comProfissional.length > 1) {
      const escolhidaId = cotaEscolhidaPorExame[codigo.toUpperCase()];
      return comProfissional.find((c) => c.id === escolhidaId) ?? null;
    }
    return null;
  }

  const solicitacoesFiltradas = $derived(
    !valorBusca
      ? solicitacoes
      : solicitacoes.filter((s) => {
          const termo = valorBusca.toLowerCase();
          const nome = (s.nomePaciente || '').toLowerCase();
          const cpf = s.cpfPaciente || '';
          return nome.includes(termo) || cpf.includes(valorBusca);
        })
  );

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
      const profissionalId = cotaResolvida(codigo)?.profissionalId;
      if (profissionalId != null) {
        params.append('profissionalId', String(profissionalId));
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

  function cotasComProfissional(codigo: string): CotaAplicavel[] {
    return (cotasAplicaveisPorEspecialidade[codigo.toUpperCase()] ?? []).filter((c) => c.profissionalId != null);
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
    if (cotasComProfissional(codigo).length > 0) {
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


  async function carregarSolicitacoesPendentes(termo: string = '', page = 0) {
    error = '';
    paginaAtual = page;
    try {
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
      const pageJson = await response.json();
      solicitacoes = pageJson.content ?? pageJson;
    } catch (e: any) {
      error = e.message;
      console.error('Falha ao buscar pendentes', e);
      alert(e.message || 'Erro ao carregar as solicitações pendentes.');
    }
  }

  let solicitacaoDetalhe = $state<SolicitacaoDetalhe | null>(null);
  let carregandoDetalhe = $state(false);
  async function selecionarSolicitacao(solicitacao: SolicitacaoResumo) {
    const cpfLabel = solicitacao.cpfPaciente || 'CPF não informado';
    valorBusca = `${solicitacao.nomePaciente} - ${cpfLabel}`;
    solicitacaoId = String(solicitacao.id);
    comboboxAberto = false;

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

    carregandoDetalhe = true;
    try {
      const res = await getApi(`solicitacoes/buscar/${solicitacao.id}`);
      if (!res.ok) {
        const detalhe = await res.text().catch(() => '');
        throw new Error(`Erro ao carregar detalhes da solicitação (${res.status}): ${detalhe || res.statusText}`);
      }

      const detalheResposta = await res.json();
      const especialidadesNormalizadas = normalizarEspecialidadesParaAgendamento(detalheResposta?.especialidades);
      solicitacaoDetalhe = {
        ...detalheResposta,
        especialidades: especialidadesNormalizadas
      };
      examesSelecionados = [];
    } catch (e: any) {
      alert(e.message ?? 'Erro ao carregar detalhes da solicitação');
      solicitacaoDetalhe = null;
    } finally {
      carregandoDetalhe = false;
    }
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

  function enviarAgendamento(event: SubmitEvent) {
    event.preventDefault();

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
      const comProfissional = cotasComProfissional(codigo);
      if (comProfissional.length > 1) {
        const escolhida = cotaEscolhidaPorExame[chave];
        if (!escolhida) {
          alert(`Escolha o profissional para ${getEspecialidadeLabel(codigo)} — há mais de uma cota compatível.`);
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

    try {
      postApi(`agendamentos/${solicitacaoId}`, body).then(async (resposta) => {
        if (resposta.ok) {
          alert('Agendamento realizado com sucesso!');

          // Profissional real do paciente (V100): vem da resposta do POST,
          // que já resolve override do operador ou o da cota — evita depender
          // de estado local desatualizado no comprovante.
          const profissionalUnico = await (async () => {
            if (examesSelecionados.length !== 1) return null;
            try {
              const dados = await resposta.clone().json();
              const especialidade = (dados.especialidades ?? []).find(
                (e: { codigo: string }) => e.codigo?.toUpperCase() === examesSelecionados[0].toUpperCase()
              );
              if (especialidade?.profissionalExecutanteNome) {
                return especialidade.profissionalExecutanteNome as string;
              }
            } catch { /* segue para o fallback abaixo */ }
            return profissionalExibidoParaExame(examesSelecionados[0])?.nome
              ?? cotasComProfissional(examesSelecionados[0])[0]?.profissionalNome
              ?? null;
          })();

          // Horário real do paciente (V97/V99/V100): vem da resposta do POST, que já
          // inclui a hora calculada (cota dinâmica), sobrescrita ou a hora manual
          // validada — evita depender de estado local desatualizado no comprovante.
          const horarioInfo = await (async () => {
            if (examesSelecionados.length !== 1) return null;
            try {
              const dados = await resposta.clone().json();
              const especialidade = (dados.especialidades ?? []).find(
                (e: { codigo: string }) => e.codigo?.toUpperCase() === examesSelecionados[0].toUpperCase()
              );
              if (especialidade?.horaAgendada) {
                return formatarHora(especialidade.horaAgendada);
              }
            } catch { /* segue para o fallback abaixo */ }
            const cota = cotaResolvida(examesSelecionados[0]);
            if (!cota) return null;
            return cota.horarioDinamico ? null : horarioManualPorExame[examesSelecionados[0].toUpperCase()] || null;
          })();

          await gerarComprovantePDF({
            solicitacaoId: solicitacaoDetalhe.id,
            nomePaciente: solicitacaoDetalhe.nomePaciente,
            cpfPaciente: solicitacaoDetalhe.cpfPaciente,
            usfOrigem: solicitacaoDetalhe.usfOrigem,
            unidadeNome: solicitacaoDetalhe.unidadeNome,
            cns: solicitacaoDetalhe.cns,
            examesNomes: examesSelecionados.map((exame) => getEspecialidadeLabel(exame)),
            dataAgendada,
            turno,
            localLabel: getLocalLabel(localAgendamentoId),
            observacoes,
            profissionalNome: profissionalUnico,
            agendadoPorNome: get(usuarioLogado)?.nome ?? null,
            horarioInfo
          });

          solicitacaoId = '';
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
      });
    } catch (err) {
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

<div class="flex min-h-screen bg-gray-100">
  <RoleBasedMenu activePage="/agendar" />

  <div class="flex-1 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Agendar Múltiplos Exames</h1>
          <UserMenu/>
    </header>

    <main class="flex-1 overflow-auto p-6">
      {#if isLoading}
        <div class="text-center p-10">
          <p class="text-lg text-gray-600">Carregando solicitações...</p>
        </div>
      {:else if error}
        <div class="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded relative" role="alert">
          <strong class="font-bold">Ocorreu um erro:</strong>
          <span class="block sm:inline">{error}</span>
        </div>
      {:else}
        <div class="bg-white rounded-lg shadow-lg p-6">
          <h2 class="text-2xl font-bold text-emerald-800 mb-6">Novo Agendamento</h2>
        
          <form class="space-y-6" onsubmit={enviarAgendamento}>
            <div class="flex flex-col">
              <label for="combobox-agendamento" class="text-sm font-medium text-gray-700 mb-1">
                Selecionar Solicitação Pendente
              </label>
             <div class="relative">
                    <input 
                      id="combobox-agendamento"
                      type="text" 
                      bind:value={valorBusca}
                      onfocus={() => comboboxAberto = true}
                      onblur={() => setTimeout(() => { comboboxAberto = false }, 150)}
                      placeholder="Digite o nome ou CPF para buscar..."
                      class="border border-gray-300 rounded-lg p-2 w-full focus:ring-emerald-500 focus:border-emerald-500"
                    />

                    {#if comboboxAberto && solicitacoesFiltradas.length > 0}
                      <ul class="absolute z-10 w-full bg-white border border-gray-200 rounded-lg mt-1 max-h-60 overflow-y-auto shadow-lg">
                        {#each solicitacoesFiltradas as s (s.id)}
                          <li class="p-0">
                            <button
                              type="button"
                              onmousedown={() => selecionarSolicitacao(s)}
                              class="w-full text-left p-3 hover:bg-emerald-100 cursor-pointer"
                            >
                              {s.nomePaciente} - {s.cpfPaciente || 'CPF não informado'}
                            </button>
                          </li>
                        {/each}
                      </ul>
                    {/if}
</div>
            </div>

            {#if solicitacaoDetalhe}
              <div class="border-t pt-6 space-y-4">
                <h3 class="text-lg font-semibold text-gray-800">Dados do Paciente</h3>
                <div class="grid grid-cols-1 md:grid-cols-4 gap-4">
                  <div>
                    <label for="campo-nome" class="text-sm font-medium text-gray-700">Nome</label>
                    <input id="campo-nome" type="text" value={solicitacaoDetalhe.nomePaciente} readonly class="w-full bg-gray-100 border-gray-300 rounded-lg p-2" />
                  </div>
                  <div>
                    <label for="campo-cpf" class="text-sm font-medium text-gray-700">CPF</label>
                    <input id="campo-cpf" type="text" value={solicitacaoDetalhe.cpfPaciente} readonly class="w-full bg-gray-100 border-gray-300 rounded-lg p-2" />
                  </div>
                  <div>
                    <label for="campo-usf" class="text-sm font-medium text-gray-700">Unidade</label>
                    <input id="campo-usf" type="text" value={solicitacaoDetalhe.unidadeNome || solicitacaoDetalhe.usfOrigem || 'Não informado'} readonly class="w-full bg-gray-100 border-gray-300 rounded-lg p-2" />
                  </div>
                   <div>
                    <label for="campo-cns" class="text-sm font-medium text-gray-700">CNS</label>
                    <input id="campo-cns" type="text" value={solicitacaoDetalhe.cns || 'N/A'} readonly class="w-full bg-gray-100 border-gray-300 rounded-lg p-2" />
                  </div>
                </div>

                <fieldset class="flex flex-col mt-4">
                  <legend class="text-sm font-medium text-gray-700 mb-2">Exames Pendentes:</legend>
                  {#if solicitacaoDetalhe.especialidades.length > 0}
                    <div class="space-y-2 border border-gray-200 rounded-lg p-3 max-h-60 overflow-y-auto">
                      {#each solicitacaoDetalhe.especialidades as especialidade (especialidade.codigo)}
                        <label class="flex items-center space-x-2 p-2 rounded hover:bg-gray-50 cursor-pointer">
                          <input
                            type="checkbox"
                            value={especialidade.codigo}
                            bind:group={examesSelecionados}
                            class="form-checkbox h-4 w-4 text-emerald-600 rounded focus:ring-emerald-500"
                          />
                          <span class="text-gray-700">{especialidade.nome || getEspecialidadeLabel(especialidade.codigo)}</span>
                        </label>
                      {/each}
                    </div>
                  {:else}
                    <p class="text-sm text-gray-500 italic mt-2">Nenhum exame pendente para esta solicitação.</p>
                  {/if}
                </fieldset>

                {#if examesSelecionados.length > 0}
                  <div class="bg-emerald-50 border border-emerald-200 rounded-lg p-4 mt-4">
                    <h4 class="font-semibold text-emerald-700 mb-3">Contagem de vagas por especialidade selecionada</h4>
                    <div class="space-y-4">
                      {#each examesSelecionados as codigo}
                        <div class="bg-white border border-gray-200 rounded-lg p-4 space-y-3">
                          <p class="text-sm font-semibold text-gray-800">
                            {getEspecialidadeLabel(codigo)}
                            <span class="ml-1 text-xs font-normal text-gray-500">({codigo})</span>
                          </p>

                          <div class="space-y-1">
                            <p class="text-xs text-gray-600">Vagas definidas: {getEspecialidadeVagas(codigo) === 0 ? 'Sem limite (0)' : getEspecialidadeVagas(codigo)}</p>
                            {#if contagemPorEspecialidade[codigo.toUpperCase()]}
                              <p class="text-xs text-gray-600">Agendados hoje: {contagemPorEspecialidade[codigo.toUpperCase()].agendados}</p>
                              {#if getEspecialidadeVagas(codigo) === 0}
                                <span class="inline-flex items-center rounded-full bg-indigo-50 text-indigo-700 px-2 py-0.5 text-xs font-medium">Sem limite de vagas</span>
                              {:else}
                                <p class="text-xs text-gray-600">Restante: {contagemPorEspecialidade[codigo.toUpperCase()].restante}</p>
                                {#if contagemPorEspecialidade[codigo.toUpperCase()].restante <= 0}
                                  <span class="inline-flex items-center rounded-full bg-red-50 text-red-700 px-2 py-0.5 text-xs font-medium">Limite atingido — não é possível agendar mais para esta data</span>
                                {/if}
                              {/if}
                            {:else}
                              <p class="text-xs text-gray-500">Carregando contagem...</p>
                            {/if}
                          </div>

                          {#if saldoCotaPorEspecialidade[codigo.toUpperCase()] || saldoCotaPorDataEspecialidade[codigo.toUpperCase()]}
                            <div class="space-y-1 pt-3 border-t border-gray-100">
                              <p class="text-xs font-semibold text-gray-500">Cota</p>
                              {#if saldoCotaPorEspecialidade[codigo.toUpperCase()]}
                                {@const cota = saldoCotaPorEspecialidade[codigo.toUpperCase()]}
                                {#if cota.quantidadeTotal == null}
                                  <p class="text-xs text-gray-500">Sem cota mensal configurada.</p>
                                {:else}
                                  <p class="text-xs text-gray-600">
                                    Cota do mês: {cota.quantidadeUtilizada}/{cota.quantidadeTotal} — saldo {cota.saldoDisponivel}
                                  </p>
                                  {#if cota.saldoDisponivel !== null && cota.saldoDisponivel <= 0}
                                    <span class="inline-flex items-center rounded-full bg-red-50 text-red-700 px-2 py-0.5 text-xs font-medium">Cota do mês esgotada</span>
                                  {/if}
                                {/if}
                              {/if}
                              {#if saldoCotaPorDataEspecialidade[codigo.toUpperCase()]}
                                {@const cotaData = saldoCotaPorDataEspecialidade[codigo.toUpperCase()]}
                                {#if cotaData.quantidadeTotal != null}
                                  <p class="text-xs text-gray-600">
                                    Cota da data: {cotaData.quantidadeUtilizada}/{cotaData.quantidadeTotal} — saldo {cotaData.saldoDisponivel}
                                  </p>
                                  {#if cotaData.saldoDisponivel !== null && cotaData.saldoDisponivel <= 0}
                                    <span class="inline-flex items-center rounded-full bg-red-50 text-red-700 px-2 py-0.5 text-xs font-medium">Cota da data esgotada</span>
                                  {/if}
                                {/if}
                              {/if}
                            </div>
                          {/if}

                          {#if cotasComProfissional(codigo).length === 1}
                            {@const cota = cotasComProfissional(codigo)[0]}
                            <div class="space-y-1 pt-3 border-t border-gray-100">
                              <p class="text-xs font-semibold text-gray-500">Atendimento</p>
                              <div class="text-xs text-indigo-700 space-y-0.5">
                                <p><strong>Profissional:</strong> {cota.profissionalNome ?? 'não informado'}</p>
                                {#if cota.localAgendamentoNome}<p><strong>Local:</strong> {cota.localAgendamentoNome}</p>{/if}
                                {#if cota.horarioDinamico}
                                  <p><strong>Horário:</strong> calculado automaticamente ao agendar (período {formatarHora(cota.horaInicial)} às {formatarHora(cota.horaFinal)})</p>
                                {:else if cota.horaInicial}
                                  <p><strong>Período de referência:</strong> {formatarHora(cota.horaInicial)} às {formatarHora(cota.horaFinal)}</p>
                                {/if}
                              </div>
                            </div>
                          {:else if cotasComProfissional(codigo).length > 1}
                            <div class="space-y-1 pt-3 border-t border-gray-100">
                              <p class="text-xs font-semibold text-gray-500">Atendimento</p>
                              <label class="text-xs font-medium text-indigo-700">
                                Mais de um profissional disponível — escolha um:
                              </label>
                              <select bind:value={cotaEscolhidaPorExame[codigo.toUpperCase()]}
                                class="w-full mt-1 border border-gray-300 rounded-lg p-2 text-xs">
                                <option value={null}>Selecionar profissional...</option>
                                {#each cotasComProfissional(codigo) as cota (cota.id)}
                                  <option value={cota.id}>
                                    {cota.profissionalNome}
                                    {#if cota.horaInicial}{' '}({formatarHora(cota.horaInicial)} às {formatarHora(cota.horaFinal)}){/if}
                                  </option>
                                {/each}
                              </select>
                            </div>
                          {/if}
                          <div class="space-y-1 pt-3 border-t border-gray-100">
                            <label class="text-xs font-medium text-gray-600">
                              Hora do atendimento (opcional{cotaResolvida(codigo)?.horaInicial
                                ? ` — período de referência: ${formatarHora(cotaResolvida(codigo)?.horaInicial ?? null)} às ${formatarHora(cotaResolvida(codigo)?.horaFinal ?? null)}`
                                : ''})
                            </label>
                            <input type="time" bind:value={horarioManualPorExame[codigo.toUpperCase()]}
                              min={cotaResolvida(codigo)?.horaInicial ? formatarHora(cotaResolvida(codigo)?.horaInicial ?? null) : undefined}
                              max={cotaResolvida(codigo)?.horaFinal ? formatarHora(cotaResolvida(codigo)?.horaFinal ?? null) : undefined}
                              class="w-full mt-1 border rounded-lg p-2 text-xs {horaManualForaDoPeriodo(codigo) ? 'border-red-400' : 'border-gray-300'}" />
                            {#if cotaResolvida(codigo)?.horarioDinamico}
                              <p class="text-xs text-gray-500 mt-1">
                                Calculado automaticamente ao confirmar — deixe em branco para manter o cálculo, ou informe um horário para substituí-lo.
                              </p>
                            {/if}
                            {#if horaManualForaDoPeriodo(codigo)}
                              <p class="text-xs text-red-600 mt-1">
                                Hora fora do período liberado pela cota ({formatarHora(cotaResolvida(codigo)?.horaInicial ?? null)} às {formatarHora(cotaResolvida(codigo)?.horaFinal ?? null)}).
                              </p>
                            {/if}
                          </div>

                          <div class="space-y-1 pt-3 border-t border-gray-100">
                            <label class="text-xs font-medium text-gray-600">
                              Profissional que atende (opcional{cotaResolvida(codigo)
                                ? ' — substitui o definido pela cota'
                                : ''})
                            </label>
                            {#if profissionalExibidoParaExame(codigo)}
                              <div class="flex items-center justify-between bg-indigo-50 border border-indigo-200 rounded-lg p-2 mt-1 text-xs text-indigo-800">
                                <span>{profissionalExibidoParaExame(codigo)?.nome}</span>
                                <button type="button" onclick={() => removerProfissionalParaExame(codigo)}
                                  class="text-indigo-700 hover:text-indigo-900">Remover</button>
                              </div>
                            {:else}
                              <input type="text" placeholder="Buscar profissional por nome..."
                                bind:value={profissionalTermoPorExame[codigo]}
                                oninput={() => buscarProfissionalParaExame(codigo)}
                                class="w-full mt-1 border border-gray-300 rounded-lg p-2 text-xs" />
                              {#if profissionalBuscandoPorExame[codigo]}
                                <p class="text-xs text-gray-400 mt-1">Buscando...</p>
                              {:else if (profissionalResultadosPorExame[codigo] ?? []).length > 0}
                                <ul class="mt-1 border border-gray-200 rounded-lg divide-y divide-gray-100 max-h-32 overflow-y-auto">
                                  {#each profissionalResultadosPorExame[codigo] as p (p.id)}
                                    <li>
                                      <button type="button" onclick={() => selecionarProfissionalParaExame(codigo, p)}
                                        class="w-full text-left px-2 py-1 text-xs text-gray-700 hover:bg-gray-50">
                                        {p.nome}
                                      </button>
                                    </li>
                                  {/each}
                                </ul>
                              {/if}
                            {/if}
                          </div>
                        </div>
                      {/each}
                    </div>
                  </div>
                {/if}

                {#if solicitacaoDetalhe.especialidades.length > 0}
                  <div class="grid grid-cols-1 md:grid-cols-2 gap-6 mt-4">
                    <div>
                      <label for="dataAgendada" class="text-sm font-medium text-gray-700 mb-1">Data do Agendamento</label>
                      <input type="date" id="dataAgendada" bind:value={dataAgendada} class="w-full border border-gray-300 rounded-lg p-2" required />
                    </div>
                    <div>
                      <label for="turno" class="text-sm font-medium text-gray-700 mb-1">Turno</label>
                      <select id="turno" bind:value={turno} class="w-full border border-gray-300 rounded-lg p-2">
                        <option value="MANHA">Manhã</option>
                        <option value="TARDE">Tarde</option>
                      </select>
                    </div>
                  </div>
                  <div class="flex flex-col mt-4">
                    <label for="localAgendamentoId" class="text-sm font-medium text-gray-700 mb-1">Local do Agendamento</label>
                    <select id="localAgendamentoId" bind:value={localAgendamentoId} disabled={localTravadoPorCota}
                      class="w-full border border-gray-300 rounded-lg p-2 disabled:bg-gray-100" required>
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
                      <p class="text-xs text-indigo-600 mt-1">Local definido pela cota liberada — não pode ser alterado aqui.</p>
                    {/if}
                  </div>

                  <div class="flex flex-col mt-4">
                    <label for="orientacoes" class="text-sm font-medium text-gray-700 mb-1">Preparo</label>
                    <select name="" id="" class="w-full border border-gray-300 rounded-lg p-2" bind:value={preparoSelecionado} onchange={aplicarPreparo}>
                      <option value="">Selecione...</option>
                      <option value="USG_ABD_TOTAL">Preparo de USG Abdomen Total</option>
                      <option value="USG_PARTES_MOLES_TIREOIDE">Preparo de USG PARTES MOLES E USG TIREOIDE </option>
                      <option value="USG_RINS_PROSTATA">Preparo de USG DE US DE RINS E VIAS URINÁRIAS,
USG DE PRÓSTATA</option>
                        <option value="LABORATORIO">Preparo de Laboratório</option>
                    </select>
                  </div>
                  <div class="flex flex-col mt-4">
                    <label for="observacoes" class="text-sm font-medium text-gray-700 mb-1">Observações</label>
                    <textarea id="observacoes" bind:value={observacoes} rows="3" class="w-full border border-gray-300 rounded-lg p-2"></textarea>
                  </div>

                  <button
                    type="submit"
                    class="w-full bg-emerald-800 text-white py-3 rounded-lg hover:bg-emerald-900 transition mt-6 disabled:bg-gray-400"
                    disabled={examesSelecionados.length === 0}
                  >
                    Agendar Exames Selecionados
                  </button>
                {/if}
              </div>
            {/if}
          </form>
        </div>
      {/if}
    </main>
  </div>
</div>

<style>
  .form-checkbox {
    appearance: none;
    background-color: #fff;
    border: 1px solid #ccc;
    border-radius: 0.25rem;
    display: inline-block;
    height: 1.1em;
    position: relative;
    vertical-align: middle;
    width: 1.1em;
    cursor: pointer;
    flex-shrink: 0;
  }
  .form-checkbox:checked {
    background-color: #10b981;
    border-color: #059669;
  }
  .form-checkbox:checked::before {
    content: '✓';
    color: white;
    font-size: 0.8em;
    font-weight: bold;
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
    line-height: 1;
  }
</style>
