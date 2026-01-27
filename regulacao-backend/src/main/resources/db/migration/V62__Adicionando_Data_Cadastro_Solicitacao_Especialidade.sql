ALTER TABLE solicitacao_especialidade
    ADD COLUMN data_cadastro TIMESTAMP NOT NULL DEFAULT now()