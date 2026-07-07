package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

public interface TempoEsperaGeralProjection {
    long getTotalAgendados();
    Double getTempoMedioEsperaDias();
    Integer getTempoMinimoEsperaDias();
    Integer getTempoMaximoEsperaDias();
}
