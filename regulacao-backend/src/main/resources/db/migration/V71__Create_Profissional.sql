CREATE TABLE IF NOT EXISTS profissional (
    id                    BIGSERIAL PRIMARY KEY,
    nome                  VARCHAR(200) NOT NULL,
    conselho              VARCHAR(20),
    numero_registro       VARCHAR(50),
    especialidade_atuacao VARCHAR(200),
    telefone              VARCHAR(20),
    unidade_id            BIGINT REFERENCES unidade(id) ON DELETE SET NULL,
    ativo                 BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em             TIMESTAMP NOT NULL DEFAULT NOW(),
    atualizado_em         TIMESTAMP
);
