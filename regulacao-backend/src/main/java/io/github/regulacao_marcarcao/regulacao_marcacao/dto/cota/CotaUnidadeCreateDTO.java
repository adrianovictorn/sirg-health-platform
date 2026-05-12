package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota;

public record CotaUnidadeCreateDTO(
        Long unidadeId,
        Long especialidadeId,
        String periodo,
        Integer quantidadeTotal) {
}
