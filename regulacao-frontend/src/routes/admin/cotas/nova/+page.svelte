<script>
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { getApi, postApi } from '$lib/api.js';
  import Content from '$lib/Content.svelte';
  import { toast } from 'svelte-sonner';

  let unidades = $state([]);
  let grupos = $state([]);
  let especialidades = $state([]);
  let salvando = $state(false);

  const periodoAtual = new Date().toISOString().slice(0, 7);
  const hojeISO = new Date().toISOString().slice(0, 10);

  let form = $state({
    titular: 'UNIDADE',
    escopo: 'ESPECIALIDADE',
    grupoUnidadesId: null,
    grupoEspecialidadesId: null,
    unidadeId: null,
    especialidadeId: null,
    tipoPeriodo: 'MENSAL',
    periodo: periodoAtual,
    dataEspecifica: hojeISO,
    quantidadeTotal: 0
  });

  // Titular = VARIAS_UNIDADES: cria a mesma regra (escopo/período/quantidade)
  // para N unidades de uma vez, sem mudar o backend — dispara um POST /api/cotas
  // por unidade selecionada e reporta sucesso/falha individualmente (best-effort:
  // uma unidade com conflito não deve travar a criação das demais).
  let unidadesSelecionadas = $state([]);
  let resultadosReplicacao = $state(null);

  function alternarUnidadeSelecionada(id) {
    unidadesSelecionadas = unidadesSelecionadas.includes(id)
      ? unidadesSelecionadas.filter((u) => u !== id)
      : [...unidadesSelecionadas, id];
  }

  onMount(async () => {
    await Promise.all([carregarUnidades(), carregarEspecialidades(), carregarGrupos()]);
  });

  async function carregarUnidades() {
    try {
      const res = await getApi('unidades/ativas');
      unidades = await res.json();
    } catch { /* ignora - combo fica vazio */ }
  }

  async function carregarGrupos() {
    try {
      const res = await getApi('grupo-relatorio/listar');
      grupos = res.ok ? await res.json() : [];
    } catch { /* ignora - combo fica vazio */ }
  }

  async function carregarEspecialidades() {
    try {
      const res = await getApi('catalog/especialidades/listar');
      especialidades = await res.json();
    } catch { /* ignora - combo fica vazio */ }
  }

  const grupoSelecionado = $derived(
    form.escopo === 'GRUPO_ESPECIALIDADES' && form.grupoEspecialidadesId
      ? grupos.find((g) => g.id === Number(form.grupoEspecialidadesId))
      : null
  );

  /** Campos comuns (escopo, período, quantidade) — iguais para os 3 modos de titular. */
  function construirPayloadEscopoPeriodo() {
    const porGrupoEsp = form.escopo === 'GRUPO_ESPECIALIDADES';
    if (porGrupoEsp && !form.grupoEspecialidadesId) {
      toast.error('Selecione o grupo de especialidades.');
      return null;
    }
    return {
      especialidadeId: !porGrupoEsp && form.especialidadeId ? Number(form.especialidadeId) : null,
      grupoEspecialidadesId: porGrupoEsp ? Number(form.grupoEspecialidadesId) : null,
      tipoPeriodo: form.tipoPeriodo,
      periodo: form.tipoPeriodo === 'MENSAL' ? form.periodo : null,
      dataEspecifica: form.tipoPeriodo === 'DATA' ? form.dataEspecifica : null,
      quantidadeTotal: Number(form.quantidadeTotal)
    };
  }

  async function salvar() {
    const porGrupoUnidades = form.titular === 'GRUPO';
    if (porGrupoUnidades && !form.grupoUnidadesId) { toast.error('Selecione o grupo de unidades.'); return; }
    if (!porGrupoUnidades && !form.unidadeId) { toast.error('Selecione a unidade.'); return; }

    const base = construirPayloadEscopoPeriodo();
    if (!base) return;

    const payload = {
      unidadeId: porGrupoUnidades ? null : Number(form.unidadeId),
      grupoUnidadesId: porGrupoUnidades ? Number(form.grupoUnidadesId) : null,
      ...base
    };

    salvando = true;
    try {
      const res = await postApi('cotas', payload);
      if (!res.ok) {
        const erro = await res.json().catch(() => ({}));
        toast.error(erro.message ?? 'Erro ao criar cota.');
        return;
      }
      toast.success('Cota criada.');
      goto('/admin/cotas');
    } catch {
      toast.error('Erro ao salvar cota.');
    } finally {
      salvando = false;
    }
  }

  /**
   * Replica a mesma regra (escopo/período/quantidade) para N unidades — um
   * POST /api/cotas por unidade, em paralelo, sem mudar o backend nem o
   * constraint ck_cota_titular_exclusivo (cada linha criada continua com
   * exatamente 1 titular). Best-effort: uma unidade que falhar (ex. cota
   * duplicada) não impede a criação das demais.
   */
  async function salvarVarias() {
    if (unidadesSelecionadas.length === 0) {
      toast.error('Selecione ao menos uma unidade.');
      return;
    }
    const base = construirPayloadEscopoPeriodo();
    if (!base) return;

    salvando = true;
    resultadosReplicacao = null;
    try {
      const respostas = await Promise.allSettled(
        unidadesSelecionadas.map(async (unidadeId) => {
          const payload = { unidadeId, grupoUnidadesId: null, ...base };
          const res = await postApi('cotas', payload);
          if (!res.ok) {
            const erro = await res.json().catch(() => ({}));
            throw new Error(erro.message ?? 'Erro ao criar cota.');
          }
        })
      );

      const resultados = respostas.map((resultado, i) => {
        const unidadeId = unidadesSelecionadas[i];
        const unidadeNome = unidades.find((u) => u.id === unidadeId)?.nome ?? `Unidade #${unidadeId}`;
        return resultado.status === 'fulfilled'
          ? { unidadeId, unidadeNome, ok: true }
          : { unidadeId, unidadeNome, ok: false, mensagem: resultado.reason?.message ?? 'Erro desconhecido.' };
      });

      resultadosReplicacao = resultados;
      const sucesso = resultados.filter((r) => r.ok).length;
      if (sucesso === resultados.length) {
        toast.success(`Cota criada para as ${sucesso} unidades selecionadas.`);
      } else if (sucesso === 0) {
        toast.error('Nenhuma cota foi criada — veja os erros abaixo.');
      } else {
        toast.error(`${sucesso} de ${resultados.length} unidades tiveram cota criada — veja os detalhes abaixo.`);
      }
    } finally {
      salvando = false;
    }
  }

  function aoClicarSalvar() {
    return form.titular === 'VARIAS_UNIDADES' ? salvarVarias() : salvar();
  }
</script>

<Content titleH1="Nova Cota" page="/admin/cotas">
  <main class="p-6">
    <div class="bg-white border border-gray-200 rounded-xl p-6 space-y-4">
      <div>
        <label class="block text-xs text-gray-500 mb-1">Titular da cota *</label>
        <div class="flex gap-4">
          <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
            <input type="radio" bind:group={form.titular} value="UNIDADE" class="accent-emerald-600" />
            Unidade
          </label>
          <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
            <input type="radio" bind:group={form.titular} value="GRUPO" class="accent-emerald-600" />
            Grupo
          </label>
          <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
            <input type="radio" bind:group={form.titular} value="VARIAS_UNIDADES" class="accent-emerald-600" />
            Várias unidades
          </label>
        </div>
      </div>

      {#if form.titular === 'UNIDADE'}
        <div>
          <label class="block text-xs text-gray-500 mb-1">Unidade *</label>
          <select bind:value={form.unidadeId}
            class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500">
            <option value={null}>Selecionar unidade...</option>
            {#each unidades as u (u.id)}
              <option value={u.id}>{u.nome}</option>
            {/each}
          </select>
        </div>
      {:else if form.titular === 'GRUPO'}
        <div>
          <label class="block text-xs text-gray-500 mb-1">Grupo de Unidades *</label>
          <select bind:value={form.grupoUnidadesId}
            class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500">
            <option value={null}>Selecionar grupo...</option>
            {#each grupos as g (g.id)}
              <option value={g.id}>{g.nome}</option>
            {/each}
          </select>
          <p class="text-xs text-gray-500 mt-1">
            Saldo compartilhado entre as unidades do grupo — consumido por ordem de chegada.
          </p>
        </div>
      {:else}
        <div>
          <label class="block text-xs text-gray-500 mb-1">Unidades * ({unidadesSelecionadas.length} selecionada{unidadesSelecionadas.length === 1 ? '' : 's'})</label>
          <div class="max-h-48 overflow-y-auto border border-gray-300 rounded-lg p-2 space-y-1">
            {#each unidades as u (u.id)}
              <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
                <input type="checkbox"
                  checked={unidadesSelecionadas.includes(u.id)}
                  onchange={() => alternarUnidadeSelecionada(u.id)}
                  class="accent-emerald-600" />
                {u.nome}
              </label>
            {:else}
              <p class="text-xs text-gray-400">Nenhuma unidade ativa cadastrada.</p>
            {/each}
          </div>
          <p class="text-xs text-gray-500 mt-1">
            Cria uma cota independente para cada unidade selecionada, com a mesma
            especialidade/grupo, período e quantidade — cada cota conta o próprio saldo.
            Se alguma falhar (ex.: já existe cota para aquela unidade), as demais continuam
            sendo criadas normalmente.
          </p>
        </div>
      {/if}

      {#if resultadosReplicacao}
        <div class="border border-gray-200 rounded-lg overflow-hidden">
          <div class="px-3 py-2 text-xs font-semibold uppercase tracking-wide text-gray-500 bg-gray-50">
            Resultado da criação em lote
          </div>
          <ul class="divide-y divide-gray-200">
            {#each resultadosReplicacao as r (r.unidadeId)}
              <li class="px-3 py-2 text-sm flex items-center justify-between gap-3">
                <span class="text-gray-800">{r.unidadeNome}</span>
                {#if r.ok}
                  <span class="px-2 py-0.5 rounded-full text-xs font-medium bg-emerald-100 text-emerald-700">Criada</span>
                {:else}
                  <span class="text-xs text-red-700 text-right">{r.mensagem}</span>
                {/if}
              </li>
            {/each}
          </ul>
        </div>
      {/if}

      <div>
        <label class="block text-xs text-gray-500 mb-1">O que a cota limita *</label>
        <div class="flex flex-wrap gap-4">
          <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
            <input type="radio" bind:group={form.escopo} value="ESPECIALIDADE" class="accent-emerald-600" />
            Uma especialidade
          </label>
          <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
            <input type="radio" bind:group={form.escopo} value="GRUPO_ESPECIALIDADES" class="accent-emerald-600" />
            Um grupo de especialidades
          </label>
          <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
            <input type="radio" bind:group={form.escopo} value="GERAL" class="accent-emerald-600" />
            Todas (cota geral)
          </label>
        </div>
      </div>

      {#if form.escopo === 'ESPECIALIDADE'}
        <div>
          <label class="block text-xs text-gray-500 mb-1">Especialidade</label>
          <select bind:value={form.especialidadeId}
            class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500">
            <option value={null}>Selecionar especialidade...</option>
            {#each especialidades as e (e.id)}
              <option value={e.id}>{e.nome}</option>
            {/each}
          </select>
        </div>
      {:else if form.escopo === 'GRUPO_ESPECIALIDADES'}
        <div>
          <label class="block text-xs text-gray-500 mb-1">Grupo de Especialidades *</label>
          <select bind:value={form.grupoEspecialidadesId}
            class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500">
            <option value={null}>Selecionar grupo...</option>
            {#each grupos as g (g.id)}
              <option value={g.id}>{g.nome}</option>
            {/each}
          </select>
          <p class="text-xs text-gray-500 mt-1">
            Evita cadastrar uma cota por especialidade. O saldo é <strong>único e
            compartilhado</strong> entre todas as especialidades do grupo.
          </p>
          {#if grupoSelecionado}
            <details class="mt-2 text-xs">
              <summary class="cursor-pointer text-emerald-700 hover:text-emerald-600 select-none">
                Ver especialidades deste grupo ({grupoSelecionado.especialidades?.length ?? 0})
              </summary>
              <ul class="mt-2 space-y-1 pl-3 border-l border-gray-200 text-gray-700">
                {#each grupoSelecionado.especialidades ?? [] as e (e.id)}
                  <li>{e.nome}</li>
                {:else}
                  <li class="text-gray-400">Nenhuma especialidade vinculada a este grupo ainda.</li>
                {/each}
              </ul>
            </details>
          {/if}
        </div>
      {:else}
        <p class="text-xs text-gray-500">
          A cota valerá para qualquer especialidade deste titular.
        </p>
      {/if}

      <div>
        <label class="block text-xs text-gray-500 mb-1">Tipo de Controle *</label>
        <div class="flex gap-4">
          <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
            <input type="radio" bind:group={form.tipoPeriodo} value="MENSAL" class="accent-emerald-600" />
            Mensal
          </label>
          <label class="flex items-center gap-2 text-sm text-gray-700 cursor-pointer">
            <input type="radio" bind:group={form.tipoPeriodo} value="DATA" class="accent-emerald-600" />
            Por Data Específica
          </label>
        </div>
      </div>

      {#if form.tipoPeriodo === 'MENSAL'}
        <div>
          <label class="block text-xs text-gray-500 mb-1">Período (YYYY-MM) *</label>
          <input type="month" bind:value={form.periodo}
            class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
      {:else}
        <div>
          <label class="block text-xs text-gray-500 mb-1">Data Específica *</label>
          <input type="date" bind:value={form.dataEspecifica}
            class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
      {/if}

      <div>
        <label class="block text-xs text-gray-500 mb-1">Quantidade Total *</label>
        <input type="number" min="0" bind:value={form.quantidadeTotal}
          class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
      </div>

      <div class="flex justify-end gap-3 pt-2">
        <button onclick={() => goto('/admin/cotas')}
          class="px-4 py-2 rounded-lg bg-gray-100 text-gray-700 text-sm hover:bg-gray-200 transition-colors">
          Cancelar
        </button>
        <button onclick={aoClicarSalvar} disabled={salvando}
          class="px-4 py-2 rounded-lg bg-emerald-600 text-white text-sm hover:bg-emerald-500 transition-colors disabled:opacity-50">
          {salvando ? 'Salvando...' : 'Salvar'}
        </button>
      </div>
    </div>
  </main>
</Content>
