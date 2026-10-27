package io.github.regulacao_marcarcao.regulacao_marcacao.dto;

import java.util.List;

/**
 * Envelope de listagem paginada das telas administrativas. Contrato próprio em
 * vez de {@code Page} do Spring Data, para o JSON não depender da serialização
 * de {@code PageImpl}. {@code pagina} começa em 0.
 */
public record PaginaDTO<T>(
        List<T> conteudo,
        int pagina,
        int tamanho,
        long totalElementos,
        int totalPaginas) {

    public static final int TAMANHO_MAXIMO = 200;

    /** Página negativa é erro do chamador (400); tamanho fora de 1..200 é ajustado. */
    public static int validarPagina(int pagina) {
        if (pagina < 0) {
            throw new IllegalArgumentException("A página não pode ser negativa.");
        }
        return pagina;
    }

    public static int limitarTamanho(int tamanho) {
        return Math.min(Math.max(tamanho, 1), TAMANHO_MAXIMO);
    }

    public static <T> PaginaDTO<T> de(List<T> conteudo, int pagina, int tamanho, long totalElementos) {
        int totalPaginas = tamanho <= 0 ? 0 : (int) ((totalElementos + tamanho - 1) / tamanho);
        return new PaginaDTO<>(conteudo, pagina, tamanho, totalElementos, totalPaginas);
    }
}
