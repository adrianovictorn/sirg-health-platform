<script lang="ts">
  import { base } from '$app/paths';
  import { env } from '$env/dynamic/public';
  import { getApi, putApi, deleteByIdApi, postApi } from '$lib/api.js';
  import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';
  import UserMenu from '$lib/UserMenu.svelte';
  import { onMount } from 'svelte';

  type LocalDateValue = string | number | [number, number, number];
  type LocalTimeValue = string | [number, number, number];

  interface TransporteResumo {
    id: number;
    nome: string;
    vagas: number;
  }

  interface LocalAgendamentoResumo {
    id: number;
    nomeLocal: string;
    endereco: string;
    numero: string;
  }

  interface CidadeResumo {
    id: number;
    nome: string;
  }

  interface MotoristaResumo {
    id: number;
    nome: string;
    telefone?: string;
  }

  interface SolicitacaoResumo {
    id: number;
    nomePaciente: string;
    cpf?: string;
    telefone?: string;
    usfOrigem?: string;
    turno?: string | null;
    retornaMesmoDia?: boolean | null;
    localAgendamento?: LocalAgendamentoResumo | LocalAgendamentoResumo[] | null;
    locaisAgendamento?: LocalAgendamentoResumo[] | null;
    local?: LocalAgendamentoResumo[] | null;
  }

  interface AgendamentoTransporte {
    id: number;
    localAgendamento: LocalAgendamentoResumo[];
    cidade: CidadeResumo;
    transporte: TransporteResumo;
    motorista?: MotoristaResumo | null;
    status: string;
    pacientes: SolicitacaoResumo[];
    data: LocalDateValue;
    horaSaida?: LocalTimeValue | null;
  }

  const STATUS_CONCLUIDO = 'CONCLUIDO';

  let transportes: TransporteResumo[] = [];
  let motoristas: MotoristaResumo[] = [];
  let locaisDisponiveis: LocalAgendamentoResumo[] = [];
  let solicitacoesDisponiveis: SolicitacaoResumo[] = [];
  let agendamentos: AgendamentoTransporte[] = [];
  let relatorioResultados: AgendamentoTransporte[] = [];
  let agendamentosAtivos: AgendamentoTransporte[] = [];
  let agendamentosHistorico: AgendamentoTransporte[] = [];
  let agendamentosVisiveis: AgendamentoTransporte[] = [];

  let filtroData = '';
  let filtroTransporte = '';
  let filtroMotorista = '';

  let relatorioData = '';
  let relatorioTransporte = '';
  let relatorioMotorista = '';

  let abaAtiva: 'lista' | 'relatorio' | 'historico' = 'lista';
  let carregandoLista = false;
  let carregandoRelatorio = false;
  let detalhesAbertos = new Set<number>();
  let exibindoHistorico = false;

  let modalAberto = false;
  let agendamentoSelecionado: AgendamentoTransporte | null = null;
  let pacientesEmEdicao: string[] = [];
  let termosFiltroPaciente: string[] = [];
  let turnosEmEdicao: string[] = [];
  let retornosEmEdicao: boolean[] = [];
  let locaisEmEdicao: string[] = [];
  let sugestoesAbertas: Set<number> = new Set();
  let motoristaModalId = '';
  let horaSaidaModal = '';
  let salvandoEdicao = false;
  let mensagemErroModal = '';
  let mensagemSucessoModal = '';

  let relatorioErro = '';
  let relatorioSucesso = '';
  let brasaoBase64 = '';

  function formatarData(valor: LocalDateValue): string {
    if (Array.isArray(valor) && valor.length === 3) {
      const [ano, mes, dia] = valor;
      return new Date(ano, mes - 1, dia).toLocaleDateString('pt-BR');
    }
    if (typeof valor === 'number') {
      return new Date(valor).toLocaleDateString('pt-BR');
    }
    if (typeof valor === 'string' && valor) {
      const partes = valor.split('-').map((parte) => Number.parseInt(parte, 10));
      if (partes.length === 3 && partes.every((parte) => !Number.isNaN(parte))) {
        const [ano, mes, dia] = partes;
        return new Date(ano, mes - 1, dia).toLocaleDateString('pt-BR');
      }
      const data = new Date(valor);
      return Number.isNaN(data.getTime()) ? valor : data.toLocaleDateString('pt-BR');
    }
    return '';
  }

  function formatarHora(valor?: LocalTimeValue | null): string {
    if (!valor) return '';
    if (Array.isArray(valor) && valor.length >= 2) {
      const [hora, minuto] = valor;
      return `${hora.toString().padStart(2, '0')}:${minuto.toString().padStart(2, '0')}`;
    }
    if (typeof valor === 'string' && valor) {
      return valor.substring(0, 5);
    }
    return '';
  }

  function normalizarDataParaEnvio(valor: LocalDateValue): string | null {
    if (Array.isArray(valor) && valor.length === 3) {
      const [ano, mes, dia] = valor;
      return `${ano.toString().padStart(4, '0')}-${mes.toString().padStart(2, '0')}-${dia.toString().padStart(2, '0')}`;
    }
    if (typeof valor === 'string' && valor) {
      return valor.length === 10 ? valor : valor.slice(0, 10);
    }
    if (typeof valor === 'number') {
      const data = new Date(valor);
      const ano = data.getFullYear();
      const mes = `${data.getMonth() + 1}`.padStart(2, '0');
      const dia = `${data.getDate()}`.padStart(2, '0');
      return `${ano}-${mes}-${dia}`;
    }
    return null;
  }

  function normalizarHoraParaEnvio(valor?: LocalTimeValue | null): string | null {
    if (!valor) return null;
    if (Array.isArray(valor) && valor.length >= 2) {
      const [hora, minuto] = valor;
      return `${hora.toString().padStart(2, '0')}:${minuto.toString().padStart(2, '0')}:00`;
    }
    if (typeof valor === 'string' && valor) {
      return valor.length === 5 ? `${valor}:00` : valor;
    }
    return null;
  }

  function converterParaData(valor: LocalDateValue): Date | null {
    if (Array.isArray(valor) && valor.length === 3) {
      const [ano, mes, dia] = valor;
      const data = new Date(ano, mes - 1, dia);
      data.setHours(0, 0, 0, 0);
      return data;
    }
    if (typeof valor === 'number') {
      const data = new Date(valor);
      if (Number.isNaN(data.getTime())) return null;
      data.setHours(0, 0, 0, 0);
      return data;
    }
    if (typeof valor === 'string' && valor) {
      const partes = valor.split('-').map((parte) => Number.parseInt(parte, 10));
      if (partes.length === 3 && partes.every((parte) => !Number.isNaN(parte))) {
        const [ano, mes, dia] = partes;
        const dataFormatada = new Date(ano, mes - 1, dia);
        dataFormatada.setHours(0, 0, 0, 0);
        return dataFormatada;
      }
      const data = new Date(valor);
      if (!Number.isNaN(data.getTime())) {
        data.setHours(0, 0, 0, 0);
        return data;
      }
    }
    return null;
  }

  function obterLocaisDoRegistro(registro: AgendamentoTransporte): string {
    if (!registro?.localAgendamento?.length) {
      return '-';
    }
    return registro.localAgendamento
      .map((local) => local?.nomeLocal)
      .filter(Boolean)
      .join(', ');
  }

  function extrairLocalDoPaciente(paciente?: SolicitacaoResumo | null): LocalAgendamentoResumo | null {
    if (!paciente) {
      return null;
    }

    const candidatoDireto = (paciente as unknown as { localAgendamento?: LocalAgendamentoResumo | LocalAgendamentoResumo[] | null }).localAgendamento;
    if (Array.isArray(candidatoDireto) && candidatoDireto.length > 0) {
      return candidatoDireto[0] ?? null;
    }
    if (candidatoDireto && !Array.isArray(candidatoDireto)) {
      return candidatoDireto as LocalAgendamentoResumo;
    }

    const listaAlternativa = (paciente as unknown as { locaisAgendamento?: LocalAgendamentoResumo[] | null }).locaisAgendamento;
    if (Array.isArray(listaAlternativa) && listaAlternativa.length > 0) {
      return listaAlternativa[0] ?? null;
    }

    const localLegacy = (paciente as unknown as { local?: LocalAgendamentoResumo[] | null }).local;
    if (Array.isArray(localLegacy) && localLegacy.length > 0) {
      return localLegacy[0] ?? null;
    }

    return null;
  }

  function obterLocalDoPaciente(registro: AgendamentoTransporte, paciente?: SolicitacaoResumo): string {
    const local = extrairLocalDoPaciente(paciente ?? null);
    if (local?.nomeLocal) {
      return local.nomeLocal;
    }

    return obterLocaisDoRegistro(registro);
  }

  async function carregarBrasao() {
    try {
      const resposta = await fetch(`${base}/images/${env.PUBLIC_MUNICIPIO_BRASAO ?? 'brasao.png'}`);
      if (!resposta.ok) {
        console.warn('Não foi possível carregar o brasão.');
        return;
      }

      const blob = await resposta.blob();
      brasaoBase64 = await new Promise<string>((resolve, reject) => {
        const leitor = new FileReader();
        leitor.onloadend = () => {
          const resultado = leitor.result;
          if (typeof resultado === 'string') {
            const base64 = resultado.split(',')[1] ?? '';
            resolve(base64);
          } else {
            reject(new Error('Falha ao converter o brasão.'));
          }
        };
        leitor.onerror = () => reject(new Error('Falha ao ler o brasão.'));
        leitor.readAsDataURL(blob);
      });
    } catch (error) {
      console.error('Erro ao carregar o brasão.', error);
    }
  }

  function statusEhHistorico(status?: string | null): boolean {
    if (!status) return false;
    return status.toUpperCase() === STATUS_CONCLUIDO;
  }

  function ehAgendamentoHistorico(agendamento: AgendamentoTransporte, referencia: Date): boolean {
    const statusNormalizado = agendamento.status?.toUpperCase() ?? '';
    if (statusNormalizado === STATUS_CONCLUIDO) {
      return true;
    }

    const dataAgendamento = converterParaData(agendamento.data);
    if (!dataAgendamento) {
      return false;
    }

    return dataAgendamento.getTime() < referencia.getTime();
  }

  $: {
    const hoje = new Date();
    hoje.setHours(0, 0, 0, 0);
    const ativos: AgendamentoTransporte[] = [];
    const historicos: AgendamentoTransporte[] = [];
    for (const item of agendamentos) {
      if (ehAgendamentoHistorico(item, hoje)) {
        historicos.push(item);
      } else {
        ativos.push(item);
      }
    }
    agendamentosAtivos = ativos;
    agendamentosHistorico = historicos;
  }

  $: exibindoHistorico = abaAtiva === 'historico';

  $: agendamentosVisiveis = exibindoHistorico ? agendamentosHistorico : agendamentosAtivos;
  function cpfFormatado(dados: SolicitacaoResumo): string {
    return dados.cpf ?? '';
  }

  function formatarPacienteDisplay(dados: SolicitacaoResumo): string {
    const cpf = cpfFormatado(dados);
    const cpfTexto = cpf && cpf.trim().length > 0 ? cpf : 'N/A';
    return `${dados.nomePaciente} - CPF ${cpfTexto}`;
  }

  function abrirSugestoes(index: number) {
    const atualizado = new Set(sugestoesAbertas);
    atualizado.add(index);
    sugestoesAbertas = atualizado;
  }

  function fecharSugestoes(index: number) {
    const atualizado = new Set(sugestoesAbertas);
    atualizado.delete(index);
    sugestoesAbertas = atualizado;
  }

  function agendarFecharSugestoes(index: number) {
    setTimeout(() => fecharSugestoes(index), 120);
  }

  function atualizarBuscaPaciente(index: number, valor: string) {
    const termosAtualizados = [...termosFiltroPaciente];
    termosAtualizados[index] = valor;
    termosFiltroPaciente = termosAtualizados;

    const idsAtualizados = [...pacientesEmEdicao];
    idsAtualizados[index] = '';
    pacientesEmEdicao = idsAtualizados;

    abrirSugestoes(index);
  }

  function selecionarPaciente(index: number, paciente: SolicitacaoResumo) {
    if (pacienteJaSelecionado(paciente.id, index)) {
      return;
    }

    const idsAtualizados = [...pacientesEmEdicao];
    idsAtualizados[index] = String(paciente.id);
    pacientesEmEdicao = idsAtualizados;

    const termosAtualizados = [...termosFiltroPaciente];
    termosAtualizados[index] = formatarPacienteDisplay(paciente);
    termosFiltroPaciente = termosAtualizados;

    const turnosAtualizados = [...turnosEmEdicao];
    turnosAtualizados[index] = paciente.turno ?? 'MANHA';
    turnosEmEdicao = turnosAtualizados;

    const retornosAtualizados = [...retornosEmEdicao];
    retornosAtualizados[index] = Boolean(paciente.retornaMesmoDia);
    retornosEmEdicao = retornosAtualizados;

    const locaisAtualizados = [...locaisEmEdicao];
    const localSelecionado = extrairLocalDoPaciente(paciente);
    locaisAtualizados[index] = localSelecionado?.id ? String(localSelecionado.id) : '';
    locaisEmEdicao = locaisAtualizados;

    fecharSugestoes(index);
  }

  function solicitacoesFiltradas(indice: number): SolicitacaoResumo[] {
    const termo = termosFiltroPaciente[indice]?.trim().toLowerCase() ?? '';
    const termoNumerico = termo.replace(/\D/g, '');

    return solicitacoesDisponiveis.filter((sol) => {
      if (pacienteJaSelecionado(sol.id, indice)) {
        return false;
      }

      if (!termo) {
        return true;
      }

      const nome = sol.nomePaciente.toLowerCase();
      const cpf = cpfFormatado(sol).replace(/\D/g, '');

      return nome.includes(termo) || (termoNumerico && cpf.includes(termoNumerico));
    });
  }

  function turnoFormatado(turno?: string | null): string {
    if (!turno) return 'Nao informado';
    switch (turno) {
      case 'MANHA':
        return 'Manha';
      case 'TARDE':
        return 'Tarde';
      case 'NOITE':
        return 'Noite';
      default:
        return 'Nao informado';
    }
  }

  function retornaMesmoDiaFormatado(valor?: boolean | null): string {
    if (valor === true) return 'Sim';
    if (valor === false) return 'Nao';
    return 'Nao informado';
  }

  function resumoRetornoAgendamento(agendamento: AgendamentoTransporte): string {
    if (!agendamento.pacientes || agendamento.pacientes.length === 0) {
      return 'Nao informado';
    }

    const valores = agendamento.pacientes.map((paciente) => paciente.retornaMesmoDia);
    const temTrue = valores.some((valor) => valor === true);
    const temFalse = valores.some((valor) => valor === false);
    const totalInformado = valores.filter((valor) => valor !== null && valor !== undefined).length;

    if (totalInformado === 0) {
      return 'Nao informado';
    }

    if (temTrue && !temFalse && totalInformado === agendamento.pacientes.length) {
      return 'Sim';
    }

    if (temFalse && !temTrue && totalInformado === agendamento.pacientes.length) {
      return 'Nao';
    }

    return 'Parcial';
  }

  async function carregarAuxiliares() {
    try {
      const [transportesRes, motoristasRes, solicitacoesRes, locaisRes] = await Promise.all([
        getApi('transporte'),
        getApi('motoristas'),
        getApi('solicitacoes'),
        getApi('local/agendamento')
      ]);

      if (transportesRes.ok) {
        const lista = await transportesRes.json();
        transportes = lista.map((item: any) => ({
          id: item.id,
          nome: item.nomeVeiculo ?? item.nome,
          vagas: item.vagas ?? 0
        }));
      }

      if (motoristasRes.ok) {
        motoristas = await motoristasRes.json();
      }

      if (solicitacoesRes.ok) {
        const lista = await solicitacoesRes.json();
        solicitacoesDisponiveis = lista.map((item: any) => ({
          id: item.id,
          nomePaciente: item.nomePaciente,
          cpf: item.cpf ?? item.cpfPaciente,
          telefone: item.telefone,
          usfOrigem: item.usfOrigem
        }));
      }

      if (locaisRes.ok) {
        locaisDisponiveis = await locaisRes.json();
      }
    } catch (error) {
      console.error('Erro ao carregar dados auxiliares', error);
    }
  }

  function construirQuery(params: Record<string, string | undefined>) {
    const query = Object.entries(params)
      .filter(([, value]) => value)
      .map(([key, value]) => `${key}=${encodeURIComponent(value!)}`)
      .join('&');
    return query ? `?${query}` : '';
  }

  async function carregarAgendamentos() {
    carregandoLista = true;
    try {
      const query = construirQuery({
        data: filtroData || undefined,
        transporteId: filtroTransporte || undefined,
        motoristaId: filtroMotorista || undefined
      });
      const endpoint = `agendamento/transporte${query}`;
      const res = await getApi(endpoint);
      if (!res.ok) throw new Error('Erro ao buscar agendamentos');
      agendamentos = await res.json();
      detalhesAbertos = new Set();
    } catch (error) {
      console.error(error);
      alert('Não foi possível carregar os agendamentos.');
    } finally {
      carregandoLista = false;
    }
  }

  async function carregarRelatorio(mostrarFeedback = true) {
    relatorioErro = '';
    relatorioSucesso = '';

    if (!relatorioData) {
      relatorioErro = 'Informe a data para gerar o relatório.';
      relatorioResultados = [];
      return;
    }

    carregandoRelatorio = true;
    try {
      const query = construirQuery({
        data: relatorioData,
        transporteId: relatorioTransporte || undefined,
        motoristaId: relatorioMotorista || undefined
      });
      const res = await getApi(`agendamento/transporte${query}`);
      if (!res.ok) throw new Error('Erro ao gerar relatório');
      relatorioResultados = await res.json();
      if (mostrarFeedback) {
        relatorioSucesso = relatorioResultados.length
          ? 'Relatório gerado. Use os botões abaixo para exportar ou imprimir.'
          : 'Nenhum agendamento encontrado para os filtros informados.';
      }
    } catch (error) {
      console.error(error);
      relatorioErro = 'Falha ao gerar o relatório.';
    } finally {
      carregandoRelatorio = false;
    }
  }

  function alternarAba(aba: 'lista' | 'relatorio' | 'historico') {
    abaAtiva = aba;
    if (aba === 'relatorio' && relatorioResultados.length === 0 && relatorioData) {
      carregarRelatorio(false);
    }
    if (aba !== 'relatorio') {
      carregarAgendamentos();
    }
  }

  function alternarDetalhes(id: number) {
    const novaSelecao = new Set(detalhesAbertos);
    if (novaSelecao.has(id)) {
      novaSelecao.delete(id);
    } else {
      novaSelecao.add(id);
    }
    detalhesAbertos = novaSelecao;
  }

  function limparFiltrosLista() {
    filtroData = '';
    filtroTransporte = '';
    filtroMotorista = '';
    carregarAgendamentos();
  }
  function limparFiltrosRelatorio() {
    relatorioData = '';
    relatorioTransporte = '';
    relatorioMotorista = '';
    relatorioResultados = [];
    relatorioErro = '';
    relatorioSucesso = '';
  }

  function abrirEdicao(agendamento: AgendamentoTransporte) {
    agendamentoSelecionado = agendamento;
    pacientesEmEdicao = agendamento.pacientes.map((paciente) => String(paciente.id));
    termosFiltroPaciente = agendamento.pacientes.map((paciente) => formatarPacienteDisplay(paciente));
    turnosEmEdicao = agendamento.pacientes.map((paciente) => paciente.turno ?? 'NAO_INFORMADO');
    retornosEmEdicao = agendamento.pacientes.map((paciente) => Boolean(paciente.retornaMesmoDia));
    locaisEmEdicao = agendamento.pacientes.map((paciente) => {
      const local = extrairLocalDoPaciente(paciente);
      return local?.id ? String(local.id) : '';
    });
    motoristaModalId = agendamento.motorista?.id ? String(agendamento.motorista.id) : '';
    horaSaidaModal = formatarHora(agendamento.horaSaida) || '';
    mensagemErroModal = '';
    mensagemSucessoModal = '';
    sugestoesAbertas = new Set();
    modalAberto = true;
  }

  function adicionarPacienteCampo() {
    pacientesEmEdicao = [...pacientesEmEdicao, ''];
    termosFiltroPaciente = [...termosFiltroPaciente, ''];
    turnosEmEdicao = [...turnosEmEdicao, 'NAO_INFORMADO'];
    retornosEmEdicao = [...retornosEmEdicao, false];
    locaisEmEdicao = [...locaisEmEdicao, ''];
    sugestoesAbertas = new Set();
  }

  function removerPacienteCampo(index: number) {
    pacientesEmEdicao = pacientesEmEdicao.filter((_, i) => i !== index);
    termosFiltroPaciente = termosFiltroPaciente.filter((_, i) => i !== index);
    turnosEmEdicao = turnosEmEdicao.filter((_, i) => i !== index);
    retornosEmEdicao = retornosEmEdicao.filter((_, i) => i !== index);
    locaisEmEdicao = locaisEmEdicao.filter((_, i) => i !== index);
    sugestoesAbertas = new Set();
  }

  function pacienteJaSelecionado(id: number, indiceAtual: number): boolean {
    return pacientesEmEdicao.some((valor, idx) => idx !== indiceAtual && valor === String(id));
  }

  async function salvarEdicao() {
    if (!agendamentoSelecionado) return;

    const entradas = pacientesEmEdicao
      .map((valor, idx) => ({
        indice: idx,
        idTexto: valor.trim(),
        turno: turnosEmEdicao[idx] ?? 'NAO_INFORMADO',
        retorna: Boolean(retornosEmEdicao[idx])
      }))
      .filter((entrada) => entrada.idTexto.length > 0);

    if (entradas.length === 0) {
      mensagemErroModal = 'Selecione pelo menos um paciente.';
      mensagemSucessoModal = '';
      return;
    }

    const idsNumericos = entradas.map((entrada) => Number(entrada.idTexto));
    if (idsNumericos.some((valor) => Number.isNaN(valor))) {
      mensagemErroModal = 'Alguma seleção de paciente é inválida.';
      mensagemSucessoModal = '';
      return;
    }

    const idsUnicos = new Set(idsNumericos);
    if (idsUnicos.size !== idsNumericos.length) {
      mensagemErroModal = 'Existem pacientes duplicados na lista.';
      mensagemSucessoModal = '';
      return;
    }

    const locaisSelecionados = entradas.map((entrada) => (locaisEmEdicao[entrada.indice] ?? '').trim());
    if (locaisSelecionados.some((valor) => valor.length === 0)) {
      mensagemErroModal = 'Informe o local de atendimento para todos os pacientes.';
      mensagemSucessoModal = '';
      return;
    }

    const locaisNumericos = locaisSelecionados.map((valor) => Number(valor));
    if (locaisNumericos.some((valor) => Number.isNaN(valor))) {
      mensagemErroModal = 'Alguma seleção de local é inválida.';
      mensagemSucessoModal = '';
      return;
    }

    const dataEnvio = normalizarDataParaEnvio(agendamentoSelecionado.data);
    if (!dataEnvio) {
      mensagemErroModal = 'Data do agendamento inválida.';
      mensagemSucessoModal = '';
      return;
    }

    salvandoEdicao = true;
    mensagemErroModal = '';
    mensagemSucessoModal = '';

    const motoristaId = motoristaModalId ? Number(motoristaModalId) : null;
    const horaSaidaNormalizada = horaSaidaModal ? normalizarHoraParaEnvio(horaSaidaModal) : null;

    const pacientesPayload = entradas.map((entrada, posicao) => ({
      solicitacaoId: Number(entrada.idTexto),
      localAgendamentoId: locaisNumericos[posicao],
      turno: entrada.turno as string,
      retornaMesmoDia: entrada.retorna
    }));

    const payload: Record<string, unknown> = {
      pacientes: pacientesPayload,
      localId: Array.from(new Set(locaisNumericos)),
      cidadeId: agendamentoSelecionado.cidade.id,
      transporteId: agendamentoSelecionado.transporte.id,
      data: dataEnvio,
      status: agendamentoSelecionado.status,
      motoristaId,
      horaSaida: horaSaidaNormalizada
    };

    try {
      const res = await putApi(`agendamento/transporte/${agendamentoSelecionado.id}`, payload);
      if (!res.ok) throw new Error(await res.text());
      mensagemSucessoModal = 'Pacientes atualizados com sucesso.';
      await carregarAgendamentos();
      if (abaAtiva === 'relatorio') {
        await carregarRelatorio(false);
      }
      const atualizado = agendamentos.find((item) => item.id === agendamentoSelecionado?.id);
      if (atualizado) {
        agendamentoSelecionado = atualizado;
        pacientesEmEdicao = atualizado.pacientes.map((pac) => String(pac.id));
        termosFiltroPaciente = atualizado.pacientes.map((pac) => formatarPacienteDisplay(pac));
        turnosEmEdicao = atualizado.pacientes.map((pac) => pac.turno ?? 'NAO_INFORMADO');
        retornosEmEdicao = atualizado.pacientes.map((pac) => Boolean(pac.retornaMesmoDia));
        locaisEmEdicao = atualizado.pacientes.map((pac) => {
          const local = extrairLocalDoPaciente(pac);
          return local?.id ? String(local.id) : '';
        });
        motoristaModalId = atualizado.motorista?.id ? String(atualizado.motorista.id) : '';
        horaSaidaModal = formatarHora(atualizado.horaSaida) || '';
        sugestoesAbertas = new Set();
      }
    } catch (error) {
      console.error(error);
      mensagemErroModal = 'Não foi possível atualizar. Tente novamente.';
      mensagemSucessoModal = '';
    } finally {
      salvandoEdicao = false;
    }
  }

  function fecharModal() {
    modalAberto = false;
    agendamentoSelecionado = null;
    pacientesEmEdicao = [];
    termosFiltroPaciente = [];
    turnosEmEdicao = [];
    retornosEmEdicao = [];
    locaisEmEdicao = [];
    motoristaModalId = '';
    horaSaidaModal = '';
    mensagemErroModal = '';
    mensagemSucessoModal = '';
    sugestoesAbertas = new Set();
  }

  async function confirmarAgendamento(id: number) {
    try {
      const res = await postApi(`agendamento/transporte/${id}/confirmar`, {});
      if (!res.ok) throw new Error('Falha ao confirmar agendamento.');
      await carregarAgendamentos();
      if (abaAtiva === 'relatorio') {
        await carregarRelatorio(false);
      }
    } catch (error) {
      console.error(error);
      alert('Não foi possível confirmar o agendamento.');
    }
  }

  async function removerAgendamento(id: number) {
    if (!confirm('Deseja remover este agendamento?')) return;
    try {
      const res = await deleteByIdApi(`agendamento/transporte/${id}`);
      if (!res.ok && res.status !== 204) throw new Error('Falha ao remover.');
      await carregarAgendamentos();
      if (abaAtiva === 'relatorio') {
        await carregarRelatorio(false);
      }
    } catch (error) {
      console.error(error);
      alert('Não foi possível remover o agendamento.');
    }
  }

  async function gerarRelatorioExcel() {
    if (relatorioResultados.length === 0) {
      alert('Gere o relatório antes de exportar.');
      return;
    }

    if (!brasaoBase64) {
      await carregarBrasao();
    }

    const { Workbook } = await import('exceljs');
    const workbook = new Workbook();
    const TOTAL_COLUNAS = 7;
    const COR_VERDE_CLARO = 'FFDFF5E1';
    const COR_VERDE_MEDIO = 'FFC9E8D6';
    const COR_HEADER_TABELA = 'FFE8F3E8';
    const COR_TEXTO = 'FF1F2937';
    const sheet = workbook.addWorksheet('Relatório');

    sheet.pageSetup = {
      orientation: 'landscape',
      fitToPage: true,
      fitToWidth: 1,
      fitToHeight: 0,
      margins: { top: 0.5, bottom: 0.5, left: 0.4, right: 0.4, header: 0.2, footer: 0.2 }
    };

    sheet.columns = [
      { width: 32 },
      { width: 18 },
      { width: 18 },
      { width: 18 },
      { width: 12 },
      { width: 12 },
      { width: 26 }
    ];

    let titleRow = 1;
    if (brasaoBase64) {
      const imageId = workbook.addImage({ base64: brasaoBase64, extension: 'png' });
      sheet.addImage(imageId, 'C1:D3');
      titleRow = 4;
    }

    sheet.mergeCells(titleRow, 3, titleRow, 4);
    const tituloCell = sheet.getCell(titleRow, 3);
    tituloCell.value = 'SIRG';
    tituloCell.font = { size: 20, bold: true, color: { argb: COR_TEXTO } };
    tituloCell.alignment = { horizontal: 'center', vertical: 'middle' };

    let currentRow = titleRow + 2;
    const borderStyle = {
      top: { style: 'thin', color: { argb: 'FFE5E7EB' } },
      left: { style: 'thin', color: { argb: 'FFE5E7EB' } },
      right: { style: 'thin', color: { argb: 'FFE5E7EB' } },
      bottom: { style: 'thin', color: { argb: 'FFE5E7EB' } }
    };

    relatorioResultados.forEach((registro, index) => {
      if (index > 0) {
        currentRow += 2;
      }

      sheet.mergeCells(currentRow, 1, currentRow, TOTAL_COLUNAS);
      const transporteCell = sheet.getCell(currentRow, 1);
      transporteCell.value = registro.transporte.nome;
      transporteCell.font = { bold: true, size: 16, color: { argb: COR_TEXTO } };
      transporteCell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: COR_VERDE_MEDIO } };
      transporteCell.alignment = { horizontal: 'center', vertical: 'middle' };
      transporteCell.border = borderStyle;
      sheet.getRow(currentRow).height = 24;
      currentRow += 1;

      const metadataRowIndex = currentRow;
      const dataFormatada = formatarData(registro.data);
      const horaFormatada = formatarHora(registro.horaSaida) || '-';
      const motorista = registro.motorista?.nome ?? '-';
      const cidade = registro.cidade.nome;

      const metadataSections = [
        { start: 1, end: 2, value: `Data: ${dataFormatada}` },
        { start: 3, end: 4, value: `Hora saída: ${horaFormatada}` },
        { start: 5, end: 6, value: `Motorista: ${motorista}` },
        { start: 7, end: 7, value: `Cidade: ${cidade}` }
      ];

      metadataSections.forEach(({ start, end, value }) => {
        sheet.mergeCells(metadataRowIndex, start, metadataRowIndex, end);
        const cell = sheet.getCell(metadataRowIndex, start);
        cell.value = value;
        cell.font = { bold: true, color: { argb: COR_TEXTO } };
        cell.alignment = { horizontal: 'left', vertical: 'middle' };
        for (let coluna = start; coluna <= end; coluna += 1) {
          const alvo = sheet.getCell(metadataRowIndex, coluna);
          alvo.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: COR_VERDE_CLARO } };
          alvo.border = borderStyle;
        }
      });
      sheet.getRow(metadataRowIndex).height = 18;
      currentRow += 1;

      const headerLabels = ['Paciente', 'CPF', 'Telefone', 'USF', 'Turno', 'Retorno', 'Local de Atendimento'];
      headerLabels.forEach((label, idx) => {
        const cell = sheet.getCell(currentRow, idx + 1);
        cell.value = label;
        cell.font = { bold: true };
        cell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: COR_HEADER_TABELA } };
        cell.alignment = { horizontal: 'center', vertical: 'middle', wrapText: true };
        cell.border = borderStyle;
      });
      sheet.getRow(currentRow).height = 20;
      currentRow += 1;

      if (registro.pacientes.length === 0) {
        sheet.mergeCells(currentRow, 1, currentRow, TOTAL_COLUNAS);
        const vazio = sheet.getCell(currentRow, 1);
        vazio.value = 'Sem pacientes cadastrados.';
        vazio.font = { italic: true, color: { argb: 'FF6B7280' } };
        vazio.alignment = { horizontal: 'center', vertical: 'middle' };
        vazio.border = borderStyle;
        currentRow += 1;
        return;
      }

      registro.pacientes.forEach((paciente) => {
        const linha = [
          paciente.nomePaciente,
          cpfFormatado(paciente) || '-',
          paciente.telefone ?? '-',
          paciente.usfOrigem ?? '-',
          turnoFormatado(paciente.turno),
          retornaMesmoDiaFormatado(paciente.retornaMesmoDia),
          obterLocalDoPaciente(registro, paciente)
        ];

        linha.forEach((valor, idx) => {
          const cell = sheet.getCell(currentRow, idx + 1);
          cell.value = valor;
          const horizontal =
            idx === 0 || idx === linha.length - 1 ? 'left' : 'center';
          cell.alignment = { horizontal, vertical: 'middle', wrapText: true };
          cell.border = borderStyle;
        });
        sheet.getRow(currentRow).height = 20;
        currentRow += 1;
      });
    });

    const buffer = await workbook.xlsx.writeBuffer();
    const blob = new Blob([buffer], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `relatorio-transporte-${relatorioData || 'todos'}.xlsx`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  function imprimirRelatorio() {
    if (relatorioResultados.length === 0) {
      alert('Gere o relatório antes de imprimir.');
      return;
    }
    window.print();
  }

  onMount(async () => {
    carregarBrasao();
    await carregarAuxiliares();
    await carregarAgendamentos();
  });
</script>
<div class="flex min-h-screen bg-gray-100">
  <RoleBasedMenu activePage="/consultar/transporte" />

  <div class="flex flex-1 flex-col">
    <header class="bg-emerald-700 text-white shadow flex items-center justify-between px-6 py-4">
      <h1 class="text-xl font-semibold">Gestão de Transporte</h1>
      <UserMenu />
    </header>

    <main class="flex-1 overflow-auto p-6 space-y-6">
      <section class="bg-white rounded-xl shadow-sm border border-emerald-100">
        <nav class="flex gap-2 border-b border-gray-200 px-6 pt-4">
          <button
            type="button"
            class={`px-4 py-2 text-sm font-medium rounded-t-md transition ${abaAtiva === 'lista' ? 'bg-white text-emerald-700' : 'bg-gray-100 text-gray-600'}`}
            on:click={() => alternarAba('lista')}
          >
            Agenda do Transporte
          </button>
          <button
            type="button"
            class={`px-4 py-2 text-sm font-medium rounded-t-md transition ${abaAtiva === 'historico' ? 'bg-white text-emerald-700' : 'bg-gray-100 text-gray-600'}`}
            on:click={() => alternarAba('historico')}
          >
            Histórico
          </button>
          <button
            type="button"
            class={`px-4 py-2 text-sm font-medium rounded-t-md transition ${abaAtiva === 'relatorio' ? 'bg-white text-emerald-700' : 'bg-gray-100 text-gray-600'}`}
            on:click={() => alternarAba('relatorio')}
          >
            Relatório
          </button>
        </nav>

        <div class="p-6 space-y-6">
          {#if abaAtiva === 'lista'}
            <form class="grid gap-4 md:grid-cols-[repeat(auto-fit,minmax(200px,1fr))] items-end" on:submit|preventDefault={() => carregarAgendamentos()}>
              <div class="flex flex-col">
                <label class="text-sm font-medium text-gray-700 mb-1">Data</label>
                <input type="date" class="border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500" bind:value={filtroData} />
              </div>
              <div class="flex flex-col">
                <label class="text-sm font-medium text-gray-700 mb-1">Transporte</label>
                <select class="border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500" bind:value={filtroTransporte}>
                  <option value="">Todos</option>
                  {#each transportes as transporte}
                    <option value={String(transporte.id)}>{transporte.nome}</option>
                  {/each}
                </select>
              </div>
              <div class="flex flex-col">
                <label class="text-sm font-medium text-gray-700 mb-1">Motorista</label>
                <select class="border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500" bind:value={filtroMotorista}>
                  <option value="">Todos</option>
                  {#each motoristas as motorista}
                    <option value={String(motorista.id)}>{motorista.nome}</option>
                  {/each}
                </select>
              </div>
              <div class="flex gap-2">
                <button type="submit" class="inline-flex items-center justify-center rounded-lg bg-emerald-700 px-4 py-2 text-sm font-medium text-white transition hover:bg-emerald-800">Aplicar filtros</button>
                <button type="button" class="inline-flex items-center justify-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 transition hover:bg-gray-100" on:click={limparFiltrosLista}>Limpar</button>
              </div>
            </form>
            {#if carregandoLista}
              <p class="text-sm text-gray-500">Carregando agendamentos...</p>
            {:else if agendamentosVisiveis.length === 0}
              <p class="text-sm text-gray-500">
                {exibindoHistorico ? 'Nenhum agendamento histórico encontrado.' : 'Nenhum agendamento encontrado.'}
              </p>
            {:else}
              <div class="space-y-4">
                {#each agendamentosVisiveis as agendamento}
                  <article class="rounded-xl border border-gray-200 bg-white shadow-sm p-4 space-y-3">
                    <div class="grid gap-4 md:grid-cols-6 items-center">
                      <div>
                        <p class="text-xs uppercase text-gray-500">Data</p>
                        <p class="text-base font-semibold text-gray-800">{formatarData(agendamento.data)}</p>
                      </div>
                      <div>
                        <p class="text-xs uppercase text-gray-500">Hora saída</p>
                        <p class="text-base font-semibold text-gray-800">{formatarHora(agendamento.horaSaida) || '-'}</p>
                      </div>
                      <div>
                        <p class="text-xs uppercase text-gray-500">Transporte</p>
                        <p class="text-base font-semibold text-gray-800">{agendamento.transporte.nome}</p>
                        <p class="text-xs text-gray-500">Vagas: {agendamento.transporte.vagas}</p>
                      </div>
                      <div>
                        <p class="text-xs uppercase text-gray-500">Motorista</p>
                        <p class="text-base font-semibold text-gray-800">{agendamento.motorista?.nome ?? '-'}</p>
                      </div>
                      <div>
                        <p class="text-xs uppercase text-gray-500">Status</p>
                        <p class="text-base font-semibold text-gray-800">{agendamento.status}</p>
                      </div>

                      <div>
                        <p class="text-xs uppercase text-gray-500">Pacientes</p>
                        <p class="text-base font-semibold text-gray-800">{agendamento.pacientes.length}</p>
                      </div>
                    </div>

                    <div class="flex flex-wrap gap-2">
                      <button type="button" class="rounded-lg border border-gray-300 px-3 py-2 text-sm font-medium text-gray-600 transition hover:bg-gray-100" on:click={() => alternarDetalhes(agendamento.id)}>
                        {detalhesAbertos.has(agendamento.id) ? 'Ocultar detalhes' : 'Ver detalhes'}
                      </button>
                      <button type="button" class="rounded-lg bg-emerald-600 px-3 py-2 text-sm font-medium text-white transition hover:bg-emerald-700" on:click={() => abrirEdicao(agendamento)}>
                        Editar pacientes
                      </button>
                      {#if !statusEhHistorico(agendamento.status)}
                        <button type="button" class="rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white transition hover:bg-blue-700" on:click={() => confirmarAgendamento(agendamento.id)}>
                          Confirmar
                        </button>
                      {/if}
                      <button type="button" class="rounded-lg bg-red-600 px-3 py-2 text-sm font-medium text-white transition hover:bg-red-700" on:click={() => removerAgendamento(agendamento.id)}>
                        Remover
                      </button>
                    </div>

                    {#if detalhesAbertos.has(agendamento.id)}
                      <div class="border-t border-gray-200 pt-4 space-y-3">
                        <div>
                          <p class="text-xs uppercase text-gray-500 mb-1">Cidade</p>
                          <p class="text-sm text-gray-800">{agendamento.cidade.nome}</p>
                        </div>

                        <div>
                          <p class="text-xs uppercase text-gray-500 mb-1">Locais de parada</p>
                          {#if agendamento.localAgendamento.length === 0}
                            <p class="text-sm text-gray-700">-</p>
                          {:else}
                            <ul class="list-disc list-inside text-sm text-gray-700">
                              {#each agendamento.localAgendamento as local}
                                <li>{local.nomeLocal} - {local.endereco} {local.numero}</li>
                              {/each}
                            </ul>
                          {/if}
                        </div>

                        <div>
                          <p class="text-xs uppercase text-gray-500 mb-2">Pacientes</p>
                          {#if agendamento.pacientes.length === 0}
                            <p class="text-sm text-gray-700">Nenhum paciente associado.</p>
                          {:else}
                            <div class="overflow-x-auto">
                              <table class="min-w-full table-fixed divide-y divide-gray-200 text-sm">
                                <thead class="bg-gray-50">
                                  <tr>
                                    <th class="px-3 py-2 text-left font-medium text-gray-600">Nome</th>
                                    <th class="px-3 py-2 text-left font-medium text-gray-600">CPF</th>
                                    <th class="px-3 py-2 text-left font-medium text-gray-600">Telefone</th>
                                    <th class="px-3 py-2 text-left font-medium text-gray-600">USF</th>
                                    <th class="px-3 py-2 text-left font-medium text-gray-600">Turno</th>
                                    <th class="px-3 py-2 text-left font-medium text-gray-600">Retorno</th>
                                    <th class="px-3 py-2 text-left font-medium text-gray-600 w-40">Local de atendimento</th>
                                  </tr>
                                </thead>
                                <tbody class="divide-y divide-gray-100">
                                  {#each agendamento.pacientes as paciente}
                                    <tr>
                                      <td class="px-3 py-2 text-gray-800">{paciente.nomePaciente}</td>
                                      <td class="px-3 py-2 text-gray-700">{cpfFormatado(paciente) || '-'}</td>
                                      <td class="px-3 py-2 text-gray-700">{paciente.telefone || '-'}</td>
                                      <td class="px-3 py-2 text-gray-700">{paciente.usfOrigem || '-'}</td>
                                      <td class="px-3 py-2 text-gray-700">{turnoFormatado(paciente.turno)}</td>
                                      <td class="px-3 py-2 text-gray-700">{retornaMesmoDiaFormatado(paciente.retornaMesmoDia)}</td>
                                      <td class="px-3 py-2 text-gray-700 w-40 whitespace-normal break-words">{obterLocalDoPaciente(agendamento, paciente)}</td>
                                    </tr>
                                  {/each}
                                </tbody>
                              </table>
                            </div>
                          {/if}
                        </div>
                      </div>
                    {/if}
                  </article>
                {/each}
              </div>
            {/if}
          {:else if abaAtiva === 'historico'}
            <div class="rounded-lg border border-emerald-100 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
              O histórico exibe automaticamente agendamentos confirmados ou com data futura.
            </div>

            {#if carregandoLista}
              <p class="text-sm text-gray-500">Carregando agendamentos...</p>
            {:else if agendamentosVisiveis.length === 0}
              <p class="text-sm text-gray-500">
                {exibindoHistorico ? 'Nenhum agendamento histórico encontrado.' : 'Nenhum agendamento encontrado.'}
              </p>
            {:else}
              <div class="space-y-4">
                {#each agendamentosVisiveis as agendamento}
                  <article class="rounded-xl border border-gray-200 bg-white shadow-sm p-4 space-y-3">
                    <div class="grid gap-4 md:grid-cols-6 items-center">
                      <div>
                        <p class="text-xs uppercase text-gray-500">Data</p>
                        <p class="text-base font-semibold text-gray-800">{formatarData(agendamento.data)}</p>
                      </div>
                      <div>
                        <p class="text-xs uppercase text-gray-500">Hora saída</p>
                        <p class="text-base font-semibold text-gray-800">{formatarHora(agendamento.horaSaida) || '-'}</p>
                      </div>
                      <div>
                        <p class="text-xs uppercase text-gray-500">Transporte</p>
                        <p class="text-base font-semibold text-gray-800">{agendamento.transporte.nome}</p>
                        <p class="text-xs text-gray-500">Vagas: {agendamento.transporte.vagas}</p>
                      </div>
                      <div>
                        <p class="text-xs uppercase text-gray-500">Motorista</p>
                        <p class="text-base font-semibold text-gray-800">{agendamento.motorista?.nome ?? '-'}</p>
                      </div>
                      <div>
                        <p class="text-xs uppercase text-gray-500">Status</p>
                        <p class="text-base font-semibold text-gray-800">{agendamento.status}</p>
                      </div>
                      
                      <div>
                        <p class="text-xs uppercase text-gray-500">Pacientes</p>
                        <p class="text-base font-semibold text-gray-800">{agendamento.pacientes.length}</p>
                      </div>
                    </div>

                    <div class="flex flex-wrap gap-2">
                      <button type="button" class="rounded-lg border border-gray-300 px-3 py-2 text-sm font-medium text-gray-600 transition hover:bg-gray-100" on:click={() => alternarDetalhes(agendamento.id)}>
                        {detalhesAbertos.has(agendamento.id) ? 'Ocultar detalhes' : 'Ver detalhes'}
                      </button>
                      <button type="button" class="rounded-lg bg-emerald-600 px-3 py-2 text-sm font-medium text-white transition hover:bg-emerald-700" on:click={() => abrirEdicao(agendamento)}>
                        Editar pacientes
                      </button>
                      {#if !statusEhHistorico(agendamento.status)}
                        <button type="button" class="rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white transition hover:bg-blue-700" on:click={() => confirmarAgendamento(agendamento.id)}>
                          Confirmar
                        </button>
                      {/if}
                      <button type="button" class="rounded-lg bg-red-600 px-3 py-2 text-sm font-medium text-white transition hover:bg-red-700" on:click={() => removerAgendamento(agendamento.id)}>
                        Remover
                      </button>
                    </div>

                    {#if detalhesAbertos.has(agendamento.id)}
                      <div class="border-t border-gray-200 pt-4 space-y-3">
  
                      <div>
                          <p class="text-xs uppercase text-gray-500 mb-1">Cidade</p>
                          <p class="text-sm text-gray-800">{agendamento.cidade.nome}</p>
                        </div>
  
                      <div>
                          <p class="text-xs uppercase text-gray-500 mb-1">Locais de parada</p>
                          {#if agendamento.localAgendamento.length === 0}
                            <p class="text-sm text-gray-700">-</p>
                          {:else}
                            <ul class="list-disc list-inside text-sm text-gray-700">
                              {#each agendamento.localAgendamento as local}
                                <li>{local.nomeLocal} â€” {local.endereco} {local.numero}</li>
                              {/each}
                            </ul>
                          {/if}
                        </div>
  
                      <div>
                          <p class="text-xs uppercase text-gray-500 mb-2">Pacientes</p>
                          {#if agendamento.pacientes.length === 0}
                            <p class="text-sm text-gray-700">Nenhum paciente associado.</p>
                          {:else}
                            <table class="min-w-full table-fixed divide-y divide-gray-200 text-sm">
                              <thead class="bg-gray-50">
                                <tr>
                                  <th class="px-3 py-2 text-left font-medium text-gray-600">Nome</th>
                                  <th class="px-3 py-2 text-left font-medium text-gray-600">CPF</th>
                                  <th class="px-3 py-2 text-left font-medium text-gray-600">Telefone</th>
                                  <th class="px-3 py-2 text-left font-medium text-gray-600">USF Origem</th>
                                  <th class="px-3 py-2 text-left font-medium text-gray-600">Turno</th>
                              <th class="px-3 py-2 text-left font-medium text-gray-600">Retorno no dia</th>
                              <th class="px-3 py-2 text-left font-medium text-gray-600 w-40">Local de Atendimento</th>
                                </tr>
                              </thead>
                              <tbody class="divide-y divide-gray-100">
                                {#each agendamento.pacientes as paciente}
                                  <tr>
                                    <td class="px-3 py-2 text-gray-800">{paciente.nomePaciente}</td>
                                    <td class="px-3 py-2 text-gray-700">{cpfFormatado(paciente) || '-'}</td>
                                    <td class="px-3 py-2 text-gray-700">{paciente.telefone || '-'}</td>
                                    <td class="px-3 py-2 text-gray-700">{paciente.usfOrigem || '-'}</td>
                                    <td class="px-3 py-2 text-gray-700">{turnoFormatado(paciente.turno)}</td>
                                    <td class="px-3 py-2 text-gray-700">{retornaMesmoDiaFormatado(paciente.retornaMesmoDia)}</td>
                                    <td class="px-3 py-2 text-gray-700 w-40 whitespace-normal break-words">{obterLocalDoPaciente(agendamento, paciente)}</td>
                                  </tr>
                                {/each}
                              </tbody>
                            </table>
                          {/if}
                        </div>
                      </div>
                    {/if}
                  </article>
                {/each}
              </div>
            {/if}
          {:else}
            <form class="grid gap-4 md:grid-cols-[repeat(auto-fit,minmax(200px,1fr))] items-end" on:submit|preventDefault={() => carregarRelatorio(true)}>
              <div class="flex flex-col">
                <label class="text-sm font-medium text-gray-700 mb-1">Data *</label>
                <input type="date" class="border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500" bind:value={relatorioData} />
              </div>
              <div class="flex flex-col">
                <label class="text-sm font-medium text-gray-700 mb-1">Transporte</label>
                <select class="border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500" bind:value={relatorioTransporte}>
                  <option value="">Todos</option>
                  {#each transportes as transporte}
                    <option value={String(transporte.id)}>{transporte.nome}</option>
                  {/each}
                </select>
              </div>
              <div class="flex flex-col">
                <label class="text-sm font-medium text-gray-700 mb-1">Motorista</label>
                <select class="border border-gray-300 rounded-lg px-3 py-2 focus:outline-none focus:ring-2 focus:ring-emerald-500" bind:value={relatorioMotorista}>
                  <option value="">Todos</option>
                  {#each motoristas as motorista}
                    <option value={String(motorista.id)}>{motorista.nome}</option>
                  {/each}
                </select>
              </div>
              <div class="flex gap-2">
                <button type="submit" class="inline-flex items-center justify-center rounded-lg bg-emerald-700 px-4 py-2 text-sm font-medium text-white transition hover:bg-emerald-800">Gerar relatório</button>
                <button type="button" class="inline-flex items-center justify-center rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-700 transition hover:bg-gray-100" on:click={limparFiltrosRelatorio}>Limpar</button>
                <button type="button" class="inline-flex items-center justify-center rounded-lg border border-emerald-600 px-4 py-2 text-sm font-medium text-emerald-700 transition hover:bg-emerald-50" on:click={gerarRelatorioExcel}>Exportar Excel</button>
              </div>
            </form>

            {#if relatorioErro}
              <p class="text-sm text-red-600">{relatorioErro}</p>
            {/if}
            {#if relatorioSucesso}
              <p class="text-sm text-emerald-700">{relatorioSucesso}</p>
            {/if}

            {#if carregandoRelatorio}
              <p class="text-sm text-gray-500">Gerando relatório...</p>
            {:else if relatorioResultados.length === 0}
              <p class="text-sm text-gray-500">Informe a data e, opcionalmente, os demais filtros para visualizar o relatório.</p>
            {:else}
              <div class="flex items-center justify-between gap-3 rounded-lg border border-gray-200 bg-white px-4 py-3">
                <h3 class="text-sm font-semibold text-gray-800">Resultados do relatório</h3>
                <button type="button" class="inline-flex items-center justify-center rounded-lg border border-emerald-600 px-4 py-2 text-sm font-medium text-emerald-700 transition hover:bg-emerald-50" on:click={imprimirRelatorio}>Imprimir</button>
              </div>
              <div id="relatorio-imprimir" class="space-y-6 bg-white">
                {#each relatorioResultados as registro}
                  <section class="rounded-lg border border-gray-200 p-5 shadow-sm">
                    <header class="grid gap-2 md:grid-cols-3 border-b border-gray-200 pb-4">
                      <div>
                        <p class="text-xs uppercase text-gray-500">Data</p>
                        <p class="text-base font-semibold text-gray-800">{formatarData(registro.data)}</p>
                      </div>
                      <div>
                        <p class="text-xs uppercase text-gray-500">Hora saída</p>
                        <p class="text-base font-semibold text-gray-800">{formatarHora(registro.horaSaida) || '-'}</p>
                      </div>
                      <div>
                        <p class="text-xs uppercase text-gray-500">Transporte</p>
                        <p class="text-base font-semibold text-gray-800">{registro.transporte.nome}</p>
                      </div>
                      <div>
                        <p class="text-xs uppercase text-gray-500">Motorista</p>
                        <p class="text-base font-semibold text-gray-800">{registro.motorista?.nome ?? '-'}</p>
                      </div>
                      <div>
                        <p class="text-xs uppercase text-gray-500">Retorno no dia</p>
                        <p class="text-base font-semibold text-gray-800">{resumoRetornoAgendamento(registro)}</p>
                      </div>
                    </header>

                    <div class="mt-4 space-y-2">
                      <p class="text-sm font-semibold text-gray-700">Pacientes</p>
                      <table class="min-w-full table-fixed divide-y divide-gray-200 text-sm">
                        <thead class="bg-gray-50 text-left">
                          <tr>
                            <th class="px-3 py-2 font-medium text-gray-600">Nome</th>
                            <th class="px-3 py-2 font-medium text-gray-600">CPF</th>
                            <th class="px-3 py-2 font-medium text-gray-600">Telefone</th>
                            <th class="px-3 py-2 font-medium text-gray-600">USF</th>
                            <th class="px-3 py-2 font-medium text-gray-600">Turno</th>
                            <th class="px-3 py-2 font-medium text-gray-600">Retorno</th>
                            <th class="px-3 py-2 font-medium text-gray-600 w-40">Local de Atendimento</th>
                          </tr>
                        </thead>
                        <tbody class="divide-y divide-gray-100">
                          {#if registro.pacientes.length === 0}
                            <tr>
                              <td colspan="7" class="px-3 py-3 text-gray-500 italic text-center">Nenhum paciente associado a este transporte.</td>
                            </tr>
                          {:else}
                            {#each registro.pacientes as paciente}
                              <tr>
                                <td class="px-3 py-2 text-gray-800">{paciente.nomePaciente}</td>
                                <td class="px-3 py-2 text-gray-700">{cpfFormatado(paciente) || '-'}</td>
                                <td class="px-3 py-2 text-gray-700">{paciente.telefone || '-'}</td>
                                <td class="px-3 py-2 text-gray-700">{paciente.usfOrigem || '-'}</td>
                                <td class="px-3 py-2 text-gray-700">{turnoFormatado(paciente.turno)}</td>
                                <td class="px-3 py-2 text-gray-700">{retornaMesmoDiaFormatado(paciente.retornaMesmoDia)}</td>
                                <td class="px-3 py-2 text-gray-700 w-40 whitespace-normal break-words">
                                  {obterLocalDoPaciente(registro, paciente)}
                                </td>
                              </tr>
                            {/each}
                          {/if}
                        </tbody>
                      </table>

                      <div class="pt-2">
                        <p class="text-sm font-semibold text-gray-700">Locais de parada</p>
                        <ul class="list-disc list-inside text-sm text-gray-700">
                          {#if registro.localAgendamento.length === 0}
                            <li>-</li>
                          {:else}
                            {#each registro.localAgendamento as local}
                              <li>{local.nomeLocal} - {local.endereco} {local.numero}</li>
                            {/each}
                          {/if}
                        </ul>
                      </div>
                    </div>
                  </section>
                {/each}
              </div>
            {/if}
          {/if}
        </div>
      </section>
    </main>
  </div>
</div>
{#if modalAberto && agendamentoSelecionado}
  <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/40 px-4">
    <div class="w-full max-w-3xl rounded-2xl bg-white shadow-xl">
      <header class="flex items-center justify-between border-b border-gray-200 px-6 py-4">
        <div>
          <h2 class="text-lg font-semibold text-gray-800">Editar pacientes do transporte</h2>
          <p class="text-sm text-gray-500">
            {agendamentoSelecionado.transporte.nome} - {formatarData(agendamentoSelecionado.data)} - {agendamentoSelecionado.cidade.nome}
          </p>
        </div>
        <button type="button" class="text-gray-500 hover:text-gray-700" on:click={fecharModal} aria-label="Fechar">?</button>
      </header>

      <div class="px-6 py-4 space-y-4 max-h-[70vh] overflow-y-auto">
        {#if mensagemErroModal}
          <div class="rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{mensagemErroModal}</div>
        {/if}
        {#if mensagemSucessoModal}
          <div class="rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">{mensagemSucessoModal}</div>
        {/if}

        <div class="grid gap-3 md:grid-cols-2">
          <div class="flex flex-col">
            <label class="text-sm font-medium text-gray-700 mb-1">Motorista</label>
            <select
              class="border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-200"
              bind:value={motoristaModalId}
            >
              <option value="">Sem motorista</option>
              {#each motoristas as motorista}
                <option value={String(motorista.id)}>{motorista.nome}</option>
              {/each}
            </select>
          </div>
          <div class="flex flex-col">
            <label class="text-sm font-medium text-gray-700 mb-1">Hora de saída</label>
            <input
              type="time"
              class="border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-200"
              bind:value={horaSaidaModal}
            />
          </div>
        </div>

        <div class="space-y-3">
          {#each pacientesEmEdicao as paciente, index}
            <div class="rounded-lg border border-gray-200 bg-gray-50 p-3 space-y-2">
              <label class="text-sm font-medium text-gray-700 block">Paciente {index + 1}</label>
              <div class="relative">
                <input
                  type="text"
                  placeholder="Buscar paciente por nome ou CPF"
                  class="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-200"
                  bind:value={termosFiltroPaciente[index]}
                  autocomplete="off"
                  on:focus={() => abrirSugestoes(index)}
                  on:input={(event) => atualizarBuscaPaciente(index, (event.target as HTMLInputElement).value)}
                  on:blur={() => agendarFecharSugestoes(index)}
                />
                {#if sugestoesAbertas.has(index)}
                  {#if solicitacoesFiltradas(index).length > 0}
                    <ul class="absolute z-20 mt-1 max-h-56 w-full overflow-auto rounded-lg border border-gray-200 bg-white shadow-lg">
                      {#each solicitacoesFiltradas(index) as sol}
                        <li>
                          <button
                            type="button"
                            class="flex w-full flex-col items-start gap-0.5 px-3 py-2 text-left text-sm text-gray-700 hover:bg-emerald-50"
                            on:mousedown|preventDefault={() => selecionarPaciente(index, sol)}
                          >
                            <span class="font-medium text-gray-800">{sol.nomePaciente}</span>
                            <span class="text-xs text-gray-500">
                              CPF {cpfFormatado(sol) || 'N/A'}
                              {#if sol.usfOrigem}
                                &bull; USF {sol.usfOrigem}
                              {/if}
                            </span>
                          </button>
                        </li>
                      {/each}
                    </ul>
                  {:else}
                    <div class="absolute z-20 mt-1 w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm text-gray-500 shadow-lg">
                      Nenhum paciente encontrado.
                    </div>
                  {/if}
                {/if}
              </div>
              <div class="flex flex-col">
                <label class="text-xs font-medium text-gray-600">Local de atendimento</label>
                <select class="border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-200" bind:value={locaisEmEdicao[index]}>
                  <option value="">Selecione...</option>
                  {#each locaisDisponiveis as local}
                    <option value={String(local.id)}>{local.nomeLocal}</option>
                  {/each}
                </select>
              </div>

              <div class="grid gap-3 md:grid-cols-2">
                <div class="flex flex-col">
                  <label class="text-xs font-medium text-gray-600">Turno</label>
                  <select class="border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-200" bind:value={turnosEmEdicao[index]}>
                    <option value="MANHA">Manha</option>
                    <option value="TARDE">Tarde</option>
                    <option value="NAO_INFORMADO">Nao informado</option>
                  </select>
                </div>
                <label class="flex items-center gap-2 text-xs font-medium text-gray-600 md:pt-6">
                  <input type="checkbox" class="h-4 w-4 rounded border-gray-300 text-emerald-600 focus:ring-emerald-500" bind:checked={retornosEmEdicao[index]} />
                  Retorno no mesmo dia
                </label>
              </div>
              <div class="flex justify-end">
                <button type="button" class="text-red-500 hover:text-red-700 text-sm" on:click={() => removerPacienteCampo(index)}>Remover</button>
              </div>
            </div>
          {/each}
        </div>

        <button type="button" class="text-sm font-medium text-emerald-700 hover:text-emerald-900" on:click={adicionarPacienteCampo}>
          + Adicionar paciente
        </button>
      </div>

      <footer class="flex justify-end gap-2 border-t border-gray-200 px-6 py-4">
        <button type="button" class="rounded-lg border border-gray-300 px-4 py-2 text-sm font-medium text-gray-600 hover:bg-gray-100" on:click={fecharModal}>Cancelar</button>
        <button type="button" class="rounded-lg bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-700 disabled:opacity-60" disabled={salvandoEdicao} on:click={salvarEdicao}>
          {salvandoEdicao ? 'Salvando...' : 'Salvar alterações'}
        </button>
      </footer>
    </div>
  </div>
{/if}

<style>
  @media print {
    body * {
      visibility: hidden;
    }

    #relatorio-imprimir,
    #relatorio-imprimir * {
      visibility: visible;
    }

    #relatorio-imprimir {
      position: absolute;
      inset: 0;
      margin: 0;
      padding: 0;
    }
  }
</style>




