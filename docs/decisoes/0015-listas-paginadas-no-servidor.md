---
titulo: Listas administrativas paginadas e filtradas no servidor
tags:
  - sirg/adr
status: aceita
data: 2026-10-23
---

# 0015 — Listas administrativas paginadas e filtradas no servidor

## Contexto

`/admin/cotas`, `/admin/unidades`, `/cadastrar/grupo-relatorio` e `/admin/profissionais`
carregavam a lista inteira e filtravam no navegador (cotas: 12 filtros em `cotasFiltro.js`).
Com a base crescendo isso pesa, e o contador "20 de 174" confundia quando havia filtro:
parecia 20 itens de um universo de 174, sem dizer que 174 era o total sem filtro.
`/admin/profissionais` pedia `size=50` fixo e sem controles — quem passasse de 50
profissionais não via o resto.

Em `/admin/cotas` há duas restrições a mais. As cotas de uma agenda (N ocorrências × M
unidades) aparecem como **uma linha-resumo** com a soma de total, usado e saldo. E a lista
plana, com as cotas de USF01, USF02... uma abaixo da outra, obrigava o operador a ler tudo
e a conferir a unidade linha a linha — fácil de errar.

## Decisão

1. **Filtro, ordem e página no servidor**, com `Specification` e `PaginaDTO` próprio
   (não o `Page` do Spring). Endpoints novos e explícitos, só ADMIN: `/api/cotas/pagina`,
   `/api/unidades/pagina`, `/api/grupo-relatorio/pagina`. Os endpoints de lista completa
   **não mudam** — têm outros consumidores (selects, outras telas). `profissionais/buscar`
   já era paginado e só ganhou parâmetros opcionais.
2. **Em `/admin/cotas` a unidade de paginação é o titular** — uma unidade, ou um grupo de
   unidades dono de cota compartilhada. Cada página traz N titulares **completos**, cada um
   num bloco com todas as suas cotas que passaram pelo filtro (`CotaUnidadeBlocoDTO`), e o
   cabeçalho do bloco mostra nome, cotas, total, utilizado e o "saldo das cotas listadas" (soma
   bruta de total − utilizado, inclusive inativas; não é o saldo efetivo do motor de cota). O
   contador mostra unidades e, entre parênteses, cotas. Filtros que não são de unidade mostram
   todas as unidades com cota que passou; o filtro de unidade mostra só a dela (mais o bloco
   do grupo dela, como o filtro sempre fez). Unidade sem cota não aparece. Cota sem unidade e
   sem grupo (dado inconsistente) vai a um bloco "Sem titular", por último: nenhuma cota some.
   **A agenda se desfaz por unidade**: dentro de cada unidade há uma linha-resumo por agenda,
   somando só as cotas daquela unidade.
3. **Ordem**: titulares em ordem alfabética (unidades e grupos misturados; `Collator` pt-BR,
   que ignora acento e caixa). Dentro de cada titular: ativas antes de inativas (na agenda vale
   `agenda.ativo`, como a linha-resumo já mostrava), data de referência mais recente primeiro
   (`dataEspecifica` se for por data, dia 1 do `periodo` se for mensal; agenda = a mais
   recente), cota sem data por último, desempate pelo maior id. Revisão (23/10/2026): a
   primeira versão ordenava a lista inteira por ativas e data, com as inativas ao fim da
   lista completa; passou a valer "inativas ao fim **de cada unidade**".
4. **Estado na URL** (`pagina` 1-based, `tamanho`, filtros), com lista branca na leitura.
   CPF nunca é filtro nem vai à URL.
5. **Visual IBM puro** nessas quatro telas, em CSS escopado na página, com rodapé de
   paginação reutilizável. `/admin/unidades` sai da casca `SirgDesign` (ADR 0012) só na lista.
6. **Escrita de grupos de relatório passa a exigir ADMIN** (estava aberta a qualquer
   autenticado); a leitura continua aberta.

## Alternativas consideradas

- **Paginar por cota individual** em `/admin/cotas`: parte a agenda entre páginas e a soma
  da linha-resumo fica errada. Descartada.
- **Paginar por item e só desenhar cabeçalhos por unidade**: uma unidade ficaria partida
  entre páginas e o total do cabeçalho enganaria (40 cotas sobre 12 linhas). Descartada.
- **Cota de grupo repetida dentro de cada unidade do grupo**: duplica a linha e dobra o saldo
  agregado. Descartada: o grupo é um bloco próprio.
- **Mudar o formato dos endpoints de lista para `Page`**: quebraria `grupo-relatorio/listar`
  (mais de uma dúzia de consumidores). Descartada em favor de rotas novas.
- **Ordenar/paginar no banco** com expressão de data e `GROUP BY` por agenda: depende de
  função do dialeto e de contagem em duas consultas. Como são centenas de cotas, a
  ordenação e a paginação acontecem em memória sobre uma projeção leve (id, agenda, ativo,
  data) e só as cotas da página são carregadas por inteiro. **Se a tabela crescer para
  dezenas de milhares de linhas, reavaliar** (índice em `data_especifica`/`periodo` e
  consulta agregada).
- **Busca sem acento** (`unaccent`): exigiria a extensão do PostgreSQL nas duas VPS. A busca
  do servidor ignora a caixa mas **diferencia acento** — a busca antiga do navegador ignorava
  os dois.
- **`SirgDesign` nas telas novas**: descartado a pedido; o visual ainda será refinado.

## Consequências

- A soma de uma agenda **inteira** (todas as unidades) deixou de aparecer em uma linha só:
  cada unidade vê a soma das suas cotas daquela agenda. Quem comparava esse total com outra
  tela verá números diferentes.
- `tamanho` na URL de `/admin/cotas` passa a significar unidades por página (10/20/50).
- Uma unidade com muitas cotas faz a página grande; o usuário escolhe 10 por página. Reavaliar
  se a base chegar a dezenas de milhares de cotas.
- Filtros de `/admin/cotas` existem em dois lugares até a limpeza: `CotaUnidadeSpecification`
  (efetivo) e `filtrarCotas`/`opcoesDeFiltro` em `cotasFiltro.js`, que a tela não usa mais
  mas ainda têm testes. Apagar as duas funções é decisão à parte.
- Mudar uma regra de filtro de cota é mudar a `Specification`, não o navegador.
- Uma cota pode trocar de página entre duas requisições (saldo muda a cada agendamento
  quando o filtro de saldo está ativo). Aceito.
- Frontend e backend precisam ser publicados juntos nas duas VPS: o front novo chama
  endpoints que o backend antigo não tem.
