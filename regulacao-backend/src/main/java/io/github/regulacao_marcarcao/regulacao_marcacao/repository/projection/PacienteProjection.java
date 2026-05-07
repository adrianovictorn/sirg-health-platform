package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

import java.time.LocalDate;

public interface PacienteProjection {
    Long getId();
    String getNomePaciente();
    String getCpfPaciente();
    String getCns();
    String getUsfOrigem();
    LocalDate getDataNascimento();
    String getEspecialidade();
    String getPrioridade();
    Long getSolicitacaoEspecialidadeId();
}
