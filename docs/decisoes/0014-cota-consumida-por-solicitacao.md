---
titulo: Cota consumida por solicitação, não por item
tags:
  - sirg/adr
status: aceita
data: 2026-10-15
---

# 0014 — Cota consumida por solicitação, não por item

## Contexto

Até a V112, cada exame ou especialidade agendado consumia uma vaga da cota. As cotas de
laboratório são pactuadas com o executante em **pacientes por dia**, não em exames. Um
paciente com 19 exames numa cota de 10 vagas só conseguia agendar 10, e a tela de
agendamento mostrava "a última vaga já foi usada por outro item do mesmo lote". Na prática,
uma cota de 15 vagas se esgotava com dois pacientes.

## Decisão

**Uma solicitação ocupa no máximo uma vaga de cada cota que incide sobre ela.** Isso vale
para todas as cotas (consulta, exame, grupo e geral).

- Quantos itens forem agendados na mesma cota (num lote só, em agendamentos separados ou em
  dias diferentes do mês, na cota mensal), a solicitação continua ocupando uma vaga.
- Duas solicitações do mesmo paciente ocupam duas vagas.
- Se o lote cai sob duas cotas que incidem juntas, ocupa uma vaga em cada uma.
- **Estorno:** a vaga volta quando sai o **último** item da solicitação naquela cota, seja
  pela exclusão do agendamento ou pela remoção da ficha de um item AGENDADO. Mudar o status
  continua sem devolver vaga. Um item com falta devolvido à fila e reagendado na mesma cota
  não consome vaga nova.
- **O teto financeiro continua por item**, porque cada exame tem o seu preço.

"Quais cotas a solicitação já ocupa" é **derivado** dos itens agendados dela
(`CotaUnidadeService.cotasOcupadasPelaSolicitacao`), com as mesmas regras de incidência e
de isolamento por profissional do consumo. Agendar e estornar travam a linha da solicitação
(`SELECT ... FOR UPDATE`), para que dois POSTs simultâneos da mesma ficha não consumam duas
vagas. A V113 recalcula o "usado" das cotas vigentes pelo número de solicitações distintas.
O recálculo só reduz o contador.

## Alternativas consideradas

- **Livro-razão `cota_consumo(cota_unidade_id, solicitacao_id)` com UNIQUE** e
  `INSERT ... ON CONFLICT DO NOTHING`. É exato: também resolve o agendamento feito por
  ADMIN/GESTOR, que não consome, e dispensa a trava. Foi descartado por enquanto porque o
  diff é bem maior: DDL, preenchimento herdando a mesma imprecisão da incidência, e manutenção
  na migração de data e na unificação. Fica como evolução se a imprecisão abaixo incomodar.
- **Valer só para cotas de laboratório.** Foi descartado porque a mesma lógica (a cota conta
  pacientes) vale para consulta, e duas regras de consumo seriam mais difíceis de manter.

## Consequências

- A derivação por incidência não distingue item agendado por ADMIN/GESTOR, que não consumiu.
  Se um ADMIN agendou um exame e depois a unidade agenda outro do mesmo paciente na mesma cota
  geral, o segundo também não consome. É a mesma imprecisão que `consumidoresDe` já aceita,
  mas aqui ela **altera o contador**: a unidade pode usar mais do que o pactuado. Isso foi
  aceito por ser raro, e está documentado em
  `CotaConsumoPorSolicitacaoIT.itemDeAdminFazASolicitacaoOcupar`. Se incomodar, a correção
  é desconsiderar os agendamentos com `criado_por` ADMIN/GESTOR, ou adotar o livro-razão.
- Cota de horário dinâmico: o item novo de uma solicitação que já ocupa a cota herda o
  horário dela, no mesmo lote ou num agendamento posterior, em vez de ganhar o slot de
  outro paciente.
- "Usado", "agendadas" e "esgotadas" (telas de cota e indicadores) passam a contar
  **pacientes**. Os números das cotas vigentes caem depois da V113. Cotas passadas não foram
  recalculadas.
- Código e V113 precisam subir **juntos**: o código novo estorna uma vaga por solicitação,
  e contadores no formato antigo deixariam vagas presas.
- Proibido voltar a consumir ou estornar cota sem passar pelo conjunto de cotas ocupadas
  (`incrementarUtilizacao(..., jaOcupadas)` / `estornarCotasDesocupadas`).
