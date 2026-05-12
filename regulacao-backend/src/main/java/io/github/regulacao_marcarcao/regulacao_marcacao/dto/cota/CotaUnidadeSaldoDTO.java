package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota;

public record CotaUnidadeSaldoDTO(
        Long unidadeId,
        String unidadeNome,
        Long especialidadeId,
        String especialidadeNome,
        String periodo,
        Integer quantidadeTotal,
        Integer quantidadeUtilizada,
        Integer saldoDisponivel,
        boolean disponivel) {
}
