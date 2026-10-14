-- V109: registro do desfecho do atendimento pelo operador da unidade executante
-- (tela /atendimentos) e devolucao do paciente a fila pela unidade solicitante.
--
-- Ate aqui o desfecho era so a troca de status do item, e a "observacao da falta"
-- sobrescrevia a observacao do AGENDAMENTO — a nota de quem agendou, compartilhada
-- por todos os itens dele. Agora o item guarda a propria observacao e quem lancou:
--
--   atendimento_observacao        observacao da falta (opcional) ou motivo do
--                                 cancelamento (obrigatorio). O status diz qual.
--   atendimento_registrado_por_id operador que lancou o desfecho.
--   atendimento_registrado_em     quando lancou. E tambem a marca de "lancado na
--                                 tela de Atendimentos": so esses registros podem
--                                 ser desfeitos ali. Faltas antigas (gravadas como
--                                 CANCELADO pelo fluxo legado) ficam com nulo.
--   devolvido_como_item_id        no item FALTOU/CANCELADO, aponta para o item novo
--                                 (AGUARDANDO) criado quando a unidade devolve o
--                                 paciente a fila. O item antigo nao e alterado: a
--                                 vaga e o teto do atendimento perdido continuam
--                                 ocupados e o desfecho segue nos relatorios.
--                                 SET NULL: remover o item novo libera nova devolucao.
--
-- data_coleta (V81) e reutilizada, sem coluna nova. O CHECK de status (V64) ja
-- aceita REALIZADO, FALTOU e CANCELADO e nao e tocado.
--
-- Tudo NULL-avel e sem backfill: nao ha como saber quem lancou os desfechos
-- antigos, nem distinguir falta de cancelamento nos CANCELADO ja gravados.
--
-- Operacoes ADITIVAS: nenhuma coluna removida, nenhum dado existente alterado.

ALTER TABLE solicitacao_especialidade ADD COLUMN IF NOT EXISTS atendimento_observacao VARCHAR(500);
ALTER TABLE solicitacao_especialidade ADD COLUMN IF NOT EXISTS atendimento_registrado_por_id BIGINT;
ALTER TABLE solicitacao_especialidade ADD COLUMN IF NOT EXISTS atendimento_registrado_em TIMESTAMPTZ;
ALTER TABLE solicitacao_especialidade ADD COLUMN IF NOT EXISTS devolvido_como_item_id BIGINT;

ALTER TABLE solicitacao_especialidade
    DROP CONSTRAINT IF EXISTS fk_solicitacao_especialidade_atendimento_registrado_por;

ALTER TABLE solicitacao_especialidade
    ADD CONSTRAINT fk_solicitacao_especialidade_atendimento_registrado_por
    FOREIGN KEY (atendimento_registrado_por_id) REFERENCES usuarios(id) ON DELETE RESTRICT;

ALTER TABLE solicitacao_especialidade
    DROP CONSTRAINT IF EXISTS fk_solicitacao_especialidade_devolvido_como;

ALTER TABLE solicitacao_especialidade
    ADD CONSTRAINT fk_solicitacao_especialidade_devolvido_como
    FOREIGN KEY (devolvido_como_item_id) REFERENCES solicitacao_especialidade(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS ix_solicitacao_especialidade_devolvido_como
    ON solicitacao_especialidade (devolvido_como_item_id);
