package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

import java.time.Instant;
import java.time.LocalDate;

/** Linha do Relatorio de Atendimento: um item com desfecho. CPF e CNS saem completos — so vai para a planilha. */
public interface AtendimentoRelatorioProjection {
    LocalDate getDataAgendada();
    String getNomePaciente();
    String getCpfPaciente();
    String getCns();
    LocalDate getDataNascimento();
    String getUnidadeNome();
    String getEspecialidadeNome();
    String getStatus();
    LocalDate getDataColeta();
    String getObservacao();
    String getRegistradoPorNome();
    Instant getRegistradoEm();
}
