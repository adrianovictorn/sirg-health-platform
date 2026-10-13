---
titulo: Migração de agendamentos ao mudar a data da cota
tags:
  - sirg/adr
status: aceita
data: 2026-10-13
substitui:
---

# 0008 — Migração de agendamentos ao mudar a data da cota

## Contexto

Em `/admin/cotas`, o administrador podia editar a data de uma cota "por data" mesmo com
agendamentos já feitos nela. A cota ia para a data nova e os pacientes continuavam marcados na
antiga: o saldo das duas datas ficava errado e ninguém avisava o paciente. O pedido foi perguntar,
na edição, se os agendamentos devem ir junto — e, se sim, fazer isso de verdade (remarcação real),
sem deixar o sistema pela metade se algo der errado.

Duas características do motor de saldo tornam o problema menos simples do que parece:

- **Só a cota com profissional deixa rastro no item** (`solicitacao_especialidade.cota_unidade_id`).
  Cota sem profissional consome sem registrar qual cota foi.
- **Várias cotas incidem juntas** sobre o mesmo agendamento (mensal do grupo, cota do dia, cota da
  especialidade). Mudar a data de uma muda, para as outras, onde o agendamento é contado.

## Decisão

**Quem migra.** Só cota de tipo `DATA` e origem manual. Cota com profissional: os itens que
apontam para ela pela FK. Cota sem profissional: os itens **por incidência** (mesma unidade ou
grupo, mesma especialidade, grupo ou "qualquer uma" se geral, data do agendamento = data da cota,
sem cota fixada). Isso alcança também agendamentos feitos por ADMIN/GESTOR, que nunca consumiram
cota; a prévia mostra um aviso de divergência quando a contagem difere de `quantidadeUtilizada`.
Migram em qualquer status — mudança de status não estorna cota — e o agendamento vai inteiro, com
todos os exames.

**O contador viaja com a cota.** A cota migrada e seu `quantidade_utilizada` mudam de data juntos;
nada é devolvido nem consumido nela. As **demais** cotas aplicáveis acompanham os itens por
`CotaUnidadeService.transferirUtilizacao`: devolve as vagas só da data antiga e consome as só da
nova, e não toca a cota que vale nos dois lados (a mensal do mesmo mês). Os métodos existentes do
motor (`incrementarUtilizacao`, `estornarUtilizacao`, `consumirVaga`, `devolverVaga`) não mudam.

**Tudo ou nada.** Uma só transação. Cota esgotada na data nova, capacidade global da especialidade
(`Especialidade.vagas`), cota de destino inativa ou de agenda: 409 com o motivo, nada alterado.
A cota é carregada com bloqueio pessimista e gravada com `@Version`; o lock otimista passou a
responder 409 com mensagem.

**Cota já existente na data nova: unir.** Soma `quantidadeTotal` e `quantidadeUtilizada` no
destino, reaponta **todas** as FKs dos itens (inclusive as antigas, de itens já desvinculados) e
exclui a cota de origem. A auditoria não usa FK para cota justamente por isso.

**Exame "de carona" com outra cota com profissional.** Se o agendamento migrado tem item
vinculado (FK) a outra cota com profissional que não vale na data nova, a migração é recusada com
409 ("Há agendamento com exame vinculado a outra cota com profissional (id=X) que não vale em
{data}. Migre também essa cota ou trate o agendamento antes."), sem dado de paciente. É
deliberado: mover o item sem a cota deixaria o consumo registrado numa cota de outra data.

**Fora da entrega:** cota de agenda (continua somente leitura), cota mensal (não muda de mês com
agendamentos: 409), troca de tipo de período com agendamentos (409), transporte sanitário (só um
aviso no diálogo).

**Efeitos.** `data_agendada_original` guarda a primeira data; `cota_migracao_data` guarda uma linha
por operação; o snapshot `fechamento_indicadores_dia` das duas datas é apagado e recalculado;
WhatsApp recebe uma `REMARCACAO` por agendamento com item `AGENDADO`; a federação recebe o evento
novo `agendamento-remarcado.{MUNICIPIO}`, depois do commit e só para município de origem
diferente do local (sem o fallback `BROADCAST`). Permissão `ADMIN`, como o `PUT /cotas/{id}`.

## Alternativas consideradas

- **Só avisar e deixar os agendamentos na data antiga.** É o comportamento que existia; deixa o
  saldo errado e o paciente sem aviso. Descartado.
- **Migrar sem remarcar de fato (só mover a cota).** Não resolve o paciente; os relatórios que
  leem `data_agendada` continuariam apontando a data antiga.
- **Excluir e recriar cada agendamento** (o jeito atual de remarcar). Reconsumiria e reestornaria
  todas as cotas e o teto, perderia a autoria e dispararia cancelamento + confirmação por
  paciente. Descartado.
- **Aceitar a mudança de mês para a cota mensal.** O ganho é pequeno e o risco de saldo errado em
  produção é alto; ficou para quando houver pedido.
- **Redebitar o teto financeiro na migração.** O teto é por mês da data agendada e tem regras
  próprias de bloqueio; mover valor entre meses dentro desta operação acopla duas coisas que o
  sistema mantém separadas (V107). Descartado nesta entrega.

## Consequências

- **Agendamentos de ADMIN/GESTOR (que nunca consumiram cota)** são tratados como consumidores por
  `transferirUtilizacao`, igual ao estorno atual de `deleteAgendamento`. Ao mudar de mês, a cota
  mensal ou de grupo de origem recebe devolução e a de destino consumo por esses agendamentos, e a
  migração pode ser recusada por falta de saldo no destino mesmo por causa deles. O aviso
  `divergente` da prévia só compara com a cota migrada, não com as demais.

- Mudar a data de uma cota com agendamentos passa **sempre** por `CotaMigracaoDataService`; o
  `PUT /api/cotas/{id}` devolve 409. Quem editar a data direto no banco quebra os contadores.
- **Teto financeiro não é redebitado.** Ao migrar para outro mês, o teto do mês antigo continua
  debitado e o do novo não é; o painel de custos (que soma pelo valor gravado no item) e o teto
  divergem nesses itens.
- **Transporte sanitário não é ajustado.** Quem tem viagem na data antiga precisa conferir.
- **Janela de corrida residual.** Um agendamento individual que leu as cotas aplicáveis antes do
  bloqueio da migração e consome depois do commit pode usar a vaga na data antiga. Operar a
  migração fora do pico reduz a chance; o consumo atômico (`consumirVaga`) segue protegendo os
  limites de cada cota.
- **Limite diário do WhatsApp.** Migrações grandes enfileiram muitas mensagens de uma vez; o que
  passar de `WHATSAPP_LIMITE_DIARIO` fica "não enviada".
- **Deploy nas duas VPS** por causa do binding novo do RabbitMQ (ver `INFRA.md`).
- Registro completo em [[03 - Entidades e Classes de Domínio]] §3.15e.
