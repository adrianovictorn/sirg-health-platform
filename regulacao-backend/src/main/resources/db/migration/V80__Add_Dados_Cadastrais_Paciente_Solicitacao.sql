-- V80: Dados cadastrais obrigatórios do paciente na Solicitação.
-- Nome do Pai, Nome da Mãe e Endereço passam a ser exigidos no cadastro.
--
-- As colunas são criadas como NULLABLE para não quebrar os registros já existentes
-- (que foram cadastrados antes da regra). A obrigatoriedade é aplicada na camada de
-- validação (DTO/Bean Validation), de modo que:
--   - todo cadastro/edição novo precisa informar os três campos;
--   - solicitações legadas continuam legíveis e agendáveis.
-- Quando a base histórica estiver saneada, estas colunas podem receber NOT NULL
-- em uma migração futura.

ALTER TABLE solicitacao
    ADD COLUMN IF NOT EXISTS nome_pai VARCHAR(150),
    ADD COLUMN IF NOT EXISTS nome_mae VARCHAR(150),
    ADD COLUMN IF NOT EXISTS endereco VARCHAR(300);
