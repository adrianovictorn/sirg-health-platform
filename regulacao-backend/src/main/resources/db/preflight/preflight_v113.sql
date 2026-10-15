-- ============================================================================
-- PRE-FLIGHT — conferencia da V113 (cota consumida por solicitacao)
-- ============================================================================
--
-- SOMENTE LEITURA. Rode na VPS ANTES do deploy (mostra o que a V113 vai mudar)
-- e DEPOIS (a coluna `muda` deve vir toda 'nao'):
--
--   docker compose -f docker-compose.prod.yaml exec -T postgres \
--     psql -U "$DB_USER" -d "$DB_NAME" -f - < preflight_v113.sql
--
-- Uma linha por cota vigente (data ou mes a partir de hoje) que tem consumo:
--   utilizada_atual  -> quantidade_utilizada hoje (regra antiga: 1 por item)
--   itens            -> itens agendados que consomem a cota
--   solicitacoes     -> solicitacoes distintas (regra nova)
--   utilizada_v113   -> o que a V113 grava: LEAST(utilizada_atual, solicitacoes)
--   vagas_liberadas  -> utilizada_atual - utilizada_v113
--
-- Mesma reconstrucao de V113__Cota_Consumo_Por_Solicitacao.sql. Nao mostra
-- nome, CPF nem CNS de paciente.
-- ============================================================================

WITH cp AS (
    SELECT c.id, c.unidade_id, c.grupo_unidades_id, c.especialidade_id, c.grupo_especialidades_id,
           c.profissional_id, c.dias_semana, c.tipo_periodo, c.periodo, c.data_especifica,
           c.quantidade_total, c.quantidade_utilizada,
           (c.profissional_id IS NOT NULL OR c.local_agendamento_id IS NOT NULL OR c.hora_inicial IS NOT NULL)
               AS especifica,
           CASE WHEN c.tipo_periodo = 'MENSAL' THEN to_date(c.periodo || '-01', 'YYYY-MM-DD')
                ELSE c.data_especifica END AS de,
           CASE WHEN c.tipo_periodo = 'MENSAL'
                THEN (to_date(c.periodo || '-01', 'YYYY-MM-DD') + INTERVAL '1 month' - INTERVAL '1 day')::date
                ELSE c.data_especifica END AS ate
    FROM cota_unidade c
    WHERE (c.tipo_periodo = 'DATA' AND c.data_especifica >= CURRENT_DATE)
       OR (c.tipo_periodo = 'MENSAL' AND c.periodo >= to_char(CURRENT_DATE, 'YYYY-MM'))
),
consumo AS (
    SELECT cp.id AS cota_id, se.id AS item_id, se.solicitacao_id, ag.data_agendada
    FROM cp
    JOIN solicitacao_especialidade se ON se.cota_unidade_id = cp.id
    JOIN agendamento_solicitacao ag ON ag.id = se.agendamento_id
    WHERE cp.especifica
      AND ag.data_agendada BETWEEN cp.de AND cp.ate
    UNION
    SELECT cp.id AS cota_id, se.id AS item_id, se.solicitacao_id, ag.data_agendada
    FROM cp
    JOIN agendamento_solicitacao ag ON ag.data_agendada BETWEEN cp.de AND cp.ate
    JOIN solicitacao_especialidade se ON se.agendamento_id = ag.id AND se.cota_unidade_id IS NULL
    JOIN solicitacao s ON s.id = se.solicitacao_id
    LEFT JOIN especialidade e ON e.id = se.especialidade_id
    WHERE cp.profissional_id IS NULL
      AND (s.unidade_id = cp.unidade_id
           OR s.unidade_id IN (SELECT un.id FROM unidade un WHERE un.grupo_relatorio_id = cp.grupo_unidades_id))
      AND ((cp.especialidade_id IS NULL AND cp.grupo_especialidades_id IS NULL)
           OR e.id = cp.especialidade_id
           OR e.grupo_relatorio_id = cp.grupo_especialidades_id)
),
contagem AS (
    SELECT k.cota_id, COUNT(DISTINCT k.item_id) AS itens, COUNT(DISTINCT k.solicitacao_id) AS solicitacoes
    FROM consumo k
    JOIN cp ON cp.id = k.cota_id
    WHERE cp.dias_semana IS NULL OR btrim(cp.dias_semana) = ''
       OR position((ARRAY['SEG','TER','QUA','QUI','SEX','SAB','DOM'])[EXTRACT(ISODOW FROM k.data_agendada)::int]
                   IN upper(cp.dias_semana)) > 0
    GROUP BY k.cota_id
)
SELECT cp.id AS cota_id,
       COALESCE(cp.data_especifica::text, cp.periodo) AS periodo,
       cp.quantidade_total AS total,
       cp.quantidade_utilizada AS utilizada_atual,
       COALESCE(k.itens, 0) AS itens,
       COALESCE(k.solicitacoes, 0) AS solicitacoes,
       LEAST(cp.quantidade_utilizada, COALESCE(k.solicitacoes, 0)) AS utilizada_v113,
       cp.quantidade_utilizada - LEAST(cp.quantidade_utilizada, COALESCE(k.solicitacoes, 0)) AS vagas_liberadas,
       CASE WHEN cp.quantidade_utilizada > COALESCE(k.solicitacoes, 0) THEN 'sim' ELSE 'nao' END AS muda
FROM cp
LEFT JOIN contagem k ON k.cota_id = cp.id
WHERE cp.quantidade_utilizada > 0 OR k.cota_id IS NOT NULL
ORDER BY cp.de, cp.id;
