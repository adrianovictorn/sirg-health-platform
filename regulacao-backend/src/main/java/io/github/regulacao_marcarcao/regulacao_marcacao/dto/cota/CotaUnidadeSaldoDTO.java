package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota;

/**
 * Saldo de cota para uma unidade/especialidade num periodo.
 *
 * Quando nao existe cota configurada para o caso, as quantidades vem nulas e
 * {@code disponivel = true}: significa "sem restricao", nao "zero vagas".
 * {@code doGrupo} indica que a cota mais restritiva encontrada e a do grupo de
 * unidades, e nao a da unidade em si.
 */
public record CotaUnidadeSaldoDTO(
        Long unidadeId,
        String unidadeNome,
        Long especialidadeId,
        String especialidadeNome,
        String periodo,
        Integer quantidadeTotal,
        Integer quantidadeUtilizada,
        Integer saldoDisponivel,
        boolean disponivel,
        boolean doGrupo) {
}
