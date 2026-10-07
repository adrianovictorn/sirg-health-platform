package io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.TetoFinanceiro;

/** Teto financeiro com saldo — so para ADMIN e GESTOR. Saldo negativo = teto ultrapassado. */
public record TetoFinanceiroViewDTO(
        Long id,
        Long unidadeId,
        String unidadeNome,
        Long grupoEspecialidadesId,
        String grupoEspecialidadesNome,
        String periodo,
        BigDecimal valorTotal,
        BigDecimal valorUtilizado,
        BigDecimal saldoDisponivel,
        boolean ativo,
        LocalDateTime criadoEm,
        Long version) {

    public static TetoFinanceiroViewDTO from(TetoFinanceiro t) {
        return new TetoFinanceiroViewDTO(
                t.getId(),
                t.getUnidade().getId(),
                t.getUnidade().getNome(),
                t.getGrupoEspecialidades().getId(),
                t.getGrupoEspecialidades().getNome(),
                t.getPeriodo(),
                t.getValorTotal(),
                t.getValorUtilizado(),
                t.getValorTotal().subtract(t.getValorUtilizado()),
                t.isAtivo(),
                t.getCriadoEm(),
                t.getVersion());
    }
}
