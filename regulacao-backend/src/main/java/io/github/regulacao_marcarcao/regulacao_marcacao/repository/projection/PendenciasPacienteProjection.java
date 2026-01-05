package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

import java.time.LocalDate;

public interface PendenciasPacienteProjection {
    Long getId();
    String getNomePaciente();
    String getCpfPaciente();
    LocalDate getDataNascimento();
    String getCns();
    String getEspecialidades();
    
}
