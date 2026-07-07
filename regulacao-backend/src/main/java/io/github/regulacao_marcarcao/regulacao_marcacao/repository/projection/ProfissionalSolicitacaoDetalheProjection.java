package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

import java.time.LocalDateTime;

public interface ProfissionalSolicitacaoDetalheProjection {
    Long getProfissionalId();
    String getProfissionalNome();
    String getConselho();
    String getNumeroRegistro();
    String getTipo();
    String getEspecialidadeNome();
    String getPacienteNome();
    String getCpfPaciente();
    LocalDateTime getDataSolicitacao();
}
