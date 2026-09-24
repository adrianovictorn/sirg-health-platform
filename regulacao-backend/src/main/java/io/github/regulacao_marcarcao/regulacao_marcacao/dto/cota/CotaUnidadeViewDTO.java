package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota;

import java.time.LocalDate;
import java.time.LocalDateTime;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.CotaUnidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.TipoPeriodoCota;

public record CotaUnidadeViewDTO(
        Long id,
        // titular
        Long unidadeId,
        String unidadeNome,
        Long grupoUnidadesId,
        String grupoUnidadesNome,
        // escopo
        Long especialidadeId,
        String especialidadeNome,
        Long grupoEspecialidadesId,
        String grupoEspecialidadesNome,
        // periodo e saldo
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
                c.getUnidade() != null ? c.getUnidade().getId() : null,
                c.getUnidade() != null ? c.getUnidade().getNome() : null,
                c.getGrupoUnidades() != null ? c.getGrupoUnidades().getId() : null,
                c.getGrupoUnidades() != null ? c.getGrupoUnidades().getNome() : null,
                c.getEspecialidade() != null ? c.getEspecialidade().getId() : null,
                c.getEspecialidade() != null ? c.getEspecialidade().getNome() : null,
                c.getGrupoEspecialidades() != null ? c.getGrupoEspecialidades().getId() : null,
                c.getGrupoEspecialidades() != null ? c.getGrupoEspecialidades().getNome() : null,
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
