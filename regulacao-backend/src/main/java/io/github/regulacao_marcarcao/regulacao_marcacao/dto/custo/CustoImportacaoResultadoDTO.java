package io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo;

import java.util.List;

/**
 * O que a importacao gravou de fato.
 *
 * @param gravadas    especialidades que tiveram preco ou codigo alterado
 * @param inalteradas itens que ja estavam iguais ao cadastro
 * @param ignoradas   itens que nao foram gravados (cada um tem um aviso)
 * @param avisos      motivo de cada item ignorado
 */
public record CustoImportacaoResultadoDTO(
        int gravadas,
        int inalteradas,
        int ignoradas,
        List<String> avisos) {
}
