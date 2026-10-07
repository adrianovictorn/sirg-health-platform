<script lang="ts">
  // Indicadores gerenciais (fila e operação, cotas, custos e WhatsApp) — só ADMIN e GESTOR.
  //
  // Quem decide o acesso é o backend (IndicadoresGerenciaisController e
  // CustoIndicadoresController respondem 403 aos demais perfis); a página só monta este
  // componente para esses dois perfis para não disparar chamadas que seriam negadas.
  //
  // Cada seção carrega sozinha: uma que falha ou demora não segura as outras.
  import { onMount } from "svelte";
  import { getApi } from "$lib/api";
  import LoadingSpinner from "$lib/LoadingSpinner.svelte";
  import ChartSeries from "$lib/components/ChartSeries.svelte";
  import {
    carregarEnvelhecimentoFila,
    carregarBalancoFila,
    carregarAntecedencia,
    carregarUtilizacaoCotas,
    carregarOcupacaoProfissional,
    carregarAlcanceWhatsApp,
    carregarTelefoneInvalido
  } from "$lib/indicadoresApi.js";
  import {
    carregarExecucaoTeto,
    carregarCustoFaltas,
    carregarCoberturaPreco,
    carregarEvolucaoCusto,
    formatarReais
  } from "$lib/custosApi.js";
  import {
    percentual,
    textoPercentual,
    percentualDoTeto,
    situacaoDoTeto,
    rotuloMes,
    rotuloMotivo,
    rotuloPrioridade,
    textoDias
  } from "$lib/indicadores.js";

  type Carga = { carregando: boolean; erro: string };
  type Faixas = { id: number | null; nome: string | null; ate30: number; de31a60: number; de61a90: number; mais90: number; total: number };

  // --- Filtros: os campos são o que o operador digita; `filtro` é o que está aplicado.
  let unidades = $state<{ id: number; nome: string }[]>([]);
  let campoDe = $state("");
  let campoAte = $state("");
  let campoUnidade = $state("");
  let filtro = $state({ de: "", ate: "", unidadeId: "" });

  let fila = $state<Carga & { envelhecimento: any; antecedencia: any; balanco: any }>({
    carregando: true, erro: "", envelhecimento: null, antecedencia: null, balanco: null
  });
  let cotas = $state<Carga & { utilizacao: any; ocupacao: any }>({
    carregando: true, erro: "", utilizacao: null, ocupacao: null
  });
  let custos = $state<Carga & { teto: any; faltas: any; cobertura: any; evolucao: any }>({
    carregando: true, erro: "", teto: null, faltas: null, cobertura: null, evolucao: null
  });
  let whatsapp = $state<Carga & { alcance: any; telefone: any }>({
    carregando: true, erro: "", alcance: null, telefone: null
  });

  // Resposta de um filtro antigo que chega depois da de um novo é descartada.
  const rodadas: Record<string, number> = {};

  async function carregar(chave: string, alvo: Carga & Record<string, any>, buscas: Record<string, () => Promise<any>>) {
    const rodada = (rodadas[chave] = (rodadas[chave] ?? 0) + 1);
    alvo.carregando = true;
    alvo.erro = "";
    const nomes = Object.keys(buscas);
    // Limpa antes de buscar: número do filtro anterior não pode ficar na tela sob o
    // rótulo do filtro novo — nem durante a recarga, nem se a busca falhar.
    nomes.forEach((nome) => { alvo[nome] = null; });
    try {
      const resultados = await Promise.all(nomes.map((nome) => buscas[nome]()));
      if (rodada !== rodadas[chave]) return;
      nomes.forEach((nome, i) => { alvo[nome] = resultados[i]; });
    } catch (e) {
      if (rodada === rodadas[chave]) {
        alvo.erro = e instanceof Error ? e.message : "Não foi possível carregar estes indicadores.";
      }
    } finally {
      if (rodada === rodadas[chave]) alvo.carregando = false;
    }
  }

  function carregarTudo() {
    const f = { unidadeId: filtro.unidadeId, de: filtro.de, ate: filtro.ate };
    carregar("fila", fila, {
      envelhecimento: () => carregarEnvelhecimentoFila(f),
      antecedencia: () => carregarAntecedencia(f),
      balanco: () => carregarBalancoFila(f)
    });
    carregar("cotas", cotas, {
      utilizacao: () => carregarUtilizacaoCotas(f),
      ocupacao: () => carregarOcupacaoProfissional(f)
    });
    carregar("custos", custos, {
      teto: () => carregarExecucaoTeto(f),
      faltas: () => carregarCustoFaltas(f),
      cobertura: () => carregarCoberturaPreco(f),
      evolucao: () => carregarEvolucaoCusto(f)
    });
    carregar("whatsapp", whatsapp, {
      alcance: () => carregarAlcanceWhatsApp(f),
      telefone: () => carregarTelefoneInvalido(f)
    });
  }

  function aplicarFiltros(event: SubmitEvent) {
    event.preventDefault();
    filtro = { de: campoDe, ate: campoAte, unidadeId: campoUnidade };
    carregarTudo();
  }

  function limparFiltros() {
    campoDe = "";
    campoAte = "";
    campoUnidade = "";
    filtro = { de: "", ate: "", unidadeId: "" };
    carregarTudo();
  }

  onMount(async () => {
    carregarTudo();
    try {
      const res = await getApi("unidades/ativas");
      unidades = res.ok ? await res.json() : [];
    } catch {
      unidades = [];
    }
  });

  // --- Apresentação
  const CAMPO = "w-full border border-gray-300 rounded-lg p-2 bg-white text-gray-900 focus:outline-hidden focus:ring-2 focus:ring-emerald-500 focus:border-emerald-500";
  const ROTULO = "block text-sm font-semibold text-gray-700 mb-1";
  const BOTAO_PRIMARIO = "inline-flex items-center justify-center min-h-10 px-4 py-2 rounded-lg bg-emerald-700 text-white text-sm font-semibold hover:bg-emerald-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:opacity-60 disabled:cursor-not-allowed transition-colors cursor-pointer";
  const BOTAO_SECUNDARIO = "inline-flex items-center justify-center min-h-10 px-4 py-2 rounded-lg border border-gray-400 bg-white text-gray-800 text-sm font-semibold hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 transition-colors cursor-pointer";
  const CARTAO = "bg-white rounded-lg shadow p-4 md:p-6 space-y-4";
  const TH = "p-2 text-left text-xs font-semibold uppercase tracking-wide text-gray-700";
  const TH_NUM = "p-2 text-right text-xs font-semibold uppercase tracking-wide text-gray-700";
  const TD = "p-2 text-gray-900 break-words";
  const TD_NUM = "p-2 text-right text-gray-900 tabular-nums";

  const COR = { fila: "#d97706", agendado: "#0284c7", concluido: "#047857", falta: "#be123c", liberado: "#9ca3af" };

  const numero = (n: number | null | undefined) => (n === null || n === undefined ? "—" : Number(n).toLocaleString("pt-BR"));

  function dataBr(iso: string | null | undefined): string {
    if (!iso) return "";
    const [ano, mes, dia] = iso.split("-");
    return `${dia}/${mes}/${ano}`;
  }

  const periodoDe = (dados: any) => (dados?.de ? `${dataBr(dados.de)} a ${dataBr(dados.ate)}` : "");

  const unidadeAplicada = $derived(
    filtro.unidadeId ? unidades.find((u) => String(u.id) === String(filtro.unidadeId))?.nome ?? "unidade selecionada" : "Todas as unidades"
  );

  // --- Derivados para os gráficos e resumos
  const balancoMeses = $derived<any[]>(fila.balanco?.meses ?? []);
  const evolucaoMeses = $derived<any[]>(custos.evolucao?.meses ?? []);
  const tetoSerie = $derived<any[]>(custos.teto?.serie ?? []);
  const mesCorrente = $derived(evolucaoMeses.length > 0 ? evolucaoMeses[evolucaoMeses.length - 1] : null);
  const tetosEmAtencao = $derived<any[]>((custos.teto?.tetos ?? []).filter((t: any) => situacaoDoTeto(t) !== "NORMAL"));
  const temMovimentoDeCusto = $derived(
    evolucaoMeses.some((m) => Number(m.agendado) > 0 || Number(m.concluido) > 0 || Number(m.faltas) > 0)
  );
  const naoEnviados = $derived<[string, number][]>(
    Object.entries((whatsapp.alcance?.naoEnviadosPorMotivo ?? {}) as Record<string, number>).sort((a, b) => b[1] - a[1])
  );

  const ROTULO_TIPO_COTA: Record<string, string> = { MENSAL: "Cotas mensais", DATA: "Cotas por dia" };
  const SITUACAO_TETO: Record<string, { texto: string; classes: string }> = {
    ACIMA: { texto: "Acima do teto", classes: "bg-red-100 text-red-900 border-red-300" },
    ESGOTADO: { texto: "Esgotado", classes: "bg-red-100 text-red-900 border-red-300" },
    ATENCAO: { texto: "80% ou mais", classes: "bg-amber-100 text-amber-900 border-amber-300" },
    NORMAL: { texto: "Dentro do teto", classes: "bg-gray-100 text-gray-800 border-gray-300" }
  };
</script>

{#snippet cabecalhoSecao(id: string, titulo: string, descricao: string)}
  <div class="flex flex-wrap items-baseline gap-x-3 gap-y-1">
    <h2 id={`${id}-titulo`} class="text-xl font-bold text-gray-900">{titulo}</h2>
    <p class="text-sm text-gray-700">{descricao}</p>
  </div>
{/snippet}

{#snippet numeroChave(rotulo: string, valor: string, detalhe: string, borda: string)}
  <article class={`rounded-lg p-5 border-l-4 bg-white shadow ${borda}`}>
    <h4 class="text-sm font-semibold text-gray-700">{rotulo}</h4>
    <p class="text-3xl font-bold tracking-tight text-gray-900 mt-1 tabular-nums break-words">{valor}</p>
    <p class="text-sm text-gray-700 mt-2">{detalhe}</p>
  </article>
{/snippet}

{#snippet aviso(texto: string)}
  <p class="text-sm text-amber-900 bg-amber-50 border border-amber-300 rounded-lg px-3 py-2">
    <span class="font-semibold">Como ler:</span> {texto}
  </p>
{/snippet}

{#snippet vazio(mensagem: string)}
  <p class="text-sm text-gray-700 bg-gray-50 border border-dashed border-gray-300 rounded-lg px-4 py-6 text-center">
    {mensagem}
  </p>
{/snippet}

{#snippet estado(carga: Carga, temDados: boolean)}
  {#if carga.erro}
    <p role="alert" class="text-sm text-red-900 bg-red-50 border border-red-300 rounded-lg px-4 py-3">
      {carga.erro}
    </p>
  {:else if carga.carregando && !temDados}
    <div class="bg-white rounded-lg shadow p-6">
      <LoadingSpinner tamanho={24} inline mensagem="Carregando indicadores..." />
    </div>
  {/if}
{/snippet}

<!-- Faixas de espera em tabela: uma linha por unidade, especialidade ou prioridade. -->
{#snippet tabelaFaixas(legenda: string, coluna: string, linhas: Faixas[], nomeDe: (l: Faixas) => string)}
  <div class="overflow-x-auto">
    <table class="w-full text-sm">
      <caption class="sr-only">{legenda}</caption>
      <thead>
        <tr class="bg-gray-50 border-b border-gray-200">
          <th scope="col" class={TH}>{coluna}</th>
          <th scope="col" class={TH_NUM}>Até 30 dias</th>
          <th scope="col" class={TH_NUM}>31 a 60</th>
          <th scope="col" class={TH_NUM}>61 a 90</th>
          <th scope="col" class={TH_NUM}>Mais de 90</th>
          <th scope="col" class={TH_NUM}>Total</th>
        </tr>
      </thead>
      <tbody>
        {#each linhas as linha, i (i)}
          <tr class="border-b border-gray-100">
            <th scope="row" class={`${TD} font-medium text-left`}>{nomeDe(linha)}</th>
            <td class={TD_NUM}>{numero(linha.ate30)}</td>
            <td class={TD_NUM}>{numero(linha.de31a60)}</td>
            <td class={TD_NUM}>{numero(linha.de61a90)}</td>
            <td class={`${TD_NUM} font-bold ${linha.mais90 > 0 ? "text-red-800" : ""}`}>{numero(linha.mais90)}</td>
            <td class={`${TD_NUM} font-bold`}>{numero(linha.total)}</td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
{/snippet}

<!-- Barra de proporção com o número ao lado: não depende só de cor. -->
{#snippet barra(rotulo: string, parte: number, total: number, cor: string)}
  {@const pct = percentual(parte, total)}
  <div class="grid grid-cols-[minmax(0,1fr)_auto] items-baseline gap-x-3 gap-y-1 text-sm">
    <span class="text-gray-900">{rotulo}</span>
    <span class="font-bold text-gray-900 tabular-nums">
      {textoPercentual(pct)}
      <span class="font-normal text-gray-700">({numero(parte)} de {numero(total)})</span>
    </span>
    <span class="col-span-2 block h-2 rounded-full bg-gray-200 overflow-hidden" aria-hidden="true">
      <span class={`block h-full rounded-full ${cor}`} style={`width: ${Math.min(pct ?? 0, 100)}%`}></span>
    </span>
  </div>
{/snippet}

<!-- ═══════════════════════════════════════════════════════════
     FILTROS DOS INDICADORES GERENCIAIS
════════════════════════════════════════════════════════════════ -->
<section id="gestao" aria-labelledby="gestao-titulo" class="space-y-4 scroll-mt-4">
  {@render cabecalhoSecao("gestao", "Indicadores de gestão", "Visíveis só para administrador e gestor. Somente números agregados, sem dado de paciente.")}
  <form class={CARTAO} onsubmit={aplicarFiltros}>
    <div class="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-[1fr_1fr_2fr_auto] gap-4 items-end">
      <div>
        <label class={ROTULO} for="gestao-de">Data inicial</label>
        <input id="gestao-de" type="date" bind:value={campoDe} class={CAMPO} />
      </div>
      <div>
        <label class={ROTULO} for="gestao-ate">Data final</label>
        <input id="gestao-ate" type="date" bind:value={campoAte} class={CAMPO} />
      </div>
      <div>
        <label class={ROTULO} for="gestao-unidade">Unidade</label>
        <select id="gestao-unidade" bind:value={campoUnidade} class={CAMPO}>
          <option value="">Todas as unidades</option>
          {#each unidades as u (u.id)}
            <option value={String(u.id)}>{u.nome}</option>
          {/each}
        </select>
      </div>
      <div class="flex flex-wrap gap-2">
        <button type="submit" class={BOTAO_PRIMARIO}>Aplicar filtros</button>
        <button type="button" class={BOTAO_SECUNDARIO} onclick={limparFiltros}>Limpar</button>
      </div>
    </div>
    <p class="text-sm text-gray-700">
      Sem datas, o período é o dos últimos 30 dias (no máximo 366 dias). A fila de hoje e os gráficos
      de 12 meses não usam o período, só a unidade. Mostrando: <strong>{unidadeAplicada}</strong>.
    </p>
  </form>
</section>

<!-- ═══════════════════════════════════════════════════════════
     FILA E OPERAÇÃO
════════════════════════════════════════════════════════════════ -->
<section id="gestao-fila" aria-labelledby="gestao-fila-titulo" class="space-y-4 scroll-mt-4" aria-busy={fila.carregando}>
  {@render cabecalhoSecao("gestao-fila", "Fila e operação", "Há quanto tempo se espera, com quanta folga se marca e se a fila cresce ou diminui.")}
  {@render estado(fila, fila.envelhecimento !== null)}

  {#if fila.envelhecimento}
    {@const e = fila.envelhecimento}
    <article class={CARTAO}>
      <h3 class="text-lg font-semibold text-gray-900">Envelhecimento da fila — hoje</h3>
      {#if e.total.total === 0}
        {@render vazio("Ninguém aguardando agendamento.")}
      {:else}
        <div class="grid grid-cols-2 xl:grid-cols-4 gap-4">
          {@render numeroChave("Até 30 dias", numero(e.total.ate30), "pacientes", "border-l-emerald-600")}
          {@render numeroChave("31 a 60 dias", numero(e.total.de31a60), "pacientes", "border-l-amber-400")}
          {@render numeroChave("61 a 90 dias", numero(e.total.de61a90), "pacientes", "border-l-amber-600")}
          {@render numeroChave("Mais de 90 dias", numero(e.total.mais90), `de ${numero(e.total.total)} pacientes na fila`, "border-l-red-600")}
        </div>
        {@render aviso("O paciente entra na faixa do seu pedido mais antigo, como na tela da fila de espera. Pedidos cadastrados antes da adoção da data de cadastro aparecem com espera menor que a real, e retorno conta desde o pedido original.")}

        <div class="grid grid-cols-1 xl:grid-cols-2 gap-6 items-start">
          <div class="space-y-2">
            <h4 class="text-sm font-semibold text-gray-800">Por unidade (pacientes)</h4>
            {@render tabelaFaixas("Pacientes na fila por faixa de espera e unidade", "Unidade", e.porUnidade, (l) => l.nome ?? "Sem unidade")}
          </div>
          <div class="space-y-2">
            <h4 class="text-sm font-semibold text-gray-800">Por prioridade (pedidos)</h4>
            {@render tabelaFaixas("Pedidos na fila por faixa de espera e prioridade", "Prioridade", e.porPrioridade, (l) => rotuloPrioridade(l.nome))}
          </div>
        </div>
        <div class="space-y-2">
          <h4 class="text-sm font-semibold text-gray-800">
            Por especialidade (pedidos) — as {Math.min(e.porEspecialidade.length, 15)} com mais espera acima de 90 dias
          </h4>
          {@render tabelaFaixas("Pedidos na fila por faixa de espera e especialidade", "Especialidade", e.porEspecialidade.slice(0, 15), (l) => l.nome ?? "")}
        </div>
      {/if}
    </article>
  {/if}

  <div class="grid grid-cols-1 xl:grid-cols-2 gap-4 items-start">
    {#if fila.antecedencia}
      {@const a = fila.antecedencia}
      {@const mediveis = a.total - a.semDataCriacao - a.retroativos}
      <article class={CARTAO}>
        <div>
          <h3 class="text-lg font-semibold text-gray-900">Antecedência do agendamento</h3>
          <p class="text-sm text-gray-700">Atendimentos marcados para {periodoDe(a)}.</p>
        </div>
        {#if a.total === 0}
          {@render vazio("Nenhum agendamento com data marcada no período.")}
        {:else}
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {@render numeroChave("Antecedência média", textoDias(a.mediaDias), "entre marcar e a data do atendimento", "border-l-sky-500")}
            {@render numeroChave("Mediana", textoDias(a.medianaDias), "metade dos agendamentos fica abaixo disso", "border-l-sky-500")}
          </div>
          {#if mediveis > 0}
            <div class="space-y-3">
              {@render barra("No dia ou na véspera (0 a 1 dia)", a.ate1, mediveis, "bg-amber-500")}
              {@render barra("2 a 7 dias", a.de2a7, mediveis, "bg-sky-600")}
              {@render barra("8 a 15 dias", a.de8a15, mediveis, "bg-sky-600")}
              {@render barra("16 a 30 dias", a.de16a30, mediveis, "bg-sky-600")}
              {@render barra("Mais de 30 dias", a.mais30, mediveis, "bg-sky-800")}
            </div>
          {/if}
          {@render aviso(`Mede a folga da marcação, não a espera do paciente. Agendamento remarcado conta desde a remarcação. Ficaram fora da conta ${numero(a.semDataCriacao)} sem data de criação registrada e ${numero(a.retroativos)} lançados depois da data do atendimento.`)}
        {/if}
      </article>
    {/if}

    {#if fila.balanco}
      <article class={CARTAO}>
        <div>
          <h3 class="text-lg font-semibold text-gray-900">Balanço da fila — últimos 12 meses</h3>
          <p class="text-sm text-gray-700">Em pedidos (consultas e exames), não em pacientes.</p>
        </div>
        {#if balancoMeses.every((m) => m.novos === 0 && m.agendados === 0)}
          {@render vazio("Nenhum pedido nem agendamento nos últimos 12 meses.")}
        {:else}
          <div role="img" aria-label="Gráfico de barras com pedidos novos, agendados e concluídos por mês. Os valores estão na tabela abaixo.">
            <ChartSeries
              rotulos={balancoMeses.map((m) => rotuloMes(m.mes))}
              series={[
                { rotulo: "Novos", valores: balancoMeses.map((m) => m.novos), cor: COR.fila },
                { rotulo: "Agendados", valores: balancoMeses.map((m) => m.agendados), cor: COR.agendado },
                { rotulo: "Concluídos", valores: balancoMeses.map((m) => m.concluidos), cor: COR.concluido }
              ]}
            />
          </div>
          <div class="overflow-x-auto">
            <table class="w-full text-sm">
              <caption class="sr-only">Pedidos novos, agendados e concluídos por mês</caption>
              <thead>
                <tr class="bg-gray-50 border-b border-gray-200">
                  <th scope="col" class={TH}>Mês</th>
                  <th scope="col" class={TH_NUM}>Novos</th>
                  <th scope="col" class={TH_NUM}>Agendados</th>
                  <th scope="col" class={TH_NUM}>Concluídos</th>
                </tr>
              </thead>
              <tbody>
                {#each balancoMeses as m (m.mes)}
                  <tr class="border-b border-gray-100">
                    <th scope="row" class={`${TD} font-medium text-left`}>{rotuloMes(m.mes)}</th>
                    <td class={TD_NUM}>{numero(m.novos)}</td>
                    <td class={TD_NUM}>{numero(m.agendados)}</td>
                    <td class={TD_NUM}>{numero(m.concluidos)}</td>
                  </tr>
                {/each}
              </tbody>
            </table>
          </div>
          {@render aviso("É uma fotografia do que existe hoje, agrupada por data: novos pelo mês do cadastro; agendados e concluídos pelo mês da data marcada. “Agendados” são todos os pedidos com agendamento, inclusive os que terminaram em falta ou cancelamento. Pedido removido e agendamento excluído ou remarcado deixam de contar no mês original.")}
        {/if}
      </article>
    {/if}
  </div>
</section>

<!-- ═══════════════════════════════════════════════════════════
     COTAS
════════════════════════════════════════════════════════════════ -->
<section id="gestao-cotas" aria-labelledby="gestao-cotas-titulo" class="space-y-4 scroll-mt-4" aria-busy={cotas.carregando}>
  {@render cabecalhoSecao("gestao-cotas", "Cotas", "Onde sobra e onde falta vaga.")}
  {@render estado(cotas, cotas.utilizacao !== null)}

  {#if cotas.utilizacao}
    {@const u = cotas.utilizacao}
    <article class={CARTAO}>
      <div>
        <h3 class="text-lg font-semibold text-gray-900">Utilização das cotas</h3>
        <p class="text-sm text-gray-700">Cotas ativas de {periodoDe(u)}.</p>
      </div>
      {#if u.porTipo.length === 0}
        {@render vazio("Nenhuma cota ativa no período.")}
      {:else}
        <div class="grid grid-cols-1 xl:grid-cols-2 gap-4">
          {#each u.porTipo as tipo (tipo.tipo)}
            <div class="rounded-lg border border-gray-200 p-4 space-y-3">
              <h4 class="text-sm font-semibold text-gray-800">{ROTULO_TIPO_COTA[tipo.tipo] ?? tipo.tipo}</h4>
              <div class="grid grid-cols-2 gap-3">
                {@render numeroChave("Utilização média", tipo.utilizacaoMedia === null ? "—" : `${Math.round(tipo.utilizacaoMedia * 100)}%`, `de ${numero(tipo.cotas)} cotas`, "border-l-emerald-600")}
                {@render numeroChave("Esgotadas", numero(tipo.esgotadas), "sem nenhuma vaga", "border-l-red-600")}
              </div>
              {@render barra("Ociosas (período encerrado sem nenhum uso)", tipo.ociosas, tipo.cotas, "bg-amber-500")}
            </div>
          {/each}
        </div>
        <div class="overflow-x-auto">
          <table class="w-full text-sm">
            <caption class="sr-only">Utilização das cotas por tipo e titular</caption>
            <thead>
              <tr class="bg-gray-50 border-b border-gray-200">
                <th scope="col" class={TH}>Titular</th>
                <th scope="col" class={TH}>Tipo</th>
                <th scope="col" class={TH_NUM}>Cotas</th>
                <th scope="col" class={TH_NUM}>Utilização média</th>
                <th scope="col" class={TH_NUM}>Esgotadas</th>
                <th scope="col" class={TH_NUM}>Ociosas</th>
              </tr>
            </thead>
            <tbody>
              {#each u.porTitular as linha, i (i)}
                <tr class="border-b border-gray-100">
                  <th scope="row" class={`${TD} font-medium text-left`}>
                    {linha.titular ?? "—"}
                    {#if linha.unidadeId === null}<span class="font-normal text-gray-700"> (grupo de unidades)</span>{/if}
                  </th>
                  <td class={TD}>{linha.tipo === "MENSAL" ? "Mensal" : "Por dia"}</td>
                  <td class={TD_NUM}>{numero(linha.cotas)}</td>
                  <td class={`${TD_NUM} font-bold`}>{linha.utilizacaoMedia === null ? "—" : `${Math.round(linha.utilizacaoMedia * 100)}%`}</td>
                  <td class={`${TD_NUM} ${linha.esgotadas > 0 ? "font-bold text-red-800" : ""}`}>{numero(linha.esgotadas)}</td>
                  <td class={TD_NUM}>{numero(linha.ociosas)}</td>
                </tr>
              {/each}
            </tbody>
          </table>
        </div>
        {@render aviso("A utilização é a média de cada cota, nunca a soma: a cota mensal e a do dia valem juntas para o mesmo agendamento, e somá-las contaria a mesma vaga duas vezes. A cota mensal entra pelo mês inteiro, mesmo quando o período pega só parte dele. Agendamento feito por administrador ou gestor não consome cota e não aparece aqui.")}
      {/if}
    </article>
  {/if}

  {#if cotas.ocupacao}
    {@const o = cotas.ocupacao}
    <article class={CARTAO}>
      <div>
        <h3 class="text-lg font-semibold text-gray-900">Ocupação por profissional e horário</h3>
        <p class="text-sm text-gray-700">Cotas com profissional definido, de {periodoDe(o)}.</p>
      </div>
      {#if o.porProfissional.length === 0}
        {@render vazio("Nenhuma cota com profissional definido no período.")}
      {:else}
        <div class="grid grid-cols-1 xl:grid-cols-[2fr_1fr] gap-6 items-start">
          <div class="overflow-x-auto">
            <table class="w-full text-sm">
              <caption class="sr-only">Vagas ofertadas e agendadas por profissional</caption>
              <thead>
                <tr class="bg-gray-50 border-b border-gray-200">
                  <th scope="col" class={TH}>Profissional</th>
                  <th scope="col" class={TH_NUM}>Ofertadas</th>
                  <th scope="col" class={TH_NUM}>Agendadas</th>
                  <th scope="col" class={TH_NUM}>Livres</th>
                  <th scope="col" class={TH_NUM}>Ocupação</th>
                </tr>
              </thead>
              <tbody>
                {#each o.porProfissional as linha (linha.id)}
                  <tr class="border-b border-gray-100">
                    <th scope="row" class={`${TD} font-medium text-left`}>{linha.nome}</th>
                    <td class={TD_NUM}>{numero(linha.ofertadas)}</td>
                    <td class={TD_NUM}>{numero(linha.agendadas)}</td>
                    <td class={TD_NUM}>{numero(linha.ofertadas - linha.agendadas)}</td>
                    <td class={`${TD_NUM} font-bold`}>{textoPercentual(percentual(linha.agendadas, linha.ofertadas))}</td>
                  </tr>
                {/each}
              </tbody>
            </table>
          </div>
          <div class="space-y-3">
            <h4 class="text-sm font-semibold text-gray-800">Por faixa de horário</h4>
            {#if o.porHorario.length === 0}
              <p class="text-sm text-gray-700">As cotas do período não têm horário definido.</p>
            {:else}
              {#each o.porHorario as linha (linha.nome)}
                {@render barra(linha.nome, linha.agendadas, linha.ofertadas, "bg-sky-600")}
              {/each}
            {/if}
          </div>
        </div>
        {@render aviso("O profissional é o da cota. Quando o operador troca o profissional em um agendamento específico, a vaga continua contada na cota de origem. Cota mensal entra com todas as vagas do mês, inclusive as de dias fora do período ou ainda por vir — por isso “Livres” pode incluir vagas futuras.")}
      {/if}
    </article>
  {/if}
</section>

<!-- ═══════════════════════════════════════════════════════════
     CUSTOS
════════════════════════════════════════════════════════════════ -->
<section id="gestao-custos" aria-labelledby="gestao-custos-titulo" class="space-y-4 scroll-mt-4" aria-busy={custos.carregando}>
  <div class="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
    {@render cabecalhoSecao("gestao-custos", "Custos", "Teto financeiro, faltas e quanto do movimento tem preço.")}
    <a href="/custos" class="text-sm font-semibold text-emerald-800 underline hover:text-emerald-950 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600">
      Ver painel de custos completo
    </a>
  </div>
  {@render estado(custos, custos.cobertura !== null)}

  {#if custos.cobertura}
    {@const c = custos.cobertura}
    <article class={CARTAO}>
      <div>
        <h3 class="text-lg font-semibold text-gray-900">Cobertura de preço</h3>
        <p class="text-sm text-gray-700">Quanto os totais em reais abaixo conseguem enxergar.</p>
      </div>
      <div class="grid grid-cols-1 xl:grid-cols-3 gap-6">
        {@render barra("Especialidades ativas com preço (município)", c.especialidadesAtivas - c.especialidadesSemPreco, c.especialidadesAtivas, "bg-emerald-600")}
        {@render barra("Pedidos na fila hoje com preço", c.filaComPreco, c.filaComPreco + c.filaSemPreco, "bg-emerald-600")}
        {@render barra(`Itens agendados com valor gravado (${periodoDe(c)})`, c.agendadosComValor, c.agendadosComValor + c.agendadosSemValor, "bg-emerald-600")}
      </div>
      {@render aviso("O que não tem preço fica fora dos totais; nunca entra como R$ 0,00. Item agendado sem valor pode ser de especialidade sem preço na época ou de agendamento anterior à gravação do valor.")}
    </article>
  {/if}

  {#if custos.teto}
    {@const t = custos.teto}
    <article class={CARTAO}>
      <div>
        <h3 class="text-lg font-semibold text-gray-900">Execução do teto financeiro</h3>
        <p class="text-sm text-gray-700">Tetos ativos dos meses de {periodoDe(t)}.</p>
      </div>
      {#if t.tetos.length === 0}
        {@render vazio("Nenhum teto financeiro ativo nos meses do período. Unidade sem teto não tem limite de valor.")}
      {:else}
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {@render numeroChave("Tetos ativos", numero(t.tetos.length), "nos meses do período", "border-l-emerald-600")}
          {@render numeroChave("Em 80% ou mais", numero(tetosEmAtencao.length), "inclui esgotados e acima do teto", tetosEmAtencao.length > 0 ? "border-l-red-600" : "border-l-gray-300")}
        </div>
        <div class="overflow-x-auto">
          <table class="w-full text-sm">
            <caption class="sr-only">Execução do teto financeiro por unidade, grupo e mês</caption>
            <thead>
              <tr class="bg-gray-50 border-b border-gray-200">
                <th scope="col" class={TH}>Unidade</th>
                <th scope="col" class={TH}>Grupo</th>
                <th scope="col" class={TH}>Mês</th>
                <th scope="col" class={TH_NUM}>Liberado</th>
                <th scope="col" class={TH_NUM}>Utilizado</th>
                <th scope="col" class={TH_NUM}>Uso</th>
                <th scope="col" class={TH}>Situação</th>
              </tr>
            </thead>
            <tbody>
              {#each t.tetos as teto, i (i)}
                {@const situacao = SITUACAO_TETO[situacaoDoTeto(teto)]}
                <tr class="border-b border-gray-100">
                  <th scope="row" class={`${TD} font-medium text-left`}>{teto.unidadeNome}</th>
                  <td class={TD}>{teto.grupoNome}</td>
                  <td class={TD}>{rotuloMes(teto.periodo)}</td>
                  <td class={TD_NUM}>{formatarReais(teto.valorTotal)}</td>
                  <td class={TD_NUM}>{formatarReais(teto.valorUtilizado)}</td>
                  <td class={`${TD_NUM} font-bold`}>{textoPercentual(percentualDoTeto(teto))}</td>
                  <td class={TD}>
                    <span class={`inline-block px-2 py-0.5 rounded-full border text-xs font-semibold ${situacao.classes}`}>{situacao.texto}</span>
                  </td>
                </tr>
              {/each}
            </tbody>
          </table>
        </div>
      {/if}
      {#if tetoSerie.some((m) => m.tetos > 0)}
        <h4 class="text-sm font-semibold text-gray-800">Liberado e utilizado — últimos 12 meses</h4>
        <div role="img" aria-label="Gráfico de barras com o valor liberado e o utilizado dos tetos ativos por mês.">
          <ChartSeries
            rotulos={tetoSerie.map((m) => rotuloMes(m.mes))}
            series={[
              { rotulo: "Liberado", valores: tetoSerie.map((m) => Number(m.liberado)), cor: COR.liberado },
              { rotulo: "Utilizado", valores: tetoSerie.map((m) => Number(m.utilizado)), cor: COR.concluido }
            ]}
            formatar={formatarReais}
          />
        </div>
      {/if}
      {@render aviso("O utilizado só conta o que foi agendado depois de o teto existir, e pode passar do liberado: agendamento de administrador ou gestor debita sem ser barrado.")}
    </article>
  {/if}

  <div class="grid grid-cols-1 xl:grid-cols-2 gap-4 items-start">
    {#if custos.faltas}
      {@const f = custos.faltas}
      <article class={CARTAO}>
        <div>
          <h3 class="text-lg font-semibold text-gray-900">Custo de faltas e cancelamentos</h3>
          <p class="text-sm text-gray-700">Atendimentos marcados para {periodoDe(f)}.</p>
        </div>
        {#if f.total.itensComValor + f.total.itensSemValor === 0}
          {@render vazio("Nenhuma falta ou cancelamento de atendimento agendado no período.")}
        {:else}
          {@render numeroChave("Valor não realizado", formatarReais(f.total.valor), `${numero(f.total.itensComValor)} itens com valor; ${numero(f.total.itensSemValor)} sem valor gravado, fora da soma`, "border-l-red-600")}
          <div class="overflow-x-auto">
            <table class="w-full text-sm">
              <caption class="sr-only">Custo de faltas e cancelamentos por especialidade</caption>
              <thead>
                <tr class="bg-gray-50 border-b border-gray-200">
                  <th scope="col" class={TH}>Especialidade</th>
                  <th scope="col" class={TH_NUM}>Itens</th>
                  <th scope="col" class={TH_NUM}>Valor</th>
                </tr>
              </thead>
              <tbody>
                {#each f.porEspecialidade.slice(0, 10) as linha (linha.id)}
                  <tr class="border-b border-gray-100">
                    <th scope="row" class={`${TD} font-medium text-left`}>{linha.nome}</th>
                    <td class={TD_NUM}>{numero(linha.itensComValor + linha.itensSemValor)}</td>
                    <td class={`${TD_NUM} font-bold`}>{linha.itensComValor > 0 ? formatarReais(linha.valor) : "sem valor"}</td>
                  </tr>
                {/each}
              </tbody>
            </table>
          </div>
          {#if f.porUnidade.length > 1}
            <div class="overflow-x-auto">
              <table class="w-full text-sm">
                <caption class="sr-only">Custo de faltas e cancelamentos por unidade</caption>
                <thead>
                  <tr class="bg-gray-50 border-b border-gray-200">
                    <th scope="col" class={TH}>Unidade</th>
                    <th scope="col" class={TH_NUM}>Itens</th>
                    <th scope="col" class={TH_NUM}>Valor</th>
                  </tr>
                </thead>
                <tbody>
                  {#each f.porUnidade as linha, i (i)}
                    <tr class="border-b border-gray-100">
                      <th scope="row" class={`${TD} font-medium text-left`}>{linha.nome ?? "Sem unidade"}</th>
                      <td class={TD_NUM}>{numero(linha.itensComValor + linha.itensSemValor)}</td>
                      <td class={`${TD_NUM} font-bold`}>{linha.itensComValor > 0 ? formatarReais(linha.valor) : "sem valor"}</td>
                    </tr>
                  {/each}
                </tbody>
              </table>
            </div>
          {/if}
        {/if}
        {@render aviso("O sistema registra a falta como cancelamento: os dois não se distinguem aqui. Conta o item cancelado que manteve o agendamento; o que voltou para a fila (agendamento excluído ou remarcado) não entra.")}
      </article>
    {/if}

    {#if custos.evolucao}
      <article class={CARTAO}>
        <div>
          <h3 class="text-lg font-semibold text-gray-900">Evolução do custo — últimos 12 meses</h3>
          <p class="text-sm text-gray-700">Pelo mês da data marcada e pelo valor gravado no agendamento.</p>
        </div>
        {#if !temMovimentoDeCusto}
          {@render vazio("Nenhum item agendado com valor nos últimos 12 meses. Cadastre os preços das especialidades para o custo aparecer.")}
        {:else}
          {#if mesCorrente}
            {@render numeroChave(`Custo médio por paciente atendido — ${rotuloMes(mesCorrente.mes)}`, formatarReais(mesCorrente.custoMedioPorPaciente), `${numero(mesCorrente.pacientesAtendidos)} pacientes com atendimento concluído no mês`, "border-l-emerald-600")}
          {/if}
          <div role="img" aria-label="Gráfico de barras com o custo ainda agendado, o concluído e o de faltas e cancelamentos por mês.">
            <ChartSeries
              rotulos={evolucaoMeses.map((m) => rotuloMes(m.mes))}
              series={[
                { rotulo: "Ainda agendado", valores: evolucaoMeses.map((m) => Number(m.agendado)), cor: COR.agendado },
                { rotulo: "Concluído", valores: evolucaoMeses.map((m) => Number(m.concluido)), cor: COR.concluido },
                { rotulo: "Faltas e cancelamentos", valores: evolucaoMeses.map((m) => Number(m.faltas)), cor: COR.falta }
              ]}
              formatar={formatarReais}
            />
          </div>
          {@render aviso("“Ainda agendado” em mês passado encolhe conforme os itens viram concluídos, faltas ou cancelamentos. Agendamentos anteriores à gravação do valor ficam fora, então os primeiros meses começam baixos.")}
        {/if}
      </article>
    {/if}
  </div>
</section>

<!-- ═══════════════════════════════════════════════════════════
     WHATSAPP
════════════════════════════════════════════════════════════════ -->
<section id="gestao-whatsapp" aria-labelledby="gestao-whatsapp-titulo" class="space-y-4 scroll-mt-4" aria-busy={whatsapp.carregando}>
  {@render cabecalhoSecao("gestao-whatsapp", "WhatsApp", "Quantos pacientes são avisados do agendamento e por que os outros não.")}
  {@render estado(whatsapp, whatsapp.alcance !== null)}

  {#if whatsapp.alcance}
    {@const w = whatsapp.alcance}
    {#if !w.configurado}
      <p class="text-sm text-gray-900 bg-white border border-gray-300 rounded-lg px-4 py-3">
        <strong>WhatsApp não configurado nesta instância.</strong> Nenhuma mensagem é registrada, então não há alcance a medir.
      </p>
    {:else if !w.envioLigado}
      <p class="text-sm text-amber-900 bg-amber-50 border border-amber-300 rounded-lg px-4 py-3">
        <strong>O envio está desligado.</strong> As mensagens são registradas como não enviadas e o telefone dos pacientes não é avaliado.
      </p>
    {/if}

    <div class="grid grid-cols-1 xl:grid-cols-2 gap-4 items-start">
      <article class={CARTAO}>
        <div>
          <h3 class="text-lg font-semibold text-gray-900">Alcance do aviso de agendamento</h3>
          <p class="text-sm text-gray-700">Agendamentos com aviso registrado de {periodoDe(w)}.</p>
        </div>
        {#if w.agendamentos === 0}
          {@render vazio("Nenhum aviso de agendamento registrado no período.")}
        {:else}
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {@render numeroChave("Receberam o aviso", textoPercentual(percentual(w.alcancados, w.agendamentos)), `${numero(w.alcancados)} de ${numero(w.agendamentos)} agendamentos`, "border-l-emerald-600")}
            {@render numeroChave("Leram", textoPercentual(percentual(w.lidos, w.agendamentos)), `${numero(w.lidos)} agendamentos`, "border-l-sky-500")}
          </div>
          <div class="space-y-3">
            <h4 class="text-sm font-semibold text-gray-800">Os que não receberam</h4>
            {@render barra("Aceita pelo WhatsApp, sem confirmação de entrega", w.aceitasSemConfirmacao, w.agendamentos, "bg-sky-600")}
            {@render barra("Ainda na fila de envio", w.pendentes, w.agendamentos, "bg-gray-500")}
            {@render barra("Falha no envio", w.falhas, w.agendamentos, "bg-red-600")}
            {#each naoEnviados as [motivo, total] (motivo)}
              {@render barra(`Não enviada — ${rotuloMotivo(motivo)}`, total, w.agendamentos, "bg-amber-500")}
            {/each}
          </div>
        {/if}
        {@render aviso("Conta agendamentos, não mensagens: o reenvio de um aviso não conta de novo. Remarcar cria outro agendamento, então o original e o novo contam os dois — o original costuma aparecer como “Agendamento removido antes do envio” ou como já avisado. Agendamento feito enquanto o WhatsApp não estava configurado não tem registro e fica fora.")}
      </article>

      {#if whatsapp.telefone}
        {@const tel = whatsapp.telefone}
        <article class={CARTAO}>
          <div>
            <h3 class="text-lg font-semibold text-gray-900">Telefone inválido ou ausente</h3>
            <p class="text-sm text-gray-700">Pacientes com mensagem registrada de {periodoDe(tel)}.</p>
          </div>
          {#if tel.total.avaliados === 0}
            {@render vazio("Telefone não avaliado no período: nenhuma mensagem chegou à etapa de envio. Isso não significa que os telefones estão corretos.")}
          {:else}
            {@render numeroChave("Com telefone inválido ou sem telefone", textoPercentual(percentual(tel.total.invalidos, tel.total.avaliados)), `${numero(tel.total.invalidos)} de ${numero(tel.total.avaliados)} pacientes avaliados`, tel.total.invalidos > 0 ? "border-l-red-600" : "border-l-emerald-600")}
            <div class="overflow-x-auto">
              <table class="w-full text-sm">
                <caption class="sr-only">Pacientes com telefone inválido por unidade</caption>
                <thead>
                  <tr class="bg-gray-50 border-b border-gray-200">
                    <th scope="col" class={TH}>Unidade</th>
                    <th scope="col" class={TH_NUM}>Avaliados</th>
                    <th scope="col" class={TH_NUM}>Inválidos</th>
                    <th scope="col" class={TH_NUM}>%</th>
                  </tr>
                </thead>
                <tbody>
                  {#each tel.porUnidade as linha, i (i)}
                    <tr class="border-b border-gray-100">
                      <th scope="row" class={`${TD} font-medium text-left`}>{linha.nome ?? "Sem unidade"}</th>
                      <td class={TD_NUM}>{numero(linha.avaliados)}</td>
                      <td class={`${TD_NUM} ${linha.invalidos > 0 ? "font-bold text-red-800" : ""}`}>{numero(linha.invalidos)}</td>
                      <td class={`${TD_NUM} font-bold`}>{textoPercentual(percentual(linha.invalidos, linha.avaliados))}</td>
                    </tr>
                  {/each}
                </tbody>
              </table>
            </div>
          {/if}
          {@render aviso("Só entram pacientes cujo telefone chegou a ser avaliado. Mensagem barrada antes disso (envio desligado, paciente que pediu para não receber) não diz nada sobre o telefone. “Inválido” é o número sem DDD ou fora do formato; número bem formado que o WhatsApp recusa aparece como falha de envio, não aqui. A tela mostra só contagens, nunca os números.")}
        </article>
      {/if}
    </div>
  {/if}
</section>
