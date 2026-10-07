-- V101: fila e registro de envios do WhatsApp, e a chave que liga/desliga o envio.
--
-- whatsapp_mensagem e ao mesmo tempo a FILA (linhas PENDENTE, despachadas por
-- tarefa agendada) e o REGISTRO de cada tentativa. Decisoes:
--  - Nao guarda o texto da mensagem nem o telefone: o conteudo e remontado na
--    hora do envio a partir do agendamento. So ficam os 4 ultimos digitos do
--    numero, para o operador reconhecer o destino.
--  - alvo_id NAO tem FK: o agendamento sofre hard delete (remarcar hoje e
--    excluir e criar de novo) e o registro do envio precisa sobreviver.
--  - chave_idempotencia e unica para impedir lembrete em dobro (reinicio, tarefa
--    repetida, "rodar agora"). Envio manual grava NULL de proposito: NULL nao
--    colide em indice unico, entao o reenvio manual sempre passa.
--  - meta_message_id e o id devolvido pela Meta; e por ele que o webhook de
--    status localiza a linha.
--
-- whatsapp_config tem uma unica linha (id = 1). O envio nasce DESLIGADO: subir
-- esta versao nao envia nada ate um ADMIN ligar pela tela.
--
-- Operacao ADITIVA: so tabelas novas. Na instancia sem credenciais do WhatsApp
-- as tabelas ficam vazias (nada e enfileirado sem configuracao).

CREATE TABLE IF NOT EXISTS whatsapp_mensagem (
    id                  BIGSERIAL PRIMARY KEY,
    alvo_tipo           VARCHAR(30)  NOT NULL,
    alvo_id             BIGINT       NOT NULL,
    solicitacao_id      BIGINT       REFERENCES solicitacao(id) ON DELETE SET NULL,
    tipo                VARCHAR(30)  NOT NULL,
    origem              VARCHAR(20)  NOT NULL,
    resultado           VARCHAR(20)  NOT NULL,
    motivo              VARCHAR(40),
    data_referencia     DATE         NOT NULL,
    itens_ref           VARCHAR(500),
    chave_idempotencia  VARCHAR(120),
    meta_message_id     VARCHAR(255),
    telefone_final      VARCHAR(4),
    tentativas          INTEGER      NOT NULL DEFAULT 0,
    erro_codigo         VARCHAR(40),
    cobravel            BOOLEAN,
    categoria_cobranca  VARCHAR(40),
    solicitado_por_id   BIGINT       REFERENCES usuarios(id) ON DELETE SET NULL,
    criado_em           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    enviar_apos         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    enviado_em          TIMESTAMPTZ,
    entregue_em         TIMESTAMPTZ,
    lido_em             TIMESTAMPTZ,
    falhou_em           TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_whatsapp_mensagem_chave ON whatsapp_mensagem (chave_idempotencia);
CREATE UNIQUE INDEX IF NOT EXISTS uk_whatsapp_mensagem_meta_id ON whatsapp_mensagem (meta_message_id);
CREATE INDEX IF NOT EXISTS idx_whatsapp_mensagem_fila ON whatsapp_mensagem (resultado, enviar_apos);
CREATE INDEX IF NOT EXISTS idx_whatsapp_mensagem_criado_em ON whatsapp_mensagem (criado_em);

CREATE TABLE IF NOT EXISTS whatsapp_config (
    id              BIGINT      PRIMARY KEY CHECK (id = 1),
    envio_ligado    BOOLEAN     NOT NULL DEFAULT FALSE,
    alterado_por_id BIGINT      REFERENCES usuarios(id) ON DELETE SET NULL,
    alterado_em     TIMESTAMPTZ
);

INSERT INTO whatsapp_config (id, envio_ligado)
SELECT 1, FALSE
WHERE NOT EXISTS (SELECT 1 FROM whatsapp_config WHERE id = 1);
