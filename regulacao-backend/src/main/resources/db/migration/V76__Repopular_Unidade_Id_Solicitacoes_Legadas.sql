-- V76: Re-executa o vínculo solicitacao → unidade para registros legados.
-- A V73 rodou quando a tabela unidade ainda estava vazia, portanto o UPDATE
-- original não encontrou correspondências. Esta migration corrige isso.
--
-- A correspondência funciona por:
--   1. unidade.codigo  == solicitacao.usf_origem  (ex: 'USF01' = 'USF01')
--   2. unidade.nome normalizado == usf_origem normalizado
--      (ex: 'USF 01' → 'USF01' casa com 'USF01')

UPDATE solicitacao s
SET unidade_id = u.id
FROM unidade u
WHERE s.unidade_id IS NULL
  AND s.usf_origem IS NOT NULL
  AND u.ativo = true
  AND (
    UPPER(TRIM(u.codigo)) = UPPER(TRIM(s.usf_origem::text))
    OR UPPER(REPLACE(TRIM(u.nome), ' ', '')) = UPPER(REPLACE(TRIM(s.usf_origem::text), ' ', ''))
  );
