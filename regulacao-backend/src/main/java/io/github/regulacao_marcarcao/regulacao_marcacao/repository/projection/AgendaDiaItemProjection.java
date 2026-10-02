package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

import java.time.LocalDate;

public interface AgendaDiaItemProjection {
    Long getSolicitacaoEspecialidadeId();
    Long getSolicitacaoId();
    String getNomePaciente();
    String getCpfPaciente();
    String getCns();
    LocalDate getDataNascimento();
    String getUnidadeNome();
    String getUsfOrigem();
    String getEspecialidadeNome();
    String getStatus();
    String getTurno();
    String getHoraAgendada();
}
