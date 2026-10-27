package io.github.regulacao_marcarcao.regulacao_marcacao.entity;

/** Utilidades de texto compartilhadas pelas Specifications de busca. */
final class SpecificationTexto {

    private SpecificationTexto() {
    }

    /** Escapa {@code \}, {@code %} e {@code _} para o termo digitado valer como texto literal num LIKE. */
    static String escaparLike(String termo) {
        return termo.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
