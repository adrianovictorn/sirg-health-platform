package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

public interface ProfissionalQuantitativoProjection {
    Long getProfissionalId();
    String getProfissionalNome();
    String getConselho();
    String getNumeroRegistro();
    long getTotalNoPeriodo();
    long getConsultas();
    long getExamesProcedimentos();
}
