CREATE TABLE IF NOT EXISTS unidade (
    id          BIGSERIAL PRIMARY KEY,
    nome        VARCHAR(200) NOT NULL,
    codigo      VARCHAR(50),
    cnes        VARCHAR(20),
    telefone    VARCHAR(20),
    endereco    VARCHAR(300),
    ativo       BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em   TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_unidade_nome UNIQUE (nome),
    CONSTRAINT uk_unidade_cnes UNIQUE (cnes)
);
