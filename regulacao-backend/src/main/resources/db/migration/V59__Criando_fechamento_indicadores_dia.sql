CREATE TABLE fechamento_indicadores_dia(
  id BIGSERIAL PRIMARY KEY,
  data_referencia DATE NOT NULL,
  local_id BIGINT NOT NULL,
  grupo_id BIGINT NOT NULL,
  agendados_total int not NULL,
  atendidos_total int NOT NULL,
  faltosos_total int NOT NULL,
  cancelados_total int NOT NULL,
  solicitacoes_cadastradas_total int NOT NULL,
  fechado_em TIMESTAMP NOT NULL,
  fechado_por_user_id BIGINT,
  
  CONSTRAINT uk_fechamento_data_local_grupo UNIQUE (data_referencia, local_id, grupo_id),
  
  FOREIGN KEY (local_id) REFERENCES local_agendamento(id),
  FOREIGN KEY (grupo_id) REFERENCES grupo_relatorio(id),
  FOREIGN KEY (fechado_por_user_id) REFERENCES usuarios(id)
  );

CREATE INDEX idx_fechamento_data_local
ON fechamento_indicadores_dia (data_referencia, local_id);
