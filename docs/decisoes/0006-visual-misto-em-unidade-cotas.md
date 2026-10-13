---
titulo: Visual misto, filtros e paginação em /unidade/cotas
tags:
  - sirg/adr
status: aceita
data: 2026-10-13
substitui:
---

# 0006 — Visual misto, filtros e paginação em /unidade/cotas

## Contexto

"Cotas da Unidade" (`/unidade/cotas`) mostra, para o administrador da unidade, as cotas que
limitam os agendamentos dela: as da própria unidade e as do grupo de unidades. A tela era
somente leitura, com um único filtro (`?grupo=`, enviado pelo dashboard), uma ordenação e
paginação fixa de 20 itens. Como cotas passadas nunca somem e uma agenda recorrente gera uma
cota por ocorrência, a lista só cresce, e esgotadas e inativas ficavam misturadas às
disponíveis (a inativa só com `opacity-50`, o que derruba o contraste).

O ADR 0005 registrou o visual IBM em `/admin/cotas`, e as telas `nova` e `editar` receberam
depois uma versão que mistura essa linguagem com a identidade do SIRG. O `DESIGN.md` diz para
não copiar o visual para outra tela sem uma decisão nova.

## Decisão

- **Visual:** `/unidade/cotas` adota a mistura SIRG + IBM de `admin/cotas/nova` e
  `admin/cotas/[id]/editar`, com os mesmos tokens `--ibm-*` num `<style>` escopado por
  `.cotas-ibm` (quarta cópia, com comentário de origem). Acento emerald, fonte só como pilha
  de fallback. O cabeçalho e o menu da página não mudam.
- **Organização de esgotadas e inativas, sem esconder nada:**
  - Três cartões clicáveis com contagem (Disponíveis, Esgotadas, Inativas) que funcionam
    como filtro. Os três formam uma partição que soma o total.
  - A lista ordena disponíveis primeiro, depois esgotadas, depois inativas, preservando a
    ordenação escolhida dentro de cada grupo.
  - Cada situação tem identidade própria (cor, forma e texto), repetida no cartão, no badge
    da linha e no filete lateral.
- **Filtros novos:** status, saldo, competência, intervalo de datas, especialidade, grupo de
  especialidades, tipo e busca textual, mais tags removíveis, "Limpar filtros" e "X de Y
  cotas". O `?grupo=` do dashboard continua com o mesmo significado.
- **Paginação, no navegador:** números de página, tamanho 10, 20 ou 50, e a página na URL.
- **Estado na URL:** filtros, ordem, página e tamanho. Valor inválido cai no padrão.
- **Segurança:** os filtros `unidadeId`, `profissionalId`, `executanteId` e `origem`, que
  existem na lista do ADMIN, são descartados nesta tela. A unidade continua vindo de
  `/users/me`.
- **Estrutura:** a lógica pura saiu da página para `src/lib/cotasUnidade.js`, com testes que
  incluem comparação diferencial contra o código original. A página migrou para runes.
  `cotas.js` e `cotasFiltro.js` não foram alterados.

## Alternativas consideradas

- **Paginar e filtrar no servidor:** descartado nesta rodada; muda o contrato de
  `GET /api/cotas/unidade/{id}`, que o dashboard e o agendar também usam.
- **Abas (Todas, Com saldo, Esgotadas, Inativas):** descartado; o operador pode não abrir a
  aba Esgotadas e achar que a unidade tem saldo.
- **Ocultar inativas por padrão, ou seção recolhível:** descartado; esconde cota por padrão e
  funciona como accordion, que o `DESIGN.md` desaconselha.
- **Agrupar por agenda, como em `/admin/cotas`:** fora do pedido; exigiria carregar agendas
  nesta tela e decidir o que a paginação conta.
- **Extrair o CSS compartilhado agora:** descartado; tocaria três telas em produção.

## Consequências

- Esgotada continua sendo o saldo **próprio** da cota (`saldoDisponivel <= 0`), não o saldo
  efetivo da unidade, que cruza cotas de escopos diferentes. A tela avisa: "Esgotada = saldo
  da própria cota zerado. Confira o período de cada cota."
- A ordenação "Período (mais antigo primeiro)" é a regra que sempre existiu; o rótulo antigo,
  "Data mais próxima", era impreciso.
- A página continua baixando todas as cotas; os filtros e a paginação não melhoram o volume.
- `/unidade/cotas` fica visualmente diferente de `/dashboard/unidade` e de `/admin/cotas`
  (lista), que ainda não foram alinhadas.
- A tela abre por URL para perfis sem item de menu (RECEPCAO, ENFERMEIRO, MEDICO), que veem
  só as cotas da unidade: `GET /api/cotas/grupo-unidades/{id}` retorna 403 para eles. Agora a
  tela avisa quando as cotas do grupo não carregam.
- **Pendência de segurança, fora do escopo:** `GET /api/cotas/grupo-unidades/{id}` aceita
  qualquer id para ADMIN_UNIDADE, sem passar por `UnidadeAcessoService`, e não há teste de
  segurança para os endpoints de cota da unidade.
- **Em aberto:** a verificação visual do estado "inativa" com dados e do painel de grupo
  expandido, que o banco local não permitiu, e a inconsistência de `AgendaService.desativar`,
  que não propaga `ativo = false` às cotas filhas.
- A landing (`sirg-landing`) espelha a tabela desta tela e ficou visualmente defasada.
