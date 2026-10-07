-- V104: contagem diaria de mensagens RECEBIDAS de pacientes pelo WhatsApp.
--
-- So a quantidade por dia. O texto, o remetente e o id da mensagem NAO sao
-- gravados: o tratamento das respostas dos pacientes e outra entrega, com
-- decisao de retencao ainda pendente. Sem o id nao ha como descartar reenvio
-- da Meta, entao o numero e aproximado — serve para ver volume no painel.
--
-- Operacao ADITIVA: tabela nova.

CREATE TABLE IF NOT EXISTS whatsapp_recebidas_dia (
    data       DATE    PRIMARY KEY,
    quantidade INTEGER NOT NULL DEFAULT 0
);
