package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

import java.time.LocalDate;

/**
 * Um item agendado de uma solicitacao — so o que decide quais cotas a solicitacao
 * ocupa (V113). Sem dado do paciente.
 */
public interface ItemAgendadoSolicitacaoProjection {
    Long getItemId();
    Long getAgendamentoId();
    Long getEspecialidadeId();
    Long getCotaUnidadeId();
    LocalDate getDataAgendada();
}
