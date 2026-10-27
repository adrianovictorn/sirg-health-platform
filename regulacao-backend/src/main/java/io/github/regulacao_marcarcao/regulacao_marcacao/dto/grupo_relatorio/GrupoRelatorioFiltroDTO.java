package io.github.regulacao_marcarcao.regulacao_marcacao.dto.grupo_relatorio;

/**
 * Filtros da listagem paginada de grupos de relatório. {@code q} busca em nome e
 * código. {@code status}: ativo | inativo. {@code direcionadoHospital}: sim | nao.
 */
public record GrupoRelatorioFiltroDTO(
        String q,
        String status,
        String direcionadoHospital) {
}
