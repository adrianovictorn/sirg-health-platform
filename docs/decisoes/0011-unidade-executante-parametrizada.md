---
titulo: Unidade executante parametrizada e recorte da tela de Atendimentos
tags:
  - sirg/adr
status: substituída
data: 2026-10-14
substitui:
---

# 0011 — Unidade executante parametrizada e recorte da tela de Atendimentos

> **Substituída pelo [[0013-executante-por-lotacao-falha-fechada-e-grupo-compartilhado]].**
> Mudaram os itens 3 (grupo e especialidade podem ser compartilhados) e 4 (lotação também
> vincula; USER sem executante configurada não vê nada). O resto continua valendo.

## Contexto

Desde a V109 o Usuário Padrão (USER) opera `/atendimentos` vendo o **município inteiro**. Locais
que realizam o atendimento (Centro de Fisioterapia, laboratório, ambulatório do hospital) queriam
ver só o que é deles. O agendamento não grava a unidade que executa: grava um `LocalAgendamento`
(tabela de destinos, sem relação com `Unidade`) e a especialidade. Os dois municípios já têm
agendamentos e locais em produção, e toda unidade foi migrada como `AMBOS` na V86.

## Decisão

1. **Executante é `Unidade`** (tipo `EXECUTANTE`/`AMBOS`), parametrizada com grupos inteiros e
   especialidades avulsas, ligada aos `LocalAgendamento` já cadastrados e aos operadores USER
   (vários por usuário). Nada é gravado no agendamento.
2. **Pertencimento de um item = especialidade da executante E local dela.** Agendamento sem local
   pertence só pela especialidade. Calculado na consulta, sem backfill: o histórico aparece por
   dedução.
3. **Uma especialidade pertence a uma executante só.** É o que torna a dedução inequívoca —
   inclusive para o agendamento sem local. Barrado por índice único (direto) e no service (grupo
   de A × avulsa de B). Mover especialidade de grupo pela tela de Especialidades é **tolerado**:
   o item passa a aparecer para as duas executantes, ninguém some.
4. **Só restringe executante configurada** (ao menos um local e um grupo ou especialidade).
   Operador sem vínculo, ou vinculado só a executante incompleta, segue com a visão municipal: o
   deploy não esvazia a tela de ninguém.
5. **O recorte é só de leitura.** O desfecho por id (`PATCH /api/atendimentos/{id}/…`) **não**
   confere a executante do item — decisão do usuário nesta entrega.
6. Vincular um local que já é de outra executante **move** o local.

## Alternativas consideradas

- **Gravar a executante no agendamento** (`agendamento_solicitacao.unidade_executante_id`):
  exato, mas exige backfill que adivinharia a executante dos agendamentos antigos e mexe em
  `/agendar`, onde a cota é consumida. Desnecessário com a exclusividade da especialidade.
- **Só a especialidade** (sem local): mais simples, mas não distingue o mesmo exame feito em dois
  lugares no futuro; o usuário pediu a ligação com os locais já cadastrados.
- **Reaproveitar `unidade.grupo_relatorio_id`**: esse campo é o titular de cota coletiva da
  unidade solicitante (V82). Misturar os papéis afetaria cota e relatório.
- **Restringir todo USER no deploy**: telas vazias até a parametrização (já aconteceu na §3.15c).
- **Bloquear o desfecho fora da executante (403)**: descartado nesta entrega por decisão do
  usuário.

## Consequências

- **Risco aceito:** um USER restrito ainda consegue confirmar, marcar falta, cancelar, informar
  coleta ou desfazer item de **outra** executante por chamada direta à API (ids sequenciais).
  Mitigação parcial: a autoria fica gravada (`atendimento_registrado_por_id`). Fechar isso é
  acrescentar a mesma checagem de pertencimento em `AtendimentoExecutanteService.carregar`.
- O recorte (`AtendimentoExecutanteRepository.FILTRO_EXECUTANTE`) tem de entrar em **toda**
  consulta nova da tela, inclusive contagem e bloco do paciente; senão vaza paciente.
- Item com `especialidade_id` nulo (legado) e item cujo local não está ligado a nenhuma
  executante ficam fora da visão do operador restrito; ADMIN e operador sem vínculo os veem.
- **Volta à visão municipal.** A regra do item 4 vale nos dois sentidos: uma executante que
  **deixa** de estar configurada — unidade desativada, tipo trocado para "Só solicitante", último
  local desvinculado, última especialidade excluída — devolve os operadores dela à visão do
  município inteiro, com CPF e CNS e o Relatório de Atendimento. Por isso as telas de Unidades
  (desativar, mudar tipo) e de Locais (desvincular, mover) pedem confirmação quando isso pode
  acontecer. Excluir uma especialidade não avisa. Trocar para "falha fechada" (operador vinculado
  a executante incompleta não vê nada) é uma mudança de regra, a decidir.
- Operador que perde o perfil USER ou é desativado continua vinculado até o administrador
  desmarcá-lo na parametrização (aparece marcado como inválido). Enquanto isso o vínculo não tem
  efeito: o recorte só vale para quem está usando o perfil USER.
- Cada município parametriza pela tela; nada vem por migration. Até lá, nada muda.
- Ver [[03 - Entidades e Classes de Domínio]] §3.15g.
