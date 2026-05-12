ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS unidade_id BIGINT,
    ADD CONSTRAINT fk_usuarios_unidade
        FOREIGN KEY (unidade_id) REFERENCES unidade(id) ON DELETE SET NULL;
