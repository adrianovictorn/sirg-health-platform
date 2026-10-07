package io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;

/**
 * Edicao de um teto: so o valor e o ativo. Unidade, grupo e mes nao mudam —
 * sao a identidade do teto e o destino dos debitos ja feitos.
 *
 * @param version versao lida pela tela; edicao sobre versao antiga e recusada (409)
 */
public record TetoFinanceiroUpdateDTO(
        @NotNull(message = "Informe o valor do teto.")
        BigDecimal valorTotal,
        boolean ativo,
        Long version) {
}
