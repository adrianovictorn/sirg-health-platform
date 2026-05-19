-- V77: Garante que as unidades padrão (USF01-USF06 + HMCA) existam e vincula
-- os registros legados de solicitacao que ainda têm usf_origem preenchido
-- mas unidade_id = NULL.
--
-- ON CONFLICT ON CONSTRAINT uk_unidade_nome:
--   - Se a unidade já existe com aquele nome mas sem codigo → preenche o codigo.
--   - Se já existe com codigo → não altera nada (WHERE unidade.codigo IS NULL).
--   - Se não existe → insere.
--
-- O CNES é deixado NULL intencionalmente para não violar uk_unidade_cnes.
-- O nome/codigo podem ser ajustados via interface administrativa depois.

INSERT INTO unidade (nome, codigo, ativo)
VALUES
    ('USF 01', 'USF01', true),
    ('USF 02', 'USF02', true),
    ('USF 03', 'USF03', true),
    ('USF 04', 'USF04', true),
    ('USF 05', 'USF05', true),
    ('USF 06', 'USF06', true),
    ('HMCA',   'HMCA',  true)
ON CONFLICT ON CONSTRAINT uk_unidade_nome
DO UPDATE SET codigo = EXCLUDED.codigo
WHERE unidade.codigo IS NULL;

-- Vincula solicitacoes legadas (usf_origem preenchido, unidade_id ainda NULL).
-- Casa por: 1) unidade.codigo == usf_origem  2) nome normalizado == usf_origem normalizado
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
