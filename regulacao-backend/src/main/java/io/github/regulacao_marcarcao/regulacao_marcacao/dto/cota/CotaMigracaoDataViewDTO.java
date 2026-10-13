package io.github.regulacao_marcarcao.regulacao_marcacao.dto.cota;

/** Resultado da migracao. {@code cotaResultanteId} e a cota de destino quando houve unificacao. */
public record CotaMigracaoDataViewDTO(
        Long cotaResultanteId,
        boolean unificada,
        int agendamentosMigrados,
        int itensMigrados) {
}
