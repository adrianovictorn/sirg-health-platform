-- Remove coluna legada criada em V1 antes de existir 'datanascimento' (V29).
-- A entidade Solicitacao mapeia 'datanascimento'; esta coluna nunca foi usada pela aplicação.
ALTER TABLE solicitacao DROP COLUMN IF EXISTS data_nascimento;
