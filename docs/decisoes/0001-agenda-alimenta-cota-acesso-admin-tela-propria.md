---
titulo: Agenda alimenta cota, acesso restrito a ADMIN, cota vira tela própria
tags:
  - sirg/adr
status: aceita
data: 2026-09-26
---

# 0001 — Agenda alimenta cota, acesso restrito a ADMIN, cota vira tela própria

## Contexto

O ofício da Central de Regulação de São Felipe (25/09/2026) pediu uma ferramenta robusta
de abertura de agenda, registrada em [[Agenda e Oferta]] com as regras R1–R10, acesso
previsto para `ADMIN`, `GESTOR` e `ADMIN_UNIDADE` (restrito à própria unidade — regra R5) e
uma `reservaRegulador` sem unidade titular (regra R4). A implementação desta entrega (fases
3–4 da especificação) tomou três decisões que divergem ou detalham o que estava
especificado, tomadas junto ao usuário durante o `/feature`:

1. Como a agenda se relaciona com o motor de saldo (`CotaUnidade`) já em produção.
2. Quem pode acessar o novo módulo "Liberação de Agenda" e a tela de cotas.
3. Se a criação/edição de cota continua em modal ou vira tela própria.

## Decisão

**1. A agenda gera cota, nunca a substitui.** Cada par (`AgendaOcorrencia` × unidade
solicitante) materializa uma `CotaUnidade` com `tipoPeriodo = DATA` e `origem = AGENDA`,
usando o mesmo motor de consumo/estorno/`@Version` que as cotas manuais (`CotaUnidadeService`,
inalterado em sua lógica de saldo). Cota de origem `AGENDA` não é editável na tela de cotas —
edita-se a agenda de origem, que aplica as mesmas regras de negócio (R2, R3) antes de propagar
a mudança para as cotas.

**2. Acesso ao módulo "Liberação de Agenda" (agenda e a tela de cotas) é restrito
exclusivamente à role `ADMIN`.** Isso diverge da regra R5 da especificação original, que
previa também `GESTOR` (acesso global) e `ADMIN_UNIDADE` (restrito à própria unidade, via
`UnidadeAcessoService`). Decisão explícita do usuário durante a revisão do
plano, não uma omissão: `AgendaController` e as rotas de escrita/edição de `CotaUnidade` usam
`@PreAuthorize("hasRole('ADMIN')")` puro, sem passar pelo escopo de unidade.

**3. Criar/editar uma `CotaUnidade` deixa de ser um modal em `/admin/cotas` e vira rota
própria** (`/admin/cotas/nova`, `/admin/cotas/{id}/editar`), com substituição direta — o
modal antigo foi removido, sem período de convivência. A edição de cota manual também deixou
de se limitar a `quantidadeTotal`/`ativo`: titular, escopo e período agora são editáveis
(sempre por substituição total do registro, nunca PUT parcial), mantendo o bloqueio de
reduzir `quantidadeTotal` abaixo do já utilizado.

## Alternativas consideradas

- **Reescrever o motor de saldo dentro da agenda** — descartado: duplicaria a regra mais
  sensível e mais testada do sistema (consumo, estorno, *optimistic locking*) e criaria dois
  números de saldo para reconciliar. Ver também a decisão 3.2 já registrada em
  [[Agenda e Oferta]].
- **Seguir a R5 tal como especificada** (`ADMIN` + `GESTOR` + `ADMIN_UNIDADE` restrito) —
  avaliada e descartada nesta entrega a pedido do usuário. Reabrir agenda para `GESTOR`/
  `ADMIN_UNIDADE` exige um ADR novo revertendo esta decisão, não uma mudança silenciosa de
  `@PreAuthorize`.
- **Manter o modal e só adicionar campos** — descartado porque o formulário de agenda tem
  volume e ramificações (individual/grupo, distribuição por N unidades, dias da semana) que
  não cabem confortavelmente num modal, e a própria especificação já previa "tela própria"
  para a fase 4.

## Consequências

- Fica **fora de escopo nesta entrega**: `reservaRegulador` (R4, vagas sem unidade titular).
  O modelo atual de `CotaUnidade` exige exatamente um titular (`CHECK ck_cota_titular_exclusivo`,
  V84) e a especificação não detalhava como representar uma cota sem titular sem violar essa
  regra. A agenda distribui vagas diretamente às unidades solicitantes (`AgendaDistribuicao`),
  sem pool central. Se a reserva do regulador for retomada, este ADR precisa ser revisto junto
  com o modelo de `CotaUnidade`.
- Também ficou fora desta entrega: adicionar/remover unidade solicitante depois que a agenda já
  foi materializada (edição de agenda só altera `vagasPorOcorrencia` das distribuições
  existentes); as quatro telas de fase 4 fora de agenda/cota (busca CNES e CPF/vínculo na UI de
  Unidades e Profissionais).
- Se um dia `GESTOR` ou `ADMIN_UNIDADE` precisar abrir agenda ou editar cota da própria
  unidade, é preciso um ADR novo revertendo a decisão 2 — não presuma esse acesso a partir da
  regra R5 original.
- Relatórios/exports que leem `CotaUnidadeViewDTO` continuam recebendo o mesmo formato,
  apenas com dois campos adicionais (`origem`, `agendaOcorrenciaId`, `agendaId`) — aditivo,
  não quebra consumidores existentes.

## Riscos conhecidos e aceitos (R9)

- **Janela de corrida em R9** (profissional não pode ter duas agendas ativas com horário
  sobreposto): a checagem de sobreposição em `AgendaOcorrenciaRepository.existeSobreposicao`
  é um `SELECT` sem lock, dentro da mesma transação do `POST /api/agendas`. Duas requisições
  concorrentes para o mesmo profissional/horário podem, em tese, passar na validação antes de
  qualquer uma persistir. Não há `UNIQUE`/`EXCLUDE` constraint no banco servindo de rede de
  segurança (diferente de `uk_agenda_ocorrencia_data`, que é por agenda). Aceito nesta entrega
  porque abrir agenda é uma operação administrativa pouco frequente e feita por um único
  operador por vez na prática — mas não é uma garantia de banco. Se abertura de agenda virar
  uma operação de alto volume/concorrência, este ponto precisa de um `EXCLUDE` constraint por
  profissional+data+faixa de horário.
- **R3 (desativar agenda) e R9 não são totalmente coerentes**: `AgendaService.desativar()` só
  marca `agenda.ativo = false`, sem cancelar as ocorrências já materializadas — elas continuam
  `ABERTA` e as cotas geradas continuam ativas. Como `existeSobreposicao` só considera
  `agenda.ativo = true`, uma agenda desativada some da checagem de R9, abrindo uma via teórica
  para dupla-agenda via "desativar + criar nova" no mesmo profissional/horário. Aceito como
  comportamento literal do R3 (que só fala em desativar a agenda, não em cancelar ocorrências
  materializadas) — se isso se mostrar um problema na prática, a correção é fazer `desativar()`
  cancelar também as ocorrências `ABERTA` futuras, reaproveitando `cancelarOcorrencia`.

---

Volta para [[Início]] · relacionados: [[Agenda e Oferta]], [[Mapa - Domínio]], [[Mapa - Segurança e Acesso]]
