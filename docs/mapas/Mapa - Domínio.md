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

> [!todo] Agenda — especificada, ainda não implementada
> Hoje "abrir agenda" é cadastrar cota à mão. A entidade `Agenda` (executante, profissional,
> procedimento, vigência, horário) passará a **gerar** cotas por data e a distribuir vagas entre
> as unidades solicitantes. Ver [[Agenda e Oferta]].

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
