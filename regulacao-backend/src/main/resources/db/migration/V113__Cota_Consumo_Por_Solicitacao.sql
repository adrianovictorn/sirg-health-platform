-- V113: a cota passa a ser consumida por SOLICITACAO (paciente), nao por item.
--
-- Contexto: ate a V112 cada exame/especialidade agendado consumia uma vaga. Um
-- paciente com 19 exames de laboratorio numa cota de 10 vagas so conseguia
-- agendar 10 ("a ultima vaga ja foi usada por outro item do mesmo lote"). A
-- cota pactuada com o executante conta PACIENTES: uma solicitacao ocupa no
-- maximo uma vaga de cada cota que incide sobre ela, quantos itens forem
-- agendados nela (num lote so ou em agendamentos separados). A vaga volta
-- quando sai o ultimo item da solicitacao que a ocupava (CotaUnidadeService).
--
-- Este script recalcula quantidade_utilizada das cotas VIGENTES (data ou mes
-- a partir de hoje) pela regra nova: numero de solicitacoes distintas que
-- consomem a cota — mesma reconstrucao de CotaUnidadeService.consumidoresDe:
--   * pela FK solicitacao_especialidade.cota_unidade_id, so para cota
--     especifica (profissional, local ou horario) e so com o agendamento
--     dentro do periodo da cota (a FK pode ser velha);
--   * por incidencia, para cota sem profissional: item sem FK, mesmo titular
--     (unidade ou grupo de unidades), mesmo escopo (especialidade, grupo ou
--     geral) e agendamento no periodo;
--   * cota com dias_semana so incide nesses dias.
--
-- So REDUZ (LEAST com o valor atual), nunca aumenta: a reconstrucao por
-- incidencia tambem alcanca itens agendados por ADMIN/GESTOR, que nunca
-- consumiram cota, e aumentar o contador poderia deixar saldo negativo.
-- Cotas passadas ficam como estao (historico e indicadores ja fechados).
--
-- Idempotente: rodar de novo nao muda nada (LEAST sobre o proprio resultado).
-- Nao altera a coluna version (o consumo atomico tambem nao altera).
-- Conferencia antes/depois: db/preflight/preflight_v113.sql.

WITH cp AS (
    SELECT c.id, c.unidade_id, c.grupo_unidades_id, c.especialidade_id, c.grupo_especialidades_id,
           c.profissional_id, c.dias_semana,
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
    SELECT cp.id AS cota_id, se.solicitacao_id, ag.data_agendada
    FROM cp
    JOIN solicitacao_especialidade se ON se.cota_unidade_id = cp.id
    JOIN agendamento_solicitacao ag ON ag.id = se.agendamento_id
    WHERE cp.especifica
      AND ag.data_agendada BETWEEN cp.de AND cp.ate
    UNION
    SELECT cp.id AS cota_id, se.solicitacao_id, ag.data_agendada
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
    SELECT k.cota_id, COUNT(DISTINCT k.solicitacao_id) AS n
    FROM consumo k
    JOIN cp ON cp.id = k.cota_id
    WHERE cp.dias_semana IS NULL OR btrim(cp.dias_semana) = ''
       OR position((ARRAY['SEG','TER','QUA','QUI','SEX','SAB','DOM'])[EXTRACT(ISODOW FROM k.data_agendada)::int]
                   IN upper(cp.dias_semana)) > 0
    GROUP BY k.cota_id
)
UPDATE cota_unidade c
SET quantidade_utilizada = COALESCE(k.n, 0)
FROM cp
LEFT JOIN contagem k ON k.cota_id = cp.id
WHERE c.id = cp.id
  AND c.quantidade_utilizada > COALESCE(k.n, 0);
