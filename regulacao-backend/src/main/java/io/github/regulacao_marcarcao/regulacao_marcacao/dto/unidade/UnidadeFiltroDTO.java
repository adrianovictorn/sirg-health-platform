package io.github.regulacao_marcarcao.regulacao_marcacao.dto.unidade;

/**
 * Filtros da listagem paginada de unidades. {@code q} busca em nome, código e CNES
 * (nunca em telefone ou CNPJ). {@code status}: ativa | inativa. {@code tipo}:
 * SOLICITANTE | EXECUTANTE | AMBOS (o tipo declarado, não o papel derivado da
 * parametrização).
 */
public record UnidadeFiltroDTO(
        String q,
        String status,
        String tipo,
        Long grupoRelatorioId) {
}
