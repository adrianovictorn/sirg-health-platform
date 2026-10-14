-- =====================================================================
-- MASSA LOCAL da tela de Atendimentos (/atendimentos) — SOMENTE banco de
-- desenvolvimento. NAO e migration: fica fora de src/main/resources, entao
-- nao entra no JAR nem no classpath do Flyway, e nunca roda sozinha.
--
-- O que cria: 8 pacientes ficticios com atendimento na data de HOJE (fuso da
-- Bahia), espalhados por ate 3 unidades ativas ja cadastradas, mais um sem
-- unidade — nos status AGENDADO, REALIZADO (Confirmado), FALTOU e CANCELADO,
-- com exame e consulta, e um paciente com dois itens no mesmo agendamento.
--
-- Idempotente: apaga a massa anterior (marcada em solicitacao.observacoes) e
-- recria. Rodar de novo no dia seguinte "move" a massa para hoje. Nao cria
-- nem altera unidade, especialidade, cota ou teto. Exige a migration V109.
--
-- Travas contra execucao acidental fora do ambiente local — as tres precisam
-- passar, senao o script aborta sem gravar nada:
--   1. o banco tem de se chamar dev_marcacao_database;
--   2. o usuario conectado tem de ser dev_user;
--   3. a sessao tem de declarar a confirmacao abaixo.
-- Nenhuma delas e uma garantia absoluta: confira a conexao antes de rodar.
--
-- Como rodar (na MESMA sessao, a confirmacao primeiro):
--   SET sirg.confirmo_banco_local = 'sim';
--   \i regulacao-backend/dev/massa_atendimentos_hoje.sql
-- Em cliente grafico (DBeaver, pgAdmin): execute o SET e depois este arquivo
-- inteiro como script, na mesma conexao.
--
-- Para remover a massa sem recriar:
--   DELETE FROM solicitacao_especialidade WHERE solicitacao_id IN
--     (SELECT id FROM solicitacao WHERE observacoes = 'MASSA_LOCAL_ATENDIMENTOS');
--   DELETE FROM agendamento_solicitacao WHERE solicitacao_id IN
--     (SELECT id FROM solicitacao WHERE observacoes = 'MASSA_LOCAL_ATENDIMENTOS');
--   DELETE FROM solicitacao WHERE observacoes = 'MASSA_LOCAL_ATENDIMENTOS';
-- =====================================================================

BEGIN;

DO $$
DECLARE
    marcador   CONSTANT text := 'MASSA_LOCAL_ATENDIMENTOS';
    hoje       CONSTANT date := (now() AT TIME ZONE 'America/Bahia')::date;
    unidades   bigint[];
    exames     bigint[];
    consulta   bigint;
    n_unidades int;
    s_id       bigint;
    ag_id      bigint;
    p          record;
BEGIN
    IF current_database() <> 'dev_marcacao_database' THEN
        RAISE EXCEPTION 'Massa local recusada: banco "%" nao e o de desenvolvimento.', current_database();
    END IF;
    IF current_user <> 'dev_user' THEN
        RAISE EXCEPTION 'Massa local recusada: usuario "%" nao e o de desenvolvimento.', current_user;
    END IF;
    IF coalesce(current_setting('sirg.confirmo_banco_local', true), '') <> 'sim' THEN
        RAISE EXCEPTION 'Massa local recusada: rode antes, na mesma sessao, SET sirg.confirmo_banco_local = ''sim'';';
    END IF;

    SELECT array_agg(id) INTO unidades
      FROM (SELECT id FROM unidade WHERE ativo ORDER BY id LIMIT 3) u;
    SELECT array_agg(id) INTO exames
      FROM (SELECT id FROM especialidade
             WHERE ativo AND categoria = 'EXAME_OU_PROCEDIMENTO' ORDER BY id LIMIT 2) e;
    SELECT id INTO consulta
      FROM especialidade WHERE ativo AND categoria = 'ESPECIALIDADE_MEDICA' ORDER BY id LIMIT 1;

    n_unidades := coalesce(array_length(unidades, 1), 0);
    IF n_unidades = 0 THEN
        RAISE EXCEPTION 'Massa local: cadastre ao menos uma unidade ativa antes.';
    END IF;
    IF exames IS NULL OR consulta IS NULL THEN
        RAISE EXCEPTION 'Massa local: e preciso ao menos um exame/procedimento e uma especialidade medica ativos.';
    END IF;

    -- Massa anterior: itens, agendamentos e so entao as solicitacoes.
    DELETE FROM solicitacao_especialidade
     WHERE solicitacao_id IN (SELECT id FROM solicitacao WHERE observacoes = marcador);
    DELETE FROM agendamento_solicitacao
     WHERE solicitacao_id IN (SELECT id FROM solicitacao WHERE observacoes = marcador);
    DELETE FROM solicitacao WHERE observacoes = marcador;

    -- seq, nome, unidade (indice 1..3; 0 = sem unidade), turno, e ate dois itens:
    -- tipo ('E1'/'E2' = exame, 'C' = consulta), status e se a coleta ja foi feita.
    FOR p IN
        SELECT * FROM (VALUES
            (1, 'Ana Ficticia de Souza',      1, 'MANHA', 'E1', 'AGENDADO',  false, NULL, NULL,       false),
            (2, 'Bruno Ficticio Lima',        1, 'MANHA', 'C',  'AGENDADO',  false, NULL, NULL,       false),
            (3, 'Carla Ficticia Santos',      1, 'MANHA', 'E1', 'AGENDADO',  false, 'E2', 'AGENDADO', false),
            (4, 'Davi Ficticio Oliveira',     2, 'MANHA', 'E1', 'REALIZADO', true,  NULL, NULL,       false),
            (5, 'Elisa Ficticia Rocha',       2, 'TARDE', 'C',  'FALTOU',    false, NULL, NULL,       false),
            (6, 'Fabio Ficticio Pereira',     3, 'MANHA', 'E1', 'CANCELADO', false, NULL, NULL,       false),
            (7, 'Gabriela Ficticia Costa',    3, 'TARDE', 'C',  'AGENDADO',  false, NULL, NULL,       false),
            (8, 'Heitor Ficticio Sem Unidade', 0, 'MANHA', 'E1', 'AGENDADO', false, NULL, NULL,       false)
        ) AS t(seq, nome, unidade, turno, tipo1, status1, coleta1, tipo2, status2, coleta2)
    LOOP
        INSERT INTO solicitacao (nome_paciente, cpf_paciente, cns, datanascimento, nome_mae,
                                 telefone, whatsapp_opt_out, observacoes, unidade_id)
        VALUES (p.nome,
                '999000000' || lpad(p.seq::text, 2, '0'),
                '9990000000000' || lpad(p.seq::text, 2, '0'),
                date '1980-01-01' + (p.seq * 400),
                'Mae Ficticia',
                NULL, true, marcador,
                CASE WHEN p.unidade = 0 THEN NULL
                     ELSE unidades[((p.unidade - 1) % n_unidades) + 1] END)
        RETURNING id INTO s_id;

        INSERT INTO agendamento_solicitacao (solicitacao_id, data_agendada, turno, observacoes, data_criacao)
        VALUES (s_id, hoje, p.turno, 'Massa local de atendimentos', now())
        RETURNING id INTO ag_id;

        INSERT INTO solicitacao_especialidade (solicitacao_id, agendamento_id, especialidade_id,
                                               especialidade_solicitada, status, prioridade,
                                               data_cadastro, data_coleta)
        SELECT s_id, ag_id, e.id, e.codigo, i.status, 'NORMAL', now(),
               CASE WHEN i.coleta THEN hoje END
          FROM (VALUES (p.tipo1, p.status1, p.coleta1), (p.tipo2, p.status2, p.coleta2)) AS i(tipo, status, coleta)
          JOIN especialidade e
            ON e.id = CASE i.tipo
                          WHEN 'E1' THEN exames[1]
                          -- Com um unico exame cadastrado, o segundo item vira a consulta.
                          WHEN 'E2' THEN coalesce(exames[2], consulta)
                          WHEN 'C'  THEN consulta
                      END
         WHERE i.tipo IS NOT NULL;
    END LOOP;

    -- Desfechos "lancados na tela de Atendimentos" (V109): o Confirmado e a Falta
    -- ganham autoria e podem ser desfeitos pelo operador. O CANCELADO fica sem
    -- autoria de proposito: e o caso legado, que a tela nao deixa desfazer.
    UPDATE solicitacao_especialidade se
       SET atendimento_registrado_em = now(),
           atendimento_registrado_por_id = (SELECT id FROM usuarios WHERE cargo = 'USER' ORDER BY id LIMIT 1),
           atendimento_observacao = CASE WHEN se.status = 'FALTOU'
                                         THEN 'Paciente nao compareceu (massa local)' END
     WHERE se.status IN ('REALIZADO', 'FALTOU')
       AND se.solicitacao_id IN (SELECT id FROM solicitacao WHERE observacoes = marcador);

    RAISE NOTICE 'Massa local criada para % em % unidade(s).', hoje, n_unidades;
END $$;

COMMIT;
