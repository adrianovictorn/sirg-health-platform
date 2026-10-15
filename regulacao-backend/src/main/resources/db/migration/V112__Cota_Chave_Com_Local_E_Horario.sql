-- V112: uk_cota_data (V94) e uk_cota_mensal (V99) passam a considerar local de
-- atendimento e faixa de horario (V92), alem do profissional.
--
-- Contexto: a chave de unicidade ja aceitava duas cotas da mesma
-- unidade/escopo/data para profissionais diferentes, mas recusava duas cotas
-- que diferem so no LOCAL (ex.: UBS Central x Hospital Regional) ou so na
-- FAIXA DE HORARIO (manha x tarde) com o mesmo profissional — ou sem
-- profissional. A unidade precisa ofertar mais de uma cota no mesmo dia desde
-- que local, profissional ou horario sejam diferentes.
--
-- Nova regra: no maximo uma cota por
-- unidade/escopo/data(ou periodo)/profissional/local/hora_inicial/hora_final.
-- Campos opcionais nulos entram na chave via COALESCE, como ja faziam os
-- demais — duas cotas identicas com os campos vazios continuam recusadas.
-- O sentinela de hora (00:00, 00:00) nunca colide com uma faixa real: o CHECK
-- ck_cota_horario_final_apos_inicial (V92) exige hora_final > hora_inicial.
--
-- A regra "cota generica (sem profissional, local e horario) nao convive com
-- cota especifica na mesma data/periodo" NAO cabe num indice unico; fica em
-- CotaUnidadeService, no cadastro e na edicao manuais.
--
-- Operacao ADITIVA na pratica: recria dois indices existentes com colunas a
-- mais na chave (a chave nova contem a antiga) — nenhuma linha e removida,
-- alterada ou passa a conflitar.

DROP INDEX IF EXISTS uk_cota_data;

CREATE UNIQUE INDEX IF NOT EXISTS uk_cota_data
    ON cota_unidade (
        COALESCE(unidade_id, -1),
        COALESCE(grupo_unidades_id, -1),
        COALESCE(especialidade_id, -1),
        COALESCE(grupo_especialidades_id, -1),
        COALESCE(profissional_id, -1),
        COALESCE(local_agendamento_id, -1),
        COALESCE(hora_inicial, TIME '00:00'),
        COALESCE(hora_final, TIME '00:00'),
        data_especifica
    )
    WHERE tipo_periodo = 'DATA';

DROP INDEX IF EXISTS uk_cota_mensal;

CREATE UNIQUE INDEX IF NOT EXISTS uk_cota_mensal
    ON cota_unidade (
        COALESCE(unidade_id, -1),
        COALESCE(grupo_unidades_id, -1),
        COALESCE(especialidade_id, -1),
        COALESCE(grupo_especialidades_id, -1),
        COALESCE(profissional_id, -1),
        COALESCE(local_agendamento_id, -1),
        COALESCE(hora_inicial, TIME '00:00'),
        COALESCE(hora_final, TIME '00:00'),
        periodo
    )
    WHERE tipo_periodo = 'MENSAL';
