package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota;

import java.time.LocalDateTime;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;

public record CotaUnidadeViewDTO(
        Long id,
        Long unidadeId,
        String unidadeNome,
        Long especialidadeId,
        String especialidadeNome,
        String periodo,
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
                c.getPeriodo(),
                c.getQuantidadeTotal(),
                c.getQuantidadeUtilizada(),
                c.getQuantidadeTotal() - c.getQuantidadeUtilizada(),
                c.isAtivo(),
                c.getCriadoEm());
    }
}
