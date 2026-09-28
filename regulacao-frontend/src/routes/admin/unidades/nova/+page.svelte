<script>
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { getApi, postApi } from '$lib/api.js';
  import Content from '$lib/Content.svelte';
  import { toast } from 'svelte-sonner';
  import { cnesPertenceAOutraUnidade, divergeDosDadosAtuais, mesclarCamposCnes } from '$lib/unidadeCnes.js';

  let grupos = $state([]);
  let buscandoCnes = $state(false);
  let salvando = $state(false);

  let form = $state({
    nome: '',
    codigo: '',
    cnes: '',
    telefone: '',
    endereco: '',
    grupoRelatorioId: null,
    razaoSocial: '',
    nomeFantasia: '',
    cnpj: '',
    numero: '',
    bairro: '',
    cep: '',
    email: '',
    importadoDoCnes: false
  });

  onMount(async () => {
    try {
      const res = await getApi('grupo-relatorio/listar');
      grupos = res.ok ? await res.json() : [];
    } catch {
      /* ignora - combo fica vazio */
    }
  });

  async function buscarPorCnes() {
    const cnes = (form.cnes || '').trim();
    if (!cnes) {
      toast.error('Informe o número do CNES antes de buscar.');
      return;
    }
    buscandoCnes = true;
    try {
      const res = await getApi(`cnes/estabelecimentos/${encodeURIComponent(cnes)}`);
      if (res.status === 404) {
        toast.error('CNES não encontrado — confira o número.');
        return;
      }
      if (!res.ok) {
        const erro = await res.json().catch(() => ({}));
        toast.error(erro.message ?? 'Serviço do CNES indisponível no momento. O cadastro manual continua disponível.');
        return;
      }
      const dados = await res.json();

      if (cnesPertenceAOutraUnidade(dados, null)) {
        toast.error(`Este CNES já está cadastrado em outra unidade (#${dados.unidadeId}).`);
        return;
      }

      if (divergeDosDadosAtuais(form, dados) && !confirm('A busca trouxe dados diferentes dos já preenchidos. Sobrescrever os campos preenchidos?')) {
        return;
      }

      form = mesclarCamposCnes(form, dados);
      toast.success('Dados do CNES preenchidos. Confira antes de salvar.');
    } catch {
      toast.error('Erro ao buscar CNES.');
    } finally {
      buscandoCnes = false;
    }
  }

  async function salvar() {
    if (!form.nome.trim()) {
      toast.error('O nome é obrigatório.');
      return;
    }
    salvando = true;
    try {
      const res = await postApi('unidades', form);
      if (!res.ok) {
        const erro = await res.json().catch(() => ({}));
        toast.error(erro.message ?? 'Erro ao criar unidade.');
        return;
      }
      toast.success('Unidade criada.');
      goto('/admin/unidades');
    } catch {
      toast.error('Erro ao salvar unidade.');
    } finally {
      salvando = false;
    }
  }
</script>

<Content titleH1="Nova Unidade" page="/admin/unidades">
  <main class="p-6">
    <div class="bg-white border border-gray-200 rounded-xl p-6 space-y-4 max-w-6xl">
      <div>
        <label class="block text-xs text-gray-500 mb-1">CNES</label>
        <div class="flex gap-2">
          <input
            bind:value={form.cnes}
            class="flex-1 bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500"
          />
          <button
            type="button"
            onclick={buscarPorCnes}
            disabled={buscandoCnes}
            class="px-3 py-2 rounded-lg bg-sky-50 text-sky-700 hover:bg-sky-100 text-xs font-medium transition-colors disabled:opacity-50 whitespace-nowrap"
          >
            {buscandoCnes ? 'Buscando...' : 'Buscar CNES'}
          </button>
        </div>
        <p class="text-xs text-gray-500 mt-1">
          Busca os dados do estabelecimento na base do CNES e preenche os campos abaixo —
          confira antes de salvar, nada é gravado automaticamente.
        </p>
      </div>

      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="block text-xs text-gray-500 mb-1">Nome *</label>
          <input bind:value={form.nome} class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
        <div>
          <label class="block text-xs text-gray-500 mb-1">Código</label>
          <input bind:value={form.codigo} class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
      </div>

      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="block text-xs text-gray-500 mb-1">Telefone</label>
          <input bind:value={form.telefone} class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
        <div>
          <label class="block text-xs text-gray-500 mb-1">Email</label>
          <input bind:value={form.email} class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
      </div>

      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="block text-xs text-gray-500 mb-1">Endereço</label>
          <input bind:value={form.endereco} class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
        <div>
          <label class="block text-xs text-gray-500 mb-1">Número</label>
          <input bind:value={form.numero} class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
      </div>

      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="block text-xs text-gray-500 mb-1">Bairro</label>
          <input bind:value={form.bairro} class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
        <div>
          <label class="block text-xs text-gray-500 mb-1">CEP</label>
          <input bind:value={form.cep} class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
      </div>

      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="block text-xs text-gray-500 mb-1">Razão Social</label>
          <input bind:value={form.razaoSocial} class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
        <div>
          <label class="block text-xs text-gray-500 mb-1">Nome Fantasia</label>
          <input bind:value={form.nomeFantasia} class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
      </div>

      <div class="grid grid-cols-2 gap-3">
        <div>
          <label class="block text-xs text-gray-500 mb-1">CNPJ</label>
          <input bind:value={form.cnpj} class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500" />
        </div>
        <div>
          <label for="grupoRelatorio" class="block text-xs text-gray-500 mb-1">Grupo (cota coletiva)</label>
          <select id="grupoRelatorio" bind:value={form.grupoRelatorioId}
            class="w-full bg-white border border-gray-300 rounded-lg px-3 py-2 text-sm text-gray-800 focus:outline-none focus:ring-2 focus:ring-emerald-500">
            <option value={null}>— Sem grupo —</option>
            {#each grupos as g (g.id)}
              <option value={g.id}>{g.nome}</option>
            {/each}
          </select>
        </div>
      </div>
      <p class="text-xs text-gray-500 -mt-2">
        Com grupo definido, as cotas lançadas para ele viram um saldo compartilhado entre as
        unidades membros, somando-se à cota própria desta unidade.
      </p>

      <div class="flex justify-end gap-3 pt-2">
        <button onclick={() => goto('/admin/unidades')}
          class="px-4 py-2 rounded-lg bg-gray-100 text-gray-700 text-sm hover:bg-gray-200 transition-colors">
          Cancelar
        </button>
        <button onclick={salvar} disabled={salvando}
          class="px-4 py-2 rounded-lg bg-emerald-600 text-white text-sm hover:bg-emerald-500 transition-colors disabled:opacity-50">
          {salvando ? 'Salvando...' : 'Salvar'}
        </button>
      </div>
    </div>
  </main>
</Content>
