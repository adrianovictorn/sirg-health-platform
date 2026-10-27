package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota;

import io.github.regulacao_marcarcao.regulacao_marcacao.dto.PaginaDTO;

/**
 * Página de /admin/cotas. A paginação conta TITULARES (unidade ou grupo de unidades), e
 * cada um vem com todas as suas cotas filtradas; os totais em cotas existem para o
 * contador não confundir o operador.
 * {@code totalCotas}: cotas que passaram pelo filtro. {@code totalUnidadesGeral} e
 * {@code totalCotasGeral}: totais de titulares e de cotas sem nenhum filtro.
 */
public record CotaListaPaginaDTO(
        PaginaDTO<CotaUnidadeBlocoDTO> pagina,
        long totalCotas,
        long totalUnidadesGeral,
        long totalCotasGeral) {
}
