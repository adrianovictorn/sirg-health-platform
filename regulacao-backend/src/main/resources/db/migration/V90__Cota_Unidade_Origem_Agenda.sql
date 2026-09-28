-- V90: cota_unidade passa a saber SE e DE ONDE foi gerada por uma agenda.
--
-- Contexto: a agenda (V89) materializa uma cota_unidade por ocorrencia x unidade
-- solicitante. Sem este vinculo nao ha como (a) mostrar a origem na tela de
-- cotas, (b) recusar a edicao direta de uma cota de agenda (edita-se a agenda),
-- nem (c) localizar as cotas de uma ocorrencia para cancelar/estornar (R10).
--
-- Ver docs/especificacoes/Agenda e Oferta.md, secao 4.8.
--
-- Operacoes ADITIVAS: nenhuma coluna e removida, nenhum dado existente muda de
-- valor. Toda cota ja cadastrada recebe origem = MANUAL (o DEFAULT cobre; o
-- UPDATE abaixo e so um backfill defensivo, idempotente).

ALTER TABLE cota_unidade ADD COLUMN IF NOT EXISTS agenda_ocorrencia_id BIGINT;

ALTER TABLE cota_unidade
    DROP CONSTRAINT IF EXISTS fk_cota_agenda_ocorrencia;

ALTER TABLE cota_unidade
    ADD CONSTRAINT fk_cota_agenda_ocorrencia
    FOREIGN KEY (agenda_ocorrencia_id) REFERENCES agenda_ocorrencia(id) ON DELETE RESTRICT;

ALTER TABLE cota_unidade ADD COLUMN IF NOT EXISTS origem VARCHAR(20) NOT NULL DEFAULT 'MANUAL';

ALTER TABLE cota_unidade
    DROP CONSTRAINT IF EXISTS ck_cota_origem;

ALTER TABLE cota_unidade
    ADD CONSTRAINT ck_cota_origem
    CHECK (origem IN ('MANUAL', 'AGENDA'));

UPDATE cota_unidade SET origem = 'MANUAL' WHERE origem IS NULL;

-- R10: cancelar uma ocorrencia precisa localizar rapido as cotas que ela gerou.
CREATE INDEX IF NOT EXISTS ix_cota_unidade_agenda_ocorrencia
    ON cota_unidade (agenda_ocorrencia_id);
