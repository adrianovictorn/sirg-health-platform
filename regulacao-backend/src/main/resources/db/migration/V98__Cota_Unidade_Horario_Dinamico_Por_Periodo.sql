-- V98: horario_dinamico passa a calcular um horario por vaga dividindo
-- hora_inicial/hora_final igualmente entre quantidade_total pacientes (ver
-- CotaUnidadeService#calcularHorarioSlot), em vez de depender de
-- tempo_medio_atendimento_minutos (V92, nunca chegou a ter o calculo
-- implementado — o comentario da V92 ja dizia "calculo futuro de slots").
--
-- Por isso horario_dinamico = true agora EXIGE hora_inicial e hora_final (a
-- V92 os proibia nesse caso) e NAO exige mais tempo_medio_atendimento_minutos,
-- que vira metadado opcional, sem uso no calculo.
--
-- Nao editamos a CHECK da V92 diretamente (ja aplicada em ambiente de
-- desenvolvimento) — substituimos a constraint por uma nova, operacao
-- aditiva de schema. Nenhuma cota com horario_dinamico = true ainda existe em
-- producao (campo introduzido nesta mesma leva de migrations, ainda nao
-- implantada), entao nao ha necessidade de backfill.

ALTER TABLE cota_unidade
    DROP CONSTRAINT IF EXISTS ck_cota_horario_dinamico_coerente;

ALTER TABLE cota_unidade
    ADD CONSTRAINT ck_cota_horario_dinamico_coerente
    CHECK (
        (horario_dinamico = FALSE)
        OR (horario_dinamico = TRUE AND hora_inicial IS NOT NULL AND hora_final IS NOT NULL)
    );
