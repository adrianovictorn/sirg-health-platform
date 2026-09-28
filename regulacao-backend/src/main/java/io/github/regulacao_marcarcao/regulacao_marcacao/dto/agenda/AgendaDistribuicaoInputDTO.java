package io.github.regulacao_marcarcao.regulacao_marcacao.dto.agenda;

/** Vagas por ocorrencia que a agenda destina a uma unidade solicitante. */
public record AgendaDistribuicaoInputDTO(
        Long unidadeSolicitanteId,
        Integer vagasPorOcorrencia) {
}
