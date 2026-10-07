package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

/** Antecedencia (dias entre criar o agendamento e a data marcada) num periodo. */
public interface AntecedenciaProjection {
    Long getTotal();
    Long getSemDataCriacao();
    Long getRetroativos();
    Double getMedia();
    Double getMediana();
    Long getAte1();
    Long getDe2a7();
    Long getDe8a15();
    Long getDe16a30();
    Long getMais30();
}
