-- V93: rastreabilidade de autoria — quem criou o agendamento e quem adicionou
-- cada especialidade a uma solicitacao. Complementa a V92 (profissional
-- EXECUTANTE da cota, que atende) com o operador do sistema que registrou o
-- dado — sao pessoas diferentes, em tabelas diferentes (Profissional x User).
--
-- Nao ha auditoria generica no sistema; o unico precedente e Agenda.criadoPor
-- (V89), NOT NULL porque a tabela nasceu junto com o requisito. Aqui as
-- tabelas ja tem dados reais em producao sem essa informacao, entao os campos
-- sao NULL-aveis por natureza — nao ha backfill confiavel de "quem criou"
-- registros antigos. Exibido como "-" nas telas quando ausente.
--
-- Separada da V92 (cota) para permitir reverter uma sem a outra.
--
-- Operacoes ADITIVAS: nenhuma coluna removida, nenhum dado existente alterado.

ALTER TABLE agendamento_solicitacao ADD COLUMN IF NOT EXISTS criado_por_id BIGINT;

ALTER TABLE agendamento_solicitacao
    DROP CONSTRAINT IF EXISTS fk_agendamento_solicitacao_criado_por;

ALTER TABLE agendamento_solicitacao
    ADD CONSTRAINT fk_agendamento_solicitacao_criado_por
    FOREIGN KEY (criado_por_id) REFERENCES usuarios(id) ON DELETE RESTRICT;

ALTER TABLE solicitacao_especialidade ADD COLUMN IF NOT EXISTS criado_por_id BIGINT;

ALTER TABLE solicitacao_especialidade
    DROP CONSTRAINT IF EXISTS fk_solicitacao_especialidade_criado_por;

ALTER TABLE solicitacao_especialidade
    ADD CONSTRAINT fk_solicitacao_especialidade_criado_por
    FOREIGN KEY (criado_por_id) REFERENCES usuarios(id) ON DELETE RESTRICT;

-- Rastreabilidade por profissional (V92): qual cota (e portanto qual
-- profissional) efetivamente atendeu esta especialidade, quando ha mais de
-- uma cota compativel (ex.: dois ginecologistas no mesmo dia/horario) e a
-- unidade escolheu uma delas no agendamento.
ALTER TABLE solicitacao_especialidade ADD COLUMN IF NOT EXISTS cota_unidade_id BIGINT;

ALTER TABLE solicitacao_especialidade
    DROP CONSTRAINT IF EXISTS fk_solicitacao_especialidade_cota_unidade;

ALTER TABLE solicitacao_especialidade
    ADD CONSTRAINT fk_solicitacao_especialidade_cota_unidade
    FOREIGN KEY (cota_unidade_id) REFERENCES cota_unidade(id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS ix_solicitacao_especialidade_cota_unidade
    ON solicitacao_especialidade (cota_unidade_id);
