package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota;

import java.time.LocalDate;
import java.time.LocalDateTime;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;

public record CotaUnidadeViewDTO(
        Long id,
        Long unidadeId,
        String unidadeNome,
        Long especialidadeId,
        String especialidadeNome,
        TipoPeriodoCota tipoPeriodo,
        String periodo,
        LocalDate dataEspecifica,
        Integer quantidadeTotal,
        Integer quantidadeUtilizada,
        Integer saldoDisponivel,
        boolean ativo,
        LocalDateTime criadoEm) {

    public static CotaUnidadeViewDTO from(CotaUnidade c) {
        return new CotaUnidadeViewDTO(
                c.getId(),
                c.getUnidade().getId(),
                c.getUnidade().getNome(),
                c.getEspecialidade() != null ? c.getEspecialidade().getId() : null,
                c.getEspecialidade() != null ? c.getEspecialidade().getNome() : null,
                c.getTipoPeriodo(),
                c.getPeriodo(),
                c.getDataEspecifica(),
                c.getQuantidadeTotal(),
                c.getQuantidadeUtilizada(),
                c.getQuantidadeTotal() - c.getQuantidadeUtilizada(),
                c.isAtivo(),
                c.getCriadoEm());
    }
}
