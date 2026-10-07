package io.github.regulacao_marcarcao.regulacao_marcacao.dto.custo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import io.github.regulacao_marcarcao.regulacao_marcacao.entity.Especialidade;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.ItemCategoria;
import io.github.regulacao_marcarcao.regulacao_marcacao.entity.enums.OrigemValorEspecialidade;

/**
 * Especialidade com preco e codigo SUS — so para ADMIN e GESTOR.
 *
 * <p>DTO separado de {@code EspecialidadeViewDTO} de proposito: aquele e
 * devolvido a qualquer usuario autenticado (combos, solicitacao, agendamento) e
 * nunca pode carregar valor.
 */
public record EspecialidadeCustoViewDTO(
        Long id,
        String codigo,
        String nome,
        ItemCategoria categoria,
        Boolean ativo,
        Long grupoRelatorioId,
        String grupoRelatorioNome,
        String codigoSus,
        BigDecimal valorUnitario,
        OrigemValorEspecialidade valorOrigem,
        LocalDateTime valorAtualizadoEm,
        String valorAtualizadoPorNome) {

    public static EspecialidadeCustoViewDTO from(Especialidade e) {
        return new EspecialidadeCustoViewDTO(
                e.getId(),
                e.getCodigo(),
                e.getNome(),
                e.getCategoria(),
                e.getAtivo(),
                e.getGrupoRelatorio() != null ? e.getGrupoRelatorio().getId() : null,
                e.getGrupoRelatorio() != null ? e.getGrupoRelatorio().getNome() : null,
                e.getCodigoSus(),
                e.getValorUnitario(),
                e.getValorOrigem(),
                e.getValorAtualizadoEm(),
                e.getValorAtualizadoPor() != null ? e.getValorAtualizadoPor().getNome() : null);
    }
}
