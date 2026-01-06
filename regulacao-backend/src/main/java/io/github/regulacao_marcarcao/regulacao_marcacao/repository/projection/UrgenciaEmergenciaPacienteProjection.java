package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

public interface UrgenciaEmergenciaPacienteProjection {
    Long getId();
    String getNomePaciente();
    String getCpfPaciente();
    String getDataNascimento();
    String getCns();
    String getItens();
    String getUsfOrigem();
}
