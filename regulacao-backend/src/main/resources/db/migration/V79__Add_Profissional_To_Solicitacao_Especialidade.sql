-- V79: Adiciona campo opcional de profissional solicitante em solicitacao_especialidade.
-- Permite registrar qual profissional fez a solicitação daquela especialidade,
-- sem tornar o campo obrigatório.

ALTER TABLE solicitacao_especialidade
    ADD COLUMN IF NOT EXISTS profissional_id BIGINT
        REFERENCES profissional(id) ON DELETE SET NULL;
