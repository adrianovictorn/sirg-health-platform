package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota;

import java.util.List;

/**
 * Uma linha da tela de cotas: uma cota manual ({@code agendaId == null}, uma
 * única cota) ou o bloco de cotas de uma agenda (todas as cotas que passaram
 * pelo filtro). {@code totalCotasDaAgenda} é o total da agenda sem filtro, para a
 * linha-resumo mostrar "X de Y cotas".
 */
public record CotaListaItemDTO(
        Long agendaId,
        boolean ativo,
        long totalCotasDaAgenda,
        List<CotaUnidadeViewDTO> cotas) {
}
