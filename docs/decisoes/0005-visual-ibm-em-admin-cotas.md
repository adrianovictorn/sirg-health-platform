---
titulo: Visual IBM e filtros em /admin/cotas
tags:
  - sirg/adr
status: aceita
data: 2026-10-12
---

# 0005 — Visual IBM e filtros em /admin/cotas

## Contexto

A tela `/admin/cotas` só tinha um filtro (unidade) e a lista pode ter milhares de cotas,
porque cada agenda recorrente gera uma cota por ocorrência e por unidade solicitante. O
pedido foi incluir mais filtros e usar "o design da IBM", apresentado antes na sessão
a partir da coleção Awesome DESIGN.md.

Esse visual contraria o `regulacao-frontend/DESIGN.md`, que descreve um padrão único
(Tailwind, emerald, cantos arredondados) e diz para não criar estilo paralelo. O arquivo
da IBM é uma extração do CSS público de ibm.com e declara que não documenta tabelas de
dados nem filtros.

## Decisão

- **Filtros, todos no cliente e sem mudar backend:** status, situação do saldo, competência,
  intervalo de datas, especialidade, origem, tipo, profissional, executante e busca textual,
  além da unidade que já existia. Contador, "Limpar filtros", tags removíveis e filtros na
  URL (`replaceState`, no padrão de `custos` e `paciente/fila`). A lógica é pura e fica em
  `src/lib/cotasFiltro.js`, com testes; `cotas.js` não muda.
- **Regras:** cada filtro olha a cota antes do agrupamento, então a linha-resumo de agenda
  soma só as sobreviventes ("X de Y cotas"). O filtro de especialidade inclui cotas
  próprias, de grupo que a contenham e gerais, porque incidem juntas. Dado de apoio que
  não carregou (unidades, grupos, agendas) nunca esconde cota: ela fica visível e a tela
  avisa.
- **Visual:** a tela inteira no estilo IBM, com tokens `--ibm-*` escopados na própria
  página. Acento emerald (não o azul IBM) e fonte só como pilha de fallback, sem carregar
  nada. Os componentes de grupo ganharam a prop `variante`, com o padrão intacto.
- **Voltar de Editar:** a última query fica em `sessionStorage` e é restaurada só quando a
  navegação vem de `/admin/cotas/*` e a URL não traz filtro.

## Alternativas consideradas

- **Só os controles de filtro no estilo IBM:** teria mantido a tela coerente com as outras.
  O responsável pelo produto escolheu a tela inteira.
- **Carbon como biblioteca (`@carbon/styles`):** descartado; dependência nova e conflito com
  Tailwind 4 e `@tailwindcss/forms`.
- **Azul IBM como acento:** descartado para não pôr dois acentos na mesma tela, já que o
  cabeçalho do layout é emerald e não faz parte da tela.
- **Auto-hospedar ou instalar IBM Plex:** adiável; trocar a pilha por uma `@font-face` é
  aditivo.
- **Filtro e paginação no servidor:** fora de escopo; `GET /api/cotas` devolve tudo para
  ADMIN e não aceita parâmetros.
- **Guardar o filtro passando a query para as telas Nova e Editar:** descartado para não
  tocar em duas telas de formulário.

## Consequências

- A tela fica visualmente diferente de todas as outras telas internas, com o cabeçalho
  verde e o menu do layout acima de um corpo cinza e quadrado. Mudar isso exige tocar em
  `Content.svelte`, que é compartilhado.
- A lista continua sem paginação e continua carregando tudo num `GET`; os filtros não
  melhoram isso.
- Em Firefox desktop, `<input type="month">` vira campo de texto; o valor é validado antes
  de entrar no estado.
- **Em aberto:** o badge da linha-resumo de agenda mostra o `ativo` da agenda, e o filtro
  de status usa o `ativo` da cota; com o filtro "Inativa" pode aparecer uma linha-resumo
  "Ativa". A divergência já existia (desativar agenda não propaga para as cotas).
- **Em aberto:** a tela precisa de uma verificação visual com sessão ADMIN em 375, 768 e
  1280 px; a rolagem horizontal da página em 375 px não foi medida.
