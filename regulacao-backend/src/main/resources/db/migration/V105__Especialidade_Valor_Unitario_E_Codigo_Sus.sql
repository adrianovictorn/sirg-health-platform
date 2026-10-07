-- V105: preco e codigo SUS (SIGTAP) por especialidade.
--
--  - valor_unitario: quanto custa UMA unidade do procedimento. NULL = sem preco
--    (nao e R$ 0,00): a especialidade continua utilizavel em tudo, so fica fora
--    dos totais de custo. Nasce NULL para todas — NENHUM preco e embutido aqui.
--    Os dois municipios tem cadastros diferentes e a mesma migration roda nos
--    dois; preco entra pela tela de custos ou pela importacao com conferencia.
--  - codigo_sus: codigo SIGTAP, 10 digitos com zero a esquerda. Campo NOVO, e nao
--    substituicao de especialidade.codigo: o codigo atual e a chave do
--    agendamento entre tela e backend e de varias consultas. NAO e unico de
--    proposito — o catalogo tem conceitos duplicados (GLICOSE e GLICEMIA_JEJUM,
--    PSA_TOTAL e PSA_LIVRE) que apontam para o mesmo procedimento SIGTAP.
--  - valor_origem / valor_atualizado_em / valor_atualizado_por_id: de onde veio
--    o preco (MANUAL ou IMPORTACAO), quando e por quem. A importacao usa a
--    origem para avisar antes de sobrescrever um valor ajustado a mao.
--
-- Operacao ADITIVA: colunas anulaveis, nenhuma linha existente muda.

ALTER TABLE especialidade ADD COLUMN IF NOT EXISTS valor_unitario NUMERIC(12,2);
ALTER TABLE especialidade ADD COLUMN IF NOT EXISTS codigo_sus VARCHAR(10);
ALTER TABLE especialidade ADD COLUMN IF NOT EXISTS valor_origem VARCHAR(20);
ALTER TABLE especialidade ADD COLUMN IF NOT EXISTS valor_atualizado_em TIMESTAMP;
ALTER TABLE especialidade ADD COLUMN IF NOT EXISTS valor_atualizado_por_id BIGINT;

ALTER TABLE especialidade DROP CONSTRAINT IF EXISTS ck_especialidade_valor_unitario;
ALTER TABLE especialidade
    ADD CONSTRAINT ck_especialidade_valor_unitario
    CHECK (valor_unitario IS NULL OR valor_unitario >= 0);

ALTER TABLE especialidade DROP CONSTRAINT IF EXISTS ck_especialidade_codigo_sus;
ALTER TABLE especialidade
    ADD CONSTRAINT ck_especialidade_codigo_sus
    CHECK (codigo_sus IS NULL OR codigo_sus ~ '^[0-9]{10}$');

ALTER TABLE especialidade DROP CONSTRAINT IF EXISTS fk_especialidade_valor_atualizado_por;
ALTER TABLE especialidade
    ADD CONSTRAINT fk_especialidade_valor_atualizado_por
    FOREIGN KEY (valor_atualizado_por_id) REFERENCES usuarios(id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS ix_especialidade_codigo_sus ON especialidade (codigo_sus);
