package io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo;

import java.math.BigDecimal;

/**
 * Preco e codigo SUS de uma especialidade, como o ADMIN os informa na tela.
 *
 * <p>Substituicao dos dois campos: nulo (ou texto vazio no codigo) LIMPA o
 * valor. A tela sempre reenvia os dois.
 */
public record EspecialidadeCustoUpdateDTO(
        String codigoSus,
        BigDecimal valorUnitario) {
}
