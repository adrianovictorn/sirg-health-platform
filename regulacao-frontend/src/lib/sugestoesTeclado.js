// Navegação por teclado nas listas de sugestão (CID, especialidade, profissional,
// busca de paciente). A lista é o <ul> irmão do <input> dentro do mesmo contêiner,
// e cada opção é um <button>. Não muda como a lista abre, filtra ou fecha: só
// acrescenta setas, Enter e Esc ao que o mouse e o toque já fazem.

const CLASSE_ATIVA = "bg-emerald-100";

/**
 * Índice da opção ativa depois de uma tecla. -1 = nenhuma opção ativa.
 * Setas dão a volta; qualquer outra tecla mantém o índice.
 */
export function proximoIndice(atual, total, tecla) {
  if (total <= 0) return -1;
  if (tecla === "ArrowDown")
    return atual < 0 || atual >= total - 1 ? 0 : atual + 1;
  if (tecla === "ArrowUp") return atual <= 0 ? total - 1 : atual - 1;
  return atual >= total ? -1 : atual;
}

/**
 * Action para o <input> de uma lista de sugestão.
 * `aberta` espelha o estado da tela (para o aria-expanded); `fechar` é chamado no Esc.
 */
export function sugestoesTeclado(node, opcoes = {}) {
  let { aberta = false, fechar } = opcoes;
  let indice = -1;

  node.setAttribute("role", "combobox");
  node.setAttribute("aria-autocomplete", "list");
  node.setAttribute("aria-expanded", String(!!aberta));

  const botoes = () =>
    Array.from(node.parentElement?.querySelectorAll("ul button") ?? []);

  function marcar(lista) {
    lista.forEach((botao, i) => {
      const ativo = i === indice;
      botao.classList.toggle(CLASSE_ATIVA, ativo);
      botao.setAttribute("aria-selected", String(ativo));
      if (ativo) {
        botao.id = `${node.id || "sugestao"}-opcao-ativa`;
        node.setAttribute("aria-activedescendant", botao.id);
        botao.scrollIntoView({ block: "nearest" });
      } else if (botao.id.endsWith("-opcao-ativa")) {
        botao.removeAttribute("id");
      }
    });
    if (indice < 0) node.removeAttribute("aria-activedescendant");
  }

  function aoTeclar(event) {
    const lista = botoes();
    if (lista.length === 0) return;

    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      // A seta parte de onde o destaque está, pelo mesmo motivo do Enter abaixo.
      const atual = lista.findIndex(
        (b) => b.getAttribute("aria-selected") === "true",
      );
      indice = proximoIndice(atual, lista.length, event.key);
      marcar(lista);
    } else if (event.key === "Enter") {
      // Com a lista aberta, Enter nunca submete o formulário.
      event.preventDefault();
      // Pela marcação, não pelo índice: uma lista que chega do servidor pode
      // mudar de ordem depois da seta, e o destaque acompanha o elemento.
      const botao = lista.find(
        (b) => b.getAttribute("aria-selected") === "true",
      );
      if (!botao) return;
      indice = -1;
      // As telas selecionam por mousedown ou por click, conforme a lista.
      botao.dispatchEvent(
        new MouseEvent("mousedown", { bubbles: true, cancelable: true }),
      );
      botao.click();
    } else if (event.key === "Escape") {
      event.preventDefault();
      indice = -1;
      fechar?.();
    }
  }

  // Digitar refaz a lista: a opção ativa anterior deixa de valer.
  function aoDigitar() {
    indice = -1;
    marcar(botoes());
  }

  node.addEventListener("keydown", aoTeclar);
  node.addEventListener("input", aoDigitar);

  return {
    update(novas = {}) {
      ({ aberta = false, fechar } = novas);
      node.setAttribute("aria-expanded", String(!!aberta));
      if (!aberta) aoDigitar();
    },
    destroy() {
      node.removeEventListener("keydown", aoTeclar);
      node.removeEventListener("input", aoDigitar);
    },
  };
}
