<script lang="ts">
  import { onMount } from 'svelte';
  import { jsPDF } from 'jspdf';
  import autoTable from 'jspdf-autotable';
  import { getApi, postApi } from '$lib/api';
  import { listarEspecialidadesCatalogo } from '$lib/especialidadesApi.js';
  import Menu from '$lib/Menu.svelte';
  import UserMenu from '$lib/UserMenu.svelte';

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

  async function atualizarContagemPorEspecialidades() {
    if (!dataAgendada) {
      return;
    }

    for (const codigo of examesSelecionados) {
      await carregarContagemPorEspecialidade(codigo);
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

  const brasaoUrl =
    'https://upload.wikimedia.org/wikipedia/commons/9/97/Bras%C3%A3o_de_Concei%C3%A7%C3%A3o_do_Almeida.png';

  function getLocalLabel(value: string) {
    const found = locaisAgendamento.find((loc) => String(loc.id) === String(value));
    if (!found) {
      return 'Local não informado';
    }
    return found.cidadeNome ? `${found.nomeLocal} - ${found.cidadeNome}` : found.nomeLocal;
  }

  async function loadImage(url: string): Promise<HTMLImageElement> {
    return new Promise((resolve, reject) => {
      const img = new Image();
      img.onload = () => resolve(img);
      img.onerror = (e) => reject(new Error(`Falha ao carregar imagem: ${url}. Erro: ${e}`));
      img.crossOrigin = 'Anonymous';
      img.src = url;
    });
  }

  async function gerarComprovantePDF(dadosPDF: {
    solicitacaoId: number;
    nomePaciente: string;
    cpfPaciente: string;
    usfOrigem: string;
    cns?: string;
    examesSelecionados: string[];
    dataAgendada: string;
    turno: 'MANHA' | 'TARDE';
    localAgendamentoId: string;
    observacoes: string;
  }) {
    const doc = new jsPDF({ unit: 'pt', format: 'a4', orientation: 'portrait' });
    const margin = { top: 80, left: 40, right: 40, bottom: 60 };
    const pageWidth = doc.internal.pageSize.getWidth();
    const pageHeight = doc.internal.pageSize.getHeight();
    let currentY = margin.top;
    
    const corDestaque = '#0D7244';
    const corPadrao = '#1f2937';
    const corLabel = '#374151';

    const imgWidth = 60;
    const imgHeight = 60;
    const imgX = pageWidth / 2 - imgWidth / 2;
    const imgY = margin.top - 70;

    try {
      const brasaoImage = await loadImage(brasaoUrl);
      doc.addImage(brasaoImage, 'PNG', imgX, imgY, imgWidth, imgHeight);
    } catch (error) {
      console.error('Erro ao adicionar brasão ao PDF:', error);
    }

    currentY = imgY + imgHeight + 25;
    
    doc.setFontSize(20);
    doc.setTextColor('#115e59');
    doc.setFont('helvetica', 'bold');
    doc.text('Central de Regulação', pageWidth / 2, currentY, { align: 'center' });
    currentY += 24;
    doc.setFontSize(14);
    doc.setTextColor('#333333');
    doc.setFont('helvetica', 'normal');
    doc.text('Conceição do Almeida', pageWidth / 2, currentY, { align: 'center' });
    currentY += 40;
    doc.setFontSize(18);
    doc.setFont('helvetica', 'bold');
    doc.text('Comprovante de Agendamento', pageWidth / 2, currentY, { align: 'center' });
    currentY += 35;

    const nomesAmigaveis = dadosPDF.examesSelecionados.map((exame) => getEspecialidadeLabel(exame));

    const allInfo = [
        { label: 'ID Solicitação', value: String(dadosPDF.solicitacaoId), color: corDestaque, style: 'bold' },
        { label: 'Paciente',       value: dadosPDF.nomePaciente, color: corPadrao, style: 'bold' },
        { label: 'CPF',            value: dadosPDF.cpfPaciente, color: corPadrao },
        { label: 'USF Origem',     value: dadosPDF.usfOrigem, color: corPadrao },
        ...(dadosPDF.cns ? [{ label: 'CNS', value: dadosPDF.cns, color: corPadrao }] : []),
        { label: 'Exames Agendados', value: nomesAmigaveis.join(', '), color: corDestaque, style: 'bold' },
        { label: 'Data Agendada',  value: new Date(dadosPDF.dataAgendada + 'T00:00:00').toLocaleDateString('pt-BR'), color: corDestaque, style: 'bold' },
        { label: 'Turno',          value: dadosPDF.turno === 'MANHA' ? 'Manhã' : 'Tarde', color: corPadrao },
        { label: 'Local',          value: getLocalLabel(dadosPDF.localAgendamentoId), color: corDestaque, style: 'bold' },
        { label: 'Observações',    value: dadosPDF.observacoes || 'Nenhuma', color: corPadrao }
    ];

    allInfo.forEach((info, index) => {
        const bgColor = index % 2 === 0 ? '#f3f4f6' : '#ffffff';
        const lines = doc.splitTextToSize(String(info.value), pageWidth - margin.left - margin.right - 130);
        const rowHeight = Math.max(28, lines.length * 14 + 14);
        
        doc.setFillColor(bgColor);
        doc.rect(margin.left, currentY - 14, pageWidth - margin.left - margin.right, rowHeight, 'F');
        
        doc.setFontSize(11);
        doc.setFont('helvetica', 'bold');
        doc.setTextColor(corLabel);
        doc.text(info.label, margin.left + 10, currentY);

        const style = info.style || 'normal';
        const color = info.color || corPadrao;
        
        doc.setFont('helvetica', style as any);
        doc.setTextColor(color);
        doc.text(lines, margin.left + 130, currentY);
        
        currentY += rowHeight;
    });

    currentY += 20;
    doc.setFontSize(11);
    doc.setTextColor('#dc3545');
    doc.setFont('helvetica', 'bold');
    doc.text('Orientação Importante:', margin.left, currentY);
    currentY += 18;
    doc.setFont('helvetica', 'normal');
    doc.setTextColor('#212529');
    doc.text('• Comparecer com 15 minutos de antecedência.', margin.left + 10, currentY);
    currentY += 16;
    doc.text('• Levar RG, CPF, Cartão do SUS e este comprovante.', margin.left + 10, currentY);
    currentY += 16;
    doc.text('• Em caso de não comparecimento, favor informar com antecedência.', margin.left + 10, currentY);

    const hoje = new Date().toLocaleDateString('pt-BR');
    const hora = new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
    doc.setFontSize(9);
    doc.setTextColor('#6c757d');
    doc.text(`Emitido em: ${hoje} às ${hora}`, margin.left, pageHeight - margin.bottom + 20);
    doc.text(`SIRGE System v1.0`, pageWidth - margin.right, pageHeight - margin.bottom + 20, { align: 'right' });

    doc.save(`comprovante_${dadosPDF.nomePaciente.replace(/\s+/g, '_')}_${dadosPDF.solicitacaoId}.pdf`);
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

    const localIdNumber = localAgendamentoId ? Number(localAgendamentoId) : null;

    const body: Record<string, unknown> = {
      examesSelecionados,
      dataAgendada,
      observacoes,
      turno,
      localAgendado: null
    };

    body.localAgendamentoId = localIdNumber !== null ? localIdNumber : null;

    try {
      postApi(`agendamentos/${solicitacaoId}`, body).then(async (resposta) => {
        if (resposta.ok) {
          alert('Agendamento realizado com sucesso!');

          await gerarComprovantePDF({
            solicitacaoId: solicitacaoDetalhe.id,
            nomePaciente: solicitacaoDetalhe.nomePaciente,
            cpfPaciente: solicitacaoDetalhe.cpfPaciente,
            usfOrigem: solicitacaoDetalhe.usfOrigem,
            cns: solicitacaoDetalhe.cns,
            examesSelecionados,
            dataAgendada,
            turno,
            localAgendamentoId,
            observacoes
          });

          solicitacaoId = '';
          examesSelecionados = [];
          dataAgendada = '';
          localAgendamentoId = '';
          observacoes = '';
          turno = 'MANHA';
          valorBusca = '';
          solicitacaoDetalhe = null;

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
  <Menu activePage="/agendar" />

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
                    <label for="campo-usf" class="text-sm font-medium text-gray-700">USF Origem</label>
                    <input id="campo-usf" type="text" value={solicitacaoDetalhe.usfOrigem} readonly class="w-full bg-gray-100 border-gray-300 rounded-lg p-2" />
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
                  <div class="bg-emerald-50 border border-emerald-200 rounded-lg p-4 mt-4 space-y-2">
                    <h4 class="font-semibold text-emerald-700">Contagem de vagas por especialidade selecionada</h4>
                    {#each examesSelecionados as codigo}
                      <div class="bg-white border border-gray-200 rounded p-2">
                        <p class="text-sm font-medium">{getEspecialidadeLabel(codigo)} ({codigo})</p>
                        <p class="text-xs text-gray-600">Vagas definidas: {getEspecialidadeVagas(codigo) === 0 ? 'Sem limite (0)' : getEspecialidadeVagas(codigo)}</p>
                        {#if contagemPorEspecialidade[codigo.toUpperCase()]}
                          <p class="text-xs text-gray-600">Agendados hoje: {contagemPorEspecialidade[codigo.toUpperCase()].agendados}</p>
                          {#if getEspecialidadeVagas(codigo) === 0}
                            <p class="text-xs text-indigo-700">Sem limite de vagas.</p>
                          {:else}
                            <p class="text-xs text-gray-600">Restante: {contagemPorEspecialidade[codigo.toUpperCase()].restante}</p>
                            {#if contagemPorEspecialidade[codigo.toUpperCase()].restante <= 0}
                              <p class="text-xs text-red-600">Limite atingido. Não é possível agendar mais para esta data.</p>
                            {/if}
                          {/if}
                        {:else}
                          <p class="text-xs text-gray-500">Carregando contagem...</p>
                        {/if}
                      </div>
                    {/each}
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
                    <select id="localAgendamentoId" bind:value={localAgendamentoId} class="w-full border border-gray-300 rounded-lg p-2" required>
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
