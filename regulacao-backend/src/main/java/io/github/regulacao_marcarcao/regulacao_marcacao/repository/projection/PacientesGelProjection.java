package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

import java.time.LocalDate;

public interface PacientesGelProjection {
    String getNomePaciente();
    String getCpfPaciente();
    String getCns();
    String getUsfOrigem();
    LocalDate getDataNascimento();
    String getEspecialidade();
    String getPrioridade();
    
} 