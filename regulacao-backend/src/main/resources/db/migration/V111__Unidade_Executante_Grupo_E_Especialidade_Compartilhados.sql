-- V111: grupo de relatorio e especialidade avulsa podem estar em MAIS DE UMA
-- unidade executante (ex.: dois laboratorios que fazem os mesmos exames).
--
-- A V110 tornava cada grupo e cada especialidade avulsa exclusivos de uma
-- executante (indices unicos so na coluna do grupo/especialidade). A V110 nao e
-- editada — ja rodou em ambiente de teste —; esta migration troca os dois indices
-- unicos por indices unicos no PAR (grupo/especialidade, unidade), que continuam
-- impedindo o vinculo repetido na mesma executante e cobrem a busca por
-- grupo/especialidade usada no recorte da tela de Atendimentos.
--
-- Pertencimento de um item a uma executante (calculado na consulta, ver
-- AtendimentoExecutanteRepository.FILTRO_EXECUTANTE):
--   - com local de agendamento: decide o dono do local (inalterado);
--   - sem local: so a executante que for a UNICA (ativa, EXECUTANTE/AMBOS) com
--     aquela especialidade; especialidade compartilhada sem local so aparece na
--     visao municipal.
--
-- Nenhum dado alterado; as linhas existentes ja respeitam o indice de par.
-- Decisao registrada no ADR 0013.

DROP INDEX IF EXISTS ux_unidade_executante_grupo_grupo;

CREATE UNIQUE INDEX IF NOT EXISTS ux_unidade_executante_grupo_par
    ON unidade_executante_grupo (grupo_relatorio_id, unidade_id);

DROP INDEX IF EXISTS ux_unidade_executante_especialidade_esp;

CREATE UNIQUE INDEX IF NOT EXISTS ux_unidade_executante_especialidade_par
    ON unidade_executante_especialidade (especialidade_id, unidade_id);
