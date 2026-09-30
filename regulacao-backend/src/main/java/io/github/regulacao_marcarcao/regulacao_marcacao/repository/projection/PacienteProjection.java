package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

import java.time.LocalDate;
import java.time.LocalTime;

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

    /**
     * Profissional executante (V100): o sobrescrito pelo operador no
     * agendamento, ou, na ausencia, o espelhado pela cota usada. Nulo quando
     * nenhum dos dois define profissional, ou a especialidade nao foi
     * agendada por cota.
     */
    String getProfissionalExecutanteNome();

    /** Hora efetiva do agendamento (V97/V99/V100). Nula quando nao definida. */
    LocalTime getHoraAgendada();
}
