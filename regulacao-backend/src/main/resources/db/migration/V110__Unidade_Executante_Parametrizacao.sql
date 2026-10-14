-- V110: unidade executante parametrizada — o que cada local que REALIZA o
-- atendimento (Centro de Fisioterapia, laboratorio, ambulatorio do hospital)
-- faz, em quais locais de agendamento ele atende e quais operadores (USER) o
-- representam na tela de Atendimentos.
--
-- Executante continua sendo uma Unidade (tipo EXECUTANTE ou AMBOS, V86); nao ha
-- entidade nova. A parametrizacao tem quatro partes:
--
--   unidade_executante_grupo          Grupos de Relatorio inteiros que a unidade
--                                     realiza (atalho). Um grupo pertence a no
--                                     maximo uma executante.
--   unidade_executante_especialidade  Especialidades avulsas (ajuste fino). Uma
--                                     especialidade avulsa pertence a no maximo
--                                     uma executante. O cruzamento "grupo de A x
--                                     avulsa de B" e barrado no service.
--   local_agendamento.unidade_executante_id
--                                     Liga os locais ja cadastrados em producao a
--                                     executante. Um local pertence a no maximo
--                                     uma executante; nulo = nao ligado.
--   usuario_unidade_executante        Operadores vinculados. Um usuario pode estar
--                                     em varias executantes. A lotacao unica
--                                     (usuarios.unidade_id) nao e tocada.
--
-- Pertencimento de um item a uma executante (calculado na consulta, nunca
-- gravado): especialidade no conjunto efetivo dela E local do agendamento ligado
-- a ela — ou, quando o agendamento nao tem local, so a especialidade. Por isso
-- nao ha backfill: o historico aparece por deducao.
--
-- grupo_relatorio_id e RESTRICT para combinar com a recusa amigavel (409) em
-- GrupoRelatorioService; demais FKs em CASCADE/SET NULL para nao fazer falhar a
-- exclusao de unidade, usuario, especialidade ou local que ja existe hoje.
--
-- Nenhum dado municipal: a parametrizacao e feita pela tela, em cada instancia.
-- Operacoes ADITIVAS: nenhuma coluna removida, nenhum dado existente alterado.

ALTER TABLE local_agendamento ADD COLUMN IF NOT EXISTS unidade_executante_id BIGINT;

ALTER TABLE local_agendamento
    DROP CONSTRAINT IF EXISTS fk_local_agendamento_unidade_executante;

ALTER TABLE local_agendamento
    ADD CONSTRAINT fk_local_agendamento_unidade_executante
    FOREIGN KEY (unidade_executante_id) REFERENCES unidade(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS ix_local_agendamento_unidade_executante
    ON local_agendamento (unidade_executante_id);

CREATE TABLE IF NOT EXISTS unidade_executante_grupo (
    id                 BIGSERIAL   PRIMARY KEY,
    unidade_id         BIGINT      NOT NULL REFERENCES unidade(id) ON DELETE CASCADE,
    grupo_relatorio_id BIGINT      NOT NULL REFERENCES grupo_relatorio(id) ON DELETE RESTRICT,
    criado_em          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_unidade_executante_grupo_grupo
    ON unidade_executante_grupo (grupo_relatorio_id);

CREATE INDEX IF NOT EXISTS ix_unidade_executante_grupo_unidade
    ON unidade_executante_grupo (unidade_id);

CREATE TABLE IF NOT EXISTS unidade_executante_especialidade (
    id               BIGSERIAL   PRIMARY KEY,
    unidade_id       BIGINT      NOT NULL REFERENCES unidade(id) ON DELETE CASCADE,
    especialidade_id BIGINT      NOT NULL REFERENCES especialidade(id) ON DELETE CASCADE,
    criado_em        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_unidade_executante_especialidade_esp
    ON unidade_executante_especialidade (especialidade_id);

CREATE INDEX IF NOT EXISTS ix_unidade_executante_especialidade_unidade
    ON unidade_executante_especialidade (unidade_id);

CREATE TABLE IF NOT EXISTS usuario_unidade_executante (
    id         BIGSERIAL   PRIMARY KEY,
    usuario_id BIGINT      NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    unidade_id BIGINT      NOT NULL REFERENCES unidade(id) ON DELETE CASCADE,
    criado_em  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_usuario_unidade_executante
    ON usuario_unidade_executante (usuario_id, unidade_id);

CREATE INDEX IF NOT EXISTS ix_usuario_unidade_executante_unidade
    ON usuario_unidade_executante (unidade_id);
