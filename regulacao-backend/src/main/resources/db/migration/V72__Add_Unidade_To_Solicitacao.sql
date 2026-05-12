ALTER TABLE solicitacao
    ADD COLUMN IF NOT EXISTS unidade_id BIGINT,
    ADD CONSTRAINT fk_solicitacao_unidade
        FOREIGN KEY (unidade_id) REFERENCES unidade(id) ON DELETE SET NULL;
