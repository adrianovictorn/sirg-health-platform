<script lang="ts">
  import { deleteApi, getApi, postApi, putApi } from '$lib/api';
  import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';
  import UserMenu from '$lib/UserMenu.svelte';
    import { Pencil, Trash } from 'lucide-svelte';
  import { onMount } from 'svelte';

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
    cidadeId: number | null;
    cidadeNome: string | null;
    enumValue?: string | null;
    cnes?: string | null;
  }

  interface LocalDraft {
    nomeLocal: string;
    endereco: string;
    numero: string;
    cidadeId: string;
  }

  interface NovoLocalForm {
    nomeLocal: string;
    endereco: string;
    numero: string;
    // Campos do estabelecimento (V91) — só no cadastro; a edição inline da
    // tabela abaixo não os expõe (editar CNES exige recriar o registro).
    cnes: string;
    cnpj: string;
    razaoSocial: string;
    nomeFantasia: string;
    bairro: string;
    cep: string;
    telefone: string;
    email: string;
    importadoDoCnes: boolean;
  }

  const NOVO_LOCAL_VAZIO: NovoLocalForm = {
    nomeLocal: '',
    endereco: '',
    numero: '',
    cnes: '',
    cnpj: '',
    razaoSocial: '',
    nomeFantasia: '',
    bairro: '',
    cep: '',
    telefone: '',
    email: '',
    importadoDoCnes: false
  };

  let novoLocal = $state<NovoLocalForm>({ ...NOVO_LOCAL_VAZIO });
  let buscandoCnes = $state(false);
  let cidadesExistentes = $state<Cidade[]>([]);
  let cidadeId = $state<string>('');
  let locais = $state<LocalAgendamento[]>([]);
  let localAgendamentoId = $state<number | null>(null);
  let draft = $state<LocalDraft>({ nomeLocal: '', endereco: '', numero: '', cidadeId: '' });

  let termoBuscaCidade = $state('');
  let termoBuscaLocal = $state('');
  let isLoading = $state(false);
  let erro = $state('');
  let mensagem = $state('');
  
  const cidadesFiltradas = $derived(
    termoBuscaCidade.trim()
    ? cidadesExistentes.filter((c) => c.nomeCidade.toLocaleLowerCase().includes(termoBuscaCidade.toLocaleLowerCase()))
    : cidadesExistentes
  );
  
  const locaisFiltrados = $derived(
    termoBuscaLocal.trim()
    ? locais.filter((l) =>
        l.nomeLocal.toLocaleLowerCase().includes(termoBuscaLocal.toLocaleLowerCase()) ||
        (l.cnes != null && l.cnes === termoBuscaLocal.trim())
      )
    : locais
  );
  
    function startEdicao(localAgendamento: LocalAgendamento){
      localAgendamentoId = localAgendamento.id;
      draft = {
        nomeLocal: localAgendamento.nomeLocal ?? '',
        endereco: localAgendamento.endereco ?? '',
        numero: localAgendamento.numero ?? '',
        cidadeId: localAgendamento.cidadeId != null ? String(localAgendamento.cidadeId) : ''
      };

    }

    function cancelarEdicao(){
      localAgendamentoId = null;
      draft = { nomeLocal: '', endereco: '', numero: '', cidadeId: '' };
    }

  async function atualizarLocalAgendamento(){
    if (localAgendamentoId === null) return;

    const cidadeIdNumero = draft.cidadeId ? Number(draft.cidadeId) : null;
    if (!cidadeIdNumero) {
      alert('Selecione uma cidade válida.');
      return;
    }

    const payload = {
      nomeLocal: draft.nomeLocal.trim(),
      numero: draft.numero.trim(),
      cidadeId: cidadeIdNumero,
      endereco: draft.endereco.trim()
    };

    try{
      const res = await putApi(`local/agendamento/${localAgendamentoId}`, payload);

      if (!res.ok) {
        alert('Erro ao atualizar o local de agendamento.');
        return;
      }

      await carregarLocaisAgendamento();
      cancelarEdicao();
    } catch{
      alert ("Erro ao se conectar ao servidor !");
    }
  }  

  async function carregarCidades() {
    try {
      const res = await getApi('cidades');
      if (!res.ok) {
        throw new Error('Erro ao receber dados das cidades.');
      }
      cidadesExistentes = await res.json();
    } catch (e: any) {
      erro = e.message ?? 'Erro ao buscar cidades.';
    }
  }

  async function carregarLocaisAgendamento() {
    try {
      const res = await getApi('local/agendamento');
      if (!res.ok) {
        throw new Error('Erro ao buscar locais de agendamento.');
      }
      locais = await res.json();
    } catch (e: any) {
      erro = e.message ?? 'Erro ao carregar locais.';
    }
  }

  async function deletarLocalAgendamento(localAgendamentoID: number) {
    try {
      const res = await deleteApi(`local/agendamento/${localAgendamentoID}`);

      if (!res.ok) {
        alert('Erro ao enviar dados ao servidor!');
        return;
      }

      if (localAgendamentoId === localAgendamentoID) {
        cancelarEdicao();
      }

      await carregarLocaisAgendamento();
    } catch {
      alert('Erro ao se conectar ao servidor!');
    }
  }

  async function buscarPorCnesNovoLocal() {
    const cnes = novoLocal.cnes.trim();
    if (!cnes) {
      erro = 'Informe o número do CNES antes de buscar.';
      return;
    }
    buscandoCnes = true;
    erro = '';
    try {
      const res = await getApi(`cnes/estabelecimentos/${encodeURIComponent(cnes)}`);
      if (res.status === 404) {
        erro = 'CNES não encontrado — confira o número.';
        return;
      }
      if (!res.ok) {
        const corpo = await res.json().catch(() => ({}));
        erro = corpo.message ?? 'Serviço do CNES indisponível no momento. O cadastro manual continua disponível.';
        return;
      }
      const dados = await res.json();

      // Este endpoint só sabe dizer se o CNES já é uma Unidade — Local de
      // Atendimento tem sua própria checagem de duplicidade, feita pelo
      // backend no momento de cadastrar (findByCnes em LocalAgendamento).
      novoLocal = {
        ...novoLocal,
        endereco: dados.endereco || novoLocal.endereco,
        numero: dados.numero || novoLocal.numero,
        cnpj: dados.cnpj || novoLocal.cnpj,
        razaoSocial: dados.razaoSocial || novoLocal.razaoSocial,
        nomeFantasia: dados.nomeFantasia || novoLocal.nomeFantasia,
        bairro: dados.bairro || novoLocal.bairro,
        cep: dados.cep || novoLocal.cep,
        telefone: dados.telefone || novoLocal.telefone,
        email: dados.email || novoLocal.email,
        importadoDoCnes: true
      };
      mensagem = 'Dados do CNES preenchidos. Confira antes de cadastrar.';
    } catch {
      erro = 'Erro ao buscar CNES.';
    } finally {
      buscandoCnes = false;
    }
  }

  async function cadastrarLocalAgendamento(event: Event) {
    event.preventDefault();
    mensagem = '';
    erro = '';

    const cidadeSelecionadaId = cidadeId ? Number(cidadeId) : null;

    if (!cidadeSelecionadaId) {
      erro = 'Selecione uma cidade antes de cadastrar o local.';
      return;
    }

    const payload = {
      nomeLocal: novoLocal.nomeLocal,
      endereco: novoLocal.endereco,
      numero: novoLocal.numero,
      cidadeId: cidadeSelecionadaId,
      cnes: novoLocal.cnes || null,
      cnpj: novoLocal.cnpj || null,
      razaoSocial: novoLocal.razaoSocial || null,
      nomeFantasia: novoLocal.nomeFantasia || null,
      bairro: novoLocal.bairro || null,
      cep: novoLocal.cep || null,
      telefone: novoLocal.telefone || null,
      email: novoLocal.email || null,
      importadoDoCnes: novoLocal.importadoDoCnes
    };

    try {
      const res = await postApi('local/agendamento/cadastrar', payload);
      if (!res.ok) {
        const corpo = await res.json().catch(() => ({}));
        throw new Error(corpo.message ?? 'Erro ao cadastrar o local de agendamento.');
      }

      mensagem = 'Local cadastrado com sucesso!';
      novoLocal = { ...NOVO_LOCAL_VAZIO };
      cidadeId = '';

      await carregarLocaisAgendamento();
    } catch (e: any) {
      erro = e.message ?? 'Erro ao se conectar ao servidor!';
    }
  }

  onMount(async () => {
    isLoading = true;
    await Promise.all([carregarCidades(), carregarLocaisAgendamento()]);
    isLoading = false;
  });
</script>

<svelte:head>
  <title>Cadastro de Local de Agendamento</title>
</svelte:head>

<div class="flex min-h-screen bg-gray-100">
  <RoleBasedMenu activePage="/cadastrar/cidade/local-agendamento" />

  <div class="flex-1 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Cadastrar Local de Agendamento</h1>
      <UserMenu />
    </header>

    <main class="flex-1 overflow-auto p-6">
      <div class="grid gap-6 lg:grid-cols-3">
        <section class="lg:col-span-1 bg-white rounded-lg shadow p-6 space-y-4">
          <h2 class="text-lg font-semibold text-emerald-700">Novo Local</h2>

          {#if mensagem}
            <div class="rounded bg-emerald-100 text-emerald-800 px-3 py-2 text-sm">{mensagem}</div>
          {/if}

          {#if erro}
            <div class="rounded bg-red-100 text-red-700 px-3 py-2 text-sm">{erro}</div>
          {/if}

          <form class="space-y-4" on:submit={cadastrarLocalAgendamento}>
            <div class="flex flex-col space-y-1">
              <label class="text-sm font-medium text-gray-700" for="localCnes">CNES</label>
              <div class="flex gap-2">
                <input
                  id="localCnes"
                  type="text"
                  bind:value={novoLocal.cnes}
                  class="flex-1 border border-gray-300 rounded-lg p-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
                <button
                  type="button"
                  on:click={buscarPorCnesNovoLocal}
                  disabled={buscandoCnes}
                  class="px-3 rounded-lg bg-sky-50 text-sky-700 hover:bg-sky-100 text-xs font-medium transition-colors disabled:opacity-50 whitespace-nowrap"
                >
                  {buscandoCnes ? 'Buscando...' : 'Buscar CNES'}
                </button>
              </div>
              <p class="text-xs text-gray-500">
                Busca os dados do estabelecimento na base do CNES e preenche os campos abaixo —
                confira antes de cadastrar, nada é gravado automaticamente.
              </p>
            </div>

            <div class="flex flex-col space-y-1">
              <label class="text-sm font-medium text-gray-700" for="nomeLocal">Nome do Local</label>
              <input
                id="nomeLocal"
                type="text"
                bind:value={novoLocal.nomeLocal}
                required
                placeholder="Hospital, clínica, etc."
                class="border border-gray-300 rounded-lg p-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>

            <div class="flex flex-col space-y-1">
              <label class="text-sm font-medium text-gray-700" for="endereco">Endereço</label>
              <input
                id="endereco"
                type="text"
                bind:value={novoLocal.endereco}
                placeholder="Rua, bairro..."
                class="border border-gray-300 rounded-lg p-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>

            <div class="grid grid-cols-2 gap-3">
              <div class="flex flex-col space-y-1">
                <label class="text-sm font-medium text-gray-700" for="numero">Número</label>
                <input
                  id="numero"
                  type="text"
                  bind:value={novoLocal.numero}
                  class="border border-gray-300 rounded-lg p-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>
              <div class="flex flex-col space-y-1">
                <label class="text-sm font-medium text-gray-700" for="localBairro">Bairro</label>
                <input
                  id="localBairro"
                  type="text"
                  bind:value={novoLocal.bairro}
                  class="border border-gray-300 rounded-lg p-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>
            </div>

            <div class="grid grid-cols-2 gap-3">
              <div class="flex flex-col space-y-1">
                <label class="text-sm font-medium text-gray-700" for="localCep">CEP</label>
                <input
                  id="localCep"
                  type="text"
                  bind:value={novoLocal.cep}
                  class="border border-gray-300 rounded-lg p-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>
              <div class="flex flex-col space-y-1">
                <label class="text-sm font-medium text-gray-700" for="localCnpj">CNPJ</label>
                <input
                  id="localCnpj"
                  type="text"
                  bind:value={novoLocal.cnpj}
                  class="border border-gray-300 rounded-lg p-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>
            </div>

            <div class="flex flex-col space-y-1">
              <label class="text-sm font-medium text-gray-700" for="localRazaoSocial">Razão Social</label>
              <input
                id="localRazaoSocial"
                type="text"
                bind:value={novoLocal.razaoSocial}
                class="border border-gray-300 rounded-lg p-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>

            <div class="flex flex-col space-y-1">
              <label class="text-sm font-medium text-gray-700" for="localNomeFantasia">Nome Fantasia</label>
              <input
                id="localNomeFantasia"
                type="text"
                bind:value={novoLocal.nomeFantasia}
                class="border border-gray-300 rounded-lg p-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>

            <div class="grid grid-cols-2 gap-3">
              <div class="flex flex-col space-y-1">
                <label class="text-sm font-medium text-gray-700" for="localTelefone">Telefone</label>
                <input
                  id="localTelefone"
                  type="text"
                  bind:value={novoLocal.telefone}
                  class="border border-gray-300 rounded-lg p-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>
              <div class="flex flex-col space-y-1">
                <label class="text-sm font-medium text-gray-700" for="localEmail">Email</label>
                <input
                  id="localEmail"
                  type="text"
                  bind:value={novoLocal.email}
                  class="border border-gray-300 rounded-lg p-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
                />
              </div>
            </div>

            <div class="flex flex-col space-y-1">
              <label class="text-sm font-medium text-gray-700" for="buscaCidade">Buscar cidade</label>
              <input
                id="buscaCidade"
                type="text"
                bind:value={termoBuscaCidade}
                placeholder="Digite para filtrar..."
                class="border border-gray-300 rounded-lg p-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>

            <div class="flex flex-col space-y-1">
              <label class="text-sm font-medium text-gray-700" for="cidade">Cidade</label>
              <select
                id="cidade"
                bind:value={cidadeId}
                class="border border-gray-300 rounded-lg p-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
              >
                <option value="" disabled>Selecione...</option>
                {#each cidadesFiltradas as cidade}
                  <option value={cidade.id}>{cidade.nomeCidade}</option>
                {/each}
              </select>
            </div>

            <button
              type="submit"
              class="w-full bg-emerald-700 text-white py-2 rounded-lg hover:bg-emerald-800 transition"
            >
              Cadastrar Local
            </button>
          </form>
        </section>

        <section class="lg:col-span-2 bg-white rounded-lg shadow p-6 space-y-4">
          <div class="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
            <div>
              <h2 class="text-lg font-semibold text-emerald-700">Locais cadastrados</h2>
              <p class="text-sm text-gray-500">Consulte e filtre os locais registrados.</p>
            </div>

            <div class="w-full md:w-64">
              <input
                type="text"
                bind:value={termoBuscaLocal}
                placeholder="Buscar pelo nome ou pelo CNES (código exato)..."
                class="w-full border border-gray-300 rounded-lg p-2 focus:outline-none focus:ring-2 focus:ring-emerald-500"
              />
            </div>
          </div>

          {#if isLoading}
            <p class="text-sm text-gray-500">Carregando informações...</p>
          {:else if locaisFiltrados.length === 0}
            <p class="text-sm text-gray-500">Nenhum local cadastrado até o momento.</p>
          {:else}
            <div class="overflow-hidden rounded-lg border border-gray-200">
              <table class="min-w-full divide-y divide-gray-200 text-sm">
                <thead class="bg-gray-50">
                  <tr>
                    <th class="px-4 py-2 text-left font-medium text-gray-600">Id</th>
                    <th class="px-4 py-2 text-left font-medium text-gray-600">Nome</th>
                    <th class="px-4 py-2 text-left font-medium text-gray-600">Endereço</th>
                    <th class="px-4 py-2 text-left font-medium text-gray-600">Número</th>
                    <th class="px-4 py-2 text-left font-medium text-gray-600">Cidade</th>
                    <th class="px-4 py-2 text-left font-medium ">Ações</th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-100">
                  {#each locaisFiltrados as loc}
                    <tr class="hover:bg-gray-50">
                      <td class="px-4 py-2 text-gray-700">
                        {loc.id}
                      </td>
                      <td class="px-4 py-2 text-gray-700">
                        {#if localAgendamentoId === loc.id}
                          <input
                            type="text"
                            bind:value={draft.nomeLocal}
                            class="border border-gray-300 rounded px-2 py-1 w-full"
                          />
                        {:else}
                          {loc.nomeLocal}
                        {/if}
                      </td>
                      <td class="px-4 py-2 text-gray-600">
                        {#if localAgendamentoId === loc.id}
                          <input
                            type="text"
                            bind:value={draft.endereco}
                            class="border border-gray-300 rounded px-2 py-1 w-full"
                          />
                        {:else}
                           {loc.endereco || '—'}
                        {/if}
                      </td>
                      <td class="px-4 py-2 text-gray-600">
                        {#if localAgendamentoId === loc.id}
                          <input
                            type="text"
                            bind:value={draft.numero}
                            class="border border-gray-300 rounded px-2 py-1 w-full"
                          />
                        {:else}
                          {loc.numero || '—'}
                        {/if}
                      </td>
                      <td class="px-4 py-2 text-gray-600">
                        {#if localAgendamentoId === loc.id}
                          <select
                            bind:value={draft.cidadeId}
                            class="border border-gray-300 rounded px-2 py-1 w-full"
                          >
                            <option value="" disabled>Selecione...</option>
                            {#each cidadesExistentes as cidade}
                              <option value={String(cidade.id)}>{cidade.nomeCidade}</option>
                            {/each}
                          </select>
                        {:else}
                            {loc.cidadeNome || '—'}
                        {/if}
                      </td>
                      <td>
                        {#if localAgendamentoId === loc.id}
                        <div class="px-2 justify-center">
                          <button type="button" on:click={atualizarLocalAgendamento}> Atualizar</button>
                          <button type="button" on:click={cancelarEdicao}> Cancelar</button>
                        </div>
                        {:else} 
                        <div class="px-2 justify-center">
                          <button type="button" on:click={() => startEdicao(loc)}><Pencil size={16}/></button>
                          <button type="button"on:click={() => deletarLocalAgendamento(loc.id)}><Trash size={16}/></button>
                        </div>

                        {/if}
                      </td>
                    </tr>
                  {/each}
                </tbody>
              </table>
            </div>
          {/if}
        </section>
      </div>
    </main>
  </div>
</div>
