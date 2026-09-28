-- V88: profissional ganha CPF e passa a ter MULTIPLOS vinculos (executante x CBO).
--
-- Contexto: hoje `profissional.unidade_id` e um ManyToOne unico — um profissional
-- pertence a uma unidade e pronto. Isso nao descreve a realidade que o oficio da
-- Central de Regulacao de Sao Felipe (25/09/2026) trouxe: o mesmo medico atende
-- na policlinica, no hospital e numa USF, com ocupacao (CBO) possivelmente
-- diferente em cada lugar. A agenda precisa saber quem atende NAQUELE
-- estabelecimento executante para filtrar o combo de profissionais.
--
-- Ver docs/especificacoes/Agenda e Oferta.md, secoes 4.3 e 6.
--
-- Operacoes ADITIVAS: nenhuma coluna e removida e nenhum dado existente muda de
-- valor. `profissional.unidade_id` CONTINUA populado e funcionando — as telas
-- atuais leem dele. A coluna fica marcada como @Deprecated na entidade e sai
-- numa migracao futura, depois que os pontos de leitura migrarem para o vinculo.

-- ---------------------------------------------------------------------------
-- 1. CPF do profissional
-- ---------------------------------------------------------------------------
-- E a chave de deduplicacao da importacao do CNES: o arquivo do DATASUS traz
-- CPF, e e por ele que se decide entre criar um profissional novo ou apenas
-- acrescentar um vinculo a quem ja existe.
--
-- NULLABLE: os profissionais ja cadastrados a mao nao tem CPF, e exigir o campo
-- agora travaria a edicao de todos eles. UNIQUE parcial (WHERE NOT NULL) para
-- que os registros antigos convivam sem colidir entre si.

ALTER TABLE profissional ADD COLUMN IF NOT EXISTS cpf VARCHAR(11);

DROP INDEX IF EXISTS uk_profissional_cpf;

CREATE UNIQUE INDEX uk_profissional_cpf
    ON profissional (cpf)
    WHERE cpf IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 2. Vinculo profissional x estabelecimento executante x CBO
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS profissional_vinculo (
    id               BIGSERIAL PRIMARY KEY,

    profissional_id  BIGINT      NOT NULL,

    -- O estabelecimento onde o profissional atua. Aponta para `unidade` porque a
    -- V86 transformou `unidade` no cadastro unico de estabelecimento (coluna
    -- `tipo`), em vez de criar uma tabela paralela de prestadores.
    unidade_id       BIGINT      NOT NULL,

    -- Ocupacao naquele vinculo. NULLABLE porque os vinculos herdados do campo
    -- antigo (item 3 abaixo) nao tem CBO — o dado nunca foi coletado.
    cbo_id           BIGINT,

    ativo            BOOLEAN     NOT NULL DEFAULT TRUE,

    -- MANUAL = cadastrado na tela. IMPORTACAO_CNES = veio do arquivo do DATASUS.
    -- Serve para saber o que pode ser sobrescrito por uma reimportacao.
    origem           VARCHAR(20) NOT NULL DEFAULT 'MANUAL',

    criado_em        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_vinculo_profissional
        FOREIGN KEY (profissional_id) REFERENCES profissional(id) ON DELETE CASCADE,

    -- RESTRICT no estabelecimento: excluir uma unidade nao pode apagar em
    -- silencio os vinculos que a agenda usa para montar o combo de
    -- profissionais. CASCADE apenas no profissional, que e o dono do vinculo.
    CONSTRAINT fk_vinculo_unidade
        FOREIGN KEY (unidade_id) REFERENCES unidade(id) ON DELETE RESTRICT,

    CONSTRAINT fk_vinculo_cbo
        FOREIGN KEY (cbo_id) REFERENCES cbo(id) ON DELETE RESTRICT,

    CONSTRAINT ck_vinculo_origem
        CHECK (origem IN ('MANUAL', 'IMPORTACAO_CNES'))
);

-- Unicidade da tripla (profissional, unidade, cbo).
--
-- COALESCE no cbo_id pelo mesmo motivo da V84: no Postgres dois NULL sao
-- distintos, entao um UNIQUE comum deixaria passar N vinculos duplicados sem
-- CBO — exatamente o caso dos vinculos herdados. Ids sao BIGSERIAL, sempre
-- positivos, logo -1 nunca colide com um id real.
CREATE UNIQUE INDEX IF NOT EXISTS uk_vinculo_profissional_unidade_cbo
    ON profissional_vinculo (profissional_id, unidade_id, COALESCE(cbo_id, -1));

-- Consulta que a abertura de agenda faz: "quem atende neste executante?".
CREATE INDEX IF NOT EXISTS ix_vinculo_unidade_ativo
    ON profissional_vinculo (unidade_id, ativo);

-- ---------------------------------------------------------------------------
-- 3. Herda os vinculos que ja existem em profissional.unidade_id
-- ---------------------------------------------------------------------------
-- Sem isso, todo profissional cadastrado ate hoje ficaria invisivel para a
-- agenda: o combo de profissionais do executante le o vinculo, nao a coluna
-- antiga. `cbo_id` nulo e origem MANUAL porque e exatamente o que se sabe do
-- dado legado — nao se inventa ocupacao.
--
-- Idempotente pelo NOT EXISTS: reaplicar em base ja migrada nao duplica nada.

INSERT INTO profissional_vinculo (profissional_id, unidade_id, cbo_id, ativo, origem)
SELECT p.id, p.unidade_id, NULL, p.ativo, 'MANUAL'
FROM profissional p
WHERE p.unidade_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM profissional_vinculo v
      WHERE v.profissional_id = p.id
        AND v.unidade_id = p.unidade_id
        AND v.cbo_id IS NULL
  );
