package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

/**
 * Filtros da listagem paginada de cotas. Todos opcionais; os valores de lista
 * ({@code status}, {@code saldo}, {@code origem}, {@code tipo}) são validados em
 * {@code CotaListagemService}. Espelha as chaves de {@code cotasFiltro.js}.
 *
 * {@code status}: ativa | inativa (vazio = todas). {@code saldo}: com-saldo |
 * esgotada. {@code origem}: manual | agenda. {@code tipo}: mensal | data.
 * {@code competencia}: YYYY-MM.
 */
public record CotaUnidadeFiltroDTO(
        Long unidadeId,
        Long especialidadeId,
        String status,
        String saldo,
        String origem,
        String tipo,
        Long profissionalId,
        Long executanteId,
        String competencia,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataDe,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataAte,
        String q) {

    public static CotaUnidadeFiltroDTO vazio() {
        return new CotaUnidadeFiltroDTO(null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
