---
titulo: Atendimentos por paciente, CPF e CNS completos e tela de Quantitativos
tags:
  - sirg/adr
status: aceita
data: 2026-10-14
substitui:
---

# 0010 — Atendimentos por paciente, CPF e CNS completos e tela de Quantitativos

## Contexto

A tela `/atendimentos` (módulo do Usuário Padrão, o operador da unidade executante) nasceu com
três características que o uso mostrou erradas:

- **Muita coisa acoplada.** Totais do dia, tabela por unidade solicitante, lista de pacientes e
  relatório na mesma tela. O trabalho do operador é a lista; os números são consulta.
- **Uma linha por item.** Paciente com três exames aparecia em três linhas, às vezes cortado
  entre duas páginas, e cada exame era confirmado num clique separado.
- **CPF e CNS mascarados** (2 e 4 últimos dígitos), no mesmo padrão da Agenda do Dia do ADMIN.
  O operador confere o documento do paciente no balcão e precisava do número inteiro.

## Decisão

**CPF e CNS completos — decisão consciente de privacidade.** Em `/atendimentos` e na Agenda do
Dia consolidada do ADMIN, CPF e CNS saem como estão gravados, sem máscara. Com isso, todo perfil
USER das duas instâncias vê nome, CPF e CNS de todos os pacientes agendados do município, em
qualquer data consultada; o ADMIN também. O Relatório de Atendimento (Excel) já entregava isso
para até 31 dias. Os campos passam a se chamar `cpf`/`cns` (antes `cpfMascarado`/`cnsMascarado`)
e o método `mascarar` foi removido: um campo chamado "mascarado" carregando o número inteiro
induziria alguém a logá-lo ou expô-lo.

**Busca só por documento completo.** Nome por trecho; CPF (11 dígitos) ou CNS (15 dígitos) só
casam completos, com ou sem pontuação. Sem a máscara, o motivo original (impedir reconstruir
dígitos ocultos) acabou, mas a regra fica: um trecho de número listaria pacientes que o operador
não está atendendo. É diferente, de propósito, da Fila de Espera, que busca por trecho.

**Lista por paciente.** `GET /api/atendimentos` pagina por **ficha** (`solicitacao.id`), nunca
por nome — dois homônimos são dois blocos. Duas consultas, no padrão da Fila de Espera: a
primeira pagina as fichas, a segunda traz os itens delas. Com a tela filtrada por uma agenda
(grupo), o bloco só tem os itens dessa agenda. O total e a paginação contam **pacientes**; nos
Totais do dia, "Pacientes" conta fichas e agendados, confirmados, faltas e cancelados continuam
contando **itens**, como o relatório.

**Filtro "Situação" seleciona pacientes.** Traz quem tem ao menos um item naquela situação e
mostra o bloco inteiro, sem recortar. A unidade de trabalho é o paciente no balcão, e o bloco não
muda de composição depois de uma ação.

**"Confirmar marcados" é N confirmações individuais.** O navegador chama, em sequência, o mesmo
`PATCH /api/atendimentos/{id}/confirmar` de sempre. O efeito é idêntico a confirmar um a um
(transação, trava de linha e autoria por item; nada de cota, teto ou evento), e uma falha não
desfaz os que já gravaram: a tela lista o que não gravou e recarrega a página. Falta e
cancelamento continuam por item, com observação ou motivo próprio.

**Quantitativos em tela própria.** `/atendimentos/quantitativos` (menu "Quantitativos", USER e
ADMIN) recebe os Totais do dia e a tabela por unidade, com atualização a cada 60 s. Clicar numa
unidade abre `/atendimentos?data=…&grupo=…&unidadeId=…` (ou `semUnidade=true`), que lê esses
parâmetros na entrada.

## Alternativas descartadas

- **Endpoint de confirmação em lote.** Uma ida ao servidor só, mas exige um orquestrador fora de
  transação (com `@Transactional` no laço, a falha de um item marcaria tudo para rollback; com o
  laço no próprio service, a auto-invocação pularia o proxy) e um teste com commit real. Fica
  como evolução se o laboratório reclamar de lentidão.
- **Manter `cpfMascarado`/`cnsMascarado` com valor completo** na Agenda do Dia, para evitar que um
  navegador com JavaScript antigo em cache mostre "—" até recarregar. Nome enganoso permanente.
- **Filtro de situação que esconde itens.** "Confirmar marcados" pareceria cobrir o paciente todo
  quando não cobre.
- **Agrupar por agendamento.** Paciente com dois agendamentos no mesmo dia viraria dois blocos.

## Consequências

- `GET /api/atendimentos` devolve `Page<AtendimentoPacienteViewDTO>`; o item
  (`AtendimentoViewDTO`) perdeu nome, documentos, nascimento e unidade. As respostas das ações
  deixaram de trafegar documento.
- `SegurancaEndpointsIT` deixou de exigir que a lista não contenha CPF/CNS; continua exigindo
  isso do resumo.
- A Agenda do Dia do ADMIN mudou de contrato (`cpf`/`cns`). Frontend e backend sobem juntos.
- Confirmar 20 exames leva cerca de um a dois segundos; fechar a aba no meio deixa o resto
  agendado.
- Visual das duas telas: emenda do ADR 0009.
