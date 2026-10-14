<script>
  import { onMount } from 'svelte';
  import { goto } from '$app/navigation';
  import { getApi, patchApi } from '$lib/api.js';
  import Content from '$lib/Content.svelte';
  import { toast } from 'svelte-sonner';
  import { listarUnidadesExecutantes } from '$lib/unidadesExecutantesApi.js';
  import { resumoDaLista } from '$lib/papelUnidade.js';
  import SirgDesign from '$lib/sirg-design/SirgDesign.svelte';
  import TagPapelUnidade from '$lib/sirg-design/TagPapelUnidade.svelte';

  let unidades = [];
  let loading = true;
  // Executantes configuradas com operadores (V110): desativar uma delas devolve os
  // operadores à visão municipal, então pede confirmação.
  let executantes = [];
  // Tag de papel (ADR 0012): sem o resumo de executantes não dá para saber o papel;
  // a tag some e a tela avisa, em vez de mostrar todas como "solicitante".
  let executantesFalhou = false;

  onMount(async () => {
    await Promise.all([carregarUnidades(), carregarExecutantes()]);
  });

  async function carregarExecutantes() {
    try {
      executantes = await listarUnidadesExecutantes();
      executantesFalhou = false;
    } catch {
      executantes = [];
      executantesFalhou = true;
    }
  }

  async function carregarUnidades() {
    loading = true;
    try {
      const res = await getApi('unidades');
      unidades = await res.json();
    } catch {
      toast.error('Erro ao carregar unidades.');
    } finally {
      loading = false;
    }
  }

  async function toggleAtivo(u) {
    const ex = executantes.find((e) => e.unidadeId === u.id);
    if (u.ativo && ex?.configurada && ex.operadores > 0
      && !confirm(`${u.nome} é uma unidade executante configurada. Ao desativá-la, os operadores vinculados a ela (pela lista ou pela lotação) deixam de ver os atendimentos desta executante. Desativar mesmo assim?`)) {
      return;
    }
    try {
      await patchApi(`unidades/${u.id}/status`);
      toast.success(u.ativo ? 'Unidade desativada.' : 'Unidade ativada.');
      await Promise.all([carregarUnidades(), carregarExecutantes()]);
    } catch {
      toast.error('Erro ao alterar status.');
    }
  }
</script>

<Content titleH1="Gestão de Unidades" page="/admin/unidades">
  <SirgDesign>
    <div class="space-y-6 p-4 md:p-6 lg:p-8">
      <section aria-labelledby="unidades-titulo" class="sd-painel">
        <div class="sd-painel-topo un-topo">
          <div class="min-w-0">
            <h2 id="unidades-titulo" class="sd-titulo">
              Unidades cadastradas
              {#if !loading}<span class="sd-contador ml-1 align-middle">{unidades.length}</span>{/if}
            </h2>
            <p class="sd-nota mt-1">
              O papel reflete a parametrização: a unidade só aparece como executante (ou Ambas) depois de ter local e
              grupo/especialidade configurados.
            </p>
          </div>
          <button type="button" class="sd-btn sd-btn--primario" on:click={() => goto('/admin/unidades/nova')}>
            <span aria-hidden="true">+</span> Nova unidade
          </button>
        </div>

        <div class="sd-painel-corpo space-y-4">
          {#if executantesFalhou}
            <p class="sd-aviso" role="status">
              Não foi possível carregar a parametrização das executantes; o papel das unidades não está disponível.
            </p>
          {/if}

          {#if loading}
            <p class="sd-texto-apoio" role="status">Carregando unidades…</p>
          {:else if unidades.length === 0}
            <p class="sd-vazio">Nenhuma unidade cadastrada.</p>
          {:else}
            <div class="sd-tabela-contorno">
              <table class="sd-tabela">
                <thead>
                  <tr>
                    <th scope="col">Nome</th>
                    <th scope="col">Papel</th>
                    <th scope="col">Código</th>
                    <th scope="col">CNES</th>
                    <th scope="col">Telefone</th>
                    <th scope="col">Status</th>
                    <th scope="col">Ações</th>
                  </tr>
                </thead>
                <tbody>
                  {#each unidades as u (u.id)}
                    <tr>
                      <th scope="row">{u.nome}</th>
                      <td>
                        {#if executantesFalhou}
                          <span class="sd-nota" title="Papel indisponível">—</span>
                        {:else}
                          <TagPapelUnidade resumo={resumoDaLista(u, executantes)} />
                        {/if}
                      </td>
                      <td>{u.codigo || '—'}</td>
                      <td class="un-num">{u.cnes || '—'}</td>
                      <td class="un-num">{u.telefone || '—'}</td>
                      <td>
                        <span class="sd-selo {u.ativo ? 'sd-selo--ok' : 'sd-selo--neutro'}">
                          {u.ativo ? 'Ativa' : 'Inativa'}
                        </span>
                      </td>
                      <td>
                        <div class="flex flex-wrap gap-2">
                          <button type="button" class="sd-btn sd-btn--neutro sd-btn--compacto"
                            aria-label="Editar {u.nome}"
                            on:click={() => goto(`/admin/unidades/${u.id}/editar`)}>
                            Editar
                          </button>
                          {#if u.tipo !== 'SOLICITANTE'}
                            <button type="button" class="sd-btn sd-btn--secundario sd-btn--compacto"
                              aria-label="Parametrizar executante {u.nome}"
                              on:click={() => goto(`/admin/unidades/${u.id}/executante`)}>
                              Parametrizar executante
                            </button>
                          {/if}
                          <button type="button"
                            class="sd-btn sd-btn--compacto {u.ativo ? 'sd-btn--perigo' : 'sd-btn--secundario'}"
                            aria-label="{u.ativo ? 'Desativar' : 'Ativar'} {u.nome}"
                            on:click={() => toggleAtivo(u)}>
                            {u.ativo ? 'Desativar' : 'Ativar'}
                          </button>
                        </div>
                      </td>
                    </tr>
                  {/each}
                </tbody>
              </table>
            </div>
          {/if}
        </div>
      </section>
    </div>
  </SirgDesign>
</Content>

<style>
  /* Topo do painel: título à esquerda, ação primária à direita (empilha no mobile). */
  .sd-painel-topo.un-topo {
    align-items: center;
  }
  .un-num {
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
  }
</style>
