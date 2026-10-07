-- V106: valor da epoca em cada item agendado.
--
-- valor_unitario_agendado guarda o preco da especialidade NO MOMENTO do
-- agendamento. O painel de custos soma esta coluna (agendado e concluido), e nao
-- o preco atual do catalogo: um reajuste nao pode alterar relatorios de periodos
-- passados ja apresentados ao gestor.
--
-- Sem backfill, de proposito: nao existe "valor da epoca" para agendamentos
-- anteriores a esta versao. Eles ficam NULL e fora dos totais.
--
-- Operacao ADITIVA: coluna anulavel, nenhuma linha existente muda.

ALTER TABLE solicitacao_especialidade ADD COLUMN IF NOT EXISTS valor_unitario_agendado NUMERIC(12,2);
