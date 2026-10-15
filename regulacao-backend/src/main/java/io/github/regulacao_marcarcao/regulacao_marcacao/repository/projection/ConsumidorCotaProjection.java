package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

import java.time.LocalDate;

/** Um item agendado que consome (ou incide sobre) uma cota — so ids e data. */
public interface ConsumidorCotaProjection {
    Long getItemId();
    Long getAgendamentoId();
    LocalDate getDataAgendada();
    /** V113: a cota e consumida por solicitacao — e ela que conta para o saldo. */
    Long getSolicitacaoId();
}
