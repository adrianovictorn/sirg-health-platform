-- Popula solicitacao.unidade_id para registros legados onde ainda é NULL.
-- Tenta casar usf_origem (enum como texto) com unidade.codigo ou unidade.nome
-- de forma case-insensitive e ignorando espaços extras.
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
