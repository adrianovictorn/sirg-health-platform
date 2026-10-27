/**
 * Estado de uma lista administrativa paginada e filtrada no servidor, para as
 * telas /admin/unidades, /cadastrar/grupo-relatorio e /admin/profissionais.
 *
 * A URL é a fonte da verdade: filtros e página são lidos dela, e mudar qualquer
 * um é navegar (`replaceState`). Assim recarregar, voltar e compartilhar o link
 * funcionam, e clicar no item do menu (URL sem parâmetros) limpa tudo sozinho.
 * Mudar um filtro volta para a primeira página; a busca textual espera 300 ms de
 * pausa na digitação antes de consultar o servidor.
 *
 * Deve ser chamado durante a inicialização do componente (usa $effect).
 * A lógica pura (leitura/escrita da URL, textos) vive em `listaPaginada.js`.
 */
import { goto } from "$app/navigation";
import { page } from "$app/state";
import { getApi } from "$lib/api.js";
import {
  filtrosVazios,
  haFiltrosPorEsquema,
  lerFiltrosPorEsquema,
  lerPaginacao,
  montarQuery,
  montarQueryDaApi,
  sanitizarFiltroPorEsquema,
  serializarFiltrosPorEsquema,
} from "./listaPaginada.js";

const ATRASO_DA_BUSCA_MS = 300;

/** Resposta `PaginaDTO` do backend ({ conteudo, totalElementos, totalPaginas }). */
function lerPaginaDto(json) {
  return {
    itens: json.conteudo ?? [],
    totalElementos: json.totalElementos ?? 0,
    totalPaginas: json.totalPaginas ?? 0,
  };
}

/**
 * @param {object} opcoes
 * @param {object} opcoes.esquema filtros da tela (ver `filtrosVazios`)
 * @param {string} opcoes.caminho endpoint paginado, sem query (ex.: "unidades/pagina")
 * @param {(json: any) => {itens: any[], totalElementos: number, totalPaginas: number}} [opcoes.lerPagina]
 *   adapta a resposta; o padrão lê o `PaginaDTO`
 * @param {string} [opcoes.mensagemDeErro]
 * @param {(mensagem: string) => void} [opcoes.aoFalhar]
 */
export function criarListaPaginada({
  esquema,
  caminho,
  lerPagina = lerPaginaDto,
  mensagemDeErro = "Erro ao carregar a lista.",
  aoFalhar = () => {},
}) {
  let itens = $state.raw([]);
  let totalElementos = $state(0);
  let totalPaginas = $state(0);
  let carregou = $state(false);
  let carregando = $state(true);
  let falhou = $state(false);

  let requisicaoAtual = 0;
  let temporizador = null;

  const filtros = $derived(lerFiltrosPorEsquema(page.url.searchParams, esquema));
  const paginacao = $derived(lerPaginacao(page.url.searchParams));
  // Campo de busca: segue a URL (menu, voltar) e é reescrito a cada tecla até o debounce navegar.
  let busca = $derived(filtros.q ?? "");
  const filtrado = $derived(haFiltrosPorEsquema(filtros, esquema));
  const query = $derived(
    montarQueryDaApi(serializarFiltrosPorEsquema(filtros, esquema), paginacao),
  );

  function navegar(novosFiltros, novaPaginacao) {
    const naUrl = montarQuery(serializarFiltrosPorEsquema(novosFiltros, esquema), novaPaginacao);
    goto(naUrl ? `?${naUrl}` : page.url.pathname, {
      replaceState: true,
      keepFocus: true,
      noScroll: true,
    });
  }

  // Uma resposta atrasada de uma consulta antiga é descartada (contador).
  async function carregar(consulta) {
    const minha = ++requisicaoAtual;
    const obsoleta = () => minha !== requisicaoAtual;
    carregando = true;
    falhou = false;
    try {
      const res = await getApi(`${caminho}?${consulta}`);
      if (!res.ok) {
        const corpo = await res.json().catch(() => ({}));
        throw new Error(corpo.message ?? mensagemDeErro);
      }
      const pagina = lerPagina(await res.json());
      if (obsoleta()) return;
      itens = pagina.itens;
      totalElementos = pagina.totalElementos;
      totalPaginas = pagina.totalPaginas;
      carregou = true;
      // Página que deixou de existir (ex.: registros removidos): vai para a última.
      const ultima = Math.max(pagina.totalPaginas - 1, 0);
      if (pagina.itens.length === 0 && paginacao.pagina > ultima) {
        navegar(filtros, { ...paginacao, pagina: ultima });
      }
    } catch (e) {
      if (obsoleta()) return;
      falhou = true;
      aoFalhar(e.message || mensagemDeErro);
    } finally {
      if (!obsoleta()) carregando = false;
    }
  }

  $effect(() => {
    carregar(query);
  });

  $effect(() => () => clearTimeout(temporizador));

  return {
    get itens() {
      return itens;
    },
    get totalElementos() {
      return totalElementos;
    },
    get totalPaginas() {
      return totalPaginas;
    },
    get carregou() {
      return carregou;
    },
    get carregando() {
      return carregando;
    },
    get falhou() {
      return falhou;
    },
    get filtros() {
      return filtros;
    },
    get paginacao() {
      return paginacao;
    },
    get filtrado() {
      return filtrado;
    },
    get busca() {
      return busca;
    },

    definirFiltro(chave, valor) {
      // Busca ainda no debounce: vale o que está no campo, não o `q` antigo da URL.
      const q = temporizador !== null && chave !== "q" ? sanitizarFiltroPorEsquema(esquema, "q", busca) : filtros.q;
      clearTimeout(temporizador);
      temporizador = null;
      navegar(
        { ...filtros, q, [chave]: sanitizarFiltroPorEsquema(esquema, chave, valor) },
        { ...paginacao, pagina: 0 },
      );
    },
    definirBusca(valor) {
      busca = valor;
      clearTimeout(temporizador);
      temporizador = setTimeout(() => {
        temporizador = null;
        navegar(
          { ...filtros, q: sanitizarFiltroPorEsquema(esquema, "q", valor) },
          { ...paginacao, pagina: 0 },
        );
      }, ATRASO_DA_BUSCA_MS);
    },
    limparFiltros() {
      clearTimeout(temporizador);
      navegar(filtrosVazios(esquema), { ...paginacao, pagina: 0 });
    },
    irParaPagina(pagina) {
      navegar(filtros, { ...paginacao, pagina });
    },
    definirTamanho(tamanho) {
      navegar(filtros, { pagina: 0, tamanho });
    },
    /** Recarrega a página atual (depois de criar, editar, ativar ou excluir). */
    recarregar() {
      return carregar(query);
    },
  };
}
