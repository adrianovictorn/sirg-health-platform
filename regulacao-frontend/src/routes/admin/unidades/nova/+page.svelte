<script>
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { getApi, postApi } from '$lib/api.js';
  import Content from '$lib/Content.svelte';
  import { toast } from 'svelte-sonner';
  import { cnesPertenceAOutraUnidade, divergeDosDadosAtuais, mesclarCamposCnes } from '$lib/unidadeCnes.js';
  import SirgDesign from '$lib/sirg-design/SirgDesign.svelte';

  let grupos = $state([]);
  let buscandoCnes = $state(false);
  let salvando = $state(false);

  // V86: SOLICITANTE pede e recebe cota; EXECUTANTE realiza; AMBOS faz os dois.
  const TIPOS_UNIDADE = [
    { valor: 'AMBOS', rotulo: 'Solicitante e executante' },
    { valor: 'SOLICITANTE', rotulo: 'Só solicitante' },
    { valor: 'EXECUTANTE', rotulo: 'Só executante' }
  ];

  let form = $state({
    nome: '',
    codigo: '',
    cnes: '',
    telefone: '',
    endereco: '',
    grupoRelatorioId: null,
    tipo: 'AMBOS',
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
  <SirgDesign>
    <div class="space-y-6 p-4 md:p-6 lg:p-8">
      <header class="sd-cabecalho">
        <div class="min-w-0">
          <h2 class="sd-titulo">Cadastro de nova unidade</h2>
          <p class="sd-texto-apoio mt-1">
            Preencha pela busca no CNES ou à mão. Campos com <span aria-hidden="true">*</span> são obrigatórios.
          </p>
        </div>
      </header>

      <section aria-labelledby="sec-identificacao" class="sd-painel">
        <div class="sd-painel-topo">
          <h3 id="sec-identificacao" class="sd-titulo">Identificação</h3>
        </div>
        <div class="sd-painel-corpo grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
          <div class="sd-campo-grupo md:col-span-2">
            <label for="unidadeCnes" class="sd-rotulo">CNES</label>
            <div class="flex gap-2">
              <input id="unidadeCnes" bind:value={form.cnes} inputmode="numeric" autocomplete="off"
                aria-describedby="unidadeCnesAjuda" class="sd-campo min-w-0 flex-1" />
              <button type="button" onclick={buscarPorCnes} disabled={buscandoCnes} aria-busy={buscandoCnes}
                class="sd-btn sd-btn--secundario">
                {buscandoCnes ? 'Buscando…' : 'Buscar CNES'}
              </button>
            </div>
            <p id="unidadeCnesAjuda" class="sd-ajuda">
              Preenche os campos com os dados do CNES. Confira antes de salvar: nada é gravado automaticamente.
            </p>
          </div>
          <div class="sd-campo-grupo md:col-span-2">
            <label for="unidadeNome" class="sd-rotulo">Nome <span aria-hidden="true">*</span></label>
            <input id="unidadeNome" bind:value={form.nome} aria-required="true" class="sd-campo" />
          </div>
          <div class="sd-campo-grupo md:col-span-2">
            <label for="unidadeRazaoSocial" class="sd-rotulo">Razão social</label>
            <input id="unidadeRazaoSocial" bind:value={form.razaoSocial} class="sd-campo" />
          </div>
          <div class="sd-campo-grupo md:col-span-2">
            <label for="unidadeNomeFantasia" class="sd-rotulo">Nome fantasia</label>
            <input id="unidadeNomeFantasia" bind:value={form.nomeFantasia} class="sd-campo" />
          </div>
          <div class="sd-campo-grupo">
            <label for="unidadeCodigo" class="sd-rotulo">Código</label>
            <input id="unidadeCodigo" bind:value={form.codigo} class="sd-campo" />
          </div>
          <div class="sd-campo-grupo">
            <label for="unidadeCnpj" class="sd-rotulo">CNPJ</label>
            <input id="unidadeCnpj" bind:value={form.cnpj} class="sd-campo" />
          </div>
        </div>
      </section>

      <section aria-labelledby="sec-contato" class="sd-painel">
        <div class="sd-painel-topo">
          <h3 id="sec-contato" class="sd-titulo">Endereço e contato</h3>
        </div>
        <div class="sd-painel-corpo grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4">
          <div class="sd-campo-grupo md:col-span-2">
            <label for="unidadeEndereco" class="sd-rotulo">Endereço</label>
            <input id="unidadeEndereco" bind:value={form.endereco} class="sd-campo" />
          </div>
          <div class="sd-campo-grupo">
            <label for="unidadeNumero" class="sd-rotulo">Número</label>
            <input id="unidadeNumero" bind:value={form.numero} class="sd-campo" />
          </div>
          <div class="sd-campo-grupo">
            <label for="unidadeBairro" class="sd-rotulo">Bairro</label>
            <input id="unidadeBairro" bind:value={form.bairro} class="sd-campo" />
          </div>
          <div class="sd-campo-grupo">
            <label for="unidadeCep" class="sd-rotulo">CEP</label>
            <input id="unidadeCep" bind:value={form.cep} inputmode="numeric" class="sd-campo" />
          </div>
          <div class="sd-campo-grupo">
            <label for="unidadeTelefone" class="sd-rotulo">Telefone</label>
            <input id="unidadeTelefone" type="tel" bind:value={form.telefone} class="sd-campo" />
          </div>
          <div class="sd-campo-grupo md:col-span-2">
            <label for="unidadeEmail" class="sd-rotulo">E-mail</label>
            <input id="unidadeEmail" type="email" bind:value={form.email} class="sd-campo" />
          </div>
        </div>
      </section>

      <section aria-labelledby="sec-papel" class="sd-painel">
        <div class="sd-painel-topo">
          <h3 id="sec-papel" class="sd-titulo">Tipo e cota</h3>
        </div>
        <div class="sd-painel-corpo grid grid-cols-1 gap-4 md:grid-cols-2">
          <div class="sd-campo-grupo">
            <label for="tipoUnidade" class="sd-rotulo">Tipo</label>
            <select id="tipoUnidade" bind:value={form.tipo} aria-describedby="tipoUnidadeAjuda tipoUnidadePapel"
              class="sd-campo">
              {#each TIPOS_UNIDADE as t (t.valor)}
                <option value={t.valor}>{t.rotulo}</option>
              {/each}
            </select>
            <p id="tipoUnidadeAjuda" class="sd-ajuda">
              Com “Só executante” ou “Solicitante e executante”, o que a unidade realiza é definido depois em
              Unidades → Parametrizar executante.
            </p>
            <p id="tipoUnidadePapel" class="sd-ajuda">
              O papel da unidade (solicitante, executante ou ambas) aparece depois de parametrizar a executante.
            </p>
          </div>
          <div class="sd-campo-grupo">
            <label for="grupoRelatorio" class="sd-rotulo">Grupo (cota coletiva)</label>
            <select id="grupoRelatorio" bind:value={form.grupoRelatorioId} aria-describedby="grupoRelatorioAjuda"
              class="sd-campo">
              <option value={null}>— Sem grupo —</option>
              {#each grupos as g (g.id)}
                <option value={g.id}>{g.nome}</option>
              {/each}
            </select>
            <p id="grupoRelatorioAjuda" class="sd-ajuda">
              Com grupo definido, as cotas lançadas para ele viram um saldo compartilhado entre as
              unidades membros, somando-se à cota própria desta unidade.
            </p>
          </div>
        </div>
      </section>

      <div class="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <button type="button" onclick={() => goto('/admin/unidades')} class="sd-btn sd-btn--neutro">
          Cancelar
        </button>
        <button type="button" onclick={salvar} disabled={salvando} aria-busy={salvando} class="sd-btn sd-btn--primario">
          {salvando ? 'Salvando…' : 'Salvar unidade'}
        </button>
      </div>
    </div>
  </SirgDesign>
</Content>
