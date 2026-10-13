package io.github.regulacao_marcarcao.regulacao_marcacao.repository.projection;

/** Totais acumulados do municipio para a vitrine publica. So numeros. */
public interface VitrineTotaisProjection {
    Long getSolicitacoes();
    Long getAgendamentos();
    Long getPacientesAtendidos();
    Long getViagensTransporte();
    Long getUnidades();
    Long getEspecialidades();
}
