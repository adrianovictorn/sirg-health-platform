---
titulo: Mapa - Domínio
tags:
  - sirg/mapa
gerado: manual
---

# Mapa — Domínio

O vocabulário do negócio. Detalhe campo a campo em [[03 - Entidades e Classes de Domínio]]; tabelas e relacionamentos em [[06 - Banco de Dados]].

## O fluxo central

Uma **Solicitação** é o paciente e seus dados cadastrais. Dentro dela há uma ou mais **SolicitacaoEspecialidade** — cada item pedido (consulta ou exame), com status e prioridade próprios. Quando o item é marcado, nasce um **AgendamentoSolicitacao** (local, data, turno), e esse agendamento **consome cota**.

`Solicitacao` → `SolicitacaoEspecialidade` → `AgendamentoSolicitacao`

Ver [[diagrama-solicitacoes]] e, para o passo a passo, [[05 - Fluxos do Sistema]].

## Cotas

**CotaUnidade** é o limite de marcações por titular e período. O titular é **exclusivamente** uma unidade **ou** um grupo (`GrupoRelatorio` reaproveitado) — cota de grupo é um pool compartilhado entre as unidades membros. Agendar consome; cancelar estorna.

Regras em [[03 - Entidades e Classes de Domínio]] (seções 3.15 e 3.15.1) e [[10 - Padrões e Arquitetura]].

> [!note] Agenda (oferta) — implementada, com a entrada de menu oculta
> A entidade `Agenda` (executante, profissional, procedimento, vigência, horário) **gera** cotas
> por data e distribui vagas entre as unidades solicitantes (`AgendaController`, V89/V90, rota
> `/liberacao-agenda`, só ADMIN). O item de menu está comentado em `menuConfig.js`; no dia a dia
> a cota ainda é cadastrada à mão. Ver [[Agenda e Oferta]].

> [!note] Atendimentos — a tela de quem executa
> O Usuário Padrão (operador da unidade executante) registra o desfecho de cada atendimento em
> `/atendimentos`, com o município inteiro: Confirmado (`REALIZADO`), Faltou, Cancelado e data da
> coleta. Falta e cancelamento não devolvem vaga; a unidade solicitante devolve o paciente à fila
> pela ficha, criando um item novo. Detalhes em [[03 - Entidades e Classes de Domínio]] (3.15f).

## Blocos de entidades

| Bloco | Entidades |
|---|---|
| Pessoas e acesso | `User`, `Roles`, `Unidade`, `Profissional` |
| Demanda | `Solicitacao`, `SolicitacaoEspecialidade`, `Especialidade`, `CID` |
| Oferta | `CotaUnidade`, `GrupoRelatorio`, `LocalAgendamento`, `Cidade` |
| Marcação | `AgendamentoSolicitacao` |
| Transporte | `AgendamentoTransporte`, `AgendamentoTransportePaciente`, `Transporte`, `Motorista` |
| Federação | `Municipio`, `Pacto`, `PactoEvento`, `Notificacao` |

## Transporte sanitário

Leva o paciente à consulta: veículo com vagas, motorista, turno, retorno no mesmo dia. Usa *optimistic locking* (`version`) porque várias pessoas disputam as mesmas vagas. Ver [[diagrama-transporte]].

## Federação entre municípios

Municípios pactuados trocam itens de fila via RabbitMQ — cada município tem sua queue, e `PactoEvento` registra o que foi publicado e por quem foi consumido. Ver [[diagrama-federacao]] e [[Infraestrutura]].

---

Volta para [[Início]] · vizinhos: [[Mapa - Arquitetura]], [[Mapa - Segurança e Acesso]]
