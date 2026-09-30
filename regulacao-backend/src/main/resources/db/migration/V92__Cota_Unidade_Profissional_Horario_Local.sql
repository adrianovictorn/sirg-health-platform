-- V92: cota_unidade ganha profissional executante, horario e local opcionais.
--
-- Contexto: a unidade agenda hoje sem saber qual profissional vai atender —
-- quando duas cotas da mesma especialidade cobrem o mesmo dia (ex.: dois
-- ginecologistas), nao ha como provar quem atendeu quem. Estes campos viram
-- um "espelho" mostrado na tela de agendamento, travando a unidade a nao
-- registrar dado diferente do liberado na cota.
--
-- Profissional e local sao OPCIONAIS (cota geral/laboratorio continua sem
-- eles, como hoje). Horario e configuravel: horario_dinamico = true exige
-- tempo_medio_atendimento_minutos (calculo futuro de slots); false aceita
-- hora_inicial/hora_final como periodo livre informado a mao.
--
-- Nao ha coluna de data nova: cota com estes campos reaproveita a coluna
-- data_especifica ja existente, exigindo tipoPeriodo = DATA (validado no
-- service) — evita duas colunas de data competindo pela mesma informacao.
--
-- Operacoes ADITIVAS: nenhuma coluna removida, nenhum dado existente alterado.
-- Todos os campos sao NULL-aveis; cotas ja cadastradas continuam validas sem
-- migration de dados.

ALTER TABLE cota_unidade ADD COLUMN IF NOT EXISTS profissional_id BIGINT;

ALTER TABLE cota_unidade
    DROP CONSTRAINT IF EXISTS fk_cota_profissional;

ALTER TABLE cota_unidade
    ADD CONSTRAINT fk_cota_profissional
    FOREIGN KEY (profissional_id) REFERENCES profissional(id) ON DELETE RESTRICT;

ALTER TABLE cota_unidade ADD COLUMN IF NOT EXISTS local_agendamento_id BIGINT;

ALTER TABLE cota_unidade
    DROP CONSTRAINT IF EXISTS fk_cota_local_agendamento;

ALTER TABLE cota_unidade
    ADD CONSTRAINT fk_cota_local_agendamento
    FOREIGN KEY (local_agendamento_id) REFERENCES local_agendamento(id) ON DELETE RESTRICT;

ALTER TABLE cota_unidade ADD COLUMN IF NOT EXISTS horario_dinamico BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE cota_unidade ADD COLUMN IF NOT EXISTS tempo_medio_atendimento_minutos INT;
ALTER TABLE cota_unidade ADD COLUMN IF NOT EXISTS hora_inicial TIME;
ALTER TABLE cota_unidade ADD COLUMN IF NOT EXISTS hora_final TIME;

ALTER TABLE cota_unidade
    DROP CONSTRAINT IF EXISTS ck_cota_horario_dinamico_coerente;

ALTER TABLE cota_unidade
    ADD CONSTRAINT ck_cota_horario_dinamico_coerente
    CHECK (
        (horario_dinamico = FALSE)
        OR (horario_dinamico = TRUE AND tempo_medio_atendimento_minutos IS NOT NULL
            AND tempo_medio_atendimento_minutos > 0)
    );

ALTER TABLE cota_unidade
    DROP CONSTRAINT IF EXISTS ck_cota_horario_final_apos_inicial;

ALTER TABLE cota_unidade
    ADD CONSTRAINT ck_cota_horario_final_apos_inicial
    CHECK (hora_final IS NULL OR hora_inicial IS NULL OR hora_final > hora_inicial);

CREATE INDEX IF NOT EXISTS ix_cota_unidade_profissional
    ON cota_unidade (profissional_id);
