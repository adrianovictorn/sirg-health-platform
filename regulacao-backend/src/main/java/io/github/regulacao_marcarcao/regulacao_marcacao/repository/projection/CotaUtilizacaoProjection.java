package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

/**
 * Utilizacao das cotas de um tipo de periodo, no total ou de um titular.
 * {@code utilizacaoMedia} e a media da razao utilizada/total de cada cota (0 a 1).
 */
public interface CotaUtilizacaoProjection {
    String getTipo();
    Long getUnidadeId();
    String getTitular();
    Long getCotas();
    Long getEsgotadas();
    Long getOciosas();
    Double getUtilizacaoMedia();
}
