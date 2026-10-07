<script lang="ts">
  import { onMount } from "svelte";
  import { toast } from "svelte-sonner";
  import UserMenu from "$lib/UserMenu.svelte";
  import RoleBasedMenu from "$lib/RoleBasedMenu.svelte";
  import LoadingSpinner from "$lib/LoadingSpinner.svelte";
  import {
    alterarEnvioWhatsApp,
    buscarEstadoWhatsApp,
    buscarIndicadoresWhatsApp,
    executarLembretesWhatsApp,
    listarMensagensWhatsApp,
    reenviarMensagemWhatsApp
  } from "$lib/whatsappApi.js";

  type Estado = {
    configurado: boolean;
    webhookLigado: boolean;
    envioLigado: boolean;
    alteradoPorNome: string | null;
    alteradoEm: string | null;
    limiteDiario: number;
    enviadasHoje: number;
    modoTeste: boolean;
  };

  type Indicadores = {
    de: string;
    ate: string;
    total: number;
    enviadas: number;
    entregues: number;
    lidas: number;
    falhas: number;
    pendentes: number;
    naoEnviadas: number;
    naoEnviadasPorMotivo: Record<string, number>;
    cobraveis: number;
    cobraveisPorCategoria: Record<string, number>;
    recebidas: number;
  };

  type Mensagem = {
    id: number;
    agendamentoId: number;
    solicitacaoId: number | null;
    tipo: string;
    origem: string;
    resultado: string;
    motivo: string | null;
    dataReferencia: string;
    telefoneFinal: string | null;
    tentativas: number;
    erroCodigo: string | null;
    cobravel: boolean | null;
    categoriaCobranca: string | null;
    criadoEm: string;
    enviadoEm: string | null;
    entregueEm: string | null;
    lidoEm: string | null;
    falhouEm: string | null;
  };

  const TIPOS = [
    { valor: "CONFIRMACAO", rotulo: "Confirmação" },
    { valor: "REMARCACAO", rotulo: "Remarcação" },
    { valor: "CANCELAMENTO", rotulo: "Cancelamento" },
    { valor: "LEMBRETE", rotulo: "Lembrete" }
  ];
  const RESULTADOS = [
    { valor: "PENDENTE", rotulo: "Na fila" },
    { valor: "ENVIANDO", rotulo: "Enviando" },
    { valor: "ENVIADO", rotulo: "Enviada" },
    { valor: "ENTREGUE", rotulo: "Entregue" },
    { valor: "LIDO", rotulo: "Lida" },
    { valor: "FALHOU", rotulo: "Falhou" },
    { valor: "NAO_ENVIADO", rotulo: "Não enviada" }
  ];
  const MOTIVOS: Record<string, string> = {
    SEM_TELEFONE: "Paciente sem telefone",
    TELEFONE_INVALIDO: "Telefone inválido ou sem DDD",
    OPT_OUT: "Paciente pediu para não receber",
    ENVIO_DESLIGADO: "Envio estava desligado",
    NAO_CONFIGURADO: "Envio não configurado",
    LIMITE_DIARIO: "Limite diário atingido",
    FORA_DA_LISTA_DE_TESTE: "Fora da lista de teste",
    AGENDAMENTO_REMOVIDO: "Agendamento excluído ou sem item agendado",
    SUBSTITUIDO_POR_REMARCACAO: "Substituída pela remarcação",
    PACIENTE_DE_OUTRO_MUNICIPIO: "Paciente de outro município",
    DATA_PASSADA: "Data do atendimento já passou",
    SEM_MOTIVO: "Sem motivo registrado"
  };
  const ITENS_POR_PAGINA = 20;

  function dataLocal(deslocamentoDias = 0): string {
    const d = new Date();
    d.setDate(d.getDate() + deslocamentoDias);
    const mes = String(d.getMonth() + 1).padStart(2, "0");
    const dia = String(d.getDate()).padStart(2, "0");
    return `${d.getFullYear()}-${mes}-${dia}`;
  }

  // ---- Estado da integracao
  let estado = $state<Estado | null>(null);
  let erroEstado = $state<string | null>(null);
  let alterandoEnvio = $state(false);
  let rodandoLote = $state(false);

  // ---- Filtros
  let dataDe = $state(dataLocal(-6));
  let dataAte = $state(dataLocal());
  let tipo = $state("");
  let resultado = $state("");

  // ---- Dados
  let indicadores = $state<Indicadores | null>(null);
  let mensagens = $state<Mensagem[]>([]);
  let totalElements = $state(0);
  let totalPages = $state(0);
  let paginaAtual = $state(1);
  let carregando = $state(true);
  let cargaInicial = $state(true);
  let erro = $state<string | null>(null);
  let reenviandoId = $state<number | null>(null);
  let ultimaBusca: symbol | null = null;

  const periodoInvalido = $derived(!!dataDe && !!dataAte && dataDe > dataAte);
  const podeDisparar = $derived(!!estado?.configurado && !!estado?.envioLigado);
  const naoEnviadasPorMotivo = $derived(
    Object.entries(indicadores?.naoEnviadasPorMotivo ?? {}).sort((a, b) => b[1] - a[1])
  );
  const cobraveisPorCategoria = $derived(
    Object.entries(indicadores?.cobraveisPorCategoria ?? {}).sort((a, b) => b[1] - a[1])
  );

  const rotuloTipo = (valor: string) => TIPOS.find((t) => t.valor === valor)?.rotulo ?? valor;
  const rotuloResultado = (valor: string) => RESULTADOS.find((r) => r.valor === valor)?.rotulo ?? valor;
  const rotuloMotivo = (valor: string) => MOTIVOS[valor] ?? valor;

  function classeResultado(valor: string): string {
    if (valor === "LIDO" || valor === "ENTREGUE") return "bg-emerald-100 text-emerald-900 border-emerald-200";
    if (valor === "ENVIADO") return "bg-sky-100 text-sky-900 border-sky-200";
    if (valor === "FALHOU") return "bg-red-100 text-red-900 border-red-200";
    if (valor === "NAO_ENVIADO") return "bg-amber-100 text-amber-900 border-amber-200";
    return "bg-gray-100 text-gray-800 border-gray-200";
  }

  function formatarData(iso: string | null): string {
    if (!iso) return "—";
    const [ano, mes, dia] = iso.slice(0, 10).split("-").map(Number);
    return new Date(ano, mes - 1, dia).toLocaleDateString("pt-BR");
  }

  function formatarDataHora(iso: string | null): string {
    if (!iso) return "—";
    return new Date(iso).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
  }

  /** Reenvio so faz sentido para o que o agendamento ainda permite montar. */
  function tipoParaReenvio(m: Mensagem): "CONFIRMACAO" | "LEMBRETE" | null {
    if (m.tipo === "LEMBRETE") return "LEMBRETE";
    if (m.tipo === "CONFIRMACAO" || m.tipo === "REMARCACAO") return "CONFIRMACAO";
    return null;
  }

  async function carregarEstado() {
    try {
      estado = await buscarEstadoWhatsApp();
      erroEstado = null;
    } catch (e) {
      erroEstado = e instanceof Error ? e.message : "Erro ao carregar o estado do WhatsApp.";
    }
  }

  async function buscar(pagina: number) {
    if (periodoInvalido) return;
    paginaAtual = Math.max(pagina, 1);
    carregando = true;
    erro = null;
    const estaBusca = Symbol();
    ultimaBusca = estaBusca;
    try {
      const [ind, lista] = await Promise.all([
        buscarIndicadoresWhatsApp({ de: dataDe, ate: dataAte }),
        listarMensagensWhatsApp({
          de: dataDe,
          ate: dataAte,
          tipo,
          resultado,
          page: paginaAtual - 1,
          size: ITENS_POR_PAGINA
        })
      ]);
      if (ultimaBusca !== estaBusca) return;
      indicadores = ind;
      mensagens = lista.content ?? [];
      totalElements = lista.totalElements ?? mensagens.length;
      totalPages = lista.totalPages ?? 0;
    } catch (e) {
      if (ultimaBusca !== estaBusca) return;
      erro = e instanceof Error ? e.message : "Erro ao carregar as mensagens.";
      mensagens = [];
      totalElements = 0;
      totalPages = 0;
    } finally {
      if (ultimaBusca === estaBusca) {
        carregando = false;
        cargaInicial = false;
      }
    }
  }

  function atualizar() {
    carregarEstado();
    buscar(paginaAtual);
  }

  async function alternarEnvio() {
    if (!estado) return;
    const ligar = !estado.envioLigado;
    const pergunta = ligar
      ? "Ligar o envio de mensagens pelo WhatsApp?\n\nA partir de agora, todo agendamento novo envia uma confirmação ao paciente, e o lote de lembretes passa a rodar às 8h."
      : "Desligar o envio de mensagens pelo WhatsApp?\n\nNenhuma mensagem será enviada até religar. As que estão na fila serão registradas como não enviadas.";
    if (!confirm(pergunta)) return;
    alterandoEnvio = true;
    try {
      estado = await alterarEnvioWhatsApp(ligar);
      toast.success(ligar ? "Envio de mensagens ligado." : "Envio de mensagens desligado.");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Não foi possível alterar o envio.");
    } finally {
      alterandoEnvio = false;
    }
  }

  async function rodarLembretes() {
    if (
      !confirm(
        "Rodar agora o lote de lembretes?\n\nSerão avisados os pacientes com atendimento daqui a 3 dias. Quem já recebeu o lembrete não recebe de novo."
      )
    )
      return;
    rodandoLote = true;
    try {
      const { enfileirados } = await executarLembretesWhatsApp();
      toast.success(
        enfileirados === 0
          ? "Nenhum lembrete novo: todos já estavam na fila ou enviados."
          : `${enfileirados} lembrete(s) colocado(s) na fila de envio.`
      );
      buscar(1);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Não foi possível rodar o lote de lembretes.");
    } finally {
      rodandoLote = false;
    }
  }

  async function reenviar(m: Mensagem) {
    const tipoReenvio = tipoParaReenvio(m);
    if (!tipoReenvio) return;
    const oQue = tipoReenvio === "LEMBRETE" ? "o lembrete" : "a confirmação";
    if (
      !confirm(
        `Reenviar ${oQue} do agendamento nº ${m.agendamentoId}?\n\nO paciente receberá uma nova mensagem, com os dados atuais do agendamento.`
      )
    )
      return;
    reenviandoId = m.id;
    try {
      await reenviarMensagemWhatsApp(m.agendamentoId, tipoReenvio);
      toast.success("Mensagem colocada na fila de envio.");
      buscar(1);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Não foi possível reenviar a mensagem.");
    } finally {
      reenviandoId = null;
    }
  }

  function limparFiltros() {
    dataDe = dataLocal(-6);
    dataAte = dataLocal();
    tipo = "";
    resultado = "";
    buscar(1);
  }

  onMount(() => {
    carregarEstado();
    buscar(1);
  });
</script>

<svelte:head>
  <title>WhatsApp</title>
</svelte:head>

{#snippet situacaoDaMensagem(m: Mensagem)}
  <span class={`inline-block text-xs font-semibold px-2 py-0.5 rounded border ${classeResultado(m.resultado)}`}>
    {rotuloResultado(m.resultado)}
  </span>
  {#if m.motivo}
    <span class="block text-xs text-gray-700 mt-1">{rotuloMotivo(m.motivo)}</span>
  {/if}
  {#if m.erroCodigo}
    <span class="block text-xs text-gray-700 mt-1">código {m.erroCodigo}</span>
  {/if}
{/snippet}

{#snippet entregaDaMensagem(m: Mensagem)}
  {#if m.lidoEm}
    lida em {formatarDataHora(m.lidoEm)}
  {:else if m.entregueEm}
    entregue em {formatarDataHora(m.entregueEm)}
  {:else}
    —
  {/if}
{/snippet}

{#snippet linkDaFicha(m: Mensagem)}
  {#if m.solicitacaoId}
    <a
      href={`/paciente/${m.solicitacaoId}`}
      class="inline-block py-1 text-emerald-800 font-medium underline decoration-emerald-300 underline-offset-2 rounded hover:decoration-emerald-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600"
    >
      Abrir ficha
    </a>
  {:else}
    <span class="text-gray-600">Sem ficha</span>
  {/if}
{/snippet}

{#snippet botaoReenviar(m: Mensagem, larguraTotal: boolean)}
  <button
    type="button"
    onclick={() => reenviar(m)}
    disabled={!podeDisparar || reenviandoId === m.id}
    aria-busy={reenviandoId === m.id}
    aria-label={`Reenviar a mensagem do agendamento nº ${m.agendamentoId}`}
    title={podeDisparar ? undefined : "Disponível só com o envio ligado"}
    class={`px-3 py-1.5 text-xs font-medium rounded-lg border border-gray-300 bg-white text-gray-800 hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:opacity-50 disabled:cursor-not-allowed transition ${
      larguraTotal ? "min-h-9" : ""
    }`}
  >
    {reenviandoId === m.id ? "Enviando..." : "Reenviar"}
  </button>
{/snippet}

<div class="flex min-h-screen bg-gray-100">
  <RoleBasedMenu activePage="/admin/whatsapp" />

  <div class="flex-1 flex flex-col min-w-0">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">WhatsApp</h1>
      <UserMenu />
    </header>

    <main class="flex-1 overflow-auto p-4 md:p-6 space-y-6">
      <!-- Estado e chave do envio -->
      <section class="bg-white rounded-lg shadow p-4 md:p-6 space-y-4" aria-label="Estado da integração">
        <h2 class="text-xs font-semibold text-gray-700 uppercase tracking-widest">Envio de mensagens</h2>

        {#if erroEstado}
          <div
            class="bg-red-50 border-l-4 border-red-500 rounded-r-lg p-4 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 text-red-800"
            role="alert"
          >
            <p><strong>Erro ao carregar o estado do envio:</strong> {erroEstado}</p>
            <button
              type="button"
              onclick={carregarEstado}
              class="shrink-0 self-start sm:self-auto px-3 py-1.5 text-sm font-medium rounded-lg border border-red-300 bg-white text-red-800 hover:bg-red-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600 transition"
            >
              Tentar novamente
            </button>
          </div>
        {/if}
        <!-- Erro ao ATUALIZAR nao esconde a chave: num incidente o admin precisa do botao de desligar. -->
        {#if !estado}
          {#if !erroEstado}
            <LoadingSpinner mensagem="Carregando o estado da integração..." />
          {/if}
        {:else}
          <!-- Situacao atual + a chave. A cor reforca, mas icone e texto dizem o estado sozinhos. -->
          <div
            class={`rounded-lg border border-l-4 p-4 flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4 ${
              !estado.configurado
                ? "bg-gray-50 border-gray-300 border-l-gray-500"
                : estado.envioLigado
                  ? "bg-emerald-50 border-emerald-200 border-l-emerald-600"
                  : "bg-amber-50 border-amber-200 border-l-amber-500"
            }`}
          >
            <div class="flex items-start gap-3 min-w-0">
              <svg
                class={`w-7 h-7 shrink-0 ${
                  !estado.configurado ? "text-gray-600" : estado.envioLigado ? "text-emerald-700" : "text-amber-700"
                }`}
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                viewBox="0 0 24 24"
                aria-hidden="true"
              >
                <circle cx="12" cy="12" r="9" />
                {#if !estado.configurado}
                  <path stroke-linecap="round" stroke-linejoin="round" d="M5.6 5.6l12.8 12.8" />
                {:else if estado.envioLigado}
                  <path stroke-linecap="round" stroke-linejoin="round" d="M8 12.5l2.5 2.5L16 9.5" />
                {:else}
                  <path stroke-linecap="round" stroke-linejoin="round" d="M10 9v6m4-6v6" />
                {/if}
              </svg>
              <div class="space-y-1 min-w-0">
                <p class="text-lg font-bold text-gray-900 leading-7">
                  {#if !estado.configurado}
                    Não configurado nesta instância
                  {:else if estado.envioLigado}
                    Envio ligado
                  {:else}
                    Envio desligado
                  {/if}
                </p>
                <p class="text-sm text-gray-800">
                  {#if !estado.configurado}
                    Faltam as credenciais de envio no servidor. Nenhuma mensagem é enviada e nada é registrado.
                  {:else if estado.envioLigado}
                    Agendamentos novos enviam confirmação; remarcações e cancelamentos avisam o paciente; o lote de
                    lembretes roda todo dia às 8h.
                  {:else}
                    Nenhuma mensagem é enviada. Os agendamentos continuam funcionando normalmente.
                  {/if}
                </p>
                {#if estado.alteradoEm}
                  <p class="text-xs text-gray-700">
                    Última alteração: {formatarDataHora(estado.alteradoEm)}{estado.alteradoPorNome
                      ? `, por ${estado.alteradoPorNome}`
                      : ""}
                  </p>
                {/if}
              </div>
            </div>

            <button
              type="button"
              onclick={alternarEnvio}
              disabled={!estado.configurado || alterandoEnvio}
              aria-busy={alterandoEnvio}
              class={`inline-flex items-center justify-center gap-2 w-full sm:w-auto sm:self-start lg:self-auto shrink-0 px-5 py-2.5 text-sm font-semibold rounded-lg text-white focus-visible:outline-2 focus-visible:outline-offset-2 disabled:opacity-50 disabled:cursor-not-allowed transition ${
                estado.envioLigado
                  ? "bg-red-700 hover:bg-red-800 focus-visible:outline-red-700"
                  : "bg-emerald-700 hover:bg-emerald-800 focus-visible:outline-emerald-700"
              }`}
            >
              <svg
                class="w-4 h-4 shrink-0"
                fill="none"
                stroke="currentColor"
                stroke-width="2.5"
                viewBox="0 0 24 24"
                aria-hidden="true"
              >
                <path stroke-linecap="round" stroke-linejoin="round" d="M12 3v9m5.7-5.7a8 8 0 1 1-11.4 0" />
              </svg>
              {#if alterandoEnvio}
                Salvando...
              {:else if estado.envioLigado}
                Desligar envio
              {:else}
                Ligar envio
              {/if}
            </button>
          </div>

          <!-- Lote de lembretes: bloco proprio, longe da chave, para nao confundir as duas acoes. -->
          <div
            class="border border-gray-200 rounded-lg p-4 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3"
          >
            <div class="space-y-1 min-w-0">
              <h3 class="text-sm font-semibold text-gray-900">Lote de lembretes</h3>
              <p class="text-sm text-gray-700">
                Avisa os pacientes com atendimento daqui a 3 dias. Roda sozinho todo dia às 8h; use o botão para
                rodar fora desse horário.
              </p>
              {#if !podeDisparar}
                <p id="wpp-lembretes-dica" class="text-xs font-medium text-gray-700">
                  Disponível só com o envio ligado.
                </p>
              {/if}
            </div>
            <button
              type="button"
              onclick={rodarLembretes}
              disabled={!podeDisparar || rodandoLote}
              aria-busy={rodandoLote}
              aria-describedby={!podeDisparar ? "wpp-lembretes-dica" : undefined}
              class="w-full sm:w-auto shrink-0 px-4 py-2 text-sm font-medium rounded-lg border border-gray-400 bg-white text-gray-800 hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:opacity-50 disabled:cursor-not-allowed transition"
            >
              {rodandoLote ? "Rodando..." : "Rodar lembretes agora"}
            </button>
          </div>

          <dl class="grid grid-cols-1 sm:grid-cols-3 gap-x-6 gap-y-3 text-sm border-t border-gray-200 pt-4">
            <div>
              <dt class="font-semibold text-gray-600">Recebimento (webhook)</dt>
              <dd class="text-gray-900">{estado.webhookLigado ? "Ligado" : "Desligado nesta instância"}</dd>
            </div>
            <div>
              <dt class="font-semibold text-gray-600">Enviadas hoje</dt>
              <dd class="text-gray-900 tabular-nums">{estado.enviadasHoje} de {estado.limiteDiario} (limite diário)</dd>
            </div>
            <div>
              <dt class="font-semibold text-gray-600">Modo de teste</dt>
              <dd class="text-gray-900">
                {estado.modoTeste ? "Ativo: só os números de teste recebem" : "Inativo: sem lista de números de teste"}
              </dd>
            </div>
          </dl>

          {#if estado.configurado && estado.modoTeste}
            <p
              class="flex items-start gap-2 text-sm text-amber-900 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2"
              role="status"
            >
              <svg
                class="w-5 h-5 text-amber-700 shrink-0"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                viewBox="0 0 24 24"
                aria-hidden="true"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  d="M12 9v4m0 4h.01M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"
                />
              </svg>
              <span>
                Há uma lista de números de teste configurada no servidor. Mensagens para os demais pacientes ficam
                registradas como "Fora da lista de teste" e não são enviadas.
              </span>
            </p>
          {/if}
        {/if}
      </section>

      <!-- Filtros -->
      <section class="bg-white rounded-lg shadow p-4 md:p-6 space-y-4" aria-label="Filtros">
        <div class="flex flex-wrap items-center justify-between gap-3">
          <h2 class="text-xs font-semibold text-gray-700 uppercase tracking-widest">Período e filtros</h2>
          <div class="flex flex-wrap gap-2">
            <button
              type="button"
              onclick={atualizar}
              class="text-sm font-medium px-3 py-1.5 rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 transition"
            >
              Atualizar
            </button>
            <button
              type="button"
              onclick={limparFiltros}
              class="text-sm font-medium px-3 py-1.5 rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 transition"
            >
              Limpar filtros
            </button>
          </div>
        </div>

        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
          <div>
            <label for="wpp-de" class="block text-sm font-semibold text-gray-700 mb-1">De</label>
            <input
              id="wpp-de"
              type="date"
              bind:value={dataDe}
              onchange={() => buscar(1)}
              aria-invalid={periodoInvalido}
              aria-describedby={periodoInvalido ? "wpp-periodo-erro" : undefined}
              class={`w-full border rounded-lg p-2 bg-white text-gray-900 focus:outline-hidden focus:ring-2 ${
                periodoInvalido
                  ? "border-red-500 focus:ring-red-500 focus:border-red-500"
                  : "border-gray-300 focus:ring-emerald-500 focus:border-emerald-500"
              }`}
            />
          </div>
          <div>
            <label for="wpp-ate" class="block text-sm font-semibold text-gray-700 mb-1">Até</label>
            <input
              id="wpp-ate"
              type="date"
              bind:value={dataAte}
              onchange={() => buscar(1)}
              aria-invalid={periodoInvalido}
              aria-describedby={periodoInvalido ? "wpp-periodo-erro" : undefined}
              class={`w-full border rounded-lg p-2 bg-white text-gray-900 focus:outline-hidden focus:ring-2 ${
                periodoInvalido
                  ? "border-red-500 focus:ring-red-500 focus:border-red-500"
                  : "border-gray-300 focus:ring-emerald-500 focus:border-emerald-500"
              }`}
            />
          </div>
          <div>
            <label for="wpp-tipo" class="block text-sm font-semibold text-gray-700 mb-1">Tipo</label>
            <select
              id="wpp-tipo"
              bind:value={tipo}
              onchange={() => buscar(1)}
              class="w-full border border-gray-300 rounded-lg p-2 bg-white text-gray-900 focus:outline-hidden focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500"
            >
              <option value="">Todos</option>
              {#each TIPOS as t (t.valor)}
                <option value={t.valor}>{t.rotulo}</option>
              {/each}
            </select>
          </div>
          <div>
            <label for="wpp-resultado" class="block text-sm font-semibold text-gray-700 mb-1">Situação</label>
            <select
              id="wpp-resultado"
              bind:value={resultado}
              onchange={() => buscar(1)}
              class="w-full border border-gray-300 rounded-lg p-2 bg-white text-gray-900 focus:outline-hidden focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500"
            >
              <option value="">Todas</option>
              {#each RESULTADOS as r (r.valor)}
                <option value={r.valor}>{r.rotulo}</option>
              {/each}
            </select>
          </div>
        </div>
        {#if periodoInvalido}
          <p id="wpp-periodo-erro" class="text-sm font-medium text-red-700" role="alert">
            A data inicial não pode ser depois da data final.
          </p>
        {:else}
          <p class="text-xs text-gray-600">
            Tipo e situação filtram só a lista de mensagens. O volume considera todas as mensagens do período.
          </p>
        {/if}
      </section>

      <!-- Volume -->
      <section class="bg-white rounded-lg shadow p-4 md:p-6 space-y-4" aria-label="Volume de mensagens">
        <div class="flex flex-wrap items-baseline justify-between gap-2">
          <h2 class="text-xs font-semibold text-gray-700 uppercase tracking-widest">Volume no período</h2>
          {#if indicadores}
            <p class="text-sm text-gray-600 tabular-nums">
              {formatarData(indicadores.de)} a {formatarData(indicadores.ate)}
            </p>
          {/if}
        </div>

        {#if indicadores}
          {#if erro}
            <p class="text-sm text-amber-900 bg-amber-50 border border-amber-200 rounded-lg px-3 py-2" role="status">
              Não foi possível atualizar. Os números abaixo são da última consulta que funcionou.
            </p>
          {/if}

          <div class="space-y-4 transition-opacity" class:opacity-60={carregando} aria-busy={carregando}>
            <dl class="grid grid-cols-2 sm:grid-cols-4 xl:grid-cols-8 gap-3">
              {#each [{ rotulo: "Registradas", valor: indicadores.total, tom: "" }, { rotulo: "Enviadas", valor: indicadores.enviadas, tom: "" }, { rotulo: "Entregues", valor: indicadores.entregues, tom: "" }, { rotulo: "Lidas", valor: indicadores.lidas, tom: "" }, { rotulo: "Falhas", valor: indicadores.falhas, tom: indicadores.falhas > 0 ? "erro" : "" }, { rotulo: "Não enviadas", valor: indicadores.naoEnviadas, tom: indicadores.naoEnviadas > 0 ? "alerta" : "" }, { rotulo: "Na fila", valor: indicadores.pendentes, tom: "" }, { rotulo: "Cobráveis", valor: indicadores.cobraveis, tom: "" }] as card (card.rotulo)}
                <div
                  class={`border rounded-lg p-3 ${
                    card.tom === "erro"
                      ? "border-red-300 bg-red-50"
                      : card.tom === "alerta"
                        ? "border-amber-300 bg-amber-50"
                        : "border-gray-200"
                  }`}
                >
                  <dt class="text-xs font-semibold text-gray-700 uppercase tracking-wide">{card.rotulo}</dt>
                  <dd
                    class={`text-2xl font-bold tabular-nums ${
                      card.tom === "erro" ? "text-red-800" : card.tom === "alerta" ? "text-amber-900" : "text-gray-900"
                    }`}
                  >
                    {card.valor}
                  </dd>
                </div>
              {/each}
            </dl>

            <div class="grid grid-cols-1 lg:grid-cols-3 gap-3 text-sm">
              <div class="border border-gray-200 rounded-lg p-4">
                <h3 class="font-semibold text-gray-900 mb-2">Não enviadas, por motivo</h3>
                {#if naoEnviadasPorMotivo.length === 0}
                  <p class="text-gray-600">Nenhuma no período.</p>
                {:else}
                  <ul class="divide-y divide-gray-100">
                    {#each naoEnviadasPorMotivo as [motivo, quantidade] (motivo)}
                      <li class="flex justify-between gap-3 py-1.5">
                        <span class="text-gray-800">{rotuloMotivo(motivo)}</span>
                        <span class="font-semibold text-gray-900 tabular-nums">{quantidade}</span>
                      </li>
                    {/each}
                  </ul>
                {/if}
              </div>
              <div class="border border-gray-200 rounded-lg p-4">
                <h3 class="font-semibold text-gray-900 mb-2">Cobráveis, por categoria</h3>
                {#if cobraveisPorCategoria.length === 0}
                  <p class="text-gray-600">Nenhuma informada pela Meta no período.</p>
                {:else}
                  <ul class="divide-y divide-gray-100">
                    {#each cobraveisPorCategoria as [categoria, quantidade] (categoria)}
                      <li class="flex justify-between gap-3 py-1.5">
                        <span class="text-gray-800 break-words min-w-0">{categoria}</span>
                        <span class="font-semibold text-gray-900 tabular-nums">{quantidade}</span>
                      </li>
                    {/each}
                  </ul>
                {/if}
                <p class="text-xs text-gray-600 mt-2">
                  O valor cobrado não aparece aqui: consulte a fatura no painel da Meta.
                </p>
              </div>
              <div class="border border-gray-200 rounded-lg p-4">
                <h3 class="font-semibold text-gray-900 mb-2">Recebidas de pacientes</h3>
                <p class="text-2xl font-bold text-gray-900 tabular-nums">{indicadores.recebidas}</p>
                <p class="text-xs text-gray-600 mt-1">
                  Contagem aproximada. O texto das respostas não é guardado nem lido pelo sistema.
                </p>
              </div>
            </div>
          </div>
        {:else if erro}
          <p class="text-sm text-gray-700 py-4">
            Volume indisponível: a consulta falhou. Use "Tentar novamente" na lista de mensagens, logo abaixo.
          </p>
        {:else}
          <LoadingSpinner mensagem="Carregando o volume..." />
        {/if}
      </section>

      <!-- Lista de operacao -->
      <section class="bg-white rounded-lg shadow-lg p-4 md:p-6 space-y-4" aria-label="Mensagens">
        <h2 class="text-2xl font-bold text-emerald-800">Mensagens</h2>

        {#if cargaInicial && carregando}
          <LoadingSpinner mensagem="Carregando as mensagens..." />
        {:else if erro}
          <div
            class="bg-red-50 border-l-4 border-red-500 rounded-r-lg p-4 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 text-red-800"
            role="alert"
          >
            <p><strong>Erro ao carregar:</strong> {erro}</p>
            <button
              type="button"
              onclick={() => buscar(paginaAtual)}
              class="shrink-0 self-start sm:self-auto px-3 py-1.5 text-sm font-medium rounded-lg border border-red-300 bg-white text-red-800 hover:bg-red-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600 transition"
            >
              Tentar novamente
            </button>
          </div>
        {:else}
          <div class="flex flex-wrap items-center gap-x-3 gap-y-1 min-h-6">
            <p class="text-gray-700" aria-live="polite">
              <strong class="text-gray-900">{totalElements}</strong>
              {totalElements === 1 ? "mensagem" : "mensagens"}
            </p>
            {#if carregando}<LoadingSpinner tamanho={14} inline mensagem="Atualizando..." />{/if}
          </div>

          {#if totalElements === 0}
            <div class="flex flex-col items-center text-center gap-3 py-12 px-4">
              <svg
                class="w-10 h-10 text-gray-400"
                fill="none"
                stroke="currentColor"
                stroke-width="1.5"
                viewBox="0 0 24 24"
                aria-hidden="true"
              >
                <path
                  stroke-linecap="round"
                  stroke-linejoin="round"
                  d="M8 10h8M8 14h5m8-2a9 9 0 0 1-13.4 7.9L3 21l1.2-4.4A9 9 0 1 1 21 12z"
                />
              </svg>
              <p class="text-gray-700 font-medium">
                Nenhuma mensagem registrada para o período e os filtros escolhidos.
              </p>
              {#if tipo || resultado}
                <button
                  type="button"
                  onclick={limparFiltros}
                  class="px-4 py-2 text-sm font-medium rounded-lg bg-emerald-600 hover:bg-emerald-800 text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 transition"
                >
                  Limpar filtros
                </button>
              {/if}
            </div>
          {:else}
            {#if estado && !podeDisparar}
              <p class="flex items-start gap-2 text-xs text-gray-700 bg-gray-50 border border-gray-200 rounded-lg px-3 py-2">
                <svg
                  class="w-4 h-4 text-gray-500 shrink-0 mt-px"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <circle cx="12" cy="12" r="9" />
                  <path stroke-linecap="round" stroke-linejoin="round" d="M12 11v5m0-8h.01" />
                </svg>
                <span>O reenvio fica disponível só com o envio ligado.</span>
              </p>
            {/if}

            <!-- Telas estreitas: um cartao por mensagem, sem rolagem lateral. -->
            <ul class="lg:hidden space-y-3 transition-opacity" class:opacity-60={carregando} aria-busy={carregando}>
              {#each mensagens as m (m.id)}
                {@const tipoReenvio = tipoParaReenvio(m)}
                <li class="border border-gray-200 rounded-lg p-3 space-y-3 text-sm">
                  <div class="flex items-start justify-between gap-3">
                    <div class="min-w-0">
                      <p class="font-semibold text-gray-900">
                        {rotuloTipo(m.tipo)}
                        {#if m.origem === "MANUAL"}
                          <span class="font-normal text-xs text-gray-600">· disparo manual</span>
                        {/if}
                      </p>
                      <p class="text-xs text-gray-600 tabular-nums">Registrada em {formatarDataHora(m.criadoEm)}</p>
                    </div>
                    <div class="text-right shrink-0 max-w-[55%]">
                      {@render situacaoDaMensagem(m)}
                    </div>
                  </div>

                  <dl class="grid grid-cols-2 gap-x-4 gap-y-2">
                    <div>
                      <dt class="text-xs font-semibold text-gray-600">Atendimento</dt>
                      <dd class="text-gray-900 tabular-nums">
                        {formatarData(m.dataReferencia)}
                        <span class="block text-xs text-gray-600">agendamento nº {m.agendamentoId}</span>
                      </dd>
                    </div>
                    <div>
                      <dt class="text-xs font-semibold text-gray-600">Telefone</dt>
                      <dd class="text-gray-900 tabular-nums">{m.telefoneFinal ? `final ${m.telefoneFinal}` : "—"}</dd>
                    </div>
                    <div class="col-span-2">
                      <dt class="text-xs font-semibold text-gray-600">Entregue / lida</dt>
                      <dd class="text-gray-900 tabular-nums">{@render entregaDaMensagem(m)}</dd>
                    </div>
                  </dl>

                  <div class="flex items-center justify-between gap-3 pt-2 border-t border-gray-100">
                    {@render linkDaFicha(m)}
                    {#if tipoReenvio}
                      {@render botaoReenviar(m, true)}
                    {/if}
                  </div>
                </li>
              {/each}
            </ul>

            <!-- Telas largas: tabela. -->
            <div
              class="hidden lg:block overflow-x-auto transition-opacity"
              class:opacity-60={carregando}
              aria-busy={carregando}
            >
              <table class="min-w-full text-sm">
                <caption class="sr-only">Mensagens registradas no período, da mais recente para a mais antiga</caption>
                <thead>
                  <tr
                    class="text-left text-xs font-semibold text-gray-700 uppercase tracking-wide bg-gray-50 border-b border-gray-200"
                  >
                    <th scope="col" class="py-2 px-3">Registrada em</th>
                    <th scope="col" class="py-2 px-3">Tipo</th>
                    <th scope="col" class="py-2 px-3">Atendimento</th>
                    <th scope="col" class="py-2 px-3">Paciente</th>
                    <th scope="col" class="py-2 px-3">Telefone</th>
                    <th scope="col" class="py-2 px-3">Situação</th>
                    <th scope="col" class="py-2 px-3">Entregue / lida</th>
                    <th scope="col" class="py-2 px-3 text-right">Ação</th>
                  </tr>
                </thead>
                <tbody>
                  {#each mensagens as m (m.id)}
                    {@const tipoReenvio = tipoParaReenvio(m)}
                    <tr class="border-b border-gray-100 align-top hover:bg-gray-50">
                      <td class="py-2 px-3 whitespace-nowrap tabular-nums">{formatarDataHora(m.criadoEm)}</td>
                      <td class="py-2 px-3">
                        <span class="font-medium text-gray-900">{rotuloTipo(m.tipo)}</span>
                        {#if m.origem === "MANUAL"}
                          <span class="block text-xs text-gray-600">disparo manual</span>
                        {/if}
                      </td>
                      <td class="py-2 px-3 whitespace-nowrap tabular-nums">
                        {formatarData(m.dataReferencia)}
                        <span class="block text-xs text-gray-600">agendamento nº {m.agendamentoId}</span>
                      </td>
                      <td class="py-1 px-3 whitespace-nowrap">{@render linkDaFicha(m)}</td>
                      <td class="py-2 px-3 whitespace-nowrap tabular-nums">
                        {m.telefoneFinal ? `final ${m.telefoneFinal}` : "—"}
                      </td>
                      <td class="py-2 px-3">{@render situacaoDaMensagem(m)}</td>
                      <td class="py-2 px-3 whitespace-nowrap tabular-nums">{@render entregaDaMensagem(m)}</td>
                      <td class="py-2 px-3 text-right whitespace-nowrap">
                        {#if tipoReenvio}
                          {@render botaoReenviar(m, false)}
                        {:else}
                          <span class="sr-only">Sem reenvio para este tipo de mensagem</span>
                        {/if}
                      </td>
                    </tr>
                  {/each}
                </tbody>
              </table>
            </div>

            {#if totalPages > 1}
              <nav class="flex justify-center items-center gap-3 pt-2" aria-label="Paginação das mensagens">
                <button
                  type="button"
                  onclick={() => buscar(paginaAtual - 1)}
                  disabled={paginaAtual <= 1 || carregando}
                  class="px-3 py-1.5 text-sm font-medium rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:opacity-50 disabled:cursor-not-allowed transition"
                >
                  &laquo; Anterior
                </button>
                <p class="text-sm text-gray-700 tabular-nums">Página {paginaAtual} de {totalPages}</p>
                <button
                  type="button"
                  onclick={() => buscar(paginaAtual + 1)}
                  disabled={paginaAtual >= totalPages || carregando}
                  class="px-3 py-1.5 text-sm font-medium rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:opacity-50 disabled:cursor-not-allowed transition"
                >
                  Próxima &raquo;
                </button>
              </nav>
            {/if}
          {/if}
        {/if}
      </section>
    </main>
  </div>
</div>
