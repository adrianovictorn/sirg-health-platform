package io.github.regulacao_marcarcao.regulacao_marcacao.dto.agenda;

import java.util.List;

/**
 * Edicao de agenda (R2, R3): nao altera ocorrencia que ja tem marcacao.
 *
 * <p>Reduzir {@code vagasPorOcorrencia} de uma distribuicao abaixo do que ja
 * foi consumido nas ocorrencias em aberto e recusado (409) — cancele a
 * ocorrencia e remaneje antes.
 */
public record AgendaUpdateDTO(
        String localDescricao,
        String observacao,
        boolean ativo,
        List<AgendaDistribuicaoInputDTO> distribuicoes) {
}
