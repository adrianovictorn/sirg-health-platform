package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

import java.math.BigDecimal;

/** Linha agregada do painel de custos (total, por unidade ou por especialidade). */
public interface CustoPainelLinhaProjection {
    Long getId();
    String getNome();
    String getCodigoSus();
    BigDecimal getEstimado();
    Long getEstimadoItens();
    Long getEstimadoSemPreco();
    BigDecimal getAgendado();
    Long getAgendadoItens();
    Long getAgendadoSemValor();
    BigDecimal getConcluido();
    Long getConcluidoItens();
    Long getConcluidoSemValor();
}
