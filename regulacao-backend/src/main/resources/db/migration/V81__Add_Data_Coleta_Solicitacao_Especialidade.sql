-- V81: Data da coleta no cadastro de Exame/Procedimento.
-- Campo opcional: identifica quando o material do exame foi (ou será) coletado.
-- Fica em solicitacao_especialidade porque é um dado por item solicitado —
-- a mesma Solicitação pode conter vários exames com coletas em datas distintas.

ALTER TABLE solicitacao_especialidade
    ADD COLUMN IF NOT EXISTS data_coleta DATE;
