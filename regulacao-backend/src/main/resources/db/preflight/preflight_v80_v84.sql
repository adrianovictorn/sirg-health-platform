-- ============================================================================
-- PRE-FLIGHT — validação do banco de PRODUÇÃO antes de aplicar V80..V84
-- ============================================================================
--
-- Este script é SOMENTE LEITURA. Não altera nada. Rode-o na VPS antes do deploy:
--
--   docker compose -f docker-compose.prod.yaml exec -T postgres \
--     psql -U "$DB_USER" -d "$DB_NAME" -f - < preflight_v80_v84.sql
--
-- Ou, com o arquivo já copiado para dentro do container:
--   psql -U <user> -d <banco> -f preflight_v80_v84.sql
--
-- Leia a coluna `resultado` de cada bloco:
--   OK        -> pode prosseguir
--   ATENCAO   -> não impede o deploy, mas muda o comportamento operacional
--   BLOQUEIO  -> corrija antes de aplicar as migrations
--
-- ============================================================================

\echo ''
\echo '=== 1. Estado atual do Flyway ================================================'
-- As migrations novas assumem que V79 é a última aplicada. Se a versão for menor,
-- as migrations intermediárias rodarão junto — verifique-as também.
SELECT
    MAX(version::numeric) AS ultima_versao_aplicada,
    CASE
        WHEN MAX(version::numeric) >= 84 THEN 'OK - V80..V84 ja aplicadas'
        WHEN MAX(version::numeric) = 79  THEN 'OK - pronto para V80..V84'
        WHEN MAX(version::numeric) <  79 THEN 'ATENCAO - banco atras da V79, revise as intermediarias'
        ELSE 'ATENCAO - estado inesperado'
    END AS resultado
FROM flyway_schema_history
WHERE success = TRUE AND version ~ '^[0-9]+$';

\echo ''
\echo '=== 1b. Alguma migration FALHOU anteriormente? ================================'
-- Uma linha com success=false trava o Flyway no próximo deploy (precisa de repair).
SELECT
    COALESCE(
        (SELECT string_agg(version || ' (' || description || ')', ', ')
         FROM flyway_schema_history WHERE success = FALSE),
        '(nenhuma)'
    ) AS migrations_com_falha,
    CASE
        WHEN EXISTS (SELECT 1 FROM flyway_schema_history WHERE success = FALSE)
        THEN 'BLOQUEIO - rode flyway repair antes do deploy'
        ELSE 'OK'
    END AS resultado;

\echo ''
\echo '=== 2. V83: cargos existentes vs. nova constraint ============================='
-- A V83 apenas AMPLIA a lista da V50 (acrescenta ADMIN_UNIDADE), portanto nao deve
-- falhar. Esta consulta confirma que nao ha cargo fora da lista permitida.
SELECT
    cargo,
    COUNT(*) AS qtd,
    CASE
        WHEN cargo IN ('ADMIN','ADMIN_UNIDADE','USER','PACIENTE',
                       'ENFERMEIRO','MEDICO','RECEPCAO','COORD_TRANSPORTE')
        THEN 'OK'
        ELSE 'BLOQUEIO - cargo fora da constraint da V83'
    END AS resultado
FROM usuarios
GROUP BY cargo
ORDER BY cargo;

\echo ''
\echo '=== 3. V80: colunas novas ja existem em solicitacao? ========================='
-- As migrations usam ADD COLUMN IF NOT EXISTS, entao a presenca previa nao quebra
-- o deploy. Mas se ja existirem com OUTRO tipo, o Hibernate (ddl-auto=validate)
-- recusa o boot. Esperado: 0 linhas.
SELECT
    column_name, data_type, character_maximum_length,
    'ATENCAO - coluna ja existe, confira o tipo esperado' AS resultado
FROM information_schema.columns
WHERE table_name = 'solicitacao'
  AND column_name IN ('nome_pai','nome_mae','endereco');

\echo ''
\echo '=== 4. IMPACTO OPERACIONAL: solicitacoes legadas sem os novos campos ========='
-- Os campos sao OBRIGATORIOS apenas no CADASTRO NOVO (SolicitacaoCreateDTO).
-- A EDICAO (SolicitacaoUpdateDTO) NAO os exige, justamente para que as
-- solicitacoes anteriores a V80 continuem editaveis sem preenchimento forcado.
-- Portanto: nenhum impacto operacional sobre os registros existentes.
SELECT
    COUNT(*) AS total_solicitacoes_legadas,
    'OK - legado permanece legivel, agendavel e editavel' AS resultado
FROM solicitacao;

\echo ''
\echo '=== 4b. IMPACTO OPERACIONAL: CNS vazio em registros legados =================='
-- cns e NOT NULL desde a V1, mas string vazia satisfaz NOT NULL. Como o CNS so e
-- exigido no cadastro novo, isto e apenas um indicador de qualidade da base.
SELECT
    COUNT(*) AS solicitacoes_com_cns_vazio,
    'INFO - nao bloqueia edicao; apenas indica registros incompletos' AS resultado
FROM solicitacao
WHERE cns IS NULL OR btrim(cns) = '';

\echo ''
\echo '=== 4c. IMPACTO OPERACIONAL: nome do paciente vazio =========================='
SELECT
    COUNT(*) AS solicitacoes_com_nome_vazio,
    'INFO - indicador de qualidade da base' AS resultado
FROM solicitacao
WHERE nome_paciente IS NULL OR btrim(nome_paciente) = '';

\echo ''
\echo '=== 5. V82/V84: cota_unidade satisfaz o CHECK de titular exclusivo? =========='
-- Apos a V82 cada cota deve ter unidade_id OU grupo_relatorio_id (nunca os dois,
-- nunca nenhum). As linhas atuais tem unidade_id NOT NULL e grupo_relatorio_id novo
-- (NULL), entao devem passar. Esperado: violacoes = 0.
SELECT
    COUNT(*) AS total_cotas,
    COUNT(*) FILTER (WHERE unidade_id IS NULL) AS violacoes,
    CASE WHEN COUNT(*) FILTER (WHERE unidade_id IS NULL) = 0
         THEN 'OK'
         ELSE 'BLOQUEIO - cota sem unidade_id impede o CHECK ck_cota_titular_exclusivo'
    END AS resultado
FROM cota_unidade;

\echo ''
\echo '=== 6. V84: duplicidades que impediriam os indices unicos ===================='
-- Os indices recriados sao MAIS restritos em escopo que os da V78 (ganham o filtro
-- unidade_id IS NOT NULL), entao cobrem menos linhas. Se a V78 ja esta aplicada,
-- nao pode haver duplicidade. Esperado: 0 linhas em ambos os blocos.
SELECT 'MENSAL com especialidade' AS indice, unidade_id, especialidade_id, periodo, COUNT(*) AS qtd,
       'BLOQUEIO - duplicidade impede indice unico' AS resultado
FROM cota_unidade
WHERE tipo_periodo = 'MENSAL' AND unidade_id IS NOT NULL AND especialidade_id IS NOT NULL
GROUP BY unidade_id, especialidade_id, periodo HAVING COUNT(*) > 1;

SELECT 'MENSAL geral' AS indice, unidade_id, periodo, COUNT(*) AS qtd,
       'BLOQUEIO - duplicidade impede indice unico' AS resultado
FROM cota_unidade
WHERE tipo_periodo = 'MENSAL' AND unidade_id IS NOT NULL AND especialidade_id IS NULL
GROUP BY unidade_id, periodo HAVING COUNT(*) > 1;

SELECT 'DATA com especialidade' AS indice, unidade_id, especialidade_id, data_especifica, COUNT(*) AS qtd,
       'BLOQUEIO - duplicidade impede indice unico' AS resultado
FROM cota_unidade
WHERE tipo_periodo = 'DATA' AND unidade_id IS NOT NULL AND especialidade_id IS NOT NULL
GROUP BY unidade_id, especialidade_id, data_especifica HAVING COUNT(*) > 1;

SELECT 'DATA geral' AS indice, unidade_id, data_especifica, COUNT(*) AS qtd,
       'BLOQUEIO - duplicidade impede indice unico' AS resultado
FROM cota_unidade
WHERE tipo_periodo = 'DATA' AND unidade_id IS NOT NULL AND especialidade_id IS NULL
GROUP BY unidade_id, data_especifica HAVING COUNT(*) > 1;

\echo ''
\echo '=== 7. V82: grupo_relatorio disponivel para agrupar unidades? ================'
-- A V82 NAO cria tabela nova: reaproveita grupo_relatorio (V58) e apenas adiciona
-- unidade.grupo_relatorio_id. Esperado: a tabela existe (1).
SELECT COUNT(*) AS grupo_relatorio_existe,
       CASE WHEN COUNT(*) = 1 THEN 'OK' ELSE 'BLOQUEIO - grupo_relatorio ausente (V58 nao aplicada)' END AS resultado
FROM information_schema.tables WHERE table_name = 'grupo_relatorio';

\echo ''
\echo '=== 7b. Grupos disponiveis para vincular unidades ============================'
-- Os grupos existentes hoje agrupam ESPECIALIDADES para relatorio. Os mesmos
-- grupos passam a poder agrupar UNIDADES para cota. Nenhuma unidade e vinculada
-- automaticamente: o vinculo e manual, em /admin/unidades.
SELECT id, codigo, nome, ativo,
       'INFO - disponivel tambem como grupo de cota' AS resultado
FROM grupo_relatorio ORDER BY nome;

\echo ''
\echo '=== 8. Consistencia de cotas ja consumidas =================================='
-- O estorno (novo) decrementa quantidade_utilizada. Se hoje houver cota com
-- utilizada > total (possivel por conta da antiga condicao de corrida), a edicao
-- da cota passara a ser recusada ate que o total seja ajustado.
SELECT
    COUNT(*) FILTER (WHERE quantidade_utilizada > quantidade_total) AS cotas_estouradas,
    CASE WHEN COUNT(*) FILTER (WHERE quantidade_utilizada > quantidade_total) = 0
         THEN 'OK'
         ELSE 'ATENCAO - cota com utilizado > total; ajuste o total antes de editar'
    END AS resultado
FROM cota_unidade;

\echo ''
\echo '=== 9. Usuarios que ficariam sem unidade com o novo perfil =================='
-- ADMIN_UNIDADE exige unidade de lotacao: sem ela o acesso e negado (proposital).
-- Antes de PROMOVER alguem a esse perfil, confirme que a unidade esta vinculada.
SELECT
    COUNT(*) FILTER (WHERE unidade_id IS NULL) AS usuarios_sem_unidade,
    'INFO - so importa para quem for virar ADMIN_UNIDADE' AS resultado
FROM usuarios
WHERE ativo = TRUE;

\echo ''
\echo '=== FIM ====================================================================='
\echo 'Nenhum BLOQUEIO acima => seguro aplicar V80..V84.'
\echo ''
