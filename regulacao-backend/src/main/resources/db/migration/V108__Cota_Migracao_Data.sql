-- V108: migracao dos agendamentos de uma cota quando a DATA dela muda.
--
-- Ate aqui a data de uma cota por data podia ser editada livremente, mas os
-- agendamentos que ja a consumiam ficavam para tras: a cota ia para outra data e
-- os pacientes continuavam marcados na antiga. A migracao (CotaMigracaoDataService)
-- move, na mesma transacao, a cota e os agendamentos para a data nova.
--
--  - agendamento_solicitacao.data_agendada_original: a data em que o paciente foi
--    MARCADO pela primeira vez. Preenchida so na primeira migracao do agendamento
--    (COALESCE), de modo que migracoes sucessivas nao apagam a origem. Nula = nunca
--    foi migrado. Nao e exposta em nenhum DTO existente.
--
--  - cota_migracao_data: uma linha por operacao, para auditoria. Os ids de cota
--    NAO tem FK de proposito: quando ja existe cota na data nova as duas sao
--    unificadas e a de origem e EXCLUIDA — a linha de auditoria precisa sobreviver
--    a ela (mesmo raciocinio de whatsapp_mensagem.alvo_id, V101).
--
-- Operacoes ADITIVAS: coluna anulavel e tabela nova. Nenhum dado existente e alterado.

ALTER TABLE agendamento_solicitacao ADD COLUMN IF NOT EXISTS data_agendada_original DATE;

CREATE TABLE IF NOT EXISTS cota_migracao_data (
    id                   BIGSERIAL   PRIMARY KEY,
    cota_origem_id       BIGINT      NOT NULL,
    cota_resultante_id   BIGINT      NOT NULL,
    data_de              DATE        NOT NULL,
    data_para            DATE        NOT NULL,
    unificada            BOOLEAN     NOT NULL DEFAULT FALSE,
    agendamentos         INTEGER     NOT NULL,
    itens                INTEGER     NOT NULL,
    realizado_por_id     BIGINT      REFERENCES usuarios(id) ON DELETE RESTRICT,
    realizado_em         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_cota_migracao_data_origem ON cota_migracao_data (cota_origem_id);
