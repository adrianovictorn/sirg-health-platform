-- Permite que usf_origem seja nulo para suportar solicitações vinculadas
-- exclusivamente via unidade_id (novo fluxo), sem exigir o enum legado.
ALTER TABLE solicitacao
    ALTER COLUMN usf_origem DROP NOT NULL;
