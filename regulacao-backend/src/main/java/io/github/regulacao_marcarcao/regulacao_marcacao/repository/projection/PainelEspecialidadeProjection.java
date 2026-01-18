package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

import java.time.LocalDate;

public interface PainelEspecialidadeProjection {
    Long getSolicitacaoId();
    String getNomePaciente();
    String getCpfPaciente();
    String getCns();
    LocalDate getDataNascimento();
    String getUsfOrigem();
    String getSolicitacaoEspecialidadeId();
    String getEspecialidades();

}
