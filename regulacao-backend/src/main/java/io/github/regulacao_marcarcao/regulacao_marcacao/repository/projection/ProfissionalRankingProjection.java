package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

public interface ProfissionalRankingProjection {
    Long getId();
    String getNome();
    String getConselho();
    String getNumeroRegistro();
    long getTotalSolicitacoes();
}
