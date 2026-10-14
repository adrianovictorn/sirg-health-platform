package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

import java.time.LocalDate;

/** Um paciente (ficha) com atendimento no dia. CPF e CNS saem como estao gravados, completos. */
public interface AtendimentoPacienteProjection {
    Long getSolicitacaoId();
    String getNomePaciente();
    String getCpf();
    String getCns();
    LocalDate getDataNascimento();
    String getUnidadeNome();
}
