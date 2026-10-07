package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

import java.math.BigDecimal;

/** Preco e grupo de uma especialidade, lidos sem carregar a entidade. */
public interface EspecialidadeCustoProjection {
    /** Codigo da especialidade em maiusculas. */
    String getCodigo();
    BigDecimal getValorUnitario();
    Long getGrupoId();
}
