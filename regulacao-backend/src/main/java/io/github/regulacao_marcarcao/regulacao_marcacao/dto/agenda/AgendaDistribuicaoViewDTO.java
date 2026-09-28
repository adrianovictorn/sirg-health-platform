package io.github.regulacao_marcarcao.regulacao_marcacao.dto.agenda;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.AgendaDistribuicao;

public record AgendaDistribuicaoViewDTO(
        Long id,
        Long unidadeSolicitanteId,
        String unidadeSolicitanteNome,
        Integer vagasPorOcorrencia) {

    public static AgendaDistribuicaoViewDTO from(AgendaDistribuicao d) {
        return new AgendaDistribuicaoViewDTO(
                d.getId(),
                d.getUnidadeSolicitante().getId(),
                d.getUnidadeSolicitante().getNome(),
                d.getVagasPorOcorrencia());
    }
}
