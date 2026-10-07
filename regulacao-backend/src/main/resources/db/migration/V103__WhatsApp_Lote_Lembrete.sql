-- V103: registro dos dias em que o lote de lembretes do WhatsApp rodou.
--
-- O lote roda as 8h e avisa os agendamentos de daqui a 3 dias. Esta tabela
-- responde uma pergunta so: "o lote de ontem rodou?". Se nao rodou (servidor
-- fora do ar, envio desligado), o lote de hoje inclui tambem os agendamentos de
-- daqui a 2 dias — recuperacao de um dia, nunca mais que isso.
--
-- So e gravada quando o lote roda com o envio configurado e ligado; ligar o
-- envio depois de semanas desligado nao dispara lembretes retroativos.
--
-- Operacao ADITIVA: tabela nova.

CREATE TABLE IF NOT EXISTS whatsapp_lote_lembrete (
    id            BIGSERIAL   PRIMARY KEY,
    data_execucao DATE        NOT NULL,
    executado_em  TIMESTAMPTZ NOT NULL DEFAULT now(),
    origem        VARCHAR(20) NOT NULL,
    enfileirados  INTEGER     NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_whatsapp_lote_lembrete_data ON whatsapp_lote_lembrete (data_execucao);
