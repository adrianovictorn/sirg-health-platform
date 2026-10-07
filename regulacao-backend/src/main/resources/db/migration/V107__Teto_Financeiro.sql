-- V107: teto financeiro por unidade, grupo de especialidades e mes.
--
-- E um limite em REAIS, ao lado da cota (que e em quantidade). Tabela propria, e
-- nao colunas em cota_unidade, por tres motivos: o motor de cota nao e tocado; a
-- unidade de medida e outra (NUMERIC x inteiro); e os endpoints de cota sao
-- lidos por ADMIN_UNIDADE, que nao pode ver valor nenhum.
--
--  - grupo_especialidades_id: quais especialidades debitam. O ADMIN escolhe o
--    grupo ao liberar (na pratica, Laboratorio) — nada fica fixo em codigo, e
--    cada municipio usa o seu cadastro.
--  - periodo: mes da DATA AGENDADA, formato YYYY-MM (mesmo criterio da cota).
--  - valor_utilizado: so e alterado por UPDATE atomico (debito/estorno), nunca
--    pela escrita da entidade.
--  - Todas as colunas da chave unica sao NOT NULL, entao nao ha a armadilha de
--    NULL em indice unico que a cota precisou contornar.
--
-- solicitacao_especialidade.teto_financeiro_id registra QUAL teto o item
-- debitou: o estorno devolve exatamente a ele, sem recalcular (a especialidade
-- pode ter trocado de grupo, o preco pode ter mudado).
--
-- Operacoes ADITIVAS: tabela nova e coluna anulavel.

CREATE TABLE IF NOT EXISTS teto_financeiro (
    id                       BIGSERIAL     PRIMARY KEY,
    unidade_id               BIGINT        NOT NULL,
    grupo_especialidades_id  BIGINT        NOT NULL,
    periodo                  VARCHAR(7)    NOT NULL,
    valor_total              NUMERIC(14,2) NOT NULL DEFAULT 0,
    valor_utilizado          NUMERIC(14,2) NOT NULL DEFAULT 0,
    ativo                    BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em                TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    criado_por_id            BIGINT,
    version                  BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT fk_teto_financeiro_unidade
        FOREIGN KEY (unidade_id) REFERENCES unidade(id) ON DELETE RESTRICT,
    CONSTRAINT fk_teto_financeiro_grupo_especialidades
        FOREIGN KEY (grupo_especialidades_id) REFERENCES grupo_relatorio(id) ON DELETE RESTRICT,
    CONSTRAINT fk_teto_financeiro_criado_por
        FOREIGN KEY (criado_por_id) REFERENCES usuarios(id) ON DELETE RESTRICT,
    CONSTRAINT uk_teto_financeiro_unidade_grupo_periodo
        UNIQUE (unidade_id, grupo_especialidades_id, periodo),
    CONSTRAINT ck_teto_financeiro_periodo
        CHECK (periodo ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
    CONSTRAINT ck_teto_financeiro_valores
        CHECK (valor_total >= 0 AND valor_utilizado >= 0)
);

CREATE INDEX IF NOT EXISTS ix_teto_financeiro_periodo ON teto_financeiro (periodo);

ALTER TABLE solicitacao_especialidade ADD COLUMN IF NOT EXISTS teto_financeiro_id BIGINT;

ALTER TABLE solicitacao_especialidade
    DROP CONSTRAINT IF EXISTS fk_solicitacao_especialidade_teto_financeiro;

ALTER TABLE solicitacao_especialidade
    ADD CONSTRAINT fk_solicitacao_especialidade_teto_financeiro
    FOREIGN KEY (teto_financeiro_id) REFERENCES teto_financeiro(id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS ix_solicitacao_especialidade_teto_financeiro
    ON solicitacao_especialidade (teto_financeiro_id);
