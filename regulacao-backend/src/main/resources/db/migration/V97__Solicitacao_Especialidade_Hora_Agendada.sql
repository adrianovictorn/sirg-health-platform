-- V97: solicitacao_especialidade ganha hora_agendada, o horario efetivo do
-- paciente para este exame/consulta.
--
-- Quando a cota usada (solicitacao_especialidade.cota_unidade_id, V93) tem
-- horario_dinamico = true, este horario e calculado automaticamente pelo
-- CotaUnidadeService dividindo [hora_inicial, hora_final] da cota em
-- quantidade_total partes iguais, uma por vaga. Quando a cota nao e dinamica,
-- o operador pode informa-lo manualmente na tela de agendamento; se nao
-- informar, a tela usa o periodo (hora_inicial/hora_final) da cota so como
-- referencia, sem gravar nada aqui.
--
-- Operacao ADITIVA: coluna NULL-avel, sem indice, sem backfill — nenhum
-- agendamento ja existente tem este dado, e comprovantes ja entregues nao sao
-- recalculados retroativamente.

ALTER TABLE solicitacao_especialidade ADD COLUMN IF NOT EXISTS hora_agendada TIME;
