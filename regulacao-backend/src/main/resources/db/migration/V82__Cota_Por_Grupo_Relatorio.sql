-- V82: Cota coletiva por Grupo.
--
-- O agrupamento de unidades reaproveita a tabela `grupo_relatorio` (V58), que ja
-- existe e ja tem o desenho codigo/nome/ativo. Assim como uma Especialidade
-- pertence a um GrupoRelatorio (`especialidade.grupo_relatorio_id`), uma Unidade
-- passa a pertencer a um GrupoRelatorio (`unidade.grupo_relatorio_id`).
--
-- `cota_unidade` passa a aceitar cota de GRUPO alem de cota de UNIDADE:
--   - unidade_id preenchido    + grupo_relatorio_id nulo => cota da unidade
--   - grupo_relatorio_id preenchido + unidade_id nulo    => cota do grupo
--     (pool compartilhado entre as unidades membros)
-- Exatamente um dos dois deve estar preenchido (CHECK abaixo).
--
-- Todas as operacoes sao ADITIVAS: nenhuma coluna e removida, nenhum dado e
-- alterado. As linhas existentes de cota_unidade tem unidade_id NOT NULL (V70) e
-- recebem grupo_relatorio_id nulo, portanto satisfazem o CHECK sem ajuste.

-- ---------------------------------------------------------------------------
-- 1. Vinculo Unidade -> Grupo
-- ---------------------------------------------------------------------------

ALTER TABLE unidade
    ADD COLUMN IF NOT EXISTS grupo_relatorio_id BIGINT;

ALTER TABLE unidade
    DROP CONSTRAINT IF EXISTS fk_unidade_grupo_relatorio;

ALTER TABLE unidade
    ADD CONSTRAINT fk_unidade_grupo_relatorio
    FOREIGN KEY (grupo_relatorio_id) REFERENCES grupo_relatorio(id) ON DELETE SET NULL;

-- ---------------------------------------------------------------------------
-- 2. cota_unidade: suporte a cota de grupo
-- ---------------------------------------------------------------------------

ALTER TABLE cota_unidade
    ADD COLUMN IF NOT EXISTS grupo_relatorio_id BIGINT;

ALTER TABLE cota_unidade
    DROP CONSTRAINT IF EXISTS fk_cota_grupo_relatorio;

-- RESTRICT (e nao CASCADE) de proposito: `grupo_relatorio` ja era usado para
-- agrupar especialidades em relatorios e possui endpoint de exclusao
-- (DELETE /api/grupo-relatorio/deletar/{id}). Com CASCADE, apagar um grupo de
-- relatorio destruiria silenciosamente a configuracao de cotas atrelada a ele.
-- Com RESTRICT o banco recusa, e o servico devolve uma mensagem explicita.
ALTER TABLE cota_unidade
    ADD CONSTRAINT fk_cota_grupo_relatorio
    FOREIGN KEY (grupo_relatorio_id) REFERENCES grupo_relatorio(id) ON DELETE RESTRICT;

-- unidade_id passa a ser opcional (nulo quando a cota e de grupo)
ALTER TABLE cota_unidade ALTER COLUMN unidade_id DROP NOT NULL;

-- Garante que a cota pertence a exatamente um titular: unidade OU grupo
ALTER TABLE cota_unidade
    DROP CONSTRAINT IF EXISTS ck_cota_titular_exclusivo;

ALTER TABLE cota_unidade
    ADD CONSTRAINT ck_cota_titular_exclusivo
    CHECK (
        (unidade_id IS NOT NULL AND grupo_relatorio_id IS NULL)
     OR (unidade_id IS NULL AND grupo_relatorio_id IS NOT NULL)
    );

-- ---------------------------------------------------------------------------
-- 3. Indices unicos das cotas de grupo (espelham os de unidade da V78)
-- ---------------------------------------------------------------------------

CREATE UNIQUE INDEX IF NOT EXISTS uk_cota_grupo_esp_mensal
    ON cota_unidade (grupo_relatorio_id, especialidade_id, periodo)
    WHERE tipo_periodo = 'MENSAL' AND grupo_relatorio_id IS NOT NULL AND especialidade_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_cota_grupo_geral_mensal
    ON cota_unidade (grupo_relatorio_id, periodo)
    WHERE tipo_periodo = 'MENSAL' AND grupo_relatorio_id IS NOT NULL AND especialidade_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_cota_grupo_esp_data
    ON cota_unidade (grupo_relatorio_id, especialidade_id, data_especifica)
    WHERE tipo_periodo = 'DATA' AND grupo_relatorio_id IS NOT NULL AND especialidade_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_cota_grupo_geral_data
    ON cota_unidade (grupo_relatorio_id, data_especifica)
    WHERE tipo_periodo = 'DATA' AND grupo_relatorio_id IS NOT NULL AND especialidade_id IS NULL;

-- ---------------------------------------------------------------------------
-- 4. Recria os indices de unidade com o filtro unidade_id IS NOT NULL
-- ---------------------------------------------------------------------------
-- Os indices da V78 nao filtravam por unidade_id NOT NULL (a coluna era
-- obrigatoria a epoca). Sem o filtro, varias cotas de grupo (todas com
-- unidade_id nulo) colidiriam entre si no indice de unidade.
-- Os novos indices cobrem um subconjunto das linhas cobertas pelos antigos,
-- entao se a V78 esta aplicada nao pode haver duplicidade nova.

DROP INDEX IF EXISTS uk_cota_unidade_esp_mensal;
DROP INDEX IF EXISTS uk_cota_unidade_geral_mensal;
DROP INDEX IF EXISTS uk_cota_unidade_esp_data;
DROP INDEX IF EXISTS uk_cota_unidade_geral_data;

CREATE UNIQUE INDEX IF NOT EXISTS uk_cota_unidade_esp_mensal
    ON cota_unidade (unidade_id, especialidade_id, periodo)
    WHERE tipo_periodo = 'MENSAL' AND unidade_id IS NOT NULL AND especialidade_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_cota_unidade_geral_mensal
    ON cota_unidade (unidade_id, periodo)
    WHERE tipo_periodo = 'MENSAL' AND unidade_id IS NOT NULL AND especialidade_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_cota_unidade_esp_data
    ON cota_unidade (unidade_id, especialidade_id, data_especifica)
    WHERE tipo_periodo = 'DATA' AND unidade_id IS NOT NULL AND especialidade_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_cota_unidade_geral_data
    ON cota_unidade (unidade_id, data_especifica)
    WHERE tipo_periodo = 'DATA' AND unidade_id IS NOT NULL AND especialidade_id IS NULL;
