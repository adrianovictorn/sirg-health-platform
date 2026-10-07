package io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Liberacao de teto financeiro, em lote: o mesmo valor para varias unidades,
 * no mesmo grupo de especialidades e no mesmo mes.
 *
 * @param unidadeIds            unidades que recebem o teto
 * @param grupoEspecialidadesId grupo cujas especialidades debitam (ex.: Laboratorio)
 * @param periodo               mes, formato YYYY-MM
 * @param valorTotal            limite em reais para cada unidade
 */
public record TetoFinanceiroCreateDTO(
        @NotEmpty(message = "Selecione ao menos uma unidade.")
        List<Long> unidadeIds,
        @NotNull(message = "Selecione o grupo de especialidades.")
        Long grupoEspecialidadesId,
        @NotNull(message = "Informe o mês do teto.")
        String periodo,
        @NotNull(message = "Informe o valor do teto.")
        BigDecimal valorTotal) {
}
