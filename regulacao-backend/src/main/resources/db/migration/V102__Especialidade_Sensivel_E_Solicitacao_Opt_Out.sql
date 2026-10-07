-- V102: dois campos que controlam o que o WhatsApp pode dizer e para quem.
--
--  - especialidade.sensivel: quando TRUE, a mensagem nao cita o nome da
--    especialidade nem o local ("atendimento especializado"). O nome do exame
--    pode revelar condicao de saude a quem ler a tela do celular.
--    Nasce FALSE para todas: marcar e decisao de quem conhece o catalogo, feita
--    na tela de especialidades ANTES de ligar o envio.
--  - solicitacao.whatsapp_opt_out: paciente pediu para nao receber mensagens.
--    Fica em solicitacao porque e ali que vive o cadastro do paciente (nao ha
--    tabela de paciente). whatsapp_opt_out_em guarda quando foi marcado.
--
-- Operacao ADITIVA: colunas com default constante (sem reescrita de tabela no
-- PostgreSQL 11+), nenhuma linha existente muda de significado.

ALTER TABLE especialidade ADD COLUMN IF NOT EXISTS sensivel BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE solicitacao ADD COLUMN IF NOT EXISTS whatsapp_opt_out BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE solicitacao ADD COLUMN IF NOT EXISTS whatsapp_opt_out_em TIMESTAMPTZ;
