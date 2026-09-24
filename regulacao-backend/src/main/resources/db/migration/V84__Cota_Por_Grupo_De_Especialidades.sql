-- V84: Cota por GRUPO DE ESPECIALIDADES (alem da cota por especialidade avulsa).
--
-- Motivacao: configurar cota uma especialidade por vez e inviavel na pratica —
-- o grupo "Laboratorio" sozinho tem 172 especialidades. Agora o administrador
-- pode lancar a cota no grupo, e ela cobre todas as especialidades dele.
--
-- A cota passa a ter DUAS dimensoes independentes, ambas reaproveitando
-- `grupo_relatorio` (que ja agrupa especialidades desde a V58):
--
--   TITULAR (quem detem a cota):
--     unidade_id         -> a cota e de uma unidade
--     grupo_unidades_id  -> a cota e um pool compartilhado entre as unidades do grupo
--                           (vinculo em unidade.grupo_relatorio_id, V82)
--     ... exatamente um dos dois.
--
--   ESCOPO (o que a cota limita):
--     especialidade_id        -> uma especialidade especifica
--     grupo_especialidades_id -> todas as especialidades do grupo (pool unico
--                                compartilhado entre elas)
--     ambos nulos             -> cota geral, vale para qualquer especialidade
--     ... no maximo um dos dois.
--
-- Cotas de escopos diferentes INCIDEM JUNTAS: havendo cota de Hemograma e cota do
-- grupo Laboratorio, as duas precisam ter saldo. Isso permite configurar
-- "100 exames de laboratorio no mes, sendo no maximo 10 de Hemograma".
--
-- Operacoes ADITIVAS: nenhuma coluna e removida, nenhum dado existente e alterado.
-- As cotas ja cadastradas ficam com grupo_especialidades_id nulo, ou seja,
-- mantem exatamente o comportamento atual.

-- ---------------------------------------------------------------------------
-- 1. Renomeia o titular-grupo para deixar os dois papeis distinguiveis
-- ---------------------------------------------------------------------------
-- `grupo_relatorio_id` era ambiguo: a partir daqui existem DOIS grupos possiveis
-- numa mesma cota (o de unidades e o de especialidades).

ALTER TABLE cota_unidade RENAME COLUMN grupo_relatorio_id TO grupo_unidades_id;

-- ---------------------------------------------------------------------------
-- 2. Nova dimensao: escopo por grupo de especialidades
-- ---------------------------------------------------------------------------

ALTER TABLE cota_unidade
    ADD COLUMN IF NOT EXISTS grupo_especialidades_id BIGINT;

ALTER TABLE cota_unidade
    DROP CONSTRAINT IF EXISTS fk_cota_grupo_especialidades;

-- RESTRICT pelo mesmo motivo do titular (ver V82): excluir um grupo de relatorio
-- nao pode apagar silenciosamente a configuracao de cotas atrelada a ele.
ALTER TABLE cota_unidade
    ADD CONSTRAINT fk_cota_grupo_especialidades
    FOREIGN KEY (grupo_especialidades_id) REFERENCES grupo_relatorio(id) ON DELETE RESTRICT;

-- ---------------------------------------------------------------------------
-- 3. Constraints das duas dimensoes
-- ---------------------------------------------------------------------------

-- TITULAR: exatamente um (recriada por causa do rename da coluna)
ALTER TABLE cota_unidade
    DROP CONSTRAINT IF EXISTS ck_cota_titular_exclusivo;

ALTER TABLE cota_unidade
    ADD CONSTRAINT ck_cota_titular_exclusivo
    CHECK (
        (unidade_id IS NOT NULL AND grupo_unidades_id IS NULL)
     OR (unidade_id IS NULL AND grupo_unidades_id IS NOT NULL)
    );

-- ESCOPO: no maximo um (ambos nulos = cota geral)
ALTER TABLE cota_unidade
    DROP CONSTRAINT IF EXISTS ck_cota_escopo_exclusivo;

ALTER TABLE cota_unidade
    ADD CONSTRAINT ck_cota_escopo_exclusivo
    CHECK (NOT (especialidade_id IS NOT NULL AND grupo_especialidades_id IS NOT NULL));

-- ---------------------------------------------------------------------------
-- 4. Unicidade
-- ---------------------------------------------------------------------------
-- Com 2 titulares x 3 escopos x 2 tipos de periodo seriam 12 indices parciais.
-- Em vez disso, um indice por tipo de periodo sobre a chave inteira, usando
-- COALESCE para tratar os nulos (ids sao BIGSERIAL, sempre positivos, entao -1
-- nunca colide com um id real).

DROP INDEX IF EXISTS uk_cota_unidade_esp_mensal;
DROP INDEX IF EXISTS uk_cota_unidade_geral_mensal;
DROP INDEX IF EXISTS uk_cota_unidade_esp_data;
DROP INDEX IF EXISTS uk_cota_unidade_geral_data;
DROP INDEX IF EXISTS uk_cota_grupo_esp_mensal;
DROP INDEX IF EXISTS uk_cota_grupo_geral_mensal;
DROP INDEX IF EXISTS uk_cota_grupo_esp_data;
DROP INDEX IF EXISTS uk_cota_grupo_geral_data;

CREATE UNIQUE INDEX IF NOT EXISTS uk_cota_mensal
    ON cota_unidade (
        COALESCE(unidade_id, -1),
        COALESCE(grupo_unidades_id, -1),
        COALESCE(especialidade_id, -1),
        COALESCE(grupo_especialidades_id, -1),
        periodo
    )
    WHERE tipo_periodo = 'MENSAL';

CREATE UNIQUE INDEX IF NOT EXISTS uk_cota_data
    ON cota_unidade (
        COALESCE(unidade_id, -1),
        COALESCE(grupo_unidades_id, -1),
        COALESCE(especialidade_id, -1),
        COALESCE(grupo_especialidades_id, -1),
        data_especifica
    )
    WHERE tipo_periodo = 'DATA';

-- ---------------------------------------------------------------------------
-- 5. Indice de apoio a consulta de cotas aplicaveis
-- ---------------------------------------------------------------------------
-- A verificacao de cota roda a cada agendamento; este indice cobre o filtro por
-- titular usado na consulta.

CREATE INDEX IF NOT EXISTS ix_cota_unidade_titular
    ON cota_unidade (unidade_id, grupo_unidades_id)
    WHERE ativo = TRUE;
