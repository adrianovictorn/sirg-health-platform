CREATE TABLE grupo_relatorio(
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(155) NOT NULL,
    nome VARCHAR(155) NOT NULL,
    ativo boolean NOT NULL DEFAULT TRUE
);


ALTER TABLE especialidade 
    ADD COLUMN grupo_relatorio_id BIGINT;

ALTER TABLE especialidade
    ADD CONSTRAINT fk_grupo_relatorio_especialidade
    FOREIGN KEY (grupo_relatorio_id) REFERENCES grupo_relatorio(id);
