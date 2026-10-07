<script lang="ts">
  import { onMount } from "svelte";
  import UserMenu from "$lib/UserMenu.svelte";
  import RoleBasedMenu from "$lib/RoleBasedMenu.svelte";
  import LoadingSpinner from "$lib/LoadingSpinner.svelte";
  import { listarEspecialidadesCatalogo } from "$lib/especialidadesApi.js";
  import { previaImportacaoCustos, confirmarImportacaoCustos, formatarReais } from "$lib/custosApi.js";

  type Situacao =
    | "NOVO_VALOR"
    | "NOVO_CODIGO"
    | "VALOR_IGUAL"
    | "VALOR_DIFERENTE"
    | "AMBIGUA"
    | "SEM_CORRESPONDENCIA"
    | "INVALIDA";

  type LinhaPrevia = {
    linha: number;
    codigoSus: string | null;
    procedimento: string | null;
    valorUnitario: number | null;
    especialidadeId: number | null;
    especialidadeNome: string | null;
    criterio: "CODIGO_SUS" | "NOME" | null;
    codigoSusAtual: string | null;
    valorAtual: number | null;
    valorOrigemAtual: "MANUAL" | "IMPORTACAO" | null;
    situacao: Situacao;
    detalhe: string;
  };

  type Previa = {
    arquivo: string;
    totalLinhas: number;
    prontas: number;
    iguais: number;
    diferentes: number;
    pendentes: number;
    invalidas: number;
    linhas: LinhaPrevia[];
    avisos: string[];
  };

  type Resultado = { gravadas: number; inalteradas: number; ignoradas: number; avisos: string[] };

  // Estado de conferência de cada linha da prévia (mesmo índice de previa.linhas).
  type Conferencia = { marcada: boolean; destinoNome: string };

  type EspecialidadeCatalogo = { id: number; nome: string };

  const SITUACOES: { valor: Situacao; rotulo: string; classe: string }[] = [
    { valor: "NOVO_VALOR", rotulo: "Novo preço", classe: "bg-emerald-100 text-emerald-900 border-emerald-300" },
    { valor: "NOVO_CODIGO", rotulo: "Novo código", classe: "bg-emerald-100 text-emerald-900 border-emerald-300" },
    { valor: "VALOR_DIFERENTE", rotulo: "Substitui o atual", classe: "bg-amber-100 text-amber-900 border-amber-300" },
    { valor: "SEM_CORRESPONDENCIA", rotulo: "Sem correspondência", classe: "bg-sky-100 text-sky-900 border-sky-300" },
    { valor: "AMBIGUA", rotulo: "Nome repetido", classe: "bg-sky-100 text-sky-900 border-sky-300" },
    { valor: "VALOR_IGUAL", rotulo: "Já gravado", classe: "bg-gray-100 text-gray-800 border-gray-300" },
    { valor: "INVALIDA", rotulo: "Inválida", classe: "bg-red-100 text-red-900 border-red-300" }
  ];
  const TAMANHO_MAXIMO_MB = 5;

  let arquivo = $state<File | null>(null);
  let lendo = $state(false);
  let erroLeitura = $state<string | null>(null);

  let previa = $state<Previa | null>(null);
  let conferencia = $state<Conferencia[]>([]);
  let filtroSituacao = $state<Situacao | "">("");

  let gravando = $state(false);
  let erroGravacao = $state<string | null>(null);
  let resultado = $state<Resultado | null>(null);

  let catalogo = $state<EspecialidadeCatalogo[]>([]);
  const idPorNome = $derived(new Map(catalogo.map((e) => [e.nome.trim().toLowerCase(), e.id])));

  const precisaEscolher = (l: LinhaPrevia) => l.situacao === "SEM_CORRESPONDENCIA" || l.situacao === "AMBIGUA";
  const podeMarcar = (l: LinhaPrevia) => l.situacao !== "INVALIDA" && l.situacao !== "VALOR_IGUAL";

  function destinoDe(indice: number): number | null {
    const linha = previa!.linhas[indice];
    if (!precisaEscolher(linha)) return linha.especialidadeId;
    return idPorNome.get(conferencia[indice].destinoNome.trim().toLowerCase()) ?? null;
  }

  const linhasVisiveis = $derived(
    (previa?.linhas ?? [])
      .map((linha, indice) => ({ linha, indice }))
      .filter(({ linha }) => !filtroSituacao || linha.situacao === filtroSituacao)
  );

  const contagem = $derived(
    Object.fromEntries(
      SITUACOES.map((s) => [s.valor, (previa?.linhas ?? []).filter((l) => l.situacao === s.valor).length])
    ) as Record<Situacao, number>
  );

  // Marcadas e com destino resolvido: é o que de fato será enviado.
  const prontasParaGravar = $derived(
    previa ? conferencia.filter((c, indice) => c.marcada && destinoDe(indice) !== null).length : 0
  );
  const marcadasSemDestino = $derived(
    previa ? conferencia.filter((c, indice) => c.marcada && destinoDe(indice) === null).length : 0
  );
  const substituicoesMarcadas = $derived(
    previa ? conferencia.filter((c, indice) => c.marcada && previa!.linhas[indice].situacao === "VALOR_DIFERENTE").length : 0
  );

  function aoEscolherArquivo(evento: Event) {
    const escolhido = (evento.currentTarget as HTMLInputElement).files?.[0] ?? null;
    erroLeitura = null;
    if (escolhido && escolhido.size > TAMANHO_MAXIMO_MB * 1024 * 1024) {
      arquivo = null;
      erroLeitura = `O arquivo passa de ${TAMANHO_MAXIMO_MB} MB.`;
      return;
    }
    arquivo = escolhido;
  }

  async function lerPlanilha(evento: SubmitEvent) {
    evento.preventDefault();
    if (!arquivo || lendo) return;
    lendo = true;
    erroLeitura = null;
    resultado = null;
    erroGravacao = null;
    try {
      const dados: Previa = await previaImportacaoCustos(arquivo);
      previa = dados;
      // Nasce marcado só o que grava sem substituir nada. Substituição e escolha
      // manual exigem um gesto explícito do operador.
      conferencia = dados.linhas.map((l) => ({
        marcada: l.situacao === "NOVO_VALOR" || l.situacao === "NOVO_CODIGO",
        destinoNome: ""
      }));
      filtroSituacao = "";
    } catch (e) {
      previa = null;
      erroLeitura = e instanceof Error ? e.message : "Não foi possível ler a planilha.";
    } finally {
      lendo = false;
    }
  }

  function marcarSituacao(situacao: Situacao, marcada: boolean) {
    conferencia = conferencia.map((c, indice) =>
      previa!.linhas[indice].situacao === situacao ? { ...c, marcada } : c
    );
  }

  async function gravar() {
    if (!previa || gravando || prontasParaGravar === 0) return;
    gravando = true;
    erroGravacao = null;
    try {
      const itens = previa.linhas
        .map((linha, indice) => ({ linha, indice }))
        .filter(({ indice }) => conferencia[indice].marcada && destinoDe(indice) !== null)
        .map(({ linha, indice }) => ({
          linha: linha.linha,
          especialidadeId: destinoDe(indice),
          codigoSus: linha.codigoSus,
          valorUnitario: linha.valorUnitario,
          // Marcar uma linha "Substitui o atual" É a confirmação da substituição.
          sobrescrever: linha.situacao === "VALOR_DIFERENTE"
        }));
      resultado = await confirmarImportacaoCustos(itens);
      previa = null;
      conferencia = [];
      arquivo = null;
    } catch (e) {
      erroGravacao = e instanceof Error ? e.message : "Não foi possível gravar a importação.";
    } finally {
      gravando = false;
    }
  }

  function recomecar() {
    previa = null;
    conferencia = [];
    resultado = null;
    arquivo = null;
    erroLeitura = null;
    erroGravacao = null;
  }

  const situacao = (valor: Situacao) => SITUACOES.find((s) => s.valor === valor)!;

  onMount(() => {
    // Só alimenta a escolha manual; se falhar, as linhas pendentes ficam sem destino.
    listarEspecialidadesCatalogo()
      .then((lista: EspecialidadeCatalogo[]) => (catalogo = (lista ?? []).sort((a, b) => a.nome.localeCompare(b.nome, "pt-BR"))))
      .catch(() => (catalogo = []));
  });
</script>

<svelte:head>
  <title>Importar Preços</title>
</svelte:head>

<datalist id="catalogo-especialidades">
  {#each catalogo as e (e.id)}
    <option value={e.nome}></option>
  {/each}
</datalist>

<div class="flex min-h-screen bg-gray-100">
  <RoleBasedMenu activePage="/admin/custos/importar" />

  <div class="flex-1 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Importar Preços</h1>
      <UserMenu />
    </header>

    <main class="flex-1 overflow-auto p-4 md:p-6 space-y-6">
      <ol
        class="bg-white rounded-lg shadow px-4 py-3 md:px-6 flex flex-wrap items-center gap-x-3 gap-y-2 text-sm"
        aria-label="Etapas da importação"
      >
        {#each ["Enviar a planilha", "Conferir", "Gravar"] as etapa, i (etapa)}
          {@const concluida = resultado ? true : previa ? i === 0 : false}
          {@const atual = !resultado && (previa ? i === 1 : i === 0)}
          <li class="flex items-center gap-2" aria-current={atual ? "step" : undefined}>
            {#if i > 0}
              <span class="hidden sm:block w-8 h-px bg-gray-300 mr-1" aria-hidden="true"></span>
            {/if}
            <span
              class={`inline-flex items-center justify-center w-7 h-7 shrink-0 rounded-full border text-xs font-bold tabular-nums ${
                concluida
                  ? "bg-emerald-700 border-emerald-700 text-white"
                  : atual
                    ? "bg-emerald-50 border-emerald-700 text-emerald-800"
                    : "bg-white border-gray-400 text-gray-600"
              }`}
            >
              {#if concluida}
                <svg
                class="w-4 h-4 shrink-0"
                fill="none"
                stroke="currentColor"
                stroke-width="3"
                viewBox="0 0 24 24"
                aria-hidden="true"
              >
                <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
              </svg>
              {:else}
                {i + 1}
              {/if}
            </span>
            <span class={atual ? "font-semibold text-gray-900" : concluida ? "text-gray-700" : "text-gray-600"}>
              {etapa}{#if concluida}<span class="sr-only"> (concluída)</span>{/if}
            </span>
          </li>
        {/each}
      </ol>

      <section class="bg-white rounded-lg shadow p-4 md:p-6 space-y-4" aria-label="Enviar planilha">
        <h2 class="flex items-center gap-3 text-xl font-bold text-emerald-800">
          <span
            class="inline-flex items-center justify-center w-8 h-8 shrink-0 rounded-full bg-emerald-700 text-white text-base tabular-nums"
          >1</span>
          Enviar a planilha
        </h2>
        <p class="text-sm text-gray-700">
          Arquivo <strong>.xlsx</strong> ou <strong>.csv</strong> com as colunas <strong>Código SUS</strong>,
          <strong>Procedimento</strong> e <strong>Valor Unit</strong>. Outras colunas (quantidade, valor total) são
          ignoradas. Nada é gravado nesta etapa: primeiro você confere.
        </p>
        <form onsubmit={lerPlanilha} class="flex flex-col sm:flex-row sm:items-end gap-3" novalidate>
          <div class="flex-1 min-w-0">
            <label for="importar-arquivo" class="block text-sm font-semibold text-gray-700 mb-1">Planilha de preços</label>
            <input
              id="importar-arquivo"
              type="file"
              accept=".xlsx,.csv,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
              onchange={aoEscolherArquivo}
              aria-describedby={erroLeitura ? "importar-erro" : undefined}
              class="w-full border border-gray-300 rounded-lg p-2 bg-white text-sm text-gray-900 cursor-pointer file:mr-3 file:px-3 file:py-1.5 file:rounded-lg file:border-0 file:bg-emerald-50 file:text-emerald-800 file:font-medium file:cursor-pointer hover:file:bg-emerald-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600"
            />
          </div>
          <button
            type="submit"
            disabled={!arquivo || lendo}
            class="shrink-0 whitespace-nowrap px-4 py-2 sm:py-3 text-sm font-medium rounded-lg bg-emerald-700 hover:bg-emerald-800 text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:opacity-50 disabled:cursor-not-allowed transition"
          >
            {lendo ? "Lendo..." : "Ler e conferir"}
          </button>
        </form>
        {#if lendo}<LoadingSpinner mensagem="Lendo a planilha e comparando com o cadastro..." />{/if}
        {#if erroLeitura}
          <p id="importar-erro" class="text-sm font-medium text-red-700" role="alert">{erroLeitura}</p>
        {/if}
      </section>

      {#if resultado}
        <section class="bg-white rounded-lg shadow p-4 md:p-6 space-y-4" aria-label="Resultado da importação" role="status">
          <h2 class="text-xl font-bold text-emerald-800">Importação concluída</h2>
          <div class="flex flex-wrap gap-x-10 gap-y-4">
            <div class="min-w-32">
              <p class="text-sm font-semibold text-gray-600">Gravadas</p>
              <p class="text-2xl font-bold text-emerald-800 tabular-nums">{resultado.gravadas}</p>
            </div>
            <div class="min-w-32">
              <p class="text-sm font-semibold text-gray-600">Já estavam iguais</p>
              <p class="text-2xl font-bold text-gray-900 tabular-nums">{resultado.inalteradas}</p>
            </div>
            <div class="min-w-32">
              <p class="text-sm font-semibold text-gray-600">Não gravadas</p>
              <p class={`text-2xl font-bold tabular-nums ${resultado.ignoradas > 0 ? "text-amber-800" : "text-gray-900"}`}>
                {resultado.ignoradas}
              </p>
            </div>
          </div>
          {#if resultado.avisos.length > 0}
            <ul class="list-disc text-sm text-amber-900 bg-amber-50 border border-amber-200 rounded-lg p-3 pl-8 space-y-1">
              {#each resultado.avisos as aviso (aviso)}
                <li>{aviso}</li>
              {/each}
            </ul>
          {/if}
          <div class="flex flex-wrap gap-2">
            <a
              href="/custos/especialidades"
              class="inline-flex items-center px-4 py-2 text-sm font-medium rounded-lg bg-emerald-700 hover:bg-emerald-800 text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 transition"
            >
              Ver preços cadastrados
            </a>
            <button
              type="button"
              onclick={recomecar}
              class="px-4 py-2 text-sm font-medium rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 transition"
            >
              Importar outra planilha
            </button>
          </div>
        </section>
      {/if}

      {#if previa}
        <section class="bg-white rounded-lg shadow-lg p-4 md:p-6 space-y-5" aria-label="Conferência da planilha">
          <div>
            <h2 class="flex items-center gap-3 text-xl font-bold text-emerald-800">
          <span
            class="inline-flex items-center justify-center w-8 h-8 shrink-0 rounded-full bg-emerald-700 text-white text-base tabular-nums"
          >2</span>
          Conferir
        </h2>
            <p class="text-sm text-gray-700 mt-2 break-words">
              <strong class="text-gray-900">{previa.arquivo}</strong> · {previa.totalLinhas}
              {previa.totalLinhas === 1 ? "linha lida" : "linhas lidas"}. Só as linhas marcadas serão gravadas.
            </p>
          </div>

          {#if previa.avisos.length > 0}
            <ul class="list-disc text-sm text-amber-900 bg-amber-50 border border-amber-200 rounded-lg p-3 pl-8 space-y-1">
              {#each previa.avisos as aviso (aviso)}
                <li>{aviso}</li>
              {/each}
            </ul>
          {/if}

          <fieldset>
            <legend class="text-sm font-semibold text-gray-700 mb-2">Mostrar</legend>
            <div class="flex flex-wrap gap-2">
              <button
                type="button"
                aria-pressed={filtroSituacao === ""}
                onclick={() => (filtroSituacao = "")}
                class={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-medium border transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 ${
                  filtroSituacao === ""
                    ? "bg-emerald-700 text-white border-emerald-700 hover:bg-emerald-800"
                    : "bg-white text-gray-700 border-gray-400 hover:bg-gray-50"
                }`}
              >
                {#if filtroSituacao === ""}
                  <svg
                  class="w-4 h-4 shrink-0"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="3"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
                </svg>
                {/if}
                Todas ({previa.linhas.length})
              </button>
              {#each SITUACOES.filter((s) => contagem[s.valor] > 0) as s (s.valor)}
                <button
                  type="button"
                  aria-pressed={filtroSituacao === s.valor}
                  onclick={() => (filtroSituacao = s.valor)}
                  class={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-full text-sm font-medium border transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 ${
                    filtroSituacao === s.valor
                      ? "bg-emerald-700 text-white border-emerald-700 hover:bg-emerald-800"
                      : "bg-white text-gray-700 border-gray-400 hover:bg-gray-50"
                  }`}
                >
                  {#if filtroSituacao === s.valor}
                    <svg
                      class="w-4 h-4 shrink-0"
                      fill="none"
                      stroke="currentColor"
                      stroke-width="3"
                      viewBox="0 0 24 24"
                      aria-hidden="true"
                    >
                      <path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7" />
                    </svg>
                  {/if}
                  {s.rotulo} ({contagem[s.valor]})
                </button>
              {/each}
            </div>
          </fieldset>

          {#if contagem.VALOR_DIFERENTE > 0}
            <div class="flex flex-wrap items-center gap-x-3 gap-y-2 text-sm text-amber-900 bg-amber-50 border border-amber-200 rounded-lg p-3">
              <span>
                {contagem.VALOR_DIFERENTE}
                {contagem.VALOR_DIFERENTE === 1 ? "linha substituiria" : "linhas substituiriam"} um preço ou código já
                gravado. Elas começam desmarcadas.
              </span>
              <button
                type="button"
                onclick={() => marcarSituacao("VALOR_DIFERENTE", true)}
                class="px-3 py-1.5 font-medium rounded-lg border border-amber-500 bg-white text-amber-900 hover:bg-amber-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-amber-600 transition"
              >
                Marcar todas
              </button>
              <button
                type="button"
                onclick={() => marcarSituacao("VALOR_DIFERENTE", false)}
                class="px-3 py-1.5 font-medium rounded-lg border border-amber-500 bg-white text-amber-900 hover:bg-amber-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-amber-600 transition"
              >
                Desmarcar todas
              </button>
            </div>
          {/if}

          <div class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="text-left text-gray-700 border-b border-gray-300">
                  <th scope="col" class="py-2 pr-3 font-semibold">Gravar</th>
                  <th scope="col" class="py-2 px-3 font-semibold">Linha</th>
                  <th scope="col" class="py-2 px-3 font-semibold min-w-48">Na planilha</th>
                  <th scope="col" class="py-2 px-3 font-semibold text-right whitespace-nowrap">Valor</th>
                  <th scope="col" class="py-2 px-3 font-semibold min-w-56">Especialidade no SIRG</th>
                  <th scope="col" class="py-2 px-3 font-semibold text-right whitespace-nowrap">Valor atual</th>
                  <th scope="col" class="py-2 pl-3 font-semibold min-w-48">Situação</th>
                </tr>
              </thead>
              <tbody>
                {#each linhasVisiveis as { linha, indice } (indice)}
                  {@const s = situacao(linha.situacao)}
                  <tr class="border-b border-gray-200 align-top hover:bg-gray-50">
                    <td class="py-2 pr-3">
                      {#if podeMarcar(linha)}
                        <label class="inline-flex items-center justify-center p-1 -m-1 cursor-pointer">
                          <input
                            type="checkbox"
                            bind:checked={conferencia[indice].marcada}
                            aria-label={`Gravar a linha ${linha.linha}: ${linha.procedimento ?? linha.codigoSus ?? ""}`}
                            class="w-5 h-5 rounded border-gray-400 text-emerald-700 cursor-pointer focus:ring-emerald-500"
                          />
                        </label>
                      {:else}
                        <span class="text-gray-500" aria-hidden="true">—</span>
                      {/if}
                    </td>
                    <td class="py-2 px-3 tabular-nums text-gray-700">{linha.linha}</td>
                    <td class="py-2 px-3">
                      <span class="font-medium text-gray-900">{linha.procedimento ?? "—"}</span>
                      <span class="block text-xs text-gray-600 tabular-nums">{linha.codigoSus ?? "sem código"}</span>
                    </td>
                    <td class="py-2 px-3 text-right tabular-nums whitespace-nowrap font-medium text-gray-900">
                      {formatarReais(linha.valorUnitario)}
                    </td>
                    <td class="py-2 px-3">
                      {#if precisaEscolher(linha)}
                        {@const destinoInvalido =
                          !!conferencia[indice].destinoNome.trim() && destinoDe(indice) === null}
                        <label class="sr-only" for={`destino-${indice}`}>
                          Especialidade do SIRG para a linha {linha.linha}
                        </label>
                        <input
                          id={`destino-${indice}`}
                          type="text"
                          list="catalogo-especialidades"
                          bind:value={conferencia[indice].destinoNome}
                          oninput={() => (conferencia[indice].marcada = destinoDe(indice) !== null)}
                          placeholder="Digite para escolher"
                          autocomplete="off"
                          aria-invalid={destinoInvalido}
                          aria-describedby={destinoInvalido ? `destino-erro-${indice}` : undefined}
                          class={`w-full min-w-56 border rounded-lg p-1.5 text-sm bg-white text-gray-900 ${
                            destinoInvalido
                              ? "border-red-500 focus:ring-red-500 focus:border-red-500"
                              : "border-gray-300 focus:ring-emerald-500 focus:border-emerald-500"
                          }`}
                        />
                        {#if destinoInvalido}
                          <span id={`destino-erro-${indice}`} class="block text-xs font-medium text-red-700 mt-1">
                            Escolha um nome da lista, exatamente como está cadastrado.
                          </span>
                        {/if}
                      {:else if linha.especialidadeNome}
                        <span class="text-gray-900">{linha.especialidadeNome}</span>
                        <span class="block text-xs text-gray-600">
                          {linha.criterio === "CODIGO_SUS" ? "Casou pelo código SUS" : "Casou pelo nome"}
                        </span>
                      {:else}
                        <span class="text-gray-500">—</span>
                      {/if}
                    </td>
                    <td class="py-2 px-3 text-right tabular-nums whitespace-nowrap text-gray-800">
                      {formatarReais(linha.valorAtual)}
                      {#if linha.valorOrigemAtual === "MANUAL"}
                        <span class="block text-xs text-gray-600">digitado à mão</span>
                      {/if}
                      {#if linha.codigoSusAtual && linha.codigoSusAtual !== linha.codigoSus}
                        <span class="block text-xs text-gray-600">código {linha.codigoSusAtual}</span>
                      {/if}
                    </td>
                    <td class="py-2 pl-3">
                      <span class={`inline-block text-xs font-semibold px-2 py-0.5 rounded border whitespace-nowrap ${s.classe}`}>
                        {s.rotulo}
                      </span>
                      <span class="block text-xs text-gray-700 mt-1">{linha.detalhe}</span>
                    </td>
                  </tr>
                {/each}
              </tbody>
            </table>
          </div>

          <div class="rounded-lg border border-gray-200 bg-gray-50 p-4 space-y-3">
            <h2 class="flex items-center gap-3 text-xl font-bold text-emerald-800">
          <span
            class="inline-flex items-center justify-center w-8 h-8 shrink-0 rounded-full bg-emerald-700 text-white text-base tabular-nums"
          >3</span>
          Gravar
        </h2>
            <p class="text-sm text-gray-800" aria-live="polite">
              <strong>{prontasParaGravar}</strong>
              {prontasParaGravar === 1 ? "linha será gravada" : "linhas serão gravadas"}{#if substituicoesMarcadas > 0}, das
                quais <strong>{substituicoesMarcadas}</strong>
                {substituicoesMarcadas === 1 ? "substitui" : "substituem"} um preço ou código já gravado{/if}.
              {#if marcadasSemDestino > 0}
                <span class="text-amber-900">
                  {marcadasSemDestino}
                  {marcadasSemDestino === 1 ? "linha marcada está" : "linhas marcadas estão"} sem especialidade escolhida e
                  não {marcadasSemDestino === 1 ? "entra" : "entram"}.
                </span>
              {/if}
            </p>
            {#if erroGravacao}
              <p class="text-sm font-medium text-red-700" role="alert">{erroGravacao}</p>
            {/if}
            <div class="flex flex-wrap gap-2">
              <button
                type="button"
                onclick={gravar}
                disabled={gravando || prontasParaGravar === 0}
                class="px-4 py-2 text-sm font-medium rounded-lg bg-emerald-700 hover:bg-emerald-800 text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:opacity-50 disabled:cursor-not-allowed transition"
              >
                {gravando ? "Gravando..." : "Gravar as linhas marcadas"}
              </button>
              <button
                type="button"
                onclick={recomecar}
                disabled={gravando}
                class="px-4 py-2 text-sm font-medium rounded-lg border border-gray-300 text-gray-700 hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-emerald-600 disabled:opacity-50 disabled:cursor-not-allowed transition"
              >
                Descartar e recomeçar
              </button>
            </div>
          </div>
        </section>
      {/if}
    </main>
  </div>
</div>
