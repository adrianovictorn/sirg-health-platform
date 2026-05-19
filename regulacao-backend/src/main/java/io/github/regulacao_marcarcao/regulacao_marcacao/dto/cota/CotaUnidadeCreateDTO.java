package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota;

import java.time.LocalDate;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;

public record CotaUnidadeCreateDTO(
        Long unidadeId,
        Long especialidadeId,
        TipoPeriodoCota tipoPeriodo,
        String periodo,
        LocalDate dataEspecifica,
        Integer quantidadeTotal) {
}
