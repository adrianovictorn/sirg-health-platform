<script lang="ts">
    import { patchApi, patchApiData, postApi } from "$lib/api";
    import Content from "$lib/Content.svelte";
    import { toast } from "svelte-sonner";
    import { textoDoContadorSimples } from "$lib/listaPaginada.js";
    import { criarListaPaginada } from "$lib/listaPaginada.svelte.js";
    import Paginacao from "$lib/components/ListaPaginada/Paginacao.svelte";

    interface GrupoRelatorio {
        id: number
        codigo: string
        nome: string
        ativo: boolean
        direcionadoHospital: boolean
    }
    type formGrupo = {
        codigo: string,
        nome: string,
        ativo: boolean,
        direcionadoHospital: boolean
    }

    let form = $state<formGrupo>({
        codigo: "",
        nome: "",
        ativo: true,
        direcionadoHospital: false
    })

    let idSelecionado = $state(0)
    let modoEdicao = $state(false)

    // Filtros no servidor (GrupoRelatorioSpecification).
    const ESQUEMA = {
        q: "texto",
        status: ["ativo", "inativo"],
        direcionadoHospital: ["sim", "nao"]
    }

    const lista = criarListaPaginada({
        esquema: ESQUEMA,
        caminho: "grupo-relatorio/pagina",
        mensagemDeErro: "Erro ao carregar os grupos de relatório.",
        aoFalhar: (mensagem: string) => toast.error(mensagem)
    })

    // Depois de cadastrar, atualizar ou ativar/desativar, recarrega a página atual
    // (sem voltar à primeira) e mantém os filtros.
    async function buscarGrupos() {
        await lista.recarregar()
    }

    async function cadastrarGrupo(){
        const payload = {
            codigo: form.codigo,
            nome: form.nome,
            direcionadoHospital: form.direcionadoHospital
        }

        try {
            const res = await postApi(`grupo-relatorio/cadastrar`, payload)

            if(res.ok){
                alert("Novo Grupo Cadastrado com Sucesso !")
            }else{
                alert("Erro ao enviar dados ao servidor !");
            }

            form.codigo = ""
            form.nome = ""
            form.direcionadoHospital = false
            buscarGrupos()

        } catch (error) {

        }
    }

    async function ativarOrDesativarGrupo(id: number) {
        try {
            const res = await patchApi(`grupo-relatorio/ativarOrDesativar/${id}`)
            if(res.ok){
                alert("Grupo Relatório atualizado com sucesso !");
            }
            buscarGrupos()
        } catch (error) {

        }

    }

    async function atualizarGrupo(id: number) {
        const payload = {
            codigo: form.codigo,
            nome: form.nome,
            ativo: form.ativo,
            direcionadoHospital: form.direcionadoHospital
        }
        try{
            const res = await patchApiData(`grupo-relatorio/atualizar/${id}`, payload)

            if(!res.ok){
                throw new Error('Erro ao enviar dados de atualizar')
            }

            if(res.ok){
                alert("Paciente atualizado com sucesso !")
            }
            form.codigo = ""
            form.nome = ""
            form.direcionadoHospital = false
            modoEdicao = false
            buscarGrupos()
        }catch(error){
            throw new Error('Erro ao estabelecer conexão com o servidor !')
        }
    }

    function editarSelecionado(grupo: GrupoRelatorio){
        modoEdicao = true
        idSelecionado = grupo.id
        form.nome = grupo.nome
        form.codigo = grupo.codigo
        form.direcionadoHospital = grupo.direcionadoHospital

    }

    const textoContador = $derived(
        textoDoContadorSimples({
            pagina: lista.paginacao.pagina,
            tamanho: lista.paginacao.tamanho,
            totalElementos: lista.totalElementos,
            filtrado: lista.filtrado,
            singular: "grupo",
            plural: "grupos",
            vazio: "Nenhum grupo cadastrado"
        })
    )

    const ROTULOS: Record<string, Record<string, string>> = {
        status: { ativo: "Ativo", inativo: "Inativo" },
        direcionadoHospital: { sim: "Sim", nao: "Não" }
    }

    // Uma tag por filtro ativo; remover a tag é zerar a chave.
    const tags = $derived(
        [
            lista.filtros.q && { chave: "q", rotulo: `Busca: ${lista.filtros.q}` },
            lista.filtros.status && { chave: "status", rotulo: `Status: ${ROTULOS.status[lista.filtros.status]}` },
            lista.filtros.direcionadoHospital && {
                chave: "direcionadoHospital",
                rotulo: `Direcionado ao hospital: ${ROTULOS.direcionadoHospital[lista.filtros.direcionadoHospital]}`
            }
        ].filter(Boolean) as { chave: string, rotulo: string }[]
    )
</script>
<Content page="/cadastrar/grupo-relatorio" titleH1="Cadastrar Grupo de Relatório">
    <div class="tela-ibm flex-1 min-w-0 p-4 md:p-6 space-y-6">
        <section aria-label={modoEdicao ? "Editar grupo de relatório" : "Cadastrar grupo de relatório"} class="ibm-painel p-4 md:p-6">
            <h2 class="ibm-titulo-secao mb-4">{modoEdicao ? "Editar grupo" : "Novo grupo"}</h2>
            <form onsubmit={(e) => { e.preventDefault(); cadastrarGrupo(); }} class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 items-end">
                <div class="min-w-0">
                    <label for="grupo-nome" class="ibm-rotulo">Nome</label>
                    <input id="grupo-nome" type="text" placeholder="Digite o nome do Grupo" class="ibm-campo w-full" bind:value={form.nome} required>
                </div>
                <div class="min-w-0">
                    <label for="grupo-codigo" class="ibm-rotulo">Código</label>
                    <input id="grupo-codigo" type="text" class="ibm-campo w-full" placeholder="Código de identificação do Grupo" bind:value={form.codigo} required>
                </div>
                <div class="min-w-0">
                    <label for="grupo-hospital" class="ibm-rotulo">Direcionado ao Hospital</label>
                    <select id="grupo-hospital" bind:value={form.direcionadoHospital} class="ibm-campo w-full">
                        <option value={true}>Sim</option>
                        <option value={false}>Não</option>
                    </select>
                </div>
                {#if modoEdicao}
                    <div class="min-w-0">
                        <label for="grupo-ativo" class="ibm-rotulo">Ativo</label>
                        <select id="grupo-ativo" bind:value={form.ativo} class="ibm-campo w-full">
                            <option value={true}>Ativo</option>
                            <option value={false}>Inativo</option>
                        </select>
                    </div>
                {/if}

                <div class="min-w-0">
                    {#if modoEdicao}
                        <button type="button" onclick={()=> atualizarGrupo(idSelecionado)} class="ibm-btn ibm-btn--primario w-full">Atualizar</button>
                    {:else}
                        <button type="submit" class="ibm-btn ibm-btn--primario w-full">Cadastrar</button>
                    {/if}
                </div>
            </form>
        </section>

        <section aria-label="Filtros da lista de grupos de relatório" class="ibm-painel p-4 md:p-6 space-y-4">
            <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                <div class="min-w-0 sm:col-span-2">
                    <label for="filtro-busca" class="ibm-rotulo">Buscar</label>
                    <input
                        id="filtro-busca"
                        type="search"
                        value={lista.busca}
                        maxlength="80"
                        autocomplete="off"
                        placeholder="Nome ou código"
                        oninput={(e) => lista.definirBusca(e.currentTarget.value)}
                        class="ibm-campo w-full"
                    />
                </div>
                <div class="min-w-0">
                    <label for="filtro-status" class="ibm-rotulo">Status</label>
                    <select
                        id="filtro-status"
                        value={lista.filtros.status}
                        onchange={(e) => lista.definirFiltro("status", e.currentTarget.value)}
                        class="ibm-campo w-full"
                    >
                        <option value="">Todos os status</option>
                        <option value="ativo">Ativo</option>
                        <option value="inativo">Inativo</option>
                    </select>
                </div>
                <div class="min-w-0">
                    <label for="filtro-hospital" class="ibm-rotulo">Direcionado ao Hospital</label>
                    <select
                        id="filtro-hospital"
                        value={lista.filtros.direcionadoHospital}
                        onchange={(e) => lista.definirFiltro("direcionadoHospital", e.currentTarget.value)}
                        class="ibm-campo w-full"
                    >
                        <option value="">Todos</option>
                        <option value="sim">Sim</option>
                        <option value="nao">Não</option>
                    </select>
                </div>
            </div>

            <div class="ibm-resumo">
                <p class="ibm-contador" aria-live="polite">
                    {#if !lista.carregou}
                        Carregando…
                    {:else}
                        {#each textoContador.split(" · ") as parte, i (i)}
                            {#if i === 0}
                                <strong class="ibm-contador-destaque">{parte}</strong>
                            {:else}
                                <span class="ibm-contador-suave">&nbsp;· {parte}</span>
                            {/if}
                        {/each}
                    {/if}
                </p>
                {#if tags.length > 0 || lista.filtrado}
                    <div class="ibm-filtros-ativos">
                        {#each tags as tag (tag.chave)}
                            <button
                                type="button"
                                onclick={() => lista.definirFiltro(tag.chave, "")}
                                aria-label="Remover filtro: {tag.rotulo}"
                                class="ibm-tag"
                            >
                                <span>{tag.rotulo}</span>
                                <span aria-hidden="true">×</span>
                            </button>
                        {/each}
                        {#if lista.filtrado}
                            <button type="button" onclick={() => lista.limparFiltros()} class="ibm-btn ibm-btn--fantasma">Limpar filtros</button>
                        {/if}
                    </div>
                {/if}
            </div>
        </section>

        {#if !lista.carregou}
            <p class="ibm-suave-texto" role="status">
                {lista.falhou ? "Não foi possível carregar os grupos de relatório." : "Carregando grupos…"}
            </p>
        {:else}
            <div>
                <!-- svelte-ignore a11y_no_noninteractive_tabindex -->
                <div
                    class="ibm-tabela-contorno"
                    class:ibm-carregando={lista.carregando}
                    aria-busy={lista.carregando}
                    role="region"
                    aria-label="Lista de grupos de relatório, com rolagem horizontal"
                    tabindex="0"
                >
                    <table class="ibm-tabela w-full">
                        <thead>
                            <tr>
                                <th scope="col" class="text-left">Id</th>
                                <th scope="col" class="text-left">Nome</th>
                                <th scope="col" class="text-left">Código</th>
                                <th scope="col" class="text-left">Ativo</th>
                                <th scope="col" class="text-left">Direcionado ao Hospital</th>
                                <th scope="col" class="text-right">Ações</th>
                            </tr>
                        </thead>

                        <tbody>
                            {#each lista.itens as grupo (grupo.id)}
                                <tr>
                                    <td class="ibm-num">{grupo.id}</td>
                                    <td class="ibm-titular">{grupo.nome}</td>
                                    <td>{grupo.codigo}</td>
                                    <td>
                                        <span class="ibm-badge {grupo.ativo ? 'ibm-badge--ok' : 'ibm-badge--erro'}">
                                            {grupo.ativo ? "Ativo" : "Inativo"}
                                        </span>
                                    </td>
                                    <td>{grupo.direcionadoHospital ? "Sim" : "Não"}</td>
                                    <td class="text-right">
                                        <div class="flex flex-wrap justify-end gap-2">
                                            <button type="button" class="ibm-btn ibm-btn--terciario" aria-label="Editar {grupo.nome}" onclick={() => editarSelecionado(grupo)}>
                                                Editar
                                            </button>
                                            <button type="button" class="ibm-btn {grupo.ativo ? 'ibm-btn--perigo' : 'ibm-btn--terciario'}" aria-label="{grupo.ativo ? 'Desativar' : 'Ativar'} {grupo.nome}" onclick={() => ativarOrDesativarGrupo(grupo.id)}>
                                                {grupo.ativo ? "Desativar" : "Ativar"}
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            {:else}
                                <tr class="ibm-linha-vazia">
                                    <td colspan="6" class="ibm-vazio">
                                        {#if lista.filtrado}
                                            Nenhum grupo corresponde aos filtros.
                                            <button type="button" onclick={() => lista.limparFiltros()} class="ibm-btn ibm-btn--fantasma ml-1">
                                                Limpar filtros
                                            </button>
                                        {:else}
                                            Nenhum grupo cadastrado.
                                        {/if}
                                    </td>
                                </tr>
                            {/each}
                        </tbody>
                    </table>
                </div>
                <Paginacao
                    pagina={lista.paginacao.pagina}
                    totalPaginas={lista.totalPaginas}
                    tamanho={lista.paginacao.tamanho}
                    desabilitado={lista.carregando}
                    onPagina={lista.irParaPagina}
                    onTamanho={lista.definirTamanho}
                />
            </div>
        {/if}
    </div>
</Content>

<style>
  /*
   * Visual inspirado no Carbon (IBM), escopado a esta tela. Nenhum token vaza
   * para o resto do sistema. O acento segue o emerald do SIRG (--ibm-acento) e
   * pode ser trocado num único ponto. A fonte é uma pilha com fallback: nada é
   * baixado nem instalado.
   */
  .tela-ibm {
    --ibm-acento: #047857;
    --ibm-acento-hover: #065f46;
    --ibm-acento-ativo: #064e3b;
    --ibm-acento-suave: #d1fae5;
    --ibm-acento-suave-texto: #065f46;
    --ibm-fonte: 'IBM Plex Sans', 'Helvetica Neue', Arial, sans-serif;

    --ibm-tinta: #161616;
    --ibm-tinta-suave: #525252;
    --ibm-fundo: #f4f4f4;
    --ibm-camada: #ffffff;
    --ibm-campo: #f4f4f4;
    --ibm-linha: #e0e0e0;
    --ibm-borda-campo: #8c8c8c;
    --ibm-cabecalho: #e0e0e0;
    --ibm-hover: #ececec;
    --ibm-erro: #da1e28;
    --ibm-erro-texto: #a2191f;
    --ibm-ok-fundo: #a7f0ba;
    --ibm-ok-texto: #0e6027;
    --ibm-erro-fundo: #ffd7d9;
    --ibm-aviso-fundo: #fcf4d6;
    --ibm-aviso-borda: #f1c21b;

    background: var(--ibm-fundo);
    color: var(--ibm-tinta);
    font-family: var(--ibm-fonte);
    font-size: 14px;
    line-height: 1.29;
    letter-spacing: 0.16px;
  }
  .ibm-titulo-secao {
    font-size: 20px;
    font-weight: 400;
    line-height: 1.4;
    color: var(--ibm-tinta);
  }
  .ibm-rotulo {
    display: block;
    margin-bottom: 4px;
    font-size: 12px;
    line-height: 1.33;
    letter-spacing: 0.32px;
    color: var(--ibm-tinta-suave);
  }

  /* Painel (camada branca, contorno de 1 px, raio zero) */
  .ibm-painel {
    background: var(--ibm-camada);
    border: 1px solid var(--ibm-linha);
    border-radius: 0;
  }

  /* Campos: fundo cinza, borda inferior de 3:1, foco em contorno de 2 px */
  .ibm-campo {
    min-height: 40px;
    padding: 0 16px;
    border: 1px solid transparent;
    border-bottom: 1px solid var(--ibm-borda-campo);
    border-radius: 0;
    background-color: var(--ibm-campo);
    box-shadow: none;
    color: var(--ibm-tinta);
    font-family: inherit;
    font-size: 14px;
    letter-spacing: 0.16px;
  }
  select.ibm-campo {
    padding-right: 40px;
  }
  .ibm-campo::placeholder {
    color: var(--ibm-tinta-suave);
    opacity: 1;
  }
  .ibm-campo:focus,
  .ibm-campo:focus-visible {
    outline: 2px solid var(--ibm-acento);
    outline-offset: -2px;
    border-color: transparent;
    border-bottom-color: var(--ibm-borda-campo);
    box-shadow: none;
  }
  .ibm-campo:disabled {
    background-color: var(--ibm-linha);
    color: var(--ibm-tinta-suave);
    cursor: not-allowed;
  }

  /* Botões: raio zero, texto de ação */
  .ibm-btn {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-height: 40px;
    padding: 0 16px;
    border: 1px solid transparent;
    border-radius: 0;
    font-family: inherit;
    font-size: 14px;
    font-weight: 400;
    letter-spacing: 0.16px;
    white-space: nowrap;
    cursor: pointer;
  }
  .ibm-btn:focus-visible,
  .ibm-tag:focus-visible {
    outline: 2px solid var(--ibm-acento);
    outline-offset: 2px;
  }
  .ibm-btn--primario {
    background: var(--ibm-acento);
    color: #ffffff;
  }
  .ibm-btn--perigo {
    min-height: 32px;
    padding: 0 12px;
    border-color: var(--ibm-erro-texto);
    background: transparent;
    color: var(--ibm-erro-texto);
  }
  .ibm-btn--perigo:hover {
    background: var(--ibm-erro-texto);
    color: #ffffff;
  }
  .ibm-btn--primario:hover {
    background: var(--ibm-acento-hover);
  }
  .ibm-btn--primario:active {
    background: var(--ibm-acento-ativo);
  }
  .ibm-btn--terciario {
    min-height: 32px;
    padding: 0 12px;
    border-color: var(--ibm-acento);
    background: transparent;
    color: var(--ibm-acento);
  }
  .ibm-btn--terciario:hover {
    background: var(--ibm-acento-hover);
    border-color: var(--ibm-acento-hover);
    color: #ffffff;
  }
  .ibm-btn--terciario:active {
    background: var(--ibm-acento-ativo);
    border-color: var(--ibm-acento-ativo);
    color: #ffffff;
  }
  .ibm-btn--fantasma {
    min-height: 32px;
    padding: 0 8px;
    background: transparent;
    color: var(--ibm-acento);
    text-decoration: underline;
    text-underline-offset: 3px;
  }
  .ibm-btn--fantasma:hover {
    background: var(--ibm-hover);
    color: var(--ibm-acento-hover);
  }

  /* Contador, tags de filtro e mensagens */
  .ibm-resumo {
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding-top: 12px;
    border-top: 1px solid var(--ibm-linha);
  }
  .ibm-contador {
    min-height: 20px;
    font-size: 14px;
    color: var(--ibm-tinta);
  }
  .ibm-contador-destaque {
    font-weight: 600;
  }
  .ibm-contador-suave {
    margin-left: 0.25em;
    color: var(--ibm-tinta-suave);
  }
  .ibm-filtros-ativos {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
  }
  .ibm-tag {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    min-height: 28px;
    padding: 0 12px;
    border: 0;
    border-radius: 9999px;
    background: var(--ibm-acento-suave);
    color: var(--ibm-acento-suave-texto);
    font-family: inherit;
    font-size: 12px;
    letter-spacing: 0.32px;
    cursor: pointer;
  }
  .ibm-tag:hover {
    background: #a7f3d0;
  }
  .ibm-suave-texto {
    color: var(--ibm-tinta-suave);
    font-size: 14px;
  }

  /* Tabela (extrapolação do Carbon: cabeçalho cinza, linhas de 1 px, hover cinza) */
  .ibm-tabela-contorno {
    overflow-x: auto;
    background: var(--ibm-camada);
    border: 1px solid var(--ibm-linha);
    border-radius: 0;
  }
  .ibm-carregando {
    opacity: 0.55;
    transition: opacity 120ms;
  }
  .ibm-tabela-contorno:focus-visible {
    outline: 2px solid var(--ibm-acento);
    outline-offset: -2px;
  }
  .ibm-tabela {
    min-width: 960px;
    font-size: 14px;
  }
  .ibm-tabela th {
    padding: 12px 16px;
    background: var(--ibm-cabecalho);
    color: var(--ibm-tinta);
    font-size: 14px;
    font-weight: 600;
    white-space: nowrap;
  }
  .ibm-tabela td {
    padding: 8px 16px;
    border-bottom: 1px solid var(--ibm-linha);
    vertical-align: middle;
    color: var(--ibm-tinta);
  }
  .ibm-tabela tbody tr:last-child td {
    border-bottom: 0;
  }
  .ibm-tabela tbody tr:hover > td {
    background: var(--ibm-hover);
  }
  .ibm-tabela .ibm-linha-vazia:hover > td {
    background: var(--ibm-camada);
  }
  .ibm-tabela td.ibm-vazio {
    padding: 48px 16px;
    text-align: center;
    color: var(--ibm-tinta-suave);
  }
  .ibm-titular {
    font-weight: 600;
  }
  .ibm-num {
    font-variant-numeric: tabular-nums;
  }

  /* Badges (pílula, como o Carbon faz para status) */
  .ibm-badge {
    display: inline-flex;
    align-items: center;
    min-height: 20px;
    padding: 0 8px;
    border: 1px solid transparent;
    border-radius: 9999px;
    font-size: 12px;
    font-weight: 400;
    line-height: 1.33;
    letter-spacing: 0.32px;
    white-space: nowrap;
  }
  .ibm-badge--ok {
    background: var(--ibm-ok-fundo);
    color: var(--ibm-ok-texto);
  }
  .ibm-badge--erro {
    background: var(--ibm-erro-fundo);
    color: var(--ibm-erro-texto);
  }</style>
