-- V87: tabela CBO (Classificacao Brasileira de Ocupacoes).
--
-- Contexto: o vinculo entre profissional e estabelecimento executante precisa
-- dizer EM QUE OCUPACAO o profissional atua ali — o mesmo CPF pode ser
-- "medico clinico" num prestador e "medico do trabalho" em outro. O CBO e a
-- chave que o proprio CNES usa para isso, e e o que aparece nos filtros da tela
-- de importacao pedida no oficio (25/09/2026).
--
-- Ver docs/especificacoes/Agenda e Oferta.md, secao 4.2.
--
-- SEM SEED, de proposito
-- ----------------------
-- A especificacao previa "carga inicial com as ocupacoes usadas no municipio".
-- Semear codigos aqui significaria digita-los a mao, e um codigo de CBO errado
-- e pior que ausente: ele entra silenciosamente no vinculo e passa a rotular o
-- profissional com a ocupacao de outra pessoa, sem nada que acuse o erro.
--
-- A tabela nasce vazia e e populada por duas vias, ambas com dado de origem
-- confiavel:
--   1. a importacao do CSV do CNES, que traz codigo e descricao do proprio
--      DATASUS e faz upsert por codigo (ver ProfissionalImportacaoService);
--   2. POST /api/cbos, para o administrador cadastrar uma ocupacao avulsa.
--
-- Tabela completa do CBO fica fora de escopo (secao 10 da especificacao).

CREATE TABLE IF NOT EXISTS cbo (
    id          BIGSERIAL PRIMARY KEY,

    -- 6 digitos no padrao CBO 2002 (ex.: 225125 = medico clinico).
    codigo      VARCHAR(6)   NOT NULL,

    descricao   VARCHAR(255) NOT NULL,

    -- Desativar em vez de excluir: o vinculo que aponta para o CBO precisa
    -- continuar legivel depois que a ocupacao sai de uso.
    ativo       BOOLEAN      NOT NULL DEFAULT TRUE,

    criado_em   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uk_cbo_codigo UNIQUE (codigo)
);

-- Os combos listam por descricao; o filtro por ativo vem sempre junto.
CREATE INDEX IF NOT EXISTS ix_cbo_ativo_descricao
    ON cbo (ativo, descricao);
