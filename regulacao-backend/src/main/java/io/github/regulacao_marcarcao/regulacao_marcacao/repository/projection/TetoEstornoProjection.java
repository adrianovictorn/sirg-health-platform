package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

import java.math.BigDecimal;

/** Valor que um agendamento debitou de um teto financeiro. */
public interface TetoEstornoProjection {
    Long getTetoId();
    BigDecimal getValor();
}
