package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

/** Vagas ofertadas e agendadas nas cotas de um profissional executante ou de uma faixa de horario. */
public interface OcupacaoCotaProjection {
    Long getId();
    String getNome();
    Long getCotas();
    Long getOfertadas();
    Long getAgendadas();
}
