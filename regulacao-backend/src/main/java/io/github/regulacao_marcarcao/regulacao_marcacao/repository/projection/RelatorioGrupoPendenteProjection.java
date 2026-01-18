package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

import java.time.LocalDate;

public interface RelatorioGrupoPendenteProjection {
    Long getSolicitacaoId();
    String getNomePaciente();
    String getCpfPaciente();
    String getCns();
    LocalDate getDataNascimento();
    String getUsfOrigem();
    String getStatus();
    String getEspecialidades();
    LocalDate getDataMalote();
    String getPrioridade();
}
