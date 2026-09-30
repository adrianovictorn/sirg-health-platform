-- V100: solicitacao_especialidade ganha profissional_executante_id, o
-- profissional que efetivamente atende esta especialidade/paciente no
-- agendamento.
--
-- Distinto de:
--  - solicitacao_especialidade.profissional_id (V79/profissionalSolicitante):
--    quem PEDIU o exame, nao quem atende.
--  - cota_unidade.profissional_id (V92/profissionalExecutante, o "espelho"):
--    profissional vinculado a cota. Continua sendo o valor padrao/sugerido;
--    este campo novo so existe para o operador SOBRESCREVER, por agendamento,
--    sem alterar o cadastro da cota.
--
-- Operacao ADITIVA: coluna NULL-avel, sem indice, sem backfill — nenhum
-- agendamento ja existente tem este dado.

ALTER TABLE solicitacao_especialidade ADD COLUMN IF NOT EXISTS profissional_executante_id BIGINT REFERENCES profissional(id) ON DELETE SET NULL;
