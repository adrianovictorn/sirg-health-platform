-- V86: Unidade ganha TIPO (solicitante/executante) e os dados cadastrais do CNES.
--
-- Contexto: o oficio da Central de Regulacao de Sao Felipe (25/09/2026) pediu o
-- cadastro dos "estabelecimentos executantes" — o prestador que realiza o
-- atendimento — para depois vincular profissionais e abrir agenda sobre ele.
--
-- A decisao foi ESTENDER `unidade` em vez de criar uma tabela nova: cotas,
-- filas, dashboards, relatorios e o escopo de acesso por unidade ja filtram por
-- `unidade_id`. Uma entidade paralela obrigaria a duplicar toda essa logica.
-- O que separa USF de prestador passa a ser a coluna `tipo`.
--
-- Todas as operacoes sao ADITIVAS: nenhuma coluna e removida e nenhum dado
-- existente e alterado de valor. Ver docs/especificacoes/Agenda e Oferta.md.

-- ---------------------------------------------------------------------------
-- 1. Tipo da unidade
-- ---------------------------------------------------------------------------
-- DEFAULT 'AMBOS' de proposito: toda unidade que ja existe continua podendo
-- solicitar (como sempre fez) e passa a poder ser escolhida como executante.
-- Restringir depois, unidade a unidade, e seguro; restringir agora quebraria os
-- combos em producao.

ALTER TABLE unidade
    ADD COLUMN IF NOT EXISTS tipo VARCHAR(20) NOT NULL DEFAULT 'AMBOS';

ALTER TABLE unidade
    DROP CONSTRAINT IF EXISTS ck_unidade_tipo;

ALTER TABLE unidade
    ADD CONSTRAINT ck_unidade_tipo
    CHECK (tipo IN ('SOLICITANTE', 'EXECUTANTE', 'AMBOS'));

-- ---------------------------------------------------------------------------
-- 2. Dados cadastrais que vem da API do CNES
-- ---------------------------------------------------------------------------
-- Espelham a tela de cadastro de estabelecimento enviada pela coordenacao.
-- `cnes` ja existe desde a criacao da tabela e e UNIQUE — e a chave usada para
-- sincronizar com https://apidadosabertos.saude.gov.br/cnes/estabelecimentos.
--
-- Todas nullable: o cadastro manual continua valido quando o CNES esta fora do
-- ar ou quando a unidade nao tem CNES proprio.

ALTER TABLE unidade ADD COLUMN IF NOT EXISTS cnpj VARCHAR(14);
ALTER TABLE unidade ADD COLUMN IF NOT EXISTS razao_social VARCHAR(255);
ALTER TABLE unidade ADD COLUMN IF NOT EXISTS nome_fantasia VARCHAR(255);
ALTER TABLE unidade ADD COLUMN IF NOT EXISTS numero VARCHAR(20);
ALTER TABLE unidade ADD COLUMN IF NOT EXISTS bairro VARCHAR(150);
ALTER TABLE unidade ADD COLUMN IF NOT EXISTS cep VARCHAR(8);
ALTER TABLE unidade ADD COLUMN IF NOT EXISTS email VARCHAR(150);

-- Quando os dados foram puxados da API. Nulo = cadastro 100% manual.
ALTER TABLE unidade ADD COLUMN IF NOT EXISTS sincronizado_cnes_em TIMESTAMP;

-- ---------------------------------------------------------------------------
-- 3. Indice para o combo de executantes
-- ---------------------------------------------------------------------------
-- A tela de abertura de agenda lista apenas unidades ativas que executam.

CREATE INDEX IF NOT EXISTS ix_unidade_tipo_ativo
    ON unidade (tipo, ativo);
