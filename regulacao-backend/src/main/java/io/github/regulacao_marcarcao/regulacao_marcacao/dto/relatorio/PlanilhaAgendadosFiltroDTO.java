package io.github.regulacao_marcarcao.regulacao_marcacao.dto.relatorio;

import java.time.LocalDate;

/**
 * Filtros da planilha de agendados de /relatorio ({@code /api/exportar/planilha} e
 * {@code /api/exportar/verificar-dados}).
 *
 * {@code grupo} e obrigatorio. A data agendada vem como {@code data} (dia unico) ou
 * como o par {@code dataInicio}/{@code dataFim} (periodo, pontas inclusivas), nunca os
 * dois. {@code unidadeId} e a unidade solicitante; {@code unidadeExecutanteId}, a
 * executante; {@code profissionalExecutanteId} e {@code semExecutante} sao excludentes.
 * Todos os opcionais so estreitam o resultado dentro do escopo de acesso do usuario.
 */
public record PlanilhaAgendadosFiltroDTO(
        String grupo,
        LocalDate data,
        LocalDate dataInicio,
        LocalDate dataFim,
        Long unidadeId,
        Long unidadeExecutanteId,
        Long profissionalExecutanteId,
        boolean semExecutante) {

    /** Filtro de hoje (grupo, dia e unidade solicitante), sem os filtros novos. */
    public static PlanilhaAgendadosFiltroDTO porData(String grupo, LocalDate data, Long unidadeId) {
        return new PlanilhaAgendadosFiltroDTO(grupo, data, null, null, unidadeId, null, null, false);
    }
}
