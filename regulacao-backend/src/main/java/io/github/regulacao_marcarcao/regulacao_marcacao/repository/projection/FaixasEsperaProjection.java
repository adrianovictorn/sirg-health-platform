package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

/** Contagem por faixa de espera (dias na fila) de uma unidade, especialidade ou prioridade. */
public interface FaixasEsperaProjection {
    Long getId();
    String getNome();
    Long getAte30();
    Long getDe31a60();
    Long getDe61a90();
    Long getMais90();
}
