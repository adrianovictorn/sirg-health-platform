CREATE TABLE IF NOT EXISTS cota_unidade (
    id                   BIGSERIAL PRIMARY KEY,
    unidade_id           BIGINT NOT NULL REFERENCES unidade(id) ON DELETE CASCADE,
    especialidade_id     BIGINT REFERENCES especialidade(id) ON DELETE SET NULL,
    periodo              VARCHAR(7) NOT NULL,
    quantidade_total     INTEGER NOT NULL DEFAULT 0,
    quantidade_utilizada INTEGER NOT NULL DEFAULT 0,
    ativo                BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em            TIMESTAMP NOT NULL DEFAULT NOW(),
    version              BIGINT DEFAULT 0
);

-- Unique para cotas por especialidade (quando especialidade_id não é NULL)
CREATE UNIQUE INDEX IF NOT EXISTS uk_cota_unidade_especialidade
    ON cota_unidade (unidade_id, especialidade_id, periodo)
    WHERE especialidade_id IS NOT NULL;

-- Unique para cotas gerais (quando especialidade_id é NULL)
CREATE UNIQUE INDEX IF NOT EXISTS uk_cota_unidade_geral
    ON cota_unidade (unidade_id, periodo)
    WHERE especialidade_id IS NULL;
