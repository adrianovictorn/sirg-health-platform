package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

/**
 * Linha do resumo de atendimentos do dia. A mesma consulta devolve uma linha por
 * unidade e a linha do municipio (GROUPING SETS): {@code totalGeral} vale 1 na
 * linha do municipio e 0 nas linhas por unidade — onde {@code unidadeId} nulo e o
 * balde "Sem unidade".
 */
public interface AtendimentoResumoProjection {
    Long getUnidadeId();
    String getUnidadeNome();
    Integer getTotalGeral();
    Long getPacientes();
    Long getItens();
    Long getAgendados();
    Long getConfirmados();
    Long getFaltas();
    Long getCancelados();
}
