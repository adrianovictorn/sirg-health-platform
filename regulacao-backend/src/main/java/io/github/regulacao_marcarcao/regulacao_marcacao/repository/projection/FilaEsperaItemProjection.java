package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

import java.time.LocalDateTime;

public interface FilaEsperaItemProjection {
    Long getId();
    Long getSolicitacaoId();
    String getEspecialidadeNome();
    String getCategoria();
    String getStatus();
    String getPrioridade();
    LocalDateTime getDataCadastro();
}
