package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

import java.time.Instant;
import java.time.LocalDate;

/** Um item agendado para o dia. Os dados do paciente ficam em {@link AtendimentoPacienteProjection}. */
public interface AtendimentoProjection {
    Long getSolicitacaoEspecialidadeId();
    Long getSolicitacaoId();
    String getEspecialidadeNome();
    String getCategoria();
    String getStatus();
    String getTurno();
    String getHoraAgendada();
    LocalDate getDataColeta();
    String getObservacao();
    String getRegistradoPorNome();
    Instant getRegistradoEm();
    Long getDevolvidoComoItemId();
}
