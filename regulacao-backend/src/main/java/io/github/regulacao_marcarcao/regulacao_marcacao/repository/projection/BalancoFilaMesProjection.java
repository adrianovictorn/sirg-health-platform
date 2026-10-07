package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

/** Um mes do balanco da fila. {@code mes} no formato AAAA-MM. */
public interface BalancoFilaMesProjection {
    String getMes();
    Long getNovos();
    Long getAgendados();
    Long getConcluidos();
}
