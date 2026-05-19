-- V78: Adiciona suporte a cota por data específica (além do mensal já existente).
-- tipo_periodo: MENSAL (padrão) ou DATA
-- data_especifica: preenchido apenas quando tipo_periodo = DATA
-- periodo passa a ser nullable (nulo para cotas do tipo DATA)

ALTER TABLE cota_unidade
    ADD COLUMN IF NOT EXISTS tipo_periodo VARCHAR(10) NOT NULL DEFAULT 'MENSAL',
    ADD COLUMN IF NOT EXISTS data_especifica DATE;

ALTER TABLE cota_unidade ALTER COLUMN periodo DROP NOT NULL;

-- Remove os índices únicos antigos
DROP INDEX IF EXISTS uk_cota_unidade_especialidade;
DROP INDEX IF EXISTS uk_cota_unidade_geral;

-- MENSAL – com especialidade
CREATE UNIQUE INDEX uk_cota_unidade_esp_mensal
    ON cota_unidade (unidade_id, especialidade_id, periodo)
    WHERE tipo_periodo = 'MENSAL' AND especialidade_id IS NOT NULL;

-- MENSAL – geral (sem especialidade)
CREATE UNIQUE INDEX uk_cota_unidade_geral_mensal
    ON cota_unidade (unidade_id, periodo)
    WHERE tipo_periodo = 'MENSAL' AND especialidade_id IS NULL;

-- DATA – com especialidade
CREATE UNIQUE INDEX uk_cota_unidade_esp_data
    ON cota_unidade (unidade_id, especialidade_id, data_especifica)
    WHERE tipo_periodo = 'DATA' AND especialidade_id IS NOT NULL;

-- DATA – geral (sem especialidade)
CREATE UNIQUE INDEX uk_cota_unidade_geral_data
    ON cota_unidade (unidade_id, data_especifica)
    WHERE tipo_periodo = 'DATA' AND especialidade_id IS NULL;
