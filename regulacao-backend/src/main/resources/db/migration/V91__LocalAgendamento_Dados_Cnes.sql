-- V91: local_agendamento ganha os dados cadastrais do CNES, igual ao que a
-- V86 fez para unidade.
--
-- Contexto: o cadastro de "Local de Atendimento" (entidade LocalAgendamento,
-- endereco opcional vinculado a uma Agenda — ver docs/especificacoes/Agenda e
-- Oferta.md, 4.4, R7) ganhou busca por CNES na mesma tela, para o operador
-- preencher o endereco/dados do estabelecimento sem digitar tudo a mao.
--
-- `numero` e `endereco` ja existiam na tabela e sao reaproveitados — so os
-- campos que faltam para guardar o retorno da busca sao adicionados aqui.
--
-- Operacoes ADITIVAS: nenhuma coluna e removida, nenhum dado existente muda
-- de valor. Registros ja gravados (referenciados por agenda, agendamento_
-- transporte, agendamento_solicitacao, fechamento_indicadores_dia) ficam com
-- os campos novos nulos, sem necessidade de backfill.

ALTER TABLE local_agendamento ADD COLUMN IF NOT EXISTS cnes VARCHAR(20);
ALTER TABLE local_agendamento ADD COLUMN IF NOT EXISTS cnpj VARCHAR(14);
ALTER TABLE local_agendamento ADD COLUMN IF NOT EXISTS razao_social VARCHAR(255);
ALTER TABLE local_agendamento ADD COLUMN IF NOT EXISTS nome_fantasia VARCHAR(255);
ALTER TABLE local_agendamento ADD COLUMN IF NOT EXISTS bairro VARCHAR(150);
ALTER TABLE local_agendamento ADD COLUMN IF NOT EXISTS cep VARCHAR(8);
ALTER TABLE local_agendamento ADD COLUMN IF NOT EXISTS telefone VARCHAR(20);
ALTER TABLE local_agendamento ADD COLUMN IF NOT EXISTS email VARCHAR(150);

-- Quando os dados foram puxados da API. Nulo = cadastro 100% manual.
ALTER TABLE local_agendamento ADD COLUMN IF NOT EXISTS sincronizado_cnes_em TIMESTAMP;

-- Indice unico parcial (ignora NULL por construcao, sem precisar de COALESCE):
-- a maioria dos registros legados nao tem CNES, e um UNIQUE comum do Postgres
-- ja trata varios NULL como distintos entre si — o WHERE aqui so deixa isso
-- explicito, em vez de depender do comportamento implicito.
CREATE UNIQUE INDEX IF NOT EXISTS ux_local_agendamento_cnes
    ON local_agendamento (cnes)
    WHERE cnes IS NOT NULL;
