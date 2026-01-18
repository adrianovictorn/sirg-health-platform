<script lang="ts">
  import RoleBasedMenu from '$lib/RoleBasedMenu.svelte';
  import UserMenu from '$lib/UserMenu.svelte';
  import { onMount } from 'svelte';
  import { listarEspecialidadesCatalogo, criarEspecialidadeCatalogo, listarGrupoRelatorio } from '$lib/especialidadesApi.js';
    import { patchApi, putApi } from '$lib/api';

  interface GrupoRelatorioSimpleViewDTO{
    id: number
    codigo: string
    nome: string
  }

  interface EspecialidadeViewDTO{
    id: number
    codigo: string
    nome: string
    categoria: string
    grupoRelatorio: GrupoRelatorioSimpleViewDTO
    ativo: boolean
  }

  type Categoria = 'ESPECIALIDADE_MEDICA' | 'EXAME_OU_PROCEDIMENTO' 

  let isLoading = $state(false);
  let grupoRelatorio = $state<GrupoRelatorioSimpleViewDTO[]>([])
  let nome = $state('');
  let codigo = $state('');
  let categoria= $state<Categoria>('ESPECIALIDADE_MEDICA');
  let grupoRelatorioId = $state<number | null> (null)
  let ativo = $state(true);
  let lista = $state<EspecialidadeViewDTO[]>([])
  let erro = $state<string | null>(null);
  let termoBusca = $state('');
  let modoEdicao = $state(false)
  let idEmEdicao = $state<number>(0)
  

  function normalize(s: string) {
    return (s || '')
      .toString()
      .normalize('NFD')
      .replace(/\p{Diacritic}/gu, '')
      .toLowerCase();
  }

  

  async function carregarLista() {
    try {
      const data = await listarEspecialidadesCatalogo();
      lista = data;
    } catch (e: any) {
      erro = e?.message || 'Falha ao carregar lista';
    }
  }

  async function atualizarEspecialidade(id: number) {
    const payload = {
      codigo: codigo,
      nome: nome,
      categoria: categoria,
      grupoRelatorioId: grupoRelatorioId,
      ativo: ativo
      
    }
    try {
      console.log(payload)
      const res = putApi(`catalog/especialidades/${id}`,payload)
      if((await res).ok){
        alert("Especialidade atualizada com sucesso !");
      }

      codigo = ''
      nome = ''
      categoria = 'ESPECIALIDADE_MEDICA'
      grupoRelatorioId = 0
      ativo = 
      modoEdicao = false
      idEmEdicao = 0
      carregarLista()
    } catch (error) {
      
    }
  }

  async function carregarGrupo() {
    try {
      const data = await listarGrupoRelatorio();
      grupoRelatorio = data;
    } catch (e:any) {
       erro = e?.message || 'Falha ao carregar lista';
    }
    
  }

  async function salvar(e: Event) {
    e.preventDefault();
    isLoading = true;
    erro = null;
    try {
      const res = await criarEspecialidadeCatalogo({ codigo: codigo?.trim() || undefined, nome: nome?.trim(), categoria, grupoRelatorioId, ativo });
      if (res.ok) {
        alert('Especialidade salva com sucesso');
        nome = '';
        codigo = '';
        categoria = 'ESPECIALIDADE_MEDICA';
        grupoRelatorioId = 0
        ativo = true;
        await carregarLista();
      } else {
        const body = await res.json().catch(() => ({}));
        alert(`Erro ao salvar: ${body.message || res.status}`);
      }
    } finally {
      isLoading = false;
    }
  }

  async function ativarOrDesativarEspecialidade(id: number) {
    let resposta = confirm("Deseja alterar esse registro ? ");

    if(resposta === true){
      try {
        const res = await patchApi(`catalog/especialidades/ativo/${id}`)
  
        if(res.ok){
          alert("Especialidade atualizada com sucesso !");
        }
      } catch (error) {
        throw Error("Erro ao atualizar especialidade !")
      }
      carregarLista()
    } else{
      return;
    }
  }

  function selecionarEspecialidade(especialidade: EspecialidadeViewDTO){
    idEmEdicao = especialidade.id
    modoEdicao = true
    nome = especialidade.nome
    codigo = especialidade.codigo
    ativo = especialidade.ativo
    categoria = especialidade.categoria as Categoria
    grupoRelatorioId = especialidade.grupoRelatorio?.id ?? null
    console.log(`ID VINDO DO GRUPO RELATÓRIO: ${grupoRelatorioId}`)
  } 

  onMount(()=> {
    carregarLista()
    carregarGrupo()
  })
</script>

<svelte:head>
  <title>Cadastrar Especialidade</title>
</svelte:head>

<div class="flex min-h-screen bg-gray-100">
  <RoleBasedMenu activePage="/cadastrar/especialidade" />
  <div class="flex-1 flex flex-col">
    <header class="bg-emerald-700 text-white shadow p-4 flex items-center justify-between">
      <h1 class="text-xl font-semibold">Cadastro de Especialidades</h1>
      <UserMenu />
    </header>

    <main class="flex-1 overflow-auto p-6">
      <div class="bg-white rounded-lg shadow-lg p-6">
        <h2 class="text-2xl font-bold text-emerald-800 mb-6">Nova Especialidade</h2>
        <form onsubmit={salvar}  class="space-y-4">
          <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div class="flex flex-col">
              <label class="text-sm font-medium text-gray-700 mb-1">Nome</label>
              <input class="border border-gray-300 rounded-lg p-2" bind:value={nome} required />
            </div>
            <div class="flex flex-col">
              <label class="text-sm font-medium text-gray-700 mb-1">Código (opcional)</label>
              <input class="border border-gray-300 rounded-lg p-2" bind:value={codigo} placeholder="Se vazio, será gerado do nome" />
            </div>
            <div class="flex flex-col">
              <label class="text-sm font-medium text-gray-700 mb-1">Grupo</label>
              <select name="" id="" class="border border-gray-300 rounded-lg p-2" bind:value={grupoRelatorioId}>
                <option value={null}>Selecione...</option>
                {#each grupoRelatorio as gr}
                  <option value={gr.id}>{gr.nome}</option>
                {/each}
              </select>
            </div>
            <div class="flex flex-col">
              <label class="text-sm font-medium text-gray-700 mb-1">Categoria</label>
              <select class="border border-gray-300 rounded-lg p-2" bind:value={categoria}>
                <option value="ESPECIALIDADE_MEDICA">Especialidade Médica</option>
                <option value="EXAME_OU_PROCEDIMENTO">Exame ou Procedimento</option>
              </select>
            </div>
            <div class="flex items-center">
              <label class="inline-flex items-center space-x-2">
                <input type="checkbox" bind:checked={ativo} />
                <span>Ativo</span>
              </label>
            </div>
          </div>
          {#if !modoEdicao}
            <button type="submit"  class="bg-emerald-700 text-white px-4 py-2 rounded hover:bg-emerald-800" disabled={isLoading}>
              {#if isLoading}Salvando...{:else}Salvar{/if}
            </button>
          {:else}
            <button type="button" onclick={() => atualizarEspecialidade(idEmEdicao)} class="bg-blue-700 text-white px-4 py-2 rounded hover:bg-blue-800" disabled={isLoading}>
              {#if isLoading}Salvando...{:else}Atualizar{/if}
            </button>
          {/if}
        </form>
      </div>

      <div class="bg-white rounded-lg shadow-lg p-6 mt-6">
        <h2 class="text-xl font-semibold text-emerald-800 mb-4">Especialidades Cadastradas</h2>
        <div class="flex items-center gap-3 mb-4">
          <input
            type="text"
            class="border border-gray-300 rounded-lg p-2 w-full md:w-96"
            placeholder="Buscar por nome, código ou categoria..."
            bind:value={termoBusca}
          />
          <button
            class="px-3 py-2 text-sm bg-gray-100 rounded border border-gray-300 hover:bg-gray-200"
            type="button"
            onclick={() => { termoBusca = ''; }}
          >Limpar</button>
        </div>
        {#if erro}
          <p class="text-red-500">{erro}</p>
        {/if}
        <div class="overflow-auto">
          <table class="w-full text-left text-sm">
            <thead>
              <tr class="border-b">
                <th class="py-2 px-2">Nome</th>
                <th class="py-2 px-2">Categoria</th>
                <th class="py-2 px-2">Grupo</th>
                <th class="py-2 px-2">Ativo</th>
                <th class="py-2 px-2 text-center">Ações</th>
              </tr>
            </thead>
            <tbody>
              {#each lista as e}
                <tr class="border-b hover:bg-gray-50">
                  <td class="py-2 px-2">{e.nome}</td>
                  <td class="py-2 px-2">{e.categoria}</td>
                  <td class="py-2 px-2">{e.grupoRelatorio?.nome ?? '-'}</td>
                  <td class="py-2 px-2">{e.ativo ? 'Sim' : 'Não'}</td>
                  <td>
                    <div>
                      <button type="button" aria-label="editar" title="Editar" onclick={() => selecionarEspecialidade(e)} class="cursor-pointer">
                        <svg class="w-6 h-6 text-gray-800 dark:text-gray-800" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="none" viewBox="0 0 24 24">
                          <path stroke="currentColor" stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M18 5V4a1 1 0 0 0-1-1H8.914a1 1 0 0 0-.707.293L4.293 7.207A1 1 0 0 0 4 7.914V20a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-5M9 3v4a1 1 0 0 1-1 1H4m11.383.772 2.745 2.746m1.215-3.906a2.089 2.089 0 0 1 0 2.953l-6.65 6.646L9 17.95l.739-3.692 6.646-6.646a2.087 2.087 0 0 1 2.958 0Z"/>
                        </svg>


                      </button>
                      {#if e.ativo}
                      <button type="button" aria-label="inativar" title="Desativar especialidade" class="cursor-pointer" onclick={() => ativarOrDesativarEspecialidade(e.id)}>
                        <svg class="text-green-800 dark:text-green-800 w-6 h-6" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="currentColor" viewBox="0 0 24 24">
                          <path fill-rule="evenodd" d="M4 4a2 2 0 1 0 0 4h16a2 2 0 1 0 0-4H4Zm0 6h16v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-8Zm10.707 5.707a1 1 0 0 0-1.414-1.414l-.293.293V12a1 1 0 1 0-2 0v2.586l-.293-.293a1 1 0 0 0-1.414 1.414l2 2a1 1 0 0 0 1.414 0l2-2Z" clip-rule="evenodd"/>
                        </svg>
                      </button>
                      {:else}
                      <button type="button" aria-label="inativar" title="Ativar especialidade" onclick={() => ativarOrDesativarEspecialidade(e.id)}>
                        <svg class=" text-red-800 dark:text-red-800 w-6 h-6" aria-hidden="true" xmlns="http://www.w3.org/2000/svg" width="24" height="24" fill="currentColor" viewBox="0 0 24 24">
                          <path fill-rule="evenodd" d="M4 4a2 2 0 1 0 0 4h16a2 2 0 1 0 0-4H4Zm0 6h16v8a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-8Zm10.707 5.707a1 1 0 0 0-1.414-1.414l-.293.293V12a1 1 0 1 0-2 0v2.586l-.293-.293a1 1 0 0 0-1.414 1.414l2 2a1 1 0 0 0 1.414 0l2-2Z" clip-rule="evenodd"/>
                        </svg>
                      </button>
                      
                    
                      {/if}
                    </div>
                  </td>
                </tr>
              {/each}
            </tbody>
          </table>
        </div>
      </div>
    </main>
  </div>
</div>
