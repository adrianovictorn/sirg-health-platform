package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface FilaEsperaPacienteProjection {
    Long getSolicitacaoId();
    String getNomePaciente();
    String getCpfPaciente();
    String getCns();
    LocalDate getDataNascimento();
    Long getUnidadeId();
    String getUnidadeNome();
    LocalDateTime getEntradaMaisAntiga();
}
